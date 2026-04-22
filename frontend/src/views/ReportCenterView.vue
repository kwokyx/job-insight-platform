<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ReportDetailPanel from '../components/report/ReportDetailPanel.vue'
import ReportSchedulePanel from '../components/report/ReportSchedulePanel.vue'
import ReportPublicationPanel from '../components/report/ReportPublicationPanel.vue'
import {
  createReport,
  deleteReport,
  exportReportFormat,
  fetchPublicReports,
  fetchReportCenterMeta,
  fetchReportDownloadMeta,
  fetchReportDrill,
  fetchReports,
  fetchReportSchedules,
  fetchReportStatus,
  normalizeError,
  openReportPdf,
  submitReportReview
} from '../api'
import { useAuthStore } from '../store/auth'
import { getRoleLabel } from '../utils/role'
import {
  BookOpen,
  ChevronLeft,
  FileText,
  Globe,
  LockKeyhole,
  PanelLeftClose,
  PanelLeftOpen,
  RefreshCw,
  Send,
  Shield,
  Sparkles,
  Trash2,
  UserRound
} from 'lucide-vue-next'

const authStore = useAuthStore()

const publicReports = ref([])
const privateReports = ref([])
const schedules = ref([])
const selectedReport = ref(null)
const selectedTask = ref(null)
const activeSection = ref('generate')
// 列表栏折叠：选中报告后自动折叠，给详情让出空间
const listCollapsed = ref(false)
const reportMeta = ref(buildLocalMeta(authStore.user?.roleType ?? 0))
const loading = ref(true)
const detailLoading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const success = ref('')
const exportFormat = ref('pdf')
const autoReportName = ref('')

const generateForm = ref({
  reportType: reportMeta.value.defaultReportType,
  reportName: reportMeta.value.defaultReportName
})
autoReportName.value = generateForm.value.reportName

const canManageReports = computed(() => authStore.isLoggedIn)
const currentRoleType = computed(() => authStore.user?.roleType ?? 0)
const currentRoleLabel = computed(() => getRoleLabel(currentRoleType.value))
const isAdmin = computed(() => currentRoleType.value === 1)
const currentReportTypes = computed(() => reportMeta.value?.reportTypes || [])
const currentReportTypeConfig = computed(
  () => currentReportTypes.value.find((item) => item.code === generateForm.value.reportType) || currentReportTypes.value[0] || null
)

const sidebarGroups = computed(() => {
  const groups = [
    { key: 'gen', label: '生成', items: [{ key: 'generate', label: '角色化生成' }] }
  ]
  const libraryItems = []
  if (canManageReports.value) {
    libraryItems.push({ key: 'private', label: isAdmin.value ? '全站私有' : '我的报告', count: privateReports.value.length })
  }
  libraryItems.push({ key: 'public', label: '公开报告', count: publicReports.value.length })
  if (isAdmin.value) libraryItems.push({ key: 'review', label: '待审核' })
  groups.push({ key: 'lib', label: '报告库', items: libraryItems })
  if (canManageReports.value) {
    groups.push({ key: 'tool', label: '工具', items: [{ key: 'schedule', label: '调度计划', count: schedules.value.length }] })
  }
  return groups
})

const allSectionKeys = computed(() => sidebarGroups.value.flatMap((g) => g.items.map((i) => i.key)))

watch(allSectionKeys, (keys) => {
  if (!keys.includes(activeSection.value)) activeSection.value = keys[0] || 'generate'
}, { immediate: true })

watch(activeSection, () => {
  selectedReport.value = null
  listCollapsed.value = false
})

// 从空态首次进入详情时自动折叠列表；用户手动展开后切换报告不再反复折叠
watch(selectedReport, (now, prev) => {
  if (now && !prev) listCollapsed.value = true
  if (!now) listCollapsed.value = false
})

const latestTaskSummary = computed(() => {
  if (!selectedTask.value) return []
  return [
    { label: '任务状态', value: taskStatusLabel(selectedTask.value.status) },
    { label: '生成进度', value: `${selectedTask.value.progress ?? 0}%` },
    { label: '开始时间', value: formatDateTime(selectedTask.value.startedAt) },
    { label: '完成时间', value: formatDateTime(selectedTask.value.completedAt) }
  ]
})

