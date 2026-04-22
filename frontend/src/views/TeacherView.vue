<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  createTeacherCourse,
  deleteTeacherCourse,
  downloadTeacherMaterialTemplate,
  fetchCurriculums,
  fetchTeacherCourses,
  fetchTeacherMarketMatch,
  fetchTeacherMaterialStatus,
  fetchTeachingReform,
  uploadCurriculumExcel,
  uploadTeacherMaterial
} from '../api'
import {
  BookOpen,
  CheckCircle2,
  Download,
  FileSpreadsheet,
  Files,
  FolderOpen,
  Plus,
  Trash2,
  Upload
} from 'lucide-vue-next'

const authStore = useAuthStore()
const router = useRouter()
const { success, error } = useToast()

const loading = ref(true)
const saving = ref(false)
const uploadLoading = ref(false)
const courses = ref([])
const curriculums = ref([])
const matchResult = ref(null)
const selectedExcel = ref(null)
const selectedExcelName = ref('')

const teachingReform = ref(null)
const reformLoading = ref(false)
const reformError = ref('')

// ---------- Materials (教师素材) ----------
const materialStatus = ref(null)
const materialLoading = ref(false)
const materialError = ref('')
// Per-type busy flags, keyed by materialType (e.g. CURRICULUM / SYLLABUS / STUDENT_STATUS).
const materialUploading = ref({})
const materialDownloading = ref({})

const materialItems = computed(() => {
  const raw = materialStatus.value?.items
  return Array.isArray(raw) ? raw : []
})
const materialsReady = computed(() => Boolean(materialStatus.value?.ready))
const materialUploadedCount = computed(
  () => materialItems.value.filter((item) => item.uploaded).length
)
const materialPendingCount = computed(
  () => materialItems.value.filter((item) => !item.uploaded).length
)
const materialLatestUploadedAt = computed(() => {
  const stamps = materialItems.value
    .map((item) => item.latestUploadedAt)
    .filter(Boolean)
    .map((value) => new Date(value).getTime())
    .filter((value) => !Number.isNaN(value))
  if (!stamps.length) return ''
  return formatTimestamp(Math.max(...stamps))
})
const materialGuidance = computed(() => {
  const raw = materialStatus.value?.guidance
  return Array.isArray(raw) ? raw : []
})

