<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
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
  BriefcaseBusiness,
  FileSpreadsheet,
  Files,
  GraduationCap,
  Lightbulb,
  Plus,
  Sparkles,
  Trash2,
  TrendingUp,
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
  { label: '自建课程', value: courses.value.length, hint: '教师手工维护的课程资产' },
  { label: '课程库条数', value: curriculums.value.length, hint: '已进入教学课程库的数据' },
  { label: '技能覆盖率', value: matchResult.value?.coverageRate || '--', hint: '课程内容对市场热门技能的覆盖情况' },
  { label: '市场缺口', value: matchResult.value?.marketGaps?.length || 0, hint: '当前教学侧仍未覆盖的关键技能' }
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
  <div class="teacher-view page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">教师视角</span>
        <h1 class="page-intro-title">课程管理与供需分析工作台</h1>
        <p class="page-intro-text">
          这里不只展示课程和技能列表，还把课程供给、市场需求和教学风险转成可判断的结论，帮助教师做课程结构调整。
        </p>
      </div>
      <div class="page-intro-meta">
        <div v-for="card in overviewCards" :key="card.label" class="intro-metric">
          <span class="intro-metric-label">{{ card.label }}</span>
          <span class="intro-metric-value">{{ card.value }}</span>
        </div>
      </div>
    </section>

    <div v-if="loading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载教师工作台...</p>
    </div>

    <template v-else>
      <section class="teacher-grid top-grid">
        <PremiumCard title="教学诊断" glowColor="primary">
          <div class="insight-list">
            <div v-for="item in teacherInsights" :key="item.title" class="insight-card">
              <div class="insight-head">
                <strong>{{ item.title }}</strong>
                <span class="pill">{{ item.summary }}</span>
              </div>
              <p>{{ item.detail }}</p>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="教师动作建议" glowColor="teal">
          <div class="feature-list">
            <button class="feature-card" @click="router.push('/reports')">
              <Sparkles :size="20" />
              <div>
                <strong>教学建议报告</strong>
                <p>把市场缺口、课程覆盖和学生求职成果整合成教师/院校报告。</p>
              </div>
            </button>
            <button class="feature-card" @click="router.push('/insights')">
              <TrendingUp :size="20" />
              <div>
                <strong>行业趋势映射</strong>
                <p>优先观察技能热度变化，避免课程体系长期停留在历史需求上。</p>
              </div>
            </button>
            <button class="feature-card" @click="router.push('/recommend')">
              <Lightbulb :size="20" />
              <div>
                <strong>学生成果回看</strong>
                <p>从学生侧推荐与报告结果倒推，判断课程训练是否真正转成求职竞争力。</p>
              </div>
            </button>
          </div>
        </PremiumCard>
      </section>

      <section class="teacher-grid mid-grid">
        <PremiumCard title="Excel 批量导入课程" glowColor="secondary">
          <div class="upload-panel">
            <p class="panel-muted">上传课程 Excel 后，系统会自动沉淀课程库并重建技能映射，方便后续供需分析。</p>
            <label class="upload-box">
              <input type="file" accept=".xlsx,.xls" class="hidden-input" @change="handlePickExcel" />
              <Files :size="22" />
              <div>
                <strong>{{ selectedExcelName || '选择课程 Excel 文件' }}</strong>
                <p>适合一次性导入课程名称、专业、学期、学时和关键词。</p>
              </div>
            </label>
            <div class="button-row">
              <GlowButton variant="primary" :loading="uploadLoading" @click="handleUploadExcel">
                <Upload :size="14" />
                上传并导入
              </GlowButton>
              <GlowButton variant="ghost" @click="router.push('/profile')">
                <FileSpreadsheet :size="14" />
                查看账号资料
              </GlowButton>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="新增课程" glowColor="secondary">
          <form class="form-stack" @submit.prevent="handleCreateCourse">
            <div class="form-grid">
              <input v-model="courseForm.courseName" class="glass-input" placeholder="课程名称" required />
              <input v-model="courseForm.major" class="glass-input" placeholder="所属专业" />
              <input v-model="courseForm.semester" class="glass-input" placeholder="开课学期" />
              <input v-model="courseForm.creditHours" type="number" class="glass-input" placeholder="学时" />
            </div>
            <input v-model="courseForm.coreSkills" class="glass-input" placeholder="核心技能，多个技能请用英文逗号分隔" required />
            <textarea v-model="courseForm.description" class="glass-textarea" rows="4" placeholder="课程简介、教学目标或大纲关键词" />
            <GlowButton variant="primary" type="submit" :loading="saving">
              <Plus :size="14" />
              添加课程
            </GlowButton>
          </form>
        </PremiumCard>
      </section>

      <section class="teacher-grid content-grid">
        <PremiumCard title="供需分析重点" glowColor="purple">
          <div class="analysis-block">
            <div class="rate-card">
              <span class="rate-label">覆盖率</span>
              <strong>{{ matchResult?.coverageRate || '--' }}</strong>
              <p>这是课程内容与市场热门技能的贴合程度，不是学生就业结果本身。</p>
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
        </PremiumCard>

        <PremiumCard title="课程资产与课程库" glowColor="purple">
          <div v-if="courses.length" class="course-list">
            <div v-for="course in courses" :key="course.id" class="course-item">
              <div class="course-main">
                <div class="course-head">
                  <strong>{{ course.courseName }}</strong>
                  <span class="course-chip">{{ course.major || '未填写专业' }}</span>
                </div>
                <p>{{ course.description || '暂无课程说明' }}</p>
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
            <BookOpen :size="28" />
            <p>当前还没有教师自建课程。可以先手动新增，也可以直接批量上传 Excel。</p>
          </div>

          <div v-if="curriculums.length" class="curriculum-list">
            <div v-for="item in curriculums" :key="item.id" class="curriculum-item">
              <div class="curriculum-head">
                <strong>{{ item.courseName }}</strong>
                <span class="course-chip">{{ item.major || '未填写专业' }}</span>
              </div>
              <p>{{ item.description || '暂无课程描述' }}</p>
              <div class="course-meta">
                <span>{{ item.department || '院系未填写' }}</span>
                <span>{{ item.semester || '学期未填写' }}</span>
              </div>
            </div>
          </div>
        </PremiumCard>
      </section>
    </template>
  </div>
