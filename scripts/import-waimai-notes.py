#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""一次性导入脚本：把 samples/sky-take-out/*.md 逐个 POST /api/import/doc"""
import glob, json, os, urllib.request

BASE = "http://localhost:8080"
TOKEN = "integration-token-123"   # .env 里的 APP_API_TOKEN，后端启动时注入

_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def import_doc(filename, content):
    body = json.dumps({"filename": filename, "content": content,
                       "onConflict": "NEW_VERSION"}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/import/doc", data=body,
                                 headers={"Content-Type": "application/json",
                                          "X-Api-Token": TOKEN}, method="POST")
    try:
        with _OPENER.open(req, timeout=30) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")

ok = 0
fail = 0
for f in sorted(glob.glob(r"F:\deespeekharness\learn-notes\samples\sky-take-out\*.md")):
    name = os.path.basename(f)
    with open(f, encoding="utf-8") as fh:
        content = fh.read()
    code, resp = import_doc(name, content)
    try:
        d = json.loads(resp)["data"]
        resolved = d["resolvedBy"]
        created = d["created"]
        version = d["version"]
        warns = d["warnings"]
        cat = d["category"]["name"]
        topic = d["topic"]["name"]
        print(f"[{code}] {name} -> {resolved} created={created} v{version} "
              f"cat={cat}/{topic} warnings={len(warns)}")
        if warns:
            print("    warnings:", warns)
        if resolved == "INBOX" or warns:
            fail += 1
        else:
            ok += 1
    except Exception as e:
        print(f"[{code}] {name} -> 解析失败: {e} :: {resp[:200]}")
        fail += 1

print(f"\n完成：成功 {ok} 篇，失败 {fail} 篇")
