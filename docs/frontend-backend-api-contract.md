# 前后端接口契约

本文档整理当前线程已落地、可直接联调的新功能接口，重点覆盖认证安全、找回密码、图形验证码 UI 约定、报告脱敏导出、开放 API 能力说明。前后端分离开发时，以本文档和实际接口返回结构对齐。

## 1. 通用返回结构

所有 JSON 接口统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "total": 0,
  "page": 1,
  "pageSize": 20,
  "timestamp": "2026-04-20T15:00:00"
}
```

说明：

- `code = 200` 表示成功。
- `code != 200` 表示业务失败，前端直接读取 `message` 提示。
- 分页接口额外返回 `total/page/pageSize`。
- 非分页接口通常只返回 `code/message/data/timestamp`。

## 2. 认证与安全接口

基础路径：`/api/v1/auth`

### 2.1 获取验证码

`GET /api/v1/auth/captcha`

查询参数：

- `type` 可选：`AUTO | MATH | CHAR`

示例：

```http
GET /api/v1/auth/captcha?type=CHAR
```

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "captchaId": "0f8fad5bd9cb469fa16570867728950e",
    "captchaPrompt": "A 7 C 2",
    "captchaType": "CHAR",
    "expiresInSeconds": 300
  }
}
```

前端约定：

- 登录、注册、找回密码都先拉取验证码。
- 当前后端验证码仍是“文本挑战 + 服务端校验”模式。
- 前端不需要解析题型，直接把 `captchaPrompt` 当作可视化素材展示。
- 提交失败后应刷新验证码。

### 2.2 图形验证码 UI 约定

当前已经在前端实现“视觉化验证码卡片”，不是后端返回图片流。

约定如下：

- 后端返回 `captchaPrompt` 文本题面。
- 前端使用字符拆分、旋转、错位、渐变背景等方式渲染成图形化验证码区域。
- 用户输入框仍提交纯文本答案 `captchaCode`。
- 前端场景：
  - 登录表单
  - 注册表单
  - 找回密码第一步

这意味着：

- 前端不依赖图片接口。
- 后续如果升级为真实图片验证码，可保留 `captchaId + captchaCode` 提交协议不变，只替换渲染来源。

### 2.3 注册

`POST /api/v1/auth/register`

请求体：

```json
{
  "username": "teacher01",
  "password": "abc12345",
  "email": "teacher01@example.com",
  "nickname": "张老师",
  "roleType": 2,
  "captchaId": "0f8fad5bd9cb469fa16570867728950e",
  "captchaCode": "7"
}
```

字段说明：

- `roleType` 前台仅允许：
  - `0` 学生
  - `2` 教师
- 管理员不能通过前台注册。
- 密码规则：
  - 至少 8 位
  - 必须同时包含字母和数字

成功响应：

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": 101,
    "roleType": 2
  }
}
```

失败场景：

- `400` 用户名已存在
- `400` 邮箱已被注册
- `400` 验证码错误或过期
- `400` 密码强度不足
- `429` 注册过于频繁

### 2.4 登录

`POST /api/v1/auth/login`

请求体：

```json
{
  "username": "teacher01",
  "password": "abc12345",
  "captchaId": "0f8fad5bd9cb469fa16570867728950e",
  "captchaCode": "7"
}
```

说明：

- 正常情况下后端可按失败次数决定是否强制验证码。
- 前端建议始终携带 `captchaId/captchaCode`，避免风控状态切换时再改逻辑。

成功响应：

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "expiresIn": 7200,
    "user": {
      "id": 101,
      "username": "teacher01",
      "nickname": "张老师",
      "roleType": 2,
      "avatarUrl": ""
    }
  }
}
```

失败场景：

- `401` 用户名或密码错误
- `403` 账号已被禁用
- `400` 验证码错误或过期
- `429` 登录尝试过于频繁

### 2.5 刷新令牌

`POST /api/v1/auth/refresh`

