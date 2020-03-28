/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <condition_variable>
#include <cstdint>
#include <unordered_map>

#include "common/bidi_queue.h"
#include "common/bind.h"
#include "grpc/grpc_event_queue.h"
#include "hci/address.h"
#include "l2cap/classic/facade.grpc.pb.h"
#include "l2cap/classic/facade.h"
#include "l2cap/classic/l2cap_classic_module.h"
#include "os/log.h"
#include "packet/raw_builder.h"

using ::grpc::ServerAsyncResponseWriter;
using ::grpc::ServerAsyncWriter;
using ::grpc::ServerContext;

using ::bluetooth::packet::RawBuilder;

namespace bluetooth {
namespace l2cap {
namespace classic {

class L2capClassicModuleFacadeService : public L2capClassicModuleFacade::Service {
 public:
  L2capClassicModuleFacadeService(L2capClassicModule* l2cap_layer, os::Handler* facade_handler)
      : l2cap_layer_(l2cap_layer), facade_handler_(facade_handler) {
    ASSERT(l2cap_layer_ != nullptr);
    ASSERT(facade_handler_ != nullptr);
  }

  ::grpc::Status FetchConnectionComplete(::grpc::ServerContext* context, const ::google::protobuf::Empty* request,
                                         ::grpc::ServerWriter<classic::ConnectionCompleteEvent>* writer) override {
    return pending_connection_complete_.RunLoop(context, writer);
  }

  ::grpc::Status FetchConnectionClose(::grpc::ServerContext* context, const ::google::protobuf::Empty* request,
                                      ::grpc::ServerWriter<classic::ConnectionCloseEvent>* writer) override {
    return pending_connection_close_.RunLoop(context, writer);
  }

  ::grpc::Status SendDynamicChannelPacket(::grpc::ServerContext* context, const DynamicChannelPacket* request,
                                          ::google::protobuf::Empty* response) override {
    std::unique_lock<std::mutex> lock(channel_map_mutex_);
    if (dynamic_channel_helper_map_.find(request->psm()) == dynamic_channel_helper_map_.end()) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Psm not registered");
    }
    std::vector<uint8_t> packet(request->payload().begin(), request->payload().end());
    if (!dynamic_channel_helper_map_[request->psm()]->SendPacket(packet)) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Channel not open");
    }
    return ::grpc::Status::OK;
  }

  ::grpc::Status OpenChannel(::grpc::ServerContext* context,
                             const ::bluetooth::l2cap::classic::OpenChannelRequest* request,
                             ::google::protobuf::Empty* response) override {
    auto service_helper = dynamic_channel_helper_map_.find(request->psm());
    if (service_helper == dynamic_channel_helper_map_.end()) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Psm not registered");
    }
    hci::Address peer;
    ASSERT(hci::Address::FromString(request->remote().address(), peer));
    dynamic_channel_helper_map_[request->psm()]->Connect(peer);
    return ::grpc::Status::OK;
  }

