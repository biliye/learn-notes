#!/usr/bin/env bash
# ============================================================
# 批量投稿脚本（T17）：把指定目录下所有 .md 逐个 POST /api/import/doc
# 用法：./scripts/import-docs.sh [目录] [HOST] [PORT]
#   目录默认 samples/；HOST 默认 127.0.0.1；PORT 默认 8088
# 任一出现 resolvedBy=INBOX 或非空 warnings 时以非 0 退出（便于 agent 自检）
# ============================================================
set -euo pipefail

DIR="${1:-samples}"
HOST="${2:-127.0.0.1}"
PORT="${3:-8088}"

[ -f .env ] && set -a && . ./.env && set +a
TOKEN="${APP_API_TOKEN:?APP_API_TOKEN 未设置}"
BASE="http://${HOST}:${PORT}"

FAIL=0
for f in "$DIR"/*.md; do
  [ -f "$f" ] || continue
  fname="$(basename "$f")"
  payload="$(python3 - "$f" "$fname" <<'PY'
import json, sys
path, fname = sys.argv[1], sys.argv[2]
print(json.dumps({"filename": fname, "content": open(path, encoding="utf-8").read(),
                  "onConflict": "NEW_VERSION"}, ensure_ascii=False))
PY
)"
  resp="$(curl -fsS -X POST "$BASE/api/import/doc" \
    -H "X-Api-Token: $TOKEN" -H 'Content-Type: application/json' -d "$payload")" \
    || { echo "[FAIL] $f 请求失败"; FAIL=1; continue; }

  data="$(echo "$resp" | python3 -c 'import sys,json;print(json.dumps(json.load(sys.stdin)["data"],ensure_ascii=False))')"
  resolved="$(echo "$data" | python3 -c 'import sys,json;print(json.load(sys.stdin)["resolvedBy"])')"
  version="$(echo "$data" | python3 -c 'import sys,json;print(json.load(sys.stdin)["version"])')"
  warns="$(echo "$data" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["warnings"]))')"
  reanchor="$(echo "$data" | python3 -c 'import sys,json;r=json.load(sys.stdin)["reanchor"];print("a=%d s=%d o=%d"%(r["active"],r["stale"],r["orphan"]))')"

  echo "✓ $f -> $resolved v$version warnings=$warns reanchor[$reanchor]"
  if [ "$resolved" = "INBOX" ] || [ "$warns" != "0" ]; then
    echo "  ⚠ 需要修正：$data"
    FAIL=1
  fi
done

[ "$FAIL" = "0" ] && echo "全部投稿成功" || { echo "存在失败项，请修正后重投" >&2; exit 1; }
