#include <OneWire.h>
#include <DallasTemperature.h>

class DS18B20 {
public:
    DS18B20(uint8_t pin, uint8_t resolution);
    void begin();
    float getTemperature();
    float getMinTemperature() const { return _minTemperature; }
    float getMaxTemperature() const { return _maxTemperature; }
    bool isConnected();
private:
    uint8_t _pin;
    uint8_t _resolution;
    OneWire _oneWire;
    DallasTemperature _sensors;
    float _minTemperature = 125.0; // temperatura Mínima (Se utiliza un valor elevado para la primera lectura)
    float _maxTemperature = -55.0; // temperatura Máxima (Se utiliza un valor bajo para la primera lectura)
};