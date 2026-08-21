# learn-notes · 个人学习笔记网站

网站形态的个人笔记本：把学习内容按 **大类 → 小方向 → 文档** 归置，以 Markdown 文档形式展现（代码与正文差异化渲染），文档主要由 AI agent 编写后通过接口上传并**自动归类**，我可以在文档的任意正文块上写**可折叠的个人见解**，支持本地图片上传；新建文档页支持**一键导入 .zip 压缩包**（md + 图片），解析进源码/预览后由我核对保存。单用户登录，Docker 部署到云服务器，**并有经过实测演练的备份与恢复能力**。

> **当前状态：已实现并通过端到端验收（16 条中除需云服务器复核的容器项外全部通过）。**
> 后端（Spring Boot）+ 前端（Vue 3）+ 备份恢复脚本 + 部署文档均已交付；恢复演练已实测（只用 `notes-export/` 在空环境重建成功）。
> 部署步骤见 `docs/DEPLOY.md`，备份与恢复见 `docs/BACKUP.md`，逐条验收记录见 `docs/ACCEPTANCE.md`。

---

## 文档导航

| 文档 | 读者 | 作用 |
|---|---|---|
| `docs/aegis/specs/2026-08-20-learn-notes-design.md` | 所有人 | **唯一权威**：需求条目 R1–R37、关键决策 D1–D13、数据模型、API 契约、16 条验收标准 |
| `docs/aegis/plans/2026-08-20-learn-notes-implementation.md` | 写代码的 agent | 20 张任务卡（依赖图、产出文件、验收命令、边界、可直接粘贴的提示词） |
| `docs/AGENT-DOC-SPEC.md` | **写笔记内容的 agent** | 文档怎么写、front-matter 怎么填、文件名怎么起、图片怎么放、怎么上传 |
| `docs/aegis/BASELINE-GOVERNANCE.md` / `INDEX.md` | 维护者 | Aegis 工作区治理与文档索引 |
| `docs/DEPLOY.md` | 部署者 | 云服务器部署与端口探测（任务 T16 产出） |
| `docs/BACKUP.md` | 我自己 | 备份与恢复的唯一 owner 文档（任务 T20 产出） |
| `docs/ACCEPTANCE.md` | 验收者 | 16 条端到端验收记录，含恢复演练（任务 T17/T20 产出） |

---

## 技术栈

- **后端**：Java 17 + Spring Boot 3.2 + MyBatis + MySQL 8 + Flyway + commonmark-java + JWT
- **前端**：Vue 3 + Vite + Element Plus + markdown-it + highlight.js
- **部署**：Docker Compose（`ln-mysql` / `ln-backend` / `ln-web`），默认端口 `8088`（服务器上已有 astrbot + napcat，不抢 80）

## 规划目录结构

```text
learn-notes/
├── backend/                 # Spring Boot 后端（T01 起）
│   └── src/main/java/com/learnnotes/
│       ├── config/ common/ auth/ catalog/ doc/ search/
│       ├── markdown/        # 块切分 + 锚点（全项目唯一权威）
│       ├── imports/         # front-matter / 文件名 → 自动归类
│       ├── annotation/      # 个人见解 + 锚点重挂
│       ├── uploads/         # 图片哈希落盘（T18）
│       └── export/          # 全量导出 + 见解回灌（T19）
├── frontend/                # Vue 3 SPA（T11 起）
├── docs/                    # 规格、计划、写作规范、部署、备份、验收
├── samples/                 # 示例笔记（T17）
├── scripts/                 # 投稿 / 备份 / 同步 / 恢复脚本（T17、T20）
├── notes-export/            # ★ 笔记全量快照（md + insights.json）——进 git，主恢复路径
├── storage/                 # 服务器侧：导入原文落盘 + 上传图片（不入 git）
├── docker-compose.yml       # T16
└── .env.example             # T16
```

仓库外：`F:\deespeekharness\learn-notes-backup\` 存数据库 dump 与图片归档（二进制，不进 git）。

---

## 四条不可动摇的契约

执行 agent 若需要改动以下任一项，必须**停下回报**，不得自行修改后继续：

1. **锚点格式与算法**（规格 D5）：`anchor = "b" + 块序号 + "-" + sha1(归一化块文本) 前 8 位`。个人见解全靠它在文档被重写后不错位。
2. **块切分只在后端做**（规格 D3）：后端返回 `blocks[]`，前端按 `type` 分组件渲染；前端**不得**自己解析整篇 Markdown 生成锚点。
3. **front-matter 是自动归类的权威**（规格 D8）：优先级 `请求 hint > front-matter > 文件名 > INBOX 兜底`。
4. **导出包结构与 `.insights.json` 字段**（规格 D12、§5.7）：这是恢复路径的输入格式，改了等于让历史备份不可用。同理：**见解绝不能内嵌进 md 正文**——`<!-- -->` 会被解析成一个块并打乱锚点。

---

## 备份怎么工作（三层，各有职责）

| 层 | 内容 | 位置 | 进 git | 用途 |
|---|---|---|---|---|
| L1 | `<大类>/<小方向>/<slug>.md` + `<slug>.insights.json` | `notes-export/` | ✅ | **主恢复路径**。最坏情况只剩本地 git，也能把文字与见解全部还原 |
| L2 | `mysqldump` + `uploads` 归档 | 服务器 `backup/` → 本机 `learn-notes-backup/` | ❌ | 快速还原到出事前状态，含版本历史 |
| L3 | 导入时写下的原始 md | 服务器 `storage/docs/` | ❌ | 导入侧兜底 |

**规则：未演练过的备份等于没有备份。** T20 必须在干净环境里**只用 `notes-export/`** 完成一次还原，并核对分类/小方向/文档/见解/图片五项计数。

---

## 给 agent 分发任务时的开场三句

> ① 只在 `F:\deespeekharness\learn-notes` 目录内改动；
> ② 先读 `docs/aegis/specs/2026-08-20-learn-notes-design.md` 与实施计划里你负责的那张任务卡；
> ③ 不得修改设计规格的 D1–D13、数据库表结构与 API 契约，需要改就停下回报。

## 提交约定

每张任务卡完成并自验通过后，在本地做**一次** git 提交：`feat(T05): Markdown 块切分与锚点服务`（类型用 `feat/fix/chore/docs/test`）。
