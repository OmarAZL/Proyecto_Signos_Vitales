#include "MAX30100.h"

MAX30100::MAX30100(uint8_t address) : _address(address) {}

void MAX30100::begin() {
    Wire.beginTransmission(_address);
    Wire.write(0x06); // Modo SpO2
    Wire.write(0x03);
    Wire.endTransmission();
}

bool MAX30100::readRaw(uint16_t &ir, uint16_t &red) {
    Wire.beginTransmission(_address);
    Wire.write(0x05); // Dirección del registro de datos
    Wire.endTransmission(false);
    Wire.requestFrom(_address, (uint8_t)4);
    if (Wire.available() >= 4) {
        ir = (Wire.read() << 8) | Wire.read();
        red = (Wire.read() << 8) | Wire.read();
        return true;
    }
    return false;
}
