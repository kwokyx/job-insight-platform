# Implementation Plan: Intelligent Job Matching Enhancement

## Overview

本实施计划将智能岗位匹配增强功能分解为可执行的编码任务。实施策略采用增量开发方式,在现有Spring Boot + MyBatis Plus架构基础上扩展,通过扩展现有表、新增服务组件和增强现有Controller来实现功能。

**核心实施原则**:
- 复用现有基础设施(Spring Security、RedisTemplate、AsyncConfig等)
- 扩展现有表,不创建新表
- 增强现有服务,避免重复开发
- 算法服务与业务服务解耦

**技术栈**: Java 8, Spring Boot 2.7.18, MyBatis Plus 3.5.5, MySQL 8.0, Redis 7, Python FastAPI (算法服务)

---

## Tasks

- [ ] 1. 数据库表结构扩展
  - 执行ALTER TABLE语句扩展user_profile表(10个新字段)
  - 执行ALTER TABLE语句扩展biz_recommendation_result表(5个新字段)
  - 执行ALTER TABLE语句扩展biz_job_favorite表(3个新字段)
  - 创建数据库迁移脚本并验证执行
  - _Requirements: 1.1, 1.2, 3.2, 4.1, 19.1-19.3_

- [ ] 2. 核心数据模型和实体类扩展
  - [ ] 2.1 扩展UserProfile实体类
    - 添加智能匹配相关字段(graduationYear, workExperience, industryInterest, profileCompleteness, preferenceWeights)
    - 添加简历解析相关字段(resumeFileName, resumeParseStatus, resumeParseData, resumeAccuracyScore, resumeParsedAt)
    - 配置JSON类型字段的TypeHandler
    - _Requirements: 1.1, 3.2, 3.5_

  - [ ] 2.2 扩展RecommendationResult实体类
    - 添加dimensionScores字段(Map<String, Integer>)
    - 添加explanation字段(MatchExplanation对象)
    - 添加configVersion, algorithmVersion, calculationTimeMs字段
    - 创建MatchExplanation值对象类(strengths, gaps列表)
    - _Requirements: 1.1, 1.2, 2.1, 2.2_

  - [ ] 2.3 扩展JobFavorite实体类
    - 添加interactionType字段(VIEW/FAVORITE/APPLY/DISMISS)
    - 添加interactionWeight字段
    - 添加matchScore字段
    - 创建InteractionType枚举类
    - _Requirements: 4.1, 4.2_

  - [ ] 2.4 创建算法配置相关数据模型
    - 创建AlgorithmConfig类(dimensionWeights, minMatchScore, ensembleWeights, diversityPenalty)
    - 创建MatchResult类(matchScore, dimensionScores, explanation, calculationTime)
    - 创建JobRecommendation类(jobId, matchScore, rank, explanation)
    - _Requirements: 11.1, 11.2_

- [ ] 3. Redis缓存配置和Key规范
  - 定义Redis Key常量类(RECOMMEND_STUDENT, MATCH_SCORE, PROFILE_COMPLETENESS, CONFIG_ACTIVE, BATCH_TASK, FEEDBACK_RECENT)
  - 配置RedisTemplate序列化器(JSON序列化)
  - 实现缓存工具类封装常用操作(get, set, delete, expire)
  - _Requirements: 23.1, 23.2, 23.3_

- [ ] 4. 算法配置服务实现
  - [ ] 4.1 实现AlgorithmConfigService接口
    - 实现getActiveConfig方法(从Redis读取当前激活配置)
    - 实现createConfig方法(创建新配置版本并存储到Redis)
    - 实现activateConfig方法(激活指定配置版本)
    - 实现配置权重验证逻辑(权重和必须等于100)
    - _Requirements: 11.1, 11.2, 11.6_

  - [ ]* 4.2 编写属性测试 - Property 3: 配置权重验证
    - **Property 3: 配置权重验证**
    - **Validates: Requirements 11.2**
    - 使用jqwik生成随机权重组合
    - 验证权重和为100时配置创建成功
    - 验证权重和不为100时抛出InvalidWeightsException

  - [ ]* 4.3 编写单元测试
    - 测试从Redis读取配置
    - 测试配置版本管理
    - 测试配置激活逻辑
    - 测试权重验证边界条件

