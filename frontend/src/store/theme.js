import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'careerPlatform-theme'

function getSystemPreference() {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref('system')
  const systemPrefersDark = ref(getSystemPreference())
  let mediaQuery = null
  let mediaHandler = null

  const isDark = computed(() => (mode.value === 'system' ? systemPrefersDark.value : mode.value === 'dark'))
  const isSystem = computed(() => mode.value === 'system')
  const resolvedMode = computed(() => (isDark.value ? 'dark' : 'light'))

  function applyTheme() {
    if (typeof document === 'undefined') return
    const root = document.documentElement
    root.setAttribute('data-theme', resolvedMode.value)
    root.style.colorScheme = resolvedMode.value
  }

  function setMode(nextMode) {
    mode.value = ['light', 'dark', 'system'].includes(nextMode) ? nextMode : 'system'
    if (typeof window !== 'undefined') {
      if (mode.value === 'system') {
        window.localStorage.removeItem(STORAGE_KEY)
      } else {
        window.localStorage.setItem(STORAGE_KEY, mode.value)
      }
    }
    applyTheme()
  }

  function toggleTheme() {
    if (resolvedMode.value === 'dark') {
      setMode('light')
    } else {
      setMode('dark')
    }
  }

  function bindSystemTheme() {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function' || mediaQuery) {
      return
    }

    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    mediaHandler = (event) => {
      systemPrefersDark.value = event.matches
      if (mode.value === 'system') {
        applyTheme()
      }
    }

    systemPrefersDark.value = mediaQuery.matches
    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', mediaHandler)
    } else if (typeof mediaQuery.addListener === 'function') {
      mediaQuery.addListener(mediaHandler)
    }
  }

  function initTheme() {
    if (typeof window !== 'undefined') {
      const saved = window.localStorage.getItem(STORAGE_KEY)
      mode.value = saved === 'light' || saved === 'dark' ? saved : 'system'
    }
    bindSystemTheme()
    applyTheme()
  }

  return {
    mode,
    isDark,
    isSystem,
    resolvedMode,
    initTheme,
    setMode,
    toggleTheme
  }
})
