# API 接口文档

## 概述

本文档描述了分布式数据采集模块的 RESTful API 接口。所有接口均使用 JSON 格式进行数据交换，采用标准的 HTTP 状态码。

### 基础信息
- **基础URL**: `http://localhost:8000/api`
- **认证方式**: JWT Token（暂未实现，预留接口）
- **响应格式**: 
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 状态码说明
| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 任务管理

### 1. 获取任务列表
**GET** `/tasks`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | int | 否 | 页码，默认1 |
| size | int | 否 | 每页大小，默认20，最大100 |
| status | int | 否 | 任务状态：0待执行 1运行中 2完成 3失败 |
| channel | string | 否 | 采集渠道：zhaopin/boss |
| keyword | string | 否 | 任务名称关键词搜索 |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "task_id": "task_123456",
        "task_name": "Java岗位采集-2026-04",
        "channel": "zhaopin",
        "status": 1,
        "priority": 5,
        "total_count": 100,
        "finished_count": 45,
        "duplicate_count": 3,
        "start_time": "2026-04-16 10:30:00",
        "end_time": null,
        "created_at": "2026-04-16 10:00:00",
        "updated_at": "2026-04-16 10:30:00"
      }
    ],
    "total": 156,
    "page": 1,
    "size": 20,
    "pages": 8
  }
}
```

### 2. 创建任务
**POST** `/tasks`

**请求体**:
```json
{
  "task_name": "Java岗位采集-2026-04",
  "channel": "zhaopin",
  "keywords": ["Java工程师", "后端开发"],
  "city": ["北京", "上海", "深圳"],
  "priority": 5,
  "schedule_type": "IMMEDIATE",
  "cron_expression": "0 0 2 * * ?",
  "incremental": false,
  "create_user": "admin"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "任务创建成功",
  "data": {
    "task_id": "task_123456",
    "status": 0
  }
}
```

### 3. 获取任务详情
**GET** `/tasks/{task_id}`

**路径参数**:
- `task_id`: 任务ID

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "task_id": "task_123456",
    "task_name": "Java岗位采集-2026-04",
    "channel": "zhaopin",
    "keywords": ["Java工程师", "后端开发"],
    "city": ["北京", "上海", "深圳"],
    "status": 1,
    "priority": 5,
    "total_count": 100,
    "finished_count": 45,
    "duplicate_count": 3,
    "start_time": "2026-04-16 10:30:00",
    "end_time": null,
    "create_user": "admin",
    "created_at": "2026-04-16 10:00:00",
    "updated_at": "2026-04-16 10:30:00"
  }
}
```

### 4. 更新任务
**PUT** `/tasks/{task_id}`

**请求体**:
```json
{
  "task_name": "更新后的任务名称",
  "priority": 3,
  "status": 1
}
```

### 5. 删除任务
**DELETE** `/tasks/{task_id}`

### 6. 启动任务
**POST** `/tasks/{task_id}/start`

### 7. 暂停任务
**POST** `/tasks/{task_id}/pause`

### 8. 获取任务分片列表
**GET** `/tasks/{task_id}/shards`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | int | 否 | 页码 |
| size | int | 否 | 每页大小 |
| status | int | 否 | 分片状态 |

### 9. 获取任务统计
**GET** `/tasks/{task_id}/stats`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total_shards": 100,
    "pending_shards": 20,
    "running_shards": 30,
    "completed_shards": 45,
    "failed_shards": 5
  }
}
```

---

## 节点管理

### 1. 获取节点列表
**GET** `/workers`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | int | 否 | 页码 |
| size | int | 否 | 每页大小 |
| status | int | 否 | 节点状态：0离线 1在线 |

### 2. 获取节点详情
**GET** `/workers/{worker_id}`

### 3. 删除节点
**DELETE** `/workers/{worker_id}`

### 4. 获取节点统计
**GET** `/workers/{worker_id}/stats`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "worker_id": "worker_001",
    "status": "ONLINE",
    "cpu_usage": 45.5,
    "memory_usage": 68.2,
    "current_tasks": 3,
    "total_shards": 150,
    "completed_shards": 120
  }
}
```

### 5. 获取节点任务分片
**GET** `/workers/{worker_id}/shards`

### 6. 清理离线节点
**POST** `/workers/cleanup`

---

## 监控统计

### 1. 获取仪表盘数据
**GET** `/monitor/dashboard`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "stats": {
      "total_tasks": 156,
      "running_tasks": 8,
      "online_workers": 5,
      "today_collected": 12456,
      "success_rate": 98.5,
      "error_count": 23
    },
    "worker_stats": [
      {
        "worker_id": "worker_001",
        "status": "ONLINE",
        "cpu_usage": 45.5,
        "memory_usage": 68.2,
        "current_tasks": 3,
        "total_shards": 150,
        "completed_shards": 120
      }
    ],
    "task_trend": [
      {"date": "2026-04-15", "count": 12000},
      {"date": "2026-04-16", "count": 12456}
    ]
  }
}
```

### 2. 获取任务统计
**GET** `/monitor/task-stats`

### 3. 获取节点统计
**GET** `/monitor/worker-stats`

### 4. 获取数据统计
**GET** `/monitor/data-stats`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total_data": 256789,
    "today_new": 12456,
    "city_distribution": [
      {"city": "北京", "count": 45678},
      {"city": "上海", "count": 42345}
    ],
    "salary_distribution": [
      {"range": "10k-11k", "count": 1234},
      {"range": "11k-12k", "count": 2345}
    ]
  }
}
```

---

## 日志查询

