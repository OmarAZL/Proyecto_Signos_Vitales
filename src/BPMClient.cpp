// BPMClient.cpp
#include "BPMClient.h"
#include <string>
#include <iomanip> // For std::hex, std::setw, std::setfill

// Constructor
BPMClient::BPMClient() {}

void BPMClient::begin() {
  Serial.println("Initializing BLE...");
  BLEDevice::init(DEVICE_NAME); // Initialize BLE for the ESP32

  // Create a new BLE client instance
  pClient = BLEDevice::createClient();
  if (!pClient) {
    Serial.println("Failed to create BLE client!");
    return;
  }
  // Set the client callbacks for connection/disconnection events
  pClient->setClientCallbacks(this);

  // Start BLE scanning
  pBLEScan = BLEDevice::getScan();
  pBLEScan->setAdvertisedDeviceCallbacks(this); // Set the scan callback
  pBLEScan->setActiveScan(true); // Active scan will seek more info
  pBLEScan->setInterval(100);
  pBLEScan->setWindow(99);    // less or equal to interval
  pBLEScan->start(0, false); // Start scan indefinitely until stopped
  Serial.println("BLE scan started...");
}

bool BPMClient::connectToServer() {
  if (!myDevice) {
    Serial.println("No advertised device found to connect to.");
    return false;
  }

  Serial.print("Connecting to ");
  Serial.println(myDevice->getAddress().toString().c_str());

  if (pClient->connect(myDevice)) {
    Serial.println("Successfully connected to device.");
    connected = true;
  } else {
    Serial.println("Failed to connect to device.");
    connected = false;
    doScan = true; // Restart scan on failure
    return false;
  }

  // Obtain a reference to the service we are interested in
  BLERemoteService* pRemoteService = pClient->getService(SERVICE_UUID);
  if (pRemoteService == nullptr) {
    Serial.print("Failed to find service UUID: ");
    Serial.println(SERVICE_UUID);
    pClient->disconnect();
    connected = false;
    doScan = true; // Restart scan
    return false;
  }
  Serial.println("Found service.");

  // Obtain a reference to the characteristics in the service
  // Characteristic 0000a621-0000-1000-8000-00805f9b34fb (Main data)
  BLERemoteCharacteristic* pCharA621 = pRemoteService->getCharacteristic(CHAR_A621_UUID);
  if (pCharA621 == nullptr) {
    Serial.print("Failed to find characteristic A621 UUID: ");
    Serial.println(CHAR_A621_UUID);
    pClient->disconnect();
    connected = false;
    doScan = true; // Restart scan
    return false;
  }
  Serial.println("Found characteristic A621.");

  // Subscribe to notifications for CHAR_A621_UUID
  if (pCharA621->canNotify()) {
    pCharA621->registerForNotify(BPMClient::notifyCallback, true);
    Serial.println("Registered for notifications on A621.");
  } else {
    Serial.println("Characteristic A621 cannot notify.");
  }

  // Characteristic 0000a625-0000-1000-8000-00805f9b34fb (Status/Ack)
  BLERemoteCharacteristic* pCharA625 = pRemoteService->getCharacteristic(CHAR_A625_UUID);
  if (pCharA625 == nullptr) {
    Serial.print("Failed to find characteristic A625 UUID: ");
    Serial.println(CHAR_A625_UUID);
  } else {
    Serial.println("Found characteristic A625.");
    if (pCharA625->canNotify()) {
      pCharA625->registerForNotify(BPMClient::notifyCallback, true);
      Serial.println("Registered for notifications on A625.");
    } else {
      Serial.println("Characteristic A625 cannot notify.");
    }
  }

  // Characteristic 0000a620-0000-1000-8000-00805f9b34fb (Indication)
  BLERemoteCharacteristic* pCharA620 = pRemoteService->getCharacteristic(CHAR_A620_UUID);
  if (pCharA620 == nullptr) {
    Serial.print("Failed to find characteristic A620 UUID: ");
    Serial.println(CHAR_A620_UUID);
  } else {
    Serial.println("Found characteristic A620.");
    if (pCharA620->canIndicate()) {
      pCharA620->registerForNotify(BPMClient::notifyCallback, false); // For indication, isNotify is false
      Serial.println("Registered for indications on A620.");
    } else {
      Serial.println("Characteristic A620 cannot indicate.");
    }
  }
  
  // Try to find and subscribe to characteristics in the second service (0000ff00-...)
  BLERemoteService* pRemoteServiceFF00 = pClient->getService(SERVICE_FF00_UUID);
  if (pRemoteServiceFF00 == nullptr) {
    Serial.print("Failed to find service UUID: ");
    Serial.println(SERVICE_FF00_UUID);
  } else {
    Serial.println("Found service FF00.");
    BLERemoteCharacteristic* pCharFF02 = pRemoteServiceFF00->getCharacteristic(CHAR_FF02_UUID);
    if (pCharFF02 == nullptr) {
      Serial.print("Failed to find characteristic FF02 UUID: ");
      Serial.println(CHAR_FF02_UUID);
    } else {
      Serial.println("Found characteristic FF02.");
      if (pCharFF02->canNotify()) {
        pCharFF02->registerForNotify(BPMClient::notifyCallback, true);
        Serial.println("Registered for notifications on FF02.");
      } else {
        Serial.println("Characteristic FF02 cannot notify.");
      }
    }
  }

  return true;
}

