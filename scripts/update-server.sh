#!/usr/bin/env bash
# ============================================================
# learn-notes 阿里云服务器更新部署脚本（Workbench CLI 通道）
# 用途：把本地已推送的 master 更新到云服务器并重建 docker 服务
# 环境：Windows 需 Git Bash；依赖 workbench CLI + python3（解析 JSON 输出）
#
# 用法：
#   ./scripts/update-server.sh               # 完整更新（pull + 重建 + 验证）
#   ./scripts/update-server.sh --check       # 只查服务器当前状态，不更新
#   ./scripts/update-server.sh --timeout 600 # 自定义构建等待上限（秒）
#
# 必须通过环境变量提供实例 ID（不再入库）：
#   INSTANCE_ID=i-xxx ./scripts/update-server.sh
# 可覆盖的环境变量：INSTANCE_ID（必填）/ REGION / REMOTE_DIR / WEB_PORT / DEPLOY_LOG
#
# 注意：exec 必须带 --output json —— v1.0.0 在 Windows 上 text 模式
#       走命名管道 exec-stream 会报 missing port in address（见记忆/README）
# ============================================================
set -euo pipefail

# ---------- 配置 ----------
INSTANCE_ID="${INSTANCE_ID:?请通过环境变量设置 INSTANCE_ID（不再入库，见脚本头注释）}"
REGION="${REGION:-cn-hangzhou}"
REMOTE_DIR="${REMOTE_DIR:-/opt/learn-notes}"
WEB_PORT="${WEB_PORT:-8088}"
BUILD_TIMEOUT=600            # 容器重建等待上限（秒）
POLL_INTERVAL=15             # 构建轮询间隔（秒）
CHECK_ONLY=0
# 固定 /tmp 路径可被低权用户预置符号链接攻击（root 重定向截断任意文件），
# 默认放 root 家目录，也可用环境变量覆盖
DEPLOY_LOG="${DEPLOY_LOG:-/root/ln-deploy.log}"

# ---------- 工具函数 ----------
info()  { echo "[INFO] $(date '+%F %T') $*"; }
warn()  { echo "[WARN] $(date '+%F %T') $*" >&2; }
fail()  { echo "[ERROR] $(date '+%F %T') $*" >&2; exit 1; }

# 远程执行命令：走 --output json 规避 Windows exec-stream bug，
# 打印 output，远程退出码非 0 时整体失败。
# 用法：wb_exec "命令" [超时秒] [allow_fail]
wb_exec() {
  local cmd="$1" timeout="${2:-60}" allow_fail="${3:-0}"
  local json rc
  json="$(workbench exec --instance-id "$INSTANCE_ID" --command "$cmd" \
          --output json --timeout "$timeout" 2>&1)" || rc=$?
  if [ -z "${json:-}" ] && [ "${rc:-0}" -ne 0 ]; then
    [ "$allow_fail" = "1" ] && return 1
    fail "workbench exec 调用失败（rc=${rc:-?}）"
  fi
  python3 - "$json" "$allow_fail" <<'PY' || true
import json, sys
raw, allow = sys.argv[1], sys.argv[2] == "1"
try:
    d = json.loads(raw)
except Exception:
    sys.stdout.write(raw); sys.exit(1)  # 非 JSON（多为 CLI 错误）原样透传
if d.get("output"):
    sys.stdout.write(d["output"])
if d.get("stderr"):
    sys.stdout.write(d["stderr"])
code = d.get("exit_code", 0)
sys.exit(0 if (code == 0 or allow) else code)
PY
}

# 从远端取单个 .env 配置项（无则回退默认值）
env_get() {
  local key="$1" def="${2:-}"
  wb_exec "grep -oP '^${key}=\K.*' '$REMOTE_DIR/.env' 2>/dev/null | head -1 || echo '$def'" 30 1 \
    | tr -d '\r\n' || echo "$def"
}

