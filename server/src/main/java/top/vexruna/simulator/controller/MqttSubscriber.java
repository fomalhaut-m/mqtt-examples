package top.vexruna.simulator.controller;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * MQTT 消息订阅器
 * 负责连接 MQTT Broker 并订阅设备数据上报主题
 */
@Slf4j
@Component
public class MqttSubscriber {
    
    @Value("${mqtt.broker.host}")
    private String host;
    
    @Value("${mqtt.broker.port}")
    private int port;
    
    @Value("${mqtt.broker.username}")
    private String username;
    
    @Value("${mqtt.broker.password}")
    private String password;
    
    private MqttClient client;
    
    /**
     * 启动时连接 MQTT Broker 并订阅主题
     */
    @PostConstruct
    public void subscribe() {
        try {
            String url = "tcp://" + host + ":" + port;
            client = new MqttClient(url, "subscriber", null);
            
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.toCharArray());
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
    
    /**
     * 关闭时断开 MQTT 连接
     */
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