// Callback for when a device is found during scanning
void BPMClient::onResult(BLEAdvertisedDevice advertisedDevice) {
  // Found a device
  Serial.print("Advertised Device found: ");
  Serial.println(advertisedDevice.toString().c_str());

  // Check if it's the target device
  if (advertisedDevice.getName() == DEVICE_NAME) {
    Serial.print("Found target device: ");
    Serial.println(DEVICE_NAME);
    BLEDevice::getScan()->stop(); // Stop scanning once found
    myDevice = new BLEAdvertisedDevice(advertisedDevice); // Store a copy
    doScan = false; // Stop scanning flag
  }
}

// Client connection event callbacks
void BPMClient::onConnect(BLEClient* client) {
  Serial.println("Connected to BPM device.");
  connected = true;
  doScan = false; // Stop scanning once connected
}

// Helper to call onDisconnect from static context
void BPMClient::voidOnDisconnect(BLEClient* client) {
    this->connected = false;
    this->doScan = true; // Restart scan
    Serial.println("Disconnected from BPM device. Restarting scan...");
}

// Actual overridden onDisconnect method
void BPMClient::onDisconnect(BLEClient* client) {
  Serial.println("BPMClient::onDisconnect called.");
  // Use the global instance to modify its state
  g_bpmClient.connected = false;
  g_bpmClient.doScan = true; // Restart scan
  Serial.println("Disconnected from BPM device. Restarting scan...");
  pClient->disconnect(); // Ensure full disconnect process
}


// Static callback function for characteristic notifications/indications
void BPMClient::notifyCallback(BLERemoteCharacteristic* pBLERemoteCharacteristic, uint8_t* pData, size_t length, bool isNotify) {
  Serial.print("Notification/Indication received from ");
  Serial.print(pBLERemoteCharacteristic->getUUID().toString().c_str());
  Serial.print(", value: (0x) ");
  g_bpmClient.printHexData(pData, length); // Use global instance to call helper

  // Check which characteristic sent the notification
  if (pBLERemoteCharacteristic->getUUID().equals(BLEUUID(CHAR_A621_UUID))) {
    Serial.println(" (from CHAR_A621_UUID - Main Data)");

    if (length >= 1) { // At least flags byte should be present
      uint8_t flags = pData[0]; // Access the first byte directly

      // Parse Blood Pressure Measurement characteristic data (assuming similar format to standard BPP)
      // Reference: Bluetooth GATT Specification Supplement, Blood Pressure Measurement characteristic
      // Byte 0: Flags
      // Bytes 1-2: Systolic (medfloat16)
      // Bytes 3-4: Diastolic (medfloat16)
      // Bytes 5-6: Mean Arterial Pressure (medfloat16)
      // ... subsequent bytes depending on flags

      // Ensure enough bytes are present for base measurements
      if (length >= 7) { // Flags + 3 medfloat16 values = 1 + 2*3 = 7 bytes minimum
        uint16_t systolic_raw = pData[1] | (pData[2] << 8);
        uint16_t diastolic_raw = pData[3] | (pData[4] << 8);
        uint16_t mean_raw = pData[5] | (pData[6] << 8);

        // Access the global instance to call non-static member function
        g_bpmClient.lastReading.systolic = g_bpmClient.medfloat16ToFloat(systolic_raw);
        g_bpmClient.lastReading.diastolic = g_bpmClient.medfloat16ToFloat(diastolic_raw);
        g_bpmClient.lastReading.meanArterialPressure = g_bpmClient.medfloat16ToFloat(mean_raw);

        Serial.printf("  Systolic: %.1f mmHg\n", g_bpmClient.lastReading.systolic);
        Serial.printf("  Diastolic: %.1f mmHg\n", g_bpmClient.lastReading.diastolic);
        Serial.printf("  Mean Arterial Pressure: %.1f mmHg\n", g_bpmClient.lastReading.meanArterialPressure);

        int currentByte = 7; // After the initial 7 bytes for flags and 3 medfloat16 values

        // Check for Time Stamp (Flag bit 1)
        g_bpmClient.lastReading.hasTimestamp = (flags & 0x02);
        if (g_bpmClient.lastReading.hasTimestamp && (length >= currentByte + 7)) { // Year(2)+Month(1)+Day(1)+Hour(1)+Minute(1)+Second(1) = 7 bytes
          uint16_t year = pData[currentByte] | (pData[currentByte+1] << 8);
          uint8_t month = pData[currentByte+2];
          uint8_t day = pData[currentByte+3];
          uint8_t hour = pData[currentByte+4];
          uint8_t minute = pData[currentByte+5];
          uint8_t second = pData[currentByte+6];
          
          struct tm timeinfo;
          timeinfo.tm_year = year - 1900; // tm_year is years since 1900
          timeinfo.tm_mon = month - 1;   // tm_mon is 0-11
          timeinfo.tm_mday = day;
          timeinfo.tm_hour = hour;
          timeinfo.tm_min = minute;
          timeinfo.tm_sec = second;
          g_bpmClient.lastReading.timestamp = mktime(&timeinfo);

          char timeStr[30];
          strftime(timeStr, sizeof(timeStr), "%Y-%m-%d %H:%M:%S", &timeinfo);
          Serial.printf("  Timestamp: %s\n", timeStr);
          currentByte += 7;
        }

        // Check for Pulse Rate (Flag bit 2)
        g_bpmClient.lastReading.hasPulseRate = (flags & 0x04);
        if (g_bpmClient.lastReading.hasPulseRate && (length >= currentByte + 2)) { // 2 bytes for medfloat16
          uint16_t pulse_rate_raw = pData[currentByte] | (pData[currentByte+1] << 8);
          g_bpmClient.lastReading.pulseRate = g_bpmClient.medfloat16ToFloat(pulse_rate_raw);
          Serial.printf("  Pulse Rate: %.1f bpm\n", g_bpmClient.lastReading.pulseRate);
          currentByte += 2;
        }

        // Check for User ID (Flag bit 3)
        g_bpmClient.lastReading.hasUserID = (flags & 0x08);
        if (g_bpmClient.lastReading.hasUserID && (length >= currentByte + 1)) { // 1 byte for User ID
          g_bpmClient.lastReading.userID = pData[currentByte];
          Serial.printf("  User ID: %d\n", g_bpmClient.lastReading.userID);
          currentByte += 1;
        }
      } else {
        Serial.println("  Data length too short for basic blood pressure measurements.");
      }
    }
  } else if (pBLERemoteCharacteristic->getUUID().equals(BLEUUID(CHAR_A625_UUID))) {
    Serial.println(" (from CHAR_A625_UUID - Status/Acknowledgement)");
    // Additional parsing for A625 if its purpose is determined
  } else if (pBLERemoteCharacteristic->getUUID().equals(BLEUUID(CHAR_A620_UUID))) {
    Serial.println(" (from CHAR_A620_UUID - Indication Data)");
    // Additional parsing for A620 if its purpose is determined
  } else if (pBLERemoteCharacteristic->getUUID().equals(BLEUUID(CHAR_FF02_UUID))) {
    Serial.println(" (from CHAR_FF02_UUID)");
    // Additional parsing for FF02 if its purpose is determined
  } else {
    Serial.println(" (from other characteristic)");
  }
}