### 1. 查询日志
**GET** `/logs`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | int | 否 | 页码 |
| size | int | 否 | 每页大小 |
| level | string | 否 | 日志级别：DEBUG/INFO/WARN/ERROR |
| task_id | string | 否 | 任务ID |
| worker_id | string | 否 | 节点ID |
| start_time | datetime | 否 | 开始时间 |
| end_time | datetime | 否 | 结束时间 |
| keyword | string | 否 | 关键词搜索 |

### 2. 获取日志统计
**GET** `/logs/stats`

**请求参数**:
- `days`: 统计天数，默认7天

### 3. 清理旧日志
**DELETE** `/logs/cleanup`

**请求参数**:
- `days`: 保留天数，默认30天

---

## 系统配置

### 1. 获取配置列表
**GET** `/config`

### 2. 获取配置详情
**GET** `/config/{config_key}`

### 3. 创建配置
**POST** `/config`

**请求体**:
```json
{
  "config_key": "zhaopin.token.mme",
  "config_value": "MmEwMD=",
  "channel": "zhaopin",
  "status": 1,
  "expire_time": "2026-04-30 23:59:59"
}
```

### 4. 更新配置
**PUT** `/config/{config_key}`

### 5. 删除配置
**DELETE** `/config/{config_key}`

### 6. 获取渠道配置
**GET** `/config/channel/{channel}`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "zhaopin.token.mme": "MmEwMD=",
    "zhaopin.token.c1k": "c1K5tw0w6_=",
    "proxy.enabled": "true"
  }
}
```

### 7. 批量更新配置
**POST** `/config/batch`

**请求体**:
```json
[
  {
    "config_key": "zhaopin.token.mme",
    "config_value": "MmEwMD=",
    "channel": "zhaopin",
    "status": 1
  },
  {
    "config_key": "proxy.enabled",
    "config_value": "true",
    "channel": "default",
    "status": 1
  }
]
```

---

## 爬虫节点API

### 1. 接收节点心跳
**POST** `/workers/{worker_id}/heartbeat`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| cpu_usage | float | 是 | CPU使用率(0-100) |
| memory_usage | float | 是 | 内存使用率(0-100) |
| current_tasks | int | 是 | 当前任务数 |

**说明**: 此接口也用于节点注册，如果节点不存在会自动创建。

---

## 消息队列接口

### 消息格式规范

#### 1. 任务分片消息
**Exchange**: `task.exchange`  
**Routing Key**: `task.shard`  
**Queue**: `task.queue`

```json
{
  "shard_id": "shard_123456",
  "task_id": "task_123456",
  "keyword": "Java工程师",
  "city": "北京",
  "category_code": "100001",
  "page": 1,
  "retry_count": 0,
  "priority": 5
}
```

#### 2. 心跳消息
**Exchange**: `heartbeat.exchange`  
**Routing Key**: `heartbeat`  
**Queue**: `heartbeat.queue`

```json
{
  "worker_id": "worker_001",
  "node_name": "爬虫节点1",
  "ip": "192.168.1.100",
  "cpu_usage": 45.5,
  "memory_usage": 68.2,
  "current_tasks": 3,
  "status": "ONLINE",
  "timestamp": "2026-04-16T10:30:00Z"
}
```

#### 3. 职位数据消息
**Exchange**: `data.exchange`  
**Routing Key**: `data.job.{city}`  
**Queue**: `data.queue`

```json
{
  "url_obj_id": "123456789",
  "title": "Java高级开发工程师",
  "salary_min": 25000,
  "salary_max": 35000,
  "salary_raw": "25k-35k",
  "job_city": "北京",
  "experience_year": "3-5年",
  "education_need": "本科",
  "publish_date": "2026-04-16T10:00:00Z",
  "job_welfare": "五险一金,年终奖,带薪年假",
  "job_labels": "Java,Spring,MySQL",
  "position_info": "岗位职责...",
  "job_classification": "计算机/互联网/通信",
  "company_name": "XX科技有限公司",
  "company_size": "500-999人",
  "company_finance": "D轮及以上",
  "company_logo": "https://...",
  "task_id": "task_123456",
  "shard_id": "shard_123456",
  "crawl_time": "2026-04-16T10:30:00Z"
}
```

#### 4. 任务结果消息
**Exchange**: `task.exchange`  
**Routing Key**: `task.result`  
**Queue**: `task.result.queue`

```json
{
  "shard_id": "shard_123456",
  "task_id": "task_123456",
  "status": 2,
  "worker_id": "worker_001",
  "data_count": 15,
  "error_message": "",
  "timestamp": "2026-04-16T10:30:00Z"
}
```

---

## 错误码说明

| 错误码 | 说明 | 建议处理方式 |
|--------|------|------------|
| 10001 | 参数验证失败 | 检查请求参数格式 |
| 10002 | 资源不存在 | 检查资源ID是否正确 |
| 10003 | 操作不允许 | 检查资源状态和操作权限 |
| 10004 | 任务已在运行中 | 无需重复启动 |
| 10005 | 节点有运行中任务 | 先停止节点任务再删除 |
| 20001 | 数据库操作失败 | 联系系统管理员 |
| 20002 | 消息队列连接失败 | 检查RabbitMQ服务状态 |
| 20003 | Redis操作失败 | 检查Redis服务状态 |
| 30001 | 爬虫Token失效 | 更新Token配置 |
| 30002 | 代理IP不可用 | 检查代理IP池 |
| 30003 | 请求频率过高 | 降低采集频率 |

---

## 更新记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-04-16 | 初始版本，包含所有基础接口 |