---
name: video-note-pipeline
description: 端到端流水线：把 B站（bilibili）视频/图文/opus 做成"学习型笔记"并发布到个人 learn-notes 知识库网站（http://47.99.138.54:8088/catalog，bailoayi 名下）。当用户要求"提取这个 B 站视频/图文的内容""整理成学习笔记""放进我的知识库/目录体系""发到网站/上传站点/回灌/提交到 catalog"、或甩来一个 bilibili 链接并希望得到结构化笔记时使用——只要目标含"去网站"，就优先用本技能而不是单用 bili-note。内部六步：①环境检查 ②提取与转写（字幕→网页AI字幕→音频 ASR，复用 bili-note 与本地 FunASR Server）③按预算写笔记 ④按站点规范转成可发布文档并本地入库 ⑤登录站点导入并验收 ⑥交付汇报。当前内容路线基于字幕/音频转写，已为后续"视觉理解（关键帧/OCR/多模态）"预留扩展位。
---

# Video Note Pipeline

把「B 站内容 → 学习笔记 → learn-notes 网站」整条链路固化成可重复执行的流程。它是**编排层**：提取/转写/预算/评分复用 `bili-note` skill 的脚本；站点规范与导入复用本机 `learn-notes` 仓库（默认 `F:\deespeekharness\learn-notes`）。本技能不重复实现它们，只负责把每一步串对、产物落对地方、验收做齐。

## 一、与 bili-note 的边界

- `bili-note`：负责"抓内容、转写、归档、预算定标、笔记验收"。
- `video-note-pipeline`：在 bili-note 之上加三样东西——(a) 产出**站点规范版**笔记（front-matter + 无禁项 Markdown）；(b) 把站点版**提交到远程网站**并验收 `resolvedBy=FRONT_MATTER`、零 warning；(c) 本地留 canonical 源文件。
- 只"提取内容到本地归档/写一份本地学习笔记"、不打算发网站 → 直接用 bili-note，本技能不必加载。

## 二、前提与配置

按需提前确认，缺什么补什么：

| 项 | 解析顺序 / 默认 | 说明 |
|---|---|---|
| Python 解释器 | Windows 用 `py`（本机 Git Bash 裸 `python` 是 Store stub，静默退出 49） | 本机必用 `py` |
| bili-note 目录 | `$BILI_NOTE_SKILL_DIR` → `%USERPROFILE%\.zcode\skills\bili-note` → `%USERPROFILE%\.agents\skills\bili-note` → `%USERPROFILE%\.codex\skills\bili-note` → `<repo>\.zcode/skills/bili-note` → `<repo>\.agents/skills/bili-note` | 找不到就先按 bili-note README 的安装语安装 |
| learn-notes 仓库 | `$LN_REPO` → `F:\deespeekharness\learn-notes`（存在才用） | 站点规范、canonical 落位、凭据回退都靠它 |
| 站点地址 | `$LN_SITE_BASE` → `http://47.99.138.54:8088` | |
| 站点账号 | `$LN_SITE_USERNAME` / `$LN_SITE_PASSWORD` → 仓库 `scripts/import-*.py` 里现成的 `USERNAME`/`PASSWORD` 常量（脚本会自动读） | 不要在 SKILL/正文里复制粘贴密码 |

凭据属敏感信息：只在命令/环境变量里用，不要写进交付说明、不要 commit 到 `.env` 或技能目录。

## 三、六步流程

### 0. 收集需求

先弄清四件事，缺的当场问：

1. 链接类型：`/video/BV...`、`/opus/...`、`/dynamic/...` 还是纯 opus id。
2. 要不要评论区（默认不要，评论只保留纠错/补充/实践类内容）。
3. 站点归类：大类 → 小方向 想叫什么；不给就按内容合理新建（中文大类给 ASCII `category_slug`），新分类会自动创建。
4. 是否只要提炼笔记 vs 也要完整归档原文（默认都做：归档进本地材料包，站点只放笔记）。

### 1. 环境检查与选路

```powershell
$bili = "C:\Users\123\.zcode\skills\bili-note"   # 或解析出来的目录
py "$bili\scripts\check_environment.py"
```

读输出选路：

- 公开字幕/图文 OK → 走字幕正文路线。
- 只有 `ai-zh` 空 url 且 Chrome+web-access 可用 → 网页 AI 字幕路线（本机可启用：`powershell -File C:\Users\123\.cache\web-access\start.ps1` 起独立 Chrome 并在其中登录 B 站一次，详见 `references/web-access.md` 或 `C:\Users\123\.cache\web-access\README.md`）。
- 都没有 → 音频 ASR 兜底：`Audio ASR fallback` 里若有 `FunASR Server ...: OK`，用 `--asr-backend funasr-server`；否则按 bili-note 建议用共享 Qwen3-ASR。
- `visual_dependency` 高/中风险时见第 3 步视觉提示，不要硬写。

### 2. 提取与转写

**视频**：先尝试 `run_bili_note.py` 一键（能拿到公开字幕/图文时最省事）；字幕缺失就走分步：

```powershell
py "$bili\scripts\extract_bilibili.py" "<url>" --out "<work>" --parts key `
  --download-audio --transcribe --asr-backend funasr-server --asr-language zh
