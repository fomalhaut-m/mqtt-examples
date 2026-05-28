/**
 * ============================================================
 *  ESP32 MQTT 连接测试 - 入门示例
 * ============================================================
 * 
 * 【文件作用】
 * 这个程序演示了 ESP32 如何：
 *   1. 连接到 WiFi 网络
 *   2. 连接到 MQTT Broker（消息服务器）
 *   3. 定时发布消息到指定主题（Topic）
 *   4. 订阅主题，接收消息
 *   5. 通过 LED 指示灯显示当前设备状态
 * 
 * 【硬件要求】
 *   - ESP32 开发板
 *   - 3 个 LED 灯（红、绿、黄）
 *   - 3 个 220Ω 电阻
 *   - SSD1306 OLED 显示屏（128x64，I2C 接口）
 * 
 * 【接线说明】
 *   OLED SDA → GPIO 21, OLED SCL → GPIO 22
 *   LED 红 → GPIO 2, LED 绿 → GPIO 4, LED 黄 → GPIO 5
 * 
 * 【LED 状态灯说明】
 *   - 红色 LED 闪烁 → 正在连接 WiFi / MQTT
 *   - 绿色 LED 常亮 → 一切正常，MQTT 已连接
 *   - 黄色 LED 闪烁 → 连接出错
 * 
 * 【依赖库】
 *   - WiFi.h            : ESP32 内置，用于连接 WiFi
 *   - PubSubClient      : 第三方 MQTT 客户端库，需要安装
 *   - Adafruit SSD1306  : OLED 显示屏驱动，需要安装
 *   - Adafruit GFX      : 图形基础库，需要安装
 */

// ============================================================
// 一、头文件引入
// ============================================================

#include <WiFi.h>          // ESP32 的 WiFi 库，提供 WiFi 连接功能
#include <PubSubClient.h>  // MQTT 客户端库，提供发布/订阅消息功能
#include <stdarg.h>        // C 标准库，提供 va_list 可变参数支持（日志函数使用）
#include <Wire.h>           // I2C 通信库，用于连接 OLED 显示屏
#include <Adafruit_GFX.h>   // Adafruit 图形库，提供绘图基础功能
#include <Adafruit_SSD1306.h> // SSD1306 OLED 显示屏驱动库


// ============================================================
// 二、WiFi 网络配置
// ============================================================
// 修改为你自己的 WiFi 名称和密码

const char* WIFI_SSID = "NYF_72";             // WiFi 名称（SSID）
const char* WIFI_PASSWORD = "woaiwojia@2019"; // WiFi 密码


// ============================================================
// 三、MQTT Broker 连接配置
// ============================================================
// MQTT Broker 就像"消息快递中转站"，所有设备都通过它收发消息
// 10.0.2.2 是 Wokwi 模拟器中的特殊地址，表示"宿主机"
// 真实设备中改为你的 MQTT 服务器 IP（如 192.168.1.100）
// 
// 端口说明：Docker 映射 11883→1883，所以连 11883
// 认证信息来自 certs/pwfile（mqtt_user / public）

const char* MQTT_BROKER = "10.0.2.2";       // MQTT 服务器地址
const int   MQTT_PORT = 11883;              // MQTT 端口号（Docker 宿主机映射端口）

const char* MQTT_USERNAME = "mqtt_user";    // MQTT 用户名
const char* MQTT_PASSWORD = "public";       // MQTT 密码

const char* MQTT_CLIENT_ID = "esp32-mqtt-test";  // 客户端 ID，在 Broker 中必须唯一
const char* MQTT_TOPIC = "esp32/test";           // 要发布/订阅的主题名称
const char* MQTT_LOG_TOPIC = "esp32/log";        // 日志专用主题：所有日志通过 MQTT 发布到这里
// 主题（Topic）就像"聊天频道名"，发布者和订阅者通过同一个主题名通信


// ============================================================
// 四、LED 引脚定义
// ============================================================
// GPIO 引脚编号，对应 ESP32 开发板上的物理引脚

