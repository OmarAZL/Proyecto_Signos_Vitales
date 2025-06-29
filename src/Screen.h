#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "I2CDevice.h"

class Screen : public I2CDevice {
public:
    Screen(uint8_t address, int width, int height);
    bool begin();
    void clear();
    void showMessage(const String& message);
    void showAllSensors(float &temperature1, float &temperature2, float &ecg);
    Adafruit_SSD1306& getDisplay() { return display; }
private:
    Adafruit_SSD1306 display;
};
