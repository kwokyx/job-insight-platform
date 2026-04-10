# 🎯 职业能力大数据服务平台

> 基于真实招聘市场数据的就业分析与智能辅助平台。  
> 为求职者提供**薪资洞察、技能图谱、岗位推荐、AI 职业规划**等一站式服务。

---

## 📌 项目概述

本平台从前程无忧 (51job.com) 采集了 **2,444 条真实岗位数据**，覆盖 1,263 家企业、12 大技能类目，通过前后端分离 + 微服务算法引擎的架构，将原始招聘数据转化为可交互的可视化洞察与智能预测工具。

**核心亮点**：
- 🔍 **7+ 交互式 ECharts 图表** — 城市热力图、行业雷达、技能排行、薪资趋势等
- 🤖 **AI 职业顾问** — 接入 DeepSeek 大模型，支持 SSE 流式对话 + 思考链展示
- 📊 **机器学习薪资预测** — 基于 XGBoost 模型，输入城市/学历/经验/技能即可估算薪资区间
- 🌓 **深色/浅色主题切换** — 全局响应式设计，适配桌面与移动端
- 🌐 **一键 Docker 部署** — 含 Cloudflare Tunnel 公网穿透，开箱即用

---

## 🏗 技术架构

```
┌─────────────────────────────────────────────────────┐
│                    用户浏览器                         │
│              http://localhost (80)                   │
└──────────────────────┬──────────────────────────────┘
                       │
              ┌────────▼────────┐
              │   Nginx (前端)   │  Vue 3 SPA 静态资源
              │   Port: 80      │  + 反向代理 /api → 后端
              └────────┬────────┘
                       │ /api/*
              ┌────────▼────────┐
              │  Spring Boot    │  RESTful API
              │  Port: 8080     │  JWT 认证 / Redis 缓存
              └───┬────────┬────┘
                  │        │
         ┌────────▼──┐  ┌──▼────────┐
         │  MySQL 8   │  │ Redis 7   │
         │  Port:3306 │  │ Port:6379 │
         └────────────┘  └───────────┘
                  │
              ┌───▼──────────┐
              │ FastAPI 算法  │  XGBoost / LightGBM
              │ Port: 8000   │  薪资预测 / 技能图谱
              └──────────────┘
```

---

## 🛠 技术栈

| 层级 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **前端** | Vue 3 + Vite + ECharts | Vue 3.5 / Vite 6 | 响应式 SPA，毛玻璃主题，7+ 可视化图表 |
| **后端** | Spring Boot + MyBatis-Plus | Boot 2.7 / Java 8 | RESTful API，JWT 认证，Redis 缓存 |
| **算法** | Python FastAPI + XGBoost | Python 3.11 | 薪资预测、技能图谱、趋势分析 |
| **AI 助手** | SiliconFlow (DeepSeek) | R1-0528-Qwen3-8B | SSE 流式对话，意图识别，平台数据增强 |
| **数据库** | MySQL 8.0 + Redis 7 | — | 结构化存储 + 高速缓存 |
| **部署** | Docker Compose | — | 一键 6 容器编排，含 Cloudflare 穿透 |

---

## 📁 项目结构

```
occupational_competencies_platform/
│
├── frontend/                  # 🖥  前端工程 (Vue 3 + Vite)
│   ├── src/
│   │   ├── views/             #    页面组件 (概览/岗位/洞察/AI助手/个人中心)
│   │   ├── components/        #    通用 UI 组件 (PremiumCard/GlowButton/StatWidget)
│   │   ├── assets/styles/     #    全局样式 + CSS 变量主题系统
│   │   ├── store/             #    Pinia 状态管理 (auth/theme)
│   │   ├── router/            #    Vue Router 路由配置
│   │   └── api.js             #    Axios API 接口封装
│   ├── nginx.conf             #    生产环境 Nginx 反向代理配置
│   ├── Dockerfile             #    多阶段构建 (Node 构建 → Nginx 托管)
│   └── package.json
│
├── backend/                   # ☕  后端工程 (Spring Boot 2.7)
│   ├── src/main/java/com/career/platform/
│   │   ├── controller/        #    REST 控制器
│   │   ├── service/           #    业务层
│   │   ├── entity/            #    数据实体
│   │   ├── mapper/            #    MyBatis-Plus 数据映射
│   │   ├── config/            #    安全/CORS/Redis/AI 配置
│   │   └── util/              #    JWT/AI 工具类
│   ├── src/main/resources/
│   │   └── application.yml    #    Spring Boot 主配置文件
│   ├── Dockerfile             #    多阶段构建 (Maven 编译 → JRE 运行)
│   └── pom.xml
│
├── algorithm/                 # 🧠  算法引擎 (Python FastAPI)
│   ├── app/
│   │   ├── main.py            #    FastAPI 入口 + 路由
│   │   ├── predictor.py       #    XGBoost 薪资预测模型
│   │   └── skill_graph.py     #    技能共现图谱生成
│   ├── requirements.txt       #    Python 依赖清单
│   └── Dockerfile
│
├── config/                    # ⚙️  基础配置
│   ├── init.sql               #    MySQL 初始化 (建表 + 种子数据)
│   └── nginx.conf             #    备用 Nginx 配置
│
├── data/                      # 📦  数据资产
│   ├── processed/             #    清洗后的 JSON 数据文件
│   └── raw/                   #    原始采集数据
│
├── scripts/                   # 🔧  运维脚本
│   └── import_data.py         #    JSON → MySQL 数据导入工具
│
├── docker-compose.yml         # 🐳  容器编排 (6 服务一键启动)
├── .env                       # 🔐  环境变量 (密钥/密码/API Key)
└── README.md                  # 📖  项目说明 (本文件)
```

