# SSE 实时数据推送使用指南

## 📊 架构说明

```
MQTT消息 → DeviceDataCollector → SseService → 前端浏览器
                                    ↓
                            所有连接的客户端实时接收
```

## 🎯 功能特性

✅ **实时推送** - MQTT消息到达后立即推送到前端  
✅ **多客户端支持** - 支持多个浏览器同时连接  
✅ **自动重连** - 连接断开后自动清理资源  
✅ **多种事件类型** - deviceData、deviceStatus、alert  
✅ **跨域支持** - 允许任何域名访问  

## 🔌 API接口

### 1. 建立SSE连接

**请求：**
```
GET http://localhost:8081/api/sse/connect?clientId=web-client-1
```

**参数：**
- `clientId` (可选) - 客户端ID，不提供则自动生成

**响应：**
- Content-Type: `text/event-stream`
- 持续的事件流

### 2. 获取连接数

**请求：**
```
GET http://localhost:8081/api/sse/connections
```

**响应：**
```json
5
```

### 3. 断开连接

**请求：**
```
POST http://localhost:8081/api/sse/disconnect/{clientId}
```

## 📝 前端使用示例

### JavaScript原生实现

```javascript
// 建立连接
const eventSource = new EventSource('http://localhost:8081/api/sse/connect?clientId=my-client');

// 监听设备数据
eventSource.addEventListener('deviceData', (event) => {
    const data = JSON.parse(event.data);
    console.log('收到设备数据:', data);
    // 更新UI
    updateDeviceDisplay(data);
});

// 监听设备状态
eventSource.addEventListener('deviceStatus', (event) => {
    const data = JSON.parse(event.data);
    console.log('设备状态变化:', data);
});

// 监听告警
eventSource.addEventListener('alert', (event) => {
    const data = JSON.parse(event.data);
    console.log('收到告警:', data);
    showNotification(data);
});

// 错误处理
eventSource.onerror = (error) => {
    console.error('SSE连接错误:', error);
    eventSource.close();
};

// 断开连接
function disconnect() {
    eventSource.close();
}
```

### Vue.js 实现

```vue
<template>
  <div>
    <div v-for="device in devices" :key="device.deviceId">
      {{ device.deviceId }}: {{ device.temperature }}°C
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      eventSource: null,
      devices: {}
    };
  },
  mounted() {
    this.connect();
  },
  beforeUnmount() {
    this.disconnect();
  },
  methods: {
    connect() {
      this.eventSource = new EventSource('http://localhost:8081/api/sse/connect');
      
      this.eventSource.addEventListener('deviceData', (event) => {
        const data = JSON.parse(event.data);
        this.$set(this.devices, data.deviceId, data);
      });
    },
    disconnect() {
      if (this.eventSource) {
        this.eventSource.close();
      }
    }
  }
};
</script>
```

### React 实现

```jsx
import { useEffect, useState } from 'react';

function DeviceMonitor() {
  const [devices, setDevices] = useState({});
  
  useEffect(() => {
    const eventSource = new EventSource('http://localhost:8081/api/sse/connect');
    
    eventSource.addEventListener('deviceData', (event) => {
      const data = JSON.parse(event.data);
      setDevices(prev => ({
        ...prev,
        [data.deviceId]: data
      }));
    });
    
    return () => {
      eventSource.close();
    };
  }, []);
  
  return (
    <div>
      {Object.values(devices).map(device => (
        <div key={device.deviceId}>
          {device.deviceId}: {device.temperature}°C
        </div>
      ))}
    </div>
  );
}
```

## 🎨 测试页面

项目已提供完整的测试页面：

**文件位置：** `web/sse-monitor.html`

**使用方法：**
1. 启动Spring Boot应用
2. 在浏览器中打开 `web/sse-monitor.html`
3. 点击"连接"按钮
4. 运行Python测试客户端发送数据
5. 实时查看设备数据和日志

