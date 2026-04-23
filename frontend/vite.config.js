import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// Split heaviest third-party libs into their own chunks so the browser
// can cache them across navigations. Before this, InsightsView pulled
// ECharts into its view chunk (~595 kB); after splitting, ECharts becomes
// a shared chunk cached once — second visits to any chart page are
// near-instant.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 后端地址可通过 .env.local 覆盖（见 .env.example）。默认打到本机 8080，
  // 适配两种常见场景：(a) 队友用 Docker 且 compose 已把 backend:8080 暴露到宿主；
  // (b) 本地直接 mvn spring-boot:run 起后端。
  const proxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'
  // 算法服务直连地址：简历解析 / 简历评分 / 技能演化等接口走 /algorithm，
  // 由 Python FastAPI 直接承载，默认监听 8000。若队友把算法服务挂在其它端口，
  // 可以在 .env.local 里覆盖 VITE_ALGO_PROXY_TARGET。
  const algoProxyTarget = env.VITE_ALGO_PROXY_TARGET || 'http://localhost:8000'
  const devPort = Number(env.VITE_DEV_PORT) || 5173

  return {
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: devPort,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true
        },
        '/algorithm': {
          target: algoProxyTarget,
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
