<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BookOpen,
  BriefcaseBusiness,
  Download,
  FileSpreadsheet,
  GraduationCap,
  Lightbulb,
  Pencil,
  Plus,
  Sparkles,
  Trash2,
  TrendingUp,
  Upload,
  Users
} from 'lucide-vue-next'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  createTeacherCourse,
  deleteCurriculum,
  deleteTeacherMaterialAsset,
  deleteTeacherCourse,
  downloadCurriculumTemplate,
  downloadTeacherMaterialTemplate,
  fetchCurriculums,
  fetchPlatformStudentResumeStatus,
  fetchTeacherCourses,
  fetchTeacherMaterialStatus,
  fetchTeacherMaterials,
  fetchTeacherMarketMatch,
  fetchTeacherStudentRetrace,
  fetchTeacherTeachingReform,
  replaceCurriculumExcel,
  updateCurriculum,
  updateTeacherCourse,
  updateTeacherMaterialAsset,
  uploadCurriculumExcel,
  uploadTeacherMaterial
} from '../api'

const authStore = useAuthStore()
const router = useRouter()
const { success, error } = useToast()

const loading = ref(true)
const savingCourse = ref(false)
const savingCurriculum = ref(false)
const uploadLoadingByType = ref({
  CURRICULUM: false,
  SYLLABUS: false,
  STUDENT_STATUS: false
})

const courses = ref([])
const curriculums = ref([])
const teacherMaterials = ref([])
const materialStatus = ref({ items: [], ready: false, guidance: [] })
const matchResult = ref(null)
const teachingReform = ref(null)
const studentRetrace = ref({})
const resumeStatus = ref({ stats: {}, items: [], total: 0, page: 1, pageSize: 20 })
const selectedMajor = ref('')
const editingCourseId = ref(null)
const editingCurriculumId = ref(null)

const selectedFiles = ref({
  CURRICULUM: null,
  SYLLABUS: null,
  STUDENT_STATUS: null
})
const selectedFileNames = ref({
  CURRICULUM: '',
  SYLLABUS: '',
  STUDENT_STATUS: ''
})

const courseForm = ref(createEmptyCourseForm())
const curriculumForm = ref(createEmptyCurriculumForm())

function createEmptyCourseForm() {
  return {
    courseName: '',
    major: '',
    semester: '',
    creditHours: '',
    coreSkills: '',
    description: ''
  }
}

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

const materialItems = computed(() => {
  const items = Array.isArray(materialStatus.value?.items) ? materialStatus.value.items : []
  const map = Object.fromEntries(items.map((item) => [item.type, item]))
  return [
    {
      type: 'CURRICULUM',
      title: '课程清单 Excel',
      description: '导入课程名称、所属专业、开课学期、学分学时和技能关键词，更新当前教师自己的课程库。',
      status: map.CURRICULUM || {}
    },
    {
      type: 'SYLLABUS',
      title: '教学大纲 Excel',
      description: '导入课程目标、能力点、毕业要求和考核方式，为教学改革分析提供依据。',
      status: map.SYLLABUS || {}
    },
    {
      type: 'STUDENT_STATUS',
      title: '学生情况 Excel',
      description: '导入班级规模、能力短板、目标岗位和帮扶重点，后续回查将基于这份数据。',
      status: map.STUDENT_STATUS || {}
    }
  ]
})

const materialsReady = computed(() => Boolean(materialStatus.value?.ready))
const uploadedMaterialCount = computed(() => materialItems.value.filter((item) => item.status?.uploaded).length)

