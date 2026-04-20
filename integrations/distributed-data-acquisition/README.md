# 分布式数据采集模块

## 项目概述

本项目是一个面向招聘数据场景的分布式采集系统，当前重点实现了智联招聘渠道的数据采集、任务调度、节点管理、日志监控和鉴权配置管理。

系统采用“调度中心 + 爬虫节点 + 消息队列”的分布式架构：

- 调度中心负责任务管理、任务拆分、配置管理、节点状态监控和接口服务
- 爬虫节点负责消费任务、执行实际采集、处理反爬逻辑并上传结果
- RabbitMQ 负责任务消息和结果消息的异步传递
- MySQL 负责持久化任务、配置、日志和采集结果
- Redis 作为缓存与辅助组件使用

这个仓库当前以 Python 为主，调度中心实际实现为 FastAPI，爬虫节点为 Python requests/Selenium 组合，不是旧文档中提到的 Spring Boot 版本。

## 适用场景

- 招聘职位数据的定时采集与增量更新
- 多关键词、多城市、多分类组合下的大规模任务拆分
- 多节点并发采集
- 需要对智联招聘登录态、token 过期、验证态进行自动恢复的场景

## 核心能力

- 分布式任务调度：任务按关键词、城市、分类码、页码等维度拆分后投递到消息队列，由多个节点并发执行
- 节点心跳与监控：节点定期上报状态，调度中心可查看节点在线情况与任务执行情况
- 智联招聘采集链路：内置智联搜索接口的兼容请求和主请求逻辑
- 采集结果标准化：统一输出职位标题、薪资、城市、企业信息、标签、发布时间等字段
- 反爬与鉴权恢复：支持本地 `.env`、调度中心配置、浏览器登录态提取三层鉴权来源
- 本地调试友好：提供诊断脚本、鉴权刷新脚本和一键启动脚本

## 技术栈

| 组件 | 技术实现 | 说明 |
|------|----------|------|
| 调度中心 | FastAPI + SQLAlchemy + Pydantic | 任务管理、节点管理、配置管理、监控接口 |
| 爬虫节点 | Python 3.11 + requests + Selenium | 任务消费、接口请求、浏览器登录态提取 |
| 消息队列 | RabbitMQ | 任务分发、节点通信 |
| 数据库 | MySQL 8.0 | 任务、日志、配置、结果数据存储 |
| 缓存 | Redis 7 | 缓存与辅助状态 |
| 浏览器自动化 | Microsoft Edge + msedgedriver + Selenium | 智联登录态提取、token 刷新 |

## 当前交付状态

当前仓库已完成分布式采集模块第一阶段交付，具备：

- 任务创建、拆分、分发、执行与回写
- MySQL 元数据与职位明细写入
- HDFS 原始职位明细双写

当前 HDFS 原始明细路径固定为：

```text
/data/recruitment_crawler/ods/job_detail/channel=zhaopin/dt=<YYYY-MM-DD>/
```

当前阶段已适合与 Spring Boot 后端进入联调整合，推荐先对接任务、节点、监控、日志等接口，以及 HDFS 原始数据位置说明。  
后续 Hive/Spark 分析层与推荐结果层将在下一阶段继续完善。

## 目录结构

```text
DistributedDataAcquisition_V3/
├── scheduler-center/              # 调度中心
│   ├── app/
│   │   ├── api/                   # REST API
│   │   ├── db/                    # 数据库会话
│   │   ├── models/                # ORM 模型
│   │   ├── mq/                    # RabbitMQ 生产/消费
│   │   ├── schemas/               # 请求响应模型
│   │   └── services/              # 调度、任务服务
│   ├── config.py                  # 调度中心配置
│   └── requirements.txt
├── crawler-node/                  # 爬虫节点
│   ├── core/
│   │   ├── spider/                # 渠道爬虫实现
│   │   ├── worker.py              # 节点执行器
│   │   └── logger.py              # 日志
│   ├── mq/                        # RabbitMQ 消费/生产
│   ├── utils/
│   │   ├── http_client.py         # HTTP 客户端
│   │   ├── heartbeat.py           # 心跳上报
│   │   ├── zhaopin_auth.py        # 智联鉴权读取/刷新
│   │   └── zhaopin_category_resolver.py
│   ├── main.py                    # 节点入口
│   ├── diagnose_zhaopin.py        # 诊断脚本
│   ├── refresh_zhaopin_auth.py    # 鉴权刷新脚本
│   └── requirements.txt
├── deploy/                        # 部署与初始化文件
├── docs/                          # 设计文档和 API 文档
├── category_label/                # 职类分类辅助文件
├── start*.bat / start*.txt        # 本地启动脚本
└── README.md
```

## 系统架构

