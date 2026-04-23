<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { mapErrorMessage } from '../utils/errorMap'
import {
  deleteCurriculum,
  deleteTeacherMaterialAsset,
  downloadCurriculumTemplate,
  downloadTeacherMaterialTemplate,
  fetchCurriculums,
  fetchTeacherMarketMatch,
  fetchTeacherMaterialStatus,
  fetchTeachingReform,
  invalidateApiCache,
  replaceCurriculumExcel,
  updateCurriculum,
  updateTeacherMaterialAsset,
  uploadCurriculumExcel,
  uploadTeacherMaterial
} from '../api'
import {
  AlertTriangle,
  BookOpen,
  CheckCircle2,
  Download,
  FileSpreadsheet,
  FolderOpen,
  Pencil,
  Trash2,
  Upload,
  X
} from 'lucide-vue-next'

const authStore = useAuthStore()
const router = useRouter()
const { success, error } = useToast()

const loading = ref(true)
const curriculums = ref([])
const curriculumTotal = ref(0)
const matchResult = ref(null)

const teachingReform = ref(null)
const reformLoading = ref(false)
const reformError = ref('')

const selectedMajor = ref('')

// ---------- Materials (教师素材) ----------
const materialStatus = ref(null)
const materialLoading = ref(false)
const materialError = ref('')
const materialUploading = ref({})
const materialDownloading = ref({})
const materialDeleting = ref({})
const materialDeleteDialog = ref(createEmptyMaterialDeleteDialog())

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

