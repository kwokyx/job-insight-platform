import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  // Check local storage or system preference
  const isDark = ref(true)
  
  const initTheme = () => {
    const saved = localStorage.getItem('careerPlatform-theme')
    if (saved) {
      isDark.value = saved === 'dark'
    } else {
      isDark.value = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    applyTheme()
  }

  const toggleTheme = () => {
    isDark.value = !isDark.value
    localStorage.setItem('careerPlatform-theme', isDark.value ? 'dark' : 'light')
    applyTheme()
  }

  const applyTheme = () => {
    if (isDark.value) {
      document.documentElement.setAttribute('data-theme', 'dark')
    } else {
      document.documentElement.setAttribute('data-theme', 'light')
    }
  }

  return {
    isDark,
    initTheme,
    toggleTheme
  }
})
