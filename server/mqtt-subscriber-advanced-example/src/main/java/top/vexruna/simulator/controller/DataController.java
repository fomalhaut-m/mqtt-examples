/**
 * 数据查询控制器 —— 提供 HTTP 查询接口
 *
 * 提供设备数据、系统状态、死信记录的查询能力
 * 前端通过这些接口获取历史数据、查看系统健康状态
 */
package top.vexruna.simulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.vexruna.simulator.dto.DeadLetterMessage;
import top.vexruna.simulator.dto.DeviceData;
import top.vexruna.simulator.mqtt.DeadLetterService;
import top.vexruna.simulator.mqtt.MqttConnectionListener;
import top.vexruna.simulator.service.DeviceDataService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DataController {

    private final DeviceDataService deviceDataService;
    private final DeadLetterService deadLetterService;
    private final MqttConnectionListener mqttConnectionListener;

    @GetMapping("/devices/{deviceId}/data")
    public List<DeviceData> getDeviceHistory(@PathVariable String deviceId) {
        return deviceDataService.getDeviceHistory(deviceId);
    }

    @GetMapping("/devices/{deviceId}/latest")
    public DeviceData getLatestData(@PathVariable String deviceId) {
        return deviceDataService.getLatestData(deviceId);
    }

    @GetMapping("/data/history")
    public List<DeviceData> getGlobalHistory() {
        return deviceDataService.getGlobalHistory();
    }

    @GetMapping("/data/statistics")
    public Map<String, Object> getStatistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("perDevice", deviceDataService.getStatistics());
        result.put("totalMessages", deviceDataService.getTotalMessageCount());
        result.put("deviceCount", deviceDataService.getDeviceCount());
        return result;
    }

    @GetMapping("/system/status")
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = deviceDataService.getSystemStatus();
        status.put("mqttConnected", mqttConnectionListener.isConnected());
        status.put("reconnectAttempts", mqttConnectionListener.getReconnectAttempt());
        return status;
    }

    @GetMapping("/dead-letters")
    public Map<String, Object> getDeadLetters() {
        Map<String, Object> result = new HashMap<>();
        result.put("count", deadLetterService.getDeadLetterCount());
        result.put("messages", deadLetterService.getRecentDeadLetters());
        return result;
    }
}
