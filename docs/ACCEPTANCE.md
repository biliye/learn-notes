# learn-notes 端到端验收记录

- 验收日期：`2026-08-20`
- 验收环境：本机 Windows（后端 `mvn spring-boot:run`，前端 `npm run dev`，MySQL 8.0 测试实例 127.0.0.1:3307）
- 规格来源：`docs/aegis/specs/2026-08-20-learn-notes-design.md` §9（16 条）
- 相关脚本：`scripts/e2e-local-check.py`（16/16 自动检查）、`scripts/import-docs.*`、`scripts/restore-from-export.*`

> 说明：本机没有 Docker，第 1/12/16 条的容器部分需在云服务器复核；单测与 HTTP 层已在本机全部验证。

---

## 逐条结果

### 1. 部署后 health 返回 UP

- 执行：`curl http://localhost:8080/api/health`（本地等价：`mvn spring-boot:run` 后）
- 期望：`{"code":0,"msg":"ok","data":{"status":"UP",...}}`
- 实际：`{"code":0,"msg":"ok","data":{"status":"UP","version":"0.1.0"}}`
- 结论：**通过**（容器编排部分见 `docs/DEPLOY.md`，需在云服务器 `docker compose up -d` 复核）

### 2. 未带 token 访问返回 401；登录后返回树

- 执行：`curl -i /api/catalog/tree` → 401；`POST /api/auth/login` 后带 `Authorization: Bearer` → 树
- 实际：无 token → `HTTP/1.1 401`；登录后返回两级树（含 INBOX/未归类 兜底）
- 结论：**通过**

### 3. X-Api-Token 导入带 front-matter 的文档 → 自动归类

- 执行：`POST /api/import/doc`，front-matter 为 `java / 函数`
- 实际：`resolvedBy=FRONT_MATTER`，首次 `topic.autoCreated=true`，树中出现该节点且带自动创建标记
- 结论：**通过**

### 4. 文件名约定导入

- 执行：导入 `vue__组件__props-basics.md`（无 front-matter）
- 实际：`resolvedBy=FILENAME`，归类到 vue / 组件
- 结论：**通过**（注：Windows 下 curl 的 UTF-8 文件名会被 GBK 编码，验收用 Python/服务器 Linux 验证，脚本已用 `basename` 修正）

### 5. 无元数据 → INBOX

- 执行：导入 `随手记.md`
- 实际：`resolvedBy=INBOX`，落到 `INBOX / 未归类`
- 结论：**通过**

### 6. 前端渲染：代码与正文差异化、行内代码、TOC

- 执行：浏览器打开 `java/function/lambda-basics` 文档（含 java 代码块、行内代码、多级标题）
- 实际：代码块深色底 + 语言角标 `java` + 复制按钮 + 高亮；正文常规排版；行内代码 `int` 有底色；右侧 TOC 可点击跳转
- 结论：**通过**（浏览器 DOM 快照验证）

### 7. 段落与代码块上各加见解，默认折叠

- 执行：浏览器在段落块、代码块悬浮"＋ 见解"→ 内联编辑器 → 保存
- 实际：见解条 `💡 我的见解 (n)` 默认折叠，点击展开显示内容；同一块多条按时间排序，可编辑/删除
- 结论：**通过**

### 8. NEW_VERSION 重导入（只改一段）→ 版本 +1，未改动块见解保持，被改动块 STALE/ORPHAN

- 执行：导入 v1 → 加见解 → 修改一个段落重导入
- 实际：`version=2`，未改动块的见解仍 `ACTIVE` 且位置正确；被改动段的见解被标记（相似度命中 `STALE`，可一键确认回 `ACTIVE`）
- 结论：**通过**（`e2e-local-check.py` 第 10-12 项 + 浏览器确认交互）

### 9. ORPHAN 见解手动重挂 → ACTIVE

- 执行：库内置 `ORPHAN` 见解 → `POST /api/annotations/{id}/reanchor {"anchor": "<当前块>"}`
- 实际：`200`，`status=ACTIVE`，anchor 更新为当前块（`b0-0d60f38f`）
- 结论：**通过**

### 10. 删除含文档的小方向 → 409

- 执行：`DELETE /api/catalog/{function 小方向}`
- 实际：`409`，msg：`请先迁移该方向下的 1 篇文档`
- 结论：**通过**

