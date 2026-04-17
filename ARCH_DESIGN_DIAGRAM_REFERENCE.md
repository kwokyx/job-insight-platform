# 职业能力大数据服务平台系统架构与详细设计说明书图示参考

## 使用说明

本文档专门补充《系统架构与详细设计说明书》中需要配套插入的图。所有图均使用 `Mermaid` 编写，且围绕当前项目实际模块展开，包括：

1. 分布式数据采集模块
2. 数据处理与存储模块
3. 报告分析系统
4. 报告自动生成系统
5. 就业岗位推送系统
6. 用户画像与个人中心
7. AI 助手分析模块
8. 对外公开 API 模块
9. 平台管理与监控模块

可将以下图按章节插入 Word，对应“总体架构图、用例图、活动图、流程图、部署图、模块关系图、箱型图、时序图”等位置。

---

## 1. 总体架构图

### 1.1 平台总体架构图

```mermaid
flowchart LR
    subgraph U["用户与调用方"]
        S1["学生用户"]
        S2["教师用户"]
        S3["系统管理员"]
        S4["外部合作系统"]
    end

    subgraph F["前端展示层 Vue3"]
        F1["登录注册与个人中心"]
        F2["就业分析大屏"]
        F3["岗位大厅与推荐页面"]
        F4["报告中心"]
        F5["AI 助手页面"]
        F6["后台管理控制台"]
    end

    subgraph B["业务服务层 Spring Boot"]
        B1["认证与权限服务"]
        B2["岗位数据服务"]
        B3["分析服务"]
        B4["报告服务"]
        B5["推荐服务"]
        B6["AI 会话与工具服务"]
        B7["开放 API 服务"]
        B8["后台管理与监控服务"]
    end

    subgraph A["算法与智能服务层"]
        A1["FastAPI 算法服务"]
        A2["薪资预测模型"]
        A3["技能图谱分析"]
        A4["推荐排序与路径规划"]
        A5["大模型问答与摘要生成"]
    end

    subgraph D["数据采集与数据处理层"]
        D1["采集调度中心 Quartz"]
        D2["消息队列 RabbitMQ"]
        D3["采集从节点集群"]
        D4["ETL 清洗与去重"]
        D5["结构化转换与校验"]
    end

    subgraph S["数据存储层"]
        SDB1["MySQL 业务库"]
        SDB2["Redis 缓存"]
        SDB3["报告文件存储"]
        SDB4["日志与审计数据"]
    end

    S1 --> F
    S2 --> F
    S3 --> F
    S4 --> F6
    S4 --> B7

    F --> B
    B3 --> A1
    B4 --> A5
    B5 --> A4
    B6 --> A5
    A1 --> A2
    A1 --> A3
    A1 --> A4

    D1 --> D2
    D2 --> D3
    D3 --> D4
    D4 --> D5
    D5 --> SDB1
    D5 --> SDB2

    B --> S
    B8 --> D1
    B8 --> D3
```

### 1.2 平台功能模块箱型图

```mermaid
flowchart TB
    P["职业能力大数据服务平台"]

    P --> M1["账号基础管理模块"]
    P --> M2["分布式数据采集模块"]
    P --> M3["数据处理与存储模块"]
    P --> M4["就业分析系统"]
    P --> M5["报告自动生成系统"]
    P --> M6["就业岗位推送系统"]
    P --> M7["用户画像与个人中心"]
    P --> M8["AI 助手分析模块"]
    P --> M9["对外公开 API 模块"]
    P --> M10["平台管理与监控模块"]

    M2 --> M3
    M3 --> M4
    M4 --> M5
    M3 --> M6
    M7 --> M6
    M7 --> M8
    M4 --> M8
    M4 --> M9
    M10 --> M2
    M10 --> M9
```

---

## 2. 分层架构图与逻辑视图

### 2.1 四层逻辑视图

```mermaid
flowchart TB
    subgraph L1["表现层"]
        V1["Web 前端页面"]
        V2["后台管理页面"]
        V3["可视化图表页"]
        V4["AI 会话界面"]
    end

    subgraph L2["接口控制层"]
        C1["AuthController"]
        C2["JobController"]
        C3["AnalysisController"]
        C4["RecommendController"]
        C5["ReportController"]
        C6["AiController"]
        C7["OpenApiController"]
        C8["AdminController"]
    end

    subgraph L3["业务服务层"]
        S1["认证服务"]
        S2["岗位服务"]
        S3["分析服务"]
        S4["推荐服务"]
        S5["报告服务"]
        S6["AI 服务"]
        S7["画像服务"]
        S8["监控服务"]
    end

    subgraph L4["数据与基础设施层"]
        D1["MySQL"]
        D2["Redis"]
        D3["RabbitMQ"]
        D4["Quartz"]
        D5["FastAPI"]
        D6["LLM API"]
        D7["文件存储"]
    end

    L1 --> L2
    L2 --> L3
    L3 --> L4
```

### 2.2 数据流转逻辑图

```mermaid
flowchart LR
    A["招聘平台原始职位数据"] --> B["采集节点抓取"]
    B --> C["原始数据缓冲"]
    C --> D["字段清洗"]
    D --> E["去重与校验"]
    E --> F["结构化岗位表"]
    F --> G["统计分析引擎"]
    G --> H["报告生成"]
    G --> I["推荐计算"]
    G --> J["开放接口"]
    F --> K["画像匹配"]
    K --> I
    G --> L["AI 助手工具调用"]
```

---

## 3. 部署视图

### 3.1 容器部署图

```mermaid
flowchart LR
    U["浏览器"] --> N["Nginx / Vue3 前端容器"]
    N --> J["Spring Boot 后端容器"]
    J --> M["MySQL 容器"]
    J --> R["Redis 容器"]
    J --> Q["RabbitMQ 容器"]
    J --> P["FastAPI 算法容器"]
    J --> L["大模型 API"]
    J --> FS["报告文件目录/对象存储"]

    C0["采集主节点容器"] --> Q
    Q --> C1["采集从节点 1"]
    Q --> C2["采集从节点 2"]
    Q --> C3["采集从节点 N"]
    C1 --> J
    C2 --> J
    C3 --> J
```

### 3.2 分布式采集部署图

```mermaid
flowchart TB
    subgraph MC["主节点"]
        M1["任务配置管理"]
        M2["Quartz 调度器"]
        M3["节点注册与心跳检测"]
        M4["任务拆分器"]
        M5["异常重试中心"]
    end

    subgraph MQ["消息中间件"]
        Q1["RabbitMQ 公平分发队列"]
    end

    subgraph WC["从节点集群"]
        W1["从节点 A"]
        W2["从节点 B"]
        W3["从节点 C"]
    end

    subgraph RES["资源池"]
        T1["Token 池"]
        T2["代理 IP 池"]
    end

    subgraph ST["存储"]
        S1["MySQL 原始/结构化数据"]
        S2["Redis 热点缓存"]
        S3["异常日志"]
    end

    M2 --> M4
    M4 --> Q1
    M3 --> W1
    M3 --> W2
    M3 --> W3
    Q1 --> W1
    Q1 --> W2
    Q1 --> W3
    W1 --> T1
    W1 --> T2
    W2 --> T1
    W2 --> T2
    W3 --> T1
    W3 --> T2
    W1 --> S1
    W2 --> S1
    W3 --> S1
    W1 --> S3
    W2 --> S3
    W3 --> S3
    S1 --> S2
    M5 --> Q1
```

---

## 4. 关键用例图

### 4.1 平台总体用例图

```mermaid
flowchart LR
    U1["学生"]
    U2["教师"]
    U3["系统管理员"]
    U4["外部系统"]

    UC1["注册/登录"]
    UC2["维护个人画像"]
    UC3["浏览岗位数据"]
    UC4["查看就业分析结果"]
    UC5["获取岗位推荐"]
    UC6["生成分析报告"]
    UC7["导出 PDF 报告"]
    UC8["使用 AI 助手问答"]
    UC9["管理采集任务"]
    UC10["管理采集节点"]
    UC11["管理用户与权限"]
    UC12["管理开放 API"]
    UC13["调用开放岗位接口"]
    UC14["调用开放分析接口"]
    UC15["查看系统监控与日志"]

    U1 --> UC1
    U1 --> UC2
    U1 --> UC3
    U1 --> UC4
    U1 --> UC5
    U1 --> UC6
    U1 --> UC7
    U1 --> UC8

    U2 --> UC1
    U2 --> UC3
    U2 --> UC4
    U2 --> UC6
    U2 --> UC7
    U2 --> UC8

    U3 --> UC1
    U3 --> UC9
    U3 --> UC10
    U3 --> UC11
    U3 --> UC12
    U3 --> UC15

    U4 --> UC13
    U4 --> UC14
```

