# Open API 治理说明

本文档定义平台对外开放 API 的治理边界，目标是把当前接口从“可演示”推进到“可接入、可审计、可治理”。

## 1. 当前开放能力

- 元数据接口：`GET /api/v1/open/meta`
- 能力目录：`GET /api/v1/open/capabilities`
- 订阅元数据：`GET /api/v1/open/subscriptions/meta`
- 岗位查询：`GET /api/v1/open/jobs`
- 行业快照：`GET /api/v1/open/analysis/industry`
- 公开报告：`GET /api/v1/open/reports/public`

## 2. 版本与治理

- 当前主版本：`v1`
- 访问模式：匿名只读、API Key
- 分页约束：默认 `pageSize=20`，最大 `50`
- 请求追踪：响应头返回 `X-Request-Id`
- 数据分级：响应头返回 `X-Data-Classification=employment-insight`
- 租户范围：响应头返回 `X-Tenant-Scope`

## 3. 权限模型

- `basic`：仅返回公开岗位基础字段
- `extended`：可返回标签、福利、来源、企业规模等扩展字段
- 字段控制通过 API Key 中的 `allowedJobFields` 和 `allowedReportFields` 白名单完成

## 4. 审计与限流

- API Key 每日配额由 `dailyQuota` 控制
- 秒级并发由 `rateLimitQps` 控制
- 认证请求进入 `sys_api_call_log`
- Redis 不可用时，系统保留降级日志，但审计与限流能力会下降

## 5. 订阅式数据推送约定

### 交付模式

- `scheduled-pull`：第三方按计划轮询开放接口
- `webhook`：平台向订阅方回调事件

### 支持事件

- `openapi.industry.snapshot.ready`
- `openapi.report.publication`
- `openapi.jobs.delta.available`

### webhook 请求头

- `X-Request-Id`
- `X-Timestamp`
- `X-Signature`

### webhook 签名

- 算法：`HMAC-SHA256`
- 校验串建议为：`timestamp + "." + requestBody`
- 订阅方必须校验签名、时钟偏差和重复投递

### 建议负载

```json
{
  "eventType": "openapi.industry.snapshot.ready",
  "requestId": "7ddcfa3ef0a5467b98f8d0f8b5c9f001",
  "tenantScope": "public",
  "publishedAt": "2026-04-20T11:30:00+08:00",
  "data": {
    "industry": "人工智能",
    "city": "上海",
    "snapshotUrl": "/api/v1/open/analysis/industry?industry=人工智能&city=上海"
  }
}
```

## 6. 当前仍未完全闭环的点

- 租户隔离目前以声明式范围为主，还未形成真实多租户数据分区
- 字段级权限已覆盖岗位与报告基础场景，但还未覆盖所有扩展资源
- 订阅式推送当前完成了契约说明，生产级回调编排与重试中心仍可继续补强
