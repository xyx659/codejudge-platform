#!/usr/bin/env bash
# 一键停止：按端口停掉后端与前端（数据库由系统服务管理，这里不动）

set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$PROJECT_DIR/.run"

stop_port() {
  local port="$1" name="$2"
  local pids
  pids=$(ss -ltnp 2>/dev/null | grep -E ":${port}[[:space:]]" | grep -oE 'pid=[0-9]+' | grep -oE '[0-9]+' | sort -u)
  if [ -n "$pids" ]; then
    echo "停止 $name（端口 $port）：$pids"
    echo "$pids" | xargs -r kill 2>/dev/null || true
    sleep 2
    if ss -ltn 2>/dev/null | grep -Eq ":${port}[[:space:]]"; then
      echo "$pids" | xargs -r kill -9 2>/dev/null || true
    fi
  else
    echo "$name（端口 $port）未在运行"
  fi
}

stop_port 8080 "后端"
stop_port 5173 "前端"

rm -f "$RUN_DIR/backend.pid" "$RUN_DIR/frontend.pid"
echo "已停止"
