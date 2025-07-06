package com.medm.bluetoothle;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import com.medm.bluetooth.BtMan;
import com.medm.logger.Logger;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* loaded from: classes3.dex */
public class BtLEServer {
    private volatile Context m_context;
    private static final UUID CURRENT_TIME_SERVICE = UUID.fromString("00001805-0000-1000-8000-00805F9B34FB");
    private static final UUID CURRENT_TIME_CHARACTERISTIC = UUID.fromString("00002A2B-0000-1000-8000-00805F9B34FB");
    private static final UUID LOCAL_TIME_INFO_CHARACTERISTIC = UUID.fromString("00002A0F-0000-1000-8000-00805F9B34FB");
    private static final UUID REFERENCE_TIME_INFO_CHARACTERISTIC = UUID.fromString("00002A14-0000-1000-8000-00805F9B34FB");
    private static final UUID GENERIC_ATTRIBUTE_SERVICE = UUID.fromString("00001801-0000-1000-8000-00805F9B34FB");
    private static final UUID GENERIC_ACCESS_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805F9B34FB");
    private static final UUID GENERIC_ACCESS_NAME_CHARACTERISTIC = UUID.fromString("00002A00-0000-1000-8000-00805F9B34FB");
    private static final UUID GENERIC_ACCESS_APPEARANCE_CHARACTERISTIC = UUID.fromString("00002A01-0000-1000-8000-00805F9B34FB");
    private static BtLEServer instance = null;
    private ExecutorService m_ServerExecutor = Executors.newSingleThreadExecutor();
    private GattServerCallback m_gattServerCallback = null;
    private BluetoothGattServer m_gattServer = null;

    public static BtLEServer getInstance() {
        return instance;
    }

    public static void create(Context context) {
        Logger.str("BLEServer::create()");
        if (instance == null) {
            BtLEServer btLEServer = new BtLEServer(context);
            instance = btLEServer;
            BtMan.registerBtActivity(btLEServer);
        }
    }

    private BtLEServer(Context context) {
        this.m_context = null;
        Logger.str("BLEServer::BLEServer()");
        this.m_context = context;
        BtMan.TurnBluetooth(true, false, new BtMan.Listener() { // from class: com.medm.bluetoothle.BtLEServer.1
            @Override // com.medm.bluetooth.BtMan.Listener
            public void onBluetoothTurnedOn() {
                Logger.str("BLEServer::BtMan.Listener::onBluetoothTurnedOn()");
                BtMan.unregisterListener(this);
                BtLEServer.this.m_ServerExecutor.submit(new Runnable() { // from class: com.medm.bluetoothle.BtLEServer.1.1
                    @Override // java.lang.Runnable
                    public void run() {
                        Logger.str("BLEServer::BtMan.Listener::onBluetoothTurnedOn() initialize server");
                        BtLEServer.this.initialize();
                    }
                });
            }

            @Override // com.medm.bluetooth.BtMan.Listener
            public void onTimeout() {
                Logger.str("BLEServer::BtMan.Listener::onTimeout()");
                BtMan.unregisterListener(this);
            }

            @Override // com.medm.bluetooth.BtMan.Listener
            public void onError() {
                Logger.str("BLEServer::BtMan.Listener::onError()");
                BtMan.unregisterListener(this);
            }
        });
    }

    public void initialize() {
        Logger.str("BLEServer::initialize()");
        BluetoothManager bluetoothManager = (BluetoothManager) this.m_context.getSystemService("bluetooth");
        if (bluetoothManager == null) {
            Logger.str("No BluetoothManager");
            return;
        }
        this.m_gattServerCallback = new GattServerCallback();
        BluetoothGattServer bluetoothGattServerOpenGattServer = bluetoothManager.openGattServer(this.m_context, this.m_gattServerCallback);
        this.m_gattServer = bluetoothGattServerOpenGattServer;
        if (bluetoothGattServerOpenGattServer == null) {
            Logger.str("[E] Error opening gatt server");
            terminate();
        } else {
            Logger.str("Init services");
            InitServices();
        }
    }

    public static void terminate() {
        if (getInstance() == null) {
            return;
        }
        if (getInstance().m_gattServerCallback != null) {
            getInstance().m_gattServerCallback = null;
        }
        if (getInstance().m_gattServer != null) {
            getInstance().m_gattServer.close();
            getInstance().m_gattServer = null;
        }
        instance = null;
    }

