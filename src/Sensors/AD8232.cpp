#include "AD8232.h"

AD8232::AD8232(uint8_t pinOUT, uint8_t pinLOplus, uint8_t pinLOminus)
    : _pinOUT(pinOUT), _pinLOplus(pinLOplus), _pinLOminus(pinLOminus) {}

void AD8232::begin() {
    pinMode(_pinOUT, INPUT);
    pinMode(_pinLOplus, INPUT);
    pinMode(_pinLOminus, INPUT);
}

int AD8232::readECG() {
    return analogRead(_pinOUT);
}

bool AD8232::electrodesConnected() {
    // Si cualquiera de los pines LO+ o LO- está en HIGH, los electrodos están desconectados
    return !(digitalRead(_pinLOplus) || digitalRead(_pinLOminus));
}
