#include <Arduino.h>
#include "Sensors/DS18B20.h"
#include "Screen.h"
#include "config.h"

DS18B20 temperatureSensor(DS18B20_PIN, DS18B20_RESOLUTION); // Pin y resolución del sensor de temperatura DS18B20
Screen screen(SCREEN_SDA, SCREEN_SCL, SCREEN_WIDTH, SCREEN_HEIGHT);

void setup() {
  Serial.begin(115200);
  temperatureSensor.begin();
}

void serialTemperature() {
  float temp = temperatureSensor.getTemperature();
  Serial.print("Temperatura: ");
  if (isnan(temp)) {
    Serial.println("Error al leer la temperatura!");
  } else {
    Serial.print(temp);
    Serial.println(" °C");
  }
}

void loop() {
  screen.showMessage("Leyendo temperatura...");
  serialTemperature();
  delay(2000); // Espera 2 segundos antes de la siguiente lectura
  // put your main code here, to run repeatedly:
}

// put function definitions here:

