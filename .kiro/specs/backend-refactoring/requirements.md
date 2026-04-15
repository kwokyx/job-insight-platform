# Requirements Document

## Introduction

本文档定义了职业能力大数据服务平台后端重构项目的需求。该项目旨在将现有的轻量面向求职者的展示型平台重构为面向学院教学改革与管理决策的大数据分析服务平台。重构将分6个阶段实施，对齐五大核心子系统的设计目标，同时保持单体Spring Boot架构，复用现有可用模块。

## Glossary

- **Platform**: 职业能力大数据服务平台（Career Platform）
- **Backend_Service**: Java Spring Boot后端服务
- **Algorithm_Service**: Python FastAPI算法服务
- **Crawl_Module**: 分布式数据采集管理模块（crawl/）
- **Warehouse_Module**: 数据仓库与深度分析模块（warehouse/）
- **Report_Module**: 报告自动生成系统（report/）
- **Recommend_Module**: 就业岗位推送系统（recommend/）
- **Subscription_Module**: 岗位推送与Webhook订阅模块（subscription/）
- **Open_API_Module**: 对外公开API模块（open/）
- **Knowledge_Graph_Module**: 知识图谱模块（knowledgegraph/）
- **Auth_Module**: 认证鉴权模块（auth/）
- **Job_Module**: 职位数据CRUD模块（job/）
- **Profile_Module**: 用户画像模块（profile/）
- **AI_Module**: AI助手模块（ai/）
- **System_Module**: 管理后台模块（system/）
- **Administrator**: 管理员角色
- **Teacher**: 教师角色
- **Student**: 学生角色
- **Regular_User**: 普通用户角色
- **Data_Source**: 数据采集源配置
- **Crawl_Task**: 数据采集任务
- **ODS_Layer**: 数据仓库操作数据层（Operational Data Store）
- **DWD_Layer**: 数据仓库明细数据层（Data Warehouse Detail）
- **DWS_Layer**: 数据仓库汇总数据层（Data Warehouse Summary）
- **ADS_Layer**: 数据仓库应用数据层（Application Data Store）
- **Report_Template**: 报告模板
- **Knowledge_Entity**: 知识图谱实体
- **Webhook_Subscription**: Webhook订阅配置

## Requirements

---

## 一、基础设施与权限体系

### Requirement 1: 角色权限体系扩展

**User Story:** 作为系统管理员，我希望将角色体系从2级扩展到4级，以便支持不同用户群体的差异化访问控制。

#### Acceptance Criteria

1. THE Auth_Module SHALL support four role levels: Regular_User, Student, Teacher, and Administrator
2. WHEN a user authenticates, THE Auth_Module SHALL assign exactly one role to the user
3. THE Auth_Module SHALL enforce role-based access control for all protected endpoints
4. WHEN a Teacher accesses teaching analytics features, THE Auth_Module SHALL grant access
5. WHEN a Regular_User attempts to access teaching analytics features, THE Auth_Module SHALL deny access and return HTTP 403
6. THE Backend_Service SHALL maintain backward compatibility with existing JWT token structure
7. FOR ALL role assignments, THE Auth_Module SHALL persist role information in the user database table

---

## 二、分布式数据采集模块（Crawl Module）

### Requirement 2: 数据采集源管理

**User Story:** 作为管理员，我希望能够配置和管理多个数据采集源，以便从不同招聘平台获取职位数据。

#### Acceptance Criteria

1. WHEN an Administrator creates a Data_Source, THE Crawl_Module SHALL validate the source configuration and store it
2. THE Crawl_Module SHALL support Data_Source types including: web scraping, API integration, and file import
3. WHEN a Data_Source configuration is updated, THE Crawl_Module SHALL validate the new configuration before applying changes
4. THE Crawl_Module SHALL allow Administrators to enable or disable a Data_Source
5. WHEN a Data_Source is disabled, THE Crawl_Module SHALL prevent new Crawl_Tasks from using that source
6. THE Crawl_Module SHALL store Data_Source metadata including: name, type, URL, authentication credentials, rate limits, and status
7. FOR ALL Data_Source credentials, THE Crawl_Module SHALL encrypt sensitive information before storage

### Requirement 3: 数据采集任务调度

**User Story:** 作为管理员，我希望能够创建和调度数据采集任务，以便定期从配置的数据源获取最新职位信息。

#### Acceptance Criteria

1. WHEN an Administrator creates a Crawl_Task, THE Crawl_Module SHALL validate task parameters and store the task configuration
2. THE Crawl_Module SHALL support scheduling modes: one-time execution, cron expression, and interval-based
3. WHEN a Crawl_Task is scheduled, THE Backend_Service SHALL send task configuration to the Algorithm_Service for execution
4. THE Crawl_Module SHALL record task execution status including: pending, running, completed, failed, and cancelled
5. WHEN a Crawl_Task execution completes, THE Algorithm_Service SHALL report execution results to the Backend_Service
6. THE Crawl_Module SHALL store execution logs including: start time, end time, records collected, errors encountered, and execution duration
7. WHEN a Crawl_Task fails, THE Crawl_Module SHALL record error details and support retry configuration
8. THE Crawl_Module SHALL allow Administrators to manually trigger, pause, or cancel a Crawl_Task

### Requirement 4: 采集数据质量监控

**User Story:** 作为管理员，我希望能够监控数据采集质量，以便及时发现和处理数据异常。

#### Acceptance Criteria

1. WHEN a Crawl_Task completes, THE Crawl_Module SHALL calculate data quality metrics including: completeness rate, duplication rate, and field validity rate
2. THE Crawl_Module SHALL detect duplicate job records across different Data_Sources
3. WHEN data quality metrics fall below configured thresholds, THE Crawl_Module SHALL generate quality alerts
4. THE Crawl_Module SHALL provide quality trend analysis for each Data_Source over time
5. THE Crawl_Module SHALL allow Administrators to view detailed quality reports for each Crawl_Task execution
6. WHEN invalid data is detected, THE Crawl_Module SHALL log validation errors with specific field and reason information

### Requirement 4.1: 增量抓取与历史追踪

**User Story:** 作为管理员，我希望系统能够追踪同一岗位随时间的变化，以便分析岗位演变趋势。

#### Acceptance Criteria

1. THE Crawl_Module SHALL maintain a job_history table to store snapshots of each job posting over time
2. WHEN a job posting is re-crawled, THE Crawl_Module SHALL compare with the previous version to detect changes
3. THE Crawl_Module SHALL track changes in: salary range, job requirements, job status, and description
4. THE Crawl_Module SHALL use composite unique key (source_site + job_id_source) to identify duplicate jobs
5. THE Crawl_Module SHALL support incremental crawling mode that only fetches new or updated jobs
6. THE Crawl_Module SHALL calculate job freshness score based on last update time
7. THE Crawl_Module SHALL identify fake job postings using pattern detection (e.g., jobs posted for extended periods without hiring)

