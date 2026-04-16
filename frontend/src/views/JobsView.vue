<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import JobCard from '../components/jobs/JobCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { Search, MapPin, Building2, SlidersHorizontal, X, ChevronLeft, ChevronRight, ExternalLink, Clock, GraduationCap, Briefcase } from 'lucide-vue-next'
import { fetchJobs, fetchJobDetail } from '../api'

const route = useRoute()
const router = useRouter()

const query = ref({
  keyword: '',
  city: '',
  industry: '',
  education: '',
  experience: '',
  salaryMin: null,
  salaryMax: null
})

const jobs = ref([])
const totalJobs = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const isLoading = ref(false)
const showFilters = ref(false)

// 详情弹窗
const selectedJob = ref(null)
const isLoadingDetail = ref(false)

const totalPages = computed(() => Math.ceil(totalJobs.value / pageSize.value) || 1)
const activeFilters = computed(() => {
  const chips = []

  if (query.value.keyword.trim()) {
    chips.push({ key: 'keyword', label: '关键词', value: query.value.keyword.trim() })
  }
  if (query.value.city.trim()) {
    chips.push({ key: 'city', label: '城市', value: query.value.city.trim() })
  }
  if (query.value.industry.trim()) {
    chips.push({ key: 'industry', label: '行业', value: query.value.industry.trim() })
  }
  if (query.value.education) {
    chips.push({ key: 'education', label: '学历', value: query.value.education })
  }
  if (query.value.experience) {
    chips.push({ key: 'experience', label: '经验', value: query.value.experience })
  }
  if (query.value.salaryMin !== null || query.value.salaryMax !== null) {
    chips.push({
      key: 'salary',
      label: '薪资',
      value: `${query.value.salaryMin ?? '不限'} ~ ${query.value.salaryMax ?? '不限'} K`
    })
  }

  return chips
})
const pageSignals = computed(() => [
  {
    label: '结果总量',
    value: totalJobs.value.toLocaleString(),
    note: '岗位样本'
  },
  {
    label: '当前页',
    value: `${currentPage.value}/${totalPages.value}`,
    note: '分页浏览'
  },
  {
    label: '已选条件',
    value: `${activeFilters.value.length}`,
    note: '可一键清空'
  }
])

const loadJobs = async (page = 1) => {
  isLoading.value = true
  currentPage.value = page
  try {
    const res = await fetchJobs({
      ...query.value,
      page,
      pageSize: pageSize.value
    })
    jobs.value = res.data
    totalJobs.value = res.total
  } catch (error) {
    console.error('加载岗位失败', error)
  } finally {
    isLoading.value = false
  }
}

const openDetail = async (job) => {
  isLoadingDetail.value = true
  selectedJob.value = { ...job }
  try {
    const detail = await fetchJobDetail(job.id)
    selectedJob.value = detail
  } catch (e) {
    // 如果详情加载失败，仍展示列表数据
    console.error('加载职位详情失败', e)
  } finally {
    isLoadingDetail.value = false
  }
}

const openDetailById = async (jobId) => {
  const normalizedId = Number(jobId)
  if (!Number.isFinite(normalizedId)) {
    return
  }

  const existingJob = jobs.value.find((job) => Number(job.id) === normalizedId)
  if (existingJob) {
    await openDetail(existingJob)
    return
  }

  isLoadingDetail.value = true
  selectedJob.value = { id: normalizedId, title: '职位详情加载中...' }
  try {
    const detail = await fetchJobDetail(normalizedId)
    selectedJob.value = detail
  } catch (e) {
    console.error('加载职位详情失败', e)
    selectedJob.value = null
  } finally {
    isLoadingDetail.value = false
  }
}

const closeDetail = () => {
  selectedJob.value = null
  if (route.query.jobId) {
    const nextQuery = { ...route.query }
    delete nextQuery.jobId
    delete nextQuery.from
    router.replace({ path: route.path, query: nextQuery })
  }
}

const resetFilters = () => {
  query.value = { keyword: '', city: '', industry: '', education: '', experience: '', salaryMin: null, salaryMax: null }
  loadJobs(1)
}

