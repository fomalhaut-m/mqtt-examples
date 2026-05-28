package top.vexruna.simulator.mqtt;

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

            if (deviceId != null) {
                deviceService.registerOrUpdateDevice(deviceId);
            }

            clickHouseDataService.saveDeviceData(deviceData);
            sseService.pushDeviceData(deviceData);
            log.info("[采集] 处理数据 - {} 温度:{} 湿度:{}",
                    effectiveDeviceId, deviceData.getTemperature(), deviceData.getHumidity());

        } catch (Exception e) {
            log.error("[采集] 处理失败 - Topic: {} - Error: {}", topic, e.getMessage());
        }
    }

    public void processSystemMetrics(String topic, String payload, int qos) {
        try {
            Object systemData = objectMapper.readValue(payload, Object.class);
            sseService.pushSystemMetrics(systemData);
            log.debug("[系统] 收到系统指标 - Topic: {}", topic);
        } catch (Exception e) {
            log.error("[系统] 处理失败 - Topic: {} - Error: {}", topic, e.getMessage());
        }
    }

    public void processLanScan(String topic, String payload, int qos) {
        try {
            Object lanData = objectMapper.readValue(payload, Object.class);
            sseService.pushLanScan(lanData);
            log.info("[LAN] 收到局域网扫描数据 - Topic: {}", topic);
        } catch (Exception e) {
            log.error("[LAN] 处理失败 - Topic: {} - Error: {}", topic, e.getMessage());
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