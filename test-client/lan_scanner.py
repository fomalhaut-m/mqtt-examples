import json
import time
import socket
import struct
import subprocess
import platform
import re
import paho.mqtt.client as mqtt

MQTT_BROKER = "localhost"
MQTT_PORT = 11883
MQTT_CLIENT_ID = "lan-scanner"
MQTT_USERNAME = "mqtt_user"
MQTT_PASSWORD = "mqtt_password"

connected = False
PLATFORM_OS = platform.system()
HOSTNAME = platform.node()


def get_all_interfaces():
    """获取所有网卡及其网络信息"""
    interfaces = []

    try:
        import psutil
        stats = psutil.net_if_stats()

        for name, addrs in psutil.net_if_addrs().items():
            name_lower = name.lower()
            if_stats = stats.get(name)

            mac = ""
            ipv4 = ""
            netmask = ""
            ipv6_list = []

            for addr in addrs:
                if addr.family == socket.AF_INET:
                    ipv4 = addr.address
                    netmask = addr.netmask or ""
                elif addr.family == getattr(psutil, 'AF_LINK', -1) or addr.family == -1:
                    if addr.address and (addr.address.count("-") == 5 or addr.address.count(":") == 5):
                        mac = addr.address
                elif addr.family == socket.AF_INET6:
                    ipv6_list.append(addr.address)

            VIRTUAL_KEYWORDS = [
                "virtual", "vmware", "virtualbox", "hyper-v", "vether", "wsl",
                "docker", "vpn", "tunnel", "pseudo", "miniport", "teredo",
                "isatap", "6to4", "loopback", "bluetooth"
            ]
            is_virtual = any(kw in name_lower for kw in VIRTUAL_KEYWORDS)
            is_up = if_stats.isup if if_stats else False
            speed = if_stats.speed if if_stats else 0

            if ipv4:
                cidr = ip_to_range(ipv4, netmask) if netmask else ""
                prefix = "/" + cidr.split("/")[-1] if cidr else ""

                interfaces.append({
                    "name": name,
                    "mac": mac,
                    "ip": ipv4,
                    "netmask": netmask,
                    "cidr": cidr,
                    "prefix": prefix,
                    "ipv6": ipv6_list,
                    "is_up": is_up,
                    "is_virtual": is_virtual,
                    "speed_mbps": int(speed) if speed > 0 else 0,
                })

    except ImportError:
        pass

    if not interfaces and PLATFORM_OS == "Windows":
        interfaces = _get_all_interfaces_windows()

    return interfaces


def _get_all_interfaces_windows():
    interfaces = []
    try:
        result = subprocess.check_output("ipconfig /all", encoding="utf-8", errors="ignore", shell=True)
        blocks = re.split(r'\r?\n\r?\n', result)

        for block in blocks:
            if not block.strip():
                continue
            name_match = re.search(r'(?:Ethernet adapter|Wireless LAN adapter|无线局域网适配器|以太网适配器)\s+(.+?):', block)
            if not name_match:
                name_match = re.search(r'适配器\s+(.+?):', block)
            name = name_match.group(1).strip() if name_match else "Unknown"

            ip = ""
            mask = ""
            mac = ""
            for line in block.split("\n"):
                if "IPv4" in line or "IPv4" in line:
                    m = re.search(r'(\d+\.\d+\.\d+\.\d+)', line)
                    if m: ip = m.group(1)
                if "子网掩码" in line or "Subnet Mask" in line:
                    m = re.search(r'(\d+\.\d+\.\d+\.\d+)', line)
                    if m: mask = m.group(1)
                if "物理地址" in line or "Physical Address" in line:
                    m = re.search(r'([\dA-Fa-f]{2}[-:][\dA-Fa-f]{2}[-:][\dA-Fa-f]{2}[-:][\dA-Fa-f]{2}[-:][\dA-Fa-f]{2}[-:][\dA-Fa-f]{2})', line)
                    if m: mac = m.group(1).replace("-", ":")

            if ip:
                cidr = ip_to_range(ip, mask) if mask else ""
                interfaces.append({
                    "name": name, "mac": mac, "ip": ip, "netmask": mask or "",
                    "cidr": cidr, "prefix": "/" + cidr.split("/")[-1] if cidr else "",
                    "ipv6": [], "is_up": True, "is_virtual": False, "speed_mbps": 0,
                })
    except:
        pass
    return interfaces


