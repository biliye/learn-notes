# 个人学习笔记网站（learn-notes）设计规格

- 日期：`2026-08-20`
- 状态：`待用户评审`
- 类型：Design Spec（高复杂度 / 含契约 / 跨模块 / 多 agent 交接）
- 项目根：`F:\deespeekharness\learn-notes`
- 下游文档：`docs/AGENT-DOC-SPEC.md`（写文档的 agent 必读）、`docs/aegis/plans/2026-08-20-learn-notes-implementation.md`（写代码的 agent 必读）

---

## 1. 目的与范围

做一个**自用**的"网站形态的笔记本"：把学习内容按 `大类 → 小方向 → 文档` 三层归置，文档以 Markdown 形式存储与渲染，代码与正文在视觉上明确区分；文档正文主要由 AI agent 批量编写并通过接口上传，上传时能**自动归类**；我可以在文档的任意正文块上写"个人见解"，默认折叠、点击展开。

本规格是需求与契约的唯一权威。实现任务拆解见实施计划文档，本规格不含逐步代码。

### 1.1 需求来源

- 需求来源：用户口述需求（2026-08-20 会话），本文件为其结构化固化版本。
- 已确认的三项用户决策：
  1. 源码与文档落在 `F:\deespeekharness\learn-notes`
  2. 用户模型 = **单用户**（仅管理员一人，登录后可读写；无注册）
  3. 个人见解绑定方式 = **块级稳定锚点（正文块内容哈希 + 序号）**

### 1.2 技术栈（定稿）

| 层 | 选型 | 说明 |
|---|---|---|
| 后端 | Java 17 + Spring Boot 3.2.x + Maven | 不引入 Spring Security 全栈，见 D7 |
| 持久层 | MyBatis（`mybatis-spring-boot-starter` 3.0.x）+ MySQL 8.0 | XML Mapper，不用 MyBatis-Plus |
| 迁移 | Flyway | Docker 部署必需，见 D9 |
| Markdown 解析（后端） | `commonmark-java` 0.22.x | 只做块切分与锚点，不产出 HTML |
| 鉴权 | `jjwt` 0.12.x + `spring-security-crypto`（仅 BCrypt） | 见 D7 |
| 前端 | Vue 3 + Vite + JavaScript + Vue Router + Pinia + Element Plus | 不上 TypeScript，降低 agent 出错率 |
| 前端渲染 | `markdown-it` + `highlight.js` + `dompurify` | 见 D3 / D4 |
| 部署 | Docker + docker-compose（mysql / backend / web-nginx） | 见 D9 |

---

## 2. 需求条目（可验收）

每条需求都必须能在实施计划中指到至少一个任务，并在验收清单中被验证。

### 2.1 分类与目录

| 编号 | 需求 | 验收要点 |
|---|---|---|
| R1 | 两级分类：大类（如 Java、Vue、MySQL）→ 小方向（如 Java 下的"类"、"函数"） | 目录树接口返回两级嵌套；前端左侧树可展开折叠 |
| R2 | 大类与小方向都支持我自己写的**注释/说明**（`remark` 字段），可随时编辑 | 树节点悬浮或详情处显示注释；可编辑保存 |
| R3 | 我可以手动新增 / 重命名 / 调整排序 / 删除大类与小方向 | 非空节点删除返回 409 并提示先迁移文档 |
| R4 | 分类节点展示其下文档数量 | 树接口返回 `docCount` |

### 2.2 文档与渲染

| 编号 | 需求 | 验收要点 |
|---|---|---|
| R5 | 文档以 Markdown 存储，前端渲染 | 数据库存原始 Markdown，不存 HTML |
| R6 | **渲染时能分辨代码与文本，且表现形式明显不同**：代码块为深色底 + 等宽字体 + 语法高亮 + 语言标签 + 复制按钮；正文为常规排版 | 打开任一含代码的文档，代码区与文本区视觉差异明显；复制按钮可用 |
| R7 | 行内代码（`` `x` ``）与代码块样式区分，行内代码不做高亮但有底色 | 视觉检查 |
| R8 | 文档右侧生成标题目录（TOC），点击跳转 | 含多级标题的文档可跳转 |
| R9 | 文档支持按关键字搜索（标题 + 正文），支持按小方向筛选 | 搜索接口返回命中列表 |
| R10 | 每次内容更新产生新版本，可查看历史版本列表与正文 | 版本号递增；旧版正文可读 |
| R11 | 可下载 / 复制文档原始 Markdown | `GET /api/docs/{id}/raw` 返回纯文本 |

