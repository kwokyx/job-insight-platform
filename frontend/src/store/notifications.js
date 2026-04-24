import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchNotifications, invalidateApiCache } from '../api'
import { useAuthStore } from './auth'

// 通知中心的全局状态：
// - unreadCount：顶部导航栏通知按钮的小红点判定
// - recent：顶部 hover 气泡里展示最近几条（最多 5 条），主列表仍由 ProfileView 负责
export const useNotificationsStore = defineStore('notifications', () => {
  const unreadCount = ref(0)
  const recent = ref([])
  const lastFetchedAt = ref(0)

  async function refresh({ force = false, withList = false } = {}) {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      unreadCount.value = 0
      recent.value = []
      return
    }
    // 只取计数时用 pageSize=1，走 hover 气泡才顺带拿前 5 条
    if (!force && Date.now() - lastFetchedAt.value < 1000) return
    if (force) invalidateApiCache('/notifications')
    try {
      const res = await fetchNotifications(auth.token, { page: 1, pageSize: withList ? 5 : 1 })
      unreadCount.value = res.unreadCount || 0
      if (withList) {
        // 显式按 createdAt 降序，保证 hover 气泡里最新消息永远在最上面；
        // 后端已返回降序，这里是兜底 + 防止任何调用方改顺序后破坏展示
        const list = Array.isArray(res.data) ? [...res.data] : []
        list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
        recent.value = list
      }
      lastFetchedAt.value = Date.now()
    } catch {
      // 静默失败：顶部通知入口不是关键路径
    }
  }

  function set(count) { unreadCount.value = Math.max(0, Number(count) || 0) }
  function decrement() { unreadCount.value = Math.max(0, unreadCount.value - 1) }
  function clear() { unreadCount.value = 0; recent.value = [] }

  return { unreadCount, recent, refresh, set, decrement, clear }
})
