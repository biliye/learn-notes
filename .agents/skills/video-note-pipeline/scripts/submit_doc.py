#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Upload one site-ready Markdown note to the learn-notes site and verify it.

Credentials resolution order:
  1. CLI  --username / --password
  2. env  LN_SITE_USERNAME / LN_SITE_PASSWORD
  3. repo <repo>/scripts/import-*.py 里的 USERNAME / PASSWORD 常量（learn-notes 仓库惯例）
Base URL resolution: --base > env LN_SITE_BASE > http://47.99.138.54:8088
Repo resolution:     --repo > env LN_REPO > F:/deespeekharness/learn-notes (若存在)

Pure stdlib; run with `py` on Windows (bare `python` may be a Store stub).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_BASE = "http://47.99.138.54:8088"
DEFAULT_REPO = r"F:/deespeekharness/learn-notes"


def make_opener():
    return urllib.request.build_opener(urllib.request.ProxyHandler({}))


def request_json(op, method: str, url: str, payload=None, token=None, timeout: int = 120):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with op.open(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            body = json.loads(raw)
        except Exception:
            body = {"raw": raw[:500]}
        return exc.code, body


def repo_import_creds(repo: Path | None) -> tuple[str | None, str | None]:
    """Reuse USERNAME/PASSWORD constants already kept in repo import scripts (no new secret copy)."""
    if not repo or not repo.exists():
        return None, None
    user_re = re.compile(r'^\s*USERNAME\s*=\s*["\']([^"\']+)["\']', re.M)
    pass_re = re.compile(r'^\s*PASSWORD\s*=\s*["\']([^"\']+)["\']', re.M)
    scripts_dir = repo / "scripts"
    files = sorted(scripts_dir.glob("import-*.py")) if scripts_dir.exists() else []
    for f in files:
        text = f.read_text(encoding="utf-8", errors="replace")
        user = user_re.search(text)
        pwd = pass_re.search(text)
        if user and pwd:
            return user.group(1), pwd.group(1)
    return None, None


def parse_args(argv):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--file", required=True, help="Site-ready Markdown note path")
    p.add_argument("--repo", default=None, help="learn-notes repo dir (credentials fallback)")
    p.add_argument("--base", default=None, help="Site base URL")
    p.add_argument("--username", default=None)
    p.add_argument("--password", default=None)
    p.add_argument("--on-conflict", default="NEW_VERSION", help="NEW_VERSION (default) or SKIP")
    p.add_argument("--verify", action="store_true", help="Check catalog tree after import")
    p.add_argument("--timeout", type=int, default=120)
    return p.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    note_path = Path(args.file).resolve()
    if not note_path.exists():
        print(f"[fail] note file not found: {note_path}")
        return 1

    repo = Path(args.repo or os.environ.get("LN_REPO") or DEFAULT_REPO).expanduser()
    base = (args.base or os.environ.get("LN_SITE_BASE") or DEFAULT_BASE).strip().rstrip("/")

    username = args.username or os.environ.get("LN_SITE_USERNAME")
    password = args.password or os.environ.get("LN_SITE_PASSWORD")
    if not (username and password):
        u2, p2 = repo_import_creds(repo)
        username = username or u2
        password = password or p2
    if not (username and password):
        print(
            "[fail] no site credentials. Provide --username/--password, set "
            "LN_SITE_USERNAME/LN_SITE_PASSWORD, or point --repo at a learn-notes repo "
            "whose scripts/import-*.py carry USERNAME/PASSWORD."
        )
        return 1

    op = make_opener()
    code, body = request_json(
        op, "POST", base + "/api/auth/login",
        {"username": username, "password": password}, timeout=args.timeout,
    )
    if code != 200 or not isinstance(body.get("data"), dict) or not body["data"].get("token"):
        print(f"[fail] login failed HTTP {code}: {json.dumps(body, ensure_ascii=False)[:400]}")
        return 1
    token = body["data"]["token"]
    print(f"[ok] logged in as {body['data'].get('username')} (userId={body['data'].get('userId')})")

    filename = note_path.name
    content = note_path.read_text(encoding="utf-8")
    code, body = request_json(
        op, "POST", base + "/api/import/doc",
        {"filename": filename, "content": content, "onConflict": args.on_conflict},
        token=token, timeout=args.timeout,
    )
    if code != 200:
        print(f"[fail] import HTTP {code}: {json.dumps(body, ensure_ascii=False)[:500]}")
        return 1
    data = body.get("data") or {}
    resolved = data.get("resolvedBy")
    warnings = data.get("warnings") or []
    print(f"[ok] docId={data.get('docId')} created={data.get('created')} version={data.get('version')}")
    print(f"[ok] resolvedBy={resolved} | category={data.get('category')} | topic={data.get('topic')}")
    if warnings:
        print("[warn] warnings:")
        for w in warnings:
            print("   -", w)
    if resolved == "INBOX":
        print("[fail] resolvedBy=INBOX: classification failed, fix front-matter/filename and resubmit.")
        return 1

    if args.verify:
        code, body = request_json(op, "GET", base + "/api/catalog/tree", token=token, timeout=args.timeout)
        if code == 200:
            cat_name = (data.get("category") or {}).get("name")
            top = [n for n in (body.get("data") or []) if (n.get("name") == cat_name)]
            print("[ok] verify catalog:", f"found category '{cat_name}'" if top else f"NOT FOUND '{cat_name}' (maybe cached)")
        else:
            print("[warn] verify catalog skipped (HTTP %s)" % code)

    print("[done] note submitted to learn-notes site.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
