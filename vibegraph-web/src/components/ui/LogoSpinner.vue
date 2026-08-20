<script setup lang="ts">
/**
 * LogoSpinner - brand loading animation drawn on a canvas.
 *
 * Adapted from the design asset `loading_spinner.html`: the VibeGraph logo as a
 * tiny living graph (9 nodes + 12 curved gradient links) that "breathes" and
 * oscillates while analysis runs. Used by the import modal, background import
 * toasts, the analyzing project card and the graph loading overlay.
 *
 * Performance / a11y behavior:
 *   - devicePixelRatio-aware so the logo stays crisp on HiDPI screens.
 *   - Pauses the rAF loop while the tab is hidden (document.visibilitychange).
 *   - Honors `prefers-reduced-motion`: renders one static frame, no animation.
 *   - Cancels the loop and detaches listeners on unmount.
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    /** Rendered box edge in px (the logo scales to fill it). */
    size?: number
    /** Optional visible caption under the logo (e.g. "Indexing…"). */
    label?: string
  }>(),
  { size: 160, label: '' },
)

const canvasRef = ref<HTMLCanvasElement | null>(null)

// Logo geometry ported from the design asset: coordinates are relative to the
// center of a 240px design box; every length scales by `k = size / 240`.
const NODES = [
  { id: 0, x: -70, y: -60, r: 22, color: '#f59e0b' }, // amber — left V peak
  { id: 1, x: -35, y: -10, r: 12, color: '#4338ca' },
  { id: 2, x: -20, y: -25, r: 10, color: '#2563eb' },
  { id: 3, x: 5, y: 15, r: 16, color: '#3b82f6' },
  { id: 4, x: 25, y: 5, r: 14, color: '#0ea5e9' },
  { id: 5, x: -5, y: 60, r: 18, color: '#2563eb' }, // V bottom
  { id: 6, x: 60, y: -80, r: 20, color: '#0ea5e9' }, // right V peak
  { id: 7, x: 30, y: -35, r: 14, color: '#4338ca' },
  { id: 8, x: 65, y: -30, r: 12, color: '#06b6d4' },
] as const

const LINKS: ReadonlyArray<readonly [number, number]> = [
  [0, 1],
  [0, 2],
  [1, 3],
  [1, 5],
  [2, 3],
  [3, 4],
  [4, 5],
  [3, 7],
  [4, 6],
  [5, 6],
  [7, 6],
  [7, 8],
  [8, 6],
]

interface LiveNode {
  id: number
  baseX: number
  baseY: number
  r: number
  color: string
  phaseX: number
  phaseY: number
  ampX: number
  ampY: number
  speedX: number
  speedY: number
  currentX: number
  currentY: number
}

let nodes: LiveNode[] = []
let frame = 0
let rafId = 0
let running = false
let reducedMotion = false

function initNodes(): void {
  nodes = NODES.map((data) => ({
    id: data.id,
    baseX: data.x,
    baseY: data.y,
    r: data.r,
    color: data.color,
    phaseX: Math.random() * Math.PI * 2,
    phaseY: Math.random() * Math.PI * 2,
    ampX: Math.random() * 8 + 4,
    ampY: Math.random() * 8 + 4,
    speedX: (Math.random() * 0.02 + 0.01) * (Math.random() > 0.5 ? 1 : -1),
    speedY: (Math.random() * 0.02 + 0.01) * (Math.random() > 0.5 ? 1 : -1),
    currentX: data.x,
    currentY: data.y,
  }))
}

