# 接口基线与后续变动（单文档）

更新时间：2026-04-22  
说明：本文件是唯一对齐文档，分为两部分：`现阶段接口`、`后续变动`。

## 一、现阶段接口（可直接联调，先按这个做）

## 1) 通用约定

- 基础前缀：`/api/v1`
- 统一响应：`code/message/data`，分页接口再返回 `total/page/pageSize`
- 错误响应补充：`errorCode/requestId/timestamp`（排障与追踪统一字段）
- 成功标准：`code===200`
- 失败处理：前端直接展示 `message`
- 鉴权方式：登录后接口统一使用 `Authorization: Bearer {accessToken}`

## 2) 认证与画像

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/auth/captcha` | 获取验证码题面 | 否 | `type=AUTO/MATH/CHAR` |
| POST | `/auth/register` | 注册账号 | 否 | `username,password,email,nickname,roleType,captchaId,captchaCode` |
| POST | `/auth/login` | 登录并获取令牌 | 否 | `username,password,captchaId,captchaCode` |
| POST | `/auth/refresh` | 刷新 accessToken | 否 | `refreshToken` |
| GET | `/auth/profile` | 查询账号资料 | 是 | 返回 `id,username,nickname,roleType,avatarUrl,email,phone` |
| PUT | `/auth/profile` | 修改账号资料 | 是 | `nickname,email,phone,avatarUrl` |
| PUT | `/auth/password` | 已登录改密 | 是 | `oldPassword,newPassword` |
| POST | `/auth/password/reset/request` | 找回密码第一步 | 否 | `username,email,captchaId,captchaCode`，返回 `resetToken` |
| POST | `/auth/password/reset/confirm` | 找回密码第二步 | 否 | `resetToken,newPassword` |
| GET | `/profile` | 查询职业画像 | 是 | 返回 `profile + skills` |
| PUT | `/profile` | 更新职业画像 | 是 | `majorId,targetCityCode,expectedSalaryMin,expectedSalaryMax,skills,profileSummary` |
| PUT | `/profile/skills` | 单独更新技能 | 是 | `skills[]` |

冻结字段（必须保留）：

- 登录返回：`accessToken,refreshToken,expiresIn,user`
- 重置密码第一步返回：`resetToken,expiresInSeconds,maskedEmail`

## 3) 分析与洞察

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/analysis/overview` | 总览指标 | 否 | 首页公开看板 |
| GET | `/analysis/overview/personalized` | 个性化总览 | 是 | 登录用户画像驱动 |
| GET | `/analysis/salary` | 薪资分布 | 否 | `groupBy,limit` |
| GET | `/analysis/salary/trend` | 薪资趋势 | 否 | `city,industry,months` |
| POST | `/analysis/salary/predict` | 薪资预测 | 是 | 岗位/技能条件输入 |
| GET | `/analysis/skills` | 技能排行 | 否 | `limit` |
| GET | `/analysis/skills/graph` | 技能图谱数据 | 否 | `topN` |
| GET | `/analysis/regions/heatmap` | 城市热力 | 否 | 区域分布 |
| GET | `/analysis/sentiment` | 市场情绪 | 否 | 城市/行业情绪 |
| GET | `/analysis/insights/deep` | 深度洞察摘要 | 是 | 个性化洞察 |
| GET | `/analysis/welfare` | 福利分布 | 否 | `limit` |
| GET | `/analysis/company-size` | 公司规模分布 | 否 | - |
| GET | `/analysis/finance-stage` | 融资阶段分布 | 否 | - |
| GET | `/analysis/deep/supply-demand` | 供需分析结果 | 是 | `city,industry,months` |
| POST | `/analysis/deep/supply-demand` | 触发供需分析 | 是 | 分析参数 |
| POST | `/analysis/deep/curriculum-gap` | 课程与岗位缺口分析 | 是 | 课程相关参数 |
| GET | `/analysis/deep/salary-premium` | 溢价分析 | 是 | 维度参数 |
| GET | `/analysis/deep/trend-forecast` | 趋势预测 | 是 | 时间范围 |
| POST | `/analysis/deep/etl/run` | 触发深度分析 ETL | 是 | ETL 参数 |
| GET | `/analysis/deep/warehouse/overview` | 数仓概览 | 是 | 数仓状态 |