def get_best_interface(interfaces):
    """从网卡列表中选出最佳扫描网卡"""
    if not interfaces:
        return None

    REAL_KEYWORDS = ["wlan", "wi-fi", "wireless", "以太网", "无线", "局域网", "本地连接"]

    candidates = []
    for iface in interfaces:
        if not iface["is_up"]:
            continue
        if iface.get("is_virtual"):
            continue
        if iface["ip"].startswith("127.") or iface["ip"].startswith("169.254."):
            continue
        if not iface["cidr"]:
            continue

        name_lower = iface["name"].lower()
        is_real = any(kw in name_lower for kw in REAL_KEYWORDS)
        candidates.append({**iface, "real": is_real})

    if not candidates:
        for iface in interfaces:
            if iface["is_up"] and iface["cidr"] and not iface["ip"].startswith("127."):
                candidates.append({**iface, "real": False})

    if not candidates:
        return None

    candidates.sort(key=lambda c: (not c["real"], -c["speed_mbps"]))
    return candidates[0]


def ip_to_range(ip, netmask):
    try:
        ip_int = struct.unpack("!I", socket.inet_aton(ip))[0]
        mask_int = struct.unpack("!I", socket.inet_aton(netmask))[0]
        network = ip_int & mask_int
        broadcast = network | (~mask_int & 0xFFFFFFFF)
        mask_bits = bin(mask_int).count("1")
        network_ip = socket.inet_ntoa(struct.pack("!I", network))
        return f"{network_ip}/{mask_bits}"
    except:
        return None


def cidr_to_range(cidr):
    """将 CIDR 转换为 (起始IP, 结束IP, 总数)"""
    try:
        ip_str, bits = cidr.split("/")
        bits = int(bits)
        ip_int = struct.unpack("!I", socket.inet_aton(ip_str))[0]
        mask = (0xFFFFFFFF << (32 - bits)) & 0xFFFFFFFF
        network = ip_int & mask
        broadcast = network | (~mask & 0xFFFFFFFF)
        return socket.inet_ntoa(struct.pack("!I", network + 1)), socket.inet_ntoa(struct.pack("!I", broadcast - 1)), broadcast - network - 1
    except:
        return None, None, 0


