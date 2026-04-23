<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import JobCard from '../components/jobs/JobCard.vue'
import {
  Search,
  Building2,
  X,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ExternalLink,
  Clock,
  GraduationCap,
  Briefcase,
  Inbox
} from 'lucide-vue-next'
import { fetchJobs, fetchJobDetail, fetchSimilarJobs } from '../api'
import { useAuthStore } from '../store/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

function createDefaultQuery() {
  return {
    keyword: '',
    education: '',
    experience: '',
    positionType: '',
    companyNature: '',
    companySize: '',
    salaryMin: null,
    salaryMax: null
  }
}

const query = ref(createDefaultQuery())
const jobs = ref([])
const totalJobs = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const isLoading = ref(false)

// Preset options for the dropdown-chip filters. Each chip holds a
// canonical label + the query-field mutation. Salary presets collapse
// min/max into one picker; "不限" clears both. Education/experience
// presets bind directly to query.education / query.experience.
const salaryOptions = [
  { label: '不限', min: null, max: null },
  { label: '3K 以下', min: null, max: 3 },
  { label: '3-5K', min: 3, max: 5 },
  { label: '5-10K', min: 5, max: 10 },
  { label: '10-20K', min: 10, max: 20 },
  { label: '20-50K', min: 20, max: 50 },
  { label: '50K 以上', min: 50, max: null }
]
const educationOptions = ['不限', '大专', '本科', '硕士', '博士']
const experienceOptions = ['不限', '经验不限', '1年以内', '1-3年', '3-5年', '5-10年', '10年以上']
const positionTypeOptions = ['不限', '全职', '兼职', '实习']
const companyNatureOptions = ['不限', '民营', '国企', '外企', '合资', '上市公司', '事业单位', '政府/非盈利组织']
const companySizeOptions = ['不限', '20人以下', '20-99人', '100-499人', '500-999人', '1000-9999人', '10000人以上']
const openFilterKey = ref('')
let filterCloseTimer = null

function openFilter(key) {
  if (filterCloseTimer) {
    clearTimeout(filterCloseTimer)
    filterCloseTimer = null
  }
  openFilterKey.value = key
}
function scheduleCloseFilter() {
  if (filterCloseTimer) clearTimeout(filterCloseTimer)
  filterCloseTimer = setTimeout(() => {
    openFilterKey.value = ''
    filterCloseTimer = null
  }, 120)
}
function closeFilterNow() {
  if (filterCloseTimer) {
    clearTimeout(filterCloseTimer)
    filterCloseTimer = null
  }
  openFilterKey.value = ''
}
function handleFilterOutsideClick(e) {
  if (!openFilterKey.value) return
  const target = e.target
  if (target instanceof Element && target.closest('.zp-chip-wrap')) return
  closeFilterNow()
}
function handleFilterKey(e) {
  if (e.key !== 'Escape') return
  // Filter popover takes priority: close it first, let detail modal
  // keep showing. If no popover is open, close the detail modal.
  if (openFilterKey.value) {
    closeFilterNow()
  } else if (selectedJob.value) {
    closeDetail()
  }
}

// 详情弹窗
const selectedJob = ref(null)
const isLoadingDetail = ref(false)
const similarJobs = ref([])
const skipRouteWatch = ref(false)

const totalPages = computed(() => Math.ceil(totalJobs.value / pageSize.value) || 1)

// Human-readable label for each filter chip: shows the current value
// when set, or the default placeholder otherwise. Used both for the
// visible chip text and for deciding whether a chip is in "active"
// state (bold + blue).
const salaryChipLabel = computed(() => {
  const { salaryMin, salaryMax } = query.value
  const match = salaryOptions.find((o) => o.min === salaryMin && o.max === salaryMax)
  if (match && match.label !== '不限') return match.label
  if (salaryMin !== null || salaryMax !== null) {
    return `${salaryMin ?? '不限'}-${salaryMax ?? '不限'}K`
  }
  return '薪资要求'
})
const educationChipLabel = computed(() => query.value.education || '学历要求')
const experienceChipLabel = computed(() => query.value.experience || '工作经验')
const positionTypeChipLabel = computed(() => query.value.positionType || '职位类型')
const companyNatureChipLabel = computed(() => query.value.companyNature || '公司性质')
const companySizeChipLabel = computed(() => query.value.companySize || '公司规模')

