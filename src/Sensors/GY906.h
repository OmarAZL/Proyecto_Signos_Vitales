#include "I2CDevice.h"

class GY906 : public I2CDevice {
public:
    GY906(uint8_t address) : I2CDevice(address) {}
    void begin();
    float readObjectTempC();
    float readAmbientTempC();
};