### 2.3 上传与自动归类（agent 主要入口）

| 编号 | 需求 | 验收要点 |
|---|---|---|
| R12 | 提供 agent 友好的导入接口：JSON 单篇导入 + multipart 多文件上传 | 两个接口均可用 |
| R13 | **上传后自动挂到对应小方向下**：优先读文档头部 YAML front-matter，其次按文件名约定解析，都失败则进入 `INBOX / 未归类` | 三条路径各有一个通过案例 |
| R14 | 目标大类/小方向不存在时自动创建，并标记 `autoCreated=1` 供我事后整理 | 前端对 `autoCreated` 节点显示标记 |
| R15 | 同一小方向下 slug 重复时，默认视为**同一文档的新版本**（不静默覆盖、不报错），并保留旧版本 | 重复导入同一文件两次，版本号从 1 变 2，见解不丢 |
| R16 | 导入接口可用固定 API Token 调用，不必先走登录，方便 agent 脚本 | `X-Api-Token` 有效；错误 token 返回 401 |
| R17 | 上传的原始 Markdown 除入库外，另落盘一份到 `storage/docs/<category>/<topic>/<slug>.md` 作为备份 | 容器卷内可见对应文件 |

### 2.4 个人见解（可折叠批注）

| 编号 | 需求 | 验收要点 |
|---|---|---|
| R18 | 可对文档中**任一正文块**（段落 / 标题 / 代码块 / 列表 / 表格 / 引用）添加个人见解，见解本身支持 Markdown | 悬浮块出现"+ 见解"入口；添加后立即显示 |
| R19 | 见解默认**折叠**，显示为一条提示条（如 `💡 我的见解 (1)`），点击展开/收起；支持"全部展开 / 全部折叠" | 交互验证 |
| R20 | 同一块可有多条见解，按时间排序；可编辑、删除 | 交互验证 |
| R21 | 见解绑定块级稳定锚点；文档被 agent 重写后，未改动块上的见解**不错位** | 重写文档（改动其中一段）后，其他块见解仍在原位 |
| R22 | 原文改动导致锚点失配时，见解不删除：能迁移的标记 `STALE`（"原文已变更，请确认"），无法迁移的标记 `ORPHAN` 并集中在侧栏"游离见解"，可手动重新挂到某个块 | 制造三种情形各验证一次 |

### 2.5 登录与部署

| 编号 | 需求 | 验收要点 |
|---|---|---|
| R23 | 登录校验：单账号 + BCrypt 密码 + JWT；除白名单外所有 `/api/**` 需要有效 token | 无 token 访问返回 401；前端跳登录页 |
| R24 | 初始账号由环境变量配置，首次启动自动创建；无注册接口 | 改环境变量重建容器可换账号 |
| R25 | token 过期 / 失效时前端自动跳登录页并保留原目标路由 | 手动改坏 token 验证 |
| R26 | 一条 `docker compose up -d` 可在云服务器起全栈（MySQL + 后端 + 前端 Nginx），数据卷持久化 | 干净机器上部署成功 |
| R27 | 数据库结构由 Flyway 自动迁移，不需要手工执行 SQL | 空库首启自动建表 |
| R28 | 敏感配置（DB 密码、JWT secret、管理员密码、API Token）全部走环境变量，仓库只提交 `.env.example` | 仓库中无明文密码 |
| R29 | 源码保存在 `F:\deespeekharness\learn-notes`，本地 git 仓库保有完整内容，每个任务完成后一次提交 | `git log` 可见分任务提交 |

---

## 3. 关键设计决策

> 每条给出：决策、理由、被否方案。实现 agent **不得**私自更改 D1–D9，如需变更须回到本规格评审。

### D1：分类用单表自引用树，固定两级

