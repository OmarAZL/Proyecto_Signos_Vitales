#include <Wire.h>

class GY906 {
public:
    GY906(uint8_t SDA, uint8_t SCL, uint8_t address);
    void begin();
    float readObjectTempC();
    float readAmbientTempC();
private:
    uint8_t _SDA;
    uint8_t _SCL;
    uint8_t _address;
};
