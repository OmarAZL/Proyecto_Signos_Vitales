#include <Wire.h>

class GY906 {
public:
    GY906(uint8_t address = 0x5A);
    void begin();
    float readObjectTempC();
    float readAmbientTempC();
private:
    uint8_t _address;
};
