#include "DS18B20.h"

DS18B20::DS18B20(uint8_t pin, uint8_t resolution) : _pin(pin), _resolution(resolution), _oneWire(pin), _sensors(&_oneWire) {
}

void DS18B20::begin() {
    _sensors.begin();
    _sensors.setResolution(_resolution); // Ajusta la resolución después de inicializar
}

float DS18B20::getTemperature() {
    _sensors.requestTemperatures();
    float temperature = _sensors.getTempCByIndex(0);
    if (temperature == DEVICE_DISCONNECTED_C) {
        return NAN; // Device disconnected or error
    }
    return temperature;
}