watch(() => authStore.user?.roleType, (roleType) => {
  if (!authStore.isLoggedIn) {
    applyMeta(buildLocalMeta(roleType ?? 0))
  }
})

watch(() => generateForm.value.reportType, (nextType, prevType) => {
  if (!nextType) return
  const nextConfig = currentReportTypes.value.find((item) => item.code === nextType)
  const prevConfig = currentReportTypes.value.find((item) => item.code === prevType)
  const currentName = generateForm.value.reportName?.trim() || ''
  if (!currentName || currentName === autoReportName.value || currentName === prevConfig?.defaultName) {
    generateForm.value.reportName = nextConfig?.defaultName || ''
    autoReportName.value = generateForm.value.reportName
  }
})

// 与 backend/ReportGenerationService.reportProfile + reportTypesForRole 保持一致。
// 这里只在未登录 / meta 加载失败时作为兜底，文案、顺序、默认类型均与后端对齐，避免"登录前后入口数量/文案不同"。
function buildLocalMeta(roleType) {
  const roleLabel = getRoleLabel(roleType)
  if (roleType === 1) {
    return {
      roleType, roleLabel,
      moduleTitle: '运营分析工作台',
      moduleDescription: '管理员入口优先突出平台运营分析、供需结构和增长抓手，帮助快速判断资源投向。',
      defaultReportType: 'OPERATIONS',
      defaultReportName: '平台运营分析报告',
      privateListScope: '可查看全站私有报告',
      publicListScope: '公开报告对所有登录或未登录用户可见',
      reportTypes: [
        { code: 'OPERATIONS', label: '平台运营分析', defaultName: '平台运营分析报告', description: '聚焦用户分层、内容供给、转化抓手与运营优先级。', templateDescription: '适合管理员快速判断哪些用户群体、岗位赛道和技能缺口最值得投入运营资源。', entryHint: '优先看用户匹配度、头部赛道和高频缺口。' },
        { code: 'SUPPLY_DEMAND', label: '平台供需分析', defaultName: '平台供需分析报告', description: '聚焦岗位需求、能力缺口与供给侧对齐情况。', templateDescription: '适合管理员判断平台招聘需求和用户能力之间的结构性矛盾。', entryHint: '优先看高频岗位赛道、缺口技能和对应补强动作。' },
        { code: 'INDUSTRY', label: '行业治理报告', defaultName: '院校治理行业分析报告', description: '聚焦行业景气度、区域对比、岗位族变化和治理决策线索。', templateDescription: '适合管理端跟踪重点行业变化，为专业建设、资源配置和公开报告提供依据。', entryHint: '优先看趋势方向、样本置信度和重点行业。' },
        { code: 'COMPREHENSIVE', label: '平台综合报告', defaultName: '平台综合分析报告', description: '整合关键图表、对比项和行动建议，适合做阶段总览。', templateDescription: '适合当前角色快速获取一份覆盖核心判断与动作建议的综合报告。', entryHint: '适合作为阶段复盘和多维观察入口。' }
      ]
    }
  }
  if (roleType === 2) {
    return {
      roleType, roleLabel,
      moduleTitle: '教学支持工作台',
      moduleDescription: '教师入口优先突出供需分析、教学建议和能力缺口观察，帮助把岗位需求映射到课程与辅导。',
      defaultReportType: 'SUPPLY_DEMAND',
      defaultReportName: '班级供需分析报告',
      privateListScope: '仅查看本人生成的私有报告',
      publicListScope: '公开报告对所有登录或未登录用户可见',
      reportTypes: [
        { code: 'SUPPLY_DEMAND', label: '供需分析报告', defaultName: '班级供需分析报告', description: '聚焦岗位需求、能力缺口与供给侧对齐情况。', templateDescription: '适合教师识别学生群体与岗位需求之间最需要补齐的能力缺口。', entryHint: '优先看高频岗位赛道、缺口技能和对应补强动作。' },
        { code: 'TEACHING_ADVICE', label: '教学建议报告', defaultName: '教学建议与课程对齐报告', description: '聚焦课程、实训、项目产出与就业要求如何对齐。', templateDescription: '适合教师把岗位能力要求映射到课程设计、实训任务和辅导动作。', entryHint: '优先看学生共性短板、课程输出缺口和辅导优先级。' },
        { code: 'INDUSTRY', label: '专业行业分析', defaultName: '专业行业分析与教改报告', description: '聚焦专业对应岗位族、能力点、课程模块与毕业要求的行业映射。', templateDescription: '适合教师将行业趋势直接映射到专业建设、课程整改和能力点设计。', entryHint: '优先看岗位族、能力点和课程整改建议。' },
        { code: 'SKILL', label: '能力缺口观察', defaultName: '教学能力缺口观察报告', description: '聚焦岗位高频能力与教学侧能力供给差距。', templateDescription: '适合教师定位哪些技能最适合融入课程、实训和辅导。', entryHint: '优先看高频技能与课程补位点。' },
        { code: 'COMPREHENSIVE', label: '教学支持总览', defaultName: '教学支持综合报告', description: '整合关键图表、对比项和行动建议，适合做阶段总览。', templateDescription: '适合当前角色快速获取一份覆盖核心判断与动作建议的综合报告。', entryHint: '适合作为阶段复盘和多维观察入口。' }
      ]
    }
  }
  return {
    roleType: roleType ?? 0, roleLabel,
    moduleTitle: '个人求职工作台',
    moduleDescription: '学生入口优先突出个人求职分析、技能差距和薪资趋势，帮助围绕目标岗位制定行动。',
    defaultReportType: 'JOB_SEEKING',
    defaultReportName: '个人求职分析报告',
    privateListScope: '仅查看本人生成的私有报告',
    publicListScope: '公开报告对所有登录或未登录用户可见',
    reportTypes: [
      { code: 'JOB_SEEKING', label: '个人求职分析', defaultName: '个人求职分析报告', description: '聚焦岗位匹配、目标城市、技能差距和投递策略。', templateDescription: '适合学生从岗位视角快速看清自己当前该补什么、该投什么、该怎么表达。', entryHint: '优先看匹配度、目标城市机会和岗位样本。' },
      { code: 'INDUSTRY', label: '个人行业机会报告', defaultName: '个人行业机会分析报告', description: '聚焦个人目标方向的行业机会、城市分布、薪资趋势和能力要求。', templateDescription: '适合学生用行业趋势校准岗位方向、学习投入和投递策略。', entryHint: '优先看行业趋势、承接城市和高频能力。' },
      { code: 'SKILL_GAP', label: '技能差距分析', defaultName: '个人技能差距分析报告', description: '聚焦当前技能与高频岗位要求之间的缺口。', templateDescription: '适合学生快速识别最值得优先补齐的核心技能。', entryHint: '优先看高频缺口技能与补齐顺序。' },
      { code: 'SALARY', label: '薪资趋势参考', defaultName: '个人薪资趋势参考报告', description: '聚焦市场薪资区间、预期校准和区域机会。', templateDescription: '适合学生校准薪资预期，避免目标岗位和薪资层级错配。', entryHint: '优先看薪资区间变化与目标城市机会密度。' },
      { code: 'COMPREHENSIVE', label: '个人综合报告', defaultName: '个人综合求职报告', description: '整合关键图表、对比项和行动建议，适合做阶段总览。', templateDescription: '适合当前角色快速获取一份覆盖核心判断与动作建议的综合报告。', entryHint: '适合作为阶段复盘和多维观察入口。' }
    ]
  }
}

