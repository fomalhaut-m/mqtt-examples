package top.vexruna.simulator.dto;

/**
 * 设备上报数据 DTO
 *
 * 对应设备上报的 JSON 格式，例如：
 *   {"deviceId":"device-1","temperature":25.5,"humidity":60.0,"voltage":3.8,"status":"online"}
 *
 * @JsonIgnoreProperties(ignoreUnknown = true)：JSON 中多余的字段自动忽略，防止反序列化报错
 */
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
    /** 来源 Topic，由服务端从 MQTT 消息头中提取后填入 */
    private String topic;
    /** 消息质量等级，由服务端从 MQTT 消息头中提取后填入 */
    private Integer qos;
}
