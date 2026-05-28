/**
 * 设备数据服务 —— 数据处理 + 双层环形队列存储
 *
 * 为什么需要双层存储：
 *   - 全局 RingBuffer：存所有设备的数据，用于"查看全部设备历史"
 *   - 每设备 RingBuffer：按 deviceId 隔离，用于"查看某台设备历史"
 *   - latestDataCache：快速查最新一条，不用遍历 RingBuffer
 *
 * 数据流：
 *   MessageProcessor.processMessage()
 *     → 本类.processAndStore()
 *       → 写入 RingBuffer（全局 + 按设备）
 *       → 写入 latestDataCache
 *       → 更新统计计数
 *       → SseService.pushDeviceData() 推送到前端
 */
package top.vexruna.simulator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.vexruna.simulator.dto.DeviceData;
import top.vexruna.simulator.queue.RingBuffer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class DeviceDataService {

    private final Map<String, RingBuffer<DeviceData>> deviceDataBuffers = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> statistics = new ConcurrentHashMap<>();
    private final Map<String, DeviceData> latestDataCache = new ConcurrentHashMap<>();
    private final RingBuffer<DeviceData> globalRingBuffer;

    private final SseService sseService;

    public DeviceDataService(
            @Value("${spring.ring-buffer.capacity:200}") int ringBufferCapacity,
            SseService sseService) {
        this.sseService = sseService;
        this.globalRingBuffer = new RingBuffer<>(ringBufferCapacity);
        log.info("[存储] 环形队列初始化 - 全局容量: {}, 每设备容量: {}",
                ringBufferCapacity, ringBufferCapacity);
    }

    public void processAndStore(DeviceData deviceData) {
        String deviceId = deviceData.getDeviceId();
        if (deviceId == null) {
            deviceId = "unknown";
            deviceData.setDeviceId(deviceId);
        }

        latestDataCache.put(deviceId, deviceData);
        statistics.computeIfAbsent(deviceId, k -> new AtomicLong()).incrementAndGet();

        RingBuffer<DeviceData> deviceBuffer = deviceDataBuffers
                .computeIfAbsent(deviceId, k -> new RingBuffer<>(200));
        deviceBuffer.add(deviceData);
        globalRingBuffer.add(deviceData);

        sseService.pushDeviceData(deviceData);

        log.debug("[存储] 设备 {} 数据已入队 - 温度: {}, 湿度: {}, 队列: {}/{}",
                deviceId, deviceData.getTemperature(), deviceData.getHumidity(),
                deviceBuffer.size(), deviceBuffer.getCapacity());
    }

    public DeviceData getLatestData(String deviceId) {
        return latestDataCache.get(deviceId);
    }

    public List<DeviceData> getDeviceHistory(String deviceId) {
        RingBuffer<DeviceData> buffer = deviceDataBuffers.get(deviceId);
        if (buffer == null) {
            return List.of();
        }
        return buffer.toList();
    }

    public List<DeviceData> getGlobalHistory() {
        return globalRingBuffer.toList();
    }

    public Map<String, Long> getStatistics() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        statistics.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public long getTotalMessageCount() {
        return statistics.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

    public int getDeviceCount() {
        return latestDataCache.size();
    }

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("totalMessages", getTotalMessageCount());
        status.put("deviceCount", getDeviceCount());
        status.put("globalBufferSize", globalRingBuffer.size());
        status.put("globalBufferCapacity", globalRingBuffer.getCapacity());
        status.put("perDeviceBufferSizes", getPerDeviceBufferSizes());
        return status;
    }

    private Map<String, Integer> getPerDeviceBufferSizes() {
        Map<String, Integer> sizes = new ConcurrentHashMap<>();
        deviceDataBuffers.forEach((k, v) -> sizes.put(k, v.size()));
        return sizes;
    }
}
