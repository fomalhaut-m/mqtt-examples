/**
 * MQTT 连接配置类（进阶版）
 *
 * 与基础版的区别：
 *   - 使用 ExecutorChannel 代替 DirectChannel → 多线程消费
 *   - 配置自动重连参数 → 断线后自动恢复
 *   - 设置 errorChannel → 异常消息走错误通道兜底
 */
package top.vexruna.simulator.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
    private boolean autoReconnect;
    private int maxReconnectDelay;
    private int connectionTimeout;
    private int keepAliveInterval;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{host});
        options.setCleanSession(true);
        options.setAutomaticReconnect(autoReconnect);
        options.setMaxReconnectDelay(maxReconnectDelay);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);

        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }

        factory.setConnectionOptions(options);
        log.info("[MQTT] 连接配置 - Host: {}, 自动重连: {}, 最大重连延迟: {}ms",
                host, autoReconnect, maxReconnectDelay);
        return factory;
    }

    @Bean
    public MessageChannel mqttExecutorChannel(ThreadPoolTaskExecutor mqttTaskExecutor) {
        return new ExecutorChannel(mqttTaskExecutor);
    }

    @Bean
    public MessageProducer inbound(MqttPahoClientFactory factory, MessageChannel mqttExecutorChannel) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, factory, "device/+/reporter");
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttExecutorChannel);
        adapter.setErrorChannelName("mqttErrorChannel");
        log.info("[MQTT] 适配器已配置 - Topics: device/+/reporter, QoS: {}, 通道: ExecutorChannel(多线程)", qos);
        return adapter;
    }
}
