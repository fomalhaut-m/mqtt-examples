# MQTT 数据采集器

基于 `mqtt-spring-boot-starter` 实现的MQTT数据采集系统。

## 功能特性

- ✅ 自动订阅MQTT主题
- ✅ 实时接收设备数据
- ✅ JSON数据解析
- ✅ 设备数据缓存
- ✅ 统计信息收集
- ✅ REST API查询接口

## 架构设计

```
┌─────────────┐      MQTT       ┌──────────────────┐
│  MQTT       │ ──────────────► │ MqttMessage      │
│  Broker     │   device/+/     │ Listener         │
│             │   reporter      │                  │
└─────────────┘                 └────────┬─────────┘
                                         │
                                         ▼
                                ┌──────────────────┐
                                │ DeviceData       │
                                │ Collector        │
                                │                  │
                                │ - 解析JSON       │
                                │ - 提取设备ID     │
                                │ - 更新缓存       │
                                │ - 统计数据       │
                                └────────┬─────────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    ▼                    ▼                    ▼
          ┌─────────────────┐ ┌────────────────┐ ┌─────────────────┐
          │ 最新数据缓存     │ │ 统计信息        │ │ REST API        │
          │ (ConcurrentMap) │ │ (AtomicLong)   │ │ 查询接口        │
          └─────────────────┘ └────────────────┘ └─────────────────┘
```

## 配置说明

在 `application.yml` 中配置MQTT连接参数：

```yaml
onlytl:
  mqtt:
    host-url: tcp://localhost:11883
    username: mqtt_user
    password: mqtt_password
    client-id: mqtt-collector-${random.value}
    default-qos: 1
    automatic-reconnect: true
    clean-session: true
    connection-timeout: 30
    keep-alive-interval: 60
    ssl-enabled: false
```

## 使用示例

### 1. 启动应用

```bash
cd server
mvn spring-boot:run
```

### 2. 运行Python测试客户端

```bash
cd test-client
python mqtt_publisher.py
```

### 3. 查询采集的数据

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

#### 清除缓存
```bash
curl -X POST http://localhost:8081/api/collector/clear
```

## API接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/collector/devices` | 获取所有设备最新数据 |
| GET | `/api/collector/device/{deviceId}` | 获取指定设备最新数据 |
| GET | `/api/collector/statistics` | 获取消息统计信息 |
| POST | `/api/collector/clear` | 清除缓存 |

## 扩展开发

### 添加新的监听主题

在 `MqttMessageListener` 中添加新的监听方法：

```java
@MqttListener(topic = "device/+/status", qos = 0)
public void handleDeviceStatus(MqttMessageModel message) {
    log.info("收到设备状态: {}", new String(message.getPayload()));
    // 处理逻辑
}
```

### 自定义数据处理

在 `DeviceDataCollector.handleDeviceData()` 方法中添加业务逻辑：

```java
private void handleDeviceData(DeviceData data) {
    // 1. 存储到数据库
    deviceRepository.save(data);
    
    // 2. 触发告警
    if (data.getTemperature() > 40.0) {
        alertService.sendAlert(data);
    }
    
    // 3. 数据转发
    kafkaTemplate.send("device-data", data);
}
```

## 数据模型

```java
{
  "deviceId": "device-1",
  "timestamp": 1234567890,
  "temperature": 25.5,
  "humidity": 60.2,
  "voltage": 3.8,
  "status": "online",
  "topic": "device/device-1/reporter",
  "qos": 1
}
```

## 注意事项

1. 确保Mosquitto服务正在运行
2. 检查用户名密码配置是否正确
3. 主题格式必须匹配：`device/{deviceId}/reporter`
4. 消息格式必须是JSON
