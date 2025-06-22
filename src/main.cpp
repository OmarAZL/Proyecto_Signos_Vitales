#include <Arduino.h>
#include "Sensors/DS18B20.h"
#include "Sensors/AD8232.h"
#include "Sensors/GY906.h"
#include "MAX30100_PulseOximeter.h"
#include "Screen.h"
#include "config.h"

DS18B20 temperatureSensor(DS18B20_PIN, DS18B20_RESOLUTION); // Pin y resolución del sensor de temperatura DS18B20
Screen screen(SCREEN_ADDRESS, SCREEN_WIDTH, SCREEN_HEIGHT);
AD8232 ecgSensor(AD8232_OUT_PIN, AD8232_LOPLUS_PIN, AD8232_LOMINUS_PIN);
GY906 gy906(GY906_ADDRESS);
PulseOximeter max30100;

uint32_t tsLastReport = 0;

// Callback para cuando se detecta un latido
void onBeatDetected() {
    Serial.println("Latido detectado!");
}

void setup() {
  Serial.begin(115200);
  Wire.begin(PIN_SDA, PIN_SCL); // Inicializa I2C con los pines SDA y SCL
  temperatureSensor.begin();
  ecgSensor.begin();
  gy906.begin();
  
  // Verificación de sensores
  if (!temperatureSensor.isConnected()) {
    Serial.println("DS18B20 no detectado!");
    for(;;);
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
     for(;;);
  } else {
    Serial.println("GY-906 detectado correctamente.");
  }

  if (!max30100.begin()) {
    Serial.println("MAX30100 no detectado!");
    for(;;);
  } else {
    Serial.println("MAX30100 detectado correctamente.");
    max30100.setOnBeatDetectedCallback(onBeatDetected);
  }
  delay(1000); // Espera para estabilizar la comunicación

}

void loop() {
  max30100.update();
  unsigned long now = millis();

  if (now - tsLastReport > REPORTING_PERIOD_MS) {
    Serial.print("BPM: ");
        Serial.print(max30100.getHeartRate());
        Serial.print(" | SpO2: ");
        Serial.println(max30100.getSpO2());
        tsLastReport = millis();
  }

  if (now - tsLastReport >= 2000) {
    tsLastReport = now;
    // Lectura de sensores
    float tempDS18B20 = temperatureSensor.getTemperature();
    float tempGY906 = gy906.readObjectTempC();
    bool ecgConnected = ecgSensor.electrodesConnected();

    // Formatear mensaje para la pantalla
    String msg = "T1: ";
    if (isnan(tempDS18B20)) msg += "Err"; else msg += String(tempDS18B20, 1);
    msg += " C\nT2: ";
    if (isnan(tempGY906)) msg += "Err"; else msg += String(tempGY906, 1);
    msg += " C\nECG: ";
    msg += (ecgConnected ? "OK" : "NO");
    //msg += "\nMAX: ";
    //if (maxOk) { msg += String(ir) + "," + String(red); } else { msg += "Err"; }

    screen.showMessage(msg);
  }
}

// put function definitions here:

