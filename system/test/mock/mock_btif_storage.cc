/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:44
 */

#include <cstdint>
#include <string>

#include "bta/include/bta_hearing_aid_api.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_sec_api_types.h"
#include "test/common/mock_functions.h"
#include "types/ble_address_with_type.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

Octet16 btif_storage_get_gatt_cl_db_hash(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  Octet16 octet;
  return octet;
}
bool btif_has_ble_keys(const std::string& bdstr) {
  inc_func_call_count(__func__);
  return false;
}
bool btif_storage_get_hearing_aid_prop(
    const RawAddress& address, uint8_t* capabilities, uint64_t* hi_sync_id,
    uint16_t* render_delay, uint16_t* preparation_delay, uint16_t* codecs) {
  inc_func_call_count(__func__);
  return false;
}
bool btif_storage_get_stored_remote_name(const RawAddress& bd_addr,
                                         char* name) {
  inc_func_call_count(__func__);
  return false;
}
bool btif_storage_is_restricted_device(const RawAddress* remote_bd_addr) {
  inc_func_call_count(__func__);
  return false;
}
bt_status_t btif_storage_add_ble_bonding_key(RawAddress* remote_bd_addr,
                                             const uint8_t* key,
                                             uint8_t key_type,
                                             uint8_t key_length) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_add_ble_local_key(const Octet16& key,
                                           uint8_t key_type) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_add_bonded_device(RawAddress* remote_bd_addr,
                                           LinkKey link_key, uint8_t key_type,
                                           uint8_t pin_length) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_add_hid_device_info(
    RawAddress* remote_bd_addr, uint16_t attr_mask, uint8_t sub_class,
    uint8_t app_id, uint16_t vendor_id, uint16_t product_id, uint16_t version,
    uint8_t ctry_code, uint16_t ssr_max_latency, uint16_t ssr_min_tout,
    uint16_t dl_len, uint8_t* dsc_list) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_add_remote_device(const RawAddress* remote_bd_addr,
                                           uint32_t num_properties,
                                           bt_property_t* properties) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_get_adapter_property(bt_property_t* property) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_get_ble_bonding_key(const RawAddress& remote_bd_addr,
                                             uint8_t key_type,
                                             uint8_t* key_value,
                                             int key_length) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_get_ble_local_key(uint8_t key_type,
                                           Octet16* key_value) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_get_remote_addr_type(const RawAddress* remote_bd_addr,
                                              tBLE_ADDR_TYPE* addr_type) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_get_remote_device_property(
    const RawAddress* remote_bd_addr, bt_property_t* property) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_load_bonded_devices(void) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_load_bonded_hid_info(void) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_load_hidd(void) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_remove_ble_bonding_keys(
    const RawAddress* remote_bd_addr) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_remove_ble_local_keys(void) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_remove_bonded_device(
    const RawAddress* remote_bd_addr) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_remove_hid_info(const RawAddress& remote_bd_addr) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_remove_hidd(RawAddress* remote_bd_addr) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_set_adapter_property(bt_property_t* property) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_set_hidd(const RawAddress& remote_bd_addr) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_set_remote_addr_type(const RawAddress* remote_bd_addr,
                                              tBLE_ADDR_TYPE addr_type) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t btif_storage_set_remote_device_property(
    const RawAddress* remote_bd_addr, bt_property_t* property) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
void btif_storage_add_hearing_aid(const HearingDevice& dev_info) {
  inc_func_call_count(__func__);
}
int btif_storage_get_num_bonded_devices(void) {
  inc_func_call_count(__func__);
  return 0;
}
size_t btif_split_uuids_string(const char* str, bluetooth::Uuid* p_uuid,
                               size_t max_uuids) {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t btif_storage_get_gatt_cl_supp_feat(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return 0;
}
tBTM_IO_CAP btif_storage_get_local_io_caps() {
  inc_func_call_count(__func__);
  return BTM_IO_CAP_UNKNOWN;
}
tBTM_IO_CAP btif_storage_get_local_io_caps_ble() {
  inc_func_call_count(__func__);
  return BTM_IO_CAP_UNKNOWN;
}
void btif_storage_load_bonded_hearing_aids() { inc_func_call_count(__func__); }
void btif_storage_remove_gatt_cl_db_hash(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
}
void btif_storage_remove_gatt_cl_supp_feat(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
}
void btif_storage_remove_hearing_aid(const RawAddress& address) {
  inc_func_call_count(__func__);
}
void btif_storage_set_gatt_cl_db_hash(const RawAddress& bd_addr, Octet16 hash) {
  inc_func_call_count(__func__);
}
void btif_storage_set_gatt_cl_supp_feat(const RawAddress& bd_addr,
                                        uint8_t feat) {
  inc_func_call_count(__func__);
}
void btif_storage_set_hearing_aid_acceptlist(const RawAddress& address,
                                             bool add_to_acceptlist) {
  inc_func_call_count(__func__);
}
void btif_storage_set_gatt_sr_supp_feat(const RawAddress& addr, uint8_t feat) {
  inc_func_call_count(__func__);
}
uint8_t btif_storage_get_sr_supp_feat(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return 0;
}
void btif_storage_add_groups(RawAddress const&) {
  inc_func_call_count(__func__);
}
void btif_storage_remove_groups(RawAddress const&) {
  inc_func_call_count(__func__);
}
void btif_storage_load_bonded_groups() { inc_func_call_count(__func__); }
void btif_storage_set_csis_autoconnect(const RawAddress& addr,
                                       bool autoconnect) {
  inc_func_call_count(__func__);
}
void btif_storage_update_csis_info(const RawAddress& addr) {
  inc_func_call_count(__func__);
}
void btif_storage_load_bonded_csis_devices() { inc_func_call_count(__func__); }
void btif_storage_remove_csis_device(const RawAddress& address) {
  inc_func_call_count(__func__);
}
void btif_storage_set_pce_profile_version(const RawAddress& remote_bd_addr,
                                          uint16_t peer_pce_version) {
  inc_func_call_count(__func__);
}
bool btif_storage_is_pce_version_102(const RawAddress& remote_bd_addr) {
  inc_func_call_count(__func__);
  return false;
}