const clearFilter = (key) => {
  if (key === 'salary') {
    query.value.salaryMin = null
    query.value.salaryMax = null
    loadJobs(1)
    return
  }

  query.value[key] = ''
  loadJobs(1)
}

const educationOptions = ['不限', '大专', '本科', '硕士', '博士']

onMounted(() => {
  loadJobs()
})

watch(
  () => route.query.jobId,
  async (jobId) => {
    if (!jobId) {
      return
    }
    await openDetailById(jobId)
  },
  { immediate: true }
)
</script>

<template>
  <div class="jobs-layout page-shell">
    <header class="page-header">
      <div class="page-copy">
        <p class="page-kicker">岗位检索</p>
        <h1>把职位搜索、筛选和详情放在同一页</h1>
        <p>支持关键词、城市、行业、学历、经验和薪资筛选。</p>
      </div>
      <div class="summary-grid">
        <article v-for="signal in pageSignals" :key="signal.label" class="summary-card">
          <span>{{ signal.label }}</span>
          <strong>{{ signal.value }}</strong>
          <p>{{ signal.note }}</p>
        </article>
      </div>
    </header>

    <section class="jobs-toolbar">
      <div class="search-grid">
        <div class="input-group main-search">
          <Search class="input-icon" :size="18" />
          <input v-model="query.keyword" type="text" placeholder="搜索职位名称、公司或关键词" class="glass-input" @keyup.enter="loadJobs(1)" />
        </div>
        <div class="input-group">
          <MapPin class="input-icon" :size="18" />
          <input v-model="query.city" type="text" placeholder="城市" class="glass-input" @keyup.enter="loadJobs(1)" />
        </div>
        <div class="input-group">
          <Building2 class="input-icon" :size="18" />
          <input v-model="query.industry" type="text" placeholder="行业" class="glass-input" @keyup.enter="loadJobs(1)" />
        </div>
        <div class="toolbar-actions">
          <GlowButton variant="primary" @click="loadJobs(1)">搜索</GlowButton>
          <GlowButton variant="ghost" @click="showFilters = !showFilters">
            <SlidersHorizontal :size="18" />
          </GlowButton>
        </div>
      </div>

      <transition name="slide-fade">
        <div v-if="showFilters" class="filter-drawer">
          <div class="filter-row">
            <div class="filter-group">
              <label>学历要求</label>
              <div class="filter-chips">
                <button
                  v-for="edu in educationOptions"
                  :key="edu"
                  class="filter-chip"
                  :class="{ active: query.education === (edu === '不限' ? '' : edu) }"
                  @click="query.education = edu === '不限' ? '' : edu"
                >{{ edu }}</button>
              </div>
            </div>
            <div class="filter-group">
              <label>薪资范围（千元/月）</label>
              <div class="salary-range">
                <input v-model.number="query.salaryMin" type="number" placeholder="最低" class="glass-input-sm" />
                <span class="range-sep">—</span>
                <input v-model.number="query.salaryMax" type="number" placeholder="最高" class="glass-input-sm" />
              </div>
            </div>
          </div>
          <div class="filter-actions">
            <GlowButton variant="primary" @click="loadJobs(1)">应用筛选</GlowButton>
            <GlowButton variant="ghost" @click="resetFilters">重置</GlowButton>
          </div>
        </div>
      </transition>

      <div v-if="activeFilters.length" class="active-filter-strip">
        <span class="active-filter-label">已选条件</span>
        <button
          v-for="chip in activeFilters"
          :key="chip.key"
          class="active-filter-chip"
          type="button"
          @click="clearFilter(chip.key)"
        >
          <span>{{ chip.label }}</span>
          <strong>{{ chip.value }}</strong>
          <X :size="12" />
        </button>
        <button class="clear-all" type="button" @click="resetFilters">清空全部</button>
      </div>
    </section>

    <div class="results-meta">
      <span>共找到 <strong>{{ totalJobs.toLocaleString() }}</strong> 个岗位</span>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
    </div>

    <!-- 加载中 -->
    <div v-if="isLoading" class="loading-state">
      <div class="loader-ring"></div>
    </div>

    <!-- 空结果 -->
    <div v-else-if="jobs.length === 0" class="empty-state glass-panel">
      <Search :size="48" class="empty-icon" />
      <p>没有找到匹配的岗位</p>
      <span>试试调整搜索条件或清除筛选</span>
    </div>

    <!-- 岗位列表 -->
    <TransitionGroup v-else name="list" tag="div" class="jobs-grid">
      <JobCard v-for="job in jobs" :key="job.id" :job="job" @open="openDetail" />
    </TransitionGroup>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="pagination">
      <button class="page-btn" :disabled="currentPage <= 1" @click="loadJobs(currentPage - 1)">
        <ChevronLeft :size="18" />
      </button>
      <template v-for="p in totalPages" :key="p">
        <button 
          v-if="p === 1 || p === totalPages || (p >= currentPage - 2 && p <= currentPage + 2)"
          class="page-btn"
          :class="{ active: p === currentPage }"
          @click="loadJobs(p)"
        >{{ p }}</button>
        <span v-else-if="p === currentPage - 3 || p === currentPage + 3" class="page-ellipsis">...</span>
      </template>
      <button class="page-btn" :disabled="currentPage >= totalPages" @click="loadJobs(currentPage + 1)">
        <ChevronRight :size="18" />
      </button>
    </div>

    <!-- 职位详情弹窗 -->
    <Teleport to="body">
      <transition name="modal-fade">
        <div v-if="selectedJob" class="modal-overlay" @click.self="closeDetail">
          <div class="modal-wrapper">
            <div class="modal-content-premium">
              <button class="modal-close" @click="closeDetail">
                <X :size="20" />
              </button>
              
              <div class="modal-header">
                <div class="header-main">
                  <h2 class="modal-title">{{ selectedJob.title }}</h2>
                  <div class="modal-meta-row">
                    <span class="company">{{ selectedJob.companyName }}</span>
                    <span class="dot">·</span>
                    <span class="location">{{ selectedJob.city }}</span>
                  </div>
                </div>
                <div class="salary-box">
                  <span class="salary-label">薪资预算</span>
                  <span class="modal-salary">{{ selectedJob.salaryText || '面议' }}</span>
                </div>
              </div>

              <div class="modal-tags">
                <div class="tag-group">
                  <span class="detail-tag" v-if="selectedJob.education"><GraduationCap :size="14" /> {{ selectedJob.education }}</span>
                  <span class="detail-tag" v-if="selectedJob.experience"><Clock :size="14" /> {{ selectedJob.experience }}</span>
                  <span class="detail-tag" v-if="selectedJob.industryName"><Building2 :size="14" /> {{ selectedJob.industryName }}</span>
                  <span class="detail-tag" v-if="selectedJob.employmentType"><Briefcase :size="14" /> {{ selectedJob.employmentType }}</span>
                </div>
                <div class="time-stamp" v-if="selectedJob.publishDate">发布于 {{ selectedJob.publishDate }}</div>
              </div>

              <div class="modal-body">
                <div v-if="isLoadingDetail" class="loading-state-simple">
                  <div class="loader-ring-sm"></div>
                  <span>正在调配职位详情...</span>
                </div>
                <template v-else>
                  <div v-if="selectedJob.description" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>职位描述</h3>
                    </div>
                    <div class="detail-text" v-html="(selectedJob.description || '').replace(/\n/g, '<br/>')"></div>
                  </div>
                  <div v-if="selectedJob.requirements" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>任职要求</h3>
                    </div>
                    <div class="detail-text" v-html="(selectedJob.requirements || '').replace(/\n/g, '<br/>')"></div>
                  </div>
                </template>
              </div>

              <div class="modal-footer">
                <a 
                  v-if="selectedJob.sourceUrl" 
                  :href="selectedJob.sourceUrl" 
                  target="_blank" 
                  rel="noopener noreferrer" 
                  class="action-button primary"
                >
                  <ExternalLink :size="16" /> 查看原始页面
                </a>
                <button class="action-button outline" @click="closeDetail">关闭详情</button>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
