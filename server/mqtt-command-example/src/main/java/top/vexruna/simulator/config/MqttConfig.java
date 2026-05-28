/**
 * MQTT 连接配置类
 *
 * 作用：配置 MQTT 客户端的连接参数、订阅通道、发布通道
 * 为什么单独一个配置类：
 *   - MQTT 涉及两个方向：订阅（收数据）和 发布（发指令），需要分别配置
 *   - Spring Integration 通过 Channel 串联各组件，这里定义了输入/输出通道
 *
 * 数据流向：
 *   订阅方向：EMQX → mqttInbound(适配器) → mqttInputChannel → MqttCommandService.handleMessage()
 *   发布方向：MqttCommandService.sendCommand() → mqttOutbound(适配器) → EMQX
 */
package top.vexruna.simulator.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
@Slf4j
@IntegrationComponentScan
@ConfigurationProperties(prefix = "spring.mqtt")
@Data
public class MqttConfig {

    /** MQTT Broker 地址，例如 "tcp://localhost:11883"，从 application.yml 读取 */
    private String host;

    /** MQTT 客户端唯一标识，Broker 用它区分不同连接 */
    private String clientId;

    /** 订阅的主题列表（逗号分隔），例如 "device/+/reporter,device/+/reply" */
    private String subscribeTopics;

    /** 发布指令的默认主题，例如 "device/cmd/pc" */
    private String commandTopic;

    /** 消息质量等级：0=最多一次, 1=至少一次, 2=恰好一次 */
    private int qos;

    /** MQTT 认证用户名 */
    private String username;

    /** MQTT 认证密码 */
    private String password;

    /**
     * 创建 MQTT 连接工厂（核心配置）
     *
     * 为什么用 Factory 模式：
     *   - MqttPahoClientFactory 封装了 Paho 客户端的创建细节
     *   - Spring Integration 的 inbound/outbound 适配器都依赖这个工厂来创建连接
     *
     * 关键参数说明：
     *   - setCleanSession(true)：每次连接都是全新会话，不接收离线消息
     *   - setAutomaticReconnect(true)：断线后 Paho 自动重连（指数退避）
     *   - setKeepAliveInterval(60)：每 60 秒发一次心跳，Broker 据此判断客户端是否在线
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{host});
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);

        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * 消息输入通道（Spring Integration 的管道）
     *
     * 为什么需要 Channel：
     *   - Spring Integration 用 Channel 连接"消息源"和"消息处理器"
     *   - mqttInbound 收到消息后放入 mqttInputChannel
     *   - MqttCommandService.handleMessage() 从 mqttInputChannel 取出处理
     *   - DirectChannel 是同步通道，一条消息处理完才接下一条
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT 订阅适配器（收消息）
     *
     * 作用：连接 EMQX Broker，订阅指定主题，收到消息后放入 mqttInputChannel
     * 为什么叫"适配器"：它把 MQTT 协议的消息转换成 Spring Integration 的 Message 对象
     */
    @Bean
    public MessageProducer mqttInbound(MqttPahoClientFactory mqttClientFactory, MessageChannel mqttInputChannel) {
        // 将逗号分隔的 topic 字符串拆分为数组
        String[] topics = subscribeTopics.split(",");
        // clientId + "-sub"：订阅用独立客户端 ID，避免和发布端冲突
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-sub", mqttClientFactory, topics);
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel);
        log.info("[MQTT] 订阅: {}, QoS: {}", subscribeTopics, qos);
        return adapter;
    }

    /**
     * MQTT 发布适配器（发消息）
     *
     * 作用：将 Spring Integration 的 Message 发送到 MQTT Broker
     * 使用场景：MqttCommandService 调用 mqttOutbound.handleMessage() 时，消息经由此适配器发出
     *
     * 关键配置：
     *   - setAsync(true)：异步发送，不阻塞调用线程
     *   - setDefaultTopic()：默认发送到 device/cmd/pc
     *   - setDefaultQos(1)：QoS 1 保证指令至少送达一次
     */
    @Bean
    public MessageHandler mqttOutbound(MqttPahoClientFactory mqttClientFactory) {
        // clientId + "-pub"：发布用独立客户端 ID
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientId + "-pub", mqttClientFactory);
        handler.setAsync(true);
        handler.setDefaultTopic(commandTopic);
        handler.setDefaultQos(1);
        log.info("[MQTT] 发布指令 -> {}, QoS: 1", commandTopic);
        return handler;
    }
}