## 4) 岗位与收藏

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/jobs` | 岗位分页查询 | 否 | `keyword,city,industry,education,experience,page,pageSize,sortBy,sortOrder` |
| GET | `/jobs/{id}` | 岗位详情 | 否 | `id` |
| GET | `/jobs/search` | 全文检索 | 否 | `keyword,page,pageSize` |
| GET | `/jobs/stats` | 岗位总览统计 | 否 | - |
| GET | `/jobs/by-city` | 按城市聚合 | 否 | `limit` |
| GET | `/jobs/by-industry` | 按行业聚合 | 否 | `limit` |
| GET | `/jobs/by-education` | 按学历聚合 | 否 | - |
| GET | `/jobs/by-experience` | 按经验聚合 | 否 | - |
| GET | `/jobs/hot` | 热门岗位 TOP | 否 | `limit` |
| GET | `/favorites` | 我的收藏分页 | 是 | `page,pageSize` |
| POST | `/favorites/{jobId}` | 收藏岗位 | 是 | 可带 `note` |
| DELETE | `/favorites/{jobId}` | 取消收藏 | 是 | `jobId` |
| GET | `/favorites/{jobId}/check` | 是否已收藏 | 是 | 返回 `favorited:boolean` |

## 5) 知识图谱

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/kg/skill-map` | 技能关系图 | 否 | `limit` 等 |
| GET | `/kg/job-skill-matrix` | 岗位-技能矩阵 | 否 | `city,industry` 等 |
| GET | `/kg/career-ladder/{jobTitle}` | 职业阶梯 | 否 | `jobTitle` |
| POST | `/kg/build` | 触发图谱构建 | 是 | `minSupport` |

## 6) 推荐系统

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| POST | `/recommend/jobs` | 推荐岗位 | 是 | `skills,coreSkills,preferredCities,targetJobType,salaryMin,salaryMax,limit` |
| POST | `/recommend/skills` | 技能差距建议 | 是 | `userSkills,targetJobType,city` |
| POST | `/recommend/career-path` | 职业路径模拟 | 是 | `currentJob,targetJob,currentSkills,city` |
| POST | `/recommend/skill-radar` | 能力雷达 | 是 | 与技能建议类似 |
| POST | `/recommend/resume-review` | 简历评审建议 | 是 | `targetJob,resumeText,userSkills,currentJob,education,experienceYears,targetCity` |
| GET | `/recommend/similar-jobs/{jobId}` | 相似岗位推荐 | 是 | `limit` |
| GET | `/recommend/plan` | 综合推荐计划 | 是 | 汇总岗位+技能+行动 |
| GET | `/recommend/ranker-status` | 排序模型状态 | 是 | 当前训练状态 |
| POST | `/recommend/train-ranker` | 触发排序训练 | 是 | `limit` |
| GET | `/recommend/ops/recommend-health` | 推荐服务健康检查 | 是 | 运维检查 |

