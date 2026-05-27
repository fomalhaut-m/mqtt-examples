# Spring Integration MQTT 数据采集器

## 📋 技术栈

- **Spring Integration MQTT** - Spring官方MQTT集成方案（企业级标准）
- **Eclipse Paho MQTT Client** - 成熟的MQTT客户端库
- **Spring Boot** - 应用框架

## ✅ 核心特性

- ✅ 纯官方依赖，无第三方starter
- ✅ 企业生产环境标准写法
- ✅ 自动订阅和消息处理
- ✅ 支持QoS配置
- ✅ 自动重连机制
- ✅ 线程安全的消息处理

## 🏗️ 架构说明

```
┌─────────────┐      MQTT       ┌──────────────────────┐
│  MQTT       │ ──────────────► │ MqttPahoMessage      │
│  Broker     │   device/+/     │ DrivenChannelAdapter │
│             │   reporter      │                      │
└─────────────┘                 └──────────┬───────────┘
                                           │
                                           ▼ (DirectChannel)
                                  ┌──────────────────────┐
                                  │ @ServiceActivator    │
                                  │ MqttMessageListener  │
                                  └──────────┬───────────┘
                                             │
                                             ▼
                                  ┌──────────────────────┐
                                  │ DeviceDataCollector  │
                                  │                      │
                                  │ - JSON解析           │
                                  │ - 数据缓存           │
                                  │ - 统计分析           │
                                  └──────────┬───────────┘
                                             │
                                             ▼
                                  ┌──────────────────────┐
                                  │ REST API             │
                                  │ CollectorController  │
                                  └──────────────────────┘
```

## 📦 项目结构

```
src/main/java/top/vexruna/simulator/
├── config/
│   └── MqttConfig.java              # MQTT配置类（核心）
├── collector/
│   ├── MqttMessageListener.java     # 消息监听器
│   ├── DeviceDataCollector.java     # 数据采集器
│   ├── CollectorController.java     # REST API控制器
│   └── model/
│       └── DeviceData.java          # 设备数据模型
└── SimulatorApplication.java        # 启动类（@EnableIntegration）
```

## ⚙️ 配置说明

### application.yml
```yaml
spring:
  mqtt:
    host: tcp://localhost:11883          # MQTT Broker地址
    client-id: mqtt-server-${random.value} # 客户端ID（随机）
    topic: device/+/reporter              # 订阅主题（支持通配符）
    qos: 1                                # QoS等级
    username: mqtt_user                   # 用户名（可选）
    password: mqtt_password               # 密码（可选）
```

## 🚀 快速开始

### 1. 确保Mosquitto服务运行
```bash
cd mqtt-example
docker-compose up -d
```

### 2. 启动Spring Boot应用
```bash
cd server
mvn spring-boot:run
```

### 3. 运行Python测试客户端（发送数据）
```bash
cd test-client
python mqtt_publisher.py
```

### 4. 查询采集的数据

#### 获取所有设备数据
```bash
curl http://localhost:8081/api/collector/devices
```

#### 获取指定设备数据
```bash
curl http://localhost:8081/api/collector/device/device-1
```

#### 获取统计信息
```bash
curl http://localhost:8081/api/collector/statistics
```

## 🔧 核心代码说明

### 1. MqttConfig.java - MQTT配置类
```java
@Configuration
public class MqttConfig {
    // 创建MQTT客户端工厂
    @Bean
    public MqttPahoClientFactory mqttClientFactory() { ... }
    
    // 创建消息通道
    @Bean
    public MessageChannel mqttInputChannel() { ... }
    
    // 创建入站适配器（订阅消息）
    @Bean
    public MessageProducer inbound(MqttPahoClientFactory factory) { ... }
}
```

### 2. MqttMessageListener.java - 消息监听器
```java
@Component
public class MqttMessageListener {
    // 官方标准写法：使用 @ServiceActivator
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(
            @Payload byte[] payload,
            @Header(MqttHeaders.RECEIVED_TOPIC) String topic,
            @Header(MqttHeaders.QOS) Integer qos
    ) {
        // 处理消息逻辑
    }
}
```

### 3. SimulatorApplication.java - 启动类
```java
@SpringBootApplication
@EnableIntegration // 必须添加此注解
public class SimulatorApplication { ... }
```

## 📊 API接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/collector/devices` | 获取所有设备最新数据 |
| GET | `/api/collector/device/{deviceId}` | 获取指定设备数据 |
| GET | `/api/collector/statistics` | 获取消息统计 |
| POST | `/api/collector/clear` | 清除缓存 |

## 🔍 常见问题

### Q1: 为什么不用 @MqttListener？
A: `@MqttListener` 是某些第三方starter提供的注解，不是Spring官方标准。企业生产环境推荐使用 `@ServiceActivator` + Spring Integration Channel 的标准写法。

### Q2: 如何添加更多订阅主题？
A: 在 `MqttConfig.inbound()` 方法中修改：
```java
new MqttPahoMessageDrivenChannelAdapter(clientId, factory, 
    "device/+/reporter", "device/+/status");
```

### Q3: 如何启用SSL/TLS？
A: 在 `MqttConfig.mqttClientFactory()` 中配置：
```java
options.setServerURIs(new String[]{"ssl://localhost:8883"});
// 配置证书...
```

## 🎯 优势对比

| 特性 | Spring Integration MQTT | 第三方Starter |
|------|------------------------|--------------|
| 官方支持 | ✅ Spring官方 | ❌ 社区维护 |
| 稳定性 | ✅ 企业级 | ⚠️ 不确定 |
| 文档完善 | ✅ 完整文档 | ⚠️ 文档少 |
| 长期维护 | ✅  guaranteed | ❌ 可能废弃 |
| 灵活性 | ✅ 高度可定制 | ⚠️ 受限于starter |

## 📝 总结

本方案采用**Spring官方推荐的Spring Integration MQTT**，是企业生产环境的首选方案：
- ✅ 零第三方依赖
- ✅ 完全可控
- ✅ 稳定可靠
- ✅ 易于扩展
