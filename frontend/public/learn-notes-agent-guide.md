# learn-notes 笔记写作指南（Agent 版）

> 这份文件是**给"负责写学习笔记内容的 agent"看的写作契约**，从 learn-notes 网站的「快速开始」下载。
> 它定义了：笔记文件长什么样、front-matter 怎么填、文件名怎么起、正文怎么写、怎么上传。
> 与仓库 `docs/AGENT-DOC-SPEC.md`（v1，`2026-08-20`）保持一致。
>
> 你的任务：按下面的格式，把用户交给你的学习材料整理成一篇或多篇笔记，并按第 4 节的方法上传。

---

## 0. 一分钟速览

写一篇学习笔记 = 一个 `.md` 文件，由两部分组成：

1. **文件头的 YAML front-matter**：告诉系统这篇文档归到哪个大类 / 哪个小方向（这是自动归类的权威依据）。
2. **正文 Markdown**：**代码必须写在三反引号围栏里并标注语言**，其余按普通 Markdown 写。

````markdown
---
category: Java
category_slug: java
topic: 函数
topic_slug: function
title: Java 方法与函数式接口
slug: java-method-functional-interface
tags: [基础, lambda]
summary: 讲清方法定义、可变参数，以及 Lambda 与函数式接口的关系。
order: 20
---

# Java 方法与函数式接口

方法是一段可复用的、带名字的逻辑单元。

```java
public int add(int a, int b) {
    return a + b;
}
```

## 可变参数

调用时可以传任意个 `int`。
````

三条铁律：

1. **代码只能用三反引号围栏 + 语言标签**，绝不用四空格缩进表示代码。
2. **不用引用式链接、脚注、原始 HTML**（渲染按块进行，跨块引用会失效）。
3. **标题只用 `#` 形式**（ATX），不用 `====` / `----` 下划线式标题。

---

## 1. 文件名约定（front-matter 缺失时的兜底通道）

推荐格式，三段用**双下划线** `__` 分隔：

```text
<大类>__<小方向>__<文档标识>.md
```

示例：

| 文件名 | 解析结果 |
|---|---|
| `java__函数__lambda-basics.md` | 大类 `java` / 小方向 `函数` / slug `lambda-basics` |
| `vue__组件__props-basics.md` | 大类 `vue` / 小方向 `组件` / slug `props-basics` |
| `mysql__索引__b-plus-tree.md` | 大类 `mysql` / 小方向 `索引` / slug `b-plus-tree` |
| `随手记.md` | 无法解析 → 落入 `INBOX / 未归类`，等人工整理 |

规则：

- 段数 = 3 时按上表解析；段数 = 2 时视为 `<大类>__<文档标识>`，小方向取 `未归类`；段数 = 1 时整体进 `INBOX / 未归类`。
- 文件名与 front-matter 同时存在时，**front-matter 优先**。
- 文件名允许中文，但第三段（文档标识）**建议写成纯 ASCII 短横线 slug**，否则系统会按 slug 规则把中文替换掉并追加哈希，URL 会不好看。
- 扩展名只接受 `.md` / `.markdown`。

---

## 2. front-matter 字段表

必须位于文件**最开头**，以 `---` 单独一行开始、`---` 单独一行结束。

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `category` | ✅ | string | 大类显示名，如 `Java`、`Vue`、`MySQL`。不存在则自动创建 |
| `category_slug` | 建议 | string | 大类匹配键，纯小写 ASCII，如 `java`。**强烈建议显式给出**，否则中文大类名会生成带哈希的丑 slug |
| `topic` | ✅ | string | 小方向显示名，如 `函数`、`类`、`集合`、`组件` |
| `topic_slug` | 建议 | string | 小方向匹配键，如 `function`、`class`、`collection`、`component` |
| `title` | ✅ | string | 文档标题；若缺省则取正文第一个 `#` 一级标题 |
| `slug` | 建议 | string | 文档 URL 键，同一小方向内唯一。**这是"同一篇文档的新版本"的判定依据**，重写同一篇时必须保持不变 |
| `tags` | ❌ | string[] | 如 `[基础, lambda]`，最多 8 个 |
| `summary` | ❌ | string | 一句话摘要，≤ 200 字，用于列表页 |
| `order` | ❌ | int | 同小方向内排序，小的在前，缺省 100 |
| `spec_version` | ❌ | string | 本规范版本，建议写 `v1` |

匹配顺序（后端实现）：`category_slug` 精确（忽略大小写）→ `category` 名称精确 → `category` 名称忽略空格与大小写 → 都不中则新建并标记"自动创建"。小方向在其大类下同理。

> **重写已有文档时**：保持 `category_slug` / `topic_slug` / `slug` 三者不变，系统会自动生成新版本、保留历史，并尽力把用户写的"个人见解"重新挂回未改动的段落。**改了 `slug` 就会变成一篇新文档，见解不会跟过来。**

