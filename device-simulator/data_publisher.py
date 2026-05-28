"""
多设备数据模拟上报器

作用：模拟 13 台 IoT 设备，定时向 MQTT Broker 上报温度、湿度等传感器数据
为什么用 Python：Python 的 paho-mqtt 库简单易用，适合快速搭建模拟器
为什么不用真实设备：开发阶段没有硬件，用软件模拟可以快速验证系统

设备类型：
  - 普通设备（10台）：正常上报温度/湿度
  - 重启型设备（1台）：模拟设备重启事件
  - 闪断型设备（1台）：模拟网络不稳定
  - 故障型设备（1台）：模拟传感器故障，上报错误码

数据流：
  本脚本 → MQTT Broker（device/device-N/reporter）→ SpringBoot 服务端
"""
import random
import json
import time
import sys
import paho.mqtt.client as mqtt

MQTT_BROKER = "localhost"
MQTT_PORT = 11883
MQTT_CLIENT_ID = "python-publisher"
MQTT_USERNAME = "mqtt_user"
MQTT_PASSWORD = "mqtt_password"

DEVICE_COUNT = 10
QOS_TYPES = [0, 1, 2]

connected = False
device_data = []
device_timers = []
device_type = []
device_names = []
device_types = []

def init_device_data():
    global device_data, device_timers, device_type, device_names, device_types
    
    device_type = ["normal"] * DEVICE_COUNT
    device_type[8] = "restart"
    device_type.append("flapping")
    device_type.append("error")
    
    device_names = [
        "温度传感器-01号", "温度传感器-02号", "温度传感器-03号", "温度传感器-04号", "温度传感器-05号",
        "湿度监测器-01号", "湿度监测器-02号", "湿度监测器-03号", "压力仪表-01号", "压力仪表-02号",
        "流量计-重启型", "功率监控-闪断型", "电压传感器-故障型"
    ]
    
    device_types = [
        "temperature_sensor", "temperature_sensor", "temperature_sensor", "temperature_sensor", "temperature_sensor",
        "humidity_monitor", "humidity_monitor", "humidity_monitor", "pressure_gauge", "pressure_gauge",
        "restart_device", "flapping_device", "error_device"
    ]
    
    device_data = []
    device_timers = []
    
    for i in range(DEVICE_COUNT + 3):
        device_data.append({
            "temp": 0.0, 
            "humidity": 0.0, 
            "qos": 0,
            "error_code": "",
            "online": True
        })
        device_timers.append(0)

def clear_screen():
    sys.stdout.write('\033[2J\033[H')
    sys.stdout.flush()

def on_connect(client, userdata, flags, rc, properties):
    global connected
    connected = (rc == 0)

def on_disconnect(client, userdata, rc, properties, reason_code):
    global connected
    connected = False

def generate_temperature():
    return round(random.uniform(15.0, 45.0), 2)

def generate_error():
    errors = ["E001:温度传感器故障", "E002:电压异常", "E003:通信超时", "E004:数据校验失败"]
    return random.choice(errors)

def publish_data(client):
    global device_data, device_timers
    
    clear_screen()
    
    while True:
        if not connected:
            print(f"[{time.strftime('%H:%M:%S')}] 连接中... {MQTT_BROKER}:{MQTT_PORT}")
            try:
                client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
            except Exception as e:
                print(f"[{time.strftime('%H:%M:%S')}] 错误: {e}")
            time.sleep(2)
            clear_screen()
            continue

        lines = []
        lines.append(f"[{time.strftime('%H:%M:%S')}] 已连接 | Broker: {MQTT_BROKER}:{MQTT_PORT}")
        
        for i in range(DEVICE_COUNT + 3):
            d_type = device_type[i] if i < len(device_type) else "normal"
            
            if device_timers[i] <= 0:
                device_timers[i] = random.randint(3, 60)
                device_data[i]["qos"] = random.choice(QOS_TYPES)
                device_data[i]["temp"] = generate_temperature()
                device_data[i]["humidity"] = round(random.uniform(30.0, 90.0), 1)
                
                topic = f"device/device-{i+1}/reporter"
                payload = {
                    "deviceId": f"device-{i+1}",
                    "deviceName": device_names[i] if i < len(device_names) else f"设备-{i+1}",
                    "deviceType": device_types[i] if i < len(device_types) else "generic",
                    "timestamp": int(time.time() * 1000),
                    "temperature": device_data[i]["temp"],
                    "humidity": device_data[i]["humidity"],
                    "status": "online"
                }
                
                if d_type == "error":
                    device_data[i]["error_code"] = generate_error()
                    payload["error"] = device_data[i]["error_code"]
                
                if d_type == "restart":
                    device_data[i]["restart_count"] = device_data[i].get("restart_count", 0) + 1
                    payload["event"] = "restart"
                    payload["restart_count"] = device_data[i]["restart_count"]
                
                client.publish(topic, json.dumps(payload), qos=device_data[i]["qos"])
            else:
                if d_type == "error" and random.random() < 0.1:
                    device_data[i]["error_code"] = generate_error()
                    topic = f"device/device-{i+1}/reporter"
                    payload = {
                        "deviceId": f"device-{i+1}",
                        "deviceName": device_names[i] if i < len(device_names) else f"设备-{i+1}",
                        "deviceType": device_types[i] if i < len(device_types) else "generic",
                        "timestamp": int(time.time() * 1000),
                        "error": device_data[i]["error_code"],
                        "status": "error"
                    }
                    client.publish(topic, json.dumps(payload), qos=1)
            
            device_timers[i] -= 1
            
            type_tag = {"normal": "", "restart": "[R]", "flapping": "[F]", "error": "[!]"}[d_type]
            
            if d_type == "error" and device_data[i]["error_code"]:
                lines.append(f"  device-{i+1:2} {type_tag}| {device_data[i]['temp']:>5.1f}°C | {device_data[i]['humidity']:>5.1f}% | {device_data[i]['error_code']:<16} | {device_timers[i]:2}s")
            else:
                lines.append(f"  device-{i+1:2} {type_tag}| {device_data[i]['temp']:>5.1f}°C | {device_data[i]['humidity']:>5.1f}% | QoS {device_data[i]['qos']} | {device_timers[i]:2}s")
        
        clear_screen()
        for line in lines:
            print(line)
        
        time.sleep(1)

def main():
    init_device_data()
    print("[MQTT Publisher] 启动中...")
    print(f"Broker: {MQTT_BROKER}:{MQTT_PORT} | 用户: {MQTT_USERNAME}")
    print(f"设备数量: {DEVICE_COUNT + 3} (普通: {DEVICE_COUNT}, 重启型: 1, 闪断型: 1, 故障型: 1)")
    print("图例: [R]=重启型 [F]=闪断型 [!]=故障型")
    time.sleep(1)
    
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=MQTT_CLIENT_ID)
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect

    try:
        client.loop_start()
        publish_data(client)

    except KeyboardInterrupt:
        print("\n\n[已停止]")
    finally:
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    main()