  ::grpc::Status CloseChannel(::grpc::ServerContext* context,
                              const ::bluetooth::l2cap::classic::CloseChannelRequest* request,
                              ::google::protobuf::Empty* response) override {
    auto psm = request->psm();
    if (dynamic_channel_helper_map_.find(request->psm()) == dynamic_channel_helper_map_.end()) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Psm not registered");
    }
    dynamic_channel_helper_map_[psm]->Disconnect();
    return ::grpc::Status::OK;
  }

  ::grpc::Status FetchL2capData(::grpc::ServerContext* context, const ::google::protobuf::Empty* request,
                                ::grpc::ServerWriter<classic::L2capPacket>* writer) override {
    auto status = pending_l2cap_data_.RunLoop(context, writer);

    return status;
  }

  ::grpc::Status SetDynamicChannel(::grpc::ServerContext* context, const SetEnableDynamicChannelRequest* request,
                                   google::protobuf::Empty* response) override {
    dynamic_channel_helper_map_.emplace(
        request->psm(), std::make_unique<L2capDynamicChannelHelper>(this, l2cap_layer_, facade_handler_, request->psm(),
                                                                    request->retransmission_mode()));
    return ::grpc::Status::OK;
  }

  class L2capDynamicChannelHelper {
   public:
    L2capDynamicChannelHelper(L2capClassicModuleFacadeService* service, L2capClassicModule* l2cap_layer,
                              os::Handler* handler, Psm psm, RetransmissionFlowControlMode mode)
        : facade_service_(service), l2cap_layer_(l2cap_layer), handler_(handler), psm_(psm), mode_(mode) {
      dynamic_channel_manager_ = l2cap_layer_->GetDynamicChannelManager();
      DynamicChannelConfigurationOption configuration_option = {};
      configuration_option.channel_mode = (DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode)mode;
      dynamic_channel_manager_->RegisterService(
          psm, configuration_option, {},
          common::BindOnce(&L2capDynamicChannelHelper::on_l2cap_service_registration_complete,
                           common::Unretained(this)),
          common::Bind(&L2capDynamicChannelHelper::on_connection_open, common::Unretained(this)), handler_);
    }

    ~L2capDynamicChannelHelper() {
      if (channel_ != nullptr) {
        channel_->GetQueueUpEnd()->UnregisterDequeue();
        channel_ = nullptr;
      }
    }

    void Connect(hci::Address address) {
      DynamicChannelConfigurationOption configuration_option = l2cap::classic::DynamicChannelConfigurationOption();
      configuration_option.channel_mode = (DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode)mode_;

      dynamic_channel_manager_->ConnectChannel(
          address, configuration_option, psm_,
          common::Bind(&L2capDynamicChannelHelper::on_connection_open, common::Unretained(this)),
          common::Bind(&L2capDynamicChannelHelper::on_connect_fail, common::Unretained(this)), handler_);
      std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
      if (!channel_open_cv_.wait_for(lock, std::chrono::seconds(2), [this] { return channel_ != nullptr; })) {
        LOG_WARN("Channel is not open for psm %d", psm_);
      }
    }

    void Disconnect() {
      if (channel_ == nullptr) {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        if (!channel_open_cv_.wait_for(lock, std::chrono::seconds(2), [this] { return channel_ != nullptr; })) {
          LOG_WARN("Channel is not open for psm %d", psm_);
          return;
        }
      }
      channel_->Close();
    }

    void on_l2cap_service_registration_complete(DynamicChannelManager::RegistrationResult registration_result,
                                                std::unique_ptr<DynamicChannelService> service) {}

    // invoked from Facade Handler
    void on_connection_open(std::unique_ptr<DynamicChannel> channel) {
      ConnectionCompleteEvent event;
      event.mutable_remote()->set_address(channel->GetDevice().GetAddress().ToString());
      facade_service_->pending_connection_complete_.OnIncomingEvent(event);
      {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        channel_ = std::move(channel);
      }
      channel_open_cv_.notify_all();
      channel_->RegisterOnCloseCallback(
          facade_service_->facade_handler_,
          common::BindOnce(&L2capDynamicChannelHelper::on_close_callback, common::Unretained(this)));
      channel_->GetQueueUpEnd()->RegisterDequeue(
          facade_service_->facade_handler_,
          common::Bind(&L2capDynamicChannelHelper::on_incoming_packet, common::Unretained(this)));
    }

    void on_close_callback(hci::ErrorCode error_code) {
      {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        channel_->GetQueueUpEnd()->UnregisterDequeue();
      }
      classic::ConnectionCloseEvent event;
      event.mutable_remote()->set_address(channel_->GetDevice().GetAddress().ToString());
      event.set_reason(static_cast<uint32_t>(error_code));
      facade_service_->pending_connection_close_.OnIncomingEvent(event);
      channel_ = nullptr;
    }

    void on_connect_fail(DynamicChannelManager::ConnectionResult result) {}

    void on_incoming_packet() {
      auto packet = channel_->GetQueueUpEnd()->TryDequeue();
      std::string data = std::string(packet->begin(), packet->end());
      L2capPacket l2cap_data;
      //      l2cap_data.set_channel(cid_);
      l2cap_data.set_payload(data);
      facade_service_->pending_l2cap_data_.OnIncomingEvent(l2cap_data);
    }

    bool SendPacket(std::vector<uint8_t> packet) {
      if (channel_ == nullptr) {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        if (!channel_open_cv_.wait_for(lock, std::chrono::seconds(2), [this] { return channel_ != nullptr; })) {
          LOG_WARN("Channel is not open");
          return false;
        }
      }
      std::promise<void> promise;
      auto future = promise.get_future();
      channel_->GetQueueUpEnd()->RegisterEnqueue(
          handler_, common::Bind(&L2capDynamicChannelHelper::enqueue_callback, common::Unretained(this), packet,
                                 common::Passed(std::move(promise))));
      auto status = future.wait_for(std::chrono::milliseconds(500));
      if (status != std::future_status::ready) {
        LOG_ERROR("Can't send packet");
        return false;
      }
      return true;
    }

    std::unique_ptr<packet::BasePacketBuilder> enqueue_callback(std::vector<uint8_t> packet,
                                                                std::promise<void> promise) {
      auto packet_one = std::make_unique<packet::RawBuilder>(2000);
      packet_one->AddOctets(packet);
      channel_->GetQueueUpEnd()->UnregisterEnqueue();
      promise.set_value();
      return packet_one;
    };

    L2capClassicModuleFacadeService* facade_service_;
    L2capClassicModule* l2cap_layer_;
    os::Handler* handler_;
    std::unique_ptr<DynamicChannelManager> dynamic_channel_manager_;
    std::unique_ptr<DynamicChannelService> service_;
    std::unique_ptr<DynamicChannel> channel_ = nullptr;
    Psm psm_;
    RetransmissionFlowControlMode mode_ = RetransmissionFlowControlMode::BASIC;
    std::condition_variable channel_open_cv_;
    std::mutex channel_open_cv_mutex_;
  };

  L2capClassicModule* l2cap_layer_;
  ::bluetooth::os::Handler* facade_handler_;
  std::mutex channel_map_mutex_;
  std::map<Psm, std::unique_ptr<L2capDynamicChannelHelper>> dynamic_channel_helper_map_;
  bool fetch_l2cap_data_ = false;
  ::bluetooth::grpc::GrpcEventQueue<classic::ConnectionCompleteEvent> pending_connection_complete_{
      "FetchConnectionComplete"};
  ::bluetooth::grpc::GrpcEventQueue<classic::ConnectionCloseEvent> pending_connection_close_{"FetchConnectionClose"};
  ::bluetooth::grpc::GrpcEventQueue<L2capPacket> pending_l2cap_data_{"FetchL2capData"};
};

void L2capClassicModuleFacadeModule::ListDependencies(ModuleList* list) {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<l2cap::classic::L2capClassicModule>();
}

void L2capClassicModuleFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new L2capClassicModuleFacadeService(GetDependency<l2cap::classic::L2capClassicModule>(), GetHandler());
}

void L2capClassicModuleFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* L2capClassicModuleFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory L2capClassicModuleFacadeModule::Factory =
    ::bluetooth::ModuleFactory([]() { return new L2capClassicModuleFacadeModule(); });

}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
