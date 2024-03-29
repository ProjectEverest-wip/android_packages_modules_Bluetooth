/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.bluetooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.TetheringManager;
import android.net.TetheringManager.TetheredInterfaceCallback;
import android.net.TetheringManager.TetheredInterfaceRequest;
import android.os.Environment;
import android.util.Log;

import junit.framework.Assert;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class BluetoothTestUtils extends Assert {

    /** Timeout for enable/disable in ms. */
    private static final int ENABLE_DISABLE_TIMEOUT = 20000;

    /** Timeout for discoverable/undiscoverable in ms. */
    private static final int DISCOVERABLE_UNDISCOVERABLE_TIMEOUT = 5000;

    /** Timeout for starting/stopping a scan in ms. */
    private static final int START_STOP_SCAN_TIMEOUT = 5000;

    /** Timeout for pair/unpair in ms. */
    private static final int PAIR_UNPAIR_TIMEOUT = 20000;

    /** Timeout for connecting/disconnecting a profile in ms. */
    private static final int CONNECT_DISCONNECT_PROFILE_TIMEOUT = 20000;

    /** Timeout to start or stop a SCO channel in ms. */
    private static final int START_STOP_SCO_TIMEOUT = 10000;

    /** Timeout to connect a profile proxy in ms. */
    private static final int CONNECT_PROXY_TIMEOUT = 5000;

    /** Time between polls in ms. */
    private static final int POLL_TIME = 100;

    /** Timeout to get map message in ms. */
    private static final int GET_UNREAD_MESSAGE_TIMEOUT = 10000;

    /** Timeout to set map message status in ms. */
    private static final int SET_MESSAGE_STATUS_TIMEOUT = 2000;

    private abstract class FlagReceiver extends BroadcastReceiver {
        private int mExpectedFlags = 0;
        private int mFiredFlags = 0;
        private long mCompletedTime = -1;

        FlagReceiver(int expectedFlags) {
            mExpectedFlags = expectedFlags;
        }

        public int getFiredFlags() {
            synchronized (this) {
                return mFiredFlags;
            }
        }

        public long getCompletedTime() {
            synchronized (this) {
                return mCompletedTime;
            }
        }

        protected void setFiredFlag(int flag) {
            synchronized (this) {
                mFiredFlags |= flag;
                if ((mFiredFlags & mExpectedFlags) == mExpectedFlags) {
                    mCompletedTime = System.currentTimeMillis();
                }
            }
        }
    }

    private class BluetoothReceiver extends FlagReceiver {
        private static final int DISCOVERY_STARTED_FLAG = 1;
        private static final int DISCOVERY_FINISHED_FLAG = 1 << 1;
        private static final int SCAN_MODE_NONE_FLAG = 1 << 2;
        private static final int SCAN_MODE_CONNECTABLE_FLAG = 1 << 3;
        private static final int SCAN_MODE_CONNECTABLE_DISCOVERABLE_FLAG = 1 << 4;
        private static final int STATE_OFF_FLAG = 1 << 5;
        private static final int STATE_TURNING_ON_FLAG = 1 << 6;
        private static final int STATE_ON_FLAG = 1 << 7;
        private static final int STATE_TURNING_OFF_FLAG = 1 << 8;
        private static final int STATE_GET_MESSAGE_FINISHED_FLAG = 1 << 9;
        private static final int STATE_SET_MESSAGE_STATUS_FINISHED_FLAG = 1 << 10;

        BluetoothReceiver(int expectedFlags) {
            super(expectedFlags);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(intent.getAction())) {
                setFiredFlag(DISCOVERY_STARTED_FLAG);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                setFiredFlag(DISCOVERY_FINISHED_FLAG);
            } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(intent.getAction())) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
                assertNotSame(-1, mode);
                switch (mode) {
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        setFiredFlag(SCAN_MODE_NONE_FLAG);
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        setFiredFlag(SCAN_MODE_CONNECTABLE_FLAG);
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        setFiredFlag(SCAN_MODE_CONNECTABLE_DISCOVERABLE_FLAG);
                        break;
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                assertNotSame(-1, state);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        setFiredFlag(STATE_OFF_FLAG);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        setFiredFlag(STATE_TURNING_ON_FLAG);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        setFiredFlag(STATE_ON_FLAG);
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        setFiredFlag(STATE_TURNING_OFF_FLAG);
                        break;
                }
            }
        }
    }

    private class PairReceiver extends FlagReceiver {
        private static final int STATE_BONDED_FLAG = 1;
        private static final int STATE_BONDING_FLAG = 1 << 1;
        private static final int STATE_NONE_FLAG = 1 << 2;

        private BluetoothDevice mDevice;
        private int mPasskey;
        private byte[] mPin;

        PairReceiver(BluetoothDevice device, int passkey, byte[] pin, int expectedFlags) {
            super(expectedFlags);

            mDevice = device;
            mPasskey = passkey;
            mPin = pin;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mDevice.equals(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))) {
                return;
            }

            if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
                int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
                assertNotSame(-1, variant);
                switch (variant) {
                    case BluetoothDevice.PAIRING_VARIANT_PIN:
                    case BluetoothDevice.PAIRING_VARIANT_PIN_16_DIGITS:
                        mDevice.setPin(mPin);
                        break;
                    case BluetoothDevice.PAIRING_VARIANT_PASSKEY:
                        break;
                    case BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION:
                    case BluetoothDevice.PAIRING_VARIANT_CONSENT:
                        mDevice.setPairingConfirmation(true);
                        break;
                    case BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT:
                        break;
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                assertNotSame(-1, state);
                switch (state) {
                    case BluetoothDevice.BOND_NONE:
                        setFiredFlag(STATE_NONE_FLAG);
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        setFiredFlag(STATE_BONDING_FLAG);
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        setFiredFlag(STATE_BONDED_FLAG);
                        break;
                }
            }
        }
    }

    private class ConnectProfileReceiver extends FlagReceiver {
        private static final int STATE_DISCONNECTED_FLAG = 1;
        private static final int STATE_CONNECTING_FLAG = 1 << 1;
        private static final int STATE_CONNECTED_FLAG = 1 << 2;
        private static final int STATE_DISCONNECTING_FLAG = 1 << 3;

        private BluetoothDevice mDevice;
        private int mProfile;
        private String mConnectionAction;

        ConnectProfileReceiver(BluetoothDevice device, int profile, int expectedFlags) {
            super(expectedFlags);

            mDevice = device;
            mProfile = profile;

            switch (mProfile) {
                case BluetoothProfile.A2DP:
                    mConnectionAction = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED;
                    break;
                case BluetoothProfile.HEADSET:
                    mConnectionAction = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED;
                    break;
                case BluetoothProfile.HID_HOST:
                    mConnectionAction = BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED;
                    break;
                case BluetoothProfile.PAN:
                    mConnectionAction = BluetoothPan.ACTION_CONNECTION_STATE_CHANGED;
                    break;
                case BluetoothProfile.MAP_CLIENT:
                    mConnectionAction = BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED;
                    break;
                default:
                    mConnectionAction = null;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mConnectionAction != null && mConnectionAction.equals(intent.getAction())) {
                if (!mDevice.equals(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))) {
                    return;
                }

                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                assertNotSame(-1, state);
                switch (state) {
                    case BluetoothProfile.STATE_DISCONNECTED:
                        setFiredFlag(STATE_DISCONNECTED_FLAG);
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        setFiredFlag(STATE_CONNECTING_FLAG);
                        break;
                    case BluetoothProfile.STATE_CONNECTED:
                        setFiredFlag(STATE_CONNECTED_FLAG);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        setFiredFlag(STATE_DISCONNECTING_FLAG);
                        break;
                }
            }
        }
    }

    private class ConnectPanReceiver extends ConnectProfileReceiver {
        private int mRole;

        ConnectPanReceiver(BluetoothDevice device, int role, int expectedFlags) {
            super(device, BluetoothProfile.PAN, expectedFlags);

            mRole = role;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mRole != intent.getIntExtra(BluetoothPan.EXTRA_LOCAL_ROLE, -1)) {
                return;
            }

            super.onReceive(context, intent);
        }
    }

    private class StartStopScoReceiver extends FlagReceiver {
        private static final int STATE_CONNECTED_FLAG = 1;
        private static final int STATE_DISCONNECTED_FLAG = 1 << 1;

        StartStopScoReceiver(int expectedFlags) {
            super(expectedFlags);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(intent.getAction())) {
                int state =
                        intent.getIntExtra(
                                AudioManager.EXTRA_SCO_AUDIO_STATE,
                                AudioManager.SCO_AUDIO_STATE_ERROR);
                assertNotSame(AudioManager.SCO_AUDIO_STATE_ERROR, state);
                switch (state) {
                    case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                        setFiredFlag(STATE_CONNECTED_FLAG);
                        break;
                    case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                        setFiredFlag(STATE_DISCONNECTED_FLAG);
                        break;
                }
            }
        }
    }

    private class MceSetMessageStatusReceiver extends FlagReceiver {
        private static final int MESSAGE_RECEIVED_FLAG = 1;
        private static final int STATUS_CHANGED_FLAG = 1 << 1;

        MceSetMessageStatusReceiver(int expectedFlags) {
            super(expectedFlags);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothMapClient.ACTION_MESSAGE_RECEIVED.equals(intent.getAction())) {
                String handle = intent.getStringExtra(BluetoothMapClient.EXTRA_MESSAGE_HANDLE);
                assertNotNull(handle);
                setFiredFlag(MESSAGE_RECEIVED_FLAG);
                mMsgHandle = handle;
            } else if (BluetoothMapClient.ACTION_MESSAGE_DELETED_STATUS_CHANGED.equals(
                    intent.getAction())) {
                int result =
                        intent.getIntExtra(
                                BluetoothMapClient.EXTRA_RESULT_CODE,
                                BluetoothMapClient.RESULT_FAILURE);
                assertEquals(result, BluetoothMapClient.RESULT_SUCCESS);
                setFiredFlag(STATUS_CHANGED_FLAG);
            } else if (BluetoothMapClient.ACTION_MESSAGE_READ_STATUS_CHANGED.equals(
                    intent.getAction())) {
                int result =
                        intent.getIntExtra(
                                BluetoothMapClient.EXTRA_RESULT_CODE,
                                BluetoothMapClient.RESULT_FAILURE);
                assertEquals(result, BluetoothMapClient.RESULT_SUCCESS);
                setFiredFlag(STATUS_CHANGED_FLAG);
            }
        }
    }

    private BluetoothProfile.ServiceListener mServiceListener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    synchronized (this) {
                        switch (profile) {
                            case BluetoothProfile.A2DP:
                                mA2dp = (BluetoothA2dp) proxy;
                                break;
                            case BluetoothProfile.HEADSET:
                                mHeadset = (BluetoothHeadset) proxy;
                                break;
                            case BluetoothProfile.HID_HOST:
                                mInput = (BluetoothHidHost) proxy;
                                break;
                            case BluetoothProfile.PAN:
                                mPan = (BluetoothPan) proxy;
                                break;
                            case BluetoothProfile.MAP_CLIENT:
                                mMce = (BluetoothMapClient) proxy;
                                break;
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    synchronized (this) {
                        switch (profile) {
                            case BluetoothProfile.A2DP:
                                mA2dp = null;
                                break;
                            case BluetoothProfile.HEADSET:
                                mHeadset = null;
                                break;
                            case BluetoothProfile.HID_HOST:
                                mInput = null;
                                break;
                            case BluetoothProfile.PAN:
                                mPan = null;
                                break;
                            case BluetoothProfile.MAP_CLIENT:
                                mMce = null;
                                break;
                        }
                    }
                }
            };

    private List<BroadcastReceiver> mReceivers = new ArrayList<BroadcastReceiver>();

    private BufferedWriter mOutputWriter;
    private String mTag;
    private String mOutputFile;

    private Context mContext;
    private BluetoothA2dp mA2dp = null;
    private BluetoothHeadset mHeadset = null;
    private BluetoothHidHost mInput = null;
    private BluetoothPan mPan = null;
    private BluetoothMapClient mMce = null;
    private String mMsgHandle = null;
    private TetheredInterfaceCallback mPanCallback = null;
    private TetheredInterfaceRequest mBluetoothIfaceRequest;

    /**
     * Creates a utility instance for testing Bluetooth.
     *
     * @param context The context of the application using the utility.
     * @param tag The log tag of the application using the utility.
     */
    public BluetoothTestUtils(Context context, String tag) {
        this(context, tag, null);
    }

    /**
     * Creates a utility instance for testing Bluetooth.
     *
     * @param context The context of the application using the utility.
     * @param tag The log tag of the application using the utility.
     * @param outputFile The path to an output file if the utility is to write results to a separate
     *     file.
     */
    public BluetoothTestUtils(Context context, String tag, String outputFile) {
        mContext = context;
        mTag = tag;
        mOutputFile = outputFile;

        if (mOutputFile == null) {
            mOutputWriter = null;
        } else {
            try {
                mOutputWriter =
                        new BufferedWriter(
                                new FileWriter(
                                        new File(
                                                Environment.getExternalStorageDirectory(),
                                                mOutputFile),
                                        true));
            } catch (IOException e) {
                Log.w(mTag, "Test output file could not be opened", e);
                mOutputWriter = null;
            }
        }
    }

    /** Closes the utility instance and unregisters any BroadcastReceivers. */
    public void close() {
        while (!mReceivers.isEmpty()) {
            mContext.unregisterReceiver(mReceivers.remove(0));
        }

        if (mOutputWriter != null) {
            try {
                mOutputWriter.close();
            } catch (IOException e) {
                Log.w(mTag, "Test output file could not be closed", e);
            }
        }
    }

    /**
     * Enables Bluetooth and checks to make sure that Bluetooth was turned on and that the correct
     * actions were broadcast.
     *
     * @param adapter The BT adapter.
     */
    public void enable(BluetoothAdapter adapter) {
        writeOutput("Enabling Bluetooth adapter.");
        assertFalse(adapter.isEnabled());
        int btState = adapter.getState();
        final Semaphore completionSemaphore = new Semaphore(0);
        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final String action = intent.getAction();
                        if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                            return;
                        }
                        final int state =
                                intent.getIntExtra(
                                        BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        if (state == BluetoothAdapter.STATE_ON) {
                            completionSemaphore.release();
                        }
                    }
                };

        final IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(receiver, filter);
        // Note: for Wear Local Edition builds, which have Permission Review Mode enabled to
        // obey China CMIIT, BluetoothAdapter may not startup immediately on methods enable/disable.
        // So no assertion applied here.
        adapter.enable();
        boolean success = false;
        try {
            success = completionSemaphore.tryAcquire(ENABLE_DISABLE_TIMEOUT, TimeUnit.MILLISECONDS);
            writeOutput(String.format("enable() completed in 0 ms"));
        } catch (final InterruptedException e) {
            // This should never happen but just in case it does, the test will fail anyway.
        }
        mContext.unregisterReceiver(receiver);
        if (!success) {
            fail(
                    String.format(
                            "enable() timeout: state=%d (expected %d)",
                            btState, BluetoothAdapter.STATE_ON));
        }
    }

    /**
     * Disables Bluetooth and checks to make sure that Bluetooth was turned off and that the correct
     * actions were broadcast.
     *
     * @param adapter The BT adapter.
     */
    public void disable(BluetoothAdapter adapter) {
        writeOutput("Disabling Bluetooth adapter.");
        assertTrue(adapter.isEnabled());
        int btState = adapter.getState();
        final Semaphore completionSemaphore = new Semaphore(0);
        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final String action = intent.getAction();
                        if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                            return;
                        }
                        final int state =
                                intent.getIntExtra(
                                        BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        if (state == BluetoothAdapter.STATE_OFF) {
                            completionSemaphore.release();
                        }
                    }
                };

        final IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(receiver, filter);
        // Note: for Wear Local Edition builds, which have Permission Review Mode enabled to
        // obey China CMIIT, BluetoothAdapter may not startup immediately on methods enable/disable.
        // So no assertion applied here.
        adapter.disable();
        boolean success = false;
        try {
            success = completionSemaphore.tryAcquire(ENABLE_DISABLE_TIMEOUT, TimeUnit.MILLISECONDS);
            writeOutput(String.format("disable() completed in 0 ms"));
        } catch (final InterruptedException e) {
            // This should never happen but just in case it does, the test will fail anyway.
        }
        mContext.unregisterReceiver(receiver);
        if (!success) {
            fail(
                    String.format(
                            "disable() timeout: state=%d (expected %d)",
                            btState, BluetoothAdapter.STATE_OFF));
        }
    }

    /**
     * Puts the local device into discoverable mode and checks to make sure that the local device is
     * in discoverable mode and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     */
    public void discoverable(BluetoothAdapter adapter) {
        if (!adapter.isEnabled()) {
            fail("discoverable() bluetooth not enabled");
        }

        int scanMode = adapter.getScanMode();
        if (scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE) {
            return;
        }

        final Semaphore completionSemaphore = new Semaphore(0);
        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final String action = intent.getAction();
                        if (!BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                            return;
                        }
                        final int mode =
                                intent.getIntExtra(
                                        BluetoothAdapter.EXTRA_SCAN_MODE,
                                        BluetoothAdapter.SCAN_MODE_NONE);
                        if (mode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                            completionSemaphore.release();
                        }
                    }
                };

        final IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(receiver, filter);
        assertEquals(
                adapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE),
                BluetoothStatusCodes.SUCCESS);
        boolean success = false;
        try {
            success =
                    completionSemaphore.tryAcquire(
                            DISCOVERABLE_UNDISCOVERABLE_TIMEOUT, TimeUnit.MILLISECONDS);
            writeOutput(String.format("discoverable() completed in 0 ms"));
        } catch (final InterruptedException e) {
            // This should never happen but just in case it does, the test will fail anyway.
        }
        mContext.unregisterReceiver(receiver);
        if (!success) {
            fail(
                    String.format(
                            "discoverable() timeout: scanMode=%d (expected %d)",
                            scanMode, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE));
        }
    }

    /**
     * Puts the local device into connectable only mode and checks to make sure that the local
     * device is in connectable mode and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     */
    public void undiscoverable(BluetoothAdapter adapter) {
        if (!adapter.isEnabled()) {
            fail("undiscoverable() bluetooth not enabled");
        }

        int scanMode = adapter.getScanMode();
        if (scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            return;
        }

        final Semaphore completionSemaphore = new Semaphore(0);
        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final String action = intent.getAction();
                        if (!BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                            return;
                        }
                        final int mode =
                                intent.getIntExtra(
                                        BluetoothAdapter.EXTRA_SCAN_MODE,
                                        BluetoothAdapter.SCAN_MODE_NONE);
                        if (mode == BluetoothAdapter.SCAN_MODE_CONNECTABLE) {
                            completionSemaphore.release();
                        }
                    }
                };

        final IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(receiver, filter);
        assertEquals(
                adapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE),
                BluetoothStatusCodes.SUCCESS);
        boolean success = false;
        try {
            success =
                    completionSemaphore.tryAcquire(
                            DISCOVERABLE_UNDISCOVERABLE_TIMEOUT, TimeUnit.MILLISECONDS);
            writeOutput(String.format("undiscoverable() completed in 0 ms"));
        } catch (InterruptedException e) {
            // This should never happen but just in case it does, the test will fail anyway.
        }
        mContext.unregisterReceiver(receiver);
        if (!success) {
            fail(
                    String.format(
                            "undiscoverable() timeout: scanMode=%d (expected %d)",
                            scanMode, BluetoothAdapter.SCAN_MODE_CONNECTABLE));
        }
    }

    /**
     * Starts a scan for remote devices and checks to make sure that the local device is scanning
     * and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     */
    public void startScan(BluetoothAdapter adapter) {
        int mask = BluetoothReceiver.DISCOVERY_STARTED_FLAG;

        if (!adapter.isEnabled()) {
            fail("startScan() bluetooth not enabled");
        }

        if (adapter.isDiscovering()) {
            return;
        }

        BluetoothReceiver receiver = getBluetoothReceiver(mask);

        long start = System.currentTimeMillis();
        assertTrue(adapter.startDiscovery());

        while (System.currentTimeMillis() - start < START_STOP_SCAN_TIMEOUT) {
            if (adapter.isDiscovering() && ((receiver.getFiredFlags() & mask) == mask)) {
                writeOutput(
                        String.format(
                                "startScan() completed in %d ms",
                                (receiver.getCompletedTime() - start)));
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "startScan() timeout: isDiscovering=%b, flags=0x%x (expected 0x%x)",
                        adapter.isDiscovering(), firedFlags, mask));
    }

    /**
     * Stops a scan for remote devices and checks to make sure that the local device is not scanning
     * and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     */
    public void stopScan(BluetoothAdapter adapter) {
        int mask = BluetoothReceiver.DISCOVERY_FINISHED_FLAG;

        if (!adapter.isEnabled()) {
            fail("stopScan() bluetooth not enabled");
        }

        if (!adapter.isDiscovering()) {
            return;
        }

        BluetoothReceiver receiver = getBluetoothReceiver(mask);

        long start = System.currentTimeMillis();
        assertTrue(adapter.cancelDiscovery());

        while (System.currentTimeMillis() - start < START_STOP_SCAN_TIMEOUT) {
            if (!adapter.isDiscovering() && ((receiver.getFiredFlags() & mask) == mask)) {
                writeOutput(
                        String.format(
                                "stopScan() completed in %d ms",
                                (receiver.getCompletedTime() - start)));
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "stopScan() timeout: isDiscovering=%b, flags=0x%x (expected 0x%x)",
                        adapter.isDiscovering(), firedFlags, mask));
    }

    /**
     * Enables PAN tethering on the local device and checks to make sure that tethering is enabled.
     *
     * @param adapter The BT adapter.
     */
    public void enablePan(BluetoothAdapter adapter) {
        if (mPan == null) mPan = (BluetoothPan) connectProxy(adapter, BluetoothProfile.PAN);
        assertNotNull(mPan);

        long start = System.currentTimeMillis();
        mPanCallback =
                new TetheringManager.TetheredInterfaceCallback() {
                    @Override
                    public void onAvailable(String iface) {}

                    @Override
                    public void onUnavailable() {}
                };
        mBluetoothIfaceRequest =
                mPan.requestTetheredInterface(mContext.getMainExecutor(), mPanCallback);
        long stop = System.currentTimeMillis();
        assertTrue(mPan.isTetheringOn());

        writeOutput(String.format("enablePan() completed in %d ms", (stop - start)));
    }

    /**
     * Disables PAN tethering on the local device and checks to make sure that tethering is
     * disabled.
     *
     * @param adapter The BT adapter.
     */
    public void disablePan(BluetoothAdapter adapter) {
        if (mPan == null) mPan = (BluetoothPan) connectProxy(adapter, BluetoothProfile.PAN);
        assertNotNull(mPan);

        long start = System.currentTimeMillis();
        if (mBluetoothIfaceRequest != null) {
            mBluetoothIfaceRequest.release();
            mBluetoothIfaceRequest = null;
        }
        long stop = System.currentTimeMillis();
        assertFalse(mPan.isTetheringOn());

        writeOutput(String.format("disablePan() completed in %d ms", (stop - start)));
    }

    /**
     * Initiates a pairing with a remote device and checks to make sure that the devices are paired
     * and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param passkey The pairing passkey if pairing requires a passkey. Any value if not.
     * @param pin The pairing pin if pairing requires a pin. Any value if not.
     */
    public void pair(BluetoothAdapter adapter, BluetoothDevice device, int passkey, byte[] pin) {
        pairOrAcceptPair(adapter, device, passkey, pin, true);
    }

    /**
     * Accepts a pairing with a remote device and checks to make sure that the devices are paired
     * and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param passkey The pairing passkey if pairing requires a passkey. Any value if not.
     * @param pin The pairing pin if pairing requires a pin. Any value if not.
     */
    public void acceptPair(
            BluetoothAdapter adapter, BluetoothDevice device, int passkey, byte[] pin) {
        pairOrAcceptPair(adapter, device, passkey, pin, false);
    }

    /**
     * Helper method used by {@link #pair(BluetoothAdapter, BluetoothDevice, int, byte[])} and
     * {@link #acceptPair(BluetoothAdapter, BluetoothDevice, int, byte[])} to either pair or accept
     * a pairing request.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param passkey The pairing passkey if pairing requires a passkey. Any value if not.
     * @param pin The pairing pin if pairing requires a pin. Any value if not.
     * @param shouldPair Whether to pair or accept the pair.
     */
    private void pairOrAcceptPair(
            BluetoothAdapter adapter,
            BluetoothDevice device,
            int passkey,
            byte[] pin,
            boolean shouldPair) {
        int mask = PairReceiver.STATE_BONDING_FLAG | PairReceiver.STATE_BONDED_FLAG;
        long start = -1;
        String methodName;
        if (shouldPair) {
            methodName = String.format("pair(device=%s)", device);
        } else {
            methodName = String.format("acceptPair(device=%s)", device);
        }

        if (!adapter.isEnabled()) {
            fail(String.format("%s bluetooth not enabled", methodName));
        }

        PairReceiver receiver = getPairReceiver(device, passkey, pin, mask);

        int state = device.getBondState();
        switch (state) {
            case BluetoothDevice.BOND_NONE:
                assertFalse(adapter.getBondedDevices().contains(device));
                start = System.currentTimeMillis();
                if (shouldPair) {
                    assertTrue(device.createBond());
                }
                break;
            case BluetoothDevice.BOND_BONDING:
                mask = 0; // Don't check for received intents since we might have missed them.
                break;
            case BluetoothDevice.BOND_BONDED:
                assertTrue(adapter.getBondedDevices().contains(device));
                return;
            default:
                removeReceiver(receiver);
                fail(String.format("%s invalid state: state=%d", methodName, state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < PAIR_UNPAIR_TIMEOUT) {
            state = device.getBondState();
            if (state == BluetoothDevice.BOND_BONDED && (receiver.getFiredFlags() & mask) == mask) {
                assertTrue(adapter.getBondedDevices().contains(device));
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(
                            String.format("%s completed in %d ms", methodName, (finish - start)));
                } else {
                    writeOutput(String.format("%s completed", methodName));
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%x)",
                        methodName, state, BluetoothDevice.BOND_BONDED, firedFlags, mask));
    }

    /**
     * Deletes a pairing with a remote device and checks to make sure that the devices are unpaired
     * and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     */
    public void unpair(BluetoothAdapter adapter, BluetoothDevice device) {
        int mask = PairReceiver.STATE_NONE_FLAG;
        long start = -1;
        String methodName = String.format("unpair(device=%s)", device);

        if (!adapter.isEnabled()) {
            fail(String.format("%s bluetooth not enabled", methodName));
        }

        PairReceiver receiver = getPairReceiver(device, 0, null, mask);

        int state = device.getBondState();
        switch (state) {
            case BluetoothDevice.BOND_NONE:
                assertFalse(adapter.getBondedDevices().contains(device));
                removeReceiver(receiver);
                return;
            case BluetoothDevice.BOND_BONDING:
                start = System.currentTimeMillis();
                assertTrue(device.removeBond());
                break;
            case BluetoothDevice.BOND_BONDED:
                assertTrue(adapter.getBondedDevices().contains(device));
                start = System.currentTimeMillis();
                assertTrue(device.removeBond());
                break;
            default:
                removeReceiver(receiver);
                fail(String.format("%s invalid state: state=%d", methodName, state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < PAIR_UNPAIR_TIMEOUT) {
            if (device.getBondState() == BluetoothDevice.BOND_NONE
                    && (receiver.getFiredFlags() & mask) == mask) {
                assertFalse(adapter.getBondedDevices().contains(device));
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(
                            String.format("%s completed in %d ms", methodName, (finish - start)));
                } else {
                    writeOutput(String.format("%s completed", methodName));
                }
                removeReceiver(receiver);
                return;
            }
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%x)",
                        methodName, state, BluetoothDevice.BOND_BONDED, firedFlags, mask));
    }

    /**
     * Deletes all pairings of remote devices
     *
     * @param adapter the BT adapter
     */
    public void unpairAll(BluetoothAdapter adapter) {
        Set<BluetoothDevice> devices = adapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            unpair(adapter, device);
        }
    }

    /**
     * Connects a profile from the local device to a remote device and checks to make sure that the
     * profile is connected and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param profile The profile to connect. One of {@link BluetoothProfile#A2DP}, {@link
     *     BluetoothProfile#HEADSET}, {@link BluetoothProfile#HID_HOST} or {@link
     *     BluetoothProfile#MAP_CLIENT}..
     * @param methodName The method name to printed in the logs. If null, will be
     *     "connectProfile(profile=&lt;profile&gt;, device=&lt;device&gt;)"
     */
    public void connectProfile(
            BluetoothAdapter adapter, BluetoothDevice device, int profile, String methodName) {
        if (methodName == null) {
            methodName = String.format("connectProfile(profile=%d, device=%s)", profile, device);
        }
        int mask =
                (ConnectProfileReceiver.STATE_CONNECTING_FLAG
                        | ConnectProfileReceiver.STATE_CONNECTED_FLAG);
        long start = -1;

        if (!adapter.isEnabled()) {
            fail(String.format("%s bluetooth not enabled", methodName));
        }

        if (!adapter.getBondedDevices().contains(device)) {
            fail(String.format("%s device not paired", methodName));
        }

        BluetoothProfile proxy = connectProxy(adapter, profile);
        assertNotNull(proxy);

        ConnectProfileReceiver receiver = getConnectProfileReceiver(device, profile, mask);

        int state = proxy.getConnectionState(device);
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                removeReceiver(receiver);
                return;
            case BluetoothProfile.STATE_CONNECTING:
                mask = 0; // Don't check for received intents since we might have missed them.
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
            case BluetoothProfile.STATE_DISCONNECTING:
                start = System.currentTimeMillis();
                if (profile == BluetoothProfile.A2DP) {
                    assertTrue(((BluetoothA2dp) proxy).connect(device));
                } else if (profile == BluetoothProfile.HEADSET) {
                    assertTrue(((BluetoothHeadset) proxy).connect(device));
                } else if (profile == BluetoothProfile.HID_HOST) {
                    assertTrue(((BluetoothHidHost) proxy).connect(device));
                } else if (profile == BluetoothProfile.MAP_CLIENT) {
                    assertTrue(((BluetoothMapClient) proxy).connect(device));
                }
                break;
            default:
                removeReceiver(receiver);
                fail(String.format("%s invalid state: state=%d", methodName, state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < CONNECT_DISCONNECT_PROFILE_TIMEOUT) {
            state = proxy.getConnectionState(device);
            if (state == BluetoothProfile.STATE_CONNECTED
                    && (receiver.getFiredFlags() & mask) == mask) {
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(
                            String.format("%s completed in %d ms", methodName, (finish - start)));
                } else {
                    writeOutput(String.format("%s completed", methodName));
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%x)",
                        methodName, state, BluetoothProfile.STATE_CONNECTED, firedFlags, mask));
    }

    /**
     * Disconnects a profile between the local device and a remote device and checks to make sure
     * that the profile is disconnected and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param profile The profile to disconnect. One of {@link BluetoothProfile#A2DP}, {@link
     *     BluetoothProfile#HEADSET}, or {@link BluetoothProfile#HID_HOST}.
     * @param methodName The method name to printed in the logs. If null, will be
     *     "connectProfile(profile=&lt;profile&gt;, device=&lt;device&gt;)"
     */
    public void disconnectProfile(
            BluetoothAdapter adapter, BluetoothDevice device, int profile, String methodName) {
        if (methodName == null) {
            methodName = String.format("disconnectProfile(profile=%d, device=%s)", profile, device);
        }
        int mask =
                (ConnectProfileReceiver.STATE_DISCONNECTING_FLAG
                        | ConnectProfileReceiver.STATE_DISCONNECTED_FLAG);
        long start = -1;

        if (!adapter.isEnabled()) {
            fail(String.format("%s bluetooth not enabled", methodName));
        }

        if (!adapter.getBondedDevices().contains(device)) {
            fail(String.format("%s device not paired", methodName));
        }

        BluetoothProfile proxy = connectProxy(adapter, profile);
        assertNotNull(proxy);

        ConnectProfileReceiver receiver = getConnectProfileReceiver(device, profile, mask);

        int state = proxy.getConnectionState(device);
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
            case BluetoothProfile.STATE_CONNECTING:
                start = System.currentTimeMillis();
                if (profile == BluetoothProfile.A2DP) {
                    assertTrue(((BluetoothA2dp) proxy).disconnect(device));
                } else if (profile == BluetoothProfile.HEADSET) {
                    assertTrue(((BluetoothHeadset) proxy).disconnect(device));
                } else if (profile == BluetoothProfile.HID_HOST) {
                    assertTrue(((BluetoothHidHost) proxy).disconnect(device));
                } else if (profile == BluetoothProfile.MAP_CLIENT) {
                    assertTrue(((BluetoothMapClient) proxy).disconnect(device));
                }
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                removeReceiver(receiver);
                return;
            case BluetoothProfile.STATE_DISCONNECTING:
                mask = 0; // Don't check for received intents since we might have missed them.
                break;
            default:
                removeReceiver(receiver);
                fail(String.format("%s invalid state: state=%d", methodName, state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < CONNECT_DISCONNECT_PROFILE_TIMEOUT) {
            state = proxy.getConnectionState(device);
            if (state == BluetoothProfile.STATE_DISCONNECTED
                    && (receiver.getFiredFlags() & mask) == mask) {
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(
                            String.format("%s completed in %d ms", methodName, (finish - start)));
                } else {
                    writeOutput(String.format("%s completed", methodName));
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%x)",
                        methodName, state, BluetoothProfile.STATE_DISCONNECTED, firedFlags, mask));
    }

    /**
     * Connects the PANU to a remote NAP and checks to make sure that the PANU is connected and that
     * the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     */
    public void connectPan(BluetoothAdapter adapter, BluetoothDevice device) {
        connectPanOrIncomingPanConnection(adapter, device, true);
    }

    /**
     * Checks that a remote PANU connects to the local NAP correctly and that the correct actions
     * were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     */
    public void incomingPanConnection(BluetoothAdapter adapter, BluetoothDevice device) {
        connectPanOrIncomingPanConnection(adapter, device, false);
    }

    /**
     * Helper method used by {@link #connectPan(BluetoothAdapter, BluetoothDevice)} and {@link
     * #incomingPanConnection(BluetoothAdapter, BluetoothDevice)} to either connect to a remote NAP
     * or verify that a remote device connected to the local NAP.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param connect If the method should initiate the connection (is PANU)
     */
    private void connectPanOrIncomingPanConnection(
            BluetoothAdapter adapter, BluetoothDevice device, boolean connect) {
        long start = -1;
        int mask, role;
        String methodName;

        if (connect) {
            methodName = String.format("connectPan(device=%s)", device);
            mask =
                    (ConnectProfileReceiver.STATE_CONNECTED_FLAG
                            | ConnectProfileReceiver.STATE_CONNECTING_FLAG);
            role = BluetoothPan.LOCAL_PANU_ROLE;
        } else {
            methodName = String.format("incomingPanConnection(device=%s)", device);
            mask = ConnectProfileReceiver.STATE_CONNECTED_FLAG;
            role = BluetoothPan.LOCAL_NAP_ROLE;
        }

        if (!adapter.isEnabled()) {
            fail(String.format("%s bluetooth not enabled", methodName));
        }

        if (!adapter.getBondedDevices().contains(device)) {
            fail(String.format("%s device not paired", methodName));
        }

        mPan = (BluetoothPan) connectProxy(adapter, BluetoothProfile.PAN);
        assertNotNull(mPan);
        ConnectPanReceiver receiver = getConnectPanReceiver(device, role, mask);

        int state = mPan.getConnectionState(device);
        switch (state) {
            case BluetoothPan.STATE_CONNECTED:
                removeReceiver(receiver);
                return;
            case BluetoothPan.STATE_CONNECTING:
                mask = 0; // Don't check for received intents since we might have missed them.
                break;
            case BluetoothPan.STATE_DISCONNECTED:
            case BluetoothPan.STATE_DISCONNECTING:
                start = System.currentTimeMillis();
                if (role == BluetoothPan.LOCAL_PANU_ROLE) {
                    Log.i("BT", "connect to pan");
                    assertTrue(mPan.connect(device));
                }
                break;
            default:
                removeReceiver(receiver);
                fail(String.format("%s invalid state: state=%d", methodName, state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < CONNECT_DISCONNECT_PROFILE_TIMEOUT) {
            state = mPan.getConnectionState(device);
            if (state == BluetoothPan.STATE_CONNECTED
                    && (receiver.getFiredFlags() & mask) == mask) {
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(
                            String.format("%s completed in %d ms", methodName, (finish - start)));
                } else {
                    writeOutput(String.format("%s completed", methodName));
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%s)",
                        methodName, state, BluetoothPan.STATE_CONNECTED, firedFlags, mask));
    }

    /**
     * Disconnects the PANU from a remote NAP and checks to make sure that the PANU is disconnected
     * and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     */
    public void disconnectPan(BluetoothAdapter adapter, BluetoothDevice device) {
        disconnectFromRemoteOrVerifyConnectNap(adapter, device, true);
    }

    /**
     * Checks that a remote PANU disconnects from the local NAP correctly and that the correct
     * actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     */
    public void incomingPanDisconnection(BluetoothAdapter adapter, BluetoothDevice device) {
        disconnectFromRemoteOrVerifyConnectNap(adapter, device, false);
    }

    /**
     * Helper method used by {@link #disconnectPan(BluetoothAdapter, BluetoothDevice)} and {@link
     * #incomingPanDisconnection(BluetoothAdapter, BluetoothDevice)} to either disconnect from a
     * remote NAP or verify that a remote device disconnected from the local NAP.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param disconnect Whether the method should connect or verify.
     */
    private void disconnectFromRemoteOrVerifyConnectNap(
            BluetoothAdapter adapter, BluetoothDevice device, boolean disconnect) {
        long start = -1;
        int mask, role;
        String methodName;

        if (disconnect) {
            methodName = String.format("disconnectPan(device=%s)", device);
            mask =
                    (ConnectProfileReceiver.STATE_DISCONNECTED_FLAG
                            | ConnectProfileReceiver.STATE_DISCONNECTING_FLAG);
            role = BluetoothPan.LOCAL_PANU_ROLE;
        } else {
            methodName = String.format("incomingPanDisconnection(device=%s)", device);
            mask = ConnectProfileReceiver.STATE_DISCONNECTED_FLAG;
            role = BluetoothPan.LOCAL_NAP_ROLE;
        }

        if (!adapter.isEnabled()) {
            fail(String.format("%s bluetooth not enabled", methodName));
        }

        if (!adapter.getBondedDevices().contains(device)) {
            fail(String.format("%s device not paired", methodName));
        }

        mPan = (BluetoothPan) connectProxy(adapter, BluetoothProfile.PAN);
        assertNotNull(mPan);
        ConnectPanReceiver receiver = getConnectPanReceiver(device, role, mask);

        int state = mPan.getConnectionState(device);
        switch (state) {
            case BluetoothPan.STATE_CONNECTED:
            case BluetoothPan.STATE_CONNECTING:
                start = System.currentTimeMillis();
                if (role == BluetoothPan.LOCAL_PANU_ROLE) {
                    assertTrue(mPan.disconnect(device));
                }
                break;
            case BluetoothPan.STATE_DISCONNECTED:
                removeReceiver(receiver);
                return;
            case BluetoothPan.STATE_DISCONNECTING:
                mask = 0; // Don't check for received intents since we might have missed them.
                break;
            default:
                removeReceiver(receiver);
                fail(String.format("%s invalid state: state=%d", methodName, state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < CONNECT_DISCONNECT_PROFILE_TIMEOUT) {
            state = mPan.getConnectionState(device);
            if (state == BluetoothHidHost.STATE_DISCONNECTED
                    && (receiver.getFiredFlags() & mask) == mask) {
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(
                            String.format("%s completed in %d ms", methodName, (finish - start)));
                } else {
                    writeOutput(String.format("%s completed", methodName));
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%s)",
                        methodName, state, BluetoothHidHost.STATE_DISCONNECTED, firedFlags, mask));
    }

    /**
     * Opens a SCO channel using {@link android.media.AudioManager#startBluetoothSco()} and checks
     * to make sure that the channel is opened and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     */
    public void startSco(BluetoothAdapter adapter, BluetoothDevice device) {
        startStopSco(adapter, device, true);
    }

    /**
     * Closes a SCO channel using {@link android.media.AudioManager#stopBluetoothSco()} and checks
     * to make sure that the channel is closed and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     */
    public void stopSco(BluetoothAdapter adapter, BluetoothDevice device) {
        startStopSco(adapter, device, false);
    }

    /**
     * Helper method for {@link #startSco(BluetoothAdapter, BluetoothDevice)} and {@link
     * #stopSco(BluetoothAdapter, BluetoothDevice)}.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param isStart Whether the SCO channel should be opened.
     */
    private void startStopSco(BluetoothAdapter adapter, BluetoothDevice device, boolean isStart) {
        long start = -1;
        int mask;
        String methodName;

        if (isStart) {
            methodName = String.format("startSco(device=%s)", device);
            mask = StartStopScoReceiver.STATE_CONNECTED_FLAG;
        } else {
            methodName = String.format("stopSco(device=%s)", device);
            mask = StartStopScoReceiver.STATE_DISCONNECTED_FLAG;
        }

        if (!adapter.isEnabled()) {
            fail(String.format("%s bluetooth not enabled", methodName));
        }

        if (!adapter.getBondedDevices().contains(device)) {
            fail(String.format("%s device not paired", methodName));
        }

        AudioManager manager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        assertNotNull(manager);

        if (!manager.isBluetoothScoAvailableOffCall()) {
            fail(String.format("%s device does not support SCO", methodName));
        }

        boolean isScoOn = manager.isBluetoothScoOn();
        if (isStart == isScoOn) {
            return;
        }

        StartStopScoReceiver receiver = getStartStopScoReceiver(mask);
        start = System.currentTimeMillis();
        if (isStart) {
            manager.startBluetoothSco();
        } else {
            manager.stopBluetoothSco();
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < START_STOP_SCO_TIMEOUT) {
            isScoOn = manager.isBluetoothScoOn();
            if (isStart == isScoOn && (receiver.getFiredFlags() & mask) == mask) {
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(
                            String.format("%s completed in %d ms", methodName, (finish - start)));
                } else {
                    writeOutput(String.format("%s completed", methodName));
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "%s timeout: on=%b (expected %b), flags=0x%x (expected 0x%x)",
                        methodName, isScoOn, isStart, firedFlags, mask));
    }

    /**
     * Writes a string to the logcat and a file if a file has been specified in the constructor.
     *
     * @param s The string to be written.
     */
    public void writeOutput(String s) {
        Log.i(mTag, s);
        if (mOutputWriter == null) {
            return;
        }
        try {
            mOutputWriter.write(s + "\n");
            mOutputWriter.flush();
        } catch (IOException e) {
            Log.w(mTag, "Could not write to output file", e);
        }
    }

    public void mceGetUnreadMessage(BluetoothAdapter adapter, BluetoothDevice device) {
        int mask;
        String methodName = "getUnreadMessage";

        if (!adapter.isEnabled()) {
            fail(String.format("%s bluetooth not enabled", methodName));
        }

        if (!adapter.getBondedDevices().contains(device)) {
            fail(String.format("%s device not paired", methodName));
        }

        mMce = (BluetoothMapClient) connectProxy(adapter, BluetoothProfile.MAP_CLIENT);
        assertNotNull(mMce);

        if (mMce.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
            fail(String.format("%s device is not connected", methodName));
        }

        mMsgHandle = null;
        mask = MceSetMessageStatusReceiver.MESSAGE_RECEIVED_FLAG;
        MceSetMessageStatusReceiver receiver = getMceSetMessageStatusReceiver(device, mask);
        assertTrue(mMce.getUnreadMessages(device));

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < GET_UNREAD_MESSAGE_TIMEOUT) {
            if ((receiver.getFiredFlags() & mask) == mask) {
                writeOutput(String.format("%s completed", methodName));
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }
        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%s)",
                        methodName,
                        mMce.getConnectionState(device),
                        BluetoothMapClient.STATE_CONNECTED,
                        firedFlags,
                        mask));
    }

    /** Set a message to read/unread/deleted/undeleted */
    public void mceSetMessageStatus(BluetoothAdapter adapter, BluetoothDevice device, int status) {
        int mask;
        String methodName = "setMessageStatus";

        if (!adapter.isEnabled()) {
            fail(String.format("%s bluetooth not enabled", methodName));
        }

        if (!adapter.getBondedDevices().contains(device)) {
            fail(String.format("%s device not paired", methodName));
        }

        mMce = (BluetoothMapClient) connectProxy(adapter, BluetoothProfile.MAP_CLIENT);
        assertNotNull(mMce);

        if (mMce.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
            fail(String.format("%s device is not connected", methodName));
        }

        assertNotNull(mMsgHandle);
        mask = MceSetMessageStatusReceiver.STATUS_CHANGED_FLAG;
        MceSetMessageStatusReceiver receiver = getMceSetMessageStatusReceiver(device, mask);

        assertTrue(mMce.setMessageStatus(device, mMsgHandle, status));

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < SET_MESSAGE_STATUS_TIMEOUT) {
            if ((receiver.getFiredFlags() & mask) == mask) {
                writeOutput(String.format("%s completed", methodName));
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        fail(
                String.format(
                        "%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%s)",
                        methodName,
                        mMce.getConnectionState(device),
                        BluetoothPan.STATE_CONNECTED,
                        firedFlags,
                        mask));
    }

    private void addReceiver(BroadcastReceiver receiver, String[] actions) {
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        for (String action : actions) {
            filter.addAction(action);
        }
        mContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        mReceivers.add(receiver);
    }

    private BluetoothReceiver getBluetoothReceiver(int expectedFlags) {
        String[] actions = {
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
            BluetoothAdapter.ACTION_DISCOVERY_STARTED,
            BluetoothAdapter.ACTION_SCAN_MODE_CHANGED,
            BluetoothAdapter.ACTION_STATE_CHANGED
        };
        BluetoothReceiver receiver = new BluetoothReceiver(expectedFlags);
        addReceiver(receiver, actions);
        return receiver;
    }

    private PairReceiver getPairReceiver(
            BluetoothDevice device, int passkey, byte[] pin, int expectedFlags) {
        String[] actions = {
            BluetoothDevice.ACTION_PAIRING_REQUEST, BluetoothDevice.ACTION_BOND_STATE_CHANGED
        };
        PairReceiver receiver = new PairReceiver(device, passkey, pin, expectedFlags);
        addReceiver(receiver, actions);
        return receiver;
    }

    private ConnectProfileReceiver getConnectProfileReceiver(
            BluetoothDevice device, int profile, int expectedFlags) {
        String[] actions = {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED
        };
        ConnectProfileReceiver receiver =
                new ConnectProfileReceiver(device, profile, expectedFlags);
        addReceiver(receiver, actions);
        return receiver;
    }

    private ConnectPanReceiver getConnectPanReceiver(
            BluetoothDevice device, int role, int expectedFlags) {
        String[] actions = {BluetoothPan.ACTION_CONNECTION_STATE_CHANGED};
        ConnectPanReceiver receiver = new ConnectPanReceiver(device, role, expectedFlags);
        addReceiver(receiver, actions);
        return receiver;
    }

    private StartStopScoReceiver getStartStopScoReceiver(int expectedFlags) {
        String[] actions = {AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED};
        StartStopScoReceiver receiver = new StartStopScoReceiver(expectedFlags);
        addReceiver(receiver, actions);
        return receiver;
    }

    private MceSetMessageStatusReceiver getMceSetMessageStatusReceiver(
            BluetoothDevice device, int expectedFlags) {
        String[] actions = {
            BluetoothMapClient.ACTION_MESSAGE_RECEIVED,
            BluetoothMapClient.ACTION_MESSAGE_READ_STATUS_CHANGED,
            BluetoothMapClient.ACTION_MESSAGE_DELETED_STATUS_CHANGED
        };
        MceSetMessageStatusReceiver receiver = new MceSetMessageStatusReceiver(expectedFlags);
        addReceiver(receiver, actions);
        return receiver;
    }

    private void removeReceiver(BroadcastReceiver receiver) {
        mContext.unregisterReceiver(receiver);
        mReceivers.remove(receiver);
    }

    private BluetoothProfile connectProxy(BluetoothAdapter adapter, int profile) {
        switch (profile) {
            case BluetoothProfile.A2DP:
                if (mA2dp != null) {
                    return mA2dp;
                }
                break;
            case BluetoothProfile.HEADSET:
                if (mHeadset != null) {
                    return mHeadset;
                }
                break;
            case BluetoothProfile.HID_HOST:
                if (mInput != null) {
                    return mInput;
                }
                break;
            case BluetoothProfile.PAN:
                if (mPan != null) {
                    return mPan;
                }
                break;
            case BluetoothProfile.MAP_CLIENT:
                if (mMce != null) {
                    return mMce;
                }
                break;
            default:
                return null;
        }
        adapter.getProfileProxy(mContext, mServiceListener, profile);
        long s = System.currentTimeMillis();
        switch (profile) {
            case BluetoothProfile.A2DP:
                while (mA2dp == null && System.currentTimeMillis() - s < CONNECT_PROXY_TIMEOUT) {
                    sleep(POLL_TIME);
                }
                return mA2dp;
            case BluetoothProfile.HEADSET:
                while (mHeadset == null && System.currentTimeMillis() - s < CONNECT_PROXY_TIMEOUT) {
                    sleep(POLL_TIME);
                }
                return mHeadset;
            case BluetoothProfile.HID_HOST:
                while (mInput == null && System.currentTimeMillis() - s < CONNECT_PROXY_TIMEOUT) {
                    sleep(POLL_TIME);
                }
                return mInput;
            case BluetoothProfile.PAN:
                while (mPan == null && System.currentTimeMillis() - s < CONNECT_PROXY_TIMEOUT) {
                    sleep(POLL_TIME);
                }
                return mPan;
            case BluetoothProfile.MAP_CLIENT:
                while (mMce == null && System.currentTimeMillis() - s < CONNECT_PROXY_TIMEOUT) {
                    sleep(POLL_TIME);
                }
                return mMce;
            default:
                return null;
        }
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
        }
    }
}
