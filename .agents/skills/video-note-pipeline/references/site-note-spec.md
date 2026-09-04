# learn-notes 站点笔记规范（精简版）

> 本文件是 AGENT-DOC-SPEC 的可执行精简版，供"产物 B：站点规范版笔记"写作时直接对照。
> 完整权威版：`<repo>\docs\AGENT-DOC-SPEC.md`；两者冲突以仓库完整版为准。

## 1. 一份站点笔记 = front-matter + 正文

front-matter 必须在文件最开头，`---` 起止。必填/建议字段：

| 字段 | 必填 | 写法示例 | 说明 |
|---|---|---|---|
| `category` | ✅ | `AI Agent` | 大类显示名 |
| `category_slug` | ✅ | `ai-agent` | 大类匹配键，纯小写 ASCII（中文名会自动带哈希，务必显式给） |
| `topic` | ✅ | `Agent 岗位面试` | 叶目录显示名 |
| `topic_slug` | ✅ | `agent-interview` | 叶目录匹配键 |
| `title` | ✅ | 面试官视角：… | 与正文唯一 `#` 一致 |
| `slug` | ✅ | `interviewer-perspective-agent-intern-hiring` | 文档 URL 键，同目录唯一；重写同一篇必须保持不变 |
| `summary` | 建议 | ≤200 字 | 列表页摘要 |
| `tags` | 建议 | `[Agent, 面试]` | ≤8 个 |
| `order` | 建议 | `10` | 目录内排序，缺省 100 |
| `spec_version` | 建议 | `v2` | |

多级目录可用 `path: [Java, 集合, 类]` + `slugs: [java, collections, class]` 代替 `category/topic`（`path` 优先）。文档只能放在无子目录的叶目录。

## 2. 文件名约定（front-matter 缺失时的兜底）

`<大类slug>__<小方向slug>__<slug>.md`（双下划线，段数可 ≥3 表示多级）。front-matter 与文件名都有时 **front-matter 优先**。

## 3. 正文硬规则（违规会被后端记 warnings，重写前必须清零）

- 恰好一个 `#` 一级标题，且与 `title` 一致；标题只用 ATX（`#`/`##`/`###`/`####`），不跳级。
- **代码一律三反引号围栏 + 语言标签**；禁四空格缩进代码块、禁嵌套围栏。
- 禁引用式链接 `[文字][ref]` + `[ref]: url`；用内联 `[文字](https://…)`。
- 禁脚注 `[^1]`。
- 禁原始 HTML（`<div>`、`<br>`、`<details>`…）与 `<!-- 注释 -->`；折叠请交给系统的"个人见解"。
- 禁 `---` 水平分割线与 Setext 标题（与 front-matter 混淆）。
- 图片只用 `https://` 外链或站内 `/uploads/…`；禁本地相对路径；图片单独成段。
- 数学公式 `$$…$$`、Mermaid 不渲染，用代码块 text 代替或文字描述。
- 段落之间必须空行（块切分依据），单段建议 40–300 字。
- 不用本地材料包专属标记（如 `P01@L…` 证据表）——站点笔记独立可读，来源写文首引用 + 链接。

## 4. 提交流程（对接 skill 的 submit_doc.py）

1. 写/改 canonical md 到 `<repo>\samples\<来源>\<文件名>.md`。
2. `py "<skill>\scripts\submit_doc.py" --file <canonical路径> --repo <repo> [--verify]`
3. 验收：`resolvedBy=FRONT_MATTER`、`warnings=[]`；`INBOX` 或 warnings 非空 → 修正重提。

导入响应字段含义：

- `resolvedBy=FRONT_MATTER`（权威）/ `FILENAME`（front-matter 缺失走了文件名兜底）/ `INBOX`（归类失败，必须修）。
- `created=false + version≥2` 说明识别为已有文档的新版本（`slug` 相同），个人见解会尽力重挂。
- 重写已有文档时 `slug`/`slugs` 必须保持原样，否则会当成新文档。
