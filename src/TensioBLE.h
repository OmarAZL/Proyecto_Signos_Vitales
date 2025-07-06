#ifndef TENSIO_BLE_H
#define TENSIO_BLE_H

#include <BLEDevice.h>
#include "Screens/LCD.h"

class TensioBLE {
public:
    TensioBLE(LCD& screen);
    void begin();
    void loop();
    bool isConnected();
    
    // Permite asignar callbacks personalizados para cada característica
    void setNotifyCallbackA621(void (*cb)(uint8_t*, size_t));
    void setNotifyCallbackA625(void (*cb)(uint8_t*, size_t));
    void setIndicateCallbackA620(void (*cb)(uint8_t*, size_t));

private:
    LCD& _screen;
    BLEAddress tensiometroAddress;
    BLEUUID serviceUUID;
    BLEUUID charA620UUID;
    BLEUUID charA621UUID;
    BLEUUID charA625UUID;
    BLEClient* pClient;
    BLERemoteCharacteristic* pCharA620;
    BLERemoteCharacteristic* pCharA621;
    BLERemoteCharacteristic* pCharA625;

    bool connected;
    class ClientCB;
    
    void (*userNotifyCallbackA621)(uint8_t*, size_t);
    void (*userNotifyCallbackA625)(uint8_t*, size_t);
    void (*userIndicateCallbackA620)(uint8_t*, size_t);

    unsigned long lastReconnectAttempt;
    void connect();

    // Callbacks estáticos para BLE
    static void notifyCallbackA621 (
        BLERemoteCharacteristic*, uint8_t*, size_t, bool);
    static void notifyCallbackA625 (
        BLERemoteCharacteristic*, uint8_t*, size_t, bool);
    static void indicateCallbackA620 (
        BLERemoteCharacteristic*, uint8_t*, size_t, bool);

    // Métodos para llamar a los callbacks de usuario
    void handleNotifyA621(uint8_t*, size_t);
    void handleNotifyA625(uint8_t*, size_t);
    void handleIndicateA620(uint8_t*, size_t);

    // Para acceso desde callbacks estáticos
    static TensioBLE* instance;

};

#endif