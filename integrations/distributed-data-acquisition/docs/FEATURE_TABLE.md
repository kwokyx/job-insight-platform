# 前后端功能页面表格

## 概述

本表格详细描述了分布式数据采集模块的前端页面功能点、对应的后端接口，以及前后端交互的数据格式。前端采用 Vue 3 + Vite + ECharts 技术栈，后端采用 FastAPI + MySQL + RabbitMQ 架构。

---

## 任务管理模块

### 页面：任务列表页 (`TaskList.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **任务列表展示** | `el-table` | `/api/tasks` | GET | 分页查询参数 | 显示所有采集任务，支持分页、筛选、排序 |
| **任务状态标签** | `el-tag` | - | - | - | 不同状态使用不同颜色标签：待执行(灰色)、运行中(蓝色)、完成(绿色)、失败(红色) |
| **任务操作按钮** | `el-button-group` | 多个接口 | 多种 | - | 启动、暂停、编辑、删除、查看详情 |
| **创建新任务** | `el-dialog` + 表单 | `/api/tasks` | POST | `TaskCreate` | 弹出对话框，填写任务参数 |
| **批量操作** | `el-checkbox` | `/api/tasks/batch` | POST | 任务ID数组 | 批量启动、暂停、删除任务 |
| **实时刷新** | 定时器 | `/api/tasks` | GET | - | 每30秒自动刷新任务状态 |
| **导出任务数据** | `el-button` | `/api/tasks/{id}/export` | GET | CSV/Excel | 导出任务详情和采集数据 |

### 页面：任务详情页 (`TaskDetail.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **任务基本信息** | `el-descriptions` | `/api/tasks/{id}` | GET | `TaskResponse` | 显示任务名称、状态、创建时间等 |
| **任务统计图表** | `ECharts` | `/api/tasks/{id}/stats` | GET | 统计JSON | 环形图显示分片状态分布，折线图显示采集进度 |
| **分片列表** | `el-table` | `/api/tasks/{id}/shards` | GET | 分页数据 | 显示任务所有分片，支持状态筛选 |
| **分片详情查看** | `el-drawer` | `/api/shard/{id}` | GET | `TaskShardResponse` | 侧边栏显示分片详情和采集日志 |
| **实时监控** | `WebSocket` | `/ws/task/{id}` | WebSocket | 实时消息 | 实时接收分片状态更新和数据采集进度 |

### 页面：任务创建/编辑页 (`TaskForm.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **任务参数表单** | `el-form` | `/api/tasks` | POST/PUT | `TaskCreate/TaskUpdate` | 表单验证，支持关键词、城市、分类等多选 |
| **关键词管理** | `el-select` + `el-tag` | - | - | 字符串数组 | 支持添加、删除关键词，关键词去重 |
| **城市选择** | `el-cascader` | `/api/config/cities` | GET | 城市树数据 | 级联选择器，支持按省份、城市选择 |
| **分类选择** | `el-tree-select` | `/api/config/categories` | GET | 分类树数据 | 树形选择器，选择职位分类 |
| **调度设置** | `el-radio-group` | - | - | - | 立即执行、定时执行（Cron表达式） |
| **预览任务规模** | `el-alert` | `/api/tasks/preview` | POST | 预估数据 | 根据参数预估分片数和预计采集时间 |

---

## 节点管理模块

### 页面：节点监控页 (`WorkerMonitor.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **节点状态卡片** | `el-row` + `el-col` | `/api/workers` | GET | `WorkerResponse[]` | 卡片式展示节点状态，不同状态不同颜色 |
| **节点性能图表** | `ECharts`仪表盘 | `/api/workers/{id}/stats` | GET | 性能数据 | 仪表盘显示CPU、内存使用率，折线图显示历史趋势 |
| **节点地图分布** | 地图组件 | `/api/workers/map` | GET | 地理坐标数据 | 在地图上显示节点分布，点击查看详情 |
| **节点操作菜单** | `el-dropdown` | `/api/workers/{id}` | DELETE | - | 重启节点、下线节点、查看日志 |
| **实时心跳显示** | `el-badge` | WebSocket | WebSocket | 心跳消息 | 实时显示节点最后心跳时间，超时报警 |
| **节点负载排行** | `el-table` | `/api/workers/stats` | GET | 负载数据 | 按CPU、内存、任务数排序显示节点负载 |