def mac_vendor_lookup(mac):
    """根据 MAC 前缀查询厂商"""
    OUI_DB = {
        "00:50:56": "VMware",
        "00:0C:29": "VMware",
        "00:05:69": "VMware",
        "08:00:27": "VirtualBox",
        "00:1C:42": "Parallels",
        "00:15:5D": "Hyper-V",
        "00:1A:A0": "Asustek",
        "00:1B:FC": "Asustek",
        "AC:22:0B": "Asustek",
        "F0:DE:F1": "ASUSTek",
        "3C:7C:3F": "ASUSTek",
        "B8:27:EB": "Raspberry Pi",
        "DC:A6:32": "Raspberry Pi",
        "E4:5F:01": "Raspberry Pi",
        "D8:3A:DD": "Raspberry Pi",
        "DC:A6:32": "Raspberry Pi",
        "B8:27:EB": "Raspberry Pi",
        "00:14:22": "Dell",
        "00:21:70": "Dell",
        "B8:AC:6F": "Dell",
        "F0:4D:A2": "Dell",
        "00:1E:C9": "Dell",
        "F0:1F:AF": "Dell",
        "28:6E:D4": "Intel",
        "00:1B:77": "Intel",
        "00:0C:F1": "Intel",
        "00:1E:64": "Intel",
        "3C:A0:67": "Intel",
        "58:FB:96": "Intel",
        "80:86:F2": "Intel",
        "C4:85:08": "Intel",
        "00:26:BB": "Apple",
        "00:23:DF": "Apple",
        "14:10:9F": "Apple",
        "4C:BC:98": "Apple",
        "A8:86:DD": "Apple",
        "B0:34:95": "Apple",
        "D4:61:DA": "Apple",
        "F0:D1:A9": "Apple",
        "D8:96:95": "Apple",
        "AC:BC:32": "Apple",
        "A4:B1:97": "Apple",
        "A4:D1:8C": "Apple",
        "00:17:F2": "Apple",
        "64:B9:E8": "Apple",
        "E0:AC:CB": "Apple",
        "34:36:3B": "Apple",
        "F4:0F:24": "Apple",
        "F8:1E:DF": "Apple",
        "00:25:00": "Apple",
        "E0:F8:47": "Apple",
        "60:F8:1D": "Apple",
        "68:5B:35": "Apple",
        "98:FE:94": "Apple",
        "54:72:4F": "Apple",
        "A4:2B:B0": "TP-Link",
        "50:C7:BF": "TP-Link",
        "90:F6:52": "TP-Link",
        "E8:DE:27": "TP-Link",
        "E8:94:F6": "TP-Link",
        "14:CC:20": "TP-Link",
        "10:FE:ED": "TP-Link",
        "6C:E8:73": "TP-Link",
        "00:0F:B0": "Cisco",
        "00:1A:E2": "Cisco",
        "00:1B:53": "Cisco",
        "00:23:AC": "Cisco",
        "00:25:B5": "Cisco",
        "1C:87:2C": "Samsung",
        "00:1E:DF": "Samsung",
        "8C:77:16": "Samsung",
        "A0:D3:C1": "Samsung",
        "B4:CF:DB": "Samsung",
        "CC:3A:61": "Samsung",
        "CC:05:77": "Huawei",
        "00:18:82": "Huawei",
        "4C:1F:CC": "Huawei",
        "48:DB:50": "Huawei",
        "00:E0:4C": "Realtek",
        "00:E0:4C": "Realtek",
        "48:EE:0C": "Xiaomi",
        "64:64:4B": "Xiaomi",
        "34:CE:00": "Xiaomi",
        "80:EA:07": "Xiaomi",
        "F0:B4:29": "Xiaomi",
        "8C:53:C3": "Xiaomi",
        "FC:64:BA": "Xiaomi",
        "04:4B:FF": "Guangdong",
        "34:42:62": "Nintendo",
        "B8:AE:6E": "Nintendo",
        "7C:BB:8A": "Nintendo",
        "00:1E:A7": "Nintendo",
        "00:14:A4": "Hon Hai/Foxconn",
        "00:1F:3B": "Intelbras",
        "00:04:4B": "Nokia",
        "00:80:77": "Brother",
        "00:1B:A9": "Brother",
        "00:25:4B": "Nokia",
        "44:4E:6D": "Ruckus",
        "F8:1A:67": "TP-Link",
        "00:0C:43": "Ralink",
        "00:90:4C": "Epigram",
        "00:9E:C8": "Xiaomi",
        "00:1D:0F": "TP-Link",
        "00:23:CD": "TP-Link",
        "00:25:86": "TP-Link",
        "F4:F2:6D": "TP-Link",
        "C0:25:A2": "Netgear",
        "00:14:6C": "Netgear",
        "E0:46:9A": "Netgear",
        "20:E5:2A": "Netgear",
        "84:1B:5E": "Netgear",
        "30:B5:C2": "Netgear",
        "C4:3D:C7": "Netgear",
        "CC:40:D0": "Netgear",
        "A0:21:B7": "Netgear",
        "2C:30:33": "Netgear",
        "B0:39:56": "Netgear",
        "6C:B0:CE": "Netgear",
        "10:0D:7F": "Netgear",
        "00:24:B2": "Netgear",
        "00:1E:2A": "Netgear",
        "00:14:BF": "Netgear",
        "30:46:9A": "Netgear",
        "D0:67:E5": "D-Link",
        "00:1B:11": "D-Link",
        "00:1C:F0": "D-Link",
        "F0:7D:68": "D-Link",
        "C0:A0:BB": "D-Link",
        "1C:BD:B9": "D-Link",
        "BC:F6:85": "D-Link",
        "9C:D2:1E": "D-Link",
        "78:54:2E": "D-Link",
        "C8:D3:A3": "D-Link",
        "B0:C5:54": "D-Link",
        "00:1E:58": "D-Link",
        "00:21:91": "D-Link",
        "00:22:B0": "D-Link",
        "00:26:5A": "D-Link",
        "00:1C:F0": "D-Link",
        "00:24:01": "D-Link",
        "1C:AF:F7": "D-Link",
        "90:94:E4": "D-Link",
        "C8:BE:19": "D-Link",
        "E4:6F:13": "D-Link",
        "00:50:BA": "D-Link",
        "00:05:5D": "D-Link",
        "00:80:C8": "D-Link",
        "28:10:7B": "D-Link",
        "C8:D3:A3": "D-Link",
        "40:01:C6": "Xiaomi",
        "28:6C:07": "Xiaomi",
        "4C:49:6C": "Xiaomi",
        "9C:99:A0": "Xiaomi",
        "98:9E:63": "Xiaomi",
        "A4:9B:4F": "Xiaomi",
    }
    if not mac:
        return "Unknown"
    upper = mac.upper()
    for prefix, vendor in OUI_DB.items():
        if upper.startswith(prefix):
            return vendor
    return "Unknown"


