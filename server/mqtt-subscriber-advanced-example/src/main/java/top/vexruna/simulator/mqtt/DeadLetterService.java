/**
 * 死信服务 —— 记录无法正常处理的 MQTT 消息
 *
 * 什么是死信队列（Dead Letter Queue）：
 *   - 正常消息走主流程（解析 → 存储 → SSE 推送）
 *   - 异常消息（JSON 错误、格式不对等）走死信队列
 *   - 这样一条坏消息不会阻塞其他正常消息
 *
 * 为什么用 RingBuffer 而不是 List：
 *   - List 无限增长会 OOM（内存溢出）
 *   - RingBuffer 固定 100 条，写满自动覆盖最旧的，内存可控
 *
 * 日志策略：
 *   - 主日志（@Slf4j）：记录正常流程
 *   - DEAD_LETTER 日志：死信单独写到 logs/dead-letter.log，方便排查
 */
package top.vexruna.simulator.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.vexruna.simulator.dto.DeadLetterMessage;
import top.vexruna.simulator.queue.RingBuffer;

import java.util.List;

@Slf4j
@Service
public class DeadLetterService {

    private static final Logger deadLetterLog = LoggerFactory.getLogger("DEAD_LETTER");

    private final RingBuffer<DeadLetterMessage> deadLetterBuffer = new RingBuffer<>(100);

    public void capture(String topic, String rawPayload, String reason, String exceptionType) {
        DeadLetterMessage dlq = DeadLetterMessage.of(topic, rawPayload, reason, exceptionType);
        deadLetterBuffer.add(dlq);
        deadLetterLog.warn("[死信] topic={}, reason={}, exceptionType={}", topic, reason, exceptionType);
    }

    public List<DeadLetterMessage> getRecentDeadLetters() {
        return deadLetterBuffer.toList();
    }

    public int getDeadLetterCount() {
        return deadLetterBuffer.size();
    }
}
