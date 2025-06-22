#include "I2CDevice.h">

class MAX30100 : public I2CDevice {
public:
    MAX30100(uint8_t address) : I2CDevice(address) {}
    void begin();
    void setMode(uint8_t mode); // 0x02 = HR/BPM, 0x03 = SpO2
    bool readRaw(uint16_t &ir, uint16_t &red);
    float getBPM();
    float getSpO2();
};