请求体：

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "Token 刷新成功",
  "data": {
    "accessToken": "new-access-token",
    "expiresIn": 7200
  }
}
```

### 2.6 获取个人资料

`GET /api/v1/auth/profile`

请求头：

```http
Authorization: Bearer {accessToken}
```

### 2.7 更新个人资料

`PUT /api/v1/auth/profile`

请求体：

```json
{
  "nickname": "张老师",
  "email": "teacher01@example.com",
  "phone": "13800000000",
  "avatarUrl": "https://example.com/avatar.png"
}
```

### 2.8 修改密码

`PUT /api/v1/auth/password`

请求体：

```json
{
  "oldPassword": "abc12345",
  "newPassword": "new123456"
}
```

### 2.9 找回密码申请

`POST /api/v1/auth/password/reset/request`

用途：

- 用户通过 `用户名 + 邮箱 + 验证码` 发起密码找回。
- 当前版本不依赖邮件服务，后端直接返回短期有效 `resetToken`，便于前后端先联调。

请求体：

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "captchaId": "cid999",
  "captchaCode": "6"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "找回密码校验通过",
  "data": {
    "resetToken": "reset-123",
    "expiresInSeconds": 900,
    "username": "alice",
    "maskedEmail": "al***@example.com"
  }
}
```

前端约定：

- 前端第一步提交后进入“重置密码第二步”。
- `maskedEmail` 用于页面提示。
- `resetToken` 暂存在前端状态，不写入 URL。
- 生产环境如接入邮件或短信，可把 `resetToken` 改为只下发到第三方渠道，前端协议保持不变。

失败场景：

- `400` 用户名和邮箱不匹配
- `400` 验证码错误或过期

### 2.10 找回密码确认

`POST /api/v1/auth/password/reset/confirm`

请求体：

```json
{
  "resetToken": "reset-123",
  "newPassword": "secret123"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "密码重置成功",
  "data": null
}
```

失败场景：

- `400` `resetToken` 无效或已过期
- `400` 新密码强度不足

## 3. 报告中心接口

基础路径：`/api/v1/reports`

### 3.1 报告中心元数据

`GET /api/v1/reports/meta`

用途：

- 返回当前角色可生成的报告类型、报告中心配置等。

### 3.2 生成报告

`POST /api/v1/reports/generate`

请求体：

```json
{
  "reportName": "2026年4月教师岗位能力分析",
  "reportType": "teacher",
  "params": {
    "majorId": 12,
    "industry": "人工智能"
  }
}
```

说明：

- 后端会根据当前登录角色校验 `reportType` 是否允许生成。
- 系统支持“角色自有报告”和“行业分析报告”两类报告入口，具体可选项以 `/meta` 返回为准。

成功响应：

```json
{
  "code": 200,
  "message": "Report generation task created",
  "data": {
    "taskId": 301
  }
}
```

### 3.3 查询任务状态

`GET /api/v1/reports/{taskId}/status`

返回字段重点：

- `taskId`
- `status`
- `progress`
- `startedAt`
- `completedAt`
- `errorMessage`
- `resultSummary`

### 3.4 获取私有报告列表

`GET /api/v1/reports?page=1&pageSize=20`

### 3.5 获取公开报告列表

`GET /api/v1/reports/public?page=1&pageSize=20`

### 3.6 获取报告钻取详情

`GET /api/v1/reports/{id}/drill`

返回字段重点：

- `reportId`
- `reportName`
- `reportType`
- `targetAudience`
- `reportFocus`
- `sections`
- `chartCards`
- `chartInsights`
- `recommendations`
- `actionPlan`
- `comparisonItems`
- `reportMeta`
- `reportGovernance`
- `reportVersioning`
- `reportLifecycle`

说明：

- 该接口已自动进行敏感字段脱敏。
- 前端 drill 页面不应再自行裸展示邮箱、手机号、token 等敏感内容。

### 3.7 导出报告

`GET /api/v1/reports/{id}/export?format=pdf`

支持格式：

- `pdf`
- `html`
- `md`

快捷接口：

- `GET /api/v1/reports/{id}/pdf`

说明：

- 导出前统一做敏感字段脱敏。
- 导出文件中的邮箱、手机号、token/secret 等敏感信息会自动掩码。

### 3.8 报告调度

接口：