function formatTimestamp(input) {
  const date = input instanceof Date ? input : new Date(input)
  if (!date || Number.isNaN(date.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// Defensive helpers so the template can treat unknown shapes uniformly.
const reformSuggestions = computed(() => {
  const raw = teachingReform.value?.suggestions
  return Array.isArray(raw) ? raw : []
})
const reformHighlights = computed(() => {
  const raw = teachingReform.value?.highlights
  return Array.isArray(raw) ? raw : []
})
const reformHasItems = computed(
  () => reformSuggestions.value.length > 0 || reformHighlights.value.length > 0
)

const courseForm = ref({
  courseName: '',
  coreSkills: '',
  creditHours: '',
  semester: '',
  major: '',
  description: ''
})

const overviewCards = computed(() => [
  { label: '自建课程', value: courses.value.length, hint: '教师手工维护' },
  { label: '课程库条数', value: curriculums.value.length, hint: '已进入课程库' },
  { label: '技能覆盖率', value: matchResult.value?.coverageRate || '--', hint: '对市场热门技能' },
  { label: '市场缺口', value: matchResult.value?.marketGaps?.length || 0, hint: '待补齐技能项' }
])

const teacherInsights = computed(() => {
  if (!matchResult.value) return []
  const coverage = parseFloat(String(matchResult.value.coverageRate || '0').replace('%', '')) || 0
  const gapCount = matchResult.value.marketGaps?.length || 0
  const outdatedCount = matchResult.value.possiblyOutdated?.length || 0

  return [
    {
      title: '课程与市场对齐度',
      summary: `覆盖率 ${coverage.toFixed(1)}%`,
      detail: coverage >= 70
        ? '课程结构与市场需求已经较为接近，下一步应从“教过”转向“学生能否产出可展示成果”。'
        : coverage >= 40
          ? '课程体系处于可用但不充分的状态，优先补齐缺口最大的技能，再考虑课程深度。'
          : '课程内容与市场需求存在明显偏差，当前最重要的是先修正核心技能供给方向。'
    },
    {
      title: '缺口优先级',
      summary: `待补齐技能 ${gapCount} 项`,
      detail: gapCount > 8
        ? '缺口项较多，说明课程供给仍未形成系统覆盖，建议按市场需求高低分层补课。'
        : '缺口数量可控，可以围绕重点技能做专题强化或项目制训练。'
    },
    {
      title: '课程老化风险',
      summary: `疑似过时技能 ${outdatedCount} 项`,
      detail: outdatedCount > 3
        ? '需要审视是否仍有过多旧技术内容占据课时，影响学生对新需求的投入。'
        : '现有课程内容整体较新，更多问题可能在项目表达和成果转译，而非课程是否过时。'
    }
  ]
})

const topGapSkills = computed(() => (matchResult.value?.marketGaps || []).slice(0, 5))
const topCoveredSkills = computed(() => (matchResult.value?.coveredSkills || []).slice(0, 8))

// ---------- Sidebar navigation ----------
// Sections are arranged into two logical groups. The order here is the order
// they render in the right column, and the order they appear in the sidebar.
const navGroups = [
  {
    title: '概览',
    items: [
      { id: 'section-diagnostic', label: '教学诊断' },
      { id: 'section-actions', label: '动作建议' },
      { id: 'section-teaching-reform', label: '教改建议' }
    ]
  },
  {
    title: '课程管理',
    items: [
      { id: 'section-materials', label: '素材管理' },
      { id: 'section-excel-import', label: '批量导入' },
      { id: 'section-new-course', label: '新增课程' },
      { id: 'section-analysis', label: '供需分析' },
      { id: 'section-courses', label: '课程列表' }
    ]
  }
]

const activeSection = ref('section-diagnostic')
let observer = null

function scrollToSection(sectionId) {
  const element = document.getElementById(sectionId)
  if (!element) return

  // Match DashboardView's pattern: prefer .main-content scroll container, then
  // fall back to the document/window scroll.
  const scrollContainer = document.querySelector('.main-content')

  if (scrollContainer instanceof HTMLElement) {
    const containerRect = scrollContainer.getBoundingClientRect()
    const elementRect = element.getBoundingClientRect()
    const targetTop = scrollContainer.scrollTop + elementRect.top - containerRect.top - 12

    scrollContainer.scrollTo({
      top: Math.max(targetTop, 0),
      behavior: 'smooth'
    })
    activeSection.value = sectionId
    return
  }

  const targetTop = element.getBoundingClientRect().top + window.scrollY - 12
  window.scrollTo({ top: Math.max(targetTop, 0), behavior: 'smooth' })
  activeSection.value = sectionId
}

function setupObserver() {
  if (typeof IntersectionObserver === 'undefined') return
  if (observer) {
    observer.disconnect()
    observer = null
  }

  const sections = document.querySelectorAll('.teacher-section')
  if (!sections.length) return

  const root = document.querySelector('.main-content') || null

  observer = new IntersectionObserver(
    (entries) => {
      // Prefer the topmost currently-intersecting section. Falling back to
      // isIntersecting alone can flicker when two sections overlap the band;
      // sorting by boundingClientRect.top keeps the "current" one stable.
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)

      if (visible[0]) activeSection.value = visible[0].target.id
    },
    {
      root,
      rootMargin: '-20% 0px -60% 0px',
      threshold: 0
    }
  )

  sections.forEach((section) => observer.observe(section))
}

async function loadData() {
  if (![1, 2].includes(authStore.user?.roleType)) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    const [courseResult, matchResultValue, curriculumResult] = await Promise.allSettled([
      fetchTeacherCourses(authStore.token),
      fetchTeacherMarketMatch(authStore.token),
      fetchCurriculums(authStore.token, { page: 1, pageSize: 6 })
    ])

    courses.value = courseResult.status === 'fulfilled' ? courseResult.value : []
    matchResult.value = matchResultValue.status === 'fulfilled' ? matchResultValue.value : null
    curriculums.value = curriculumResult.status === 'fulfilled' ? curriculumResult.value.data : []
  } catch (e) {
    error(`教师工作台加载失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

async function loadMaterialStatus({ silent = false } = {}) {
  if (![1, 2].includes(authStore.user?.roleType)) return
  if (!silent) materialLoading.value = true
  materialError.value = ''
  try {
    const result = await fetchTeacherMaterialStatus(authStore.token)
    materialStatus.value = result || null
  } catch (e) {
    materialError.value = e?.message || '加载素材状态失败'
  } finally {
    materialLoading.value = false
  }
}

async function handleDownloadTemplate(materialType) {
  if (materialDownloading.value[materialType]) return
  materialDownloading.value = { ...materialDownloading.value, [materialType]: true }
  try {
    const blob = await downloadTeacherMaterialTemplate(authStore.token, materialType)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${materialTypeLabel(materialType)}模板.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    // Revoke slightly later — some browsers need the URL to still exist when the click resolves.
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    success(`${materialTypeLabel(materialType)}模板已开始下载`)
  } catch (e) {
    error(e?.message || '模板下载失败')
  } finally {
    const next = { ...materialDownloading.value }
    delete next[materialType]
    materialDownloading.value = next
  }
}

function pickMaterialFile(materialType) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.addEventListener('change', () => {
    const file = input.files && input.files[0]
    if (file) handleUploadMaterial(materialType, file)
  })
  input.click()
}

async function handleUploadMaterial(materialType, file) {
  if (!file) return
  if (materialUploading.value[materialType]) return
  materialUploading.value = { ...materialUploading.value, [materialType]: true }
  try {
    if (materialType === 'CURRICULUM') {
      // Backend rejects CURRICULUM on /teacher/materials/upload. Route to the
      // dedicated curriculum Excel endpoint so the "素材管理" card still works
      // as a one-stop entry.
      const result = await uploadCurriculumExcel(authStore.token, file)
      success(`课程 Excel 导入完成：成功 ${result.imported || 0} 条，映射技能 ${result.mappedSkills || 0} 条。`)
    } else {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('materialType', materialType)
      await uploadTeacherMaterial(authStore.token, formData)
      success(`${materialTypeLabel(materialType)}上传成功`)
    }
    await loadMaterialStatus({ silent: true })
    // Curriculum uploads change course counts/insights — refresh overall dashboard too.
    if (materialType === 'CURRICULUM') await loadData()
  } catch (e) {
    error(e?.message || '上传失败')
  } finally {
    const next = { ...materialUploading.value }
    delete next[materialType]
    materialUploading.value = next
  }
}

function materialTypeLabel(materialType) {
  const match = materialItems.value.find((item) => item.type === materialType)
  if (match?.label) return match.label
  if (materialType === 'CURRICULUM') return '课程清单 Excel'
  if (materialType === 'SYLLABUS') return '教学大纲 Excel'
  if (materialType === 'STUDENT_STATUS') return '学生情况 Excel'
  return materialType
}

async function loadTeachingReform() {
  reformLoading.value = true
  reformError.value = ''
  try {
    const result = await fetchTeachingReform(authStore.token)
    teachingReform.value = result || null
  } catch (e) {
    // Section-level banner only — deliberately no toast here.
    reformError.value = e?.message || '加载教改建议失败'
  } finally {
    reformLoading.value = false
  }
}

async function handleCreateCourse() {
  if (saving.value) return
  saving.value = true
  try {
    await createTeacherCourse(authStore.token, {
      ...courseForm.value,
      creditHours: courseForm.value.creditHours ? Number(courseForm.value.creditHours) : null
    })
    success('课程已添加，并已刷新供需分析。')
    courseForm.value = {
      courseName: '',
      coreSkills: '',
      creditHours: '',
      semester: '',
      major: '',
      description: ''
    }
    await loadData()
  } catch (e) {
    error(e.message)
  } finally {
    saving.value = false
  }
}

async function handleDeleteCourse(id) {
  try {
    await deleteTeacherCourse(authStore.token, id)
    success('课程已删除。')
    await loadData()
  } catch (e) {
    error(e.message)
  }
}

function handlePickExcel(event) {
  const [file] = event.target.files || []
  selectedExcel.value = file || null
  selectedExcelName.value = file?.name || ''
}

async function handleUploadExcel() {
  if (!selectedExcel.value || uploadLoading.value) return
  uploadLoading.value = true
  try {
    const result = await uploadCurriculumExcel(authStore.token, selectedExcel.value)
    success(`课程 Excel 导入完成：成功 ${result.imported || 0} 条，映射技能 ${result.mappedSkills || 0} 条。`)
    selectedExcel.value = null
    selectedExcelName.value = ''
    await loadData()
  } catch (e) {
    error(e.message)
  } finally {
    uploadLoading.value = false
  }
}

onMounted(async () => {
  await loadData()
  // Non-blocking: the section shows its own loader while this resolves.
  loadTeachingReform()
  loadMaterialStatus()
  await nextTick()
  setupObserver()
})

// Sections mount/unmount as `loading` flips, so rewire the observer whenever
// the rendered section set changes.
watch(loading, async () => {
  await nextTick()
  setupObserver()
})

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
})
</script>

<template>
  <div class="teacher-view page-animate">
    <div v-if="loading" class="tv-skel">
      <div class="tv-skel-row">
        <SkeletonCard v-for="i in 3" :key="`s-${i}`" type="stat" />
      </div>
      <SkeletonCard type="chart" />
      <SkeletonCard type="list" :lines="5" />
    </div>

    <div v-else class="teacher-shell">
      <aside class="teacher-sidebar" aria-label="教师工作台目录">
        <div class="teacher-sidebar-inner">
          <h1 class="teacher-hero-title">课程与供需</h1>
          <nav
            v-for="group in navGroups"
            :key="group.title"
            class="teacher-nav-group"
            :aria-label="group.title"
          >
            <div class="teacher-nav-group-label">{{ group.title }}</div>
            <ul class="teacher-nav-list">
              <li v-for="item in group.items" :key="item.id">
                <a
                  :href="`#${item.id}`"
                  class="teacher-nav-link"
                  :class="{ 'is-active': activeSection === item.id }"
                  @click.prevent="scrollToSection(item.id)"
                >
                  <span class="teacher-nav-link-label">{{ item.label }}</span>
                </a>
              </li>
            </ul>
          </nav>
        </div>
      </aside>

      <div class="teacher-main">
        <section class="workspace-metric-strip">
          <article v-for="card in overviewCards" :key="card.label" class="metric-card">
            <div class="metric-head">
              <span class="metric-label">{{ card.label }}</span>
              <span class="metric-dot" aria-hidden="true"></span>
            </div>
            <div class="metric-value">{{ card.value }}</div>
            <div class="metric-note">{{ card.hint }}</div>
          </article>
        </section>

        <article id="section-diagnostic" class="teacher-section panel">
          <header class="panel-head">
            <h2 class="panel-title">教学诊断</h2>
          </header>
          <div class="panel-body">
            <div class="insight-list">
              <div v-for="item in teacherInsights" :key="item.title" class="insight-card">
                <div class="insight-head">
                  <strong>{{ item.title }}</strong>
                  <span class="pill">{{ item.summary }}</span>
                </div>
                <p>{{ item.detail }}</p>
              </div>
            </div>
          </div>
        </article>

        <article id="section-actions" class="teacher-section panel">
          <header class="panel-head">
            <h2 class="panel-title">教师动作建议</h2>
          </header>
          <div class="panel-body">
            <div class="feature-list">
              <button class="feature-card" @click="router.push('/reports')">
                <strong>教学建议报告</strong>
              </button>
              <button class="feature-card" @click="router.push('/insights')">
                <strong>行业趋势映射</strong>
              </button>
              <button class="feature-card" @click="router.push('/recommend')">
                <strong>学生成果回看</strong>
              </button>
            </div>
          </div>
        </article>

        <article id="section-teaching-reform" class="teacher-section panel">
          <header class="panel-head">
            <h2 class="panel-title">教改建议</h2>
          </header>
          <div class="panel-body">
            <div v-if="reformLoading && !teachingReform" class="reform-loading">
              <SkeletonCard type="list" :lines="4" />
            </div>

            <div v-else-if="reformError" class="reform-error">
              {{ reformError }}
            </div>

            <template v-else-if="teachingReform">
              <div v-if="reformHasItems" class="insight-list">
                <div
                  v-for="(item, index) in reformSuggestions"
                  :key="`suggestion-${index}`"
                  class="insight-card"
                >
                  <strong>{{ item.title || item.name || `建议 ${index + 1}` }}</strong>
                  <p>{{ item.detail || item.summary || item.description || '' }}</p>
                </div>
                <div
                  v-for="(item, index) in reformHighlights"
                  :key="`highlight-${index}`"
                  class="insight-card"
                >
                  <strong>{{ item.title || item.name || `亮点 ${index + 1}` }}</strong>
                  <p>{{ item.detail || item.summary || item.description || '' }}</p>
                </div>
              </div>

              <div v-else-if="Object.keys(teachingReform).length === 0" class="empty-state">
                <BookOpen :size="26" />
                <p>暂无教改建议。</p>
              </div>

              <pre v-else class="reform-raw">{{ JSON.stringify(teachingReform, null, 2) }}</pre>
            </template>

            <div v-else class="empty-state">
              <BookOpen :size="26" />
              <p>暂无教改建议。</p>
            </div>
          </div>
        </article>

        <article id="section-materials" class="teacher-section panel">
          <header class="panel-head">
            <h2 class="panel-title">素材管理</h2>
          </header>
          <div class="panel-body">
            <div v-if="materialLoading && !materialStatus" class="material-loading">
              <SkeletonCard type="list" :lines="3" />
            </div>

            <template v-else>
              <div class="material-summary" :class="{ 'is-ready': materialsReady }">
                <div class="material-summary-main">
                  <div class="material-summary-badge">
                    <CheckCircle2 v-if="materialsReady" :size="18" />
                    <FolderOpen v-else :size="18" />
                    <span>{{ materialsReady ? '资料已齐备' : '仍有资料待补交' }}</span>
                  </div>
                  <div class="material-summary-stats">
                    <div class="material-summary-stat">
                      <span class="stat-label">已上传</span>
                      <strong>{{ materialUploadedCount }} / {{ materialItems.length || 0 }}</strong>
                    </div>
                    <div class="material-summary-stat">
                      <span class="stat-label">待补交</span>
                      <strong>{{ materialPendingCount }}</strong>
                    </div>
                    <div class="material-summary-stat">
                      <span class="stat-label">最近更新</span>
                      <strong>{{ materialLatestUploadedAt || '暂无记录' }}</strong>
                    </div>
                  </div>
                </div>
                <ul v-if="materialGuidance.length" class="material-guidance">
                  <li v-for="(line, index) in materialGuidance" :key="index">{{ line }}</li>
                </ul>

                <!-- 资料齐备后的报告生成入口，跳 /reports 自动触发 -->
                <div v-if="materialsReady" class="material-report-cta">
                  <div class="material-report-copy">
                    <strong>可生成班级供需分析报告</strong>
                    <p>综合当前课程、教学大纲和学生情况，输出一份面向教学改进的完整分析报告。</p>
                  </div>
                  <GlowButton variant="primary" @click="router.push('/reports?autogen=1')">
                    生成供需分析报告
                  </GlowButton>
                </div>
              </div>

              <div v-if="materialError" class="material-error-banner">
                {{ materialError }}
              </div>

              <div v-if="materialItems.length" class="material-grid">
                <article
                  v-for="item in materialItems"
                  :key="item.type"
                  class="material-card"
                  :class="{ 'is-uploaded': item.uploaded }"
                >
                  <header class="material-card-head">
                    <div class="material-card-title">
                      <FileSpreadsheet :size="18" />
                      <strong>{{ item.label }}</strong>
                    </div>
                    <span class="material-status-pill" :class="item.uploaded ? 'is-ok' : 'is-warn'">
                      {{ item.uploaded ? '已上传' : '待补交' }}
                    </span>
                  </header>

                  <p v-if="item.hint" class="material-hint">{{ item.hint }}</p>

                  <dl class="material-meta">
                    <div>
                      <dt>记录数</dt>
                      <dd>{{ item.recordCount ?? 0 }}</dd>
                    </div>
                    <div>
                      <dt>最近文件</dt>
                      <dd>{{ item.latestFileName || '—' }}</dd>
                    </div>
                    <div>
                      <dt>更新时间</dt>
                      <dd>{{ item.latestUploadedAt ? formatTimestamp(item.latestUploadedAt) : '—' }}</dd>
                    </div>
                  </dl>

                  <div class="material-actions">
                    <GlowButton
                      variant="ghost"
                      :loading="!!materialDownloading[item.type]"
                      @click="handleDownloadTemplate(item.type)"
                    >
                      <Download :size="14" />
                      下载模板
                    </GlowButton>
                    <GlowButton
                      variant="primary"
                      :loading="!!materialUploading[item.type]"
                      @click="pickMaterialFile(item.type)"
                    >
                      <Upload :size="14" />
                      {{ item.uploaded ? '重新上传' : '上传文件' }}
                    </GlowButton>
                  </div>
                </article>
              </div>

              <div v-else-if="!materialError" class="empty-state">
                <FolderOpen :size="26" />
                <p>暂无素材类型配置，请稍后再试。</p>
              </div>
            </template>
          </div>
        </article>

        <article id="section-excel-import" class="teacher-section panel">
          <header class="panel-head">
            <h2 class="panel-title">Excel 批量导入课程</h2>
          </header>
          <div class="panel-body">
            <div class="upload-panel">
              <label class="upload-box">
                <input type="file" accept=".xlsx,.xls" class="hidden-input" @change="handlePickExcel" />
                <Files :size="20" />
                <div>
                  <strong>{{ selectedExcelName || '选择课程 Excel 文件' }}</strong>
                </div>
              </label>
              <div class="button-row">
                <GlowButton variant="primary" :loading="uploadLoading" @click="handleUploadExcel">
                  <Upload :size="14" />
                  上传并导入
                </GlowButton>
              </div>
            </div>
          </div>
        </article>

        <article id="section-new-course" class="teacher-section panel">
          <header class="panel-head">
            <h2 class="panel-title">新增课程</h2>
          </header>
          <div class="panel-body">
            <form class="form-stack" @submit.prevent="handleCreateCourse">
              <div class="form-grid">
                <input v-model="courseForm.courseName" class="panel-input" placeholder="课程名称" required />
                <input v-model="courseForm.major" class="panel-input" placeholder="所属专业" />
                <input v-model="courseForm.semester" class="panel-input" placeholder="开课学期" />
                <input v-model="courseForm.creditHours" type="number" class="panel-input" placeholder="学时" />
              </div>
              <input v-model="courseForm.coreSkills" class="panel-input" placeholder="核心技能，多个技能请用英文逗号分隔" required />
              <textarea v-model="courseForm.description" class="panel-textarea" rows="4" placeholder="课程简介、教学目标或大纲关键词" />
              <GlowButton variant="primary" type="submit" :loading="saving">
                <Plus :size="14" />
                添加课程
              </GlowButton>
            </form>
          </div>
        </article>

        <article id="section-analysis" class="teacher-section panel">
          <header class="panel-head">
            <h2 class="panel-title">供需分析重点</h2>
          </header>
          <div class="panel-body">
            <div class="analysis-block">
              <div class="rate-card">
                <span class="rate-label">覆盖率</span>
                <strong>{{ matchResult?.coverageRate || '--' }}</strong>
                <p>课程内容与市场热门技能的贴合程度。</p>
              </div>

              <div class="analysis-section">
                <h3>优先补齐的技能</h3>
                <div class="gap-list">
                  <div v-for="gap in topGapSkills" :key="gap.skill" class="gap-item">
                    <span>{{ gap.skill }}</span>
                    <strong>{{ gap.marketDemand }}</strong>
                  </div>
                  <span v-if="!topGapSkills.length" class="panel-muted">暂无待补齐技能</span>
                </div>
              </div>

              <div class="analysis-section">
                <h3>已经覆盖的市场技能</h3>
                <div class="skills-list">
                  <span v-for="skill in topCoveredSkills" :key="skill" class="skill-tag covered">{{ skill }}</span>
                  <span v-if="!topCoveredSkills.length" class="panel-muted">暂无覆盖技能</span>
                </div>
              </div>

              <div class="analysis-section">
                <h3>教学建议</h3>
                <ul class="suggestions">
                  <li v-for="(item, index) in matchResult?.recommendations || []" :key="index">{{ item }}</li>
                </ul>
              </div>
            </div>
          </div>
        </article>

        <article id="section-courses" class="teacher-section panel">
          <header class="panel-head">
            <h2 class="panel-title">课程资产与课程库</h2>
          </header>
          <div class="panel-body">
            <div v-if="courses.length" class="course-list">
              <div v-for="course in courses" :key="course.id" class="course-item">
                <div class="course-main">
                  <div class="course-head">
                    <strong>{{ course.courseName }}</strong>
                    <span class="course-chip">{{ course.major || '未填写专业' }}</span>
                  </div>
                  <p v-if="course.description">{{ course.description }}</p>
                  <div class="course-meta">
                    <span>{{ course.semester || '学期未设置' }}</span>
                    <span>{{ course.creditHours || '--' }} 学时</span>
                  </div>
                  <div class="skill-tags">
                    <span
                      v-for="skill in String(course.coreSkills || '').split(',').map((item) => item.trim()).filter(Boolean)"
                      :key="`${course.id}-${skill}`"
                      class="skill-tag"
                    >
                      {{ skill }}
                    </span>
                  </div>
                </div>
                <button class="icon-btn delete" @click="handleDeleteCourse(course.id)">
                  <Trash2 :size="16" />
                </button>
              </div>
            </div>
            <div v-else class="empty-state">
              <BookOpen :size="26" />
              <p>暂无教师自建课程。</p>
            </div>

            <div v-if="curriculums.length" class="curriculum-list">
              <div v-for="item in curriculums" :key="item.id" class="curriculum-item">
                <div class="curriculum-head">
                  <strong>{{ item.courseName }}</strong>
                  <span class="course-chip">{{ item.major || '未填写专业' }}</span>
                </div>
                <p v-if="item.description">{{ item.description }}</p>
                <div class="course-meta">
                  <span>{{ item.department || '院系未填写' }}</span>
                  <span>{{ item.semester || '学期未填写' }}</span>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<style scoped>
.teacher-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ---------------- Metric strip (aligned with DataCollectorView) ---------------- */
.workspace-metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 134px;
  padding: 18px 20px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-quiet);
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.metric-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.metric-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--c-accent-primary);
  opacity: 0.75;
}

.metric-value {
  margin-top: auto;
  font-family: var(--font-serif);
  font-size: clamp(24px, 2.2vw, 28px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.1;
  color: var(--c-text-primary);
  font-variant-numeric: tabular-nums;
}

.metric-note {
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-secondary);
}

/* ---------------- Shell: sidebar + main column ---------------- */
.teacher-shell {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

/* ---------------- Sidebar (cloned from OpenApiSidebar visuals) ---------------- */
.teacher-sidebar {
  position: sticky;
  top: 0;
  align-self: start;
  /* Keep the sidebar's inner list scrollable if it ever overflows the viewport,
     without creating an outer scrollbar on the shell. */
  max-height: calc(100vh - 24px);
  overflow-y: auto;
  border-right: 1px solid var(--c-border-glass);
  background: transparent;
  scrollbar-width: none;
}
.teacher-sidebar::-webkit-scrollbar {
  display: none;
}

.teacher-sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 16px 32px;
}

.teacher-hero-title {
  margin: 0 0 4px;
  padding: 0 8px 14px;
  border-bottom: 1px solid var(--c-border-glass);
  font-family: var(--font-serif);
  font-size: clamp(20px, 1.8vw, 24px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--c-text-primary);
}

.teacher-nav-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.teacher-nav-group-label {
  padding: 0 8px 2px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.teacher-nav-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.teacher-nav-link {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px 7px 12px;
  border-radius: 6px;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 400;
  line-height: 1.4;
  text-decoration: none;
  transition: background-color 140ms ease, color 140ms ease;
  cursor: pointer;
}
.teacher-nav-link-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.teacher-nav-link:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.teacher-nav-link.is-active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-weight: 600;
}
.teacher-nav-link.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 2px;
  border-radius: 2px;
  background: var(--c-accent-primary);
}

