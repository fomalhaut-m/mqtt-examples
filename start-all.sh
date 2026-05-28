#!/bin/bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "=========================================="
echo "  MQTT 多设备模拟系统 - 一键启动"
echo "  Root: $ROOT_DIR"
echo "=========================================="

DOCKER_COMPOSE="$ROOT_DIR/mqtt-example/docker-compose.yml"
DOCKER_PROJECT="mqtt-example"

check_tool() {
    if ! command -v "$1" &>/dev/null; then
        echo "[ERROR] $1 未安装"
        return 1
    fi
}

wait_port() {
    local host=$1 port=$2 label=$3 max=${4:-30}
    echo -n "  -> 等待 $label ($host:$port) ..."
    for ((i=1; i<=max; i++)); do
        if timeout 2 bash -c "echo > /dev/tcp/$host/$port" 2>/dev/null; then
            echo " OK"
            return 0
        fi
        sleep 1
    done
    echo " TIMEOUT"
    return 1
}

# ====================
# 检查环境
# ====================
echo ""
echo "[1/4] 环境检查"
echo "-----"
check_tool docker    || exit 1
check_tool java      || exit 1
check_tool mvn       || exit 1
check_tool node      || exit 1
check_tool npm       || exit 1
check_tool python3   || exit 1
echo "OK"

# ====================
# Docker 容器
# ====================
echo ""
echo "[2/4] 启动 Docker 容器"
echo "-----"
cd "$ROOT_DIR/mqtt-example"

if ! docker compose -f "$DOCKER_COMPOSE" -p "$DOCKER_PROJECT" ps --format json 2>/dev/null | grep -q "Running"; then
    echo "启动容器..."
    mkdir -p data/mosquitto/data data/mosquitto/log data/clickhouse/data data/clickhouse/logs
    docker compose -f "$DOCKER_COMPOSE" -p "$DOCKER_PROJECT" up -d
    echo "等待容器就绪..."
    sleep 5
else
    echo "容器已在运行"
fi

wait_port localhost 11883 "MQTT Broker"   20 || echo "[WARN] MQTT 可能未就绪"
wait_port localhost 18123 "ClickHouse"    20 || echo "[WARN] ClickHouse 可能未就绪"

# ====================
# Java 后端
# ====================
echo ""
echo "[3/4] 启动 SpringBoot 后端"
echo "-----"
cd "$ROOT_DIR/server"

if netstat -ano 2>/dev/null | grep -q ":8081.*LISTENING"; then
    echo "后端已在运行 (:8081)"
else
    echo "编译并启动 SpringBoot..."
    mvn compile -q 2>&1 | tail -1
    mvn spring-boot:run &
    SPRING_PID=$!
    wait_port localhost 8081 "SpringBoot" 40 || {
        echo "[ERROR] 后端启动失败"; exit 1
    }
fi

# ====================
# 前端 + Python 脚本
# ====================
echo ""
echo "[4/4] 启动前端 & Python 脚本"
echo "-----"

cd "$ROOT_DIR/web"
echo "启动 Vue 前端..."
npm run dev &
VITE_PID=$!

cd "$ROOT_DIR/test-client"

echo "启动 mqtt_publisher.py ..."
python3 mqtt_publisher.py &
PUB_PID=$!

echo "启动 system_monitor.py ..."
python3 system_monitor.py &
SYS_PID=$!

echo "启动 lan_scanner.py ..."
python3 lan_scanner.py &
LAN_PID=$!

sleep 3

# ====================
# 结果汇总
# ====================
VITE_PORT=$(grep -oP 'localhost:\K\d+' /dev/null 2>/dev/null || for p in 5173 5174 5175 5176; do timeout 1 bash -c "echo > /dev/tcp/localhost/$p" 2>/dev/null && echo "$p" && break; done)

echo ""
echo "=========================================="
echo "  启动完成"
echo "=========================================="
echo "  MQTT Broker:    localhost:11883"
echo "  ClickHouse:     localhost:18123"
echo "  SpringBoot:     http://localhost:8081/"
echo "  Vue 前端:       http://localhost:5175/"
echo "  H2 Console:     http://localhost:8081/h2-console"
echo "=========================================="
echo "  PIDs:"
echo "    SpringBoot:    ${SPRING_PID:-已有进程}"
echo "    Vite:          ${VITE_PID:-已有进程}"
echo "    Publisher:     ${PUB_PID:-已有进程}"
echo "    SystemMonitor: ${SYS_PID:-已有进程}"
echo "    LanScanner:    ${LAN_PID:-已有进程}"
echo "=========================================="
echo ""
echo "按 Ctrl+C 停止所有服务"

trap 'echo "正在停止..."; kill ${SPRING_PID:-} ${VITE_PID:-} ${PUB_PID:-} ${SYS_PID:-} ${LAN_PID:-} 2>/dev/null; exit 0' SIGINT SIGTERM

wait
