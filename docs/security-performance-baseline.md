# 安全与性能基线

本文档记录当前线程已补到的安全/性能能力，以及还需要继续推进的部分。

## 1. 已补齐

### 安全

- 受保护接口基于 JWT 鉴权。
- 登录失败次数达到阈值后，后端会强制校验验证码。
- 注册、登录已接入限流与节流控制，降低暴力尝试风险。
- 注册、修改密码、找回密码确认都使用统一密码强度校验：
  - 至少 8 位
  - 同时包含字母和数字
- 验证码支持：
  - `AUTO`
  - `MATH`
  - `CHAR`

### 性能

- 新增两步式密码重置流程：
  - `POST /api/v1/auth/password/reset/request`
  - `POST /api/v1/auth/password/reset/confirm`
- 使用短期有效的 `resetToken`，默认有效期 15 分钟。
- 支持 Redis 存储，Redis 不可用时可回退到本地内存。

## 2. 当前验证方式

- 当前实现面向“先联调、先跑通”的工程阶段，`resetToken` 直接返回前端。
- 正式生产建议改为邮箱或短信投递，并增加操作审计。

## 3. 建议压测基线

- 报告钻取详情接口已接入敏感信息脱敏。
- 报告导出接口已接入敏感信息脱敏。
- 公开报告详情接口已接入敏感信息脱敏。

当前脱敏覆盖：

- 邮箱
- 手机号
- token / secret / authorization
- 自由文本中的邮箱与手机号

### 1.4 Open API 治理

- 支持 API Key 鉴权。
- 支持配额和 QPS 限流。
- 支持租户范围隔离。
- 支持字段级白名单过滤。
- 支持 API 调用审计日志查询。

### 1.5 性能基础能力

- 开放 API 查询统一分页，避免超大响应。
- 查询型接口对 `page/pageSize` 做安全边界限制。
- 连接 Redis 时支持密码和连接池配置。
- MySQL、Redis、算法服务 URL 已具备基础外部化配置能力。

## 2. 已提供的验证入口

### 2.1 构建与测试

- 后端测试：

```powershell
cd backend
mvn test
```

- 前端构建：

```powershell
cd frontend
npm run build
```

### 2.2 脚本入口

- 本地环境检查：
  - `scripts/check_local_env.ps1`
- Open API 冒烟和基线压测：
  - `scripts/run_openapi_smoke_benchmark.ps1`
- k6 综合基线脚本：
  - `scripts/k6_security_performance_baseline.js`

## 3. 推荐压测对象

优先覆盖以下接口：

- `GET /api/v1/auth/captcha`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/password/reset/request`
- `POST /api/v1/auth/password/reset/confirm`
- `GET /api/v1/open/jobs`
- `GET /api/v1/open/analysis/overview`
- `GET /api/v1/open/analysis/insights`
- `POST /api/v1/reports/generate`
- `GET /api/v1/reports/{id}/export`

### 建议指标

- P95 响应时间
- P99 响应时间
- 吞吐量
- 错误率
- 限流命中率
- 验证码失败率
- 慢查询数量
- 导出接口平均生成时长

## 4. 还没有做完的部分

下面这些能力目前仍属于“已补基础，但未达到工业级完备”：

### 4.1 图形验证码安全强度

- 当前是文本挑战 + 前端视觉化渲染，不是真实图片验证码。
- 如果平台面向公网高风险场景，建议升级为：
  - 图片验证码
  - 滑块验证码
  - 第三方风控校验

### 4.2 找回密码生产化

- 当前未接入邮件或短信服务。
- 当前未记录完整密码找回审计日志。
- 建议补充：
  - 发送通道
  - 发送频控
  - IP / 用户维度风控
  - 重置成功告警

### 4.3 数据安全与隐私分级

- 现有脱敏主要覆盖报告和开放报告详情。
- 仍建议继续补充：
  - 学生简历导出脱敏
  - 管理端导表脱敏
  - 数据分级标签
  - 操作人审计

### 4.4 性能和容量治理

- 当前已有脚本，但还缺固定环境下的正式压测报告。
- 仍建议补充：
  - 峰值并发基线
  - 报告导出队列容量
  - Redis / DB 热点分析
  - JVM 与连接池观测指标

### 4.5 专项安全测试

- 仍需专项验证：
  - SQL 注入
  - 越权访问
  - 批量枚举
  - API Key 滥用
  - 敏感字段泄露
  - 报告下载刷取

## 4. 当前缺口

- 尚未形成正式性能测试报告和容量评估结论
- 缺少数据库索引体检与慢 SQL 持续治理
- 验证码策略已具备基础能力，但还未接入前端完整风控链路
- 隐私分级、脱敏、数据保留周期仍需制度化
- 还没有完整的 SLA/SLO 监控看板
