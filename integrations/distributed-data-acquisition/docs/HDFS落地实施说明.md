# 采集结果落 HDFS 实施说明

## 1. 目标与范围

当前实现只处理分布式采集模块产出的职位明细数据，并将其双写到：

- MySQL `job_data` 表
- HDFS JSONL 文件

本阶段**不进入 HDFS**的数据包括：

- `crawl_task`
- `crawl_task_shard`
- `crawl_worker`
- `crawl_task_log`
- `system_config`
- Spring Boot 用户/权限/AI 相关表
- 分析结果展示表

也就是说，当前 HDFS 仅用于保存职位采集明细，不承担系统事务型数据主存储。

## 2. 集群角色

当前三台虚拟机角色约定如下：

- `hadoop001 (192.168.152.151)`：NameNode
- `hadoop002 (192.168.152.152)`：DataNode
- `hadoop003 (192.168.152.153)`：DataNode

建议 HDFS 副本数：

- `dfs.replication=2`

## 3. HDFS 目录设计

### 3.1 根目录

本项目 HDFS 根目录固定为：

```text
/data/recruitment_crawler
```

这个目录表示“招聘采集业务数据域”，不是源码目录。

### 3.2 当前阶段目标目录

当前职位明细数据写入目录固定为：

```text
/data/recruitment_crawler/ods/job_detail/channel=zhaopin/dt=YYYY-MM-DD/
```

目录含义：

- `/data`：HDFS 业务数据总入口
- `/recruitment_crawler`：采集模块数据域
- `/ods`：原始数据层
- `/job_detail`：职位明细数据集
- `channel=zhaopin`：渠道分区
- `dt=YYYY-MM-DD`：采集日期分区

### 3.3 明确示例路径

若当天为 `2026-04-19`，目标路径为：

```text
/data/recruitment_crawler/ods/job_detail/channel=zhaopin/dt=2026-04-19/
```

文件示例：

```text
/data/recruitment_crawler/ods/job_detail/channel=zhaopin/dt=2026-04-19/part-20260419-153000-001.jsonl
/data/recruitment_crawler/ods/job_detail/channel=zhaopin/dt=2026-04-19/part-20260419-153000-002.jsonl
```

## 4. 入 HDFS 的字段

每条 JSONL 记录只保留以下 21 个字段：

- `url`
- `url_obj_id`
- `title`
- `salary_min`
- `salary_max`
- `salary_raw`
- `job_city`
- `experience_year`
- `education_need`
- `publish_date`
- `job_welfare`
- `job_labels`
- `position_info`
- `job_classification`
- `company_name`
- `company_size`
- `company_finance`
- `crawl_time`
- `crawl_update_time`
- `company_logo`
- `task_id`

字段约束：

- `url_obj_id` 必须存在，否则该条不会写入 HDFS
- 时间字段统一格式：`%Y-%m-%d %H:%M:%S`
- 数值字段 `salary_min`、`salary_max` 保持数值语义，无法转换时写 `null`

## 5. 当前实现方式

## 5.1 代码位置

本次实现主要集中在调度中心：

- 配置：`scheduler-center/app/config.py`
- 消费者：`scheduler-center/app/mq/consumer.py`
- HDFS 写入服务：`scheduler-center/app/services/hdfs_storage.py`

## 5.2 数据链路

当前链路：

```text
crawler-node -> RabbitMQ(data.queue) -> scheduler-center DataConsumer
                                            |- 写 MySQL job_data
                                            |- 批量写本地 staging
                                            |- 上传 HDFS JSONL
```

## 5.3 写入策略

当前不采用“每条消息一个 HDFS 文件”。

实际策略为：

- 消费消息后先写 MySQL
- MySQL 提交成功后，将 21 字段投影结果加入 HDFS 缓冲
- 缓冲达到阈值或时间窗口时生成本地 JSONL staging 文件
- 通过 WebHDFS 上传到目标目录

默认阈值：

- `HDFS_WRITE_BATCH_SIZE=1000`
- `HDFS_FLUSH_INTERVAL_SECONDS=60`

