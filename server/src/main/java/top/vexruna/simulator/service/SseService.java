package top.vexruna.simulator.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    private static final Long DEFAULT_TIMEOUT = 30 * 60 * 1000L;
    private static final Long HEARTBEAT_INTERVAL = 30 * 1000L;

    @Autowired
    private TaskScheduler taskScheduler;

    public SseEmitter connect(String clientId) {
        log.info("[SSE] 客户端连接请求: {}", clientId);

        if (emitters.containsKey(clientId)) {
            log.info("[SSE] 存在旧连接，执行断开: {}", clientId);
            disconnect(clientId);
        }

        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        emitter.onCompletion(() -> {
            log.info("[SSE] 连接完成: {}", clientId);
            cleanup(clientId);
        });

        emitter.onTimeout(() -> {
            log.warn("[SSE] 连接超时: {}", clientId);
            cleanup(clientId);
        });

        emitter.onError((throwable) -> {
            log.error("[SSE] 连接错误: {} - {}", clientId, throwable.getMessage());
            cleanup(clientId);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"message\":\"SSE连接成功\",\"clientId\":\"" + clientId + "\"}"));
        } catch (IOException e) {
            log.error("[SSE] 握手消息发送失败，终止连接: {}", clientId);
            try {
                emitter.complete();
            } catch (IOException ex) {
                log.warn("[SSE] 关闭连接失败: {}", clientId);
            }
            return emitter;
        }

        emitters.put(clientId, emitter);
        startHeartbeat(clientId, emitter);

        return emitter;
    }

    private void startHeartbeat(String clientId, SseEmitter emitter) {
        ScheduledFuture<?> heartbeatTask = taskScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("{\"type\":\"ping\",\"timestamp\":" + System.currentTimeMillis() + "}"));
                log.debug("[SSE] 心跳发送成功: {}", clientId);
            } catch (IOException e) {
                log.warn("[SSE] 心跳发送失败，触发资源回收: {}", clientId);
                cleanup(clientId);
            }
        }, HEARTBEAT_INTERVAL);

        heartbeatTasks.put(clientId, heartbeatTask);
    }

    private void stopHeartbeat(String clientId) {
        ScheduledFuture<?> task = heartbeatTasks.remove(clientId);
        if (task != null) {
            task.cancel(false);
            log.debug("[SSE] 心跳任务已取消: {}", clientId);
        }
    }

    private void cleanup(String clientId) {
        stopHeartbeat(clientId);
        emitters.remove(clientId);
        log.info("[SSE] 资源已回收: {}", clientId);
    }

    public void broadcast(String eventName, Object data) {
        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                log.error("[SSE] 广播消息失败: {}", clientId);
                cleanup(clientId);
            }
        });
    }

    public void sendToClient(String clientId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                log.error("[SSE] 发送消息失败: {}", clientId);
                cleanup(clientId);
            }
        }
    }

    public void pushDeviceData(Object deviceData) {
        broadcast("deviceData", deviceData);
    }

    public void pushDeviceStatus(String deviceId, String status) {
        broadcast("deviceStatus", Map.of(
                "deviceId", deviceId,
                "status", status,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void pushAlert(Object alert) {
        broadcast("alert", alert);
    }

    public int getActiveConnectionCount() {
        return emitters.size();
    }

    public void disconnect(String clientId) {
        log.info("[SSE] 主动断开连接: {}", clientId);
        cleanup(clientId);
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("[SSE] 完成连接关闭时异常: {}", clientId);
            }
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("[SSE] ========== 服务停机处理 ==========");
        log.info("[SSE] 通知停机，当前活跃连接数: {}", emitters.size());

        broadcast("serverShutdown", Map.of(
                "message", "服务器即将停机",
                "timestamp", System.currentTimeMillis()
        ));

        List<String> clientIds = List.copyOf(emitters.keySet());
        clientIds.forEach(clientId -> {
            log.info("[SSE] 关闭连接: {}", clientId);
            disconnect(clientId);
        });

        log.info("[SSE] 所有连接已关闭，流程闭环");
    }
}