package top.vexruna.simulator;

/**
 * MQTT 指令通信示例 —— 启动类
 *
 * 本项目演示 MQTT 双向通信：
 *   1. 订阅设备上报数据（device/+/reporter）
 *   2. 向设备发送控制指令（device/cmd/pc）
 *   3. HTTP 接口接收前端指令 → 通过 MQTT 发给设备
 *
 * 运行前提：
 *   - EMQX Broker 已启动（默认 localhost:11883）
 *   - application.yml 中配置了正确的连接信息
 */
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class CommandApplication {
    public static void main(String[] args) {
        // SpringApplication.run() 会自动扫描同包下的所有 @Component 类，完成依赖注入
        SpringApplication.run(CommandApplication.class, args);
        log.info("[启动] MQTT 指令通信示例启动成功");
    }
}
