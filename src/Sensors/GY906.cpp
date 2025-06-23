#include "GY906.h"

void GY906::begin() {
    Wire.beginTransmission(_address);
    Wire.write(0x01); // Write to configuration register
    Wire.write(0x00); // Set to normal mode
    Wire.endTransmission();
    
    // Wait for the sensor to initialize
    delay(100);
}

float GY906::readObjectTempC() {
    return readTemp(0x07);
}

float GY906::readAmbientTempC() {
    return readTemp(0x06);
}

float GY906::readTemp(uint8_t reg) {
    Wire.beginTransmission(_address);
    Wire.write(reg);
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