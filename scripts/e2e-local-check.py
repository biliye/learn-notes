#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""后端集成验收脚本（本地联调用，T17 将把正式版本写入 scripts/）。
用 Python requests 避免 Windows curl 的 GBK 文件名/中文编码问题。
"""
import json
import os
import sys
import time
import urllib.request

BASE = "http://localhost:8080"
TOKEN = ""
API_TOKEN = "integration-token-123"

# 本机可能有注册表代理，禁用代理避免 urllib 走代理导致 404
_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def call(method, path, body=None, token=None, raw=False, files=None):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if api_token():
        headers["X-Api-Token"] = api_token()
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with _OPENER.open(req, timeout=30) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")

def api_token():
    return API_TOKEN

def check(name, cond, detail=""):
    status = "PASS" if cond else "FAIL"
    print(f"[{status}] {name}" + (f"  -> {detail}" if detail and not cond else ""))
    return cond

results = []
def run(name, fn):
    try:
        ok = fn()
        results.append(ok)
    except Exception as e:
        print(f"[FAIL] {name}  -> 异常: {e}")
        results.append(False)

def login():
    global TOKEN
    # 管理员凭据从环境变量读，不在脚本里猜测密码
    user = os.environ.get("LN_ADMIN_USER", "admin")
    pwd = os.environ.get("LN_ADMIN_PASSWORD", "")
    if not pwd:
        sys.exit("请先设置 LN_ADMIN_PASSWORD 环境变量")
    code, resp = call("POST", "/api/auth/login", {"username": user, "password": pwd})
    d = json.loads(resp)["data"]
    TOKEN = d["token"]
    return check("登录成功", code == 200 and len(TOKEN) > 50)

def main():
    run("登录", login)

    code, resp = call("GET", "/api/health")
    run("健康检查", lambda: check("health=UP", json.loads(resp)["data"]["status"] == "UP"))

    code, resp = call("GET", "/api/catalog/tree")  # 无 token
    run("未带token访问返回401", lambda: check("401", code == 401))

    code, resp = call("GET", "/api/catalog/tree", token=TOKEN)
    tree = json.loads(resp)["data"]
    run("登录后目录树含INBOX", lambda: check("INBOX存在", any(n["slug"] == "inbox" for n in tree)))

    # 1) front-matter 通道导入
    lambda_md = """---
category: Java
category_slug: java
topic: 函数
topic_slug: function
title: Lambda 基础
slug: lambda-basics
tags: [基础, lambda]
summary: 讲清 Lambda 与函数式接口的关系。
---

# Lambda 基础

第一段讲 Lambda 表达式，它是函数式接口的匿名实现。

```java
list.forEach(x -> System.out.println(x));
```

## 可变参数

调用时可以传任意个 `int`。
"""
    code, resp = call("POST", "/api/import/doc", {
        "filename": "x.md", "content": lambda_md, "onConflict": "NEW_VERSION"})
    r = json.loads(resp)["data"]
    run("front-matter导入", lambda: check("resolvedBy=FRONT_MATTER", r["resolvedBy"] == "FRONT_MATTER",
                                          json.dumps(r, ensure_ascii=False)))
    run("分类自动创建", lambda: check("topic.autoCreated", r["topic"]["autoCreated"] is True))
    doc_id = r["docId"]

    # 2) 详情 blocks：代码/正文差异化数据
    code, resp = call("GET", f"/api/docs/{doc_id}", token=TOKEN)
    detail = json.loads(resp)["data"]
    types = [b["type"] for b in detail["blocks"]]
    run("详情blocks正确", lambda: check("含heading/paragraph/code", "heading" in types and "paragraph" in types and "code" in types,
                                          str(types)))
    code_block = next(b for b in detail["blocks"] if b["type"] == "code")
    run("代码块lang=java且含围栏", lambda: check("lang=java", code_block["lang"] == "java" and code_block["raw"].startswith("```")))

    # 3) 加见解（正文块）
    para_block = next(b for b in detail["blocks"] if b["type"] == "paragraph")
    code, resp = call("POST", "/api/annotations", {
        "docId": doc_id, "anchor": para_block["anchor"], "contentMd": "这里注意自动装箱"}, token=TOKEN)
    ann = json.loads(resp)["data"]
    run("创建见解", lambda: check("ACTIVE", ann["status"] == "ACTIVE" and ann["anchor"] == para_block["anchor"]))

    # 4) 重新导入（只改一段）→ 版本+1，见解保持
    lambda_md_v2 = lambda_md.replace("第一段讲 Lambda 表达式，它是函数式接口的匿名实现。",
                                     "第一段讲 Lambda 表达式，它是函数式接口的匿名实现，等价于匿名内部类。")
    code, resp = call("POST", "/api/import/doc", {
        "filename": "x.md", "content": lambda_md_v2, "onConflict": "NEW_VERSION"})
    r2 = json.loads(resp)["data"]
    run("同slug重导入产生v2", lambda: check("version=2", r2["version"] == 2 and r2["created"] is False))
    code, resp = call("GET", f"/api/docs/{doc_id}", token=TOKEN)
    detail2 = json.loads(resp)["data"]
    run("版本号=2", lambda: check("currentVersion=2", detail2["currentVersion"] == 2))
    anns2 = detail2["annotations"]
    run("被改动块的见解标记STALE/ORPHAN且不丢失", lambda: check("见解存在且未ACTIVE",
        any(a["contentMd"] == "这里注意自动装箱" and a["status"] in ("STALE", "ORPHAN") for a in anns2),
        str([(a["contentMd"], a["status"]) for a in anns2])))

    # 5) 文件名通道
    code, resp = call("POST", "/api/import/upload")
    # 用 /api/import/doc 模拟 FILENAME（filename 三段）—— multipart 由 T17 脚本覆盖
    props_md = "# props 基础\n\n组件属性传参。\n"
    code, resp = call("POST", "/api/import/doc", {"filename": "vue__组件__props-basics.md", "content": props_md})
    r3 = json.loads(resp)["data"]
    run("文件名通道", lambda: check("resolvedBy=FILENAME", r3["resolvedBy"] == "FILENAME"
        and r3["category"]["name"] == "vue" and r3["topic"]["name"] == "组件"))

    # 6) INBOX 兜底
    code, resp = call("POST", "/api/import/doc", {"filename": "随手记.md", "content": "# 随手记\n\n随便写点什么\n"})
    r4 = json.loads(resp)["data"]
    run("INBOX兜底", lambda: check("resolvedBy=INBOX", r4["resolvedBy"] == "INBOX"
        and r4["category"]["name"] == "INBOX" and r4["topic"]["name"] == "未归类"))

    # 7) 搜索
    code, resp = call("GET", "/api/search?q=Lambda", token=TOKEN)
    hits = json.loads(resp)["data"]
    run("搜索命中", lambda: check("命中lambda文档", len(hits) > 0 and any("Lambda" in h["title"] for h in hits)))

    # 8) 导出 zip（二进制，需按字节读取）
    try:
        req = urllib.request.Request(BASE + "/api/export/all", headers={
            "X-Api-Token": API_TOKEN})
        with _OPENER.open(req, timeout=60) as resp:
            zip_bytes = resp.read()
        run("导出zip", lambda: check("zip下载", zip_bytes[:2] == b"PK"))
    except Exception as e:
        run("导出zip", lambda: check("zip下载", False, str(e)))

    print("\n==== 结果汇总 ====")
    passed = sum(1 for r in results if r)
    print(f"{passed}/{len(results)} 通过")
    sys.exit(0 if all(results) else 1)

if __name__ == "__main__":
    main()
