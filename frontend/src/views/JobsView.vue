<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import JobCard from '../components/jobs/JobCard.vue'
import {
  Search,
  MapPin,
  Building2,
  X,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ExternalLink,
  Heart,
  Clock,
  GraduationCap,
  Briefcase
} from 'lucide-vue-next'
import { addFavorite, checkFavorite, fetchJobDetail, fetchJobs, fetchSimilarJobs, removeFavorite } from '../api'
import { useAuthStore } from '../store/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

function createDefaultQuery() {
  return {
    keyword: '',
    city: '',
    industry: '',
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
const experienceOptions = ['不限', '1年以下', '1-3年', '3-5年', '5-10年', '10年以上']
// City picker: limited to common Chinese cities. "不限" clears the
// filter. Fujian cities grouped up front because the deployment is
// based in Nanping; the rest are the 30-odd tier-1/2 cities most
// commonly represented in job-board datasets. Swap for a backend
// city list later if needed.
const cityOptions = [
  '不限',
  '南平', '福州', '厦门', '泉州', '漳州', '宁德', '三明', '龙岩', '莆田',
  '北京', '上海', '广州', '深圳', '杭州', '南京', '苏州', '成都', '武汉',
  '西安', '重庆', '天津', '青岛', '长沙', '郑州', '济南', '合肥', '宁波',
  '沈阳', '大连', '昆明', '东莞', '佛山', '南宁'
]
const positionTypeOptions = ['不限', '全职', '兼职', '实习', '校园招聘', '合同工']
const companyNatureOptions = [
  '不限',
  '国企',
  '民营',
  '外资 (欧美)',
  '外资 (非欧美)',
  '合资',
  '上市公司',
  '事业单位',
  '国家机关'
]
const companySizeOptions = [
  '不限',
  '20人以下',
  '20-99人',
  '100-499人',
  '500-999人',
  '1000-9999人',
  '10000人以上'
]

// Which chip popover is currently open ('salary' | 'education' |
// 'experience' | 'positionType' | 'companyNature' | 'companySize' | '').
// Only one opens at a time. Hover opens; mouseleave schedules a close
// with a 120 ms grace period so the user can traverse from the chip to
// the panel without the menu snapping shut — mirrors the App.vue top-nav
// dropdown pattern. Focus/blur keyboard behavior works the same way.
// Document-level outside click + Escape act as safety nets.
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
  if (e.key === 'Escape') closeFilterNow()
}

// 详情弹窗
const selectedJob = ref(null)
const isLoadingDetail = ref(false)
const similarJobs = ref([])
const skipRouteWatch = ref(false)
const favoriteLoading = ref(false)
const isFavorited = ref(false)

const totalPages = computed(() => Math.ceil(totalJobs.value / pageSize.value) || 1)

// Human-readable label for each filter chip — shows the current value
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
const cityChipLabel = computed(() => query.value.city.trim() || '城市')
const educationChipLabel = computed(() => query.value.education || '学历要求')
const experienceChipLabel = computed(() => query.value.experience || '工作经验')
const positionTypeChipLabel = computed(() => query.value.positionType || '职位类型')
const companyNatureChipLabel = computed(() => query.value.companyNature || '公司性质')
const companySizeChipLabel = computed(() => query.value.companySize || '公司规模')

// Whether any filter is currently active (drives the "清空筛选条件"
// link visibility and the per-chip active styling). City now counts
// as a chip since it became a dropdown. Keyword remains a top-row
// input and is excluded from the chip-filter count.
const isCityActive = computed(() => !!query.value.city.trim())
const isSalaryActive = computed(() => query.value.salaryMin !== null || query.value.salaryMax !== null)
const isEducationActive = computed(() => !!query.value.education)
const isExperienceActive = computed(() => !!query.value.experience)
const isPositionTypeActive = computed(() => !!query.value.positionType)
const isCompanyNatureActive = computed(() => !!query.value.companyNature)
const isCompanySizeActive = computed(() => !!query.value.companySize)
const activeFilterCount = computed(() =>
  Number(isCityActive.value) +
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
function pickCity(opt) {
  query.value.city = opt === '不限' ? '' : opt
  closeFilterNow()
  loadJobs(1)
}
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

// Per-chip clear helpers — bound to the × button at the start of each
// active chip. Clears that single filter and reloads. Because these
// handlers stopPropagation on the button, they won't also open the
// dropdown.
function clearCity() {
  query.value.city = ''
  closeFilterNow()
  loadJobs(1)
}
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

function applyRouteQuery(routeQuery) {
  query.value = {
    keyword: normalizeRouteValue(routeQuery.keyword),
    city: normalizeRouteValue(routeQuery.city),
    industry: normalizeRouteValue(routeQuery.industry),
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
    jobs.value = res.data || []
    totalJobs.value = res.total || 0
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
    const [detail, similar, favoriteState] = await Promise.all([
      fetchJobDetail(job.id),
      fetchSimilarJobs(authStore.token, job.id, 6).catch(() => ({})),
      authStore.token ? checkFavorite(authStore.token, job.id).catch(() => ({ favorited: false })) : Promise.resolve({ favorited: false })
    ])
    selectedJob.value = detail
    similarJobs.value = Array.isArray(similar.recommendations) ? similar.recommendations : []
    isFavorited.value = Boolean(favoriteState?.favorited)
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
  isFavorited.value = false
  skipRouteWatch.value = true
  router.replace({ path: '/jobs', query: buildRouteQuery(currentPage.value) })
}

async function toggleFavorite() {
  if (!authStore.token || !selectedJob.value?.id || favoriteLoading.value) {
    return
  }
  favoriteLoading.value = true
  try {
    if (isFavorited.value) {
      await removeFavorite(authStore.token, selectedJob.value.id)
      isFavorited.value = false
    } else {
      await addFavorite(authStore.token, selectedJob.value.id)
      isFavorited.value = true
    }
  } catch (error) {
    console.error('Failed to toggle favorite', error)
  } finally {
    favoriteLoading.value = false
  }
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
         navigation band in the Zhaopin style — the 100 vw / negative
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

        <!-- Row 2: city dropdown (replaces free-text input). Uses the
             same chip-popover pattern as the filter chips. -->
        <div class="zp-location-row">
          <div
            class="zp-chip-wrap zp-chip-wrap--city"
            :class="{ open: openFilterKey === 'city' }"
            @mouseenter="openFilter('city')"
            @mouseleave="scheduleCloseFilter"
          >
            <div
              class="zp-chip zp-chip--city"
              :class="{ active: isCityActive }"
              tabindex="0"
              role="button"
              :aria-expanded="openFilterKey === 'city'"
              @focus="openFilter('city')"
              @blur="scheduleCloseFilter"
            >
              <MapPin :size="14" :stroke-width="1.9" class="zp-chip-city-icon" />
              <button
                v-if="isCityActive"
                type="button"
                class="zp-chip-clear"
                :aria-label="`清除 ${cityChipLabel}`"
                @click.stop.prevent="clearCity"
              >
                <X :size="12" :stroke-width="2" />
              </button>
              <span class="zp-chip-label">{{ cityChipLabel }}</span>
              <ChevronDown :size="14" :stroke-width="1.8" class="zp-chip-caret" />
            </div>
            <div v-if="openFilterKey === 'city'" class="zp-chip-panel zp-chip-panel--grid" role="menu">
              <button
                v-for="opt in cityOptions"
                :key="opt"
                class="zp-chip-option"
                :class="{ active: query.city === (opt === '不限' ? '' : opt) }"
                type="button"
                role="menuitem"
                @click="pickCity(opt)"
              >{{ opt }}</button>
            </div>
          </div>
        </div>

        <!-- Row 3: filter chips. Each chip opens a small popover panel
             on hover (120 ms grace period between chip and panel).
             Active chips display "× {value}" — clicking the × clears
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
          <p>没有找到匹配的岗位</p>
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
            <span v-else class="page-ellipsis">…</span>
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
              <button class="modal-close" type="button" @click="closeDetail">
                <X :size="18" :stroke-width="2" />
              </button>

              <div class="modal-header">
                <div class="header-main">
                  <h2 class="modal-title">{{ selectedJob.title }}</h2>
                  <div class="modal-meta-row">
                    <span class="company">{{ selectedJob.companyName }}</span>
                    <span class="dot">·</span>
                    <span class="location">{{ selectedJob.city || 'Nationwide' }}</span>
                  </div>
                </div>
                <div class="salary-box">
                  <span class="salary-label">月薪区间</span>
                  <span class="modal-salary">{{ formatSalary(selectedJob) }}</span>
                </div>
              </div>

              <div class="modal-tags">
                <div class="tag-group">
                  <span class="detail-tag" v-if="selectedJob.education">
                    <GraduationCap :size="13" :stroke-width="1.8" />
                    {{ selectedJob.education }}
                  </span>
                  <span class="detail-tag" v-if="selectedJob.experience">
                    <Clock :size="13" :stroke-width="1.8" />
                    {{ selectedJob.experience }}
                  </span>
                  <span class="detail-tag" v-if="selectedJob.industryName">
                    <Building2 :size="13" :stroke-width="1.8" />
                    {{ selectedJob.industryName }}
                  </span>
                  <span class="detail-tag" v-if="selectedJob.employmentType">
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
                      <h3>Description</h3>
                    </div>
                    <div class="detail-text" v-html="renderDetailHtml(selectedJob.description)"></div>
                  </div>
                  <div v-if="selectedJob.requirements" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>Requirements</h3>
                    </div>
                    <div class="detail-text" v-html="renderDetailHtml(selectedJob.requirements)"></div>
                  </div>
                  <div v-if="similarJobs.length" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>Similar Jobs</h3>
                    </div>
                    <div class="similar-list">
                      <button
                        v-for="item in similarJobs"
                        :key="item.id"
                        class="similar-item"
                        @click="openDetail(item)"
                      >
                        <div>
                          <strong>{{ item.title }}</strong>
                          <p>{{ item.companyName }} · {{ item.city || 'Nationwide' }}</p>
                        </div>
                        <span>{{ item.salaryText || '--' }}</span>
                      </button>
                    </div>
                  </div>
                </template>
              </div>

              <div class="modal-footer">
                <button
                  v-if="authStore.isLoggedIn"
                  class="action-button outline"
                  type="button"
                  :disabled="favoriteLoading"
                  @click="toggleFavorite"
                >
                  <Heart :size="14" :stroke-width="1.8" :fill="isFavorited ? 'currentColor' : 'none'" />
                  {{ isFavorited ? '取消收藏' : '收藏岗位' }}
                </button>
                <a
                  v-if="selectedJob.sourceUrl"
                  :href="selectedJob.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="action-button primary"
                >
                  <ExternalLink :size="14" :stroke-width="1.8" />
                  查看原始页面
                </a>
                <button class="action-button outline" type="button" @click="closeDetail">
                  关闭详情
                </button>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
/* ---------------- Layout ---------------- */
.jobs-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ---------------- Panel (shared) ---------------- */
.jobs-panel {
  background: var(--c-bg-base-elevated, #ffffff);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: 0 6px 18px color-mix(in srgb, var(--c-text-primary, #181b23) 8%, transparent);
  overflow: hidden;
}

.jobs-panel-body {
  padding: 18px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ═══════════════════════════════════════════════════════════════════
   Search strip: full-viewport-width flat bar (no card chrome).
   Uses the classic "100 vw via negative margins" trick to escape
   .main-content's horizontal padding and the .page-container's max
   width, so the strip visually spans edge to edge like a secondary
   nav. The inner container re-constrains content to 1360px so the
   fields don't stretch on wide monitors.
   ═══════════════════════════════════════════════════════════════════ */
.jobs-search-strip {
  /* Break out of the constrained main-content padding + .page-container
     max-width. `margin-left/right: calc(50% - 50vw)` is the canonical
     no-JS trick for full-bleed inside a centered parent. */
  position: relative;
  margin-left: calc(50% - 50vw);
  margin-right: calc(50% - 50vw);
  /* Negative top margin pulls the strip up over main-content's 16px
     top padding so it sits directly under the sticky topbar. */
  margin-top: -16px;
  background: var(--c-bg-base-elevated, #ffffff);
  border-bottom: 1px solid var(--c-border-glass);
  box-shadow: 0 1px 0 color-mix(in srgb, var(--c-text-primary, #181b23) 5%, transparent);
}
.jobs-search-inner {
  width: min(100%, 1360px);
  margin: 0 auto;
  padding: 18px clamp(20px, 2.5vw, 36px) 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* -- Row 1: search input + pill search button ------------------------ */
.zp-search-row {
  display: flex;
  align-items: center;
  gap: 10px;
  /* The row is the primary call to action; make it visually dominant. */
  border-bottom: 2px solid var(--c-accent-primary);
  padding-bottom: 10px;
}
.zp-search-input-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}
.zp-search-input {
  width: 100%;
  border: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 15px;
  line-height: 1.4;
  color: var(--c-text-primary);
  padding: 4px 2px;
  outline: none;
}
.zp-search-input::placeholder {
  color: var(--c-text-faint);
}
.zp-search-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: var(--c-accent-primary);
  color: #ffffff;
  cursor: pointer;
  transition: background-color 150ms ease, transform 150ms ease;
}
.zp-search-btn:hover { background: var(--c-accent-primary-hover, #004ba8); }
.zp-search-btn:active { transform: scale(0.96); }

/* -- Row 2: city selector ------------------------------------------- */
.zp-location-row {
  display: flex;
  align-items: center;
  gap: 18px;
}
.zp-location-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px;
}
.zp-location-cell :deep(svg),
.zp-location-cell svg {
  color: var(--c-text-muted);
  flex: none;
}
.zp-location-input {
  border: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 13.5px;
  color: var(--c-text-primary);
  min-width: 0;
  width: 140px;
  padding: 4px 2px;
  outline: none;
  border-bottom: 1px dashed transparent;
  transition: border-color 140ms ease;
}
.zp-location-input:focus {
  border-bottom-color: var(--c-accent-primary);
}
.zp-location-input::placeholder {
  color: var(--c-text-faint);
}

/* -- Row 3: chip filter row ---------------------------------------- */
.zp-chip-row {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  padding-top: 6px;
}
.zp-chip-wrap {
  position: relative;
  display: inline-flex;
}
.zp-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 2px;
  border: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 500;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: color 140ms ease;
  /* `.zp-chip` is now a focusable div (role=button) so hover-open
     dropdowns can also be keyboard-triggered via focus/blur. Add a
     subtle focus ring consistent with the rest of the design. */
  outline: none;
  border-radius: 6px;
}
.zp-chip:hover { color: var(--c-text-primary); }
.zp-chip:focus-visible {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--c-accent-primary) 30%, transparent);
}
.zp-chip.active {
  color: var(--c-accent-primary);
  font-weight: 600;
}
.zp-chip-label {
  line-height: 1.2;
}
/* Per-chip clear button — the × that appears to the left of an active
   chip's value. stopPropagation in the click handler prevents it from
   also triggering the chip's open behavior. */
.zp-chip-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  margin-right: 2px;
  border: none;
  border-radius: 50%;
  background: color-mix(in srgb, var(--c-accent-primary) 14%, transparent);
  color: var(--c-accent-primary);
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}
.zp-chip-clear:hover {
  background: var(--c-accent-primary);
  color: #ffffff;
}
.zp-chip-clear:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--c-accent-primary) 40%, transparent);
}
.zp-chip-caret {
  transition: transform 180ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
  color: currentColor;
  opacity: 0.7;
}
.zp-chip-wrap.open .zp-chip-caret {
  transform: rotate(180deg);
  opacity: 1;
}

.zp-chip-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  min-width: 160px;
  padding: 6px;
  background: var(--c-bg-base-elevated, #ffffff);
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  box-shadow: 0 14px 28px color-mix(in srgb, var(--c-text-primary, #0f172a) 14%, transparent);
  display: flex;
  flex-direction: column;
  gap: 2px;
  /* Enter animation: fade + slide down 4px, matches the nav dropdown
     timing so the two feel cohesive. */
  animation: zp-chip-in 140ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
}
.zp-chip-panel--wide {
  min-width: 240px;
  padding: 10px;
  gap: 8px;
}

@keyframes zp-chip-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.zp-chip-option {
  text-align: left;
  padding: 7px 10px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: background-color 120ms ease, color 120ms ease;
}
.zp-chip-option:hover {
  background: color-mix(in srgb, var(--c-accent-primary) 8%, transparent);
  color: var(--c-accent-primary);
}
.zp-chip-option.active {
  background: color-mix(in srgb, var(--c-accent-primary) 11%, transparent);
  color: var(--c-accent-primary);
  font-weight: 600;
}
.zp-chip-option.primary {
  background: var(--c-accent-primary);
  color: #ffffff;
}
.zp-chip-option.primary:hover {
  background: var(--c-accent-primary-hover, #004ba8);
}

.zp-chip-input-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border: 1px solid var(--c-border-glass);
  border-radius: 8px;
  background: var(--c-bg-surface, #ffffff);
  color: var(--c-text-muted);
}
.zp-chip-input-wrap:focus-within {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-accent-primary) 14%, transparent);
}
.zp-chip-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-primary);
}
.zp-chip-input-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.zp-clear-link {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  border: none;
  background: transparent;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12.5px;
  cursor: pointer;
  transition: color 150ms ease;
}
.zp-clear-link:hover { color: var(--c-accent-primary); }

/* ═══════════════════════════════════════════════════════════════════
   Sort tabs (智能匹配 / 薪酬最高 / 最新发布)
   ═══════════════════════════════════════════════════════════════════ */
.zp-sort-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 14px 0;
  border-bottom: 1px solid color-mix(in srgb, var(--c-text-primary, #181b23) 8%, transparent);
}
.zp-sort-tab {
  position: relative;
  padding: 12px 16px;
  border: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: color 160ms ease;
}
.zp-sort-tab:hover { color: var(--c-text-primary); }
.zp-sort-tab.active {
  color: var(--c-accent-primary);
  font-weight: 600;
}
.zp-sort-tab.active::after {
  content: '';
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--c-accent-primary);
}

/* ---------------- Jobs grid ---------------- */
.jobs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.list-enter-active,
.list-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

/* ---------------- Empty + Loading ---------------- */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.loader-ring {
  width: 36px;
  height: 36px;
  border: 2px solid color-mix(in srgb, var(--c-accent-primary) 16%, transparent);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 240px;
  padding: 40px;
  border: 1px dashed color-mix(in srgb, var(--c-accent-primary) 22%, transparent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--c-accent-primary) 5%, transparent);
}
.empty-icon {
  color: var(--c-accent-primary);
  opacity: 0.35;
  margin-bottom: 8px;
}
.empty-state p {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text-primary);
}
.empty-state span {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-muted);
}

