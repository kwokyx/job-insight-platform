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
  fetchTeacherTeachingReform,
  updateTeacherCourse,
  uploadCurriculumExcel
} from '../api'
import {
  BookOpen,
  BriefcaseBusiness,
  FileSpreadsheet,
  Files,
  GraduationCap,
  Lightbulb,
  Pencil,
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
const teachingReform = ref(null)
const selectedExcel = ref(null)
const selectedExcelName = ref('')
const editingCourseId = ref(null)
const selectedMajor = ref('')

const courseForm = ref(createEmptyCourseForm())

function createEmptyCourseForm() {
  return {
    courseName: '',
    coreSkills: '',
    creditHours: '',
    semester: '',
    major: '',
    description: ''
  }
}

const overviewCards = computed(() => [
  { label: '自建课程', value: courses.value.length, hint: '教师手工维护的课程资产' },
  { label: '课程库条数', value: curriculums.value.length, hint: '已同步进入平台课程库的数据' },
  { label: '技能覆盖率', value: matchResult.value?.coverageRate || '--', hint: '课程内容对市场热门技能的覆盖程度' },
  {
    label: '教改总分',
    value: teachingReform.value?.governanceScorecard?.overallScore ?? '--',
    hint: '依据课程资产、能力覆盖和证据化准备度综合生成'
  }
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
        ? '课程结构已经接近市场主流需求，接下来更值得强化项目产出与学生成果表达。'
        : coverage >= 40
          ? '课程体系可用但不充分，应该先补齐高频技能缺口，再优化课程深度。'
          : '课程供给与市场需求存在明显偏差，当前优先级是重排核心技能供给。'
    },
    {
      title: '缺口优先级',
      summary: `待补技能 ${gapCount} 项`,
      detail: gapCount > 8
        ? '缺口较多，建议先围绕高需求岗位簇建立分层课程，再逐步做专题强化。'
        : '缺口数量可控，可以把重点技能做成模块化专题和结课项目。'
    },
    {
      title: '课程老化风险',
      summary: `疑似过时 ${outdatedCount} 项`,
      detail: outdatedCount > 3
        ? '需要评估旧技能是否仍占据过多学时，避免挤压对新能力的投入。'
        : '现有课程内容整体较新，问题更可能在成果转化而不是课程陈旧。'
    }
  ]
})

const topGapSkills = computed(() => (matchResult.value?.marketGaps || []).slice(0, 6))
const topCoveredSkills = computed(() => (matchResult.value?.coveredSkills || []).slice(0, 8))
const scoreCards = computed(() => {
  const scorecard = teachingReform.value?.governanceScorecard || {}
  return [
    { label: '课程资产', value: scorecard.courseAssetScore ?? '--' },
    { label: '能力覆盖', value: scorecard.capabilityCoverageScore ?? '--' },
    { label: '市场对齐', value: scorecard.marketAlignmentScore ?? '--' },
    { label: '证据化准备', value: scorecard.evidenceReadinessScore ?? '--' }
  ]
})
const reformActions = computed(() => teachingReform.value?.blueprint?.reformActions || [])
const graduationRequirements = computed(() => teachingReform.value?.blueprint?.graduationRequirements || [])
const curriculumModules = computed(() => teachingReform.value?.blueprint?.curriculumModules || [])
const assessmentSuggestions = computed(() => teachingReform.value?.blueprint?.assessmentSuggestions || [])
const capabilityDimensions = computed(() => teachingReform.value?.blueprint?.capabilityDimensions || [])
const jobFamilies = computed(() => teachingReform.value?.blueprint?.jobFamilies || [])
const submitLabel = computed(() => (editingCourseId.value ? '保存课程修改' : '添加课程'))