- **决策**：`catalog_node` 单表，`parent_id`（根用 `0` 而非 `NULL`）+ `node_level`（1=大类，2=小方向）。
- **理由**：两级够用又不封死三级；`parent_id=0` 是为了让 `UNIQUE(parent_id, slug)` 在 MySQL 下真正生效（MySQL 唯一索引把多个 `NULL` 视为互不相同，用 `NULL` 会导致大类 slug 唯一约束失效）。
- **被否**：`category` + `topic` 两张表（改成三级要动表结构）；闭包表 / 路径枚举（当前规模纯属过度设计）。

### D2：不做软删除

- **决策**：分类与文档均物理删除；小方向下有文档、大类下有小方向时禁止删除，返回 409 要求先迁移。
- **理由**：单用户场景，软删会把唯一索引、计数、锚点重挂全部复杂化。
- **被否**：`deleted` 标记位（唯一键需带 `deleted`，重复删除会撞键）。

### D3：块切分与锚点的**唯一权威在后端**，前端按块渲染

- **决策**：后端用 commonmark-java 把 Markdown 解析成 AST，取 **Document 的直接子节点**为"块"，为每块产出 `{index, anchor, type, lang, raw}`；`GET /api/docs/{id}` 返回 `blocks[]`；前端 `v-for` 遍历 blocks，按 `type` 选择不同 Vue 组件渲染（代码块组件 / 正文块组件 / 表格块组件…），每个块 DOM 上带 `data-anchor`。
- **理由**：
  1. 若前后端各写一套切块规则，锚点必然漂移 —— 这是本项目最大的架构风险，必须单一权威。
  2. 按 `type` 分组件渲染，天然满足 R6「代码与文本表现形式不一致」，不需要靠 CSS 猜结构。
  3. 见解挂载点 = 块，天然对齐 R18–R22。
- **被否**：后端返回整篇 HTML（agent 重写后 diff 困难、XSS 面更大、无法逐块挂批注）；前端自行切块（双实现，锚点必漂）。
- **代价与约束**：逐块渲染会切断跨块引用，因此 `AGENT-DOC-SPEC.md` 必须**禁止引用式链接 `[x]: url`、脚注、跨块 HTML 标签**，只允许内联链接。

### D4：Markdown 方言收敛为「CommonMark + 围栏代码块 + 表格」，禁用原始 HTML

- **决策**：后端解析与前端 `markdown-it` 均关闭 `html` 选项；渲染结果再过一遍 DOMPurify。代码块必须使用三反引号围栏并显式标注语言。
- **理由**：内容由 agent 生成，禁 HTML 既防 XSS 又保证块切分稳定；显式语言标签是语法高亮与"语言角标"UI 的输入。
- **被否**：允许内联 HTML（块边界不可控 + 注入风险）。

### D5：锚点算法（精确定义，实现不得改动）

块归一化文本 `norm(block)`：

1. `\r\n` → `\n`；
2. 逐行去掉行尾空白；
3. 去掉首尾空行；
4. **仅对非代码块**：把行内连续空白（空格 / Tab）折叠为单个空格（代码块必须保留缩进，缩进是语义）。

锚点：

```text
hash8  = sha1(norm(block)).hex().toLowerCase().substring(0, 8)
anchor = "b" + index + "-" + hash8      // index 为 0 起的块序号
例：b3-9f2a1c04
```

### D6：见解重挂（re-anchor）算法

文档写入新版本后，对该文档所有见解依次执行：

1. 新块列表中 `hash8` **唯一命中** → 更新 `anchor_index`，`status=ACTIVE`；
2. `hash8` **多个命中**（存在完全相同的块）→ 取与原 `anchor_index` 距离最近者，`status=ACTIVE`；
3. `hash8` 无命中 → 在 `[anchor_index-2, anchor_index+2]` 窗口内计算 trigram Jaccard 相似度，最高者 ≥ `0.6` → 迁移到该块，`status=STALE`；
4. 仍无 → `status=ORPHAN`，保留 `block_snippet`（创建时的块文本快照）供我手动重挂。

前端表现：`ACTIVE` 正常显示；`STALE` 折叠条加 `⚠ 原文已变更，请确认`，确认后置回 `ACTIVE`；`ORPHAN` 不挂在正文，集中在右侧「游离见解」抽屉，可选一个块手动重挂。

