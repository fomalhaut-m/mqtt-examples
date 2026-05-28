package top.vexruna.simulator.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.vexruna.simulator.dto.ApiResponse;
import top.vexruna.simulator.dto.DeviceData;
import top.vexruna.simulator.mqtt.DeviceDataCollector;
import top.vexruna.simulator.service.ClickHouseDataService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

    private final ClickHouseDataService clickHouseDataService;
    private final DeviceDataCollector deviceDataCollector;

    @GetMapping("/latest")
    public ApiResponse<List<DeviceData>> getLatestAllDevicesData() {
        List<DeviceData> data = clickHouseDataService.getLatestAllDevicesData();
        return ApiResponse.success(data);
    }

    @GetMapping("/latest/{deviceId}")
    public ApiResponse<DeviceData> getLatestDeviceData(@PathVariable String deviceId) {
        DeviceData data = clickHouseDataService.getLatestData(deviceId);
        if (data == null) {
            DeviceData cacheData = deviceDataCollector.getLatestData(deviceId);
            if (cacheData != null) {
                return ApiResponse.success(cacheData);
            }
            return ApiResponse.error("设备数据不存在: " + deviceId);
        }
        return ApiResponse.success(data);
    }

    @GetMapping("/history/{deviceId}")
    public ApiResponse<List<DeviceData>> getHistoryData(
            @PathVariable String deviceId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "100") int limit) {
        List<DeviceData> data = clickHouseDataService.getHistoryData(deviceId, startTime, endTime, limit);
        return ApiResponse.success(data);
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = clickHouseDataService.getStatistics();
        stats.put("sseConnections", 0);
        return ApiResponse.success(stats);
    }
}