```text
前端 / 调用方
      |
      v
调度中心 FastAPI
  - 任务管理
  - 节点管理
  - 配置管理
  - 监控统计
      |
      +---- MySQL
      |
      +---- Redis
      |
      +---- RabbitMQ <----> 爬虫节点 1..N
                             - 任务消费
                             - 智联接口请求
                             - 鉴权检测 / 自动刷新
                             - 数据标准化
                             - 结果回传
```

## 分布式模块说明

### 1. 调度中心

调度中心位于 `scheduler-center/`，职责包括：

- 提供任务、节点、监控、日志、配置相关 API
- 管理系统配置项，包含智联渠道鉴权字段
- 启动消息消费与定时调度线程
- 汇总节点心跳和任务执行状态

当前主要 API 路由：

- `/api/tasks`：任务管理
- `/api/workers`：节点管理
- `/api/monitor`：监控统计
- `/api/logs`：日志查询
- `/api/config`：系统配置与智联鉴权管理

与智联鉴权最相关的接口：

- `GET /api/config/zhaopin/auth/status`
  - 返回当前智联鉴权摘要状态
- `POST /api/config/zhaopin/auth/refresh`
  - 触发一次智联鉴权刷新，并将结果写回配置中心
- `GET /api/config/channel/zhaopin`
  - 返回智联渠道配置，供爬虫节点拉取

### 2. 爬虫节点

爬虫节点位于 `crawler-node/`，职责包括：

- 启动节点并注册心跳
- 从 RabbitMQ 消费任务分片
- 执行智联招聘接口请求
- 自动检测鉴权失效、验证态和空结果异常
- 标准化职位字段并输出结果

节点初始化时会：

1. 加载项目根目录 `.env`
2. 输出智联鉴权配置摘要
3. 启动 Worker
4. 启动任务消费者
5. 启动心跳线程

### 3. RabbitMQ 消息流

典型链路如下：

1. 调度中心创建任务
2. 任务被拆分为多个 shard
3. shard 投递到 RabbitMQ
4. 某个爬虫节点消费该 shard
5. 节点执行智联采集逻辑
6. 结果写库或通过消息回传
7. 节点持续发送心跳，调度中心更新监控状态

### 4. 任务拆分与并发执行

当前系统面向招聘采集场景，任务可以从多个维度拆分：

- 关键词
- 城市
- 职类编码
- 页码

拆分后的任务可以由多个节点同时处理，从而提升吞吐量和可扩展性。  
当单个节点失效时，调度中心可以基于节点状态和任务状态重新调度未完成分片。

## 智联招聘采集链路

智联采集实现主要位于：

- `crawler-node/core/spider/zhaopin_spider.py`
- `crawler-node/utils/zhaopin_auth.py`

采集链路包含两种请求方式：

- 兼容请求：更贴近你提供的 `search/positions` 请求协议
- 主请求：项目原始请求协议，请求头中带 `Authorization`、`C1K`、`Cookie`、`x-zp-*`

爬虫会先尝试兼容请求，再尝试主请求。  
如果请求过程中发现鉴权异常、验证态或默认 token，会自动进入鉴权恢复流程。

## 智联 token 过期解决方案

这是本项目最关键、也最实用的一部分。

### 1. 问题背景

智联招聘接口调用依赖一组动态鉴权字段，主要包括：

- `token_mme`
- `token_c1k`
- `cookie`
- `page_request_id`
- `request_id`
- `client_id`

这些字段会随登录态、浏览器上下文和服务端风控变化而变化。  
如果仍然使用旧 token，常见现象是：

- HTTP `401` / `403`
- 返回 `isVerification=1`
- 返回 message 中包含登录、验证、auth 相关提示
- 返回 200 但 `list/results` 为空

### 2. 当前实现的总体策略

项目采用“三层来源 + 按需刷新”的方案：

1. 优先使用调度中心中的智联配置
2. 调度中心不可用时，退回使用项目根目录 `.env`
3. 仅当检测到失效或没有可用鉴权时，才自动拉起 Edge 浏览器重新获取

这意味着：

- 平时不会每次都重新打开浏览器
- 默认先复用已经获取到的 token/cookie
- 只有确认失效或你手工强制刷新时才重新获取

### 3. 如何判断 token 已过期

在 `ZhaopinAuthManager.should_refresh()` 中，系统会在以下场景判定需要刷新：

- HTTP 状态码是 `401` 或 `403`
- 接口返回 `data.isVerification == 1`
- 返回 message 出现 `login`、`auth`、`验证`
- 当前 token 仍是默认值
- 请求结果为空且本地鉴权字段不完整

这是“过期了再重新获取”的核心判定逻辑。

### 4. 如何获取新的 token

