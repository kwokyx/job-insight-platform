# 安全与性能基线

本文档记录当前线程已补到的安全/性能能力，以及还需要继续推进的部分。

## 1. 已补齐

### 安全

- Spring Security 接入 JWT 鉴权
- 登录失败次数统计与验证码服务已接入
- Open API 支持 API Key 鉴权
- Open API 已接入每日配额与 QPS 限流
- 基础安全响应头已启用
- 接口审计日志表 `sys_api_call_log` 已接入

### 性能

- 后端开启压缩响应
- MySQL 连接池改为 HikariCP
- Redis 连接池参数已配置
- Open API 查询分页统一收口，避免超大页

## 2. 当前验证方式

- 后端测试：`cd backend && mvn test`
- 前端构建：`cd frontend && npm run build`
- Open API 冒烟压测脚本：`scripts/run_openapi_smoke_benchmark.ps1`
- 本地环境检查脚本：`scripts/check_local_env.ps1`

## 3. 建议压测基线

### 目标接口

- `GET /api/v1/open/jobs`
- `GET /api/v1/open/analysis/overview`
- `GET /api/v1/open/analysis/industry`
- `POST /api/v1/reports/generate`

### 建议指标

- P95 响应时间
- P99 响应时间
- 错误率
- 每秒吞吐
- Redis 限流命中率
- 数据库慢查询数量

## 4. 当前缺口

- 尚未形成正式性能测试报告和容量评估结论
- 缺少数据库索引体检与慢 SQL 持续治理
- 验证码策略已具备基础能力，但还未接入前端完整风控链路
- 隐私分级、脱敏、数据保留周期仍需制度化
- 还没有完整的 SLA/SLO 监控看板
