<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import JobCard from '../components/jobs/JobCard.vue'
import JobDetailModal from '../components/jobs/JobDetailModal.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import {
  Search,
  MapPin,
  X,
  ChevronLeft,
  ChevronRight,
  ChevronDown
} from 'lucide-vue-next'
import {
  fetchJobs,
  fetchJobDetail,
  fetchJobsByEducation,
  fetchJobsByExperience,
  fetchCompanySizeDistribution,
  fetchFinanceStageDistribution
} from '../api'
import { mapErrorMessage } from '../utils/errorMap'

const route = useRoute()
const router = useRouter()

function createDefaultQuery() {
  return {
    keyword: '',
    city: '',
    education: '',
    experience: '',
    positionType: '',
    companyNature: '',
    companySize: '',
    salaryMin: '',
    salaryMax: '',
    sortOrder: 'desc'
  }
}

const query = ref(createDefaultQuery())
const jobs = ref([])
const totalJobs = ref(0)
const currentPage = ref(1)
const pageSize = ref(21)
const isLoading = ref(false)
const listError = ref('')
const detailError = ref('')

const DEFAULT_EDUCATION_OPTIONS = ['不限', '学历不限', '初中及以下', '高中', '中专/中技', '大专', '本科', '硕士', '博士', '其他']
const DEFAULT_EXPERIENCE_OPTIONS = ['不限', '经验不限', '1年以下', '1-3年', '3-5年', '5-10年', '10年以上']
const DEFAULT_COMPANY_NATURE_OPTIONS = ['不限', '未融资', '天使轮', 'A轮', 'B轮', 'C轮', 'D轮及以上', '已上市', '不需要融资']
const DEFAULT_COMPANY_SIZE_OPTIONS = ['不限', '20人以下', '20-99人', '100-299人', '300-499人', '500-999人', '1000-9999人', '10000人以上']
const educationOptions = ref([...DEFAULT_EDUCATION_OPTIONS])
const experienceOptions = ref([...DEFAULT_EXPERIENCE_OPTIONS])
const positionTypeOptions = ['不限', '全职', '兼职', '实习', '校招', '社招']
const companyNatureOptions = ref([...DEFAULT_COMPANY_NATURE_OPTIONS])
const companySizeOptions = ref([...DEFAULT_COMPANY_SIZE_OPTIONS])
const salaryRangePresets = [
  { label: '不限', min: '', max: '' },
  { label: '3K 以下', min: '', max: 3 },
  { label: '3-5K', min: 3, max: 5 },
  { label: '5-10K', min: 5, max: 10 },
  { label: '10-20K', min: 10, max: 20 },
  { label: '20-50K', min: 20, max: 50 },
  { label: '50K 以上', min: 50, max: '' }
]
const sortOptions = [
  { value: 'desc', label: '最新发布' },
  { value: 'asc', label: '最早发布' }
]

const cityGroups = {
  '福建': ['福州', '厦门', '泉州', '漳州', '南平', '宁德', '三明', '龙岩', '莆田'],
  '北京': ['北京'],
  '上海': ['上海'],
  '广东': ['广州', '深圳', '东莞', '佛山', '珠海', '中山'],
  '浙江': ['杭州', '宁波', '温州', '绍兴', '嘉兴'],
  '江苏': ['南京', '苏州', '无锡', '常州', '南通'],
  '山东': ['青岛', '济南', '烟台', '潍坊'],
  '四川': ['成都', '绵阳'],
  '湖北': ['武汉', '宜昌'],
  '湖南': ['长沙', '株洲'],
  '陕西': ['西安', '宝鸡'],
  '河南': ['郑州', '洛阳'],
  '天津': ['天津'],
  '重庆': ['重庆'],
  '安徽': ['合肥', '芜湖'],
  '辽宁': ['沈阳', '大连'],
  '云南': ['昆明', '大理'],
  '广西': ['南宁', '桂林']
}
const openFilterKey = ref('')
let filterCloseTimer = null