function darken(color: string, amount: number): string {
  const hex = color.startsWith('#') ? color.slice(1) : color
  const num = parseInt(hex, 16)
  const channel = (value: number) => Math.min(255, Math.max(0, value + amount))
  const r = channel(num >> 16)
  const g = channel((num >> 8) & 0xff)
  const b = channel(num & 0xff)
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`
}

function drawFrame(ctx: CanvasRenderingContext2D, size: number): void {
  const k = size / 240
  const center = size / 2
  ctx.clearRect(0, 0, size, size)

  // Curved gradient links between the nodes.
  ctx.lineWidth = 3 * k
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  for (const [sourceId, targetId] of LINKS) {
    const source = nodes[sourceId]
    const target = nodes[targetId]
    if (!source || !target) continue
    const sX = center + source.currentX * k
    const sY = center + source.currentY * k
    const tX = center + target.currentX * k
    const tY = center + target.currentY * k

    const midX = (sX + tX) / 2
    const midY = (sY + tY) / 2
    const dx = tX - sX
    const dy = tY - sY
    const dist = Math.sqrt(dx * dx + dy * dy) || 1
    const curveForce = ((source.id + target.id) % 3 === 0 ? 15 : -15) * k
    const cpX = midX + (-dy / dist) * curveForce
    const cpY = midY + (dx / dist) * curveForce

    const gradient = ctx.createLinearGradient(sX, sY, tX, tY)
    gradient.addColorStop(0, source.color)
    gradient.addColorStop(1, target.color)

    ctx.beginPath()
    ctx.moveTo(sX, sY)
    ctx.quadraticCurveTo(cpX, cpY, tX, tY)
    ctx.strokeStyle = gradient
    ctx.globalAlpha = 0.6
    ctx.stroke()
    ctx.globalAlpha = 1
  }

  // Nodes with a soft glow and a light 3D radial gradient.
  for (const node of nodes) {
    const x = center + node.currentX * k
    const y = center + node.currentY * k
    const r = node.r * k

    ctx.beginPath()
    ctx.arc(x, y, r + 5 * k, 0, Math.PI * 2)
    ctx.fillStyle = node.color
    ctx.globalAlpha = 0.2
    ctx.fill()
    ctx.globalAlpha = 1

    const gradient = ctx.createRadialGradient(x - r / 3, y - r / 3, r / 4, x, y, r)
    gradient.addColorStop(0, '#ffffff')
    gradient.addColorStop(0.3, node.color)
    gradient.addColorStop(1, darken(node.color, -20))
    ctx.beginPath()
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fillStyle = gradient
    ctx.fill()
  }
}

function stepNodes(): void {
  frame += 1
  // Shared "breathing" scale so the whole mark gently expands/contracts.
  const breathScale = 1 + Math.sin(frame * 0.05) * 0.05
  for (const node of nodes) {
    node.phaseX += node.speedX
    node.phaseY += node.speedY
    node.currentX = node.baseX * breathScale + Math.sin(node.phaseX) * node.ampX
    node.currentY = node.baseY * breathScale + Math.cos(node.phaseY) * node.ampY
  }
}

function renderStatic(): void {
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx) return
  const dpr = window.devicePixelRatio || 1
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  drawFrame(ctx, props.size)
}

function loop(): void {
  if (!running) return
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (canvas && ctx) {
    stepNodes()
    drawFrame(ctx, props.size)
  }
  rafId = window.requestAnimationFrame(loop)
}

function start(): void {
  const canvas = canvasRef.value
  if (!canvas || running) return
  const dpr = window.devicePixelRatio || 1
  canvas.width = Math.max(1, Math.round(props.size * dpr))
  canvas.height = Math.max(1, Math.round(props.size * dpr))
  const ctx = canvas.getContext('2d')
  if (ctx) ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

  if (reducedMotion) {
    renderStatic()
    return
  }
  running = true
  rafId = window.requestAnimationFrame(loop)
}

function stop(): void {
  running = false
  if (rafId) {
    window.cancelAnimationFrame(rafId)
    rafId = 0
  }
}

function onVisibility(): void {
  if (document.hidden || reducedMotion) {
    stop()
  } else {
    start()
  }
}

onMounted(() => {
  initNodes()
  reducedMotion =
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  start()
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  stop()
  document.removeEventListener('visibilitychange', onVisibility)
})

// Re-render a static frame if the size changes while reduced motion is active.
watch(
  () => props.size,
  () => {
    if (reducedMotion) renderStatic()
  },
)
</script>

<template>
  <span class="logo-spinner" :style="{ width: `${size}px`, height: `${size}px` }">
    <canvas ref="canvasRef" aria-hidden="true"></canvas>
    <span v-if="label" class="logo-spinner__label">{{ label }}</span>
  </span>
</template>

<style scoped>
.logo-spinner {
  position: relative;
  display: inline-block;
  flex-shrink: 0;
}
.logo-spinner canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.logo-spinner__label {
  position: absolute;
  inset-inline: 0;
  bottom: -1.4rem;
  text-align: center;
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  animation: logo-spinner-blink 1.5s infinite;
}
@keyframes logo-spinner-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
@media (prefers-reduced-motion: reduce) {
  .logo-spinner__label {
    animation: none;
  }
}
</style>
