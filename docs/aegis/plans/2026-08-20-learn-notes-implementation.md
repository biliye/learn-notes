# learn-notes 实施计划（多 agent 交接版）

- 日期：`2026-08-20`
- 状态：`待用户评审 → 评审通过后可直接分发给执行 agent`
- 上游权威：`docs/aegis/specs/2026-08-20-learn-notes-design.md`（需求 R1–R29、决策 D1–D10、数据模型、API 契约）
- 文档写作契约：`docs/AGENT-DOC-SPEC.md`
- 项目根：`F:\deespeekharness\learn-notes`

---

## 0. 计划头

**Goal**：把设计规格落成一个可在云服务器用 `docker compose up -d` 跑起来的个人学习笔记站：两级分类 + Markdown 文档（代码/正文差异化渲染）+ agent 导入自动归类 + 块级可折叠个人见解 + 单用户登录。

**Architecture**：

```text
Vue3 SPA (Nginx)  ──/api──►  Spring Boot 3 (JWT 拦截器)  ──MyBatis──►  MySQL 8
      │                              │
      │                              ├── markdown 模块：块切分 + 锚点（唯一权威，D3/D5）
      │                              ├── imports 模块：front-matter/文件名 → 自动归类（D8）
      │                              └── annotation 模块：见解 + 重挂（D6）
      └── 按 blocks[].type 分组件渲染：CodeBlock / ProseBlock / TableBlock …
```

**Tech Stack**：Java 17、Spring Boot 3.2.x、MyBatis 3.0.x starter、MySQL 8.0、Flyway、commonmark-java 0.22.x、jjwt 0.12.x、Vue 3 + Vite + JS、Element Plus、markdown-it、highlight.js、dompurify、Docker Compose。

**Baseline / Authority Refs**：本项目为全新仓库，无历史基线。权威只有两份：设计规格（需求与契约）、AGENT-DOC-SPEC（文档写作契约）。任何实现细节冲突以设计规格为准；执行 agent **不得**自行修改 D1–D9。

**Compatibility Boundary**（一旦实现即为契约，改动须回评审）：

1. `anchor` 格式 `b{index}-{hash8}` 与归一化规则（规格 D5）
2. `GET /api/docs/{id}` 的 `blocks[]` / `annotations[]` 结构（规格 D3、§5.3）
3. front-matter 字段名与解析优先级（规格 D8、AGENT-DOC-SPEC §2）
4. 统一响应体 `{code,msg,data}` 与 HTTP 状态码语义（规格 §5）
5. 表结构与 Flyway 版本号（只能追加 `V3__`、`V4__`，不可改已发布的 `V1`/`V2`）

**TDD Route**：

```text
TDD Route:
- Mode: off
- Decision: skipped
- Strict authority: not applicable（用户未要求 test-first，Aegis TDD 模式为 off）
- Test posture: post-change regression（对三个算法型任务强制补单元测试）
- Reason: 大部分任务是 CRUD 与 UI 装配，strict RED/GREEN 收益低；但块切分/锚点、重挂、元数据解析是"错了会静默毁数据"的算法，必须有回归测试锁住行为
- Verification: T05 / T08 / T09 必须交付 JUnit 5 单元测试并通过 `mvn test`；其余任务用接口 curl + 前端手工验收（本文件每张卡都给了命令）
```

**Verification（总）**：设计规格 §9 的 12 条端到端验收，由 T17 统一执行并留证。

---

## 1. 前置检查

**Requirement Ready Check**

```text
Requirement Ready Check:
- Requirement source refs: docs/aegis/specs/2026-08-20-learn-notes-design.md §2（R1–R29）
- Goals and scope refs: 规格 §1、§7（非目标）
- User / scenario refs: 单用户（管理员本人）+ 写文档的 agent（导入接口调用方）
- Requirement item refs: R1–R29 全部带验收要点
- Acceptance / verification criteria refs: 规格 §9（12 条端到端）+ 本文件每张任务卡的验收命令
- Open blocker questions: 无阻塞项。规格 §10 的 Q1–Q4 已给默认处置，不阻塞任务分发
- Decision: ready
```

**Change Necessity**

```text
Change Necessity:
- User-visible need: 需要一个可访问的网站来分类存放、渲染、批注学习笔记，并让 agent 批量投稿
- No-change / non-code option: 用 Obsidian / VS Code + 本地 md 文件夹；或用现成 wiki（Wiki.js、Docusaurus）
- Why code change is necessary: 现成方案无法满足两条硬需求——(a) agent 通过 HTTP 上传后按 front-matter/文件名自动归类并版本化；(b) 见解绑定到"块级稳定锚点"，文档被 agent 整篇重写后仍不错位。这两条都需要自有块解析与重挂逻辑，属于新建项目而非改造既有代码
- Minimum change boundary: 一个新仓库，后端 8 个模块 + 前端 5 个视图 + 一套 compose 编排，不引入 Spring Security 全栈、不引入检索引擎、不做多用户
- Decision: code-change
```

**Existence Check**：见设计规格 附录 C（已逐项裁决，`doc_block` 表与 MyBatis-Plus / Spring Security 全栈均为 `reject`）。

**Plan Pressure Test**

```text
Plan Pressure Test:
- Owner / contract / retirement: 锚点唯一 owner = 后端 markdown 模块；无历史包袱，无需退役轨道
- Architecture integrity / higher-level path: 已用"后端出 blocks、前端按 type 渲染"消掉了前后端双实现的结构性风险；无更高层简化路径
- Verification scope: 算法任务有单测，接口任务有 curl，UI 任务有手工清单，整体有 12 条端到端
- Task executability: 每张卡给了产出文件路径、契约引用、验收命令、边界与可直接粘贴的 agent 提示词
- Pressure result: proceed
```

**Plan-Time Complexity Check**

```text
Complexity Budget:
- Artifact class: 新建项目（无既有文件压力）
- Target files / artifacts: 后端约 45 个文件、前端约 25 个文件
- Current pressure: 无
- Projected post-change pressure: within-budget（单文件预期 < 300 行；MarkdownBlockParser 与 ReanchorService 各自独立成类）
- Budget result: within-budget
- Planned governance: 见解重挂、块解析、元数据解析三者各自独立类 + 独立测试类，禁止塞进 DocService

Plan-Time Complexity Check:
- Target files: DocService / ImportService 是最容易膨胀的两个
- Owner fit: 解析与锚点归 markdown 包，归类归 imports 包，重挂归 annotation 包
- Add-in-place risk: 若把重挂写进 DocService，后续文档功能演进会连坐见解逻辑
- Recommendation: add owner file（三个算法各自独立类，DocService 只做编排）
```

**计划边界说明**：本文件是**任务契约与验收契约**，不是代码清单。每张卡给出精确的文件路径、类名/接口签名、算法定义引用与验收命令；具体实现代码由执行 agent 在卡内边界中完成。这是用户明确要求的交接形态（"只需要拆解需求，交给其他 agent 完成"）。

---

## 2. Execution Readiness View

```text
Execution Readiness View:
- Intent Lock: 实现设计规格 R1–R29，不扩展 §7 非目标
- Scope Fence: 仅在 F:\deespeekharness\learn-notes 下新增文件；不改动该目录之外的任何内容
- Baseline Lock: 设计规格 D1–D10 + §4 数据模型 + §5 API 契约；执行期只读不改
- Approved Behavior: 规格 §2 需求表 + §9 验收标准
- Owner / Contract Constraints: 锚点=后端 markdown 包；归类=imports 包；重挂=annotation 包；前端不得自行切块
- Compatibility Boundary: 见 §0（5 条）
- Retirement Boundary: 无历史owner需退役；`INBOX/未归类` 是长期兜底而非临时路径，不得删除
- Task Batches: 见 §3 依赖图（A 后端 / B 算法 / C 前端 三条并行车道 + 交付批次）
- Test Obligations: T05/T08/T09 必须有 JUnit 单测；其余按卡内验收命令
- Review Gates: 每车道完成后一次评审；T17 端到端为最终门
- Drift / Rewind Rules: 任何需要改动 D1–D9 或表结构的诉求 → 停下回报用户，不得自行改契约后继续
- Evidence Required Before Completion: mvn test 通过截图/输出、12 条端到端逐条结果、docker compose 部署日志
- Advisory Boundary: 方法包执行指导，不构成完成授权
```

