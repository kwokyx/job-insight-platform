<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  CircleGeometry,
  Color,
  DoubleSide,
  InstancedMesh,
  Matrix4,
  MeshBasicMaterial,
  PerspectiveCamera,
  Scene,
  WebGLRenderer
} from 'three'
import Poisson from 'fast-2d-poisson-disk-sampling'

// Three.js ambient particle field inspired by antigravity.google.
//
// Revised per real-DOM analysis of the original bundle:
//   - ~200 particles (not thousands)
//   - Rectangular confetti flakes (4×5 world units), not round sprites
//     — rendered as InstancedMesh of tiny PlaneGeometry with per-
//     instance rotation + color
//   - Three-color palette (#318bf7 / #bada4c / #e35058) via
//     InstancedBufferAttribute('instanceColor')
//   - No dominant motion direction. Particles live in a 500×500 field
//     (camera z=150 → real perspective sizeAttenuation), each one
//     orbits a base angle around origin with a gentle ring-radius
//     pulse (amp 0.175 × sin(t)) and slow self-rotation — the
//     "breathing GPGPU ring field" feel without actually writing a
//     compute shader
//   - Mouse parallax: mesh.position lerps by ±parallax toward the
//     cursor (Y stronger than X, matching the measured ratio)
//
// Canvas stays fixed inset:0, z-index:30, pointer-events:none so
// interactions aren't blocked.

const canvasRef = ref(null)

// Project's soft dot palette — the original CSS variables
// --c-bg-dot-blue / cyan / gold / rose. The RGB values are kept; the
// alpha part of the rgba() definitions is handled by the material's
// opacity below (Three.js InstancedMesh setColorAt doesn't carry alpha).
const PALETTE = [
  new Color('rgb(0, 89, 199)'),    // blue
  new Color('rgb(74, 183, 255)'),  // cyan
  new Color('rgb(255, 190, 72)'),  // gold
  new Color('rgb(255, 124, 124)')  // rose
]

const CONFIG = {
  count: 220,

  // Field footprint (matches the original's [-250, 250] square)
  fieldSize: 500,
  poissonMinDistance: 26,
  poissonTries: 12,

  // Z spread — with camera at z=200 this gives a visible near/far
  // depth fan via real perspective, but the spread is tight enough
  // that even near-camera flakes stay small on screen.
  zNear: 25,
  zFar: -90,

  // Dot radius in world units. With camera z=200 and FOV 55, 1 world
  // unit ≈ 2.6 CSS px at z=0, so radius 0.9 → ~5 CSS px dots at the
  // mid-depth plane; size attenuation + per-particle scale spread keeps
  // a visible size hierarchy.
  dotRadius: 0.9,
  dotSegments: 12,

  // Per-particle size multiplier for visible size layers.
  minScale: 0.45,
  maxScale: 1.7,

  // Orbit motion
  baseAngularSpeed: 0.08,   // radians / second
  angularJitter: 0.12,      // ± multiplier per particle
  ringAmp: 0.175,           // matches uRingRadius = sin(t) × 0.175
  ringFreq: 0.6,
  selfRotSpeed: 0.45,       // max radians / second each flake spins
  wobbleAmp: 1.4,           // small XY wobble amplitude
  wobbleFreq: 0.8,

  // Mouse parallax (measured ratios: Y ~0.44, X ~0.10)
  parallaxX: 22,
  parallaxY: 95,
  parallaxEase: 0.05,

  // Camera
  cameraFov: 55,
  cameraZ: 200
}

let renderer = null
let scene = null
let camera = null
let mesh = null
let geometry = null
let material = null

// Per-particle state (CPU side)
let baseRadius = null
let baseAngle = null
let angularSpeed = null
let baseZ = null
let baseScale = null
let phase = null
let rotOffset = null
let rotSpeed = null

const dummyMatrix = new Matrix4()
const dummyColor = new Color()

let animationFrameId = 0
let startTime = 0
let resizeTimer = 0

let mouseX = 0
let mouseY = 0
let offsetX = 0
let offsetY = 0

