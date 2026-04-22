import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import TeacherView from './TeacherView.vue'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock })
}))

vi.mock('../store/auth', () => ({
  useAuthStore: () => ({
    token: 'test-token',
    user: { roleType: 2 }
  })
}))

vi.mock('../composables/useToast', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn()
  })
}))

const apiMocks = vi.hoisted(() => ({
  createTeacherCourse: vi.fn().mockResolvedValue({}),
  deleteTeacherCourse: vi.fn().mockResolvedValue({}),
  downloadTeacherMaterialTemplate: vi.fn().mockResolvedValue(new Blob()),
  fetchCurriculums: vi.fn().mockResolvedValue({ data: [] }),
  fetchTeacherCourses: vi.fn().mockResolvedValue([]),
  fetchTeacherMarketMatch: vi.fn().mockResolvedValue({
    coverageRate: '0.0%',
    marketGaps: [],
    coveredSkills: [],
    possiblyOutdated: []
  }),
  fetchTeacherMaterialStatus: vi.fn().mockResolvedValue({ items: [], ready: false, guidance: [] }),
  fetchTeachingReform: vi.fn().mockResolvedValue({}),
  uploadCurriculumExcel: vi.fn().mockResolvedValue({ imported: 0, mappedSkills: 0 }),
  uploadTeacherMaterial: vi.fn().mockResolvedValue({})
}))

vi.mock('../api', () => apiMocks)

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function waitFor(check: () => boolean, attempts = 20) {
  for (let i = 0; i < attempts; i += 1) {
    await flushPromises()
    if (check()) return true
  }
  return false
}

function mountView() {
  return mount(TeacherView, {
    global: {
      stubs: {
        GlowButton: {
          template: '<button class="stub-glow" @click="$emit(\'click\')"><slot /></button>'
        },
        BookOpen: true,
        CheckCircle2: true,
        Download: true,
        FileSpreadsheet: true,
        Files: true,
        FolderOpen: true,
        Plus: true,
        Trash2: true,
        Upload: true
      }
    }
  })
}

describe('TeacherView guards', () => {
  beforeEach(() => {
    pushMock.mockClear()
    apiMocks.fetchTeachingReform.mockResolvedValue({})
  })

  it('does not render raw reform json fallback', async () => {
    const wrapper = mountView()
    await flushPromises()
    await flushPromises()
    expect(wrapper.find('.reform-raw').exists()).toBe(false)
  })

  it('renders safely when teachingReform misses nested fields', async () => {
    apiMocks.fetchTeachingReform.mockResolvedValue({ blueprint: {}, governanceScorecard: {} })
    const wrapper = mountView()
    await waitFor(() => wrapper.exists())
    expect(wrapper.exists()).toBe(true)
  })

})