---

## ⚡ 功能模块

### 📊 数据分析与可视化
| 功能 | 描述 |
|------|------|
| **概览大屏** | 平台 KPI 一览（总岗位数、平均薪资、企业覆盖等）+ 城市/行业 TOP 10 条形图 |
| **数据洞察** | 7 个 ECharts 交互式图表：岗位分布饼图、行业柱状图、技能雷达图、薪资箱线图等 |
| **岗位大厅** | 高级筛选（城市/行业/薪资/学历/经验）、分页浏览、职位详情 |
| **技能图谱** | 技能热度排行、共现关系网络、D3 力导向图 |

### 🤖 智能 AI 功能
| 功能 | 描述 |
|------|------|
| **AI 职业顾问** | 接入 DeepSeek 大模型的流式聊天，附带思考链可视化展示 |
| **AI 薪资预测** | 基于 XGBoost 的机器学习模型，输入维度参数即可获得预测区间 |
| **意图识别** | AI 自动识别用户意图，注入平台真实数据（薪资、技能、城市分布）增强回答 |

### 👤 账号与个性化
| 功能 | 描述 |
|------|------|
| **注册 / 登录** | JWT Token 认证体系，支持用户名 + 密码登录 |
| **个人中心** | 头像更换、昵称修改、邮箱换绑、密码修改、角色展示 |
| **主题切换** | 全局深色 / 浅色模式，一键切换，自动持久化 |

---

## 🚀 快速启动

### 前提条件
- 已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)

### 一键启动
```bash
# 1. 克隆或解压项目
# 2. 进入项目根目录
cd occupational_competencies_platform

# 3. 构建并启动所有服务（首次约 5-10 分钟）
docker compose up -d --build

# 4. 导入招聘数据到数据库
pip install pymysql
python scripts/import_data.py
```

### 访问平台

| 方式 | 地址 |
|------|------|
| 🖥 本地访问 | http://localhost |
| 🌐 公网访问 | 运行 `docker compose logs tunnel` 查看 Cloudflare 分配的临时域名 |
| 📄 API 文档 | http://localhost:8080/doc.html |

### 停止服务
```bash
docker compose down          # 停止并移除容器（保留数据卷）
docker compose down -v       # 停止并清空所有数据
```

---

## 📚 相关文档

| 文档 | 说明 |
|------|------|
| [配置指南 (SETUP.md)](./SETUP.md) | 从零开始的完整环境搭建教程 |
| [演示指南 (DEMO.md)](./DEMO.md) | 开发调试与生产部署的详细运行步骤 |

---

## 🔐 环境变量

项目根目录的 `.env` 文件用于集中管理所有敏感配置：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `MYSQL_PASSWORD` | 数据库密码 | `career2026` |
| `AI_API_KEY` | SiliconFlow API 密钥 | （需填写，可免费获取） |
| `AI_MODEL` | AI 大模型名称 | `deepseek-ai/DeepSeek-R1-0528-Qwen3-8B` |
| `JWT_SECRET` | JWT 签名密钥 | （建议生产环境修改） |

> ⚠️ **注意**：请勿将包含真实 API Key 的 `.env` 文件提交到公开仓库。

---

## 📊 数据来源

平台数据来自前程无忧 (51job.com) 公开招聘信息，经脚本采集、清洗、去重后导入 MySQL：

- 📌 **2,444** 个真实职位
- 🏢 **1,263** 家企业
- 🏷 **12** 类技能标签
- 🔗 **6,804** 条技能关联

---

## 🧰 常用命令速查

```bash
docker compose up -d              # 启动所有服务
docker compose down               # 停止所有服务
docker compose up -d --build      # 重新构建并启动
docker compose restart backend    # 仅重启后端
docker compose logs -f backend    # 实时查看后端日志
docker compose logs tunnel        # 查看公网穿透地址
docker compose ps                 # 查看各容器运行状态
```

---

## 📜 开源许可

本项目仅用于学习与毕业设计用途。