**功能：**
- ✅ 实时显示所有设备数据
- ✅ 设备状态指示器
- ✅ 实时日志面板
- ✅ 美观的UI界面

## 📊 事件类型

### 1. deviceData - 设备数据

**触发时机：** 收到MQTT设备上报消息

**数据格式：**
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

### 2. deviceStatus - 设备状态

**触发时机：** 处理设备数据时

**数据格式：**
```json
{
  "deviceId": "device-1",
  "status": "online",
  "timestamp": 1779774647341
}
```

### 3. alert - 告警信息

**触发时机：** 设备上报错误信息

**数据格式：**
```json
{
  "type": "error",
  "deviceId": "device-12",
  "error": "E001:Temp fault",
  "status": "error",
  "timestamp": 1779774647341
}
```

### 4. connected - 连接成功

**触发时机：** SSE连接建立成功

**数据格式：**
```json
{
  "message": "SSE连接成功",
  "clientId": "web-client-1"
}
```

## 🔧 后端集成

### 在业务代码中推送数据

```java
@Autowired
private SseService sseService;

// 推送设备数据
sseService.pushDeviceData(deviceData);

// 推送设备状态
sseService.pushDeviceStatus("device-1", "online");

// 推送告警
sseService.pushAlert(alertData);

// 广播自定义事件
sseService.broadcast("customEvent", customData);

// 发送给指定客户端
sseService.sendToClient("client-1", "eventName", data);
```

## ⚙️ 配置说明

### 超时时间

默认超时时间：30分钟

修改方法：
```java
private static final Long DEFAULT_TIMEOUT = 60 * 60 * 1000L; // 1小时
```

### 跨域配置

已配置允许所有域名访问：
```java
@CrossOrigin(origins = "*")
```

生产环境建议限制特定域名：
```java
@CrossOrigin(origins = {"http://localhost:3000", "https://yourdomain.com"})
```

## 💡 最佳实践

### 1. 心跳保活

虽然SSE有自动重连机制，但建议添加心跳：

```java
@Scheduled(fixedRate = 30000) // 每30秒
public void sendHeartbeat() {
    sseService.broadcast("heartbeat", Map.of(
        "timestamp", System.currentTimeMillis()
    ));
}
```

### 2. 连接数监控

```java
@GetMapping("/monitor/connections")
public Map<String, Object> getConnectionInfo() {
    return Map.of(
        "activeConnections", sseService.getActiveConnectionCount(),
        "timestamp", System.currentTimeMillis()
    );
}
```

### 3. 错误处理

前端应实现重试逻辑：

```javascript
let retryCount = 0;
const maxRetries = 5;

function connectWithRetry() {
    eventSource.onerror = () => {
        eventSource.close();
        
        if (retryCount < maxRetries) {
            retryCount++;
            setTimeout(connectWithRetry, 5000 * retryCount);
        }
    };
}
```

## 🚀 快速开始

1. **启动后端**
   ```bash
   cd server
   mvn spring-boot:run
   ```

2. **打开测试页面**
   ```
   在浏览器中打开 web/sse-monitor.html
   ```

3. **点击连接**
   ```
   点击页面上的"连接"按钮
   ```

4. **发送测试数据**
   ```bash
   cd test-client
   python mqtt_publisher.py
   ```

5. **查看实时数据**
   ```
   在页面上实时查看设备数据更新
   ```

## 📈 性能优化

1. **限制推送频率** - 对于高频数据，可以合并推送
2. **数据压缩** - 大数据量时启用GZIP压缩
3. **连接池管理** - 定期清理无效连接
4. **消息队列** - 高并发时使用消息队列缓冲

## ⚠️ 注意事项

1. **浏览器兼容性** - IE不支持SSE，需要使用polyfill
2. **连接数限制** - 浏览器对同一域名的SSE连接数有限制（通常6个）
3. **防火墙** - 确保防火墙允许SSE连接
4. **代理配置** - Ngin等代理需要正确配置SSE支持

现在你的系统已经具备完整的实时数据推送能力！🎉