const majorOptions = computed(() => {
  const values = new Set()
  courses.value.forEach((item) => item?.major && values.add(String(item.major).trim()))
  curriculums.value.forEach((item) => item?.major && values.add(String(item.major).trim()))
  teacherMaterials.value.forEach((item) => item?.major && values.add(String(item.major).trim()))
  if (teachingReform.value?.major) values.add(String(teachingReform.value.major).trim())
  if (studentRetrace.value?.major) values.add(String(studentRetrace.value.major).trim())
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

const dashboardCards = computed(() => [
  {
    label: '资料完备度',
    value: `${uploadedMaterialCount.value}/3`,
    hint: materialsReady.value ? '可继续查看诊断与教改建议' : '还需补齐课程清单、教学大纲和学生情况'
  },
  {
    label: '教师自建课程',
    value: courses.value.length,
    hint: '当前教师维护的课程条目'
  },
  {
    label: '课程库条目',
    value: curriculums.value.length,
    hint: '仅展示当前教师上传的课程库'
  },
  {
    label: '技能覆盖率',
    value: matchResult.value?.coverageRate || '--',
    hint: matchResult.value?.explicitMajor ? '已按指定专业分析' : '按系统推断专业分析'
  }
])

const topGapSkills = computed(() => (matchResult.value?.marketGaps || []).slice(0, 6))
const topCoveredSkills = computed(() => (matchResult.value?.coveredSkills || []).slice(0, 8))
const governanceDimensions = computed(() => teachingReform.value?.governanceScorecard?.dimensions || [])
const governanceRisks = computed(() => teachingReform.value?.governanceScorecard?.risks || [])
const reformActions = computed(() => teachingReform.value?.blueprint?.reformActions || [])
const assessmentSuggestions = computed(() => teachingReform.value?.blueprint?.assessmentSuggestions || [])
const materialReadiness = computed(() => teachingReform.value?.blueprint?.materialReadiness || [])
const retraceSummary = computed(() => studentRetrace.value?.cohortSummary || {})
const retraceProfiles = computed(() => studentRetrace.value?.studentProfiles || [])
const retraceInsights = computed(() => studentRetrace.value?.insights || [])
const resumeStats = computed(() => resumeStatus.value?.stats || {})

async function loadData() {
  if (![1, 2].includes(authStore.user?.roleType)) {
    loading.value = false
    return
  }

  loading.value = true
  const majorParams = { major: selectedMajor.value || undefined }
  try {
    const [statusRes, materialRes, courseRes, curriculumRes, matchRes, reformRes, retraceRes, resumeRes] = await Promise.allSettled([
      fetchTeacherMaterialStatus(authStore.token, majorParams),
      fetchTeacherMaterials(authStore.token, majorParams),
      fetchTeacherCourses(authStore.token),
      fetchCurriculums(authStore.token, { page: 1, pageSize: 50, ...majorParams }),
      fetchTeacherMarketMatch(authStore.token, majorParams),
      fetchTeacherTeachingReform(authStore.token, majorParams),
      fetchTeacherStudentRetrace(authStore.token, majorParams),
      fetchPlatformStudentResumeStatus(authStore.token, { page: 1, pageSize: 20, ...majorParams })
    ])

    materialStatus.value = statusRes.status === 'fulfilled'
      ? statusRes.value
      : { items: [], ready: false, guidance: ['资料状态读取失败，请稍后重试。'] }
    teacherMaterials.value = materialRes.status === 'fulfilled' ? materialRes.value : []
    courses.value = courseRes.status === 'fulfilled' ? courseRes.value : []
    curriculums.value = curriculumRes.status === 'fulfilled' ? curriculumRes.value.data : []
    matchResult.value = matchRes.status === 'fulfilled' ? matchRes.value : null
    teachingReform.value = reformRes.status === 'fulfilled' ? reformRes.value : null
    studentRetrace.value = retraceRes.status === 'fulfilled' ? retraceRes.value : {}
    resumeStatus.value = resumeRes.status === 'fulfilled'
      ? resumeRes.value
      : { stats: {}, items: [], total: 0, page: 1, pageSize: 20 }

    if (!selectedMajor.value) {
      const nextMajor =
        studentRetrace.value?.major ||
        teachingReform.value?.major ||
        curriculums.value.find((item) => item?.major)?.major ||
        courses.value.find((item) => item?.major)?.major ||
        ''
      selectedMajor.value = nextMajor ? String(nextMajor).trim() : ''
    }
  } catch (e) {
    error(`教师工作台加载失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

function resetCourseForm() {
  editingCourseId.value = null
  courseForm.value = createEmptyCourseForm()
}

function resetCurriculumForm() {
  editingCurriculumId.value = null
  curriculumForm.value = createEmptyCurriculumForm()
}

function handleEditCourse(course) {
  editingCourseId.value = course.id
  courseForm.value = {
    courseName: course.courseName || '',
    major: course.major || '',
    semester: course.semester || '',
    creditHours: course.creditHours || '',
    coreSkills: course.coreSkills || '',
    description: course.description || ''
  }
}

function handleEditCurriculum(item) {
  editingCurriculumId.value = item.id
  curriculumForm.value = {
    courseName: item.courseName || '',
    courseCode: item.courseCode || '',
    department: item.department || '',
    major: item.major || '',
    credit: item.credit || '',
    semester: item.semester || '',
    description: item.description || '',
    keywords: Array.isArray(item.keywords) ? item.keywords.join(', ') : ''
  }
}

async function handleSubmitCourse() {
  if (savingCourse.value) return
  savingCourse.value = true
  try {
    const payload = {
      ...courseForm.value,
      creditHours: courseForm.value.creditHours ? Number(courseForm.value.creditHours) : null
    }
    if (editingCourseId.value) {
      await updateTeacherCourse(authStore.token, editingCourseId.value, payload)
      success('教师课程已更新')
    } else {
      await createTeacherCourse(authStore.token, payload)
      success('教师课程已新增')
    }
    resetCourseForm()
    await loadData()
  } catch (e) {
    error(e.message)
  } finally {
    savingCourse.value = false
  }
}

async function handleDeleteCourse(id) {
  try {
    await deleteTeacherCourse(authStore.token, id)
    if (editingCourseId.value === id) resetCourseForm()
    success('教师课程已删除')
    await loadData()
  } catch (e) {
    error(e.message)
  }
}

function handlePickMaterial(type, event) {
  const [file] = event.target.files || []
  selectedFiles.value[type] = file || null
  selectedFileNames.value[type] = file?.name || ''
  event.target.value = ''
}

function buildTeacherMaterialFormData(type) {
  const formData = new FormData()
  formData.append('file', selectedFiles.value[type])
  formData.append('materialType', type)
  if (selectedMajor.value) {
    formData.append('major', selectedMajor.value)
  }
  return formData
}

async function handleDownloadMaterialTemplate(type) {
  try {
    if (type === 'CURRICULUM') {
      await downloadCurriculumTemplate(authStore.token)
    } else {
      await downloadTeacherMaterialTemplate(authStore.token, type)
    }
    success('模板已开始下载')
  } catch (e) {
    error(e.message)
  }
}

async function handleUploadMaterial(type) {
  if (!selectedFiles.value[type] || uploadLoadingByType.value[type]) return
  uploadLoadingByType.value[type] = true
  try {
    if (type === 'CURRICULUM') {
      const hasExistingCurriculum = Boolean(materialItems.value.find((item) => item.type === 'CURRICULUM')?.status?.uploaded)
      const result = hasExistingCurriculum
        ? await replaceCurriculumExcel(authStore.token, selectedFiles.value.CURRICULUM)
        : await uploadCurriculumExcel(authStore.token, selectedFiles.value.CURRICULUM)
      if (hasExistingCurriculum) {
        success(`课程清单已替换：停用 ${result.replaced || 0} 条旧记录，导入 ${result.imported || 0} 条新记录`)
      } else {
        success(`课程清单导入完成：成功 ${result.imported || 0} 条，映射技能 ${result.mappedSkills || 0} 条`)
      }
    } else {
      const assetId = materialItems.value.find((item) => item.type === type)?.status?.assetId
      const result = assetId
        ? await updateTeacherMaterialAsset(authStore.token, assetId, selectedFiles.value[type], selectedMajor.value || '')
        : await uploadTeacherMaterial(authStore.token, buildTeacherMaterialFormData(type))
      success(`${type === 'SYLLABUS' ? '教学大纲' : '学生情况'}上传成功，共 ${result.rowCount || 0} 行`)
    }
    selectedFiles.value[type] = null
    selectedFileNames.value[type] = ''
    await loadData()
  } catch (e) {
    error(e.message)
  } finally {
    uploadLoadingByType.value[type] = false
  }
}

async function handleDeleteMaterial(type) {
  if (type === 'CURRICULUM') {
    error('课程清单按课程条目导入，请在课程库列表中删除具体条目。')
    return
  }
  const assetId = materialItems.value.find((item) => item.type === type)?.status?.assetId
  if (!assetId) return
  try {
    await deleteTeacherMaterialAsset(authStore.token, assetId)
    selectedFiles.value[type] = null
    selectedFileNames.value[type] = ''
    success(type === 'SYLLABUS' ? '教学大纲资料已删除' : '学生情况资料已删除')
    await loadData()
  } catch (e) {
    error(e.message)
  }
}

async function handleSubmitCurriculum() {
  if (!editingCurriculumId.value || savingCurriculum.value) return
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
    await loadData()
  } catch (e) {
    error(e.message)
  } finally {
    savingCurriculum.value = false
  }
}

async function handleDeleteCurriculum(id) {
  try {
    await deleteCurriculum(authStore.token, id)
    if (editingCurriculumId.value === id) resetCurriculumForm()
    success('课程库条目已删除')
    await loadData()
  } catch (e) {
    error(e.message)
  }
}

async function handleMajorChange(major) {
  selectedMajor.value = major
  await loadData()
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="teacher-view page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-head">
        <div>
          <span class="page-eyebrow">教师工作台</span>
          <h1 class="page-intro-title">课程治理与学生回查</h1>
          <p class="page-intro-text">
            这里统一管理课程清单、教学大纲、学生情况和教师自建课程。课程供需分析支持显式指定专业，
            学生回查则基于教师本人上传的学生情况数据，并可直接查看平台学生简历上传状态。
          </p>
        </div>
        <div class="page-intro-actions">
          <GlowButton variant="secondary" @click="router.push('/reports')">
            <FileSpreadsheet :size="14" />
            前往报告中心
          </GlowButton>
          <GlowButton variant="ghost" @click="router.push('/jobs')">
            <BriefcaseBusiness :size="14" />
            查看岗位样本
          </GlowButton>
        </div>
      </div>

      <div class="metric-grid">
        <div v-for="item in dashboardCards" :key="item.label" class="metric-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <p>{{ item.hint }}</p>
        </div>
      </div>

      <div class="major-filter">
        <GlowButton variant="ghost" :class="{ active: !selectedMajor }" @click="handleMajorChange('')">
          全部专业
        </GlowButton>
        <GlowButton
          v-for="major in majorOptions"
          :key="major"
          variant="ghost"
          :class="{ active: selectedMajor === major }"
          @click="handleMajorChange(major)"
        >
          {{ major }}
        </GlowButton>
      </div>
    </section>

    <section class="content-grid">
      <PremiumCard title="资料上传与替换" glowColor="primary">
        <div class="material-grid">
          <div v-for="item in materialItems" :key="item.type" class="material-card">
            <div class="card-head">
              <div>
                <strong>{{ item.title }}</strong>
                <p>{{ item.description }}</p>
              </div>
              <span class="pill" :class="{ active: item.status?.uploaded }">
                {{ item.status?.uploaded ? '已上传' : '待上传' }}
              </span>
            </div>

            <label class="upload-panel">
              <input type="file" accept=".xlsx,.xls" class="hidden-input" @change="handlePickMaterial(item.type, $event)" />
              <Upload :size="18" />
              <div>
                <strong>{{ selectedFileNames[item.type] || `选择${item.title}` }}</strong>
                <p>{{ item.status?.uploaded ? '再次上传将覆盖当前资料。' : '请先选择 Excel 文件，再执行上传。' }}</p>
              </div>
            </label>

            <div class="meta-row">
              <span>记录数：{{ item.status?.recordCount || 0 }}</span>
              <span>最近更新时间：{{ item.status?.latestUploadedAt || '--' }}</span>
            </div>

            <div v-if="item.status?.latestFileName" class="meta-note">
              当前文件：{{ item.status.latestFileName }}
            </div>

            <div class="button-row">
              <GlowButton variant="primary" :loading="uploadLoadingByType[item.type]" @click="handleUploadMaterial(item.type)">
                <Upload :size="14" />
                {{ item.status?.uploaded ? '替换上传' : '立即上传' }}
              </GlowButton>
              <GlowButton variant="ghost" @click="handleDownloadMaterialTemplate(item.type)">
                <Download :size="14" />
                下载模板
              </GlowButton>
              <GlowButton
                v-if="item.type !== 'CURRICULUM' && item.status?.assetId"
                variant="ghost"
                @click="handleDeleteMaterial(item.type)"
              >
                <Trash2 :size="14" />
                删除资料
              </GlowButton>
            </div>
          </div>
        </div>

        <div class="guidance-box">
          <strong>当前提示</strong>
          <ul>
            <li v-for="(item, index) in materialStatus.guidance || []" :key="index">{{ item }}</li>
          </ul>
        </div>
      </PremiumCard>

      <PremiumCard :title="editingCourseId ? '编辑教师课程' : '新增教师课程'" glowColor="secondary">
        <div class="teacher-course-shell">
          <div class="teacher-course-intro">
            <div>
              <strong>{{ editingCourseId ? '调整当前课程信息' : '补充教师自建课程信息' }}</strong>
              <p>按课程名称、所属专业、开课学期、学时、核心技能和课程说明维护数据，供后续课程供需分析直接使用。</p>
            </div>
            <span class="course-count-badge">当前 {{ filteredCourses.length }} 门</span>
          </div>

          <form class="form-stack teacher-course-form" @submit.prevent="handleSubmitCourse">
            <div class="course-form-grid">
              <label class="field-block course-form-span-2">
                <span class="field-label">课程名称</span>
                <input v-model="courseForm.courseName" class="glass-input" placeholder="例如：Python 数据分析基础" required />
              </label>

              <label class="field-block">
                <span class="field-label">所属专业</span>
                <input v-model="courseForm.major" class="glass-input" placeholder="例如：数据科学与大数据技术" />
              </label>

              <label class="field-block">
                <span class="field-label">开课学期</span>
                <input v-model="courseForm.semester" class="glass-input" placeholder="例如：第 3 学期 / 2026 春" />
              </label>

              <label class="field-block">
                <span class="field-label">学时</span>
                <input v-model="courseForm.creditHours" type="number" min="0" class="glass-input" placeholder="例如：48" />
              </label>

              <label class="field-block course-form-span-2">
                <span class="field-label">核心技能，多个技能用逗号分隔</span>
                <input
                  v-model="courseForm.coreSkills"
                  class="glass-input"
                  placeholder="例如：Python, 数据清洗, 可视化"
                  required
                />
                <span class="field-help">建议填写 3 到 6 个可以直接映射岗位能力的技能点。</span>
              </label>

              <label class="field-block course-form-span-2">
                <span class="field-label">课程简介、教学目标或说明</span>
                <textarea
                  v-model="courseForm.description"
                  class="glass-textarea"
                  rows="5"
                  placeholder="说明课程内容、教学目标、实践场景或与岗位能力的对应关系"
                />
              </label>
            </div>

            <div class="button-row teacher-course-actions">
              <GlowButton variant="primary" type="submit" :loading="savingCourse">
                <Plus v-if="!editingCourseId" :size="14" />
                <Pencil v-else :size="14" />
                {{ editingCourseId ? '保存修改' : '新增课程' }}
              </GlowButton>
              <GlowButton v-if="editingCourseId" variant="ghost" type="button" @click="resetCourseForm">
                取消编辑
              </GlowButton>
            </div>
          </form>

          <div v-if="filteredCourses.length" class="teacher-course-list">
            <div class="teacher-course-list-head">
              <div>
                <strong>已录入课程</strong>
                <p>按当前专业筛选展示，可继续编辑课程说明、技能和学期信息。</p>
              </div>
            </div>

            <div class="list-stack">
              <div v-for="course in filteredCourses" :key="course.id" class="list-card teacher-course-card">
                <div class="card-head">
                  <div>
                    <strong>{{ course.courseName }}</strong>
                    <p>{{ course.description || '暂无课程说明' }}</p>
                  </div>
                  <span class="pill">{{ course.major || '未填写专业' }}</span>
                </div>
                <div class="meta-row teacher-course-meta">
                  <span>{{ course.semester || '未填写学期' }}</span>
                  <span>{{ course.creditHours || '--' }} 学时</span>
                </div>
                <div class="tag-row">
                  <span
                    v-for="skill in String(course.coreSkills || '').split(',').map((item) => item.trim()).filter(Boolean)"
                    :key="`${course.id}-${skill}`"
                    class="skill-tag"
                  >
                    {{ skill }}
                  </span>
                </div>
                <div class="button-row">
                  <GlowButton variant="ghost" @click="handleEditCourse(course)">
                    <Pencil :size="14" />
                    编辑
                  </GlowButton>
                  <GlowButton variant="ghost" @click="handleDeleteCourse(course.id)">
                    <Trash2 :size="14" />
                    删除
                  </GlowButton>
                </div>
              </div>
            </div>
          </div>
        </div>
      </PremiumCard>
    </section>

    <section class="content-grid">
      <PremiumCard title="课程库条目管理" glowColor="teal">
        <form v-if="editingCurriculumId" class="form-stack editor-box" @submit.prevent="handleSubmitCurriculum">
          <div class="form-grid">
            <input v-model="curriculumForm.courseName" class="glass-input" placeholder="课程名称" required />
            <input v-model="curriculumForm.courseCode" class="glass-input" placeholder="课程代码" />
            <input v-model="curriculumForm.department" class="glass-input" placeholder="院系" />
            <input v-model="curriculumForm.major" class="glass-input" placeholder="专业" />
            <input v-model="curriculumForm.credit" type="number" step="0.5" class="glass-input" placeholder="学分" />
            <input v-model="curriculumForm.semester" class="glass-input" placeholder="开课学期" />
          </div>
          <input v-model="curriculumForm.keywords" class="glass-input" placeholder="技能关键词，多个关键词用逗号分隔" />
          <textarea v-model="curriculumForm.description" class="glass-textarea" rows="4" placeholder="课程描述" />
          <div class="button-row">
            <GlowButton variant="primary" type="submit" :loading="savingCurriculum">
              <Pencil :size="14" />
              保存课程库修改
            </GlowButton>
            <GlowButton variant="ghost" type="button" @click="resetCurriculumForm">
              取消编辑
            </GlowButton>
          </div>
        </form>

        <div v-if="filteredCurriculums.length" class="list-stack">
          <div v-for="item in filteredCurriculums" :key="item.id" class="list-card">
            <div class="card-head">
              <div>
                <strong>{{ item.courseName }}</strong>
                <p>{{ item.description || '暂无课程描述' }}</p>
              </div>
              <span class="pill">{{ item.major || '未填写专业' }}</span>
            </div>
            <div class="meta-row">
              <span>{{ item.department || '未填写院系' }}</span>
              <span>{{ item.semester || '未填写学期' }}</span>
              <span>{{ item.credit || '--' }} 学分</span>
            </div>
            <div class="button-row">
              <GlowButton variant="ghost" @click="handleEditCurriculum(item)">
                <Pencil :size="14" />
                编辑
              </GlowButton>
              <GlowButton variant="ghost" @click="handleDeleteCurriculum(item.id)">
                <Trash2 :size="14" />
                删除
              </GlowButton>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">
          <BookOpen :size="24" />
          <p>当前筛选条件下没有课程库条目。</p>
        </div>
      </PremiumCard>

      <PremiumCard title="课程供需与教学改革" glowColor="purple">
        <div class="metric-grid compact">
          <div class="metric-card">
            <span>技能覆盖率</span>
            <strong>{{ matchResult?.coverageRate || '--' }}</strong>
            <p>{{ matchResult?.explicitMajor ? '已按指定专业分析岗位需求。' : '当前为系统推断专业结果。' }}</p>
          </div>
          <div class="metric-card">
            <span>高优先级缺口</span>
            <strong>{{ topGapSkills.length }}</strong>
            <p>优先补齐这些技能，可以最快改善课程供需匹配。</p>
          </div>
          <div class="metric-card">
            <span>治理风险</span>
            <strong>{{ governanceRisks.length }}</strong>
            <p>教学改革分析识别出的重点风险项。</p>
          </div>
        </div>

        <div class="analysis-block">
          <div>
            <h3>待补技能</h3>
            <div class="tag-row">
              <span v-for="item in topGapSkills" :key="item.skill" class="skill-tag">{{ item.skill }}</span>
              <span v-if="!topGapSkills.length" class="muted">暂无明显缺口</span>
            </div>
          </div>

          <div>
            <h3>已覆盖技能</h3>
            <div class="tag-row">
              <span v-for="item in topCoveredSkills" :key="item" class="skill-tag covered">{{ item }}</span>
              <span v-if="!topCoveredSkills.length" class="muted">暂无覆盖数据</span>
            </div>
          </div>

          <div>
            <h3>治理维度</h3>
            <div class="list-stack compact-list">
              <div v-for="item in governanceDimensions" :key="item.label" class="mini-card">
                <strong>{{ item.label }}</strong>
                <span>{{ item.score ?? '--' }}</span>
                <p>{{ item.evidence || '暂无证据说明' }}</p>
              </div>
              <div v-if="!governanceDimensions.length" class="muted">暂无治理维度数据</div>
            </div>
          </div>

          <div>
            <h3>资料就绪情况</h3>
            <div class="list-stack compact-list">
              <div v-for="item in materialReadiness" :key="item.name" class="mini-card">
                <strong>{{ item.name }}</strong>
                <span>{{ item.ready ? '已就绪' : '未就绪' }}</span>
                <p>{{ item.detail }}</p>
              </div>
              <div v-if="!materialReadiness.length" class="muted">暂无资料就绪明细</div>
            </div>
          </div>

          <div>
            <h3>教改动作</h3>
            <ul class="plain-list">
              <li v-for="item in reformActions" :key="`${item.priority}-${item.title}`">
                <strong>{{ item.priority }} · {{ item.title }}</strong>
                <span>{{ item.detail }}</span>
              </li>
              <li v-if="!reformActions.length" class="muted">暂无教改动作</li>
            </ul>
          </div>

          <div>
            <h3>考核建议</h3>
            <ul class="plain-list">
              <li v-for="item in assessmentSuggestions" :key="item.label">
                <strong>{{ item.label }}</strong>
                <span>{{ item.detail }}</span>
              </li>
              <li v-if="!assessmentSuggestions.length" class="muted">暂无考核建议</li>
            </ul>
          </div>
        </div>

        <div class="button-row">
          <GlowButton variant="secondary" :disabled="!materialsReady" @click="router.push('/reports')">
            <Sparkles :size="14" />
            生成教改报告
          </GlowButton>
          <GlowButton variant="ghost" @click="router.push('/insights')">
            <TrendingUp :size="14" />
            查看行业趋势
          </GlowButton>
          <GlowButton variant="ghost" @click="router.push('/jobs')">
            <GraduationCap :size="14" />
            对照岗位样本
          </GlowButton>
        </div>
      </PremiumCard>
    </section>

    <section class="content-grid">
      <PremiumCard title="基于我上传学生情况的回查" glowColor="gold">
        <div class="metric-grid compact">
          <div class="metric-card">
            <span>回查范围</span>
            <strong>{{ studentRetrace.studentStatusUploaded ? '已锁定' : '未准备' }}</strong>
            <p>{{ studentRetrace.major || '未指定专业' }}</p>
          </div>
          <div class="metric-card">
            <span>学生情况记录</span>
            <strong>{{ retraceSummary.rowCount || 0 }}</strong>
            <p>来自教师本人上传的学生情况表。</p>
          </div>
          <div class="metric-card">
            <span>匹配到的学生画像</span>
            <strong>{{ retraceProfiles.length }}</strong>
            <p>按当前专业范围汇总的平台学生画像。</p>
          </div>
        </div>

        <div class="section-stack">
          <div class="section-panel">
            <div class="section-head">
              <strong>教师上传样本摘要</strong>
              <span class="pill" :class="{ active: studentRetrace.studentStatusUploaded }">
                {{ studentRetrace.studentStatusUploaded ? '已上传' : '未上传' }}
              </span>
            </div>
            <div class="meta-row">
              <span>文件：{{ studentRetrace.studentStatusAsset?.fileName || '--' }}</span>
              <span>最近更新：{{ studentRetrace.studentStatusAsset?.updatedAt || '--' }}</span>
            </div>
            <div class="tag-row">
              <span v-for="item in retraceSummary.classNames || []" :key="item" class="skill-tag">{{ item }}</span>
              <span v-if="!(retraceSummary.classNames || []).length" class="muted">暂未提取到班级字段</span>
            </div>
            <div class="sample-grid">
              <div class="sample-card">
                <strong>目标岗位</strong>
                <p>{{ (retraceSummary.targetRoles || []).join('、') || '暂无' }}</p>
              </div>
              <div class="sample-card">
                <strong>能力短板</strong>
                <p>{{ (retraceSummary.weakSkills || []).join('、') || '暂无' }}</p>
              </div>
            </div>
          </div>

          <div class="section-panel">
            <div class="section-head">
              <strong>回查结论</strong>
              <Users :size="18" />
            </div>
            <ul class="plain-list">
              <li v-for="item in retraceInsights" :key="item">{{ item }}</li>
              <li v-if="!retraceInsights.length" class="muted">暂无回查结论</li>
            </ul>
          </div>
        </div>

        <div class="list-stack compact-list">
          <div v-for="item in retraceProfiles" :key="item.userId" class="list-card student-card">
            <div class="card-head">
              <div>
                <strong>{{ item.nickname || item.username }}</strong>
                <p>{{ item.majorName || '未填写专业' }}</p>
              </div>
              <span class="pill" :class="{ active: item.profileReady }">
                {{ item.profileReady ? '画像就绪' : '待补画像' }}
              </span>
            </div>
            <div class="meta-row">
              <span>简历状态：{{ item.resumeUploaded ? '已上传/已形成画像' : '未上传' }}</span>
              <span>完整度：{{ item.profileCompleteness }}%</span>
              <span>最近更新：{{ item.updatedAt || '--' }}</span>
            </div>
            <div class="tag-row">
              <span v-for="skill in (item.skills || []).slice(0, 6)" :key="`${item.userId}-${skill}`" class="skill-tag covered">
                {{ skill }}
              </span>
            </div>
          </div>
          <div v-if="!retraceProfiles.length" class="empty-state">
            <Users :size="24" />
            <p>当前专业范围下还没有可回查的学生画像。</p>
          </div>
        </div>
      </PremiumCard>

      <PremiumCard title="平台学生简历上传情况" glowColor="indigo">
        <div class="metric-grid compact">
          <div class="metric-card">
            <span>学生总数</span>
            <strong>{{ resumeStats.totalStudents || 0 }}</strong>
            <p>当前筛选专业范围内的学生账号。</p>
          </div>
          <div class="metric-card">
            <span>简历上传率</span>
            <strong>{{ resumeStats.resumeUploadedRate || '0%' }}</strong>
            <p>只统计已形成画像或有简历关键信息的学生。</p>
          </div>
          <div class="metric-card">
            <span>平均完整度</span>
            <strong>{{ resumeStats.averageCompleteness || 0 }}%</strong>
            <p>用于观察学生画像填写质量。</p>
          </div>
        </div>

        <div class="list-stack compact-list">
          <div v-for="item in resumeStatus.items || []" :key="`resume-${item.userId}`" class="list-card student-card">
            <div class="card-head">
              <div>
                <strong>{{ item.nickname || item.username }}</strong>
                <p>{{ item.majorName || '未填写专业' }}</p>
              </div>
              <span class="pill" :class="{ active: item.resumeUploaded }">
                {{ item.resumeUploaded ? '已上传简历' : '未上传简历' }}
              </span>
            </div>
            <div class="meta-row">
              <span>目标方向：{{ item.targetJob || '未填写' }}</span>
              <span>技能数：{{ item.skillsCount || 0 }}</span>
              <span>完整度：{{ item.profileCompleteness }}%</span>
            </div>
          </div>
          <div v-if="!(resumeStatus.items || []).length" class="empty-state">
            <FileSpreadsheet :size="24" />
            <p>当前没有学生简历上传记录。</p>
          </div>
        </div>
      </PremiumCard>
    </section>

    <div v-if="loading" class="loading-mask">正在加载教师工作台...</div>
  </div>
</template>

<style scoped>
.teacher-view,
.content-grid,
.metric-grid,
.material-grid,
.list-stack,
.form-stack,
.analysis-block,
.section-stack {
  display: grid;
  gap: 24px;
}

.page-intro,
.metric-card,
.material-card,
.list-card,
.editor-box,
.guidance-box,
.mini-card,
.section-panel,
.sample-card {
  border-radius: 20px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 248, 252, 0.95));
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.05);
}

.page-intro {
  padding: 24px;
}

.page-intro-head,
.card-head,
.meta-row,
.button-row,
.page-intro-actions,
.section-head {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
}

.page-eyebrow {
  display: inline-block;
  margin-bottom: 8px;
  color: var(--c-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-intro-title {
  margin: 0;
  font-size: 34px;
  line-height: 1.15;
  color: var(--c-text-primary);
}

.page-intro-text,
.metric-card p,
.material-card p,
.list-card p,
.teacher-course-intro p,
.teacher-course-list-head p,
.mini-card p,
.plain-list span,
.muted,
.sample-card p {
  margin: 0;
  color: var(--c-text-secondary);
  line-height: 1.7;
}

.content-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.metric-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-grid.compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-card,
.material-card,
.list-card,
.editor-box,
.guidance-box,
.section-panel,
.sample-card {
  padding: 18px;
}

.metric-card span {
  font-size: 12px;
  color: var(--c-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.metric-card strong {
  display: block;
  margin: 10px 0 8px;
  font-size: 30px;
  color: var(--c-text-primary);
}

.major-filter,
.tag-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.major-filter :deep(.glow-button.active) {
  border-color: rgba(15, 118, 110, 0.32);
  background: rgba(15, 118, 110, 0.12);
}

.material-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.material-card,
.list-card,
.mini-card,
.section-panel {
  display: grid;
  gap: 14px;
}

.upload-panel {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 12px;
  padding: 16px;
  border-radius: 16px;
  border: 1px dashed rgba(15, 23, 42, 0.18);
  background: rgba(15, 23, 42, 0.03);
  cursor: pointer;
}

.hidden-input {
  display: none;
}

.pill,
.skill-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.pill.active,
.skill-tag.covered {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.meta-row,
.meta-note {
  color: var(--c-text-muted);
  font-size: 13px;
}

.guidance-box ul,
.plain-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 10px;
}

.plain-list li {
  display: grid;
  gap: 4px;
}

.plain-list strong,
.card-head strong,
.mini-card strong,
.section-head strong,
.sample-card strong {
  color: var(--c-text-primary);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.teacher-course-shell {
  display: grid;
  gap: 18px;
}

.teacher-course-intro,
.teacher-course-list-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.teacher-course-intro strong,
.teacher-course-list-head strong,
.field-label {
  color: var(--c-text-primary);
}

.course-count-badge {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
}

.teacher-course-form {
  gap: 18px;
  padding: 20px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background:
    linear-gradient(135deg, rgba(15, 118, 110, 0.06), rgba(37, 99, 235, 0.04)),
    rgba(255, 255, 255, 0.9);
}

.course-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 16px;
}

.course-form-span-2 {
  grid-column: 1 / -1;
}

.field-block {
  display: grid;
  gap: 8px;
}

.field-label {
  font-size: 13px;
  font-weight: 700;
}

.field-help {
  font-size: 12px;
  line-height: 1.6;
  color: var(--c-text-muted);
}

.teacher-course-actions {
  align-items: center;
}

.teacher-course-list,
.section-panel {
  display: grid;
  gap: 16px;
}

.teacher-course-card,
.student-card {
  gap: 16px;
}

.teacher-course-meta {
  padding-bottom: 2px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.glass-input,
.glass-textarea {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: rgba(255, 255, 255, 0.92);
  color: var(--c-text-primary);
}

.glass-textarea {
  resize: vertical;
}

.empty-state,
.loading-mask {
  padding: 20px;
  border-radius: 18px;
  border: 1px dashed rgba(15, 23, 42, 0.18);
  color: var(--c-text-secondary);
  display: grid;
  gap: 10px;
  justify-items: center;
  text-align: center;
}

.analysis-block {
  gap: 18px;
}

.compact-list {
  gap: 12px;
}

.mini-card span {
  color: var(--c-text-muted);
  font-size: 13px;
}

.section-stack {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.sample-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.loading-mask {
  position: sticky;
  bottom: 12px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
}

@media (max-width: 1100px) {
  .content-grid,
  .material-grid,
  .metric-grid,
  .metric-grid.compact,
  .form-grid,
  .course-form-grid,
  .section-stack,
  .sample-grid {
    grid-template-columns: 1fr;
  }

  .course-form-span-2 {
    grid-column: auto;
  }
}
</style>