/* ---------------- Main column ---------------- */
.teacher-main {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

/* ---------------- Panel (plain section) ---------------- */
.panel {
  display: flex;
  flex-direction: column;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: var(--shadow-card-quiet);
  overflow: hidden;
}

.teacher-section {
  /* Leave a little room below sticky headers when anchors scroll into view. */
  scroll-margin-top: 16px;
}

.panel-head {
  padding: 16px 22px 12px;
  border-bottom: 1px solid var(--c-border-glass);
}

.panel-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.25;
  color: var(--c-text-primary);
}

.panel-body {
  padding: 18px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-muted {
  color: var(--c-text-secondary);
  font-size: 13px;
}

/* ---------------- Insight list ---------------- */
.insight-list {
  display: grid;
  gap: 12px;
}

.insight-card {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}

.insight-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.insight-head strong {
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
}

.insight-card p {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* ---------------- Feature list ---------------- */
.feature-list {
  display: grid;
  gap: 10px;
}

.feature-card {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.feature-card:hover {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
}

.feature-card strong {
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
}

.feature-card:hover strong {
  color: var(--c-accent-primary);
}

/* ---------------- Upload + form ---------------- */
.upload-panel,
.form-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upload-box {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px dashed var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out);
}

.upload-box:hover {
  border-color: var(--c-accent-primary);
}

.upload-box strong {
  color: var(--c-text-primary);
  font-size: 13px;
  font-weight: 600;
}

.hidden-input {
  display: none;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.panel-input,
.panel-textarea {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.4;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.panel-input::placeholder,
.panel-textarea::placeholder {
  color: var(--c-text-faint);
}

.panel-input:focus,
.panel-textarea:focus {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
  outline: none;
}

.panel-textarea {
  resize: vertical;
}

.button-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

/* ---------------- Analysis block ---------------- */
.analysis-block {
  display: grid;
  gap: 18px;
}

.rate-card {
  padding: 18px;
  border-radius: 12px;
  background: var(--c-accent-primary-glow);
  border: 1px solid var(--c-border-glass);
}

.rate-label {
  display: block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.rate-card strong {
  display: block;
  margin: 6px 0 4px;
  font-family: var(--font-serif);
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--c-accent-primary);
}

.rate-card p {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 12.5px;
  line-height: 1.5;
}

.analysis-section {
  display: grid;
  gap: 10px;
}

.analysis-section h3 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.gap-list,
.skills-list,
.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.gap-list {
  flex-direction: column;
  gap: 6px;
}

.gap-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
}

.gap-item span {
  color: var(--c-text-primary);
  font-size: 13px;
}

.gap-item strong {
  color: var(--c-accent-primary);
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

.suggestions {
  display: grid;
  gap: 8px;
  padding-left: 18px;
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.skill-tag {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
}

.skill-tag.covered {
  color: var(--c-accent-primary);
  background: var(--c-accent-primary-glow);
}

/* ---------------- Course list ---------------- */
.course-list,
.curriculum-list {
  display: grid;
  gap: 10px;
}

.curriculum-list {
  margin-top: 4px;
}

.course-item,
.curriculum-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}

.course-main {
  display: grid;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.course-head,
.curriculum-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.course-head strong,
.curriculum-head strong {
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
}

.course-item p,
.curriculum-item p {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.course-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 600;
}

.course-meta {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  color: var(--c-text-muted);
  font-size: 12px;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  cursor: pointer;
  transition: color var(--duration-fast), border-color var(--duration-fast);
  flex-shrink: 0;
}

.icon-btn.delete:hover {
  color: #b23b2e;
  border-color: rgba(178, 59, 46, 0.32);
}

/* ---------------- Materials section ---------------- */
.material-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
  color: var(--c-text-muted);
  font-size: 13px;
}
.material-loading p {
  margin: 0;
}

.material-summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
}
.material-summary.is-ready {
  border-color: var(--c-accent-primary);
  background: var(--c-accent-primary-glow);
}
.material-summary-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}
.material-summary-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 600;
}
.material-summary.is-ready .material-summary-badge {
  color: var(--c-accent-primary);
  border-color: var(--c-accent-primary);
}
.material-summary-stats {
  display: flex;
  gap: 22px;
  flex-wrap: wrap;
}
.material-summary-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 90px;
}
.material-summary-stat .stat-label {
  color: var(--c-text-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.material-summary-stat strong {
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.material-guidance {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 4px;
  color: var(--c-text-secondary);
  font-size: 12.5px;
  line-height: 1.55;
}

.material-report-cta {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(130deg, rgba(30, 117, 255, 0.08), rgba(30, 117, 255, 0.02) 70%);
  border: 1px solid rgba(30, 117, 255, 0.22);
  flex-wrap: wrap;
}
.material-report-copy { min-width: 0; flex: 1 1 280px; }
.material-report-copy strong {
  display: block;
  margin-bottom: 4px;
  font-family: var(--font-serif);
  font-size: 14px;
  color: var(--c-text-primary);
}
.material-report-copy p {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--c-text-secondary);
}

.material-error-banner {
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid rgba(178, 59, 46, 0.32);
  background: rgba(178, 59, 46, 0.08);
  color: var(--c-text-secondary);
  font-size: 13px;
}

.material-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.material-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}
.material-card.is-uploaded {
  border-color: var(--c-accent-primary);
}
.material-card:hover {
  border-color: var(--c-border-glass-hover);
}

.material-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
.material-card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--c-text-primary);
}
.material-card-title strong {
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
}

