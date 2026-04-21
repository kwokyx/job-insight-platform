# 环境配置指南

本文档用于在 Windows 本地完整拉起前端、后端、算法服务和依赖组件。

## 1. 基础依赖

请先准备以下软件：

- Node.js 20+
- JDK 8
- Maven 3.9+
- MySQL 8
- Redis 7
- Python 3.11+ 或 3.12+

你当前机器可直接使用的 Python 路径：

```powershell
D:\Python\python.exe
```

## 2. 数据库与缓存

### MySQL

建议创建本地数据库：

```sql
CREATE DATABASE career_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

建议创建本地账号：

```sql
CREATE USER 'career'@'%' IDENTIFIED BY 'career2026';
GRANT ALL PRIVILEGES ON career_platform.* TO 'career'@'%';
FLUSH PRIVILEGES;
```

### Redis

确保本地 Redis 已启动，并能执行：

```powershell
redis-cli ping
```

预期返回：

```text
PONG
```

## 3. 环境变量

项目根目录可放置 `.env`，也可以在后端增加 `application-local.yml`。

建议至少配置：

```ini
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=career_platform
MYSQL_USERNAME=career
MYSQL_PASSWORD=career2026

REDIS_HOST=localhost
REDIS_PORT=6379

ALGORITHM_SERVICE_URL=http://localhost:8000

JWT_SECRET=careerPlatform-jwt-secret-2026-change-in-production
AI_API_KEY=
AI_MODEL=deepseek-ai/DeepSeek-R1-Distill-Qwen-7B
```

## 4. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

## 5. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端测试：

```powershell
cd backend
mvn test
```

## 6. 启动算法服务

```powershell
cd algorithm
& "D:\Python\python.exe" -m pip install -r requirements.txt
& "D:\Python\python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

算法服务健康检查：

```powershell
Invoke-WebRequest http://localhost:8000/health
```

## 7. 前端构建验证

```powershell
cd frontend
npm run build
```

## 8. Docker 方式

如果不想逐项安装依赖，可以直接使用：

```powershell
docker compose up -d --build
```

## 9. 常见问题

### 算法服务无法启动

优先检查：

- `D:\Python\python.exe` 是否存在
- 是否已执行 `pip install -r requirements.txt`
- `ALGORITHM_SERVICE_URL` 是否与后端配置一致

### 后端无法连接算法服务

检查：

- 算法服务是否在 `8000` 端口运行
- [application.yml](C:/Users/32020/Desktop/occupational%20_competencies_platform/backend/src/main/resources/application.yml) 中的 `career.algorithm.service-url`
- 本地防火墙是否阻止了 8000 端口

### 文本乱码

请统一使用 UTF-8 编码保存：

- `README.md`
- `docs/guides/setup.md`
- `application*.yml`
- 前端 `.vue` 文件

Windows 终端若仍出现乱码，优先切换到 UTF-8 代码页：

```powershell
chcp 65001
```