### D7：鉴权用 JWT + 自写拦截器，不引入 Spring Security 全栈

- **决策**：`spring-security-crypto` 只用来做 BCrypt；JWT 用 jjwt 签发校验；`HandlerInterceptor` 做校验，白名单 `POST /api/auth/login`、`GET /api/health`；导入接口额外接受 `X-Api-Token`。
- **理由**：单用户单角色，Spring Security 的过滤器链、CSRF、会话管理都是净负担，且是 agent 实现时最容易配错的地方。
- **被否**：Spring Security + OAuth2 Resource Server（复杂度远超需求）。
- **边界**：本决策不追求企业级安全等级；见 §8 风险。

### D8：导入元数据解析优先级 —— front-matter 权威，文件名兜底

- **决策**：`请求显式参数 hint` > `文档 YAML front-matter` > `文件名约定` > `INBOX/未归类`。
- **理由**：front-matter 与内容同源、可版本化、可被 agent 稳定生成，是权威；文件名约定是人手拖文件时的便捷通道，不能作为唯一真相（重命名即丢分类）。
- **被否**：只认文件名（改名即错分类，且中文文件名编码问题多）；只认目录结构（agent 走 HTTP 时没有目录）。
- 完整规则、字段表、示例见 `docs/AGENT-DOC-SPEC.md`（该文件是写文档 agent 的唯一契约）。

### D9：Docker Compose 三服务 + Flyway 迁移

- **决策**：`mysql:8.0`（命名卷 `mysql-data`）、`backend`（多阶段 maven→jre-alpine）、`web`（多阶段 node→nginx，Nginx 反代 `/api` 并对前端路由 `try_files ... /index.html`）。建表只由 Flyway 负责，不用 MySQL 的 `initdb` 脚本。
- **理由**：单机自用最省心；Flyway 保证重建容器、换机器时结构一致，避免"忘了执行 SQL"。
- **被否**：K8s（无谓复杂）；后端内嵌 H2（云端需要持久与备份）。

### D10：搜索用 MySQL LIKE，不引入检索引擎

- **决策**：`title LIKE %q%` OR `content_md LIKE %q%`，限制返回 50 条并给出摘要片段。文档量级预期 < 5000，够用。
- **被否**：Elasticsearch（重）；MySQL 全文索引 + 中文分词（ngram 需调参，收益不明显，留待文档量超 5000 再评估）。

---

## 4. 数据模型

MySQL 8.0，`utf8mb4` / `utf8mb4_0900_ai_ci`，全部表 `InnoDB`。所有 DDL 以 Flyway 迁移文件形式提交（`V1__init.sql`）。

```sql
-- 用户（单用户，仅一行）
CREATE TABLE `sys_user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(64)  NOT NULL,
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  `nickname`      VARCHAR(64)  NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分类树：node_level 1=大类 2=小方向；根节点 parent_id=0
CREATE TABLE `catalog_node` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `node_level`   TINYINT      NOT NULL,
  `name`         VARCHAR(80)  NOT NULL COMMENT '显示名，如 Java / 函数',
  `slug`         VARCHAR(80)  NOT NULL COMMENT '匹配键，小写 ASCII',
  `remark`       VARCHAR(500) NULL COMMENT '我自己写的注释/说明（R2）',
  `icon`         VARCHAR(40)  NULL,
  `sort_order`   INT          NOT NULL DEFAULT 100,
  `auto_created` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '导入自动创建，待整理（R14）',
  `doc_count`    INT          NOT NULL DEFAULT 0 COMMENT '冗余计数，写时维护',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_node_parent_slug` (`parent_id`, `slug`),
  KEY `idx_node_parent` (`parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文档（当前版本正文内联，读路径单表命中）
CREATE TABLE `doc` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `topic_id`        BIGINT UNSIGNED NOT NULL COMMENT 'catalog_node(node_level=2)',
  `slug`            VARCHAR(120) NOT NULL,
  `title`           VARCHAR(200) NOT NULL,
  `summary`         VARCHAR(500) NULL,
  `tags`            VARCHAR(300) NULL COMMENT '逗号分隔',
  `source_filename` VARCHAR(255) NULL,
  `current_version` INT          NOT NULL DEFAULT 1,
  `content_md`      LONGTEXT     NOT NULL,
  `content_hash`    CHAR(40)     NOT NULL COMMENT 'sha1(content_md)，用于跳过无变更写入',
  `block_count`     INT          NOT NULL DEFAULT 0,
  `word_count`      INT          NOT NULL DEFAULT 0,
  `sort_order`      INT          NOT NULL DEFAULT 100,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_topic_slug` (`topic_id`, `slug`),
  KEY `idx_doc_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 历史版本（只追加）
