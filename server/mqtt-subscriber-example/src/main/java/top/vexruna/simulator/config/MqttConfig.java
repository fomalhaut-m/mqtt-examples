package top.vexruna.simulator.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;

@Configuration
@Slf4j
@IntegrationComponentScan
@ConfigurationProperties(prefix = "spring.mqtt")
@Data
public class MqttConfig {

    private String host;

    private String clientId;

    private String topic;

    private int qos;

    private String username;

    private String password;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{host});
        options.setCleanSession(true);

        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageProducer inbound(MqttPahoClientFactory factory, MessageChannel mqttInputChannel) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, factory, "device/+/reporter", "system/+/metrics", "lan/#");
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel);
        log.info("MQTT适配器已配置 - Topics: device/+/reporter, system/+/metrics, lan/#, QoS: {}", qos);
        return adapter;
    }
}