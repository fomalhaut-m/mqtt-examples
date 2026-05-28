/**
 * SSE 控制器 —— 提供 SSE 连接端点
 *
 * 前端通过 EventSource("http://localhost:8082/sse/data?clientId=xxx") 连接
 * 连接后，服务端会持续推送设备数据、系统状态等实时信息
 */
package top.vexruna.simulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.vexruna.simulator.service.SseService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @GetMapping("/data")
    public SseEmitter streamData(@RequestParam(required = false) String clientId) {
        String effectiveClientId = (clientId != null && !clientId.isBlank())
                ? clientId
                : UUID.randomUUID().toString().substring(0, 8);
        log.info("[SSE] 客户端连接: {}", effectiveClientId);
        return sseService.connect(effectiveClientId);
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeConnections", sseService.getActiveConnectionCount());
        return status;
    }
}
