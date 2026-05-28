import json
import time
import sys
import platform
import os
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

try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False

try:
    from pythonping import ping
    HAS_PING = True
except ImportError:
    HAS_PING = False

HOSTNAME = platform.node()
PLATFORM_OS = platform.system()
ARCH = platform.machine()

prev_net = None
prev_disk = None
prev_time = time.time()

def get_cpu_info():
    cpu = {}
    try:
        cpu["percent"] = psutil.cpu_percent(interval=0.1)
        cpu["count_logical"] = psutil.cpu_count(logical=True)
        cpu["count_physical"] = psutil.cpu_count(logical=False)
        per_core = psutil.cpu_percent(interval=0, percpu=True)
        cpu["per_core"] = per_core if per_core else []
        cpu["freq_current"] = getattr(psutil.cpu_freq(), "current", 0) if psutil.cpu_freq() else 0
        load = os.getloadavg() if hasattr(os, "getloadavg") and PLATFORM_OS != "Windows" else (0, 0, 0)
        cpu["load_1m"], cpu["load_5m"], cpu["load_15m"] = load[0], load[1], load[2]
    except:
        cpu = {"percent": 0, "count_logical": 0, "count_physical": 0, "per_core": [], "freq_current": 0, "load_1m": 0, "load_5m": 0, "load_15m": 0}
    return cpu

def get_memory_info():
    mem = {}
    try:
        vmem = psutil.virtual_memory()
        mem["percent"] = vmem.percent
        mem["total_gb"] = round(vmem.total / (1024**3), 2)
        mem["used_gb"] = round(vmem.used / (1024**3), 2)
        mem["available_gb"] = round(vmem.available / (1024**3), 2)
        mem["cached_gb"] = round(getattr(vmem, "cached", 0) / (1024**3), 2)
        swap = psutil.swap_memory()
        mem["swap_total_gb"] = round(swap.total / (1024**3), 2)
        mem["swap_used_gb"] = round(swap.used / (1024**3), 2)
        mem["swap_percent"] = swap.percent
    except:
        mem = {"percent": 0, "total_gb": 0, "used_gb": 0, "available_gb": 0, "cached_gb": 0, "swap_total_gb": 0, "swap_used_gb": 0, "swap_percent": 0}
    return mem

def get_disk_info():
    global prev_disk, prev_time
    disks = []
    try:
        for part in psutil.disk_partitions():
            try:
                usage = psutil.disk_usage(part.mountpoint)
                disks.append({
                    "device": part.device,
                    "mountpoint": part.mountpoint,
                    "fstype": part.fstype,
                    "total_gb": round(usage.total / (1024**3), 2),
                    "used_gb": round(usage.used / (1024**3), 2),
                    "free_gb": round(usage.free / (1024**3), 2),
                    "percent": usage.percent
                })
            except:
                pass
    except:
        pass

    disk_rate = {"read_mb_s": 0, "write_mb_s": 0}
    try:
        current_disk = psutil.disk_io_counters()
        current_time = time.time()
        if prev_disk and current_time > prev_time:
            elapsed = current_time - prev_time
            read_bytes = current_disk.read_bytes - prev_disk.read_bytes
            write_bytes = current_disk.write_bytes - prev_disk.write_bytes
            disk_rate["read_mb_s"] = round(read_bytes / (1024**2) / elapsed, 2)
            disk_rate["write_mb_s"] = round(write_bytes / (1024**2) / elapsed, 2)
        prev_disk = current_disk
        prev_time = current_time
    except:
        pass

    return disks, disk_rate

def get_network_info():
    global prev_net
    net = {}
    try:
        net_io = psutil.net_io_counters()
        net["sent_mb"] = round(net_io.bytes_sent / (1024**2), 2)
        net["recv_mb"] = round(net_io.bytes_recv / (1024**2), 2)
        net["packets_sent"] = net_io.packets_sent
        net["packets_recv"] = net_io.packets_recv

        current_time = time.time()
        if prev_net and current_time > prev_time:
            elapsed = current_time - prev_time
            net["upload_kb_s"] = round((net_io.bytes_sent - prev_net.bytes_sent) / 1024 / elapsed, 2)
            net["download_kb_s"] = round((net_io.bytes_recv - prev_net.bytes_recv) / 1024 / elapsed, 2)
        else:
            net["upload_kb_s"] = 0
            net["download_kb_s"] = 0
        prev_net = net_io

        conns = psutil.net_connections()
        net["tcp_count"] = sum(1 for c in conns if c.type == 1)
        net["udp_count"] = sum(1 for c in conns if c.type == 2)

        net["interface"] = {}
        for name, addrs in psutil.net_if_addrs().items():
            for addr in addrs:
                if str(addr.family).endswith("INET") and not addr.address.startswith("127."):
                    net["interface"][name] = addr.address
    except:
        pass
    return net

def get_battery_info():
    bat = {"has_battery": False, "percent": 100, "charging": False, "remaining_min": 0}
    try:
        battery = psutil.sensors_battery()
        if battery:
            bat["has_battery"] = True
            bat["percent"] = battery.percent
            bat["charging"] = battery.power_plugged
            bat["remaining_min"] = round(battery.secsleft / 60, 1) if battery.secsleft > 0 else 0
    except:
        pass
    return bat

def get_temperatures():
    temps = {}
    try:
        sensors = psutil.sensors_temperatures()
        for name, entries in sensors.items():
            temps[name] = [{"label": e.label or f"sensor_{i}", "current": e.current, "high": e.high, "critical": e.critical} for i, e in enumerate(entries)]
    except:
        pass
    return temps

def get_fan_speeds():
    fans = {}
    try:
        sensors = psutil.sensors_fans()
        for name, entries in sensors.items():
            fans[name] = [{"label": e.label or f"fan_{i}", "current": e.current} for i, e in enumerate(entries)]
    except:
        pass
    return fans

def get_uptime():
    ut = {"boot_time": 0, "uptime_hours": 0, "uptime_str": "unknown"}
    try:
        ut["boot_time"] = psutil.boot_time()
        uptime_sec = time.time() - ut["boot_time"]
        ut["uptime_hours"] = round(uptime_sec / 3600, 1)
        days = int(uptime_sec // 86400)
        hours = int((uptime_sec % 86400) // 3600)
        minutes = int((uptime_sec % 3600) // 60)
        ut["uptime_str"] = f"{days}d {hours}h {minutes}m"
    except:
        pass
    return ut

def get_gateway_ping():
    ping_result = {"gateway_ms": 0, "baidu_ms": 0}
    if not HAS_PING:
        return ping_result
    try:
        gateways = psutil.net_if_addrs()
        for name, addrs in gateways.items():
            for addr in addrs:
                if str(addr.family).endswith("INET") and addr.address and not addr.address.startswith("127."):
                    gw = ".".join(addr.address.split(".")[:3]) + ".1"
                    try:
                        resp = ping(gw, count=1, timeout=1)
                        if resp.success():
                            ping_result["gateway_ms"] = round(resp.rtt_avg_ms, 1)
                    except:
                        pass
                    break
        try:
            resp = ping("baidu.com", count=1, timeout=2)
            if resp.success():
                ping_result["baidu_ms"] = round(resp.rtt_avg_ms, 1)
        except:
            pass
    except:
        pass
    return ping_result

def get_process_top():
    procs = []
    try:
        for p in psutil.process_iter(["pid", "name", "cpu_percent", "memory_percent", "status"]):
            try:
                info = p.info
                if info["cpu_percent"] and info["cpu_percent"] > 0.5:
                    procs.append({
                        "pid": info["pid"],
                        "name": info["name"],
                        "cpu_percent": round(info["cpu_percent"], 1),
                        "memory_percent": round(info["memory_percent"] or 0, 2),
                        "status": info["status"]
                    })
            except:
                pass
        procs.sort(key=lambda x: x["cpu_percent"], reverse=True)
        procs = procs[:15]
    except:
        pass
    return procs

def get_public_ip():
    ip_info = {"public_ip": ""}
    if not HAS_REQUESTS:
        return ip_info
    try:
        resp = requests.get("https://api.ipify.org?format=json", timeout=3)
        ip_info["public_ip"] = resp.json().get("ip", "")
    except:
        pass
    return ip_info

def collect_all_metrics():
    cpu = get_cpu_info()
    mem = get_memory_info()
    disks, disk_rate = get_disk_info()
    net = get_network_info()
    bat = get_battery_info()
    temps = get_temperatures()
    fans = get_fan_speeds()
    uptime = get_uptime()
    ping = get_gateway_ping()
    procs = get_process_top()

    return {
        "hostname": HOSTNAME,
        "platform": PLATFORM_OS,
        "arch": ARCH,
        "timestamp": int(time.time() * 1000),
        "cpu": cpu,
        "memory": mem,
        "disks": disks,
        "disk_rate": disk_rate,
        "network": net,
        "battery": bat,
        "temperatures": temps,
        "fans": fans,
        "uptime": uptime,
        "ping": ping,
        "top_processes": procs
    }

last_public_ip_time = 0
cached_public_ip = ""

def get_public_ip_cached():
    global last_public_ip_time, cached_public_ip
    if time.time() - last_public_ip_time > 60:
        ip_info = get_public_ip()
        cached_public_ip = ip_info.get("public_ip", "")
        last_public_ip_time = time.time()
    return cached_public_ip

def on_connect(client, userdata, flags, rc, properties):
    global connected
    connected = (rc == 0)
    if connected:
        print(f"[CONNECTED] {MQTT_BROKER}:{MQTT_PORT}")

def on_disconnect(client, userdata, rc, properties, reason_code):
    global connected
    connected = False

def publish_data(client):
    counter = 0
    while True:
        if not connected:
            try:
                client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
            except Exception as e:
                print(f"[{time.strftime('%H:%M:%S')}] Connection failed: {e}")
                time.sleep(2)
            time.sleep(1)
            continue

        data = collect_all_metrics()
        data["public_ip"] = get_public_ip_cached()

        topic = f"system/{HOSTNAME}/metrics"
        payload = json.dumps(data, ensure_ascii=False)
        client.publish(topic, payload, qos=0)

        counter += 1
        cpu_pct = data["cpu"]["percent"]
        mem_pct = data["memory"]["percent"]
        net_dl = data["network"].get("download_kb_s", 0)
        net_ul = data["network"].get("upload_kb_s", 0)
        print(f"[{time.strftime('%H:%M:%S')}] #{counter} CPU:{cpu_pct:.1f}% MEM:{mem_pct:.1f}% NET:↓{net_dl:.1f}↑{net_ul:.1f}KB/s published", end="\r")

        time.sleep(1)

def main():
    print("=" * 50)
    print(f"System Monitor | {HOSTNAME}")
    print(f"Broker: {MQTT_BROKER}:{MQTT_PORT}")
    print(f"Platform: {PLATFORM_OS} {ARCH}")
    print(f"Psutil: {'OK' if HAS_PSUTIL else 'MISSING'}")
    print(f"Requests: {'OK' if HAS_REQUESTS else 'MISSING'}")
    print(f"Ping: {'OK' if HAS_PING else 'MISSING'}")
    print("=" * 50)
    time.sleep(1)

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=MQTT_CLIENT_ID)
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect

    try:
        client.loop_start()
        publish_data(client)
    except KeyboardInterrupt:
        print("\n[STOPPED]")
    finally:
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    main()
