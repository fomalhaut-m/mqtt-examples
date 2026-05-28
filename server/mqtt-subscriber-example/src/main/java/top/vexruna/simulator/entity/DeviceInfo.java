package top.vexruna.simulator.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "device_info")
public class DeviceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String deviceId;

    @Column(length = 128)
    private String deviceName;

    @Column(length = 64)
    private String deviceType;

    @Column(length = 32)
    private String status;

    @Column(name = "last_online_time")
    private LocalDateTime lastOnlineTime;

    @CreationTimestamp
    @Column(name = "register_time", updatable = false)
    private LocalDateTime registerTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
