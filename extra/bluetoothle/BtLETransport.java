package com.medm.bluetoothle;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.work.WorkRequest;
import com.google.android.gms.location.DeviceOrientationRequest;
import com.medm.bluetooth.BtMan;
import com.medm.logger.Logger;
import com.medm.logger.Utils;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/* loaded from: classes3.dex */
public class BtLETransport {
    private static final int BLE_CLOSE_AFTER_DISCONNECT_TIMEOUT = 10000;
    private static final int BLE_CONNECT_TIMEOUT = 25000;
    private static final int BLE_DISCONNECT_TIMEOUT = 25000;
    private static final int BLE_GATT_OPERATION_DISCOVER_SERVICES_TIMEOUT = 30000;
    private static final int BLE_GATT_OPERATION_TIMEOUT = 7000;
    private static final int BLE_IDLE_TIMEOUT = 600000;
    private static final long BLE_READ_PHY_TIMEOUT = 15000;
    private static final long BLE_REQUEST_MTU_TIMEOUT = 15000;
    private static final long BLE_RESET_GATT_TIMEOUT = 15000;
    private static final int BLE_UNPAIRING_TIMEOUT = 5000;
    public static final int GATT_AUTH_FAIL = 137;
    public static final int GATT_CONN_LMP_TIMEOUT = 34;
    public static final int GATT_ERROR = 133;
    public static final int GATT_INSUF_AUTHORIZATION = 8;
    public static final int GATT_INTERNAL_ERROR = 129;
    public static final int GATT_NO_RESOURCES = 128;
    private static final long N_WAIT_FOR_BOND_TIMEOUT = 15000;
    private Handler mDiscoverHandler;
    private volatile ExecutorService m_BLECallbackExecutor;
    private volatile Context m_Context;
    private volatile BluetoothAdapter m_bluetoothAdapter;
    private static final UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(5);
    private volatile long m_nListenerPtr = 0;
    private volatile BluetoothGatt m_btGatt = null;
    private volatile BLEOperationsQueue m_bleOperationsQueue = null;
    private volatile BluetoothDevice m_device = null;
    private volatile BLEGattCallback m_callback = null;
    private volatile ArrayList<BluetoothGattCharacteristic> m_AskCharacterArray = null;
    private volatile ArrayList<BluetoothGattCharacteristic> m_NotifyCharacterArray = null;
    private volatile ScheduledFuture m_timeoutTaskFuture = null;
    private final Object m_timeoutTaskLock = new Object();
    private volatile boolean m_bOnDisconnectedFired = false;
    private volatile boolean m_bDeviceWasConnected = false;
    private volatile BtErrorMonitor m_btErrorMonitor = null;
    private volatile PairingBroadcastReceiver m_pairingReceiver = null;
    private boolean m_bWaitBonding = false;
    private boolean m_bPreferWriteWithoutResponse = false;
    private ArrayList<UUID> m_characteristicsReadBlackList = null;
    private ArrayList<UUID> m_characteristicsNotificationBlackList = null;
    private boolean m_bDelayServicesDiscovery = false;
    private boolean m_bDelayBleOperations = false;
    private volatile Runnable m_delayServicesDiscoveryRunnable = null;
    private boolean m_bOnServicesDiscoveredEventPosted = false;
    private volatile boolean m_bIsConnected = false;
    private volatile boolean m_bExplicitDisconnecting = false;
    private boolean m_bRequestBigMTU = false;
    private volatile ScheduledFuture m_disconnectTimeoutTaskFuture = null;
    private volatile boolean m_bNoConnectTimeout = false;
    private volatile boolean m_bNoOpTimeout = false;
    private final Object m_lock = new Object();
    private final Object m_listenerLock = new Object();
    private volatile int m_nConnectionUpdateStatus = 0;

    public static native void NotifyCharacteristic(long j, String str, String str2);

    public static native void NotifyService(long j, String str);

    public static native boolean isUsefulServiceUUID(String str);

    public static native void onConnected(long j);

    public static native void onDescriptorValue(long j, String str, byte[] bArr, int i);

    public static native void onDisconnected(long j, int i, String str);

    public static native void onKeyValuePair(long j, String str, String str2, byte[] bArr, int i);

    public static native void onPair(long j, int i);

    private static native void onUnpair(long j);

    public static BtLETransport create(Context context) {
        if (BtLEManager.isBLEAvailable()) {
            return new BtLETransport(context);
        }
        return null;
    }

    private BtLETransport(Context context) {
        this.m_bluetoothAdapter = null;
        this.m_Context = null;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService("bluetooth");
        if (bluetoothManager != null) {
            this.m_bluetoothAdapter = bluetoothManager.getAdapter();
            if (this.m_bluetoothAdapter == null) {
                Logger.str("No BluetoothAdapter");
            } else if (Build.VERSION.SDK_INT >= 26) {
                Logger.str("BluetoothAdapter features: isLe2MPhySupported = " + this.m_bluetoothAdapter.isLe2MPhySupported() + ", isLeCodedPhySupported = " + this.m_bluetoothAdapter.isLeCodedPhySupported());
            }
        } else {
            Logger.str("No BluetoothManager");
        }
        this.m_Context = context;
        this.mDiscoverHandler = new Handler(Looper.getMainLooper());
    }

