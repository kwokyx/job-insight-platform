# API Handoff

## Runtime Verification

This document matches the Docker runtime verified on 2026-04-15.

- Frontend gateway: `http://localhost`
- API base URL for frontend integration: `http://localhost/api/v1`
- Docker services verified running:
  - `career-frontend`
  - `career-backend`
  - `career-mysql`
  - `career-redis`
- Seed admin account for联调:
  - username: `admin`
  - password: `admin123`

Verified live in Docker:

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/profile`
- `GET /api/v1/analysis/overview`
- `GET /api/v1/ai/quota`
- `POST /api/v1/ai/agent/query`
- `GET /api/v1/recommend/plan`
- `GET /api/v1/platform/advisory`
- `GET /api/v1/reports`
- `GET /api/v1/reports/public`

## Scope

This document is the handoff version of the platform APIs.

It now includes:

- frontend-facing core APIs
- admin and operation APIs
- data collection and governance APIs
- subscription, notification, and webhook APIs
- curriculum, knowledge graph, and deep analysis APIs

Frozen modules for frontend integration:

- `/api/v1/auth/*`
- `/api/v1/profile/*`
- `/api/v1/ai/*`
- `/api/v1/analysis/*`
- `/api/v1/jobs/*`
- `/api/v1/recommend/*`
- `/api/v1/reports/*`
- `/api/v1/open/*`
- `/api/v1/platform/*`

Operational or secondary modules included in this document but not recommended for strict first-phase frontend coupling:

- `/api/v1/admin/*`
- `/api/v1/crawl/*`
- `/api/v1/curriculum/*`
- `/api/v1/subscriptions/*`
- `/api/v1/notifications/*`
- `/api/v1/webhooks/*`
- `/api/v1/kg/*`
- `/api/v1/analysis/deep/*`

## Base Rules

- Base path: `/api/v1`
- Auth header: `Authorization: Bearer <accessToken>`
- Common response envelope:

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "total": 0,
  "page": 1,
  "pageSize": 20,
  "timestamp": "2026-04-15T09:00:00"
}
```

- Error code convention:
  - `200`: success
  - `400`: bad request
  - `401`: unauthorized or token invalid
  - `403`: forbidden
  - `404`: resource not found
  - `500`: server error

## Role Rules

- Anonymous:
  - public `open`
  - public `jobs`
  - public `analysis` GET
  - public report list
  - public knowledge graph read endpoints

- Authenticated user:
  - `auth/profile`
  - `profile`
  - `ai`
  - `recommend`
  - private `reports`
  - `subscriptions`
  - `notifications`
  - `webhooks`
  - `platform`

- Teacher or Admin:
  - `curriculum`
  - `analysis/deep`

- Admin only:
  - `admin`
  - `crawl`
  - `open/api-keys`
  - `kg/build`

## SSE Rules

Used by `POST /api/v1/ai/chat`.

Event names:

- `session`
- `message`
- `done`
- `error`

Example stream payloads:

```text
event: session
data: {"sessionId":"abc123"}

event: message
data: {"content":"Hello","reasoning_content":""}

event: done
data: {"latencyMs":823}

event: error
data: {"message":"AI service error"}
```

## Auth

### POST `/api/v1/auth/register`

- Auth required: no
- Purpose: register account
- Request body:

```json
{
  "username": "alice",
  "password": "secret123",
  "email": "alice@example.com",
  "nickname": "Alice"
}
```

- Response data:

```json
{
  "userId": 1
}
```

### POST `/api/v1/auth/login`

- Auth required: no
- Purpose: login
- Request body:

```json
{
  "username": "alice",
  "password": "secret123"
}
```

- Response data:

```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token",
  "expiresIn": 7200,
  "user": {
    "id": 1,
    "username": "alice",
    "nickname": "Alice",
    "roleType": 0,
    "avatarUrl": ""
  }
}
```

### POST `/api/v1/auth/refresh`

- Auth required: no
- Purpose: refresh access token
- Request body:

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

- Response data:

```json
{
  "accessToken": "new-jwt-access-token",
  "expiresIn": 7200
}
```

### GET `/api/v1/auth/profile`

- Auth required: yes
- Purpose: get current user basic profile

- Response data:

```json
{
  "id": 1,
  "username": "alice",
  "nickname": "Alice",
  "email": "alice@example.com",
  "phone": "13800000000",
  "avatarUrl": "",
  "roleType": 0,
  "lastLoginAt": "2026-04-15 09:00:00",
  "createdAt": "2026-04-01 12:00:00"
}
```

### PUT `/api/v1/auth/profile`

- Auth required: yes
- Purpose: update current user basic profile
- Request body:

```json
{
  "nickname": "Alice",
  "email": "alice@example.com",
  "phone": "13800000000",
  "avatarUrl": "https://example.com/avatar.png"
}
```

### PUT `/api/v1/auth/password`

- Auth required: yes
- Purpose: change password
- Request body:

```json
{
  "oldPassword": "secret123",
  "newPassword": "secret456"
}
```

## Profile

### GET `/api/v1/profile`

- Auth required: yes
- Purpose: get extended career profile
- Response data:

```json
{
  "profile": {
    "id": 10,
    "userId": 1,
    "majorId": null,
    "educationLevel": "Bachelor",
    "targetRegionCode": null,
    "targetProvinceCode": null,
    "targetCityCode": "Beijing",
    "expectedSalaryMin": 12000,
    "expectedSalaryMax": 20000,
    "targetJobCategoryId": null,
    "skills": "[\"Java\",\"Spring Boot\"]",
    "profileSummary": "Backend engineer profile"
  },
  "skills": [
    {
      "id": 1,
      "profileId": 10,
      "skillId": 100,
      "proficiency": 4,
      "source": "manual"
    }
  ]
}
```

### PUT `/api/v1/profile`

- Auth required: yes
- Purpose: update extended career profile
- Request body:

```json
{
  "majorId": 1,
  "educationLevel": "Bachelor",
  "targetRegionCode": "CN",
  "targetProvinceCode": "BJ",
  "targetCityCode": "Beijing",
  "expectedSalaryMin": 12000,
  "expectedSalaryMax": 20000,
  "targetJobCategoryId": 2,
  "skills": ["Java", "Spring Boot", "MySQL"],
  "profileSummary": "Backend engineer profile"
}
```

### PUT `/api/v1/profile/skills`

- Auth required: yes
- Purpose: replace skill list
- Request body:

```json
{
  "skills": [
    { "name": "Java", "proficiency": 4 },
    { "name": "Spring Boot", "proficiency": 4 },
    { "name": "MySQL", "proficiency": 3 }
  ]
}
```

- Response data:

```json
{
  "count": 3
}
```

## AI

### POST `/api/v1/ai/chat`

- Auth required: yes
- Purpose: streaming AI chat
- Content type: `application/json`
- Response type: `text/event-stream`
- Request body:

```json
{
  "message": "Compare backend salaries in Beijing and Shanghai",
  "sessionId": "optional-session-id"
}
```

### POST `/api/v1/ai/agent/query`

- Auth required: yes
- Purpose: tool-backed agent query
- Request body:

```json
{
  "message": "Analyze my skill gap for senior backend roles",
  "tool": "skill_gap"
}
```

- Expected response data shape:

```json
{
  "answer": "You are missing distributed systems design and observability depth.",
  "toolResult": {}
}
```

### POST `/api/v1/ai/agent/import-profile`

- Auth required: yes
- Purpose: import profile from uploaded file
- Content type: `multipart/form-data`
- Form fields:
  - `file`
  - `overwriteSkills` default `false`

### GET `/api/v1/ai/conversations`

- Auth required: yes
- Purpose: list current user AI conversations

### GET `/api/v1/ai/conversations/{sessionId}`

- Auth required: yes
- Purpose: get a conversation and message history
- Response data:

```json
{
  "conversation": {},
  "messages": []
}
```

### DELETE `/api/v1/ai/conversations/{sessionId}`

- Auth required: yes
- Purpose: archive a conversation

### GET `/api/v1/ai/quota`

- Auth required: yes
- Purpose: get AI daily quota usage
- Response data:

```json
{
  "used": 3,
  "limit": 50,
  "remaining": 47
}
```

## Analysis

### GET `/api/v1/analysis/overview`

- Auth required: no
- Purpose: overview dashboard

### GET `/api/v1/analysis/overview/personalized`

- Auth required: yes
- Purpose: personalized overview

### GET `/api/v1/analysis/salary`

- Auth required: no
- Purpose: salary grouped analysis
- Query:
  - `groupBy=city|industry|education|experience`
  - `limit=20`

### GET `/api/v1/analysis/salary/trend`

- Auth required: no
- Purpose: salary trend
- Query:
  - `city?`
  - `industry?`

### POST `/api/v1/analysis/salary/predict`

- Auth required: no
- Purpose: salary prediction
- Request body:

```json
{
  "city": "Beijing",
  "education": "Bachelor",
  "experience": "1-3 years",
  "skills": ["Java", "Spring Boot", "MySQL"],
  "industry": "Internet"
}
```

### GET `/api/v1/analysis/skills`

- Auth required: no
- Purpose: skill ranking
- Query:
  - `limit=20`

### GET `/api/v1/analysis/skills/graph`

- Auth required: no
- Purpose: skill graph
- Query:
  - `topN=50`

### GET `/api/v1/analysis/regions/heatmap`

- Auth required: no
- Purpose: city heatmap

### GET `/api/v1/analysis/sentiment`

- Auth required: no
- Purpose: sentiment index
- Query:
  - `city?`
  - `industry?`

## Jobs

### GET `/api/v1/jobs`

- Auth required: no
- Purpose: job list
- Query:
  - `keyword?`
  - `city?`
  - `industry?`
  - `education?`
  - `experience?`
  - `salaryMin?`
  - `salaryMax?`
  - `page=1`
  - `pageSize=20`
  - `sortBy=publish_date`
  - `sortOrder=desc`

### GET `/api/v1/jobs/{id}`

- Auth required: no
- Purpose: job detail

### GET `/api/v1/jobs/stats`

- Auth required: no
- Purpose: job stats overview

### GET `/api/v1/jobs/by-city`

- Auth required: no
- Purpose: city aggregation
- Query:
  - `limit=20`

### GET `/api/v1/jobs/by-industry`

- Auth required: no
- Purpose: industry aggregation
- Query:
  - `limit=20`

### GET `/api/v1/jobs/by-education`

- Auth required: no
- Purpose: education aggregation

### GET `/api/v1/jobs/by-experience`

- Auth required: no
- Purpose: experience aggregation

### GET `/api/v1/jobs/search`

- Auth required: no
- Purpose: full-text search
- Query:
  - `keyword`
  - `page=1`
  - `pageSize=20`

- Response data:

```json
{
  "keyword": "java backend",
  "records": [],
  "aggregations": {
    "cities": [],
    "industries": [],
    "education": [],
    "experience": []
  }
}
```

### GET `/api/v1/jobs/hot`

- Auth required: no
- Purpose: hot jobs
- Query:
  - `limit=10`

## Recommendation

### POST `/api/v1/recommend/jobs`

- Auth required: yes
- Purpose: recommend jobs
- Request body:

```json
{
  "skills": ["Java", "Spring Boot", "MySQL"],
  "preferredCities": ["Beijing", "Shanghai"],
  "education": "Bachelor",
  "experience": "1-3 years",
  "salaryMin": 12000,
  "salaryMax": 25000,
  "industry": "Internet",
  "limit": 8
}
```

### POST `/api/v1/recommend/skills`

- Auth required: yes
- Purpose: skill gap analysis
- Request body:

```json
{
  "userSkills": ["Java", "MySQL", "Vue"],
  "targetJobType": "Backend Engineer",
  "city": "Beijing"
}
```

### POST `/api/v1/recommend/career-path`

- Auth required: yes
- Purpose: career path planning
- Request body:

```json
{
  "currentJob": "Java Engineer",
  "targetJob": "Architecture Engineer",
  "currentSkills": ["Java", "Spring", "Redis", "MySQL"],
  "city": "Beijing"
}
```

### POST `/api/v1/recommend/skill-radar`

- Auth required: yes
- Purpose: skill radar
- Request body:

```json
{
  "userSkills": ["Java", "MySQL", "Vue"],
  "targetJobType": "Backend Engineer",
  "city": "Beijing"
}
```

### POST `/api/v1/recommend/resume-review`

- Auth required: yes
- Purpose: resume review
- Request body:

```json
{
  "targetJob": "Backend Engineer",
  "resumeText": "Two years of backend development experience...",
  "userSkills": ["Java", "Spring Boot", "Redis"]
}
```

### GET `/api/v1/recommend/similar-jobs/{jobId}`

- Auth required: no
- Purpose: similar jobs for a given job
- Query:
  - `limit=10`

### GET `/api/v1/recommend/plan`

- Auth required: yes
- Purpose: personalized recommendation plan

## Reports

### GET `/api/v1/reports`

- Auth required: yes
- Purpose: list private reports
- Query:
  - `page=1`
  - `pageSize=20`

### GET `/api/v1/reports/public`

- Auth required: no
- Purpose: list public reports
- Query:
  - `page=1`
  - `pageSize=20`

### POST `/api/v1/reports/generate`

- Auth required: yes
- Purpose: create report generation task
- Request body:

```json
{
  "reportName": "Career Intelligence Report",
  "reportType": "COMPREHENSIVE",
  "params": {}
}
```

- Response data:

```json
{
  "taskId": 1001
}
```

### GET `/api/v1/reports/{taskId}/status`

- Auth required: yes
- Purpose: get report task status

### POST `/api/v1/reports/schedule`

- Auth required: yes
- Purpose: create report schedule
- Request body:

```json
{
  "scheduleName": "Weekly Intelligence Brief",
  "reportType": "COMPREHENSIVE",
  "cronExpr": "0 00 09 * * MON",
  "params": {}
}
```

### GET `/api/v1/reports/schedules`

- Auth required: yes
- Purpose: list report schedules

### GET `/api/v1/reports/schedules/{id}`

- Auth required: yes
- Purpose: get report schedule detail

### PUT `/api/v1/reports/schedules/{id}/toggle`

- Auth required: yes
- Purpose: enable or disable report schedule

### DELETE `/api/v1/reports/schedules/{id}`

- Auth required: yes
- Purpose: delete report schedule

### GET `/api/v1/reports/{id}/download`

- Auth required: yes
- Purpose: get report metadata

### GET `/api/v1/reports/{id}/drill`

- Auth required: yes
- Purpose: get report drill-down detail
- Response core fields:

```json
{
  "reportId": 1,
  "reportName": "Career Intelligence Report",
  "reportType": "COMPREHENSIVE",
  "summary": "Summary text",
  "sections": {},
  "sampleJobs": [],
  "chartInsights": [],
  "recommendations": [],
  "userContext": {},
  "advisory": {}
}
```

### GET `/api/v1/reports/{id}/pdf`

- Auth required: yes
- Purpose: export PDF
- Response content type: `application/pdf`

## Open API

### GET `/api/v1/open/jobs`

- Auth required: no
- Purpose: public jobs query

### GET `/api/v1/open/analysis/overview`

- Auth required: no
- Purpose: public overview

### GET `/api/v1/open/analysis/skills`

- Auth required: no
- Purpose: public skills ranking

### GET `/api/v1/open/analysis/salary`

- Auth required: no
- Purpose: public salary aggregation

### GET `/api/v1/open/analysis/trend`

- Auth required: no
- Purpose: public trend data

### GET `/api/v1/open/reports/public`

- Auth required: no
- Purpose: public report list

### GET `/api/v1/open/reports/{id}`

- Auth required: no
- Purpose: public report detail

## Platform

### GET `/api/v1/platform/advisory`

- Auth required: yes
- Purpose: personalized cross-module advisory

## Admin

### GET `/api/v1/admin/dashboard`

- Auth required: admin
- Purpose: admin dashboard overview

### GET `/api/v1/admin/users`

- Auth required: admin
- Purpose: user list with search and filter
- Query:
  - `keyword?`
  - `roleType?`
  - `status?`
  - `page=1`
  - `pageSize=20`

### PUT `/api/v1/admin/users/{id}/status`

- Auth required: admin
- Purpose: enable or disable user
- Request body:

```json
{
  "status": 0
}
```

### PUT `/api/v1/admin/users/{id}/role`

- Auth required: admin
- Purpose: update user role
- Request body:

```json
{
  "roleType": 1
}
```

Role type convention:

- `0`: user or student
- `1`: admin
- `2`: teacher

### GET `/api/v1/admin/logs`

- Auth required: admin
- Purpose: operation log query
- Query:
  - `username?`
  - `operation?`
  - `startDate?`
  - `endDate?`
  - `page=1`
  - `pageSize=20`

## Crawl

### GET `/api/v1/crawl/sources`

- Auth required: admin
- Purpose: list external data sources

### GET `/api/v1/crawl/sources/{id}`

- Auth required: admin
- Purpose: get data source detail

### POST `/api/v1/crawl/sources`

- Auth required: admin
- Purpose: create data source
- Request body:

```json
{
  "sourceName": "Boss直聘",
  "sourceCode": "boss",
  "baseUrl": "https://www.zhipin.com",
  "crawlStrategy": "job-listing"
}
```

### PUT `/api/v1/crawl/sources/{id}`

- Auth required: admin
- Purpose: update data source
- Request body:
  - `sourceName?`
  - `baseUrl?`
  - `crawlStrategy?`
  - `isActive?`
  - `healthStatus?`
  - `totalRecords?`
  - `lastCrawlAt?`

### DELETE `/api/v1/crawl/sources/{id}`

- Auth required: admin
- Purpose: delete data source

### GET `/api/v1/crawl/tasks`

- Auth required: admin
- Purpose: crawl task list
- Query:
  - `channel?`
  - `status?`
  - `page=1`
  - `pageSize=20`

### POST `/api/v1/crawl/tasks`

- Auth required: admin
- Purpose: create crawler task
- Request body:

```json
{
  "taskName": "Beijing backend jobs",
  "channel": "boss",
  "keywords": "java backend",
  "city": "Beijing",
  "priority": 5
}
```

- Response data:

```json
{
  "taskId": "uuid-string",
  "executionMode": "managed-python-crawler"
}
```

### GET `/api/v1/crawl/tasks/{id}`

- Auth required: admin
- Purpose: crawl task detail

### PUT `/api/v1/crawl/tasks/{id}/status`

- Auth required: admin
- Purpose: pause, resume, finish, or cancel task
- Request body:

```json
{
  "status": 1
}
```

Status convention:

- `0`: pending
- `1`: running
- `2`: finished
- `3`: cancelled or stopped

### GET `/api/v1/crawl/tasks/{taskId}/logs`

- Auth required: admin
- Purpose: crawler task logs
- Query:
  - `page=1`
  - `pageSize=50`

### GET `/api/v1/crawl/tasks/quality`

- Auth required: admin
- Purpose: data quality report

### POST `/api/v1/crawl/tasks/quality/history/backfill`

- Auth required: admin
- Purpose: backfill history snapshots
- Query:
  - `limit=100`

## Deep Analysis

### GET `/api/v1/analysis/deep/supply-demand`

- Auth required: teacher or admin
- Purpose: supply-demand gap diagnosis
- Query:
  - `major?`

### POST `/api/v1/analysis/deep/supply-demand`

- Auth required: teacher or admin
- Purpose: supply-demand gap diagnosis with request body
- Request body:

```json
{
  "major": "Computer Science"
}
```

### POST `/api/v1/analysis/deep/curriculum-gap`

- Auth required: teacher or admin
- Purpose: curriculum versus market gap
- Request body:

```json
{
  "major": "Computer Science"
}
```

### GET `/api/v1/analysis/deep/salary-premium`

- Auth required: teacher or admin
- Purpose: education and experience salary premium analysis

### GET `/api/v1/analysis/deep/trend-forecast`

- Auth required: teacher or admin
- Purpose: employment trend forecast
- Query:
  - `city?`
  - `industry?`
  - `months=3`

### POST `/api/v1/analysis/deep/etl/run`

- Auth required: teacher or admin
- Purpose: trigger warehouse ETL

### GET `/api/v1/analysis/deep/warehouse/overview`

- Auth required: teacher or admin
- Purpose: warehouse table overview

## Knowledge Graph

### GET `/api/v1/kg/skill-map`

- Auth required: no
- Purpose: skill relationship graph
- Query:
  - `topN=50`

### GET `/api/v1/kg/job-skill-matrix`

- Auth required: no
- Purpose: job-skill matrix
- Query:
  - `topJobs=10`
  - `topSkills=15`

### GET `/api/v1/kg/career-ladder/{jobTitle}`

- Auth required: no
- Purpose: career ladder paths for a job title

### POST `/api/v1/kg/build`

- Auth required: admin
- Purpose: rebuild graph
- Query:
  - `minSupport=3`

## Curriculum

### GET `/api/v1/curriculum`

- Auth required: teacher or admin
- Purpose: list curriculum
- Query:
  - `major?`
  - `department?`
  - `keyword?`
  - `page=1`
  - `pageSize=20`

### POST `/api/v1/curriculum/upload`

- Auth required: teacher or admin
- Purpose: upload curriculum Excel
- Content type: `multipart/form-data`
- Form field:
  - `file`

### GET `/api/v1/curriculum/{id}/skills`

- Auth required: teacher or admin
- Purpose: get auto-mapped skills for curriculum

### DELETE `/api/v1/curriculum/{id}`

- Auth required: teacher or admin
- Purpose: soft delete curriculum

## Subscriptions

### POST `/api/v1/subscriptions`

- Auth required: yes
- Purpose: create subscription
- Request body:

```json
{
  "subscriptionType": "JOB_PUSH",
  "filterConfig": "{\"city\":\"Beijing\",\"keywords\":[\"Java\"]}",
  "channel": "IN_APP"
}
```

### GET `/api/v1/subscriptions`

- Auth required: yes
- Purpose: list current user subscriptions
- Query:
  - `page=1`
  - `pageSize=20`

### DELETE `/api/v1/subscriptions/{id}`

- Auth required: yes
- Purpose: delete subscription

### GET `/api/v1/subscriptions/{id}/matches`

- Auth required: yes
- Purpose: preview matched jobs
- Query:
  - `limit=20`

### POST `/api/v1/subscriptions/{id}/dispatch`

- Auth required: yes
- Purpose: dispatch matched jobs to notifications and webhooks
- Query:
  - `limit=10`

- Response data:

```json
{
  "deliveredCount": 5
}
```

## Notifications

### GET `/api/v1/notifications`

- Auth required: yes
- Purpose: notification list
- Query:
  - `type?`
  - `isRead?`
  - `page=1`
  - `pageSize=20`

- Response data:

```json
{
  "records": [],
  "total": 0,
  "unreadCount": 0
}
```

### PUT `/api/v1/notifications/{id}/read`

- Auth required: yes
- Purpose: mark one notification as read

### PUT `/api/v1/notifications/read-all`

- Auth required: yes
- Purpose: mark all notifications as read

## Webhooks

### GET `/api/v1/webhooks`

- Auth required: yes
- Purpose: list current user webhooks

### GET `/api/v1/webhooks/{id}/deliveries`

- Auth required: yes
- Purpose: list webhook delivery history

### POST `/api/v1/webhooks`

- Auth required: yes
- Purpose: register webhook endpoint
- Request body:

```json
{
  "endpointUrl": "https://example.com/webhook",
  "eventTypes": "JOB_MATCH,REPORT_READY"
}
```

### DELETE `/api/v1/webhooks/{id}`

- Auth required: yes
- Purpose: delete webhook

### PUT `/api/v1/webhooks/{id}/toggle`

- Auth required: yes
- Purpose: enable or disable webhook

## Freeze Policy

For frozen frontend modules:

- Path must not change.
- HTTP method must not change.
- Auth requirement must not change.
- Existing response fields should not be removed.
- New fields may be added only as backward-compatible optional fields.

## Frontend Mapping

Suggested frontend-to-API ownership:

- Login and account center:
  - `/auth/*`
  - `/profile/*`

- AI workspace:
  - `/ai/*`

- Dashboard and insights:
  - `/analysis/*`
  - `/jobs/*`
  - `/open/*`

- Recommendation center:
  - `/recommend/*`

- Report center:
  - `/reports/*`

- Personalized dashboard blocks:
  - `/platform/advisory`
  - `/analysis/overview/personalized`
  - `/recommend/plan`

## Full API Coverage Note

This document now covers the main backend controller groups present in the project:

- `auth`
- `profile`
- `ai`
- `analysis`
- `analysis/deep`
- `jobs`
- `recommend`
- `reports`
- `open`
- `platform`
- `admin`
- `crawl`
- `curriculum`
- `kg`
- `subscriptions`
- `notifications`
- `webhooks`

If new controllers are added later, this file should be updated in the same release.
