#include <Arduino.h>
#include <WiFi.h>
#include "WiFiController.h"

WiFiController wifiController;

void setup() {
  Serial.begin(115200);
  
  // Initialize WiFi connection
  const char* ssid = "your_SSID"; // Replace with your WiFi SSID
  const char* password = "your_PASSWORD"; // Replace with your WiFi password
  wifiController.begin(ssid, password);
  
  // Attempt to connect to WiFi
  wifiController.connect();
}

void loop() {
  // Check WiFi connection status and handle reconnections
  wifiController.handleReconnect();
  
  // Add your main application logic here
}