// Whether any filter is currently active (drives the clear-filter link
// visibility and the per-chip active styling). Keyword remains a top-row
// input and is excluded from the chip-filter count.
const isSalaryActive = computed(() => query.value.salaryMin !== null || query.value.salaryMax !== null)
const isEducationActive = computed(() => !!query.value.education)
const isExperienceActive = computed(() => !!query.value.experience)
const isPositionTypeActive = computed(() => !!query.value.positionType)
const isCompanyNatureActive = computed(() => !!query.value.companyNature)
const isCompanySizeActive = computed(() => !!query.value.companySize)
const activeFilterCount = computed(() =>
  Number(isSalaryActive.value) +
  Number(isEducationActive.value) +
  Number(isExperienceActive.value) +
  Number(isPositionTypeActive.value) +
  Number(isCompanyNatureActive.value) +
  Number(isCompanySizeActive.value)
)
const hasAnyFilter = computed(() => activeFilterCount.value > 0)

// Chip pick handlers. Each commits the change to `query` and
// reloads. The popover closes regardless.
function pickSalary(opt) {
  query.value.salaryMin = opt.min
  query.value.salaryMax = opt.max
  closeFilterNow()
  loadJobs(1)
}
function pickEducation(opt) {
  query.value.education = opt === '不限' ? '' : opt
  closeFilterNow()
  loadJobs(1)
}
function pickExperience(opt) {
  query.value.experience = opt === '不限' ? '' : opt
  closeFilterNow()
  loadJobs(1)
}
function pickPositionType(opt) {
  query.value.positionType = opt === '不限' ? '' : opt
  closeFilterNow()
  loadJobs(1)
}
function pickCompanyNature(opt) {
  query.value.companyNature = opt === '不限' ? '' : opt
  closeFilterNow()
  loadJobs(1)
}
function pickCompanySize(opt) {
  query.value.companySize = opt === '不限' ? '' : opt
  closeFilterNow()
  loadJobs(1)
}

// Per-chip clear helpers: bound to the X button at the start of each
// active chip. Clears that single filter and reloads. Because these
// handlers stopPropagation on the button, they won't also open the
// dropdown.
function clearSalary() {
  query.value.salaryMin = null
  query.value.salaryMax = null
  closeFilterNow()
  loadJobs(1)
}
function clearEducation() {
  query.value.education = ''
  closeFilterNow()
  loadJobs(1)
}
function clearExperience() {
  query.value.experience = ''
  closeFilterNow()
  loadJobs(1)
}
function clearPositionType() {
  query.value.positionType = ''
  closeFilterNow()
  loadJobs(1)
}
function clearCompanyNature() {
  query.value.companyNature = ''
  closeFilterNow()
  loadJobs(1)
}
function clearCompanySize() {
  query.value.companySize = ''
  closeFilterNow()
  loadJobs(1)
}