### 4.2 报告生成系统用例图

```mermaid
flowchart LR
    A1["学生/教师"]
    A2["系统管理员"]

    R1["选择报告类型"]
    R2["设置分析维度"]
    R3["提交报告生成任务"]
    R4["轮询报告状态"]
    R5["查看报告详情"]
    R6["下钻查看统计数据"]
    R7["导出 PDF"]
    R8["创建定时报告计划"]
    R9["启停报告计划"]
    R10["管理公开报告"]

    A1 --> R1
    A1 --> R2
    A1 --> R3
    A1 --> R4
    A1 --> R5
    A1 --> R6
    A1 --> R7

    A2 --> R8
    A2 --> R9
    A2 --> R10
```

### 4.3 AI 助手模块用例图

```mermaid
flowchart LR
    U1["学生/教师"]
    U2["系统管理员"]

    C1["发起就业问题咨询"]
    C2["查看历史会话"]
    C3["继续上下文追问"]
    C4["导入简历/画像文件"]
    C5["触发 Agent 工具分析"]
    C6["查看 AI 额度与配额"]
    C7["删除历史会话"]

    U1 --> C1
    U1 --> C2
    U1 --> C3
    U1 --> C4
    U1 --> C5
    U1 --> C7
    U2 --> C6
```

### 4.4 后台管理模块用例图

```mermaid
flowchart LR
    M["系统管理员"]

    G1["新增/编辑采集任务"]
    G2["启动/暂停采集任务"]
    G3["查看节点在线状态"]
    G4["监控 CPU/内存/采集速度"]
    G5["查看异常日志"]
    G6["管理平台用户"]
    G7["配置角色权限"]
    G8["管理 API Key"]
    G9["查看 API 调用日志"]
    G10["查看报告任务状态"]

    M --> G1
    M --> G2
    M --> G3
    M --> G4
    M --> G5
    M --> G6
    M --> G7
    M --> G8
    M --> G9
    M --> G10
```

---

## 5. 活动图

### 5.1 用户登录活动图

```mermaid
flowchart TD
    A["进入登录页面"] --> B["输入用户名/密码"]
    B --> C["前端表单校验"]
    C -->|校验失败| D["提示输入错误"]
    D --> B
    C -->|校验通过| E["调用 /api/v1/auth/login"]
    E --> F["后端校验账号状态"]
    F -->|账号不存在或禁用| G["返回认证失败"]
    G --> H["前端提示登录失败原因"]
    H --> B
    F -->|认证成功| I["生成 JWT 与用户信息"]
    I --> J["返回角色、令牌、基础资料"]
    J --> K["前端保存 Token"]
    K --> L["加载角色菜单与首页数据"]
    L --> M["进入系统首页"]
```

### 5.2 分布式采集活动图

```mermaid
flowchart TD
    A["管理员配置采集任务"] --> B["Quartz 到时触发"]
    B --> C["主节点读取任务配置"]
    C --> D["按关键词/城市拆分子任务"]
    D --> E["写入 RabbitMQ 队列"]
    E --> F["从节点抢占子任务"]
    F --> G["加载 Token 与代理资源"]
    G --> H["访问招聘站点接口"]
    H -->|失败| I["记录失败原因"]
    I --> J["重试或回收任务"]
    J --> E
    H -->|成功| K["解析岗位原始数据"]
    K --> L["本地初步清洗与去重"]
    L --> M["回传主服务或直接入库"]
    M --> N["更新采集状态与监控指标"]
    N --> O["任务完成/进入下一批次"]
```

### 5.3 数据处理与入库活动图

