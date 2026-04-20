<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  createTeacherCourse,
  deleteTeacherCourse,
  fetchCurriculums,
  fetchTeacherCourses,
  fetchTeacherMarketMatch,
  uploadCurriculumExcel
} from '../api'
import {
  BookOpen,
  Files,
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

onMounted(loadData)
</script>

<template>
  <div class="teacher-view page-animate">
    <header class="workspace-page-head">
      <h1 class="workspace-page-title">课程与供需</h1>
    </header>

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

    <div v-if="loading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载教师工作台...</p>
    </div>

    <template v-else>
      <section class="teacher-grid top-grid">
        <article class="panel">
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

        <article class="panel">
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
      </section>

      <section class="teacher-grid mid-grid">
        <article class="panel">
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

        <article class="panel">
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
      </section>

      <section class="teacher-grid content-grid">
        <article class="panel">
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

        <article class="panel">
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
      </section>
    </template>
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

/* ---------------- Grid ---------------- */
.teacher-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
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

.loader-ring {
  width: 28px;
  height: 28px;
  border: 2px solid var(--c-accent-primary-glow);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---------------- Responsive ---------------- */
@media (max-width: 1024px) {
  .teacher-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .workspace-metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .workspace-metric-strip {
    grid-template-columns: 1fr;
  }
}
</style>
