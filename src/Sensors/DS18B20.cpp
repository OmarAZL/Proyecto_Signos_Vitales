#include "DS18B20.h"

DS18B20::DS18B20(uint8_t pin) : _pin(pin), _oneWire(pin), _sensors(&_oneWire) {
}

void DS18B20::begin() {
    _sensors.begin();
}

float DS18B20::getTemperature() {
    _sensors.requestTemperatures();
    float temperature = _sensors.getTempCByIndex(0);
    if (temperature == DEVICE_DISCONNECTED_C) {
        return NAN; // Device disconnected or error
    }
    return temperature;
}