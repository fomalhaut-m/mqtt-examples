package top.vexruna.simulator.dto;

/**
 * 指令消息 DTO（数据传输对象）
 *
 * 作用：定义发送给设备的控制指令格式
 * 流程：前端传 JSON → SpringBoot 反序列化为本对象 → 序列化为 JSON 发到 MQTT
 *
 * 示例 JSON：
 *   {"commandId":"abc-123","type":"restart","params":{},"timestamp":1716912000000}
 */
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommandMessage {

    /** 指令唯一 ID，用于幂等防重（同一个 ID 只允许执行一次） */
    private String commandId;

    /**
     * 指令类型，设备根据 type 决定执行什么操作：
     *   - "restart"    → 重启模拟
     *   - "set_interval" → 调整采集频率（params 中带 interval 值）
     *   - "custom"     → 自定义透传指令
     */
    private String type;

    /** 指令参数，不同 type 对应不同的 params 结构，例如 {"interval": 2000} */
    private Object params;

    /** 指令创建时间（毫秒级时间戳） */
    private long timestamp;
}
