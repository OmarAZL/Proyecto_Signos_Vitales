#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Wire.h>

class Screen {
public:
    Screen(uint8_t address, int width, int height);
    bool begin();
    void clear();
    void showMessage(const String& message);
private:
    Adafruit_SSD1306 display;
    uint8_t _address;
};