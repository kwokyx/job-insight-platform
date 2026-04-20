# 分布式数据采集模块详细设计文档

## 1. 系统架构

### 1.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         前端界面 (Vue 3)                             │
│                    (任务管理、节点监控、数据统计)                     │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ HTTP RESTful API (JWT 认证)
┌──────────────────────────────▼──────────────────────────────────────┐
│                   调度中心 (FastAPI)                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │
│  │  任务管理   │ │  节点管理   │ │  调度引擎   │ │  监控统计   │  │
│  │  模块       │ │  模块       │ │  模块       │ │  模块       │  │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │
│               │              │              │              │       │
│               └──────┬───────┴──────┬───────┴──────┬──────┘       │
│                      ▼              ▼              ▼              │
│                ┌─────────┐    ┌─────────┐    ┌─────────┐          │
│                │ MySQL   │    │ Redis   │    │RabbitMQ │          │
│                │ 8.0     │    │ 7       │    │ 3.12    │          │
│                └─────────┘    └─────────┘    └─────────┘          │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ 任务队列 / 心跳消息
┌──────────────────────────────▼──────────────────────────────────────┐
│                   爬虫节点集群 (Python 3.11)                         │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │
│  │  节点1      │ │  节点2      │ │  节点3      │ │  节点N      │  │
│  │ (Scrapy +   │ │ (Scrapy +   │ │ (Scrapy +   │ │ (Scrapy +   │  │
│  │  Selenium)  │ │  Selenium)  │ │  Selenium)  │ │  Selenium)  │  │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 组件职责

| 组件 | 职责 | 技术实现 |
|------|------|----------|
| **调度中心** | 全局任务调度、节点管理、监控统计、配置管理 | FastAPI + SQLAlchemy + Alembic |
| **爬虫节点** | 执行具体爬虫任务、数据预处理、心跳上报 | Python + Scrapy + Selenium + requests |
| **消息队列** | 任务分发、节点通信、解耦组件 | RabbitMQ |
| **MySQL** | 存储任务数据、节点信息、采集结果 | MySQL 8.0 |
| **Redis** | 缓存热点数据、Token池、代理IP池、分布式锁 | Redis 7 |
| **前端界面** | 用户操作界面、数据可视化、系统监控 | Vue 3 + Vite + ECharts |

## 2. 核心模块设计

### 2.1 任务管理模块

#### 2.1.1 任务生命周期
```
创建任务 → 任务拆分 → 分片分发 → 节点执行 → 数据收集 → 任务完成
```

#### 2.1.2 任务拆分策略
- **按关键词拆分**：每个关键词作为一个分片
- **按城市拆分**：每个城市作为一个分片  
- **按分类码拆分**：每个职位分类作为一个分片
- **按页码拆分**：对于大量数据，按页码分片（每页20条）

#### 2.1.3 任务状态机
```
待执行(0) → 运行中(1) → 完成(2)
                ↓
              失败(3) → 重试 → 运行中(1)
```

### 2.2 节点管理模块

#### 2.2.1 节点注册与发现
1. 节点启动时向调度中心注册
2. 调度中心记录节点信息（IP、端口、能力）
3. 节点定期发送心跳（30秒间隔）
4. 调度中心检测心跳超时（90秒）标记节点离线

#### 2.2.2 负载均衡策略
- **轮询调度**：平均分配任务给在线节点
- **加权轮询**：根据节点性能（CPU、内存）分配任务
- **最少连接**：优先分配给当前任务最少的节点

### 2.3 调度引擎模块

#### 2.3.1 调度算法
```java
// 伪代码
public class TaskScheduler {
    public void schedule(Task task) {
        // 1. 任务拆分
        List<TaskShard> shards = splitTask(task);
        
        // 2. 选择可用节点
        List<Worker> availableWorkers = getAvailableWorkers();
        
        // 3. 分配分片
        for (TaskShard shard : shards) {
            Worker worker = selectWorker(availableWorkers);
            assignShardToWorker(shard, worker);
            
            // 4. 发送到消息队列
            sendToRabbitMQ(shard, worker);
        }
    }
}
```

#### 2.3.2 故障转移
- 节点离线时，重新分配其未完成的任务
- 任务执行失败时，自动重试（最大3次）
- 重试失败后标记任务失败，记录日志

### 2.4 反爬虫策略模块

#### 2.4.1 Token池管理
- 存储多个智联招聘API Token
- Token失效自动切换
- 定期检测Token有效性

#### 2.4.2 代理IP池管理
- 支持HTTP/HTTPS代理
- 自动检测代理可用性
- 根据成功率动态调整代理优先级

#### 2.4.3 请求频率控制
- 限制单个IP的请求频率
- 随机延迟请求（1-3秒）
- 自适应调整频率避免封禁

## 3. 数据库设计