</template>

<style scoped>
.teacher-grid,
.feature-list,
.insight-list,
.analysis-block,
.form-stack,
.course-list,
.curriculum-list,
.upload-panel {
  display: grid;
  gap: 24px;
}

.top-grid,
.mid-grid,
.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.insight-card,
.feature-card,
.upload-box,
.course-item,
.curriculum-item,
.gap-item {
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.insight-card,
.curriculum-item {
  padding: 18px;
}

.insight-head,
.course-head,
.curriculum-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.insight-head strong,
.feature-card strong,
.course-head strong,
.curriculum-head strong {
  color: var(--c-text-primary);
}

.insight-card p,
.feature-card p,
.course-main p,
.curriculum-item p,
.rate-card p,
.panel-muted {
  color: var(--c-text-secondary);
}

.pill,
.course-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(56, 189, 248, 0.12);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
}

.feature-card,
.upload-box {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 14px;
  padding: 18px;
  color: inherit;
  text-align: left;
}

.hidden-input {
  display: none;
}

.upload-box {
  cursor: pointer;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.glass-input,
.glass-textarea {
  width: 100%;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
  color: var(--c-text-primary);
}

.glass-textarea {
  resize: vertical;
}

.button-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.rate-card {
  padding: 20px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.18), rgba(168, 85, 247, 0.12));
  border: 1px solid rgba(56, 189, 248, 0.25);
}

.rate-label {
  display: block;
  font-size: 12px;
  color: var(--c-text-muted);
}

.rate-card strong {
  display: block;
  margin: 8px 0;
  font-size: 34px;
  line-height: 1;
}

.analysis-section {
  display: grid;
  gap: 12px;
}

.gap-list,
.skills-list,
.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.gap-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
}

.gap-item strong {
  color: var(--c-accent-secondary);
}

.suggestions {
  display: grid;
  gap: 10px;
  padding-left: 18px;
  list-style: disc;
}

.course-item {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 18px;
}

.course-main {
  display: grid;
  gap: 10px;
  flex: 1;
}

.course-meta {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  color: var(--c-text-muted);
  font-size: 13px;
}

.skill-tag {
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  background: var(--c-bg-surface-hover);
}

.skill-tag.covered {
  color: var(--c-accent-teal);
  background: rgba(20, 184, 166, 0.12);
}

.icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
  border: 1px solid var(--c-border-glass);
}

.icon-btn.delete:hover {
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.32);
}

@media (max-width: 1024px) {
  .top-grid,
  .mid-grid,
  .content-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