- [ ] 5. 匹配服务核心实现
  - [ ] 5.1 创建算法服务客户端
    - 创建AlgorithmServiceClient接口
    - 使用RestTemplate实现HTTP调用
    - 配置连接超时(5秒)和重试机制(3次,指数退避)
    - 实现Circuit Breaker模式(使用Resilience4j)
    - _Requirements: 24.1, 24.2, 24.3_

  - [ ] 5.2 实现MatchingService核心逻辑
    - 实现getRecommendations方法(优先从Redis缓存读取)
    - 实现calculateMatch方法(调用算法服务计算匹配分数)
    - 实现缓存未命中时的实时计算逻辑
    - 实现降级策略(算法服务不可用时使用规则匹配)
    - _Requirements: 1.1, 1.2, 1.6, 1.7, 23.3, 24.3_

  - [ ] 5.3 实现预计算推荐功能
    - 实现preComputeRecommendations方法
    - 创建定时任务(每天02:00执行)
    - 批量计算所有活跃学生的推荐结果
    - 将结果存储到Redis(TTL 24小时)
    - _Requirements: 23.1, 23.2_

  - [ ] 5.4 实现实时匹配度更新
    - 实现updateMatchScores方法
    - 当学生编辑画像时触发
    - 重新计算收藏岗位的匹配分数
    - 返回分数变化(MatchScoreChange对象)
    - _Requirements: 17.1, 17.2, 17.5_

  - [ ]* 5.5 编写属性测试 - Property 1: 匹配分数计算正确性
    - **Property 1: 匹配分数计算正确性**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
    - 使用jqwik生成随机StudentProfile和JobPosting
    - 验证所有6个维度都被计算
    - 验证所有维度分数在[0,100]范围内
    - 验证最终分数等于加权和

  - [ ]* 5.6 编写属性测试 - Property 2: 推荐过滤和排序
    - **Property 2: 推荐过滤和排序**
    - **Validates: Requirements 1.6, 1.7**
    - 验证所有推荐的匹配分数>=60
    - 验证推荐列表按分数降序排列

  - [ ]* 5.7 编写属性测试 - Property 6: 推荐多样性约束
    - **Property 6: 推荐多样性约束**
    - **Validates: Requirements 22.1, 22.2, 22.3**
    - 验证至少3个不同行业
    - 验证单个公司不超过5个岗位
    - 验证至少20%来自非偏好行业

  - [ ]* 5.8 编写单元测试
    - 测试缓存命中场景
    - 测试缓存未命中场景
    - 测试算法服务超时降级
    - 测试Circuit Breaker触发

- [ ] 6. Checkpoint - 验证核心匹配功能
  - 确保所有测试通过,如有问题请询问用户

- [ ] 7. 简历解析服务增强
  - [ ] 7.1 扩展AiFileImportService为ResumeParserService
    - 添加parseResume方法(支持PDF/DOCX/TXT格式)
    - 使用Apache PDFBox解析PDF
    - 使用Apache POI解析DOCX
    - 调用算法服务NER接口提取技能关键词
    - _Requirements: 3.1, 3.2, 3.3_

  - [ ] 7.2 实现技能标准化映射
    - 实现extractSkills方法(从文本提取技能)
    - 实现mapToTaxonomy方法(映射到标准技能分类)
    - 查询dim_skill表获取标准技能分类树
    - 使用模糊匹配算法映射原始技能到标准分类
    - _Requirements: 3.4, 19.4_

  - [ ] 7.3 实现解析准确性验证
    - 实现validateAccuracy方法
    - 检查必填字段完整性(education, major, graduationYear, skills)
    - 计算准确性评分(0-100)
    - 将评分存储到user_profile.resume_accuracy_score
    - _Requirements: 3.7_

  - [ ] 7.4 实现简历解析历史记录
    - 将解析结果存储到user_profile.resume_parse_data(JSON格式)
    - 记录解析时间(resume_parsed_at)
    - 更新解析状态(resume_parse_status: SUCCESS/FAILED/PENDING)
    - _Requirements: 3.5_

  - [ ]* 7.5 编写属性测试 - Property 7: 简历解析数据完整性
    - **Property 7: 简历解析数据完整性**
    - **Validates: Requirements 3.2, 3.5**
    - 验证成功解析的简历包含所有必填字段
    - 验证Student_Profile字段被正确填充
    - 验证自动填充字段有元数据标记

  - [ ]* 7.6 编写单元测试
    - 测试PDF解析成功场景
    - 测试DOCX解析成功场景
    - 测试不支持格式抛出异常
    - 测试文件过大抛出异常
    - 测试技能提取和标准化

