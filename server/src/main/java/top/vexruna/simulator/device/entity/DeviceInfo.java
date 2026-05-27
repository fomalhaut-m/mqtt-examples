package top.vexruna.simulator.device.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设备信息实体
 * 用于存储在 H2 数据库中的设备基础信息
 */
@Data
@Entity
@Table(name = "device_info")
public class DeviceInfo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 100)
    private String deviceId;
    
    @Column(length = 200)
    private String deviceName;
    
    @Column(length = 50)
    private String deviceType;
    
    @Column(length = 20)
    private String status;
    
    private LocalDateTime lastOnlineTime;
    private LocalDateTime registerTime;
    private LocalDateTime updateTime;
    
    @Column(length = 500)
    private String description;
    
    @PrePersist
    protected void onCreate() {
        registerTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = "offline";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}