function applyMeta(meta) {
  const safeMeta = meta && meta.reportTypes?.length ? meta : buildLocalMeta(currentRoleType.value)
  reportMeta.value = safeMeta
  generateForm.value.reportType = safeMeta.defaultReportType
  generateForm.value.reportName = safeMeta.defaultReportName
  autoReportName.value = safeMeta.defaultReportName
}

function reportTypeLabel(code) {
  return currentReportTypes.value.find((item) => item.code === code)?.label || code || '--'
}

function getRoleIcon() {
  if (currentRoleType.value === 1) return Shield
  if (currentRoleType.value === 2) return BookOpen
  return UserRound
}

function formatDateTime(value) {
  if (!value) return '--'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN', { hour12: false })
}

function taskStatusLabel(status) {
  if (status === 'SUCCESS') return '已完成'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '生成中'
  if (status === 'PENDING') return '排队中'
  return status || '--'
}

function flashSuccess(msg) {
  success.value = msg
  setTimeout(() => { success.value = '' }, 3000)
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    const publicResult = await fetchPublicReports({ page: 1, pageSize: 6 })
    publicReports.value = publicResult.data || []

    if (!canManageReports.value) {
      privateReports.value = []
      schedules.value = []
      selectedReport.value = null
      selectedTask.value = null
      applyMeta(buildLocalMeta(currentRoleType.value))
      return
    }

    const [metaResult, reportsResult, schedulesResult] = await Promise.allSettled([
      fetchReportCenterMeta(authStore.token),
      fetchReports(authStore.token, { page: 1, pageSize: 10 }),
      fetchReportSchedules(authStore.token)
    ])

    applyMeta(metaResult.status === 'fulfilled' ? metaResult.value : buildLocalMeta(currentRoleType.value))
    privateReports.value = reportsResult.status === 'fulfilled' ? (reportsResult.value.data || []) : []
    schedules.value = schedulesResult.status === 'fulfilled' ? (schedulesResult.value || []) : []
    if (reportsResult.status === 'rejected' || schedulesResult.status === 'rejected') {
      error.value = '部分报告数据加载失败，已展示当前可用内容。'
    }
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function reloadSchedules() {
  try {
    schedules.value = await fetchReportSchedules(authStore.token)
  } catch (e) {
    error.value = normalizeError(e)
  }
}

async function pollTask(taskId) {
  let attempts = 0
  while (attempts < 12) {
    attempts += 1
    selectedTask.value = await fetchReportStatus(authStore.token, taskId)
    if (['SUCCESS', 'FAILED'].includes(selectedTask.value?.status)) break
    await new Promise((resolve) => setTimeout(resolve, 1200))
  }
}

async function handleCreateReport() {
  if (!canManageReports.value) {
    error.value = '请先登录后再生成角色专属报告。'
    return
  }
  actionLoading.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await createReport(authStore.token, {
      reportName: generateForm.value.reportName.trim() || currentReportTypeConfig.value?.defaultName || reportMeta.value.defaultReportName,
      reportType: generateForm.value.reportType,
      params: { targetRoleType: currentRoleType.value }
    })
    if (result.taskId) {
      success.value = `报告任务已提交，任务号 ${result.taskId}`
      await pollTask(result.taskId)
      await loadPage()
    } else {
      success.value = '报告请求已提交'
    }
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    actionLoading.value = false
  }
}

