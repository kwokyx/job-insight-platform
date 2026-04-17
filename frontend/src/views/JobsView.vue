<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  Search,
  MapPin,
  Building2,
  SlidersHorizontal,
  X,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  Clock,
  GraduationCap,
  Briefcase,
  ArrowRight
} from 'lucide-vue-next'
import { fetchJobDetail, fetchJobs, fetchSimilarJobs } from '../api'
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
const showFilters = ref(false)
const selectedJob = ref(null)
const isLoadingDetail = ref(false)
const similarJobs = ref([])
const skipRouteWatch = ref(false)

const educationOptions = ['Unlimited', 'College', 'Bachelor', 'Master', 'PhD']
const totalPages = computed(() => Math.ceil(totalJobs.value / pageSize.value) || 1)

function normalizeRouteValue(value) {
  if (value === undefined || value === null || value === '') {
    return ''
  }
  return `${value}`
}

function formatSalary(job) {
  return job.salaryText || 'Negotiable'
}

function formatExcerpt(job) {
  return job.description || job.requirements || 'Open the detail panel to inspect responsibilities and requirements.'
}

function applyRouteQuery(routeQuery) {
  query.value = {
    keyword: normalizeRouteValue(routeQuery.keyword),
    city: normalizeRouteValue(routeQuery.city),
    industry: normalizeRouteValue(routeQuery.industry),
    education: normalizeRouteValue(routeQuery.education),
    experience: normalizeRouteValue(routeQuery.experience),
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
    const [detail, similar] = await Promise.all([
      fetchJobDetail(job.id),
      fetchSimilarJobs(authStore.token, job.id, 6).catch(() => ({}))
    ])
    selectedJob.value = detail
    similarJobs.value = Array.isArray(similar.recommendations) ? similar.recommendations : []
  } catch (error) {
    console.error('Failed to load job detail', error)
  } finally {
    isLoadingDetail.value = false
  }
}

function closeDetail() {
  selectedJob.value = null
  similarJobs.value = []
  skipRouteWatch.value = true
  router.replace({ path: '/jobs', query: buildRouteQuery(currentPage.value) })
}

function resetFilters() {
  query.value = createDefaultQuery()
  loadJobs(1)
}

