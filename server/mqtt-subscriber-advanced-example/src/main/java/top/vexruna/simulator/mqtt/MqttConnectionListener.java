/**
 * MQTT 连接状态监听器
 *
 * 实现 Paho 的 MqttCallback 接口，监听连接生命周期事件
 * 作用：让系统知道 MQTT 连接是否正常，断了几次，什么时候恢复的
 * 使用场景：前端通过 /api/system/status 查看连接健康状态
 */
package top.vexruna.simulator.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MqttConnectionListener implements MqttCallback {

    private volatile boolean connected = false;
    private int reconnectAttempt = 0;

    @Override
    public void connectionLost(Throwable cause) {
        connected = false;
        reconnectAttempt++;
        log.warn("[重连] MQTT 连接断开 - 第 {} 次检测, 原因: {}", reconnectAttempt, cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        log.info("[消息] MQTT 收到消息 - Topic: {}, Payload: {}", topic, new String(message.getPayload()));
    }

    @Override
    public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
        log.info("[消息] MQTT 消息发送完成 - Token: {}", token);
    }

    public void onConnected(MqttClient client) {
        connected = true;
        reconnectAttempt = 0;
        log.info("[连接] MQTT 连接建立成功 - ClientId: {}", client.getClientId());
    }

    public void onReconnected(MqttClient client) {
        connected = true;
        log.info("[重连] MQTT 重连成功 - 第 {} 次重连, ClientId: {}", reconnectAttempt, client.getClientId());
        reconnectAttempt = 0;
    }

    public boolean isConnected() {
        return connected;
    }

    public int getReconnectAttempt() {
        return reconnectAttempt;
    }
}
