package top.vexruna.simulator.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.vexruna.simulator.dto.ApiResponse;
import top.vexruna.simulator.dto.DeviceResponse;
import top.vexruna.simulator.service.DeviceService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ApiResponse<List<DeviceResponse>> getDevices(
            @RequestParam(required = false) String status) {
        List<DeviceResponse> devices;
        if ("online".equalsIgnoreCase(status)) {
            devices = deviceService.getOnlineDevices();
        } else if ("offline".equalsIgnoreCase(status)) {
            devices = deviceService.getOfflineDevices();
        } else {
            devices = deviceService.getAllDevices();
        }
        return ApiResponse.success(devices);
    }

    @GetMapping("/{deviceId}")
    public ApiResponse<DeviceResponse> getDevice(@PathVariable String deviceId) {
        Optional<DeviceResponse> device = deviceService.getDeviceById(deviceId);
        return device.map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("设备不存在: " + deviceId));
    }

    @PostMapping
    public ApiResponse<DeviceResponse> createDevice(@RequestBody Map<String, Object> body) {
        try {
            String deviceId = (String) body.get("deviceId");
            String deviceName = (String) body.getOrDefault("deviceName", "Device " + deviceId);
            String deviceType = (String) body.getOrDefault("deviceType", "sensor");

            if (deviceId == null || deviceId.isBlank()) {
                return ApiResponse.error("deviceId 不能为空");
            }

            DeviceResponse device = deviceService.createDevice(deviceId, deviceName, deviceType);
            return ApiResponse.success("设备创建成功", device);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<DeviceResponse> updateDevice(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            DeviceResponse current = deviceService.getDeviceByIdInternal(id);
            String deviceName = (String) body.getOrDefault("deviceName", current.getDeviceName());
            String deviceType = (String) body.getOrDefault("deviceType", current.getDeviceType());

            DeviceResponse device = deviceService.updateDevice(current.getDeviceId(), deviceName, deviceType);
            return ApiResponse.success("设备更新成功", device);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDevice(@PathVariable Long id) {
        try {
            deviceService.deleteDeviceById(id);
            return ApiResponse.success("设备删除成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/start")
    public ApiResponse<Void> startDevice(@PathVariable Long id) {
        try {
            DeviceResponse device = deviceService.getDeviceByIdInternal(id);
            deviceService.setDeviceOnline(device.getDeviceId());
            return ApiResponse.success("设备已启动", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<Void> stopDevice(@PathVariable Long id) {
        try {
            DeviceResponse device = deviceService.getDeviceByIdInternal(id);
            deviceService.setDeviceOffline(device.getDeviceId());
            return ApiResponse.success("设备已停止", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/report")
    public ApiResponse<Void> triggerReport(@PathVariable Long id) {
        try {
            DeviceResponse device = deviceService.getDeviceByIdInternal(id);
            deviceService.triggerReport(device.getDeviceId());
            return ApiResponse.success("已触发上报", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/setInterval")
    public ApiResponse<Void> setReportInterval(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            DeviceResponse device = deviceService.getDeviceByIdInternal(id);
            int interval = body.get("interval") instanceof Number
                    ? ((Number) body.get("interval")).intValue()
                    : 5;
            deviceService.setReportInterval(device.getDeviceId(), interval);
            return ApiResponse.success("上报间隔已更新", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
