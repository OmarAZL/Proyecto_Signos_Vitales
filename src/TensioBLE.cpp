#include "TensioBLE.h"
#include <Arduino.h>

// Callback de desconexión
class TensioBLE::ClientCB : public BLEClientCallbacks {
    TensioBLE* parent;
public:
    ClientCB(TensioBLE* p) : parent(p) {}
    void onConnect(BLEClient*) override {}
    void onDisconnect(BLEClient*) override {
        Serial.println("Desconectado del tensiómetro");
        parent->_screen.clear();
        parent->_screen.printl0("Bluetooth:");
        parent->_screen.printl1("> Desconectado");
        parent->connected = false;
    }
};

TensioBLE* TensioBLE::instance = nullptr;

TensioBLE::TensioBLE(LCD& screen)
    : tensiometroAddress("67:7F:CF:F5:42:60"),
      //serviceUUID("0000a610-0000-1000-8000-00805f9b34fb"),
      serviceUUID("00002902-0000-1000-8000-00805f9b34fb"),
      //charA620UUID("0000a620-0000-1000-8000-00805f9b34fb"),
      charA620UUID("00002a21-0000-1000-8000-00805f9b34fb"),
      //charA621UUID("0000a621-0000-1000-8000-00805f9b34fb"),
      charA621UUID("0000ffb1-0000-1000-8000-00805f9b34fb"),
      charA625UUID("0000a625-0000-1000-8000-00805f9b34fb"),
      pClient(nullptr),
      pCharA620(nullptr),
      pCharA621(nullptr),
      pCharA625(nullptr),
      connected(false),
      userNotifyCallbackA621(nullptr),
      userNotifyCallbackA625(nullptr),
      userIndicateCallbackA620(nullptr),
      lastReconnectAttempt(0),
      _screen(screen)
{
    instance = this;
}

void TensioBLE::begin() {
    BLEDevice::init("");
    connect();
}

void TensioBLE::connect() {
    if(pClient == nullptr) {
        pClient = BLEDevice::createClient();
        pClient->setClientCallbacks(new ClientCB(this)); // <-- Asigna el callback
    }
    if(pClient->connect(tensiometroAddress)) {
        Serial.println("Conectado al tensiómetro");
        _screen.clear();
        _screen.printl0("Bluetooth:");
        _screen.printl1("> Conectado");
        BLERemoteService* pRemoteService = pClient->getService(serviceUUID);
        if (pRemoteService) {
            // 1. Característica A620 (Indicación)
            pCharA620 = pRemoteService->getCharacteristic(charA620UUID);
            if (pCharA620 && pCharA620->canIndicate()) {
                pCharA620->registerForNotify(indicateCallbackA620);
            }
            // 2. Característica A621 (Notificación)
            pCharA621 = pRemoteService->getCharacteristic(charA621UUID);
            if (pCharA621 && pCharA621->canNotify()) {
                pCharA621->registerForNotify(notifyCallbackA621);
            }
            // 3. Característica A625 (Notificación)
            pCharA625 = pRemoteService->getCharacteristic(charA625UUID);
            if (pCharA625 && pCharA625->canNotify()) {
                pCharA625->registerForNotify(notifyCallbackA625);
            }
            connected = true;
        }
    } else {
        Serial.println("No se pudo conectar al tensiómetro");
        _screen.clear();
        _screen.printl0("Bluetooth: ");
        _screen.printl1("> Error");
        connected = false;
    }
}

void TensioBLE::loop() {
    if(!connected) {
        unsigned long now = millis();
        if (now - lastReconnectAttempt > 5000) { // Intenta reconectar cada 5 segundos
            Serial.println("Intentando reconectar...");
            _screen.clear();
            _screen.printl0("Bluetooth:");
            _screen.printl1("> Reconectando");
            connect();
            lastReconnectAttempt = now;
        }
    }
}

bool TensioBLE::isConnected() {
    return connected;
}

// Callbacks de usuario
void TensioBLE::setNotifyCallbackA621(void (*cb)(uint8_t*, size_t)) {
    userNotifyCallbackA621 = cb;
}
void TensioBLE::setNotifyCallbackA625(void (*cb)(uint8_t*, size_t)) {
    userNotifyCallbackA625 = cb;
}
void TensioBLE::setIndicateCallbackA620(void (*cb)(uint8_t*, size_t)) {
    userIndicateCallbackA620 = cb;
}

void TensioBLE::handleNotifyA621(uint8_t* data, size_t len) {
    if (userNotifyCallbackA621) userNotifyCallbackA621(data, len);
}
void TensioBLE::handleNotifyA625(uint8_t* data, size_t len) {
    if (userNotifyCallbackA625) userNotifyCallbackA625(data, len);
}
void TensioBLE::handleIndicateA620(uint8_t* data, size_t len) {
    if (userIndicateCallbackA620) userIndicateCallbackA620(data, len);
}

// Callbacks estáticos para BLE
void TensioBLE::notifyCallbackA621(BLERemoteCharacteristic*, uint8_t* data, size_t len, bool) {
    Serial.print("[A621] Notificación: ");
    for (size_t i = 0; i < len; i++) Serial.printf("%02X ", data[i]);
    Serial.println();
    if (instance) instance->handleNotifyA621(data, len);
}
void TensioBLE::notifyCallbackA625(BLERemoteCharacteristic*, uint8_t* data, size_t len, bool) {
    Serial.print("[A625] Notificación: ");
    for (size_t i = 0; i < len; i++) Serial.printf("%02X ", data[i]);
    Serial.println();
    if (instance) instance->handleNotifyA625(data, len);
}
void TensioBLE::indicateCallbackA620(BLERemoteCharacteristic*, uint8_t* data, size_t len, bool) {
    Serial.print("[A620] Indicación: ");
    for (size_t i = 0; i < len; i++) Serial.printf("%02X ", data[i]);
    Serial.println();
    if (instance) instance->handleIndicateA620(data, len);
}
