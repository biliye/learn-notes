#!/usr/bin/env bash
# ============================================================
# learn-notes 从 notes-export/ 重建（R36 恢复演练，最坏情况路径）
# 只使用本机 git 里的 notes-export/（md + insights.json + uploads/ + manifest.json），
# 不用任何数据库 dump。
#
# 前置：后端已在运行（空库 + 空 storage 的干净环境），API Token 可用。
# 用法：./scripts/restore-from-export.sh [HOST] [PORT]
#   HOST 默认 127.0.0.1，PORT 默认 8088
# ============================================================
set -euo pipefail

HOST="${1:-127.0.0.1}"
PORT="${2:-8088}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXPORT="$ROOT/notes-export"

[ -f .env ] && set -a && . ./.env && set +a
TOKEN="${APP_API_TOKEN:?APP_API_TOKEN 未设置}"
BASE="http://${HOST}:${PORT}"

[ -d "$EXPORT" ] || { echo "找不到 $EXPORT，请先跑 sync-to-local.ps1" >&2; exit 1; }
command -v python3 >/dev/null || { echo "需要 python3" >&2; exit 1; }

api() { # api <method> <path> <json?>
  local method="$1" path="$2" data="${3:-}"
  if [ -n "$data" ]; then
    curl -fsS -X "$method" "$BASE$path" \
      -H "X-Api-Token: $TOKEN" -H 'Content-Type: application/json' -d "$data"
  else
    curl -fsS -X "$method" "$BASE$path" -H "X-Api-Token: $TOKEN"
  fi
}

echo "[1/4] 按 manifest.json 重建分类与 remark..."
python3 "$ROOT/scripts/_restore_categories.py" "$EXPORT" "$BASE" "$TOKEN"

echo "[2/4] 导入文档..."
FAIL=0
while IFS= read -r md; do
  rel="${md#$EXPORT/}"
  cat_slug="$(dirname "$rel" | cut -d/ -f1)"
  topic_slug="$(dirname "$rel" | cut -d/ -f2)"
  filename="$(basename "$md")"
  payload="$(python3 - "$md" <<'PY'
import json, sys
path = sys.argv[1]
content = open(path, encoding="utf-8").read()
print(json.dumps({"filename": path.split("/")[-1], "content": content}, ensure_ascii=False))
PY
)"
  resp="$(api POST /api/import/doc "$payload")" || { echo "  导入失败: $rel"; FAIL=1; continue; }
  echo "  ✓ $rel  -> $(echo "$resp" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d.get("resolvedBy"), "v"+str(d.get("version")), "warnings="+str(len(d.get("warnings") or [])))')"
done < <(find "$EXPORT" -name "*.md" | sort)

echo "[3/4] 回灌见解..."
while IFS= read -r jsonf; do
  rel="${jsonf#$EXPORT/}"
  doc_slug="$(basename "$rel" .insights.json)"
  [ -f "${jsonf%.insights.json}.md" ] || continue
  # slugPath = insights.json 所在目录链的全部 slug（大类 → … → 叶目录），docSlug 单独传
  payload="$(python3 - "$jsonf" "$rel" "$doc_slug" <<'PY'
import json, sys
path, rel, slug = sys.argv[1], sys.argv[2], sys.argv[3]
insights = json.load(open(path, encoding="utf-8"))
dirs = rel.split("/")[:-1]
print(json.dumps({"slugPath": dirs, "docSlug": slug, "insights": insights}, ensure_ascii=False))
PY
)"
  resp="$(api POST /api/import/insights "$payload")" || { echo "  回灌失败: $rel"; FAIL=1; continue; }
  echo "  ✓ $rel -> $(echo "$resp" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print("created=%d skipped=%d stale=%d orphan=%d" % (d["created"],d["skipped"],d["stale"],d["orphan"]))')"
done < <(find "$EXPORT" -name "*.insights.json" | sort)

echo "[4/4] 复制 uploads/ 回 storage/uploads..."
if [ -d "$EXPORT/uploads" ]; then
  mkdir -p "${APP_UPLOAD_DIR:-storage/uploads}"
  cp -r "$EXPORT/uploads/." "${APP_UPLOAD_DIR:-storage/uploads}/"
  echo "  uploads 已复制"
fi

echo
echo "==== 恢复完成，期望 vs 实际计数对比 ===="
python3 "$ROOT/scripts/_verify_restore.py" "$EXPORT" "$BASE" "$TOKEN" || FAIL=1

exit "$FAIL"
