#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把 samples/hm-dianping-complete/*.md（黑马点评完整实现版笔记）导入远程 learn-notes，归属 bailoayi。

笔记用 V4 多级目录 front-matter（path: [项目, 黑马点评] + slugs: [project, hm-dianping]），
由后端 MetaResolver/ImportService 逐级建目录。仍以 Bearer JWT 导入（归 bailoayi）。
"""
import glob, json, os, sys, urllib.request

NOTES_DIR = r"F:\deespeekharness\learn-notes\samples\hm-dianping-complete"
BASE = "http://47.99.138.54:8088"
USERNAME = "bailoayi"
PASSWORD = "[REDACTED]"
_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def login():
    body = json.dumps({"username": USERNAME, "password": PASSWORD}).encode("utf-8")
    r = urllib.request.Request(BASE + "/api/auth/login", data=body,
                               headers={"Content-Type": "application/json"}, method="POST")
    return json.loads(_OPENER.open(r, timeout=30).read())["data"]["token"]

def import_doc(token, filename, content):
    body = json.dumps({"filename": filename, "content": content,
                       "onConflict": "NEW_VERSION"}, ensure_ascii=False).encode("utf-8")
    r = urllib.request.Request(BASE + "/api/import/doc", data=body,
                               headers={"Content-Type": "application/json",
                                        "Authorization": "Bearer " + token}, method="POST")
    try:
        with _OPENER.open(r, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")

def main():
    token = login()
    files = sorted(glob.glob(os.path.join(NOTES_DIR, "*.md")))
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
            cat = d.get("category", {}).get("name", "?")
            topic = d.get("topic", {}).get("name", "?")
            print(f"[{code}] {name} -> {resolved} created={created} v{version} "
                  f"path={cat}/{topic} warnings={len(warns)}")
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
