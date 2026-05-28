package top.vexruna.simulator.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceResponse {

    private Long id;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String status;
    private LocalDateTime lastOnlineTime;
    private LocalDateTime registerTime;
    private LocalDateTime updateTime;
}