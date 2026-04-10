import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('career-platform-access-token') || '')
  const user = ref(JSON.parse(localStorage.getItem('career-platform-user') || 'null'))

  const isLoggedIn = computed(() => !!token.value && !!user.value)

  function setAuth(newToken, newUser) {
    token.value = newToken
    user.value = newUser
    if (newToken) {
      localStorage.setItem('career-platform-access-token', newToken)
      localStorage.setItem('career-platform-user', JSON.stringify(newUser))
    } else {
      localStorage.removeItem('career-platform-access-token')
      localStorage.removeItem('career-platform-user')
    }
  }

  function logout() {
    setAuth('', null)
  }

  return {
    token,
    user,
    isLoggedIn,
    setAuth,
    logout
  }
})