def arp_scan(cidr):
    """使用 ARP 扫描局域网"""
    devices = {}
    try:
        from scapy.all import Ether, ARP, srp, conf
        conf.verb = 0
        ans, _ = srp(Ether(dst="ff:ff:ff:ff:ff:ff") / ARP(pdst=cidr), timeout=3, iface=None, inter=0.02)
        for _, rcv in ans:
            mac = rcv.hwsrc
            ip = rcv.psrc
            vendor = mac_vendor_lookup(mac)
            try:
                hostname = socket.gethostbyaddr(ip)[0]
            except:
                hostname = ""
            devices[ip] = {"mac": mac, "vendor": vendor, "hostname": hostname, "ip": ip}
    except ImportError:
        pass
    except Exception as e:
        print(f"[ARP Scan] Error: {e}")
    return devices


def ping_sweep(start_ip, end_ip, count):
    """使用 ping 扫描"""
    devices = {}
    start_int = struct.unpack("!I", socket.inet_aton(start_ip))[0]
    end_int = struct.unpack("!I", socket.inet_aton(end_ip))[0]
    actual_end = min(end_int, start_int + count)

    if PLATFORM_OS == "Windows":
        ping_cmd = lambda ip: ["ping", "-n", "1", "-w", "300", ip]
        arp_cmd = "arp -a"
    else:
        ping_cmd = lambda ip: ["ping", "-c", "1", "-W", "1", ip]
        arp_cmd = "arp -n"

    import concurrent.futures

    def ping_host(ip):
        try:
            subprocess.check_output(ping_cmd(ip), stderr=subprocess.DEVNULL, timeout=2)
            return ip
        except:
            return None

    with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
        ips_to_scan = [socket.inet_ntoa(struct.pack("!I", i)) for i in range(start_int, actual_end + 1)]
        results = list(executor.map(ping_host, ips_to_scan))

    alive_ips = [ip for ip in results if ip]
    if not alive_ips:
        return devices

    try:
        arp_table = subprocess.check_output(arp_cmd, encoding="utf-8", errors="ignore", shell=True)
    except:
        arp_table = ""

    for ip in alive_ips:
        mac = ""
        hostname = ""
        for line in arp_table.split("\n"):
            if ip in line:
                parts = line.split()
                for p in parts:
                    if p.count("-") == 5 or p.count(":") == 5:
                        mac = p.replace("-", ":").upper()
                        break
                break
        try:
            hostname = socket.gethostbyaddr(ip)[0]
        except:
            pass
        vendor = mac_vendor_lookup(mac) if mac else "Unknown"
        devices[ip] = {"mac": mac, "vendor": vendor, "hostname": hostname, "ip": ip}

    return devices


def do_port_scan(ip, ports):
    """对单个 IP 做端口扫描"""
    open_ports = []
    for port in ports:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(0.3)
            result = sock.connect_ex((ip, port))
            if result == 0:
                service = get_service_name(port)
                open_ports.append({"port": port, "service": service})
            sock.close()
        except:
            pass
    return open_ports


def get_service_name(port):
    """根据端口号返回常见服务名"""
    SERVICE_MAP = {
        21: "FTP", 22: "SSH", 23: "Telnet", 25: "SMTP", 53: "DNS",
        80: "HTTP", 110: "POP3", 135: "RPC", 139: "NetBIOS",
        143: "IMAP", 443: "HTTPS", 445: "SMB", 993: "IMAPS",
        995: "POP3S", 1080: "SOCKS", 1433: "MSSQL", 1521: "Oracle",
        1723: "PPTP", 1883: "MQTT", 3128: "Squid", 3306: "MySQL",
        3389: "RDP", 4443: "HTTPS-Alt", 5432: "PostgreSQL",
        5900: "VNC", 6379: "Redis", 8000: "HTTP-Alt", 8006: "PVE",
        8080: "HTTP-Proxy", 8081: "HTTP-Alt", 8443: "HTTPS-Alt",
        8883: "MQTT-SSL", 9000: "PHP-FPM", 9090: "Prometheus",
        9200: "Elasticsearch", 27017: "MongoDB", 5000: "UPNP",
        5040: "Unknown", 5353: "mDNS", 5355: "LLMNR", 7000: "AFP",
        8008: "HTTP-Alt", 8009: "AJP", 8123: "ClickHouse", 9001: "MQTT-WS",
    }
    return SERVICE_MAP.get(port, f"port-{port}")