const citySelectedProvince = ref('福建')
const citySelectedCities = computed(() => cityGroups[citySelectedProvince.value] || [])

watch(
  [openFilterKey, () => query.value.city],
  ([key, city]) => {
    if (key !== 'city') return
    if (!city) return
    const match = Object.keys(cityGroups).find((prov) => cityGroups[prov].includes(city))
    if (match) citySelectedProvince.value = match
  },
  { immediate: true }
)

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
  if (openFilterKey.value) closeFilterNow()
}

// 详情弹窗
const selectedJob = ref(null)
const isLoadingDetail = ref(false)
const skipRouteWatch = ref(false)

const totalPages = computed(() => Math.ceil(totalJobs.value / pageSize.value) || 1)

const cityChipLabel = computed(() => query.value.city.trim() || '城市')
const educationChipLabel = computed(() => query.value.education || '学历要求')
const experienceChipLabel = computed(() => query.value.experience || '工作经验')
const positionTypeChipLabel = computed(() => query.value.positionType || '职位类型')
const companyNatureChipLabel = computed(() => query.value.companyNature || '融资阶段')
const companySizeChipLabel = computed(() => query.value.companySize || '公司规模')
const salaryChipLabel = computed(() => {
  const min = query.value.salaryMin
  const max = query.value.salaryMax
  if (min === '' && max === '') return '薪资范围'
  if (min !== '' && max !== '') return `${min}-${max}K`
  if (min !== '') return `${min}K 以上`
  return `${max}K 以下`
})
const sortChipLabel = computed(
  () => sortOptions.find((o) => o.value === query.value.sortOrder)?.label || '最新发布'
)

const isCityActive = computed(() => !!query.value.city.trim())
const isEducationActive = computed(() => !!query.value.education)
const isExperienceActive = computed(() => !!query.value.experience)
const isPositionTypeActive = computed(() => !!query.value.positionType)
const isCompanyNatureActive = computed(() => !!query.value.companyNature)
const isCompanySizeActive = computed(() => !!query.value.companySize)
const isSalaryActive = computed(() => query.value.salaryMin !== '' || query.value.salaryMax !== '')
const isSortActive = computed(() => query.value.sortOrder && query.value.sortOrder !== 'desc')
const activeFilterCount = computed(() =>
  Number(isCityActive.value) +
  Number(isEducationActive.value) +
  Number(isExperienceActive.value) +
  Number(isPositionTypeActive.value) +
  Number(isCompanyNatureActive.value) +
  Number(isCompanySizeActive.value) +
  Number(isSalaryActive.value) +
  Number(isSortActive.value)
)
const hasAnyFilter = computed(() => activeFilterCount.value > 0)

function pickCity(opt) {
  query.value.city = opt === '不限' ? '' : opt
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
function pickSalaryPreset(preset) {
  query.value.salaryMin = preset.min === '' ? '' : preset.min
  query.value.salaryMax = preset.max === '' ? '' : preset.max
  closeFilterNow()
  loadJobs(1)
}
function applySalaryCustom() {
  closeFilterNow()
  loadJobs(1)
}
function pickSort(opt) {
  query.value.sortOrder = opt.value
  closeFilterNow()
  loadJobs(1)
}

function extractChartRows(payload) {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.data)) return payload.data
  return []
}

function buildBackendOptions(rows, field, fallback, preferredOrder = fallback.slice(1)) {
  const backendValues = extractChartRows(rows)
    .map((item) => `${item?.[field] ?? ''}`.trim())
    .filter(Boolean)

  const seen = new Set()
  const ordered = []
  preferredOrder.forEach((value) => {
    if (backendValues.includes(value) && !seen.has(value)) {
      ordered.push(value)
      seen.add(value)
    }
  })
  backendValues.forEach((value) => {
    if (!seen.has(value)) {
      ordered.push(value)
      seen.add(value)
    }
  })

  return ordered.length ? ['不限', ...ordered] : fallback
}