### 页面：节点详情页 (`WorkerDetail.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **节点基本信息** | `el-descriptions` | `/api/workers/{id}` | GET | `WorkerResponse` | 显示节点ID、IP、状态、上线时间等 |
| **实时性能监控** | `ECharts`实时图表 | `/api/workers/{id}/metrics` | WebSocket | 实时指标 | 实时折线图显示CPU、内存、网络IO变化 |
| **当前任务列表** | `el-table` | `/api/workers/{id}/shards` | GET | 分页数据 | 显示节点当前执行的任务分片 |
| **历史任务统计** | `ECharts`柱状图 | `/api/workers/{id}/history` | GET | 历史数据 | 显示最近7天任务完成情况 |
| **节点日志查看** | `el-card` + 日志组件 | `/api/workers/{id}/logs` | GET | 日志数据 | 分页查看节点运行日志，支持级别过滤 |

---

## 监控统计模块

### 页面：监控仪表盘 (`Dashboard.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **核心指标卡片** | `el-card` + 数字动画 | `/api/monitor/dashboard` | GET | `DashboardStats` | 显示总任务数、在线节点、今日采集量等核心指标 |
| **采集趋势图表** | `ECharts`折线图 | `/api/monitor/task-trend` | GET | 趋势数据 | 显示最近7天/30天采集数据量趋势 |
| **节点状态分布** | `ECharts`饼图 | `/api/monitor/worker-dist` | GET | 分布数据 | 显示节点在线/离线比例，负载分布 |
| **任务状态分布** | `ECharts`环形图 | `/api/monitor/task-dist` | GET | 分布数据 | 显示任务状态分布，失败任务高亮 |
| **实时告警列表** | `el-table`滚动 | `/api/monitor/alerts` | WebSocket | 告警数据 | 实时显示系统告警，支持告警级别筛选 |
| **地理热力图** | 地图热力组件 | `/api/monitor/geo-heat` | GET | 地理热力数据 | 显示采集数据地理分布热力图 |

### 页面：数据统计页 (`DataStatistics.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **数据总量统计** | 数字卡片 | `/api/monitor/data-stats` | GET | 总量数据 | 显示总数据量、今日新增、去重率等 |
| **城市分布图表** | `ECharts`地图 | `/api/monitor/city-dist` | GET | 城市分布数据 | 在地图上显示各城市采集数据量 |
| **薪资分布分析** | `ECharts`柱状图 | `/api/monitor/salary-dist` | GET | 薪资分布数据 | 显示不同薪资区间的职位数量 |
| **行业分类分析** | `ECharts`旭日图 | `/api/monitor/industry-dist` | GET | 行业数据 | 旭日图显示行业、职位分类层级分布 |
| **热门技能词云** | 词云组件 | `/api/monitor/skill-cloud` | GET | 技能词频数据 | 词云显示职位要求中的热门技能 |
| **数据质量指标** | `el-progress` | `/api/monitor/data-quality` | GET | 质量指标 | 显示数据完整性、准确性、及时性评分 |

### 页面：实时监控大屏 (`MonitorScreen.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **全屏显示** | 全屏API | - | - | - | 适配大屏显示，自动全屏，支持分辨率适配 |
| **实时数据流** | 轮播组件 | WebSocket | WebSocket | 实时数据 | 轮播显示最新采集的职位数据 |
| **节点状态网格** | 网格布局 | `/api/workers` | GET | 节点数据 | 网格显示所有节点状态，颜色表示健康度 |
| **任务进度环** | `ECharts`进度环 | `/api/tasks/running` | GET | 进度数据 | 环形进度条显示运行中任务完成比例 |
| **系统健康度** | 仪表盘组件 | `/api/monitor/health` | GET | 健康度数据 | 显示数据库、Redis、RabbitMQ健康状态 |
| **告警通知中心** | 通知组件 | WebSocket | WebSocket | 告警通知 | 实时弹出系统告警通知，支持声音提示 |