- [ ] 8. 反馈学习服务实现
  - [ ] 8.1 实现FeedbackService接口
    - 实现recordInteraction方法(记录用户交互)
    - 扩展biz_job_favorite表记录所有交互类型
    - 根据交互类型分配权重(VIEW=1, FAVORITE=3, APPLY=5, DISMISS=-2)
    - 记录交互时的匹配分数
    - _Requirements: 4.1, 4.2_

  - [ ] 8.2 实现偏好权重更新
    - 实现updatePreferenceWeights方法
    - 分析最近90天的交互数据
    - 计算各维度的偏好权重
    - 更新user_profile.preference_weights字段
    - _Requirements: 4.3, 4.4_

  - [ ] 8.3 实现交互历史查询
    - 实现getInteractionHistory方法
    - 从biz_job_favorite表查询指定天数的交互记录
    - 支持按交互类型过滤
    - 返回交互记录列表
    - _Requirements: 4.2_

  - [ ] 8.4 实现偏好重置功能
    - 实现resetPreferences方法
    - 将preference_weights恢复为默认值
    - 清除交互历史(可选)
    - 确保幂等性(多次调用结果相同)
    - _Requirements: 4.7_

  - [ ]* 8.5 编写属性测试 - Property 8: 反馈记录完整性
    - **Property 8: 反馈记录完整性**
    - **Validates: Requirements 4.1, 4.2**
    - 验证所有交互类型都被正确记录
    - 验证交互权重正确分配
    - 验证记录包含时间戳、学生ID、岗位ID、匹配分数

  - [ ]* 8.6 编写属性测试 - Property 9: 偏好重置幂等性
    - **Property 9: 偏好重置幂等性**
    - **Validates: Requirements 4.7**
    - 验证重置后权重恢复为默认值
    - 验证多次调用重置产生相同结果

  - [ ]* 8.7 编写单元测试
    - 测试不同交互类型的记录
    - 测试偏好权重计算逻辑
    - 测试交互历史查询
    - 测试偏好重置功能

- [ ] 9. 批量推荐服务实现
  - [ ] 9.1 实现BatchRecommendService接口
    - 实现createBatchTask方法(创建批量推荐任务)
    - 复用biz_analysis_task表存储任务信息
    - 设置task_type为"BATCH_RECOMMEND"
    - 验证学生数量不超过100
    - _Requirements: 7.1, 7.2, 7.6_

  - [ ] 9.2 实现异步任务处理
    - 使用Redis Stream作为消息队列
    - 将任务ID推送到队列
    - 创建后台Worker消费任务
    - 逐个学生生成推荐并更新进度
    - _Requirements: 7.3, 7.4_

  - [ ] 9.3 实现任务进度查询
    - 实现getTaskStatus方法
    - 从biz_analysis_task表查询任务状态
    - 从Redis读取实时进度(已完成数/总数)
    - 返回BatchTaskStatus对象
    - _Requirements: 7.3_

  - [ ] 9.4 实现Excel导出功能
    - 实现exportToExcel方法
    - 使用Apache POI生成Excel文件
    - 包含列: 学生姓名、岗位标题、公司、匹配分数、匹配解释
    - 支持多Sheet(每个学生一个Sheet或汇总Sheet)
    - _Requirements: 7.5_

  - [ ]* 9.5 编写单元测试
    - 测试批量任务创建
    - 测试学生数量超限抛出异常
    - 测试任务进度查询
    - 测试Excel导出格式

- [ ] 10. Controller层增强
  - [ ] 10.1 增强RecommendController
    - 添加GET /api/recommend/students/{studentId}接口(获取推荐列表)
    - 添加GET /api/recommend/match接口(计算单个匹配分数)
    - 添加POST /api/recommend/feedback接口(记录用户反馈)
    - 添加POST /api/recommend/batch接口(创建批量推荐任务)
    - 添加GET /api/recommend/batch/{taskId}接口(查询任务状态)
    - _Requirements: 1.1, 4.1, 7.1_

  - [ ] 10.2 添加请求参数验证
    - 使用@Valid注解验证请求参数
    - 创建DTO类(RecommendRequest, FeedbackRequest, BatchRequest)
    - 添加参数校验注解(@NotNull, @Min, @Max等)
    - _Requirements: 1.1, 7.2_

  - [ ] 10.3 添加统一异常处理
    - 扩展现有@ControllerAdvice
    - 处理业务异常(ProfileNotFoundException, JobNotFoundException等)
    - 处理外部服务异常(AlgorithmServiceException)
    - 返回统一错误响应格式
    - _Requirements: Error Handling_

  - [ ]* 10.4 编写集成测试
    - 测试推荐列表接口端到端流程
    - 测试匹配分数计算接口
    - 测试反馈记录接口
    - 测试批量推荐接口

