# Proyecto_Signos_Vitales

## Overview
This project is designed to connect an ESP32 microcontroller to a Node.js server over WiFi. It includes a `WiFiController` class that manages the WiFi connection, handles reconnections, and provides methods for checking the connection status.

## Project Structure
```
Proyecto_Signos_Vitales
├── include
│   └── WiFiController.h
├── lib
├── src
│   ├── main.cpp
│   └── WiFiController.cpp
├── platformio.ini
└── README.md
```

## Setup Instructions
1. **Install PlatformIO**: Ensure you have PlatformIO installed in your development environment.
2. **Clone the Repository**: Clone this repository to your local machine.
3. **Open the Project**: Open the project folder in PlatformIO.
4. **Configure WiFi Credentials**: In `src/WiFiController.cpp`, update the SSID and password with your WiFi credentials.
5. **Build the Project**: Use PlatformIO to build the project.
6. **Upload to ESP32**: Connect your ESP32 to your computer and upload the code.

## Usage
- The `WiFiController` class is responsible for managing the WiFi connection. 
- Call `begin(ssid, password)` to initialize the connection with your WiFi credentials.
- Use `connect()` to attempt to connect to the WiFi network.
- Check the connection status using `isConnected()`.
- If the connection is lost, `handleReconnect()` can be called to attempt to reconnect.

## Example
```cpp
#include "WiFiController.h"

WiFiController wifiController;

void setup() {
    Serial.begin(115200);
    wifiController.begin("your_SSID", "your_PASSWORD");
    wifiController.connect();
}

void loop() {
    if (!wifiController.isConnected()) {
        wifiController.handleReconnect();
    }
    // Other application logic
}
```

## Additional Information
- The project may include additional libraries in the `lib` directory for handling HTTP requests or JSON parsing.
- Ensure that the ESP32 board is selected in the PlatformIO configuration (`platformio.ini`).
- Monitor the serial output for connection status and error messages.

## License
This project is licensed under the MIT License.