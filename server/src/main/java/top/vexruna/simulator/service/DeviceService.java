package top.vexruna.simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.vexruna.simulator.dto.DeviceResponse;
import top.vexruna.simulator.entity.DeviceInfo;
import top.vexruna.simulator.repository.DeviceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    @Transactional
    public DeviceResponse registerDevice(String deviceId, String deviceName, String deviceType) {
        log.info("注册新设备 - DeviceId: {}, Name: {}", deviceId, deviceName);

        if (deviceRepository.existsByDeviceId(deviceId)) {
            throw new RuntimeException("设备已存在: " + deviceId);
        }

        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceId(deviceId);
        deviceInfo.setDeviceName(deviceName);
        deviceInfo.setDeviceType(deviceType != null ? deviceType : "unknown");
        deviceInfo.setStatus("offline");

        deviceRepository.save(deviceInfo);
        return convertToResponse(deviceInfo);
    }

    @Transactional
    public DeviceInfo registerOrUpdateDevice(String deviceId) {
        Optional<DeviceInfo> existing = deviceRepository.findByDeviceId(deviceId);

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

        return deviceRepository.save(device);
    }

    @Transactional
    public void updateDeviceStatus(String deviceId, String status) {
        Optional<DeviceInfo> optional = deviceRepository.findByDeviceId(deviceId);
        if (optional.isPresent()) {
            DeviceInfo device = optional.get();
            device.setStatus(status);
            if ("online".equals(status)) {
                device.setLastOnlineTime(LocalDateTime.now());
            }
            deviceRepository.save(device);
            log.info("[设备] 更新状态 - {}: {}", deviceId, status);
        } else {
            log.warn("[设备] 设备不存在: {}", deviceId);
        }
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevices() {
        return deviceRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<DeviceResponse> getDeviceById(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .map(this::convertToResponse);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getOnlineDevices() {
        return deviceRepository.findByStatus("online").stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getOfflineDevices() {
        return deviceRepository.findByStatus("offline").stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional
    public void deleteDevice(String deviceId) {
        log.info("删除设备 - DeviceId: {}", deviceId);
        deviceRepository.deleteByDeviceId(deviceId);
    }

    @Transactional
    public void setDeviceOnline(String deviceId) {
        deviceRepository.findByDeviceId(deviceId).ifPresent(device -> {
            device.setStatus("online");
            device.setLastOnlineTime(LocalDateTime.now());
            deviceRepository.save(device);
        });
    }

    @Transactional
    public void setDeviceOffline(String deviceId) {
        deviceRepository.findByDeviceId(deviceId).ifPresent(device -> {
            device.setStatus("offline");
            deviceRepository.save(device);
        });
    }

    private DeviceResponse convertToResponse(DeviceInfo deviceInfo) {
        DeviceResponse response = new DeviceResponse();
        response.setDeviceId(deviceInfo.getDeviceId());
        response.setDeviceName(deviceInfo.getDeviceName());
        response.setDeviceType(deviceInfo.getDeviceType());
        response.setStatus(deviceInfo.getStatus());
        response.setLastOnlineTime(deviceInfo.getLastOnlineTime());
        response.setRegisterTime(deviceInfo.getRegisterTime());
        response.setUpdateTime(deviceInfo.getUpdateTime());
        return response;
    }
}