const int LED_RED    = 2;  // 红色 LED 连接到 GPIO 2
const int LED_GREEN  = 4;  // 绿色 LED 连接到 GPIO 4
const int LED_YELLOW = 5;  // 黄色 LED 连接到 GPIO 5


// ============================================================
// 五、全局对象创建
// ============================================================

WiFiClient espClient;          // 创建一个 WiFi 客户端对象
PubSubClient client(espClient);// 创建 MQTT 客户端对象，它依赖 WiFiClient 进行网络通信

// OLED 显示屏对象（I2C 地址 0x3C，128x64 像素）
// SDA → GPIO 21, SCL → GPIO 22
#define OLED_ADDR 0x3C
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);


// ============================================================
// 六、定时器变量
// ============================================================
// unsigned long 是 32 位无符号整数，最大约 4294967295（约 50 天）
// millis() 函数返回自程序启动以来的毫秒数，用于非阻塞定时

unsigned long lastMsgTime = 0;    // 上一次发布消息的时间戳
unsigned long lastBlinkTime = 0;  // 上一次 LED 闪烁的时间戳
bool ledState = false;            // LED 当前状态（true=亮, false=灭）
unsigned long g_loopCount = 0;    // 主循环计数器（全局，供 OLED 显示使用）

// 时间间隔宏定义（毫秒）
#define MSG_INTERVAL   5000  // 消息发布间隔：每 5 秒发一次
#define BLINK_INTERVAL 500   // LED 闪烁间隔：每 0.5 秒翻转一次


// ============================================================
// 七、设备状态枚举
// ============================================================
// 用枚举（enum）定义设备可能的状态，让代码更易读
// 每种状态对应不同的 LED 显示效果

enum DeviceState {
    STATE_CONNECTING,  // 正在连接中（WiFi 或 MQTT）
    STATE_CONNECTED,   // 已连接成功
    STATE_ERROR        // 连接出错
};

DeviceState currentState = STATE_CONNECTING;  // 记录当前设备状态，初始为"连接中"


// ============================================================
// 八、日志系统
// ============================================================

/**
 * logOutput - 统一日志输出函数
 * 
 * 日志同时输出到两个通道：
 *   1. Serial（串口）— Wokwi 串口可能有 bug，不保证能看到
 *   2. MQTT（esp32/log 主题）— 用 mosquitto_sub 命令在电脑上接收
 * 
 * 接收 MQTT 日志的命令（在电脑终端运行）：
 *   mosquitto_sub -h localhost -p 1883 -t "esp32/log"
 */
void logOutput(const char* level, const char* fmt, ...) {
    char buf[256];

    // ---- 格式化时间戳 + 标签前缀 ----
    int prefixLen = snprintf(buf, sizeof(buf), "[%8lu][%-5s] ", millis(), level);

    // ---- 格式化用户消息 ----
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf + prefixLen, sizeof(buf) - prefixLen, fmt, args);
    va_end(args);

    // ---- 通道1：串口输出（即使 Wokwi 不显示，也要发） ----
    Serial.println(buf);

    // ---- 通道2：MQTT 输出（如果已连接） ----
    // Wokwi 的端口转发会把 localhost:1883 转发到 Wokwi 内部
    if (client.connected()) {
        client.publish(MQTT_LOG_TOPIC, buf);
    }
}

/**
 * LOG 宏 - 统一日志输出入口
 * 
 * 用法示例：
 *   LOG("WIFI", "WiFi 已连接, IP: %s", ip);
 * 
 * 输出格式（串口和 MQTT 相同）：
 *   [    1234][WIFI ] WiFi 已连接, IP: 192.168.1.100
 */