async function openReportDetail(report) {
  if (detailLoading.value) return
  error.value = ''
  detailLoading.value = true
  try {
    selectedReport.value = await fetchReportDrill(authStore.token, report.id)
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    detailLoading.value = false
  }
}

async function handleFormatExport(payload) {
  if (!payload?.id) return
  try {
    // 先拿下载元数据：服务端据此校验访问权限并累加 viewCount。失败即中止导出。
    const meta = await fetchReportDownloadMeta(authStore.token, payload.id).catch((e) => {
      throw new Error(normalizeError(e) || '无法获取下载元数据')
    })
    const views = meta?.viewCount ?? meta?.downloadCount
    await exportReportFormat(authStore.token, payload.id, payload.reportName, exportFormat.value)
    const suffix = Number.isFinite(Number(views)) ? `，累计查看 ${views} 次` : ''
    flashSuccess(`报告已导出为 ${exportFormat.value.toUpperCase()}${suffix}`)
  } catch (e) {
    error.value = normalizeError(e)
  }
}

async function handlePreviewPdf(id) {
  error.value = ''
  try {
    // 预览也先走 download 元数据，触发权限校验 + 访问计数。
    await fetchReportDownloadMeta(authStore.token, id).catch((e) => {
      throw new Error(normalizeError(e) || '无法获取下载元数据')
    })
    await openReportPdf(authStore.token, id)
  } catch (e) {
    error.value = normalizeError(e)
  }
}

async function handleDeleteReport(id, event) {
  if (event) event.stopPropagation()
  if (!confirm('确定要删除这份报告吗？删除后无法恢复。')) return
  actionLoading.value = true
  error.value = ''
  try {
    await deleteReport(authStore.token, id)
    flashSuccess('报告已删除')
    if (selectedReport.value?.reportId === id || selectedReport.value?.id === id) {
      selectedReport.value = null
    }
    await loadPage()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    actionLoading.value = false
  }
}

