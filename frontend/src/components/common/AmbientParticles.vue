<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvasRef = ref(null)

let ctx = null
let animationFrameId = 0
let resizeTimer = 0
let themeObserver = null
let particles = []
let width = 0
let height = 0
let dpr = 1

function createParticle(palette) {
  const radius = Math.random() * 2.8 + 1.6
  return {
    x: Math.random() * width,
    y: Math.random() * height,
    vx: (Math.random() - 0.5) * 0.12,
    vy: (Math.random() - 0.5) * 0.12,
    radius,
    color: palette[Math.floor(Math.random() * palette.length)],
    alpha: Math.random() * 0.35 + 0.18
  }
}

function getPalette() {
  const styles = getComputedStyle(document.documentElement)
  return [
    styles.getPropertyValue('--c-bg-dot-blue').trim() || 'rgba(0, 89, 199, 0.24)',
    styles.getPropertyValue('--c-bg-dot-cyan').trim() || 'rgba(74, 183, 255, 0.22)',
    styles.getPropertyValue('--c-bg-dot-gold').trim() || 'rgba(255, 190, 72, 0.22)',
    styles.getPropertyValue('--c-bg-dot-rose').trim() || 'rgba(255, 124, 124, 0.18)'
  ]
}

function setCanvasSize() {
  const canvas = canvasRef.value
  if (!canvas) return

  dpr = Math.min(window.devicePixelRatio || 1, 2)
  width = window.innerWidth
  height = window.innerHeight

  canvas.width = Math.round(width * dpr)
  canvas.height = Math.round(height * dpr)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

function seedParticles() {
  const palette = getPalette()
  const particleCount = Math.max(14, Math.min(30, Math.round((width * height) / 70000)))
  particles = Array.from({ length: particleCount }, () => createParticle(palette))
}

function draw() {
  if (!ctx) return

  ctx.clearRect(0, 0, width, height)

  for (const particle of particles) {
    particle.x += particle.vx
    particle.y += particle.vy

    if (particle.x < -12) particle.x = width + 12
    if (particle.x > width + 12) particle.x = -12
    if (particle.y < -12) particle.y = height + 12
    if (particle.y > height + 12) particle.y = -12

    ctx.globalAlpha = particle.alpha
    ctx.beginPath()
    ctx.fillStyle = particle.color
    ctx.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.globalAlpha = 1
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
  themeObserver?.disconnect()
})
</script>

<template>
  <canvas ref="canvasRef" class="ambient-particles" aria-hidden="true"></canvas>
</template>

<style scoped>
.ambient-particles {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 0.95;
}
</style>