### 11. 导入原文落盘

- 执行：导入后检查 `storage/docs/java/function/lambda-basics.md`
- 实际：文件存在，内容与库内一致（含 front-matter 原文）
- 结论：**通过**

### 12. 重启后数据保留

- 执行：本机多次重启后端进程（含 `down`/重建等价操作），数据仍在
- 实际：文档/分类/见解全部保留（MySQL 持久化 + Flyway 幂等）
- 结论：**通过**（容器 `docker compose down` 不带 `-v` 再 `up` 需云服务器复核）

### 13. 粘贴截图自动上传并插入；同图去重

- 执行：`POST /api/uploads/image` 上传 PNG → 再传同图
- 实际：返回 `{url:"/uploads/2026/08/<hash>.png",width,height,bytes,dedup}`；二次上传 `dedup:true` 且磁盘仅一份；伪图（txt 改 png）被 magic number 校验拒绝 400
- 结论：**通过**（编辑器 paste/drop 前端逻辑见 T15，浏览器已验证按钮插入路径）

### 14. 导出 zip 结构完整

- 执行：`GET /api/export/all` → 解包
- 实际：`<大类>/<小方向>/<slug>.md` + 同名 `.insights.json` + `uploads/`（被引用图片）+ `manifest.json`；manifest 计数与真实一致
- 结论：**通过**（`ExportRoundTripTest` 全绿 + 手工解包核对）

### 15. 恢复演练（最重要）

- 执行（完整过程）：
  1. 记录导出计数：分类 3 / 小方向 4 / 文档 5 / 见解 2 / 图片 1
  2. 导出 → 解压到 `notes-export/` → git 提交（快照 `fe6836c`）
  3. 清空全部业务表与 `storage/`（模拟空库 + 空 storage）
  4. **只用 `notes-export/`** 执行 `scripts/restore-from-export.ps1`（不使用任何 dump）
  5. 脚本按 manifest 重建分类 → 逐篇导入文档 → 逐篇回灌见解 → 复制 uploads → 对比计数
- 实际结果：
  ```
  categories  期望 3 实际 3 ✓
  topics      期望 4 实际 4 ✓
  docs        期望 5 实际 5 ✓
  annotations 期望 2 实际 2 ✓
  [OK] 恢复成功
  ```
- 附加核对：恢复后 `storage/uploads/2026/08/63bf1a521f58c962.png` 存在，`GET /uploads/...` 返回 `200 image/png`；`b-plus-tree` 文档图片引用完好
- 结论：**通过**（本项目兜底价值所在，已实测）

### 16. 服务器定时备份与 14 份清理

- 执行：`scripts/backup.sh` 已交付（mysqldump.gz + storage.tgz + export zip 三类各留 14 份，任一步失败非 0 退出，cron 示例在 `docs/BACKUP.md`）
- 实际：本机无 Docker 无法跑 cron 全流程；脚本逻辑与清理循环已代码审查
- 结论：**待云服务器执行一次 `backup.sh` 复核**（脚本见 `scripts/backup.sh`）

---

## 缺陷清单

| # | 发现 | 处置 | 状态 |
|---|---|---|---|
| 1 | 后端块解析曾把 front-matter 当正文切块（用 `parseBody` 而非 `parse`） | 统一改用 `MarkdownBlockParser.parse()` | 已修复并重测 |
| 2 | 导出 zip 未打包被引用图片（`substring(1)` 路径错误，多了一层 `uploads/`） | 改为去掉 `/uploads/` 前缀 | 已修复，往返测试新增断言 |
| 3 | Windows 下 curl/PS 5.1 的中文编码问题（非后端缺陷） | 投稿/恢复脚本改用 Python/PowerShell 的 UTF-8 显式处理；服务器 Linux 无此问题 | 已规避 |
| 4 | PS 5.1 `ConvertTo-Json -Compress` 遇 Unicode 卡死 | 脚本移除 `-Compress` | 已修复 |
| 5 | PS 5.1 `Get-Content` 返回值带 ETS 属性被序列化成对象 | 脚本强制 `[string]` 转换 | 已修复 |

**不涉及设计契约改动**：D1–D13、表结构、API 契约、锚点算法均未修改。
