#include <WiFi.h>
#include "WiFiController.h"

const char* _ssid;
const char* _password;

WiFiEventId_t _eventId;

void WiFiController::begin(const char* ssid, const char* password) {
    _ssid = ssid;
    _password = password;
    WiFi.onEvent(WiFiEvent);
    connect();
}

void WiFiController::connect() {
    Serial.print("Connecting to WiFi...");
    WiFi.begin(_ssid, _password);
}

void WiFiController::disconnect() {
    WiFi.disconnect();
    Serial.println("Disconnected from WiFi.");
}

bool WiFiController::isConnected() {
    return WiFi.status() == WL_CONNECTED;
}

void WiFiController::handleReconnect() {
    if (!isConnected()) {
        Serial.println("WiFi disconnected. Attempting to reconnect...");
        connect();
    }
}

void WiFiController::WiFiEvent(WiFiEvent_t event) {
    switch (event) {
        case SYSTEM_EVENT_STA_CONNECTED:
            Serial.println("Connected to WiFi.");
            break;
        case SYSTEM_EVENT_STA_DISCONNECTED:
            Serial.println("Disconnected from WiFi.");
            handleReconnect();
            break;
        default:
            break;
    }
}