- [ ] 11. Checkpoint - 验证业务服务层
  - 确保所有测试通过,如有问题请询问用户

- [ ] 12. 算法服务API实现(Python FastAPI)
  - [ ] 12.1 创建MatchingEngine API
    - 创建POST /api/v1/matching/calculate接口
    - 接收StudentProfile和JobPosting参数
    - 实现ContentBasedMatcher(基于内容的匹配)
    - 实现CollaborativeFilter(协同过滤)
    - 实现DeepLearningEmbedding(深度学习嵌入)
    - _Requirements: 1.1, 20.1, 20.2_

  - [ ] 12.2 实现多模型融合
    - 创建EnsembleScorer类
    - 计算各算法的独立分数
    - 使用加权平均融合分数
    - 支持动态调整融合权重
    - _Requirements: 20.3, 20.4_

  - [ ] 12.3 实现批量推荐接口
    - 创建POST /api/v1/matching/batch接口
    - 接收学生ID列表和推荐数量
    - 异步处理批量请求
    - 返回任务ID和预估时间
    - _Requirements: 7.1, 7.6_

  - [ ] 12.4 实现简历解析NER接口
    - 创建POST /api/v1/resume/parse接口
    - 使用NLP模型提取技能关键词
    - 识别教育背景、工作经验等实体
    - 返回结构化解析结果
    - _Requirements: 3.3, 3.4_

  - [ ]* 12.5 编写Python单元测试
    - 测试各匹配算法独立计算
    - 测试多模型融合逻辑
    - 测试批量推荐接口
    - 测试简历解析NER

- [ ] 13. 岗位数据标准化处理
  - [ ] 13.1 实现岗位数据清洗服务
    - 创建JobDataCleansingService
    - 实现岗位标题标准化(映射到标准分类)
    - 实现薪资标准化(统一为月薪范围格式)
    - 实现地点标准化(省-市-区层级)
    - _Requirements: 19.1, 19.2, 19.3_

  - [ ] 13.2 实现岗位数据验证
    - 实现validateJobPosting方法
    - 验证必填字段(title, company, location, salary)
    - 拒绝不完整的岗位数据
    - 记录验证失败原因
    - _Requirements: 19.5_

  - [ ] 13.3 实现重复岗位识别和合并
    - 基于(title, company, location)元组识别重复
    - 合并重复岗位数据
    - 保留最新发布的版本
    - _Requirements: 19.6_

  - [ ]* 13.4 编写属性测试 - Property 4: 岗位数据标准化
    - **Property 4: 岗位数据标准化**
    - **Validates: Requirements 19.1, 19.2, 19.3**
    - 验证岗位标题被标准化
    - 验证薪资格式统一为[min, max]月薪范围
    - 验证地点标准化为省-市-区层级

  - [ ]* 13.5 编写属性测试 - Property 5: 岗位数据验证
    - **Property 5: 岗位数据验证**
    - **Validates: Requirements 19.5, 19.6**
    - 验证必填字段存在性
    - 验证不完整岗位被拒绝
    - 验证重复岗位被识别和合并

  - [ ]* 13.6 编写单元测试
    - 测试岗位标题标准化
    - 测试薪资标准化
    - 测试地点标准化
    - 测试重复识别逻辑

- [ ] 14. 推荐效果监控实现
  - [ ] 14.1 实现推荐指标追踪
    - 创建RecommendationMetricsService
    - 追踪CTR(点击率)、申请率、收藏率、忽略率
    - 按日/周/月粒度聚合指标
    - 存储指标到MySQL或时序数据库
    - _Requirements: 12.1, 12.2_

  - [ ] 14.2 实现指标可视化接口
    - 创建GET /api/metrics/recommendations接口
    - 返回指标趋势数据
    - 支持时间范围过滤
    - 支持按学生专业、毕业年份、目标行业分组
    - _Requirements: 12.3, 12.4_

  - [ ] 14.3 实现自动告警
    - 创建定时任务检查指标阈值
    - CTR < 15%或忽略率 > 30%时触发告警
    - 发送邮件或消息通知管理员
    - _Requirements: 12.5_

  - [ ]* 14.4 编写单元测试
    - 测试指标计算逻辑
    - 测试指标聚合
    - 测试告警触发条件