### 3.1 表关系图
```
crawl_task ────┬──── crawl_task_shard
               ├──── crawl_task_log
               └──── job_data (职位数据)
               
crawl_worker ──┬──── crawl_task_shard (执行节点)
               └──── crawl_proxy (绑定代理)
```

### 3.2 关键表结构说明

#### 3.2.1 采集任务主表 (crawl_task)
- `task_id`: UUID主键，任务唯一标识
- `parent_task_id`: 支持父子任务，用于增量更新
- `status`: 任务状态（0待执行，1运行中，2完成，3失败）
- `priority`: 调度优先级，数值越小优先级越高

#### 3.2.2 任务分片表 (crawl_task_shard)
- `shard_id`: 分片唯一标识
- `task_id`: 关联主任务
- `page`: 采集页码（用于分页采集）
- `worker_id`: 执行该分片的节点ID

#### 3.2.3 爬虫节点表 (crawl_worker)
- `worker_id`: 节点唯一标识
- `ip`: 节点服务器IP
- `status`: 节点状态（0离线，1在线）
- `last_heartbeat`: 最后心跳时间，用于健康检查

#### 3.2.4 职位数据表 (job_data)
- `url_obj_id`: 来源平台职位唯一标识（主键）
- `task_id`: 关联采集任务
- 包含职位所有详细信息（标题、薪资、城市、公司等）

## 4. 消息队列设计

### 4.1 Exchange和Queue配置

| Exchange | Type | Queue | Routing Key | 用途 |
|----------|------|-------|-------------|------|
| `task.exchange` | direct | `task.queue` | `task.shard` | 任务分片分发 |
| `heartbeat.exchange` | fanout | `heartbeat.queue` | `heartbeat` | 心跳消息 |
| `data.exchange` | topic | `data.queue` | `data.job.#` | 采集数据上传 |

### 4.2 消息格式

#### 4.2.1 任务消息
```json
{
  "messageId": "uuid",
  "type": "TASK_SHARD",
  "timestamp": "2026-04-16T10:30:00Z",
  "data": {
    "shardId": "shard_123",
    "taskId": "task_456",
    "keyword": "Java工程师",
    "city": "北京",
    "categoryCode": "100001",
    "page": 1,
    "retryCount": 0
  }
}
```

#### 4.2.2 心跳消息
```json
{
  "messageId": "uuid",
  "type": "HEARTBEAT",
  "timestamp": "2026-04-16T10:30:00Z",
  "data": {
    "workerId": "worker_001",
    "cpuUsage": 45.5,
    "memoryUsage": 68.2,
    "currentTasks": 3,
    "status": "ONLINE"
  }
}
```

## 5. 接口设计

### 5.1 RESTful API 概览

| 模块 | 路径 | 方法 | 描述 |
|------|------|------|------|
| 任务管理 | `/api/tasks` | GET | 获取任务列表 |
| 任务管理 | `/api/tasks` | POST | 创建新任务 |
| 任务管理 | `/api/tasks/{id}` | GET | 获取任务详情 |
| 任务管理 | `/api/tasks/{id}` | PUT | 更新任务 |
| 任务管理 | `/api/tasks/{id}/start` | POST | 启动任务 |
| 任务管理 | `/api/tasks/{id}/pause` | POST | 暂停任务 |
| 节点管理 | `/api/workers` | GET | 获取节点列表 |
| 节点管理 | `/api/workers/{id}` | DELETE | 删除节点 |
| 监控统计 | `/api/monitor/dashboard` | GET | 仪表盘数据 |
| 监控统计 | `/api/monitor/task-stats` | GET | 任务统计 |
| 日志查询 | `/api/logs` | GET | 查询日志 |

### 5.2 关键接口详情

#### 5.2.1 创建采集任务
```
POST /api/tasks
Content-Type: application/json

{
  "taskName": "Java岗位采集-2026-04",
  "channel": "zhaopin",
  "keywords": ["Java工程师", "后端开发"],
  "city": ["北京", "上海", "深圳"],
  "priority": 5,
  "scheduleType": "IMMEDIATE", // IMMEDIATE, CRON
  "cronExpression": "0 0 2 * * ?", // 每天凌晨2点
  "incremental": false // 是否增量采集
}
```

响应：
```json
{
  "code": 200,
  "message": "任务创建成功",
  "data": {
    "taskId": "task_123456",
    "status": "PENDING"
  }
}
```

#### 5.2.2 获取监控仪表盘数据
```
GET /api/monitor/dashboard
```

