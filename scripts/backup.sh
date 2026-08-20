#!/usr/bin/env bash
# ============================================================
# learn-notes 服务器端定时备份脚本（R34，供 cron 调用）
# 三层归档，各自只保留最近 14 份；任何一步失败以非 0 退出（cron 才能暴露问题）
# 用法：在项目根目录执行  ./scripts/backup.sh
# cron 示例（每天 03:30）：
#   30 3 * * * cd /opt/learn-notes && ./scripts/backup.sh >> backup/backup.log 2>&1
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# 从 .env 读取（若存在）
[ -f .env ] && set -a && . ./.env && set +a

BACKUP_DIR="$ROOT/backup"
DB_DIR="$BACKUP_DIR/db"
STORAGE_DIR="$BACKUP_DIR/storage"
EXPORT_DIR="$BACKUP_DIR/export"
KEEP=14
TS="$(date +%Y%m%d-%H%M)"
WEB_PORT="${WEB_PORT:-8088}"

mkdir -p "$DB_DIR" "$STORAGE_DIR" "$EXPORT_DIR"

fail() {
  echo "[ERROR] $(date '+%F %T') $*" >&2
  exit 1
}

# 1) mysqldump（经容器执行，--single-transaction 不锁业务写）
echo "[$(date '+%F %T')] 备份数据库..."
docker exec ln-mysql mysqldump --single-transaction --default-character-set=utf8mb4 \
  "${MYSQL_DATABASE:-learn_notes}" | gzip > "$DB_DIR/learn_notes-$TS.sql.gz" \
  || fail "mysqldump 失败"

# 2) storage 归档（导入原文 + 上传图片）
echo "[$(date '+%F %T')] 备份 storage..."
tar czf "$STORAGE_DIR/storage-$TS.tgz" storage/ || fail "storage 打包失败"

# 3) 全量导出 zip（人可读、含见解，走 X-Api-Token）
echo "[$(date '+%F %T')] 调用 /api/export/all 导出..."
curl -fsS -H "X-Api-Token: ${APP_API_TOKEN:?APP_API_TOKEN 未设置}" \
  -o "$EXPORT_DIR/learn-notes-export-$TS.zip" \
  "http://127.0.0.1:${WEB_PORT}/api/export/all" || fail "导出接口调用失败"

# 4) 各目录只保留最近 $KEEP 份
for dir in "$DB_DIR" "$STORAGE_DIR" "$EXPORT_DIR"; do
  ls -1t "$dir" | tail -n +$((KEEP + 1)) | while read -r f; do
    rm -f "$dir/$f"
    echo "  清理旧归档: $dir/$f"
  done
done

# 5) 汇总
echo "[$(date '+%F %T')] 备份完成："
echo "  db      : $(ls -1 "$DB_DIR" | wc -l) 份 最新 $(ls -1t "$DB_DIR" | head -1) ($(du -h "$DB_DIR"/* | tail -1 | cut -f1))"
echo "  storage : $(ls -1 "$STORAGE_DIR" | wc -l) 份 最新 $(ls -1t "$STORAGE_DIR" | head -1)"
echo "  export  : $(ls -1 "$EXPORT_DIR" | wc -l) 份 最新 $(ls -1t "$EXPORT_DIR" | head -1)"
