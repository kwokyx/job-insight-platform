USE career_platform;

INSERT INTO dim_major (
    major_code, major_name, major_category, education_level, status, created_at, updated_at
) VALUES
    ('MAJOR_CS', '计算机科学与技术', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_SE', '软件工程', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_DS', '数据科学与大数据技术', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_AI', '人工智能', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_NE', '网络工程', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_IS', '信息安全', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_IMIS', '信息管理与信息系统', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_MATH', '数学与应用数学', '理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_STAT', '统计学', '理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_APPSTAT', '应用统计学', '理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_EE', '电子信息工程', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_CE', '通信工程', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_AUTO', '自动化', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_ELECTRIC', '电气工程及其自动化', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_ME', '机械设计制造及其自动化', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_ROBOT', '机器人工程', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_CIVIL', '土木工程', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_ARCH', '建筑学', '工学', '本科', 1, NOW(), NOW()),
    ('MAJOR_PM', '工程管理', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_ACC', '会计学', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_FINM', '财务管理', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_AUDIT', '审计学', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_FIN', '金融学', '经济学', '本科', 1, NOW(), NOW()),
    ('MAJOR_ECON', '经济学', '经济学', '本科', 1, NOW(), NOW()),
    ('MAJOR_TRADE', '国际经济与贸易', '经济学', '本科', 1, NOW(), NOW()),
    ('MAJOR_MKT', '市场营销', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_BA', '工商管理', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_HRM', '人力资源管理', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_LOG', '物流管理', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_SCM', '供应链管理', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_ECOM', '电子商务', '管理学', '本科', 1, NOW(), NOW()),
    ('MAJOR_JOUR', '新闻学', '文学', '本科', 1, NOW(), NOW()),
    ('MAJOR_COMM', '传播学', '文学', '本科', 1, NOW(), NOW()),
    ('MAJOR_ADV', '广告学', '文学', '本科', 1, NOW(), NOW()),
    ('MAJOR_ENG', '英语', '文学', '本科', 1, NOW(), NOW()),
    ('MAJOR_TRANSL', '翻译', '文学', '本科', 1, NOW(), NOW()),
    ('MAJOR_LAW', '法学', '法学', '本科', 1, NOW(), NOW()),
    ('MAJOR_PHARM', '药学', '医学', '本科', 1, NOW(), NOW()),
    ('MAJOR_CLIN', '临床医学', '医学', '本科', 1, NOW(), NOW()),
    ('MAJOR_NURSE', '护理学', '医学', '本科', 1, NOW(), NOW()),
    ('MAJOR_BME', '生物医学工程', '工学', '本科', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    major_name = VALUES(major_name),
    major_category = VALUES(major_category),
    education_level = VALUES(education_level),
    status = VALUES(status),
    updated_at = NOW();

INSERT INTO major_job_match_rule (
    major_id, job_category_id, match_weight, core_skills, rule_source, status, remark, created_at, updated_at
)
SELECT
    m.id,
    c.id,
    seed.match_weight,
    seed.core_skills,
    'manual',
    1,
    seed.remark,
    NOW(),
    NOW()
FROM (
    SELECT 'MAJOR_CS' AS major_code, 'Java' AS category_name, 0.95 AS match_weight, 'Java,数据结构,算法,数据库' AS core_skills, '计算机基础开发方向' AS remark
    UNION ALL SELECT 'MAJOR_CS', 'Python', 0.95, 'Python,算法,脚本开发,数据库', '计算机基础开发方向'
    UNION ALL SELECT 'MAJOR_CS', 'web前端', 0.88, 'HTML,CSS,JavaScript,前端工程化', '计算机基础前端方向'
    UNION ALL SELECT 'MAJOR_CS', '前端开发', 0.88, 'JavaScript,Vue,React,前端工程化', '计算机基础前端方向'
    UNION ALL SELECT 'MAJOR_CS', '测试开发', 0.86, '自动化测试,脚本开发,测试框架', '计算机基础测试方向'
    UNION ALL SELECT 'MAJOR_CS', '运维工程师', 0.82, 'Linux,网络,Shell,部署', '计算机基础运维方向'

    UNION ALL SELECT 'MAJOR_SE', 'Java', 0.95, 'Java,设计模式,数据库,后端开发', '软件工程开发方向'
    UNION ALL SELECT 'MAJOR_SE', 'Python', 0.90, 'Python,脚本开发,后端开发', '软件工程开发方向'
    UNION ALL SELECT 'MAJOR_SE', 'web前端', 0.88, 'HTML,CSS,JavaScript', '软件工程前端方向'
    UNION ALL SELECT 'MAJOR_SE', '测试工程师', 0.88, '测试用例,功能测试,缺陷管理', '软件工程测试方向'
    UNION ALL SELECT 'MAJOR_SE', '测试开发', 0.90, '自动化测试,Python,测试框架', '软件工程测试方向'
    UNION ALL SELECT 'MAJOR_SE', '产品经理', 0.70, '需求分析,原型设计,沟通协调', '软件工程产品协同方向'

    UNION ALL SELECT 'MAJOR_DS', '数据分析师', 0.96, 'SQL,Python,统计分析,可视化', '数据专业核心方向'
    UNION ALL SELECT 'MAJOR_DS', '数据开发', 0.92, 'SQL,ETL,数据建模,大数据', '数据专业核心方向'
    UNION ALL SELECT 'MAJOR_DS', '数据挖掘', 0.95, '机器学习,特征工程,Python', '数据专业核心方向'
    UNION ALL SELECT 'MAJOR_DS', '数据仓库', 0.92, '维度建模,ETL,SQL', '数据专业核心方向'
    UNION ALL SELECT 'MAJOR_DS', '算法工程师', 0.90, '机器学习,建模,Python', '数据专业算法方向'
    UNION ALL SELECT 'MAJOR_DS', '金融数据分析', 0.88, '统计分析,SQL,报表分析', '数据专业行业延展'

    UNION ALL SELECT 'MAJOR_AI', '算法工程师', 0.98, '机器学习,深度学习,模型训练', '人工智能核心方向'
    UNION ALL SELECT 'MAJOR_AI', '推荐算法', 0.95, '推荐系统,特征工程,Python', '人工智能核心方向'
    UNION ALL SELECT 'MAJOR_AI', '搜索算法', 0.94, '信息检索,排序学习,NLP', '人工智能核心方向'
    UNION ALL SELECT 'MAJOR_AI', '导航算法', 0.90, '路径规划,控制算法,建模', '人工智能核心方向'
    UNION ALL SELECT 'MAJOR_AI', 'AI产品经理', 0.82, 'AI产品设计,需求分析,模型应用', '人工智能产品方向'
    UNION ALL SELECT 'MAJOR_AI', '数据标注/AI训练师', 0.80, '数据标注,模型训练,质量控制', '人工智能支持方向'

    UNION ALL SELECT 'MAJOR_NE', '网络运维', 0.95, 'TCP/IP,交换路由,故障排查', '网络工程核心方向'
    UNION ALL SELECT 'MAJOR_NE', '运维工程师', 0.90, 'Linux,网络,服务器运维', '网络工程核心方向'
    UNION ALL SELECT 'MAJOR_NE', '运维开发工程师', 0.85, 'Python,Shell,自动化运维', '网络工程延展方向'
    UNION ALL SELECT 'MAJOR_NE', '数据通信工程师', 0.90, '通信协议,网络设备,排障', '网络工程通信方向'

    UNION ALL SELECT 'MAJOR_IS', '渗透测试', 0.96, '漏洞评估,安全测试,网络安全', '信息安全核心方向'
    UNION ALL SELECT 'MAJOR_IS', '网络运维', 0.78, '网络安全,网络架构,监控', '信息安全运维方向'
    UNION ALL SELECT 'MAJOR_IS', '运维工程师', 0.75, 'Linux,安全加固,监控', '信息安全运维方向'

    UNION ALL SELECT 'MAJOR_IMIS', '产品经理', 0.86, '需求分析,业务建模,数据思维', '信管产品方向'
    UNION ALL SELECT 'MAJOR_IMIS', '数据产品经理', 0.92, '数据治理,指标体系,产品设计', '信管产品方向'
    UNION ALL SELECT 'MAJOR_IMIS', '数据分析师', 0.88, 'SQL,业务分析,可视化', '信管数据方向'
    UNION ALL SELECT 'MAJOR_IMIS', '数据运营', 0.84, '数据分析,活动优化,报表', '信管运营方向'

    UNION ALL SELECT 'MAJOR_MATH', '算法工程师', 0.92, '数学建模,优化算法,Python', '数学算法方向'
    UNION ALL SELECT 'MAJOR_MATH', '数据分析师', 0.90, '统计分析,建模,SQL', '数学数据方向'
    UNION ALL SELECT 'MAJOR_MATH', '金融数据分析', 0.88, '量化分析,统计建模,SQL', '数学金融方向'
    UNION ALL SELECT 'MAJOR_MATH', '财务分析', 0.75, '统计建模,财务分析,Excel', '数学分析方向'

    UNION ALL SELECT 'MAJOR_STAT', '数据分析师', 0.96, '统计分析,SQL,Python,可视化', '统计学核心方向'
    UNION ALL SELECT 'MAJOR_STAT', '销售数据分析', 0.88, '业务分析,报表分析,统计推断', '统计学业务方向'
    UNION ALL SELECT 'MAJOR_STAT', '金融数据分析', 0.90, '统计模型,时间序列,报表分析', '统计学金融方向'
    UNION ALL SELECT 'MAJOR_STAT', '财务分析', 0.78, '数据建模,经营分析,Excel', '统计学分析方向'

    UNION ALL SELECT 'MAJOR_APPSTAT', '数据分析师', 0.96, '统计分析,实验设计,Python', '应用统计核心方向'
    UNION ALL SELECT 'MAJOR_APPSTAT', '数据挖掘', 0.90, '机器学习,建模,特征工程', '应用统计核心方向'
    UNION ALL SELECT 'MAJOR_APPSTAT', '销售数据分析', 0.88, '指标分析,业务分析,SQL', '应用统计业务方向'
    UNION ALL SELECT 'MAJOR_APPSTAT', '金融数据分析', 0.88, '统计建模,风险分析,报表', '应用统计金融方向'

    UNION ALL SELECT 'MAJOR_EE', '电气工程师', 0.88, '电路分析,嵌入式,硬件调试', '电子信息方向'
    UNION ALL SELECT 'MAJOR_EE', '硬件测试', 0.86, '硬件测试,电路调试,仪器使用', '电子信息方向'
    UNION ALL SELECT 'MAJOR_EE', '测试工程师', 0.80, '测试流程,缺陷分析,硬件/软件联调', '电子信息方向'

    UNION ALL SELECT 'MAJOR_CE', '数据通信工程师', 0.95, '通信协议,网络优化,设备调试', '通信工程核心方向'
    UNION ALL SELECT 'MAJOR_CE', '网络运维', 0.85, '网络架构,监控,故障排查', '通信工程运维方向'
    UNION ALL SELECT 'MAJOR_CE', '测试工程师', 0.75, '通信测试,故障分析', '通信工程测试方向'

    UNION ALL SELECT 'MAJOR_AUTO', '自动化工程师', 0.96, '控制系统,PLC,自动化集成', '自动化核心方向'
    UNION ALL SELECT 'MAJOR_AUTO', '测试工程师', 0.78, '控制测试,系统联调,故障排查', '自动化测试方向'
    UNION ALL SELECT 'MAJOR_AUTO', '硬件测试', 0.76, '仪器设备,联调测试', '自动化测试方向'

    UNION ALL SELECT 'MAJOR_ELECTRIC', '电气工程师', 0.97, '电气控制,供配电,系统设计', '电气核心方向'
    UNION ALL SELECT 'MAJOR_ELECTRIC', '自动化工程师', 0.86, 'PLC,控制系统,调试', '电气自动化方向'
    UNION ALL SELECT 'MAJOR_ELECTRIC', '测试工程师', 0.72, '设备测试,故障排查', '电气测试方向'

    UNION ALL SELECT 'MAJOR_ME', '机械工程师', 0.97, '机械设计,制图,工艺分析', '机械核心方向'
    UNION ALL SELECT 'MAJOR_ME', '自动化工程师', 0.80, '设备自动化,控制集成', '机械自动化方向'
    UNION ALL SELECT 'MAJOR_ME', '硬件测试', 0.70, '设备测试,结构验证', '机械测试方向'

    UNION ALL SELECT 'MAJOR_ROBOT', '自动化工程师', 0.90, '机器人控制,自动化集成', '机器人工程方向'
    UNION ALL SELECT 'MAJOR_ROBOT', '算法工程师', 0.82, '控制算法,机器视觉,路径规划', '机器人工程方向'
    UNION ALL SELECT 'MAJOR_ROBOT', '测试工程师', 0.74, '机器人测试,系统联调', '机器人工程方向'

    UNION ALL SELECT 'MAJOR_CIVIL', '土木/土建工程师', 0.98, '结构设计,施工管理,工程识图', '土木核心方向'
    UNION ALL SELECT 'MAJOR_CIVIL', '工程管理', 0.82, '项目管理,施工组织,成本控制', '土木延展方向'

    UNION ALL SELECT 'MAJOR_ARCH', '建筑设计师', 0.98, '建筑设计,制图,方案表达', '建筑学核心方向'
    UNION ALL SELECT 'MAJOR_ARCH', '土木/土建工程师', 0.70, '建筑识图,项目配合', '建筑学延展方向'

    UNION ALL SELECT 'MAJOR_PM', '工程管理', 0.95, '项目管理,成本控制,进度管理', '工程管理核心方向'
    UNION ALL SELECT 'MAJOR_PM', '采购专员/助理', 0.72, '供应商管理,成本核算,项目协同', '工程管理供应方向'

    UNION ALL SELECT 'MAJOR_ACC', '会计', 0.98, '会计准则,账务处理,Excel', '会计学核心方向'
    UNION ALL SELECT 'MAJOR_ACC', '会计助理', 0.95, '票据处理,账务录入,报销审核', '会计学核心方向'
    UNION ALL SELECT 'MAJOR_ACC', '总账会计', 0.94, '总账核算,结账报表,财务制度', '会计学进阶方向'
    UNION ALL SELECT 'MAJOR_ACC', '成本会计', 0.92, '成本归集,核算分析,报表', '会计学进阶方向'
    UNION ALL SELECT 'MAJOR_ACC', '注册会计师', 0.90, '审计准则,财务报告,法规', '会计学证照方向'
    UNION ALL SELECT 'MAJOR_ACC', '财务专员/助理', 0.88, '财务处理,对账,报表', '会计学财务方向'
    UNION ALL SELECT 'MAJOR_ACC', '审计', 0.86, '审计基础,凭证检查,风险识别', '会计学审计方向'

    UNION ALL SELECT 'MAJOR_FINM', '财务分析', 0.95, '预算分析,经营分析,财务建模', '财务管理核心方向'
    UNION ALL SELECT 'MAJOR_FINM', '财务专员/助理', 0.90, '报表编制,预算执行,对账', '财务管理基础方向'
    UNION ALL SELECT 'MAJOR_FINM', '财务主管', 0.88, '财务管理,流程控制,预算', '财务管理进阶方向'
    UNION ALL SELECT 'MAJOR_FINM', '财务经理/主管', 0.86, '预算管理,财务统筹,经营分析', '财务管理进阶方向'
    UNION ALL SELECT 'MAJOR_FINM', '会计', 0.82, '账务处理,核算,报表', '财务管理延展方向'

    UNION ALL SELECT 'MAJOR_AUDIT', '审计', 0.98, '审计程序,内控评价,底稿编制', '审计学核心方向'
    UNION ALL SELECT 'MAJOR_AUDIT', '审计经理', 0.88, '审计项目管理,内控审查', '审计学进阶方向'
    UNION ALL SELECT 'MAJOR_AUDIT', '注册会计师', 0.90, '审计准则,财报分析,法规', '审计学证照方向'
    UNION ALL SELECT 'MAJOR_AUDIT', '财务分析', 0.76, '报表分析,风险识别', '审计学分析方向'

    UNION ALL SELECT 'MAJOR_FIN', '金融数据分析', 0.92, '金融分析,SQL,报表建模', '金融学数据方向'
    UNION ALL SELECT 'MAJOR_FIN', '投行财务分析', 0.92, '估值建模,财务报表,行业研究', '金融学投行方向'
    UNION ALL SELECT 'MAJOR_FIN', '财务分析', 0.88, '经营分析,财务模型,Excel', '金融学分析方向'
    UNION ALL SELECT 'MAJOR_FIN', '金融产品经理', 0.82, '金融产品,需求分析,业务理解', '金融学产品方向'

    UNION ALL SELECT 'MAJOR_ECON', '财务分析', 0.86, '经济分析,经营分析,数据处理', '经济学分析方向'
    UNION ALL SELECT 'MAJOR_ECON', '数据分析师', 0.82, '统计分析,SQL,可视化', '经济学数据方向'
    UNION ALL SELECT 'MAJOR_ECON', '市场营销', 0.72, '市场研究,用户分析,策略制定', '经济学市场方向'

    UNION ALL SELECT 'MAJOR_TRADE', '市场营销', 0.82, '市场开拓,客户分析,商务沟通', '国贸营销方向'
    UNION ALL SELECT 'MAJOR_TRADE', '采购专员/助理', 0.84, '供应商协同,采购执行,商务谈判', '国贸供应方向'
    UNION ALL SELECT 'MAJOR_TRADE', '物流运营', 0.78, '供应链协同,订单流程,物流跟踪', '国贸物流方向'

    UNION ALL SELECT 'MAJOR_MKT', '市场营销', 0.98, '品牌推广,市场策划,用户洞察', '市场营销核心方向'
    UNION ALL SELECT 'MAJOR_MKT', '新媒体运营', 0.90, '内容运营,投放,用户增长', '市场营销新媒体方向'
    UNION ALL SELECT 'MAJOR_MKT', '产品运营', 0.84, '活动运营,增长分析,用户运营', '市场营销运营方向'

    UNION ALL SELECT 'MAJOR_BA', '产品运营', 0.84, '业务分析,运营策略,执行落地', '工商管理运营方向'
    UNION ALL SELECT 'MAJOR_BA', '产品经理', 0.78, '需求管理,跨部门协作,业务建模', '工商管理产品方向'
    UNION ALL SELECT 'MAJOR_BA', '市场营销', 0.80, '市场分析,客户管理,策略制定', '工商管理市场方向'
    UNION ALL SELECT 'MAJOR_BA', '人力资源专员/助理', 0.72, '组织协同,沟通执行', '工商管理综合方向'

    UNION ALL SELECT 'MAJOR_HRM', '人力资源专员/助理', 0.98, '招聘,培训,员工关系', '人力资源核心方向'

    UNION ALL SELECT 'MAJOR_LOG', '物流运营', 0.95, '物流流程,调度协调,数据跟踪', '物流管理核心方向'
    UNION ALL SELECT 'MAJOR_LOG', '采购专员/助理', 0.80, '采购执行,订单协同,供应商管理', '物流管理采购方向'

    UNION ALL SELECT 'MAJOR_SCM', '物流运营', 0.92, '供应链协同,计划执行,库存管理', '供应链管理核心方向'
    UNION ALL SELECT 'MAJOR_SCM', '采购专员/助理', 0.88, '采购流程,供应商管理,成本控制', '供应链管理采购方向'
    UNION ALL SELECT 'MAJOR_SCM', '供应链产品经理', 0.82, '供应链系统,需求分析,流程设计', '供应链管理产品方向'

    UNION ALL SELECT 'MAJOR_ECOM', '产品运营', 0.90, '电商运营,活动策划,转化优化', '电子商务核心方向'
    UNION ALL SELECT 'MAJOR_ECOM', '电商产品经理', 0.86, '电商产品设计,业务流程,需求分析', '电子商务产品方向'
    UNION ALL SELECT 'MAJOR_ECOM', '新媒体运营', 0.82, '内容运营,用户增长,投放优化', '电子商务增长方向'
    UNION ALL SELECT 'MAJOR_ECOM', '数据运营', 0.80, '数据分析,指标监控,活动复盘', '电子商务数据方向'

    UNION ALL SELECT 'MAJOR_JOUR', '新媒体运营', 0.92, '内容采编,热点策划,传播分析', '新闻学核心方向'
    UNION ALL SELECT 'MAJOR_JOUR', '产品运营', 0.74, '内容运营,用户沟通,活动执行', '新闻学运营方向'

    UNION ALL SELECT 'MAJOR_COMM', '新媒体运营', 0.94, '内容传播,用户增长,数据复盘', '传播学核心方向'
    UNION ALL SELECT 'MAJOR_COMM', '市场营销', 0.82, '传播策划,品牌营销,活动推广', '传播学市场方向'
    UNION ALL SELECT 'MAJOR_COMM', '产品运营', 0.78, '用户运营,内容运营,活动执行', '传播学运营方向'

    UNION ALL SELECT 'MAJOR_ADV', '市场营销', 0.90, '广告策划,品牌传播,投放分析', '广告学核心方向'
    UNION ALL SELECT 'MAJOR_ADV', '新媒体运营', 0.88, '内容创意,投放,增长运营', '广告学新媒体方向'
    UNION ALL SELECT 'MAJOR_ADV', 'UI设计师', 0.70, '视觉表达,创意设计,品牌物料', '广告学设计延展方向'

    UNION ALL SELECT 'MAJOR_ENG', '英语教师', 0.96, '英语教学,听说读写,课程辅导', '英语核心方向'
    UNION ALL SELECT 'MAJOR_TRANSL', '英语教师', 0.85, '双语表达,语言转化,教学支持', '翻译延展方向'

    UNION ALL SELECT 'MAJOR_LAW', '律师', 0.98, '法律检索,合同审查,诉讼/非诉', '法学核心方向'

    UNION ALL SELECT 'MAJOR_PHARM', '医药产品经理', 0.82, '药学知识,产品理解,行业法规', '药学产品方向'
    UNION ALL SELECT 'MAJOR_PHARM', '临床研究', 0.80, 'GCP,临床试验,医学文献', '药学临床方向'

    UNION ALL SELECT 'MAJOR_CLIN', '临床研究', 0.96, '临床试验,病例管理,医学知识', '临床医学核心方向'
    UNION ALL SELECT 'MAJOR_CLIN', '临床数据分析', 0.86, '临床数据,统计分析,医学研究', '临床医学分析方向'
    UNION ALL SELECT 'MAJOR_CLIN', '护士', 0.72, '临床护理,沟通协作,患者管理', '临床医学延展方向'

    UNION ALL SELECT 'MAJOR_NURSE', '护士', 0.98, '护理操作,患者沟通,临床规范', '护理学核心方向'
    UNION ALL SELECT 'MAJOR_NURSE', '临床研究', 0.72, '临床支持,病例跟踪,沟通协调', '护理学延展方向'

    UNION ALL SELECT 'MAJOR_BME', '临床研究', 0.82, '医学工程,设备理解,临床协同', '生物医学工程临床方向'
    UNION ALL SELECT 'MAJOR_BME', '临床数据分析', 0.84, '医学数据,统计分析,研究支持', '生物医学工程数据方向'
    UNION ALL SELECT 'MAJOR_BME', '硬件测试', 0.76, '仪器设备,验证测试,问题定位', '生物医学工程测试方向'
) seed
JOIN dim_major m
  ON m.major_code = seed.major_code
JOIN dim_job_category c
  ON c.category_name = seed.category_name
ON DUPLICATE KEY UPDATE
    match_weight = VALUES(match_weight),
    core_skills = VALUES(core_skills),
    rule_source = VALUES(rule_source),
    status = VALUES(status),
    remark = VALUES(remark),
    updated_at = NOW();

SELECT
    (SELECT COUNT(*) FROM dim_major) AS major_count,
    (SELECT COUNT(*) FROM major_job_match_rule) AS match_rule_count;
