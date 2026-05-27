# 项目包结构说明

## 📦 重构后的包结构

```
top.vexruna.simulator/
│
├── SimulatorApplication.java              # Spring Boot 启动类
│
├── config/                                 # 【配置层】Spring配置类
│   └── MqttConfig.java                    # Spring Integration MQTT配置
│
├── mqtt/                                   # 【MQTT层】MQTT相关功能
│   ├── listener/                          # MQTT消息监听器
│   │   ├── DeviceReporterListener.java    # 设备上报监听器 (Spring Integration)
│   │   ├── MicaMqttListener.java          # Mica MQTT监听器 (@MqttListener)
│   │   └── MqttMessageListener.java       # 通用MQTT监听器 (调试用)
│   └── model/                             # MQTT消息模型
│       └── DeviceData.java                # 设备数据模型
│
├── collector/                              # 【采集层】数据采集核心
│   ├── DeviceDataCollector.java           # 核心数据采集器
│   └── CollectorController.java           # 采集器REST API
│
├── device/                                 # 【设备管理层】设备信息管理
│   ├── entity/DeviceInfo.java             # 设备信息JPA实体（H2）
│   ├── repository/DeviceInfoRepository.java  # JPA Repository
│   ├── service/DeviceInfoService.java     # 设备信息服务
│   └── controller/DeviceController.java   # 设备管理REST API
│
├── data/                                   # 【数据层】时序数据存储
│   └── service/ClickHouseDataService.java # ClickHouse数据服务
│
└── sse/                                    # 【推送层】实时数据推送
    ├── SseService.java                    # SSE核心服务
    └── SseController.java                 # SSE REST API
```

## ✅ 重构成果

### 清晰的职责划分

| 包名 | 职责 | 关键组件 |
|------|------|---------|
| `config` | Spring配置 | MQTT连接配置、Channel配置 |
| `mqtt/listener` | 消息监听 | 三种监听方案并存 |
| `mqtt/model` | 数据模型 | DeviceData JSON模型 |
| `collector` | 数据采集 | 解析、缓存、统计、业务逻辑 |
| `device` | 设备管理 | H2数据库CRUD |
| `data/service` | 数据存储 | ClickHouse时序数据 |
| `sse` | 实时推送 | Server-Sent Events |

### 重构前后对比

**之前的问题：**
- ❌ 所有MQTT相关类都在 `collector` 包
- ❌ 监听器、模型、采集器混在一起
- ❌ 职责不清晰，难以维护

**现在的优势：**
- ✅ 按功能分层，职责明确
- ✅ MQTT监听器独立成包
- ✅ 数据模型单独管理
- ✅ 易于扩展和维护

## 🔑 各层职责说明

### 1. MQTT层 (`mqtt`)
- **listener**: 接收MQTT消息
  - `DeviceReporterListener`: 只处理 `device/+/reporter` 主题
  - `MicaMqttListener`: 使用mica-mqtt的注解式监听
  - `MqttMessageListener`: 通用监听器（调试用）
- **model**: 定义数据结构
  - `DeviceData`: 设备数据JSON模型

### 2. 采集层 (`collector`)
- `DeviceDataCollector`: 
  - 解析JSON数据
  - 提取设备ID
  - 更新缓存和统计
  - 调用下层服务（H2、ClickHouse、SSE）
  - 处理业务逻辑（错误码处理）
- `CollectorController`: 提供查询接口

### 3. 设备管理层 (`device`)
- 负责设备元数据的CRUD
- 使用H2内存数据库
- JPA + Hibernate实现

### 4. 数据层 (`data`)
- 负责时序数据存储
- 使用ClickHouse列式数据库
- 30天TTL自动清理

### 5. 推送层 (`sse`)
- 实时推送设备数据到前端
- 支持多客户端并发连接
- 三种事件类型：deviceData、deviceStatus、alert

## 🔄 数据流转

```
MQTT Broker
    ↓
[mqtt/listener] ← 接收消息
    ↓
[collector] ← 解析、缓存、统计
    ↓
    ├→ [device] → H2数据库（设备信息）
    ├→ [data] → ClickHouse（时序数据）
    └→ [sse] → 前端浏览器（实时推送）
```

## 📝 依赖关系

```
mqtt/listener → collector → device (H2)
                            → data (ClickHouse)
                            → sse (推送)
```

## 🚀 未来扩展建议

1. **添加告警层** (`alert`)
   - AlertService: 告警规则引擎
   - AlertController: 告警管理API

2. **添加分析层** (`analytics`)
   - AnalyticsService: 数据分析
   - ReportService: 报表生成

3. **添加缓存层** (`cache`)
   - RedisCacheService: 分布式缓存
   - CacheManager: 缓存管理

4. **添加安全层** (`security`)
   - JwtAuthentication: JWT认证
   - Authorization: 权限控制
