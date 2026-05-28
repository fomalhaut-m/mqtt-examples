/**
 * 全局异常处理器（第三层异常防线）
 *
 * @RestControllerAdvice：拦截所有 @Controller/@RestController 抛出的异常
 * 为什么需要：防止异常直接暴露给前端（堆栈信息泄露 + 用户体验差）
 *
 * 三层防线总结：
 *   1. MessageProcessor.try-catch      → 捕获 MQTT 消息处理异常
 *   2. MqttErrorHandler                → 兜底 Spring Integration 错误通道
 *   3. GlobalExceptionHandler          → 兜底 HTTP 接口层异常
 */
package top.vexruna.simulator.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("[异常] 未处理异常: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "type", e.getClass().getSimpleName()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[异常] 参数错误: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "type", "IllegalArgumentException"
        ));
    }
}