CREATE TABLE `doc_version` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `doc_id`       BIGINT UNSIGNED NOT NULL,
  `version`      INT          NOT NULL,
  `content_md`   LONGTEXT     NOT NULL,
  `content_hash` CHAR(40)     NOT NULL,
  `change_note`  VARCHAR(255) NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ver_doc_version` (`doc_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 个人见解（块级锚点）
CREATE TABLE `doc_annotation` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `doc_id`                BIGINT UNSIGNED NOT NULL,
  `anchor_hash`           CHAR(8)      NOT NULL COMMENT 'D5 hash8',
  `anchor_index`          INT          NOT NULL COMMENT '块序号，0 起',
  `block_snippet`         VARCHAR(300) NOT NULL COMMENT '创建时块文本快照，供 ORPHAN 人工重挂',
  `content_md`            TEXT         NOT NULL COMMENT '见解正文，支持 Markdown',
  `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/STALE/ORPHAN',
  `doc_version_at_create` INT          NOT NULL,
  `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ann_doc_status` (`doc_id`, `status`),
  KEY `idx_ann_doc_anchor` (`doc_id`, `anchor_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**不建 `doc_block` 表**：块可由 `content_md` 随时重算（毫秒级），持久化会引入"正文与块表不一致"的第二真相。

`V2__seed.sql` 只插入一个兜底分类：大类 `INBOX / 收件箱`，小方向 `未归类`（`auto_created=1`），供 R13 兜底使用。管理员账号由后端启动时按环境变量创建（不写进迁移脚本，避免密码进仓库）。

---

## 5. API 契约

统一使用真实 HTTP 状态码 + 统一响应体。前端 axios 拦截器按 `code` 与状态码统一处理。

```json
{ "code": 0, "msg": "ok", "data": { } }
```

- `200` 成功；`400` 参数错误；`401` 未登录/token 失效；`404` 不存在；`409` 冲突（非空删除、slug 冲突且 `onConflict=FAIL`）；`500` 服务端错误。
- 非 0 `code` 用于前端区分业务分支，`msg` 直接可展示给用户。
- 鉴权头：`Authorization: Bearer <jwt>`；导入接口额外接受 `X-Api-Token: <token>`。

### 5.1 认证

| 方法 | 路径 | 请求 | 响应 data |
|---|---|---|---|
| POST | `/api/auth/login` | `{username, password}` | `{token, expiresIn, username, nickname}` |
| GET | `/api/auth/me` | — | `{username, nickname}` |
| GET | `/api/health` | — | `{status:"UP", version}` |

无 logout 接口（前端清 token 即可，YAGNI）。

### 5.2 目录树

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/catalog/tree` | 返回两级嵌套：`[{id,name,slug,remark,icon,sortOrder,docCount,autoCreated,children:[同结构]}]` |
| POST | `/api/catalog` | `{parentId, name, slug?, remark?, icon?, sortOrder?}`；`slug` 缺省按 §6 规则由 `name` 生成 |
| PUT | `/api/catalog/{id}` | `{name?, remark?, icon?, sortOrder?}`（不允许改 `parentId`，移动用下一个接口） |
| PUT | `/api/catalog/{id}/move` | `{parentId, sortOrder}`，仅小方向可移动，且目标必须是大类 |
| DELETE | `/api/catalog/{id}` | 非空返回 `409` |

### 5.3 文档

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/docs` | 查询：`topicId?`, `categoryId?`, `keyword?`, `page=1`, `size=20`；返回分页列表，**不含正文** |
| GET | `/api/docs/{id}` | 详情，见下方结构 |
| GET | `/api/docs/{id}/raw` | `text/markdown` 纯文本 |
| POST | `/api/docs` | `{topicId, title, slug?, summary?, tags?, contentMd}` |
| PUT | `/api/docs/{id}` | `{title?, summary?, tags?, contentMd, changeNote?}`；正文变化则版本 +1 并触发 D6 重挂 |
| PUT | `/api/docs/{id}/move` | `{topicId}` |
| DELETE | `/api/docs/{id}` | 级联删除其版本与见解 |
| GET | `/api/docs/{id}/versions` | `[{version, changeNote, createdAt, wordCount}]` |
| GET | `/api/docs/{id}/versions/{version}` | `{version, contentMd, createdAt}` |
| GET | `/api/search?q=&size=20` | 标题 + 正文 LIKE，返回 `[{docId,title,breadcrumb,snippet}]` |