响应：
```json
{
  "code": 200,
  "data": {
    "totalTasks": 156,
    "runningTasks": 8,
    "onlineWorkers": 5,
    "todayCollected": 12456,
    "successRate": 98.5,
    "errorCount": 23,
    "workerStats": [
      {
        "workerId": "worker_001",
        "status": "ONLINE",
        "cpuUsage": 45.5,
        "memoryUsage": 68.2,
        "currentTasks": 3
      }
    ],
    "taskTrend": [
      {"date": "2026-04-15", "count": 12000},
      {"date": "2026-04-16", "count": 12456}
    ]
  }
}
```

## 6. 爬虫节点设计

### 6.1 节点架构

```
┌─────────────────────────────────────┐
│           爬虫节点                   │
├─────────────────────────────────────┤
│  RabbitMQ客户端  ←→  调度中心         │
│         ↓                           │
│   任务处理器                         │
│         ↓                           │
│  ┌─────────────┐ ┌─────────────┐   │
│  │  Scrapy引擎 │ │  Selenium   │   │
│  │  (API采集)  │ │  (页面渲染) │   │
│  └─────────────┘ └─────────────┘   │
│         ↓                           │
│   数据处理器                         │
│  (清洗、去重、解析)                  │
│         ↓                           │
│   数据上传器 → RabbitMQ → MySQL     │
└─────────────────────────────────────┘
```

### 6.2 节点工作流程

1. **初始化**：连接RabbitMQ，注册节点，开始心跳
2. **任务监听**：从`task.queue`消费任务消息
3. **任务执行**：调用Scrapy或Selenium进行数据采集
4. **数据处理**：清洗、解析、去重
5. **数据上传**：将处理后的数据发送到`data.queue`
6. **状态上报**：任务完成状态上报给调度中心

### 6.3 反爬虫实现

```python
class AntiSpiderManager:
    def __init__(self):
        self.token_pool = TokenPool()
        self.proxy_pool = ProxyPool()
        self.request_counter = RequestCounter()
    
    def get_headers(self):
        """生成随机请求头"""
        return {
            'User-Agent': self.user_agent_rotator.get(),
            'Authorization': f'Bearer {self.token_pool.get_valid_token()}',
            'Cookie': self.cookie_manager.get_cookie()
        }
    
    def get_proxy(self):
        """获取可用代理"""
        return self.proxy_pool.get_best_proxy()
    
    def delay_request(self):
        """随机延迟请求"""
        time.sleep(random.uniform(1, 3))
```

## 7. 部署架构

### 7.1 Docker Compose 服务

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| `scheduler` | `scheduler-center:latest` | 8080 | 调度中心 |
| `mysql` | `mysql:8.0` | 3306 | 数据库 |
| `redis` | `redis:7-alpine` | 6379 | 缓存 |
| `rabbitmq` | `rabbitmq:3.12-management` | 5672, 15672 | 消息队列 |
| `crawler-node-1` | `crawler-node:latest` | - | 爬虫节点1 |
| `crawler-node-2` | `crawler-node:latest` | - | 爬虫节点2 |

### 7.2 网络配置
- 所有服务在同一个Docker网络内
- 调度中心可通过服务名访问其他服务
- 爬虫节点可访问外网（用于数据采集）

### 7.3 持久化存储
- MySQL数据卷：`mysql_data`
- Redis数据卷：`redis_data`
- RabbitMQ数据卷：`rabbitmq_data`

## 8. 监控与日志

### 8.1 监控指标

| 指标 | 采集方式 | 报警阈值 |
|------|----------|----------|
| 节点在线率 | 心跳检测 | < 80% |
| 任务成功率 | 任务统计 | < 95% |
| 采集速度 | 数据计数 | < 1000条/小时 |
| 系统负载 | 节点上报 | CPU > 85% 或内存 > 90% |
| Token可用率 | Token池检测 | < 50% |

### 8.2 日志体系

- **任务日志**：记录任务创建、执行、完成全过程
- **节点日志**：记录节点状态、心跳、异常
- **采集日志**：记录数据采集详情、反爬虫事件
- **系统日志**：记录系统启动、配置变更、错误信息

### 8.3 日志存储
- 近期日志存入MySQL `crawl_task_log` 表
- 历史日志归档到文件系统
- 支持按时间、节点、任务类型检索

## 9. 安全设计

### 9.1 接口安全
- JWT Token认证
- API访问频率限制
- 敏感操作日志记录

### 9.2 数据安全
- 数据库连接加密
- 敏感配置项加密存储
- 数据备份与恢复机制

### 9.3 网络安全
- 服务间通信使用内部网络
- 对外暴露最小必要端口
- 防火墙规则配置

## 10. 性能优化

### 10.1 数据库优化
- 建立合适的索引
- 查询分页优化
- 读写分离（主从复制）

### 10.2 缓存优化
- 热点数据缓存
- 分布式锁避免并发问题
- 缓存失效策略

### 10.3 消息队列优化
- 消息持久化
- 消费确认机制
- 死信队列处理

---

*文档版本：v1.0*  
*最后更新：2026-04-16*