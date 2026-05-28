package top.vexruna.simulator.test_mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import top.vexruna.simulator.config.MqttConfig;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttSubscriber {

    private final MqttConfig mqttConfig;

    private MqttClient client;

    @PostConstruct
    public void subscribe() {
        try {
            String url = mqttConfig.getHost();
            client = new MqttClient(url, "subscriber", null);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(mqttConfig.getUsername());
            options.setPassword(mqttConfig.getPassword().toCharArray());
            options.setAutomaticReconnect(true);

            client.connect(options);
            log.info("[MQTT] 连接成功: {}", url);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("[MQTT] 连接断开: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    log.info("[MQTT] 收到消息 [{}]: {}", topic, new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            client.subscribe("device/+/reporter", 1);
            log.info("[MQTT] 订阅成功: device/+/reporter");

        } catch (Exception e) {
            log.error("[MQTT] 订阅失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (client != null) {
                client.disconnect();
                log.info("[MQTT] 已断开连接");
            }
        } catch (MqttException e) {
            log.error("[MQTT] 断开连接失败: {}", e.getMessage());
        }
    }
}