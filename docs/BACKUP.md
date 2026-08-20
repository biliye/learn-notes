# learn-notes 备份与恢复

> **这份文件是备份与恢复的唯一 owner 文档。** 部署者请先读 `docs/DEPLOY.md`，这里只讲备份。
>
> 用户原话："主要是做备份，防止云端挂了没法恢复。"
> 所以本项目的判定标准**不是"备份文件存在"，而是"能在一台干净机器上把内容全部还原"**。
> **未演练过的备份等于没有备份。**

---

## 1. 三层备份（各司其职，不可互相替代）

| 层 | 内容 | 位置 | 进 git | 职责 |
|---|---|---|---|---|
| **L1 人可读导出** | `<大类>/<小方向>/<slug>.md` + `<slug>.insights.json` | 本机 `<仓库>/notes-export/` | ✅ **进 git** | **主恢复路径**。最坏情况（云端与备份盘全丢）只要 git 在就能重建全部文字与见解 |
| **L2 二进制备份** | `mysqldump` 归档 + `storage/` 打包（含图片） | 服务器 `backup/` → 本机 `F:\deespeekharness\learn-notes-backup\` | ❌ | **快速恢复路径**。一条命令还原到出事前状态，含版本历史 |
| **L3 导入原文落盘** | 导入时写下的原始 md（含 front-matter） | 服务器 `storage/docs/` | ❌ | 导入侧兜底，防"入库成功但内容被后续误改" |

**硬约束：**
- 见解**只存在数据库**里，纯 md 备份必然丢见解 → L1 必须有 `.insights.json` 旁挂文件。
- 见解**绝不能内嵌进 md 正文**（`<!-- -->` 会被解析成一个 HTML 块，打乱锚点）。只能旁挂。
- **`notes-export/` 必须进 git**，绝不能加进 `.gitignore`；`learn-notes-backup/`（dump/图片）必须留在仓库外。

---

## 2. 服务器端定时备份（L2 + L1 源头）

1. 把脚本复制/挂载到服务器项目根，加执行权限：

   ```bash
   chmod +x scripts/backup.sh
   ```

2. 添加 crontab（每天 03:30）：

   ```bash
   crontab -e
   # 加入：
   30 3 * * * cd /opt/learn-notes && ./scripts/backup.sh >> backup/backup.log 2>&1
   ```

3. 手动跑一次验证：

   ```bash
   ./scripts/backup.sh
   ls backup/db backup/storage backup/export   # 三类归档各出现一份
   ```

4. `backup.sh` 的行为：
   - `mysqldump --single-transaction` → `backup/db/learn_notes-<时间戳>.sql.gz`
   - `storage/` 打包 → `backup/storage/storage-<时间戳>.tgz`
   - 调 `GET /api/export/all`（X-Api-Token）→ `backup/export/learn-notes-export-<时间戳>.zip`
   - **三类各只保留最近 14 份**，超出的自动清理
   - 任一步失败 → 非 0 退出并打印原因（cron 才能暴露问题）

---

## 3. 同步回本机（R35）

在**本机**（Windows）执行：

```powershell
powershell -File scripts/sync-to-local.ps1 -ServerHost <服务器IP> -ServerUser <用户名>
```

行为：
- 拉取服务器**最新一份** export zip，**先清空** `notes-export/` 再解压（保证是全量快照，不累积残留）
- md + insights.json 进 git 并提交（内容无变化则跳过）：`backup: notes snapshot YYYY-MM-DD`
- 最新 db dump 与 storage 归档 → `F:\deespeekharness\learn-notes-backup\`（**仓库外**）
- 打印 manifest 计数（分类/小方向/文档/见解/图片）

---

## 4. 恢复（三层各有步骤）

**恢复优先级：有 dump 先用 dump（快、含版本历史）；最坏情况只用 `notes-export/`。**

### 4.1 快速恢复（用 L2 dump，30 分钟内回到出事前）

```bash
# 1) 恢复 MySQL
docker exec -i ln-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" learn_notes < backup/db/learn_notes-XXXX.sql

# 2) 恢复 storage（原文 + 图片）
tar xzf backup/storage/storage-XXXX.tgz -C .

# 3) 重启后端即可
docker compose restart ln-backend
```

### 4.2 最坏情况恢复（只剩本机 git 的 `notes-export/`）—— 这是主恢复路径（R36）

前提：另起一套干净环境（空库 + 空 storage），后端已启动，`APP_API_TOKEN` 可用。

```bash
# Linux / 服务器侧：
./scripts/restore-from-export.sh 127.0.0.1 8088

# Windows 本机：
powershell -File scripts/restore-from-export.ps1 -ServerUrl http://127.0.0.1:8088 -ApiToken <token>
```

脚本会依次：① 按 `manifest.json` 重建分类与 **remark**；② 逐篇导入文档；③ 逐篇回灌见解；④ 复制 `uploads/`；⑤ 打印**期望 vs 实际**五项计数，不一致则非 0 退出。

> **必须定期演练**：每季度至少一次，在干净环境只用 `notes-export/` 完整还原并核对计数。演练记录见 `docs/ACCEPTANCE.md` 第 15 条。

---

## 5. 注意事项

- **不要手动改 `notes-export/` 里的文件**：它是恢复输入格式（规格 §5.7），改了等于让历史备份不可用。
- dump 与图片不进 git（二进制、仓库会膨胀）。
- 备份任务失败会被 cron 暴露（非 0 退出 + 日志），收到告警先看 `backup/backup.log`。
- 服务器磁盘空间：14 份 × 三类归档，注意监控（建议定期 `du -sh backup`）。