### Requirement 4.2: 代理池状态监控

**User Story:** 作为管理员，我希望监控爬虫代理池的健康状态，以便及时处理IP封禁问题。

#### Acceptance Criteria

1. THE Crawl_Module SHALL track proxy pool metrics including: total IPs, available IPs, blocked IPs, and success rate
2. THE Crawl_Module SHALL provide a dashboard showing proxy pool health status
3. WHEN proxy success rate falls below 50%, THE Crawl_Module SHALL generate alerts
4. THE Crawl_Module SHALL log proxy usage for each crawl task including: IP address, response code, and response time
5. THE Crawl_Module SHALL support manual proxy pool refresh via admin API
6. THE Crawl_Module SHALL automatically rotate proxies when detecting rate limiting or blocking

---

## 三、数据仓库与深度分析模块（Warehouse Module）

### Requirement 5: 数据仓库分层架构

**User Story:** 作为数据分析师，我希望系统实现标准的数据仓库分层架构，以便支持复杂的数据分析需求。

#### Acceptance Criteria

1. THE Warehouse_Module SHALL implement four data layers: ODS_Layer, DWD_Layer, DWS_Layer, and ADS_Layer
2. WHEN new job data is collected, THE Warehouse_Module SHALL store raw data in the ODS_Layer
3. THE Warehouse_Module SHALL transform ODS_Layer data into cleaned and standardized DWD_Layer data
4. THE Warehouse_Module SHALL aggregate DWD_Layer data into DWS_Layer summary tables
5. THE Warehouse_Module SHALL generate ADS_Layer application-specific data marts for reporting and analytics
6. THE Warehouse_Module SHALL execute ETL processes in the correct layer sequence: ODS → DWD → DWS → ADS
7. WHEN an ETL process fails, THE Warehouse_Module SHALL log error details and support manual retry

### Requirement 6: 行业与岗位维度分析

**User Story:** 作为教师，我希望能够查看行业和岗位的多维度分析数据，以便指导学生职业规划和课程设计。

#### Acceptance Criteria

1. THE Warehouse_Module SHALL calculate industry-level metrics including: job count, average salary, skill demand distribution, and growth trends
2. THE Warehouse_Module SHALL calculate position-level metrics including: job count, salary range, education requirements, and experience requirements
3. THE Warehouse_Module SHALL support time-series analysis with granularity options: daily, weekly, monthly, and yearly
4. THE Warehouse_Module SHALL support geographic analysis at province and city levels
5. WHEN a Teacher requests industry analysis, THE Warehouse_Module SHALL retrieve data from the ADS_Layer within 2 seconds
6. THE Warehouse_Module SHALL refresh dimension analysis data at least once per day

### Requirement 7: 技能需求趋势分析

**User Story:** 作为教师，我希望能够分析技能需求趋势，以便调整教学内容和培养方案。

#### Acceptance Criteria

1. THE Warehouse_Module SHALL extract skill keywords from job descriptions and requirements
2. THE Warehouse_Module SHALL calculate skill frequency and co-occurrence patterns
3. THE Warehouse_Module SHALL identify emerging skills based on growth rate over time
4. THE Warehouse_Module SHALL categorize skills into technical skills, soft skills, and domain knowledge
5. WHEN a Teacher requests skill trend analysis, THE Warehouse_Module SHALL provide trend data for the past 12 months
6. THE Warehouse_Module SHALL support skill comparison across different industries and positions

### Requirement 7.1: 课程数据管理

**User Story:** 作为管理员，我希望能够管理学校课程和教学大纲数据，以便进行供需分析。

#### Acceptance Criteria

1. THE Warehouse_Module SHALL provide curriculum CRUD APIs for Administrators
2. THE Warehouse_Module SHALL support Excel batch import for curriculum data with columns: course_code, course_name, credit_hours, description, keywords
3. THE Warehouse_Module SHALL validate imported curriculum data for required fields and data format
4. THE Warehouse_Module SHALL store curriculum data in biz_curriculum table
5. THE Warehouse_Module SHALL extract skill keywords from curriculum descriptions automatically
6. THE Warehouse_Module SHALL maintain curriculum-skill mapping in biz_curriculum_skill_mapping table
7. THE Warehouse_Module SHALL support curriculum versioning to track teaching plan changes over time

### Requirement 7.2: 供需剪刀差诊断

**User Story:** 作为教师，我希望系统能够诊断学校课程与市场需求的差距，以便指导教学改革。

#### Acceptance Criteria

1. THE Warehouse_Module SHALL provide a curriculum management interface for Administrators to upload course syllabi
2. THE Warehouse_Module SHALL support Excel import for batch curriculum data upload
3. THE Warehouse_Module SHALL extract keywords from curriculum descriptions and map them to skill entities
4. THE Warehouse_Module SHALL use TF-IDF vector matching to compare curriculum keywords with job skill requirements
5. THE Warehouse_Module SHALL generate a supply-demand gap report identifying: skills taught but not needed, and skills needed but not taught
6. THE Warehouse_Module SHALL calculate gap severity scores based on frequency and importance weights
7. WHEN a Teacher requests supply-demand analysis, THE Warehouse_Module SHALL return diagnostic results within 5 seconds
8. THE Warehouse_Module SHALL support filtering gap analysis by industry, position type, and geographic region

### Requirement 7.3: 深度分析接口扩展

**User Story:** 作为教师，我希望获得更深入的数据分析结果，以便支持教学决策。

#### Acceptance Criteria

1. THE Analysis_Module SHALL provide POST /api/v1/analysis/deep/supply-demand endpoint for supply-demand gap diagnosis
2. THE Analysis_Module SHALL provide POST /api/v1/analysis/deep/curriculum-gap endpoint for curriculum vs market comparison
3. THE Analysis_Module SHALL provide GET /api/v1/analysis/deep/trend-forecast endpoint that forwards requests to Algorithm_Service
4. THE Analysis_Module SHALL provide GET /api/v1/analysis/deep/salary-premium endpoint for education and experience premium analysis
5. THE Analysis_Module SHALL calculate salary premium by comparing average salaries across education levels and experience ranges
6. THE Analysis_Module SHALL support time-range filtering for all deep analysis endpoints
7. THE Analysis_Module SHALL cache deep analysis results in Redis with 1-hour TTL

### Requirement 7.4: 定时ETL调度