async function handleSubmitReview(id, event) {
  if (event) event.stopPropagation()
  try {
    await submitReportReview(authStore.token, id)
    flashSuccess('已提交审核')
    await loadPage()
  } catch (e) {
    error.value = normalizeError(e)
  }
}

onMounted(() => { loadPage() })
</script>

<template>
  <div class="report-page page-animate">
    <div class="report-layout">
      <aside class="report-sidebar" aria-label="报告中心导航">
        <div class="report-sidebar-inner">
          <h1 class="report-hero-title">报告分析</h1>
          <nav class="report-nav" aria-label="报告中心章节导航">
            <div
              v-for="group in sidebarGroups"
              :key="group.key"
              class="report-nav-group"
            >
              <div class="report-nav-group-label">{{ group.label }}</div>
              <ul class="report-nav-list" role="tablist">
                <li v-for="item in group.items" :key="item.key">
                  <button
                    type="button"
                    class="report-nav-link"
                    :class="{ 'is-active': activeSection === item.key }"
                    role="tab"
                    :aria-selected="activeSection === item.key"
                    @click="activeSection = item.key"
                  >
                    <span class="report-nav-link-label">{{ item.label }}</span>
                    <span v-if="typeof item.count === 'number'" class="report-nav-link-count">{{ item.count }}</span>
                  </button>
                </li>
              </ul>
            </div>
          </nav>
        </div>
      </aside>

      <div class="report-content">
        <div v-if="error" class="status-banner error-banner">{{ error }}</div>
        <div v-if="success" class="status-banner success-banner">{{ success }}</div>

        <!-- 角色化生成 -->
        <section v-if="activeSection === 'generate'" class="report-main">
          <article class="surface section-panel workspace-module-panel">
            <div class="panel-head">
              <h2 class="workspace-panel-title inline-icon"><Sparkles :size="15" /> 角色化生成</h2>
              <GlowButton variant="ghost" @click="loadPage"><RefreshCw :size="14" />刷新</GlowButton>
            </div>

            <div class="entry-grid">
              <button
                v-for="item in currentReportTypes"
                :key="item.code"
                class="entry-card"
                :class="{ active: generateForm.reportType === item.code }"
                @click="generateForm.reportType = item.code"
              >
                <strong>{{ item.label }}</strong>
                <p>{{ item.description }}</p>
                <span>{{ item.entryHint }}</span>
              </button>
            </div>

            <div v-if="canManageReports" class="form-row">
              <input
                v-model="generateForm.reportName"
                class="glass-input"
                :placeholder="currentReportTypeConfig?.defaultName || '输入报告名称'"
                @keydown.enter="handleCreateReport"
              />
              <GlowButton variant="primary" :loading="actionLoading" @click="handleCreateReport">
                <FileText :size="14" />
                生成报告
              </GlowButton>
            </div>
            <div v-else class="inline-hint">登录后可生成该角色的专属报告。</div>

            <div v-if="selectedTask" class="task-strip">
              <div v-for="item in latestTaskSummary" :key="item.label" class="summary-box-mini">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </article>
        </section>

        <!-- 报告库（私有 / 公开 / 待审核）——列表 + 详情 -->
        <section
          v-else-if="['private','public','review'].includes(activeSection)"
          class="report-main library-layout"
          :class="{ 'list-collapsed': listCollapsed }"
        >
          <article class="surface section-panel workspace-module-panel list-panel">
            <div class="panel-head">
              <h2 class="workspace-panel-title inline-icon">
                <LockKeyhole v-if="activeSection === 'private'" :size="15" />
                <Globe v-else-if="activeSection === 'public'" :size="15" />
                <Sparkles v-else :size="15" />
                {{ activeSection === 'private' ? (isAdmin ? '全站私有报告' : '我的报告') : activeSection === 'public' ? '公开报告' : '待审核' }}
              </h2>
              <div class="inline-actions">
                <GlowButton variant="ghost" @click="loadPage"><RefreshCw :size="14" /></GlowButton>
                <button
                  v-if="selectedReport"
                  type="button"
                  class="icon-btn"
                  :title="listCollapsed ? '展开列表' : '收起列表'"
                  @click="listCollapsed = !listCollapsed"
                >
                  <component :is="listCollapsed ? PanelLeftOpen : PanelLeftClose" :size="14" />
                </button>
              </div>
            </div>

            <!-- 私有 / 我的 -->
            <div v-if="activeSection === 'private'">
              <div v-if="!canManageReports" class="empty-state-wrapper">
                <EmptyState icon="inbox" title="暂不可查看私有报告" description="登录后可查看并管理你自己的角色化报告。" />
              </div>
              <div v-else class="card-list scrollable-list">
                <div
                  v-for="report in privateReports"
                  :key="report.id"
                  class="list-item clickable"
                  :class="{ active: selectedReport?.reportId === report.id }"
                  @click="openReportDetail(report)"
                >
                  <div class="list-main">
                    <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                    <p>{{ reportTypeLabel(report.reportType) }} · {{ formatDateTime(report.generatedAt) }}</p>
                    <p class="muted">{{ report.reportLifecycle?.stateLabel || '草稿' }}</p>
                  </div>
                  <div class="inline-actions">
                    <button
                      v-if="!isAdmin && ['DRAFT','REJECTED'].includes(report.reportLifecycle?.state)"
                      class="icon-btn"
                      title="提交审核"
                      @click="handleSubmitReview(report.id, $event)"
                    >
                      <Send :size="14" />
                    </button>
                    <Trash2 class="delete-icon" :size="16" @click="handleDeleteReport(report.id, $event)" />
                  </div>
                </div>
                <div v-if="loading" class="skeleton-list mt-4">
                  <SkeletonCard type="list" :lines="4" />
                </div>
                <div v-if="!privateReports.length && !loading" class="empty-state-wrapper mt-4">
                  <EmptyState icon="file" title="还没有生成私有报告" description="先到「角色化生成」生成第一份报告。" />
                </div>
              </div>
            </div>

            <!-- 公开 -->
            <div v-else-if="activeSection === 'public'" class="card-list scrollable-list">
              <div
                v-for="report in publicReports"
                :key="report.id"
                class="list-item clickable"
                :class="{ active: selectedReport?.reportId === report.id }"
                @click="openReportDetail(report)"
              >
                <div class="list-main">
                  <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                  <p>{{ reportTypeLabel(report.reportType) }}</p>
                </div>
                <span class="pill"><Globe :size="13" />公开</span>
              </div>
              <div v-if="loading" class="skeleton-list mt-4">
                <SkeletonCard type="list" :lines="3" />
              </div>
              <div v-if="!publicReports.length && !loading" class="empty-state-wrapper mt-4">
                <EmptyState icon="file" title="暂无公开报告" description="当前还没有可以直接浏览的公开报告。" />
              </div>
            </div>

            <!-- 待审核（管理员） -->
            <div v-else-if="activeSection === 'review' && isAdmin">
              <ReportPublicationPanel
                embedded
                :token="authStore.token"
                :report-type-label="reportTypeLabel"
                @select="openReportDetail"
                @error="(msg) => (error = msg)"
                @success="flashSuccess"
              />
            </div>
          </article>

          <div class="detail-col">
            <div v-if="listCollapsed && selectedReport" class="detail-toolbar">
              <button type="button" class="detail-back-btn" @click="listCollapsed = false">
                <ChevronLeft :size="14" />
                <span>展开列表</span>
              </button>
            </div>
            <div v-if="detailLoading" class="loading-overlay">
              <RefreshCw class="spinning" :size="32" style="color: var(--c-accent-primary)" />
              <div style="margin-top: 12px; color: var(--c-text-muted); font-size: 14px;">正在加载报告详情...</div>
            </div>
            <ReportDetailPanel
              v-if="selectedReport"
              :report="selectedReport"
              v-model:export-format="exportFormat"
              :report-type-label="reportTypeLabel"
              @preview="handlePreviewPdf"
              @export="handleFormatExport"
            />
            <div v-else class="empty-state-card glass-panel">
              <EmptyState icon="search" title="选择一份报告" description="从左侧列表中选择一份报告，查看分析详情。" />
            </div>
          </div>
        </section>

        <!-- 调度计划 -->
        <section v-else-if="activeSection === 'schedule'" class="report-main">
          <ReportSchedulePanel
            v-if="canManageReports"
            :schedules="schedules"
            :token="authStore.token"
            :report-types="currentReportTypes"
            :default-report-type="reportMeta.defaultReportType"
            :report-type-label="reportTypeLabel"
            @refresh="reloadSchedules"
            @error="(msg) => (error = msg)"
            @success="flashSuccess"
          />
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 两栏布局：侧栏目录 + 主内容（仿智能推荐） ---------- */
.report-layout {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}
.report-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
  min-height: calc(100vh - 120px);
}
.report-main { min-height: 560px; }

