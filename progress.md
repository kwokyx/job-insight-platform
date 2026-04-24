# 进度日志

- 2026-04-24 12:00 已复查容器日志，确认调度器和爬虫节点能真实采集。
- 2026-04-24 12:00 已复现 `/crawl/tasks/live` 脏数据返回，后端与前端都需要修。
- 2026-04-24 12:05 已修后端 live 过滤逻辑，开始排除 `probe` / `SCHEDULED_TEMPLATE` 对实时聚焦的污染。
- 2026-04-24 12:12 已重写 `DataCollectorView`，补上实时面板、轮询、创建条数选择和创建后自动聚焦。
- 2026-04-24 12:18 已补运行态纠偏：原始 `status=3` 但无结束时间且未跑满时，后端按运行中处理，避免 live 永远盯旧任务。
- 2026-04-24 12:26 已完成前后端构建并同步到 Docker，接口实测验证新任务可真实推进，前端已改为优先跟踪当前任务详情。
# 2026-04-24 继续回归

- 重新构建并强制重建 Docker 后端容器，确认最新后端修复进入运行时
- 教师账号真实生成报告，任务 `85` 成功
- 学生账号真实生成报告，任务 `86` 成功
- 重写 `TeacherController`，将教师课程接口与 `biz_curriculum` 对齐并保留旧表兜底
- 后端全量测试再次通过：`mvn -q "-Dmaven.repo.local=.m2" test`
- Docker 同步后验证 `/api/v1/teacher/courses` 返回 9 条导入课程
- Docker 同步后再次验证教师报告生成，任务 `87` 成功
- 角色烟测补充完成：
  - 学生：`/auth/profile`、`/recommend/plan`、`/reports`
  - 教师：`/teacher/materials/status`、`/teacher/courses`、`/teacher/market-match`、`/teacher/teaching-reform`、`/teacher/student-insights/retrace`
  - 管理员：`/reports`、`/readiness/admin`、`/crawl/tasks/live`
- 清理历史脏数据：
  - `sys_user.id=15` 昵称改为 `测试学生`
  - `sys_user.id=16` 昵称改为 `测试教师`
  - `biz_analysis_report.id=73` 名称改为 `教学建议与课程对齐报告`