- [ ] 15. 前端接口对接和文档
  - 使用Swagger/OpenAPI生成API文档
  - 添加接口示例和参数说明
  - 提供Postman Collection供前端测试
  - 编写接口对接指南文档
  - _Requirements: All user-facing features_

- [ ] 16. 性能优化和压力测试
  - [ ] 16.1 实施缓存优化
    - 优化Redis Key设计和TTL配置
    - 实现缓存预热(系统启动时加载热点数据)
    - 实现缓存穿透保护(布隆过滤器)
    - _Requirements: 23.2, 23.3_

  - [ ] 16.2 实施数据库优化
    - 为扩展字段添加索引
    - 优化慢查询(使用EXPLAIN分析)
    - 实现读写分离(如需要)
    - _Requirements: 23.1_

  - [ ] 16.3 执行压力测试
    - 使用JMeter测试推荐接口(目标: 500 QPS)
    - 测试批量推荐(目标: 100学生/5分钟)
    - 测试简历解析(目标: 20 QPS)
    - 记录P95响应时间并优化
    - _Requirements: 23.1, 23.3, 7.6_

  - [ ]* 16.4 编写性能测试报告
    - 记录各接口的响应时间和吞吐量
    - 识别性能瓶颈
    - 提供优化建议

- [ ] 17. 最终集成测试和验收
  - [ ] 17.1 执行端到端集成测试
    - 测试学生推荐完整流程
    - 测试教师批量推荐流程
    - 测试管理员配置管理流程
    - 测试简历解析和画像构建流程
    - _Requirements: All requirements_

  - [ ] 17.2 执行属性测试套件
    - 运行所有9个属性测试
    - 每个属性至少100次迭代
    - 确保所有属性测试通过
    - _Requirements: Correctness Properties 1-9_

  - [ ] 17.3 验证错误处理和降级
    - 模拟算法服务不可用
    - 验证降级到规则匹配
    - 模拟Redis不可用
    - 验证直接查询MySQL
    - _Requirements: Error Handling_

  - [ ] 17.4 代码覆盖率检查
    - 运行JaCoCo生成覆盖率报告
    - 确保行覆盖率 >= 80%
    - 确保分支覆盖率 >= 70%
    - _Requirements: Testing Strategy_

- [ ] 18. Final Checkpoint - 完整功能验收
  - 确保所有测试通过,所有功能正常工作,如有问题请询问用户

---

## Notes

- 任务标记`*`的为可选测试任务,可根据项目进度跳过以加快MVP交付
- 每个任务都明确引用了需求编号,确保需求可追溯性
- Checkpoint任务用于阶段性验证,确保增量开发质量
- 属性测试使用jqwik框架,每个属性至少运行100次迭代
- 单元测试和集成测试使用JUnit 5和Spring Boot Test
- 所有代码必须通过现有的代码规范检查(Checkstyle/PMD)

## Implementation Strategy

**Phase 1: 数据层和基础设施** (Tasks 1-3)
- 扩展数据库表结构
- 扩展实体类
- 配置Redis缓存

**Phase 2: 核心服务实现** (Tasks 4-8)
- 算法配置服务
- 匹配服务
- 简历解析服务
- 反馈学习服务

**Phase 3: 批量和管理功能** (Tasks 9-11)
- 批量推荐服务
- Controller层增强
- 异常处理

**Phase 4: 算法服务和数据处理** (Tasks 12-13)
- Python算法服务API
- 岗位数据标准化

**Phase 5: 监控和优化** (Tasks 14-16)
- 推荐效果监控
- 性能优化
- 压力测试

**Phase 6: 集成和验收** (Tasks 17-18)
- 端到端测试
- 属性测试验证
- 最终验收

## Success Criteria

- ✅ 所有9个属性测试通过(100+迭代)
- ✅ 单元测试覆盖率 >= 80%
- ✅ 推荐接口P95响应时间 < 500ms
- ✅ 批量推荐100学生 < 5分钟
- ✅ 简历解析准确率 >= 85%
- ✅ 算法服务降级功能正常
- ✅ 所有集成测试通过
