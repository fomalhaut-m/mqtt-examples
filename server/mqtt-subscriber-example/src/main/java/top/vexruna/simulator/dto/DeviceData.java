package top.vexruna.simulator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

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