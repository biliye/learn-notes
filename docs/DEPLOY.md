# learn-notes 部署指南

> 适用于目标云服务器：**已在运行 astrbot + napcat**。所有操作默认不触碰既有容器与服务。
> 备份与恢复主题请见 `docs/BACKUP.md`（本文件只给一句指引，不重复展开）。

---

## 0. 部署前端口探测（必须执行，再决定 WEB_PORT）

在云服务器上依次执行，把结果贴回确认：

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

**判定规则：**

| 情况 | 处置 |
|---|---|
| 第 1 条里 **8088 无输出** | 直接用默认 `WEB_PORT=8088`，并在云厂商安全组放行 8088 |
| **80 无输出且没有宿主 Nginx** | 可以把 `WEB_PORT` 改成 `80`，访问更省事 |
| **80 被占且是宿主 Nginx** | 保持 8088，另在宿主 Nginx 加一段 `server`/`location` 反代（见 §5.3） |
| **80 被占但是 astrbot/napcat 的容器** | 保持 8088，不要动既有容器 |
| 发现已有容器名以 `ln-` 开头或已有 `ln-mysql-data` 卷 | 停下回报，改前缀 |

---

## 1. 首次部署

前置：机器上有 Docker（含 compose 插件，`docker compose version` 可跑）。

```bash
# 1) 拉代码
cd /opt
git clone <你的仓库地址> learn-notes
cd learn-notes

# 2) 配置环境变量
cp .env.example .env
vi .env
#   必改：MYSQL_ROOT_PASSWORD / APP_ADMIN_PASSWORD / APP_API_TOKEN / APP_JWT_SECRET
#   可选：WEB_PORT（按 §0 探测结果，默认 8088）

# 3) 启动全栈
docker compose up -d --build

# 4) 看日志（首次等 mysql healthcheck 通过、Flyway 建表）
docker compose logs -f ln-backend

# 5) 健康检查
curl http://127.0.0.1:8088/api/health
# → {"code":0,"msg":"ok","data":{"status":"UP","version":"0.1.0"}}

# 6) 登录验证
curl -X POST http://127.0.0.1:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$APP_ADMIN_USERNAME\",\"password\":\"$APP_ADMIN_PASSWORD\"}"
```

访问：`http://<服务器IP>:8088`（记得在云厂商安全组放行对应端口）。

---

## 1.1 多用户说明（V3 起）

- **注册**：登录页有「立即注册」入口，任何人可注册（`APP_REGISTER_ENABLED=false` 可关闭）；注册即自动建好该用户的 INBOX/未归类分类。
- **数据隔离**：每个用户只能看到自己的分类与文档（搜索/导入/导出同样只作用于自己的数据）。
- **管理员**：初始账号（`APP_ADMIN_USERNAME`）角色为 `ADMIN`，侧栏多出「全部文档」入口，可查看所有用户的文档与用户列表；升级存量部署时原账号自动转为管理员，历史数据全部归属到该账号。
- **agent 导入通道**：`X-Api-Token`（备份/投稿脚本）导入的数据归属管理员账号。
- **升级影响**：升级后旧 token 失效，所有已登录用户需重新登录一次（V3 迁移由 Flyway 自动执行，无需手工 SQL）。

---

## 2. 与 astrbot / napcat 共存注意事项

1. **不要动既有容器**，不要对它们执行任何操作。
2. **不要用 `network_mode: host`**：端口冲突面最大，且失去容器隔离。
3. 本项目已自带防护：容器名 `ln-` 前缀、网络 `ln-net`、卷 `ln-mysql-data`、compose 项目名 `learn-notes`、MySQL 不映射端口。**唯一会撞的是 WEB_PORT** —— 由 §0 探测结果决定。
4. 发现容器名或卷名冲突 → 停下回报，不要自行改前缀继续。

---

## 3. 三种访问方案

### 3.1 直接用 8088（默认，最省事）

`WEB_PORT=8088`，云厂商安全组放行 8088 即可。

### 3.2 确认 80 空闲后改用 80

`.env` 里把 `WEB_PORT` 改为 `80`，然后：

```bash
docker compose up -d
```

### 3.3 已有宿主 Nginx 时反代到 8088

在宿主 Nginx 加一段（记得 `nginx -t && systemctl reload nginx`）：

```nginx
server {
    listen 80;
    server_name notes.example.com;   # 改成你的域名或 IP

    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

---

## 4. 升级流程

日常更新走一键脚本（本机 Git Bash，经阿里云 Workbench CLI 操作无公网 IP 的服务器）：

```bash
./scripts/update-server.sh            # 完整更新：git pull --ff-only + 重建 + 验证
./scripts/update-server.sh --check    # 只查看服务器 git / 容器状态
```

脚本要求：本地已 `git push`、已安装 `workbench` CLI（`--output json` 模式，规避 Windows 下 text 模式的 exec-stream bug）。

等价的手工操作（服务器上执行）：

```bash
cd /opt/learn-notes
git pull
docker compose up -d --build
```

Flyway 会自动执行新增的迁移脚本，无需手工 SQL。

---

## 5. 常见故障排查

| 症状 | 排查 |
|---|---|
| 后端起不来 | `docker compose logs ln-mysql` 看健康状态（`docker ps` 里 ln-mysql 是否 healthy）；首次建库要等 30s 左右 |
| 全站 401 | 检查 `APP_JWT_SECRET` 是否变更过（更换会让旧 token 全部失效） |
| 图片 404 | 检查 `ln-web` 是否挂上了 `./storage` 卷（`docker compose ps` 看 volumes），以及图片路径是否在 `storage/uploads/` 下 |
| 导入接口 401 | 检查 `X-Api-Token` 是否与 `.env` 的 `APP_API_TOKEN` 一致 |
| 中文乱码 | 确认所有文件 UTF-8 上传、数据库 `utf8mb4` |

---

## 6. 备份与恢复

备份三层、cron 配置、恢复步骤与恢复演练全部见 **`docs/BACKUP.md`**（唯一 owner 文档）。部署完成后请按其中步骤配置服务器定时备份，并把导出物同步回本机 git 仓库。
