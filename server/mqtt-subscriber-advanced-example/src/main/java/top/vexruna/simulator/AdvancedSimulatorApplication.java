package top.vexruna.simulator;

/**
 * MQTT 高级订阅示例 —— 启动类
 *
 * 在基础版之上新增：
 *   1. 多线程消费 —— ExecutorChannel + 线程池，解决消息堆积
 *   2. 自动重连   —— Paho 内置重连 + 连接状态监听
 *   3. 死信逻辑   —— 非法报文单独记录，不影响主流程
 *   4. 环形队列   —— 缓存最近 N 条数据，防内存溢出
 */
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class AdvancedSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdvancedSimulatorApplication.class, args);
        log.info("[启动] MQTT 高级订阅示例启动成功 - 多线程消费 + 自动重连 + 死信逻辑 + 环形队列");
    }
}