function createEmptyMaterialDeleteDialog() {
  return {
    open: false,
    type: '',
    title: '',
    description: '',
    detail: '',
    confirmText: '确认删除'
  }
}
const REFORM_REQUIRED_MATERIALS = ['CURRICULUM', 'SYLLABUS', 'STUDENT_STATUS']
const REFORM_PROMPT_COPY = {
  CURRICULUM: {
    title: '请先上传课程清单 Excel',
    description: '教师报告依赖课程结构数据，当前未检测到可用课程清单。'
  },
  SYLLABUS: {
    title: '请先上传教学大纲 Excel',
    description: '教师报告依赖教学大纲字段，当前未检测到上传记录。'
  },
  STUDENT_STATUS: {
    title: '请先上传学生情况 Excel',
    description: '教师报告依赖学生能力与就业状态数据，当前未检测到上传记录。'
  }
}
const reformPendingMaterials = computed(() => {
  const byType = new Map(materialItems.value.map((item) => [item.type, item]))
  return REFORM_REQUIRED_MATERIALS
    .map((type) => {
      const item = byType.get(type) || {}
      const copy = REFORM_PROMPT_COPY[type] || {}
      const label = item.label || materialTypeLabel(type)
      return {
        type,
        label,
        uploaded: Boolean(item.uploaded),
        title: copy.title || `请先上传${label}`,
        description: item.hint || copy.description || `${label}是生成教改建议的必要前置资料。`,
        actionLabel: uploadActionLabel(type)
      }
    })
    .filter((item) => !item.uploaded)
})
const reformUploadedCount = computed(
  () => REFORM_REQUIRED_MATERIALS.length - reformPendingMaterials.value.length
)
const reformPrereqTitle = computed(() => {
  const pending = reformPendingMaterials.value
  if (!pending.length) return '请先上传教学资料 Excel'
  if (pending.length === 1) return pending[0].title
  const labels = pending
    .map((item) => item.label || materialTypeLabel(item.type))
    .map((label) => String(label || '').trim())
    .filter(Boolean)
  return labels.length ? `请先上传${labels.join('、')}` : '请先上传教学资料 Excel'
})
const showReformPrerequisite = computed(
  () => Boolean(materialStatus.value) && reformPendingMaterials.value.length > 0
)
function formatTimestamp(input) {
  const date = input instanceof Date ? input : new Date(input)
  if (!date || Number.isNaN(date.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// ---------- Teaching reform extracted blocks ----------
const reformActions = computed(() => {
  const raw = teachingReform.value?.blueprint?.reformActions
  return Array.isArray(raw) ? raw : []
})
const reformAssessments = computed(() => {
  const raw = teachingReform.value?.blueprint?.assessmentSuggestions
  return Array.isArray(raw) ? raw : []
})
const governanceDimensions = computed(() => {
  const raw = teachingReform.value?.governanceScorecard?.dimensions
  return Array.isArray(raw) ? raw : []
})
const governanceRisks = computed(() => {
  const raw = teachingReform.value?.governanceScorecard?.risks
  return Array.isArray(raw) ? raw : []
})
const materialReadiness = computed(() => {
  const raw = teachingReform.value?.blueprint?.materialReadiness
  return Array.isArray(raw) ? raw : []
})
const reformHasItems = computed(
  () =>
    reformActions.value.length > 0 ||
    reformAssessments.value.length > 0 ||
    governanceDimensions.value.length > 0 ||
    governanceRisks.value.length > 0 ||
    materialReadiness.value.length > 0
)

// ---------- Derived cards / filters ----------
const overviewCards = computed(() => [
  { label: '课程清单入库', value: curriculumTotal.value, hint: '仅统计课程清单 Excel' },
  { label: '技能覆盖率', value: matchResult.value?.coverageRate || '--', hint: '对市场热门技能' },
  { label: '市场缺口', value: matchResult.value?.marketGaps?.length || 0, hint: '待补齐技能项' }
])

const majorOptions = computed(() => {
  const values = new Set()
  curriculums.value.forEach((item) => item?.major && values.add(String(item.major).trim()))
  if (teachingReform.value?.major) values.add(String(teachingReform.value.major).trim())
  return [...values].filter(Boolean)
})

const filteredCurriculums = computed(() => {
  if (!selectedMajor.value) return curriculums.value
  return curriculums.value.filter((item) => String(item.major || '').trim() === selectedMajor.value)
})

const activeMajorLabel = computed(() => {
  const major = selectedMajor.value || matchResult.value?.major || teachingReform.value?.major || ''
  return String(major).trim() || '专业待推断'
})

const topGapSkills = computed(() => (matchResult.value?.marketGaps || []).slice(0, 5))
const topCoveredSkills = computed(() => (matchResult.value?.coveredSkills || []).slice(0, 8))

// ---------- Curriculum form (课程库) ----------
const savingCurriculum = ref(false)
const editingCurriculumId = ref(null)
const deleteTarget = ref(null)
const deletingCurriculum = ref(false)
const curriculumForm = ref(createEmptyCurriculumForm())
function createEmptyCurriculumForm() {
  return {
    courseName: '',
    courseCode: '',
    department: '',
    major: '',
    credit: '',
    semester: '',
    description: '',
    keywords: ''
  }
}
function resetCurriculumForm() {
  editingCurriculumId.value = null
  curriculumForm.value = createEmptyCurriculumForm()
}
function handleEditCurriculum(item) {
  editingCurriculumId.value = item.id
  curriculumForm.value = {
    courseName: item.courseName || '',
    courseCode: item.courseCode || '',
    department: item.department || '',
    major: item.major || '',
    credit: item.credit ?? '',
    semester: item.semester || '',
    description: item.description || '',
    keywords: Array.isArray(item.keywords) ? item.keywords.join(', ') : (item.keywords || '')
  }
}

function askDeleteCurriculum(item) {
  deleteTarget.value = item
}

function cancelDeleteCurriculum() {
  if (deletingCurriculum.value) return
  deleteTarget.value = null
}

// ---------- Sidebar navigation ----------
const navGroups = [
  {
    title: '',
    items: [
      { id: 'section-materials', label: '素材管理' },
      { id: 'section-overview', label: '概览' },
      { id: 'section-teaching-reform', label: '教改建议' }
    ]
  },
  {
    title: '课程管理',
    items: [
      { id: 'section-analysis', label: '供需分析' },
      { id: 'section-courses', label: '课程清单' }
    ]
  }
]

const activeSection = ref('section-materials')
let observer = null
let scrollSyncCleanup = null
let scrollSyncFrame = 0

function getMainScrollContainer() {
  const scrollContainer = document.querySelector('.main-content')
  return scrollContainer instanceof HTMLElement ? scrollContainer : null
}

function scrollToSection(sectionId) {
  const element = document.getElementById(sectionId)
  if (!element) return
  const scrollContainer = getMainScrollContainer()

  if (scrollContainer) {
    const containerRect = scrollContainer.getBoundingClientRect()
    const elementRect = element.getBoundingClientRect()
    const targetTop = scrollContainer.scrollTop + elementRect.top - containerRect.top - 12
    scrollContainer.scrollTo({ top: Math.max(targetTop, 0), behavior: 'smooth' })
    activeSection.value = sectionId
    return
  }

  const targetTop = element.getBoundingClientRect().top + window.scrollY - 12
  window.scrollTo({ top: Math.max(targetTop, 0), behavior: 'smooth' })
  activeSection.value = sectionId
}

function syncActiveSectionFromScroll(sections, scrollContainer) {
  const sectionList = sections?.length
    ? Array.from(sections)
    : Array.from(document.querySelectorAll('.teacher-section'))
  if (!sectionList.length) return

  const container = scrollContainer || getMainScrollContainer()
  const containerTop = container ? container.getBoundingClientRect().top : 0
  const containerHeight = container ? container.clientHeight : window.innerHeight
  const activationLine = containerTop + Math.min(Math.max(containerHeight * 0.12, 48), 88)

  const scrollTop = container ? container.scrollTop : window.scrollY
  const scrollHeight = container ? container.scrollHeight : document.documentElement.scrollHeight
  const clientHeight = container ? container.clientHeight : window.innerHeight

  let current = sectionList[0]
  if (scrollTop + clientHeight >= scrollHeight - 8) {
    current = sectionList[sectionList.length - 1]
  } else {
    for (const section of sectionList) {
      const rect = section.getBoundingClientRect()
      if (rect.top <= activationLine) current = section
      else break
    }
  }

  if (current?.id && activeSection.value !== current.id) {
    activeSection.value = current.id
  }
}

function setupObserver() {
  if (typeof IntersectionObserver === 'undefined') return
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (scrollSyncCleanup) {
    scrollSyncCleanup()
    scrollSyncCleanup = null
  }

  const sections = Array.from(document.querySelectorAll('.teacher-section'))
  if (!sections.length) return

  const root = getMainScrollContainer()

  observer = new IntersectionObserver(
    () => syncActiveSectionFromScroll(sections, root),
    {
      root,
      rootMargin: '-20% 0px -60% 0px',
      threshold: 0
    }
  )

  sections.forEach((section) => observer.observe(section))

  const scrollTarget = root || window
  const handleScroll = () => {
    if (scrollSyncFrame) return
    scrollSyncFrame = requestAnimationFrame(() => {
      scrollSyncFrame = 0
      syncActiveSectionFromScroll(sections, root)
    })
  }
  scrollTarget.addEventListener('scroll', handleScroll, { passive: true })
  scrollSyncCleanup = () => {
    scrollTarget.removeEventListener('scroll', handleScroll)
    if (scrollSyncFrame) {
      cancelAnimationFrame(scrollSyncFrame)
      scrollSyncFrame = 0
    }
  }
  syncActiveSectionFromScroll(sections, root)
}

// ---------- Loading ----------
function majorParams() {
  return selectedMajor.value ? { major: selectedMajor.value } : {}
}

async function loadData({ silent = false } = {}) {
  if (![1, 2].includes(authStore.user?.roleType)) {
    loading.value = false
    return
  }

  if (!silent) loading.value = true
  try {
    // 供需分析按专业传参；课程清单拉全量，专业筛选只在前端做，否则会出现：
    // 1) 切专业后「课程清单入库」跟着变小；2) majorOptions 塌到只剩当前筛选的那一个
    const [matchRes, curriculumRes] = await Promise.allSettled([
      fetchTeacherMarketMatch(authStore.token, majorParams()),
      fetchCurriculums(authStore.token, { page: 1, pageSize: 200 })
    ])

    matchResult.value = matchRes.status === 'fulfilled' ? matchRes.value : null
    curriculums.value = curriculumRes.status === 'fulfilled' ? curriculumRes.value.data : []
    curriculumTotal.value = curriculumRes.status === 'fulfilled'
      ? (curriculumRes.value.total ?? curriculumRes.value.data?.length ?? 0)
      : 0
  } catch (e) {
    if (!silent) error(`教师工作台加载失败：${mapErrorMessage(e)}`)
  } finally {
    loading.value = false
  }
}

async function loadMaterialStatus({ silent = false } = {}) {
  if (![1, 2].includes(authStore.user?.roleType)) return
  if (!silent) materialLoading.value = true
  materialError.value = ''
  try {
    const result = await fetchTeacherMaterialStatus(authStore.token, majorParams())
    materialStatus.value = result || null
  } catch (e) {
    materialError.value = mapErrorMessage(e)
  } finally {
    materialLoading.value = false
  }
}

async function loadTeachingReform({ silent = false } = {}) {
  if (!silent) reformLoading.value = true
  reformError.value = ''
  try {
    const result = await fetchTeachingReform(authStore.token, majorParams())
    teachingReform.value = result || null
  } catch (e) {
    reformError.value = mapErrorMessage(e)
  } finally {
    reformLoading.value = false
  }
}

async function handleMajorChange(major) {
  if (selectedMajor.value === major) return
  selectedMajor.value = major
  // The per-major responses share URL keys with different query strings, but our mem cache keys
  // on path, so the different query string already yields distinct entries — no invalidation needed.
  await Promise.allSettled([
    loadData({ silent: true }),
    loadMaterialStatus({ silent: true }),
    loadTeachingReform({ silent: true })
  ])
}

function invalidateTeacherDomain() {
  invalidateApiCache('/teacher/')
  invalidateApiCache('/curriculum')
  invalidateApiCache('/analysis/')
}

// ---------- Material actions ----------
async function handleDownloadTemplate(materialType) {
  if (materialDownloading.value[materialType]) return
  materialDownloading.value = { ...materialDownloading.value, [materialType]: true }
  try {
    const blob = materialType === 'CURRICULUM'
      ? await downloadCurriculumTemplate(authStore.token)
      : await downloadTeacherMaterialTemplate(authStore.token, materialType)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${materialTypeLabel(materialType)}模板.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    success(`${materialTypeLabel(materialType)}模板已开始下载`)
  } catch (e) {
    error(mapErrorMessage(e))
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
      const hasExisting = Boolean(materialItems.value.find((item) => item.type === 'CURRICULUM')?.uploaded)
      const result = hasExisting
        ? await replaceCurriculumExcel(authStore.token, file)
        : await uploadCurriculumExcel(authStore.token, file)
      if (hasExisting) {
        success(`课程清单已替换：停用 ${result.replaced || 0} 条旧记录，导入 ${result.imported || 0} 条新记录`)
      } else {
        success(`课程 Excel 导入完成：成功 ${result.imported || 0} 条，映射技能 ${result.mappedSkills || 0} 条。`)
      }
    } else {
      const assetId = materialItems.value.find((item) => item.type === materialType)?.assetId
      if (assetId) {
        await updateTeacherMaterialAsset(authStore.token, assetId, file, selectedMajor.value || '')
        success(`${materialTypeLabel(materialType)}已替换`)
      } else {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('materialType', materialType)
        if (selectedMajor.value) formData.append('major', selectedMajor.value)
        await uploadTeacherMaterial(authStore.token, formData)
        success(`${materialTypeLabel(materialType)}上传成功`)
      }
    }
    invalidateTeacherDomain()
    await loadMaterialStatus({ silent: true })
    if (materialType === 'CURRICULUM') {
      await loadData({ silent: true })
      loadTeachingReform({ silent: true })
    }
  } catch (e) {
    error(mapErrorMessage(e))
  } finally {
    const next = { ...materialUploading.value }
    delete next[materialType]
    materialUploading.value = next
  }
}

async function handleDeleteMaterial(materialType) {
  if (materialType === 'CURRICULUM') {
    const total = Number(curriculumTotal.value) || 0
    if (!total) {
      error('当前没有可删除的课程清单记录。')
      return
    }
    if (materialDeleting.value[materialType]) return
    materialDeleting.value = { ...materialDeleting.value, [materialType]: true }
    try {
      const records = await fetchAllCurriculumRecords()
      const deletableIds = records.map((item) => item?.id).filter(Boolean)
      if (!deletableIds.length) {
        error('未找到可删除的课程清单记录，请刷新后重试。')
        return
      }

      const failures = []
      for (let index = 0; index < deletableIds.length; index += 20) {
        const batch = deletableIds.slice(index, index + 20)
        const results = await Promise.allSettled(
          batch.map((id) => deleteCurriculum(authStore.token, id))
        )
        results.forEach((result, resultIndex) => {
          if (result.status === 'rejected') failures.push(batch[resultIndex])
        })
      }

      resetCurriculumForm()
      deleteTarget.value = null
      selectedMajor.value = ''
      invalidateTeacherDomain()
      await Promise.allSettled([
        loadData({ silent: true }),
        loadMaterialStatus({ silent: true }),
        loadTeachingReform({ silent: true })
      ])

      if (failures.length) {
        error(`课程清单已删除大部分内容，但仍有 ${failures.length} 条记录删除失败，请重试。`)
      } else {
        success(`课程清单已删除：共清空 ${deletableIds.length} 条记录。`)
      }
    } catch (e) {
      error(mapErrorMessage(e))
    } finally {
      const next = { ...materialDeleting.value }
      delete next[materialType]
      materialDeleting.value = next
    }
    return
  }
  const assetId = materialItems.value.find((item) => item.type === materialType)?.assetId
  if (!assetId) return
  if (materialDeleting.value[materialType]) return
  materialDeleting.value = { ...materialDeleting.value, [materialType]: true }
  try {
    await deleteTeacherMaterialAsset(authStore.token, assetId)
    success(`${materialTypeLabel(materialType)}已删除`)
    invalidateTeacherDomain()
    await Promise.allSettled([
      loadMaterialStatus({ silent: true }),
      loadTeachingReform({ silent: true })
    ])
  } catch (e) {
    error(mapErrorMessage(e))
  } finally {
    const next = { ...materialDeleting.value }
    delete next[materialType]
    materialDeleting.value = next
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

function materialStorageTarget(materialType) {
  return materialType === 'CURRICULUM' ? '课程库' : '资料归档'
}

async function fetchAllCurriculumRecords() {
  const total = Number(curriculumTotal.value) || 0
  if (!total) return []

  if (curriculums.value.length >= total) {
    return curriculums.value
  }

  const pageSize = 200
  const pages = Math.max(1, Math.ceil(total / pageSize))
  const all = []

  for (let page = 1; page <= pages; page += 1) {
    const result = await fetchCurriculums(authStore.token, { page, pageSize })
    if (Array.isArray(result.data)) {
      all.push(...result.data)
    }
    if (!result.data?.length) break
  }

  return all
}

function cleanMaterialLabel(label) {
  return String(label || '').replace(/\s*Excel$/i, '').trim()
}

function uploadActionLabel(materialType) {
  return `上传${cleanMaterialLabel(materialTypeLabel(materialType))}`
}

function canDeleteMaterial(item) {
  if (!item?.uploaded) return false
  if (item.type === 'CURRICULUM') return (Number(curriculumTotal.value) || 0) > 0
  return Boolean(item.assetId)
}

function materialDeleteCopy(item) {
  if (item?.type === 'CURRICULUM') {
    return {
      title: '删除课程清单资料',
      description: '删除后会清空当前导入的全部课程条目，并重新计算供需分析与教改建议，该操作不可撤销。'
    }
  }
  if (item?.type === 'SYLLABUS') {
    return {
      title: '删除教学大纲资料',
      description: '删除后课程目标、能力点和考核方式数据会失效，相关分析需要重新上传后才能恢复。'
    }
  }
  return {
    title: '删除学生情况资料',
    description: '删除后学生基础、能力短板与就业状态数据会失效，相关分析需要重新上传后才能恢复。'
  }
}

function materialDeleteDetail(item) {
  if (!item) return ''
  if (item.type === 'CURRICULUM') {
    const count = Number(curriculumTotal.value) || Number(item.recordCount) || 0
    return `将删除 ${count} 条课程记录${item.latestFileName ? `，最近文件：${item.latestFileName}` : ''}。`
  }

  const parts = []
  if (item.latestFileName) parts.push(`最近文件：${item.latestFileName}`)
  if (item.latestUploadedAt) parts.push(`更新时间：${formatTimestamp(item.latestUploadedAt)}`)
  if (Number.isFinite(Number(item.recordCount))) parts.push(`记录数：${item.recordCount}`)
  return parts.join(' · ')
}

function requestDeleteMaterial(item) {
  if (!canDeleteMaterial(item)) return
  const copy = materialDeleteCopy(item)
  materialDeleteDialog.value = {
    open: true,
    type: item.type,
    title: copy.title,
    description: copy.description,
    detail: materialDeleteDetail(item),
    confirmText: '确认删除'
  }
}

function cancelDeleteMaterialDialog() {
  if (materialDeleting.value[materialDeleteDialog.value.type]) return
  materialDeleteDialog.value = createEmptyMaterialDeleteDialog()
}

async function confirmDeleteMaterialDialog() {
  const type = materialDeleteDialog.value.type
  if (!type) return
  await handleDeleteMaterial(type)
  materialDeleteDialog.value = createEmptyMaterialDeleteDialog()
}

function handleReformUpload(materialType) {
  scrollToSection('section-materials')
  pickMaterialFile(materialType)
}

function jumpToMaterials() {
  scrollToSection('section-materials')
}

// ---------- Curriculum actions ----------
async function handleSubmitCurriculum() {
  if (!editingCurriculumId.value || savingCurriculum.value) return
  if (!curriculumForm.value.courseName?.trim()) {
    error('请填写课程名称')
    return
  }
  savingCurriculum.value = true
  try {
    const payload = {
      courseName: curriculumForm.value.courseName,
      courseCode: curriculumForm.value.courseCode || null,
      department: curriculumForm.value.department || null,
      major: curriculumForm.value.major || null,
      credit: curriculumForm.value.credit === '' ? null : Number(curriculumForm.value.credit),
      semester: curriculumForm.value.semester || null,
      description: curriculumForm.value.description || null,
      keywords: String(curriculumForm.value.keywords || '')
        .split(/[,\uFF0C]/)
        .map((item) => item.trim())
        .filter(Boolean)
    }
    await updateCurriculum(authStore.token, editingCurriculumId.value, payload)
    success('课程库条目已更新')
    resetCurriculumForm()
    invalidateTeacherDomain()
    await Promise.allSettled([
      loadData({ silent: true }),
      loadMaterialStatus({ silent: true }),
      loadTeachingReform({ silent: true })
    ])
  } catch (e) {
    error(mapErrorMessage(e))
  } finally {
    savingCurriculum.value = false
  }
}

async function confirmDeleteCurriculum() {
  const target = deleteTarget.value
  if (!target || deletingCurriculum.value) return
  deletingCurriculum.value = true
  try {
    await deleteCurriculum(authStore.token, target.id)
    if (editingCurriculumId.value === target.id) resetCurriculumForm()
    success('课程库条目已删除')
    deleteTarget.value = null
    invalidateTeacherDomain()
    await Promise.allSettled([
      loadData({ silent: true }),
      loadMaterialStatus({ silent: true }),
      loadTeachingReform({ silent: true })
    ])
  } catch (e) {
    error(mapErrorMessage(e))
  } finally {
    deletingCurriculum.value = false
  }
}

// ---------- Lifecycle ----------
onMounted(async () => {
  await loadData()
  loadTeachingReform()
  loadMaterialStatus()
  await nextTick()
  setupObserver()
})

watch(loading, async () => {
  await nextTick()
  setupObserver()
})

onBeforeUnmount(() => {
  if (scrollSyncCleanup) {
    scrollSyncCleanup()
    scrollSyncCleanup = null
  }
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
        <SkeletonCard v-for="i in 4" :key="`s-${i}`" type="stat" />
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
            :key="group.title || 'overview'"
            class="teacher-nav-group"
            :aria-label="group.title || '概览'"
          >
            <div v-if="group.title" class="teacher-nav-group-label">{{ group.title }}</div>
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
                      <dt>存放位置</dt>
                      <dd>{{ materialStorageTarget(item.type) }}</dd>
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

                  <div class="material-actions" :class="{ 'has-two-actions': !canDeleteMaterial(item) }">
                    <GlowButton
                      variant="ghost"
                      :loading="!!materialDownloading[item.type]"
                      @click="handleDownloadTemplate(item.type)"
                    >
                      <Download :size="14" />
                      下载模板
                    </GlowButton>
                    <GlowButton
                      v-if="canDeleteMaterial(item)"
                      variant="ghost"
                      class="danger-btn"
                      :loading="!!materialDeleting[item.type]"
                      @click="requestDeleteMaterial(item)"
                    >
                      <Trash2 :size="14" />
                      删除资料
                    </GlowButton>
                    <GlowButton
                      variant="primary"
                      :loading="!!materialUploading[item.type]"
                      @click="pickMaterialFile(item.type)"
                    >
                      <Upload :size="14" />
                      {{ item.uploaded ? '替换上传' : '上传文件' }}
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

        <section id="section-overview" class="teacher-section teacher-major-filter panel">
          <div class="panel-body teacher-major-filter-body">
            <div class="teacher-major-field">
              <span class="teacher-major-label">专业视角</span>
              <strong class="teacher-major-value">{{ activeMajorLabel }}</strong>
            </div>
            <p class="teacher-major-hint">
              系统会根据课程清单自动推断专业；如需按某个具体专业查看，可在下方切换。
            </p>
            <div v-if="majorOptions.length" class="teacher-major-chips">
              <button
                type="button"
                class="major-chip"
                :class="{ 'is-active': !selectedMajor }"
                @click="handleMajorChange('')"
              >
                全部专业
              </button>
              <button
                v-for="major in majorOptions"
                :key="major"
                type="button"
                class="major-chip"
                :class="{ 'is-active': selectedMajor === major }"
                @click="handleMajorChange(major)"
              >
                {{ major }}
              </button>
            </div>
          </div>
        </section>

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

        <article id="section-teaching-reform" class="teacher-section panel">
          <header class="panel-head">
            <h2 class="panel-title">教改建议</h2>
          </header>
          <div class="panel-body">
            <div v-if="(reformLoading && !teachingReform) || (materialLoading && !materialStatus)" class="reform-loading">
              <SkeletonCard type="list" :lines="4" />
            </div>

            <div v-else-if="showReformPrerequisite" class="reform-prereq">
              <div class="reform-prereq-copy">
                <h3>{{ reformPrereqTitle }}</h3>
              </div>

              <div class="reform-prereq-actions">
                <button type="button" class="reform-prereq-cta is-ghost" @click="jumpToMaterials">
                  <FolderOpen :size="14" />
                  <span>去素材管理</span>
                </button>
              </div>
            </div>

            <div v-else-if="reformError" class="reform-error">
              {{ reformError }}
            </div>

            <template v-else-if="teachingReform && reformHasItems">
              <div v-if="governanceDimensions.length" class="reform-subsection">
                <h3 class="reform-subtitle">治理维度</h3>
                <div class="reform-mini-grid">
                  <div v-for="item in governanceDimensions" :key="item.label" class="mini-card">
                    <div class="mini-card-head">
                      <strong>{{ item.label }}</strong>
                      <span class="mini-score">{{ item.score ?? '--' }}</span>
                    </div>
                    <p>{{ item.evidence || '暂无证据说明' }}</p>
                  </div>
                </div>
              </div>

              <div v-if="governanceRisks.length" class="reform-subsection">
                <h3 class="reform-subtitle">治理风险</h3>
                <ul class="plain-list">
                  <li v-for="(risk, index) in governanceRisks" :key="`risk-${index}`">
                    <strong v-if="risk.label">{{ risk.label }}</strong>
                    <span>{{ risk.detail || risk.description || risk }}</span>
                  </li>
                </ul>
              </div>

              <div v-if="materialReadiness.length" class="reform-subsection">
                <h3 class="reform-subtitle">资料就绪情况</h3>
                <div class="reform-mini-grid">
                  <div v-for="item in materialReadiness" :key="item.name" class="mini-card">
                    <div class="mini-card-head">
                      <strong>{{ item.name }}</strong>
                      <span class="pill" :class="{ 'is-ok': item.ready }">
                        {{ item.ready ? '已就绪' : '未就绪' }}
                      </span>
                    </div>
                    <p>{{ item.detail || '' }}</p>
                  </div>
                </div>
              </div>

              <div v-if="reformActions.length" class="reform-subsection">
                <h3 class="reform-subtitle">教改动作</h3>
                <div class="insight-list">
                  <div
                    v-for="(item, index) in reformActions"
                    :key="`action-${index}`"
                    class="insight-card"
                  >
                    <div class="insight-head">
                      <strong>{{ item.title || `建议 ${index + 1}` }}</strong>
                      <span v-if="item.priority" class="pill">{{ item.priority }}</span>
                    </div>
                    <p>{{ item.detail || '' }}</p>
                  </div>
                </div>
              </div>

              <div v-if="reformAssessments.length" class="reform-subsection">
                <h3 class="reform-subtitle">考核建议</h3>
                <div class="insight-list">
                  <div
                    v-for="(item, index) in reformAssessments"
                    :key="`assessment-${index}`"
                    class="insight-card"
                  >
                    <strong>{{ item.label || `考核 ${index + 1}` }}</strong>
                    <p>{{ item.detail || '' }}</p>
                  </div>
                </div>
              </div>
            </template>

            <div v-else class="empty-state">
              <BookOpen :size="26" />
              <p>暂无教改建议。</p>
            </div>
          </div>
        </article>

        <article id="section-analysis" class="teacher-section panel">
          <header class="panel-head panel-head-row">
            <h2 class="panel-title">供需分析重点</h2>
            <router-link to="/teacher/supply-demand" class="panel-link">
              查看完整分析 →
            </router-link>
          </header>
          <div class="panel-body">
            <div class="analysis-block">
              <div class="rate-card">
                <span class="rate-major">{{ activeMajorLabel }}</span>
                <span class="rate-label">覆盖率</span>
                <strong>{{ matchResult?.coverageRate || '--' }}</strong>
                <p>课程内容与{{ matchResult?.scope === 'major-related-jobs' ? '该专业相关岗位' : '市场热门技能' }}的贴合程度。</p>
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
          <header class="panel-head panel-head-row">
            <h2 class="panel-title">课程清单</h2>
            <span class="panel-muted">当前 {{ filteredCurriculums.length }} 条</span>
          </header>
          <div class="panel-body">
            <div v-if="filteredCurriculums.length" class="curriculum-list">
              <div v-for="item in filteredCurriculums" :key="item.id" class="curriculum-item">
                <div class="curriculum-head">
                  <strong>{{ item.courseName }}</strong>
                  <span class="course-chip">{{ item.major || '未填写专业' }}</span>
                </div>
                <p v-if="item.description">{{ item.description }}</p>
                <div class="course-meta">
                  <span>{{ item.department || '院系未填写' }}</span>
                  <span>{{ item.semester || '学期未填写' }}</span>
                  <span>{{ item.credit ?? '--' }} 学分</span>
                </div>
                <div class="button-row">
                  <GlowButton variant="ghost" @click="handleEditCurriculum(item)">
                    <Pencil :size="14" />
                    编辑
                  </GlowButton>
                  <GlowButton variant="ghost" class="danger-btn" @click="askDeleteCurriculum(item)">
                    <Trash2 :size="14" />
                    删除
                  </GlowButton>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">
              <BookOpen :size="26" />
              <p>暂无课程清单入库记录，请先在「素材管理」中上传课程清单 Excel。</p>
            </div>
          </div>
        </article>
      </div>
    </div>

    <ConfirmDialog
      :open="materialDeleteDialog.open"
      :title="materialDeleteDialog.title"
      :description="materialDeleteDialog.description"
      :detail="materialDeleteDialog.detail"
      :confirm-text="materialDeleteDialog.confirmText"
      cancel-text="再想想"
      variant="danger"
      :loading="!!materialDeleting[materialDeleteDialog.type]"
      @confirm="confirmDeleteMaterialDialog"
      @cancel="cancelDeleteMaterialDialog"
      @update:open="(v) => { if (!v) cancelDeleteMaterialDialog() }"
    />

    <!-- 课程清单编辑弹窗 -->
    <Teleport to="body">
      <div
        v-if="editingCurriculumId"
        class="tv-modal-backdrop"
        @click.self="resetCurriculumForm"
      >
        <div class="tv-modal" role="dialog" aria-modal="true" aria-labelledby="tv-edit-title">
          <header class="tv-modal-head">
            <div class="tv-modal-title">
              <Pencil :size="16" />
              <h3 id="tv-edit-title">编辑课程清单条目</h3>
            </div>
            <button
              type="button"
              class="tv-modal-close"
              aria-label="关闭"
              :disabled="savingCurriculum"
              @click="resetCurriculumForm"
            >
              <X :size="16" />
            </button>
          </header>
          <form class="tv-modal-body" @submit.prevent="handleSubmitCurriculum">
            <div class="form-grid">
              <label class="field-block">
                <span class="field-label">课程名称</span>
                <input v-model="curriculumForm.courseName" class="panel-input" placeholder="课程名称" required />
              </label>
              <label class="field-block">
                <span class="field-label">课程代码</span>
                <input v-model="curriculumForm.courseCode" class="panel-input" placeholder="课程代码" />
              </label>
              <label class="field-block">
                <span class="field-label">院系</span>
                <input v-model="curriculumForm.department" class="panel-input" placeholder="院系" />
              </label>
              <label class="field-block">
                <span class="field-label">专业</span>
                <input v-model="curriculumForm.major" class="panel-input" placeholder="专业" />
              </label>
              <label class="field-block">
                <span class="field-label">学分</span>
                <input v-model="curriculumForm.credit" type="number" step="0.5" class="panel-input" placeholder="学分" />
              </label>
              <label class="field-block">
                <span class="field-label">开课学期</span>
                <input v-model="curriculumForm.semester" class="panel-input" placeholder="开课学期" />
              </label>
              <label class="field-block field-span-2">
                <span class="field-label">技能关键词（逗号分隔）</span>
                <input v-model="curriculumForm.keywords" class="panel-input" placeholder="例如：Python, 机器学习" />
              </label>
              <label class="field-block field-span-2">
                <span class="field-label">课程描述</span>
                <textarea v-model="curriculumForm.description" class="panel-textarea" rows="3" placeholder="课程描述" />
              </label>
            </div>
            <footer class="tv-modal-footer">
              <GlowButton variant="ghost" type="button" :disabled="savingCurriculum" @click="resetCurriculumForm">
                取消
              </GlowButton>
              <GlowButton variant="primary" type="submit" :loading="savingCurriculum">
                <Pencil :size="14" />
                保存修改
              </GlowButton>
            </footer>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- 课程清单删除确认弹窗 -->
    <Teleport to="body">
      <div
        v-if="deleteTarget"
        class="tv-modal-backdrop"
        @click.self="cancelDeleteCurriculum"
      >
        <div class="tv-modal tv-modal-confirm" role="alertdialog" aria-modal="true" aria-labelledby="tv-del-title">
          <header class="tv-modal-head">
            <div class="tv-modal-title tv-modal-title-danger">
              <AlertTriangle :size="16" />
              <h3 id="tv-del-title">删除课程清单条目</h3>
            </div>
            <button
              type="button"
              class="tv-modal-close"
              aria-label="关闭"
              :disabled="deletingCurriculum"
              @click="cancelDeleteCurriculum"
            >
              <X :size="16" />
            </button>
          </header>
          <div class="tv-modal-body">
            <p class="tv-confirm-lead">
              即将删除课程 <strong>「{{ deleteTarget.courseName }}」</strong>
              <span v-if="deleteTarget.major">（{{ deleteTarget.major }}）</span>
              的入库记录。
            </p>
            <p class="tv-confirm-hint">
              删除后相关的技能映射、供需分析和教改建议都会随之刷新，该操作不可撤销。
            </p>
            <footer class="tv-modal-footer">
              <GlowButton variant="ghost" type="button" :disabled="deletingCurriculum" @click="cancelDeleteCurriculum">
                取消
              </GlowButton>
              <GlowButton variant="primary" class="danger-btn" type="button" :loading="deletingCurriculum" @click="confirmDeleteCurriculum">
                <Trash2 :size="14" />
                确认删除
              </GlowButton>
            </footer>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.teacher-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ---------------- Metric strip ---------------- */
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

/* ---------------- Sidebar ---------------- */
.teacher-sidebar {
  position: sticky;
  top: 0;
  align-self: start;
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

/* ---------------- Panel ---------------- */
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
  scroll-margin-top: 16px;
}

.panel-head {
  padding: 16px 22px 12px;
  border-bottom: 1px solid var(--c-border-glass);
}

.panel-head.panel-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-link {
  font-size: 13px;
  color: var(--c-accent-primary);
  text-decoration: none;
  font-weight: 500;
  white-space: nowrap;
}
.panel-link:hover { text-decoration: underline; }

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

/* ---------------- Major filter ---------------- */
.teacher-major-filter {
  overflow: hidden;
}

.teacher-major-filter-body {
  gap: 12px;
}

.teacher-major-field {
  display: grid;
  gap: 8px;
  max-width: 320px;
}

.teacher-major-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.teacher-major-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--c-text-secondary);
}

.teacher-major-value {
  display: inline-flex;
  align-items: center;
  width: 100%;
  min-width: 240px;
  height: 36px;
  padding: 0 12px;
  border-radius: 10px;
  background: rgba(30, 117, 255, 0.06);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: left;
  border: 1px solid rgba(30, 117, 255, 0.24);
}

.teacher-major-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.major-chip {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}
.major-chip:hover {
  color: var(--c-text-primary);
  border-color: var(--c-border-glass-hover);
}
.major-chip.is-active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  border-color: var(--c-accent-primary);
  font-weight: 600;
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
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.pill.is-ok {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

/* ---------------- Reform subsections ---------------- */
.reform-subsection {
  display: grid;
  gap: 10px;
}
.reform-subtitle {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 700;
  color: var(--c-text-primary);
}
.reform-mini-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}
.mini-card {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}
.mini-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.mini-card-head strong {
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 700;
}
.mini-score {
  color: var(--c-accent-primary);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
}
.mini-card p {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 12.5px;
  line-height: 1.55;
}
.plain-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.plain-list li strong {
  display: block;
  margin-bottom: 2px;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 700;
}

/* ---------------- Form ---------------- */
.form-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.field-span-2 {
  grid-column: span 2;
}

.field-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
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
  gap: 10px;
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

.rate-major {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--c-accent-primary);
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
.skills-list {
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

/* ---------------- Course / curriculum list ---------------- */
.curriculum-list {
  display: grid;
  gap: 10px;
  margin-top: 4px;
}

.curriculum-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}

.curriculum-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.curriculum-head strong {
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
}

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

/* ---------------- Danger button tint ---------------- */
.danger-btn:hover :deep(span),
.danger-btn:hover :deep(svg) {
  color: #b23b2e;
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: auto;
}

.material-actions.has-two-actions {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.material-actions :deep(.glow-button) {
  width: 100%;
  min-width: 0;
  padding: 10px 12px;
  font-size: 12.5px;
}

.material-actions :deep(.btn-content) {
  width: 100%;
  justify-content: center;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .material-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .material-actions {
    grid-template-columns: 1fr;
  }
}

/* ---------------- Reform / retrace stats ---------------- */
.reform-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
  color: var(--c-text-muted);
  font-size: 13px;
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

.reform-prereq {
  display: grid;
  gap: 18px;
  padding: 22px;
  border-radius: 18px;
  border: 1px solid rgba(213, 164, 84, 0.36);
  background:
    radial-gradient(circle at top right, rgba(236, 190, 103, 0.22), transparent 34%),
    linear-gradient(180deg, rgba(255, 246, 225, 0.98), rgba(255, 251, 241, 0.98));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.72),
    0 16px 36px rgba(162, 112, 39, 0.08);
}

.reform-prereq-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.reform-prereq-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(164, 92, 33, 0.18);
  background: rgba(164, 92, 33, 0.08);
  color: #9a531f;
  font-size: 12.5px;
  font-weight: 700;
}

.reform-prereq-progress {
  color: #8d6238;
  font-size: 12.5px;
  font-weight: 600;
}

.reform-prereq-copy {
  display: grid;
  gap: 8px;
}

.reform-prereq-copy h3 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(14px, 1.2vw, 16px);
  line-height: 1.35;
  color: #8a4918;
}

.reform-prereq-list {
  display: grid;
  gap: 12px;
}

.reform-prereq-item {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 16px;
  border: 1px solid rgba(210, 162, 84, 0.28);
  background: rgba(255, 252, 246, 0.76);
  box-shadow: 0 10px 24px rgba(132, 87, 29, 0.06);
}

.reform-prereq-item-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.reform-prereq-item-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #a45c21;
}

.reform-prereq-item-title strong {
  font-family: var(--font-serif);
  font-size: 19px;
  font-weight: 700;
  line-height: 1.25;
  color: #934d1d;
}

.reform-prereq-item p {
  margin: 0;
  color: #99663b;
  font-size: 14px;
  line-height: 1.75;
}

.reform-prereq-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(164, 92, 33, 0.1);
  color: #a45c21;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.reform-prereq-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.reform-prereq-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 18px;
  border-radius: 14px;
  border: 1px solid #d29a58;
  background: linear-gradient(180deg, #fff7e7, #f7e0b6);
  color: #9c531f;
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 700;
  cursor: pointer;
  transition:
    transform var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
  box-shadow: 0 10px 22px rgba(181, 126, 57, 0.12);
}

.reform-prereq-cta:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 26px rgba(181, 126, 57, 0.16);
}