```mermaid
flowchart TD
    A["接收原始岗位数据"] --> B["字段标准化"]
    B --> C["HTML 标签清洗"]
    C --> D["薪资解析与换算"]
    D --> E["学历/经验字段映射"]
    E --> F["岗位技能关键词提取"]
    F --> G["生成去重指纹"]
    G --> H{"是否重复"}
    H -->|是| I["丢弃重复数据并记录日志"]
    H -->|否| J["进行字段完整性校验"]
    J --> K{"校验通过?"}
    K -->|否| L["写入异常日志表"]
    K -->|是| M["写入 MySQL 结构化表"]
    M --> N["刷新 Redis 热点缓存"]
    N --> O["同步触发统计分析更新"]
```

### 5.4 报告生成活动图

```mermaid
flowchart TD
    A["用户进入报告中心"] --> B["选择报告类型"]
    B --> C["设置城市/行业/专业/时间范围"]
    C --> D["提交生成请求"]
    D --> E["创建异步报告任务"]
    E --> F["统计分析服务聚合数据"]
    F --> G["生成图表数据集"]
    G --> H["调用 AI 摘要生成结论文本"]
    H --> I["组合报告结构化内容"]
    I --> J["保存报告记录"]
    J --> K["渲染 HTML 模板"]
    K --> L["导出 PDF 文件"]
    L --> M["更新任务状态为完成"]
    M --> N["前端轮询获取完成状态"]
    N --> O["查看详情/下载 PDF"]
```

### 5.5 AI 助手问答活动图

```mermaid
flowchart TD
    A["用户输入自然语言问题"] --> B["创建或复用会话"]
    B --> C["识别问题意图"]
    C --> D{"是否需要工具调用"}
    D -->|否| E["直接调用大模型生成回答"]
    D -->|是| F["选择分析/画像/推荐等工具"]
    F --> G["调用平台内部接口"]
    G --> H["获取岗位/分析/画像数据"]
    H --> I["将工具结果注入上下文"]
    I --> J["调用大模型生成增强回答"]
    E --> K["SSE 流式返回文本片段"]
    J --> K
    K --> L["保存问答记录"]
    L --> M["前端更新聊天界面与历史记录"]
```

### 5.6 岗位推荐活动图

```mermaid
flowchart TD
    A["用户完善个人画像"] --> B["读取用户技能/学历/偏好"]
    B --> C["查询岗位特征库"]
    C --> D["执行岗位筛选"]
    D --> E["进行匹配度计算"]
    E --> F["计算地理偏好与薪资偏好权重"]
    F --> G["输出推荐排序结果"]
    G --> H["分析技能缺口"]
    H --> I["生成职业路径建议"]
    I --> J["前端展示推荐岗位、分数、原因和建议"]
```

---

## 6. 流程图

### 6.1 报告自动生成系统流程图

```mermaid
flowchart LR
    A["接收生成请求"] --> B["校验用户权限与参数"]
    B --> C["创建任务记录"]
    C --> D["读取分析数据源"]
    D --> E["执行聚合统计"]
    E --> F["计算图表指标"]
    F --> G["生成文字结论"]
    G --> H["落库 biz_analysis_report"]
    H --> I["渲染报告模板"]
    I --> J["输出 PDF"]
    J --> K["更新任务状态"]
    K --> L["通知前端可下载"]
```

### 6.2 开放 API 调用流程图

```mermaid
flowchart LR
    A["外部系统发起请求"] --> B["进入 /api/v1/open/*"]
    B --> C["校验 X-API-Key"]
    C -->|失败| D["返回 401/403"]
    C -->|通过| E["限流校验"]
    E -->|超限| F["返回 429"]
    E -->|通过| G["参数校验"]
    G --> H["查询公开数据视图"]
    H --> I["执行脱敏处理"]
    I --> J["记录调用日志"]
    J --> K["返回标准 JSON 结果"]
```

### 6.3 后台监控预警流程图

```mermaid
flowchart TD
    A["系统持续采集运行指标"] --> B["汇总节点状态"]
    B --> C["汇总任务成功率"]
    C --> D["汇总异常日志数量"]
    D --> E["计算 CPU/内存/队列积压"]
    E --> F{"是否超阈值"}
    F -->|否| G["更新监控面板"]
    F -->|是| H["生成告警事件"]
    H --> I["写入告警日志"]
    I --> J["前端后台通知管理员"]
    J --> K["管理员进入任务/节点详情处理"]
```

