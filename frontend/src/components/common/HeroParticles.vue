<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvasRef = ref(null)

let ctx = null
let animationFrameId = 0
let resizeTimer = 0
let themeObserver = null
let resizeObserver = null
let particles = []
let width = 0
let height = 0
let dpr = 1

function createParticle(palette) {
  const radius = Math.random() * 5.2 + 2.8
  return {
    x: Math.random() * width,
    y: Math.random() * height,
    vx: (Math.random() - 0.5) * 0.22,
    vy: (Math.random() - 0.5) * 0.18,
    radius,
    color: palette[Math.floor(Math.random() * palette.length)],
    alpha: Math.random() * 0.24 + 0.28
  }
}

function getPalette() {
  const styles = getComputedStyle(document.documentElement)
  return [
    styles.getPropertyValue('--c-bg-dot-blue').trim() || 'rgba(0, 89, 199, 0.28)',
    styles.getPropertyValue('--c-bg-dot-cyan').trim() || 'rgba(74, 183, 255, 0.26)',
    styles.getPropertyValue('--c-bg-dot-gold').trim() || 'rgba(255, 190, 72, 0.24)',
    styles.getPropertyValue('--c-bg-dot-rose').trim() || 'rgba(255, 124, 124, 0.22)'
  ]
}

function setCanvasSize() {
  const canvas = canvasRef.value
  const host = canvas?.parentElement
  if (!canvas || !host) return

  const rect = host.getBoundingClientRect()
  dpr = Math.min(window.devicePixelRatio || 1, 2)
  width = Math.max(Math.round(rect.width), 1)
  height = Math.max(Math.round(rect.height), 1)

  canvas.width = Math.round(width * dpr)
  canvas.height = Math.round(height * dpr)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

function seedParticles() {
  const palette = getPalette()
  const particleCount = Math.max(26, Math.min(46, Math.round((width * height) / 24000)))
  particles = Array.from({ length: particleCount }, () => createParticle(palette))
}

function draw() {
  if (!ctx) return

  ctx.clearRect(0, 0, width, height)

  for (const particle of particles) {
    particle.x += particle.vx
    particle.y += particle.vy

    if (particle.x < -16) particle.x = width + 16
    if (particle.x > width + 16) particle.x = -16
    if (particle.y < -16) particle.y = height + 16
    if (particle.y > height + 16) particle.y = -16

    ctx.shadowBlur = 18
    ctx.shadowColor = particle.color
    ctx.globalAlpha = particle.alpha
    ctx.beginPath()
    ctx.fillStyle = particle.color
    ctx.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.globalAlpha = 1
  ctx.shadowBlur = 0
  animationFrameId = window.requestAnimationFrame(draw)
}

function resetScene() {
  setCanvasSize()
  seedParticles()
}

function handleResize() {
  window.clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(() => {
    resetScene()
  }, 80)
}

onMounted(() => {
  resetScene()
  draw()

  window.addEventListener('resize', handleResize)

  resizeObserver = new ResizeObserver(() => {
    handleResize()
  })
  if (canvasRef.value?.parentElement) {
    resizeObserver.observe(canvasRef.value.parentElement)
  }

  themeObserver = new MutationObserver(() => {
    seedParticles()
  })

  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  })
})

onBeforeUnmount(() => {
  window.cancelAnimationFrame(animationFrameId)
  window.clearTimeout(resizeTimer)
  window.removeEventListener('resize', handleResize)
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
})
</script>

<template>
  <canvas ref="canvasRef" class="hero-particles" aria-hidden="true"></canvas>
</template>

<style scoped>
.hero-particles {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  opacity: 0.96;
}
</style>
