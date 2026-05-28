/**
 * SSE（Server-Sent Events）推送服务
 *
 * 什么是 SSE：
 *   - 服务器单向推送技术，浏览器通过 EventSource API 建立长连接
 *   - 相比 WebSocket 更轻量，只支持服务器→客户端的单向通信
 *   - 适合实时数据推送场景（设备数据、告警等）
 *
 * 为什么需要心跳：
 *   - SSE 连接长时间无数据可能被中间代理/防火墙断开
 *   - 每 30 秒发一次心跳保持连接活跃
 *
 * 资源回收：
 *   - 客户端断开、超时、出错时自动清理 SseEmitter 和心跳任务
 *   - @PreDestroy：服务停机时主动关闭所有连接
 */
package top.vexruna.simulator.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
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
        log.info("[SSE] 客户端连接: {}", clientId);

        if (emitters.containsKey(clientId)) {
            disconnect(clientId);
        }

        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        emitter.onCompletion(() -> cleanup(clientId));
        emitter.onTimeout(() -> {
            log.warn("[SSE] 超时: {}", clientId);
            cleanup(clientId);
        });
        emitter.onError(e -> {
            log.error("[SSE] 错误: {} - {}", clientId, e.getMessage());
            cleanup(clientId);
        });

        emitters.put(clientId, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"message\":\"SSE连接成功\",\"clientId\":\"" + clientId + "\"}"));
        } catch (IOException e) {
            log.error("[SSE] 握手失败: {}", clientId);
            emitter.complete();
        }

        startHeartbeat(clientId, emitter);
        return emitter;
    }

    private void startHeartbeat(String clientId, SseEmitter emitter) {
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("{\"type\":\"ping\",\"timestamp\":" + System.currentTimeMillis() + "}"));
            } catch (IOException e) {
                log.warn("[SSE] 心跳失败: {}", clientId);
                cleanup(clientId);
            }
        }, HEARTBEAT_INTERVAL);
        heartbeatTasks.put(clientId, task);
    }

    private void cleanup(String clientId) {
        ScheduledFuture<?> task = heartbeatTasks.remove(clientId);
        if (task != null) {
            task.cancel(false);
        }
        emitters.remove(clientId);
        log.info("[SSE] 资源回收: {}", clientId);
    }

    public void broadcast(String eventName, Object data) {
        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.error("[SSE] 广播失败: {}", clientId);
                cleanup(clientId);
            }
        });
    }

    public void pushDeviceData(Object deviceData) {
        broadcast("deviceData", deviceData);
    }

    public void pushSystemStatus(Object status) {
        broadcast("systemStatus", status);
    }

    public int getActiveConnectionCount() {
        return emitters.size();
    }

    public void disconnect(String clientId) {
        cleanup(clientId);
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("[SSE] 关闭异常: {}", clientId);
            }
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("[SSE] 服务停机 - 活跃连接: {}", emitters.size());
        broadcast("serverShutdown", Map.of(
                "message", "服务器即将停机",
                "timestamp", System.currentTimeMillis()
        ));
        List.copyOf(emitters.keySet()).forEach(this::disconnect);
    }
}
