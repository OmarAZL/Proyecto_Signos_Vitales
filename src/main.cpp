#include <Arduino.h>
#include "Sensors/DS18B20.h"
#include "Sensors/AD8232.h"
#include "Sensors/GY906.h"
#include "Screen.h"
#include "config.h"

DS18B20 ds18b20(DS18B20_PIN, DS18B20_RESOLUTION); // Pin y resolución del sensor de temperatura DS18B20
GY906 gy906(GY906_ADDRESS);
Screen screen(SCREEN_ADDRESS, SCREEN_WIDTH, SCREEN_HEIGHT);
AD8232 ad8232(AD8232_OUT_PIN, AD8232_LOPLUS_PIN, AD8232_LOMINUS_PIN);

// Variables para el control de tiempo no bloqueante (ECG)
unsigned long last_read_time = 0;
const long sample_interval_ms = 30; // Muestrear cada 4 ms para ~250 Hz (1000ms / 250Hz = 4ms)
unsigned long last_ecg_display_time = 0;
const long ecg_display_interval_ms = 20; // Actualizar la pantalla cada 20 ms para ~50 FPS (1000ms / 50FPS = 20ms)

// Variables para el filtro pasa-bajo simple (EMA - Exponential Moving Average)
float filtered_ecg_value = 0;
float alpha_lp = 0.1; // Constante del filtro (0.0 a 1.0), un valor más alto = más suavizado [39]

// Búfer para los valores Y de la pantalla para el trazado de desplazamiento
int previous_y_values[SCREEN_WIDTH]; // CORRECCIÓN: Declarado como un array con el tamaño de la pantalla

// Definición del pin del botón

volatile int currentDisplayMode = 0; // 0 para ECG, 1 para Temperatura
unsigned long lastButtonPressTime = 0;
const long debounceDelay = 50; // Milisegundos para el antirrebote

// Variables para el control de tiempo no bloqueante (Temperatura)
unsigned long last_temp_display_time = 0;
const long temp_display_interval_ms = 1000; // Actualizar la pantalla de temperatura cada 1 segundo

// Prototipos de funciones
void displayECGGraph();
void displayTemperatureInfo();

Adafruit_SSD1306 &display = screen.getDisplay(); 

void setup() {
    Serial.begin(115200); // Inicializar comunicación serial para depuración [8, 21]
    Wire.begin(PIN_SDA, PIN_SCL); // Inicializa I2C con los pines SDA y SCL
    screen.begin(); // Inicializa la pantalla OLED
    ds18b20.begin();
    ad8232.begin();
    gy906.begin();


    // Inicializar la pantalla OLED
    bool screenCheck = screen.isConnected();
    while (!screenCheck) {
        Serial.println("Esperando pantalla OLED...");
        screenCheck = screen.isConnected();
        delay(2000);
    }
    
    // Verificación de conexión de sensores

    bool ds18b20Connected = ds18b20.isConnected();
    bool gy906Connected = gy906.isConnected();;

    while(!ds18b20Connected) {
        screen.showMessage("Esperando DS18B20...");
        delay(1000); // Espera 1 segundo antes de volver a verificar
        ds18b20Connected = ds18b20.isConnected();
    }

    while(!gy906Connected) {
        screen.showMessage("Esperando GY-906...");
        delay(1000); // Espera 1 segundo antes de volver a verificar
        gy906Connected = gy906.isConnected();
    }
    // Inicializar el búfer de la forma de onda a la mitad de la pantalla
    for (int i = 0; i < SCREEN_WIDTH; i++) {
        previous_y_values[i] = SCREEN_HEIGHT / 2; 
    }

    // Configuración del botón
    pinMode(BUTTON_PIN, INPUT_PULLUP); // Asumiendo resistencia pull-up, el botón se conecta a GND cuando se presiona [59]
}

void loop() {
  unsigned long current_millis = millis();

  // Lógica del botón con antirrebote
  int buttonState = digitalRead(BUTTON_PIN);
  if (buttonState == LOW && (current_millis - lastButtonPressTime) > debounceDelay) {
    lastButtonPressTime = current_millis;
    currentDisplayMode = (currentDisplayMode == 0)? 1 : 0; // Alternar modo
    display.clearDisplay(); // Limpiar la pantalla al cambiar de modo para evitar el efecto fantasma
  }

  if (currentDisplayMode == 0) {
    displayECGGraph();
  } else {
    displayTemperatureInfo();
  }
}

