import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendOrigin = env.VITE_BACKEND_ORIGIN || 'http://localhost:8080'
  const algorithmOrigin = env.VITE_ALGORITHM_ORIGIN || 'http://localhost:8000'

  return {
    plugins: [vue()],
    test: {
      globals: true,
      environment: 'jsdom'
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: backendOrigin,
          changeOrigin: true
        },
        '/algorithm': {
          target: algorithmOrigin,
          changeOrigin: true
        }
      }
    },
    build: {
      chunkSizeWarningLimit: 900,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return
            if (id.includes('echarts') || id.includes('vue-echarts') || id.includes('zrender')) {
              return 'vendor-echarts'
            }
            if (id.includes('/three/') || id.includes('fast-2d-poisson-disk-sampling')) {
              return 'vendor-three'
            }
            if (id.includes('highlight.js')) {
              return 'vendor-hljs'
            }
            if (id.includes('marked')) {
              return 'vendor-markdown'
            }
            if (id.includes('lucide-vue-next')) {
              return 'vendor-icons'
            }
            if (id.includes('vue-router') || id.includes('/pinia/') || id.includes('@vue')) {
              return 'vendor-vue'
            }
          }
        }
      }
    }
  }
})
