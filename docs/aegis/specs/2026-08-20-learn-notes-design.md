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
- 已确认的用户决策：
  1. 源码与文档落在 `F:\deespeekharness\learn-notes`
  2. 用户模型 = **单用户**（仅管理员一人，登录后可读写；无注册）
  3. 个人见解绑定方式 = **块级稳定锚点（正文块内容哈希 + 序号）**
  4. **本地留存的首要目的是灾难恢复**："防止云端挂了没法恢复"。因此备份与恢复不是附属功能，而是一等需求（见 §2.6、D12），且恢复路径必须实测演练
  5. **需要本地图片上传**（不再只支持外链），见 §2.2 R30 与 D11
  6. **不做**数学公式与 Mermaid 渲染
  7. 云服务器上已运行 `astrbot` + `napcat`，端口占用情况未知 → 部署默认**不抢 80 端口**，见 D13

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
| R30 | **本地图片上传**：编辑器内粘贴 / 拖拽 / 选择图片即上传，返回站内路径并自动插入 `![](…)`；阅读页正常显示 | 粘贴截图后 md 中出现 `/uploads/2026/08/<hash>.png` 且图片可见 |

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

### 2.6 备份与恢复（一等需求）

> 用户原话："主要是做备份，防止云端挂了没法恢复"。因此**判定标准不是"备份文件存在"，而是"能在一台干净机器上把内容全部还原"**。

| 编号 | 需求 | 验收要点 |
|---|---|---|
| R31 | **人可读全量导出**：一键导出全站内容为 md 文件树 + 见解旁挂文件 + 图片，打包 zip 下载 | `GET /api/export/all` 返回 zip，解开后目录结构为 `<大类>/<小方向>/<slug>.md` 与同名 `<slug>.insights.json`，另有 `uploads/` 与 `manifest.json` |
| R32 | **导出内容必须包含个人见解**（见解只存在数据库，md 里没有，纯 md 备份会丢） | `.insights.json` 内含 `anchor / anchorIndex / blockSnippet / contentMd / status / createdAt` |
| R33 | **见解可回灌**：提供导入接口，配合文档导入即可从导出物完整重建 | `POST /api/import/insights` 按 anchor 重建；锚点失配走重挂逻辑并报告 stale/orphan 数 |
| R34 | **服务器端定时备份**：每天定时 `mysqldump` + 打包 `storage/`，本地保留最近 14 份，超出自动清理 | 服务器上 `backup/` 目录出现按日期命名的归档；第 15 天最旧一份被删 |
| R35 | **同步回本机**：一条命令把最新导出物拉回本机，其中 **md 与 insights.json 进本地 git 仓库**（文本、可 diff、体积小），数据库 dump 与图片放仓库外目录 | 本机 `notes-export/` 有内容且被 git 跟踪；`learn-notes-backup/` 有 dump 与 uploads 且不在 git 内 |
| R36 | **恢复演练必须实测**：在空库 + 空 storage 的环境里，只用 `notes-export/`（最坏情况：备份盘也没了、只剩 git 仓库）重建全部分类、文档、见解 | 演练结果逐条记录在 `docs/ACCEPTANCE.md`，分类数/文档数/见解数与导出时一致 |
| R37 | 图片必须一并备份与恢复 | 演练后阅读页图片正常显示，无裂图 |


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

### D11：图片按内容哈希落盘，Nginx 直接托管，不建表、不上对象存储

- **决策**：
  - 上传接口 `POST /api/uploads/image`（multipart，单图 ≤ 5 MB，白名单 `png/jpg/jpeg/gif/webp`）。
  - 保存路径 `${app.storageDir}/../uploads/YYYY/MM/<sha256前16位>.<ext>`，同图重复上传直接返回已有路径（天然去重）。
  - 对外 URL 为 `/uploads/YYYY/MM/<hash>.<ext>`，由 **Nginx 直接托管**该目录（`storage` 卷同时以只读方式挂给 web 容器），不经过后端流式转发。
  - **不建 `doc_image` 表**：md 正文里的 `/uploads/...` 路径即引用关系，需要时用正则从 `content_md` 提取。