## 5.4 为什么使用 WebHDFS

当前调度中心运行在 Python 环境中，为了避免强依赖本机 Hadoop CLI，本实现通过 WebHDFS 上传文件。

NameNode 在处理 `CREATE` 时会返回具体 DataNode 的重定向地址，例如 `hadoop002:9864` 或 `hadoop003:9864`。
如果调度中心所在 Windows 机器无法解析这些 Linux 主机名，上传会在第二跳失败并报 `502 Bad Gateway`。
当前实现增加了 `HDFS_REDIRECT_HOST_MAP`，会自动把重定向地址中的主机名改写为对应 IP 后再上传。

因此除了 HDFS RPC 端口 `8020` 之外，还需要 NameNode Web 端口默认开放：

- `9870`

默认配置为：

- `HDFS_PORT=8020`
- `HDFS_WEB_PORT=9870`
- `HDFS_REDIRECT_HOST_MAP=hadoop001=192.168.152.151,hadoop002=192.168.152.152,hadoop003=192.168.152.153`

## 6. 配置项

需要在项目 `.env` 中补充或确认以下配置：

```env
HDFS_ENABLED=true
HDFS_HOST=hadoop001
HDFS_PORT=8020
HDFS_WEB_PORT=9870
HDFS_USER=hadoop
HDFS_BASE_PATH=/data/recruitment_crawler/ods/job_detail
HDFS_CHANNEL=zhaopin
HDFS_HTTP_TIMEOUT_SECONDS=30
HDFS_REDIRECT_HOST_MAP=hadoop001=192.168.152.151,hadoop002=192.168.152.152,hadoop003=192.168.152.153
LOCAL_STAGING_DIR=storage/hdfs_staging
HDFS_WRITE_BATCH_SIZE=1000
HDFS_FLUSH_INTERVAL_SECONDS=60
HDFS_WRITE_MODE=dual_write
```

路径拼接规则为：

```text
${HDFS_BASE_PATH}/channel=${HDFS_CHANNEL}/dt=${采集日期}/
```

例如：

```text
/data/recruitment_crawler/ods/job_detail/channel=zhaopin/dt=2026-04-19/
```

## 7. HDFS 初始化要求

建议先在 HDFS 上创建这些目录：

```text
/data
/data/recruitment_crawler
/data/recruitment_crawler/ods
/data/recruitment_crawler/ods/job_detail
```

可在 `hadoop001` 上执行：

```bash
hdfs dfs -mkdir -p /data/recruitment_crawler/ods/job_detail
```

并确认写入用户拥有权限：

```bash
hdfs dfs -chown -R hadoop:hadoop /data/recruitment_crawler
```

## 8. 验收方法

### 8.1 HDFS 层

检查目标日期目录：

```bash
hdfs dfs -ls /data/recruitment_crawler/ods/job_detail/channel=zhaopin/dt=2026-04-19/
```

读取文件内容：

```bash
hdfs dfs -cat /data/recruitment_crawler/ods/job_detail/channel=zhaopin/dt=2026-04-19/part-*.jsonl | head
```

### 8.2 数据层

应满足：

- 每行是合法 JSON
- 只包含 21 个指定字段
- `url_obj_id` 非空
- `crawl_time` 与 `publish_date` 格式统一

### 8.3 系统层

应满足：

- 原有采集任务仍可运行
- `job_data` 明细仍正常写入
- HDFS 目录生成批次文件
- HDFS 上传失败时日志中可看到 staging 路径和目标路径

## 9. 当前实现边界

当前实现是“先跑通 HDFS 双写”的第一阶段，不包含：

- Hive 外表
- JSONL 转 Parquet
- Spark 趋势分析
- 推荐特征加工
- 分析结果回写 Spring Boot 结果表

后续扩展时，可直接在同一根目录下继续增加：

```text
/data/recruitment_crawler/dwd/...
/data/recruitment_crawler/dws/...
```

但本阶段只写：

```text
/data/recruitment_crawler/ods/job_detail/channel=zhaopin/dt=YYYY-MM-DD/
```