function ensureOption(optionsRef, value) {
  const normalized = `${value ?? ''}`.trim()
  if (normalized && !optionsRef.value.includes(normalized)) {
    optionsRef.value = [...optionsRef.value, normalized]
  }
}

function ensureSelectedFilterOptions() {
  ensureOption(educationOptions, query.value.education)
  ensureOption(experienceOptions, query.value.experience)
  ensureOption(companyNatureOptions, query.value.companyNature)
  ensureOption(companySizeOptions, query.value.companySize)
}

async function loadFilterOptions() {
  const [
    educationResult,
    experienceResult,
    companySizeResult,
    financeStageResult
  ] = await Promise.allSettled([
    fetchJobsByEducation(),
    fetchJobsByExperience(),
    fetchCompanySizeDistribution(),
    fetchFinanceStageDistribution()
  ])

  if (educationResult.status === 'fulfilled') {
    educationOptions.value = buildBackendOptions(
      educationResult.value,
      'education',
      DEFAULT_EDUCATION_OPTIONS
    )
  }
  if (experienceResult.status === 'fulfilled') {
    experienceOptions.value = buildBackendOptions(
      experienceResult.value,
      'experience',
      DEFAULT_EXPERIENCE_OPTIONS
    )
  }
  if (companySizeResult.status === 'fulfilled') {
    companySizeOptions.value = buildBackendOptions(
      companySizeResult.value,
      'companySize',
      DEFAULT_COMPANY_SIZE_OPTIONS
    )
  }
  if (financeStageResult.status === 'fulfilled') {
    companyNatureOptions.value = buildBackendOptions(
      financeStageResult.value,
      'financeStage',
      DEFAULT_COMPANY_NATURE_OPTIONS
    )
  }

  ensureSelectedFilterOptions()
}