---

## 3. 任务依赖图与并行车道

```text
T01 仓库骨架 ──┬─► T02 数据库迁移 ─────────────────┐
               │                                   │
               ├─► T03 后端骨架 ──┬─► T04 鉴权 ─────┤
               │                  ├─► T06 目录树 ───┤
               │                  ├─► T07 文档CRUD ─┼─► T08 导入归类 ─┐
               │                  └─► T10 搜索 ─────┤                 ├─► T16 Docker 部署 ─► T17 端到端验收
               ├─► T05 块解析+锚点（独立算法，可最先并行）──────────┴─► T09 见解+重挂 ─┤
               │                                                                       │
               └─► T11 前端骨架+登录 ──┬─► T12 目录/列表/搜索 ──┐                       │
                                       ├─► T13 文档块渲染 ──────┼─► T14 见解交互 ───────┤
                                       └─► T15 编辑器+分类管理 ─┘                       │
```

**可并行分发**：`T02`、`T05`、`T11` 在 `T01` 完成后即可同时开工，互不冲突（分别落在 `backend/src/main/resources/db`、`backend/.../markdown`、`frontend/`）。
**串行硬约束**：`T03` 之后才能做 T04/T06/T07/T10；`T09` 需要 T05+T07；`T13` 依赖契约（不依赖后端实现，可用契约里的 mock JSON 先做）。

**Git 约定（R29）**：每张卡完成并自验通过后，在本地做**一次**提交，信息格式 `feat(T05): markdown 块切分与锚点服务`（类型用 `feat/fix/chore/docs`）。不要一张卡里提交多次，也不要多张卡合并成一次提交。

---

## 4. 任务卡

> 每张卡的「交给 agent 的提示词」可直接复制粘贴使用；粘贴时请把 `<项目根>` 替换为 `F:\deespeekharness\learn-notes`，并要求对方先读 `docs/aegis/specs/2026-08-20-learn-notes-design.md`。

---

### T01 · 仓库骨架与工程约定

- **依赖**：无 | **可并行**：否（阻塞全部）
- **Why**：确定目录与配置约定，避免后续 agent 各建一套结构。
- **产出文件**：
  - `<根>/.gitignore`（忽略 `target/`、`node_modules/`、`dist/`、`.env`、`storage/`、IDE 目录）
  - `<根>/.gitattributes`（`* text=auto eol=lf`，避免 Windows 开发 + Linux 容器构建之间的 CRLF 噪音；`*.sh eol=lf` 必须，否则容器内脚本报 `bad interpreter`）
  - `<根>/README.md`（项目简介、目录结构、本地启动、部署指引、文档投稿指引，指向 `docs/AGENT-DOC-SPEC.md`）
  - `<根>/.env.example`（键见下）
  - `<根>/backend/pom.xml`（groupId `com.learnnotes`，artifactId `learn-notes-backend`，Java 17，依赖：spring-boot-starter-web、mybatis-spring-boot-starter 3.0.x、mysql-connector-j、flyway-core + flyway-mysql、commonmark 0.22.x + commonmark-ext-gfm-tables + commonmark-ext-yaml-front-matter、jjwt-api/impl/jackson 0.12.x、spring-security-crypto、lombok、spring-boot-starter-test）
  - `<根>/backend/src/main/resources/application.yml`（全部敏感值用 `${ENV:default}` 占位）
  - `<根>/frontend/package.json`、`vite.config.js`（`server.proxy` 把 `/api` 代理到 `http://localhost:8080`）
  - `<根>/storage/.gitkeep`
- **`.env.example` 必含键**：`MYSQL_ROOT_PASSWORD`、`MYSQL_DATABASE=learn_notes`、`APP_DB_URL`、`APP_DB_USERNAME`、`APP_DB_PASSWORD`、`APP_JWT_SECRET`、`APP_JWT_EXPIRE_MINUTES=720`、`APP_ADMIN_USERNAME`、`APP_ADMIN_PASSWORD`、`APP_API_TOKEN`、`APP_STORAGE_DIR=/app/storage/docs`、`WEB_PORT=80`
- **包结构约定（必须遵守）**：`com.learnnotes.{config,common,auth,catalog,doc,markdown,imports,annotation,search}`，每个业务包内分 `controller/service/mapper/entity/dto`。
- **验收**：`cd backend && mvn -q -DskipTests package` 成功；`cd frontend && npm i && npm run build` 成功；`git status` 干净（无被忽略文件误入）。
- **边界**：不写任何业务代码、不建表、不写 Controller。
- **提示词**：
  > 在 `<项目根>` 初始化一个前后端分离项目骨架。先读 `docs/aegis/specs/2026-08-20-learn-notes-design.md` 的技术栈表与 `docs/aegis/plans/2026-08-20-learn-notes-implementation.md` 的 T01。只做骨架与配置，不写业务代码：`.gitignore`、`README.md`、`.env.example`、`backend/pom.xml`（Spring Boot 3.2.x + MyBatis + Flyway + commonmark + jjwt + BCrypt + lombok）、`backend/src/main/resources/application.yml`（敏感值全用环境变量占位）、按约定建空的包目录、`frontend/`（Vite + Vue3 + JS，vite 代理 `/api` 到 8080）。完成后必须让 `mvn -DskipTests package` 与 `npm run build` 都通过，然后本地 git 提交一次，信息 `chore(T01): 项目骨架与工程约定`。

---

### T02 · 数据库迁移（Flyway）

- **依赖**：T01 | **可并行**：与 T03/T05/T11 并行
- **Why**：结构由迁移脚本固化，换机/重建容器不需手工 SQL（R27）。
- **产出文件**：`backend/src/main/resources/db/migration/V1__init.sql`、`V2__seed.sql`
- **实现要点**：DDL **逐字**照抄设计规格 §4，不得改字段名/类型/索引。`V2__seed.sql` 只插入兜底分类：大类 `INBOX`（`slug=inbox`、`node_level=1`、`parent_id=0`、`auto_created=1`）与其子节点 `未归类`（`slug=uncategorized`、`node_level=2`、`auto_created=1`）。**不要**在迁移里插入管理员账号（密码不进仓库，由 T04 启动时创建）。
- **验收**：本地起一个空库，`mvn spring-boot:run` 后 `SHOW TABLES` 出现 `sys_user/catalog_node/doc/doc_version/doc_annotation/flyway_schema_history`；`SELECT * FROM catalog_node` 出现 2 行兜底分类；再次启动不报错（幂等）。
- **边界**：不写 Java 代码；不建 `doc_block` 表（规格已裁决 reject）。
- **提示词**：
  > 为 `<项目根>/backend` 写 Flyway 迁移脚本。DDL 严格照抄 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §4 的全部建表语句（字段名、类型、注释、索引一字不改），放入 `V1__init.sql`；`V2__seed.sql` 只插入兜底分类 INBOX/未归类（`auto_created=1`）。不要插入管理员账号。验证：连空库启动应用后表与种子数据齐全且可重复启动。完成后 git 提交一次 `feat(T02): Flyway 初始化迁移与兜底分类`。

---

### T03 · 后端骨架：统一响应 / 异常 / 配置 / 健康检查

- **依赖**：T01 | **可并行**：与 T02/T05/T11 并行
- **Why**：统一契约外壳，避免每个 Controller 各写一套返回结构。
- **产出文件**：
  - `common/R.java`（`R.ok(data)` / `R.ok()` / `R.fail(code,msg)`，字段严格 `code/msg/data`）
  - `common/ErrorCode.java`、`common/BizException.java`、`common/GlobalExceptionHandler.java`（`@RestControllerAdvice`：`BizException`→对应 HTTP 状态；参数校验→400；未捕获→500 且日志打全栈）
  - `common/SlugUtil.java`（**严格实现规格 §6 的 7 步算法**）
  - `config/AppProperties.java`（`@ConfigurationProperties(prefix="app")`：jwt.secret/jwt.expireMinutes、admin.username/admin.password、apiToken、storageDir）
  - `config/MybatisConfig.java`（`mapper-locations: classpath:mapper/*.xml`、`map-underscore-to-camel-case: true`）
  - `HealthController.java`（`GET /api/health`，免鉴权）
  - `LearnNotesApplication.java`
