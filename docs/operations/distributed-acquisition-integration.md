# 分布式采集子系统接入说明

## 1. 目标

本仓库已按独立子系统方式接入分布式数据采集模块，目标是：

- 主项目继续作为业务展示与分析平台运行
- 分布式采集模块独立负责任务调度、节点管理、队列分发、采集入库
- 采集结果先进入 `crawl_*` 运行时表，再同步到 `biz_*` 业务表
- 主机运行中心侧服务，虚拟机运行爬虫节点，边界清晰、可扩容、可审计

当前接入目录：

- `integrations/distributed-data-acquisition/scheduler-center`
- `integrations/distributed-data-acquisition/crawler-node`

## 2. 接入原则

- 不把爬虫代码并入现有 `backend`
- 不让采集模块直接写 `biz_*` 业务表
- 先写 `crawl_job_posting`，再通过同步脚本进入业务表
- 采集中心与节点通过 RabbitMQ 解耦
- 主机负责数据库、缓存、消息队列、调度中心
- 虚拟机负责爬虫执行与浏览器登录态维护

## 3. 组件分工

主机：

- `mysql`，端口 `3307`
- `redis`，端口 `6379`
- `rabbitmq`，端口 `5672`
- `rabbitmq management`，端口 `15672`
- `scheduler-center`，端口 `8001`
- 现有 `backend`、`algorithm`、`frontend`

虚拟机：

- `crawler-node`
- Edge 浏览器
- Edge Driver
- Python 3.11

## 4. 网络规划

推荐虚拟机网络模式：`桥接`

推荐原因：

- 虚拟机和主机位于同一局域网，互访稳定
- 节点配置直接使用主机局域网 IP
- 少依赖虚拟机软件的 NAT 映射规则

示例：

- 主机 IP：`192.168.1.10`
- 调度中心：`http://192.168.1.10:8001`
- RabbitMQ：`192.168.1.10:5672`
- RabbitMQ 控制台：`http://192.168.1.10:15672`
- MySQL：`192.168.1.10:3307`
- Redis：`192.168.1.10:6379`

Windows 防火墙至少放行：

- `3307/TCP`
- `6379/TCP`
- `5672/TCP`
- `15672/TCP`
- `8001/TCP`

如果启用 HDFS，还需放行：

- `8020/TCP`
- `9870/TCP`

## 5. 数据流

数据流按以下路径运行：

1. 前端或接口在调度中心创建采集任务
2. 调度中心写入 `crawl_task` 和 `crawl_task_shard`
3. 调度中心把分片任务发送到 RabbitMQ
4. 虚拟机上的 `crawler-node` 消费任务并采集职位数据
5. 调度中心消费 `data.queue`，把结果写入 `crawl_job_posting`
6. 执行同步脚本，把 `crawl_job_posting` 增量同步到 `biz_job_posting` 和 `biz_company`
7. 业务平台继续从 `biz_*` 表读取数据

## 6. 表结构约定

采集运行态表：

- `crawl_task`
- `crawl_task_shard`
- `crawl_worker`
- `crawl_task_log`
- `system_config`
- `crawl_job_posting`

业务消费表：

- `biz_job_posting`
- `biz_company`
- 其他分析和推荐相关 `biz_*` 表

兼容策略：

- 采集模块原始实现使用 `job_data`
- 当前接入已改为使用环境变量 `JOB_DATA_TABLE`
- 默认目标表为 `crawl_job_posting`

## 7. 启动主机侧服务

在仓库根目录执行：

```powershell
docker compose --profile acquisition up -d --build
```

启动后检查：

```powershell
docker compose ps
docker compose logs -f scheduler-center
docker compose logs -f rabbitmq
```

验收点：

- `mysql` healthy
- `redis` healthy
- `rabbitmq` healthy
- `scheduler-center` healthy
- 浏览器访问 `http://localhost:8001/api/monitor/dashboard` 返回响应

## 8. 虚拟机节点配置

在虚拟机中进入：

```text
integrations/distributed-data-acquisition/crawler-node
```

参考 `crawler-node/.env.vm.example` 创建 `.env`。

关键配置示例：