---

## 日志查询模块

### 页面：日志查询页 (`LogQuery.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **多条件筛选** | `el-form`高级查询 | `/api/logs` | GET | 查询参数 | 支持时间范围、日志级别、任务ID、节点ID等多条件组合查询 |
| **日志列表展示** | `el-table`虚拟滚动 | `/api/logs` | GET | 分页数据 | 支持虚拟滚动，处理大量日志数据 |
| **日志级别过滤** | `el-checkbox-group` | - | - | - | 按DEBUG/INFO/WARN/ERROR级别过滤 |
| **日志详情查看** | `el-drawer` | `/api/logs/{id}` | GET | 日志详情 | 侧边栏显示完整日志详情，支持JSON格式化 |
| **日志导出功能** | `el-button` | `/api/logs/export` | POST | CSV/Excel | 导出查询结果到文件 |
| **日志统计图表** | `ECharts` | `/api/logs/stats` | GET | 统计数据 | 显示日志级别分布、时间分布等统计图表 |

### 页面：异常日志页 (`ErrorLog.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **异常日志筛选** | `el-select` | `/api/logs/errors` | GET | 异常日志 | 自动筛选ERROR级别日志，支持按异常类型分类 |
| **异常趋势分析** | `ECharts`折线图 | `/api/logs/error-trend` | GET | 趋势数据 | 显示异常数量时间趋势，帮助定位问题时段 |
| **异常详情分析** | `el-collapse` | `/api/logs/{id}/analysis` | GET | 分析结果 | 展开显示异常堆栈、上下文信息、可能原因 |
| **异常关联任务** | `el-link` | `/api/tasks/{id}` | GET | 任务信息 | 点击跳转到关联的任务详情页 |
| **异常解决状态** | `el-tag` | `/api/logs/{id}/status` | PUT | 状态更新 | 标记异常为已解决、待处理、忽略等状态 |
| **异常报警设置** | `el-switch` | `/api/config/alerts` | PUT | 报警配置 | 配置异常报警规则和接收人 |

---

## 系统配置模块

### 页面：系统配置页 (`SystemConfig.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **配置项管理** | `el-table`可编辑 | `/api/config` | GET/PUT | 配置列表 | 表格显示所有配置项，支持行内编辑 |
| **配置分类导航** | `el-menu` | - | - | - | 左侧菜单按功能分类：爬虫配置、调度配置、代理配置等 |
| **配置导入导出** | `el-upload` | `/api/config/import-export` | POST/GET | JSON文件 | 导入导出系统配置，便于迁移和备份 |
| **配置版本管理** | `el-timeline` | `/api/config/versions` | GET | 版本历史 | 显示配置变更历史，支持版本对比和回滚 |
| **配置生效测试** | `el-button` | `/api/config/test` | POST | 测试结果 | 测试配置项是否生效，如代理IP测试、Token验证 |
| **配置模板管理** | `el-select` | `/api/config/templates` | GET | 模板列表 | 预置常用配置模板，一键应用 |

### 页面：代理IP池管理 (`ProxyPool.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **代理IP列表** | `el-table` | `/api/proxy` | GET | 代理列表 | 显示代理IP、协议、状态、成功率等信息 |
| **代理IP测试** | `el-button` | `/api/proxy/test` | POST | 测试结果 | 批量测试代理IP可用性和响应速度 |
| **代理IP添加** | `el-dialog` | `/api/proxy` | POST | 代理信息 | 手动添加代理IP，支持批量导入 |
| **代理IP统计** | `ECharts` | `/api/proxy/stats` | GET | 统计图表 | 显示代理IP成功率分布、响应时间分布 |
| **代理轮询策略** | `el-radio-group` | `/api/config/proxy-strategy` | PUT | 策略配置 | 配置代理使用策略：轮询、随机、按成功率 |
| **代理自动更新** | `el-switch` | `/api/config/proxy-auto` | PUT | 自动更新配置 | 配置代理IP自动获取和更新 |