获取新 token 的实现位于 `crawler-node/utils/zhaopin_auth.py` 中。

刷新时的顺序如下：

1. 节点优先调用调度中心接口 `/api/config/zhaopin/auth/refresh`
2. 如果调度中心不可用，节点在本机直接拉起 Edge 浏览器
3. 打开智联搜索页，等待你登录或完成验证码
4. 从浏览器里提取以下信息：
   - `cookie`
   - `localStorage`
   - `sessionStorage`
   - 网络日志中的 `MmEwMD` 和 `c1K5tw0w6_`
5. 如果网络日志不可用，则从 cookie 中兜底提取：
   - `at -> token_mme`
   - `rt -> token_c1k`
   - `x-zp-client-id -> client_id`

这个兜底逻辑是当前项目里非常重要的一点，因为 Edge 某些版本不支持 `performance` 日志读取，但 cookie 中仍能拿到有效 token。

### 5. 获取后如何保存

刷新成功后，鉴权字段会被写回：

- 运行时内存配置
- 项目根目录 `.env`
- 调度中心配置表（如果走的是调度中心刷新）

当前 `.env` 中会维护：

- `ZHAOPIN_TOKEN_MME`
- `ZHAOPIN_TOKEN_C1K`
- `ZHAOPIN_COOKIE`
- `ZHAOPIN_PAGE_REQUEST_ID`
- `ZHAOPIN_REQUEST_ID`
- `ZHAOPIN_CLIENT_ID`
- `ZHAOPIN_AUTH_UPDATED_AT`
- `ZHAOPIN_AUTH_STATUS`

### 6. 为什么要保留浏览器窗口

在某些场景下，token 刷新成功后立即关闭浏览器，智联登录态可能很快失效或后续调试不方便。  
因此当前项目支持在成功提取 token 后保留 Edge 窗口不关闭，用于继续保持登录态。

相关环境变量：

- `EDGE_DRIVER_PATH`
- `EDGE_BINARY_PATH`
- `ZHAOPIN_AUTH_HEADLESS=false`
- `ZHAOPIN_AUTH_KEEP_BROWSER_OPEN=true`

### 7. 当前方案的边界

这个方案解决的是“自动检测失效并自动进入重新获取流程”，不是纯无人值守绕过验证码。

准确地说，当前方案具备：

- 自动检测 token 失效
- 自动触发刷新
- 自动从浏览器提取 token/cookie
- 自动回写本地与配置中心

但在以下情况下仍可能需要人工参与：

- 首次登录智联账号
- 短信验证码或滑块验证
- 账号被风控或登录态被服务端判定异常

## 环境配置

项目根目录 `.env` 是爬虫节点和调度中心共用的重要配置入口。

至少需要关注这些配置：

```env
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
RABBITMQ_USER=admin
RABBITMQ_PASSWORD=admin123

DATABASE_URL=mysql+pymysql://root:password@127.0.0.1:3306/zhaopin_db?charset=utf8mb4
REDIS_URL=redis://localhost:6379/0

HOST=0.0.0.0
PORT=8000
DEBUG=true
LOG_LEVEL=INFO

ZHAOPIN_TOKEN_MME=
ZHAOPIN_TOKEN_C1K=
ZHAOPIN_COOKIE=
ZHAOPIN_PAGE_REQUEST_ID=
ZHAOPIN_REQUEST_ID=
ZHAOPIN_CLIENT_ID=
ZHAOPIN_AUTH_UPDATED_AT=
ZHAOPIN_AUTH_STATUS=

EDGE_DRIVER_PATH=F:\Spring26\HuaDi\DistributedDataAcquisition_V3\tools\edgedriver\msedgedriver.exe
EDGE_BINARY_PATH=C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe
ZHAOPIN_AUTH_HEADLESS=false
ZHAOPIN_AUTH_KEEP_BROWSER_OPEN=true
```

## 安装与启动

### 1. Python 依赖

在项目根目录执行：

```powershell
python -m venv venv
.\venv\Scripts\Activate.ps1

pip install -r .\crawler-node\requirements.txt
pip install -r .\scheduler-center\requirements.txt
```

### 2. 基础服务

请先准备并启动：

- MySQL
- Redis
- RabbitMQ

如果使用仓库中的部署文件，可参考 `deploy/docker-compose.yml`。

### 3. 启动调度中心