function clearCity() {
  query.value.city = ''
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
function clearSalary() {
  query.value.salaryMin = ''
  query.value.salaryMax = ''
  closeFilterNow()
  loadJobs(1)
}
function clearSort() {
  query.value.sortOrder = 'desc'
  closeFilterNow()
  loadJobs(1)
}

function normalizeRouteValue(value) {
  if (value === undefined || value === null || value === '') {
    return ''
  }
  return `${value}`
}

function normalizeRouteNumber(value) {
  if (value === undefined || value === null || value === '') return ''
  const num = Number(value)
  return Number.isFinite(num) ? num : ''
}

function applyRouteQuery(routeQuery) {
  const sort = normalizeRouteValue(routeQuery.sortOrder)
  query.value = {
    keyword: normalizeRouteValue(routeQuery.keyword),
    city: normalizeRouteValue(routeQuery.city),
    education: normalizeRouteValue(routeQuery.education),
    experience: normalizeRouteValue(routeQuery.experience),
    positionType: normalizeRouteValue(routeQuery.positionType),
    companyNature: normalizeRouteValue(routeQuery.companyNature),
    companySize: normalizeRouteValue(routeQuery.companySize),
    salaryMin: normalizeRouteNumber(routeQuery.salaryMin),
    salaryMax: normalizeRouteNumber(routeQuery.salaryMax),
    sortOrder: sort === 'asc' ? 'asc' : 'desc'
  }
  currentPage.value = routeQuery.page ? Number(routeQuery.page) || 1 : 1
  ensureSelectedFilterOptions()
}

function buildRouteQuery(page = 1, extra = {}) {
  const next = {}
  Object.entries(query.value).forEach(([key, value]) => {
    if (value === null || value === undefined || `${value}`.trim() === '') return
    // 默认排序不出现在 URL 里，避免每次点击都污染历史
    if (key === 'sortOrder' && value === 'desc') return
    next[key] = value
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
    listError.value = ''
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
    listError.value = mapErrorMessage(error)
    jobs.value = []
    totalJobs.value = 0
  } finally {
    isLoading.value = false
  }
}

async function openDetail(job) {
  if (!job?.id) return
  isLoadingDetail.value = true
  detailError.value = ''
  selectedJob.value = {
    title: '岗位详情',
    ...job
  }
  skipRouteWatch.value = true
  router.replace({ path: '/jobs', query: buildRouteQuery(currentPage.value, { open: job.id }) })
  try {
    const detail = await fetchJobDetail(job.id)
    selectedJob.value = detail
  } catch (error) {
    detailError.value = mapErrorMessage(error)
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
  detailError.value = ''
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
  loadFilterOptions()
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
            <div v-if="openFilterKey === 'city'" class="zp-chip-panel zp-chip-panel--cascade" role="menu">
              <div class="zp-cascade-provinces">
                <button
                  v-for="prov in Object.keys(cityGroups)"
                  :key="prov"
                  type="button"
                  class="zp-cascade-prov"
                  :class="{ active: citySelectedProvince === prov }"
                  @mouseenter="citySelectedProvince = prov"
                  @focus="citySelectedProvince = prov"
                >{{ prov }}</button>
              </div>
              <div class="zp-cascade-cities">
                <button
                  type="button"
                  class="zp-chip-option"
                  :class="{ active: !query.city }"
                  @click="pickCity('不限')"
                >不限</button>
                <button
                  v-for="city in citySelectedCities"
                  :key="city"
                  type="button"
                  class="zp-chip-option"
                  :class="{ active: query.city === city }"
                  @click="pickCity(city)"
                >{{ city }}</button>
              </div>
            </div>
          </div>
        </div>

        <div class="zp-chip-row">
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
            <div v-if="openFilterKey === 'salary'" class="zp-chip-panel zp-chip-panel--salary" role="menu">
              <button
                v-for="preset in salaryRangePresets"
                :key="preset.label"
                class="zp-chip-option"
                :class="{ active: query.salaryMin === preset.min && query.salaryMax === preset.max }"
                type="button"
                role="menuitem"
                @click="pickSalaryPreset(preset)"
              >{{ preset.label }}</button>
              <div class="zp-salary-custom">
                <input
                  v-model.number="query.salaryMin"
                  type="number"
                  min="0"
                  placeholder="最低"
                  class="zp-salary-input"
                />
                <span class="zp-salary-sep">-</span>
                <input
                  v-model.number="query.salaryMax"
                  type="number"
                  min="0"
                  placeholder="最高"
                  class="zp-salary-input"
                />
                <span class="zp-salary-unit">K</span>
                <button type="button" class="zp-salary-apply" @click="applySalaryCustom">应用</button>
              </div>
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

          <div
            class="zp-chip-wrap zp-chip-wrap--sort"
            :class="{ open: openFilterKey === 'sort' }"
            @mouseenter="openFilter('sort')"
            @mouseleave="scheduleCloseFilter"
          >
            <div
              class="zp-chip"
              :class="{ active: isSortActive }"
              tabindex="0"
              role="button"
              :aria-expanded="openFilterKey === 'sort'"
              @focus="openFilter('sort')"
              @blur="scheduleCloseFilter"
            >
              <button
                v-if="isSortActive"
                type="button"
                class="zp-chip-clear"
                aria-label="恢复默认排序"
                @click.stop.prevent="clearSort"
              >
                <X :size="12" :stroke-width="2" />
              </button>
              <span class="zp-chip-label">{{ sortChipLabel }}</span>
              <ChevronDown :size="14" :stroke-width="1.8" class="zp-chip-caret" />
            </div>
            <div v-if="openFilterKey === 'sort'" class="zp-chip-panel" role="menu">
              <button
                v-for="opt in sortOptions"
                :key="opt.value"
                class="zp-chip-option"
                :class="{ active: query.sortOrder === opt.value }"
                type="button"
                role="menuitem"
                @click="pickSort(opt)"
              >{{ opt.label }}</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="jobs-panel">
      <div class="jobs-panel-body">
        <div v-if="listError" class="status-banner error-banner">{{ listError }}</div>

        <div v-if="isLoading" class="jobs-grid skeleton-grid" aria-hidden="true">
          <SkeletonCard v-for="i in 9" :key="i" type="card" :lines="4" class="skeleton-job" />
        </div>

        <div v-else-if="jobs.length === 0" class="empty-state">
          <Search :size="40" class="empty-icon" :stroke-width="1.4" />
          <p>没有找到匹配的岗位</p>
          <span>试试调整搜索条件或清除筛选</span>
        </div>

        <!-- TransitionGroup 给每张卡片一个 stagger 进入动画：--stagger-i 按序号配合 CSS delay 实现逐张进入 -->
        <TransitionGroup v-else name="stagger" tag="div" class="jobs-grid">
          <JobCard
            v-for="(job, index) in jobs"
            :key="job.id"
            :job="job"
            :style="{ '--stagger-i': index }"
            @open="openDetail"
          />
        </TransitionGroup>

        <div v-if="totalPages > 1 && !isLoading && jobs.length > 0" class="pagination">
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

    <JobDetailModal
      :job="selectedJob"
      :loading="isLoadingDetail"
      :error-message="detailError"
      @close="closeDetail"
    />
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
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: 0 6px 18px rgba(24, 27, 35, 0.05);
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
  background: var(--c-bg-base-elevated);
  border-bottom: 1px solid var(--c-border-glass);
  box-shadow: 0 1px 0 rgba(24, 27, 35, 0.02);
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

/* -- Row 2: city dropdown ------------------------------------------- */
.zp-location-row {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-top: 4px;
}
/* The city chip gets a subtle pin-icon prefix and slightly larger
   visual weight than the filter chips below it; it's the secondary
   anchor of the strip (primary = search input). */
.zp-chip--city {
  padding-left: 0;
}
.zp-chip-city-icon {
  color: var(--c-text-muted);
  flex: none;
  margin-right: 2px;
  transition: color 140ms ease;
}
.zp-chip--city.active .zp-chip-city-icon {
  color: var(--c-accent-primary);
}
/* City popover: two-column cascade — provinces on the left, cities on
   the right. Hovering a province tab swaps the right-hand list. */
/* Double-class selector beats the base .zp-chip-panel rule below,
   which otherwise forces display: flex column and stacks the two
   cascade columns on top of each other. The popover is also nudged
   right so the provinces don't sit flush against the chip's left
   edge — makes the two-level layout feel balanced. */
.zp-chip-panel.zp-chip-panel--cascade {
  display: grid;
  grid-template-columns: 112px minmax(280px, 1fr);
  gap: 0;
  min-width: 440px;
  max-width: 560px;
  padding: 0;
  overflow: hidden;
  left: 24px;
}
.zp-cascade-provinces {
  display: flex;
  flex-direction: column;
  padding: 6px;
  gap: 2px;
  max-height: 320px;
  overflow-y: auto;
  border-right: 1px solid var(--c-border-glass);
  background: rgba(0, 87, 194, 0.02);
}
.zp-cascade-prov {
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
.zp-cascade-prov:hover,
.zp-cascade-prov.active {
  background: var(--c-bg-base-elevated);
  color: var(--c-accent-primary);
  font-weight: 600;
}
.zp-cascade-cities {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 2px 4px;
  padding: 8px;
  align-content: start;
  max-height: 320px;
  overflow-y: auto;
}
.zp-cascade-cities .zp-chip-option {
  padding: 6px 10px;
  text-align: center;
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
.zp-chip-wrap--sort {
  margin-left: auto;
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
  box-shadow: 0 0 0 2px rgba(0, 87, 194, 0.28);
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
  background: rgba(0, 87, 194, 0.12);
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
  box-shadow: 0 0 0 2px rgba(0, 87, 194, 0.4);
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
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  gap: 2px;
  /* Enter animation: fade + slide down 4px, matches the nav dropdown
     timing so the two feel cohesive. */
  animation: zp-chip-in 140ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
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
  background: rgba(0, 87, 194, 0.06);
  color: var(--c-accent-primary);
}
.zp-chip-option.active {
  background: rgba(0, 87, 194, 0.09);
  color: var(--c-accent-primary);
  font-weight: 600;
}

.zp-clear-link {
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

/* ---------------- Jobs grid ---------------- */
.status-banner {
  padding: 12px 14px;
  border-radius: 10px;
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.5;
}

.error-banner {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.84);
  border: 1px solid rgba(248, 113, 113, 0.28);
}

.jobs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.stagger-enter-active {
  transition: opacity 240ms ease, transform 240ms cubic-bezier(0.2, 0.65, 0.32, 1);
  transition-delay: calc(var(--stagger-i, 0) * 30ms);
}
.stagger-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}
.stagger-enter-from {
  opacity: 0;
  transform: translateY(14px) scale(0.98);
}
.stagger-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
.stagger-move {
  transition: transform 260ms ease;
}
@media (prefers-reduced-motion: reduce) {
  .stagger-enter-active, .stagger-leave-active, .stagger-move {
    transition: none;
  }
  .stagger-enter-from { opacity: 1; transform: none; }
}

/* ---------------- Skeleton ---------------- */
.skeleton-grid { pointer-events: none; }
.skeleton-job {
  min-height: 170px;
  padding: 18px;
  border-radius: 14px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
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
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
}
.page-btn:hover:not(:disabled):not(.active) {
  background: rgba(0, 87, 194, 0.06);
  border-color: rgba(0, 87, 194, 0.2);
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

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 240px;
  padding: 40px;
  border: 1px dashed rgba(0, 87, 194, 0.18);
  border-radius: 12px;
  background: rgba(0, 87, 194, 0.02);
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

:global([data-theme="dark"]) .error-banner {
  background: rgba(178, 59, 46, 0.18);
  color: #ffb4a6;
  border-color: rgba(248, 113, 113, 0.18);
}

/* ---------------- Responsive ---------------- */
@media (max-width: 900px) {
  .jobs-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}

@media (max-width: 768px) {
  .jobs-search-strip {
    margin-top: -12px;
  }
  .jobs-search-inner {
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
  .zp-chip-panel.zp-chip-panel--cascade {
    min-width: 280px;
    grid-template-columns: 80px 1fr;
    left: 8px;
  }
  .zp-cascade-cities {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .jobs-panel-body {
    padding: 16px 18px 18px;
  }

  .pagination {
    flex-wrap: wrap;
    row-gap: 6px;
  }
  .page-btn {
    min-width: 36px;
    height: 36px;
  }
}

:global([data-theme="dark"]) .zp-search-btn,
:global([data-theme="dark"]) .zp-chip-clear:hover,
:global([data-theme="dark"]) .page-btn.active {
  color: #0f1420;
}
:global([data-theme="dark"]) .zp-cascade-prov:hover,
:global([data-theme="dark"]) .zp-cascade-prov.active {
  background: var(--c-bg-surface-hover);
}

.zp-chip-panel--salary {
  min-width: 220px;
}
.zp-salary-custom {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-top: 1px solid var(--c-border-subtle, #e5e7eb);
  margin-top: 4px;
}
.zp-salary-input {
  width: 58px;
  padding: 4px 8px;
  border: 1px solid var(--c-border-subtle, #d1d5db);
  border-radius: 6px;
  font-size: 12px;
  background: var(--c-bg-surface, #fff);
  color: inherit;
  outline: none;
}
.zp-salary-input:focus {
  border-color: var(--c-brand, #2a6eff);
}
.zp-salary-sep {
  color: var(--c-text-muted, #9ca3af);
  font-size: 12px;
}
.zp-salary-unit {
  color: var(--c-text-muted, #6b7280);
  font-size: 12px;
  margin-left: 2px;
}
.zp-salary-apply {
  margin-left: auto;
  padding: 4px 10px;
  border: none;
  border-radius: 6px;
  background: var(--c-brand, #2a6eff);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
.zp-salary-apply:hover {
  opacity: 0.9;
}
</style>