## 7) 报告中心

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/reports/meta` | 报告类型/中心配置 | 是 | 按角色返回 |
| GET | `/reports/readiness` | 报告生成前置检查 | 是 | 可带 `major` |
| GET | `/reports` | 私有报告列表 | 是 | `page,pageSize` |
| GET | `/reports/public` | 公开报告列表 | 是 | `page,pageSize` |
| GET | `/reports/publication/queue` | 发布审核队列 | 是（管理员） | `page,pageSize` |
| POST | `/reports/generate` | 创建报告任务 | 是 | `reportName,reportType,params` |
| GET | `/reports/{taskId}/status` | 查询任务状态 | 是 | 返回 `taskId,status,progress,startedAt,completedAt,errorMessage,resultSummary` |
| GET | `/reports/{id}/download` | 报告下载元数据 | 是 | 计下载/浏览 |
| GET | `/reports/{id}/drill` | 报告钻取详情 | 是 | 脱敏后详细内容 |
| GET | `/reports/{id}/pdf` | 下载 PDF | 是 | 文件流 |
| GET | `/reports/{id}/export` | 导出多格式 | 是 | `format=pdf/html/md` |
| DELETE | `/reports/{id}` | 删除报告 | 是 | 单条删除 |
| DELETE | `/reports/batch` | 批量删除报告 | 是 | `ids` |
| POST | `/reports/{id}/submit-review` | 提交审核 | 是 | 审核流入口 |
| POST | `/reports/{id}/review` | 审核通过/驳回 | 是（管理员） | `action=APPROVE/REJECT,comment` |
| POST | `/reports/{id}/publish` | 发布公开 | 是（管理员） | 需已审核 |
| POST | `/reports/{id}/unpublish` | 撤回公开 | 是（管理员） | 回到已审核态 |
| GET | `/reports/{id}/versions` | 版本历史 | 是 | 版本列表 |
| POST | `/reports/schedule` | 创建调度任务 | 是 | `scheduleName,reportType,cronExpr,params` |
| GET | `/reports/schedules` | 调度列表 | 是 | - |
| GET | `/reports/schedules/{id}` | 调度详情 | 是 | - |
| PUT | `/reports/schedules/{id}/toggle` | 启停调度 | 是 | - |
| DELETE | `/reports/schedules/{id}` | 删除调度 | 是 | - |

## 8) 教师与课程

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/teacher/courses` | 我的课程列表 | 是（教师/管理员） | - |
| POST | `/teacher/courses` | 新建课程 | 是 | `courseName,coreSkills,creditHours,semester,major,description` |
| PUT | `/teacher/courses/{id}` | 修改课程 | 是 | 同上 |
| DELETE | `/teacher/courses/{id}` | 删除课程 | 是 | `id` |
| GET | `/teacher/market-match` | 课程与市场匹配分析 | 是 | `major` 可选 |
| GET | `/teacher/teaching-reform` | 教改建议分析 | 是 | `major` 可选 |
| GET | `/teacher/materials/status` | 教学资料准备状态 | 是 | `major` 可选 |
| POST | `/teacher/materials/upload` | 上传教学资料 | 是 | `multipart file + materialType(SYLLABUS/STUDENT_STATUS) + major` |
| GET | `/teacher/materials/template/{materialType}` | 下载教学资料模板 | 是 | `materialType` |
| GET | `/curriculum` | 课程库分页 | 是 | `major,department,keyword,page,pageSize` |
| GET | `/curriculum/template` | 下载课程导入模板 | 是 | 文件流 |
| POST | `/curriculum/upload` | 上传课程 Excel | 是 | `multipart file` |
| GET | `/curriculum/{id}/skills` | 课程技能映射 | 是 | `id` |
| DELETE | `/curriculum/{id}` | 逻辑删除课程 | 是 | `id` |

## 9) 管理后台

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/admin/dashboard` | 管理看板 | 是（管理员） | 用户/岗位/报告统计 |
| GET | `/admin/users` | 用户分页查询 | 是 | `keyword,roleType,status,page,pageSize` |
| PUT | `/admin/users/{id}` | 编辑用户资料 | 是 | `nickname,email,phone,avatarUrl,roleType,status` |
| PUT | `/admin/users/{id}/status` | 改用户状态 | 是 | `status=0/1/2` |
| PUT | `/admin/users/{id}/role` | 改用户角色 | 是 | `roleType=0/1/2` |
| GET | `/admin/logs` | 操作日志分页 | 是 | `username,operation,startDate,endDate,page,pageSize` |

## 10) 数据采集台

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/crawl/tasks` | 采集任务列表 | 是（管理员） | `channel,status,page,pageSize` |
| POST | `/crawl/tasks` | 创建采集任务 | 是 | `taskName,channel,keywords,city,priority` |
| GET | `/crawl/tasks/{id}` | 采集任务详情 | 是 | `id` |
| PUT | `/crawl/tasks/{id}/status` | 更新任务状态 | 是 | `status=0/1/2/3` |
| GET | `/crawl/tasks/{taskId}/logs` | 任务日志分页 | 是 | `page,pageSize` |
| GET | `/crawl/tasks/quality` | 数据质量报告 | 是 | - |
| POST | `/crawl/tasks/quality/history/backfill` | 回填历史质量快照 | 是 | `limit` |
| GET | `/crawl/sources` | 数据源列表 | 是（管理员） | - |
| GET | `/crawl/sources/{id}` | 数据源详情 | 是 | `id` |
| POST | `/crawl/sources` | 新建数据源 | 是 | `sourceName,sourceCode,baseUrl,crawlStrategy` |
| PUT | `/crawl/sources/{id}` | 修改数据源 | 是 | `sourceName,baseUrl,crawlStrategy,isActive,healthStatus,totalRecords,lastCrawlAt` |
| DELETE | `/crawl/sources/{id}` | 删除数据源 | 是 | `id` |

