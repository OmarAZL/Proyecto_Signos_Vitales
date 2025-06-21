#include <Arduino.h>
#include "Sensors/DS18B20.h"
#include "Sensors/AD8232.h"
#include "Sensors/GY906.h"
#include "Sensors/MAX30100.h"
#include "Screen.h"
#include "config.h"

DS18B20 temperatureSensor(DS18B20_PIN, DS18B20_RESOLUTION); // Pin y resolución del sensor de temperatura DS18B20
Screen screen(PIN_SDA, PIN_SCL, SCREEN_WIDTH, SCREEN_HEIGHT);
AD8232 ecgSensor(AD8232_OUT_PIN, AD8232_LOPLUS_PIN, AD8232_LOMINUS_PIN);
GY906 gy906(PIN_SDA, PIN_SCL, GY906_ADDRESS);
MAX30100 max30100(PIN_SDA, PIN_SCL, MAX30100_ADDRESS);

void setup() {
  Serial.begin(115200);
  temperatureSensor.begin();
  ecgSensor.begin();
  gy906.begin();
  max30100.begin();

  // Verificación de sensores
  if (!temperatureSensor.isConnected()) {
    Serial.println("DS18B20 no detectado!");
  } else {
    Serial.println("DS18B20 detectado correctamente.");
  }

  if (!ecgSensor.electrodesConnected()) {
    Serial.println("AD8232: Electrodos desconectados!");
  } else {
    Serial.println("AD8232: Electrodos conectados.");
  }

  float tempObj = gy906.readObjectTempC();
  if (isnan(tempObj)) {
    Serial.println("GY-906 no detectado!");
  } else {
    Serial.println("GY-906 detectado correctamente.");
  }

  uint16_t ir, red;
  if (!max30100.readRaw(ir, red)) {
    Serial.println("MAX30100 no detectado!");
  } else {
    Serial.println("MAX30100 detectado correctamente.");
  }
}

void loop() {
  static unsigned long lastUpdate = 0;
  unsigned long now = millis();
  if (now - lastUpdate >= 2000) {
    lastUpdate = now;
    // Lectura de sensores
    float tempDS18B20 = temperatureSensor.getTemperature();
    float tempGY906 = gy906.readObjectTempC();
    bool ecgConnected = ecgSensor.electrodesConnected();
    uint16_t ir, red;
    bool maxOk = max30100.readRaw(ir, red);

    // Formatear mensaje para la pantalla
    String msg = "T1: ";
    if (isnan(tempDS18B20)) msg += "Err"; else msg += String(tempDS18B20, 1);
    msg += " C\nT2: ";
    if (isnan(tempGY906)) msg += "Err"; else msg += String(tempGY906, 1);
    msg += " C\nECG: ";
    msg += (ecgConnected ? "OK" : "NO");
    msg += "\nMAX: ";
    if (maxOk) { msg += String(ir) + "," + String(red); } else { msg += "Err"; }

    screen.showMessage(msg);
  }
}

// put function definitions here:

