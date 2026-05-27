package top.vexruna.simulator.device.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.vexruna.simulator.device.entity.DeviceInfo;

import java.util.List;
import java.util.Optional;

/**
 * 设备信息数据访问层
 * 提供设备信息的持久化操作
 */
@Repository
public interface DeviceInfoRepository extends JpaRepository<DeviceInfo, Long> {
    
    /**
     * 根据设备ID查询设备
     */
    Optional<DeviceInfo> findByDeviceId(String deviceId);
    
    /**
     * 根据状态查询设备列表
     */
    List<DeviceInfo> findByStatus(String status);
}