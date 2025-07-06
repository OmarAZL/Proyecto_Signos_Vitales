#ifndef MAIN_H
#define MAIN_H

#include <Arduino.h>
#include "Sensors/DS18B20.h"
#include "Sensors/AD8232.h"
#include "Sensors/GY906.h"
#include "BPMClient.h"
#include "Screens/LCD.h"
#include "Screens/OLED.h"
#include "config.h"

//DS18B20 ds18b20(DS18B20_PIN, DS18B20_RESOLUTION); // Pin y resolución del sensor de temperatura DS18B20
//GY906 gy906(GY906_ADDRESS);
//OLED oled(SCREEN_ADDRESS, SCREEN_WIDTH, SCREEN_HEIGHT);
//AD8232 ad8232(AD8232_OUT_PIN, AD8232_LOPLUS_PIN, AD8232_LOMINUS_PIN);
LCD lcd(SCREEN_LCD_RS, SCREEN_LCD_E, SCREEN_LCD_D4, SCREEN_LCD_D5, SCREEN_LCD_D6, SCREEN_LCD_D7);
BPMClient g_bpmClient;



unsigned long LastReport = 0; // Variable para almacenar el tiempo del último reporte

void setup() {
  Serial.begin(115200);
  lcd.begin();
  //Wire.begin(PIN_SDA, PIN_SCL); // Inicializa I2C con los pines SDA y SCL
  //oled.begin(); // Inicializa la pantalla OLED
  //ds18b20.begin();

  lcd.clear();
  lcd.printl0("Bluetooth:");
  lcd.printl1("> Iniciando");

  g_bpmClient.begin();
  //ad8232.begin();
  //gy906.begin();

  lcd.clear();
  lcd.printl0("Bluetooth:");
  lcd.printl1("> Iniciado");

  /*if(!oled.isConnected()) {
    Serial.println("Pantalla OLED no detectada!");
    for(;;); // Detiene la ejecución si la pantalla no está conectada
  } else {
    Serial.println("Pantalla OLED detectada correctamente.");
    oled.clear(); // Limpia la pantalla al inicio
  }*/

  // Verificación de conexión de sensores

  //bool ds18b20Connected = ds18b20.isConnected();
  //bool gy906Connected = gy906.isConnected();;

  /*while(!ds18b20Connected) {
    screen.showMessage("Esperando DS18B20...");
    delay(1000); // Espera 1 segundo antes de volver a verificar
    ds18b20Connected = ds18b20.isConnected();
  }*/

  /*while(!gy906Connected) {
    oled.showMessage("Esperando GY-906...");
    delay(1000); // Espera 1 segundo antes de volver a verificar
    gy906Connected = gy906.isConnected();
  }*/
 
  delay(1000); // Espera para estabilizar la comunicación
  lcd.clear();
  lcd.printl0("Sensores:");
  lcd.printl1("> conectados!");
  
  delay(100); 
}

int i = 10;
unsigned long LastLCDShow = 0;

void loop() {
  
  unsigned long now = millis();
  
  if (!g_bpmClient.isConnected() && (now - LastLCDShow >= 2000)) {
    if (g_bpmClient.getDoScan()) {
      g_bpmClient.startScan();
    }
  }
  
  if(now - LastReport >= 200) {
    float temperatura1 = NAN; //ds18b20.getTemperature();
    float temperature2 = NAN;
    float ecg = NAN;

    //oled.showAllSensors(temperatura1, temperature2, ecg);
  }
}

#endif