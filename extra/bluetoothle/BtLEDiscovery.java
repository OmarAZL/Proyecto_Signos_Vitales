package com.medm.bluetoothle;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.SparseArray;
import com.google.common.base.Ascii;
import com.medm.bluetooth.BtMan;
import com.medm.logger.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes3.dex */
public class BtLEDiscovery {
    public static final int ADV_128_BIT_UUID = 6;
    public static final int ADV_128_BIT_UUID_FINISH = 7;
    public static final int ADV_16_BIT_UUID = 2;
    public static final int ADV_16_BIT_UUID_FINISH = 3;
    public static final int ADV_32_BIT_UUID = 4;
    public static final int ADV_32_BIT_UUID_FINISH = 5;
    public static final int ADV_DATA_FLAG = 1;
    public static final int BLUETOOTH_TURN_ON_TIMEOUT = 15000;
    public static final int DEFAULT_LENGTH_MILLISECONDS = 6000;
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static final int LIMITED_AND_GENERAL_DISC_MASK = 3;
    private static ScheduledThreadPoolExecutor[] executors;
    private static BtLEDiscovery[] m_instances;
    private BluetoothAdapter mBluetoothAdapter;
    private GenericScanCallback mGenericCallback;
    private int mIndex;
    private String[] m_addressesToFind;
    private final Context m_context;
    private ParcelUuid[] m_servicesToFind;
    private long m_nNativeDiscoveryPtr = 0;
    private ScheduledFuture m_discoveryTimeoutTaskFuture = null;
    private ScheduledFuture m_bluetoothTurnOnTimeoutTaskFuture = null;
    private final Object m_timeoutTaskLock = new Object();
    private final HashMap<String, String[]> m_servicesCache = new HashMap<>();
    private ExecutorService m_BLECallbackExecutor = null;
    private HashMap<String, Integer> m_logFilterMap = null;
    private int m_nLengthMillisecs = 0;
    private final Object m_bleCallbackExecutorLock = new Object();
    private final Object lock = new Object();
    private BtMan.Listener m_btManListener = new BtMan.Listener() { // from class: com.medm.bluetoothle.BtLEDiscovery.1
        @Override // com.medm.bluetooth.BtMan.Listener
        public void onBluetoothTurnedOn() {
            Logger.str("BtLeDiscovery(" + BtLEDiscovery.this.mIndex + ")::BtMan.Listener::onBluetoothTurnedOn()");
            BtMan.unregisterListener(this);
            BtLEDiscovery.executors[BtLEDiscovery.this.mIndex].execute(new Runnable() { // from class: com.medm.bluetoothle.BtLEDiscovery.1.1
                @Override // java.lang.Runnable
                public void run() {
                    BtLEDiscovery.this.StartDiscovery();
                }
            });
        }

        @Override // com.medm.bluetooth.BtMan.Listener
        public void onError() {
            Logger.str("BtLeDiscovery(" + BtLEDiscovery.this.mIndex + ")::BtMan.Listener::onError()");
            BtLEDiscovery.this.stopScan();
            BtMan.unregisterListener(this);
            BtLEDiscovery.this.onDiscoveryFinished();
        }

        @Override // com.medm.bluetooth.BtMan.Listener
        public void onTimeout() {
            Logger.str("BtLeDiscovery(" + BtLEDiscovery.this.mIndex + ")::BtMan.Listener::onTimeout()");
            BtLEDiscovery.this.stopScan();
            BtMan.unregisterListener(this);
            BtLEDiscovery.this.onDiscoveryFinished();
        }
    };

    public static native boolean isKnownDevice(String str);

    public static native void onDeviceFound(long j, String str, String str2, int i, String[] strArr, byte[] bArr, String str3, boolean z);

    public static native void onDiscoveryFinished(long j);

    public static BtLEDiscovery create(Context context, int i, int i2) {
        Logger.str("BtLEDiscovery::create(" + i + ", " + i2 + ")");
        if (i > 1) {
            return null;
        }
        if (m_instances == null && BtLEManager.isBLEAvailable()) {
            m_instances = new BtLEDiscovery[i2];
            executors = new ScheduledThreadPoolExecutor[i2];
        }
        BtLEDiscovery[] btLEDiscoveryArr = m_instances;
        if (btLEDiscoveryArr[i] == null) {
            btLEDiscoveryArr[i] = new BtLEDiscovery(context, i);
            executors[i] = new ScheduledThreadPoolExecutor(1);
        }
        return m_instances[i];
    }

