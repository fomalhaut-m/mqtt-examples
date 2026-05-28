package top.vexruna.simulator.device.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.vexruna.simulator.device.entity.DeviceInfo;
import top.vexruna.simulator.device.service.DeviceInfoService;

import java.util.List;
import java.util.Optional;

/**
 * 设备管理 REST API 控制器
 * 提供设备的增删改查接口
 */
@Slf4j
@RestController
@RequestMapping("/api/devices")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DeviceController {
    
    private final DeviceInfoService deviceInfoService;
    
    /**
     * 获取所有设备
     */
    @GetMapping
    public List<DeviceInfo> getAllDevices() {
        log.info("[API] 查询所有设备");
        return deviceInfoService.getAllDevices();
    }
    
    /**
     * 获取指定设备
     */
    @GetMapping("/{deviceId}")
    public DeviceInfo getDevice(@PathVariable String deviceId) {
        log.info("[API] 查询设备: {}", deviceId);
        Optional<DeviceInfo> device = deviceInfoService.getDeviceById(deviceId);
        return device.orElse(null);
    }
    
    /**
     * 获取在线设备
     */
    @GetMapping("/online")
    public List<DeviceInfo> getOnlineDevices() {
        log.info("[API] 查询在线设备");
        return deviceInfoService.getOnlineDevices();
    }
    
    /**
     * 获取离线设备
     */
    @GetMapping("/offline")
    public List<DeviceInfo> getOfflineDevices() {
        log.info("[API] 查询离线设备");
        return deviceInfoService.getOfflineDevices();
    }
    
    /**
     * 删除设备
     */
    @DeleteMapping("/{deviceId}")
    public String deleteDevice(@PathVariable String deviceId) {
        log.info("[API] 删除设备: {}", deviceId);
        deviceInfoService.deleteDevice(deviceId);
        return "设备已删除: " + deviceId;
    }
}