## 11) 订阅与通知（Webhook 已停用）

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| POST | `/subscriptions` | 新建订阅 | 是 | `subscriptionType,filterConfig,channel` |
| GET | `/subscriptions/meta` | 订阅能力元数据 | 是 | 渠道/能力信息 |
| GET | `/subscriptions` | 订阅列表分页 | 是 | `page,pageSize` |
| DELETE | `/subscriptions/{id}` | 删除订阅 | 是 | `id` |
| GET | `/subscriptions/{id}/matches` | 查看匹配岗位 | 是 | `limit` |
| POST | `/subscriptions/{id}/dispatch` | 立即派发匹配结果 | 是 | `limit` |
| GET | `/notifications` | 通知列表 | 是 | `type,isRead,page,pageSize` |
| PUT | `/notifications/{id}/read` | 标记单条已读 | 是 | `id` |
| PUT | `/notifications/read-all` | 全部已读 | 是 | - |

停用接口（不再新增调用）：

- `GET /webhooks`
- `POST /webhooks`
- `DELETE /webhooks/{id}`
- `PUT /webhooks/{id}/toggle`
- `GET /webhooks/{id}/deliveries`

## 12) AI 助手

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| POST | `/ai/chat` | AI 流式对话 | 是 | SSE 事件：`typing/session/message/done/error`，入参 `message,sessionId` |
| POST | `/ai/agent/query` | AI Agent 工具问答 | 是 | `message,tool` |
| POST | `/ai/agent/import-profile` | 导入简历到画像 | 是 | `multipart file,overwriteSkills` |
| POST | `/ai/agent/parse-resume` | 解析简历结构化字段 | 是 | `multipart file` |
| GET | `/ai/quick-commands` | 快捷指令列表 | 是 | AI 预设动作 |
| GET | `/ai/conversations` | 会话列表 | 是 | 当前用户会话 |
| GET | `/ai/conversations/{sessionId}` | 会话详情 | 是 | 会话+消息记录 |
| DELETE | `/ai/conversations/{sessionId}` | 删除单会话 | 是 | `sessionId` |
| DELETE | `/ai/conversations/batch` | 批量删会话 | 是 | `sessionIds` |
| GET | `/ai/quota` | AI 配额查询 | 是 | 返回 `used,limit,remaining` |

AI Agent 工具白名单（按角色）：

- 学生：`RESUME_PARSE,PROFILE_IMPORT,JOB_MATCH,SKILL_GAP,SALARY_INSIGHT`
- 教师：`COURSE_MATCH,SYLLABUS_ANALYZE,TEACHING_REFORM,REPORT_ASSIST`
- 管理员：`OPS_INSIGHT,USER_GOVERNANCE,DATA_QUALITY_CHECK,REPORT_GOVERNANCE`
- 拒绝执行统一返回：`code=403,message=权限不足，当前账号无法访问该能力,errorCode=AI_TOOL_FORBIDDEN`

## 13) 平台建议与开放接口（开放平台页会用到）

