#include "Screen.h"

Screen::Screen(uint8_t SDL, uint8_t SCL, int width, int height)
    : display(Adafruit_SSD1306(width, height, &Wire, -1)), _SDL(SDL), _SCL(SCL) {
}

bool Screen::begin() {
    Wire.begin(_SDL, _SCL);
    return display.begin(SSD1306_SWITCHCAPVCC, 0x3C); // Default I2C address for SSD1306
}

void Screen::clear() {
    display.clearDisplay();
}

void Screen::showMessage(const String& message) {
    display.setTextSize(1); // Normal size
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);
    display.print(message);
    display.display(); // Update the display with the new message
}

