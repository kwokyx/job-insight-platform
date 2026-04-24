import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

import './assets/styles/base.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

app.mount('#app')

// Navigation icons use a self-hosted Material Symbols subset. Keep the
// ligature words ("dashboard", "insights"...) hidden until the font is
// ready so first paint never flashes raw icon names.
if (typeof document !== 'undefined') {
  if (document.fonts && document.fonts.load) {
    document.fonts
      .load('20px "Material Symbols Outlined"')
      .catch(() => {})
      .finally(() => document.documentElement.classList.add('ms-ready'))
  } else {
    document.documentElement.classList.add('ms-ready')
  }
}