.report-sidebar {
  position: sticky;
  top: 24px;
  align-self: start;
  min-width: 0;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  scrollbar-width: none;
}
.report-sidebar::-webkit-scrollbar { display: none; }

.report-sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 16px 32px;
}

.report-hero-title {
  margin: 0 0 4px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--c-border-glass);
  font-family: var(--font-serif);
  font-size: clamp(20px, 1.8vw, 24px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--c-text-primary);
}

.report-nav { display: flex; flex-direction: column; gap: 18px; }
.report-nav-group { display: flex; flex-direction: column; gap: 4px; }
.report-nav-group-label {
  padding: 0 8px 2px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.report-nav-list {
  display: flex; flex-direction: column; gap: 2px;
  margin: 0; padding: 0; list-style: none;
}
.report-nav-link {
  position: relative;
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  width: 100%;
  padding: 7px 10px 7px 12px;
  border: none; border-radius: 6px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px; font-weight: 400; line-height: 1.4;
  text-align: left; cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}
.report-nav-link-label {
  min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.report-nav-link-count {
  flex-shrink: 0;
  min-width: 20px; padding: 0 6px;
  font-size: 11px; line-height: 18px; text-align: center;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
}
.report-nav-link:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.report-nav-link.is-active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-weight: 600;
}
.report-nav-link.is-active .report-nav-link-count {
  background: rgba(30, 117, 255, 0.16);
  color: var(--c-accent-primary);
}
.report-nav-link.is-active::before {
  content: '';
  position: absolute;
  left: 0; top: 6px; bottom: 6px;
  width: 2px; border-radius: 2px;
  background: var(--c-accent-primary);
}
.report-nav-link:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
}