    private void InitServices() {
        BluetoothGattCharacteristic bluetoothGattCharacteristic = new BluetoothGattCharacteristic(CURRENT_TIME_CHARACTERISTIC, 2, 1);
        BluetoothGattCharacteristic bluetoothGattCharacteristic2 = new BluetoothGattCharacteristic(LOCAL_TIME_INFO_CHARACTERISTIC, 2, 1);
        BluetoothGattCharacteristic bluetoothGattCharacteristic3 = new BluetoothGattCharacteristic(REFERENCE_TIME_INFO_CHARACTERISTIC, 2, 1);
        BluetoothGattServer bluetoothGattServer = this.m_gattServer;
        UUID uuid = CURRENT_TIME_SERVICE;
        BluetoothGattService service = bluetoothGattServer.getService(uuid);
        if (service != null) {
            this.m_gattServer.removeService(service);
        }
        BluetoothGattService bluetoothGattService = new BluetoothGattService(uuid, 0);
        bluetoothGattService.addCharacteristic(bluetoothGattCharacteristic);
        bluetoothGattService.addCharacteristic(bluetoothGattCharacteristic2);
        bluetoothGattService.addCharacteristic(bluetoothGattCharacteristic3);
        this.m_gattServer.addService(bluetoothGattService);
    }

    private class GattServerCallback extends BluetoothGattServerCallback {
        private GattServerCallback() {
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onConnectionStateChange(BluetoothDevice bluetoothDevice, int i, int i2) {
            super.onConnectionStateChange(bluetoothDevice, i, i2);
            Logger.str("BLEServer::GattServerCallback::onConnectionStateChange() with device address " + bluetoothDevice.getAddress() + " and name \"" + bluetoothDevice.getName() + "\"; status = " + i + "; new state = " + i2);
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onServiceAdded(int i, BluetoothGattService bluetoothGattService) {
            super.onServiceAdded(i, bluetoothGattService);
            Logger.str("BLEServer::GattServerCallback::onServiceAdded() " + bluetoothGattService.getUuid() + "; status = " + i);
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onCharacteristicReadRequest(BluetoothDevice bluetoothDevice, int i, int i2, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
            super.onCharacteristicReadRequest(bluetoothDevice, i, i2, bluetoothGattCharacteristic);
            Logger.str("BLEServer::GattServerCallback::onCharacteristicReadRequest() from device with address " + bluetoothDevice.getAddress() + " and name \"" + bluetoothDevice.getName() + "\"; requestID = " + i + "; offset = " + i2 + "; charac = " + bluetoothGattCharacteristic.getUuid());
            if (bluetoothGattCharacteristic.getUuid().equals(UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb"))) {
                Logger.str("BLEServer::GattServerCallback current time requested");
                Calendar calendar = Calendar.getInstance();
                byte[] bArrArray = ByteBuffer.allocate(10).array();
                bArrArray[0] = (byte) (calendar.get(1) % 256);
                bArrArray[1] = (byte) (calendar.get(1) / 256);
                bArrArray[2] = (byte) (calendar.get(2) + 1);
                bArrArray[3] = (byte) calendar.get(5);
                bArrArray[4] = (byte) calendar.get(11);
                bArrArray[5] = (byte) calendar.get(12);
                bArrArray[6] = (byte) calendar.get(13);
                bArrArray[7] = (byte) calendar.get(7);
                bArrArray[8] = 0;
                bArrArray[9] = 0;
                Logger.str("BLEServer::GattServerCallback answer with time " + calendar.get(5) + "." + (calendar.get(2) + 1) + "." + calendar.get(1) + " " + calendar.get(11) + ":" + calendar.get(12));
                BtLEServer.this.m_gattServer.sendResponse(bluetoothDevice, i, 0, i2, bArrArray);
            }
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onCharacteristicWriteRequest(BluetoothDevice bluetoothDevice, int i, BluetoothGattCharacteristic bluetoothGattCharacteristic, boolean z, boolean z2, int i2, byte[] bArr) {
            super.onCharacteristicWriteRequest(bluetoothDevice, i, bluetoothGattCharacteristic, z, z2, i2, bArr);
            Logger.str("BLEServer::GattServerCallback::onCharacteristicWriteRequest() from device with address " + bluetoothDevice.getAddress() + " and name \"" + bluetoothDevice.getName() + "\"; requestID = " + i + "; offset = " + i2 + "; charac = " + bluetoothGattCharacteristic.getUuid());
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onDescriptorReadRequest(BluetoothDevice bluetoothDevice, int i, int i2, BluetoothGattDescriptor bluetoothGattDescriptor) {
            super.onDescriptorReadRequest(bluetoothDevice, i, i2, bluetoothGattDescriptor);
            Logger.str("BLEServer::GattServerCallback::onDescriptorReadRequest()");
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onDescriptorWriteRequest(BluetoothDevice bluetoothDevice, int i, BluetoothGattDescriptor bluetoothGattDescriptor, boolean z, boolean z2, int i2, byte[] bArr) {
            super.onDescriptorWriteRequest(bluetoothDevice, i, bluetoothGattDescriptor, z, z2, i2, bArr);
            Logger.str("BLEServer::GattServerCallback::onDescriptorWriteRequest()");
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onExecuteWrite(BluetoothDevice bluetoothDevice, int i, boolean z) {
            super.onExecuteWrite(bluetoothDevice, i, z);
            Logger.str("BLEServer::GattServerCallback::onExecuteWrite()");
        }
    }
}