const majorOptions = computed(() => {
  const values = new Set()
  for (const item of courses.value) {
    if (item?.major) values.add(String(item.major).trim())
  }
  for (const item of curriculums.value) {
    if (item?.major) values.add(String(item.major).trim())
  }
  if (teachingReform.value?.major) {
    values.add(String(teachingReform.value.major).trim())
  }
  return [...values].filter(Boolean)
})
const filteredCourses = computed(() => {
  if (!selectedMajor.value) return courses.value
  return courses.value.filter((item) => String(item.major || '').trim() === selectedMajor.value)
})
const filteredCurriculums = computed(() => {
  if (!selectedMajor.value) return curriculums.value
  return curriculums.value.filter((item) => String(item.major || '').trim() === selectedMajor.value)
})
const filteredCurriculumModules = computed(() => {
  if (!selectedMajor.value) return curriculumModules.value
  return curriculumModules.value.filter((item) => String(item.major || '').trim() === selectedMajor.value)
})
const majorBrief = computed(() => ({
  major: selectedMajor.value || teachingReform.value?.major || '当前专业',
  courses: filteredCourses.value.length,
  modules: filteredCurriculumModules.value.length,
  requirements: graduationRequirements.value.length
}))

async function loadData() {
  if (![1, 2].includes(authStore.user?.roleType)) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    const [courseResult, matchResultValue, curriculumResult, reformResult] = await Promise.allSettled([
      fetchTeacherCourses(authStore.token),
      fetchTeacherMarketMatch(authStore.token),
      fetchCurriculums(authStore.token, { page: 1, pageSize: 6 }),
      fetchTeacherTeachingReform(authStore.token, selectedMajor.value || undefined)
    ])

    courses.value = courseResult.status === 'fulfilled' ? courseResult.value : []
    matchResult.value = matchResultValue.status === 'fulfilled' ? matchResultValue.value : null
    curriculums.value = curriculumResult.status === 'fulfilled' ? curriculumResult.value.data : []
    teachingReform.value = reformResult.status === 'fulfilled' ? reformResult.value : null
    if (!selectedMajor.value) {
      const nextMajor = teachingReform.value?.major || courses.value.find((item) => item?.major)?.major || ''
      selectedMajor.value = nextMajor ? String(nextMajor).trim() : ''
    }
  } catch (e) {
    error(`教师工作台加载失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

async function handleMajorChange(major) {
  selectedMajor.value = major
  await loadData()
}

function resetCourseForm() {
  editingCourseId.value = null
  courseForm.value = createEmptyCourseForm()
}

function handleEditCourse(course) {
  editingCourseId.value = course.id
  courseForm.value = {
    courseName: course.courseName || '',
    coreSkills: course.coreSkills || '',
    creditHours: course.creditHours || '',
    semester: course.semester || '',
    major: course.major || '',
    description: course.description || ''
  }
}

async function handleSubmitCourse() {
  if (saving.value) return
  saving.value = true
  try {
    const payload = {
      ...courseForm.value,
      creditHours: courseForm.value.creditHours ? Number(courseForm.value.creditHours) : null
    }

    if (editingCourseId.value) {
      await updateTeacherCourse(authStore.token, editingCourseId.value, payload)
      success('课程已更新，并重新生成教改分析。')
    } else {
      await createTeacherCourse(authStore.token, payload)
      success('课程已添加，并刷新供需分析。')
    }

    resetCourseForm()
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
    if (editingCourseId.value === id) {
      resetCourseForm()
    }
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
        <h1 class="page-intro-title">课程治理与教学改革工作台</h1>
        <p class="page-intro-text">
          这里把课程供给、市场需求和教改方案放到同一个工作面里，帮助你从“有课程”走到“课程真正对岗位有效”。
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

        <PremiumCard title="教改治理得分" glowColor="teal">
          <div class="score-grid">
            <div v-for="item in scoreCards" :key="item.label" class="score-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
          <div class="analysis-section">
            <h3>能力维度</h3>
            <div class="skills-list">
              <span
                v-for="item in capabilityDimensions.slice(0, 6)"
                :key="item.dimension || item.name"
                class="skill-tag"
              >
                {{ item.dimension || item.name }}
              </span>
              <span v-if="!capabilityDimensions.length" class="panel-muted">暂无能力维度拆解</span>
            </div>
          </div>
          <div class="analysis-section">
            <h3>目标岗位族</h3>
            <div class="skills-list">
              <span
                v-for="item in jobFamilies.slice(0, 6)"
                :key="item.jobFamily || item.name"
                class="skill-tag covered"
              >
                {{ item.jobFamily || item.name }}
              </span>
              <span v-if="!jobFamilies.length" class="panel-muted">暂无岗位族映射</span>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="专业聚焦" glowColor="secondary">
          <div class="analysis-section">
            <div class="button-row">
              <GlowButton variant="ghost" :class="{ 'active-filter': !selectedMajor }" @click="handleMajorChange('')">全部专业</GlowButton>
              <GlowButton
                v-for="major in majorOptions"
                :key="major"
                variant="ghost"
                :class="{ 'active-filter': selectedMajor === major }"
                @click="handleMajorChange(major)"
              >
                {{ major }}
              </GlowButton>
            </div>
            <div class="score-grid">
              <div class="score-item">
                <span>当前专业</span>
                <strong>{{ majorBrief.major }}</strong>
              </div>
              <div class="score-item">
                <span>课程数</span>
                <strong>{{ majorBrief.courses }}</strong>
              </div>
              <div class="score-item">
                <span>模块数</span>
                <strong>{{ majorBrief.modules }}</strong>
              </div>
              <div class="score-item">
                <span>毕业要求</span>
                <strong>{{ majorBrief.requirements }}</strong>
              </div>
            </div>
          </div>
        </PremiumCard>
      </section>

      <section class="teacher-grid mid-grid">
        <PremiumCard title="Excel 批量导入课程" glowColor="secondary">
          <div class="upload-panel">
            <p class="panel-muted">上传课程 Excel 后，系统会自动沉淀课程库并重建技能映射，便于后续供需分析。</p>
            <label class="upload-box">
              <input type="file" accept=".xlsx,.xls" class="hidden-input" @change="handlePickExcel" />
              <Files :size="22" />
              <div>
                <strong>{{ selectedExcelName || '选择课程 Excel 文件' }}</strong>
                <p>适合一次性导入课程名称、专业、学期、学时和技能关键词。</p>
              </div>
            </label>
            <div class="button-row">
              <GlowButton variant="primary" :loading="uploadLoading" @click="handleUploadExcel">
                <Upload :size="14" />
                上传并导入
              </GlowButton>
              <GlowButton variant="ghost" @click="router.push('/reports')">
                <FileSpreadsheet :size="14" />
                去生成教改报告
              </GlowButton>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard :title="editingCourseId ? '编辑课程' : '新增课程'" glowColor="secondary">
          <form class="form-stack" @submit.prevent="handleSubmitCourse">
            <div class="form-grid">
              <input v-model="courseForm.courseName" class="glass-input" placeholder="课程名称" required />
              <input v-model="courseForm.major" class="glass-input" placeholder="所属专业" />
              <input v-model="courseForm.semester" class="glass-input" placeholder="开课学期" />
              <input v-model="courseForm.creditHours" type="number" class="glass-input" placeholder="学时" />
            </div>
            <input v-model="courseForm.coreSkills" class="glass-input" placeholder="核心技能，多个技能请用逗号分隔" required />
            <textarea v-model="courseForm.description" class="glass-textarea" rows="4" placeholder="课程简介、教学目标或大纲关键词" />
            <div class="button-row">
              <GlowButton variant="primary" type="submit" :loading="saving">
                <Plus v-if="!editingCourseId" :size="14" />
                <Pencil v-else :size="14" />
                {{ submitLabel }}
              </GlowButton>
              <GlowButton v-if="editingCourseId" variant="ghost" type="button" @click="resetCourseForm">
                取消编辑
              </GlowButton>
            </div>
          </form>
        </PremiumCard>
      </section>

      <section class="teacher-grid content-grid">
        <PremiumCard title="供需分析重点" glowColor="purple">
          <div class="analysis-block">
            <div class="rate-card">
              <span class="rate-label">覆盖率</span>
              <strong>{{ matchResult?.coverageRate || '--' }}</strong>
              <p>这是课程内容与市场热门技能的贴合程度，不等于学生就业结果本身。</p>
            </div>

            <div class="analysis-section">
              <h3>优先补齐的技能</h3>
              <div class="gap-list">
                <div v-for="gap in topGapSkills" :key="gap.skill" class="gap-item">
                  <span>{{ gap.skill }}</span>
                  <strong>{{ gap.marketDemand }}</strong>
                </div>
                <span v-if="!topGapSkills.length" class="panel-muted">暂无待补技能</span>
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
          <div v-if="filteredCourses.length" class="course-list">
            <div v-for="course in filteredCourses" :key="course.id" class="course-item">
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
              <div class="course-actions">
                <button class="icon-btn" @click="handleEditCourse(course)">
                  <Pencil :size="16" />
                </button>
                <button class="icon-btn delete" @click="handleDeleteCourse(course.id)">
                  <Trash2 :size="16" />
                </button>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <BookOpen :size="28" />
            <p>当前还没有教师自建课程。可以先手动新增，也可以批量上传 Excel。</p>
          </div>

          <div v-if="filteredCurriculums.length" class="curriculum-list">
            <div v-for="item in filteredCurriculums" :key="item.id" class="curriculum-item">
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

      <section class="teacher-grid reform-grid">
        <PremiumCard title="毕业要求与课程蓝图" glowColor="secondary">
          <div class="focus-banner">
            <strong>{{ majorBrief.major }}</strong>
            <span>课程 {{ majorBrief.courses }} / 模块 {{ majorBrief.modules }} / 毕业要求 {{ majorBrief.requirements }}</span>
          </div>
          <div class="requirement-list">
            <div v-for="item in graduationRequirements" :key="item.code" class="requirement-item">
              <div class="requirement-head">
                <span class="pill">{{ item.code }}</span>
                <strong>{{ item.name }}</strong>
              </div>
              <p>{{ item.description }}</p>
            </div>
          </div>

          <div class="analysis-section">
            <h3>课程模块</h3>
            <div class="module-list">
              <div v-for="module in filteredCurriculumModules" :key="`${module.courseName}-${module.semester}`" class="module-item">
                <div class="curriculum-head">
                  <strong>{{ module.courseName }}</strong>
                  <span class="course-chip">{{ module.evidenceType || 'project-demo' }}</span>
                </div>
                <div class="course-meta">
                  <span>{{ module.major || '专业未设置' }}</span>
                  <span>{{ module.semester || '学期未设置' }}</span>
                  <span>{{ module.creditHours || '--' }} 学时</span>
                </div>
                <div class="skill-tags">
                  <span v-for="point in module.capabilityPoints || []" :key="`${module.courseName}-${point}`" class="skill-tag">
                    {{ point }}
                  </span>
                </div>
              </div>
              <div v-if="!filteredCurriculumModules.length" class="empty-inline-state">
                当前专业下还没有课程模块映射，可先补充课程专业字段或重新导入课程库。
              </div>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="教学改革动作" glowColor="teal">
          <div class="focus-banner">
            <strong>整改动作聚焦</strong>
            <span>当前按 {{ majorBrief.major }} 视角输出改革动作与考核建议</span>
          </div>
          <div class="action-list">
            <div v-for="item in reformActions" :key="`${item.priority}-${item.title}`" class="action-item">
              <div class="insight-head">
                <strong>{{ item.title }}</strong>
                <span class="pill">{{ item.priority }}</span>
              </div>
              <p>{{ item.detail }}</p>
            </div>
            <div v-if="!reformActions.length" class="empty-inline-state">
              当前专业还没有生成整改动作，建议先补齐课程、技能或重新触发教改分析。
            </div>
          </div>

          <div class="analysis-section">
            <h3>考核建议</h3>
            <div class="assessment-list">
              <div v-for="item in assessmentSuggestions" :key="item.label" class="assessment-item">
                <GraduationCap :size="18" />
                <div>
                  <strong>{{ item.label }}</strong>
                  <p>{{ item.detail }}</p>
                </div>
              </div>
              <div v-if="!assessmentSuggestions.length" class="empty-inline-state">
                当前专业还没有考核建议，可在课程映射后重新生成。
              </div>
            </div>
          </div>

          <div class="feature-list">
            <button class="feature-card" @click="router.push('/reports')">
              <Sparkles :size="20" />
              <div>
                <strong>生成教改报告</strong>
                <p>把课程缺口、毕业要求和治理得分整理成可汇报的正式材料。</p>
              </div>
            </button>
            <button class="feature-card" @click="router.push('/insights')">
              <TrendingUp :size="20" />
              <div>
                <strong>查看行业走势</strong>
                <p>继续观察薪资、技能和企业要求变化，决定下一轮课程调整方向。</p>
              </div>
            </button>
            <button class="feature-card" @click="router.push('/recommend')">
              <Lightbulb :size="20" />
              <div>
                <strong>回看学生结果</strong>
                <p>从学生侧推荐与履历评估反推课程训练是否真正转成求职竞争力。</p>
              </div>
            </button>
            <button class="feature-card" @click="router.push('/jobs')">
              <BriefcaseBusiness :size="20" />
              <div>
                <strong>核对岗位样本</strong>
                <p>直接回到岗位列表抽查真实 JD，确认教改建议与市场表述一致。</p>
              </div>
            </button>
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
.upload-panel,
.score-grid,
.requirement-list,
.action-list,
.module-list,
.assessment-list {
  display: grid;
  gap: 24px;
}

.top-grid,
.mid-grid,
.content-grid,
.reform-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.insight-card,
.feature-card,
.upload-box,
.course-item,
.curriculum-item,
.gap-item,
.score-item,
.requirement-item,
.action-item,
.module-item,
.assessment-item {
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.insight-card,
.curriculum-item,
.score-item,
.requirement-item,
.action-item,
.module-item {
  padding: 18px;
}

.insight-head,
.course-head,
.curriculum-head,
.requirement-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.insight-head strong,
.feature-card strong,
.course-head strong,
.curriculum-head strong,
.requirement-head strong,
.assessment-item strong {
  color: var(--c-text-primary);
}

.insight-card p,
.feature-card p,
.course-main p,
.curriculum-item p,
.rate-card p,
.panel-muted,
.action-item p,
.requirement-item p,
.assessment-item p {
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
.upload-box,
.assessment-item {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 14px;
  padding: 18px;
  color: inherit;
  text-align: left;
}

.focus-banner,
.empty-inline-state {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
}

.focus-banner {
  display: grid;
  gap: 6px;
}

.focus-banner span,
.empty-inline-state {
  color: var(--c-text-secondary);
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

.active-filter {
  border-color: rgba(30, 117, 255, 0.45);
  background: rgba(30, 117, 255, 0.12);
}

.score-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.score-item {
  display: grid;
  gap: 6px;
}

.score-item span {
  color: var(--c-text-secondary);
  font-size: 13px;
}

.score-item strong {
  font-size: 30px;
  line-height: 1;
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

.course-actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
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

.requirement-list,
.action-list,
.module-list {
  gap: 14px;
}

.assessment-item {
  align-items: start;
}

@media (max-width: 1024px) {
  .top-grid,
  .mid-grid,
  .content-grid,
  .reform-grid,
  .form-grid,
  .score-grid {
    grid-template-columns: 1fr;
  }
}
</style>
