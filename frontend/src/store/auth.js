import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchAuthProfile, invalidateApiCache } from '../api'

// localStorage 键名约定（不要改第一项，router 和其他页面都读这个 key）
const TOKEN_KEY = 'careerPlatform-access-token'
const REFRESH_KEY = 'careerPlatform-refresh-token'
const USER_KEY = 'careerPlatform-user'
const EXPIRES_KEY = 'careerPlatform-access-expires'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref(localStorage.getItem(REFRESH_KEY) || '')
  const expiresAt = ref(Number(localStorage.getItem(EXPIRES_KEY) || 0))
  const user = ref(readStoredUser())
  const initialized = ref(false)

  const isLoggedIn = computed(() => !!token.value && !!user.value)

  // 仅写 accessToken + user（兼容旧代码路径）
  function setAuth(newToken, newUser) {
    token.value = newToken
    user.value = newUser
    if (newToken) {
      localStorage.setItem(TOKEN_KEY, newToken)
      localStorage.setItem(USER_KEY, JSON.stringify(newUser))
    } else {
      clearPersistedAuth()
    }
  }

  // 登录成功后的统一入口：把后端 /auth/login 返回的整包（含 refreshToken / expiresIn）都存起来
  function setAuthSession(session) {
    const {
      accessToken = '',
      refreshToken: newRefresh = '',
      expiresIn = 0,
      user: newUser = null
    } = session || {}

    token.value = accessToken
    refreshToken.value = newRefresh
    user.value = newUser
    expiresAt.value = accessToken && expiresIn
      ? Date.now() + Number(expiresIn) * 1000
      : 0

    if (accessToken) {
      localStorage.setItem(TOKEN_KEY, accessToken)
      if (newUser) {
        localStorage.setItem(USER_KEY, JSON.stringify(newUser))
      }
      if (newRefresh) {
        localStorage.setItem(REFRESH_KEY, newRefresh)
      } else {
        localStorage.removeItem(REFRESH_KEY)
      }
      if (expiresAt.value) {
        localStorage.setItem(EXPIRES_KEY, String(expiresAt.value))
      } else {
        localStorage.removeItem(EXPIRES_KEY)
      }
    } else {
      clearPersistedAuth()
    }
  }

  function logout() {
    token.value = ''
    refreshToken.value = ''
    expiresAt.value = 0
    user.value = null
    clearPersistedAuth()
    // 清掉 /auth/profile 等接口的内存缓存，避免旧用户资料漏到下一个账号
    invalidateApiCache('/auth/')
  }

  function clearPersistedAuth() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(EXPIRES_KEY)
  }

  async function syncProfile() {
    if (!token.value) {
      initialized.value = true
      return null
    }

    try {
      const profile = await fetchAuthProfile(token.value)
      user.value = {
        ...(user.value || {}),
        ...profile
      }
      localStorage.setItem(USER_KEY, JSON.stringify(user.value))
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
    refreshToken,
    expiresAt,
    user,
    initialized,
    isLoggedIn,
    setAuth,
    setAuthSession,
    logout,
    syncProfile
  }
})

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}