/* ---------- 主内容 ---------- */
.report-main { display: flex; flex-direction: column; gap: 20px; }

.status-banner { padding: 12px 16px; border-radius: 14px; font-size: 13px; }
.error-banner { color: #b91c1c; background: rgba(254, 226, 226, 0.84); }
.success-banner { color: #166534; background: rgba(220, 252, 231, 0.84); }
[data-theme="dark"] .error-banner { background: rgba(178, 59, 46, 0.18); color: #ffb4a6; }
[data-theme="dark"] .success-banner { background: rgba(30, 138, 91, 0.18); color: #b6e8c8; }

/* 报告库：列表 + 详情 两列 */
.library-layout {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  transition: grid-template-columns 0.25s ease;
}
.library-layout.list-collapsed {
  grid-template-columns: 0 minmax(0, 1fr);
  gap: 0;
}
.library-layout.list-collapsed .list-panel {
  display: none;
}
.list-panel { display: flex; flex-direction: column; gap: 14px; }
.detail-col { position: relative; min-height: 480px; }

.detail-toolbar {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 12px;
}
.detail-back-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px 6px 8px;
  border-radius: 999px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  color: var(--c-text-secondary);
  font-family: var(--font-sans); font-size: 12.5px; font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, background-color 0.15s ease;
}
.detail-back-btn:hover {
  border-color: rgba(30, 117, 255, 0.4);
  color: var(--c-accent-primary);
  background: rgba(255, 255, 255, 0.92);
}

.panel-head, .inline-actions { display: flex; align-items: center; gap: 12px; }
.panel-head { justify-content: space-between; }

/* 角色化生成入口卡 */
.entry-grid {
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px;
}
.entry-card {
  text-align: left; padding: 14px 16px; border-radius: 14px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer; transition: all .18s;
  min-width: 0;
}
.entry-card strong { display: block; font-size: 14px; margin-bottom: 6px; }
.entry-card p { margin: 0 0 6px; font-size: 12.5px; color: var(--c-text-secondary); line-height: 1.5; }
.entry-card span { font-size: 11.5px; color: var(--c-text-muted); line-height: 1.4; }
.entry-card:hover {
  border-color: rgba(30, 117, 255, 0.3);
  background: rgba(255, 255, 255, 0.9);
}
.entry-card.active {
  border-color: rgba(30, 117, 255, 0.6);
  background: rgba(30, 117, 255, 0.08);
  box-shadow: 0 0 14px rgba(30, 117, 255, 0.08);
}

.form-row { display: flex; gap: 10px; align-items: center; }
.form-row .glass-input { flex: 1; }
.glass-input {
  width: 100%; padding: 10px 12px; border-radius: 10px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary); font-size: 13px;
}
.inline-hint { font-size: 12px; color: var(--c-text-muted); padding: 6px 2px; }

.task-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.summary-box-mini {
  padding: 10px 12px; border-radius: 10px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  display: flex; flex-direction: column; gap: 2px;
}
.summary-box-mini span { font-size: 11px; color: var(--c-text-muted); }
.summary-box-mini strong { font-size: 13px; color: var(--c-text-primary); }

/* 列表项 */
.card-list { display: flex; flex-direction: column; gap: 8px; }
.scrollable-list {
  max-height: 640px; overflow-y: auto; padding-right: 6px;
  display: flex; flex-direction: column; gap: 8px;
}

.list-item {
  display: flex; justify-content: space-between; gap: 10px;
  padding: 11px 13px; border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 198, 215, 0.5);
  min-width: 0; transition: all .18s;
}
.list-item:hover {
  border-color: rgba(30, 117, 255, 0.3);
  background: rgba(255, 255, 255, 0.9);
}
.list-item.active {
  border-color: rgba(30, 117, 255, 0.6);
  background: rgba(30, 117, 255, 0.08);
  box-shadow: 0 0 14px rgba(30, 117, 255, 0.08);
}
.list-item .list-main { min-width: 0; flex: 1; }
.list-item strong { font-size: 13px; display: block; }
.list-item p {
  margin: 2px 0 0; color: var(--c-text-secondary); font-size: 11.5px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.list-item p.muted { color: var(--c-text-muted); font-size: 11px; }
.clickable { cursor: pointer; }

.pill {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px; border-radius: 999px; white-space: nowrap;
  background: var(--c-bg-surface-hover); color: var(--c-text-secondary);
  font-size: 11px;
}

.icon-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border-radius: 7px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.6);
  color: var(--c-text-secondary); cursor: pointer;
}
.icon-btn:hover { color: var(--c-accent-primary); border-color: rgba(30, 117, 255, 0.4); }
.delete-icon { cursor: pointer; color: var(--c-text-muted); }

