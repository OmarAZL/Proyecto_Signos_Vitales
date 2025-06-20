#include <Wire.h>

class MAX30100 {
public:
    MAX30100(uint8_t address = 0x57);
    void begin();
    bool readRaw(uint16_t &ir, uint16_t &red);
private:
    uint8_t _address;
};