.jobs-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: stretch;
}

.page-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  padding: 18px 0;
}

.page-kicker {
  margin: 0;
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-copy h1 {
  margin: 0;
  color: var(--c-text-primary);
  font-size: clamp(28px, 3vw, 42px);
  line-height: 1.12;
  letter-spacing: -0.03em;
}

.page-copy p {
  margin: 0;
  max-width: 42ch;
  color: var(--c-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px 18px 16px;
  border-radius: 18px;
  border: 1px solid var(--c-border-strong);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 255, 0.9)),
    var(--c-bg-surface);
  box-shadow: var(--shadow-panel);
}

.summary-card span {
  color: var(--c-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.summary-card strong {
  color: var(--c-text-primary);
  font-size: 28px;
  font-weight: 900;
  line-height: 1.1;
  letter-spacing: -0.03em;
}

.summary-card p {
  margin: 0;
  color: var(--c-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.jobs-toolbar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px 24px;
  border-radius: 20px;
  border: 1px solid var(--c-border-strong);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 255, 0.9)),
    var(--c-bg-surface);
  box-shadow: var(--shadow-panel);
}

.search-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  align-items: center;
}

.filter-drawer {
  padding-top: 16px;
  border-top: 1px solid var(--c-border-glass);
}

.active-filter-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.active-filter-label {
  color: var(--c-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.active-filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(193, 198, 215, 0.78);
  background: rgba(255, 255, 255, 0.7);
  color: var(--c-text-secondary);
  font-size: 13px;
  transition: all var(--duration-fast) var(--ease-out);
}

.active-filter-chip:hover {
  border-color: rgba(0, 89, 199, 0.18);
  color: var(--c-text-primary);
}

.active-filter-chip strong {
  font-weight: 700;
  color: var(--c-text-primary);
}

.clear-all {
  color: var(--c-accent-primary);
  font-size: 13px;
  font-weight: 700;
}

.search-card {
  box-shadow: var(--shadow-glass);
}

/* Search Bar */
.search-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.input-group {
  flex: 1 1 180px;
  position: relative;
  display: flex;
  align-items: center;
}
.main-search { flex: 2 1 300px; }

.input-icon {
  position: absolute;
  left: 14px;
  color: var(--c-text-muted);
  pointer-events: none;
}

.glass-input {
  width: 100%;
  padding: 12px 14px 12px 40px;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid var(--c-border-glass);
  border-radius: var(--radius-md);
  color: var(--c-text-primary);
  font-size: 14px;
  transition: all var(--duration-fast) var(--ease-out);
}

.glass-input::placeholder,
.glass-input-sm::placeholder {
  color: var(--c-text-faint);
}

.glass-input:focus {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(56, 189, 248, 0.5);
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.12);
}