def collect_lan_data(best_iface, interfaces, port_scan_enabled=False):
    if not best_iface:
        return None

    cidr = best_iface["cidr"]
    start_ip, end_ip, total = cidr_to_range(cidr)
    if not start_ip:
        return None

    start_time = time.time()

    devices = arp_scan(cidr)
    method = "ARP"

    if not devices:
        devices = ping_sweep(start_ip, end_ip, 255)
        method = "Ping"

    device_list = []
    for ip, info in devices.items():
        entry = {
            "ip": ip,
            "mac": info["mac"],
            "vendor": info["vendor"],
            "hostname": info.get("hostname", ""),
        }
        if port_scan_enabled:
            COMMON_PORTS = [21, 22, 23, 25, 53, 80, 135, 139, 443, 445, 1883, 3306, 3389, 5432, 5900, 6379, 8000, 8080, 8081, 8443, 8883, 9090, 9200, 27017]
            entry["open_ports"] = do_port_scan(ip, COMMON_PORTS)
        device_list.append(entry)

    device_list.sort(key=lambda d: sum(int(x) << (24 - 8 * i) for i, x in enumerate(d["ip"].split("."))))

    unknown_count = sum(1 for d in device_list if d["vendor"] == "Unknown")

    elapsed = round(time.time() - start_time, 2)

    return {
        "hostname": HOSTNAME,
        "timestamp": int(time.time() * 1000),
        "network": {
            "interface": best_iface["name"],
            "local_ip": best_iface["ip"],
            "netmask": best_iface["netmask"],
            "cidr": cidr,
            "scan_method": method,
            "total_hosts": total,
            "alive_hosts": len(device_list),
            "identified": len(device_list) - unknown_count,
            "unknown": unknown_count,
            "scan_duration_s": elapsed,
        },
        "interfaces": interfaces,
        "devices": device_list
    }


def on_connect(client, userdata, flags, rc, properties):
    global connected
    connected = (rc == 0)
    if connected:
        print(f"[CONNECTED] {MQTT_BROKER}:{MQTT_PORT}")


def on_disconnect(client, userdata, rc, properties, reason_code):
    global connected
    connected = False


def main():
    print("=" * 50)
    print(f"LAN Scanner | {HOSTNAME}")
    print(f"Broker: {MQTT_BROKER}:{MQTT_PORT}")
    print("=" * 50)

    interfaces = get_all_interfaces()
    if not interfaces:
        print("[ERROR] 无法获取本机网络信息")
        return

    print(f"\n发现 {len(interfaces)} 个网卡:")
    for iface in interfaces:
        tag = ""
        if not iface["is_up"]: tag = " [已断开]"
        elif iface["is_virtual"]: tag = " [虚拟]"
        elif iface["ip"].startswith("169.254."): tag = " [未连接]"
        elif iface["ip"].startswith("127."): tag = " [回环]"
        print(f"  {iface['name']:40s} {iface['ip']:16s}{iface['prefix']:>4s}  MAC:{iface['mac']}{tag}")

    best_iface = get_best_interface(interfaces)
    if not best_iface:
        print("[ERROR] 没有可用的网卡进行扫描")
        return

    print()
    print(f"扫描目标: {best_iface['name']} ({best_iface['ip']}, CIDR: {best_iface['cidr']})")
    print("=" * 50)

    enabled_port_scan = "--portscan" in __import__("sys").argv
    if enabled_port_scan:
        print("[*] 端口扫描已启用 (较慢)")
    else:
        print("[*] 仅 MAC 扫描 (加 --portscan 启用端口扫描)")
    print()

    time.sleep(1)

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=MQTT_CLIENT_ID)
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect

    try:
        client.loop_start()

        while not connected:
            try:
                client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
            except Exception as e:
                print(f"[{time.strftime('%H:%M:%S')}] Connection failed: {e}")
                time.sleep(2)
            time.sleep(1)

        while True:
            print(f"[{time.strftime('%H:%M:%S')}] Scanning LAN - CIDR: {best_iface['cidr']} ({best_iface['name']}) ...")
            data = collect_lan_data(best_iface, interfaces, port_scan_enabled=enabled_port_scan)

            if data:
                topic = "lan/scan/results"
                payload = json.dumps(data, ensure_ascii=False)
                client.publish(topic, payload, qos=0)

                net = data["network"]
                devices = data["devices"]
                print(f"[{time.strftime('%H:%M:%S')}] Results: {net['alive_hosts']} alive / {net['total_hosts']} total ({net['identified']} identified, {net['unknown']} unknown, {net['scan_duration_s']}s)")
                for d in devices[:10]:
                    extra = f" [{d['vendor']}]" if d['vendor'] != 'Unknown' else ""
                    print(f"  {d['ip']:15s} {d['mac']:17s}{extra}")
                if len(devices) > 10:
                    print(f"  ... and {len(devices) - 10} more")

            print()
            time.sleep(30)

    except KeyboardInterrupt:
        print("\n[STOPPED]")
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
