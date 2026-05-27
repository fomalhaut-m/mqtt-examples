#!/bin/bash

set -e

DOCKER_COMPOSE_FILE="docker-compose.yml"
PROJECT_NAME="mqtt-example"

MQTT_PORT=11883
CLICKHOUSE_HTTP_PORT=18123
CLICKHOUSE_TCP_PORT=19000

print_usage() {
    echo "Usage: $0 [start|stop|restart|logs|status|init]"
    echo ""
    echo "Commands:"
    echo "  start    - 启动所有服务"
    echo "  stop     - 停止所有服务"
    echo "  restart  - 重启所有服务"
    echo "  logs     - 查看服务日志"
    echo "  status   - 查看服务状态"
    echo "  init     - 初始化环境（创建目录）"
}

check_docker() {
    if ! command -v docker &> /dev/null; then
        echo "Error: Docker is not installed. Please install Docker first."
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        echo "Error: Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
}

init_env() {
    echo "Initializing environment..."

    mkdir -p data/mosquitto/data
    mkdir -p data/mosquitto/log
    mkdir -p data/clickhouse/data
    mkdir -p data/clickhouse/logs

    echo "Environment initialized successfully."
}

wait_for_port() {
    local host=$1
    local port=$2
    local service=$3
    local max_attempts=30
    local attempt=1

    echo -n "Waiting for $service ($host:$port)..."

    while [ $attempt -le $max_attempts ]; do
        if command -v nc &> /dev/null; then
            if nc -z -w 2 "$host" "$port" 2>/dev/null; then
                echo " OK"
                return 0
            fi
        elif command -v timeout &> /dev/null; then
            if timeout 2 bash -c "echo > /dev/tcp/$host/$port" 2>/dev/null; then
                echo " OK"
                return 0
            fi
        else
            if (echo > /dev/tcp/$host/$port) 2>/dev/null; then
                echo " OK"
                return 0
            fi
        fi

        echo -n "."
        sleep 1
        attempt=$((attempt + 1))
    done

    echo " FAILED"
    return 1
}

test_connectivity() {
    echo "Testing connectivity..."

    wait_for_port localhost $MQTT_PORT "MQTT Broker"
    wait_for_port localhost $CLICKHOUSE_HTTP_PORT "ClickHouse HTTP"
    wait_for_port localhost $CLICKHOUSE_TCP_PORT "ClickHouse TCP"

    echo ""
    echo "All services are ready!"
}

start_services() {
    echo "Starting services..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" -p "$PROJECT_NAME" up -d

    echo ""
    echo "Waiting for services to start..."
    sleep 5

    test_connectivity
}

stop_services() {
    echo "Stopping services..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" -p "$PROJECT_NAME" down
    echo "Services stopped successfully."
}

restart_services() {
    stop_services
    start_services
}

view_logs() {
    echo "Viewing logs..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" -p "$PROJECT_NAME" logs -f
}

check_status() {
    echo "Checking service status..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" -p "$PROJECT_NAME" ps
}

if [ $# -eq 0 ]; then
    print_usage
    exit 1
fi

case "$1" in
    start)
        check_docker
        start_services
        ;;
    stop)
        check_docker
        stop_services
        ;;
    restart)
        check_docker
        restart_services
        ;;
    logs)
        check_docker
        view_logs
        ;;
    status)
        check_docker
        check_status
        ;;
    init)
        init_env
        ;;
    *)
        print_usage
        exit 1
        ;;
esac