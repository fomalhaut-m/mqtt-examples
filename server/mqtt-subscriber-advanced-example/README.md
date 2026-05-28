# MQTT Subscriber Advanced — 与基础版的区别

> 基于 `mqtt-subscriber-example` 进阶，聚焦 SpringBoot + MQTT 的深度优化与异常处理。

---

## 一、整体对比

| 维度 | 基础版 (mqtt-subscriber-example) | 进阶版 (mqtt-subscriber-advanced-example) |
|------|----------------------------------|------------------------------------------|
| 消费线程模型 | `DirectChannel`（单线程同步） | `ExecutorChannel` + `ThreadPoolTaskExecutor`（多线程异步） |
| 自动重连 | Paho 默认行为，无显式配置 | `MqttConnectOptions.setAutomaticReconnect(true)` + `MqttConnectionListener` 监听 |
| 异常处理 | try-catch 打日志 | 三层防线 + 死信队列（RingBuffer） + 独立死信日志文件 |
| 内存存储 | `ConcurrentHashMap<String, DeviceData>`（只存最新一条） | `RingBuffer<DeviceData>`（缓存最近 200 条，按设备 + 全局双层） |
| 前端可观测性 | 无连接状态 API | `/api/system/status` 暴露 MQTT 连接状态 + 重连次数 |
| 服务端口 | 8081 | 8082 |

---

## 二、逐项详解

### 1. 多线程消费

**基础版**的问题：`DirectChannel` 是同步通道，一条消息没处理完，下一条就堵着。

**进阶版**改动：