# ---------- 预检 ----------
preflight() {
  info "预检：workbench CLI + 实例可用性"
  workbench version >/dev/null 2>&1 || fail "workbench CLI 未安装或不可用"
  workbench list ecs --region "$REGION" --output json 2>/dev/null \
    | python3 -c "
import json, sys
want = sys.argv[1]
for i in json.load(sys.stdin).get('instances', []):
    if i['instance_id'] == want:
        sys.exit(0 if i['status'] == 'Running' else 1)
sys.exit(1)
" "$INSTANCE_ID" || fail "实例 $INSTANCE_ID 不存在或未运行（region=$REGION）"
  info "实例 $INSTANCE_ID 运行中"
}

# ---------- 查看服务器当前状态 ----------
show_status() {
  info "=== 服务器当前状态（$INSTANCE_ID） ==="
  wb_exec "cd '$REMOTE_DIR' && echo '--- git ---' && git log --oneline -2 && git status -sb && \
           echo '--- 容器 ---' && docker compose ps --format 'table {{.Name}}\t{{.Status}}'" 60
}

# ---------- 更新代码 ----------
update_code() {
  info "=== 拉取最新代码（git pull --ff-only origin master） ==="
  wb_exec "cd '$REMOTE_DIR' && git pull --ff-only origin master" 180
}

# ---------- 重建部署（后台 + 轮询） ----------
redeploy() {
  info "=== 重建容器（docker compose up -d --build，上限 ${BUILD_TIMEOUT}s） ==="
  # 后台启动，日志落盘，避免单次 exec 超时
  wb_exec "cd '$REMOTE_DIR' && nohup docker compose up -d --build > '$DEPLOY_LOG' 2>&1 & echo started pid=\$!" 30
  local elapsed=0 started_at="$(date +%s)"
  while [ "$(( $(date +%s) - started_at ))" -lt "$BUILD_TIMEOUT" ]; do
    sleep "$POLL_INTERVAL"
    elapsed="$(( $(date +%s) - started_at ))"
    # 构建进程是否结束 + 容器是否就绪
    local done_info
    done_info="$(wb_exec "ps aux | grep -E '[d]ocker compose up' | grep -v grep || echo DONE; \
                          cd '$REMOTE_DIR' && docker compose ps --format '{{.Name}} {{.Status}}'" 30 1 || true)"
    if echo "$done_info" | grep -q '^DONE'; then
      info "构建进程已结束（${elapsed}s）"
      break
    fi
    info "构建中… ${elapsed}s"
  done
  if echo "$done_info" | grep -q '^DONE'; then
    :
  else
    warn "构建进程仍在运行（超过 ${BUILD_TIMEOUT}s），请手动检查：$DEPLOY_LOG"
  fi
  info "--- 构建日志尾部 ---"
  wb_exec "tail -20 '$DEPLOY_LOG'" 30 || true
  info "--- 容器状态 ---"
  wb_exec "cd '$REMOTE_DIR' && docker compose ps --format 'table {{.Name}}\t{{.Status}}'" 30
}

# ---------- 验证 ----------
verify() {
  local port
  port="$(env_get WEB_PORT "$WEB_PORT")"
  info "=== 验证：容器健康 + http://localhost:$port ==="
  wb_exec "cd '$REMOTE_DIR' && docker compose ps --format 'table {{.Name}}\t{{.Status}}'" 30
  wb_exec "sleep 3; timeout 15 curl -s -o /dev/null -w 'http_code=%{http_code} time=%{time_total}s\n' http://localhost:$port/" 30 \
    || fail "WEB 探测失败（端口 $port 无响应）"
  info "更新部署完成 ✅"
}

# ---------- 主流程 ----------
for arg in "$@"; do
  case "$arg" in
    --check)      CHECK_ONLY=1 ;;
    --timeout=*)  BUILD_TIMEOUT="${arg#*=}" ;;
    --timeout)    TIMEOUT_NEXT=1 ;;
    *) if [ "${TIMEOUT_NEXT:-0}" = "1" ]; then BUILD_TIMEOUT="$arg"; TIMEOUT_NEXT=0; else warn "忽略未知参数：$arg"; fi ;;
  esac
done
if [ "${TIMEOUT_NEXT:-0}" = "1" ]; then
  fail "--timeout 缺少参数值（用法：--timeout 600 或 --timeout=600）"
fi

preflight
if [ "$CHECK_ONLY" = "1" ]; then
  show_status
  exit 0
fi

show_status
update_code
redeploy
verify