```

- ASR 后端让 `auto` 决定即可；本机常驻 FunASR Server 时 `auto` 会自动命中 `funasr-server`。
- 长音频（≥20 分钟）客户端可能等待久：给转写命令设 `$env:FUNASR_SERVER_TIMEOUT="3600"`（默认 3600s，够 30+ 分钟视频）。
- 若 FunASR Server 假死（短音频也超时、进程 CPU/GPU 占用低）→ 重启它再重试（命令见仓库记忆/运维备注，`funasr-server.exe --device cuda --port 10095`）。

**图文/opus**：`run_bili_note.py` 或 `extract_bilibili_opus.py` 直接抓正文/图片/代码块即可，通常无需 ASR。

**ASR-only 时归档前必须补 run_summary**：`archive_bili_materials.py` 只认 `run_summary.json` 里的 `transcripts`（带 `transcript_txt`/`transcript_json` 绝对路径、`page/cid/part/duration`），否则把转写当"无字幕"，预算全错。`extract_bilibili.py` 直跑不会生成它——没有就用一次 `run_bili_note.py` 或在提取目录里手写这份 JSON（结构见 bili-note 归档脚本 `transcript_manifest_from_run_summary`）。

### 3. 写学习笔记（两份产物）

先归档出预算，再动笔：

```powershell
py "$bili\scripts\archive_bili_materials.py" --extract-dir "<work>" --archive-dir "<arch>"
# 读 <arch>\metadata\note_budget.json：recommended_note_chars_*、granularity、visual_dependency
```

产物 A（本地材料包内完整笔记）：按 bili-note 的预算与评分流程写，落 `<arch>\<标识>_学习笔记.md`，并用 `score_bili_note.py` 验收（字数在区间内、证据引用尽量全覆盖）。

产物 B（站点规范版）：见第 4 步。

### 4. 转成站点规范版并本地入库

站点笔记要符合 `learn-notes` 的 AGENT-DOC-SPEC（本技能自带精简版，见 `references/site-note-spec.md`，完整版读 `<repo>\docs\AGENT-DOC-SPEC.md`）。要点：

- 文件头 front-matter：`category`/`category_slug`/`topic`/`topic_slug`/`title`/`slug` 必给，`summary` ≤200 字、`tags` ≤8、`order`、`spec_version: v2`。
- 文件名 `<大类slug>__<小方向slug>__<slug>.md`（双下划线）。
- 正文：恰好一个 `#` 一级标题与 title 一致；无脚注、无引用式链接 `[x][ref]`、无原始 HTML/`<details>`/`<!--注释-->`、无 `---` 分割线与 Setext 标题；段落间空行、单段 40–300 字；代码一律三反引号 + 语言标签；图片只用 https 外链。
- 站点版是**独立可读**笔记：删掉"证据脚注指向本地材料包"这类只在本地成立的表格；把来源写成文首一句话 + bilibili 内联链接即可。
- 站点版 canonical 存 `<repo>\samples\<来源名>\<文件名>.md`（与 `samples\java-onenote-notes` 同级先例一致），作为提交与 git 的同一源文件。

### 5. 提交到网站并验收

用本技能自带脚本（纯标准库，读取站点规范文件）：

```powershell
py "<skill>\scripts\submit_doc.py" --file "<repo>\samples\bilibili\<文件名>.md" --repo "<repo>"
# 可选 --verify 会顺带校验目录树
```

验收标准（不达标就改后重提）：

- `resolvedBy = FRONT_MATTER`（不是 `INBOX`/`FILENAME` 兜底）；
- `warnings = []`；
- 记录 `docId`、`version`、`created`、`category/topic.name`，交给用户核对。

### 6. 交付汇报

汇报清单：站内位置（登录 http://47.99.138.54:8088/catalog 后所在大类/小方向 + docId）、本地归档与 canonical 文件路径、笔记字数/证据覆盖率/score 结果、转写覆盖与局限（尤其 ASR 同音词、无画面内容）。**不要替用户 git commit**——文件就位后问一句是否提交。

## 四、视觉理解扩展位（后续接入）

当前内容路线 = 字幕/音频转写，画面信息（PPT/板书/代码演示/产品界面）未被理解。当 `note_budget.json` 的 `visual_dependency.risk` 为 `medium`/`high`、`needs_visual_review=true` 时：

1. 先补证据：抽取代表性关键帧 → OCR/多模态模型理解，把结论并入笔记"画面证据"小节；再把 `visual_dependency` 状态更新为已补证。
2. 若当前会话没有视觉能力：**明确告诉用户**"画面理解需视觉模型/人工查看关键帧"，只能基于字幕/元数据写有限笔记并标注覆盖范围，绝不把稀疏字幕写成完整课程笔记。

后续加视觉处理时，建议新增 `scripts/vision_*` 与 `references/vision.md`，在第 2 步与第 3 步之间插入"画面证据采集"，流程骨架（六步编号）保持不变，避免重排打乱其它调用。

## 五、经验与坑（从真实运行里沉淀）

- Git Bash 里跑任何脚本用 `py`，裸 `python` 是本机 Store stub。
- 无公开字幕的 30 分钟视频：下载 wav（约 60MB）→ funasr-server 单次 POST 转写服务端需约 10–15 分钟，客户端等得起（设 `FUNASR_SERVER_TIMEOUT`）。
- FunASR Server 一次被长音频堵死后会连短音频一起排队超时；判定"假死"看进程 CPU 与 GPU 利用率都很低，处理=重启服务再跑。
- ASR 转写 JSON 若只有整段 `text` 没有 `body`/`segments`，归档脚本会回退用 txt 分行统计并生成 `P01@L行号-行号` 证据块（技能已兼容，勿手工改回）。
- 归档产出的 `subtitle_evidence_blocks` 为 0 或 `visual_dependency.risk=high` 且原因含 `no_subtitle_text` → 十有八九是第 2 步漏了 run_summary/转写没被归档识别，回到第 2 步补，别直接开写。
- 站点分类是"大类两级可配深度"：新大类会自动放宽层级、可后续在「分类管理」改名；小方向下直接放文档，别在同一目录又建子目录又放文档。