- **验收**：`curl -i http://localhost:8080/api/health` → 200 且 body 为 `{"code":0,"msg":"ok","data":{"status":"UP","version":"..."}}`；故意抛 `BizException(409,...)` 的临时端点返回 409（验证后删除该临时端点）。`SlugUtil` 至少手工验证 `Java中的Lambda基础`→`java-lambda`、`函数`→`node-` 前缀带哈希。
- **边界**：不写业务表的 Mapper；不引 Spring Security 全栈（只允许 `spring-security-crypto` 做 BCrypt）。
- **提示词**：
  > 为 `<项目根>/backend` 搭后端基础设施（不含业务）：统一响应体 `R{code,msg,data}`、`BizException`+`ErrorCode`+`GlobalExceptionHandler`（业务错误映射真实 HTTP 状态码 400/401/404/409/500）、`AppProperties` 读取 app.* 环境配置、MyBatis 配置（`mapper/*.xml`、下划线转驼峰）、免鉴权的 `GET /api/health`，以及 `SlugUtil.slugify` —— 后者必须严格按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §6 的 7 步规则实现（含非 ASCII 整段替换与空结果哈希兜底）。禁止引入 Spring Security 全栈，只能用 spring-security-crypto 的 BCrypt。验收：health 返回统一结构。完成后 git 提交一次 `feat(T03): 统一响应、全局异常与基础配置`。

---

### T04 · 登录鉴权（JWT + 拦截器 + 初始账号 + API Token）

- **依赖**：T02、T03 | **实现需求**：R23–R25、R28、决策 D7
- **Why**：唯一入口保护；agent 导入需要免登录通道（R16）。
- **产出文件**：`auth/JwtService.java`、`auth/AuthService.java`、`auth/AuthController.java`、`auth/AuthInterceptor.java`、`auth/AdminInitializer.java`（`ApplicationRunner`）、`auth/mapper/SysUserMapper.java` + `resources/mapper/SysUserMapper.xml`、`config/WebMvcConfig.java`
- **实现要点**：
  - `POST /api/auth/login` → 校验 BCrypt → 签发 HS256 JWT（`sub=username`，`exp=now+APP_JWT_EXPIRE_MINUTES`）→ 返回 `{token,expiresIn,username,nickname}`。
  - 拦截器拦 `/api/**`，白名单：`POST /api/auth/login`、`GET /api/health`。取 `Authorization: Bearer`，校验失败返回 401 统一结构。
  - **对 `/api/import/**` 额外放行 `X-Api-Token`**：与 `app.apiToken` 常量时间比较（`MessageDigest.isEqual`），命中则视为已认证；两者都没有才 401。
  - `AdminInitializer`：启动时若 `sys_user` 为空则按 `APP_ADMIN_USERNAME/PASSWORD` 创建（BCrypt）；已存在则不动、不覆盖密码。密码/secret 只从环境读，**不得**出现明文默认值（`app.jwt.secret` 缺失应启动失败并给出清晰报错）。
  - 登录失败计数：内存 `Map<username, {count, lockUntil}>`，连续 5 次失败锁 10 分钟（P1，做在 AuthService 内，不引额外依赖）。
  - `GET /api/auth/me` 返回当前用户。**不做 logout 接口。**
- **验收**：
  ```bash
  curl -i -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"<你的密码>"}'   # 200 + token
  curl -i localhost:8080/api/catalog/tree                                                    # 401
  curl -i localhost:8080/api/catalog/tree -H "Authorization: Bearer $T"                      # 200（T06 完成后）
  curl -i -X POST localhost:8080/api/import/doc -H "X-Api-Token: wrong" -d '{}'              # 401
  ```
  连续 5 次错密码后第 6 次返回"账号已锁定"提示。