- `POST /api/v1/reports/schedule`
- `GET /api/v1/reports/schedules`
- `GET /api/v1/reports/schedules/{id}`
- `PUT /api/v1/reports/schedules/{id}/toggle`
- `DELETE /api/v1/reports/schedules/{id}`

创建调度请求体：

```json
{
  "scheduleName": "每周行业趋势报告",
  "reportType": "industry",
  "cronExpr": "0 0 8 ? * MON",
  "params": {
    "industry": "人工智能",
    "city": "上海"
  }
}
```

## 4. 敏感字段脱敏规则

当前统一脱敏服务已接入：

- 报告钻取详情接口
- 报告导出接口
- 公开报告详情接口

当前规则：

- 字段名包含 `email`：
  - `alice@example.com` -> `al***@example.com`
- 字段名包含 `phone/mobile`：
  - `13812345678` -> `138****5678`
- 字段名包含 `secret/token/authorization`：
  - 长字符串保留前 3 位和后 3 位，中间用 `****`
- 自由文本中的邮箱、手机号也会被正则替换

前端约定：

- 导出预览、公开报告详情直接使用接口返回值，不再做反向还原。
- 如果后续新增导出入口，必须复用同一套脱敏服务。

## 5. 开放 API 接口

基础路径：`/api/v1/open`

### 5.1 平台元数据

- `GET /api/v1/open/meta`
- `GET /api/v1/open/capabilities`
- `GET /api/v1/open/subscriptions/meta`

### 5.2 岗位查询

`GET /api/v1/open/jobs`

查询参数：

- `keyword`
- `city`
- `industry`
- `page`
- `pageSize`

说明：

- `pageSize` 最大 50。
- 接口会按 API Key 权限做租户范围和字段白名单过滤。

### 5.3 公开分析接口

接口：

- `GET /api/v1/open/analysis/overview`
- `GET /api/v1/open/analysis/skills?limit=20`
- `GET /api/v1/open/analysis/salary`
- `GET /api/v1/open/analysis/trend?city=上海&industry=人工智能`
- `GET /api/v1/open/analysis/industry?industry=人工智能&city=上海`
- `GET /api/v1/open/analysis/insights?industry=人工智能&city=上海&months=12`

### 5.4 公开报告接口

接口：

- `GET /api/v1/open/reports/public`
- `GET /api/v1/open/reports/public-scoped`
- `GET /api/v1/open/reports/{id}`

说明：

- 公开报告详情接口已自动脱敏。
- `public-scoped` 会结合 API Key 中的租户权限返回受控结果。

### 5.5 API Key 管理

接口：

- `POST /api/v1/open/api-keys`
- `GET /api/v1/open/api-keys`
- `PUT /api/v1/open/api-keys/{id}/toggle`
- `GET /api/v1/open/api-keys/logs`

创建 API Key 请求体示例：

```json
{
  "keyName": "School BI Access",
  "rateLimitQps": 20,
  "dailyQuota": 5000,
  "permissionProfile": "advanced",
  "tenantScope": "school:demo",
  "allowedJobFields": ["id", "title", "city", "industryName", "salaryText"]
}
```

## 6. 当前前端已接入的方法

文件：`frontend/src/api.js`

已提供方法：

- `fetchAuthCaptcha(type)`
- `requestPasswordReset(payload)`
- `confirmPasswordReset(payload)`

当前找回密码 UI 已接入页面：

- `frontend/src/views/ProfileView.vue`

页面交互流程：

1. 用户点击“忘记密码？找回密码”。
2. 输入用户名、邮箱、验证码。
3. 调用 `requestPasswordReset`。
4. 前端保存 `resetToken`，显示脱敏邮箱和第二步表单。
5. 用户输入新密码。
6. 调用 `confirmPasswordReset` 完成重置。

## 7. 联调注意事项

- 当前找回密码是“开发联调版”，`resetToken` 会直接返回前端，不代表最终生产策略。
- 如果你后面接短信或邮件服务，建议保留两个接口路径与请求体不变，只调整 `request` 接口返回数据。
- 新增任何报告导出、公开报告详情、审计回放接口时，都要先确认是否复用脱敏服务。
- 前端如果需要独立“找回密码页”，可以复用现有 `ProfileView.vue` 中的接口和状态结构，不需要改后端协议。
