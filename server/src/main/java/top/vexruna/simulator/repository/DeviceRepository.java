package top.vexruna.simulator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.vexruna.simulator.entity.DeviceInfo;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<DeviceInfo, Long> {

    Optional<DeviceInfo> findByDeviceId(String deviceId);

    List<DeviceInfo> findByStatus(String status);

    boolean existsByDeviceId(String deviceId);

    void deleteByDeviceId(String deviceId);
}