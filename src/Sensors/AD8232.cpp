#include "AD8232.h"

AD8232::AD8232(uint8_t pinOUT, uint8_t pinLOplus, uint8_t pinLOminus)
    : _pinOUT(pinOUT), _pinLOplus(pinLOplus), _pinLOminus(pinLOminus) {}

void AD8232::begin() {
    pinMode(_pinOUT, INPUT);
    pinMode(_pinLOplus, INPUT);
    pinMode(_pinLOminus, INPUT);

    // Configurar la atenuación del ADC para el pin de salida del ECG
    // ADC_11db es adecuado para un rango de 0-2.5V o 0-3.1V, que es común para el AD8232 [8, 33]
    analogSetAttenuation(ADC_11db); 
    // Opcional: Establecer la resolución del ADC (por defecto es 12 bits) [8, 33]
    analogReadResolution(12);
    
}

int AD8232::readECG() {
    return analogRead(_pinOUT);
}

int AD8232::readLOplus() {
    return digitalRead(_pinLOplus);
}

int AD8232::readLOminus() {
    return digitalRead(_pinLOminus);
}

bool AD8232::electrodesConnected() {
    // Si cualquiera de los pines LO+ o LO- está en HIGH, los electrodos están desconectados
    return !(readLOplus() == HIGH || readLOminus() == HIGH);
}
