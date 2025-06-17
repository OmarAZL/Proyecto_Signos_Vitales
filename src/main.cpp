#include <Arduino.h>
#include "Sensors/DS18B20.h"
#include "Screen.h"

DS18B20 temperatureSensor(2);
Screen screen(4, 5, 128, 64); // SDL pin 4, SCL pin 5, width 128, height 64

// put function declarations here:
void setup() {
  // put your setup code here, to run once:
  Serial.begin(115200);
  temperatureSensor.begin();
}

void showTemperature() {
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
  showTemperature();
  delay(2000); // Espera 2 segundos antes de la siguiente lectura
  // put your main code here, to run repeatedly:
}

// put function definitions here:

