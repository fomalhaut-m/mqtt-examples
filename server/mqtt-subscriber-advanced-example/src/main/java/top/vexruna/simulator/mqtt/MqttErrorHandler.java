/**
 * MQTT 错误通道处理器（第二层异常防线）
 *
 * 为什么需要这个类：
 *   - MessageProcessor 是第一层 try-catch，但它可能漏掉一些 Spring 框架内部抛出的异常
 *   - Spring Integration 的 errorChannel 是全局兜底机制
 *   - 任何 @ServiceActivator 抛出的未捕获异常都会自动路由到这里
 *
 * 数据流：
 *   MessageProcessor 抛异常 → Spring 自动发到 mqttErrorChannel → 本类 handleError() 处理
 */
package top.vexruna.simulator.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.stereotype.Component;
import top.vexruna.simulator.dto.DeadLetterMessage;

@Slf4j
@Component
public class MqttErrorHandler {

    private final DeadLetterService deadLetterService;

    public MqttErrorHandler(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    @ServiceActivator(inputChannel = "mqttErrorChannel")
    public void handleError(Message<?> errorMessage) {
        if (errorMessage.getPayload() instanceof MessageHandlingException ex) {
            String topic = "unknown";
            String payload = "unknown";
            try {
                Object originalMessage = ex.getFailedMessage();
                if (originalMessage instanceof Message<?> origMsg) {
                    Object topicHeader = origMsg.getHeaders().get("mqtt_receivedTopic");
                    if (topicHeader != null) {
                        topic = topicHeader.toString();
                    }
                    payload = origMsg.getPayload().toString();
                }
            } catch (Exception ignored) {
            }
            log.error("[错误通道] 消息处理失败 - Topic: {}, Error: {}", topic, ex.getMessage());
            deadLetterService.capture(topic, payload,
                    "错误通道捕获: " + ex.getMessage(), ex.getClass().getSimpleName());
        }
    }
}