`GET /api/docs/{id}` 的 `data`：

```json
{
  "id": 12,
  "title": "Java 方法与函数式接口",
  "slug": "java-method-functional-interface",
  "summary": "…",
  "tags": ["基础", "lambda"],
  "currentVersion": 3,
  "updatedAt": "2026-08-20T20:15:00",
  "breadcrumb": [
    { "id": 1, "name": "Java", "slug": "java" },
    { "id": 7, "name": "函数", "slug": "function" }
  ],
  "blocks": [
    { "index": 0, "anchor": "b0-1a2b3c4d", "type": "heading", "level": 1, "lang": null, "raw": "# Java 方法与函数式接口" },
    { "index": 1, "anchor": "b1-77e0aa19", "type": "paragraph", "level": null, "lang": null, "raw": "方法是…" },
    { "index": 2, "anchor": "b2-9f2a1c04", "type": "code", "level": null, "lang": "java", "raw": "```java\npublic int add(int a, int b) {\n    return a + b;\n}\n```" }
  ],
  "annotations": [
    { "id": 5, "anchor": "b2-9f2a1c04", "anchorIndex": 2, "contentMd": "这里注意自动装箱…", "status": "ACTIVE", "blockSnippet": "public int add…", "createdAt": "…", "updatedAt": "…" }
  ]
}
```

- `blocks[].type` 取值：`heading` | `paragraph` | `code` | `list` | `table` | `quote` | `thematic_break` | `html`(禁用后不应出现) | `other`。
- `blocks[].raw` 是**该块的原始 Markdown**（代码块含围栏行），前端逐块渲染。
- `annotations` 内联返回，避免详情页两次请求；`ORPHAN` 状态的也一并返回，前端放进「游离见解」抽屉。

### 5.4 导入（agent 主入口）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/import/doc` | `application/json`：`{filename, content, categoryHint?, topicHint?, onConflict?}` |
| POST | `/api/import/upload` | `multipart/form-data`：`files[]`（`.md`/`.markdown`，单文件 ≤ 2 MB，单次 ≤ 20 个）+ 可选 `categoryHint`/`topicHint` |

`onConflict`：`NEW_VERSION`（默认）| `SKIP` | `FAIL`（409）。

单篇响应 `data`：

```json
{
  "docId": 12,
  "created": false,
  "version": 2,
  "title": "Java 方法与函数式接口",
  "slug": "java-method-functional-interface",
  "category": { "id": 1, "name": "Java", "autoCreated": false },
  "topic": { "id": 7, "name": "函数", "autoCreated": true },
  "resolvedBy": "FRONT_MATTER",
  "storedPath": "storage/docs/java/function/java-method-functional-interface.md",
  "reanchor": { "active": 3, "stale": 1, "orphan": 0 },
  "warnings": ["检测到引用式链接，已忽略：[ref]: https://…"]
}
```

`resolvedBy` 取值：`HINT` | `FRONT_MATTER` | `FILENAME` | `INBOX`。多文件上传返回该对象的数组。