/* Advanced Filters */
.advanced-filters {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--c-border-glass);
}

.filter-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.filter-group {
  flex: 1;
  min-width: 200px;
}

.filter-group label {
  display: block;
  font-size: 13px;
  color: var(--c-text-muted);
  margin-bottom: 8px;
}

.filter-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-chip {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  background: rgba(255,255,255,0.04);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  transition: all var(--duration-fast);
}
.filter-chip:hover {
  background: rgba(255,255,255,0.08);
}
.filter-chip.active {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.4);
  color: var(--c-accent-primary);
}

.salary-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.glass-input-sm {
  width: 100px;
  padding: 8px 12px;
  background: rgba(255,255,255,0.03);
  border: 1px solid var(--c-border-glass);
  border-radius: var(--radius-sm);
  color: var(--c-text-primary);
  font-size: 14px;
}
.glass-input-sm:focus {
  border-color: rgba(30, 117, 255, 0.5);
}

.range-sep { color: var(--c-text-muted); }

.filter-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.slide-fade-enter-active { transition: all 0.3s ease; }
.slide-fade-leave-active { transition: all 0.2s ease; }
.slide-fade-enter-from { opacity: 0; transform: translateY(-10px); }
.slide-fade-leave-to { opacity: 0; transform: translateY(-10px); }

/* Results Meta */
.results-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: var(--c-text-muted);
  padding: 0 4px;
}
.results-meta strong { color: var(--c-text-primary); }

/* Jobs Grid & Premium Card */
.jobs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
}

.job-card-premium {
  position: relative;
  padding: 1px; /* space for gradient border */
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-spring);
  overflow: hidden;
  background: rgba(255, 255, 255, 0.03);
}

.job-card-premium::before {
  content: ''; position: absolute; inset: 0; border-radius: inherit;
  background: linear-gradient(135deg, rgba(255,255,255,0.12), transparent 40%);
  z-index: 0;
}

