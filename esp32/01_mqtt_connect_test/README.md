# ESP32 MQTT 连接测试 - 学习笔记

## 学习目标

通过这个 Demo 掌握 ESP32 与 MQTT Broker 建立连接的基本流程。

---

## MQTT 基础概念

### MQTT 是什么？

MQTT（Message Queuing Telemetry Transport）是一种轻量级的消息传输协议，专为物联网设备设计。它的核心特点是：

- **轻量** - 协议开销小，适合资源受限的嵌入式设备
- **发布/订阅模式** - 设备之间不直接通信，通过 Broker 中转
- **支持 QoS** - 可靠的消息传输保障

### 发布/订阅模式

```
设备A --发布消息--> Broker --转发消息--> 订阅了该主题的设备B
```

- **Broker** - 消息中间件（本项目使用 EMQX），负责接收和转发消息
- **Topic** - 主题，消息的分类标签，类似房间的概念
- **Publish** - 发布消息到某个主题
- **Subscribe** - 订阅某个主题，接收该主题的消息

### MQTT 端口

| 端口 | 用途 |
|-----|------|
| 1883 | 明文 TCP 连接（本项目使用） |
| 8883 | 加密 TLS 连接 |
| 8083 | WebSocket 连接 |

---

## 项目结构

```
01_mqtt_connect_test/
├── platformio.ini    # PlatformIO 项目配置
├── wokwi.toml        # Wokwi 模拟器配置
├── diagram.json      # Wokwi 电路图定义
├── src/
│   └── main.cpp      # 主程序
└── README.md         # 本文件
```

---

## LED 状态指示灯

本项目使用三个 LED 灯指示设备连接状态：

| LED 颜色 | GPIO 引脚 | 状态 | 含义 |
|---------|----------|------|------|
| 红色 | GPIO 2 | 闪烁 | 连接中（WiFi/MQTT） |
| 绿色 | GPIO 4 | 常亮 | 连接成功 |
| 黄色 | GPIO 5 | 闪烁 | 连接失败/错误 |

### 电路连接

```
ESP32 GPIO 2  → 220Ω 电阻 → 红色 LED → GND
ESP32 GPIO 4  → 220Ω 电阻 → 绿色 LED → GND
ESP32 GPIO 5  → 220Ω 电阻 → 黄色 LED → GND
```

### 状态机

```cpp
enum DeviceState {
    STATE_CONNECTING,  // 红灯闪烁
    STATE_CONNECTED,   // 绿灯常亮
    STATE_ERROR        // 黄灯闪烁
};
```

---

## 依赖库

### PubSubClient

ESP32 上最常用的 MQTT 客户端库，提供了简单的 API：

| 方法 | 作用 |
|-----|------|
| `setServer()` | 设置 MQTT Broker 地址 |
| `setCallback()` | 设置消息回调函数 |
| `connect()` | 连接到 Broker |
| `publish()` | 发布消息到主题 |
| `subscribe()` | 订阅主题 |
| `loop()` | 维护连接（必须频繁调用） |
| `connected()` | 检查是否已连接 |
| `state()` | 获取连接状态 |

---

## 代码执行流程

### 1. setup_wifi()

```cpp
WiFi.mode(WIFI_STA);      // Station 模式，连接到路由器
WiFi.begin(ssid, password); // 开始连接 WiFi
// 等待连接成功...
// WiFi 连接成功后会分配一个 IP 地址
```

ESP32 的 WiFi 模式：

| 模式 | 说明 |
|-----|------|
| `WIFI_STA` | Station 模式，连接到路由器（本项目使用） |
| `WIFI_AP` | Access Point 模式，ESP32 作为热点 |

### 2. MQTT 连接

```cpp
client.setServer(broker, port);  // 指定 Broker 地址
client.setCallback(callback);     // 注册消息回调
client.connect(clientId);         // 连接到 Broker
```

连接成功后：
- 发布上线消息 `"ESP32 online"` 到 `esp32/test`
- 订阅 `esp32/test` 主题，开始接收消息

### 3. 主循环 (loop)

```cpp
client.loop();  // 必须频繁调用，处理收发消息和保活

// 每 5 秒发布一次消息
if (now - lastMsgTime > 5000) {
    client.publish("esp32/test", "Hello from ESP32!");
}
```

`client.loop()` 的作用：
- 处理接收到的消息，触发 callback
- 发送心跳包（PINGREQ）保持连接
- 发送待发的消息

### 4. 消息回调 (callback)

```cpp
void callback(char* topic, byte* message, unsigned int length) {
    // topic: 消息来源的主题
    // message: 消息内容（字节数组）
    // length: 消息长度
}
```

---

## 常见问题

### Q1: MQTT 连接失败返回状态码是什么意思？

| 状态码 | 含义 |
|-------|------|
| -1 | 连接断开 |
| -4 | 连接超时 |
| -3 | 连接丢失 |
| -2 | 连接被拒绝（协议错误） |
| -1 | 未知错误 |

### Q2: 为什么必须频繁调用 client.loop()？

