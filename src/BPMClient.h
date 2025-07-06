// BPMClient.h
#ifndef BPMCLIENT_H
#define BPMCLIENT_H

#include "Arduino.h"
#include <BLEDevice.h>
#include <BLEClient.h>
#include <BLEAdvertisedDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>

// UUIDs for the Dovant TENSUS 1200 (from nRF Connect logs)
// These are NOT the standard Bluetooth SIG Blood Pressure Profile UUIDs.
#define SERVICE_UUID "0000a610-0000-1000-8000-00805f9b34fb"
#define CHAR_A620_UUID "0000a620-0000-1000-8000-00805f9b34fb" // Indication
#define CHAR_A621_UUID "0000a621-0000-1000-8000-00805f9b34fb" // Notification (main data)
#define CHAR_A625_UUID "0000a625-0000-1000-8000-00805f9b34fb" // Notification (status/ack)
#define SERVICE_FF00_UUID "0000ff00-0000-1000-8000-00805f9b34fb" // Another service
#define CHAR_FF02_UUID "0000ff02-0000-1000-8000-00805f9b34fb" // Characteristic in FF00 service

// Device name to look for
#define DEVICE_NAME "Dovant TENSUS 1200"

// Structure to hold blood pressure readings
struct BloodPressureReading {
  float systolic;
  float diastolic;
  float meanArterialPressure;
  float pulseRate;
  bool hasTimestamp;
  time_t timestamp;
  bool hasPulseRate;
  bool hasUserID;
  uint8_t userID;
  // Add more fields as needed based on flags and data analysis
};

// Forward declaration of the global BPMClient instance
extern class BPMClient g_bpmClient;

// Define a class to handle BLE client callbacks
class BPMClient : public BLEClientCallbacks, public BLEAdvertisedDeviceCallbacks {
public:
  BPMClient();
  void begin();
  bool connectToServer();
  BloodPressureReading getLastReading();
  bool isConnected();
  bool getDoScan();
  void startScan();

private:
  BLEClient* pClient = nullptr;
  BLEAdvertisedDevice* myDevice = nullptr;
  BLEScan* pBLEScan;

  bool connected = false;
  bool doScan = true;

  BloodPressureReading lastReading;

  // Callback for when a device is found during scanning
  void onResult(BLEAdvertisedDevice advertisedDevice) override;

  // Callbacks for client connection events
  void onConnect(BLEClient* client) override;
  void voidOnDisconnect(BLEClient* client); // A helper to allow calling member from static context
  void onDisconnect(BLEClient* client) override; // This is the actual overridden method

  // Static callback function for characteristic notifications
  static void notifyCallback(BLERemoteCharacteristic* pBLERemoteCharacteristic, uint8_t* pData, size_t length, bool isNotify);

  // Helper function to convert medfloat16 to float
  float medfloat16ToFloat(uint16_t medfloat16Value);
  void printHexData(uint8_t* pData, size_t length);
};

#endif