/**
 * MQTT 消息处理器 —— 多线程消费的核心逻辑
 *
 * 为什么用 @ServiceActivator 而不是 @RabbitListener：
 *   - 这里用的是 Spring Integration 的 MQTT 适配器，不是 Spring AMQP
 *   - @ServiceActivator 声明"这个方法处理 mqttExecutorChannel 中的消息"
 *   - 因为 channel 是 ExecutorChannel，所以方法在不同线程中被并发调用
 *
 * 异常处理策略（三层防线）：
 *   - 第一层：本方法 try-catch，区分 JSON 解析异常和未知异常
 *   - 第二层：outputChannel 指向 mqttErrorChannel，Spring 抛出的异常由 MqttErrorHandler 兜底
 *   - 第三层：GlobalExceptionHandler 处理 HTTP 层异常
 */
package top.vexruna.simulator.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.stereotype.Component;
import top.vexruna.simulator.dto.DeviceData;
import top.vexruna.simulator.service.DeviceDataService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProcessor {

    private final DeviceDataService deviceDataService;
    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ServiceActivator(inputChannel = "mqttExecutorChannel", outputChannel = "mqttErrorChannel")
    public void processMessage(Message<?> message) {
        String topic = null;
        String payload = null;
        try {
            topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            Integer qos = (Integer) message.getHeaders().get("mqtt_receivedQos");
            payload = message.getPayload().toString();

            log.debug("[消费] 收到消息 - Topic: {}, QoS: {}, Thread: {}",
                    topic, qos, Thread.currentThread().getName());

            DeviceData deviceData = objectMapper.readValue(payload, DeviceData.class);
            if (deviceData == null || deviceData.getDeviceId() == null) {
                throw new MessageHandlingException(message, "消息解析结果为空或缺少 deviceId");
            }

            deviceData.setTopic(topic);
            deviceData.setQos(qos);

            String deviceId = extractDeviceId(topic);
            if (deviceId != null) {
                deviceData.setDeviceId(deviceId);
            }

            deviceDataService.processAndStore(deviceData);

        } catch (org.springframework.messaging.MessageHandlingException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[消费] JSON 解析失败 - Topic: {}, Error: {}", topic, e.getMessage());
            deadLetterService.capture(topic, payload, "JSON 解析异常: " + e.getMessage(), e.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("[消费] 处理异常 - Topic: {}, Error: {}", topic, e.getMessage(), e);
            deadLetterService.capture(topic, payload, "未知异常: " + e.getMessage(), e.getClass().getSimpleName());
        }
    }

    private String extractDeviceId(String topic) {
        if (topic != null && topic.contains("/")) {
            String[] parts = topic.split("/");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }
}
