/**
 * 00_LED_Blink — ESP32 最入门示例
 * 
 * 功能：让红色 LED 每隔 1 秒闪烁一次
 * 
 * 硬件：1 个 LED + 1 个 220Ω 电阻
 * 接线：GPIO 2 → 电阻 → LED 阳极, LED 阴极 → GND
 */
#include <Arduino.h>

const int LED_PIN = 2;   // LED 接在 GPIO 2

void setup() {
    // 设置 GPIO 2 为输出模式
    pinMode(LED_PIN, OUTPUT);
}

void loop() {
    digitalWrite(LED_PIN, HIGH);  // 点亮 LED
    delay(1000);                   // 等 1 秒
    digitalWrite(LED_PIN, LOW);   // 熄灭 LED
    delay(1000);                   // 等 1 秒
}
