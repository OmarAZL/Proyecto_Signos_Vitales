#ifndef WIFICONTROLLER_H
#define WIFICONTROLLER_H

#include <WiFi.h>

class WiFiController {
public:
    void begin(const char* ssid, const char* password);
    void connect();
    void disconnect();
    bool isConnected();
    void handleReconnect();

private:
    const char* _ssid;
    const char* _password;
    WiFiEventId_t _eventId;

    void WiFiEvent(WiFiEvent_t event);

    static void onConnected(WiFiEvent_t event, WiFiEventInfo_t info);
    static void onDisconnected(WiFiEvent_t event, WiFiEventInfo_t info);
};

#endif // WIFICONTROLLER_H