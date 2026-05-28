package top.vexruna.simulator.mqtt.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.io.Serializable;

/**
 * 设备数据模型
 * 用于解析 MQTT 消息中的设备数据
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceData implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String deviceId;
    private Long timestamp;
    private Double temperature;
    private Double humidity;
    private Double voltage;
    private String status;
    private String error;
    private String topic;
    private Integer qos;
}