import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Split heaviest third-party libs into their own chunks so the browser
// can cache them across navigations. Before this, InsightsView pulled
// ECharts into its view chunk (~595 kB); after splitting, ECharts becomes
// a shared chunk cached once — second visits to any chart page are
// near-instant.
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false
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
})
