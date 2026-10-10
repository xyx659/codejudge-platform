#!/usr/bin/env bash
# codejudge-platform 一键启动脚本
# 依次：自检/拉起 MongoDB、MySQL → 启动后端 Spring Boot → 启动前端 Vite → 打开浏览器
# 双击桌面图标（CodeJudge一键启动.desktop）即可调用本脚本；也可在终端里直接运行。

set -uo pipefail

# ===== 基础路径与常量（按本机环境配置） =====
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
MVN="/opt/apache-maven-3.9.9/bin/mvn"

RUN_DIR="$PROJECT_DIR/.run"
BACKEND_LOG="$RUN_DIR/backend.log"
FRONTEND_LOG="$RUN_DIR/frontend.log"
BACKEND_PID="$RUN_DIR/backend.pid"
FRONTEND_PID="$RUN_DIR/frontend.pid"

PORT_BACKEND=8080
PORT_FRONTEND=5173
APP_URL="http://localhost:$PORT_FRONTEND"

mkdir -p "$RUN_DIR"

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
warn() { echo "[$(date '+%H:%M:%S')] ⚠ $*"; }

# 桌面启动时 PATH 往往不含 nvm 的 node/npm，这里兜底定位
ensure_node() {
  if command -v npm >/dev/null 2>&1; then
    return 0
  fi
  local nvm_dir="${NVM_DIR:-$HOME/.nvm}"
  if [ -s "$nvm_dir/nvm.sh" ]; then
    # shellcheck disable=SC1091
    . "$nvm_dir/nvm.sh" >/dev/null 2>&1
    nvm use default >/dev/null 2>&1 || true
  fi
}

port_listening() {
  ss -ltn 2>/dev/null | grep -Eq ":${1}[[:space:]]"
}

wait_port() {
  local port="$1" timeout="$2" name="$3" waited=0
  while ! port_listening "$port"; do
    sleep 2
    waited=$((waited + 2))
    if [ "$waited" -ge "$timeout" ]; then
      warn "$name 启动超时（${timeout}s），请查看日志"
      return 1
    fi
  done
  log "$name 已就绪（端口 $port）"
}

# 数据库：已在运行则跳过；未运行则尝试用 systemctl 拉起（无密码的 sudo 下才生效）
ensure_db() {
  local port="$1" name="$2" service="$3"
  if port_listening "$port"; then
    log "$name 已在运行"
  else
    log "$name 未运行，尝试启动 $service ..."
    sudo -n systemctl start "$service" >/dev/null 2>&1 && sleep 2 || true
    if port_listening "$port"; then
      log "$name 已启动"
    else
      warn "$name 启动失败，请手动执行：sudo systemctl start $service"
    fi
  fi
}

start_backend() {
  if port_listening "$PORT_BACKEND"; then
    log "后端已在运行（端口 $PORT_BACKEND）"
    return
  fi
  log "启动后端 Spring Boot ..."
  cd "$PROJECT_DIR/backend"
  JAVA_HOME="$JAVA_HOME" nohup "$MVN" -o spring-boot:run >"$BACKEND_LOG" 2>&1 &
  echo "$!" >"$BACKEND_PID"
  cd "$PROJECT_DIR"
  wait_port "$PORT_BACKEND" 90 "后端"
}

start_frontend() {
  if port_listening "$PORT_FRONTEND"; then
    log "前端已在运行（端口 $PORT_FRONTEND）"
    return
  fi
  log "启动前端 Vite ..."
  cd "$PROJECT_DIR/frontend"
  nohup npm run dev >"$FRONTEND_LOG" 2>&1 &
  echo "$!" >"$FRONTEND_PID"
  cd "$PROJECT_DIR"
  wait_port "$PORT_FRONTEND" 60 "前端"
}

open_browser() {
  if command -v xdg-open >/dev/null 2>&1; then
    log "打开浏览器：$APP_URL"
    xdg-open "$APP_URL" >/dev/null 2>&1 || true
  fi
}

notify() {
  command -v zenity >/dev/null 2>&1 && zenity --info --title="CodeJudge" --text="$1" --width=460 >/dev/null 2>&1 &
}

main() {
  ensure_node
  log "===== CodeJudge 一键启动 ====="
  ensure_db 27017 "MongoDB" "mongod"
  ensure_db 3306  "MySQL"   "mysql"
  start_backend
  start_frontend
  open_browser
  log "启动完成：$APP_URL"
  log "日志：后端 $BACKEND_LOG / 前端 $FRONTEND_LOG"

  # 桌面双击时无终端，用弹窗反馈结果
  summary="CodeJudge 已启动
前端：$APP_URL"
  if ! port_listening "$PORT_BACKEND"; then
    summary="$summary

（后端 8080 未就绪，请查看 $BACKEND_LOG）"
  fi
  if ! port_listening "$PORT_FRONTEND"; then
    summary="$summary

（前端 5173 未就绪，请查看 $FRONTEND_LOG）"
  fi
  notify "$summary"
}

main "$@"
