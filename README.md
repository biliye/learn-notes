# learn-notes · 个人学习笔记网站

网站形态的个人笔记本：把学习内容按 **大类 → 小方向 → 文档** 归置，以 Markdown 文档形式展现（代码与正文差异化渲染），文档主要由 AI agent 编写后通过接口上传并**自动归类**，我可以在文档的任意正文块上写**可折叠的个人见解**。单用户登录，Docker 部署到云服务器。

> **当前状态：仅完成需求拆解与契约设计，尚无任何应用代码。**
> 下一步是把 `docs/aegis/plans/2026-08-20-learn-notes-implementation.md` 里的 17 张任务卡分发给执行 agent。

---

## 文档导航

| 文档 | 读者 | 作用 |
|---|---|---|
| `docs/aegis/specs/2026-08-20-learn-notes-design.md` | 所有人 | **唯一权威**：需求条目 R1–R29、关键决策 D1–D10、数据模型、API 契约、验收标准 |
| `docs/aegis/plans/2026-08-20-learn-notes-implementation.md` | 写代码的 agent | 17 张任务卡（依赖图、产出文件、验收命令、边界、可直接粘贴的提示词） |
| `docs/AGENT-DOC-SPEC.md` | **写笔记内容的 agent** | 文档怎么写、front-matter 怎么填、文件名怎么起、怎么上传 |
| `docs/aegis/BASELINE-GOVERNANCE.md` / `INDEX.md` | 维护者 | Aegis 工作区治理与文档索引 |
| `docs/DEPLOY.md` | 部署者 | 云服务器部署（由任务 T16 产出） |
| `docs/ACCEPTANCE.md` | 验收者 | 端到端验收记录（由任务 T17 产出） |

---

## 技术栈

- **后端**：Java 17 + Spring Boot 3.2 + MyBatis + MySQL 8 + Flyway + commonmark-java + JWT
- **前端**：Vue 3 + Vite + Element Plus + markdown-it + highlight.js
- **部署**：Docker Compose（mysql / backend / web-nginx）

## 规划目录结构

```text
learn-notes/
├── backend/                 # Spring Boot 后端（T01 起）
│   └── src/main/java/com/learnnotes/
│       ├── config/ common/ auth/ catalog/ doc/
│       ├── markdown/        # 块切分 + 锚点（全项目唯一权威）
│       ├── imports/         # front-matter / 文件名 → 自动归类
│       ├── annotation/      # 个人见解 + 锚点重挂
│       └── search/
├── frontend/                # Vue 3 SPA（T11 起）
├── docs/                    # 规格、计划、写作规范、部署与验收
├── samples/                 # 示例笔记（T17）
├── scripts/                 # 批量投稿脚本（T17）
├── storage/                 # 上传原文落盘备份（不入 git）
├── docker-compose.yml       # T16
└── .env.example             # T16
```

---

## 三条不可动摇的契约

执行 agent 若需要改动以下任一项，必须**停下回报**，不得自行修改后继续：

1. **锚点格式与算法**（规格 D5）：`anchor = "b" + 块序号 + "-" + sha1(归一化块文本) 前 8 位`。个人见解全靠它在文档被重写后不错位。
2. **块切分只在后端做**（规格 D3）：后端返回 `blocks[]`，前端按 `type` 分组件渲染；前端**不得**自己解析整篇 Markdown 生成锚点。
3. **front-matter 是自动归类的权威**（规格 D8）：优先级 `请求 hint > front-matter > 文件名 > INBOX 兜底`。

---

## 给 agent 分发任务时的开场三句

> ① 只在 `F:\deespeekharness\learn-notes` 目录内改动；
> ② 先读 `docs/aegis/specs/2026-08-20-learn-notes-design.md` 与实施计划里你负责的那张任务卡；
> ③ 不得修改设计规格的 D1–D9、数据库表结构与 API 契约，需要改就停下回报。

## 提交约定

每张任务卡完成并自验通过后，在本地做**一次** git 提交：`feat(T05): Markdown 块切分与锚点服务`（类型用 `feat/fix/chore/docs/test`）。
