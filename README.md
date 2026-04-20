# 职业能力大数据服务平台

面向院校治理、教学改革与学生发展的工业化职业能力数据平台。

当前项目已经不再只是岗位展示和学生求职工具集合，而是在持续补齐以下主线：

- 学校教学改革：专业、课程、能力点、岗位族、毕业要求、整改建议一体化分析
- 角色报告中心：管理员、教师、学生都能生成角色报告，且都支持行业分析报告
- 开放服务接口：从演示型查询接口升级为带治理元数据、能力目录和行业快照的平台接口
- 工程化与安全：测试全绿、构建可验证、安全头与限流基础已接入

## 目录结构

```text
occupational_competencies_platform/
├─ frontend/                 Vue 3 + Vite 前端
├─ backend/                  Spring Boot 后端
├─ algorithm/                Python FastAPI 算法服务
├─ config/                   初始化 SQL 与配置样例
├─ scripts/                  启动、校验、运维脚本
├─ deploy/                   部署相关文件
├─ docs/                     补充文档
├─ docker-compose.yml        容器编排
├─ README.md
└─ SETUP.md
```

## 当前核心能力

### 1. 教学改革分析

- 教师侧新增教学改革分析接口
- 输出专业蓝图、课程模块、毕业要求、岗位族、能力维度、整改动作、治理评分
- 供需分析不再只是关键词差集，已补充样本元数据和置信度

### 2. 报告中心

- 管理员可生成运营分析、供需分析、行业治理报告、综合报告
- 教师可生成供需分析、教学建议、专业行业分析、能力缺口观察、综合报告
- 学生可生成求职分析、行业机会报告、技能差距分析、薪资趋势参考、综合报告
- 报告内容已带上治理元数据、行业视角、趋势指标、样本置信度

### 3. 开放平台

- 公开岗位查询
- 公开概览与技能排名
- 开放平台元数据 `/api/v1/open/meta`
- 能力目录 `/api/v1/open/capabilities`
- 行业快照 `/api/v1/open/analysis/industry`

## 环境要求

### Docker 方式

- Docker Desktop 4.x+

### 本地开发方式

- Node.js 20+
- JDK 8
- Maven 3.9+
- Python 3.11+ 或 3.12+
- MySQL 8
- Redis 7

本机已验证的 Python 路径示例：

```powershell
D:\Python\python.exe
```

## 快速启动

### 方式一：Docker

```powershell
docker compose up -d --build
```

前端默认访问：

- [http://localhost](http://localhost)

后端接口文档：

- [http://localhost:8080/doc.html](http://localhost:8080/doc.html)

### 方式二：本地开发

#### 前端

```powershell
cd frontend
npm install
npm run dev
```

#### 后端

```powershell
cd backend
mvn spring-boot:run
```

#### 算法服务

```powershell
cd algorithm
& "D:\Python\python.exe" -m pip install -r requirements.txt
& "D:\Python\python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

健康检查：

- [http://localhost:8000/health](http://localhost:8000/health)

## 配置说明

后端主要配置文件：

- [application.yml](C:/Users/32020/Desktop/occupational%20_competencies_platform/backend/src/main/resources/application.yml)

关键环境变量：

- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `ALGORITHM_SERVICE_URL`
- `JWT_SECRET`
- `AI_API_KEY`
- `AI_MODEL`

建议把本地差异配置放到：

- `backend/src/main/resources/application-local.yml`
- 根目录 `.env`

## 已完成的工程化校验

- `backend` 执行 `mvn test` 通过
- `frontend` 执行 `npm run build` 通过
- 开放 API 增加了版本、请求追踪、数据分级、限流策略等响应头
- 安全配置已增加基础安全头

## 仍建议继续补齐的部分

- 压力测试与性能基线
- 登录限流与验证码
- 字段级权限与租户隔离
- 报告版本审批流
- 更完整的隐私分级与数据脱敏策略
- 算法服务容器与本地环境的一致性校验

## 相关文档

- [SETUP.md](./SETUP.md)
- [DEMO.md](./DEMO.md)
- [docs/open-api-governance.md](./docs/open-api-governance.md)
- [docs/security-performance-baseline.md](./docs/security-performance-baseline.md)