**User Story:** 作为系统架构师，我希望数据仓库能够自动执行ETL任务，以便保持数据新鲜度。

#### Acceptance Criteria

1. THE Warehouse_Module SHALL execute full ETL process daily at 02:00 AM using @Scheduled annotation
2. THE Warehouse_Module SHALL execute ETL in sequence: ODS → DWD → DWS → ADS
3. THE Warehouse_Module SHALL log ETL execution details including: start time, end time, records processed, and errors
4. WHEN ETL fails, THE Warehouse_Module SHALL send alert notifications to administrators
5. THE Warehouse_Module SHALL support manual ETL triggering via admin API endpoint
6. THE Warehouse_Module SHALL support incremental ETL mode that only processes new data since last run
7. THE Warehouse_Module SHALL maintain ETL execution history for at least 90 days

---

## 四、报告自动生成系统（Report Module）

### Requirement 8: 报告模板管理

**User Story:** 作为管理员，我希望能够创建和管理报告模板，以便生成标准化的分析报告。

#### Acceptance Criteria

1. WHEN an Administrator creates a Report_Template, THE Report_Module SHALL validate template structure and store it
2. THE Report_Module SHALL support template components including: charts, tables, text blocks, and data queries
3. THE Report_Module SHALL allow Administrators to define template parameters for dynamic content generation
4. WHEN a Report_Template is updated, THE Report_Module SHALL version the template and preserve previous versions
5. THE Report_Module SHALL support template categories including: industry analysis, position analysis, skill analysis, and custom reports
6. THE Report_Module SHALL allow Administrators to preview templates before publishing

### Requirement 9: 自动报告生成

**User Story:** 作为教师，我希望系统能够自动生成分析报告，以便快速获取数据洞察。

#### Acceptance Criteria

1. WHEN a Teacher requests report generation, THE Report_Module SHALL select the appropriate Report_Template based on report type
2. THE Report_Module SHALL query data from the Warehouse_Module ADS_Layer
3. THE Report_Module SHALL render charts and tables according to template specifications
4. THE Report_Module SHALL generate reports in PDF and HTML formats
5. WHEN report generation completes, THE Report_Module SHALL store the generated report and return a download link within 10 seconds
6. THE Report_Module SHALL support scheduled report generation with email delivery
7. WHEN report generation fails, THE Report_Module SHALL log error details and notify the requester
8. THE Report_Module SHALL use Thymeleaf template engine to render HTML reports
9. THE Report_Module SHALL use Flying Saucer (xhtmlrenderer) to convert HTML reports to PDF with watermark support

### Requirement 9.1: LLM增强报告生成

**User Story:** 作为教师，我希望报告能够包含AI生成的诊断摘要和教改建议，以便获得更深入的洞察。

#### Acceptance Criteria

1. WHEN generating a report, THE Report_Module SHALL call the AI_Module to generate diagnostic summaries
2. THE Report_Module SHALL feed structured data from DWS_Layer to the LLM via RAG enhancement
3. THE LLM SHALL generate human-readable analysis paragraphs including: trend interpretation, anomaly detection, and actionable recommendations
4. THE Report_Module SHALL integrate LLM-generated content into the final report seamlessly
5. WHEN LLM service is unavailable, THE Report_Module SHALL fall back to template-based summaries
6. THE Report_Module SHALL cache LLM-generated summaries for similar data patterns to reduce API costs

### Requirement 10: 报告历史与版本管理

**User Story:** 作为教师，我希望能够查看历史报告和版本对比，以便追踪数据变化趋势。

#### Acceptance Criteria

1. THE Report_Module SHALL store all generated reports with metadata including: generation time, template version, parameters, and requester
2. WHEN a Teacher views report history, THE Report_Module SHALL display reports in reverse chronological order
3. THE Report_Module SHALL allow Teachers to download previously generated reports
4. THE Report_Module SHALL support report comparison between two different time periods
5. THE Report_Module SHALL retain report history for at least 12 months

### Requirement 10.1: 定时报告计划

**User Story:** 作为教师，我希望能够设置定时报告计划，以便自动接收周报或月报。

#### Acceptance Criteria

1. THE Report_Module SHALL provide POST /api/v1/reports/schedule endpoint for creating scheduled report plans
2. THE Report_Module SHALL support schedule frequencies: daily, weekly, monthly, and custom cron expressions
3. THE Report_Module SHALL support delivery methods: email and in-app notification
4. WHEN a scheduled report is generated, THE Report_Module SHALL send it to configured recipients automatically
5. THE Report_Module SHALL allow Teachers to view, update, and delete their scheduled report plans
6. THE Report_Module SHALL execute scheduled report generation using Spring @Scheduled tasks
7. THE Report_Module SHALL log all scheduled report executions with status and delivery results

---

## 五、就业岗位推送系统（Recommend & Subscription Module）

### Requirement 11: 个性化岗位推荐

**User Story:** 作为学生，我希望系统能够根据我的画像推荐合适的岗位，以便找到匹配的就业机会。

#### Acceptance Criteria

1. WHEN a Student requests job recommendations, THE Recommend_Module SHALL retrieve the student's profile from the Profile_Module
2. THE Recommend_Module SHALL call the Algorithm_Service matching API to calculate job-profile similarity scores
3. THE Recommend_Module SHALL rank jobs by similarity score in descending order
4. THE Recommend_Module SHALL return the top 20 recommended jobs with similarity scores
5. THE Recommend_Module SHALL filter out expired or inactive job postings
6. WHEN a Student views a recommended job, THE Recommend_Module SHALL record the interaction for feedback learning
7. THE Recommend_Module SHALL support filtering recommendations by location, salary range, and industry

### Requirement 12: 岗位推送订阅

**User Story:** 作为学生，我希望能够订阅岗位推送，以便及时收到符合我期望的新职位通知。

#### Acceptance Criteria

1. WHEN a Student creates a subscription, THE Subscription_Module SHALL validate subscription criteria and store the configuration
2. THE Subscription_Module SHALL support subscription criteria including: keywords, location, salary range, industry, and position type
3. WHEN new jobs matching subscription criteria are collected, THE Subscription_Module SHALL identify matching subscriptions within 5 minutes
4. THE Subscription_Module SHALL send notifications via email and in-app messages
5. THE Subscription_Module SHALL allow Students to configure notification frequency: real-time, daily digest, or weekly digest
6. WHEN a Student unsubscribes, THE Subscription_Module SHALL stop sending notifications immediately
7. THE Subscription_Module SHALL limit each Student to a maximum of 10 active subscriptions

### Requirement 13: Webhook集成支持

**User Story:** 作为外部系统开发者，我希望能够通过Webhook接收岗位推送，以便集成到第三方应用。

