# ⚙️ 环境配置指南

> 本文档详细说明如何在一台全新的电脑上从零配置本项目的完整运行环境。

---

## 📋 总览

本项目包含以下技术组件，根据你选择的运行方式，需要安装不同的软件：

| 运行方式 | 必装软件 | 说明 |
|----------|----------|------|
| **Docker 一键部署**（推荐） | Docker Desktop | 最省事，一行命令启动全部服务 |
| **本地开发调试** | Node.js + JDK + Python + MySQL + Redis | 可单独调试每个模块 |

---

## 方式一：Docker 一键部署（推荐 ⭐）

> 适合：快速体验、演示答辩、部署到服务器。  
> 只需安装 Docker Desktop，其他一切由容器自动处理。

### 第 1 步：安装 Docker Desktop

#### Windows

1. 前往 [Docker Desktop 官网](https://www.docker.com/products/docker-desktop/) 下载 Windows 安装包
2. 双击安装，安装过程中如果弹出 WSL 2 相关提示，按指引开启即可
3. 安装完成后重启电脑
4. 启动 Docker Desktop，等待左下角图标变为绿色 ✅（Running）

> 💡 **系统要求**：Windows 10/11 64 位，需开启虚拟化（Hyper-V 或 WSL 2）。  
> 如果安装时提示"WSL 2 installation is incomplete"，打开 PowerShell（管理员）运行：
> ```powershell
> wsl --install
> wsl --update
> ```
> 然后重启电脑。

#### macOS

1. 前往 [Docker Desktop 官网](https://www.docker.com/products/docker-desktop/) 下载 Mac 安装包
2. 拖拽到「应用程序」文件夹并启动
3. 首次启动会请求系统权限，点击「允许」

#### Linux (Ubuntu/Debian)

```bash
# 安装 Docker Engine
curl -fsSL https://get.docker.com | sh

# 将当前用户加入 docker 组（免 sudo）
sudo usermod -aG docker $USER

# 安装 Docker Compose 插件
sudo apt install docker-compose-plugin

# 重新登录终端使权限生效
```

### 第 2 步：验证 Docker 安装

打开终端/命令行，运行：

```bash
docker --version
# 预期输出：Docker version 24.x.x 或更高

docker compose version
# 预期输出：Docker Compose version v2.x.x
```

如果两个命令都有输出，说明安装成功。

### 第 3 步：解压项目并配置环境变量

1. 将项目压缩包解压到任意目录，例如 `D:\occupational_competencies_platform\`
2. 用**文本编辑器**（VS Code / 记事本均可）打开根目录的 `.env` 文件
3. 按需修改以下配置：

```ini
# ═══════════════════════════════════
# 数据库配置（通常无需修改）
# ═══════════════════════════════════
MYSQL_ROOT_PASSWORD=career2026
MYSQL_PASSWORD=career2026
MYSQL_DATABASE=career_platform
MYSQL_USERNAME=career

# ═══════════════════════════════════
# AI 助手配置（★ 必须填写 ★）
# ═══════════════════════════════════
AI_PROVIDER=siliconflow
AI_API_KEY=你的API密钥         # ← 在此填入 SiliconFlow API Key
AI_API_URL=https://api.siliconflow.cn/v1/chat/completions
AI_MODEL=deepseek-ai/DeepSeek-R1-0528-Qwen3-8B

# ═══════════════════════════════════
# 安全配置（生产环境建议修改）
# ═══════════════════════════════════
JWT_SECRET=career-platform-jwt-secret-2026-change-in-production
```

#### 如何获取 SiliconFlow API Key（免费）

1. 访问 [SiliconFlow 官网](https://siliconflow.cn/) 并注册账号
2. 进入「控制台」→「API 密钥管理」→「创建密钥」
3. 复制生成的 `sk-xxxx` 格式密钥，粘贴到 `.env` 的 `AI_API_KEY` 中
4. 新用户附赠免费额度，DeepSeek-R1 Qwen3-8B 模型**完全免费**

### 第 4 步：启动服务

```bash
cd D:\occupational_competencies_platform   # 进入项目根目录
docker compose up -d --build               # 构建并启动所有容器
```

> ⏱ **首次构建** 需要下载基础镜像 + 编译代码，耗时约 **5 ~ 15 分钟**（取决于网络和机器性能）。  
> 后续启动只需 10 秒左右。

### 第 5 步：导入数据

首次启动后，需要将招聘数据导入 MySQL 数据库：

```bash
# 如果本机有 Python 3.x
pip install pymysql
python scripts/import_data.py
```

> 如果本机没有 Python，参见下面「方式二」的 Python 安装步骤。  
> 也可以等 MySQL 容器启动后直接用 `init.sql` 自动导入（已包含建表语句和种子数据）。

### 第 6 步：验证

打开浏览器访问 **http://localhost**，看到平台首页即为成功 🎉

---

## 方式二：本地开发环境（逐个组件配置）

> 适合：需要修改代码、调试功能时使用。

### 🟢 1. Node.js（前端运行环境）

**所需版本**：Node.js 20.x (LTS)

#### 安装步骤

1. 前往 [Node.js 官网](https://nodejs.org/) 下载 **LTS 版本**（推荐 20.x）
2. 安装时勾选 "Add to PATH"
3. 验证安装：

```bash
node --version    # 预期：v20.x.x
npm --version     # 预期：10.x.x
```

#### 安装前端依赖

```bash
cd frontend
npm install       # 安装所有前端 npm 包
```

---

### ☕ 2. JDK（后端运行环境）

**所需版本**：JDK 8（项目基于 Java 8 编写）

#### 安装步骤

**Windows**：
1. 前往 [Eclipse Temurin](https://adoptium.net/) 下载 JDK 8（推荐 Temurin/AdoptOpenJDK）
2. 安装时勾选 "Set JAVA_HOME"
3. 验证：
```bash
java -version     # 预期：openjdk version "1.8.x"
javac -version    # 预期：javac 1.8.x
```

**macOS (Homebrew)**：
```bash
brew install openjdk@8
```

**Linux (Ubuntu)**：
```bash
sudo apt install openjdk-8-jdk
```

#### 安装 Maven（构建工具）

**所需版本**：Maven 3.9+

**Windows**：
1. 前往 [Maven 官网](https://maven.apache.org/download.cgi) 下载二进制包
2. 解压到 `C:\maven`，将 `C:\maven\bin` 加入系统环境变量 PATH
3. 验证：
```bash
mvn --version     # 预期：Apache Maven 3.9.x
```

**macOS**：`brew install maven`  
**Linux**：`sudo apt install maven`

---

### 🐍 3. Python（算法引擎运行环境）

**所需版本**：Python 3.11+

#### 安装步骤

**Windows**：
1. 前往 [Python 官网](https://www.python.org/downloads/) 下载 3.11 版本
2. 安装时**务必勾选** "Add Python to PATH" ☑️
3. 验证：
```bash
python --version   # 预期：Python 3.11.x
pip --version      # 预期：pip 24.x
```

**macOS**：`brew install python@3.11`  
**Linux**：`sudo apt install python3.11 python3.11-venv python3-pip`

#### 安装算法依赖

```bash
cd algorithm
pip install -r requirements.txt
```

主要依赖包说明：

| 包名 | 作用 |
|------|------|
| `fastapi` | Web 框架 |
| `uvicorn` | ASGI 服务器 |
| `xgboost` | 梯度提升树（薪资预测核心） |
| `scikit-learn` | 机器学习工具包 |
| `pandas` / `numpy` | 数据处理 |
| `pymysql` | MySQL 连接驱动 |
| `redis` | Redis 缓存客户端 |
| `networkx` | 技能共现图谱 |

---

### 🐬 4. MySQL（数据库）

**所需版本**：MySQL 8.0

#### 安装步骤

**Windows**：
1. 前往 [MySQL 官网](https://dev.mysql.com/downloads/installer/) 下载 MySQL Installer
2. 选择 "Developer Default" 安装
3. 设置 root 密码为 `career2026`（与 `.env` 保持一致）
4. 安装完成后启动 MySQL 服务

**macOS**：`brew install mysql@8.0 && brew services start mysql`  
**Linux**：`sudo apt install mysql-server-8.0`

#### 初始化数据库

```bash
# 登录 MySQL
mysql -u root -p

# 在 MySQL 命令行中执行：
CREATE DATABASE career_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'career'@'%' IDENTIFIED BY 'career2026';
GRANT ALL PRIVILEGES ON career_platform.* TO 'career'@'%';
FLUSH PRIVILEGES;

# 导入表结构
source config/init.sql;
```

---

### 🔴 5. Redis（缓存）

**所需版本**：Redis 7.x

#### 安装步骤

**Windows**：
- 推荐使用 [Memurai](https://www.memurai.com/) (Windows 版 Redis) 或通过 WSL 安装
- WSL 安装方式：
```bash
wsl
sudo apt install redis-server
sudo service redis-server start
```

**macOS**：`brew install redis && brew services start redis`  
**Linux**：`sudo apt install redis-server && sudo systemctl start redis`

验证：
```bash
redis-cli ping   # 预期输出：PONG
```

---

## 🔧 IDE 推荐配置

| 模块 | 推荐 IDE | 说明 |
|------|----------|------|
| 前端 | VS Code | 安装 Vue - Official + ESLint 插件 |
| 后端 | IntelliJ IDEA | 社区版即可，自带 Maven 支持 |
| 算法 | PyCharm / VS Code | 安装 Python 插件 |

### VS Code 推荐插件

```
Vue - Official           # Vue 3 支持
ESLint                   # 代码规范
Docker                   # Docker 文件语法高亮
MySQL (by Weijan Chen)   # 数据库可视化
Thunder Client           # API 测试
```

---

## ❓ 常见问题

### Q1: Docker 构建时下载镜像很慢怎么办？

配置 Docker 镜像加速器。编辑 Docker Desktop 的 Settings → Docker Engine，添加：

```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

### Q2: 端口被占用怎么办？

```bash
# 查看占用 80 端口的进程
# Windows:
netstat -ano | findstr :80
# Linux/Mac:
lsof -i :80
```

修改 `docker-compose.yml` 中的端口映射，例如改为 `"8888:80"`，然后通过 `http://localhost:8888` 访问。

### Q3: MySQL 容器启动失败？

通常是数据卷残留问题，执行清理后重建：

```bash
docker compose down -v     # 删除数据卷
docker compose up -d       # 重新启动
```

### Q4: npm install 报错？

尝试清除缓存后重新安装：

```bash
cd frontend
rm -rf node_modules package-lock.json
npm cache clean --force
npm install
```

### Q5: AI 助手没有响应？

1. 确认 `.env` 中的 `AI_API_KEY` 已正确填写
2. 确认 SiliconFlow 账户有可用额度
3. 检查后端日志：`docker compose logs -f backend | grep -i ai`