---

## 3. 正文写作规则

### 3.1 代码 —— 最重要的一条

系统靠"块类型"决定渲染样式：代码块走深色底 + 等宽字体 + 语法高亮 + 语言角标 + 复制按钮，正文走普通排版。**能不能被识别成代码，完全取决于你是否用了围栏并标了语言。**

✅ 正确：

````markdown
```java
public class Demo {
    public static void main(String[] args) {
        System.out.println("hi");
    }
}
```

```bash
mvn clean package -DskipTests
```

```sql
SELECT id, title FROM doc WHERE topic_id = 7;
```

```text
Exception in thread "main" java.lang.NullPointerException
```
````

❌ 错误：

````markdown
    // 用四空格缩进表示代码 —— 禁止，可能被当成正文或列表
    int a = 1;

```
没有语言标签 —— 会退化为 text，失去高亮
```
````

- 语言标签常用值：`java`、`javascript`、`typescript`、`vue`、`html`、`css`、`scss`、`sql`、`bash`、`shell`、`yaml`、`json`、`xml`、`properties`、`python`、`go`、`dockerfile`、`diff`、`text`。
- 不确定或纯输出/日志 → 用 `text`。
- **禁止在代码块内再嵌套三反引号**。确实要展示围栏语法时，外层用四个反引号。
- 行内代码用单反引号，只用于类名、方法名、字段名、命令片段，例如 `` `HashMap` ``、`` `mvn -v` ``。**不要用行内代码承载多行代码。**

### 3.2 标题

- 只用 ATX：`#`、`##`、`###`，最多到 `####`。
- 每篇**恰好一个** `#` 一级标题，且应与 `title` 一致，放在正文最前。
- 层级不跳级（`#` → `##` → `###`）。
- 标题里不要放代码围栏、图片和链接。

### 3.3 禁止项（会被后端记为 `warnings`）

| 禁止 | 原因 | 替代写法 |
|---|---|---|
| 引用式链接 `[x][ref]` + `[ref]: https://…` | 渲染按块进行，定义块与引用块分离后链接失效 | 内联写 `[x](https://…)` |
| 脚注 `[^1]` | 同上 | 直接写在正文或用括号补充 |
| 原始 HTML 标签（`<div>`、`<br>`、`<details>`…） | 已关闭 HTML 解析并做净化，标签会被清掉 | 用 Markdown 原生语法；"折叠"由系统的个人见解功能提供，不要自己写 `<details>` |
| 四空格缩进代码块 | 无法识别语言，且与列表缩进冲突 | 三反引号围栏 |
| Setext 标题（`标题` 下一行 `===`/`---`） | `---` 与 front-matter 分隔符混淆 | ATX `#` |
| 正文中的水平分割线 `---` | 同上，且分节意义弱 | 用 `##` 二级标题分节 |
| 本地相对路径图片 `![](./img/a.png)` | 相对路径在站内渲染时找不到文件 | 用 `https://` 外链，或站内路径 `/uploads/YYYY/MM/xxx.png`（见 3.6） |
| 原始 HTML 注释 `<!-- … -->` | 会被解析成一个 HTML 块，**改变块序号并打乱用户的个人见解位置** | 不要写注释；需要说明就写正文 |
| 数学公式 `$$…$$`、Mermaid | v1 不渲染 | 用代码块 `text` 贴出，或改用文字描述 |

### 3.4 段落与列表

- 段落之间必须有**一个空行**（这是块切分的依据；缺空行会让两段并成一块，见解锚点粒度变粗）。
- 一个块尽量表达一个完整意思：**建议单个段落 40–300 字**。太长的段落会让"个人见解"只能挂在一大坨文字上；太碎则块数量膨胀。
- 列表整体算**一个块**（不是每一项一块）。如果某一项特别需要单独批注，把它拆成独立段落。
- 表格用 GFM 语法，表格整体算一个块。
- 引用块 `>` 用于摘录或结论强调，整体算一个块。

### 3.5 建议的文档结构

```markdown
# <标题>

<一段话说明这篇讲什么、解决什么问题>

## 概念

## 语法与示例

## 常见坑

## 小结
```

不强制，但保持结构一致能让笔记更好读，也方便按块加见解。

### 3.6 图片

1. **能不放图就不放图**。学习笔记里的示意图，多数可以用代码块或表格表达得更清楚。
2. 确实要引用网上的图 → 用 `https://` 完整外链：`![B+树结构](https://example.com/b-plus-tree.png)`。
3. 手上有图片二进制、需要放进站内 → 先调上传接口拿路径，再在正文引用：

```bash
curl -X POST http://<host>/api/uploads/image \
  -H "X-Api-Token: $LN_API_TOKEN" \
  -F "file=@b-plus-tree.png"
# → {"url":"/uploads/2026/08/3f7a91c4d5e6b208.png", ...}
```

