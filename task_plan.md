# 任务计划

## 目标
让数据采集页在项目内真实显示爬取过程，创建任务后立即可见、可选采集条数、能持续刷新进度，并修正 `/api/v1/crawl/tasks/live` 返回错误聚焦任务的问题。

## 阶段
- [completed] 核实当前运行态与接口脏数据来源
- [completed] 修复后端 live 聚焦与噪声任务过滤
- [completed] 修复前端实时面板、创建表单和自动聚焦逻辑
- [completed] 构建、同步到 Docker、实测创建任务和实时刷新

## 残留风险
- 调度中心原始状态码仍有漂移，实时页已改为优先跟踪当前任务详情并按进度推断展示状态。
- 本轮已做接口和容器级实测，但未做浏览器录屏级验证。

## 约束
- 不回滚用户已有改动
- 中文文本保持 UTF-8，避免继续引入乱码
- 修改后同步到 Docker 运行容器

## 2026-04-24 补充
- [completed] 后端 `mvn -q "-Dmaven.repo.local=.m2" test` 全量通过
- [completed] Docker 后端已按最新镜像 `build + force-recreate`
- [completed] 教师报告任务重新回归通过，任务 `85`、`87` 成功
- [completed] 教师课程接口已与 `biz_curriculum` 对齐，导入后的 9 条课程可通过 `/api/v1/teacher/courses` 读取
- [in_progress] 汇总完整烟测结果与剩余问题清单
