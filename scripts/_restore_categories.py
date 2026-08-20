#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""restore-from-export 辅助：按 manifest.json 重建分类（含 remark / sortOrder）。"""
import json
import sys
import urllib.request

def api(base, token, method, path, body=None):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    headers = {"X-Api-Token": token}
    if data:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"{method} {path} -> {e.code} {e.read().decode()[:300]}")

def main():
    export_dir, base, token = sys.argv[1], sys.argv[2], sys.argv[3]
    manifest = json.load(open(export_dir + "/manifest.json", encoding="utf-8"))
    # 现有树（避免重复创建）
    tree = api(base, token, "GET", "/api/catalog/tree")["data"]
    by_slug = {n["slug"]: n for n in tree}

    for cat in manifest.get("categories", []):
        node = by_slug.get(cat["slug"])
        if node is None:
            node = api(base, token, "POST", "/api/catalog", {
                "parentId": 0, "name": cat["name"], "slug": cat["slug"],
                "remark": cat.get("remark"), "sortOrder": cat.get("sortOrder", 100),
            })["data"]
        elif cat.get("remark") is not None:
            api(base, token, "PUT", f"/api/catalog/{node['id']}", {"remark": cat["remark"]})
        child_slugs = {c["slug"] for c in node.get("children", [])}
        for topic in cat.get("topics", []):
            if topic["slug"] not in child_slugs:
                api(base, token, "POST", "/api/catalog", {
                    "parentId": node["id"], "name": topic["name"], "slug": topic["slug"],
                    "remark": topic.get("remark"), "sortOrder": topic.get("sortOrder", 100),
                })
        print(f"  ✓ 分类 {cat['name']} ({cat['slug']})")

if __name__ == "__main__":
    main()