### 13.1 平台建议

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/platform/advisory` | 平台级综合建议 | 是 | 用户行动建议与看板摘要 |

### 13.2 开放平台公开查询

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| GET | `/open/meta` | 开放平台元数据 | 否 | 公开能力描述 |
| GET | `/open/capabilities` | 开放能力列表 | 否 | 能力说明 |
| GET | `/open/subscriptions/meta` | 开放订阅元信息 | 否 | 订阅说明 |
| GET | `/open/jobs` | 开放岗位查询 | 否/Key | `keyword,city,industry,page,pageSize` |
| GET | `/open/analysis/overview` | 开放总览分析 | 否 | - |
| GET | `/open/analysis/skills` | 开放技能分析 | 否 | `limit` |
| GET | `/open/analysis/salary` | 开放薪资分析 | 否 | `city,industry` 等 |
| GET | `/open/analysis/trend` | 开放趋势分析 | 否 | `city,industry,months` |
| GET | `/open/analysis/industry` | 开放行业分析 | 否 | `industry,city` |
| GET | `/open/analysis/insights` | 开放洞察分析 | 否 | `industry,city,months` |
| GET | `/open/reports/public` | 开放公开报告列表 | 否 | `page,pageSize` |
| GET | `/open/reports/public-scoped` | 租户范围公开报告 | 是（Key） | `page,pageSize` |
| GET | `/open/reports/{id}` | 开放报告详情 | 否/Key | `id` |

### 13.3 开放平台 API Key 管理（管理员）

| 方法 | 路径 | 用途 | 鉴权 | 关键参数/说明 |
|---|---|---|---|---|
| POST | `/open/api-keys` | 创建 API Key | 是（管理员） | `keyName,rateLimitQps,dailyQuota,permissionProfile,tenantScope,allowedJobFields` |
| GET | `/open/api-keys` | API Key 列表 | 是（管理员） | - |
| PUT | `/open/api-keys/{id}/toggle` | 启停 API Key | 是（管理员） | `active` |
| GET | `/open/api-keys/logs` | API Key 调用日志 | 是（管理员） | `page,pageSize` |

## 14) 近期已完成优化（现阶段已生效）

1. 通知主链路已切到邮件能力：`/subscriptions/meta` 已以邮件通道为核心返回可用性。
2. 推荐链路已具备稳定性保护：健康检查、熔断、fallback、排序状态查询、训练入口均可用。
3. 报告治理流已落地：readiness、审核、发布、撤回、版本、调度、导出脱敏已形成闭环。
4. 教师端课程管理已形成上传-校验-分析闭环：模板、状态检查、匹配分析、教改建议均可用。
5. 收藏反馈特征已接入推荐闭环（当前只使用收藏行为，不引入点击/投递）。
6. 角色鉴权与路由跳转已可用：前端按角色拦截路由，后端按角色控制接口访问。

---

## 二、后续变动（你后续自己改按这个顺序）

## 1) 先做减法（本周）

1. 前端去掉 Webhook 入口与渠道选项（只保留 `IN_APP/EMAIL`）。
2. 文档统一标记 Webhook 为停用。
3. 后端移除 Webhook 相关代码与接口分支：
   - `PushService` 的 `WEBHOOK` 分支
   - `WebhookController/WebhookService`
   - 对应测试与文档条目
4. 兼容窗口结束后，清理 Webhook 相关数据表与迁移脚本。

## 2) 用户友好报错（优先）

1. 统一后端异常 message 为用户可理解语义，不向前端暴露 `403/500` 等技术细节。
2. 前端请求层统一错误映射：
   - 未登录/登录过期 -> “登录状态已失效，请重新登录”
   - 权限不足 -> “权限不足，当前账号无法访问该功能”
   - 频率限制 -> “操作过于频繁，请稍后重试”
   - 服务异常 -> “系统繁忙，请稍后重试”
3. 路由守卫与接口错误联动：鉴权失败时统一跳转登录页或无权限页，避免页面静默失败。

## 3) 后端优化与算法升级（核心）

1. 推荐准确性：
   - 在现有“收藏反馈”基础上做权重调优与分角色模型评估（学生/教师/管理员）。
   - 完善召回与重排指标：`Recall@K / NDCG@K / 命中率`。
   - 强化推荐解释字段，让“为什么推荐”可追溯。
2. 分析与报告质量：
   - 统一任务状态机与失败原因枚举。
   - 补齐数据时效标识（如 `updatedAt/dataVersion`），提升结果可信度。
   - 导出与钻取链路继续保持脱敏一致性。
3. 鉴权与跳转：
   - 继续强化角色权限边界与接口白名单。
   - 统一前后端“角色无权”提示和跳转策略。

## 4) 前端角色化流程优化（报告链路 + AI 助手链路）

1. 学生端（先补齐简历，再开放分析推荐）：
   - 前置条件：先上传简历，后端完成字段补齐（按昨日已确认字段口径）。
   - 未满足前置条件时，以下功能置灰并给出引导：简历优化、工作推荐、薪资分析、报告生成。
   - 满足前置条件后，再开放上述功能与对应页面跳转。
2. 教师端（先补齐教学资料，再开放教学分析）：
   - 前置条件：先上传课程数据、学生情况、教学大纲。
   - 未满足前置条件时，教学分析与报告功能仅展示引导页，不进入正式分析页面。
   - 满足前置条件后，开放课程管理、匹配分析、教改建议、报告链路。
3. 管理员端（先有采集与运营数据，再开放管理报告）：
   - 前置条件：已采集到可用业务数据，且具备用户管理与运营数据基础。
   - 未满足前置条件时，运营报告页显示“数据准备中/待采集”状态，不展示误导性空报表。
   - 满足前置条件后，开放用户管理洞察、运营分析、管理报告导出。
4. AI 助手按角色走不同 Agent 模式与工具白名单：
   - 学生 Agent：简历解析、岗位匹配、技能差距、求职建议类工具。
   - 教师 Agent：课程-岗位匹配、教学资料分析、课程优化建议类工具。
   - 管理员 Agent：运营分析、用户治理、数据质量巡检、报告治理类工具。
   - 统一约束：未满足各角色前置条件时，AI 助手返回明确引导语，不直接执行后续分析工具。
5. 前置条件统一接口化（避免前端分散判断）：
   - 新增 readiness 统一查询：`GET /readiness/{roleType}`（`roleType=STUDENT/TEACHER/ADMIN`）。
   - 返回建议字段：`ready:boolean,missingFields:string[],nextAction:string,updatedAt,dataVersion`。
   - 页面侧统一按 readiness 渲染：`ready=false` 时只展示引导与上传入口，不放行后续功能页。

## 5) 接口工程化治理（补齐）

1. OpenAPI 自动生成并纳入 CI。
2. 契约 diff 门禁（破坏性改动阻断）。
3. 错误码字典统一（模块化：AUTH/REPORT/RECOMMEND/...）。
4. 接口变更日志自动化输出。

## 6) 变更硬规则（必须遵守）

- 允许：新增可选字段（向后兼容）。
- 禁止：删字段、改字段名、改路径、改语义但不升版本。
- 破坏性变更：必须新开 `/api/v2`。

## 7) 验收口径（每项后续变动都按此验收）

1. 推荐准确性：
   - 必须产出按角色评估结果（学生/教师/管理员各一份）。
   - 必须可查询 `Recall@K/NDCG@K/命中率`（报表或接口至少一种）。
2. 分析与报告：
   - 任务失败原因必须命中枚举字典（不允许仅自由文本）。
   - 报告查询结果必须包含 `updatedAt/dataVersion`。
3. 鉴权与跳转：
   - 未登录、无权限、限流、服务异常四类场景文案必须统一。
   - 无权限场景必须稳定跳转，不允许静默失败。
4. 前端角色化流程：
   - 三类角色前置条件未满足时，后续页面必须拦截并给出引导。
   - AI 助手在前置条件未满足时必须拒绝执行并返回统一引导语。
