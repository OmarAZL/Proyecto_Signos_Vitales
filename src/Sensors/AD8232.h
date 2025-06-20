#include <Arduino.h>

class AD8232 {
public:
    AD8232(uint8_t pinOUT, uint8_t pinLOplus, uint8_t pinLOminus);
    void begin();
    int readECG();
    bool electrodesConnected();
private:
    uint8_t _pinOUT;
    uint8_t _pinLOplus;
    uint8_t _pinLOminus;
};