#define LOG(level, fmt, ...) \
    logOutput(level, fmt, ##__VA_ARGS__)


// ============================================================
// 九、状态切换函数
// ============================================================

/**
 * setState - 切换设备状态
 * 
 * 只有当新状态与当前状态不同时，才会：
 *   1. 更新 currentState
 *   2. 打印状态切换日志
 * 
 * 为什么需要这个函数：
 *   直接修改 currentState 不会打印日志，有了这个函数，
 *   就能在串口监视器中看到状态变化的时间线，方便调试。
 */
void setState(DeviceState newState) {
    if (newState != currentState) {
        DeviceState oldState = currentState;
        currentState = newState;
        // 状态名称的中文对照表
        const char* stateNames[] = {"连接中", "已连接", "错误"};
        LOG("状态", "%s -> %s", stateNames[oldState], stateNames[currentState]);
    }
}


// ============================================================
// 十、LED 状态指示函数
// ============================================================

/**
 * updateLED - 更新三个 LED 的显示状态
 * 
 * 这个函数在 loop() 中每个循环都会被调用。
 * 它根据 currentState 的值来决定哪个 LED 亮：
 * 
 *   STATE_CONNECTING → 红色 LED 闪烁（每隔 BLINK_INTERVAL 毫秒翻转）
 *   STATE_CONNECTED  → 绿色 LED 常亮
 *   STATE_ERROR      → 黄色 LED 闪烁
 * 
 * 注意：每次调用都会先把所有 LED 拉低（熄灭），
 * 然后再根据状态点亮对应的那个。这确保了同一时刻只有一个 LED 亮。
 */
void updateLED() {
    unsigned long now = millis();

    // ---- 每隔 BLINK_INTERVAL 毫秒翻转 ledState ----
    // 这个 if 是实现"闪烁"效果的关键
    if (now - lastBlinkTime >= BLINK_INTERVAL) {
        lastBlinkTime = now;         // 记录本次翻转时间
        ledState = !ledState;        // 翻转：true → false 或 false → true
        LOG("LED", "闪烁切换 -> %s     (状态=%s)",
            ledState ? "亮" : "灭",
            currentState == STATE_CONNECTING ? "连接中" :
            currentState == STATE_CONNECTED   ? "已连接" : "错误");
    }

    // ---- 先关闭所有 LED（避免亮多个） ----
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_GREEN, LOW);
    digitalWrite(LED_YELLOW, LOW);

    // ---- 根据当前状态点亮对应的 LED ----
    switch (currentState) {
        case STATE_CONNECTING:
            // 连接中：红色 LED 闪烁
            // ledState 每 500ms 翻转，所以红灯看起来一闪一闪
            digitalWrite(LED_RED, ledState ? HIGH : LOW);
            break;
        case STATE_CONNECTED:
            // 已连接：绿色 LED 常亮
            digitalWrite(LED_GREEN, HIGH);
            break;
        case STATE_ERROR:
            // 出错：黄色 LED 闪烁
            digitalWrite(LED_YELLOW, ledState ? HIGH : LOW);
            break;
    }
}


// ============================================================
// 十一、WiFi 连接函数
// ============================================================

/**
 * setup_wifi - 初始化 WiFi 网络
 * 
 * Wokwi 模拟器中 WiFi 是虚拟的，不需要真实的 SSID/密码。
 * 调用 WiFi.begin() 即可获得 Wokwi 分配的虚拟 IP 地址。
 * 
 * 真实硬件上，需要传入实际的 SSID 和密码。
 */
void setup_wifi() {
    LOG("WIFI", "========== setup_wifi() 开始 ==========");

    setState(STATE_CONNECTING);
    updateLED();

    WiFi.mode(WIFI_STA);
    LOG("WIFI", "WiFi.mode(WIFI_STA) 完成");

    // Wokwi 模拟器：不传 SSID 也能获得虚拟 IP
    // 真实硬件：改为 WiFi.begin(WIFI_SSID, WIFI_PASSWORD)
    WiFi.begin();
    LOG("WIFI", "WiFi.begin() 已调用, 等待连接...");

    // ---- 轮询等待连接成功 ----
    int timeout = 0;
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        int currentStatus = WiFi.status();
        LOG("WIFI", "状态=%d  超时计数=%d/40", currentStatus, timeout);
        updateLED();
        timeout++;
        if (timeout > 40) {
            LOG("WIFI", "*** 超时! WiFi 连接失败 (已等待 20 秒) ***");
            LOG("WIFI", "最终 WiFi.status() = %d", WiFi.status());
            setState(STATE_ERROR);
            updateLED();
            LOG("WIFI", "========== setup_wifi() 结束 (失败) ==========");
            return;
        }
    }

    LOG("WIFI", "*** WiFi 连接成功! ***");
    LOG("WIFI", "本机 IP  : %s", WiFi.localIP().toString().c_str());
    LOG("WIFI", "网关 IP  : %s", WiFi.gatewayIP().toString().c_str());
    LOG("WIFI", "子网掩码 : %s", WiFi.subnetMask().toString().c_str());
    LOG("WIFI", "DNS      : %s", WiFi.dnsIP().toString().c_str());
    LOG("WIFI", "信号强度 : %d dBm", WiFi.RSSI());
    LOG("WIFI", "========== setup_wifi() 结束 (成功) ==========");
}


