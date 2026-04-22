import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  // Check local storage or system preference
  const isDark = ref(false)
  
  const initTheme = () => {
    const saved = localStorage.getItem('careerPlatform-theme')
    if (saved) {
      isDark.value = saved === 'dark'
    } else {
      isDark.value = false
    }
    applyTheme()
  }

  const toggleTheme = () => {
    isDark.value = !isDark.value
    localStorage.setItem('careerPlatform-theme', isDark.value ? 'dark' : 'light')
    applyTheme()
  }

  const applyTheme = () => {
    const theme = isDark.value ? 'dark' : 'light'
    document.documentElement.setAttribute('data-theme', theme)
    document.documentElement.style.colorScheme = theme
  }

  return {
    isDark,
    initTheme,
    toggleTheme
  }
})
