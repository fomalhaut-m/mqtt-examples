# DDD（领域驱动设计）包结构重构方案

## 🎯 为什么要采用DDD？

### 当前问题（技术分层架构）
- ❌ 按技术类型分组（controller、service、repository）
- ❌ 业务逻辑分散在不同包中
- ❌ 难以理解系统的业务边界
- ❌ 新增功能需要跨越多个技术层

### DDD优势（领域驱动架构）
- ✅ **按业务领域分组** - 每个限界上下文独立
- ✅ **业务逻辑内聚** - 相关代码放在一起
- ✅ **清晰的依赖关系** - 外层依赖内层
- ✅ **易于扩展** - 新增领域不影响其他领域

## 📦 DDD四层架构

```
┌─────────────────────────────────────┐
│   Interfaces (用户接口层)            │  ← REST API、MQTT适配器
├─────────────────────────────────────┤
│   Application (应用层)               │  ← 应用服务、DTO、命令
├─────────────────────────────────────┤
│   Domain (领域层)                    │  ← 聚合根、值对象、领域事件
├─────────────────────────────────────┤
│   Infrastructure (基础设施层)        │  ← 持久化、外部服务
└─────────────────────────────────────┘
```

## 🗂️ 识别的限界上下文

基于业务分析，系统包含以下4个限界上下文：

### 1. **Shared（共享内核）**
**职责**：提供跨领域的共享功能

**包含内容**：
- MQTT客户端封装
- SSE推送服务
- 通用配置
- 基础类（BaseEntity、ValueObject等）

### 2. **Device（设备管理）**
**职责**：管理设备元数据和生命周期

**核心概念**：
- **聚合根**: `Device`（设备）
- **值对象**: `DeviceId`、`DeviceStatus`
- **领域事件**: `DeviceRegisteredEvent`、`DeviceStatusChangedEvent`
- **仓储**: `DeviceRepository`

**业务规则**：
- 设备ID必须唯一
- 设备状态流转：offline → online → error
- 记录最后在线时间

### 3. **Telemetry（遥测数据）**
**职责**：处理设备上报的传感器数据

**核心概念**：
- **聚合根**: `TelemetryData`（遥测数据）
- **值对象**: `Temperature`、`Humidity`、`Voltage`
- **领域事件**: `TelemetryReceivedEvent`
- **仓储**: `TelemetryRepository`（ClickHouse）

**业务规则**：
- 温度范围：-40°C ~ 85°C
- 湿度范围：0% ~ 100%
- 电压范围：0V ~ 5V
- 数据保留30天

### 4. **Notification（通知）**
**职责**：实时推送告警和状态变化

**核心概念**：
- **聚合根**: `Notification`（通知）
- **值对象**: `Alert`、`AlertLevel`
- **领域事件**: `AlertTriggeredEvent`

**业务规则**：
- 错误码映射到告警级别
- 支持多种推送渠道（SSE、WebSocket）
- 告警去重和合并

## 📁 完整包结构

