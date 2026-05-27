package top.vexruna.simulator.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.vexruna.simulator.device.service.DeviceInfoService;
import top.vexruna.simulator.mqtt.model.DeviceData;
import top.vexruna.simulator.sse.SseService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备数据采集器
 * 负责解析 MQTT 消息并分发给各个服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDataCollector {
    
    private final DeviceInfoService deviceInfoService;
    private final SseService sseService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, AtomicLong> statistics = new ConcurrentHashMap<>();
    private final Map<String, DeviceData> latestDataCache = new ConcurrentHashMap<>();
    
    /**
     * 处理设备数据
     *
     * @param topic   MQTT 主题
     * @param payload 消息内容
     * @param qos     QoS 等级
     */
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
                deviceInfoService.registerOrUpdateDevice(deviceId);
            }
            
            sseService.pushDeviceData(deviceData);
            log.info("[采集] 处理数据 - {} 温度:{} 湿度:{}", 
                    effectiveDeviceId, deviceData.getTemperature(), deviceData.getHumidity());
            
        } catch (Exception e) {
            log.error("[采集] 处理失败 - Topic: {} - Error: {}", topic, e.getMessage());
        }
    }
    
    /**
     * 从主题中提取设备ID
     */
    private String extractDeviceId(String topic) {
        if (topic != null && topic.contains("/")) {
            String[] parts = topic.split("/");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }
    
    /**
     * 更新统计数据
     */
    private void updateStatistics(String deviceId) {
        statistics.computeIfAbsent(deviceId, k -> new AtomicLong()).incrementAndGet();
    }
    
    /**
     * 获取统计数据
     */
    public Map<String, AtomicLong> getStatistics() {
        return statistics;
    }
    
    /**
     * 获取最新设备数据
     */
    public DeviceData getLatestData(String deviceId) {
        return latestDataCache.get(deviceId);
    }
}