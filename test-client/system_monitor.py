import random
import json
import time
import sys
import platform
import paho.mqtt.client as mqtt

MQTT_BROKER = "localhost"
MQTT_PORT = 11883
MQTT_CLIENT_ID = "system-monitor"
MQTT_USERNAME = "mqtt_user"
MQTT_PASSWORD = "mqtt_password"

connected = False

try:
    import psutil
    HAS_PSUTIL = True
except ImportError:
    HAS_PSUTIL = False
    print("[WARNING] psutil not installed. Run: pip install psutil")

def clear_screen():
    sys.stdout.write('\033[2J\033[H')
    sys.stdout.flush()

def get_system_info():
    info = {
        "hostname": platform.node(),
        "platform": platform.system(),
        "arch": platform.machine()
    }
    
    if HAS_PSUTIL:
        info["cpu_percent"] = psutil.cpu_percent(interval=0.1)
        info["cpu_count"] = psutil.cpu_count()
        info["memory_percent"] = psutil.virtual_memory().percent
        info["memory_total_gb"] = round(psutil.virtual_memory().total / (1024**3), 2)
        info["memory_used_gb"] = round(psutil.virtual_memory().used / (1024**3), 2)
        
        try:
            info["disk_percent"] = psutil.disk_usage('/').percent
        except:
            info["disk_percent"] = 0
        
        try:
            net = psutil.net_io_counters()
            info["network_sent_mb"] = round(net.bytes_sent / (1024**2), 2)
            info["network_recv_mb"] = round(net.bytes_recv / (1024**2), 2)
        except:
            info["network_sent_mb"] = 0
            info["network_recv_mb"] = 0
    else:
        info["cpu_percent"] = 0
        info["cpu_count"] = 0
        info["memory_percent"] = 0
        info["memory_total_gb"] = 0
        info["memory_used_gb"] = 0
        info["disk_percent"] = 0
        info["network_sent_mb"] = 0
        info["network_recv_mb"] = 0
    
    return info

def on_connect(client, userdata, flags, rc, properties):
    global connected
    connected = (rc == 0)
    if connected:
        print(f"[CONNECTED] {MQTT_BROKER}:{MQTT_PORT}")

def on_disconnect(client, userdata, rc, properties, reason_code):
    global connected
    connected = False

def publish_data(client):
    print("\n" * 15)
    
    while True:
        if not connected:
            print(f"[{time.strftime('%H:%M:%S')}] Connecting to {MQTT_BROKER}:{MQTT_PORT}...")
            try:
                client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
            except Exception as e:
                print(f"[{time.strftime('%H:%M:%S')}] Error: {e}")
            time.sleep(2)
            clear_screen()
            continue

        info = get_system_info()
        
        lines = []
        lines.append(f"[{time.strftime('%H:%M:%S')}] System Monitor | {info['hostname']}")
        lines.append("-" * 60)
        lines.append(f"  Platform : {info['platform']} {info['arch']}")
        lines.append(f"  CPU      : {info['cpu_percent']:>5.1f}% | Cores: {info['cpu_count']}")
        lines.append(f"  Memory   : {info['memory_percent']:>5.1f}% | {info['memory_used_gb']:.1f}GB / {info['memory_total_gb']:.1f}GB")
        lines.append(f"  Disk     : {info['disk_percent']:>5.1f}%")
        lines.append(f"  Network  : TX {info['network_sent_mb']:.1f}MB | RX {info['network_recv_mb']:.1f}MB")
        lines.append("-" * 60)
        
        payload = {
            "hostname": info["hostname"],
            "timestamp": int(time.time() * 1000),
            "cpu_percent": info["cpu_percent"],
            "cpu_count": info["cpu_count"],
            "memory_percent": info["memory_percent"],
            "memory_used_gb": info["memory_used_gb"],
            "memory_total_gb": info["memory_total_gb"],
            "disk_percent": info["disk_percent"],
            "network_sent_mb": info["network_sent_mb"],
            "network_recv_mb": info["network_recv_mb"],
            "status": "online"
        }
        
        topic = f"system/{info['hostname']}/metrics"
        client.publish(topic, json.dumps(payload), qos=0)
        lines.append(f"  Published: {topic}")
        
        clear_screen()
        for line in lines:
            print(line)
        
        time.sleep(2)

def main():
    print("[System Monitor] Starting...")
    print(f"Broker: {MQTT_BROKER}:{MQTT_PORT} | User: {MQTT_USERNAME}")
    print(f"Platform: {platform.system()} {platform.machine()}")
    print(f"Hostname: {platform.node()}")
    print(f"Psutil: {'Installed' if HAS_PSUTIL else 'Not installed'}")
    time.sleep(1)
    
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=MQTT_CLIENT_ID)
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect

    try:
        client.loop_start()
        publish_data(client)

    except KeyboardInterrupt:
        print("\n\n[STOPPED]")
    finally:
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    main()