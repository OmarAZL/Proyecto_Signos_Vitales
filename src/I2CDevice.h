#include <Wire.h>

class I2CDevice {
public:
    I2CDevice(uint8_t address) : _address(address) {}
    virtual ~I2CDevice() = default;

    virtual bool isConnected() {
        Wire.beginTransmission(_address);
        return (Wire.endTransmission() == 0);
    }

protected:
    uint8_t _address;
};