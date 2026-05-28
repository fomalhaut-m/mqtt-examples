package top.vexruna.simulator.test;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Eclipse Paho MqttClient 单元测试
 * 测试MQTT连接、订阅、发布和接收消息
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MqttClientTest {

    @Value("${spring.mqtt.host}")
    private String mqttHost;

    @Value("${spring.mqtt.username}")
    private String username;

    @Value("${spring.mqtt.password}")
    private String password;

    private MqttClient publisherClient;
    private MqttClient subscriberClient;

    @BeforeEach
    void setUp() throws Exception {
        log.info("初始化MQTT客户端...");
        
        // 创建发布者客户端
        publisherClient = new MqttClient(mqttHost, "test-publisher-" + System.currentTimeMillis());
        
        // 创建订阅者客户端
        subscriberClient = new MqttClient(mqttHost, "test-subscriber-" + System.currentTimeMillis());
        
        // 配置连接选项
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        
        // 连接两个客户端
        publisherClient.connect(options);
        subscriberClient.connect(options);
        
        assertTrue(publisherClient.isConnected(), "发布者应该已连接");
        assertTrue(subscriberClient.isConnected(), "订阅者应该已连接");
        
        log.info("MQTT客户端初始化完成");
    }

    @AfterEach
    void tearDown() throws Exception {
        log.info("清理MQTT客户端...");
        
        if (publisherClient != null && publisherClient.isConnected()) {
            publisherClient.disconnect();
            publisherClient.close();
        }
        
        if (subscriberClient != null && subscriberClient.isConnected()) {
            subscriberClient.disconnect();
            subscriberClient.close();
        }
        
        log.info("MQTT客户端已清理");
    }

    @Test
    @Order(1)
    @DisplayName("测试MQTT连接")
    void testConnection() {
        log.info("测试MQTT连接...");
        
        assertTrue(publisherClient.isConnected(), "发布者客户端应该已连接");
        assertTrue(subscriberClient.isConnected(), "订阅者客户端应该已连接");
        
        log.info("MQTT连接测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("测试发布和接收消息")
    void testPublishAndSubscribe() throws Exception {
        log.info("测试发布和接收消息...");
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedPayload = new AtomicReference<>();
        AtomicReference<String> receivedTopic = new AtomicReference<>();
        
        String testTopic = "device/test-device-1/reporter";
        String testPayload = "{\"deviceId\":\"test-device-1\",\"temperature\":25.5,\"humidity\":60.0,\"voltage\":3.8,\"status\":\"online\"}";
        
        // 设置订阅者回调
        subscriberClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.error("订阅者连接丢失", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                log.info("收到消息 - Topic: {}, Payload: {}", topic, new String(message.getPayload()));
                receivedTopic.set(topic);
                receivedPayload.set(new String(message.getPayload()));
                latch.countDown();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                log.debug("消息发送完成");
            }
        });
        
        // 订阅主题
        subscriberClient.subscribe(testTopic, 1);
        log.info("已订阅主题: {}", testTopic);
        
        // 等待一小段时间确保订阅生效
        Thread.sleep(500);
        
        // 发布消息
        MqttMessage message = new MqttMessage(testPayload.getBytes());
        message.setQos(1);
        publisherClient.publish(testTopic, message);
        log.info("已发布消息到主题: {}", testTopic);
        
        // 等待接收消息（最多等待5秒）
        boolean received = latch.await(5, TimeUnit.SECONDS);
        
        assertTrue(received, "应该在5秒内收到消息");
        assertEquals(testTopic, receivedTopic.get(), "接收到的主题应该匹配");
        assertEquals(testPayload, receivedPayload.get(), "接收到的消息内容应该匹配");
        
        log.info("发布和接收消息测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("测试通配符主题订阅")
    void testWildcardSubscription() throws Exception {
        log.info("测试通配符主题订阅...");
        
        CountDownLatch latch = new CountDownLatch(3);
        AtomicReference<Integer> messageCount = new AtomicReference<>(0);
        
        String wildcardTopic = "device/+/reporter";
        
        // 设置订阅者回调
        subscriberClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.error("订阅者连接丢失", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                log.info("收到消息 - Topic: {}", topic);
                messageCount.accumulateAndGet( 1, Integer::sum);
                latch.countDown();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                log.debug("消息发送完成");
            }
        });
        
        // 订阅通配符主题
        subscriberClient.subscribe(wildcardTopic, 1);
        log.info("已订阅通配符主题: {}", wildcardTopic);
        
        // 等待一小段时间确保订阅生效
        Thread.sleep(500);
        
        // 发布多条消息到不同的设备主题
        String[] topics = {
            "device/device-1/reporter",
            "device/device-2/reporter",
            "device/device-3/reporter"
        };
        
        for (String topic : topics) {
            String payload = "{\"deviceId\":\"" + topic.split("/")[1] + "\",\"temperature\":25.0}";
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            publisherClient.publish(topic, message);
            log.info("已发布消息到: {}", topic);
            
            // 稍微延迟，避免消息过快
            Thread.sleep(100);
        }
        
        // 等待接收所有消息（最多等待5秒）
        boolean received = latch.await(5, TimeUnit.SECONDS);
        
        assertTrue(received, "应该在5秒内收到所有消息");
        assertEquals(3, messageCount.get(), "应该收到3条消息");
        
        log.info("通配符主题订阅测试通过");
    }

    @Test
    @Order(4)
    @DisplayName("测试不同QoS等级")
    void testDifferentQosLevels() throws Exception {
        log.info("测试不同QoS等级...");
        
        for (int qos = 0; qos <= 2; qos++) {
            log.info("测试QoS {}", qos);
            
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Boolean> received = new AtomicReference<>(false);
            
            String testTopic = "device/qos-test-" + qos + "/reporter";
            String testPayload = "{\"qos\":" + qos + "}";
            
            // 设置订阅者回调
            subscriberClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {}

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    received.set(true);
                    latch.countDown();
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });
            
            // 订阅主题
            subscriberClient.subscribe(testTopic, qos);
            Thread.sleep(200);
            
            // 发布消息
            MqttMessage message = new MqttMessage(testPayload.getBytes());
            message.setQos(qos);
            publisherClient.publish(testTopic, message);
            
            // 等待接收
            boolean success = latch.await(3, TimeUnit.SECONDS);
            
            assertTrue(success, "QoS " + qos + " 应该成功接收消息");
            assertTrue(received.get(), "QoS " + qos + " 消息应该被接收");
            
            log.info("QoS {} 测试通过", qos);
        }
        
        log.info("不同QoS等级测试通过");
    }

    @Test
    @Order(5)
    @DisplayName("测试断开重连")
    void testReconnection() throws Exception {
        log.info("测试断开重连...");
        
        // 先确认已连接
        assertTrue(publisherClient.isConnected(), "初始状态应该已连接");
        
        // 手动断开
        publisherClient.disconnect();
        assertFalse(publisherClient.isConnected(), "断开后应该未连接");
        
        // 重新连接
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        
        publisherClient.connect(options);
        assertTrue(publisherClient.isConnected(), "重连后应该已连接");
        
        // 测试重连后能否正常通信
        CountDownLatch latch = new CountDownLatch(1);
        
        subscriberClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {}

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                latch.countDown();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });
        
        subscriberClient.subscribe("device/reconnect-test/reporter", 1);
        Thread.sleep(200);
        
        MqttMessage message = new MqttMessage("{\"test\":\"reconnect\"}".getBytes());
        publisherClient.publish("device/reconnect-test/reporter", message);
        
        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "重连后应该能正常通信");
        
        log.info("断开重连测试通过");
    }
}
