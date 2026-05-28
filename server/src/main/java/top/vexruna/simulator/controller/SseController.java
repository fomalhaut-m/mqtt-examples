package top.vexruna.simulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.vexruna.simulator.dto.ApiResponse;
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
        log.info("[SSE Controller] 客户端请求实时数据流: {}", effectiveClientId);
        return sseService.connect(effectiveClientId);
    }

    @GetMapping("/status")
    public Map<String, Object> getSseStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeConnections", sseService.getActiveConnectionCount());
        return status;
    }

    @GetMapping("/system/snapshot")
    public ApiResponse<Object> getSystemSnapshot() {
        Object data = sseService.getLastSystemMetrics();
        if (data == null) {
            return ApiResponse.error("暂无系统指标数据");
        }
        return ApiResponse.success(data);
    }

    @GetMapping("/lan/snapshot")
    public ApiResponse<Object> getLanSnapshot() {
        Object data = sseService.getLastLanScan();
        if (data == null) {
            return ApiResponse.error("暂无局域网扫描数据");
        }
        return ApiResponse.success(data);
    }
}