---

## 7. 时序图

### 7.1 用户登录时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant V as Vue3 前端
    participant C as AuthController
    participant S as AuthService
    participant DB as MySQL
    participant J as JWT组件

    U->>V: 输入账号和密码并点击登录
    V->>C: POST /api/v1/auth/login
    C->>S: 调用登录认证服务
    S->>DB: 查询用户账号/角色/状态
    DB-->>S: 返回用户记录
    S->>S: 校验密码与状态
    S->>J: 生成 JWT Token
    J-->>S: 返回 token
    S-->>C: 返回用户信息+token+role
    C-->>V: 返回登录成功响应
    V-->>U: 跳转首页并加载菜单
```

### 7.2 报告生成时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant V as 报告中心页面
    participant RC as ReportController
    participant RS as ReportService
    participant AS as AnalysisService
    participant AI as LLM Summary Service
    participant PDF as PdfExportService
    participant DB as MySQL

    U->>V: 提交报告生成参数
    V->>RC: POST /api/v1/reports/generate
    RC->>RS: 创建异步报告任务
    RS->>DB: 保存任务记录
    RS->>AS: 获取分析统计数据
    AS-->>RS: 返回图表与聚合结果
    RS->>AI: 生成摘要与结论文本
    AI-->>RS: 返回报告摘要
    RS->>DB: 保存报告内容
    RS->>PDF: 渲染并导出 PDF
    PDF-->>RS: 返回文件地址/流
    RS->>DB: 更新状态为 COMPLETED
    V->>RC: GET /api/v1/reports/{taskId}/status
    RC-->>V: 返回完成状态与 reportId
    V-->>U: 展示报告详情/下载入口
```

### 7.3 AI 助手问答时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant V as AI 页面
    participant AC as AiController
    participant AS as AiService
    participant Tool as Agent Tool Router
    participant API as 平台内部接口
    participant LLM as 大模型服务
    participant DB as 会话存储

    U->>V: 输入问题
    V->>AC: POST /api/v1/ai/chat
    AC->>AS: 创建流式响应
    AS->>DB: 读取历史会话上下文
    AS->>Tool: 判断是否需要工具调用
    Tool->>API: 调用画像/分析/推荐接口
    API-->>Tool: 返回结构化数据
    Tool-->>AS: 返回工具结果
    AS->>LLM: 发送问题+上下文+工具结果
    LLM-->>AS: 流式返回回答片段
    AS-->>V: SSE message/done 事件
    AS->>DB: 保存本轮会话消息
    V-->>U: 实时展示回答内容
```

### 7.4 推荐计算时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant V as 推荐页面
    participant RC as RecommendController
    participant RS as RecommendService
    participant PS as ProfileService
    participant JS as JobService
    participant FS as FastAPI 算法服务
    participant DB as MySQL

    U->>V: 请求个性化推荐
    V->>RC: POST /api/v1/recommend/jobs
    RC->>RS: 执行推荐任务
    RS->>PS: 获取用户画像
    PS->>DB: 查询画像与技能标签
    DB-->>PS: 返回画像数据
    RS->>JS: 获取候选岗位集合
    JS->>DB: 查询岗位特征
    DB-->>JS: 返回候选岗位
    RS->>FS: 发送画像特征与岗位特征
    FS-->>RS: 返回匹配分和推荐原因
    RS-->>RC: 推荐结果列表
    RC-->>V: 返回推荐岗位、分数、技能缺口
    V-->>U: 渲染推荐页面
```

---

## 8. 各核心模块详细设计图

### 8.1 分布式数据采集模块箱型图

```mermaid
flowchart TB
    X["分布式数据采集模块"]
    X --> X1["采集任务配置子模块"]
    X --> X2["定时调度子模块"]
    X --> X3["任务拆分与分发子模块"]
    X --> X4["从节点执行子模块"]
    X --> X5["反爬资源管理子模块"]
    X --> X6["节点监控子模块"]
    X --> X7["异常重试子模块"]

    X5 --> X51["Token 池"]
    X5 --> X52["代理 IP 池"]
    X6 --> X61["心跳检测"]
    X6 --> X62["CPU/内存采集"]
    X7 --> X71["失败重试"]
    X7 --> X72["任务回收重分配"]
```