- **理由**：内容哈希天然去重且幂等（重复上传不产生垃圾）；Nginx 托管静态图性能最好、实现最少；不建表避免"正文引用"与"图片表"两个真相不一致。
- **被否**：存数据库 BLOB（备份膨胀、无法用 CDN）；接对象存储 / OSS（自用规模无必要，且引入密钥管理与外部依赖，违背"云端挂了要能恢复"的自持目标）。
- **代价**：会产生**孤儿图片**（文档删了图还在）。当前**接受**该代价，只在 P2 提供一个扫描脚本（比对 `uploads/` 与全部 `content_md` 的引用），不做自动删除——自动删图的误删风险高于占用磁盘的代价。
- **图片校验**：必须校验真实文件头（magic number）而非只信扩展名；文件名一律由服务端按哈希生成，绝不使用客户端文件名（防路径穿越与脚本上传）。

### D12：三层备份，恢复路径以「人可读 + 进 git」为主

- **决策**：三层，各有明确职责，不可互相替代：

  | 层 | 内容 | 位置 | 是否进 git | 职责 |
  |---|---|---|---|---|
  | L1 人可读导出 | `<大类>/<小方向>/<slug>.md` + `<slug>.insights.json` | 本机 `<仓库>/notes-export/` | ✅ 进 git | **主恢复路径**。最坏情况（云端与备份盘全丢）只要 git 在就能重建全部文字内容与见解 |
  | L2 二进制备份 | `mysqldump` 归档 + `uploads/` 图片 | 服务器 `backup/`，同步到本机 `F:\deespeekharness\learn-notes-backup\` | ❌ 不进 git | **快速恢复路径**。一条命令还原到出事前状态，含版本历史 |
  | L3 上传原文落盘 | 导入时写下的原始 md（含 front-matter） | 服务器 `storage/docs/` | ❌ 不进 git | 导入侧兜底，防"入库成功但内容被后续误改" |

- **理由**：
  1. 见解只存在数据库，**纯 md 备份必然丢见解** → 必须有 `.insights.json` 旁挂（R32）。
  2. 见解**不能内嵌进 md 正文**：Markdown 注释 `<!-- … -->` 会被 CommonMark 解析成一个 `HtmlBlock` 块，直接改变块序号与锚点（违反 D3/D5），所以只能旁挂同名文件。
  3. 文字内容进 git 是成本最低、最抗灾的备份：可 diff、可回溯、体积小，且与用户"每次提交在本地也有相同内容"的诉求天然一致。
  4. 二进制（dump / 图片）不进 git，否则仓库迅速膨胀且无法有效 diff。
- **被否**：只做 mysqldump（不可读、跨 MySQL 大版本迁移有坑、丢了就全丢）；把见解写进 md 正文（破坏锚点契约）；把 dump 提交进 git（仓库膨胀）。
- **硬约束**：`notes-export/` 的恢复能力必须**实测**（R36），未演练过的备份等于没有备份。

### D13：部署默认不抢 80 端口，且绝不对外暴露 MySQL

- **背景**：云服务器已运行 `astrbot` + `napcat`（两者通常自带 WebUI 与 HTTP/WS 端口，且很可能已用 Docker 运行），80/443 与 3306 是否占用未知。
- **决策**：
  1. `WEB_PORT` **默认 `8088`**，不默认占用 80。确认 80 空闲或已有宿主 Nginx 后，再由用户显式改配置或加反代。
  2. **MySQL 服务不做端口映射**（compose 里不写 `ports`），只在内部网络供 backend 访问 —— 既避免与既有 3306 冲突，也避免把数据库暴露到公网（这是自建服务最常见的被拖库原因）。
  3. compose 使用独立项目名与显式容器名前缀 `ln-`（`ln-mysql` / `ln-backend` / `ln-web`），并使用**自有 bridge 网络**，避免与 astrbot/napcat 的容器名或网络冲突。
  4. 卷使用命名卷 `ln-mysql-data`，避免与既有卷同名。
- **理由**：在一台已有服务的机器上部署，冲突风险远大于"少配一个端口"的便利；默认值应当选择最不可能打断既有服务的那个。
- **被否**：默认占 80（可能直接打断 astrbot/napcat 的对外访问）；`network_mode: host`（端口冲突面最大，且失去容器隔离）。


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

`GET /api/docs/{id}` 的 `data`（下面示例用四反引号外层围栏，因为 `raw` 字段里含三反引号）：

````json
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
````

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

### 5.6 图片上传（R30、D11）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/uploads/image` | `multipart/form-data`：`file`；单图 ≤ 5 MB；白名单 `png/jpg/jpeg/gif/webp`；校验文件头 |