    public void setListener(long j) {
        synchronized (this.m_listenerLock) {
            this.m_nListenerPtr = j;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void scheduleTimeout(final long j) {
        cancelTimeoutTask();
        this.m_timeoutTaskFuture = executor.schedule(new Runnable() { // from class: com.medm.bluetoothle.BtLETransport.1
            @Override // java.lang.Runnable
            public void run() {
                boolean z;
                synchronized (BtLETransport.this.m_timeoutTaskLock) {
                    if (BtLETransport.this.m_timeoutTaskFuture != null) {
                        BtLETransport.this.m_timeoutTaskFuture = null;
                        String address = BtLETransport.this.m_device != null ? BtLETransport.this.m_device.getAddress() : "unknown";
                        Logger.str("scheduleTimeout: Disconnect device " + (BtLETransport.this.m_device != null ? BtLETransport.this.m_device.getName() : "unknown") + "(" + address + ") by timeout (" + j + "ms)");
                        z = true;
                    } else {
                        z = false;
                    }
                }
                if (z) {
                    BtLETransport.this.disconnect(false);
                }
            }
        }, j, TimeUnit.MILLISECONDS);
    }

    private synchronized void scheduleDisconnectTimeout() {
        this.m_disconnectTimeoutTaskFuture = executor.schedule(new Runnable() { // from class: com.medm.bluetoothle.BtLETransport.2
            @Override // java.lang.Runnable
            public void run() {
                if (BtLETransport.this.m_btGatt != null) {
                    String address = BtLETransport.this.m_device != null ? BtLETransport.this.m_device.getAddress() : "unknown";
                    Logger.str("scheduleDisconnectTimeout: Timeout waiting for disconnect from device (name: " + (BtLETransport.this.m_device != null ? BtLETransport.this.m_device.getName() : "unknown") + ", address: " + address + ")");
                    BtLETransport.this.closeGattAndNotifyDisconnected(Utils.TransportOperationResult.BLUETOOTH_OPERATION_TIMEOUT);
                }
            }
        }, 25000L, TimeUnit.MILLISECONDS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void cancelTimeoutTask() {
        synchronized (this.m_timeoutTaskLock) {
            if (this.m_timeoutTaskFuture != null) {
                this.m_timeoutTaskFuture.cancel(false);
                this.m_timeoutTaskFuture = null;
                executor.purge();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void cancelDisconnectTimeoutTask() {
        if (this.m_disconnectTimeoutTaskFuture != null) {
            this.m_disconnectTimeoutTaskFuture.cancel(false);
            this.m_disconnectTimeoutTaskFuture = null;
            executor.purge();
        }
    }

    private synchronized void cancelDelayServicesDiscoveryRunnable() {
        if (this.m_delayServicesDiscoveryRunnable != null) {
            this.mDiscoverHandler.removeCallbacks(this.m_delayServicesDiscoveryRunnable);
        }
    }

    public void connect(final String str, final boolean z, boolean z2, boolean z3, boolean z4, boolean z5, final long j, boolean z6, boolean z7, boolean z8, boolean z9) {
        Logger.str("BLE connect to device: " + str + ", bAutoConnect = " + z + ", bWaitBonding = " + z2 + ", bUseErrorMonitor = " + z3 + ", bDelayServicesDiscovery = " + z4 + ", bDelayBleOperations = " + z5 + ", nTimeout = " + j + ", bPreferWriteWithoutResponse = " + z6);
        StringBuilder sb = new StringBuilder("bForceBigMTU = ");
        sb.append(z7);
        sb.append(", bNoConnectTimeout = ");
        sb.append(z8);
        sb.append(", bNoOpTimeout = ");
        sb.append(z9);
        Logger.str(sb.toString());
        this.m_bIsConnected = false;
        this.m_bNoConnectTimeout = z8;
        this.m_bNoOpTimeout = z9;
        BtMan.registerBtActivity(this);
        if (this.m_bluetoothAdapter == null) {
            onDisconnected(Utils.TransportOperationResult.ERROR);
            return;
        }
        try {
            this.m_device = this.m_bluetoothAdapter.getRemoteDevice(str);
            if (this.m_device == null) {
                onDisconnected(Utils.TransportOperationResult.NO_SUCH_DEVICE_IN_BLUETOOTH_MANAGER);
                return;
            }
            this.m_bOnDisconnectedFired = false;
            this.m_bRequestBigMTU = z7;
            Logger.str("bDelayServicesDiscovery " + z4);
            if (z4) {
                this.m_bDelayServicesDiscovery = true;
            }
            this.m_bDelayBleOperations = z5;
            if (this.m_BLECallbackExecutor == null) {
                this.m_BLECallbackExecutor = Executors.newSingleThreadExecutor();
            }
            BtMan.TurnBluetooth(true, false, new BtMan.Listener() { // from class: com.medm.bluetoothle.BtLETransport.3
                @Override // com.medm.bluetooth.BtMan.Listener
                public void onBluetoothTurnedOn() {
                    Logger.str("BtLETransport::BtMan.Listener::onBluetoothTurnedOn()");
                    BtMan.unregisterListener(this);
                    BtLETransport.executor.execute(new Runnable() { // from class: com.medm.bluetoothle.BtLETransport.3.1
                        @Override // java.lang.Runnable
                        public void run() {
                            boolean z10;
                            BtLETransport.this.m_callback = new BLEGattCallback();
                            if (BtLETransport.this.m_btErrorMonitor != null) {
                                BtErrorMonitor unused = BtLETransport.this.m_btErrorMonitor;
                                BtErrorMonitor.onConnectionAttempt();
                            }
                            long j2 = j > 0 ? j : 25000L;
                            if (!BtLETransport.this.m_bNoConnectTimeout) {
                                BtLETransport.this.scheduleTimeout(j2);
                            }
                            synchronized (BtLETransport.this.m_lock) {
                                if (Build.VERSION.SDK_INT >= 23) {
                                    BtLETransport.this.m_btGatt = BtLETransport.this.m_device.connectGatt(BtLETransport.this.m_Context, z, BtLETransport.this.m_callback, 2);
                                } else {
                                    BtLETransport.this.m_btGatt = BtLETransport.this.m_device.connectGatt(BtLETransport.this.m_Context, z, BtLETransport.this.m_callback);
                                }
                                z10 = BtLETransport.this.m_btGatt == null;
                            }
                            if (z10) {
                                Logger.str("[E]connectGatt to device returned null, address = " + str);
                                BtLETransport.this.onDisconnected(Utils.TransportOperationResult.CONNECT_ERROR);
                                return;
                            }
                            Logger.str("connectGatt to device succeeded, address = " + str + " waiting for onConnected with timeout = " + j2);
                        }
                    });
                }

                @Override // com.medm.bluetooth.BtMan.Listener
                public void onTimeout() {
                    Logger.str("BtLETransport::BtMan.Listener::onTimeout()");
                    BtMan.unregisterListener(this);
                    BtLETransport.this.onDisconnected(Utils.TransportOperationResult.BLUETOOTH_OPERATION_TIMEOUT);
                }

                @Override // com.medm.bluetooth.BtMan.Listener
                public void onError() {
                    Logger.str("BtLETransport::BtMan.Listener::onError()");
                    BtMan.unregisterListener(this);
                    BtLETransport.this.onDisconnected(Utils.TransportOperationResult.BLUETOOTH_OPERATION_ERROR);
                }
            });
            if (z3) {
                this.m_btErrorMonitor = new BtErrorMonitor();
            }
            this.m_bWaitBonding = z2;
            this.m_bPreferWriteWithoutResponse = z6;
        } catch (IllegalArgumentException unused) {
            Logger.str("[E]BLE connect to device: " + str + " - address is incorrect");
            onDisconnected(Utils.TransportOperationResult.NO_SUCH_DEVICE_IN_BLUETOOTH_MANAGER);
        }
    }

    public void disconnect() {
        disconnect(false);
    }

    public void disconnect(boolean z) {
        BLEOperationsQueue bLEOperationsQueue;
        if (this.m_BLECallbackExecutor != null) {
            this.m_BLECallbackExecutor.shutdownNow();
            this.m_BLECallbackExecutor = null;
        }
        cancelTimeoutTask();
        synchronized (this.m_lock) {
            if (!this.m_bExplicitDisconnecting && this.m_device != null && this.m_btGatt != null) {
                this.m_bExplicitDisconnecting = true;
                Logger.str("BLE disconnect begin, bForced = " + z);
                StringBuilder sb = new StringBuilder("Device connection control: disconnecting device with address = ");
                sb.append(this.m_btGatt.getDevice() != null ? this.m_btGatt.getDevice().getAddress() : "unknown");
                sb.append(", bForced = ");
                sb.append(z);
                Logger.str(sb.toString());
                if (this.m_bleOperationsQueue != null) {
                    this.m_bleOperationsQueue.shutdownNow();
                    bLEOperationsQueue = this.m_bleOperationsQueue;
                } else {
                    bLEOperationsQueue = null;
                }
                if (!z && bLEOperationsQueue != null && !bLEOperationsQueue.waitForRunningOperationFinish(7000L)) {
                    Logger.str("[E]BLE disconnect: error waiting for waitForRunningOperationFinish, force close!");
                }
                if (!this.m_bDeviceWasConnected) {
                    Logger.str("Device wasn't even connected, skip m_btGatt.disconnect() phase");
                }
                if (!this.m_bIsConnected) {
                    Logger.str("Device already disconnected, but hasn't cleared resources, skip m_btGatt.disconnect() phase");
                }
                synchronized (this.m_lock) {
                    this.m_bleOperationsQueue = null;
                    if (!z && this.m_bDeviceWasConnected && this.m_bIsConnected) {
                        Logger.str("BLE disconnect: perform GATT.disconnect()");
                        scheduleDisconnectTimeout();
                        if (this.m_btGatt != null) {
                            this.m_btGatt.disconnect();
                            Logger.str("BLE disconnect ended");
                        }
                    }
                }
                if (z || !this.m_bDeviceWasConnected || !this.m_bIsConnected) {
                    closeGattAndNotifyDisconnected(Utils.TransportOperationResult.OK);
                }
                this.m_bDeviceWasConnected = false;
                this.m_bExplicitDisconnecting = false;
            }
        }
    }

    public void writeCharacteristic(String str, String str2, byte[] bArr) {
        synchronized (this.m_lock) {
            if (this.m_bleOperationsQueue != null) {
                this.m_bleOperationsQueue.add(new BLEOperationKeepDataToWrite(this, this.m_btGatt, str, str2, bArr));
            } else {
                Logger.str("[E]BtLETransport::writeCharacteristic: Attempt to write BLE characteristic before connection to device");
            }
        }
    }

    public void RequestCharacteristic(String str, String str2) {
        synchronized (this.m_lock) {
            if (this.m_bleOperationsQueue != null) {
                this.m_bleOperationsQueue.add(new BLEOperationRead(this.m_btGatt, str, str2));
            } else {
                Logger.str("[E]BtLETransport::RequestCharacteristic: Attempt to request BLE characteristic before connection to device");
            }
        }
    }

    public void RequestDescriptor(String str, String str2, String str3) {
        synchronized (this.m_lock) {
            if (this.m_bleOperationsQueue != null) {
                this.m_bleOperationsQueue.add(new BLEOperationReadDescriptor(this.m_btGatt, str, str2, str3));
            } else {
                Logger.str("[E]BtLETransport::RequestDescriptor: Attempt to request BLE characteristic before connection to device");
            }
        }
    }

    public void subscribeToCharacteristic(String str, String str2, boolean z) {
        synchronized (this.m_lock) {
            if (this.m_bleOperationsQueue != null) {
                this.m_bleOperationsQueue.add(new BLEOperationSubscribeToCharacteristic(this.m_btGatt, str, str2, z));
            } else {
                Logger.str("[E]Attempt to subscribe to characteristic without the queue");
            }
        }
    }

    public void clearCache() {
        synchronized (this.m_lock) {
            if (this.m_bleOperationsQueue != null) {
                this.m_bleOperationsQueue.add(new BLEOperationResetGatt(this.m_btGatt));
            } else {
                Logger.str("[E]BtLETransport::clearCache: Attempt to clear cache before connection to device");
            }
        }
    }

    public void platformPair(final String str) {
        Logger.str("BTLETransport::platformPair(" + str + ")");
        BtMan.registerBtActivity(this);
        BtMan.TurnBluetooth(true, false, new BtMan.Listener() { // from class: com.medm.bluetoothle.BtLETransport.4
            @Override // com.medm.bluetooth.BtMan.Listener
            public void onBluetoothTurnedOn() throws IllegalAccessException, InterruptedException, IllegalArgumentException, InvocationTargetException {
                Logger.str("BTLETransport::BtMan.Listener::onBluetoothTurnedOn()");
                BtMan.unregisterListener(this);
                BtLETransport.this.doPlatformPair(str);
            }

            @Override // com.medm.bluetooth.BtMan.Listener
            public void onTimeout() {
                Logger.str("BTLETransport::BtMan.Listener::onTimeout()");
                BtMan.unregisterListener(this);
                BtLETransport.this.onPair(Utils.TransportOperationResult.BLUETOOTH_OPERATION_TIMEOUT);
            }

            @Override // com.medm.bluetooth.BtMan.Listener
            public void onError() {
                Logger.str("BTLETransport::BtMan.Listener::onError()");
                BtMan.unregisterListener(this);
                BtLETransport.this.onPair(Utils.TransportOperationResult.BLUETOOTH_OPERATION_ERROR);
            }
        });
    }

    public void unpair(final String str) {
        Logger.str("BTLETransport::unpair(" + str + ")");
        BtMan.registerBtActivity(this);
        BtMan.TurnBluetooth(true, false, new BtMan.Listener() { // from class: com.medm.bluetoothle.BtLETransport.5
            @Override // com.medm.bluetooth.BtMan.Listener
            public void onBluetoothTurnedOn() throws IllegalAccessException, InterruptedException, IllegalArgumentException, InvocationTargetException {
                Logger.str("BtRCTransport::BtMan.Listener::onBluetoothTurnedOn()");
                BtMan.unregisterListener(this);
                BtLETransport.this.doUnpair(str);
                BtLETransport.this.onUnpair();
            }

            @Override // com.medm.bluetooth.BtMan.Listener
            public void onTimeout() {
                Logger.str("BtRCTransport::BtMan.Listener::onTimeout()");
                BtMan.unregisterListener(this);
                BtLETransport.this.onUnpair();
            }

            @Override // com.medm.bluetooth.BtMan.Listener
            public void onError() {
                Logger.str("BtRCTransport::BtMan.Listener::onError()");
                BtMan.unregisterListener(this);
                BtLETransport.this.onUnpair();
            }
        });
    }

    public final void doPlatformPair(String str) throws IllegalAccessException, InterruptedException, IllegalArgumentException, InvocationTargetException {
        Logger.str("BTLETransport::doPlatformPair()");
        try {
            BluetoothDevice remoteDevice = this.m_bluetoothAdapter.getRemoteDevice(str);
            if (remoteDevice == null) {
                onPair(Utils.TransportOperationResult.NO_SUCH_DEVICE_IN_BLUETOOTH_MANAGER);
                return;
            }
            Logger.str("Kitkat, pairing");
            doUnpair(str);
            IntentFilter intentFilter = new IntentFilter("android.bluetooth.device.action.BOND_STATE_CHANGED");
            this.m_pairingReceiver = new PairingBroadcastReceiver();
            this.m_Context.registerReceiver(this.m_pairingReceiver, intentFilter);
            Logger.str("Creating bond...");
            if (remoteDevice.createBond()) {
                return;
            }
            executor.schedule(new Runnable() { // from class: com.medm.bluetoothle.BtLETransport.6
                @Override // java.lang.Runnable
                public void run() {
                    if (BtLETransport.this.m_pairingReceiver.mReceived) {
                        return;
                    }
                    Logger.str("Bonding process failed to start");
                    BtLETransport.this.m_Context.unregisterReceiver(BtLETransport.this.m_pairingReceiver);
                    BtLETransport.this.onPair(Utils.TransportOperationResult.ERROR_CREATE_BOND_CALL);
                }
            }, 5L, TimeUnit.SECONDS);
        } catch (IllegalArgumentException unused) {
            Logger.str("[E]BLE connect to device: " + str + " - address is incorrect");
            onPair(Utils.TransportOperationResult.NO_SUCH_DEVICE_IN_BLUETOOTH_MANAGER);
        }
    }

    public boolean doUnpair(String str) throws IllegalAccessException, InterruptedException, IllegalArgumentException, InvocationTargetException {
        BluetoothDevice remoteDevice;
        Logger.str("BTLETransport::doUnpair()");
        try {
            remoteDevice = this.m_bluetoothAdapter.getRemoteDevice(str);
        } catch (IllegalArgumentException unused) {
        }
        if (remoteDevice == null) {
            return true;
        }
        if (remoteDevice.getBondState() != 12) {
            Logger.str("device is not bonded");
            return true;
        }
        try {
            Logger.str("Unbonding");
            remoteDevice.getClass().getMethod("removeBond", null).invoke(remoteDevice, null);
            long jCurrentTimeMillis = System.currentTimeMillis();
            do {
                try {
                } catch (InterruptedException unused2) {
                    Thread.currentThread().interrupt();
                }
                if (System.currentTimeMillis() - jCurrentTimeMillis > DeviceOrientationRequest.OUTPUT_PERIOD_FAST) {
                    Logger.str("Timeout waiting for unbonding");
                    break;
                }
                Thread.sleep(50L);
                Logger.str("Waiting for unbonding..");
            } while (remoteDevice.getBondState() != 10);
            Logger.str("Unbonded successfully");
        } catch (Exception e) {
            Logger.str("BTLETransport::unpair: remove bond exception, address = " + remoteDevice.getAddress() + ": " + e.toString());
        }
        return true;
    }

    public void pair(String str) {
        Logger.str("BTLETransport::pair()");
        try {
            if (this.m_bluetoothAdapter.getRemoteDevice(str) == null) {
                onPair(Utils.TransportOperationResult.NO_SUCH_DEVICE_IN_BLUETOOTH_MANAGER);
            } else {
                onPair(Utils.TransportOperationResult.OK);
            }
        } catch (IllegalArgumentException unused) {
            Logger.str("[E]BLE pair device: " + str + " - address is incorrect");
            onPair(Utils.TransportOperationResult.NO_SUCH_DEVICE_IN_BLUETOOTH_MANAGER);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closeGattAndNotifyDisconnected(Utils.TransportOperationResult transportOperationResult) {
        closeGattAndNotifyDisconnected(transportOperationResult, "");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closeGattAndNotifyDisconnected(Utils.TransportOperationResult transportOperationResult, String str) {
        boolean z;
        Logger.str("closeGattAndNotifyDisconnected()");
        cancelTimeoutTask();
        cancelDelayServicesDiscoveryRunnable();
        synchronized (this.m_lock) {
            if (this.m_btGatt != null) {
                try {
                    this.m_btGatt.close();
                } catch (Exception unused) {
                    Logger.str("BluetoothGatt is no longer valid - ignoring");
                }
                this.m_btGatt = null;
                z = true;
            } else {
                z = false;
            }
        }
        if (z) {
            onDisconnected(transportOperationResult, str);
        }
        Logger.str("closeGattAndNotifyDisconnected: BLE disconnect end");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onConnected() {
        long j;
        Logger.str("BLE onConnected: listener = " + this.m_nListenerPtr);
        this.m_bDeviceWasConnected = true;
        synchronized (this.m_lock) {
            if (this.m_bRequestBigMTU) {
                this.m_bleOperationsQueue.add(new BLEOperationRequestMTU(this.m_btGatt));
            }
        }
        synchronized (this.m_listenerLock) {
            j = this.m_nListenerPtr;
        }
        if (j != 0) {
            onConnected(j);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void NotifyCharacteristics(BluetoothGatt bluetoothGatt) {
        long j;
        Logger.str("BLE NotifyCharacteristics: listener = " + this.m_nListenerPtr);
        if (bluetoothGatt == null) {
            Logger.str("BLE NotifyCharacteristics: gatt is null");
            return;
        }
        for (BluetoothGattService bluetoothGattService : bluetoothGatt.getServices()) {
            List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
            synchronized (this.m_listenerLock) {
                j = this.m_nListenerPtr;
            }
            if (j != 0) {
                Iterator<BluetoothGattCharacteristic> it = characteristics.iterator();
                while (it.hasNext()) {
                    NotifyCharacteristic(j, bluetoothGattService.getUuid().toString(), it.next().getUuid().toString());
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onDisconnected(Utils.TransportOperationResult transportOperationResult) {
        this.m_bIsConnected = false;
        onDisconnected(transportOperationResult, "");
    }

    private void onDisconnected(Utils.TransportOperationResult transportOperationResult, String str) {
        long j;
        Logger.str("BLE onDisconnected: listener = " + this.m_nListenerPtr);
        cancelTimeoutTask();
        synchronized (this.m_lock) {
            if (this.m_bOnDisconnectedFired) {
                Logger.str("m_bOnDisconnectedFired = true, return");
                return;
            }
            this.m_bOnDisconnectedFired = true;
            this.m_bOnServicesDiscoveredEventPosted = false;
            this.m_device = null;
            this.m_callback = null;
            this.m_AskCharacterArray = null;
            this.m_NotifyCharacterArray = null;
            this.m_btErrorMonitor = null;
            if (this.m_bleOperationsQueue != null) {
                this.m_bleOperationsQueue.shutdownNow();
                this.m_bleOperationsQueue = null;
            }
            BtMan.unregisterBtActivity(this);
            synchronized (this.m_listenerLock) {
                j = this.m_nListenerPtr;
            }
            if (j != 0) {
                onDisconnected(j, transportOperationResult.ordinal(), str);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void onPair(Utils.TransportOperationResult transportOperationResult) {
        long j;
        Logger.str("Pair result = " + transportOperationResult);
        BtMan.unregisterBtActivity(this);
        synchronized (this.m_listenerLock) {
            j = this.m_nListenerPtr;
        }
        if (j != 0) {
            onPair(j, transportOperationResult.ordinal());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onKeyValuePair(final String str, final String str2, final byte[] bArr, final int i) {
        Logger.str("BLE onKeyValuePair (queueing): strServiceUUID = " + str + ", strCharUUID = " + str2);
        if (this.m_btErrorMonitor != null) {
            this.m_btErrorMonitor.OnData();
        }
        if (this.m_BLECallbackExecutor == null) {
            return;
        }
        this.m_BLECallbackExecutor.submit(new Runnable() { // from class: com.medm.bluetoothle.BtLETransport.7
            @Override // java.lang.Runnable
            public void run() {
                long j;
                Logger.str("BLE onKeyValuePair (executing): strServiceUUID = " + str + ", strCharUUID = " + str2);
                synchronized (BtLETransport.this.m_listenerLock) {
                    j = BtLETransport.this.m_nListenerPtr;
                }
                if (j != 0) {
                    BtLETransport.onKeyValuePair(j, str, str2, bArr, i);
                }
            }
        });
    }

    public void SetCharacteristicsBlackList(String[] strArr, int[] iArr) {
        if (this.m_characteristicsReadBlackList == null) {
            this.m_characteristicsReadBlackList = new ArrayList<>();
        }
        if (this.m_characteristicsNotificationBlackList == null) {
            this.m_characteristicsNotificationBlackList = new ArrayList<>();
        }
        for (int i = 0; i < strArr.length; i++) {
            String str = strArr[i];
            Logger.str("BlackList: characteristic UUID = " + str + "; action = " + iArr[i]);
            int i2 = iArr[i];
            if (i2 == 1) {
                this.m_characteristicsReadBlackList.add(UUID.fromString(str));
            } else if (i2 == 3) {
                this.m_characteristicsNotificationBlackList.add(UUID.fromString(str));
            }
        }
    }

    public void subscribeToDevice(String[] strArr) {
        long j;
        ArrayList<UUID> arrayList;
        Logger.str("BLE subscribeToDevice");
        synchronized (this.m_lock) {
            if (this.m_btGatt == null) {
                Logger.str("[E]no gatt!");
                disconnect(true);
                return;
            }
            List<BluetoothGattService> services = this.m_btGatt.getServices();
            if (services == null || services.isEmpty()) {
                Logger.str("[E]no services!");
                disconnect(true);
                return;
            }
            HashSet hashSet = new HashSet();
            for (String str : strArr) {
                Logger.str("Input: characteristic UUID = " + str);
                hashSet.add(UUID.fromString(str));
            }
            LogServicesAndCharacteristics(services);
            HashSet hashSet2 = new HashSet();
            for (BluetoothGattService bluetoothGattService : services) {
                UUID uuid = bluetoothGattService.getUuid();
                if (isUsefulServiceUUID(uuid.toString())) {
                    if (hashSet2.contains(uuid)) {
                        Logger.err("Duplicated service " + uuid + ", ignoring");
                    } else {
                        hashSet2.add(uuid);
                        synchronized (this.m_listenerLock) {
                            j = this.m_nListenerPtr;
                        }
                        if (j != 0) {
                            NotifyService(j, bluetoothGattService.getUuid().toString());
                        }
                        List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
                        if (characteristics != null) {
                            for (BluetoothGattCharacteristic bluetoothGattCharacteristic : characteristics) {
                                if (hashSet.contains(bluetoothGattCharacteristic.getUuid())) {
                                    Logger.str("characteristic known, UUID = " + bluetoothGattCharacteristic.getUuid());
                                    if (IsCharacteristicReadable(bluetoothGattCharacteristic) && ((arrayList = this.m_characteristicsReadBlackList) == null || !arrayList.contains(bluetoothGattCharacteristic.getUuid()))) {
                                        this.m_AskCharacterArray.add(bluetoothGattCharacteristic);
                                    }
                                    if (IsCharacteristicNotificatable(bluetoothGattCharacteristic) || IsCharacteristicIndicatable(bluetoothGattCharacteristic)) {
                                        ArrayList<UUID> arrayList2 = this.m_characteristicsNotificationBlackList;
                                        if (arrayList2 == null || !arrayList2.contains(bluetoothGattCharacteristic.getUuid())) {
                                            this.m_NotifyCharacterArray.add(bluetoothGattCharacteristic);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SetNotifyCharacteristics(true);
        }
    }

    public void LogServicesAndCharacteristics(List<BluetoothGattService> list) {
        for (BluetoothGattService bluetoothGattService : list) {
            Logger.str("Found: service UUID = " + bluetoothGattService.getUuid());
            for (BluetoothGattCharacteristic bluetoothGattCharacteristic : bluetoothGattService.getCharacteristics()) {
                Logger.str("Found:      characteristic UUID = " + bluetoothGattCharacteristic.getUuid() + "; bReadable = " + IsCharacteristicReadable(bluetoothGattCharacteristic) + "; bWritable = " + IsCharacteristicWritable(bluetoothGattCharacteristic) + "; bNotificatable = " + IsCharacteristicNotificatable(bluetoothGattCharacteristic) + "; bIndicatable = " + IsCharacteristicIndicatable(bluetoothGattCharacteristic));
            }
        }
    }

    public synchronized void SetNotifyCharacteristics(boolean z) {
        if (this.m_NotifyCharacterArray != null) {
            Iterator<BluetoothGattCharacteristic> it = this.m_NotifyCharacterArray.iterator();
            while (it.hasNext()) {
                BluetoothGattCharacteristic next = it.next();
                if (!next.getUuid().equals(UUID.fromString("00002a21-0000-1000-8000-00805f9b34fb"))) {
                    Logger.str("subscribeToCharacteristic result: " + Boolean.toString(subscribeToCharacteristic(z, next)));
                }
            }
        }
        synchronized (this.m_lock) {
            if (z) {
                if (this.m_AskCharacterArray != null && this.m_bleOperationsQueue != null) {
                    Iterator<BluetoothGattCharacteristic> it2 = this.m_AskCharacterArray.iterator();
                    while (it2.hasNext()) {
                        this.m_bleOperationsQueue.add(new BLEOperationRead(this.m_btGatt, it2.next()));
                    }
                }
            }
        }
    }

    public boolean subscribeToCharacteristic(boolean z, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        boolean z2;
        Logger.str("subscribeToCharacteristic (" + bluetoothGattCharacteristic.getUuid().toString() + ')');
        synchronized (this.m_lock) {
            if (this.m_btGatt == null) {
                return false;
            }
            BluetoothGattDescriptor descriptor = bluetoothGattCharacteristic.getDescriptor(CCC);
            if (descriptor == null) {
                return false;
            }
            if (bluetoothGattCharacteristic.getUuid().equals(UUID.fromString("0000ffb1-0000-1000-8000-00805f9b34fb"))) {
                return false;
            }
            if (IsCharacteristicIndicatable(bluetoothGattCharacteristic)) {
                if (z) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                }
                z2 = true;
            } else {
                z2 = false;
            }
            if (IsCharacteristicNotificatable(bluetoothGattCharacteristic)) {
                descriptor.setValue(z ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                z2 = true;
            }
            if (!z2) {
                Logger.str("[E]subscribeToCharacteristic: Trying to subscribe to characteristic neither for notification nor for indication " + bluetoothGattCharacteristic.getUuid().toString());
                return false;
            }
            if (!this.m_btGatt.setCharacteristicNotification(bluetoothGattCharacteristic, z) || this.m_bleOperationsQueue == null) {
                return false;
            }
            this.m_bleOperationsQueue.add(new BLEOperationWriteDescriptor(this.m_btGatt, descriptor));
            if (bluetoothGattCharacteristic.getService() != null && bluetoothGattCharacteristic.getService().getUuid().equals(UUID.fromString("273e5100-6b90-4779-83b8-b8bf1dadac35"))) {
                this.m_bleOperationsQueue.add(new BLEOperationReadDescriptor(this.m_btGatt, descriptor));
            }
            return true;
        }
    }

    public static boolean IsCharacteristicReadable(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return (bluetoothGattCharacteristic.getProperties() & 2) > 0;
    }

    public static boolean IsCharacteristicWritable(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return (bluetoothGattCharacteristic.getProperties() & 8) > 0 || (bluetoothGattCharacteristic.getProperties() & 4) > 0;
    }

    public static boolean IsCharacteristicIndicatable(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return (bluetoothGattCharacteristic.getProperties() & 32) > 0;
    }

    public static boolean IsCharacteristicNotificatable(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return (bluetoothGattCharacteristic.getProperties() & 16) > 0;
    }

    private abstract class BLEOperation implements Runnable {
        private BluetoothGatt m_bleOpGatt;

        protected abstract void execute();

        public BLEOperation(BluetoothGatt bluetoothGatt) {
            this.m_bleOpGatt = bluetoothGatt;
            if (getGatt() == null) {
                Logger.str("[E]BLEOperation::BLEOperation GATT = NULL");
            }
        }

        protected BluetoothGatt getGatt() {
            return this.m_bleOpGatt;
        }

        public boolean isValid() {
            return this.m_bleOpGatt != null;
        }

        protected long getTimeoutMS() {
            return BtLETransport.this.m_bNoOpTimeout ? 60000L : 7000L;
        }

        @Override // java.lang.Runnable
        public void run() throws InterruptedException {
            synchronized (BtLETransport.this.m_bleOperationsQueue.m_OperationIsRunningLock) {
                BtLETransport.this.m_bleOperationsQueue.m_bOperationIsRunning = true;
            }
            boolean z = true;
            for (int i = 1; z && i <= 1; i++) {
                Logger.str("BLEOperation::run:attempt " + i + " starting with timeout " + getTimeoutMS() + " ms: " + toString());
                long jCurrentTimeMillis = System.currentTimeMillis();
                if (BtLETransport.this.m_bDelayBleOperations) {
                    try {
                        Thread.sleep(300L);
                    } catch (InterruptedException e) {
                        Logger.str("[E]BLEOperation::run: wait exception = " + e.getMessage() + " and stack = " + e.getStackTrace());
                    }
                }
                try {
                    execute();
                } catch (Throwable th) {
                    Logger.str("[E]BLEOperation::run: execute exception = " + th.getMessage() + " and stack = " + th.getStackTrace());
                }
                if (!BtLETransport.this.m_bleOperationsQueue.waitForRunningOperationFinish(getTimeoutMS())) {
                    Logger.str("BLEOperation::run: forcing disconnect in " + (System.currentTimeMillis() - jCurrentTimeMillis) + " ms after crash or timeout: " + toString());
                    BtLETransport.this.m_bleOperationsQueue.shutdownNow();
                    BtLETransport.this.disconnect(true);
                } else {
                    long jCurrentTimeMillis2 = System.currentTimeMillis();
                    int lastOperationStatus = BtLETransport.this.m_bleOperationsQueue.getLastOperationStatus();
                    if (lastOperationStatus == 0 || lastOperationStatus == 6) {
                        Logger.str("BLEOperation::run: completed in " + (jCurrentTimeMillis2 - jCurrentTimeMillis) + " ms: " + toString());
                    } else if (i < 1 && lastOperationStatus == 137 && getGatt() != null && getGatt().getDevice() != null) {
                        Logger.str("[E]BLEOperation::run: completed in " + (jCurrentTimeMillis2 - jCurrentTimeMillis) + " ms: " + toString() + " with status: " + lastOperationStatus + ", will retry");
                        waitForBondingWithTimeout(getGatt().getDevice(), 15000L, "BLEOperation::run");
                        z = true;
                    } else {
                        Logger.str("[E]BLEOperation::run: completed in " + (jCurrentTimeMillis2 - jCurrentTimeMillis) + " ms: " + toString() + " with status: " + lastOperationStatus + ", will close gatt");
                        BtLETransport.this.closeGattAndNotifyDisconnected(Utils.TransportOperationResult.GATT_ERROR, Integer.toString(lastOperationStatus));
                    }
                }
                z = false;
            }
        }

        protected boolean waitForBondingWithTimeout(BluetoothDevice bluetoothDevice, long j, String str) throws InterruptedException {
            if (bluetoothDevice == null) {
                return false;
            }
            int bondState = bluetoothDevice.getBondState();
            long jCurrentTimeMillis = System.currentTimeMillis();
            if (bondState != 12) {
                Logger.str(str + ": waiting for bonding");
            }
            while (bondState != 12 && System.currentTimeMillis() < jCurrentTimeMillis + j) {
                try {
                    Thread.sleep(50L);
                    int bondState2 = bluetoothDevice.getBondState();
                    if (bondState2 != bondState) {
                        Logger.str(str + ": bond state changed, was " + Utils.bondStateToString(bondState) + " now " + Utils.bondStateToString(bondState2));
                        bondState = bondState2;
                    }
                } catch (InterruptedException e) {
                    Logger.str("[E]" + str + ": waiting for bonding: interrupted");
                    e.printStackTrace();
                }
            }
            Logger.str(str + ": current bond state before next operation = " + Utils.bondStateToString(bondState));
            StringBuilder sb = new StringBuilder();
            sb.append(str);
            sb.append(": wait 500ms before next operation");
            Logger.str(sb.toString());
            try {
                Thread.sleep(500);
            } catch (InterruptedException e2) {
                e2.printStackTrace();
            }
            return bondState == 12;
        }
    }

    private class BLEOperationWriteDescriptor extends BLEOperation {
        private BluetoothGattDescriptor m_gattDescriptor;

        public BLEOperationWriteDescriptor(BluetoothGatt bluetoothGatt, BluetoothGattDescriptor bluetoothGattDescriptor) {
            super(bluetoothGatt);
            this.m_gattDescriptor = bluetoothGattDescriptor;
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() {
            getGatt().writeDescriptor(this.m_gattDescriptor);
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public boolean isValid() {
            return this.m_gattDescriptor != null && super.isValid();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("writeDescriptor characteristic = ");
            BluetoothGattDescriptor bluetoothGattDescriptor = this.m_gattDescriptor;
            Object uuid = "[E]null";
            sb.append((bluetoothGattDescriptor == null || bluetoothGattDescriptor.getCharacteristic() == null) ? "[E]null" : this.m_gattDescriptor.getCharacteristic().getUuid());
            sb.append(" service = ");
            BluetoothGattDescriptor bluetoothGattDescriptor2 = this.m_gattDescriptor;
            if (bluetoothGattDescriptor2 != null && bluetoothGattDescriptor2.getCharacteristic() != null && this.m_gattDescriptor.getCharacteristic().getService() != null) {
                uuid = this.m_gattDescriptor.getCharacteristic().getService().getUuid();
            }
            sb.append(uuid);
            return sb.toString();
        }
    }

    private class BLEOperationReadDescriptor extends BLEOperation {
        private BluetoothGattDescriptor m_gattDescriptor;

        public BLEOperationReadDescriptor(BluetoothGatt bluetoothGatt, BluetoothGattDescriptor bluetoothGattDescriptor) {
            super(bluetoothGatt);
            this.m_gattDescriptor = bluetoothGattDescriptor;
        }

        public BLEOperationReadDescriptor(BluetoothGatt bluetoothGatt, String str, String str2, String str3) {
            super(bluetoothGatt);
            BluetoothGattService service = getGatt().getService(UUID.fromString(str));
            if (service == null) {
                Logger.str("[E]BLEOperationReadDescriptor::BLEOperationReadDescriptor SERVICE = [E]null: " + str);
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(str2));
            if (characteristic == null) {
                Logger.str("[E]BLEOperationReadDescriptor::BLEOperationReadDescriptor CHARACTERISTIC = [E]null: " + str2);
                return;
            }
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(str3));
            this.m_gattDescriptor = descriptor;
            if (descriptor == null) {
                Logger.str("[E]BLEOperationReadDescriptor::BLEOperationReadDescriptor DESCRIPTOR = [E]null: " + str3);
            }
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() {
            getGatt().readDescriptor(this.m_gattDescriptor);
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public boolean isValid() {
            return this.m_gattDescriptor != null && super.isValid();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("readDescriptor characteristic = ");
            BluetoothGattDescriptor bluetoothGattDescriptor = this.m_gattDescriptor;
            Object uuid = "[E]null";
            sb.append((bluetoothGattDescriptor == null || bluetoothGattDescriptor.getCharacteristic() == null) ? "[E]null" : this.m_gattDescriptor.getCharacteristic().getUuid());
            sb.append(" service = ");
            BluetoothGattDescriptor bluetoothGattDescriptor2 = this.m_gattDescriptor;
            if (bluetoothGattDescriptor2 != null && bluetoothGattDescriptor2.getCharacteristic() != null && this.m_gattDescriptor.getCharacteristic().getService() != null) {
                uuid = this.m_gattDescriptor.getCharacteristic().getService().getUuid();
            }
            sb.append(uuid);
            return sb.toString();
        }
    }

    private class BLEOperationDiscoverServices extends BLEOperation {
        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        protected long getTimeoutMS() {
            return WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS;
        }

        public BLEOperationDiscoverServices(BluetoothGatt bluetoothGatt) {
            super(bluetoothGatt);
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() {
            BluetoothDevice device = getGatt().getDevice();
            Logger.str("BLEOperationDiscoverServices: current bond state = " + Utils.bondStateToString(device.getBondState()));
            if (BtLETransport.this.m_bWaitBonding && !waitForBondingWithTimeout(device, 15000L, "BLEOperationDiscoverServices")) {
                BtLETransport.this.disconnect(true);
            } else {
                Logger.str("BLEOperationDiscoverServices: will start discoverServices");
                getGatt().discoverServices();
            }
        }

        public String toString() {
            return "BLEOperationDiscoverServices";
        }
    }

    private class BLEOperationResetGatt extends BLEOperation {
        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        protected long getTimeoutMS() {
            return 15000L;
        }

        public BLEOperationResetGatt(BluetoothGatt bluetoothGatt) {
            super(bluetoothGatt);
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            try {
                getGatt().getClass().getMethod("refresh", null).invoke(getGatt(), null);
            } catch (IllegalAccessException unused) {
                Logger.str("BLEOperationResetGatt: IllegalAccessException");
            } catch (NoSuchMethodException unused2) {
                Logger.str("BLEOperationResetGatt: NoSuchMethodException");
            } catch (InvocationTargetException unused3) {
                Logger.str("BLEOperationResetGatt: InvocationTargetException");
            }
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_bleOperationsQueue != null) {
                    BtLETransport.this.m_bleOperationsQueue.onOperationComplete(0);
                }
            }
        }

        public String toString() {
            return "BLEOperationResetGatt";
        }
    }

    private class BLEOperationReadPhy extends BLEOperation {
        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        protected long getTimeoutMS() {
            return 15000L;
        }

        public BLEOperationReadPhy(BluetoothGatt bluetoothGatt) {
            super(bluetoothGatt);
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() {
            if (Build.VERSION.SDK_INT >= 26) {
                getGatt().readPhy();
            }
        }

        public String toString() {
            return "BLEOperationReadPhy";
        }
    }

    private class BLEOperationRequestMTU extends BLEOperation {
        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        protected long getTimeoutMS() {
            return 15000L;
        }

        public BLEOperationRequestMTU(BluetoothGatt bluetoothGatt) {
            super(bluetoothGatt);
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() {
            getGatt().requestMtu(200);
        }

        public String toString() {
            return "BLEOperationRequestMTU";
        }
    }

    private class BLEOperationKeepDataToWrite extends BLEOperation {
        private byte[] m_bytes;
        String m_strCharacteristic;
        String m_strService;
        private int m_writeType;

        public BLEOperationKeepDataToWrite(BtLETransport btLETransport, BluetoothGatt bluetoothGatt, String str, String str2, byte[] bArr) {
            this(bluetoothGatt, str, str2, bArr, 2);
        }

        public BLEOperationKeepDataToWrite(BluetoothGatt bluetoothGatt, String str, String str2, byte[] bArr, int i) {
            super(bluetoothGatt);
            this.m_strCharacteristic = str2;
            this.m_strService = str;
            this.m_bytes = bArr;
            this.m_writeType = i;
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        protected BluetoothGatt getGatt() {
            return super.getGatt();
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() {
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_bleOperationsQueue != null) {
                    BLEOperationsQueue bLEOperationsQueue = BtLETransport.this.m_bleOperationsQueue;
                    BtLETransport btLETransport = BtLETransport.this;
                    bLEOperationsQueue.add(btLETransport.new BLEOperationWriteCharacteristicBytes(btLETransport.m_btGatt, this.m_strService, this.m_strCharacteristic, this.m_bytes, this.m_writeType));
                    BtLETransport.this.m_bleOperationsQueue.onOperationCompleteAsync(0);
                }
            }
        }

        public String toString() {
            return "BLEOperationKeepDataToWrite: service =" + this.m_strService + " characteristic = " + this.m_strCharacteristic;
        }
    }

    private class BLEOperationSubscribeToCharacteristic extends BLEOperation {
        private boolean m_bSubscribe;
        private BluetoothGattCharacteristic m_gattCharacteristic;

        public BLEOperationSubscribeToCharacteristic(BluetoothGatt bluetoothGatt, String str, String str2, boolean z) {
            super(bluetoothGatt);
            this.m_gattCharacteristic = null;
            this.m_bSubscribe = z;
            if (getGatt() == null) {
                Logger.str("BLEOperationRead::BLEOperationSubscribeToCharacteristic GATT = [E]null");
                return;
            }
            BluetoothGattService service = getGatt().getService(UUID.fromString(str));
            if (service == null) {
                Logger.str("[E]BLEOperationRead::BLEOperationSubscribeToCharacteristic SERVICE = [E]null: " + str);
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(str2));
            this.m_gattCharacteristic = characteristic;
            if (characteristic == null) {
                Logger.str("[E]BLEOperationRead::BLEOperationSubscribeToCharacteristic CHARACTERISTIC = [E]null: " + str2);
            }
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() {
            BluetoothGattCharacteristic bluetoothGattCharacteristic = this.m_gattCharacteristic;
            if (bluetoothGattCharacteristic != null) {
                BluetoothGattDescriptor descriptor = bluetoothGattCharacteristic.getDescriptor(BtLETransport.CCC);
                if ((this.m_gattCharacteristic.getProperties() & 32) > 0) {
                    if (this.m_bSubscribe) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    }
                } else if ((this.m_gattCharacteristic.getProperties() & 16) > 0) {
                    descriptor.setValue(this.m_bSubscribe ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                } else {
                    Logger.str("[E]subscribeToCharacteristic: Trying to subscribe to characteristic neither for notification nor for indication " + this.m_gattCharacteristic.getUuid().toString());
                    return;
                }
                synchronized (BtLETransport.this.m_lock) {
                    if (BtLETransport.this.m_btGatt.setCharacteristicNotification(this.m_gattCharacteristic, this.m_bSubscribe) && BtLETransport.this.m_bleOperationsQueue != null) {
                        BLEOperationsQueue bLEOperationsQueue = BtLETransport.this.m_bleOperationsQueue;
                        BtLETransport btLETransport = BtLETransport.this;
                        bLEOperationsQueue.add(btLETransport.new BLEOperationWriteDescriptor(btLETransport.m_btGatt, descriptor));
                        if (this.m_gattCharacteristic.getService() != null && this.m_gattCharacteristic.getService().getUuid().equals(UUID.fromString("273e5100-6b90-4779-83b8-b8bf1dadac35"))) {
                            BLEOperationsQueue bLEOperationsQueue2 = BtLETransport.this.m_bleOperationsQueue;
                            BtLETransport btLETransport2 = BtLETransport.this;
                            bLEOperationsQueue2.add(btLETransport2.new BLEOperationReadDescriptor(btLETransport2.m_btGatt, descriptor));
                        }
                        BtLETransport.this.m_bleOperationsQueue.onOperationCompleteAsync(0);
                    }
                }
                return;
            }
            Logger.str("[E]BLEOperationSubscribeToCharacteristic::run: characteristic = [E]null");
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public boolean isValid() {
            return this.m_gattCharacteristic != null && super.isValid();
        }

        public String toString() {
            Object uuid;
            StringBuilder sb = new StringBuilder("BLEOperationSubscribeToCharacteristic characteristic = ");
            BluetoothGattCharacteristic bluetoothGattCharacteristic = this.m_gattCharacteristic;
            if (bluetoothGattCharacteristic != null) {
                uuid = bluetoothGattCharacteristic.getUuid();
            } else {
                uuid = "[E]null; Subscribe = " + this.m_bSubscribe;
            }
            sb.append(uuid);
            return sb.toString();
        }
    }

    private class BLEOperationRead extends BLEOperation {
        private BluetoothGattCharacteristic m_gattCharacteristic;

        public BLEOperationRead(BluetoothGatt bluetoothGatt, String str, String str2) {
            super(bluetoothGatt);
            if (getGatt() == null) {
                Logger.str("[E]BLEOperationRead::BLEOperationRead GATT = [E]null");
                return;
            }
            BluetoothGattService service = getGatt().getService(UUID.fromString(str));
            if (service == null) {
                Logger.str("[E]BLEOperationRead::BLEOperationRead SERVICE = [E]null: " + str);
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(str2));
            this.m_gattCharacteristic = characteristic;
            if (characteristic == null) {
                Logger.str("[E]BLEOperationRead::BLEOperationRead CHARACTERISTIC = [E]null: " + str2);
            }
        }

        public BLEOperationRead(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
            super(bluetoothGatt);
            this.m_gattCharacteristic = bluetoothGattCharacteristic;
            if (bluetoothGattCharacteristic == null) {
                Logger.str("[E]BLEOperationRead::BLEOperationRead CHARACTERISTIC = [E]null");
            }
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() {
            if (this.m_gattCharacteristic != null) {
                getGatt().readCharacteristic(this.m_gattCharacteristic);
            } else {
                Logger.str("[E]BLEOperationRead::run: characteristic = [E]null");
            }
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public boolean isValid() {
            return this.m_gattCharacteristic != null && super.isValid();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("BLEOperationRead characteristic = ");
            BluetoothGattCharacteristic bluetoothGattCharacteristic = this.m_gattCharacteristic;
            sb.append(bluetoothGattCharacteristic != null ? bluetoothGattCharacteristic.getUuid() : "[E]null");
            return sb.toString();
        }
    }

    private class BLEOperationWriteCharacteristicBytes extends BLEOperation {
        private byte[] m_bytes;
        private BluetoothGattCharacteristic m_gattCharacteristic;
        private int m_writeType;

        public BLEOperationWriteCharacteristicBytes(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic, byte[] bArr, int i) {
            super(bluetoothGatt);
            this.m_gattCharacteristic = bluetoothGattCharacteristic;
            this.m_bytes = bArr;
            this.m_writeType = i;
        }

        public BLEOperationWriteCharacteristicBytes(BtLETransport btLETransport, BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic, byte[] bArr) {
            this(bluetoothGatt, bluetoothGattCharacteristic, bArr, 2);
        }

        public BLEOperationWriteCharacteristicBytes(BluetoothGatt bluetoothGatt, String str, String str2, byte[] bArr, int i) {
            super(bluetoothGatt);
            this.m_gattCharacteristic = null;
            if (getGatt() == null) {
                Logger.str("[E]BLEOperationWriteCharacteristicBytes: GATT = [E]null");
                return;
            }
            BluetoothGattService service = getGatt().getService(UUID.fromString(str));
            if (service == null) {
                Logger.str("[E]BLEOperationWriteCharacteristicBytes: SERVICE = [E]null:" + str);
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(str2));
            this.m_gattCharacteristic = characteristic;
            if (characteristic == null) {
                Logger.str("[E]BLEOperationWriteCharacteristicBytes: CHARACTERISTIC = [E]null:" + str2);
                return;
            }
            this.m_bytes = bArr;
            if (((characteristic.getProperties() & 8) == 0 || BtLETransport.this.m_bPreferWriteWithoutResponse) && (this.m_gattCharacteristic.getProperties() & 4) > 0) {
                Logger.str("BLEOperationWriteCharacteristicBytes: Default write is not permitted while no response is OK for characteristic = " + this.m_gattCharacteristic.getUuid());
                this.m_writeType = 1;
                return;
            }
            this.m_writeType = i;
        }

        public BLEOperationWriteCharacteristicBytes(BtLETransport btLETransport, BluetoothGatt bluetoothGatt, String str, String str2, byte[] bArr) {
            this(bluetoothGatt, str, str2, bArr, 2);
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public void execute() {
            if (this.m_gattCharacteristic == null) {
                Logger.str("[E]BLEOperationWriteCharacteristicBytes::run: attempt to run operation against null characteristic");
                return;
            }
            Logger.str("Writing mode = " + this.m_writeType);
            Logger.str("Writing bytes = " + Logger.bytesToHex(this.m_bytes));
            if ((this.m_gattCharacteristic.getProperties() & 8) > 0 || (this.m_gattCharacteristic.getProperties() & 4) > 0) {
                this.m_gattCharacteristic.setWriteType(this.m_writeType);
                if (this.m_gattCharacteristic.setValue(this.m_bytes) && getGatt().writeCharacteristic(this.m_gattCharacteristic)) {
                    return;
                }
                Logger.str("[E]BLEOperationWriteCharacteristicBytes::run: unable to set or write to characteristic");
                return;
            }
            Logger.str("[E]BLEOperationWriteCharacteristicBytes::run: attempt to write characteristic that is not writable");
        }

        @Override // com.medm.bluetoothle.BtLETransport.BLEOperation
        public boolean isValid() {
            return this.m_gattCharacteristic != null && super.isValid();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("BLEOperationWriteCharacteristicBytes characteristic = ");
            BluetoothGattCharacteristic bluetoothGattCharacteristic = this.m_gattCharacteristic;
            Object uuid = "[E]null";
            sb.append(bluetoothGattCharacteristic != null ? bluetoothGattCharacteristic.getUuid() : "[E]null");
            sb.append(" service = ");
            BluetoothGattCharacteristic bluetoothGattCharacteristic2 = this.m_gattCharacteristic;
            if (bluetoothGattCharacteristic2 != null && bluetoothGattCharacteristic2.getService() != null) {
                uuid = this.m_gattCharacteristic.getService().getUuid();
            }
            sb.append(uuid);
            return sb.toString();
        }
    }

    private class BLEOperationsQueue {
        private boolean m_bOperationIsRunning = false;
        private final Object m_OperationIsRunningLock = new Object();
        private int m_bLastOperationStatus = 0;
        private ExecutorService m_BLEOperationsExecutor = Executors.newSingleThreadExecutor();

        public BLEOperationsQueue() {
        }

        public int getLastOperationStatus() {
            return this.m_bLastOperationStatus;
        }

        public synchronized void shutdownNow() {
            ExecutorService executorService = this.m_BLEOperationsExecutor;
            if (executorService != null) {
                executorService.shutdownNow();
                this.m_BLEOperationsExecutor = null;
            }
        }

        public synchronized void add(BLEOperation bLEOperation) {
            Logger.str("BLEOperationsQueue::add: " + bLEOperation);
            ExecutorService executorService = this.m_BLEOperationsExecutor;
            if (executorService == null) {
                Logger.str("[E]BLEOperationsQueue::add: add ignored due to queue shutdown");
            } else if (bLEOperation == null) {
                Logger.str("[E]BLEOperationsQueue::add: attempt to add null operation, forcing disconnect");
                BtLETransport.this.disconnect(false);
            } else {
                executorService.submit(bLEOperation);
            }
        }

        public synchronized void onOperationComplete(int i) {
            synchronized (this.m_OperationIsRunningLock) {
                this.m_bLastOperationStatus = i;
                this.m_bOperationIsRunning = false;
                this.m_OperationIsRunningLock.notifyAll();
            }
        }

        public void onOperationCompleteAsync(final int i) {
            BtLETransport.executor.execute(new Runnable() { // from class: com.medm.bluetoothle.BtLETransport.BLEOperationsQueue.1
                @Override // java.lang.Runnable
                public void run() {
                    synchronized (BtLETransport.this.m_lock) {
                        if (BtLETransport.this.m_bleOperationsQueue != null) {
                            BtLETransport.this.m_bleOperationsQueue.onOperationComplete(i);
                        }
                    }
                }
            });
        }

        /* JADX WARN: Removed duplicated region for block: B:11:0x0016 A[Catch: all -> 0x0021, TryCatch #0 {, blocks: (B:4:0x0003, B:6:0x0007, B:9:0x0012, B:11:0x0016, B:12:0x001b, B:13:0x001f, B:8:0x000d), top: B:18:0x0003, inners: #1 }] */
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct code enable 'Show inconsistent code' option in preferences
        */
        public boolean waitForRunningOperationFinish(long r3) {
            /*
                r2 = this;
                java.lang.Object r0 = r2.m_OperationIsRunningLock
                monitor-enter(r0)
                boolean r1 = r2.m_bOperationIsRunning     // Catch: java.lang.Throwable -> L21
                if (r1 == 0) goto L12
                java.lang.Object r1 = r2.m_OperationIsRunningLock     // Catch: java.lang.InterruptedException -> Ld java.lang.Throwable -> L21
                r1.wait(r3)     // Catch: java.lang.InterruptedException -> Ld java.lang.Throwable -> L21
                goto L12
            Ld:
                java.lang.String r3 = "[E]BLEOperationsQueue::waitForRunningOperationFinish: InterruptedException"
                com.medm.logger.Logger.str(r3)     // Catch: java.lang.Throwable -> L21
            L12:
                boolean r3 = r2.m_bOperationIsRunning     // Catch: java.lang.Throwable -> L21
                if (r3 == 0) goto L1b
                java.lang.String r3 = "[E]BLEOperationsQueue::waitForRunningOperationFinish: timeout waiting for current GATT operation"
                com.medm.logger.Logger.str(r3)     // Catch: java.lang.Throwable -> L21
            L1b:
                boolean r3 = r2.m_bOperationIsRunning     // Catch: java.lang.Throwable -> L21
                r3 = r3 ^ 1
                monitor-exit(r0)     // Catch: java.lang.Throwable -> L21
                return r3
            L21:
                r3 = move-exception
                monitor-exit(r0)     // Catch: java.lang.Throwable -> L21
                throw r3
            */
            throw new UnsupportedOperationException("Method not decompiled: com.medm.bluetoothle.BtLETransport.BLEOperationsQueue.waitForRunningOperationFinish(long):boolean");
        }
    }

    private class BLEGattCallback extends BluetoothGattCallback {
        private BLEGattCallback() {
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onPhyRead(BluetoothGatt bluetoothGatt, int i, int i2, int i3) {
            Logger.str("BLEGattCallback::onPhyRead, txPhy = " + Utils.phyToString(i) + ", rxPhy = " + Utils.phyToString(i2) + ", status = " + Utils.statusToString(i3));
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_bleOperationsQueue != null) {
                    BtLETransport.this.m_bleOperationsQueue.onOperationComplete(i3);
                }
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onPhyUpdate(BluetoothGatt bluetoothGatt, int i, int i2, int i3) {
            Logger.str("BLEGattCallback::onPhyUpdate, txPhy = " + Utils.phyToString(i) + ", rxPhy = " + Utils.phyToString(i2) + ", status = " + Utils.statusToString(i3));
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onMtuChanged(BluetoothGatt bluetoothGatt, int i, int i2) {
            Logger.str("BLEGattCallback::onMtuChanged, MTU = " + i + ", status = " + Utils.statusToString(i2));
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_bRequestBigMTU) {
                    BtLETransport.this.m_bleOperationsQueue.onOperationComplete(0);
                }
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onServiceChanged(BluetoothGatt bluetoothGatt) {
            Logger.str("BLEGattCallback::onServiceChanged(): discovering services again");
            super.onServiceChanged(bluetoothGatt);
            synchronized (BtLETransport.this.m_lock) {
                BtLETransport.this.m_bleOperationsQueue.add(BtLETransport.this.new BLEOperationDiscoverServices(bluetoothGatt));
            }
        }

        public void onConnectionUpdated(BluetoothGatt bluetoothGatt, int i, int i2, int i3, int i4) {
            if (i4 == 0) {
                Logger.str("BLEGattCallback::onConnectionUpdated, SUCCESS, parameters: interval = " + (i * 1.25d) + "ms, latency = " + i2 + ", timeout = " + (i3 * 10) + "ms, status = " + Utils.statusToString(i4));
                return;
            }
            Logger.str("BLEGattCallback::onConnectionUpdated, FAILED with status = " + Utils.statusToString(i4) + ", parameters: interval = " + (i * 1.25d) + "ms, latency = " + i2 + ", timeout = " + (i3 * 10) + "ms, status = " + Utils.statusToString(i4));
            BtLETransport.this.m_nConnectionUpdateStatus = i4;
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int i, int i2) {
            Utils.TransportOperationResult transportOperationResult;
            StringBuilder sb = new StringBuilder("BLEGattCallback::onConnectionStateChange, status = ");
            sb.append(Utils.statusToString(i));
            sb.append(", new state = ");
            sb.append(Utils.bluetoothConnectionStateToString(i2));
            sb.append(", address = ");
            sb.append((bluetoothGatt == null || bluetoothGatt.getDevice() == null) ? "unknown" : bluetoothGatt.getDevice().getAddress());
            Logger.str(sb.toString());
            if (BtLETransport.this.m_btErrorMonitor != null) {
                BtErrorMonitor unused = BtLETransport.this.m_btErrorMonitor;
                BtErrorMonitor.onConnectionStateChange(i, i2);
            }
            if (i2 != 2) {
                if (i2 == 0) {
                    Logger.str("Disconnected from GATT server");
                    BtLETransport.this.m_bIsConnected = false;
                    BtLETransport.this.cancelTimeoutTask();
                    BtLETransport.this.cancelDisconnectTimeoutTask();
                    Logger.str("BLE disconnect: notifying threads waiting on m_disconnectionMonitor");
                    if (i == 0) {
                        i = BtLETransport.this.m_nConnectionUpdateStatus;
                    }
                    BtLETransport btLETransport = BtLETransport.this;
                    if (i == 34) {
                        transportOperationResult = Utils.TransportOperationResult.FATAL_ERROR;
                    } else {
                        transportOperationResult = i == 0 ? Utils.TransportOperationResult.OK : Utils.TransportOperationResult.GATT_ERROR;
                    }
                    btLETransport.closeGattAndNotifyDisconnected(transportOperationResult, Integer.toString(i));
                    return;
                }
                Logger.str("ConnectionStateChange: " + Utils.bluetoothConnectionStateToString(i2));
                return;
            }
            if (i == 133 || i == 257) {
                Logger.str("Connection to GATT failed or timed out, disconnecting".concat(i == 133 ? ". Bluetooth restart recommended if your device is turned on and you see this error frequently!" : ""));
                BtLETransport.this.closeGattAndNotifyDisconnected(Utils.TransportOperationResult.GATT_ERROR, Integer.toString(i));
                return;
            }
            if (BtLETransport.this.m_bIsConnected) {
                Logger.str("Device is already connected! Skipping notification");
                return;
            }
            BtLETransport.this.m_bIsConnected = true;
            Logger.str("Connected to GATT server");
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_btGatt == null) {
                    Logger.str("onConnectionStateChange: m_btGatt == null, assigning");
                    BtLETransport.this.m_btGatt = bluetoothGatt;
                }
                if (BtLETransport.this.m_btGatt != null) {
                    if (BtLETransport.this.m_bleOperationsQueue == null) {
                        BtLETransport btLETransport2 = BtLETransport.this;
                        btLETransport2.m_bleOperationsQueue = btLETransport2.new BLEOperationsQueue();
                    }
                    if (BtLETransport.this.m_AskCharacterArray == null) {
                        BtLETransport.this.m_AskCharacterArray = new ArrayList();
                    }
                    if (BtLETransport.this.m_NotifyCharacterArray == null) {
                        BtLETransport.this.m_NotifyCharacterArray = new ArrayList();
                    }
                    if (BtLETransport.this.m_bDelayServicesDiscovery) {
                        Logger.str("Wait for discovering");
                        BtLETransport.this.m_delayServicesDiscoveryRunnable = new Runnable() { // from class: com.medm.bluetoothle.BtLETransport.BLEGattCallback.1
                            @Override // java.lang.Runnable
                            public void run() {
                                if (BtLETransport.this.m_bleOperationsQueue != null) {
                                    BtLETransport.this.m_bleOperationsQueue.add(BtLETransport.this.new BLEOperationDiscoverServices(BtLETransport.this.m_btGatt));
                                }
                            }
                        };
                        BtLETransport.this.mDiscoverHandler.postDelayed(BtLETransport.this.m_delayServicesDiscoveryRunnable, 1500L);
                    } else {
                        BLEOperationsQueue bLEOperationsQueue = BtLETransport.this.m_bleOperationsQueue;
                        BtLETransport btLETransport3 = BtLETransport.this;
                        bLEOperationsQueue.add(btLETransport3.new BLEOperationDiscoverServices(btLETransport3.m_btGatt));
                    }
                }
            }
            BtLETransport.this.scheduleTimeout(600000L);
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int i) {
            if (BtLETransport.this.m_bOnServicesDiscoveredEventPosted) {
                Logger.err("Prevent doubleposting of same onServicesDiscovered() event!");
                return;
            }
            BtLETransport.this.m_bOnServicesDiscoveredEventPosted = true;
            BtLETransport.this.m_btGatt = bluetoothGatt;
            BtLETransport.this.scheduleTimeout(600000L);
            Logger.str("BLEGattCallback::onServicesDiscovered, status = " + i);
            if (i == 0) {
                BtLETransport.this.NotifyCharacteristics(bluetoothGatt);
                BtLETransport.this.onConnected();
            } else {
                Logger.str("BLE onServicesDiscovered received: " + Utils.statusToString(i));
            }
            if (BtLETransport.this.m_btErrorMonitor != null) {
                BtLETransport.this.m_btErrorMonitor.OnOperationResult(i);
            }
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_bleOperationsQueue != null) {
                    BtLETransport.this.m_bleOperationsQueue.onOperationComplete(i);
                }
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onCharacteristicRead(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic, int i) {
            StringBuilder sb = new StringBuilder("BLEGattCallback::onCharacteristicRead, characteristic = ");
            sb.append(bluetoothGattCharacteristic != null ? bluetoothGattCharacteristic.getUuid().toString() : "null");
            sb.append(" status = ");
            sb.append(Utils.statusToString(i));
            Logger.str(sb.toString());
            BtLETransport.this.scheduleTimeout(600000L);
            if (i != 0) {
                Logger.str("[E]BLEGattCallback::onCharacteristicRead: status is not success " + Utils.statusToString(i));
            } else if (bluetoothGattCharacteristic != null && bluetoothGattCharacteristic.getService() != null && bluetoothGattCharacteristic.getService().getUuid() != null && bluetoothGattCharacteristic.getUuid() != null && bluetoothGattCharacteristic.getValue() != null) {
                BtLETransport.this.onKeyValuePair(bluetoothGattCharacteristic.getService().getUuid().toString(), bluetoothGattCharacteristic.getUuid().toString().toUpperCase(), bluetoothGattCharacteristic.getValue(), bluetoothGattCharacteristic.getValue().length);
            }
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_btErrorMonitor != null) {
                    BtLETransport.this.m_btErrorMonitor.OnOperationResult(i);
                }
                if (BtLETransport.this.m_bleOperationsQueue != null) {
                    BtLETransport.this.m_bleOperationsQueue.onOperationComplete(i);
                }
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic, int i) {
            Logger.str("BLEGattCallback::onCharacteristicWrite, characteristic = " + bluetoothGattCharacteristic.getUuid() + " status = " + Utils.statusToString(i));
            BtLETransport.this.scheduleTimeout(600000L);
            if (i != 0) {
                Logger.str("[E]BLEGattCallback::onCharacteristicWrite: status is not success " + Utils.statusToString(i));
            } else {
                super.onCharacteristicWrite(bluetoothGatt, bluetoothGattCharacteristic, i);
            }
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_btErrorMonitor != null) {
                    BtLETransport.this.m_btErrorMonitor.OnOperationResult(i);
                }
                if (BtLETransport.this.m_bleOperationsQueue != null) {
                    if (i == 128) {
                        Logger.str("BLEGattCallback::onCharacteristicWrite: GATT_NO_RESOURCES, skip this operation");
                        BtLETransport.this.m_bleOperationsQueue.onOperationComplete(0);
                    } else {
                        BtLETransport.this.m_bleOperationsQueue.onOperationComplete(i);
                    }
                }
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
            BtLETransport.this.scheduleTimeout(600000L);
            BtLETransport.this.onKeyValuePair(bluetoothGattCharacteristic.getService().getUuid().toString(), bluetoothGattCharacteristic.getUuid().toString(), bluetoothGattCharacteristic.getValue(), bluetoothGattCharacteristic.getValue().length);
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onDescriptorRead(BluetoothGatt bluetoothGatt, final BluetoothGattDescriptor bluetoothGattDescriptor, int i) {
            Logger.str("BLEGattCallback::onDescriptorRead, characteristic = " + bluetoothGattDescriptor.getCharacteristic().getUuid() + " status = " + Utils.statusToString(i) + ", value = " + Logger.bytesToHexNoSpaces(bluetoothGattDescriptor.getValue()));
            BtLETransport.this.scheduleTimeout(600000L);
            if (i != 0) {
                Logger.str("[E]BLEGattCallback::onDescriptorRead: status is not success: " + Utils.statusToString(i));
                return;
            }
            super.onDescriptorRead(bluetoothGatt, bluetoothGattDescriptor, i);
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_btErrorMonitor != null) {
                    BtLETransport.this.m_btErrorMonitor.OnOperationResult(i);
                }
            }
            if (BtLETransport.this.m_BLECallbackExecutor != null) {
                BtLETransport.this.m_BLECallbackExecutor.submit(new Runnable() { // from class: com.medm.bluetoothle.BtLETransport.BLEGattCallback.2
                    @Override // java.lang.Runnable
                    public void run() {
                        long j;
                        Logger.str("BLE onDescriptorRead (executing): strDescriptorUUID = " + bluetoothGattDescriptor.getUuid() + ", strCharUUID = " + bluetoothGattDescriptor.getCharacteristic().getUuid());
                        synchronized (BtLETransport.this.m_listenerLock) {
                            j = BtLETransport.this.m_nListenerPtr;
                        }
                        if (j != 0) {
                            BtLETransport.onDescriptorValue(j, bluetoothGattDescriptor.getUuid().toString(), bluetoothGattDescriptor.getValue(), bluetoothGattDescriptor.getValue().length);
                        }
                    }
                });
            }
            if (BtLETransport.this.m_bleOperationsQueue != null) {
                BtLETransport.this.m_bleOperationsQueue.onOperationComplete(i);
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onDescriptorWrite(BluetoothGatt bluetoothGatt, BluetoothGattDescriptor bluetoothGattDescriptor, int i) {
            Logger.str("BLEGattCallback::onDescriptorWrite, characteristic = " + bluetoothGattDescriptor.getCharacteristic().getUuid() + " status = " + Utils.statusToString(i));
            BtLETransport.this.scheduleTimeout(600000L);
            if (i != 0) {
                Logger.str("[E]BLEGattCallback::onDescriptorWrite: status is not success " + Utils.statusToString(i));
            } else {
                super.onDescriptorWrite(bluetoothGatt, bluetoothGattDescriptor, i);
            }
            synchronized (BtLETransport.this.m_lock) {
                if (BtLETransport.this.m_btErrorMonitor != null) {
                    BtLETransport.this.m_btErrorMonitor.OnOperationResult(i);
                }
                if (BtLETransport.this.m_bleOperationsQueue != null) {
                    BtLETransport.this.m_bleOperationsQueue.onOperationComplete(i);
                }
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onReliableWriteCompleted(BluetoothGatt bluetoothGatt, int i) {
            Logger.str("BLEGattCallback::onReliableWriteCompleted, status = " + Utils.statusToString(i));
            BtLETransport.this.scheduleTimeout(600000L);
            if (i != 0) {
                Logger.str("[E]BLEGattCallback::onReliableWriteCompleted: status is not success: " + Utils.statusToString(i) + ", close gatt");
                BtLETransport.this.closeGattAndNotifyDisconnected(Utils.TransportOperationResult.GATT_ERROR, Integer.toString(i));
            } else {
                super.onReliableWriteCompleted(bluetoothGatt, i);
            }
            if (BtLETransport.this.m_btErrorMonitor != null) {
                BtLETransport.this.m_btErrorMonitor.OnOperationResult(i);
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onReadRemoteRssi(BluetoothGatt bluetoothGatt, int i, int i2) {
            Logger.str("BLEGattCallback::onReadRemoteRssi, RSSI =" + i);
            BtLETransport.this.scheduleTimeout(600000L);
            if (i2 != 0) {
                Logger.str("[E]BLEGattCallback::onReadRemoteRssi: status is not success: " + Utils.statusToString(i2) + ", close gatt");
                BtLETransport.this.closeGattAndNotifyDisconnected(Utils.TransportOperationResult.GATT_ERROR, Integer.toString(i2));
            } else {
                super.onReadRemoteRssi(bluetoothGatt, i, i2);
            }
            if (BtLETransport.this.m_btErrorMonitor != null) {
                BtLETransport.this.m_btErrorMonitor.OnOperationResult(i2);
            }
        }
    }

    private class PairingBroadcastReceiver extends BroadcastReceiver {
        boolean mReceived;

        private PairingBroadcastReceiver() {
            this.mReceived = false;
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String str;
            if ("android.bluetooth.device.action.BOND_STATE_CHANGED".equals(intent.getAction())) {
                BluetoothDevice bluetoothDevice = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                if (bluetoothDevice != null) {
                    str = bluetoothDevice.getName() + " (" + bluetoothDevice.getAddress() + ")";
                } else {
                    str = "null";
                }
                int intExtra = intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", 10);
                int intExtra2 = intent.getIntExtra("android.bluetooth.device.extra.PREVIOUS_BOND_STATE", 10);
                Logger.str("PairingBroadcastReceiver::ACTION_BOND_STATE_CHANGED for " + str + ", oldState = " + Utils.bondStateToString(intExtra2) + ", newState = " + Utils.bondStateToString(intExtra));
                this.mReceived = true;
                if (intExtra != 10) {
                    if (intExtra != 12) {
                        return;
                    }
                    BtLETransport.this.m_Context.unregisterReceiver(this);
                    BtLETransport.this.onPair(Utils.TransportOperationResult.OK);
                    return;
                }
                if (intExtra2 == 11) {
                    Logger.str("[E]Bond fail for " + str);
                    BtLETransport.this.m_Context.unregisterReceiver(this);
                    BtLETransport.this.onPair(Utils.TransportOperationResult.ERROR);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onUnpair() {
        long j;
        synchronized (this.m_listenerLock) {
            j = this.m_nListenerPtr;
        }
        if (j != 0) {
            onUnpair(j);
        }
        BtMan.unregisterBtActivity(this);
    }
}