### 5.5 个人见解

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/annotations` | `{docId, anchor, contentMd}`；后端校验 `anchor` 必须存在于当前块列表，否则 `400` |
| PUT | `/api/annotations/{id}` | `{contentMd}` |
| POST | `/api/annotations/{id}/reanchor` | `{anchor}`，手动重挂，`status` 置 `ACTIVE`（R22） |
| POST | `/api/annotations/{id}/confirm` | `STALE` → `ACTIVE`，同时刷新 `block_snippet` |
| DELETE | `/api/annotations/{id}` | — |
| GET | `/api/annotations?status=&page=&size=` | 「我的全部见解」页面用（P2） |

---

## 6. slug 生成规则（统一实现，前后端一致）

`slugify(text)`：

1. 转小写、trim；
2. 保留 `[a-z0-9]`，把 `.` `_` 空格 及其他分隔符转为 `-`；
3. **非 ASCII 字符（中文等）整段替换为 `-`**；
4. 折叠连续 `-`、去首尾 `-`；
5. 若结果为空或只剩 `-`：使用 `doc-` + `sha1(原文).substring(0,8)`（分类节点用 `node-` 前缀）；
6. 截断至 60 字符；
7. 入库前若在同一父节点/小方向下已存在且不是同一实体，追加 `-2`、`-3`…（导入路径按 `onConflict` 处理，不追加后缀）。

例：`Java中的Lambda基础` → `java-lambda`；`函数` → `node-8f14e45f`（因此**建议 front-matter 显式给 `slug` 与分类 `slug`**，见 AGENT-DOC-SPEC）。

---

## 7. 非目标（明确不做）

- 多用户、注册、角色权限、分享链接
- 在线协同编辑、评论区、点赞、收藏夹
- 全文检索引擎（ES）、中文分词
- 图片上传与图床：**当前只支持外链图片**（`![](https://…)`）。本地图片列为 P2
- 数学公式（KaTeX）、Mermaid 图：列为 P2
- SSR / SEO、移动端 App、离线包
- K8s、CI/CD 流水线、HTTPS 证书自动签发（Nginx 配置留注释位）
- 富文本 WYSIWYG 编辑器（前端编辑器为纯 Markdown 文本域 + 实时预览）

---

## 8. 风险与边界

| 风险 | 影响 | 处置 |
|---|---|---|
| 块切分规则若被实现方"顺手优化"，历史见解全部漂移 | 高 | D3/D5 列为不可改动决策；锚点算法必须有单元测试固化（含中文、代码缩进、表格用例） |
| agent 生成的文档不遵守 AGENT-DOC-SPEC（用了引用式链接、无 front-matter、代码块没标语言） | 中 | 导入接口返回 `warnings` 而非拒收；语言缺省按 `text` 渲染；front-matter 缺失走文件名/INBOX |
| 单账号 + 静态 API Token 的安全强度有限 | 中 | 仅自用；Token 走环境变量、只对 `/api/import/**` 生效；登录失败 5 次锁 10 分钟（P1）；部署后建议前置 HTTPS |
| `content_md` 存 LONGTEXT 且列表页误查正文 | 中 | 列表查询 SQL 明确列出字段，禁止 `SELECT *`（写进任务验收） |
| MySQL 中文排序/大小写匹配导致分类重复创建（`Java` vs `java`） | 中 | 匹配一律走小写 `slug`，`name` 仅作显示 |
| Docker 内 MySQL 首启慢导致后端启动失败 | 低 | compose 加 `healthcheck` + 后端 `depends_on: condition: service_healthy`；Flyway 加重试 |

**兼容边界**：`blocks[]` 结构、`anchor` 格式、front-matter 字段名、统一响应体格式一旦实现即为契约，后续变更需回到本规格并给出迁移方案（锚点格式变更必须提供 `doc_annotation` 的重算迁移脚本）。

---

## 9. 验收标准（端到端）

在干净环境执行，全部通过才算完成：

1. `docker compose up -d` 后 `curl http://<host>/api/health` 返回 `UP`。
2. 未带 token 请求 `/api/catalog/tree` 返回 `401`；登录后返回树。
3. 用 `X-Api-Token` 调 `/api/import/doc` 导入一篇带 front-matter 的 `java / 函数` 文档 → `resolvedBy=FRONT_MATTER`，`topic.autoCreated=true`（首次），树上出现该节点且带自动创建标记。
4. 导入一个只有文件名约定的 `vue__组件__props-basics.md` → `resolvedBy=FILENAME` 且归类正确。
5. 导入一个无任何元数据的 `随手记.md` → 落到 `INBOX / 未归类`。
6. 前端打开该文档：代码块为深色底 + 语法高亮 + 语言角标 + 复制按钮；正文为常规排版；行内代码有底色；右侧 TOC 可跳转。
7. 在第 2 个正文块和第 1 个代码块上各加一条见解 → 默认折叠，点击展开正常。
8. 用 `onConflict=NEW_VERSION` 重新导入同一文档，其中**只改动一个段落** → 版本变为 2；未改动块上的见解仍 `ACTIVE` 且位置正确；被改动块的见解为 `STALE` 或 `ORPHAN` 且未丢失。
9. 手动把 `ORPHAN` 见解重挂到某块 → 变为 `ACTIVE`。
10. 删除含文档的小方向 → `409` 且提示先迁移。
11. `storage/docs/java/function/*.md` 在卷内存在且内容与库内一致。
12. 重启 compose（`down` 不带 `-v` 再 `up`）→ 数据、分类、见解全部保留。

---

## 10. 待用户确认（不阻塞任务拆解，但会影响两个任务的实现细节）

| 编号 | 问题 | 我的默认处置 |
|---|---|---|
| Q1 | "每次提交在本地也有相同内容"——指 (a) 每完成一个任务在本地 git 提交一次，本地仓库与交付内容一致；还是 (b) 上传的文档除入库外还在本地/服务器落盘一份？ | **两条都做**：(a) 每任务一次本地提交（R29）；(b) 原文落盘备份（R17）。成本都很低 |
| Q2 | 学习笔记是否需要贴本地图片？ | 当前只支持外链图片，本地图床列 P2。若需要，请说，我把"图片上传 + 静态目录挂载"提为 P1 任务 |
| Q3 | 是否需要数学公式 / Mermaid 流程图渲染？ | 默认不做（P2）。渲染层已按块分组件，后续加 `math`/`mermaid` 块类型不破坏契约 |
| Q4 | 云服务器是否已有 Nginx / 域名 / HTTPS？端口能否用 80？ | 默认 compose 内自带 Nginx 占用 80；若已有宿主 Nginx，则改为暴露 `8088` 由宿主反代（部署文档会同时给两种配置） |

---

## 附录 A：TaskIntentDraft

- **成果**：一套可交给多个 agent 并行执行的需求与契约文档，使实现方无需回问即可动手。
- **成功证据**：本规格 + AGENT-DOC-SPEC + 实施计划三份文档齐备，§9 验收标准可被逐条验证。
- **停止条件**：三份文档写完并通过用户评审；**本轮不写任何应用代码**。
- **非目标**：本轮不实现后端/前端/Docker 任何代码。
- **风险**：需求歧义（Q1–Q4 已显式登记）；锚点契约被下游实现方擅自改动。

## 附录 B：ImpactStatementDraft

- **受影响层**：全新项目，无既有代码，无兼容负担。
- **owner**：后端 = Spring Boot 模块；前端 = Vue SPA；文档写作契约 owner = `docs/AGENT-DOC-SPEC.md`；需求与接口契约 owner = 本文件。
- **不变量**：锚点格式（D5）、`blocks[]` 结构（D3）、front-matter 字段名（D8）、统一响应体。
- **兼容边界**：见 §8 末段。

## 附录 C：Existence Check（新增面）

| 提议新增面 | 复用候选 | 为何不足 | 创建证明 | 决策 |
|---|---|---|---|---|
| 后端块解析 + 锚点服务 | 前端 markdown-it 现成切分 | 前端切分会与后端形成双实现，锚点必漂 | R21/R22 要求跨重写稳定，必须单一权威 | `add-with-proof` |
| `X-Api-Token` 导入通道 | 复用 JWT 登录 | agent 脚本走登录需存账号密码并处理过期 | R16 明确要求 | `add-with-proof` |
| `doc_version` 历史表 | 只存当前正文 | R10 要求历史版本；且 D6 重挂需要可回溯 | R10 | `add-with-proof` |
| `doc_block` 持久化表 | 由 `content_md` 实时重算 | 无不足，重算足够快 | — | `reject`（避免第二真相） |
| MyBatis-Plus / Spring Security 全栈 | 原生 MyBatis + 自写拦截器 | 无不足，需求为单用户简单站 | — | `reject`（YAGNI） |
