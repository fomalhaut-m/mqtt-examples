package top.vexruna.simulator.device.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.vexruna.simulator.device.entity.DeviceInfo;
import top.vexruna.simulator.device.repository.DeviceInfoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 设备信息服务
 * 提供设备注册、状态更新、查询等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceInfoService {
    
    private final DeviceInfoRepository deviceInfoRepository;
    
    /**
     * 注册或更新设备
     * 如果设备存在则更新，不存在则创建
     */
    @Transactional
    public DeviceInfo registerOrUpdateDevice(String deviceId) {
        Optional<DeviceInfo> existing = deviceInfoRepository.findByDeviceId(deviceId);
        
        DeviceInfo device;
        if (existing.isPresent()) {
            device = existing.get();
            log.info("[设备] 更新设备: {}", deviceId);
        } else {
            device = new DeviceInfo();
            device.setDeviceId(deviceId);
            device.setDeviceName("Device " + deviceId);
            device.setDeviceType("sensor");
            log.info("[设备] 注册新设备: {}", deviceId);
        }
        
        device.setStatus("online");
        device.setLastOnlineTime(LocalDateTime.now());
        
        return deviceInfoRepository.save(device);
    }
    
    /**
     * 更新设备状态
     */
    @Transactional
    public void updateDeviceStatus(String deviceId, String status) {
        Optional<DeviceInfo> optional = deviceInfoRepository.findByDeviceId(deviceId);
        if (optional.isPresent()) {
            DeviceInfo device = optional.get();
            device.setStatus(status);
            if ("online".equals(status)) {
                device.setLastOnlineTime(LocalDateTime.now());
            }
            deviceInfoRepository.save(device);
            log.info("[设备] 更新状态 - {}: {}", deviceId, status);
        } else {
            log.warn("[设备] 设备不存在: {}", deviceId);
        }
    }
    
    /**
     * 获取所有设备
     */
    public List<DeviceInfo> getAllDevices() {
        return deviceInfoRepository.findAll();
    }
    
    /**
     * 根据设备ID获取设备
     */
    public Optional<DeviceInfo> getDeviceById(String deviceId) {
        return deviceInfoRepository.findByDeviceId(deviceId);
    }
    
    /**
     * 获取在线设备列表
     */
    public List<DeviceInfo> getOnlineDevices() {
        return deviceInfoRepository.findByStatus("online");
    }
    
    /**
     * 获取离线设备列表
     */
    public List<DeviceInfo> getOfflineDevices() {
        return deviceInfoRepository.findByStatus("offline");
    }
    
    /**
     * 删除设备
     */
    @Transactional
    public void deleteDevice(String deviceId) {
        Optional<DeviceInfo> optional = deviceInfoRepository.findByDeviceId(deviceId);
        if (optional.isPresent()) {
            deviceInfoRepository.delete(optional.get());
            log.info("[设备] 删除设备: {}", deviceId);
        }
    }
}