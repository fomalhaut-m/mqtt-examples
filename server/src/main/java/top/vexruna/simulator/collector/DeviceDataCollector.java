package top.vexruna.simulator.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.vexruna.simulator.dto.DeviceData;
import top.vexruna.simulator.service.ClickHouseDataService;
import top.vexruna.simulator.service.DeviceService;
import top.vexruna.simulator.service.SseService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDataCollector {

    private final DeviceService deviceService;
    private final ClickHouseDataService clickHouseDataService;
    private final SseService sseService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, AtomicLong> statistics = new ConcurrentHashMap<>();
    private final Map<String, DeviceData> latestDataCache = new ConcurrentHashMap<>();

    public void processDeviceData(String topic, String payload, int qos) {
        try {
            DeviceData deviceData = objectMapper.readValue(payload, DeviceData.class);
            if (deviceData == null) {
                log.warn("[采集] 无法解析设备数据 - Topic: {}", topic);
                return;
            }

            deviceData.setTopic(topic);
            deviceData.setQos(qos);

            String deviceId = extractDeviceId(topic);
            if (deviceId != null) {
                deviceData.setDeviceId(deviceId);
            }

            String effectiveDeviceId = deviceId != null ? deviceId : "unknown";
            latestDataCache.put(effectiveDeviceId, deviceData);
            updateStatistics(effectiveDeviceId);

            // 注册或更新设备信息（H2）
            if (deviceId != null) {
                deviceService.registerOrUpdateDevice(deviceId);
            }

            // 存储时序数据（ClickHouse）
            clickHouseDataService.saveDeviceData(deviceData);

            // 推送 SSE 实时通知
            sseService.pushDeviceData(deviceData);
            log.info("[采集] 处理数据 - {} 温度:{} 湿度:{}",
                    effectiveDeviceId, deviceData.getTemperature(), deviceData.getHumidity());

        } catch (Exception e) {
            log.error("[采集] 处理失败 - Topic: {} - Error: {}", topic, e.getMessage());
        }
    }

    private String extractDeviceId(String topic) {
        if (topic != null && topic.contains("/")) {
            String[] parts = topic.split("/");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }

    private void updateStatistics(String deviceId) {
        statistics.computeIfAbsent(deviceId, k -> new AtomicLong()).incrementAndGet();
    }

    public Map<String, AtomicLong> getStatistics() {
        return statistics;
    }

    public DeviceData getLatestData(String deviceId) {
        return latestDataCache.get(deviceId);
    }
}