// Helper function to convert medfloat16 (IEEE 11073-20601 SFLOAT) to float
float BPMClient::medfloat16ToFloat(uint16_t medfloat16Value) {
  // medfloat16 is a 16-bit value with a 12-bit mantissa and a 4-bit exponent.
  // Both mantissa and exponent are in two's complement.
  // Value = Mantissa * 10^Exponent

  int16_t mantissa = medfloat16Value & 0x0FFF; // Extract 12-bit mantissa
  int8_t exponent = (medfloat16Value >> 12) & 0x0F; // Extract 4-bit exponent

  // Handle two's complement for mantissa (if negative)
  if (mantissa >= 0x0800) { // If highest bit of 12-bit mantissa is set
    mantissa = -(0x1000 - mantissa); // Convert to negative value
  }

  // Handle two's complement for exponent (if negative)
  if (exponent >= 0x08) { // If highest bit of 4-bit exponent is set
    exponent = -(0x10 - exponent); // Convert to negative value
  }

  // Special values (not explicitly required by the user, but good for robustness)
  if (mantissa == 0x07FF) return NAN; // NaN
  if (mantissa == 0x0800) return INFINITY; // NRes (Not at this resolution) - often treated as positive infinity
  if (mantissa == 0x07FE && exponent == 0) return NAN; // Reserved for future use (spec. dependent)

  return (float)mantissa * pow(10, exponent);
}

// Helper to print data in hex format
void BPMClient::printHexData(uint8_t* pData, size_t length) {
  Serial.print("{");
  for (int i = 0; i < length; i++) {
    Serial.printf("%02X", pData[i]);
    if (i < length - 1) {
      Serial.print(" ");
    }
  }
  Serial.println("}");
}

BloodPressureReading BPMClient::getLastReading() {
  return lastReading;
}

bool BPMClient::isConnected() {
  return connected;
}

bool BPMClient::getDoScan() {
  return doScan;
}

void BPMClient::startScan() {
  Serial.println("Starting BLE scan again...");
  pBLEScan->start(0, false); // Start scan indefinitely
  doScan = false; // Reset scan flag
}