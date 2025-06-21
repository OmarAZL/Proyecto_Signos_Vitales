#include <Wire.h>

class MAX30100 {
public:
    MAX30100(uint8_t SDA, uint8_t SCL, uint8_t address);
    void begin();
    bool readRaw(uint16_t &ir, uint16_t &red);
private:
    uint8_t _SDA;
    uint8_t _SCL;
    uint8_t _address;
};