```env
NODE_NAME=vm-worker-01
SCHEDULER_HOST=192.168.1.10
SCHEDULER_PORT=8001
SCHEDULER_API_PREFIX=/api

RABBITMQ_HOST=192.168.1.10
RABBITMQ_PORT=5672
RABBITMQ_USER=admin
RABBITMQ_PASSWORD=admin123
RABBITMQ_VHOST=/

MYSQL_HOST=192.168.1.10
MYSQL_PORT=3307
MYSQL_USER=career
MYSQL_PASSWORD=career2026
MYSQL_DATABASE=career_platform

REDIS_HOST=192.168.1.10
REDIS_PORT=6379

HEADLESS=false
BROWSER_TYPE=edge
ZHAOPIN_AUTH_PERSIST_ENV=true
```

## 9. 虚拟机节点启动

建议先使用本地 Python 运行，不要第一阶段就强行容器化节点。

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python main.py
```

验收点：

- 节点能成功访问调度中心
- 节点能连接 RabbitMQ
- 节点开始发送心跳
- `crawl_worker` 表出现节点记录

## 10. 智联登录态与浏览器要求

建议把负责登录和令牌刷新的节点固定在一个 Windows 环境中，不要频繁迁移。

要求：

- 安装 Microsoft Edge
- 安装对应版本 Edge Driver，或允许 `webdriver-manager` 自动下载
- 首次调试时设置 `HEADLESS=false`

如果调度中心调用 `/api/config/zhaopin/auth/refresh`：

- 浏览器会打开智联页面
- 人工完成登录或验证码
- 成功后令牌写回 `system_config`
- 如启用本地持久化，也会写入节点 `.env`

## 11. 业务表同步

增量同步：

```powershell
.\scripts\sync_crawl_to_business.ps1
```

全量覆盖同步：

```powershell
.\scripts\sync_crawl_to_business.ps1 -FullRefresh
```

说明：

- `config/sync_crawl_to_business.sql` 是破坏性全量覆盖脚本
- `config/sync_crawl_to_business_incremental.sql` 是推荐的生产增量同步脚本

## 12. 工业级上线顺序

建议严格按顺序执行：

1. 启动主机侧基础设施和调度中心
2. 校验数据库和队列连通性
3. 启动一个虚拟机节点
4. 校验心跳与节点注册
5. 人工刷新一次智联登录态
6. 创建一个低规模测试任务
7. 校验 `crawl_task`、`crawl_task_shard`、`crawl_job_posting`
8. 执行一次增量同步脚本
9. 校验 `biz_job_posting` 与前端展示
10. 再增加第二个节点做扩容验证

## 13. 回滚策略

回滚边界：

- 停掉 `scheduler-center`
- 停掉 `rabbitmq`
- 停掉虚拟机 `crawler-node`
- 不影响现有 `backend`、`algorithm`、`frontend`

回滚命令：

```powershell
docker compose --profile acquisition down
```

如果只需阻断采集但保留数据：

- 不删除 MySQL 卷
- 不删除 RabbitMQ 卷

## 14. 故障排查

虚拟机节点无法连接调度中心：

- 检查虚拟机是否桥接到正确网卡
- 检查主机防火墙是否放行 `8001`
- 在虚拟机执行 `curl http://主机IP:8001/api/monitor/dashboard`

虚拟机节点无法消费任务：

- 检查 RabbitMQ `5672` 连通性
- 检查账号密码和 vhost
- 打开 `http://主机IP:15672` 检查队列积压

任务创建成功但没有入库：

- 查看 `scheduler-center` 日志
- 检查 `JOB_DATA_TABLE` 是否为 `crawl_job_posting`
- 检查 `crawl_job_posting` 表结构是否存在

智联认证频繁失效：

- 固定单一节点负责登录态维护
- 使用可视化浏览器，不要默认 headless
- 保留浏览器会话，避免登录态频繁丢失

## 15. 本次仓库内落地内容

- `docker-compose.yml` 已新增 `rabbitmq` 与 `scheduler-center`
- 采集模块已纳入 `integrations/distributed-data-acquisition`
- 调度中心已支持把职位数据写入 `crawl_job_posting`
- 已补充增量同步 SQL
- 已补充同步 PowerShell 脚本
- 已补充主机/虚拟机工业级部署说明
