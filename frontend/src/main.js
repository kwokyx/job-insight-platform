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

// Before Material Symbols finishes loading, a <span class="material-symbols-
// outlined">dashboard</span> renders the literal word "dashboard" using
// whatever system font is available — a brief visual flash on first paint.
// Flip a class on <html> as soon as the font is ready so CSS can reveal the
// icons only at that point. Safe fallback: if document.fonts isn't
// supported, reveal immediately.
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
