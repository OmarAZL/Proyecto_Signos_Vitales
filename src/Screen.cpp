#include "Screen.h"

Screen::Screen(uint8_t address, int width, int height)
    : I2CDevice(address), display(Adafruit_SSD1306(width, height, &Wire, -1)) {
}

bool Screen::begin() {
    return display.begin(SSD1306_SWITCHCAPVCC, _address);
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