const renderDetailHtml = (value) => String(value ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')
  .replace(/\n/g, '<br/>')

function normalizeRouteValue(value) {
  if (value === undefined || value === null || value === '') {
    return ''
  }
  return `${value}`
}

function formatSalary(job) {
  return job.salaryText || '面议'
}

const placeholderDescriptionPatterns = [
  /暂无详细描述/,
  /暂无描述/,
  /暂无职位描述/,
  /暂无岗位描述/,
  /暂无信息/,
  /^无$/,
  /^--$/,
  /^N\/A$/i,
  /^null$/i,
  /^undefined$/i
]

function sanitizeJobField(value) {
  const text = typeof value === 'string' ? value.trim() : ''
  if (!text) return ''
  if (placeholderDescriptionPatterns.some((pattern) => pattern.test(text))) return ''
  return text
}

function sanitizeJobContent(job) {
  if (!job || typeof job !== 'object') return job
  const description = sanitizeJobField(job.description)
  const requirements = sanitizeJobField(job.requirements)
  return {
    ...job,
    description,
    requirements
  }
}

function shouldDisplayJob(job) {
  const normalized = sanitizeJobContent(job)
  return Boolean(normalized?.description || normalized?.requirements)
}

function applyRouteQuery(routeQuery) {
  query.value = {
    keyword: normalizeRouteValue(routeQuery.keyword),
    education: normalizeRouteValue(routeQuery.education),
    experience: normalizeRouteValue(routeQuery.experience),
    positionType: normalizeRouteValue(routeQuery.positionType),
    companyNature: normalizeRouteValue(routeQuery.companyNature),
    companySize: normalizeRouteValue(routeQuery.companySize),
    salaryMin: routeQuery.salaryMin ? Number(routeQuery.salaryMin) : null,
    salaryMax: routeQuery.salaryMax ? Number(routeQuery.salaryMax) : null
  }
  currentPage.value = routeQuery.page ? Number(routeQuery.page) || 1 : 1
}

function buildRouteQuery(page = 1, extra = {}) {
  const next = {}
  Object.entries(query.value).forEach(([key, value]) => {
    if (value !== null && value !== undefined && `${value}`.trim() !== '') {
      next[key] = value
    }
  })
  if (page > 1) {
    next.page = page
  }
  return { ...next, ...extra }
}

async function loadJobs(page = 1, { syncRoute = true } = {}) {
  isLoading.value = true
  currentPage.value = page
  try {
    if (syncRoute) {
      skipRouteWatch.value = true
      router.replace({ path: '/jobs', query: buildRouteQuery(page) })
    }
    const res = await fetchJobs({
      ...query.value,
      page,
      pageSize: pageSize.value
    })
    const rawJobs = Array.isArray(res.data) ? res.data : []
    jobs.value = rawJobs
      .map((job) => sanitizeJobContent(job))
      .filter((job) => shouldDisplayJob(job))
    const removedCount = rawJobs.length - jobs.value.length
    totalJobs.value = Math.max(0, (Number(res.total) || 0) - removedCount)
  } catch (error) {
    console.error('Failed to load jobs', error)
    jobs.value = []
    totalJobs.value = 0
  } finally {
    isLoading.value = false
  }
}

async function openDetail(job) {
  if (!job?.id) return
  isLoadingDetail.value = true
  selectedJob.value = { ...job }
  similarJobs.value = []
  skipRouteWatch.value = true
  router.replace({ path: '/jobs', query: buildRouteQuery(currentPage.value, { open: job.id }) })
  try {
    const [detail, similar] = await Promise.all([
      fetchJobDetail(job.id),
      fetchSimilarJobs(authStore.token, job.id, 6).catch(() => ({}))
    ])
    selectedJob.value = sanitizeJobContent(detail)
    similarJobs.value = Array.isArray(similar.recommendations)
      ? similar.recommendations.map((item) => sanitizeJobContent(item)).filter((item) => shouldDisplayJob(item))
      : []
  } catch (error) {
    console.error('Failed to load job detail', error)
  } finally {
    isLoadingDetail.value = false
  }
}

async function openDetailById(jobId) {
  const normalizedId = Number(jobId)
  if (!Number.isFinite(normalizedId)) {
    return
  }

  const existingJob = jobs.value.find((job) => Number(job.id) === normalizedId)
  if (existingJob) {
    await openDetail(existingJob)
    return
  }

  await openDetail({ id: normalizedId })
}

function closeDetail() {
  selectedJob.value = null
  similarJobs.value = []
  skipRouteWatch.value = true
  router.replace({ path: '/jobs', query: buildRouteQuery(currentPage.value) })
}

function resetFilters() {
  query.value = createDefaultQuery()
  closeFilterNow()
  loadJobs(1)
}

const pageNumbers = computed(() => {
  const pages = []
  for (let p = 1; p <= totalPages.value; p += 1) {
    if (p === 1 || p === totalPages.value || (p >= currentPage.value - 2 && p <= currentPage.value + 2)) {
      pages.push({ type: 'page', value: p })
    } else if (p === currentPage.value - 3 || p === currentPage.value + 3) {
      pages.push({ type: 'ellipsis', value: `e-${p}` })
    }
  }
  return pages
})

onMounted(() => {
  document.addEventListener('click', handleFilterOutsideClick)
  document.addEventListener('keydown', handleFilterKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', handleFilterOutsideClick)
  document.removeEventListener('keydown', handleFilterKey)
  if (filterCloseTimer) {
    clearTimeout(filterCloseTimer)
    filterCloseTimer = null
  }
})

watch(
  () => route.query,
  async (nextQuery) => {
    if (skipRouteWatch.value) {
      skipRouteWatch.value = false
      return
    }
    applyRouteQuery(nextQuery)
    await loadJobs(currentPage.value, { syncRoute: false })
    // Support both `?open=<id>` (main) and `?jobId=<id>` (HEAD) for detail
    // drawer deep-linking so in-flight links from either side keep working.
    const openId = nextQuery.open ? Number(nextQuery.open) : (nextQuery.jobId ? Number(nextQuery.jobId) : null)
    if (openId) {
      await openDetailById(openId)
    } else if (selectedJob.value) {
      selectedJob.value = null
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="jobs-page page-animate">
    <!-- Search + filter strip: full viewport width, flat (no card
         chrome), sticky under the topbar. Renders like a secondary
         navigation band in the Zhaopin style; the 100vw / negative
         margin trick lets it escape the main-content padding. -->
    <section class="jobs-search-strip">
      <div class="jobs-search-inner">
        <!-- Row 1: big search input + solid search button -->
        <div class="zp-search-row">
          <div class="zp-search-input-wrap">
            <input
              v-model="query.keyword"
              type="text"
              placeholder="输入职位、公司等搜索"
              class="zp-search-input"
              @keyup.enter="loadJobs(1)"
            />
          </div>
          <button class="zp-search-btn" type="button" aria-label="搜索" @click="loadJobs(1)">
            <Search :size="18" :stroke-width="2.2" />
          </button>
        </div>

        <!-- Row 2: filter chips. Each chip opens a small popover panel
             on hover (120 ms grace period between chip and panel).
             Active chips display a clear button; clicking it clears
             just that filter without opening the dropdown. -->
        <div class="zp-chip-row">
        <div
          class="zp-chip-wrap"
          :class="{ open: openFilterKey === 'salary' }"
          @mouseenter="openFilter('salary')"
          @mouseleave="scheduleCloseFilter"
        >
          <div
            class="zp-chip"
            :class="{ active: isSalaryActive }"
            tabindex="0"
            role="button"
            :aria-expanded="openFilterKey === 'salary'"
            @focus="openFilter('salary')"
            @blur="scheduleCloseFilter"
          >
            <button
              v-if="isSalaryActive"
              type="button"
              class="zp-chip-clear"
              :aria-label="`清除 ${salaryChipLabel}`"
              @click.stop.prevent="clearSalary"
            >
              <X :size="12" :stroke-width="2" />
            </button>
            <span class="zp-chip-label">{{ salaryChipLabel }}</span>
            <ChevronDown :size="14" :stroke-width="1.8" class="zp-chip-caret" />
          </div>
          <div v-if="openFilterKey === 'salary'" class="zp-chip-panel" role="menu">
            <button
              v-for="opt in salaryOptions"
              :key="opt.label"
              class="zp-chip-option"
              :class="{ active: query.salaryMin === opt.min && query.salaryMax === opt.max }"
              type="button"
              role="menuitem"
              @click="pickSalary(opt)"
            >{{ opt.label }}</button>
          </div>
        </div>

        <div
          class="zp-chip-wrap"
          :class="{ open: openFilterKey === 'education' }"
          @mouseenter="openFilter('education')"
          @mouseleave="scheduleCloseFilter"
        >
          <div
            class="zp-chip"
            :class="{ active: isEducationActive }"
            tabindex="0"
            role="button"
            :aria-expanded="openFilterKey === 'education'"
            @focus="openFilter('education')"
            @blur="scheduleCloseFilter"
          >
            <button
              v-if="isEducationActive"
              type="button"
              class="zp-chip-clear"
              :aria-label="`清除 ${educationChipLabel}`"
              @click.stop.prevent="clearEducation"
            >
              <X :size="12" :stroke-width="2" />
            </button>
            <span class="zp-chip-label">{{ educationChipLabel }}</span>
            <ChevronDown :size="14" :stroke-width="1.8" class="zp-chip-caret" />
          </div>
          <div v-if="openFilterKey === 'education'" class="zp-chip-panel" role="menu">
            <button
              v-for="opt in educationOptions"
              :key="opt"
              class="zp-chip-option"
              :class="{ active: query.education === (opt === '不限' ? '' : opt) }"
              type="button"
              role="menuitem"
              @click="pickEducation(opt)"
            >{{ opt }}</button>
          </div>
        </div>

        <div
          class="zp-chip-wrap"
          :class="{ open: openFilterKey === 'experience' }"
          @mouseenter="openFilter('experience')"
          @mouseleave="scheduleCloseFilter"
        >
          <div
            class="zp-chip"
            :class="{ active: isExperienceActive }"
            tabindex="0"
            role="button"
            :aria-expanded="openFilterKey === 'experience'"
            @focus="openFilter('experience')"
            @blur="scheduleCloseFilter"
          >
            <button
              v-if="isExperienceActive"
              type="button"
              class="zp-chip-clear"
              :aria-label="`清除 ${experienceChipLabel}`"
              @click.stop.prevent="clearExperience"
            >
              <X :size="12" :stroke-width="2" />
            </button>
            <span class="zp-chip-label">{{ experienceChipLabel }}</span>
            <ChevronDown :size="14" :stroke-width="1.8" class="zp-chip-caret" />
          </div>
          <div v-if="openFilterKey === 'experience'" class="zp-chip-panel" role="menu">
            <button
              v-for="opt in experienceOptions"
              :key="opt"
              class="zp-chip-option"
              :class="{ active: query.experience === (opt === '不限' ? '' : opt) }"
              type="button"
              role="menuitem"
              @click="pickExperience(opt)"
            >{{ opt }}</button>
          </div>
        </div>

        <div
          class="zp-chip-wrap"
          :class="{ open: openFilterKey === 'positionType' }"
          @mouseenter="openFilter('positionType')"
          @mouseleave="scheduleCloseFilter"
        >
          <div
            class="zp-chip"
            :class="{ active: isPositionTypeActive }"
            tabindex="0"
            role="button"
            :aria-expanded="openFilterKey === 'positionType'"
            @focus="openFilter('positionType')"
            @blur="scheduleCloseFilter"
          >
            <button
              v-if="isPositionTypeActive"
              type="button"
              class="zp-chip-clear"
              :aria-label="`清除 ${positionTypeChipLabel}`"
              @click.stop.prevent="clearPositionType"
            >
              <X :size="12" :stroke-width="2" />
            </button>
            <span class="zp-chip-label">{{ positionTypeChipLabel }}</span>
            <ChevronDown :size="14" :stroke-width="1.8" class="zp-chip-caret" />
          </div>
          <div v-if="openFilterKey === 'positionType'" class="zp-chip-panel" role="menu">
            <button
              v-for="opt in positionTypeOptions"
              :key="opt"
              class="zp-chip-option"
              :class="{ active: query.positionType === (opt === '不限' ? '' : opt) }"
              type="button"
              role="menuitem"
              @click="pickPositionType(opt)"
            >{{ opt }}</button>
          </div>
        </div>

        <div
          class="zp-chip-wrap"
          :class="{ open: openFilterKey === 'companyNature' }"
          @mouseenter="openFilter('companyNature')"
          @mouseleave="scheduleCloseFilter"
        >
          <div
            class="zp-chip"
            :class="{ active: isCompanyNatureActive }"
            tabindex="0"
            role="button"
            :aria-expanded="openFilterKey === 'companyNature'"
            @focus="openFilter('companyNature')"
            @blur="scheduleCloseFilter"
          >
            <button
              v-if="isCompanyNatureActive"
              type="button"
              class="zp-chip-clear"
              :aria-label="`清除 ${companyNatureChipLabel}`"
              @click.stop.prevent="clearCompanyNature"
            >
              <X :size="12" :stroke-width="2" />
            </button>
            <span class="zp-chip-label">{{ companyNatureChipLabel }}</span>
            <ChevronDown :size="14" :stroke-width="1.8" class="zp-chip-caret" />
          </div>
          <div v-if="openFilterKey === 'companyNature'" class="zp-chip-panel" role="menu">
            <button
              v-for="opt in companyNatureOptions"
              :key="opt"
              class="zp-chip-option"
              :class="{ active: query.companyNature === (opt === '不限' ? '' : opt) }"
              type="button"
              role="menuitem"
              @click="pickCompanyNature(opt)"
            >{{ opt }}</button>
          </div>
        </div>

        <div
          class="zp-chip-wrap"
          :class="{ open: openFilterKey === 'companySize' }"
          @mouseenter="openFilter('companySize')"
          @mouseleave="scheduleCloseFilter"
        >
          <div
            class="zp-chip"
            :class="{ active: isCompanySizeActive }"
            tabindex="0"
            role="button"
            :aria-expanded="openFilterKey === 'companySize'"
            @focus="openFilter('companySize')"
            @blur="scheduleCloseFilter"
          >
            <button
              v-if="isCompanySizeActive"
              type="button"
              class="zp-chip-clear"
              :aria-label="`清除 ${companySizeChipLabel}`"
              @click.stop.prevent="clearCompanySize"
            >
              <X :size="12" :stroke-width="2" />
            </button>
            <span class="zp-chip-label">{{ companySizeChipLabel }}</span>
            <ChevronDown :size="14" :stroke-width="1.8" class="zp-chip-caret" />
          </div>
          <div v-if="openFilterKey === 'companySize'" class="zp-chip-panel" role="menu">
            <button
              v-for="opt in companySizeOptions"
              :key="opt"
              class="zp-chip-option"
              :class="{ active: query.companySize === (opt === '不限' ? '' : opt) }"
              type="button"
              role="menuitem"
              @click="pickCompanySize(opt)"
            >{{ opt }}</button>
          </div>
        </div>

        <button
          v-if="hasAnyFilter"
          type="button"
          class="zp-clear-link"
          @click="resetFilters"
        >
          <X :size="12" :stroke-width="2" />
          清空筛选条件 ({{ activeFilterCount }})
        </button>
        </div>
      </div>
    </section>

    <!-- Results Panel -->
    <section class="jobs-panel">
      <div class="jobs-panel-body">
        <div v-if="isLoading" class="loading-state">
          <div class="loader-ring"></div>
        </div>

        <div v-else-if="jobs.length === 0" class="empty-state">
          <Search :size="40" class="empty-icon" :stroke-width="1.4" />
          <p>没有找到匹配的职位</p>
          <span>试试调整搜索条件或清除筛选</span>
        </div>

        <TransitionGroup v-else name="list" tag="div" class="jobs-grid">
          <JobCard v-for="job in jobs" :key="job.id" :job="job" @open="openDetail" />
        </TransitionGroup>

        <div v-if="totalPages > 1" class="pagination">
          <button
            class="page-btn"
            type="button"
            :disabled="currentPage <= 1"
            @click="loadJobs(currentPage - 1)"
          >
            <ChevronLeft :size="16" :stroke-width="2" />
          </button>
          <template v-for="entry in pageNumbers" :key="entry.value">
            <button
              v-if="entry.type === 'page'"
              class="page-btn"
              type="button"
              :class="{ active: entry.value === currentPage }"
              @click="loadJobs(entry.value)"
            >{{ entry.value }}</button>
            <span v-else class="page-ellipsis">...</span>
          </template>
          <button
            class="page-btn"
            type="button"
            :disabled="currentPage >= totalPages"
            @click="loadJobs(currentPage + 1)"
          >
            <ChevronRight :size="16" :stroke-width="2" />
          </button>
        </div>
      </div>
    </section>

    <!-- Detail Modal -->
    <Teleport to="body">
      <transition name="modal-fade">
        <div v-if="selectedJob" class="modal-overlay" @click.self="closeDetail">
          <div class="modal-wrapper">
            <div class="modal-content">
              <button class="modal-close" type="button" aria-label="关闭" @click="closeDetail">
                <X :size="18" :stroke-width="2" />
              </button>

              <div class="modal-header">
                <div class="header-main">
                  <h2 class="modal-title">{{ selectedJob.title }}</h2>
                  <div class="modal-meta-row">
                    <span class="company">{{ selectedJob.companyName }}</span>
                    <span class="dot">·</span>
                    <span class="location">{{ selectedJob.city || '全国' }}</span>
                  </div>
                </div>
                <div class="salary-box">
                  <span class="modal-salary">{{ formatSalary(selectedJob) }}</span>
                </div>
              </div>

              <div class="modal-tags">
                <div class="tag-group">
                  <span class="detail-tag detail-tag--edu" v-if="selectedJob.education">
                    <GraduationCap :size="13" :stroke-width="1.8" />
                    {{ selectedJob.education }}
                  </span>
                  <span class="detail-tag detail-tag--exp" v-if="selectedJob.experience">
                    <Clock :size="13" :stroke-width="1.8" />
                    {{ selectedJob.experience }}
                  </span>
                  <span class="detail-tag detail-tag--industry" v-if="selectedJob.industryName">
                    <Building2 :size="13" :stroke-width="1.8" />
                    {{ selectedJob.industryName }}
                  </span>
                  <span class="detail-tag detail-tag--type" v-if="selectedJob.employmentType">
                    <Briefcase :size="13" :stroke-width="1.8" />
                    {{ selectedJob.employmentType }}
                  </span>
                </div>
                <div class="time-stamp" v-if="selectedJob.publishDate">
                  发布于 {{ selectedJob.publishDate }}
                </div>
              </div>

              <div class="modal-body">
                <div v-if="isLoadingDetail" class="loading-state-simple">
                  <div class="loader-ring-sm"></div>
                  <span>正在加载职位详情...</span>
                </div>
                <template v-else>
                  <div v-if="selectedJob.description" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>职位描述</h3>
                    </div>
                    <div class="detail-text" v-html="renderDetailHtml(selectedJob.description)"></div>
                  </div>
                  <div v-if="selectedJob.requirements" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>任职要求</h3>
                    </div>
                    <div class="detail-text" v-html="renderDetailHtml(selectedJob.requirements)"></div>
                  </div>

                  <!-- Empty state: no description AND no requirements -->
                  <div
                    v-if="!selectedJob.description && !selectedJob.requirements"
                    class="modal-empty"
                  >
                    <Inbox :size="28" :stroke-width="1.6" class="modal-empty-icon" />
                    <p class="modal-empty-title">暂无详细描述</p>
                    <span class="modal-empty-sub">
                      来源平台只保留了职位概要。可前往原始页面查看完整信息。
                    </span>
                    <a
                      v-if="selectedJob.sourceUrl"
                      :href="selectedJob.sourceUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="modal-empty-cta"
                    >
                      <ExternalLink :size="14" :stroke-width="1.8" />
                      查看原始页面
                    </a>
                  </div>

                  <div v-if="selectedJob.description || selectedJob.requirements" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>相似职位</h3>
                    </div>
                    <div v-if="similarJobs.length" class="similar-list">
                      <button
                        v-for="item in similarJobs"
                        :key="item.id"
                        class="similar-item"
                        @click="openDetail(item)"
                      >
                        <div>
                          <strong>{{ item.title }}</strong>
                          <p>{{ item.companyName }} · {{ item.city || '全国' }}</p>
                        </div>
                        <span>{{ item.salaryText || '--' }}</span>
                      </button>
                    </div>
                    <div v-else class="similar-empty">
                      <Inbox :size="20" :stroke-width="1.6" />
                    <span>该岗位暂时没有相似职位</span>
                    </div>
                  </div>
                </template>
              </div>

              <div v-if="selectedJob.sourceUrl && (selectedJob.description || selectedJob.requirements)" class="modal-footer">
                <a
                  :href="selectedJob.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="action-button primary"
                >
                  <ExternalLink :size="14" :stroke-width="1.8" />
                  查看原始页面
                </a>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
/* Dark-mode overrides: the accent background gets lighter in dark mode,`r`n   so button label text flips to a darker ink for contrast. */
[data-theme="dark"] .zp-search-btn,
[data-theme="dark"] .zp-chip-clear:hover,
[data-theme="dark"] .zp-chip-option.primary,
[data-theme="dark"] .action-button.primary,
[data-theme="dark"] .page-btn.active {
  color: #0f1420;
}
/* Province tabs popover: "active" tab in light theme uses #ffffff bg.
   In dark theme that white slab looks jarring against the dark
   popover: use the base-elevated token so it feels like a raised
   tile in both themes. */
[data-theme="dark"] .zp-cascade-prov:hover,
[data-theme="dark"] .zp-cascade-prov.active {
  background: var(--c-bg-surface-hover);
}
</style>


