#include "GY906.h"

GY906::GY906(uint8_t SDA, uint8_t SCL, uint8_t address) : _SDA(SDA), _SCL(SCL), _address(address) {}

void GY906::begin() {
    Wire.begin(_SDA, _SCL);
}

float GY906::readObjectTempC() {
    Wire.beginTransmission(_address);
    Wire.write(0x07);
    Wire.endTransmission(false);
    Wire.requestFrom(_address, (uint8_t)3);
    if (Wire.available() >= 3) {
        uint16_t temp = Wire.read();
        temp |= Wire.read() << 8;
        Wire.read(); // PEC
        return (temp * 0.02) - 273.15;
    }
    return NAN;
}

float GY906::readAmbientTempC() {
    Wire.beginTransmission(_address);
    Wire.write(0x06);
    Wire.endTransmission(false);
    Wire.requestFrom(_address, (uint8_t)3);
    if (Wire.available() >= 3) {
        uint16_t temp = Wire.read();
        temp |= Wire.read() << 8;
        Wire.read(); // PEC
        return (temp * 0.02) - 273.15;
    }
    return NAN;
}
