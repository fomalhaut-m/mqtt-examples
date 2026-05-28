package top.vexruna.simulator.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.vexruna.simulator.dto.ApiResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final List<Map<String, Object>> alertStore = new ArrayList<>();
    private volatile boolean alertEnabled = true;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAlerts() {
        return ApiResponse.success(new ArrayList<>(alertStore));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getAlertStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", alertEnabled);
        status.put("alertCount", alertStore.size());
        return ApiResponse.success(status);
    }

    @PostMapping("/manual")
    public ApiResponse<Map<String, Object>> triggerManualAlert(@RequestBody(required = false) Map<String, Object> alertData) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "manual");
        alert.put("message", alertData != null ? alertData.getOrDefault("message", "手动触发报警") : "手动触发报警");
        alert.put("timestamp", System.currentTimeMillis());
        alertStore.add(alert);
        return ApiResponse.success("手动报警已触发", alert);
    }

    @PostMapping("/toggle")
    public ApiResponse<Map<String, Object>> toggleAlert() {
        alertEnabled = !alertEnabled;
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", alertEnabled);
        return ApiResponse.success("报警已" + (alertEnabled ? "开启" : "关闭"), status);
    }
}