### 8.2 数据处理与存储模块箱型图

```mermaid
flowchart TB
    Y["数据处理与存储模块"]
    Y --> Y1["原始数据接收"]
    Y --> Y2["清洗规则处理"]
    Y --> Y3["去重与指纹识别"]
    Y --> Y4["字段校验"]
    Y --> Y5["结构化入库"]
    Y --> Y6["热点缓存更新"]
    Y --> Y7["异常日志管理"]

    Y5 --> Y51["岗位表"]
    Y5 --> Y52["分析报告表"]
    Y5 --> Y53["用户画像表"]
    Y5 --> Y54["会话记录表"]
```

### 8.3 报告分析系统箱型图

```mermaid
flowchart TB
    R["报告分析系统"]
    R --> R1["岗位数量分析"]
    R --> R2["城市热度分析"]
    R --> R3["行业分布分析"]
    R --> R4["薪资区间分析"]
    R --> R5["技能需求分析"]
    R --> R6["供需趋势分析"]
    R --> R7["教学改革支撑分析"]
```

### 8.4 报告自动生成系统箱型图

```mermaid
flowchart TB
    G["报告自动生成系统"]
    G --> G1["任务创建"]
    G --> G2["任务状态管理"]
    G --> G3["数据聚合"]
    G --> G4["AI 摘要生成"]
    G --> G5["模板渲染"]
    G --> G6["PDF 导出"]
    G --> G7["定时计划管理"]
    G --> G8["公开报告管理"]
```

### 8.5 就业岗位推送系统箱型图

```mermaid
flowchart TB
    T["就业岗位推送系统"]
    T --> T1["用户画像特征提取"]
    T --> T2["候选岗位筛选"]
    T --> T3["相似度计算"]
    T --> T4["推荐排序"]
    T --> T5["技能缺口分析"]
    T --> T6["职业路径规划"]
    T --> T7["推荐原因生成"]
```

### 8.6 AI 助手分析模块箱型图

```mermaid
flowchart TB
    A["AI 助手分析模块"]
    A --> A1["会话管理"]
    A --> A2["Prompt 组装"]
    A --> A3["工具路由"]
    A --> A4["平台数据增强"]
    A --> A5["大模型调用"]
    A --> A6["SSE 流式输出"]
    A --> A7["历史记录归档"]
    A --> A8["文件导入画像提取"]
```

### 8.7 开放 API 模块箱型图

```mermaid
flowchart TB
    O["开放 API 模块"]
    O --> O1["API Key 鉴权"]
    O --> O2["请求参数校验"]
    O --> O3["限流控制"]
    O --> O4["公开岗位接口"]
    O --> O5["公开分析接口"]
    O --> O6["公开报告接口"]
    O --> O7["数据脱敏处理"]
    O --> O8["调用日志记录"]
```

### 8.8 平台管理与监控模块箱型图

```mermaid
flowchart TB
    M["平台管理与监控模块"]
    M --> M1["用户与角色管理"]
    M --> M2["采集任务管理"]
    M --> M3["节点管理"]
    M --> M4["监控大屏"]
    M --> M5["异常预警"]
    M --> M6["报告任务管理"]
    M --> M7["API 调用日志管理"]
    M --> M8["系统审计日志管理"]
```

---

## 9. 数据库关系图参考

### 9.1 核心业务数据关系图

```mermaid
erDiagram
    SYS_USER ||--o{ USER_PROFILE : has
    SYS_USER ||--o{ AI_CONVERSATION : owns
    AI_CONVERSATION ||--o{ AI_MESSAGE : contains
    SYS_USER ||--o{ ANALYSIS_REPORT : creates
    SYS_USER ||--o{ RECOMMEND_RESULT : receives
    JOB_INFO ||--o{ RECOMMEND_RESULT : referenced_by
    CRAWL_TASK ||--o{ CRAWL_TASK_LOG : generates
    CRAWL_NODE ||--o{ CRAWL_TASK_LOG : executes
    API_CLIENT ||--o{ API_CALL_LOG : invokes

    SYS_USER {
        bigint id
        string username
        string password_hash
        string role
        string status
    }
    USER_PROFILE {
        bigint id
        bigint user_id
        string education
        string target_job
        string city_preference
    }
    JOB_INFO {
        bigint id
        string job_title
        string company_name
        string city
        string salary_range
    }
    ANALYSIS_REPORT {
        bigint id
        bigint user_id
        string report_type
        string status
        datetime created_at
    }
```