void displayECGGraph() {
  unsigned long current_millis_ecg = millis();

  // Leer datos del ECG a una frecuencia consistente
  if (current_millis_ecg - last_read_time >= sample_interval_ms) {
    last_read_time = current_millis_ecg;

    int raw_ecg_value = ad8232.readECG();

    // Comprobar la detección de desconexión de electrodos [13, 14]
    if (!ad8232.electrodesConnected()) {
      // Si los electrodos están desconectados, mostrar un mensaje de advertencia
      screen.showMessage("Electrodos Desconectados!");
      // Si hay desconexión, se puede establecer un valor de ECG filtrado que mantenga la línea en el centro
      filtered_ecg_value = 2047; // Valor central del ADC de 12 bits (4095/2)
    } else {
      Serial.print(">ECG:");
      Serial.println(String(raw_ecg_value));
      // Aplicar filtro pasa-bajo EMA para suavizar la señal [39]
      // Se inicializa 'filtered_ecg_value' en la primera lectura válida para evitar un salto grande
      if (filtered_ecg_value == 0 && raw_ecg_value!= 0) { 
        filtered_ecg_value = raw_ecg_value;
      } else {
        filtered_ecg_value = (alpha_lp * raw_ecg_value) + ((1 - alpha_lp) * filtered_ecg_value);
      }
    }
  }

  // Actualizar la pantalla a una frecuencia de visualización consistente
  if (current_millis_ecg - last_ecg_display_time >= ecg_display_interval_ms) {
    last_ecg_display_time = current_millis_ecg;

    display.clearDisplay();
    
    // Mapear el valor filtrado del ECG al rango de píxeles del eje Y de la pantalla
    // Invertir el mapeo para que los valores más altos estén más arriba en la pantalla
    int y_current_pixel = map(filtered_ecg_value, 0, 4095, SCREEN_HEIGHT - 1, 0); // Mapeo 0-4095 a 63-0 [8]
    y_current_pixel = constrain(y_current_pixel, 0, SCREEN_HEIGHT - 1); // Asegurar límites

    // Desplazar los valores existentes en el búfer a la izquierda
    for (int i = 0; i < SCREEN_WIDTH - 1; i++) {
        previous_y_values[i] = previous_y_values[i+1];
    }
    // Añadir el nuevo valor al final del búfer
    previous_y_values[SCREEN_WIDTH - 1] = y_current_pixel; // CORRECCIÓN: Asignar al último elemento del array

    // Dibujar todas las líneas del búfer
    for (int i = 0; i < SCREEN_WIDTH - 1; i++) {
        display.drawLine(i, previous_y_values[i], i + 1, previous_y_values[i+1], WHITE);
    }

    display.display(); // Actualizar la pantalla con el nuevo gráfico [17]
  }
}

void displayTemperatureInfo() {
  unsigned long current_millis_temp = millis();

  if (current_millis_temp - last_temp_display_time >= temp_display_interval_ms) {
    last_temp_display_time = current_millis_temp;

    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(WHITE);

    // Leer temperatura del DS18B20 [56]
    float tempC_DS18B20 = ds18b20.getTemperature(); // Obtener temperatura en Celsius

    // Leer temperaturas del GY-906 (MLX90614) [57, 58]
    float ambientTempC_MLX = gy906.readAmbientTempC(); // Leer temperatura ambiente
    float objectTempC_MLX = gy906.readObjectTempC(); // Leer temperatura del objeto

    Serial.print(">T1:");
    Serial.println(String(tempC_DS18B20));
    Serial.print(">T2A:");
    Serial.println(String(ambientTempC_MLX));
    Serial.print(">T2O:");
    Serial.println(String(objectTempC_MLX));


    display.setCursor(0, 0);
    display.println("Temp DS18B20:");
    display.print(tempC_DS18B20);
    display.println(" C");

    display.setCursor(0, 20);
    display.println("Temp GY-906 (Amb):");
    display.print(ambientTempC_MLX);
    display.println(" C");

    display.setCursor(0, 40);
    display.println("Temp GY-906 (Obj):");
    display.print(objectTempC_MLX);
    display.println(" C");

    display.display();
  }
}