// ============================================================
// 十二、MQTT 消息回调函数
// ============================================================

/**
 * callback - 收到 MQTT 消息时的回调函数
 * 
 * 这是 PubSubClient 库的回调机制：
 *   当订阅的主题上有新消息到达时，client.loop() 会自动调用这个函数。
 * 
 * 参数说明：
 *   topic  : 消息来自哪个主题（字符串）
 *   message: 消息体（字节数组，可能是二进制数据）
 *   length : 消息体的字节长度
 * 
 * 注意：
 *   - message 不一定是 C 字符串，不一定以 '\0' 结尾
 *   - topic 是 C 字符串，以 '\0' 结尾
 *   - 这个函数在 client.loop() 的执行上下文中调用，不要在里面做耗时操作
 */
void callback(char* topic, byte* message, unsigned int length) {
    LOG("回调", ">>>>> 收到消息 <<<<<");
    LOG("回调", "主题  : %s", topic);
    LOG("回调", "长度  : %u 字节", length);

    // ---- 将消息体复制到缓冲区，确保以 '\0' 结尾 ----
    // 因为 message 是原始字节数组，直接当字符串打印不安全
    char buf[256];
    unsigned int copyLen = length < 255 ? length : 255;  // 最多复制 255 字节
    memcpy(buf, message, copyLen);                        // 逐字节复制
    buf[copyLen] = '\0';                                  // 添加字符串结束符
    LOG("回调", "内容: \"%s\"", buf);

    // 如果消息太长，提示已截断
    if (length > 255) {
        LOG("回调", "(内容已截断, 实际长度=%u)", length);
    }
}


// ============================================================
// 十三、MQTT 重连函数
// ============================================================

/**
 * reconnect - 连接 MQTT Broker，并完成订阅
 * 
 * 当 MQTT 连接断开时，loop() 会调用这个函数来重新连接。
 * 
 * 执行流程：
 *   1. 尝试 client.connect() 连接 Broker
 *   2. 成功后发布一条"上线"消息
 *   3. 订阅主题，接收指令
 *   4. 失败则等 5 秒后重试（while 循环会自动重试）
 * 
 * client.connect() 返回值：
 *   true  → 连接成功
 *   false → 连接失败，通过 client.state() 获取具体错误码
 */