### 页面：Token管理页 (`TokenManager.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **Token池展示** | `el-table` | `/api/config/tokens` | GET | Token列表 | 显示各平台Token、状态、有效期、使用次数 |
| **Token有效性检测** | `el-button` | `/api/config/tokens/test` | POST | 检测结果 | 批量检测Token是否有效，自动标记失效Token |
| **Token自动更新** | `el-switch` | `/api/config/tokens/auto` | PUT | 自动更新配置 | 配置Token自动更新策略和更新源 |
| **Token使用统计** | `ECharts` | `/api/config/tokens/stats` | GET | 使用统计 | 显示Token使用频率、成功率统计 |
| **Token添加/编辑** | `el-dialog` | `/api/config/tokens` | POST/PUT | Token信息 | 手动添加或编辑Token信息 |
| **Token告警设置** | `el-form` | `/api/config/tokens/alerts` | PUT | 告警配置 | 设置Token失效告警阈值和通知方式 |

---

## 数据管理模块

### 页面：数据查询页 (`DataQuery.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **高级查询表单** | `el-form`复杂查询 | `/api/data/query` | POST | 查询条件 | 支持多字段组合查询：城市、薪资、经验、学历等 |
| **查询结果列表** | `el-table`虚拟滚动 | `/api/data/query` | POST | 分页数据 | 显示查询结果，支持列自定义和排序 |
| **数据导出功能** | `el-dropdown` | `/api/data/export` | POST | 文件流 | 支持导出为CSV、Excel、JSON格式 |
| **查询条件保存** | `el-button` | `/api/data/queries/save` | POST | 查询模板 | 保存常用查询条件为模板，方便下次使用 |
| **数据去重操作** | `el-button` | `/api/data/deduplicate` | POST | 去重结果 | 对查询结果进行去重，基于职位ID或自定义规则 |
| **数据质量检查** | `el-popover` | `/api/data/quality-check` | POST | 质量报告 | 检查数据完整性、一致性，生成质量报告 |

### 页面：数据可视化 (`DataVisualization.vue`)

| 功能点 | 前端组件 | 后端接口 | 请求方法 | 数据格式 | 说明 |
|--------|----------|----------|----------|----------|------|
| **多维分析图表** | `ECharts`组合图表 | `/api/data/analysis` | POST | 分析结果 | 支持薪资-经验-城市等多维交叉分析 |
| **词频分析图** | 词云 + 柱状图 | `/api/data/word-freq` | POST | 词频数据 | 分析职位描述中的技能词频，生成词云和TOP N柱状图 |
| **时间趋势分析** | `ECharts`时间轴 | `/api/data/time-trend` | POST | 时间序列数据 | 分析职位发布量、薪资变化的时间趋势 |
| **地理分布地图** | 地图组件 | `/api/data/geo-dist` | POST | 地理数据 | 在地图上可视化职位分布和薪资水平 |
| **公司分析图表** | `ECharts`旭日图 | `/api/data/company-analysis` | POST | 公司数据 | 分析公司规模、融资阶段与职位数量关系 |
| **图表导出分享** | `el-button` | - | - | 图片/PDF | 导出图表为图片或PDF，生成分析报告 |

---

## 前后端数据交互规范

### 1. 分页查询规范
```javascript
// 请求参数
{
  page: 1,      // 页码，从1开始
  size: 20,     // 每页大小
  sort: "created_at:desc", // 排序字段
  filters: {    // 过滤条件
    status: 1,
    channel: "zhaopin"
  }
}

// 响应格式
{
  code: 200,
  message: "success",
  data: {
    items: [],      // 数据列表
    total: 100,     // 总记录数
    page: 1,        // 当前页码
    size: 20,       // 每页大小
    pages: 5        // 总页数
  }
}
```