.material-status-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.material-status-pill.is-ok {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}
.material-status-pill.is-warn {
  background: rgba(178, 59, 46, 0.1);
  color: #b23b2e;
}

.material-hint {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 12.5px;
  line-height: 1.55;
}

.material-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  border: 1px solid var(--c-border-glass);
}
.material-meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.material-meta dt {
  color: var(--c-text-muted);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.material-meta dd {
  margin: 0;
  color: var(--c-text-primary);
  font-size: 12.5px;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: auto;
}

@media (max-width: 640px) {
  .material-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* ---------------- Teaching reform section ---------------- */
.reform-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
  color: var(--c-text-muted);
  font-size: 13px;
}

.reform-loading p {
  margin: 0;
}

.reform-error {
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid rgba(178, 59, 46, 0.32);
  background: rgba(178, 59, 46, 0.08);
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.reform-raw {
  margin: 0;
  padding: 12px 14px;
  max-height: 320px;
  overflow: auto;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------------- Empty state ---------------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 30px 20px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px;
  color: var(--c-text-muted);
  font-size: 13px;
}

.empty-state :deep(svg) {
  color: var(--c-text-faint);
}

/* ---------------- Loading ---------------- */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 0;
  color: var(--c-text-muted);
  font-size: 13px;
}

.tv-skel { display: flex; flex-direction: column; gap: 16px; padding: 8px 0; }
.tv-skel-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.reform-loading, .material-loading { padding: 8px 0 4px; }

/* ---------------- Responsive ---------------- */
@media (max-width: 1024px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .workspace-metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* Sidebar collapses into a horizontal chip strip at the top of the main
   column. The shell flattens to a single column so the chip strip sits
   directly above the content. */
@media (max-width: 900px) {
  .teacher-shell {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }

  .teacher-sidebar {
    position: sticky;
    top: 0;
    z-index: 5;
    max-height: none;
    overflow-x: auto;
    overflow-y: hidden;
    border-right: none;
    border-bottom: 1px solid var(--c-border-glass);
    background: var(--c-bg-base-elevated);
  }

  .teacher-sidebar-inner {
    flex-direction: row;
    flex-wrap: nowrap;
    gap: 18px;
    padding: 10px 12px;
    min-width: max-content;
  }

  .teacher-hero-title {
    display: none;
  }

  .teacher-nav-group {
    flex-direction: row;
    align-items: center;
    gap: 6px;
  }

  .teacher-nav-group-label {
    padding: 0 4px 0 0;
    white-space: nowrap;
    font-size: 10px;
  }

  .teacher-nav-list {
    flex-direction: row;
    gap: 6px;
  }

  .teacher-nav-link {
    padding: 6px 12px;
    border-radius: 999px;
    border: 1px solid var(--c-border-glass);
    background: var(--c-bg-base-elevated);
    white-space: nowrap;
  }

  /* On mobile/pill layout swap the left-bar for a bottom-bar indicator so the
     indicator reads naturally along the horizontal axis. */
  .teacher-nav-link.is-active {
    border-color: var(--c-accent-primary);
  }
  .teacher-nav-link.is-active::before {
    left: 10px;
    right: 10px;
    top: auto;
    bottom: 2px;
    width: auto;
    height: 2px;
  }
}

@media (max-width: 640px) {
  .workspace-metric-strip {
    grid-template-columns: 1fr;
  }
}
</style>