- [ThreadPoolConfig.java](src/main/java/top/vexruna/simulator/config/ThreadPoolConfig.java) — 配置线程池（core=4, max=16, queue=256）
- [MqttConfig.java](src/main/java/top/vexruna/simulator/config/MqttConfig.java#L58-L60) — 将 `DirectChannel` 替换为 `ExecutorChannel`

```
基础版：MQTT 消息 → DirectChannel → 同步调用 handleMqttMessage()（阻塞）
进阶版：MQTT 消息 → ExecutorChannel → 线程池异步分发 → MessageProcessor.processMessage()（并行）
```

拒绝策略使用 `CallerRunsPolicy`，线程池满时由 MQTT IO 线程直接执行，防止消息丢失。

### 2. 客户端自动重连

**基础版**的问题：Paho 虽然支持自动重连，但没有配置参数也没有状态感知，连接断了也不知道。

**进阶版**改动：

- [MqttConfig.java](src/main/java/top/vexruna/simulator/config/MqttConfig.java#L42-L49) — 显式配置重连参数：

| 参数 | 值 | 说明 |
|------|----|------|
| `setAutomaticReconnect(true)` | true | 开启 Paho 内置自动重连 |
| `setMaxReconnectDelay(30000)` | 30s | 重连间隔指数退避上限 |
| `setConnectionTimeout(10)` | 10s | 连接超时 |
| `setKeepAliveInterval(60)` | 60s | 心跳间隔 |

- [MqttConnectionListener.java](src/main/java/top/vexruna/simulator/mqtt/MqttConnectionListener.java) — 实现 `MqttCallback` 接口：

```
连接断开 → connectionLost() 被调用
  → connected = false
  → reconnectAttempt++（累加计数）
  → 打印 WARN 日志

重连成功 → onReconnected() 被调用
  → connected = true
  → reconnectAttempt = 0（归零）
  → 打印 INFO 日志
```

- [DataController.java](src/main/java/top/vexruna/simulator/controller/DataController.java#L54-L57) — 通过 HTTP API 暴露状态：

```
GET /api/system/status
→ {
    "mqttConnected": true,
    "reconnectAttempts": 0,
    ...
  }
```

### 3. 统一异常处理

**基础版**的问题：`handleMqttMessage()` 里一个大 try-catch 包所有逻辑，JSON 解析失败和连接异常混在一起，没有死信概念。

**进阶版**三层防线：

| 层级 | 位置 | 职责 |
|------|------|------|
| 第一层 | [MessageProcessor.java](src/main/java/top/vexruna/simulator/mqtt/MessageProcessor.java#L46-L55) | 区分 `JsonProcessingException` 和 `Exception`，分别记录死信 |
| 第二层 | [MqttErrorHandler.java](src/main/java/top/vexruna/simulator/mqtt/MqttErrorHandler.java) | 监听 Spring Integration 的 `mqttErrorChannel`，兜底处理漏网异常 |
| 第三层 | [GlobalExceptionHandler.java](src/main/java/top/vexruna/simulator/handler/GlobalExceptionHandler.java) | `@RestControllerAdvice` 处理 HTTP 层异常 |

**死信逻辑**：

- [DeadLetterService.java](src/main/java/top/vexruna/simulator/mqtt/DeadLetterService.java) — 非法报文入 `RingBuffer<DeadLetterMessage>(100)` 缓存
- [logback-spring.xml](src/main/resources/logback-spring.xml) — `DEAD_LETTER` logger 写入独立的 `logs/dead-letter.log` 文件
- 关键原则：**非法报文单独记录，不影响主流程**

### 4. 内存存储优化

**基础版**的问题：`latestDataCache` 是 `ConcurrentHashMap<String, DeviceData>`，每个设备只存最新一条，无法查看历史。

**进阶版**改动：

- [RingBuffer.java](src/main/java/top/vexruna/simulator/queue/RingBuffer.java) — 自研线程安全环形队列：

```
容量固定（默认 200），写满后自动覆盖最旧数据
使用 ReentrantReadWriteLock 保证读写线程安全
读操作不阻塞写，写操作不阻塞读
```

- [DeviceDataService.java](src/main/java/top/vexruna/simulator/service/DeviceDataService.java) — 双层存储：

```
全局 RingBuffer        → 存所有设备的数据，供全局历史查询
每设备 RingBuffer      → 按 deviceId 隔离，供单设备历史查询
latestDataCache       → 保留（快速查询最新一条）
```

配置在 `application.yml`：

```yaml
spring:
  ring-buffer:
    capacity: 200    # 每个 RingBuffer 的容量
```

### 5. 联动链路

**完整数据流**：

```
EMQX Broker
  ↓ MQTT TCP
MqttPahoMessageDrivenChannelAdapter
  ↓ ExecutorChannel（线程池异步分发）
MessageProcessor.processMessage()
  ↓ 解析 JSON → 校验
DeviceDataService.processAndStore()
  ↓ 写入 RingBuffer（全局 + 按设备）
SseService.pushDeviceData()
  ↓ SSE 广播
前端浏览器（实时图表）
```

异常分支：

```
JSON 解析失败 → DeadLetterService.capture() → RingBuffer + dead-letter.log
错误通道兜底 → MqttErrorHandler.handleError() → DeadLetterService.capture()
```

---

## 三、API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/sse/data?clientId=xxx` | SSE 实时数据流 |
| GET | `/sse/status` | SSE 连接状态 |
| GET | `/api/devices/{deviceId}/data` | 单设备历史数据（最近 200 条） |
| GET | `/api/devices/{deviceId}/latest` | 单设备最新数据 |
| GET | `/api/data/history` | 全局历史数据 |
| GET | `/api/data/statistics` | 消息统计 |
| GET | `/api/system/status` | 系统状态（含 MQTT 连接状态） |
| GET | `/api/dead-letters` | 死信记录查询 |

---

## 四、运行

```bash
# 1. 先启动 EMQX Broker（见 mqtt-example/docker-compose.yml）

# 2. 启动本项目
cd server/mqtt-subscriber-advanced-example
mvn spring-boot:run

# 3. 访问
# SSE: http://localhost:8082/sse/data
# API: http://localhost:8082/api/system/status
```

---

## 五、测试

```bash
mvn test

# 测试内容：
# - RingBufferTest: 8 个用例（基础读写、覆盖、并发安全等）
# - DeadLetterServiceTest: 2 个用例（入队、溢出覆盖）
```