### 2. 实时数据推送（WebSocket）
```javascript
// 连接地址
ws://localhost:8000/ws/{channel}

// 消息格式
{
  type: "HEARTBEAT",  // 消息类型：HEARTBEAT、TASK_UPDATE、DATA_UPDATE、ALERT
  timestamp: "2026-04-16T10:30:00Z",
  data: {}            // 消息数据
}

// 订阅主题
client.subscribe("/topic/task-updates")
client.subscribe("/topic/node-status")
client.subscribe("/topic/data-stream")
```

### 3. 文件上传/下载
```javascript
// 文件上传
const formData = new FormData()
formData.append('file', file)
formData.append('type', 'proxy_list')

// 文件下载
window.location.href = `/api/export/${taskId}?format=csv`
```

### 4. 错误处理
```javascript
// 错误响应
{
  code: 40001,        // 错误码
  message: "参数验证失败",
  details: {          // 错误详情
    field: "keywords",
    error: "不能为空"
  },
  timestamp: "2026-04-16T10:30:00Z"
}

// 前端统一错误处理
try {
  await api.getData()
} catch (error) {
  if (error.code === 40001) {
    this.$message.error('参数错误：' + error.details.error)
  } else if (error.code === 50001) {
    this.$message.error('系统错误，请稍后重试')
  }
}
```

---

## 前端组件库规范

### 1. 通用组件
- `DataTable`: 增强表格组件，支持虚拟滚动、列配置、行操作
- `SearchForm`: 高级查询表单，支持动态字段、条件组合
- `ChartContainer`: 图表容器，统一图表样式、工具栏
- `StatusBadge`: 状态徽章，统一任务、节点状态显示
- `Pagination`: 分页组件，与后端分页规范对接

### 2. 业务组件
- `TaskCard`: 任务卡片，显示任务基本信息和操作
- `WorkerNodeCard`: 节点卡片，显示节点状态和性能指标
- `LogViewer`: 日志查看器，支持级别过滤、关键字高亮
- `ConfigEditor`: 配置编辑器，支持JSON、YAML格式编辑
- `DataFilter`: 数据筛选器，提供多维度筛选条件

### 3. 布局组件
- `DashboardLayout`: 仪表盘布局，支持拖拽调整、组件配置
- `MonitorLayout`: 监控大屏布局，全屏适配、网格系统
- `FormLayout`: 表单布局，响应式适配、步骤引导

---

## 性能优化建议

### 1. 前端优化
- 虚拟滚动：大数据列表使用虚拟滚动减少DOM节点
- 图表懒加载：非可视区域图表延迟加载
- 请求缓存：频繁请求的数据添加本地缓存
- 组件懒加载：路由级和组件级懒加载
- WebSocket重连：自动重连机制，心跳保活

### 2. 后端优化
- 查询优化：数据库索引优化，分页查询优化
- 缓存策略：Redis缓存热点数据，减少数据库压力
- 异步处理：耗时操作异步化，消息队列解耦
- 连接池：数据库、Redis、RabbitMQ连接池管理
- 监控告警：系统性能监控，自动扩缩容

---

## 部署集成说明

### 1. 前端部署
```bash
# 构建生产版本
npm run build

# 输出到dist目录
# 可部署到Nginx、CDN或现有Vue项目
```

### 2. 后端集成
```javascript
// 现有Vue项目集成
import { createCrawlerPlugin } from '@crawler/sdk'

const app = createApp(App)
app.use(createCrawlerPlugin({
  baseURL: 'http://localhost:8000/api',
  wsURL: 'ws://localhost:8000/ws'
}))

// 路由集成
{
  path: '/crawler',
  component: Layout,
  children: [
    { path: 'tasks', component: TaskList },
    { path: 'monitor', component: Dashboard },
    // ...其他路由
  ]
}
```

### 3. 独立部署
```bash
# Docker Compose一键部署
cd deploy
docker-compose up -d

# 访问地址
# 前端：http://localhost:8080
# 后端API：http://localhost:8000/api
# RabbitMQ管理：http://localhost:15672
```

---

*文档版本：v1.0*  
*最后更新：2026-04-16*