watch(
  () => route.query,
  async (nextQuery) => {
    if (skipRouteWatch.value) {
      skipRouteWatch.value = false
      return
    }
    applyRouteQuery(nextQuery)
    await loadJobs(currentPage.value, { syncRoute: false })
    const openId = nextQuery.open ? Number(nextQuery.open) : null
    if (openId) {
      await openDetail({ id: openId })
    } else if (selectedJob.value) {
      selectedJob.value = null
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="jobs-layout page-shell">
    <PremiumCard padding="18px 20px" class="search-card">
      <div class="search-bar">
        <div class="input-group main-search">
          <Search class="input-icon" :size="18" />
          <input
            v-model="query.keyword"
            type="text"
            placeholder="Search job title or keyword"
            class="glass-input"
            @keyup.enter="loadJobs(1)"
          />
        </div>
        <div class="input-group">
          <MapPin class="input-icon" :size="18" />
          <input v-model="query.city" type="text" placeholder="City" class="glass-input" @keyup.enter="loadJobs(1)" />
        </div>
        <div class="input-group">
          <Building2 class="input-icon" :size="18" />
          <input v-model="query.industry" type="text" placeholder="Industry" class="glass-input" @keyup.enter="loadJobs(1)" />
        </div>
        <GlowButton variant="primary" @click="loadJobs(1)">Search</GlowButton>
        <GlowButton variant="ghost" @click="showFilters = !showFilters">
          <SlidersHorizontal :size="18" />
        </GlowButton>
      </div>

      <transition name="slide-fade">
        <div v-if="showFilters" class="advanced-filters">
          <div class="filter-row">
            <div class="filter-group">
              <label>Education</label>
              <div class="filter-chips">
                <button
                  v-for="edu in educationOptions"
                  :key="edu"
                  class="filter-chip"
                  :class="{ active: query.education === (edu === 'Unlimited' ? '' : edu) }"
                  @click="query.education = edu === 'Unlimited' ? '' : edu"
                >
                  {{ edu }}
                </button>
              </div>
            </div>

            <div class="filter-group">
              <label>Salary Range</label>
              <div class="salary-range">
                <input v-model.number="query.salaryMin" type="number" placeholder="Min" class="glass-input-sm" />
                <span class="range-sep">-</span>
                <input v-model.number="query.salaryMax" type="number" placeholder="Max" class="glass-input-sm" />
              </div>
            </div>
          </div>

          <div class="filter-actions">
            <GlowButton variant="primary" @click="loadJobs(1)">Apply</GlowButton>
            <GlowButton variant="ghost" @click="resetFilters">Reset</GlowButton>
          </div>
        </div>
      </transition>
    </PremiumCard>

    <div class="results-meta">
      <span>Total <strong>{{ totalJobs.toLocaleString() }}</strong> jobs</span>
      <span class="page-info">Page {{ currentPage }} / {{ totalPages }}</span>
    </div>

    <div v-if="isLoading" class="loading-state">
      <div class="loader-ring"></div>
    </div>

    <div v-else-if="jobs.length === 0" class="empty-state glass-panel">
      <Search :size="44" class="empty-icon" />
      <p>No matching jobs found.</p>
      <span>Adjust the keyword, city, or filter range and try again.</span>
    </div>

    <TransitionGroup v-else name="list" tag="div" class="jobs-grid">
      <div v-for="job in jobs" :key="job.id" class="job-card-premium glass-panel" @click="openDetail(job)">
        <div class="card-glow"></div>
        <div class="card-content">
          <div class="job-header">
            <h3 class="job-title">{{ job.title }}</h3>
            <span class="job-salary">{{ formatSalary(job) }}</span>
          </div>

          <div class="company-row">
            <span class="company-name">{{ job.companyName }}</span>
            <div class="location-badge">
              <MapPin :size="12" />
              <span>{{ job.city || 'Nationwide' }}</span>
            </div>
          </div>

          <div class="job-req-row">
            <span v-if="job.experience" class="req-chip"><Clock :size="12" /> {{ job.experience }}</span>
            <span v-if="job.education" class="req-chip"><GraduationCap :size="12" /> {{ job.education }}</span>
            <span v-if="job.industryName" class="req-chip industry">{{ job.industryName }}</span>
          </div>

          <div class="card-footer">
            <p class="job-snippet">{{ formatExcerpt(job) }}</p>
            <div class="hover-action">Open job detail <ArrowRight :size="14" /></div>
          </div>
        </div>
      </div>
    </TransitionGroup>

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
        >
          {{ p }}
        </button>
        <span v-else-if="p === currentPage - 3 || p === currentPage + 3" class="page-ellipsis">...</span>
      </template>
      <button class="page-btn" :disabled="currentPage >= totalPages" @click="loadJobs(currentPage + 1)">
        <ChevronRight :size="18" />
      </button>
    </div>

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
                    <span class="location">{{ selectedJob.city || 'Nationwide' }}</span>
                  </div>
                </div>
                <div class="salary-box">
                  <span class="salary-label">Salary</span>
                  <span class="modal-salary">{{ formatSalary(selectedJob) }}</span>
                </div>
              </div>

              <div class="modal-tags">
                <div class="tag-group">
                  <span v-if="selectedJob.education" class="detail-tag"><GraduationCap :size="14" /> {{ selectedJob.education }}</span>
                  <span v-if="selectedJob.experience" class="detail-tag"><Clock :size="14" /> {{ selectedJob.experience }}</span>
                  <span v-if="selectedJob.industryName" class="detail-tag"><Building2 :size="14" /> {{ selectedJob.industryName }}</span>
                  <span v-if="selectedJob.employmentType" class="detail-tag"><Briefcase :size="14" /> {{ selectedJob.employmentType }}</span>
                </div>
                <div v-if="selectedJob.publishDate" class="time-stamp">Published {{ selectedJob.publishDate }}</div>
              </div>

              <div class="modal-body">
                <div v-if="isLoadingDetail" class="loading-state-simple">
                  <div class="loader-ring-sm"></div>
                  <span>Loading job detail...</span>
                </div>
                <template v-else>
                  <div v-if="selectedJob.description" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>Description</h3>
                    </div>
                    <div class="detail-text" v-html="(selectedJob.description || '').replace(/\n/g, '<br/>')"></div>
                  </div>
                  <div v-if="selectedJob.requirements" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>Requirements</h3>
                    </div>
                    <div class="detail-text" v-html="(selectedJob.requirements || '').replace(/\n/g, '<br/>')"></div>
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
                <a v-if="selectedJob.sourceUrl" :href="selectedJob.sourceUrl" target="_blank" rel="noopener noreferrer" class="action-button primary">
                  <ExternalLink :size="16" /> Open source page
                </a>
                <button class="action-button outline" @click="closeDetail">Close</button>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
.jobs-layout { display: flex; flex-direction: column; gap: 20px; }
.search-card { box-shadow: var(--shadow-glass); }
.search-bar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.input-group { flex: 1 1 180px; position: relative; display: flex; align-items: center; }
.main-search { flex: 2 1 320px; }
.input-icon { position: absolute; left: 14px; color: var(--c-text-muted); pointer-events: none; }
.glass-input {
  width: 100%; padding: 12px 14px 12px 40px; background: rgba(255,255,255,.045);
  border: 1px solid var(--c-border-glass); border-radius: var(--radius-md); color: var(--c-text-primary);
}
.glass-input-sm {
  width: 110px; padding: 8px 12px; background: rgba(255,255,255,.03);
  border: 1px solid var(--c-border-glass); border-radius: var(--radius-sm); color: var(--c-text-primary);
}
.advanced-filters { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--c-border-glass); }
.filter-row { display: flex; gap: 24px; flex-wrap: wrap; }
.filter-group { flex: 1; min-width: 200px; }
.filter-group label { display: block; font-size: 13px; color: var(--c-text-muted); margin-bottom: 8px; }
.filter-chips { display: flex; gap: 8px; flex-wrap: wrap; }
.filter-chip {
  padding: 6px 14px; border-radius: 999px; font-size: 13px; background: rgba(255,255,255,.04);
  border: 1px solid var(--c-border-glass); color: var(--c-text-secondary);
}
.filter-chip.active { background: rgba(59,130,246,.15); border-color: rgba(59,130,246,.4); color: var(--c-accent-primary); }
.salary-range { display: flex; align-items: center; gap: 8px; }
.range-sep { color: var(--c-text-muted); }
.filter-actions { display: flex; gap: 12px; margin-top: 16px; }
.slide-fade-enter-active, .slide-fade-leave-active { transition: all .25s ease; }
.slide-fade-enter-from, .slide-fade-leave-to { opacity: 0; transform: translateY(-8px); }
.results-meta { display: flex; justify-content: space-between; align-items: center; color: var(--c-text-muted); font-size: 14px; padding: 0 4px; }
.results-meta strong { color: var(--c-text-primary); }
.jobs-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(350px, 1fr)); gap: 20px; }
.job-card-premium {
  position: relative; padding: 1px; border-radius: var(--radius-lg); cursor: pointer;
  transition: all var(--duration-normal) var(--ease-spring); overflow: hidden; background: rgba(255,255,255,.03);
}
.job-card-premium:hover { transform: translateY(-5px) scale(1.01); box-shadow: 0 12px 40px -10px rgba(2, 8, 23, 0.4); }
.card-glow {
  position: absolute; width: 140px; height: 140px; top: -70px; right: -70px;
  background: radial-gradient(circle, var(--c-accent-primary-glow), transparent 70%); opacity: 0; transition: opacity .5s ease;
}
.job-card-premium:hover .card-glow { opacity: .15; }
.card-content { position: relative; z-index: 1; padding: 22px; display: flex; flex-direction: column; gap: 14px; }
.job-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.job-title { font-size: 22px; font-weight: 800; margin: 0; line-height: 1.25; color: var(--c-text-primary); }
.job-salary {
  font-size: 20px; font-weight: 900; background: linear-gradient(135deg, #ff6b6b, #ffb800);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; white-space: nowrap;
}
.company-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.company-name { font-size: 15px; font-weight: 700; color: var(--c-text-secondary); }
.location-badge {
  display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; background: rgba(255,255,255,.08);
  border-radius: 999px; font-size: 12px; color: var(--c-text-muted); font-weight: 600;
}
.job-req-row { display: flex; gap: 10px; flex-wrap: wrap; }
.req-chip {
  display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 700; padding: 4px 10px;
  border-radius: 8px; background: rgba(255,255,255,.04); border: 1px solid var(--c-border-glass); color: var(--c-text-muted);
}
.req-chip.industry { background: rgba(56, 189, 248, 0.1); color: var(--c-accent-primary); border-color: rgba(56,189,248,.2); }
.card-footer { margin-top: 4px; padding-top: 14px; border-top: 1px solid rgba(255,255,255,.05); position: relative; }
.job-snippet {
  color: var(--c-text-faint); font-size: 13px; line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.hover-action {
  position: absolute; inset: 14px 0 0 0; display: flex; align-items: center; justify-content: center; gap: 8px;
  color: var(--c-accent-primary); font-size: 13px; font-weight: 700; opacity: 0; transform: translateY(5px); transition: all .3s ease;
}
.job-card-premium:hover .job-snippet { opacity: 0; }
.job-card-premium:hover .hover-action { opacity: 1; transform: translateY(0); }
.pagination { display: flex; justify-content: center; align-items: center; gap: 6px; padding: 16px 0; }
.page-btn {
  width: 36px; height: 36px; border-radius: var(--radius-sm); display: flex; align-items: center; justify-content: center;
  color: var(--c-text-secondary); background: rgba(255,255,255,.04); border: 1px solid var(--c-border-glass);
}
.page-btn.active { background: var(--c-accent-primary); border-color: transparent; color: #fff; }
.page-btn:disabled { opacity: .3; cursor: not-allowed; }
.page-ellipsis { color: var(--c-text-muted); padding: 0 4px; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 52px 24px; text-align: center; color: var(--c-text-muted); }
.empty-icon { opacity: .32; }
.loading-state { display: flex; justify-content: center; padding: 40px 0; }
.loader-ring, .loader-ring-sm {
  border: 2px solid rgba(255,255,255,.12); border-top-color: var(--c-accent-primary); border-radius: 50%; animation: spin .8s linear infinite;
}
.loader-ring { width: 42px; height: 42px; }
.loader-ring-sm { width: 32px; height: 32px; }
@keyframes spin { to { transform: rotate(360deg); } }
.modal-overlay {
  position: fixed; inset: 0; background: rgba(2,6,23,.85); backdrop-filter: blur(8px); z-index: 1100;
  display: flex; align-items: center; justify-content: center; padding: 24px;
}
.modal-wrapper { width: 100%; max-width: 800px; }
.modal-content-premium {
  background: var(--c-bg-modal); border: 1px solid var(--c-border-strong); border-radius: var(--radius-xl);
  box-shadow: var(--shadow-glass); overflow: hidden; position: relative; max-height: 90vh; display: flex; flex-direction: column;
}
.modal-close {
  position: absolute; top: 20px; right: 20px; width: 40px; height: 40px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; color: var(--c-text-muted); background: rgba(255,255,255,.05); z-index: 5;
}
.modal-header {
  padding: 40px 40px 24px; background: linear-gradient(to bottom, var(--c-bg-ambient-1), transparent);
  display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; border-bottom: 1px solid var(--c-border-glass);
}
.modal-title { font-size: 32px; font-weight: 800; margin: 0 0 12px; color: var(--c-text-primary); line-height: 1.2; }
.modal-meta-row { display: flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 600; color: var(--c-text-secondary); }
.salary-box { text-align: right; background: rgba(255,255,255,.03); padding: 12px 20px; border-radius: var(--radius-lg); }
.salary-label { display: block; font-size: 12px; color: var(--c-text-faint); margin-bottom: 4px; text-transform: uppercase; letter-spacing: .1em; }
.modal-salary { font-size: 28px; font-weight: 900; color: #ffb800; }
.modal-tags { padding: 20px 40px; display: flex; justify-content: space-between; align-items: center; background: rgba(255,255,255,.01); }
.tag-group { display: flex; gap: 10px; flex-wrap: wrap; }
.detail-tag {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 10px; background: rgba(255,255,255,.03);
  border: 1px solid rgba(255,255,255,.06); color: var(--c-text-secondary); font-size: 14px; font-weight: 600;
}
.time-stamp { font-size: 13px; color: var(--c-text-faint); font-weight: 500; }
.modal-body { flex: 1; overflow-y: auto; padding: 0 40px 40px; }
.loading-state-simple { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 0; color: var(--c-text-muted); }
.detail-section { margin-top: 40px; }
.similar-list { display: grid; gap: 12px; }
.similar-item {
  display: flex; justify-content: space-between; align-items: center; gap: 14px; text-align: left;
  padding: 14px 16px; border-radius: 16px; background: rgba(255,255,255,.035);
  border: 1px solid var(--c-border-glass); color: var(--c-text-primary);
}
.similar-item p { margin: 6px 0 0; color: var(--c-text-muted); font-size: 13px; }
.similar-item span { color: var(--c-accent-primary); font-weight: 700; }
.section-title { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.title-indicator { width: 4px; height: 18px; border-radius: 2px; background: var(--c-accent-primary); }
.section-title h3 { font-size: 20px; font-weight: 800; margin: 0; color: var(--c-text-primary); }
.detail-text { font-size: 16px; line-height: 1.9; color: var(--c-text-primary); opacity: .92; white-space: pre-line; word-break: break-all; }
.modal-footer { padding: 24px 40px; background: var(--c-bg-base); display: flex; gap: 16px; align-items: center; border-top: 1px solid var(--c-border-glass); }
.action-button { padding: 12px 24px; border-radius: var(--radius-md); font-size: 15px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.action-button.primary { background: var(--c-accent-primary); color: #000; }
.action-button.outline { background: transparent; border: 1px solid rgba(255,255,255,.1); color: var(--c-text-primary); }
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity .3s ease; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
@media (max-width: 768px) {
  .search-bar, .filter-row, .results-meta, .modal-header, .modal-tags, .modal-footer { flex-direction: column; align-items: stretch; }
  .jobs-grid { grid-template-columns: 1fr; }
  .modal-body { padding: 0 24px 32px; }
  .modal-header, .modal-tags, .modal-footer { padding-left: 24px; padding-right: 24px; }
}
</style>