---

## 10. 接口分组关系图

### 10.1 接口设计总图

```mermaid
flowchart TB
    API["统一接口前缀 /api/v1"]
    API --> A["/auth"]
    API --> B["/profile"]
    API --> C["/analysis"]
    API --> D["/jobs"]
    API --> E["/recommend"]
    API --> F["/ai"]
    API --> G["/reports"]
    API --> H["/open"]
    API --> I["/admin"]

    A --> A1["登录/注册/刷新/个人资料"]
    B --> B1["画像维护/技能维护"]
    C --> C1["概览/趋势/预测/热力图/深度分析"]
    D --> D1["列表/详情/统计/搜索"]
    E --> E1["岗位推荐/技能建议/职业路径"]
    F --> F1["聊天/Agent/会话/文件导入"]
    G --> G1["生成/状态/下钻/PDF/计划任务"]
    H --> H1["公开岗位/公开分析/公开报告"]
    I --> I1["用户/任务/节点/API/日志/监控"]
```

---

## 11. 可直接放入“详细设计”章节的小图建议

### 11.1 报告中心页面组件关系图

```mermaid
flowchart LR
    P["报告中心页面"] --> P1["报告筛选栏"]
    P["报告中心页面"] --> P2["报告列表"]
    P["报告中心页面"] --> P3["任务状态面板"]
    P["报告中心页面"] --> P4["报告详情弹层"]
    P["报告中心页面"] --> P5["PDF 导出按钮"]
    P["报告中心页面"] --> P6["定时计划管理区"]
```

### 11.2 AI 页面组件关系图

```mermaid
flowchart LR
    A["AI 助手页面"] --> A1["历史会话列表"]
    A["AI 助手页面"] --> A2["当前会话消息区"]
    A["AI 助手页面"] --> A3["问题输入框"]
    A["AI 助手页面"] --> A4["文件导入区"]
    A["AI 助手页面"] --> A5["配额信息区"]
    A2 --> A21["用户消息"]
    A2 --> A22["AI 回答消息"]
    A2 --> A23["工具调用结果折叠区"]
```

---

## 12. 建议你在 Word 中如何对应放图

### 第 2 章 架构设计

- 放 `1.1 平台总体架构图`
- 放 `1.2 平台功能模块箱型图`
- 放 `2.1 四层逻辑视图`
- 放 `3.1 容器部署图`

### 第 5 章 关键用例视图

- 放 `4.1 平台总体用例图`
- 放 `4.2 报告生成系统用例图`
- 放 `4.3 AI 助手模块用例图`
- 放 `4.4 后台管理模块用例图`

### 第 7 章 逻辑视图

- 放 `2.1 四层逻辑视图`
- 放 `2.2 数据流转逻辑图`

### 第 8 章 部署视图

- 放 `3.1 容器部署图`
- 放 `3.2 分布式采集部署图`

### 第 9 章 接口设计

- 放 `10.1 接口设计总图`
- 放 `6.2 开放 API 调用流程图`

### 第 12 章 详细设计

- 登录模块：`5.1 用户登录活动图`、`7.1 用户登录时序图`
- 采集模块：`5.2 分布式采集活动图`、`8.1 分布式数据采集模块箱型图`
- 数据处理模块：`5.3 数据处理与入库活动图`、`8.2 数据处理与存储模块箱型图`
- 报告模块：`5.4 报告生成活动图`、`7.2 报告生成时序图`、`8.4 报告自动生成系统箱型图`
- 推荐模块：`5.6 岗位推荐活动图`、`7.4 推荐计算时序图`、`8.5 就业岗位推送系统箱型图`
- AI 模块：`5.5 AI 助手问答活动图`、`7.3 AI 助手问答时序图`、`8.6 AI 助手分析模块箱型图`
- 管理模块：`6.3 后台监控预警流程图`、`8.8 平台管理与监控模块箱型图`

