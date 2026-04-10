<script setup>
import { ref, onMounted, computed } from 'vue'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { Search, MapPin, Building2, SlidersHorizontal, X, ChevronLeft, ChevronRight, ExternalLink, Clock, GraduationCap, Briefcase } from 'lucide-vue-next'
import { fetchJobs, fetchJobDetail } from '../api'

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

const closeDetail = () => {
  selectedJob.value = null
}

const resetFilters = () => {
  query.value = { keyword: '', city: '', industry: '', education: '', experience: '', salaryMin: null, salaryMax: null }
  loadJobs(1)
}

const educationOptions = ['不限', '大专', '本科', '硕士', '博士']

onMounted(() => {
  loadJobs()
})
</script>

<template>
  <div class="jobs-layout">
    <!-- 搜索栏 -->
    <PremiumCard padding="16px 20px" class="search-card">
      <div class="search-bar">
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
        <GlowButton variant="primary" @click="loadJobs(1)">搜索</GlowButton>
        <GlowButton variant="ghost" @click="showFilters = !showFilters">
          <SlidersHorizontal :size="18" />
        </GlowButton>
      </div>

      <!-- 高级筛选 -->
      <transition name="slide-fade">
        <div v-if="showFilters" class="advanced-filters">
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
    </PremiumCard>

    <!-- 结果统计 -->
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
    <div v-else class="jobs-grid">
      <div v-for="job in jobs" :key="job.id" class="job-card glass-panel" @click="openDetail(job)">
        <div class="job-header">
          <h3 class="job-title text-gradient text-gradient-primary">{{ job.title }}</h3>
          <span class="job-salary">{{ job.salaryText || '面议' }}</span>
        </div>
        
        <div class="job-meta">
          <span>{{ job.companyName }}</span>
          <span class="dot">·</span>
          <span>{{ job.city || '全国' }}</span>
        </div>
        
        <div class="job-reqs">
          <span class="req-tag" v-if="job.experience">
            <Clock :size="12" /> {{ job.experience }}
          </span>
          <span class="req-tag" v-if="job.education">
            <GraduationCap :size="12" /> {{ job.education }}
          </span>
          <span class="req-tag" v-if="job.industryName">
            <Briefcase :size="12" /> {{ job.industryName }}
          </span>
        </div>
      </div>
    </div>

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
      <transition name="fade">
        <div v-if="selectedJob" class="modal-overlay" @click.self="closeDetail">
          <div class="modal-content glass-panel">
            <button class="modal-close" @click="closeDetail">
              <X :size="20" />
            </button>
            
            <div class="modal-header">
              <h2 class="modal-title">{{ selectedJob.title }}</h2>
              <span class="modal-salary">{{ selectedJob.salaryText || '面议' }}</span>
            </div>

            <div class="modal-meta">
              <span v-if="selectedJob.companyName">{{ selectedJob.companyName }}</span>
              <span class="dot" v-if="selectedJob.city">·</span>
              <span v-if="selectedJob.city">{{ selectedJob.city }}</span>
              <span class="dot" v-if="selectedJob.publishDate">·</span>
              <span v-if="selectedJob.publishDate">{{ selectedJob.publishDate }}</span>
            </div>

            <div class="modal-tags">
              <span class="detail-tag" v-if="selectedJob.education"><GraduationCap :size="13" /> {{ selectedJob.education }}</span>
              <span class="detail-tag" v-if="selectedJob.experience"><Clock :size="13" /> {{ selectedJob.experience }}</span>
              <span class="detail-tag" v-if="selectedJob.industryName"><Building2 :size="13" /> {{ selectedJob.industryName }}</span>
              <span class="detail-tag" v-if="selectedJob.employmentType"><Briefcase :size="13" /> {{ selectedJob.employmentType }}</span>
            </div>

            <div class="modal-body">
              <div v-if="isLoadingDetail" class="loading-state small"><div class="loader-ring"></div></div>
              <template v-else>
                <div v-if="selectedJob.description" class="detail-section">
                  <h3>职位描述</h3>
                  <div class="detail-text" v-html="(selectedJob.description || '').replace(/\n/g, '<br/>')"></div>
                </div>
                <div v-if="selectedJob.requirements" class="detail-section">
                  <h3>任职要求</h3>
                  <div class="detail-text" v-html="(selectedJob.requirements || '').replace(/\n/g, '<br/>')"></div>
                </div>
              </template>
            </div>

            <div class="modal-footer" v-if="selectedJob.sourceUrl">
              <a :href="selectedJob.sourceUrl" target="_blank" rel="noopener noreferrer" class="source-link">
                <ExternalLink :size="14" /> 查看原始链接
              </a>
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
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--c-border-glass);
  border-radius: var(--radius-md);
  color: var(--c-text-primary);
  font-size: 14px;
  transition: all var(--duration-fast) var(--ease-out);
}