响应 `data`：

```json
{ "url": "/uploads/2026/08/3f7a91c4d5e6b208.png", "width": 1280, "height": 720, "bytes": 184320, "dedup": false }
```

`dedup: true` 表示该图内容已存在，直接复用既有路径。静态访问 `GET /uploads/**` 由 Nginx 直接托管，**不经过后端、不需要鉴权**（自用站点，图片路径含哈希，不可枚举）。

### 5.7 导出与恢复导入（R31–R33、D12）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/export/all` | 返回 `application/zip`，文件名 `learn-notes-export-YYYYMMDD-HHmm.zip` |
| POST | `/api/import/insights` | `application/json`：`{docSlug, topicSlug, categorySlug, insights:[…]}`，按 anchor 重建见解 |

导出 zip 结构：

```text
manifest.json                       # 导出时间、计数、spec 版本、各分类 remark
java/
  function/
    lambda-basics.md                # 含 front-matter 的完整原文
    lambda-basics.insights.json     # 该文档的全部见解（含 ORPHAN）
  class/
    inner-class.md
uploads/
  2026/08/3f7a91c4d5e6b208.png
```

`<slug>.insights.json`：

```json
[
  { "anchor": "b2-9f2a1c04", "anchorIndex": 2, "blockSnippet": "public int add…",
    "contentMd": "这里注意自动装箱…", "status": "ACTIVE",
    "docVersionAtCreate": 3, "createdAt": "2026-08-20T20:15:00" }
]
```

`POST /api/import/insights` 行为：目标文档按 `categorySlug/topicSlug/docSlug` 定位（不存在则 404，要求先导入文档）；每条见解按 `anchor` 在当前块列表中查找 —— 命中则原位创建 `ACTIVE`；未命中则走 D6 的相似度重挂，结果为 `STALE` 或 `ORPHAN`；已存在完全相同的 `(anchor, contentMd)` 则跳过（幂等，可重复回灌）。响应返回 `{created, skipped, stale, orphan}`。


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
- 图片：本地上传**已纳入范围**（R30、D11）。不做的是对象存储/OSS/CDN、图片压缩转码、孤儿图自动清理（P2 只给扫描脚本）
- 数学公式（KaTeX）、Mermaid 图：**用户已确认不做**。块渲染架构预留了扩展位（新增 `math`/`mermaid` 块类型不破坏契约），将来要加不必改锚点
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
| **备份看着有、真出事恢复不了**（最危险的一类） | 高 | R36 强制恢复演练：在空环境只用 `notes-export/` 重建并核对分类/文档/见解三项计数；未演练的备份视为不存在 |
| **纯 md 备份丢掉个人见解** | 高 | 见解只在数据库里，因此导出必须带 `.insights.json` 旁挂文件（R32）并有回灌接口（R33） |
| 见解**不能**内嵌进 md 正文来"顺便备份" | 高 | `<!-- … -->` 会被解析成 `HtmlBlock` 从而改变块序号与锚点，直接破坏 D3/D5；只能旁挂 |
| 与既有 astrbot / napcat 争抢端口，打断线上服务 | 中 | D13：`WEB_PORT` 默认 8088、MySQL 不做端口映射、容器名统一 `ln-` 前缀、独立网络与命名卷；部署前先跑端口探测命令 |
| MySQL 端口暴露到公网被拖库 | 高 | D13 硬约束：compose 中 MySQL **不写 `ports`** |
| 上传图片被用来传脚本 / 路径穿越 | 中 | D11：校验 magic number、服务端按哈希重命名、扩展名白名单、大小上限 |
| 孤儿图片长期堆积 | 低 | 已知并接受；P2 提供扫描脚本，不做自动删除（误删风险更高） |

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
13. 在编辑器里粘贴一张截图 → 自动上传并插入 `![](/uploads/…)`；阅读页图片正常显示；同一张图再传一次返回 `dedup:true` 且不新增文件。
14. `GET /api/export/all` 下载 zip → 解开后目录结构、`manifest.json` 计数、`.insights.json` 内容、`uploads/` 图片齐全。
15. **恢复演练（最重要一条）**：在**空库 + 空 storage** 的环境里，只用本机 `notes-export/` 目录（不用任何数据库 dump），按顺序跑批量导入脚本 + 见解回灌 → 分类数、文档数、见解数与导出时**完全一致**，随机抽 3 篇文档核对正文与见解位置，图片显示正常。
16. 服务器定时备份任务跑过一次后，`backup/` 出现当日归档；把系统时间/文件名手工造出 15 份后再跑一次，最旧一份被自动清理。

