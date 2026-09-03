#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""restore-from-export 辅助：按 manifest.json 重建多级目录（含 remark / sortOrder / maxLevel）。

manifest v2 结构：categories[] 为任意深度递归节点 {name,slug,remark,sortOrder,maxLevel,children[]}。
"""
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


def flatten(nodes, parent_id):
    """把现有树拍平成 [(parent_id, node)] 便于按父目录幂等查重"""
    out = []
    for n in nodes or []:
        out.append((parent_id, n))
        out.extend(flatten(n.get("children", []), n["id"]))
    return out


def restore_node(meta, parent_id, index):
    kids = index.get(parent_id, [])
    node = next((n for n in kids if n["slug"] == meta.get("slug")), None)
    if node is None:
        body = {
            "parentId": parent_id,
            "name": meta["name"],
            "slug": meta.get("slug"),
            "remark": meta.get("remark"),
            "sortOrder": meta.get("sortOrder", 100),
        }
        if parent_id == 0 and meta.get("maxLevel") is not None:
            body["maxLevel"] = meta["maxLevel"]
        node = api(base, token, "POST", "/api/catalog", body)["data"]
        index.setdefault(parent_id, []).append(node)
    elif meta.get("remark") is not None:
        api(base, token, "PUT", f"/api/catalog/{node['id']}", {"remark": meta["remark"]})
    for child in meta.get("children", []) or []:
        restore_node(child, node["id"], index)


def main():
    global base, token
    export_dir, base, token = sys.argv[1], sys.argv[2], sys.argv[3]
    manifest = json.load(open(export_dir + "/manifest.json", encoding="utf-8"))
    tree = api(base, token, "GET", "/api/catalog/tree")["data"]
    index = {}
    for parent_id, n in flatten(tree, 0):
        index.setdefault(parent_id, []).append(n)

    for cat in manifest.get("categories", []):
        restore_node(cat, 0, index)
        print(f"  ✓ 分类 {cat['name']} ({cat['slug']})")


if __name__ == "__main__":
    main()