    public BtLEDiscovery(Context context, int i) {
        this.mBluetoothAdapter = null;
        this.m_context = context;
        this.mIndex = i;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService("bluetooth");
        if (bluetoothManager != null) {
            BluetoothAdapter adapter = bluetoothManager.getAdapter();
            this.mBluetoothAdapter = adapter;
            if (adapter == null) {
                Logger.str("No BluetoothAdapter");
            }
        } else {
            Logger.str("No BluetoothManager");
        }
        this.mGenericCallback = new GenericScanCallback(new BLEScanCallback());
    }

    public synchronized boolean start(long j, int i, String[] strArr, String[] strArr2) {
        String str;
        boolean z = true;
        if (this.m_discoveryTimeoutTaskFuture != null) {
            Logger.str("Discovery is already in progress, return");
            return true;
        }
        BtMan.registerBtActivity(this);
        if (this.m_logFilterMap != null) {
            this.m_logFilterMap = null;
        }
        this.m_logFilterMap = new HashMap<>();
        this.m_nNativeDiscoveryPtr = j;
        switch (this.mBluetoothAdapter.getState()) {
            case 10:
                str = "STATE_OFF";
                break;
            case 11:
                str = "STATE_TURNING_ON";
                break;
            case 12:
                str = "STATE_ON";
                break;
            case 13:
                str = "STATE_TURNING_OFF";
                break;
            default:
                str = "Unknown";
                break;
        }
        if (i != 0) {
            this.m_nLengthMillisecs = i;
        } else {
            this.m_nLengthMillisecs = DEFAULT_LENGTH_MILLISECONDS;
        }
        Logger.str("BtLeDiscovery(" + this.mIndex + "::Start: Bluetooth Adapter State = " + str + "; Length = " + this.m_nLengthMillisecs);
        this.m_addressesToFind = strArr;
        StringBuilder sb = new StringBuilder("BtLeDiscovery(");
        sb.append(this.mIndex);
        sb.append("::start() Addresses to find: ");
        StringBuilder sb2 = new StringBuilder(sb.toString());
        for (String str2 : strArr) {
            sb2.append(str2);
            sb2.append(", ");
        }
        Logger.str(sb2.toString());
        this.m_servicesToFind = new ParcelUuid[strArr2.length];
        StringBuilder sb3 = new StringBuilder("BtLeDiscovery(" + this.mIndex + "::start() Services to find: ");
        for (int i2 = 0; i2 < strArr2.length; i2++) {
            sb3.append(strArr2[i2]);
            sb3.append(", ");
            this.m_servicesToFind[i2] = ParcelUuid.fromString(strArr2[i2]);
        }
        Logger.str(sb3.toString());
        synchronized (this.m_timeoutTaskLock) {
            this.m_bluetoothTurnOnTimeoutTaskFuture = executors[this.mIndex].schedule(new Runnable() { // from class: com.medm.bluetoothle.BtLEDiscovery.2
                @Override // java.lang.Runnable
                public void run() {
                    boolean z2;
                    Logger.str("BtLeDiscovery(" + BtLEDiscovery.this.mIndex + ": m_bluetoothTurnOnTimeoutTaskFuture runnable called");
                    synchronized (BtLEDiscovery.this.m_timeoutTaskLock) {
                        if (BtLEDiscovery.this.m_bluetoothTurnOnTimeoutTaskFuture != null) {
                            BtLEDiscovery.this.m_bluetoothTurnOnTimeoutTaskFuture = null;
                            Logger.str("BtLeDiscovery(" + BtLEDiscovery.this.mIndex + ": m_bluetoothTurnOnTimeoutTaskFuture nulled");
                            z2 = true;
                        } else {
                            z2 = false;
                        }
                    }
                    if (z2) {
                        BtLEDiscovery.this.stopScan();
                        BtLEDiscovery.this.onDiscoveryFinished();
                    }
                }
            }, 15000L, TimeUnit.MILLISECONDS);
            Logger.str("BtLeDiscovery(" + this.mIndex + ": m_bluetoothTurnOnTimeoutTaskFuture assigned");
        }
        try {
            BtMan.TurnBluetooth(true, false, this.m_btManListener);
        } catch (NullPointerException e) {
            Logger.err("NPE exception during starting bluetooth", e);
            BtMan.unregisterListener(this.m_btManListener);
            cancelTimeoutTasks();
            z = false;
        }
        return z;
    }

