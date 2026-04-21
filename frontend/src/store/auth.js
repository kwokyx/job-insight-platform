import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchAuthProfile } from '../api'
import { normalizeRoleType } from '../utils/role'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('careerPlatform-access-token') || '')
  const user = ref(readStoredUser())
  const initialized = ref(false)

  const isLoggedIn = computed(() => !!token.value && !!user.value)

  function setAuth(newToken, newUser) {
    token.value = newToken
    user.value = normalizeUser(newUser)
    if (newToken) {
      localStorage.setItem('careerPlatform-access-token', newToken)
      localStorage.setItem('careerPlatform-user', JSON.stringify(user.value))
    } else {
      localStorage.removeItem('careerPlatform-access-token')
      localStorage.removeItem('careerPlatform-user')
    }
  }

  function logout() {
    setAuth('', null)
  }

  async function syncProfile() {
    if (!token.value) {
      initialized.value = true
      return null
    }

    try {
      const profile = await fetchAuthProfile(token.value)
      user.value = normalizeUser({
        ...(user.value || {}),
        ...profile
      })
      localStorage.setItem('careerPlatform-user', JSON.stringify(user.value))
      initialized.value = true
      return user.value
    } catch (error) {
      logout()
      initialized.value = true
      return null
    }
  }

  return {
    token,
    user,
    initialized,
    isLoggedIn,
    setAuth,
    logout,
    syncProfile
  }
})

function readStoredUser() {
  try {
    return normalizeUser(JSON.parse(localStorage.getItem('careerPlatform-user') || 'null'))
  } catch {
    localStorage.removeItem('careerPlatform-user')
    return null
  }
}

function normalizeUser(user) {
  if (!user || typeof user !== 'object') return user
  return {
    ...user,
    roleType: normalizeRoleType(user.roleType)
  }
}
