#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把 samples/java-onenote/*.md 导入远程 learn-notes 服务器，归属到 bailoayi 账户。

关键：V3 多用户版中 X-Api-Token 通道归 ADMIN；要让笔记归到用户 bailoayi，
必须先用其账号登录拿 Bearer JWT，再以 Authorization: Bearer 调 /api/import/doc。
"""
import glob, json, os, sys, urllib.request

SEED_DIR = r"F:\deespeekharness\learn-notes\samples\java-onenote"
BASE = os.environ.get("LN_SITE_BASE", "http://47.99.138.54:8088")
USERNAME = os.environ.get("LN_SITE_USERNAME", "")
PASSWORD = os.environ.get("LN_SITE_PASSWORD", "")
if not USERNAME or not PASSWORD:
    sys.exit("请先设置 LN_SITE_USERNAME / LN_SITE_PASSWORD 环境变量")

_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def login():
    body = json.dumps({"username": USERNAME, "password": PASSWORD}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/auth/login", data=body,
                                 headers={"Content-Type": "application/json"}, method="POST")
    with _OPENER.open(req, timeout=30) as resp:
        d = json.loads(resp.read().decode("utf-8"))
    token = d["data"]["token"]
    print(f"登录成功: {d['data']['username']} (userId={d['data']['userId']}, role={d['data']['role']})")
    return token

def import_doc(token, filename, content):
    body = json.dumps({"filename": filename, "content": content,
                       "onConflict": "NEW_VERSION"}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/import/doc", data=body,
                                 headers={"Content-Type": "application/json",
                                          "Authorization": "Bearer " + token}, method="POST")
    try:
        with _OPENER.open(req, timeout=40) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")

def main():
    token = login()
    files = sorted(glob.glob(os.path.join(SEED_DIR, "*.md")))
    print(f"发现 {len(files)} 篇待导入\n")
    ok = fail = 0
    for f in files:
        name = os.path.basename(f)
        with open(f, encoding="utf-8") as fh:
            content = fh.read()
        code, resp = import_doc(token, name, content)
        try:
            d = json.loads(resp)["data"]
            resolved = d["resolvedBy"]; created = d["created"]; version = d["version"]
            warns = d["warnings"]
            cat = d["category"]["name"]; topic = d["topic"]["name"]
            print(f"[{code}] {name} -> {resolved} created={created} v{version} "
                  f"cat={cat}/{topic} warnings={len(warns)}")
            for w in warns:
                print("    warning:", w)
            if resolved == "INBOX" or warns:
                fail += 1
            else:
                ok += 1
        except Exception as e:
            print(f"[{code}] {name} -> 解析失败: {e} :: {resp[:300]}")
            fail += 1
    print(f"\n完成：成功 {ok} 篇，失败 {fail} 篇")
    sys.exit(1 if fail else 0)

if __name__ == "__main__":
    main()
