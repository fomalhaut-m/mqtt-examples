package top.vexruna.simulator.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 服务
 * 负责管理 SSE 连接和实时数据推送
 */
@Slf4j
@Service
public class SseService {
    
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private static final Long DEFAULT_TIMEOUT = 30 * 60 * 1000L;
    
    /**
     * 建立 SSE 连接
     */
    public SseEmitter connect(String clientId) {
        log.info("[SSE] 客户端连接: {}", clientId);
        
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.put(clientId, emitter);
        
        emitter.onCompletion(() -> {
            log.info("[SSE] 连接完成: {}", clientId);
            emitters.remove(clientId);
        });
        
        emitter.onTimeout(() -> {
            log.warn("[SSE] 连接超时: {}", clientId);
            emitters.remove(clientId);
        });
        
        emitter.onError((throwable) -> {
            log.error("[SSE] 连接错误: {} - {}", clientId, throwable.getMessage());
            emitters.remove(clientId);
        });
        
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"message\":\"SSE连接成功\",\"clientId\":\"" + clientId + "\"}"));
        } catch (IOException e) {
            log.error("[SSE] 发送连接消息失败: {}", e.getMessage());
        }
        
        return emitter;
    }
    
    /**
     * 向所有客户端广播消息
     */
    public void broadcast(String eventName, Object data) {
        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                log.error("[SSE] 广播消息失败: {}", clientId);
                emitters.remove(clientId);
            }
        });
    }
    
    /**
     * 向指定客户端发送消息
     */
    public void sendToClient(String clientId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                log.error("[SSE] 发送消息失败: {}", clientId);
                emitters.remove(clientId);
            }
        }
    }
    
    /**
     * 推送设备数据
     */
    public void pushDeviceData(Object deviceData) {
        broadcast("deviceData", deviceData);
    }
    
    /**
     * 推送设备状态变化
     */
    public void pushDeviceStatus(String deviceId, String status) {
        broadcast("deviceStatus", Map.of(
                "deviceId", deviceId,
                "status", status,
                "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * 推送告警信息
     */
    public void pushAlert(Object alert) {
        broadcast("alert", alert);
    }
    
    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return emitters.size();
    }
    
    /**
     * 断开指定客户端连接
     */
    public void disconnect(String clientId) {
        SseEmitter emitter = emitters.remove(clientId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.error("[SSE] 断开连接失败: {}", clientId);
            }
        }
    }
}