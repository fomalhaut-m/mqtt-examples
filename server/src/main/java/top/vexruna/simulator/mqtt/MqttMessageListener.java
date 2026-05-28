package top.vexruna.simulator.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

/**
 * MQTT 消息监听配置
 * 将 MQTT 接收到的消息路由到 DeviceDataCollector 处理
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttMessageListener {

    private final DeviceDataCollector deviceDataCollector;

    /**
     * 创建消息输入通道（与 MqttConfig 中的 mqttInputChannel 对应）
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * 监听 MQTT 消息并处理
     * 从消息头中提取 topic 和 qos，从 payload 中获取数据
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMqttMessage(Message<?> message) {
        try {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            Integer qos = (Integer) message.getHeaders().get("mqtt_receivedQos");
            String payload = message.getPayload().toString();

            log.debug("[MQTT] 收到消息 - Topic: {}, QoS: {}", topic, qos);

            if (topic != null && topic.startsWith("system/")) {
                deviceDataCollector.processSystemMetrics(topic, payload, qos != null ? qos : 0);
            } else if (topic != null && topic.startsWith("lan/")) {
                deviceDataCollector.processLanScan(topic, payload, qos != null ? qos : 0);
            } else {
                deviceDataCollector.processDeviceData(topic, payload, qos != null ? qos : 0);
            }

        } catch (Exception e) {
            log.error("[MQTT] 消息处理失败 - Error: {}", e.getMessage(), e);
        }
    }
}
