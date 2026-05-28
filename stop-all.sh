#!/bin/bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "=========================================="
echo "  停止所有服务"
echo "=========================================="

# Python 脚本
echo ""
echo "停止 Python 脚本..."
pkill -f "mqtt_publisher.py"    2>/dev/null || echo "  mqtt_publisher: 未运行"
pkill -f "system_monitor.py"   2>/dev/null || echo "  system_monitor: 未运行"
pkill -f "lan_scanner.py"      2>/dev/null || echo "  lan_scanner: 未运行"

# Vue 前端
echo ""
echo "停止 Vue 前端..."
pkill -f "vite" 2>/dev/null || echo "  vite: 未运行"

# Java 后端
echo ""
echo "停止 SpringBoot..."
if netstat -ano 2>/dev/null | grep -q ":8081.*LISTENING"; then
    for pid in $(netstat -ano 2>/dev/null | grep ":8081.*LISTENING" | awk '{print $NF}' | sort -u); do
        taskkill //F //PID "$pid" 2>/dev/null && echo "  已终止 PID=$pid" || echo "  跳过 PID=$pid"
    done
else
    echo "  SpringBoot: 未运行"
fi

# Docker 容器
echo ""
echo "停止 Docker 容器..."
cd "$ROOT_DIR/mqtt-example"
docker compose -f docker-compose.yml -p mqtt-example down 2>/dev/null || echo "  容器: 未运行"

echo ""
echo "=========================================="
echo "  所有服务已停止"
echo "=========================================="
