#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""restore-from-export 辅助：期望 vs 实际计数对比（R36 核心断言）。"""
import json
import sys
import urllib.request

def api(base, token, path):
    req = urllib.request.Request(base + path, headers={"X-Api-Token": token})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode())

def main():
    export_dir, base, token = sys.argv[1], sys.argv[2], sys.argv[3]
    manifest = json.load(open(export_dir + "/manifest.json", encoding="utf-8"))
    expected = manifest["counts"]

    tree = api(base, token, "/api/catalog/tree")["data"]
    categories = [n for n in tree if n["slug"] != "inbox"]
    topics = [t for n in categories for t in n["children"]]
    docs = api(base, token, "/api/docs?size=100")["data"]
    annotations = 0
    for d in docs["items"]:
        detail = api(base, token, f"/api/docs/{d['id']}")["data"]
        annotations += len(detail.get("annotations") or [])

    actual = {
        "categories": len(categories),
        "topics": len(topics),
        "docs": len(docs["items"]),
        "annotations": annotations,
    }

    ok = True
    print(f"{'项':<14} {'期望':>6} {'实际':>6} {'结果':>4}")
    for key in ("categories", "topics", "docs", "annotations"):
        match = actual[key] == expected[key]
        ok = ok and match
        print(f"{key:<14} {expected[key]:>6} {actual[key]:>6} {'✓' if match else '✗ 不一致!'}")

    # 图片计数（uploads 目录）
    import os
    uploads = export_dir + "/uploads"
    expected_images = int(expected.get("images", 0))
    if os.path.isdir(uploads):
        n = sum(1 for _, _, files in os.walk(uploads) for f in files)
    else:
        n = 0
    match = n == expected_images
    ok = ok and match
    print(f"{'images':<14} {expected_images:>6} {n:>6} {'✓' if match else '✗ 不一致!'}")

    if not ok:
        print("\n[FAIL] 计数不一致，恢复未完全成功")
        sys.exit(1)
    print("\n[OK] 五项计数全部一致，恢复成功")

if __name__ == "__main__":
    main()