#### Acceptance Criteria

1. WHEN an Administrator creates a Webhook_Subscription, THE Subscription_Module SHALL validate the webhook URL and store the configuration
2. THE Subscription_Module SHALL support webhook event types including: new_job, job_updated, and job_expired
3. WHEN a subscribed event occurs, THE Subscription_Module SHALL send an HTTP POST request to the configured webhook URL within 10 seconds
4. THE Subscription_Module SHALL include event data in JSON format in the webhook payload
5. WHEN a webhook delivery fails, THE Subscription_Module SHALL retry up to 3 times with exponential backoff
6. THE Subscription_Module SHALL log all webhook delivery attempts with status codes and response times
7. THE Subscription_Module SHALL support webhook signature verification using HMAC-SHA256

### Requirement 13.1: 推送执行调度

**User Story:** 作为系统架构师，我希望系统能够自动执行订阅匹配和推送，以便用户及时收到新岗位通知。

#### Acceptance Criteria

1. THE Subscription_Module SHALL execute subscription matching daily at 08:00 AM using @Scheduled annotation
2. THE Subscription_Module SHALL query new jobs collected in the past 24 hours
3. THE Subscription_Module SHALL match new jobs against all active subscriptions based on criteria
4. WHEN a job matches a subscription, THE Subscription_Module SHALL create a notification record
5. THE Subscription_Module SHALL send notifications via configured channels: email and in-app message
6. THE Subscription_Module SHALL batch notifications to avoid overwhelming users (max 20 jobs per notification)
7. THE Subscription_Module SHALL respect user notification frequency preferences: real-time, daily digest, or weekly digest
8. THE Subscription_Module SHALL log all push executions with match count and delivery status

### Requirement 13.2: 站内消息通知

**User Story:** 作为学生，我希望在平台内接收岗位推送通知，以便不错过重要机会。

#### Acceptance Criteria

1. THE Subscription_Module SHALL store in-app notifications in biz_notification table
2. THE Subscription_Module SHALL support notification types: job_match, system_announcement, and report_ready
3. WHEN a new notification is created, THE Subscription_Module SHALL mark it as unread
4. THE Subscription_Module SHALL provide API endpoints for: listing notifications, marking as read, and deleting notifications
5. THE Subscription_Module SHALL support notification pagination with default page size of 20
6. THE Subscription_Module SHALL automatically delete notifications older than 90 days
7. THE Subscription_Module SHALL provide unread notification count API for UI badge display

### Requirement 13.3: 邮件推送集成

**User Story:** 作为学生，我希望通过邮件接收岗位推送，以便在不登录平台时也能获取信息。

#### Acceptance Criteria

1. THE Subscription_Module SHALL integrate Spring Mail for email delivery
2. THE Subscription_Module SHALL use HTML email templates for formatted job notifications
3. THE Subscription_Module SHALL include job details in email: title, company, salary, location, and application link
4. WHEN email delivery fails, THE Subscription_Module SHALL retry up to 3 times with exponential backoff
5. THE Subscription_Module SHALL log all email delivery attempts with status and error messages
6. THE Subscription_Module SHALL support email unsubscribe links in all notification emails
7. THE Subscription_Module SHALL respect user email preferences and opt-out requests

---

## 六、对外公开API模块（Open API Module）

### Requirement 14: 公开API接口

**User Story:** 作为外部系统开发者，我希望能够通过公开API访问平台数据，以便构建第三方应用。

#### Acceptance Criteria

1. THE Open_API_Module SHALL provide RESTful API endpoints for job search, industry statistics, and skill trends
2. WHEN an external developer requests API access, THE Open_API_Module SHALL require API key authentication
3. THE Open_API_Module SHALL enforce rate limiting of 1000 requests per hour per API key
4. WHEN rate limit is exceeded, THE Open_API_Module SHALL return HTTP 429 with retry-after header
5. THE Open_API_Module SHALL return responses in JSON format with consistent error structure
6. THE Open_API_Module SHALL provide API documentation using OpenAPI 3.0 specification
7. THE Open_API_Module SHALL log all API requests with timestamp, endpoint, API key, and response status

### Requirement 15: API访问控制与配额管理

**User Story:** 作为管理员，我希望能够管理API访问权限和配额，以便控制外部系统的使用。

#### Acceptance Criteria

1. WHEN an Administrator creates an API key, THE Open_API_Module SHALL generate a unique key and store it securely
2. THE Open_API_Module SHALL allow Administrators to set custom rate limits for each API key
3. THE Open_API_Module SHALL allow Administrators to enable or disable API keys
4. WHEN an API key is disabled, THE Open_API_Module SHALL reject requests using that key with HTTP 401
5. THE Open_API_Module SHALL provide usage statistics for each API key including: request count, error rate, and average response time
6. THE Open_API_Module SHALL support API key expiration dates

### Requirement 15.1: API限流过滤器

**User Story:** 作为系统架构师，我希望实现API限流机制，以便防止滥用和保护系统资源。

#### Acceptance Criteria