因为 MQTT 需要定期发送心跳包保持连接，如果不调用 loop()，Broker 会认为客户端已离线并断开连接。

### Q3: WiFi 模式为什么选 Station 而不是 AP？

- Station 模式：ESP32 作为客户端连接到家庭/办公路由器（本项目使用）
- AP 模式：ESP32 自己创建热点，其他设备连接它（常用于配网）

---

## 开发环境搭建

### 1. 安装 Python

本项目使用 PlatformIO，需要 Python 3.8+ 环境。

### 2. 安装 PlatformIO

```bash
python -m pip install platformio
```

### 3. 安装 VS Code 扩展

在 VS Code 扩展商店搜索并安装：
- **PlatformIO IDE** - ESP32 开发必备
- **Wokwi** - 在线模拟器（可选，用于无硬件测试）

---

## 编译项目

```bash
# 进入项目目录
cd esp32/01_mqtt_connect_test

# 编译
python -m platformio run
```

编译成功后，固件文件生成在：
```
.pio/build/esp32dev/firmware.bin
```

---

## 运行方式

### 方式 1: Wokwi 模拟器（无需硬件，推荐）

#### 步骤 1: 安装 MQTT Broker

在电脑上安装 Mosquitto（Windows/macOS/Linux 均可）：

**Windows：**
1. 下载安装 [Mosquitto](https://mosquitto.org/download/)
2. 启动服务：
   ```bash
   mosquitto -v
   ```

**macOS：**
```bash
brew install mosquitto
mosquitto -v
```

**Linux：**
```bash
sudo apt install mosquitto
mosquitto -v
```

#### 步骤 2: 修改代码配置

编辑 `src/main.cpp`，修改 MQTT Broker 地址：

```cpp
// Wokwi 模拟器使用 10.0.2.2 作为电脑的地址
const char* MQTT_BROKER = "10.0.2.2";
```

#### 步骤 3: 启动 Wokwi 模拟器

1. 在 VS Code 中打开本目录
2. 按 `F1`，选择 **Wokwi: Start Simulator**
3. 模拟器会自动编译并运行

#### Wokwi 配置文件说明

**wokwi.toml** - 模拟器配置：
```toml
[wokwi]
version = 1
firmware = '.pio/build/esp32dev/firmware.bin'

# 端口转发：将电脑的 1883 端口映射到 ESP32
[[net.forward]]
from = "localhost:1883"
to = "target:1883"
```

**diagram.json** - 电路图定义：
```json
{
  "version": 1,
  "parts": [
    {
      "type": "wokwi-esp32-devkit-v1",
      "id": "esp"
    }
  ]
}
```

#### 注意事项

- Wokwi 的 ESP32 WiFi 是虚拟的，通过端口转发与电脑通信
- `10.0.2.2` 是 Wokwi 虚拟网关地址，代表电脑本机
- 需要先启动 Mosquitto，再启动模拟器

---

### 方式 2: 真实硬件

#### 步骤 1: 准备硬件

- ESP32 开发板（如 ESP32-DevKitC）
- USB 数据线

#### 步骤 2: 连接电脑

用 USB 线连接 ESP32 到电脑。

#### 步骤 3: 上传固件

```bash
python -m platformio run --target upload
```

#### 步骤 4: 监控串口输出

```bash
python -m platformio device monitor
```

#### 硬件运行时的配置

如果使用真实硬件连接到家庭路由器：

```cpp
const char* WIFI_SSID = "YourWiFiSSID";
const char* WIFI_PASSWORD = "YourWiFiPassword";
const char* MQTT_BROKER = "192.168.1.100";  // 你的 MQTT Broker IP
```

---

## 测试验证

### 测试工具

| 工具 | 用途 | 下载地址 |
|-----|------|---------|
| Mosquitto | MQTT Broker | https://mosquitto.org/download/ |
| MQTT.fx | 图形化 MQTT 客户端 | https://mqttfx.jensd.de/ |
| mosquitto_sub | 命令行订阅工具 | 随 Mosquitto 安装 |

### 验证步骤（Wokwi 模拟器）

**终端 1：启动 MQTT Broker**
```bash
mosquitto -v
```

**终端 2：订阅消息（用于观察）**
```bash
mosquitto_sub -h 127.0.0.1 -t esp32/test
```

**终端 3：启动 Wokwi 模拟器**
1. VS Code 中按 `F1`
2. 选择 **Wokwi: Start Simulator**

**观察结果：**
- Wokwi 串口输出：`WiFi connected!` → `MQTT connected!` → `Publishing: Hello from ESP32!`
- mosquitto_sub 输出：ESP32 发送的消息

### 验证步骤（真实硬件）

1. 上传固件：`python -m platformio run --target upload`
2. 打开串口监视器：`python -m platformio device monitor`
3. 订阅消息：`mosquitto_sub -h 127.0.0.1 -t esp32/test`
4. 观察串口和订阅窗口的消息

---

## 下一步

完成本 Demo 后，可以继续学习：
- 传感器数据采集并上报
- 远程控制（通过指令控制 ESP32）
- 添加 TLS 加密连接
- 实现设备断线状态上报