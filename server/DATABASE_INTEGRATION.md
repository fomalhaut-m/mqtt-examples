# 数据库集成说明

## 📊 架构设计

```
MQTT消息接收
    ↓
DeviceDataCollector
    ↓
    ├─→ H2数据库（设备信息）
    │     └─ DeviceInfoService
    │         - 设备注册
    │         - 状态管理
    │         - 元数据存储
    │
    └─→ ClickHouse（时序数据）
          └─ ClickHouseDataService
              - 温度/湿度/电压
              - 时间序列存储
              - 30天TTL自动清理
```

## 🗄️ 数据库配置

### 1. H2数据库（设备信息）

**配置：**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:device_db
    username: sa
    password: 
  jpa:
    hibernate:
      ddl-auto: update
  h2:
    console:
      enabled: true
      path: /h2-console
```

**访问H2 Console：**
- URL: http://localhost:8081/h2-console
- JDBC URL: `jdbc:h2:mem:device_db`
- Username: `sa`
- Password: (空)

**表结构：**
```sql
CREATE TABLE device_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(100) UNIQUE NOT NULL,
    device_name VARCHAR(200),
    device_type VARCHAR(50),
    status VARCHAR(20),
    last_online_time TIMESTAMP,
    register_time TIMESTAMP,
    update_time TIMESTAMP,
    description VARCHAR(500)
);
```

### 2. ClickHouse（时序数据）

**配置：**
```yaml
clickhouse:
  url: jdbc:clickhouse://localhost:18123/mqtt_db
  username: mqtt_user
  password: mqtt_password
```

**表结构：**
```sql
CREATE TABLE device_data (
    device_id String,
    timestamp DateTime64(3),
    temperature Float64,
    humidity Float64,
    voltage Float64,
    status String,
    error Nullable(String),
    topic String,
    qos UInt8,
    created_at DateTime DEFAULT now()
) ENGINE = MergeTree()
ORDER BY (device_id, timestamp)
TTL timestamp + INTERVAL 30 DAY;
```

**特性：**
- ✅ 按设备和时间排序
- ✅ 30天自动过期（TTL）
- ✅ 高效的时间序列查询

## 🔌 API接口

### 设备信息管理（H2）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/devices` | 获取所有设备 |
| GET | `/api/devices/{deviceId}` | 获取指定设备 |
| GET | `/api/devices/online` | 获取在线设备 |
| GET | `/api/devices/offline` | 获取离线设备 |
| DELETE | `/api/devices/{deviceId}` | 删除设备 |

### 设备数据查询（ClickHouse）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/devices/{deviceId}/latest-data` | 获取最新数据 |

### 数据采集器API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/collector/devices` | 获取缓存的所有设备数据 |
| GET | `/api/collector/device/{deviceId}` | 获取缓存的设备数据 |
| GET | `/api/collector/statistics` | 获取统计信息 |
| POST | `/api/collector/clear` | 清除缓存 |

## 📝 使用示例

### 1. 查询所有设备
```bash
curl http://localhost:8081/api/devices
```

**响应：**
```json
[
  {
    "id": 1,
    "deviceId": "device-1",
    "deviceName": "Device device-1",
    "deviceType": "sensor",
    "status": "online",
    "lastOnlineTime": "2026-05-26T13:50:00",
    "registerTime": "2026-05-26T13:45:00"
  }
]
```

### 2. 查询设备最新数据
```bash
curl http://localhost:8081/api/devices/device-1/latest-data
```

**响应：**
```json
{
  "deviceId": "device-1",
  "timestamp": 1779774647341,
  "temperature": 25.5,
  "humidity": 60.2,
  "voltage": 3.8,
  "status": "online",
  "topic": "device/device-1/reporter",
  "qos": 1
}
```

### 3. 查询在线设备
```bash
curl http://localhost:8081/api/devices/online
```

## 🔄 数据流程

```
1. MQTT消息到达
   ↓
2. MqttMessageListener 接收
   ↓
3. DeviceDataCollector 处理
   ├─ 解析JSON
   ├─ 提取设备ID
   ├─ 更新内存缓存
   ├─ 注册/更新设备信息 → H2
   ├─ 保存时序数据 → ClickHouse
   └─ 业务逻辑处理
```

## 💡 优势

### H2数据库
- ✅ 轻量级，无需外部依赖
- ✅ 内存数据库，速度快
- ✅ 支持SQL查询
- ✅ 内置Web Console

### ClickHouse
- ✅ 专为时序数据优化
- ✅ 高性能写入和查询
- ✅ 自动数据过期（TTL）
- ✅ 列式存储，压缩率高

## 🔧 维护建议

### H2
- 生产环境可切换到PostgreSQL/MySQL
- 定期备份重要设备信息
- 监控内存使用情况

### ClickHouse
- 监控磁盘使用量
- 根据需要调整TTL时间
- 定期优化表性能
- 设置合适的分区策略

## 🚀 启动顺序

1. **启动Mosquitto**
   ```bash
   cd mqtt-example && docker-compose up -d mqtt
   ```

2. **启动ClickHouse**
   ```bash
   cd mqtt-example && docker-compose up -d clickhouse
   ```

3. **启动Spring应用**
   ```bash
   cd server && mvn spring-boot:run
   ```

4. **运行测试客户端**
   ```bash
   cd test-client && python mqtt_publisher.py
   ```

现在系统已完整集成H2和ClickHouse数据库！🎉
