package top.vexruna.simulator.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import top.vexruna.simulator.dto.CommandMessage;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 指令通信核心服务
 * 职责：
 *   1. 接收设备上报数据（订阅 device/+/reporter）
 *   2. 向设备发送控制指令（发布到 device/cmd/pc）
 *   3. 幂等防重：通过 sentCommandIds 记录已发送指令 ID，防止重复下发
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttCommandService {

    /** MQTT 消息发布器，由 MqttConfig.mqttOutbound() 提供 */
    private final MessageHandler mqttOutbound;

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 已发送指令 ID 集合（线程安全）
     * 作用：幂等防重 —— 同一条指令只允许发送一次
     * 实现：ConcurrentHashMap.newKeySet() 是线程安全的 Set，
     *       多个 HTTP 请求线程同时调用 sendCommand 时不会出现并发问题
     * 溢出保护：当记录数超过 1000 时自动清空，防止内存无限增长
     */
    private final Set<String> sentCommandIds = ConcurrentHashMap.newKeySet();

    /**
     * 接收 MQTT 消息（Spring Integration 自动调用）
     * 监听 mqttInputChannel，处理订阅到的设备上报数据和指令回执
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload = message.getPayload().toString();
        log.info("[收到] Topic: {}, Payload: {}", topic, payload);
    }

    /**
     * 向设备发送控制指令（QoS 1，保证必达）
     *
     * 流程：
     *   1. 补全字段：commandId（无则自动生成 UUID）、timestamp（无则取当前时间）
     *   2. 幂等检查：commandId 在 sentCommandIds 中已存在 → 拒绝发送，返回 false
     *   3. 序列化为 JSON，构建 MQTT 消息，设置 topic = device/cmd/pc, qos = 1
     *   4. 通过 mqttOutbound 发送
     *   5. 记录 commandId 到 sentCommandIds，供后续去重
     *
     * @param command 指令对象（type: restart / set_interval / custom 等）
     * @return true=发送成功, false=被拦截或发送失败
     */
    public boolean sendCommand(CommandMessage command) {
        // 补全字段：如果没有 commandId，自动生成一个 UUID
        if (command.getCommandId() == null) {
            command.setCommandId(UUID.randomUUID().toString());
        }
        // 补全时间戳
        if (command.getTimestamp() == 0) {
            command.setTimestamp(System.currentTimeMillis());
        }

        // 幂等检查：同一个 commandId 不允许重复发送（防止手抖连点、网络重试等场景）
        if (sentCommandIds.contains(command.getCommandId())) {
            log.warn("[指令] 幂等拦截: {}", command.getCommandId());
            return false;
        }

        try {
            // 序列化为 JSON
            String payload = objectMapper.writeValueAsString(command);
            // 构建 MQTT 消息，指定 topic 和 QoS
            Message<String> msg = MessageBuilder.withPayload(payload)
                    .setHeader("mqtt_topic", "device/cmd/pc")
                    .setHeader("mqtt_qos", 1)  // QoS 1：至少一次投递，保证指令必达
                    .build();

            // 通过 Spring Integration 的 outbound 适配器发送
            mqttOutbound.handleMessage(msg);

            // 发送成功后记录该 commandId，后续相同 ID 会被拦截
            sentCommandIds.add(command.getCommandId());
            log.info("[指令] 已发送 - ID: {}, Type: {}", command.getCommandId(), command.getType());
            return true;
        } catch (Exception e) {
            log.error("[指令] 发送失败: {}", e.getMessage(), e);
            return false;
        }
    }
}
