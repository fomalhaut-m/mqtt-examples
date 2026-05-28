package top.vexruna.simulator.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 控制器
 * 提供 SSE 连接接口
 */
@Slf4j
@RestController
@RequestMapping("/api/sse")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SseController {
    
    private final SseService sseService;
    
    /**
     * 建立 SSE 连接
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam(required = false) String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            clientId = "client-" + System.currentTimeMillis();
        }
        log.info("[API] SSE连接请求: {}", clientId);
        return sseService.connect(clientId);
    }
    
    /**
     * 获取活跃连接数
     */
    @GetMapping("/connections")
    public int getConnectionCount() {
        return sseService.getActiveConnectionCount();
    }
    
    /**
     * 断开指定客户端连接
     */
    @PostMapping("/disconnect/{clientId}")
    public String disconnect(@PathVariable String clientId) {
        log.info("[API] 断开SSE连接: {}", clientId);
        sseService.disconnect(clientId);
        return "已断开连接: " + clientId;
    }
}