.card-glow {
  position: absolute; width: 140px; height: 140px; top: -70px; right: -70px;
  background: radial-gradient(circle, var(--c-accent-primary-glow), transparent 70%);
  opacity: 0; transition: opacity 0.5s ease; pointer-events: none;
}

.job-card-premium:hover {
  transform: translateY(-5px) scale(1.01);
  box-shadow: var(--shadow-card-raised);
  background: rgba(255, 255, 255, 0.06);
}

.job-card-premium:hover .card-glow { opacity: 0.15; }

.card-content {
  position: relative; z-index: 1; padding: 22px; display: flex; flex-direction: column; gap: 14px;
}

.job-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }

.job-title {
  font-size: 22px; font-weight: 800; margin: 0; line-height: 1.25;
  color: var(--c-text-primary); transition: color 0.3s ease;
  letter-spacing: -0.01em;
}

.job-card-premium:hover .job-title { color: var(--c-accent-primary); }

.job-salary {
  font-size: 20px; font-weight: 900;
  background: linear-gradient(135deg, #FF6B6B, #FFB800);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  font-family: var(--font-display); white-space: nowrap;
  filter: drop-shadow(0 4px 6px rgba(0,0,0,0.1));
}

.company-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 4px; }

.company-name { font-size: 15px; font-weight: 700; color: var(--c-text-secondary); }

.location-badge {
  display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px;
  background: rgba(255, 255, 255, 0.08); border-radius: 999px;
  font-size: 12px; color: var(--c-text-muted); font-weight: 600;
}

.job-req-row { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 6px; }

.req-chip {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 12px; font-weight: 700; padding: 4px 10px; border-radius: 8px;
  background: rgba(255, 255, 255, 0.04); border: 1px solid var(--c-border-glass);
  color: var(--c-text-muted);
}

.req-chip.industry { background: rgba(56, 189, 248, 0.1); color: var(--c-accent-primary); border-color: rgba(56, 189, 248, 0.2); }

.card-footer {
  margin-top: 4px; padding-top: 14px; border-top: 1px solid rgba(255,255,255,0.05);
  position: relative;
}

.job-snippet {
  color: var(--c-text-faint); font-size: 13px; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden; transition: opacity 0.3s ease;
}

.hover-action {
  position: absolute; inset: 14px 0 0 0; background: transparent;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  font-size: 13px; font-weight: 700; color: var(--c-accent-primary);
  opacity: 0; transform: translateY(5px); transition: all 0.3s ease;
}

.job-card-premium:hover .job-snippet { opacity: 0; }
.job-card-premium:hover .hover-action { opacity: 1; transform: translateY(0); }

/* Pagination */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  padding: 16px 0;
}

.page-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-secondary);
  background: rgba(255,255,255,0.04);
  border: 1px solid var(--c-border-glass);
  transition: all var(--duration-fast);
}
.page-btn:hover:not(:disabled) {
  background: rgba(255,255,255,0.08);
  color: var(--c-text-primary);
}
.page-btn.active {
  background: var(--c-accent-primary);
  border-color: transparent;
  color: #fff;
}
.page-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.page-ellipsis {
  color: var(--c-text-muted);
  padding: 0 4px;
}

.loading-state.small { min-height: 100px; }
.empty-icon { margin-bottom: 16px; opacity: 0.3; }
.empty-state p { font-size: 18px; color: var(--c-text-secondary); margin-bottom: 8px; }

/* Premium Modal Redesign */
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(2, 6, 23, 0.85);
  backdrop-filter: blur(8px);
  z-index: 1100;
  display: flex; align-items: center; justify-content: center;
  padding: 24px;
}

