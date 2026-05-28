/**
 * MQTT 消费线程池配置
 *
 * 作用：为 ExecutorChannel 提供线程池，实现多线程并行消费 MQTT 消息
 *
 * 关键参数说明：
 *   - corePoolSize(4)：核心线程数，即使空闲也不会回收（除非配置 allowCoreThreadTimeOut）
 *   - maxPoolSize(16)：最大线程数，当队列满时会创建新线程直到此上限
 *   - queueCapacity(256)：等待队列容量，核心线程忙时消息先入队
 *   - CallerRunsPolicy：队列满+线程满时，由 MQTT IO 线程直接执行，防止消息丢失
 */
package top.vexruna.simulator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "spring.thread-pool")
public class ThreadPoolConfig {

    private int coreSize;
    private int maxSize;
    private int queueCapacity;
    private int keepAlive;
    private String namePrefix;

    @Bean("mqttTaskExecutor")
    public ThreadPoolTaskExecutor mqttTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAlive);
        executor.setThreadNamePrefix(namePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("[线程池] MQTT 消费线程池初始化 - core: {}, max: {}, queue: {}",
                coreSize, maxSize, queueCapacity);
        return executor;
    }

    public void setCoreSize(int coreSize) {
        this.coreSize = coreSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public void setKeepAlive(int keepAlive) {
        this.keepAlive = keepAlive;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }
}
