# 分布式采集运维说明

## 当前拓扑

- 主机：`192.168.200.1`
- 调度中心：`http://192.168.200.1:8001/api`
- RabbitMQ：`192.168.200.1:5672`
- MySQL：`192.168.200.1:3307`
- 虚拟机节点：
  - `hadoop001` -> `192.168.200.151`
  - `hadoop002` -> `192.168.200.152`
  - `hadoop003` -> `192.168.200.153`

## 节点目录约定

- 代码目录：`/opt/crawler-node/releases/current`
- 节点环境文件：`/opt/crawler-node/releases/.env`
- 节点服务名：`crawler-node`
- 节点日志：
  - 应用日志：`/opt/crawler-node/releases/current/logs/crawler.log`
  - 服务日志：`journalctl -u crawler-node`

## 节点服务化

本仓库提供统一安装脚本：

```powershell
py -3 .\scripts\install_crawler_node_service.py
```

脚本会执行以下动作：

1. 连接 `hadoop001/002/003`
2. 校验 Python 虚拟环境和 `main.py`
3. 下发 `/etc/systemd/system/crawler-node.service`
4. 停掉旧的手工进程
5. `systemctl enable crawler-node`
6. `systemctl restart crawler-node`
7. 回显服务状态

## 常用排查命令

### 主机侧

```powershell
Invoke-WebRequest -Uri 'http://localhost:8001/api/monitor/dashboard' -UseBasicParsing | Select-Object -ExpandProperty Content
Invoke-WebRequest -Uri 'http://localhost:8001/api/tasks/?page=1&size=20' -UseBasicParsing | Select-Object -ExpandProperty Content
```

### 虚拟机侧

```bash
systemctl status crawler-node --no-pager
journalctl -u crawler-node -n 100 --no-pager
tail -n 100 /opt/crawler-node/releases/current/logs/crawler.log
```

## 鉴权配置

当前节点 `.env` 中仍未注入正式智联登录态字段，以下字段补齐后可切到更稳定的无人值守模式：

- `ZHAOPIN_TOKEN_MME`
- `ZHAOPIN_TOKEN_C1K`
- `ZHAOPIN_COOKIE`
- `ZHAOPIN_PAGE_REQUEST_ID`
- `ZHAOPIN_REQUEST_ID`
- `ZHAOPIN_CLIENT_ID`

补齐后需要执行：

```bash
systemctl restart crawler-node
```

## 演示链路

1. 前端 `/crawler` 创建即时采集任务或查看每日模板。
2. 调度中心拆分分片并投递到 RabbitMQ。
3. 三台虚拟机节点消费分片并采集数据。
4. 原始结果进入 `crawl_job_posting`。
5. `scripts/run_incremental_etl.ps1` 定时将数据同步到 `biz_job_posting`。
6. 后端和可视化页面直接使用业务表。
