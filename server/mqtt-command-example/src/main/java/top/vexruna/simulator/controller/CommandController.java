/**
 * 指令控制器 —— HTTP 接口层
 *
 * 作用：接收前端的 HTTP 请求，转换为 MQTT 指令发送给设备
 * 数据流：前端 POST 请求 → 本 Controller → MqttCommandService.sendCommand() → MQTT Broker → 设备
 *
 * 为什么用 REST 而不是直接调 MQTT：
 *   - 前端（浏览器）无法直接发 MQTT 消息
 *   - 通过 HTTP 接口中转，前端只需调 POST 接口即可下发指令
 */
package top.vexruna.simulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.vexruna.simulator.dto.CommandMessage;
import top.vexruna.simulator.mqtt.MqttCommandService;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/commands")
@RequiredArgsConstructor
public class CommandController {

    /** 指令服务，负责实际的 MQTT 发布操作 */
    private final MqttCommandService mqttCommandService;

    /**
     * 通用指令发送接口
     * 前端传任意指令 JSON，由本接口转发到 MQTT
     *
     * 示例：POST /api/commands/send
     *       Body: {"type":"restart","params":{}}
     */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody CommandMessage command) {
        // 如果前端没传 commandId，服务端自动生成一个 UUID
        if (command.getCommandId() == null) {
            command.setCommandId(UUID.randomUUID().toString());
        }
        // 调用服务层发送指令，返回是否成功
        boolean ok = mqttCommandService.sendCommand(command);
        return Map.of("success", ok, "commandId", command.getCommandId());
    }

    /**
     * 快捷指令：重启设备模拟
     * 前端点击"重启"按钮时调用，无需传 body
     *
     * 示例：POST /api/commands/restart
     */
    @PostMapping("/restart")
    public Map<String, Object> restart() {
        CommandMessage cmd = new CommandMessage();
        cmd.setCommandId(UUID.randomUUID().toString());
        cmd.setType("restart");
        cmd.setTimestamp(System.currentTimeMillis());
        boolean ok = mqttCommandService.sendCommand(cmd);
        return Map.of("success", ok, "commandId", cmd.getCommandId());
    }

    /**
     * 快捷指令：调整设备采集频率
     * 前端传入 interval 参数（单位：毫秒），例如 interval=2000 表示每 2 秒采集一次
     *
     * 示例：POST /api/commands/setInterval?interval=2000
     */
    @PostMapping("/setInterval")
    public Map<String, Object> setInterval(@RequestParam int interval) {
        CommandMessage cmd = new CommandMessage();
        cmd.setCommandId(UUID.randomUUID().toString());
        cmd.setType("set_interval");
        cmd.setParams(Map.of("interval", interval));
        cmd.setTimestamp(System.currentTimeMillis());
        boolean ok = mqttCommandService.sendCommand(cmd);
        return Map.of("success", ok, "commandId", cmd.getCommandId());
    }
}