.empty-state-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-height: 480px; height: 100%; border-radius: 20px; color: var(--c-text-muted);
}
.empty-state-wrapper {
  min-height: 160px; display: flex; align-items: center; justify-content: center;
}
.loading-overlay {
  position: absolute; inset: 0;
  background: rgba(15, 23, 42, .35);
  backdrop-filter: blur(4px);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  border-radius: 20px; z-index: 10;
}
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { 100% { transform: rotate(360deg); } }
.mt-4 { margin-top: 16px; }

/* 响应式 */
@media (max-width: 1180px) {
  .library-layout { grid-template-columns: 1fr; }
  .detail-col { min-height: auto; }
}
@media (max-width: 900px) {
  .report-layout { grid-template-columns: 1fr; gap: 16px; }
  .report-sidebar {
    position: relative; top: 0; width: 100%; max-height: none; overflow: visible;
  }
  .report-sidebar-inner {
    flex-direction: row; gap: 18px; padding: 4px 2px 6px;
    overflow-x: auto; overflow-y: hidden;
  }
  .report-hero-title { flex-shrink: 0; padding-bottom: 0; border-bottom: none; }
  .report-nav { flex-direction: row; gap: 18px; flex: 1; }
  .entry-grid { grid-template-columns: 1fr; }
  .scrollable-list { max-height: none; }
  .form-row { flex-direction: column; align-items: stretch; }
  .task-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
