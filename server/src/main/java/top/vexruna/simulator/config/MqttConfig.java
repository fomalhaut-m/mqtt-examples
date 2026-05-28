package top.vexruna.simulator.config;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;

@Configuration
@Slf4j
@IntegrationComponentScan
public class MqttConfig {

    @Value("${spring.mqtt.host}")
    private String mqttHost;

    @Value("${spring.mqtt.client-id}")
    private String clientId;

    @Value("${spring.mqtt.topic}")
    private String topic;

    @Value("${spring.mqtt.qos}")
    private int qos;

    @Value("${spring.mqtt.username:}")
    private String username;

    @Value("${spring.mqtt.password:}")
    private String password;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttHost});
        options.setCleanSession(true);

        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, factory, topic);
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel());
        log.info("MQTT适配器已配置 - Topic: {}, QoS: {}", topic, qos);
        return adapter;
    }
}