```
top.vexruna.simulator/
│
├── shared/                                 # 【共享内核】
│   ├── kernel/                            
│   │   ├── BaseEntity.java               
│   │   ├── ValueObject.java              
│   │   └── DomainEvent.java              
│   ├── config/                           
│   │   ├── MqttConfig.java
│   │   └── WebConfig.java
│   └── infrastructure/                   
│       ├── mqtt/                         # MQTT基础设施
│       │   ├── MqttClientWrapper.java
│       │   └── MqttMessageConverter.java
│       └── sse/                          # SSE基础设施
│           ├── SseConnectionManager.java
│           └── SseMessageSender.java
│
├── device/                                 # 【设备管理上下文】
│   ├── domain/                           
│   │   ├── model/                        
│   │   │   ├── Device.java              # 聚合根
│   │   │   ├── DeviceId.java            # 值对象
│   │   │   ├── DeviceStatus.java        # 枚举
│   │   │   └── DeviceInfo.java          
│   │   ├── repository/                   
│   │   │   └── DeviceRepository.java    # 接口
│   │   ├── service/                      
│   │   │   └── DeviceDomainService.java # 领域服务
│   │   └── event/                        
│   │       ├── DeviceRegisteredEvent.java
│   │       └── DeviceStatusChangedEvent.java
│   │
│   ├── application/                      
│   │   ├── service/                      
│   │   │   └── DeviceApplicationService.java
│   │   ├── dto/                          
│   │   │   ├── DeviceDTO.java
│   │   │   └── DeviceQueryDTO.java
│   │   └── command/                      
│   │       └── RegisterDeviceCommand.java
│   │
│   ├── infrastructure/                   
│   │   ├── persistence/                  
│   │   │   ├── entity/DeviceInfoEntity.java
│   │   │   ├── repository/DeviceRepositoryImpl.java
│   │   │   └── mapper/DeviceMapper.java
│   │   └── database/                     
│   │       └── H2DataSourceConfig.java
│   │
│   └── interfaces/                       
│       ├── rest/                         
│       │   └── DeviceController.java
│       └── mqtt/                         
│           └── DeviceMqttAdapter.java
│
├── telemetry/                              # 【遥测数据上下文】
│   ├── domain/                           
│   │   ├── model/                        
│   │   │   ├── TelemetryData.java       
│   │   │   ├── SensorReading.java       
│   │   │   ├── Temperature.java         
│   │   │   ├── Humidity.java            
│   │   │   └── Voltage.java             
│   │   ├── repository/                   
│   │   │   └── TelemetryRepository.java
│   │   ├── service/                      
│   │   │   └── TelemetryValidationService.java
│   │   └── event/                        
│   │       └── TelemetryReceivedEvent.java
│   │
│   ├── application/                      
│   │   ├── service/                      
│   │   │   └── TelemetryApplicationService.java
│   │   ├── dto/                          
│   │   │   └── TelemetryDataDTO.java
│   │   └── handler/                      
│   │       └── TelemetryDataHandler.java
│   │
│   ├── infrastructure/                   
│   │   ├── persistence/                  
│   │   │   ├── entity/TelemetryDataEntity.java
│   │   │   ├── repository/TelemetryRepositoryImpl.java
│   │   │   └── clickhouse/ClickHouseClient.java
│   │   └── cache/                        
│   │       └── TelemetryCacheManager.java
│   │
│   └── interfaces/                       
│       ├── rest/                         
│       │   └── TelemetryController.java
│       └── mqtt/                         
│           ├── DeviceReporterListener.java
│           └── MicaMqttListener.java
│
└── notification/                           # 【通知上下文】
    ├── domain/                           
    │   ├── model/
    │   │   ├── Notification.java
    │   │   ├── Alert.java
    │   │   └── AlertLevel.java
    │   ├── service/
    │   │   └── NotificationService.java
    │   └── event/
    │       └── AlertTriggeredEvent.java
    │
    ├── application/
    │   └── service/
    │       └── NotificationApplicationService.java
    │
    ├── infrastructure/
    │   └── push/
    │       ├── SseNotificationPusher.java
    │       └── NotificationChannel.java
    │
    └── interfaces/
        └── rest/
            └── NotificationController.java
```

## 🔄 迁移策略

### 阶段1：创建基础结构（低风险）
1. 创建新的DDD包目录
2. 迁移shared共享内核
3. 创建基础类和接口

### 阶段2：迁移Device上下文（中风险）
1. 创建领域模型（Device、DeviceId等）
2. 迁移仓储接口和实现
3. 迁移应用服务和控制器

### 阶段3：迁移Telemetry上下文（高风险）
1. 创建领域模型（TelemetryData、SensorReading等）
2. 迁移ClickHouse数据访问
3. 迁移MQTT监听器

### 阶段4：创建Notification上下文（新功能）
1. 设计通知领域模型
2. 实现SSE推送集成
3. 添加告警规则引擎

### 阶段5：清理和优化
1. 删除旧包结构
2. 更新所有import引用
3. 运行测试验证

## 📊 对比分析

| 维度 | 技术分层 | DDD分层 |
|------|---------|---------|
| **组织方式** | 按技术类型 | 按业务领域 |
| **包数量** | 7个扁平包 | 4个限界上下文 |
| **业务可见性** | ❌ 低 | ✅ 高 |
| **模块独立性** | ❌ 耦合 | ✅ 独立 |
| **学习曲线** | ⭐⭐ 简单 | ⭐⭐⭐⭐ 较陡 |
| **适合场景** | 小型项目 | 中大型复杂系统 |

## ⚠️ 注意事项

### 优点
✅ 业务逻辑清晰可见  
✅ 便于团队协作（每人负责一个上下文）  
✅ 支持微服务拆分（每个上下文可独立部署）  
✅ 符合单一职责原则  

### 挑战
⚠️ 初期学习成本高  
⚠️ 文件数量增加  
⚠️ 需要理解DDD概念（聚合根、值对象、领域事件）  
⚠️ 小项目可能过度设计  

### 建议
💡 如果项目规模较小（<10个实体），保持当前架构即可  
💡 如果预期业务复杂度增长，建议采用DDD  
💡 可以渐进式迁移，不必一次性完成  
💡 团队需要先学习DDD基础知识  

## 🚀 下一步行动

**选项A：立即执行DDD重构**
- 预计耗时：2-3小时
- 风险等级：中高
- 适合：希望深入理解DDD的团队

**选项B：保持当前架构**
- 当前架构已经很清晰
- 适合：小型项目或快速原型

**选项C：混合方案（推荐）**
- 保留当前的技术分层
- 在关键领域（如Device）尝试DDD
- 逐步演进，不急于求成

---

**你希望我执行哪个选项？**
- 输入 `A` 开始完整DDD重构
- 输入 `B` 保持当前架构
- 输入 `C` 采用混合方案（仅重构Device领域）
