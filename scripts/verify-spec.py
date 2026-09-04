# -*- coding: utf-8 -*-
"""AGENT-DOC-SPEC compliance checker (V4-aware). Usage: python scripts/verify-spec.py [DIR]"""
import os, re, sys

D = sys.argv[1] if len(sys.argv) > 1 else r"F:\deespeekharness\learn-notes\samples\hm-dianping-complete"
REQ_COMMON = ["title", "slug"]
REQ_2LVL = ["category", "category_slug", "topic", "topic_slug"]
REQ_MULTI = ["path", "slugs"]
FAIL = []

def check(cond, msg):
    if not cond:
        FAIL.append(msg)

def parse_fm(text):
    if not text.startswith("---\n"):
        return None, None
    end = text.find("\n---\n", 4)
    if end < 0:
        return None, None
    fm = {}
    for line in text[4:end].splitlines():
        if ":" in line:
            k, v = line.split(":", 1)
            fm[k.strip()] = v.strip()
    return fm, text[end + 5:]

def parse_yaml_arr(v):
    # supports "[a, b]" or plain string
    if v is None:
        return []
    if v.startswith("[") and v.endswith("]"):
        return [x.strip().strip("'\"") for x in v[1:-1].split(",") if x.strip()]
    return [v.strip()]

md = sorted(f for f in os.listdir(D) if f.endswith(".md"))
print(f"检查 {len(md)} 篇 → {D}\n")
for f in md:
    text = open(os.path.join(D, f), encoding="utf-8").read()
    fm, body = parse_fm(text)
    tag = f
    if fm is None:
        check(False, f"{tag}: front-matter 缺失/未闭合"); continue
    missing = [k for k in REQ_COMMON if k not in fm]
    if missing:
        check(False, f"{tag}: 缺常用字段 {missing}")
    form = "multi" if "path" in fm else ("legacy" if "category" in fm else "none")
    if form == "none":
        check(False, f"{tag}: 无 path 也无 category"); continue
    if form == "multi":
        miss = [k for k in REQ_MULTI if k not in fm]
        if miss:
            check(False, f"{tag}: 缺多级字段 {miss}")
        else:
            p = parse_yaml_arr(fm["path"]); s = parse_yaml_arr(fm["slugs"])
            check(len(p) >= 2, f"{tag}: path 至少 2 级（{p}）")
            check(len(s) == len(p), f"{tag}: slugs 与 path 对齐（s={s}, p={p}）")
            if s:
                check(s[0] == "project", f"{tag}: 首层 slug=project（{s[0]}）")
                check(s[-1] == "hm-dianping", f"{tag}: 末层 slug=hm-dianping（{s[-1]}）")
    else:
        miss = [k for k in REQ_2LVL if k not in fm]
        if miss:
            check(False, f"{tag}: 缺两段式字段 {miss}")

    # 标题 / 层级
    h1s = re.findall(r"^# .+$", body, re.M)
    if len(h1s) != 1 or (fm.get("title") and h1s[0][2:].strip() != fm["title"]):
        check(False, f"{tag}: 一级标题数={len(h1s)} 或与 title 不一致")
    levels = [len(m.group(1)) for m in re.finditer(r"^(#{1,4}) ", body, re.M)]
    if any(b - a > 1 for a, b in zip(levels, levels[1:])):
        check(False, f"{tag}: 标题层级跳级")

    # 代码围栏
    fences = re.findall(r"^```(\w*)\s*$", body, re.M)
    if len(fences) % 2 != 0:
        check(False, f"{tag}: 代码围栏不成对")
    if not all(x for x in fences[::2]):
        check(False, f"{tag}: 开口围栏缺语言标签 {fences[::2]}")

    # 禁止项（脚注=数字式，避免把取反字符类 [^abc] 误判）
    body_no_code = re.sub(r"```.*?```", "", body, flags=re.S)
    forb = {
        "脚注": r"\[\^\d+\]",
        "引用式链接": r"\[[^\]]+\]\[[a-zA-Z0-9_]+\]",
        "原始 HTML": r"<(div|br|details|span|table|p|a|img|pre|code)[ >/]",
        "HTML 注释": r"<!--",
        "数学公式": r"\$\$",
        "正文---": r"^---\s*$",
        "Setext": r"^[^\n]+\n(===|---)\s*$",
        "四空格代码": r"^ {4}\S",
    }
    for name, pat in forb.items():
        if re.search(pat, body_no_code, re.M):
            check(False, f"{tag}: 含禁止项「{name}」")
    rel = re.findall(r"!\[[^\]]*\]\((?!https?://|/uploads/)[^)]+\)", body)
    if rel:
        check(False, f"{tag}: 相对路径图片 {rel}")

print("结果:", "FAIL" if FAIL else "ALL PASS")
if FAIL:
    for x in FAIL[:60]:
        print("  ✗ " + x)
sys.exit(1 if FAIL else 0)
