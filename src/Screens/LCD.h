#include <LiquidCrystal.h>

class LCD {
public:
    LCD(uint8_t rs, uint8_t enable, uint8_t d4, uint8_t d5, uint8_t d6, uint8_t d7);;
    void begin();
    void clear();
    void printl0(const String& message);
    void printl1(const String& message);
    void print(const String& message);
    LiquidCrystal& getLCD() { return lcd; }
private:
    LiquidCrystal lcd;
};