```markdown
![B+树结构](/uploads/2026/08/3f7a91c4d5e6b208.png)
```

- **绝不要**写相对路径（`./img/x.png`、`../assets/x.png`），站内渲染找不到。
- 图片按内容哈希去重，同一张图重复上传不会产生多份文件。
- 图片单独成段（前后各留一个空行）。
- 如果手上同时有图片二进制、想和 md 一起打包给用户，见 4.4「压缩包一键导入」。

---

## 4. 上传方式

两个接口都需要鉴权头之一：`Authorization: Bearer <jwt>` 或 `X-Api-Token: <token>`（agent 脚本推荐用后者）。

### 4.1 JSON 单篇导入（推荐给 agent）

```bash
curl -X POST http://<host>/api/import/doc \
  -H "Content-Type: application/json" \
  -H "X-Api-Token: $LN_API_TOKEN" \
  -d '{
    "filename": "java__函数__lambda-basics.md",
    "content": "---\ncategory: Java\ncategory_slug: java\ntopic: 函数\ntopic_slug: function\ntitle: Lambda 基础\nslug: lambda-basics\n---\n\n# Lambda 基础\n\n正文…\n",
    "onConflict": "NEW_VERSION"
  }'
```

### 4.2 多文件上传

```bash
curl -X POST http://<host>/api/import/upload \
  -H "X-Api-Token: $LN_API_TOKEN" \
  -F "files=@java__函数__lambda-basics.md" \
  -F "files=@java__类__inner-class.md"
```

限制：单文件 ≤ 2 MB，单次 ≤ 20 个文件。

### 4.3 返回值怎么读

```json
{
  "docId": 12,
  "created": false,
  "version": 2,
  "resolvedBy": "FRONT_MATTER",
  "category": { "name": "Java", "autoCreated": false },
  "topic": { "name": "函数", "autoCreated": true },
  "reanchor": { "active": 3, "stale": 1, "orphan": 0 },
  "warnings": ["代码块缺少语言标签，已按 text 渲染（第 3 块）"]
}
```

- `resolvedBy` = `FRONT_MATTER` 才说明归类走了权威通道；出现 `FILENAME` 说明 front-matter 缺失或不完整；出现 `INBOX` 说明**归类失败了，需要修正后重传**。
- `created: false` + `version: 2` 说明识别为同一篇文档的更新（这通常是期望行为）。
- `warnings` 非空时应当修正文档后重新导入。
- `reanchor.orphan > 0` 说明本次重写把某些见解挂丢了 —— 请在交付说明里提醒用户。

### 4.4 压缩包一键导入（用户侧使用）

如果手上有 md 和图片二进制，可打包成一个 `.zip` 交给用户：在「新建文档」页点「📦 一键导入压缩包」，系统会解压并把正文填进编辑器（源码 + 预览），用户核对后手动保存——**不会像 4.1 / 4.2 那样直接入库**。

打包约定：

- 压缩包内至少一个 `.md`（或 `.markdown`）；**多个 md 时只取路径排序的第一个**，其余忽略。
- 图片（png/jpg/jpeg/gif/webp）在正文中用**相对路径**引用，如 `![示意图](images/a.png)`；路径按 **md 所在目录**解析（允许 `./`，`../` 不得越出压缩包根目录）。导入时自动上传并重写为站内 `/uploads/...` 路径。
- 正文里**没被引用的图片不会导入**；引用了但包内没有的图片会保留原引用并提示。
- front-matter 的 `title` / `slug` / `summary` / `tags` 会被读入新建文档表单（title/slug 直接填入输入框）；`category` / `topic` **不**参与自动归类——大类/小方向仍由用户手动选择。
- 限制：压缩包 ≤ 50MB、单 md ≤ 2MB、单图 ≤ 5MB。

---

## 5. 自查清单（提交前逐条确认）

- [ ] front-matter 在文件最开头，`category` / `topic` / `title` 齐全，并给了 `category_slug` / `topic_slug` / `slug`
- [ ] 重写已有文档时，`slug` 与原文档完全一致
- [ ] 文件名符合 `<大类>__<小方向>__<标识>.md`
- [ ] 恰好一个 `#` 一级标题，层级不跳级
- [ ] 所有代码都在三反引号围栏内，**每个围栏都标了语言**
- [ ] 没有四空格缩进代码块、没有嵌套围栏
- [ ] 没有引用式链接、脚注、原始 HTML、`<details>`、`<!-- 注释 -->`
- [ ] 没有 Setext 标题、正文中没有 `---` 分割线
- [ ] 段落之间有空行，单段长度适中（40–300 字）
- [ ] 图片只用 `https://` 外链或站内 `/uploads/...` 路径，没有相对路径；图片单独成段
- [ ] 没有写数学公式与 Mermaid（v1 不渲染）
- [ ] 导入后 `resolvedBy` 不是 `INBOX`，`warnings` 为空