- **边界**：不做注册、找回密码、多角色、refresh token。
- **提示词**：
  > 为 `<项目根>/backend` 实现登录鉴权，严格按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` 的 D7、R23–R25、§5.1。要点：BCrypt 校验 + jjwt HS256 签发；`HandlerInterceptor` 拦 `/api/**`，白名单只有 `POST /api/auth/login` 与 `GET /api/health`；`/api/import/**` 额外接受 `X-Api-Token`（常量时间比较）；`ApplicationRunner` 在 `sys_user` 为空时按环境变量创建管理员（已存在则不覆盖）；JWT secret 缺失必须启动失败；登录失败 5 次锁 10 分钟（内存计数）。不要引入 Spring Security 过滤器链，不做注册/logout/refresh。按卡内 curl 验收，完成后 git 提交一次 `feat(T04): JWT 登录鉴权与 API Token 通道`。

---

### T05 · Markdown 块切分与锚点服务【关键算法·可最先并行】

- **依赖**：T01（可与 T02/T03 并行开工，只依赖 pom 里的 commonmark） | **实现需求**：决策 D3、D5
- **Why**：整个"个人见解不错位"和"代码/正文差异化渲染"都建立在这一个类上。它是全项目**唯一**的块切分权威。
- **产出文件**：
  - `markdown/Block.java`（`int index; String anchor; String type; Integer level; String lang; String raw;`）
  - `markdown/MarkdownBlockParser.java`（`List<Block> parse(String markdown)`）
  - `markdown/AnchorUtil.java`（`String normalize(String raw, boolean isCode)`、`String hash8(String normalized)`、`String anchor(int index, String hash8)`）
  - `markdown/FrontMatter.java` + `markdown/FrontMatterParser.java`（`ParsedDoc {Map<String,Object> meta; String body;}`，用 commonmark 的 yaml-front-matter 扩展或自行按首尾 `---` 切分）
  - 测试：`backend/src/test/java/com/learnnotes/markdown/MarkdownBlockParserTest.java`、`AnchorUtilTest.java`
- **实现要点（不可偏离）**：
  - 解析 **front-matter 之后的 body**；块 = commonmark `Document` 的**直接子节点**（列表整体一块、表格整体一块、引用块整体一块）。
  - `type` 映射：`Heading→heading`（带 `level`）、`Paragraph→paragraph`、`FencedCodeBlock→code`（`lang` 取 info 字符串首个 token，空则 `text`）、`IndentedCodeBlock→code`（`lang=text`，同时产出 warning）、`BulletList/OrderedList→list`、`TableBlock→table`、`BlockQuote→quote`、`ThematicBreak→thematic_break`、`HtmlBlock→html`、其他 `other`。
  - `raw` 必须是该块的**原始 Markdown 片段**（代码块含前后围栏行）。实现方式：按 commonmark 的 `SourceSpans`（`IncludeSourceSpans.BLOCKS`）从原文切片，**不要**用 renderer 反向生成，反向生成会改写原文导致哈希不稳。
  - 归一化与哈希严格按规格 D5（代码块保留缩进、非代码块折叠行内空白；sha1 hex 小写取前 8 位；anchor = `"b"+index+"-"+hash8`）。
  - 解析结果需附带 `warnings`：缩进式代码块、代码块缺语言、引用式链接定义（正则 `^\[[^\]]+\]:\s*\S+`）、脚注、HtmlBlock。
- **必须覆盖的测试用例**：纯中文段落；含中文的代码块；代码块内含缩进（缩进不得被折叠掉）；同文档两个内容完全相同的段落（hash 相同、anchor 因 index 不同而不同）；GFM 表格；有序/无序列表；带 front-matter 与不带 front-matter；CRLF 换行输入；空文档；只有一个 H1 的文档。
- **验收**：`cd backend && mvn test -Dtest=MarkdownBlockParserTest,AnchorUtilTest` 全绿；并在测试中断言"同一段文本在 CRLF 与 LF 两种输入下 hash8 相同"。
- **边界**：这个包**不碰数据库、不碰 Spring 容器**（纯静态/无状态工具，便于单测）；不产出 HTML。
- **提示词**：
  > 在 `<项目根>/backend` 实现 Markdown 块切分与锚点服务（这是全项目最关键的算法，前端不会再做一套）。严格按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` 的 D3 与 D5：用 commonmark-java 解析，块=Document 直接子节点，`raw` 必须用 `IncludeSourceSpans.BLOCKS` 从原文切片（禁止用 renderer 反向生成），`type/level/lang` 按 T05 卡的映射表，归一化规则里代码块必须保留缩进而非代码块折叠行内空白，`anchor = "b"+index+"-"+sha1(norm).hex().toLowerCase().substring(0,8)`。同时实现 front-matter 解析与 warnings 收集（缩进式代码块、代码块缺语言、引用式链接定义、脚注、HTML 块）。该包不得依赖数据库或 Spring 容器。必须交付 JUnit 5 测试覆盖 T05 卡列出的全部用例（含中文、代码缩进、重复段落、CRLF 与 LF 哈希一致），`mvn test` 全绿后 git 提交一次 `feat(T05): Markdown 块切分与锚点服务`。

---

### T06 · 目录树 CRUD（大类 / 小方向 / 注释）

- **依赖**：T02、T03、T04 | **实现需求**：R1–R4、决策 D1、D2
- **产出文件**：`catalog/entity/CatalogNode.java`、`catalog/dto/*`、`catalog/mapper/CatalogNodeMapper.java` + `resources/mapper/CatalogNodeMapper.xml`、`catalog/service/CatalogService.java`、`catalog/controller/CatalogController.java`
- **实现要点**：
  - 接口严格按规格 §5.2（含 `PUT /api/catalog/{id}/move`）。
  - `GET /api/catalog/tree`：一次查全表（量小）在内存组树，按 `sort_order,id` 排序，返回 `docCount`、`autoCreated`、`remark`。
  - 新建时 `slug` 缺省由 `SlugUtil.slugify(name)` 生成；`UNIQUE(parent_id, slug)` 冲突时追加 `-2`、`-3`。
  - 校验：`parentId=0` 只能建大类（`node_level=1`）；`parentId!=0` 的父必须是大类且 `node_level=2`；不允许三级。
  - 删除：大类下有子节点 → 409；小方向下 `doc_count>0` → 409，`msg` 明确写"请先迁移该方向下的 N 篇文档"。
  - `doc_count` 由 T07/T08 在增删移文档时维护；本卡提供 `incrDocCount(id, delta)` 供其调用。
  - `INBOX`/`未归类` 节点禁止删除与改名（返回 400），它是长期兜底路径。
- **验收**：
  ```bash
  curl -X POST localhost:8080/api/catalog -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"parentId":0,"name":"Java","remark":"后端主语言"}'
  curl -X POST localhost:8080/api/catalog -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"parentId":<javaId>,"name":"函数","remark":"方法/Lambda/函数式接口"}'
  curl localhost:8080/api/catalog/tree -H "Authorization: Bearer $T"     # 两级嵌套 + remark + docCount=0
  curl -i -X DELETE localhost:8080/api/catalog/<javaId> -H "Authorization: Bearer $T"   # 409（有子节点）
  ```
- **边界**：不做三级分类、不做拖拽排序接口（`sortOrder` 由前端传数字即可）、不做软删除。
- **提示词**：
  > 在 `<项目根>/backend` 实现分类目录树 CRUD，接口严格按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §5.2、表结构按 §4 的 `catalog_node`、决策遵守 D1（单表自引用、根 `parent_id=0`、固定两级）与 D2（不做软删除，非空删除返回 409）。要点：tree 接口内存组树并返回 `remark/docCount/autoCreated`；slug 缺省用 `SlugUtil` 生成并处理唯一冲突；禁止三级；`INBOX`/`未归类` 不可删改；对外暴露 `incrDocCount(id,delta)` 给文档模块调用。按卡内 curl 验收，完成后 git 提交一次 `feat(T06): 分类目录树 CRUD`。

---

### T07 · 文档 CRUD + 版本 + 详情装配

- **依赖**：T02、T03、T04、T05、T06 | **实现需求**：R5、R10、R11、§5.3
- **产出文件**：`doc/entity/{Doc,DocVersion}.java`、`doc/dto/*`、`doc/mapper/{DocMapper,DocVersionMapper}.java` + 对应 XML、`doc/service/DocService.java`、`doc/controller/DocController.java`
- **实现要点**：
  - 接口严格按规格 §5.3；详情响应结构逐字段对齐 §5.3 的 JSON 示例（`breadcrumb`、`blocks`、`annotations` 都在同一个响应里；本卡先返回空 `annotations`，T09 接上）。
  - `blocks` 由 `MarkdownBlockParser` 现算，**不落库**。
  - 保存：算 `content_hash`；与当前 `content_hash` 相同则**不产生新版本**（直接返回当前版本，避免 agent 重复导入刷版本号）；不同则 `current_version+1`、写 `doc_version`（存**新**正文）、更新 `doc.content_md`。
  - `word_count` 统计规则：去掉代码块后按"中文字符数 + 英文单词数"计。
  - 列表查询 SQL **必须显式列出字段**，禁止 `SELECT *`，禁止带出 `content_md`（规格 §8 风险项）。
  - 移动文档需同步维护两个 topic 的 `doc_count`；新增/删除同理。
  - `GET /api/docs/{id}/raw` 返回 `text/markdown;charset=UTF-8` 纯文本（非统一响应体，这是唯一例外，需在代码注释里说明）。
  - 删除文档级联删 `doc_version` 与 `doc_annotation`（应用层显式删，不依赖外键级联）。
- **验收**（下面示例用四反引号外层围栏，因为 JSON 里含三反引号代码块）：
  ````bash
  curl -X POST localhost:8080/api/docs -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
    -d '{"topicId":<funcId>,"title":"Java 方法基础","contentMd":"# Java 方法基础\n\n方法是…\n\n```java\nint add(int a,int b){return a+b;}\n```\n"}'
  curl localhost:8080/api/docs/1 -H "Authorization: Bearer $T"   # blocks 含 heading/paragraph/code 三块，code 块 lang=java，anchor 形如 b2-xxxxxxxx
  curl -X PUT localhost:8080/api/docs/1 -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"contentMd":"<改一段>","changeNote":"补充说明"}'
  curl localhost:8080/api/docs/1/versions -H "Authorization: Bearer $T"   # 两条版本
  curl -X PUT localhost:8080/api/docs/1 ... -d '{"contentMd":"<与上一次完全相同>"}'   # 版本号不变
  ````
- **边界**：不做导入解析（T08）、不做见解（T09）、不做搜索（T10）、不做版本回滚（P2）。
- **提示词**：
  > 在 `<项目根>/backend` 实现文档 CRUD 与版本管理，接口与详情 JSON 结构严格按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §5.3（`blocks` 用 T05 的 `MarkdownBlockParser` 现算、不落库；`annotations` 字段先返回空数组，后续任务接入）。要点：`content_hash` 相同则不产生新版本；`doc_version` 存新正文；列表查询必须显式列字段且不带 `content_md`；增删移文档时维护 `catalog_node.doc_count`；`/raw` 返回纯 markdown 文本（统一响应体的唯一例外，加注释说明）；删除文档时应用层级联删版本与见解。按卡内 curl 验收，完成后 git 提交一次 `feat(T07): 文档 CRUD 与版本管理`。

---

### T08 · 导入与自动归类（agent 主入口）

- **依赖**：T05、T06、T07 | **实现需求**：R12–R17、决策 D8
- **Why**：这是用户"上传后自动拼接到某个学习方向下面"的直接实现，也是写文档 agent 的唯一入口。
- **产出文件**：`imports/controller/ImportController.java`、`imports/service/ImportService.java`、`imports/MetaResolver.java`（front-matter + 文件名 + hint 的优先级裁决）、`imports/DocStorage.java`（落盘）、`imports/dto/ImportResult.java`、测试 `imports/MetaResolverTest.java`
- **实现要点**：
  - 接口按规格 §5.4；`resolvedBy` ∈ `HINT|FRONT_MATTER|FILENAME|INBOX`，优先级严格：hint > front-matter > 文件名 > INBOX。
  - 文件名解析规则见 `docs/AGENT-DOC-SPEC.md` §1（`__` 三段；两段=大类+文档；一段=INBOX）。
  - 分类匹配：`slug` 精确（小写）→ `name` 精确 → `name` 去空格并小写 → 未命中则创建并置 `auto_created=1`（R14）。
  - slug 冲突：默认 `NEW_VERSION`（走 T07 的更新路径，触发 T09 重挂）；`SKIP` 直接返回现状；`FAIL` 返回 409。
  - `title` 缺省取正文第一个 H1；`slug` 缺省由 `SlugUtil.slugify(title)` 生成。
  - 落盘（R17）：写 `${app.storageDir}/<category_slug>/<topic_slug>/<doc_slug>.md`，内容为**原始上传内容**（含 front-matter），UTF-8，覆盖写；路径要做穿越校验（拒绝含 `..`、绝对路径、分隔符的 slug）。落盘失败**不回滚入库**，但要在 `warnings` 里报告。
  - `warnings` 汇总 T05 的解析警告 + 本卡的归类警告（如"front-matter 缺少 topic，已回退文件名解析"）。
  - multipart：只接受 `.md`/`.markdown`，单文件 ≤ 2 MB，单次 ≤ 20 个；逐个处理，单个失败不影响其他，返回结果数组（失败项带 `error` 字段）。
  - 整个导入在一个事务里（落盘除外，落盘放事务提交后）。
- **必须覆盖的测试**：完整 front-matter；front-matter 只有 `category`/`topic` 没 slug（中文名匹配与自动创建）；无 front-matter 但文件名三段；文件名两段；文件名一段（进 INBOX）；hint 覆盖 front-matter；同 slug 二次导入产生 v2；slug 含 `../` 被拒。
- **验收**：
  ```bash
  # 1) front-matter 通道
  curl -X POST localhost:8080/api/import/doc -H "X-Api-Token: $TOKEN" -H 'Content-Type: application/json' \
    -d '{"filename":"x.md","content":"---\ncategory: Java\ncategory_slug: java\ntopic: 函数\ntopic_slug: function\ntitle: Lambda 基础\nslug: lambda-basics\n---\n\n# Lambda 基础\n\n正文\n"}'
  # → resolvedBy=FRONT_MATTER, topic.autoCreated=true(首次), storedPath 有值
  # 2) 文件名通道
  curl -X POST localhost:8080/api/import/upload -H "X-Api-Token: $TOKEN" -F "files=@vue__组件__props-basics.md"   # resolvedBy=FILENAME
  # 3) 兜底
  curl -X POST localhost:8080/api/import/upload -H "X-Api-Token: $TOKEN" -F "files=@随手记.md"                     # resolvedBy=INBOX
  ls storage/docs/java/function/lambda-basics.md
  ```
- **边界**：不做 zip 解包、不做目录树上传、不做图片抽取、不做文档正文改写（原文入库，一字不改）。
- **提示词**：
  > 在 `<项目根>/backend` 实现文档导入与自动归类。接口按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §5.4，解析规则按 D8 与 `docs/AGENT-DOC-SPEC.md` §1/§2：优先级 hint > front-matter > 文件名(`大类__小方向__标识.md`) > INBOX 兜底；分类按 slug→name→归一化 name 匹配，未命中则创建并标记 `auto_created=1`；slug 冲突默认走"同文档新版本"（复用 T07 更新路径）；原文另落盘到 `${app.storageDir}/<category_slug>/<topic_slug>/<slug>.md` 并做路径穿越校验（落盘在事务提交后，失败只报 warning）；返回 `resolvedBy/created/version/reanchor/warnings/storedPath`。原文一字不改入库。必须交付 `MetaResolverTest` 覆盖 T08 卡列出的 8 个用例，`mvn test` 全绿。按卡内 curl 验收，完成后 git 提交一次 `feat(T08): 文档导入与自动归类`。

---

### T09 · 个人见解 + 锚点重挂

- **依赖**：T05、T07 | **实现需求**：R18–R22、决策 D5、D6
- **产出文件**：`annotation/entity/DocAnnotation.java`、`annotation/mapper/DocAnnotationMapper.java` + XML、`annotation/service/AnnotationService.java`、`annotation/ReanchorService.java`、`annotation/controller/AnnotationController.java`、测试 `annotation/ReanchorServiceTest.java`
- **实现要点**：
  - 接口按规格 §5.5。创建时：校验 `anchor` 存在于当前块列表（否则 400）；从 anchor 反解 `anchor_index`/`anchor_hash`；`block_snippet` 取该块归一化文本前 300 字符；`doc_version_at_create` 记当前版本。
  - `ReanchorService.reanchor(docId, oldBlocks, newBlocks)` **严格实现规格 D6 的四步**：唯一 hash 命中→ACTIVE；多命中→取 index 最近；无命中→窗口 `[i-2, i+2]` 内 trigram Jaccard ≥ 0.6 → 迁移并置 STALE；否则 ORPHAN。
  - trigram Jaccard：对归一化文本取长度 3 的字符 n-gram 集合，`|A∩B| / |A∪B|`；文本长度 < 3 时退化为字符串相等比较。
  - 由 `DocService` 在正文变更时调用（T07 需回来接一行编排调用；由本卡负责改 T07 的 update 路径并说明）。
  - 返回 `reanchor {active, stale, orphan}` 统计，供 T08 的导入响应使用。
  - `T07` 详情接口的 `annotations` 字段由本卡填满：返回该文档所有见解（含 ORPHAN），按 `anchor_index, created_at` 排序。
- **必须覆盖的测试**：改动一个中间段落（其他块见解全部 ACTIVE 且 index 正确）；在文档开头插入一段（全部见解 index +1 且仍 ACTIVE）；小改被批注的那段（STALE）；整段替换被批注的段落（ORPHAN）；删除被批注的段落（ORPHAN）；文档内有两个相同段落时的最近匹配；代码块缩进改动导致 hash 变化（应为 STALE 或 ORPHAN，绝不静默丢弃）。
- **验收**：
  ```bash
  curl -X POST localhost:8080/api/annotations -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
    -d '{"docId":1,"anchor":"b1-xxxxxxxx","contentMd":"这里注意自动装箱"}'
  curl -X PUT localhost:8080/api/docs/1 ... # 只改另一个段落
  curl localhost:8080/api/docs/1 -H "Authorization: Bearer $T"   # 见解仍 ACTIVE 且 anchorIndex 正确
  curl -X POST localhost:8080/api/annotations/1/reanchor -H "Authorization: Bearer $T" -d '{"anchor":"b3-yyyyyyyy"}'   # ORPHAN→ACTIVE
  ```
  `mvn test -Dtest=ReanchorServiceTest` 全绿。
- **边界**：见解不做版本历史、不做多人可见性、不做 @提醒；**任何情况下不得自动删除见解**（丢不掉是硬要求）。
- **提示词**：
  > 在 `<项目根>/backend` 实现个人见解与锚点重挂。接口按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §5.5，算法严格按 D5/D6：创建时校验 anchor 属于当前块列表并快照 `block_snippet`；重挂四步为 唯一hash命中→ACTIVE、多命中→取 index 最近、无命中→窗口 `[i-2,i+2]` 内 trigram Jaccard ≥0.6 迁移并置 STALE、否则 ORPHAN。把重挂接进 `DocService` 的正文更新路径，并把统计 `{active,stale,orphan}` 回传给导入接口；同时补全文档详情接口的 `annotations` 字段（含 ORPHAN）。硬要求：任何情况下都不能自动删除见解。必须交付 `ReanchorServiceTest` 覆盖 T09 卡列出的 7 个用例，`mvn test` 全绿。完成后 git 提交一次 `feat(T09): 个人见解与锚点重挂`。

---

### T10 · 搜索

- **依赖**：T07 | **实现需求**：R9、决策 D10
- **产出文件**：`search/SearchController.java`、`search/SearchService.java`，`DocMapper.xml` 增查询
- **实现要点**：`GET /api/search?q=&size=20`；`title LIKE %q%` 或 `content_md LIKE %q%`；对 `q` 做 `%`/`_`/`\` 转义；返回 `[{docId,title,breadcrumb,snippet}]`，`snippet` 取首个命中位置前后各 60 字并把命中词包在 `**…**` 里；`q` 长度 < 1 返回 400。文档列表接口的 `keyword` 复用同一 SQL 片段。
- **验收**：`curl "localhost:8080/api/search?q=Lambda" -H "Authorization: Bearer $T"` 返回命中并含 snippet；搜 `%` 不报错也不全量返回。
- **边界**：不做分词、不做 ES、不做高亮 HTML（只给 `**`标记，前端自行处理）。
- **提示词**：
  > 在 `<项目根>/backend` 实现搜索接口，按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §5.3 的 `/api/search` 与决策 D10：MySQL LIKE 双字段匹配、对 q 做通配符转义、返回 docId/title/breadcrumb/snippet（命中词用 `**` 包裹，上下文各 60 字），限 50 条。不引入检索引擎与分词。完成后 git 提交一次 `feat(T10): 关键字搜索`。

---

### T11 · 前端骨架 + 登录 + 路由守卫

- **依赖**：T01（不依赖后端实现，按契约开发） | **实现需求**：R23、R25
- **产出文件**：`frontend/src/main.js`、`router/index.js`、`stores/auth.js`、`api/http.js`、`api/auth.js`、`views/LoginView.vue`、`layouts/MainLayout.vue`、`styles/index.scss`
- **实现要点**：
  - `api/http.js`：axios 实例，`baseURL='/api'`；请求拦截加 `Authorization: Bearer`；响应拦截统一拆 `{code,msg,data}` 返回 `data`，非 0 或 4xx/5xx 弹 `ElMessage.error(msg)` 并 reject；**401 → 清 token + 跳 `/login?redirect=<当前路由>`**（R25）。
  - `stores/auth.js`（Pinia）：token 存 `localStorage`，含 `login/logout/isLogin`。
  - 路由：`/login`（免登录）、`/`（重定向到 `/docs`）、`/docs`（首页/列表）、`/docs/:id`（阅读页）、`/docs/:id/edit`、`/catalog`（分类管理）、`/inbox`。全局前置守卫：无 token 且非 `/login` → 跳登录并带 `redirect`。
  - `MainLayout`：左侧目录树容器 + 顶栏（搜索框、当前用户、退出）+ 内容区。
  - 全局样式定义 CSS 变量：正文字号/行高/最大宽度（建议 `--doc-max-width: 860px`）、代码块配色变量（供 T13 用）。
- **验收**：`npm run dev` 后未登录访问 `/docs` 跳 `/login?redirect=/docs`；登录成功回跳 `/docs`；手动把 localStorage 的 token 改坏后刷新，任一接口 401 → 自动回登录页。
- **边界**：不实现文档渲染（T13）、不实现见解（T14）；后端未就绪时可用契约里的 JSON 做本地 mock，但**不得**把 mock 留在提交里。
- **提示词**：
  > 在 `<项目根>/frontend` 搭 Vue3 + Vite + JS + Element Plus + Pinia + Vue Router 骨架并实现登录。按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §5（统一响应体 `{code,msg,data}`、真实 HTTP 状态码）与 R23/R25：axios 实例 baseURL `/api`、请求拦截加 Bearer token、响应拦截统一拆 data 并对错误弹提示、401 清 token 并跳 `/login?redirect=当前路由`；Pinia 存 token（localStorage）；路由含 login/docs/docs-:id/docs-:id-edit/catalog/inbox 与全局登录守卫；`MainLayout` 含左树容器+顶栏搜索+退出；全局 SCSS 定义正文与代码块用的 CSS 变量。不要实现文档渲染和见解功能，不要把 mock 数据留在提交里。完成后 git 提交一次 `feat(T11): 前端骨架与登录鉴权`。

---

### T12 · 目录树 + 文档列表 + 搜索（前端）

- **依赖**：T11 | **实现需求**：R1–R4、R9
- **产出文件**：`components/CatalogTree.vue`、`components/DocList.vue`、`views/DocsHomeView.vue`、`views/InboxView.vue`、`api/catalog.js`、`api/doc.js`、`stores/catalog.js`
- **实现要点**：`el-tree` 展示两级；节点后缀显示 `docCount`；`autoCreated` 节点显示橙色小圆点 + tooltip"导入时自动创建，待整理"；节点 tooltip/详情显示 `remark`；点击小方向 → 右侧文档列表（分页）；顶栏搜索 → 结果列表（显示 breadcrumb 与 snippet，`**x**` 渲染为高亮 span）；`InboxView` 单独展示 `INBOX/未归类` 下的文档，提供"移动到…"操作。
- **验收**：树两级正确、计数正确；点击方向切换列表；搜索命中高亮；INBOX 页可把文档移动到指定小方向且两侧计数同步刷新。
- **边界**：不做拖拽排序（用"编辑"弹窗改 `sortOrder` 数字）。
- **提示词**：
  > 在 `<项目根>/frontend` 实现左侧分类树、文档列表与搜索结果页，接口按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §5.2/§5.3。要点：`el-tree` 两级、节点显示 docCount 与 remark、`autoCreated` 节点加"待整理"标记；点击小方向加载分页文档列表；顶栏搜索展示 breadcrumb+snippet 并把 `**词**` 渲染成高亮；单独的 INBOX 页支持把未归类文档移动到指定小方向并刷新两侧计数。不做拖拽排序。完成后 git 提交一次 `feat(T12): 分类树、文档列表与搜索`。

---

### T13 · 文档渲染：代码与正文差异化【核心体验】

- **依赖**：T11（契约足够，可与后端并行） | **实现需求**：R5–R8、决策 D3、D4
- **Why**：用户最核心的诉求之一——"渲染时能分辨哪些是代码、哪些是文本，且表现形式不一致"。
- **产出文件**：`views/DocView.vue`、`components/MarkdownDoc.vue`、`components/blocks/{ProseBlock,CodeBlock,TableBlock,QuoteBlock,ListBlock,HeadingBlock}.vue`、`components/TocSidebar.vue`、`utils/markdown.js`
- **实现要点**：
  - **不在前端切块**：遍历后端 `blocks[]`，按 `type` 选择组件（`code`→`CodeBlock`，`heading`→`HeadingBlock`，`table`→`TableBlock`，`quote`→`QuoteBlock`，`list`→`ListBlock`，其余→`ProseBlock`）。每个块根 DOM 必须带 `:data-anchor="block.anchor"` 与 `:id="'blk-'+block.anchor"`。
  - `utils/markdown.js`：`markdown-it({html:false, linkify:true, breaks:false})` 渲染单块 `raw` → `DOMPurify.sanitize()` → `v-html`。**渲染器实例全局单例**。
  - `CodeBlock.vue`：不用 markdown-it，直接从 `raw` 剥掉首尾围栏取代码文本，用 `highlight.js` 按 `lang` 高亮（`lang` 未注册则不高亮）。视觉必须与正文明显不同：深色背景、等宽字体、右上角语言角标、右上角复制按钮（复制成功 toast）、可选行号、横向滚动不换行。
  - 行内代码（R7）：在全局样式里给 `.prose code:not(pre code)` 单独底色 + 圆角 + 稍小字号，与代码块区分。
  - `TocSidebar.vue`：由 `blocks` 里 `type==='heading'` 生成目录（`level` 决定缩进），点击滚动到 `#blk-<anchor>`，滚动时高亮当前项。
  - 正文排版：最大宽度 `--doc-max-width`、段间距、表格边框、引用块左竖线，整体与代码块形成清晰对比。
  - 顶部显示 breadcrumb（大类/小方向）、标题、更新时间、版本号、`tags`，以及"编辑 / 下载 md / 历史版本"入口（历史版本弹窗读 `/versions`）。
- **验收**：打开含 java/bash/sql/text 四种代码块与表格、列表、引用的文档 —— 代码块深色高亮带语言角标与复制按钮、正文常规排版、行内代码有底色；TOC 可跳转且滚动高亮；`document.querySelectorAll('[data-anchor]').length` 等于后端 `blocks.length`；DevTools 里搜不到未净化的 `<script>`。
- **边界**：不做 KaTeX/Mermaid；不自己解析 markdown 结构；不做编辑功能（T15）。
- **提示词**：
  > 在 `<项目根>/frontend` 实现文档阅读页与 Markdown 渲染，核心要求：**代码与正文表现形式必须明显不同**。严格按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` D3/D4 与 §5.3 的 `blocks[]` 结构：前端**绝对不要自己切块**，而是遍历后端返回的 blocks，按 `type` 分派到不同 Vue 组件（code/heading/table/quote/list/prose），每个块根节点带 `data-anchor` 与 `id="blk-<anchor>"`。正文块用 markdown-it（`html:false`）渲染单块 raw 后过 DOMPurify；代码块单独组件：剥围栏 + highlight.js 按 lang 高亮 + 深色底 + 等宽字体 + 语言角标 + 复制按钮 + 不换行横向滚动；行内代码用独立底色样式与代码块区分。另做右侧 TOC（由 heading 块生成，点击滚到对应块并随滚动高亮）与顶部 breadcrumb/标题/版本/tags/下载 md/历史版本入口。不做 KaTeX 与 Mermaid。按卡内验收项自检后 git 提交一次 `feat(T13): 文档块渲染与代码高亮`。

---

### T14 · 个人见解交互：挂载 / 折叠 / 失配处理

- **依赖**：T13（+ T09 提供接口） | **实现需求**：R18–R22
- **产出文件**：`components/AnnotationBar.vue`（折叠条）、`components/AnnotationList.vue`、`components/AnnotationEditor.vue`、`components/OrphanDrawer.vue`、`api/annotation.js`、`stores/annotation.js`
- **实现要点**：
  - 每个块渲染完后，在块下方插入该块的见解区（按 `anchor` 从 `annotations` 分组）。
  - 鼠标悬浮块 → 块右侧（或右侧留白处）出现 `＋ 见解` 悬浮按钮 → 点开内联编辑器（Markdown 文本域 + 预览 + 保存/取消，`Ctrl+Enter` 保存）。
  - 折叠条：`💡 我的见解 (n)`，**默认折叠**；点击展开显示见解列表（每条渲染 Markdown、显示时间、编辑/删除按钮）。顶栏提供"全部展开 / 全部折叠"，展开状态存 `localStorage`（按文档 id）。
  - `STALE`：折叠条加 `⚠ 原文已变更，请确认`，展开后显示"创建时的原文快照 vs 当前块内容"对照，提供【确认仍然适用】（调 `/confirm`）与【改挂到其他块】。
  - `ORPHAN`：不挂正文，集中在右侧「游离见解」抽屉（`OrphanDrawer.vue`），显示见解内容 + `blockSnippet`；点【重新挂载】进入"选块模式"：正文所有块出现可点击边框，点某块即调 `/reanchor`。抽屉入口在顶部显示未处理数量的红点。
  - 所有写操作后局部刷新该文档的 `annotations`（重新拉详情或只拉 annotations），不整页刷新。
- **验收**：在段落块与代码块各加一条见解并默认折叠；同一块加两条按时间排序；编辑/删除生效；后端把某条置为 STALE 后前端显示告警并可一键确认；ORPHAN 见解可通过选块模式重挂成功；刷新页面折叠状态保持。
- **边界**：不做划词选区批注（明确按"块"粒度，规格已定）；不做见解历史版本。
- **提示词**：
  > 在 `<项目根>/frontend` 实现"个人见解"交互，需求见 `docs/aegis/specs/2026-08-20-learn-notes-design.md` R18–R22 与接口 §5.5。要点：见解按 `anchor` 分组挂在对应块下方；悬浮块出现 `＋ 见解` 内联编辑器（Markdown + 预览，Ctrl+Enter 保存）；折叠条 `💡 我的见解 (n)` 默认折叠、可点击展开、支持全部展开/折叠且状态按文档存 localStorage；`STALE` 显示 `⚠ 原文已变更，请确认` 并展示"创建时快照 vs 当前块"对照，提供确认与改挂；`ORPHAN` 进右侧「游离见解」抽屉（带未处理红点），点【重新挂载】进入选块模式、点击正文任一块即调 reanchor。粒度就是"块"，不要做划词选区。按卡内验收项自检后 git 提交一次 `feat(T14): 个人见解挂载、折叠与失配处理`。

---

### T15 · Markdown 编辑器 + 分类管理页

- **依赖**：T12、T13 | **实现需求**：R2、R3、R5、R10
- **产出文件**：`views/DocEditView.vue`、`views/CatalogManageView.vue`、`components/MarkdownEditor.vue`、`components/VersionDialog.vue`
- **实现要点**：
  - 编辑器：左侧纯文本域（等宽字体、Tab 缩进、`Ctrl+S` 保存）、右侧调用 T13 的渲染组件做预览（保存前预览用前端本地切块**仅供预览**，必须在代码注释里注明"预览用切块不产生锚点、不作为权威"）；保存时填 `changeNote`；保存成功跳阅读页。
  - 新建文档：选小方向 + 标题 + 正文；`slug` 可选，留空由后端生成。
  - 分类管理页：两级表格/树，支持新增、改名、编辑 `remark`、改 `sortOrder`、移动小方向、删除（409 时把后端 `msg` 原样弹出）；`INBOX/未归类` 行禁用删改按钮。
  - 历史版本弹窗：列表 + 查看某版本正文（只读渲染，用后端 `/versions/{v}` 返回的 contentMd 本地渲染即可，注明同上）。
- **验收**：新建/编辑文档保存后版本号与内容正确；保存相同内容不涨版本；分类改名与 remark 编辑生效并在树上可见；删除非空小方向弹出后端提示；历史版本可查看。
- **边界**：不做富文本 WYSIWYG、不做草稿自动保存（P2）、不做图片上传。
- **提示词**：
  > 在 `<项目根>/frontend` 实现 Markdown 编辑器与分类管理页。按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §5.2/§5.3：编辑器为左文本域右预览（等宽字体、Ctrl+S 保存、可填 changeNote），预览时的本地切块仅用于预览、必须加注释声明它不产生锚点也不是权威；分类管理页支持两级新增/改名/编辑 remark/改排序/移动/删除，删除失败时原样展示后端 msg，`INBOX/未归类` 禁止删改；历史版本弹窗可查看旧版正文。不做 WYSIWYG、草稿自动保存与图片上传。完成后 git 提交一次 `feat(T15): Markdown 编辑器与分类管理`。

---

### T16 · Docker 化与云端部署

- **依赖**：T03、T11（骨架存在即可，建议在后端主要接口完成后做） | **实现需求**：R26–R28、决策 D9
- **产出文件**：`backend/Dockerfile`、`frontend/Dockerfile`、`frontend/nginx.conf`、`docker-compose.yml`、`.env.example`（补齐）、`docs/DEPLOY.md`、`.dockerignore` ×2
- **实现要点**：
  - `backend/Dockerfile`：多阶段 `maven:3.9-eclipse-temurin-17` 构建 → `eclipse-temurin:17-jre-alpine` 运行；`ENTRYPOINT` 带 `-Duser.timezone=Asia/Shanghai`；暴露 8080。
  - `frontend/Dockerfile`：多阶段 `node:20-alpine` `npm ci && npm run build` → `nginx:alpine`，拷 `dist` 与 `nginx.conf`。
  - `nginx.conf`：`location /` 用 `try_files $uri $uri/ /index.html`（history 路由）；`location /api/ { proxy_pass http://backend:8080; }` 并透传 `Authorization`、`X-Api-Token`、`Host`、`X-Real-IP`；`client_max_body_size 20m`；对 `.js/.css` 加长缓存、对 `index.html` 禁缓存；预留注释好的 443/HTTPS 段。
  - `docker-compose.yml`：三服务 `mysql`（`mysql:8.0`，命名卷 `mysql-data`，`--character-set-server=utf8mb4`，`healthcheck: mysqladmin ping`）、`backend`（`depends_on: mysql: condition: service_healthy`，挂 `./storage:/app/storage`，env 从 `.env` 读）、`web`（`depends_on: backend`，端口 `${WEB_PORT:-80}:80`）。统一 `restart: unless-stopped`。
  - `docs/DEPLOY.md`：云服务器全流程（装 docker、拉代码、`cp .env.example .env` 并改密码、`docker compose up -d --build`、看日志、验证 `/api/health`）；**同时给两种端口方案**：compose 内 Nginx 直接占 80，或改 `WEB_PORT=8088` 由宿主已有 Nginx 反代（给出宿主 server 段示例）；数据备份与恢复命令（`docker exec mysqldump` + `storage/` 目录打包）；升级流程（`git pull && docker compose up -d --build`，Flyway 自动迁移）；常见故障排查（后端起不来看 DB 健康、401 看 JWT secret 是否变更导致旧 token 失效）。
- **验收**：在干净环境 `cp .env.example .env`（改密码）→ `docker compose up -d --build` → `curl http://<host>/api/health` 返回 UP；浏览器访问 `http://<host>/` 出登录页并能登录；`docker compose down`（不带 `-v`）再 `up -d` 后数据仍在；`storage/docs` 内文件在宿主可见。
- **边界**：不做 K8s、不做 CI、不做证书自动签发、不把 `.env` 提交进仓库。
- **提示词**：
  > 为 `<项目根>` 做 Docker 化与部署文档，按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` D9 与 R26–R28。要点：后端多阶段 Dockerfile（maven→jre-alpine，时区 Asia/Shanghai）；前端多阶段（node→nginx）；`nginx.conf` 支持 history 路由 `try_files`、反代 `/api` 到 `backend:8080` 并透传 `Authorization` 与 `X-Api-Token`、`client_max_body_size 20m`、预留 HTTPS 注释段；`docker-compose.yml` 三服务，mysql 带 healthcheck 且 backend `depends_on: service_healthy`，命名卷持久化 MySQL，`./storage` 绑定挂载，端口用 `${WEB_PORT:-80}`；补齐 `.env.example`；写 `docs/DEPLOY.md` 覆盖首次部署、两种端口方案（自带 Nginx 占 80 / 由宿主 Nginx 反代 8088）、备份恢复、升级、常见故障。禁止把 `.env` 提交进仓库。按卡内验收项验证后 git 提交一次 `chore(T16): Docker 编排与部署文档`。

---

### T17 · 端到端验收 + 示例数据 + agent 投稿脚本

- **依赖**：全部 | **实现需求**：规格 §9 全部 12 条
- **产出文件**：`docs/ACCEPTANCE.md`（逐条结果与证据）、`scripts/import-docs.ps1` 与 `scripts/import-docs.sh`（批量投稿脚本）、`samples/*.md`（5 篇示例笔记）
- **实现要点**：
  - `samples/`：至少覆盖 `java__函数__lambda-basics.md`（完整 front-matter）、`java__类__inner-class.md`、`vue__组件__props-basics.md`（仅文件名通道）、`mysql__索引__b-plus-tree.md`（含表格与多语言代码块）、`随手记.md`（走 INBOX）。**全部必须符合 `docs/AGENT-DOC-SPEC.md` 的自查清单。**
  - 投稿脚本：读目录下所有 `.md`，逐个 POST `/api/import/doc`（带 `X-Api-Token`），打印 `resolvedBy/version/warnings/reanchor`，任一出现 `INBOX` 或非空 `warnings` 时以非 0 退出码结束，便于 agent 自检。
  - `docs/ACCEPTANCE.md`：规格 §9 的 12 条逐条写"执行命令 / 期望 / 实际 / 结论"，含第 8 条（改一段后重导入，见解不错位）的完整过程记录。
  - 发现的不符合项**不要自己改契约**：登记在 `docs/ACCEPTANCE.md` 的"缺陷清单"并回报。
- **验收**：`docs/ACCEPTANCE.md` 中 12 条全为通过，或不通过项有明确缺陷条目与责任任务号。
- **边界**：本卡只做验收与脚本，不改业务代码（除非是验收中发现的、明确属于某张卡边界内的小修，且需在提交信息里注明 `fix(Txx)`）。
- **提示词**：
  > 为 `<项目根>` 做端到端验收与投稿工具。1) 在 `samples/` 写 5 篇示例笔记，覆盖 front-matter 通道、文件名通道、INBOX 兜底、含表格与多语言代码块的文档，全部必须通过 `docs/AGENT-DOC-SPEC.md` 的自查清单；2) 写 `scripts/import-docs.ps1` 与 `.sh`，批量 POST `/api/import/doc`（`X-Api-Token`），打印 `resolvedBy/version/warnings/reanchor`，出现 INBOX 或 warnings 时非 0 退出；3) 按 `docs/aegis/specs/2026-08-20-learn-notes-design.md` §9 的 12 条逐条执行并把"命令/期望/实际/结论"写进 `docs/ACCEPTANCE.md`，第 8 条（改动一段后重新导入，未改动块的见解必须仍在原位）要有完整过程记录。发现不符合项不要自行修改设计契约，登记到缺陷清单并回报。完成后 git 提交一次 `test(T17): 端到端验收、示例笔记与投稿脚本`。

---

## 5. 风险与回滚

| 风险 | 触发信号 | 处置 |
|---|---|---|
| 执行 agent 在前端又写了一套块切分 | 前端出现 markdown 全文解析并生成 anchor 的代码 | 违反 D3，打回重做；预览用切块必须带注释声明"非权威" |
| 锚点算法被"优化"（改 hash 位数、改归一化） | `AnchorUtilTest` 被改动 | 测试即契约，改测试等于改契约，必须回评审 |
| 导入把 INBOX 当正常路径，大量文档堆在未归类 | `resolvedBy=INBOX` 比例高 | 说明写文档的 agent 未遵守 AGENT-DOC-SPEC；投稿脚本已用非 0 退出码提前暴露 |
| Flyway 已发布迁移被修改 | `V1__init.sql` 有改动 | 只能新增 `V3__`；已发布脚本改动会导致校验和失败、生产库无法启动 |
| 见解在重写后被静默丢弃 | `ReanchorServiceTest` 缺删除段落用例 | T09 硬要求：任何情况不得自动删除见解 |
| 云端 80 端口被宿主 Nginx 占用 | `docker compose up` 端口冲突 | `docs/DEPLOY.md` 已给 `WEB_PORT=8088` + 宿主反代方案 |

**回滚面**：数据库改动一律通过新增 Flyway 版本；容器层回滚 = `git checkout <上一个 tag> && docker compose up -d --build`；见解数据只增不删，最坏情况是 ORPHAN 需人工重挂，不会丢内容。

**Retirement**：本项目无历史 owner 需退役。`INBOX/未归类` 是**长期兜底路径**而非临时兼容层，不设退役触发条件。

---

## 6. 分发建议（给用户）

- **第一波（可同时开 3 个 agent）**：先由一个 agent 独立做完 `T01`，然后并行分发 `T02`、`T05`、`T11`。
- **第二波**：`T03` 完成后并行 `T04`、`T06`、`T07`；前端并行 `T12`、`T13`。
- **第三波**：`T08`、`T09`、`T10`、`T14`、`T15`。
- **收尾**：`T16` → `T17`。
- 每个 agent 的开场都应包含这三句：① 只在 `F:\deespeekharness\learn-notes` 内改动；② 先读 `docs/aegis/specs/2026-08-20-learn-notes-design.md` 与本文件对应任务卡；③ 不得修改设计规格的 D1–D9、表结构与 API 契约，需要改就停下回报。