.reform-prereq-cta.is-secondary,
.reform-prereq-cta.is-ghost {
  background: rgba(255, 252, 246, 0.9);
}

.reform-prereq-cta.is-secondary {
  border-color: rgba(210, 154, 88, 0.62);
}

.reform-prereq-cta.is-ghost {
  border-color: rgba(164, 92, 33, 0.18);
  color: #8d6238;
}

.reform-prereq-cta:focus-visible {
  outline: 3px solid rgba(210, 154, 88, 0.26);
  outline-offset: 2px;
}

.retrace-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}
.stat-card .stat-label {
  color: var(--c-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.stat-card strong {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--c-text-primary);
  font-variant-numeric: tabular-nums;
}
.stat-card p {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 12.5px;
  line-height: 1.5;
}

.retrace-summary {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
}

.retrace-sample-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.sample-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
}
.sample-card .stat-label {
  color: var(--c-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.sample-card p {
  margin: 0;
  color: var(--c-text-primary);
  font-size: 13px;
  line-height: 1.55;
}

.retrace-subsection {
  display: grid;
  gap: 8px;
}

.student-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}

.student-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
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
  text-align: center;
}

.empty-state :deep(svg) {
  color: var(--c-text-faint);
}

/* ---------------- Loading ---------------- */
.tv-skel { display: flex; flex-direction: column; gap: 16px; padding: 8px 0; }
.tv-skel-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }

/* ---------------- Responsive ---------------- */
@media (max-width: 1024px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .field-span-2 {
    grid-column: span 1;
  }
  .workspace-metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

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

  .reform-prereq {
    padding: 18px;
  }

  .reform-prereq-item {
    padding: 16px;
  }

  .reform-prereq-cta {
    width: 100%;
  }
}
</style>

<style>
/* 课程清单弹窗（用 teleport 到 body，不在 scoped 作用域下） */
.tv-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 17, 23, 0.48);
  backdrop-filter: blur(6px);
  animation: tv-modal-fade 160ms ease-out;
}

.tv-modal {
  width: min(640px, 100%);
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  background: var(--c-bg-base-elevated, #fff);
  border: 1px solid var(--c-border-glass, rgba(0, 0, 0, 0.08));
  border-radius: 14px;
  box-shadow: 0 24px 64px rgba(15, 17, 23, 0.28);
  overflow: hidden;
  animation: tv-modal-rise 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.tv-modal-confirm {
  width: min(480px, 100%);
}

.tv-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--c-border-glass, rgba(0, 0, 0, 0.08));
}

.tv-modal-title {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--c-text-primary, #1a1a1a);
}
.tv-modal-title h3 {
  margin: 0;
  font-family: var(--font-serif, serif);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.tv-modal-title-danger {
  color: #d14343;
}

.tv-modal-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--c-text-muted, #888);
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}
.tv-modal-close:hover:not(:disabled) {
  background: var(--c-bg-surface-hover, rgba(0, 0, 0, 0.04));
  color: var(--c-text-primary, #1a1a1a);
}
.tv-modal-close:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.tv-modal-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px 22px 20px;
  overflow-y: auto;
}

.tv-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--c-border-glass, rgba(0, 0, 0, 0.08));
}

.tv-confirm-lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--c-text-primary, #1a1a1a);
}
.tv-confirm-lead strong {
  color: #d14343;
}
.tv-confirm-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--c-text-secondary, #555);
}

@keyframes tv-modal-fade {
  from { opacity: 0; }
  to   { opacity: 1; }
}
@keyframes tv-modal-rise {
  from { opacity: 0; transform: translateY(8px) scale(0.98); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}
</style>
