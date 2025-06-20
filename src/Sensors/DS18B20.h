#include <OneWire.h>
#include <DallasTemperature.h>

class DS18B20 {
public:
    DS18B20(uint8_t pin, uint8_t resolution);
    void begin();
    float getTemperature();
    bool isConnected();
private:
    uint8_t _pin;
    uint8_t _resolution;
    OneWire _oneWire;
    DallasTemperature _sensors;
};