void reconnect() {
    LOG("MQTT", "========== reconnect() 开始 ==========");
    LOG("MQTT", "client.connected() = %s", client.connected() ? "是" : "否");
    LOG("MQTT", "Broker: %s:%d", MQTT_BROKER, MQTT_PORT);
    LOG("MQTT", "客户端ID: \"%s\"", MQTT_CLIENT_ID);

    setState(STATE_CONNECTING);

    // ---- 循环直到连接成功 ----
    // 如果连接失败，等待 5 秒后会自动回到 while 开头重试
    while (!client.connected()) {
        LOG("MQTT", "调用 client.connect(\"%s\")...", MQTT_CLIENT_ID);
        updateLED();

        // 尝试连接到 MQTT Broker（带用户名密码认证）
        if (client.connect(MQTT_CLIENT_ID, MQTT_USERNAME, MQTT_PASSWORD)) {
            // ========== 连接成功 ==========
            LOG("MQTT", "*** client.connect() 成功 ***");
            LOG("MQTT", "MQTT 服务器: %s:%d", MQTT_BROKER, MQTT_PORT);
            setState(STATE_CONNECTED);
            updateLED();

            // --- 发送上线消息 ---
            // 发布（Publish）：向某个主题发送消息
            // 格式：client.publish("主题名", "消息内容")
            LOG("MQTT", "发布上线消息到 \"%s\"...", MQTT_TOPIC);
            if (client.publish(MQTT_TOPIC, "ESP32 online")) {
                LOG("MQTT", "发布 \"ESP32 online\" -> 成功");
            } else {
                LOG("MQTT", "发布 \"ESP32 online\" -> 失败");
            }

            // --- 订阅主题 ---
            // 订阅（Subscribe）：告诉 Broker "我对这个主题的消息感兴趣"
            // 之后当有人向这个主题发消息时，callback() 会被自动调用
            LOG("MQTT", "订阅主题 \"%s\"...", MQTT_TOPIC);
            if (client.subscribe(MQTT_TOPIC)) {
                LOG("MQTT", "订阅 \"%s\" -> 成功", MQTT_TOPIC);
            } else {
                LOG("MQTT", "订阅 \"%s\" -> 失败", MQTT_TOPIC);
            }

        } else {
            // ========== 连接失败 ==========
            int mqttState = client.state();
            LOG("MQTT", "*** client.connect() 失败! rc=%d ***", mqttState);

            // client.state() 返回的常见错误码解释
            // 负数通常是网络层面的问题，正数是 MQTT 协议层面的拒绝
            switch (mqttState) {
                case -4: LOG("MQTT", "原因: 连接超时"); break;
                case -3: LOG("MQTT", "原因: 连接丢失"); break;
                case -2: LOG("MQTT", "原因: 连接失败"); break;
                case -1: LOG("MQTT", "原因: 已断开"); break;
                case  1: LOG("MQTT", "原因: 协议错误"); break;
                case  2: LOG("MQTT", "原因: 客户端ID不合法"); break;
                case  3: LOG("MQTT", "原因: 服务不可用"); break;
                case  4: LOG("MQTT", "原因: 凭证错误"); break;
                case  5: LOG("MQTT", "原因: 未授权"); break;
                default: LOG("MQTT", "原因: 未知 (%d)", mqttState); break;
            }

            setState(STATE_ERROR);
            updateLED();

            // 连接失败后等待 5 秒再重试，避免疯狂重连
            LOG("MQTT", "等待 5 秒后重试...");
            delay(5000);
        }
    }
    LOG("MQTT", "========== reconnect() 结束 ==========");
}


// ============================================================
// 十四、OLED 显示屏刷新函数
// ============================================================

/**
 * refreshDisplay - 刷新 OLED 屏幕显示
 * 
 * 在 128x64 的 OLED 屏幕上显示 5 行信息：
 *   第 1 行：标题
 *   第 2 行：WiFi 状态 + IP 地址
 *   第 3 行：MQTT 状态
 *   第 4 行：主题 + 运行时间
 *   第 5 行：循环次数 + 可用内存
 * 
 * 每 500ms 调用一次，不会闪烁。
 */
void refreshDisplay() {
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);

    // 第 1 行：标题
    display.println("== ESP32 MQTT Test ==");

    // 第 2 行：WiFi 状态
    if (WiFi.status() == WL_CONNECTED) {
        display.print("WiFi: OK  ");
        display.println(WiFi.localIP().toString());
    } else {
        display.println("WiFi: connecting...");
    }

    // 第 3 行：MQTT 状态
    display.print("MQTT: ");
    if (client.connected()) {
        display.println("connected");
    } else {
        const char* stateNames[] = {"CONNECTING", "CONNECTED", "ERROR"};
        display.println(stateNames[currentState]);
    }

    // 第 4 行：主题 + 运行秒数
    display.print("Topic: ");
    display.print(MQTT_TOPIC);
    display.print("  ");
    display.print(millis() / 1000);
    display.println("s");

    // 第 5 行：循环计数 + 内存
    display.print("Loop: ");
    display.print(g_loopCount);
    display.print("  Mem: ");
    display.print(ESP.getFreeHeap() / 1024);
    display.println("KB");

    display.display();
}