.glass-input:focus {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(30, 117, 255, 0.5);
  box-shadow: 0 0 0 3px rgba(30, 117, 255, 0.1);
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

/* Jobs Grid */
.jobs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.job-card {
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;
  transition: transform var(--duration-normal) var(--ease-spring), box-shadow var(--duration-normal);
}
.job-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 40px -10px rgba(59, 130, 246, 0.2);
}

.job-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.job-title { font-size: 18px; margin: 0; line-height: 1.3; }

.job-salary {
  font-size: 16px;
  font-weight: 700;
  color: var(--c-accent-secondary);
  font-family: var(--font-display);
  white-space: nowrap;
}

.job-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--c-text-secondary);
  font-size: 14px;
}
.dot { opacity: 0.5; }

.job-reqs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.req-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.05);
  color: var(--c-text-secondary);
}

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

/* Loading & Empty */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
.loading-state.small { min-height: 100px; }

.loader-ring {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(255,255,255,0.08);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  text-align: center;
  padding: 60px 24px;
  color: var(--c-text-muted);
}
.empty-icon { margin-bottom: 16px; opacity: 0.3; }
.empty-state p { font-size: 18px; color: var(--c-text-secondary); margin-bottom: 8px; }

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal-content {
  width: 100%;
  max-width: 720px;
  max-height: 85vh;
  overflow-y: auto;
  padding: 32px;
  position: relative;
  animation: modalIn 0.3s var(--ease-out);
}

@keyframes modalIn {
  from { opacity: 0; transform: scale(0.95) translateY(20px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
  background: rgba(255,255,255,0.05);
  transition: all var(--duration-fast);
}
.modal-close:hover {
  background: rgba(255,255,255,0.1);
  color: var(--c-text-primary);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 12px;
}

.modal-title {
  font-size: 24px;
  margin: 0;
  background: linear-gradient(135deg, var(--c-accent-primary), #00E5FF);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.modal-salary {
  font-size: 22px;
  font-weight: 800;
  color: var(--c-accent-secondary);
  font-family: var(--font-display);
  white-space: nowrap;
}

.modal-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--c-text-secondary);
  font-size: 15px;
  margin-bottom: 16px;
}

.modal-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.detail-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  background: rgba(255,255,255,0.05);
  color: var(--c-text-secondary);
  font-size: 13px;
}

.modal-body {
  border-top: 1px solid var(--c-border-glass);
  padding-top: 20px;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h3 {
  font-size: 16px;
  margin-bottom: 12px;
  color: var(--c-text-primary);
}

.detail-text {
  font-size: 14px;
  line-height: 1.8;
  color: var(--c-text-secondary);
  word-break: break-word;
}

.modal-footer {
  border-top: 1px solid var(--c-border-glass);
  padding-top: 16px;
  margin-top: 8px;
}

.source-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--c-accent-primary);
}
.source-link:hover { text-decoration: underline; }

/* Fade transition for modal */
.fade-enter-active { transition: opacity 0.3s ease; }
.fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 768px) {
  .search-bar { flex-direction: column; align-items: stretch; }
  .input-group { flex: 1 1 100%; }
  .main-search { flex: 1 1 100%; }
  .jobs-grid { grid-template-columns: 1fr; }
  .job-title { font-size: 16px; }
  .modal-content { padding: 20px; max-height: 90vh; }
  .modal-title { font-size: 20px; }
  .filter-row { flex-direction: column; gap: 16px; }
}
</style>