/* ---------------- Pagination ---------------- */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  padding: 4px 0 0;
}

.page-btn {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-secondary);
  background: var(--c-bg-base-elevated, #ffffff);
  border: 1px solid var(--c-border-glass);
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
}
.page-btn:hover:not(:disabled):not(.active) {
  background: color-mix(in srgb, var(--c-accent-primary) 8%, transparent);
  border-color: color-mix(in srgb, var(--c-accent-primary) 24%, transparent);
  color: var(--c-accent-primary);
}
.page-btn.active {
  background: var(--c-accent-primary);
  border-color: var(--c-accent-primary);
  color: #ffffff;
  font-weight: 600;
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-ellipsis {
  color: var(--c-text-muted);
  padding: 0 4px;
  font-family: var(--font-sans);
}

/* ---------------- Modal ---------------- */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: color-mix(in srgb, var(--c-text-primary, #181b23) 42%, transparent);
  backdrop-filter: blur(6px);
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal-wrapper {
  width: 100%;
  max-width: 760px;
  animation: modalScaleUp 0.28s cubic-bezier(0.2, 0.8, 0.2, 1);
}

@keyframes modalScaleUp {
  from { opacity: 0; transform: scale(0.96) translateY(12px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-content {
  background: var(--c-bg-base-elevated, #ffffff);
  border: 1px solid var(--c-border-glass);
  border-radius: 16px;
  box-shadow: 0 20px 48px color-mix(in srgb, var(--c-text-primary, #181b23) 18%, transparent);
  overflow: hidden;
  position: relative;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
}
.modal-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
  background: var(--c-bg-base-elevated, #ffffff);
  border: 1px solid var(--c-border-glass);
  cursor: pointer;
  transition:
    background-color 150ms ease,
    color 150ms ease,
    border-color 150ms ease;
  z-index: 5;
}
.modal-close:hover {
  background: color-mix(in srgb, var(--c-accent-primary) 8%, transparent);
  color: var(--c-accent-primary);
  border-color: color-mix(in srgb, var(--c-accent-primary) 24%, transparent);
}

.modal-header {
  padding: 28px 32px 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  border-bottom: 1px solid color-mix(in srgb, var(--c-text-primary, #181b23) 8%, transparent);
}

.header-main {
  flex: 1;
  min-width: 0;
  padding-right: 36px;
}

.modal-title {
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--c-text-primary);
  line-height: 1.25;
  letter-spacing: -0.01em;
}

.modal-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-sans);
  font-size: 13.5px;
  color: var(--c-text-secondary);
}
.modal-meta-row .dot {
  color: var(--c-text-faint);
}

.salary-box {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  text-align: right;
}

.salary-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  color: var(--c-text-muted);
  letter-spacing: 0.04em;
}

.modal-salary {
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 700;
  color: var(--c-accent-primary);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}

.modal-tags {
  padding: 14px 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: color-mix(in srgb, var(--c-accent-primary) 5%, transparent);
  border-bottom: 1px solid color-mix(in srgb, var(--c-text-primary, #181b23) 8%, transparent);
}

.tag-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.detail-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: 8px;
  background: var(--c-bg-surface, #ffffff);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
}

.detail-tag :deep(svg) {
  color: var(--c-accent-primary);
  opacity: 0.7;
}

.time-stamp {
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-muted);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 32px 28px;
  /* Firefox */
  scrollbar-width: thin;
  scrollbar-color: var(--c-border-glass-hover) transparent;
}

/* Webkit custom scrollbar */
.modal-body::-webkit-scrollbar {
  width: 6px;
}
.modal-body::-webkit-scrollbar-track {
  background: transparent;
}
.modal-body::-webkit-scrollbar-thumb {
  background: var(--c-border-glass-hover);
  border-radius: 999px;
}
.modal-body::-webkit-scrollbar-thumb:hover {
  background: var(--c-accent-primary);
}

.detail-section {
  margin-top: 20px;
}
.detail-section:first-child {
  margin-top: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.title-indicator {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--c-accent-primary);
}

.section-title h3 {
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  margin: 0;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
}

.detail-text {
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--c-text-primary);
  white-space: pre-line;
  word-break: break-word;
}

.modal-footer {
  padding: 16px 32px;
  background: color-mix(in srgb, var(--c-accent-primary) 5%, transparent);
  display: flex;
  gap: 10px;
  align-items: center;
  border-top: 1px solid color-mix(in srgb, var(--c-text-primary, #181b23) 8%, transparent);
}

.action-button {
  padding: 9px 16px;
  border-radius: 10px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
  text-decoration: none;
}

.action-button.primary {
  background: var(--c-accent-primary);
  color: #ffffff;
  border: 1px solid var(--c-accent-primary);
}
.action-button.primary:hover {
  background: #004ba8;
  border-color: #004ba8;
}

.action-button.outline {
  background: var(--c-bg-base-elevated, #ffffff);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
}
.action-button.outline:hover {
  background: color-mix(in srgb, var(--c-accent-primary) 8%, transparent);
  border-color: color-mix(in srgb, var(--c-accent-primary) 24%, transparent);
  color: var(--c-text-primary);
}

.loading-state-simple {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 0;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 13px;
}

.loader-ring-sm {
  width: 26px;
  height: 26px;
  border: 2px solid color-mix(in srgb, var(--c-accent-primary) 16%, transparent);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* Modal fade transition */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.22s ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

/* ---------------- Responsive ---------------- */
@media (max-width: 900px) {
  /* One-per-row cards once viewport gets tight enough that two
     320px columns can't breathe. */
  .jobs-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}

@media (max-width: 768px) {
  .zp-search-card {
    padding: 14px 16px 12px;
    gap: 12px;
  }
  .zp-search-input { font-size: 14.5px; }
  .zp-search-btn { width: 40px; height: 34px; }
  .zp-chip-row {
    /* Allow chips to scroll horizontally on narrow screens so they
       don't wrap into an awkward two-row stack. The clear link drops
       below the scrollable strip. */
    gap: 16px;
  }
  .zp-chip { font-size: 13px; }
  .zp-sort-tab { padding: 10px 12px; font-size: 13.5px; }
  .zp-sort-tab.active::after { left: 12px; right: 12px; }

  .jobs-panel-body {
    padding: 16px 18px 18px;
  }

  /* Modal: trade the 24px overlay padding for 12px so the content
     card isn't crammed into a 327px-wide box on a 375px phone. */
  .modal-overlay {
    padding: 12px;
    padding-bottom: max(12px, env(safe-area-inset-bottom, 0px));
  }
  .modal-content {
    max-height: 92dvh;
    border-radius: 14px;
  }
  .modal-close {
    top: 12px;
    right: 12px;
    width: 30px;
    height: 30px;
  }
  .modal-header {
    flex-direction: column;
    align-items: flex-start;
    padding: 22px 18px 14px;
    gap: 10px;
  }
  .header-main {
    padding-right: 36px;
  }
  .modal-title {
    font-size: 19px;
    line-height: 1.3;
  }
  .salary-box {
    align-items: flex-start;
    text-align: left;
  }
  .modal-tags {
    padding: 12px 18px;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .modal-body {
    padding: 14px 18px 20px;
    font-size: 13.5px;
  }
  .modal-footer {
    padding: 12px 18px calc(12px + env(safe-area-inset-bottom, 0px));
    flex-direction: column-reverse;
    gap: 8px;
  }
  .action-button {
    width: 100%;
    justify-content: center;
    min-height: 44px;
  }

  /* Pagination: bigger touch targets, wrap to a second row if many pages */
  .pagination {
    flex-wrap: wrap;
    row-gap: 6px;
  }
  .page-btn {
    min-width: 36px;
    height: 36px;
  }
}

@media (max-width: 420px) {
  .modal-header { padding: 18px 14px 12px; }
  .modal-tags { padding: 10px 14px; }
  .modal-body { padding: 12px 14px 18px; }
  .modal-footer { padding: 10px 14px calc(10px + env(safe-area-inset-bottom, 0px)); }
}
</style>