---

## 10. 问题登记（2026-08-20 已由用户答复）

| 编号 | 问题 | 结论 |
|---|---|---|
| Q1 | 本地留存的目的与形态 | **已确认：目的是灾难恢复**。升级为一等需求 §2.6 与 D12 三层备份；`notes-export/`（md + insights.json）进 git 作为主恢复路径，dump 与图片放仓库外 |
| Q2 | 是否需要本地图片上传 | **已确认：需要**。纳入范围，见 R30 与 D11（哈希落盘 + Nginx 托管 + 不建表） |
| Q3 | 是否需要数学公式 / Mermaid | **已确认：不做**。块渲染架构预留扩展位，将来加不破坏锚点契约 |
| Q4 | 云服务器端口情况 | **部分未知**：用户只知道对外暴露的接口，机器上已跑 `astrbot` + `napcat`。处置见 D13（默认 `WEB_PORT=8088`、MySQL 不映射端口、容器名 `ln-` 前缀、独立网络与命名卷）。**部署前需先执行下面的探测命令确认** |

### Q4 待执行的探测（部署前，在云服务器上跑）

```bash
# 1) 看 80 / 443 / 8088 / 3306 是否已被占用（哪个有输出就说明被占）
sudo ss -ltnp | grep -E ':(80|443|3306|8088)\b'

# 2) 看现有容器占了哪些端口、用了哪些网络与卷名（确认不会撞名）
docker ps --format 'table {{.Names}}\t{{.Ports}}\t{{.Image}}'
docker network ls
docker volume ls

# 3) 看有没有宿主 Nginx / Caddy 在做反代
systemctl is-active nginx 2>/dev/null; systemctl is-active caddy 2>/dev/null
curl -sI http://127.0.0.1 | head -3

# 4) 看云厂商安全组之外，本机防火墙是否放行准备用的端口
sudo iptables -S 2>/dev/null | grep -E '8088|dpt:80' ; sudo ufw status 2>/dev/null
```

判定规则：

- 第 1 条里 **8088 无输出** → 直接用默认 `WEB_PORT=8088`，并在云厂商安全组放行 8088。
- **80 无输出且没有宿主 Nginx** → 可以把 `WEB_PORT` 改成 `80`，访问更省事。
- **80 被占且是宿主 Nginx** → 保持 8088，另在宿主 Nginx 加一段 `server`/`location` 反代到 `127.0.0.1:8088`（`docs/DEPLOY.md` 会给现成配置）。
- **80 被占但是 astrbot/napcat 的容器** → 保持 8088，不要动既有容器。
- 第 2 条如果发现已有容器名以 `ln-` 开头或已有 `ln-mysql-data` 卷 → 回报，改前缀。


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
| `POST /api/uploads/image` + Nginx 静态托管 | 只用外链图片 | 用户明确需要贴本地截图，外链需要额外图床且外链会失效 | R30 | `add-with-proof` |
| `doc_image` 引用关系表 | 从 `content_md` 正则提取 `/uploads/...` | 无不足；建表会与正文形成两个真相，删改文档时需同步维护 | — | `reject` |
| `GET /api/export/all` + `POST /api/import/insights` | 只做 mysqldump | 见解只在数据库，纯 md 备份必丢；dump 不可读且跨版本迁移有坑；用户首要诉求是"云端挂了能恢复" | R31–R33、R36 | `add-with-proof`（并要求恢复演练作为验证信号） |
| 见解内嵌进 md 正文（HTML 注释） | 旁挂 `.insights.json` | `<!-- -->` 会成为 `HtmlBlock` 块，改变块序号与锚点，破坏 D3/D5 | — | `reject` |
| 对象存储 / OSS / CDN | 本地卷 + Nginx | 无不足；引入外部依赖与密钥管理，且与"自持可恢复"目标相悖 | — | `reject` |
