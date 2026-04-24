# 发现记录

- `career-crawler-node` 日志已证明近期 `801` 城市任务真实抓取成功，问题核心不在“完全抓不到”，而在项目页没有绑定正确实时任务。
- 当前 `/api/v1/crawl/tasks/live` 返回脏数据：
  - `runningTasks` 含 `SCHEDULED_TEMPLATE`
  - `latestTask`/`activeProgress` 指向 `probe_1e8c2097`
- 实跑发现调度中心还会把正在推进的任务写成 `status=3`，但 `finishedCount` 仍持续增长；因此需要在后端做运行态纠偏，不能只信原始状态码。
- 当前 `frontend/src/views/DataCollectorView.vue` 仍是较简版本：
  - 无 `/live` 轮询
  - 创建表单没有 `targetCount`
  - 创建后不会自动切到新任务
  - 没有实时进度面板
# 2026-04-24 补充

- 教师端真实数据链路新增确认：
  - `biz_curriculum` 中教师 `16` 已有 9 条导入课程
  - 修复前 `/api/v1/teacher/courses` 读的是 `biz_teacher_course`，返回 0 条
  - 修复后 `/api/v1/teacher/courses` 优先返回 `biz_curriculum`，当前返回 9 条

- 报告链路真实回归结果：
  - 学生报告任务 `86` 成功
  - 教师报告任务 `85`、`87` 成功
  - 教师报告在 Docker 重建后已恢复，不再停留在 `RUNNING / 10%`

- 编码检查：
  - 代码文本未继续扩散常见中文乱码特征
  - `fonts/*.woff2` 的二进制命中可忽略，不属于文案乱码

- 角色烟测结果：
  - 学生接口通过：`/api/v1/auth/profile`、`/api/v1/recommend/plan`、`/api/v1/reports`
  - 教师接口通过：`/api/v1/teacher/materials/status`、`/api/v1/teacher/courses`、`/api/v1/teacher/market-match`、`/api/v1/teacher/teaching-reform`、`/api/v1/teacher/student-insights/retrace`
  - 管理员接口通过：`/api/v1/reports`、`/api/v1/readiness/admin`、`/api/v1/crawl/tasks/live`
  - 管理员最初 401 的根因不是接口坏，而是运行容器 `JWT_SECRET` 与源码默认值不同