// ============================================================
// 十五、setup() - 程序初始化，只执行一次
// ============================================================

/**
 * setup() - Arduino 框架的初始化函数
 * 
 * 程序上电或重启后只执行一次，用于：
 *   1. 初始化串口通信（用于打印日志）
 *   2. 打印系统信息
 *   3. 初始化 LED 引脚
 *   4. 连接 WiFi
 *   5. 配置 MQTT 客户端
 * 
 * 注意：setup() 中不包含 MQTT 连接操作，
 * 实际连接在 loop() 第一次运行时由 reconnect() 完成。
 */
void setup() {
    // ---- 初始化串口 ----
    // 115200 是波特率（每秒传输的比特数），必须和串口监视器中的设置一致
    Serial.begin(115200);
    delay(1000);  // 等待串口稳定

    // ---- 串口基本测试 ----
    // 用最底层的方式验证串口是否工作
    // ets_printf 是 ESP32 ROM 内置函数，不依赖 Arduino 框架
    ets_printf("=== 系统上电 (ets_printf) ===\n");
    Serial.println("================================================");
    Serial.println("  [串口测试] 如果你看到这行，说明串口正常!");
    Serial.println("================================================");

    // ---- 打印启动信息 ----
    LOG("初始化", "");
    LOG("初始化", "==============================================");
    LOG("初始化", "   ESP32 MQTT 连接测试");
    LOG("初始化", "==============================================");
    LOG("初始化", "可用内存 : %u 字节", ESP.getFreeHeap());
    LOG("初始化", "芯片型号 : %s rev %d", ESP.getChipModel(), ESP.getChipRevision());
    LOG("初始化", "SDK 版本 : %s", ESP.getSdkVersion());
    LOG("初始化", "==============================================");
    LOG("初始化", "WiFi SSID  : \"%s\"", WIFI_SSID);
    LOG("初始化", "MQTT Broker: %s:%d", MQTT_BROKER, MQTT_PORT);
    LOG("初始化", "MQTT 主题  : \"%s\"", MQTT_TOPIC);
    LOG("初始化", "客户端ID   : \"%s\"", MQTT_CLIENT_ID);
    LOG("初始化", "LED 引脚   : 红=%d 绿=%d 黄=%d", LED_RED, LED_GREEN, LED_YELLOW);
    LOG("初始化", "==============================================");

    // ---- 初始化 LED 引脚 ----
    // pinMode(引脚, 模式):
    //   OUTPUT = 输出模式（控制 LED 亮/灭）
    //   INPUT  = 输入模式（读取传感器等）
    LOG("初始化", "初始化 LED 引脚...");
    pinMode(LED_RED, OUTPUT);
    pinMode(LED_GREEN, OUTPUT);
    pinMode(LED_YELLOW, OUTPUT);
    LOG("初始化", "pinMode 完成, 所有 LED 熄灭");

    // 确保初始状态所有 LED 都是灭的
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_GREEN, LOW);
    digitalWrite(LED_YELLOW, LOW);

    setState(STATE_CONNECTING);
    updateLED();

    // ---- 初始化 OLED 显示屏 ----
    LOG("初始化", "初始化 OLED 显示屏 (I2C 0x3C)...");
    Wire.begin(21, 22);
    if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR)) {
        LOG("初始化", "*** OLED 初始化失败! 检查 I2C 接线 ***");
    } else {
        LOG("初始化", "OLED 初始化成功");
        display.clearDisplay();
        display.setTextSize(2);
        display.setTextColor(SSD1306_WHITE);
        display.setCursor(10, 20);
        display.println("ESP32 MQTT");
        display.display();
        delay(1000);
    }

    // ---- 连接 WiFi ----
    LOG("初始化", "开始 WiFi 连接...");
    setup_wifi();

    // ---- 配置 MQTT 客户端 ----
    // 注意：这里只是"配置"，实际连接在 loop() 里由 reconnect() 完成
    LOG("初始化", "配置 MQTT 服务器: %s:%d", MQTT_BROKER, MQTT_PORT);
    client.setServer(MQTT_BROKER, MQTT_PORT);  // 设置 Broker 地址和端口
    LOG("初始化", "client.setServer() 完成");

    LOG("初始化", "设置 MQTT 回调...");
    client.setCallback(callback);  // 设置收到消息时的回调函数
    LOG("初始化", "client.setCallback() 完成");

    // 设置 MQTT 消息缓冲区大小（默认 256，设置为 512）
    LOG("初始化", "设置缓冲区大小为 512");
    client.setBufferSize(512);
    LOG("初始化", "client.setBufferSize(512) 完成");

    LOG("初始化", "========== setup() 完成 ==========");
}


