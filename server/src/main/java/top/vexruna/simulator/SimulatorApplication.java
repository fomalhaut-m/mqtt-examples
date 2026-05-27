package top.vexruna.simulator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MQTT 设备模拟系统启动类
 * 
 * 功能：
 * 1. 连接 MQTT Broker 订阅设备数据
 * 2. 存储设备信息到 H2 数据库
 * 3. 存储时序数据到 ClickHouse
 * 4. 通过 SSE 实时推送数据
 */
@Slf4j
@SpringBootApplication
public class SimulatorApplication {
    public static void main(String[] args) {
        log.info("[启动] MQTT 设备模拟系统正在启动...");
        SpringApplication.run(SimulatorApplication.class, args);
        log.info("[启动] MQTT 设备模拟系统启动成功");
    }
}