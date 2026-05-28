package top.vexruna.simulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.vexruna.simulator.service.ClickHouseDataService;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统健康检查控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final ClickHouseDataService clickHouseDataService;

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        
        // 这里可以添加更多健康检查逻辑
        return result;
    }
}
