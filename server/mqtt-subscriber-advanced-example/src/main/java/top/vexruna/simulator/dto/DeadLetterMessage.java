package top.vexruna.simulator.dto;

/**
 * 死信消息 DTO
 *
 * 什么是死信：MQTT 消息收到后但无法正常处理（JSON 格式错误、缺少必填字段等）
 * 这些消息不会丢弃，而是记录到 DeadLetterMessage 中，供排查问题
 *
 * 存储方式：RingBuffer<DeadLetterMessage>(100)，最多保留最近 100 条
 */
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息来源的 MQTT Topic */
    private String topic;

    /** 原始消息内容（未解析的 JSON 字符串） */
    private String rawPayload;

    /** 失败原因，例如 "JSON 解析异常: Unexpected character" */
    private String reason;

    /** 记录时间 */
    private long timestamp;

    /** 异常类型，例如 "JsonProcessingException" */
    private String exceptionType;

    /** 工厂方法，自动填充当前时间戳 */
    public static DeadLetterMessage of(String topic, String rawPayload, String reason, String exceptionType) {
        return new DeadLetterMessage(topic, rawPayload, reason, System.currentTimeMillis(), exceptionType);
    }
}