function seedField() {
  const count = CONFIG.count
  const half = CONFIG.fieldSize / 2

  // Poisson disk for XY gives the signature even-but-not-gridded
  // spacing (blue noise).
  const pds = new Poisson({
    shape: [CONFIG.fieldSize, CONFIG.fieldSize],
    minDistance: CONFIG.poissonMinDistance,
    maxDistance: CONFIG.poissonMinDistance * 2,
    tries: CONFIG.poissonTries
  })
  let samples = pds.fill()

  if (samples.length < count) {
    while (samples.length < count) {
      samples.push([Math.random() * CONFIG.fieldSize, Math.random() * CONFIG.fieldSize])
    }
  } else if (samples.length > count) {
    // Shuffle and trim so we don't bias corners
    for (let i = samples.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[samples[i], samples[j]] = [samples[j], samples[i]]
    }
    samples = samples.slice(0, count)
  }

  baseRadius = new Float32Array(count)
  baseAngle = new Float32Array(count)
  angularSpeed = new Float32Array(count)
  baseZ = new Float32Array(count)
  baseScale = new Float32Array(count)
  phase = new Float32Array(count)
  rotOffset = new Float32Array(count)
  rotSpeed = new Float32Array(count)

  // InstancedBufferAttribute for per-flake color
  const colorArray = new Float32Array(count * 3)

  for (let i = 0; i < count; i++) {
    const [sx, sy] = samples[i]
    const x = sx - half
    const y = sy - half

    baseRadius[i] = Math.hypot(x, y)
    baseAngle[i] = Math.atan2(y, x)
    angularSpeed[i] =
      CONFIG.baseAngularSpeed *
      (1 + (Math.random() - 0.5) * CONFIG.angularJitter * 2) *
      (Math.random() > 0.5 ? 1 : -1)
    baseZ[i] = CONFIG.zFar + (CONFIG.zNear - CONFIG.zFar) * Math.random()
    // Random scale bucket — bias toward smaller flakes (pow 1.6) so the
    // field feels sprinkled with a few prominent ones instead of all
    // being mid-sized.
    baseScale[i] = CONFIG.minScale + (CONFIG.maxScale - CONFIG.minScale) * Math.pow(Math.random(), 1.6)
    phase[i] = Math.random() * Math.PI * 2
    rotOffset[i] = Math.random() * Math.PI * 2
    rotSpeed[i] = (Math.random() - 0.5) * CONFIG.selfRotSpeed * 2

    const c = PALETTE[Math.floor(Math.random() * PALETTE.length)]
    colorArray[i * 3] = c.r
    colorArray[i * 3 + 1] = c.g
    colorArray[i * 3 + 2] = c.b
  }

  if (mesh) {
    scene.remove(mesh)
    mesh.geometry.dispose()
    mesh.material.dispose()
  }

  geometry = new CircleGeometry(CONFIG.dotRadius, CONFIG.dotSegments)

  // Opacity 0.35 roughly corresponds to the soft 0.18–0.24 alpha in
  // the original rgba() dot variables — keeps the background dreamy
  // without competing with page content.
  material = new MeshBasicMaterial({
    transparent: true,
    opacity: 0.35,
    side: DoubleSide,
    depthWrite: false
  })

  mesh = new InstancedMesh(geometry, material, count)
  mesh.instanceMatrix.setUsage(35048) // THREE.DynamicDrawUsage constant
  mesh.frustumCulled = false

  // Per-instance color attribute — the geometry gets an `instanceColor`
  // attribute, which THREE.InstancedMesh natively multiplies into the
  // material color when setColorAt is used. We'll use the helper.
  for (let i = 0; i < count; i++) {
    dummyColor.setRGB(
      colorArray[i * 3],
      colorArray[i * 3 + 1],
      colorArray[i * 3 + 2]
    )
    mesh.setColorAt(i, dummyColor)
  }
  if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true

  scene.add(mesh)
}

