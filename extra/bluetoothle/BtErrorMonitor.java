package com.medm.bluetoothle;

import com.medm.bluetooth.BtMan;
import com.medm.logger.Logger;
import java.util.Random;

/* loaded from: classes3.dex */
class BtErrorMonitor {
    private static final int MAX_FAIL_ATTEMPTS_BOUND = 5;
    private static final int MONITOR_ID = new Random().nextInt(10000);
    private static int m_nFailAttemptsNumber = 0;
    private static boolean m_bIsAttemptActive = false;

    BtErrorMonitor() {
    }

    static void LogMonitor(String str) {
        Logger.str("BtErrorMonitor (" + MONITOR_ID + "): " + str);
    }

    static void onConnectionAttempt() {
        m_bIsAttemptActive = true;
    }

    static void onConnectionStateChange(int i, int i2) {
        CheckStatus(i);
        if (i2 == 0) {
            m_bIsAttemptActive = false;
        }
    }

    void OnOperationResult(int i) {
        CheckStatus(i);
    }

    void OnData() {
        ResetMonitor();
        m_bIsAttemptActive = true;
    }

    private static void CheckStatus(int i) {
        if (!m_bIsAttemptActive || i == 0) {
            return;
        }
        IncreaseAndCheckFailsNumber();
    }

    private static void IncreaseAndCheckFailsNumber() {
        int i = m_nFailAttemptsNumber + 1;
        m_nFailAttemptsNumber = i;
        if (i > 5) {
            ResetBluetooth();
        }
    }

    private static void ResetBluetooth() {
        LogMonitor("ResetBluetooth()");
        ResetMonitor();
        BtMan.ForceResetBluetooth();
    }

    private static void ResetMonitor() {
        m_nFailAttemptsNumber = 0;
        m_bIsAttemptActive = false;
    }
}