// ============================================================
// 十五、loop() - 主循环，反复执行
// ============================================================

/**
 * loop() - Arduino 框架的主循环函数
 * 
 * setup() 执行完毕后，loop() 会被反复调用，永不停止。
 * 每一次循环执行以下操作：
 * 
 *   1. 更新 LED 显示
 *   2. 检查 MQTT 是否连接，断了就重连
 *   3. 调用 client.loop() 处理 MQTT 收/发（关键！）
 *   4. 检查是否到了发布消息的时间，到了就发一条
 *   5. 每 10 秒打印一次心跳日志
 * 
 * 【重要】client.loop() 必须被频繁调用，否则：
 *   - 收不到订阅的消息
 *   - 连接心跳无法维持，会被 Broker 踢掉
 *   - 发布操作也无法完成
 */
void loop() {
    // ---- 局部静态变量 ----
    // static 变量在函数返回后保留值，下次调用时不会重新初始化
    static unsigned long lastLoopLog = 0;      // 上次心跳日志时间
    static unsigned long lastDisplayRefresh = 0; // 上次 OLED 刷新时间

    g_loopCount++;  // 每次循环 +1

    // ---- 1. 更新 LED 状态 ----
    updateLED();

    // ---- 2. 刷新 OLED 显示屏（每 500ms） ----
    unsigned long now = millis();
    if (now - lastDisplayRefresh >= 500) {
        lastDisplayRefresh = now;
        refreshDisplay();
    }

    // ---- 3. 检查 MQTT 连接状态 ----
    if (!client.connected()) {
        LOG("主循环", "MQTT 已断开! (loopCount=%lu) 触发重连...", g_loopCount);
        reconnect();  // 内部会阻塞，直到连接成功
        LOG("主循环", "reconnect() 已返回, 继续主循环...");
    }

    // ---- 4. MQTT 消息处理 ----
    bool loopResult = client.loop();
    if (!loopResult) {
        static unsigned long lastLoopFailLog = 0;
        if (millis() - lastLoopFailLog > 10000) {
            lastLoopFailLog = millis();
            LOG("主循环", "client.loop() 返回 false (10秒内不再重复)");
        }
    }

    // ---- 5. 心跳日志（每 10 秒） ----
    if (now - lastLoopLog >= 10000) {
        lastLoopLog = now;
        LOG("主循环", "心跳  loopCount=%lu  内存=%u  WiFi=%d  MQTT=%s",
            g_loopCount, ESP.getFreeHeap(), WiFi.status(),
            client.connected() ? "已连接" : "已断开");
    }

    // ---- 6. 定时发布消息（每 MSG_INTERVAL 毫秒） ----
    if (now - lastMsgTime > MSG_INTERVAL) {
        lastMsgTime = now;

        // 构造消息体
        String payload = "Hello from ESP32! Uptime: ";
        payload += String(now / 1000);
        payload += "s";

        LOG("发布", "发布到 \"%s\": \"%s\"", MQTT_TOPIC, payload.c_str());

        if (client.publish(MQTT_TOPIC, payload.c_str())) {
            LOG("发布", "发布 -> 成功 (loopCount=%lu)", g_loopCount);
        } else {
            LOG("发布", "发布 -> 失败 (loopCount=%lu)", g_loopCount);
            LOG("发布", "MQTT 连接=%s  WiFi 状态=%d",
                client.connected() ? "是" : "否", WiFi.status());
        }
    }
}