1. THE Open_API_Module SHALL implement rate limiting using Redis and Lua scripts
2. THE Open_API_Module SHALL use token bucket algorithm for rate limiting
3. THE Open_API_Module SHALL enforce rate limits per API key with configurable QPS and daily quota
4. WHEN rate limit is exceeded, THE Open_API_Module SHALL return HTTP 429 with Retry-After header
5. THE Open_API_Module SHALL implement RateLimitFilter that intercepts all /api/v1/open/** requests
6. THE Open_API_Module SHALL log rate limit violations with API key, endpoint, and timestamp
7. THE Open_API_Module SHALL provide rate limit status in response headers: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset

### Requirement 15.2: 公开数据接口扩展

**User Story:** 作为外部开发者，我希望访问更多公开数据接口，以便构建丰富的第三方应用。

#### Acceptance Criteria

1. THE Open_API_Module SHALL provide GET /api/v1/open/analysis/salary endpoint for salary distribution data
2. THE Open_API_Module SHALL provide GET /api/v1/open/analysis/trend endpoint for job posting trend data
3. THE Open_API_Module SHALL provide GET /api/v1/open/reports/public endpoint for listing public reports
4. THE Open_API_Module SHALL provide GET /api/v1/open/reports/{id} endpoint for accessing public report details
5. THE Open_API_Module SHALL only expose reports marked as is_public=1 via public endpoints
6. THE Open_API_Module SHALL increment view_count when a public report is accessed
7. THE Open_API_Module SHALL cache public API responses in Redis with 5-minute TTL

---

## 七、知识图谱模块（Knowledge Graph Module）

### Requirement 16: 知识图谱构建

**User Story:** 作为数据分析师，我希望系统能够构建行业知识图谱，以便发现行业、岗位、技能之间的关联关系。

#### Acceptance Criteria

1. THE Knowledge_Graph_Module SHALL extract Knowledge_Entities from job data including: industries, positions, skills, companies, and education requirements
2. THE Knowledge_Graph_Module SHALL identify relationships between entities including: requires, belongs_to, similar_to, and evolves_to
3. THE Knowledge_Graph_Module SHALL store graph data in a structured format supporting graph queries
4. THE Knowledge_Graph_Module SHALL calculate entity importance scores based on frequency and connectivity
5. WHEN new job data is processed, THE Knowledge_Graph_Module SHALL update the knowledge graph incrementally
6. THE Knowledge_Graph_Module SHALL support graph queries including: shortest path, neighbor discovery, and subgraph extraction

### Requirement 17: 知识图谱查询与可视化

**User Story:** 作为教师，我希望能够查询和可视化知识图谱，以便理解行业知识结构。

#### Acceptance Criteria

1. WHEN a Teacher queries the knowledge graph, THE Knowledge_Graph_Module SHALL return matching entities and relationships within 3 seconds
2. THE Knowledge_Graph_Module SHALL support query types including: entity search, relationship traversal, and pattern matching
3. THE Knowledge_Graph_Module SHALL provide graph visualization data in JSON format compatible with D3.js or ECharts
4. THE Knowledge_Graph_Module SHALL support filtering by entity type and relationship type
5. THE Knowledge_Graph_Module SHALL limit visualization results to a maximum of 100 nodes to ensure performance
6. WHEN a Teacher explores entity details, THE Knowledge_Graph_Module SHALL provide entity attributes and connected entities

### Requirement 17.1: 技能关系图谱

**User Story:** 作为教师，我希望查看技能之间的关联关系，以便设计合理的课程体系。

#### Acceptance Criteria

1. THE Knowledge_Graph_Module SHALL provide GET /api/v1/kg/skill-map endpoint for skill relationship graph
2. THE Knowledge_Graph_Module SHALL extract skill co-occurrence patterns from biz_job_skill table
3. THE Knowledge_Graph_Module SHALL calculate skill relationship weights based on co-occurrence frequency
4. THE Knowledge_Graph_Module SHALL categorize skills into: programming languages, frameworks, tools, and soft skills
5. THE Knowledge_Graph_Module SHALL return graph data in JSON format with nodes and edges arrays
6. THE Knowledge_Graph_Module SHALL support filtering by skill category and minimum relationship weight
7. THE Knowledge_Graph_Module SHALL limit graph results to 100 nodes for performance

### Requirement 17.2: 岗位-技能需求矩阵

**User Story:** 作为教师，我希望查看不同岗位对技能的需求强度，以便了解市场需求结构。

#### Acceptance Criteria

1. THE Knowledge_Graph_Module SHALL provide GET /api/v1/kg/job-skill-matrix endpoint
2. THE Knowledge_Graph_Module SHALL calculate skill demand intensity for each job title
3. THE Knowledge_Graph_Module SHALL return matrix data with job titles as rows and skills as columns
4. THE Knowledge_Graph_Module SHALL normalize demand intensity to 0-100 scale
5. THE Knowledge_Graph_Module SHALL support filtering by industry and time period
6. THE Knowledge_Graph_Module SHALL highlight emerging skill demands with growth indicators

### Requirement 17.3: 职业晋升路径图

**User Story:** 作为学生，我希望查看某个岗位的职业晋升路径，以便规划长期发展。

#### Acceptance Criteria

1. THE Knowledge_Graph_Module SHALL provide GET /api/v1/kg/career-ladder/{jobTitle} endpoint
2. THE Knowledge_Graph_Module SHALL identify career progression relationships based on job title similarity and seniority levels
3. THE Knowledge_Graph_Module SHALL store career path relationships in biz_career_path table
4. THE Knowledge_Graph_Module SHALL return career ladder as a directed graph with nodes (positions) and edges (transitions)
5. THE Knowledge_Graph_Module SHALL include transition metadata: typical years of experience, salary increase, and required skills
6. THE Knowledge_Graph_Module SHALL support multiple career paths for the same starting position

### Requirement 17.4: 知识图谱重建

**User Story:** 作为管理员，我希望能够手动触发知识图谱重建，以便在数据更新后刷新图谱。

#### Acceptance Criteria

1. THE Knowledge_Graph_Module SHALL provide POST /api/v1/kg/build endpoint for manual graph rebuild
2. THE Knowledge_Graph_Module SHALL execute graph building asynchronously to avoid blocking
3. THE Knowledge_Graph_Module SHALL extract entities from job data: industries, positions, skills, companies, education requirements
4. THE Knowledge_Graph_Module SHALL identify relationships: requires, belongs_to, similar_to, evolves_to
5. THE Knowledge_Graph_Module SHALL calculate entity importance scores based on frequency and connectivity
6. THE Knowledge_Graph_Module SHALL store graph data in biz_skill_relation and biz_career_path tables
7. WHEN graph building completes, THE Knowledge_Graph_Module SHALL send completion notification to the administrator

---

## 八、基础设施与配置

### Requirement 18: 数据库架构扩展

**User Story:** 作为系统架构师，我希望扩展数据库架构以支持新模块，同时保持与现有模块的兼容性。

#### Acceptance Criteria

1. THE Backend_Service SHALL create database tables for Crawl_Module including: biz_data_source, biz_crawl_task, biz_crawl_log, and biz_job_history
2. THE Backend_Service SHALL create database tables for Warehouse_Module including: dwd_job_fact, dws_daily_city_summary, dws_monthly_industry_summary, ads_dashboard_kpi, biz_curriculum, and biz_curriculum_skill_mapping
3. THE Backend_Service SHALL create database tables for Subscription_Module including: biz_user_subscription, biz_notification, biz_webhook_endpoint, and biz_webhook_delivery
4. THE Backend_Service SHALL create database tables for Knowledge_Graph_Module including: biz_skill_relation and biz_career_path
5. THE Backend_Service SHALL create database tables for Open_API_Module including: sys_api_key and sys_api_call_log
6. THE Backend_Service SHALL extend sys_user table to support role_type field with values: 0=Regular_User, 1=Administrator, 2=Teacher
7. THE Backend_Service SHALL maintain referential integrity using foreign key constraints
8. THE Backend_Service SHALL create appropriate indexes for query performance optimization
9. THE Backend_Service SHALL use biz_job_posting as the ODS_Layer source table (no schema changes required)

### Requirement 19: 缓存策略优化

**User Story:** 作为系统架构师，我希望优化缓存策略以提升系统性能，特别是对于频繁访问的分析数据。

#### Acceptance Criteria

1. THE Backend_Service SHALL cache ADS_Layer analysis results in Redis with TTL of 1 hour
2. THE Backend_Service SHALL cache knowledge graph query results in Redis with TTL of 6 hours
3. THE Backend_Service SHALL cache API responses in Redis with TTL of 5 minutes
4. WHEN cached data is updated in the database, THE Backend_Service SHALL invalidate corresponding cache entries
5. THE Backend_Service SHALL use cache key naming convention: {module}:{entity}:{id}
6. THE Backend_Service SHALL monitor cache hit rate and log metrics for performance analysis

### Requirement 20: 系统监控与日志增强

**User Story:** 作为运维人员，我希望增强系统监控和日志记录，以便快速定位和解决问题。

#### Acceptance Criteria

1. THE Backend_Service SHALL log all API requests with request ID, endpoint, user ID, execution time, and status code
2. THE Backend_Service SHALL log all ETL process executions with start time, end time, records processed, and errors
3. THE Backend_Service SHALL log all external service calls including Algorithm_Service and webhook deliveries
4. WHEN an error occurs, THE Backend_Service SHALL log stack traces and contextual information
5. THE Backend_Service SHALL expose health check endpoints for each module
6. THE Backend_Service SHALL collect performance metrics including: request latency, database query time, and cache hit rate
7. THE Backend_Service SHALL support log level configuration: DEBUG, INFO, WARN, ERROR

### Requirement 20.1: 健康检查端点

**User Story:** 作为运维人员，我希望每个模块都提供健康检查端点，以便监控系统状态。

#### Acceptance Criteria

1. THE Backend_Service SHALL expose /actuator/health endpoint for overall system health
2. THE Backend_Service SHALL expose module-specific health endpoints: /actuator/health/crawl, /actuator/health/warehouse, /actuator/health/report
3. THE Backend_Service SHALL check database connectivity in health checks
4. THE Backend_Service SHALL check Redis connectivity in health checks
5. THE Backend_Service SHALL check Algorithm_Service connectivity in health checks
6. WHEN a dependency is unhealthy, THE Backend_Service SHALL return HTTP 503 with error details
7. THE Backend_Service SHALL include health check response time in metrics

---

## 九、分阶段实施与兼容性

### Requirement 21: 分阶段实施支持

**User Story:** 作为项目经理，我希望系统支持分6个阶段逐步实施，以便降低风险和验证效果。

#### Acceptance Criteria

1. THE Platform SHALL support independent deployment of each phase without breaking existing functionality
2. THE Platform SHALL maintain backward compatibility with existing Auth_Module, Job_Module, Profile_Module, AI_Module, and System_Module during all phases
3. WHEN Phase 1 completes, THE Platform SHALL have extended role system and database schema ready for subsequent phases
4. WHEN Phase 2 completes, THE Platform SHALL have functional Crawl_Module integrated with Algorithm_Service
5. WHEN Phase 3 completes, THE Platform SHALL have operational Warehouse_Module with all four data layers
6. WHEN Phase 4 completes, THE Platform SHALL have Report_Module generating automated reports
7. WHEN Phase 5 completes, THE Platform SHALL have Recommend_Module and Subscription_Module delivering personalized services
8. WHEN Phase 6 completes, THE Platform SHALL have Open_API_Module and Knowledge_Graph_Module providing advanced capabilities

### Requirement 22: 算法服务集成

**User Story:** 作为系统架构师，我希望Java后端与Python算法服务能够高效集成，以便利用各自的技术优势。

#### Acceptance Criteria

1. THE Backend_Service SHALL communicate with Algorithm_Service using RESTful HTTP APIs
2. THE Backend_Service SHALL handle Algorithm_Service timeouts gracefully with 30-second timeout limit
3. WHEN Algorithm_Service is unavailable, THE Backend_Service SHALL return appropriate error messages to users
4. THE Backend_Service SHALL pass authentication context to Algorithm_Service for user-specific operations
5. THE Backend_Service SHALL validate Algorithm_Service responses before processing
6. THE Algorithm_Service SHALL implement crawl scheduling, job matching, salary prediction, sentiment analysis, and skill graph APIs
7. THE Backend_Service SHALL log all Algorithm_Service interactions for debugging and monitoring

### Requirement 23: 数据安全与隐私保护

**User Story:** 作为安全管理员,我希望系统保护敏感数据和用户隐私,以便符合数据安全规范。

#### Acceptance Criteria

1. THE Backend_Service SHALL encrypt all passwords using BCrypt with cost factor of 10 or higher
2. THE Backend_Service SHALL encrypt Data_Source credentials using AES-256 before storage
3. THE Backend_Service SHALL encrypt API keys using AES-256 before storage
4. THE Backend_Service SHALL mask sensitive information in logs including passwords, tokens, and API keys
5. THE Backend_Service SHALL enforce HTTPS for all external API communications
6. THE Backend_Service SHALL validate and sanitize all user inputs to prevent SQL injection and XSS attacks
7. WHEN a user requests data deletion, THE Backend_Service SHALL remove or anonymize personal data within 30 days

### Requirement 24: 性能要求

**User Story:** 作为用户，我希望系统响应迅速，以便获得良好的使用体验。

#### Acceptance Criteria

1. WHEN a user requests job search, THE Backend_Service SHALL return results within 2 seconds for queries returning up to 100 records
2. WHEN a user requests industry analysis, THE Warehouse_Module SHALL return analysis data within 3 seconds
3. WHEN a user requests report generation, THE Report_Module SHALL complete generation within 10 seconds for standard templates
4. WHEN a user requests job recommendations, THE Recommend_Module SHALL return recommendations within 5 seconds
5. THE Backend_Service SHALL support at least 100 concurrent users without performance degradation
6. THE Backend_Service SHALL maintain database connection pool with minimum 10 and maximum 50 connections
7. THE Backend_Service SHALL process webhook deliveries asynchronously to avoid blocking user requests

### Requirement 25: 定时任务调度

**User Story:** 作为系统架构师，我希望系统能够自动执行定时任务，以便保持数据新鲜度和自动化运维。

#### Acceptance Criteria

1. THE Backend_Service SHALL execute data warehouse ETL process daily at 02:00 AM
2. THE Backend_Service SHALL execute job subscription matching daily at 08:00 AM
3. THE Backend_Service SHALL execute scheduled report generation on the 1st day of each month
4. THE Backend_Service SHALL use Spring @Scheduled annotation with cron expressions for task scheduling
5. THE Backend_Service SHALL log all scheduled task executions with start time, end time, and status
6. WHEN a scheduled task fails, THE Backend_Service SHALL send alert notifications to administrators
7. THE Backend_Service SHALL support manual triggering of scheduled tasks via admin API

### Requirement 26: 现有模块保留与增强

**User Story:** 作为项目经理，我希望保留现有可用模块并进行增强，以便降低重构风险和开发成本。

#### Acceptance Criteria

1. THE Backend_Service SHALL retain existing Auth_Module with JWT and BCrypt authentication without modification
2. THE Backend_Service SHALL retain existing Job_Module with pagination, full-text search, and aggregation features
3. THE Backend_Service SHALL retain existing Profile_Module with complete user profile data structure
4. THE Backend_Service SHALL retain existing AI_Module with SSE streaming, intent recognition, and RAG enhancement
5. THE Backend_Service SHALL retain existing System_Module with user management and operation logs
6. THE Backend_Service SHALL enhance existing Analysis_Module by adding deep analysis endpoints while keeping current aggregation APIs
7. THE Backend_Service SHALL enhance existing Report_Module by adding LLM report generation and PDF export while keeping current framework
8. THE Backend_Service SHALL enhance existing Recommend_Module by adding career path simulation and skill radar while keeping current recommendation APIs
9. THE Backend_Service SHALL enhance existing Open_API_Module by adding API key authentication and rate limiting while keeping current public query endpoints
10. FOR ALL enhanced modules, THE Backend_Service SHALL maintain backward compatibility with existing API contracts

### Requirement 27: 报告下钻分析

**User Story:** 作为教师，我希望能够对报告中的异常数据进行下钻分析，以便深入了解问题根源。

#### Acceptance Criteria

1. THE Report_Module SHALL provide drill-down API endpoints for each report type
2. WHEN a Teacher clicks on an anomaly data point in a report, THE Report_Module SHALL return detailed underlying records
3. THE Report_Module SHALL support drill-down dimensions including: time period, geographic region, industry, position, and skill
4. THE Report_Module SHALL limit drill-down results to 500 records per query to ensure performance
5. THE Report_Module SHALL provide export functionality for drill-down results in CSV format
6. THE Report_Module SHALL log all drill-down queries for audit and analysis purposes

### Requirement 28: 职业路径模拟

**User Story:** 作为学生，我希望系统能够模拟职业发展路径，以便规划我的职业生涯。

#### Acceptance Criteria

1. WHEN a Student requests career path simulation, THE Recommend_Module SHALL retrieve the student's current profile
2. THE Recommend_Module SHALL identify potential career progression paths based on job title similarity and skill overlap
3. THE Recommend_Module SHALL calculate transition difficulty scores based on skill gap and experience requirements
4. THE Recommend_Module SHALL return a career path graph with nodes (positions) and edges (transitions)
5. THE Recommend_Module SHALL provide skill recommendations for each career transition
6. THE Recommend_Module SHALL support filtering career paths by industry, salary range, and time horizon
7. THE Recommend_Module SHALL visualize career paths in a format compatible with graph visualization libraries

### Requirement 29: 技能雷达图

**User Story:** 作为学生，我希望看到我的技能雷达图与目标岗位的对比，以便了解我的技能差距。

#### Acceptance Criteria

1. WHEN a Student requests skill radar comparison, THE Recommend_Module SHALL retrieve the student's skill profile
2. THE Recommend_Module SHALL retrieve skill requirements for the target job position
3. THE Recommend_Module SHALL normalize skill levels to a 0-100 scale for comparison
4. THE Recommend_Module SHALL generate radar chart data with at least 6 skill dimensions
5. THE Recommend_Module SHALL highlight skill gaps where student level is below job requirement
6. THE Recommend_Module SHALL provide learning resource recommendations for each skill gap
7. THE Recommend_Module SHALL return radar chart data in JSON format compatible with ECharts or Chart.js

### Requirement 30: 简历智能优化

**User Story:** 作为学生，我希望系统能够分析我的简历并提供优化建议，以便提高求职成功率。

#### Acceptance Criteria

1. WHEN a Student uploads a resume, THE Recommend_Module SHALL parse resume content to extract skills, experience, and education
2. THE Recommend_Module SHALL compare resume content with target job requirements
3. THE Recommend_Module SHALL identify missing keywords and skills that should be highlighted
4. THE Recommend_Module SHALL provide specific suggestions including: keyword optimization, skill emphasis, and experience reordering
5. THE Recommend_Module SHALL calculate a resume match score (0-100) for the target position
6. THE Recommend_Module SHALL support resume formats including: PDF, DOCX, and plain text
7. WHEN resume parsing fails, THE Recommend_Module SHALL return a clear error message with troubleshooting guidance


---

## 十一、依赖与配置管理

### Requirement 31: Maven依赖扩展

**User Story:** 作为系统架构师，我希望添加必要的Maven依赖，以便支持新功能实现。

#### Acceptance Criteria

1. THE Backend_Service SHALL add org.thymeleaf:thymeleaf dependency for HTML template rendering
2. THE Backend_Service SHALL add org.xhtmlrenderer:flying-saucer-pdf dependency for PDF generation
3. THE Backend_Service SHALL add spring-boot-starter-mail dependency for email notifications
4. THE Backend_Service SHALL add spring-boot-starter-data-redis dependency for caching (if not already present)
5. THE Backend_Service SHALL add spring-boot-starter-actuator dependency for health checks and metrics
6. THE Backend_Service SHALL maintain compatible versions for all dependencies
7. THE Backend_Service SHALL document all new dependencies in pom.xml with version comments

### Requirement 32: 应用配置扩展

**User Story:** 作为系统架构师，我希望扩展application.yml配置，以便支持新模块的配置需求。

#### Acceptance Criteria

1. THE Backend_Service SHALL add mail configuration section with SMTP server, port, username, and password
2. THE Backend_Service SHALL add webhook configuration section with signature secret key
3. THE Backend_Service SHALL add scheduler configuration section with thread pool size and cron expressions
4. THE Backend_Service SHALL add rate-limit configuration section with default QPS and daily quota
5. THE Backend_Service SHALL add report configuration section with template directory and PDF watermark settings
6. THE Backend_Service SHALL support environment-specific configurations using Spring profiles
7. THE Backend_Service SHALL encrypt sensitive configuration values using Jasypt or similar tools

### Requirement 33: 安全配置增强

**User Story:** 作为安全管理员，我希望增强安全配置，以便支持新的认证和授权需求。

#### Acceptance Criteria

1. THE Backend_Service SHALL update SecurityConfig to support API Key authentication for /api/v1/open/** endpoints
2. THE Backend_Service SHALL configure filter chain order: JwtAuthenticationFilter → ApiKeyFilter → RateLimitFilter
3. THE Backend_Service SHALL allow both Bearer Token and API Key authentication for open API endpoints
4. THE Backend_Service SHALL enforce role-based access control with @PreAuthorize annotations
5. THE Backend_Service SHALL configure CORS to allow cross-origin requests from configured domains
6. THE Backend_Service SHALL disable CSRF for API endpoints while keeping it enabled for web endpoints
7. THE Backend_Service SHALL configure session management as STATELESS for REST APIs

---

## 十二、验证与测试支持

### Requirement 34: 编译与启动验证

**User Story:** 作为开发人员，我希望每个阶段完成后能够验证系统正常编译和启动，以便及早发现问题。

#### Acceptance Criteria

1. THE Backend_Service SHALL compile successfully with mvn clean compile after each phase
2. THE Backend_Service SHALL pass all unit tests with mvn test after each phase
3. THE Backend_Service SHALL start successfully with mvn spring-boot:run after each phase
4. THE Backend_Service SHALL expose all configured endpoints after startup
5. THE Backend_Service SHALL connect to database and Redis successfully on startup
6. THE Backend_Service SHALL log startup completion message with port number and active profiles
7. THE Backend_Service SHALL provide Dockerfile for containerized deployment verification

### Requirement 35: 接口验证脚本

**User Story:** 作为测试人员，我希望有自动化脚本验证关键接口，以便快速回归测试。

#### Acceptance Criteria

1. THE Backend_Service SHALL provide curl scripts for testing authentication endpoints
2. THE Backend_Service SHALL provide curl scripts for testing crawl task management endpoints
3. THE Backend_Service SHALL provide curl scripts for testing report generation endpoints
4. THE Backend_Service SHALL provide curl scripts for testing subscription management endpoints
5. THE Backend_Service SHALL provide curl scripts for testing open API endpoints with API key
6. THE Backend_Service SHALL document expected request/response formats for all test scripts
7. THE Backend_Service SHALL organize test scripts by module in a tests/ directory

---

## 十三、文档与API规范

### Requirement 36: API文档生成

**User Story:** 作为API使用者，我希望有完整的API文档，以便快速了解接口使用方法。

#### Acceptance Criteria

1. THE Backend_Service SHALL use Swagger/OpenAPI 3.0 annotations for all REST endpoints
2. THE Backend_Service SHALL generate interactive API documentation at /swagger-ui.html
3. THE Backend_Service SHALL provide API documentation in JSON format at /v3/api-docs
4. THE Backend_Service SHALL document all request parameters, request body schemas, and response schemas
5. THE Backend_Service SHALL provide example requests and responses for each endpoint
6. THE Backend_Service SHALL group API endpoints by module tags: Auth, Crawl, Warehouse, Report, Recommend, Subscription, Open API, Knowledge Graph
7. THE Backend_Service SHALL include authentication requirements in API documentation

### Requirement 37: 数据库迁移脚本

**User Story:** 作为数据库管理员，我希望有结构化的数据库迁移脚本，以便安全地升级数据库架构。

#### Acceptance Criteria

1. THE Backend_Service SHALL provide SQL migration scripts organized by phase: phase1.sql, phase2.sql, ..., phase6.sql
2. THE Backend_Service SHALL include CREATE TABLE statements for all new tables
3. THE Backend_Service SHALL include ALTER TABLE statements for schema modifications
4. THE Backend_Service SHALL include CREATE INDEX statements for performance optimization
5. THE Backend_Service SHALL include data migration scripts for role_type field conversion
6. THE Backend_Service SHALL provide rollback scripts for each migration phase
7. THE Backend_Service SHALL document migration dependencies and execution order

---

## 附录：实施阶段映射

### Phase 1: 基础设施与数据库扩展
- Requirement 1: 角色权限体系扩展
- Requirement 18: 数据库架构扩展
- Requirement 31: Maven依赖扩展
- Requirement 32: 应用配置扩展
- Requirement 33: 安全配置增强
- Requirement 25: 定时任务调度（配置）

### Phase 2: 数据采集管理模块
- Requirement 2: 数据采集源管理
- Requirement 3: 数据采集任务调度
- Requirement 4: 采集数据质量监控
- Requirement 4.1: 增量抓取与历史追踪
- Requirement 4.2: 代理池状态监控

### Phase 3: 数据仓库与深度分析
- Requirement 5: 数据仓库分层架构
- Requirement 6: 行业与岗位维度分析
- Requirement 7: 技能需求趋势分析
- Requirement 7.1: 课程数据管理
- Requirement 7.2: 供需剪刀差诊断
- Requirement 7.3: 深度分析接口扩展
- Requirement 7.4: 定时ETL调度

### Phase 4: 报告自动生成升级
- Requirement 8: 报告模板管理
- Requirement 9: 自动报告生成
- Requirement 9.1: LLM增强报告生成
- Requirement 10: 报告历史与版本管理
- Requirement 10.1: 定时报告计划
- Requirement 27: 报告下钻分析

### Phase 5: 推荐与推送系统
- Requirement 11: 个性化岗位推荐
- Requirement 12: 岗位推送订阅
- Requirement 13: Webhook集成支持
- Requirement 13.1: 推送执行调度
- Requirement 13.2: 站内消息通知
- Requirement 13.3: 邮件推送集成
- Requirement 28: 职业路径模拟
- Requirement 29: 技能雷达图
- Requirement 30: 简历智能优化

### Phase 6: 公开API与知识图谱
- Requirement 14: 公开API接口
- Requirement 15: API访问控制与配额管理
- Requirement 15.1: API限流过滤器
- Requirement 15.2: 公开数据接口扩展
- Requirement 16: 知识图谱构建
- Requirement 17: 知识图谱查询与可视化
- Requirement 17.1: 技能关系图谱
- Requirement 17.2: 岗位-技能需求矩阵
- Requirement 17.3: 职业晋升路径图
- Requirement 17.4: 知识图谱重建

### 跨阶段需求（所有阶段适用）
- Requirement 19: 缓存策略优化
- Requirement 20: 系统监控与日志增强
- Requirement 20.1: 健康检查端点
- Requirement 21: 分阶段实施支持
- Requirement 22: 算法服务集成
- Requirement 23: 数据安全与隐私保护
- Requirement 24: 性能要求
- Requirement 24.1: 异步处理优化
- Requirement 26: 现有模块保留与增强
- Requirement 34: 编译与启动验证
- Requirement 35: 接口验证脚本
- Requirement 36: API文档生成
- Requirement 37: 数据库迁移脚本