function initThree() {
  const canvas = canvasRef.value
  if (!canvas) return

  const width = window.innerWidth
  const height = window.innerHeight
  const dpr = Math.min(window.devicePixelRatio || 1, 2)

  renderer = new WebGLRenderer({ canvas, alpha: true, antialias: true })
  renderer.setPixelRatio(dpr)
  renderer.setSize(width, height, false)
  renderer.setClearColor(0x000000, 0)

  scene = new Scene()
  camera = new PerspectiveCamera(CONFIG.cameraFov, width / height, 0.1, 2000)
  camera.position.set(0, 0, CONFIG.cameraZ)

  seedField()
  startTime = performance.now()
}

function updateFrame(now) {
  if (!renderer || !mesh) return

  const t = (now - startTime) / 1000
  const count = CONFIG.count
  const ringFactor = 1 + Math.sin(t * CONFIG.ringFreq) * CONFIG.ringAmp

  for (let i = 0; i < count; i++) {
    // Radial orbit — angle advances with its per-particle speed, radius
    // pulses with the global ring-breath factor.
    const angle = baseAngle[i] + angularSpeed[i] * t
    const radius = baseRadius[i] * ringFactor

    // Small XY wobble so orbits don't feel perfectly circular
    const wob = Math.sin(t * CONFIG.wobbleFreq + phase[i]) * CONFIG.wobbleAmp

    const x = Math.cos(angle) * radius + wob
    const y = Math.sin(angle) * radius + Math.cos(t * CONFIG.wobbleFreq * 0.7 + phase[i]) * CONFIG.wobbleAmp
    const z = baseZ[i]

    const rotation = rotOffset[i] + rotSpeed[i] * t
    const s = baseScale[i]

    // Compose matrix: scale → rotate around Z → translate
    const cos = Math.cos(rotation)
    const sin = Math.sin(rotation)
    const m = dummyMatrix.elements
    m[0] = cos * s;  m[1] = sin * s;  m[2] = 0;  m[3] = 0
    m[4] = -sin * s; m[5] = cos * s;  m[6] = 0;  m[7] = 0
    m[8] = 0;        m[9] = 0;        m[10] = s; m[11] = 0
    m[12] = x;       m[13] = y;       m[14] = z; m[15] = 1
    mesh.setMatrixAt(i, dummyMatrix)
  }

  mesh.instanceMatrix.needsUpdate = true

  // Mouse parallax: lerp the mesh position (Y stronger than X, matching
  // the measured 0.44 vs 0.10 ratio).
  const targetX = mouseX * CONFIG.parallaxX
  const targetY = mouseY * CONFIG.parallaxY
  offsetX += (targetX - offsetX) * CONFIG.parallaxEase
  offsetY += (targetY - offsetY) * CONFIG.parallaxEase
  mesh.position.set(offsetX, -offsetY, 0)

  renderer.render(scene, camera)
  animationFrameId = window.requestAnimationFrame(updateFrame)
}

function handleResize() {
  window.clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(() => {
    if (!renderer || !camera) return
    const width = window.innerWidth
    const height = window.innerHeight
    renderer.setSize(width, height, false)
    camera.aspect = width / height
    camera.updateProjectionMatrix()
  }, 80)
}

function handleMouseMove(e) {
  mouseX = e.clientX / window.innerWidth - 0.5
  mouseY = e.clientY / window.innerHeight - 0.5
}

onMounted(() => {
  initThree()
  animationFrameId = window.requestAnimationFrame(updateFrame)
  window.addEventListener('resize', handleResize)
  window.addEventListener('mousemove', handleMouseMove, { passive: true })
})

onBeforeUnmount(() => {
  window.cancelAnimationFrame(animationFrameId)
  window.clearTimeout(resizeTimer)
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('mousemove', handleMouseMove)

  if (mesh) {
    scene?.remove(mesh)
    mesh.geometry.dispose()
    mesh.material.dispose()
    mesh = null
  }
  renderer?.dispose()
  renderer = null
  scene = null
  camera = null
})
</script>

<template>
  <canvas ref="canvasRef" class="ambient-particles" aria-hidden="true"></canvas>
</template>

<style scoped>
.ambient-particles {
  position: fixed;
  inset: 0;
  z-index: 30;
  pointer-events: none;
  opacity: 1;
}
</style>
