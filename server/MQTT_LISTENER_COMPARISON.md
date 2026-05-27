# MQTT 监听器方案对比

本项目实现了三种不同的 MQTT 消息监听方案，各有优劣。

## 📊 方案对比

| 特性 | Spring Integration | Mica MQTT | 原生 Paho |
|------|-------------------|-----------|----------|
| **依赖** | spring-integration-mqtt | mica-mqtt-spring-boot-starter | paho-client-mqttv3 |
| **注解支持** | @ServiceActivator + condition | @MqttListener ✅ | 无 |
| **主题过滤** | SpEL 表达式 | 注解直接指定 ✅ | 手动判断 |
| **代码简洁度** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **学习曲线** | 中等 | 简单 ✅ | 较陡 |
| **国内支持** | 官方支持 | 中文文档+社区 ✅ | 国际社区 |
| **自动重连** | 需配置 | 自动支持 ✅ | 需手动实现 |
| **性能** | 好 | 优秀 ✅ | 好 |
| **灵活性** | 高 | 中高 | 最高 |

## 🎯 推荐方案

### ✅ 推荐使用：Mica MQTT

**理由：**
1. **最简洁** - 使用 `@MqttListener` 注解，一行代码搞定主题订阅
2. **国内稳定** - 由国内团队维护，中文文档完善，响应及时
3. **功能完整** - 自动处理连接、重连、心跳等
4. **易于维护** - 代码清晰，主题和方法一一对应

**示例代码：**
```java
@MqttListener(topic = "device/+/reporter", qos = 1)
public void handleDeviceReporter(String topic, byte[] payload, int qos) {
    // 直接处理，无需手动过滤
    deviceDataCollector.processDeviceData(topic, new String(payload), qos);
}
```

## 📝 三种方案详解

### 方案1：Spring Integration MQTT（官方标准）

**文件：** `DeviceReporterListener.java`

**特点：**
- Spring 官方集成方案
- 使用 `@ServiceActivator` + SpEL 表达式过滤主题
- 企业级标准，稳定性高

**优点：**
- ✅ Spring 官方支持
- ✅ 与 Spring 生态完美集成
- ✅ 功能强大，可扩展性好

**缺点：**
- ❌ 配置相对复杂
- ❌ 需要理解 Channel、Adapter 等概念
- ❌ 主题过滤需要使用 SpEL 表达式

**适用场景：**
- 大型企业项目
- 需要深度定制 MQTT 行为
- 团队熟悉 Spring Integration

**代码示例：**
```java
@ServiceActivator(
    inputChannel = "mqttInputChannel",
    condition = "headers['mqtt_receivedTopic'] matches 'device/.+/reporter'"
)
public void handleDeviceReporter(
    @Payload String payload,
    @Header("mqtt_receivedTopic") String topic,
    @Header(value = "mqtt_receivedQos", required = false, defaultValue = "0") Integer qos
) {
    // 处理逻辑
}
```

### 方案2：Mica MQTT（推荐 ⭐）

**文件：** `MicaMqttListener.java`

**特点：**
- 国内最流行的 MQTT Spring Boot Starter
- 使用 `@MqttListener` 注解，极其简洁
- 中文文档完善，社区活跃

**优点：**
- ✅ 代码最简洁
- ✅ 注解式开发，直观易懂
- ✅ 自动管理连接和重连
- ✅ 中文文档和社区支持
- ✅ 性能优秀

**缺点：**
- ❌ 第三方库（但非常稳定）
- ❌ 自定义能力略低于原生 Paho

**适用场景：**
- **大多数 Spring Boot 项目（强烈推荐）**
- 快速开发
- 团队希望简化 MQTT 集成

**代码示例：**
```java
@MqttListener(topic = "device/+/reporter", qos = 1)
public void handleDeviceReporter(String topic, byte[] payload, int qos) {
    // 直接处理，主题已在注解中指定
    deviceDataCollector.processDeviceData(topic, new String(payload), qos);
}

@MqttListener(topic = "device/+/status", qos = 0)
public void handleDeviceStatus(String topic, byte[] payload, int qos) {
    // 不同主题可以用不同方法处理
    log.info("设备状态: {}", new String(payload));
}
```

### 方案3：原生 Paho Client

**文件：** `MqttMessageListener.java`

**特点：**
- 直接使用 Eclipse Paho 客户端
- 完全手动控制
- 最灵活但也最复杂

**优点：**
- ✅ 完全控制所有细节
- ✅ 无额外抽象层
- ✅ 适合特殊需求

**缺点：**
- ❌ 代码量大
- ❌ 需要手动管理连接、重连
- ❌ 需要手动过滤主题
- ❌ 容易出错

**适用场景：**
- 需要完全控制 MQTT 行为
- 特殊协议需求
- 学习 MQTT 底层原理

**代码示例：**
```java
@ServiceActivator(inputChannel = "mqttInputChannel")
public void handleMessage(Message<?> message) {
    MessageHeaders headers = message.getHeaders();
    String topic = (String) headers.get("mqtt_receivedTopic");
    
    // 需要手动判断主题
    if (topic == null || !topic.matches("device/.+/reporter")) {
        return;
    }
    
    // 手动提取参数
    Object payload = message.getPayload();
    String payloadStr = payload instanceof byte[] ? new String((byte[]) payload) : payload.toString();
    
    // 处理逻辑
}
```

## 🔧 配置对比

### Spring Integration 配置
```yaml
spring:
  mqtt:
    host: tcp://localhost:11883
    client-id: mqtt-server-${random.value}
    topic: device/+/reporter
    qos: 1
    username: mqtt_user
    password: mqtt_password
```

### Mica MQTT 配置
```yaml
mica:
  mqtt:
    client:
      enabled: true
      host: localhost
      port: 11883
      username: mqtt_user
      password: mqtt_password
      client-id: mica-mqtt-client-${random.value}
      topics:
        - topic: device/+/reporter
          qos: 1
        - topic: device/+/status
          qos: 0
```

## 💡 最佳实践建议

1. **新项目推荐**：使用 **Mica MQTT**，开发效率最高
2. **已有 Spring Integration 项目**：继续使用 Spring Integration
3. **特殊需求**：考虑原生 Paho

4. **多方案共存**：
   - 本项目同时实现了三种方案
   - 可以根据实际情况选择使用
   - 注意避免重复消费同一主题

## ⚠️ 注意事项

1. **避免重复订阅**：如果同时启用多个监听器，确保不要重复订阅相同主题
2. **客户端ID唯一**：每个 MQTT 客户端必须有唯一的 client-id
3. **主题冲突**：通配符主题（如 `device/#`）会匹配所有子主题，注意避免重复处理

## 🚀 快速开始

### 使用 Mica MQTT（推荐）

1. 添加依赖（已完成）
2. 配置 application.yml（已完成）
3. 创建监听器（参考 `MicaMqttListener.java`）
4. 启动应用即可

```bash
mvn spring-boot:run
```

查看日志确认连接成功：
```
[Mica-MQTT监听器] Mica MQTT 客户端连接成功
[Mica-MQTT监听器] 📥 [Mica] 收到设备上报 - Topic: device/device-1/reporter, QoS: 1
```