.modal-wrapper {
  width: 100%; max-width: 800px;
  animation: modalScaleUp 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes modalScaleUp {
  from { opacity: 0; transform: scale(0.9) translateY(30px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-content-premium {
  background: var(--c-bg-modal);
  border: 1px solid var(--c-border-strong);
  border-radius: var(--radius-xl);
  box-shadow: 0 20px 56px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  position: relative;
  max-height: 90vh;
  display: flex; flex-direction: column;
}

.modal-close {
  position: absolute; top: 20px; right: 20px;
  width: 40px; height: 40px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: var(--c-text-muted); background: rgba(255, 255, 255, 0.05);
  transition: all 0.2s ease; z-index: 5;
}
.modal-close:hover { background: rgba(255, 255, 255, 0.1); color: var(--c-text-primary); transform: rotate(90deg); }

.modal-header {
  padding: 40px 40px 24px;
  background: linear-gradient(to bottom, var(--c-bg-ambient-1), transparent);
  display: flex; justify-content: space-between; align-items: flex-end; gap: 24px;
  border-bottom: 1px solid var(--c-border-glass);
}

.header-main { flex: 1; min-width: 0; }

.modal-title {
  font-size: 32px; font-weight: 800; margin: 0 0 12px;
  color: var(--c-text-primary); line-height: 1.2;
}

.modal-meta-row {
  display: flex; align-items: center; gap: 10px;
  font-size: 16px; font-weight: 600; color: var(--c-text-secondary);
}

.salary-box {
  text-align: right; background: rgba(255, 255, 255, 0.03);
  padding: 12px 20px; border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.salary-label { display: block; font-size: 12px; color: var(--c-text-faint); margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.1em; }

.modal-salary {
  font-size: 28px; font-weight: 900;
  color: #FFB800; font-family: var(--font-display);
}

.modal-tags { padding: 20px 40px; display: flex; justify-content: space-between; align-items: center; background: rgba(255,255,255,0.01); }

.tag-group { display: flex; gap: 10px; flex-wrap: wrap; }

.detail-tag {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 10px;
  background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.06);
  color: var(--c-text-secondary); font-size: 14px; font-weight: 600;
}

.time-stamp { font-size: 13px; color: var(--c-text-faint); font-weight: 500; }

.modal-body {
  flex: 1; overflow-y: auto; padding: 0 40px 40px; scrollbar-width: thin;
}

.detail-section { margin-top: 40px; }

.section-title { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }

.title-indicator { width: 4px; height: 18px; border-radius: 2px; background: var(--c-accent-primary); }

.section-title h3 { font-size: 20px; font-weight: 800; margin: 0; color: var(--c-text-primary); }

.detail-text {
  font-size: 16px; line-height: 1.9; color: var(--c-text-primary);
  opacity: 0.92; white-space: pre-line; word-break: break-all;
}

.modal-footer {
  padding: 24px 40px; background: var(--c-bg-base);
  display: flex; gap: 16px; align-items: center; border-top: 1px solid var(--c-border-glass);
}

.action-button {
  padding: 12px 24px; border-radius: var(--radius-md); font-size: 15px; font-weight: 700;
  display: flex; align-items: center; gap: 8px; transition: all 0.2s ease; cursor: pointer;
}

.action-button.primary { background: var(--c-accent-primary); color: #000; }
.action-button.primary:hover { background: #56CCF2; transform: translateY(-2px); }

.action-button.outline { background: transparent; border: 1px solid rgba(255,255,255,0.1); color: var(--c-text-primary); }
.action-button.outline:hover { background: rgba(255,255,255,0.05); }

.loading-state-simple { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 0; color: var(--c-text-muted); }

.loader-ring-sm {
  width: 32px; height: 32px; border: 2px solid rgba(255, 255, 255, 0.1);
  border-top-color: var(--c-accent-primary); border-radius: 50%; animation: spin 0.8s linear infinite;
}

/* Modal Fade transition */
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity 0.3s ease; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }

@media (max-width: 1024px) {
  .page-header {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .search-grid {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .toolbar-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .page-header {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: 1fr 1fr;
  }

  .search-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: stretch;
  }

  .toolbar-actions > * {
    flex: 1;
  }

  .jobs-toolbar {
    padding: 18px;
  }

  .modal-header { flex-direction: column; align-items: flex-start; padding: 32px 24px 20px; }
  .modal-title { font-size: 24px; }
  .salary-box { width: 100%; text-align: left; }
  .modal-tags { padding: 16px 24px; flex-direction: column; align-items: flex-start; gap: 12px; }
  .modal-body { padding: 0 24px 32px; }
  .modal-footer { padding: 20px 24px; flex-direction: column; }
  .action-button { width: 100%; justify-content: center; }
}
</style>
