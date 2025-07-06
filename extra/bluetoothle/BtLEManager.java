package com.medm.bluetoothle;

/* loaded from: classes3.dex */
public class BtLEManager {
    public static boolean isBLEAvailable() {
        return existsClass("android.bluetooth.BluetoothManager");
    }

    private static boolean existsClass(String str) throws ClassNotFoundException {
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (str == null || str.equals("")) {
            return false;
        }
        try {
            systemClassLoader.loadClass(str);
            return true;
        } catch (Exception unused) {
            return false;
        }
    }
}