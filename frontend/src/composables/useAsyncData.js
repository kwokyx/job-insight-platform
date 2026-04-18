import { ref } from 'vue'

export function useAsyncData(apiFunc, initialData = null) {
  const data = ref(initialData)
  const isLoading = ref(false)
  const error = ref(null)

  const execute = async (...args) => {
    isLoading.value = true
    error.value = null
    try {
      const result = await apiFunc(...args)
      data.value = result
      return result
    } catch (err) {
      console.error('Async Data Error:', err)
      error.value = err.message || '获取数据失败'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  return {
    data,
    isLoading,
    error,
    execute
  }
}