```powershell
cd F:\Spring26\HuaDi\DistributedDataAcquisition_V3\scheduler-center
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 4. 启动爬虫节点

```powershell
cd F:\Spring26\HuaDi\DistributedDataAcquisition_V3\crawler-node
python .\main.py
```

## 常用命令

### 1. 复用现有智联鉴权

默认不会强制重新获取，而是先使用 `.env` 中已有的 token/cookie：

```powershell
cd F:\Spring26\HuaDi\DistributedDataAcquisition_V3
python .\crawler-node\refresh_zhaopin_auth.py --json-output
```

如果当前 `.env` 已有可用鉴权，返回结果会提示“已复用项目根目录 `.env` 中的现有智联鉴权信息”。

### 2. 强制重新获取智联鉴权

只有你明确需要时才使用：

```powershell
cd F:\Spring26\HuaDi\DistributedDataAcquisition_V3
python .\crawler-node\refresh_zhaopin_auth.py --force --timeout 180 --persist-env --json-output
```

执行后会拉起 Edge，等待你登录，提取到新 token 后回写 `.env`。

### 3. 诊断智联采集

```powershell
cd F:\Spring26\HuaDi\DistributedDataAcquisition_V3
python .\crawler-node\diagnose_zhaopin.py --case python_keyword_only
```

可用于排查：

- token 是否生效
- 是否命中 `isVerification=1`
- 接口是否返回职位列表
- 请求头和响应摘要是否正常

### 4. 查看调度中心中的智联鉴权状态

```powershell
curl http://127.0.0.1:8000/api/config/zhaopin/auth/status
```

### 5. 通过调度中心触发一次刷新

```powershell
curl -X POST http://127.0.0.1:8000/api/config/zhaopin/auth/refresh ^
  -H "Content-Type: application/json" ^
  -d "{\"reason\":\"manual_refresh\",\"triggered_by\":\"local_test\",\"persist_env\":true,\"timeout_seconds\":180}"
```

## 典型运行流程

### 日常采集流程

1. 调度中心创建采集任务
2. 任务拆分为多个 shard
3. shard 投递到 RabbitMQ
4. 爬虫节点消费任务并读取当前智联鉴权
5. 如果鉴权可用，则直接请求智联接口
6. 如果返回验证态或失效信息，则自动触发刷新
7. 刷新成功后重试请求
8. 采集结果标准化并入库/回传

### 智联 token 失效恢复流程

1. 节点发起智联请求
2. 接口返回 `401/403`、`isVerification=1` 或空结果异常
3. `should_refresh()` 判定为失效
4. 节点尝试调用调度中心刷新接口
5. 若调度中心不可用，则本地直接拉起 Edge
6. 人工完成登录或验证码
7. 从浏览器提取新 token/cookie
8. 回写 `.env` 和运行时配置
9. 重新发起智联请求

## 已知现象与排查建议

### 1. `读取 Edge performance 日志失败`

某些 EdgeDriver 版本不支持 `performance` 日志类型。  
当前项目已经做了兜底：即使网络日志读取失败，仍可通过 cookie 中的 `at`、`rt` 提取 token。

### 2. `localhost:8000 actively refused`

说明调度中心未启动。  
此时爬虫节点会直接回退到本地 `.env` 和本地 Edge 刷新流程，不一定影响单机调试。

### 3. `isVerification=1`

表示智联接口把当前请求识别为验证态。  
这时应优先：

- 检查登录态是否最新
- 重新执行强制刷新
- 保持 Edge 登录窗口
- 再次运行诊断脚本观察请求摘要

### 4. 获取到 token 但仍无职位结果

这种情况通常不是“完全没鉴权”，而是“鉴权字段与当前浏览器态不一致”或者接口进入风控验证态。  
建议结合以下命令重新验证：

```powershell
python .\crawler-node\refresh_zhaopin_auth.py --force --persist-env --json-output
python .\crawler-node\diagnose_zhaopin.py --case python_keyword_only
```

## 开发说明

如果你要继续扩展本项目，建议优先关注以下文件：

- `scheduler-center/app/main.py`
- `scheduler-center/app/api/endpoints/config.py`
- `scheduler-center/app/services/task_service.py`
- `crawler-node/main.py`
- `crawler-node/core/spider/zhaopin_spider.py`
- `crawler-node/utils/zhaopin_auth.py`
- `crawler-node/diagnose_zhaopin.py`

## 文档索引

- 详细设计文档：`docs/DESIGN.md`
- API 文档：`docs/API.md`
- 功能对照表：`docs/FEATURE_TABLE.md`

## 总结

本项目的价值不只在于“能爬智联”，更在于已经形成了一套可落地的分布式采集框架和实际可用的智联鉴权恢复方案。

对于智联招聘这类强风控站点，项目当前采取的核心策略是：

- 默认复用已有 token
- 失效时自动检测
- 按需触发刷新
- 浏览器态提取兜底
- 本地 `.env` 与配置中心双写回

这使得系统比“手工复制 token”更稳定，也比“每次都重新登录”更适合持续运行的分布式采集场景。
