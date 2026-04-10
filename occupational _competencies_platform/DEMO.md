# 🚀 运行与演示指南

> 本文档说明如何运行本项目，以及如何生成一个可供他人访问的公网演示链接。

---

## 目录

- [一、Docker 一键运行（推荐）](#一docker-一键运行推荐)
- [二、本地开发运行（逐模块启动）](#二本地开发运行逐模块启动)
- [三、数据导入](#三数据导入)
- [四、生成公网演示链接](#四生成公网演示链接)
- [五、常用运维命令速查](#五常用运维命令速查)
- [六、演示流程建议](#六演示流程建议)

---

## 一、Docker 一键运行（推荐）

> ✅ 最简单的方式。一行命令启动前端、后端、算法、数据库、缓存、公网穿透共 6 个服务。

### 1. 确保 Docker Desktop 已启动

打开 Docker Desktop，等待左下角状态变为绿色 "Running"。

### 2. 启动全部服务

```bash
# 进入项目根目录
cd D:\occupational_competencies_platform

# 构建并启动（首次约 5-15 分钟，之后约 10 秒）
docker compose up -d --build
```

### 3. 查看启动状态

```bash
docker compose ps
```

预期输出（所有容器状态为 `running` 或 `Up`）：

```
NAME               IMAGE                                  STATUS
career-mysql       mysql:8.0                              Up (healthy)
career-redis       redis:7-alpine                         Up (healthy)
career-algorithm   occupational_...-algorithm             Up
career-backend     occupational_...-backend               Up
career-frontend    occupational_...-frontend              Up
career-tunnel      cloudflare/cloudflared:latest           Up
```

### 4. 访问平台

打开浏览器，输入：

```
http://localhost
```

看到平台首页即代表启动成功 🎉

### 5. 停止服务

```bash
docker compose down        # 停止所有容器（保留数据）
docker compose down -v     # 停止并清空数据库（慎用）
```

---

## 二、本地开发运行（逐模块启动）

> 适合需要修改代码、调试功能时使用。需要分别启动各个模块。

### 启动顺序

```
MySQL → Redis → 算法引擎 → 后端 → 前端
```

> ⚠️ 必须严格按此顺序启动，因为后端依赖数据库和 Redis，前端依赖后端 API。

---

### 第 1 步：启动 MySQL 和 Redis

**方式 A：使用 Docker 只启动中间件**（推荐）

```bash
# 只启动 MySQL + Redis 容器
docker compose up -d mysql redis
```

**方式 B：使用本地安装的 MySQL + Redis**

确保 MySQL 和 Redis 已在本地安装并启动，且数据库已初始化（参见 [SETUP.md](./SETUP.md)）。

---

### 第 2 步：启动算法引擎（Python）

```bash
cd algorithm

# 创建虚拟环境（首次）
python -m venv venv

# 激活虚拟环境
# Windows:
venv\Scripts\activate
# macOS/Linux:
source venv/bin/activate

# 安装依赖（首次）
pip install -r requirements.txt

# 启动算法服务（默认端口 8000）
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

验证：浏览器打开 `http://localhost:8000/docs` 查看 FastAPI 自动生成的接口文档。

---

### 第 3 步：启动后端（Java Spring Boot）

```bash
cd backend

# 方式 A：使用 Maven 命令行
mvn spring-boot:run

# 方式 B：在 IntelliJ IDEA 中
# 打开 backend 文件夹作为项目 → 找到 Application.java → 右键 Run
```

> 💡 后端默认端口为 `8080`。如果 MySQL 使用 Docker 方式启动，需要确保 `.env` 中的 `MYSQL_HOST=localhost`、`MYSQL_PORT=3307`（Docker 映射到宿主机的端口是 3307）。

验证：浏览器打开 `http://localhost:8080/doc.html` 查看 Swagger API 文档。

---

### 第 4 步：启动前端（Vue 3 + Vite）

```bash
cd frontend

# 安装依赖（首次）
npm install

# 启动开发服务器（带热重载）
npm run dev
```

启动后终端会显示：

```
  VITE v6.x.x  ready in xxx ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: http://192.168.x.x:5173/
```

打开浏览器访问 **http://localhost:5173** 即可。

> 💡 开发模式下，Vite 已配置了代理：所有 `/api` 开头的请求会自动转发到 `http://localhost:8080`，所以**无需手动配置跨域**。

---

## 三、数据导入

平台需要将 `data/processed/` 目录下的 JSON 招聘数据导入 MySQL。

### Docker 环境

如果使用 Docker 启动，`config/init.sql` 会在 MySQL 容器首次启动时自动执行建表语句。如果额外需要导入更多数据：

```bash
# 确保本机有 Python 和 pymysql
pip install pymysql

# 运行导入脚本（自动连接 Docker 中的 MySQL）
python scripts/import_data.py
```

### 本地环境

```bash
# 先确保 MySQL 已启动且数据库已创建
# 然后运行导入脚本
python scripts/import_data.py
```

> 脚本会自动读取 `.env` 文件中的数据库连接信息。

---

## 四、生成公网演示链接

> 当你需要把平台展示给导师、同学或面试官时，可以生成一个临时的公网 HTTPS 链接。

### 方式 A：使用内置的 Cloudflare Tunnel（推荐）

项目的 `docker-compose.yml` 已内置了 Cloudflare Tunnel 服务，**无需任何注册或配置**：

```bash
# 1. 确保所有服务已启动
docker compose up -d --build

# 2. 查看分配的公网地址
docker compose logs tunnel
```

在日志中找到类似这样的一行：

```
INF +-----------------------------------------------------------+
INF |  Your quick Tunnel has been created! Visit it at:          |
INF |  https://xxxxx-xxxxx-xxxx.trycloudflare.com                |
INF +-----------------------------------------------------------+
```

把 `https://xxxxx-xxxxx-xxxx.trycloudflare.com` 这个链接发给对方，**对方可以直接在浏览器中打开**，无需任何安装。

> ⚠️ **注意事项**：
> - 这是临时链接，每次重启 Tunnel 容器后地址会变化
> - 需要你的电脑保持开机且 Docker 保持运行
> - 链接是 HTTPS 加密的，可放心分享
> - 免费版有带宽限制，不适合大流量场景

### 方式 B：使用 ngrok

如果 Cloudflare Tunnel 速度不理想，可以使用 ngrok：

```bash
# 1. 安装 ngrok（https://ngrok.com/ 下载）
# 2. 注册并获取 token
ngrok config add-authtoken 你的token

# 3. 启动穿透（指向前端的 80 端口）
ngrok http 80
```

### 方式 C：部署到云服务器

如果有云服务器（阿里云/腾讯云），将整个项目传上去后运行：

```bash
# 在服务器上
docker compose up -d --build

# 开放安全组的 80 端口
# 然后通过 http://你的服务器IP 访问
```

---

## 五、常用运维命令速查

### Docker 管理

```bash
# ─── 启停控制 ─────────────────────
docker compose up -d              # 后台启动全部
docker compose up -d --build      # 重新构建后启动
docker compose down               # 停止全部
docker compose restart frontend   # 仅重启前端
docker compose restart backend    # 仅重启后端

# ─── 状态查看 ─────────────────────
docker compose ps                 # 查看容器状态
docker compose logs -f backend    # 实时看后端日志
docker compose logs -f frontend   # 实时看前端日志
docker compose logs tunnel        # 查看公网地址

# ─── 调试排错 ─────────────────────
docker compose exec mysql bash    # 进入 MySQL 容器
docker compose exec mysql mysql -u career -pcareer2026 career_platform
                                  # 直接进入数据库命令行

# ─── 清理重建 ─────────────────────
docker compose down -v            # 停止并删除数据卷
docker system prune -af           # 清除所有未使用的镜像（慎用）
```

### 前端开发

```bash
cd frontend
npm run dev        # 开发模式（热重载）
npm run build      # 构建生产包（输出到 dist/）
npm run preview    # 预览构建结果
```

### 后端开发

```bash
cd backend
mvn spring-boot:run                  # 启动后端
mvn package -Dmaven.test.skip=true   # 打 JAR 包
```

### 算法引擎

```bash
cd algorithm
uvicorn app.main:app --reload --port 8000   # 开发模式启动
```

---

## 六、演示流程建议

> 以下是一个推荐的功能展示流程，适合毕业答辩或项目演示。

### 🎬 演示脚本（约 8 分钟）

| 步骤 | 时长 | 操作 | 展示重点 |
|------|------|------|----------|
| 1 | 1 min | 打开首页概览 | 大屏数据看板，KPI 统计数字跳动 |
| 2 | 1 min | 浏览「岗位大厅」 | 高级筛选（按城市/行业/薪资），展示分页 |
| 3 | 2 min | 进入「数据洞察」 | 切换多个 ECharts 图表，演示交互动画 |
| 4 | 1 min | 切换深色/浅色主题 | 展示全局主题切换效果 |
| 5 | 2 min | 使用「AI 助手」 | 问一个职业规划问题，展示流式打字效果和思考链 |
| 6 | 1 min | 操作「AI 薪资预测」 | 输入参数获取薪资区间预估，展示 ML 模型能力 |
| 7 | 30s | 展示个人中心 | 注册/登录、修改头像/昵称，展示账号管理 |
| 8 | 30s | 展示手机端适配 | 用手机扫公网链接或 F12 切换移动视图 |

### 💡 演示小贴士

1. **提前启动并预热**：演示前 10 分钟先启动 Docker 并打开页面让所有服务初始化完毕
2. **准备好公网链接**：如果对方电脑需要访问，提前把 Cloudflare Tunnel 地址准备好
3. **预设 AI 问题**：准备 2-3 个效果好的 AI 提问，例如：
   - "分析一下大模型开发岗位的薪资水平和发展前景"
   - "我是计算机专业应届生，想去杭州做 Java 开发，帮我分析一下"
4. **F12 移动端模拟**：Chrome 按 F12 → 点击左上角设备切换图标 → 选择 iPhone 14 Pro 展示移动端效果

---

## 📌 端口速查表

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 (Nginx) | `80` | Docker 生产模式 |
| 前端 (Vite) | `5173` | 本地开发模式 |
| 后端 (Spring Boot) | `8080` | REST API + Swagger |
| 算法 (FastAPI) | `8000` | ML 预测接口 |
| MySQL | `3307` | 宿主机映射端口（容器内 3306） |
| Redis | `6379` | 缓存 |
