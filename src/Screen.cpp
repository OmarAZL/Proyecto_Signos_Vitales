#include "Screen.h"

Screen::Screen(uint8_t SDA, uint8_t SCL, int width, int height)
    : display(Adafruit_SSD1306(width, height, &Wire, -1)), _SDA(SDA), _SCL(SCL) {
}

bool Screen::begin() {
    Wire.begin(_SDA, _SCL);
    return display.begin(SSD1306_SWITCHCAPVCC, 0x3C); // Default I2C address for SSD1306
}

void Screen::clear() {
    display.clearDisplay();
}

void Screen::showMessage(const String& message) {
    clear();
    display.setTextSize(1); // Normal size
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);
    display.print(message);
    display.display(); // Update the display with the new message
}