    public synchronized void stop() {
        Logger.str("BtLeDiscovery(" + this.mIndex + "::stop()");
        stopScan();
        onDiscoveryFinished();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void stopScan() {
        Logger.str("BtLeDiscovery(" + this.mIndex + "::stopScan()");
        cancelTimeoutTasks();
        BtMan.unregisterListener(this.m_btManListener);
        Logger.str("BtLeDiscovery(" + this.mIndex + "::clear scan objects");
        if (this.mBluetoothAdapter != null && this.mBluetoothAdapter.getBluetoothLeScanner() != null) {
            this.mBluetoothAdapter.getBluetoothLeScanner().stopScan((ScanCallback) this.mGenericCallback.getCallback());
        }
        synchronized (this.m_bleCallbackExecutorLock) {
            ExecutorService executorService = this.m_BLECallbackExecutor;
            if (executorService != null) {
                executorService.shutdownNow();
                this.m_BLECallbackExecutor = null;
            }
        }
        BtMan.unregisterBtActivity(this);
    }

    private void cancelTimeoutTasks() {
        synchronized (this.m_timeoutTaskLock) {
            ScheduledFuture scheduledFuture = this.m_discoveryTimeoutTaskFuture;
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
                this.m_discoveryTimeoutTaskFuture = null;
                Logger.str("BtLeDiscovery(" + this.mIndex + ": m_discoveryTimeoutTaskFuture nulled");
                executors[this.mIndex].purge();
            }
            ScheduledFuture scheduledFuture2 = this.m_bluetoothTurnOnTimeoutTaskFuture;
            if (scheduledFuture2 != null) {
                scheduledFuture2.cancel(false);
                this.m_bluetoothTurnOnTimeoutTaskFuture = null;
                Logger.str("BtLeDiscovery(" + this.mIndex + ": m_bluetoothTurnOnTimeoutTaskFuture nulled");
                executors[this.mIndex].purge();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onDeviceFound(String str, String str2, int i, byte[] bArr, String str3, boolean z) {
        if (str == null || str.isEmpty()) {
            Logger.str("BLEDiscovery: onDeviceFound: no device");
            return;
        }
        if (!this.m_logFilterMap.containsKey(str)) {
            Logger.str("BtLeDiscovery(" + this.mIndex + "): onDeviceFound: " + str2 + "(" + str + "); rssi = " + i);
            this.m_logFilterMap.put(str, 0);
        }
        if (this.m_nNativeDiscoveryPtr != 0) {
            synchronized (this.m_servicesCache) {
                onDeviceFound(this.m_nNativeDiscoveryPtr, str2, str, i, this.m_servicesCache.get(str), bArr, str3, z);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onDiscoveryFinished() {
        Logger.str("BtLeDiscovery(" + this.mIndex + "): onDiscoveryFinished, m_nNativeDiscoveryPtr = " + this.m_nNativeDiscoveryPtr);
        long j = this.m_nNativeDiscoveryPtr;
        if (j != 0) {
            this.m_nNativeDiscoveryPtr = 0L;
            onDiscoveryFinished(j);
        }
        BtMan.unregisterBtActivity(this);
    }

    private ExecutorService GetCallbackExecutor() {
        synchronized (this.m_bleCallbackExecutorLock) {
            if (this.m_nNativeDiscoveryPtr == 0) {
                return null;
            }
            if (this.m_BLECallbackExecutor == null) {
                this.m_BLECallbackExecutor = Executors.newSingleThreadExecutor();
            }
            return this.m_BLECallbackExecutor;
        }
    }

    private class GenericScanCallback<T> {
        T m_callback;

        GenericScanCallback(T t) {
            this.m_callback = t;
        }

        T getCallback() {
            return this.m_callback;
        }
    }

    static String bytesToHex(byte[] bArr) {
        char[] cArr = new char[bArr.length * 2];
        for (int i = 0; i < bArr.length; i++) {
            byte b = bArr[i];
            int i2 = i * 2;
            char[] cArr2 = HEX_ARRAY;
            cArr[i2] = cArr2[(b & 255) >>> 4];
            cArr[i2 + 1] = cArr2[b & Ascii.SI];
        }
        return new String(cArr);
    }

    private class BLEScanCallback extends ScanCallback {
        private BLEScanCallback() {
        }

        @Override // android.bluetooth.le.ScanCallback
        public void onScanResult(int i, ScanResult scanResult) throws JSONException {
            byte[] bArrValueAt;
            SparseArray<byte[]> manufacturerSpecificData = scanResult.getScanRecord().getManufacturerSpecificData();
            if (manufacturerSpecificData == null || manufacturerSpecificData.size() <= 0) {
                bArrValueAt = null;
            } else {
                try {
                    bArrValueAt = manufacturerSpecificData.valueAt(0);
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
            List<ParcelUuid> serviceUuids = scanResult.getScanRecord().getServiceUuids();
            JSONObject jSONObject = new JSONObject();
            Map<ParcelUuid, byte[]> serviceData = scanResult.getScanRecord().getServiceData();
            if (serviceData != null) {
                for (ParcelUuid parcelUuid : (ParcelUuid[]) serviceData.keySet().toArray(new ParcelUuid[0])) {
                    try {
                        jSONObject.put(parcelUuid.toString(), BtLEDiscovery.bytesToHex(serviceData.get(parcelUuid)));
                    } catch (JSONException e2) {
                        throw new RuntimeException(e2);
                    }
                }
            }
            BtLEDiscovery.this.saveServices(scanResult.getDevice(), serviceUuids);
            BtLEDiscovery.this.onBLEScanResult(scanResult.getDevice(), scanResult.getRssi(), bArrValueAt, jSONObject.toString());
        }

        @Override // android.bluetooth.le.ScanCallback
        public void onScanFailed(int i) {
            Logger.str("BluetoothAdapter::ScanCallback::onScanFailed(" + i + ")");
            BtLEDiscovery.this.stopScan();
            BtLEDiscovery.this.onDiscoveryFinished();
        }
    }

    private class BLEGattCallback implements BluetoothAdapter.LeScanCallback {
        private BLEGattCallback() {
        }

        @Override // android.bluetooth.BluetoothAdapter.LeScanCallback
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bArr) {
            BtLEDiscovery.this.extractServices(bluetoothDevice, bArr);
            BtLEDiscovery.this.onBLEScanResult(bluetoothDevice, i, null, null);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Type inference failed for: r1v0, types: [com.medm.bluetoothle.BtLEDiscovery$3] */
    public void onBLEScanResult(BluetoothDevice bluetoothDevice, int i, final byte[] bArr, String str) {
        ExecutorService executorServiceGetCallbackExecutor = GetCallbackExecutor();
        if (executorServiceGetCallbackExecutor != null) {
            try {
                executorServiceGetCallbackExecutor.submit(new Runnable() { // from class: com.medm.bluetoothle.BtLEDiscovery.3
                    int m_Rssi;
                    byte[] m_manufacturerData;
                    String m_address = null;
                    String m_name = null;
                    boolean m_isDual = false;
                    String m_servicesDataJson = null;

                    public Runnable init(BluetoothDevice bluetoothDevice2, int i2, byte[] bArr2, String str2) {
                        if (bluetoothDevice2 != null) {
                            this.m_address = bluetoothDevice2.getAddress();
                            this.m_name = bluetoothDevice2.getName();
                            this.m_isDual = bluetoothDevice2.getType() == 3;
                        }
                        this.m_Rssi = i2;
                        this.m_manufacturerData = bArr2;
                        this.m_servicesDataJson = str2;
                        return this;
                    }

                    @Override // java.lang.Runnable
                    public void run() {
                        BtLEDiscovery.this.onDeviceFound(this.m_address, this.m_name, this.m_Rssi, bArr, this.m_servicesDataJson, this.m_isDual);
                    }
                }.init(bluetoothDevice, i, bArr, str));
            } catch (RejectedExecutionException e) {
                Logger.warn("Exception during adding new task to executor with description " + e.getMessage());
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void saveServices(BluetoothDevice bluetoothDevice, List<ParcelUuid> list) {
        if (list == null || list.size() <= 0) {
            return;
        }
        String[] strArr = new String[list.size()];
        Iterator<ParcelUuid> it = list.iterator();
        int i = 0;
        while (it.hasNext()) {
            strArr[i] = it.next().toString();
            i++;
        }
        synchronized (this.m_servicesCache) {
            this.m_servicesCache.put(bluetoothDevice.getAddress(), strArr);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void extractServices(BluetoothDevice bluetoothDevice, byte[] bArr) {
        int i;
        if (bluetoothDevice == null || bArr == null || bArr.length == 0) {
            return;
        }
        ArrayList arrayList = null;
        int i2 = 0;
        while (i2 < bArr.length - 2 && (i = bArr[i2] & 255) != 0) {
            int i3 = bArr[i2 + 1] & 255;
            int i4 = i2 + 2;
            long j = 0;
            if (i3 == 2 || i3 == 3) {
                int i5 = (i - 1) / 2;
                if (i5 > 0) {
                    if (arrayList == null) {
                        arrayList = new ArrayList();
                    }
                    for (int i6 = 0; i6 < i5; i6++) {
                        arrayList.add(new UUID((bArr[i4] | (bArr[i4 + 1] << 8)) << 32, 0L).toString());
                        i4 += 2;
                    }
                }
            } else if ((i3 == 6 || i3 == 7) && i == 17) {
                if (arrayList == null) {
                    arrayList = new ArrayList();
                }
                long j2 = 0;
                int i7 = 0;
                while (i7 < 8) {
                    j2 |= (bArr[i4 + i7] & 255) << (i7 * 8);
                    i7++;
                }
                while (i7 < 16) {
                    j |= (bArr[i4 + i7] & 255) << ((i7 - 8) * 8);
                    i7++;
                }
                arrayList.add(new UUID(j, j2).toString());
            }
            i2 += i + 1;
        }
        if (arrayList == null || arrayList.size() <= 0) {
            return;
        }
        synchronized (this.m_servicesCache) {
            this.m_servicesCache.put(bluetoothDevice.getAddress(), (String[]) arrayList.toArray(new String[arrayList.size()]));
        }
    }

    public static boolean CheckIfBroadcastMode(byte[] bArr) {
        int i = 0;
        while (i < bArr.length - 2) {
            int i2 = i + 1;
            int i3 = bArr[i] & 255;
            if (i3 == 0) {
                return false;
            }
            i += 2;
            if (bArr[i2] != 1) {
                i += i3 - 1;
            } else if (i3 > 1) {
                return (bArr[i] & 3) == 0;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void StartDiscovery() {
        Logger.str("BtLeDiscovery(" + this.mIndex + "): StartDiscovery()");
        cancelTimeoutTasks();
        if (this.m_nNativeDiscoveryPtr == 0) {
            Logger.str("[E]m_nNativeDiscoveryPtr is null, no reason to start discovery");
            return;
        }
        this.mGenericCallback = new GenericScanCallback(new BLEScanCallback());
        if (this.mBluetoothAdapter == null) {
            Logger.str("[E]mBluetoothAdapter is null, get it");
            BluetoothManager bluetoothManager = (BluetoothManager) this.m_context.getSystemService("bluetooth");
            if (bluetoothManager != null) {
                BluetoothAdapter adapter = bluetoothManager.getAdapter();
                this.mBluetoothAdapter = adapter;
                if (adapter == null) {
                    Logger.str("[E]mBluetoothAdapter is null again, return");
                    return;
                }
            } else {
                Logger.str("[E]mBluetoothManager is null, return");
                return;
            }
        }
        ScanSettings scanSettingsBuild = new ScanSettings.Builder().setScanMode(2).build();
        LinkedList linkedList = new LinkedList();
        for (String str : this.m_addressesToFind) {
            try {
                linkedList.push(new ScanFilter.Builder().setDeviceAddress(str).build());
            } catch (IllegalArgumentException unused) {
                Logger.str("[E] Invalid address: " + str);
            }
        }
        for (ParcelUuid parcelUuid : this.m_servicesToFind) {
            try {
                linkedList.push(new ScanFilter.Builder().setServiceUuid(parcelUuid).build());
            } catch (IllegalArgumentException unused2) {
                Logger.str("[E] Invalid service: " + parcelUuid);
            }
        }
        if (linkedList.isEmpty()) {
            this.mBluetoothAdapter.getBluetoothLeScanner().startScan((ScanCallback) this.mGenericCallback.getCallback());
        } else {
            this.mBluetoothAdapter.getBluetoothLeScanner().startScan(linkedList, scanSettingsBuild, (ScanCallback) this.mGenericCallback.getCallback());
        }
        synchronized (this.m_timeoutTaskLock) {
            this.m_discoveryTimeoutTaskFuture = executors[this.mIndex].schedule(new Runnable() { // from class: com.medm.bluetoothle.BtLEDiscovery.4
                @Override // java.lang.Runnable
                public void run() {
                    boolean z;
                    Logger.str("BtLeDiscovery(" + BtLEDiscovery.this.mIndex + ": m_discoveryTimeoutTaskFuture runnable called");
                    synchronized (BtLEDiscovery.this.m_timeoutTaskLock) {
                        if (BtLEDiscovery.this.m_discoveryTimeoutTaskFuture != null) {
                            BtLEDiscovery.this.m_discoveryTimeoutTaskFuture = null;
                            Logger.str("BtLeDiscovery(" + BtLEDiscovery.this.mIndex + ": m_discoveryTimeoutTaskFuture nulled");
                            z = true;
                        } else {
                            z = false;
                        }
                    }
                    if (z) {
                        BtLEDiscovery.this.stopScan();
                        BtLEDiscovery.this.onDiscoveryFinished();
                    }
                }
            }, this.m_nLengthMillisecs, TimeUnit.MILLISECONDS);
            Logger.str("BtLeDiscovery(" + this.mIndex + "): m_discoveryTimeoutTaskFuture assigned");
        }
    }
}