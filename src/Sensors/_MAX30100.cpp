#include "_MAX30100.h"

void MAX30100::begin() {
    Wire.beginTransmission(_address);
    Wire.write(0x06); //  Registro de modo
    Wire.write(0x03); // 0x02 = HR/BPM, 0x03 = SpO2
    Wire.endTransmission();
}

void MAX30100::setMode(uint8_t mode) {
    Wire.beginTransmission(_address);
    Wire.write(0x06); // Registro de modo
    Wire.write(mode); // 0x02 = HR/BPM, 0x03 = SpO2
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

float MAX30100::getBPM() {
    setMode(0x02); // Modo HR/BPM
    uint16_t ir, red;
    if (readRaw(ir, red)) {
        // Aquí deberías implementar el cálculo real de BPM usando ir/red
        // Por simplicidad, retornamos ir como ejemplo
        return (float)ir; // Reemplaza con tu algoritmo de BPM
    }
    return 0.0;
}

float MAX30100::getSpO2() {
    setMode(0x03); // Modo SpO2
    uint16_t ir, red;
    if (readRaw(ir, red)) {
        // Aquí deberías implementar el cálculo real de SpO2 usando ir/red
        // Por simplicidad, retornamos red como ejemplo
        return (float)red; // Reemplaza con tu algoritmo de SpO2
    }
    return 0.0;
}