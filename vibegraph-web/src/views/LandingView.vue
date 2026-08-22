<script setup lang="ts">
/**
 * LandingView - marketing / introduction page for VibeGraph.
 *
 * Optimized with premium interaction, guided auto-cursor tour, problem/solution sections,
 * quick start guides, dynamic world map, and symmetric bento grid cards.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import BrandMark from '@/components/ui/BrandMark.vue'
import LanguageSelector from '@/components/ui/LanguageSelector.vue'
import LogoTile from '@/components/ui/LogoTile.vue'
import { publicSiteCopy } from '@/content/publicSite'

// IDE / AI-agent logos
import logoAntigravity from '@/assets/images/ide/LogoAntigravity.jpg'
import logoClaude from '@/assets/images/ide/LogoClaudeAI.jpg'
import logoCursor from '@/assets/images/ide/LogoCursor.jpg'
import logoKiro from '@/assets/images/ide/LogoKiro.jpg'
import logoWindsurf from '@/assets/images/ide/LogoWindsurd.png'

// Tech-stack logos
import logoJava from '@/assets/images/stack/LogoJava.jpg'
import logoSpringBoot from '@/assets/images/stack/LogoSpringBoot.jpg'
import logoVue from '@/assets/images/stack/LogoVueJS.jpg'
import logoTypeScript from '@/assets/images/stack/LogoTypeScript.jpg'
import logoWebSocket from '@/assets/images/stack/LogoWebSocket.jpg'
import logoSigma from '@/assets/images/stack/SigmaJs.jpg'

type LogoItem = { src: string; label: string; tone?: 'dark' | 'light'; boost?: boolean }

const agents: LogoItem[] = [
  { src: logoClaude, label: 'Claude' },
  { src: logoCursor, label: 'Cursor', boost: true },
  { src: logoKiro, label: 'Kiro' },
  { src: logoWindsurf, label: 'Windsurf', boost: true },
  { src: logoAntigravity, label: 'Antigravity', boost: true },
]

const stack: LogoItem[] = [
  { src: logoJava, label: 'Java', tone: 'light' },
  { src: logoSpringBoot, label: 'Spring Boot' },
  { src: logoVue, label: 'Vue 3' },
  { src: logoTypeScript, label: 'TypeScript' },
  { src: logoSigma, label: 'Sigma.js' },
  { src: logoWebSocket, label: 'WebSocket' },
]

const scrolled = ref(false)
const activeSection = ref('')
const auth = useAuthStore()
const { locale, t } = useI18n({ useScope: 'global' })
const publicCopy = computed(
  () => publicSiteCopy[locale.value as 'en-US' | 'vi-VN'] ?? publicSiteCopy['en-US'],
)
const primaryRoute = computed(() => {
  if (!auth.isAuthenticated) return '/login'
  return auth.user?.role === 'ADMIN' ? '/admin' : '/dashboard'
})
const primaryLabel = computed(() =>
  auth.isAuthenticated ? publicCopy.value.actions.dashboard : publicCopy.value.actions.login,
)

function onScroll(): void {
  scrolled.value = window.scrollY > 12
}

// Stats
const stats = computed(() => [
  { value: 'Java', label: t('landing.stats.javaLabel') },
  { value: 'Neo4j', label: t('landing.stats.neo4jLabel') },
  { value: '18', label: t('landing.stats.toolsLabel') },
  { value: 'CLI + MCP', label: t('landing.stats.integrationLabel') },
])

// Features
const features = computed(() => [
  {
    tag: t('landing.features.cards.graph.tag'),
    title: t('landing.features.cards.graph.title'),
    body: t('landing.features.cards.graph.body'),
    accent: 'blue',
    span: 'wide',
  },
  {
    tag: t('landing.features.cards.impact.tag'),
    title: t('landing.features.cards.impact.title'),
    body: t('landing.features.cards.impact.body'),
    accent: 'green',
    span: 'tall',
  },
  {
    tag: t('landing.features.cards.realtime.tag'),
    title: t('landing.features.cards.realtime.title'),
    body: t('landing.features.cards.realtime.body'),
    accent: 'cyan',
    span: 'normal',
  },
  {
    tag: t('landing.features.cards.diagrams.tag'),
    title: t('landing.features.cards.diagrams.title'),
    body: t('landing.features.cards.diagrams.body'),
    accent: 'violet',
    span: 'normal',
  },
  {
    tag: t('landing.features.cards.import.tag'),
    title: t('landing.features.cards.import.title'),
    body: t('landing.features.cards.import.body'),
    accent: 'amber',
    span: 'wide', // will span full 3 columns thanks to css grid updates
  },
])

// Steps for "How it works"
const steps = computed(() => [
  {
    n: '01',
    title: t('landing.how.steps.import.title'),
    body: t('landing.how.steps.import.body'),
  },
  {
    n: '02',
    title: t('landing.how.steps.explore.title'),
    body: t('landing.how.steps.explore.body'),
  },
  {
    n: '03',
    title: t('landing.how.steps.analyze.title'),
    body: t('landing.how.steps.analyze.body'),
  },
])

// --- Interactive Graph State ---
interface GraphNode {
  id: string
  label: string
  type: string
  x: number
  y: number
  r: number
  color: string
}

interface GraphEdge {
  from: string
  to: string
}

const nodes: GraphNode[] = [
  {
    id: 'test',
    label: 'ProjectApiIT.java',
    type: 'test',
    x: 60,
    y: 130,
    r: 12,
    color: 'var(--vg-blue)',
  },
  {
    id: 'controller',
    label: 'ProjectController.java',
    type: 'controller',
    x: 160,
    y: 160,
    r: 14,
    color: 'var(--vg-cyan)',
  },
  {
    id: 'security',
    label: 'SecurityConfig.java',
    type: 'security',
    x: 90,
    y: 280,
    r: 12,
    color: 'var(--vg-danger)',
  },
  {
    id: 'service',
    label: 'ProjectServiceImpl.java',
    type: 'service',
    x: 260,
    y: 170,
    r: 16,
    color: 'var(--vg-violet)',
  },
  {
    id: 'repo',
    label: 'Neo4jGraphRepository.java',
    type: 'repository',
    x: 340,
    y: 250,
    r: 13,
    color: 'var(--vg-amber)',
  },
  {
    id: 'db',
    label: 'Neo4j graph',
    type: 'database',
    x: 350,
    y: 90,
    r: 10,
    color: 'var(--vg-green-bright)',
  },
  {
    id: 'util',
    label: 'AnalyzeServiceImpl.java',
    type: 'utility',
    x: 230,
    y: 310,
    r: 11,
    color: 'var(--vg-blue-bright)',
  },
]

const edges: GraphEdge[] = [
  { from: 'test', to: 'controller' },
  { from: 'security', to: 'controller' },
  { from: 'controller', to: 'service' },
  { from: 'service', to: 'repo' },
  { from: 'repo', to: 'db' },
  { from: 'service', to: 'util' },
  { from: 'util', to: 'repo' },
]

const selectedNode = ref<GraphNode | null>(null)
const activeImpactNodes = ref<string[]>([])
const hoverNode = ref<string | null>(null)
const isPropagating = ref(false)
// F-L1: self-rescheduling timers keep their handles so unmount can stop them;
// writing to refs of an unmounted component would otherwise keep the chain alive.
let isAlive = true
let typingTimer: ReturnType<typeof setTimeout> | null = null
let propagationTimer: ReturnType<typeof setTimeout> | null = null

function getNode(id: string): GraphNode {
  return nodes.find((n) => n.id === id)!
}

function isEdgeActive(edge: GraphEdge): boolean {
  return activeImpactNodes.value.includes(edge.from) && activeImpactNodes.value.includes(edge.to)
}

function isEdgeHighlighted(edge: GraphEdge): boolean {
  return hoverNode.value === edge.from || hoverNode.value === edge.to
}

function triggerImpact(nodeId: string) {
  if (isPropagating.value) return
  isPropagating.value = true

  const node = nodes.find((n) => n.id === nodeId)
  if (!node) return

  selectedNode.value = node
  activeImpactNodes.value = []

  const visited = new Set<string>()
  const queue: string[] = [nodeId]

  function step() {
    if (queue.length === 0) {
      isPropagating.value = false
      return
    }
    const current = queue.shift()!
    if (!visited.has(current)) {
      visited.add(current)
      activeImpactNodes.value.push(current)

      // Find neighbors
      edges.forEach((edge) => {
        if (edge.to === current && !visited.has(edge.from)) {
          queue.push(edge.from)
        }
        if (edge.from === current && !visited.has(edge.to)) {
          queue.push(edge.to)
        }
      })

      if (propagationTimer) clearTimeout(propagationTimer)
      propagationTimer = setTimeout(step, 180)
    } else {
      step()
    }
  }
  step()
}

function impactLevel(count: number): string {
  if (count >= 5) return t('landing.graph.risk.critical')
  if (count >= 3) return t('landing.graph.risk.high')
  return t('landing.graph.risk.low')
}

function impactClass(count: number): string {
  if (count >= 5) return 'telemetry-value--critical'
  if (count >= 3) return 'telemetry-value--high'
  return 'telemetry-value--safe'
}

// --- Interactive Terminal State ---
const activeTerminalTab = ref<'impact' | 'context' | 'plan'>('impact')
const terminalInput = ref('')
const terminalOutput = ref('')
const terminalTyping = ref(false)

const commandsData = {
  impact: {
    command:
      'get_impact_analysis({ projectId: "<selected-project>", nodeQuery: "<symbol>", depth: 2 })',
    output: `{
  "source": "selected project graph",
  "result": "Returned by VibeGraph at call time",
  "note": "Affected nodes, flows and risk depend on the analyzed project"
}`,
  },
  context: {
    command: 'get_class_context({ projectId: "<selected-project>", classQuery: "<class>" })',
    output: `{
  "source": "selected project graph",
  "result": "Class context returned by VibeGraph at call time",
  "note": "Methods and relationships depend on the selected project"
}`,
  },
  plan: {
    command:
      'plan_code_change({ projectId: "<selected-project>", changeRequest: "<change>" })',
    output: `{
  "source": "selected project graph",
  "result": "Plan returned by VibeGraph at call time",
  "note": "Candidate files and sequence depend on the requested change"
}`,
  },
}

function loadTerminalCommand(tabKey: 'impact' | 'context' | 'plan') {
  if (terminalTyping.value) return
  activeTerminalTab.value = tabKey
  terminalTyping.value = true
  terminalInput.value = ''
  terminalOutput.value = ''

  const cmdText = commandsData[tabKey].command
  let i = 0

  function typeCmd() {
    if (!isAlive) return
    if (i < cmdText.length) {
      terminalInput.value += cmdText[i]
      i++
      if (typingTimer) clearTimeout(typingTimer)
      typingTimer = setTimeout(typeCmd, 20)
    } else {
      if (typingTimer) clearTimeout(typingTimer)
      typingTimer = setTimeout(() => {
        terminalTyping.value = false
        terminalOutput.value = commandsData[tabKey].output
      }, 250)
    }
  }

  typeCmd()
}

// --- Stepper State ---
const activeStep = ref(0)
let stepInterval: ReturnType<typeof setInterval> | null = null

function startStepTimer() {
  stepInterval = setInterval(() => {
    activeStep.value = (activeStep.value + 1) % steps.value.length
  }, 7000)
}

function selectStep(index: number) {
  activeStep.value = index
  if (stepInterval) {
    clearInterval(stepInterval)
    startStepTimer()
  }
}

// --- Bento Grid Spotlight ---
function onMouseMoveBento(e: MouseEvent, index: number) {
  const card = document.getElementById(`bento-card-${index}`)
  if (!card) return
  const rect = card.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  card.style.setProperty('--mouse-x', `${x}px`)
  card.style.setProperty('--mouse-y', `${y}px`)
}

// --- User Guide Tabs Data ---
const guideTabs = computed(() => [
  { step: '01', title: t('landing.guide.tabs.run') },
  { step: '02', title: t('landing.guide.tabs.import') },
  { step: '03', title: t('landing.guide.tabs.agent') },
])
const activeGuideTab = ref(0)

// --- Autonomous Cursor Guided Tour ---
const virtualCursor = ref({ x: 100, y: 100, clicking: false })
const autoTourActive = ref(true)
let tourTimeout: ReturnType<typeof setTimeout> | null = null
let landingMotionContext: { revert: () => void } | null = null

async function setupMotionEffects(): Promise<void> {
  if (typeof window === 'undefined' || !window.matchMedia) return
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

  const [{ default: gsap }, { ScrollTrigger }] = await Promise.all([
    import('gsap'),
    import('gsap/ScrollTrigger'),
  ])
  if (!isAlive) return
  gsap.registerPlugin(ScrollTrigger)

  landingMotionContext = gsap.context(() => {
    gsap.utils.toArray<HTMLElement>('main > .section').forEach((section) => {
      const children = Array.from(section.children) as HTMLElement[]
      if (children.length === 0) return

      gsap.fromTo(
        children,
        { autoAlpha: 0, y: 18 },
        {
          autoAlpha: 1,
          y: 0,
          duration: 0.48,
          stagger: 0.07,
          ease: 'power2.out',
          scrollTrigger: {
            trigger: section,
            start: 'top 84%',
            once: true,
          },
        },
      )
    })

    gsap.to('.hero__visual', {
      yPercent: 4,
      ease: 'none',
      scrollTrigger: {
        trigger: '.hero',
        start: 'top top',
        end: 'bottom top',
        scrub: 0.6,
      },
    })

    const sectionIds = ['goals', 'features', 'how', 'guide']
    sectionIds.forEach((id) => {
      const section = document.getElementById(id)
      if (!section) return
      ScrollTrigger.create({
        trigger: section,
        start: 'top 45%',
        end: 'bottom 45%',
        onEnter: () => {
          activeSection.value = id
        },
        onEnterBack: () => {
          activeSection.value = id
        },
      })
    })
  })
}

async function moveVirtualCursor(targetX: number, targetY: number, duration = 1200) {
  const startX = virtualCursor.value.x
  const startY = virtualCursor.value.y
  const startTime = performance.now()

  return new Promise<void>((resolve) => {
    function update(time: number) {
      if (!autoTourActive.value) return resolve()
      const elapsed = time - startTime
      const progress = Math.min(elapsed / duration, 1)
      const ease =
        progress < 0.5 ? 4 * progress * progress * progress : 1 - Math.pow(-2 * progress + 2, 3) / 2

      virtualCursor.value.x = startX + (targetX - startX) * ease
      virtualCursor.value.y = startY + (targetY - startY) * ease

      if (progress < 1) {
        requestAnimationFrame(update)
      } else {
        resolve()
      }
    }
    requestAnimationFrame(update)
  })
}

async function simulateVirtualClick() {
  virtualCursor.value.clicking = true
  await new Promise((r) => setTimeout(r, 200))
  virtualCursor.value.clicking = false
}

let currentTourStep = 0
const tourTargets = [
  { id: 'hero-node-service', action: () => triggerImpact('service') },
  { id: 'hero-node-test', action: () => triggerImpact('test') },
  { id: 'terminal-tab-context', action: () => loadTerminalCommand('context') },
  { id: 'step-control-2', action: () => selectStep(2) },
  { id: 'hero-node-repo', action: () => triggerImpact('repo') },
]

async function playTourStep() {
  const target = tourTargets[currentTourStep]
  if (!target) return
  const el = document.getElementById(target.id)
  if (el) {
    const rect = el.getBoundingClientRect()
    // Align relative to viewport since cursor is fixed-positioned
    const targetX = rect.left + rect.width / 2
    const targetY = rect.top + rect.height / 2

    await moveVirtualCursor(targetX, targetY, 1500)

    if (!autoTourActive.value) return

    await simulateVirtualClick()
    target.action()
  }

  currentTourStep = (currentTourStep + 1) % tourTargets.length
  if (tourTimeout) clearTimeout(tourTimeout)
  tourTimeout = setTimeout(playTourStep, 4500)
}

function stopAutoTour() {
  if (!autoTourActive.value) return
  autoTourActive.value = false
  if (tourTimeout) clearTimeout(tourTimeout)
}

onMounted(() => {
  void auth.refreshPublicSession()
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()

  // Set default interactive graph select
  triggerImpact('service')

  // Start terminal typing
  loadTerminalCommand('impact')

  // Start stepper timer
  startStepTimer()

  // Hook auto-guided tour cursor event listener
  window.addEventListener('scroll', stopAutoTour, { once: true })
  window.addEventListener('mousemove', stopAutoTour, { once: true })
  window.addEventListener('mousedown', stopAutoTour, { once: true })
  window.addEventListener('keydown', stopAutoTour, { once: true })

  // Start tour after a delay
  tourTimeout = setTimeout(playTourStep, 4000)

  void setupMotionEffects()
})

onBeforeUnmount(() => {
  // F-L1: stop every self-rescheduling timer; without the handles the typing
  // and propagation chains kept writing to refs after unmount.
  isAlive = false
  if (typingTimer) clearTimeout(typingTimer)
  if (propagationTimer) clearTimeout(propagationTimer)
  window.removeEventListener('scroll', onScroll)
  // F-L2: the four tour listeners are `{ once: true }` so each self-removes after
  // its first fire — but until that fire they outlive unmount; remove them here.
  window.removeEventListener('scroll', stopAutoTour)
  window.removeEventListener('mousemove', stopAutoTour)
  window.removeEventListener('mousedown', stopAutoTour)
  window.removeEventListener('keydown', stopAutoTour)
  if (stepInterval) clearInterval(stepInterval)
  landingMotionContext?.revert()
  landingMotionContext = null
  stopAutoTour()
})
</script>

<template>
  <div class="lp">
    <!-- backdrop atmosphere -->
    <div class="lp__aurora" aria-hidden="true"></div>
    <div class="lp__grid-overlay" aria-hidden="true"></div>
    <div class="lp__glow-particles" aria-hidden="true">
      <span class="particle particle--1"></span>
      <span class="particle particle--2"></span>
      <span class="particle particle--3"></span>
    </div>

    <!-- Autonomous Guided Tour Virtual Cursor -->
    <div
      v-if="autoTourActive"
      class="virtual-cursor"
      :style="{ left: `${virtualCursor.x}px`, top: `${virtualCursor.y}px` }"
      aria-hidden="true"
    >
      <svg width="22" height="22" viewBox="0 0 20 20" fill="none">
        <path
          d="M2 2L9 18L12 11L19 9L2 2Z"
          fill="var(--vg-cyan)"
          stroke="#fff"
          stroke-width="1.8"
        />
      </svg>
      <div v-if="virtualCursor.clicking" class="click-ripple"></div>
      <span class="virtual-cursor-tag">{{ t('landing.tour.label') }}</span>
    </div>

    <!-- ── Nav ── -->
    <header class="lp-nav" :class="{ 'lp-nav--scrolled': scrolled }">
      <div class="lp-nav__inner">
        <BrandMark :size="30" />
        <nav class="lp-nav__links" aria-label="Primary">
          <a href="#goals" :class="{ 'lp-nav__link--active': activeSection === 'goals' }">{{ t('landing.nav.goals') }}</a>
          <a href="#features" :class="{ 'lp-nav__link--active': activeSection === 'features' }">{{ t('landing.nav.features') }}</a>
          <a href="#how" :class="{ 'lp-nav__link--active': activeSection === 'how' }">{{ t('landing.nav.howItWorks') }}</a>
          <a href="#guide" :class="{ 'lp-nav__link--active': activeSection === 'guide' }">{{ t('landing.nav.guide') }}</a>
          <RouterLink to="/docs">{{ publicCopy.nav.docs }}</RouterLink>
        </nav>
        <div class="lp-nav__actions">
          <LanguageSelector />
          <RouterLink class="btn btn--primary btn--sm" :to="primaryRoute">
            {{ primaryLabel }}
            <span class="btn__arrow" aria-hidden="true">→</span>
          </RouterLink>
        </div>
      </div>
    </header>

    <main>
      <!-- ── Hero ── -->
      <section class="hero">
        <div class="hero__copy">
          <span class="pill">
            <span class="pill__dot" aria-hidden="true"></span>
            {{ t('landing.hero.pill') }}
          </span>
          <h1 class="hero__title">
            {{ t('landing.hero.titleLead') }}
            <span class="hero__title-grad">{{ t('landing.hero.titleHighlight') }}</span>
          </h1>
          <p class="hero__lede">{{ t('landing.hero.description') }}</p>
          <div class="hero__cta">
            <RouterLink class="btn btn--primary btn--lg" :to="primaryRoute">
              {{ primaryLabel }}
              <span class="btn__arrow" aria-hidden="true">→</span>
            </RouterLink>
            <a class="btn btn--ghost btn--lg" href="#how">{{ t('landing.actions.seeHow') }}</a>
          </div>
          <ul class="hero__stats">
            <li v-for="s in stats" :key="s.label">
              <span class="hero__stat-value">{{ s.value }}</span>
              <span class="hero__stat-label">{{ s.label }}</span>
            </li>
          </ul>
        </div>

        <!-- Interactive Animated Graph Motif -->
        <div class="hero__visual" aria-hidden="true">
          <div class="orb"></div>

          <div class="interactive-graph-card">
            <div class="graph-header">
              <span class="graph-status-dot"></span>
              <span class="graph-header-text">{{ t('landing.graph.header') }}</span>
            </div>

            <svg class="graphmotif" viewBox="0 0 420 420" fill="none">
              <defs>
                <linearGradient
                  id="lp-edge"
                  x1="0"
                  y1="0"
                  x2="420"
                  y2="420"
                  gradientUnits="userSpaceOnUse"
                >
                  <stop stop-color="#3b82f6" />
                  <stop offset="0.5" stop-color="#22d3ee" />
                  <stop offset="1" stop-color="#22c55e" />
                </linearGradient>
                <linearGradient
                  id="lp-edge-active"
                  x1="0"
                  y1="0"
                  x2="420"
                  y2="420"
                  gradientUnits="userSpaceOnUse"
                >
                  <stop stop-color="#ef4444" />
                  <stop offset="1" stop-color="#fbbf24" />
                </linearGradient>
              </defs>

              <!-- Edges -->
              <g class="graphmotif__edges" stroke-width="2">
                <line
                  v-for="edge in edges"
                  :key="`${edge.from}-${edge.to}`"
                  :x1="getNode(edge.from).x"
                  :y1="getNode(edge.from).y"
                  :x2="getNode(edge.to).x"
                  :y2="getNode(edge.to).y"
                  :stroke="isEdgeActive(edge) ? 'url(#lp-edge-active)' : 'url(#lp-edge)'"
                  :class="{
                    'edge--active': isEdgeActive(edge),
                    'edge--dimmed': selectedNode && !isEdgeActive(edge),
                    'edge--highlighted': isEdgeHighlighted(edge),
                  }"
                />
              </g>

              <!-- Nodes -->
              <g class="graphmotif__nodes">
                <g
                  v-for="node in nodes"
                  :key="node.id"
                  :id="`hero-node-${node.id}`"
                  class="node-group"
                  :class="{
                    'node-group--active': activeImpactNodes.includes(node.id),
                    'node-group--dimmed': selectedNode && !activeImpactNodes.includes(node.id),
                    'node-group--hover': hoverNode === node.id,
                  }"
                  @click="triggerImpact(node.id)"
                  @mouseenter="hoverNode = node.id"
                  @mouseleave="hoverNode = null"
                >
                  <circle
                    class="n-outer"
                    :cx="node.x"
                    :cy="node.y"
                    :r="node.r + 5"
                    :fill="node.color"
                    opacity="0.15"
                  />
                  <circle class="n" :cx="node.x" :cy="node.y" :r="node.r" :fill="node.color" />
                  <!-- Pulsing element for selected node -->
                  <circle
                    v-if="selectedNode?.id === node.id"
                    class="n-pulse"
                    :cx="node.x"
                    :cy="node.y"
                    :r="node.r + 10"
                    stroke="var(--vg-blue-bright)"
                    stroke-width="1.5"
                    fill="none"
                  />
                </g>
              </g>
            </svg>

            <!-- Dynamic Telemetry Screen -->
            <div class="telemetry-panel">
              <div class="telemetry-inner">
                <div v-if="selectedNode" class="telemetry-grid">
                  <div class="telemetry-cell">
                    <span class="telemetry-meta">{{ t('landing.graph.telemetry.targetSymbol') }}</span>
                    <span class="telemetry-data text-accent">{{ selectedNode.label }}</span>
                  </div>
                  <div class="telemetry-cell">
                    <span class="telemetry-meta">{{ t('landing.graph.telemetry.symbolType') }}</span>
                    <span class="telemetry-data text-capitalize">{{ selectedNode.type }}</span>
                  </div>
                  <div class="telemetry-cell">
                    <span class="telemetry-meta">{{ t('landing.graph.telemetry.blastRadius') }}</span>
                    <span class="telemetry-data" :class="impactClass(activeImpactNodes.length)">
                      {{ t('landing.graph.telemetry.affected', { count: activeImpactNodes.length }) }}
                    </span>
                  </div>
                  <div class="telemetry-cell">
                    <span class="telemetry-meta">{{ t('landing.graph.telemetry.riskLevel') }}</span>
                    <span class="telemetry-data" :class="impactClass(activeImpactNodes.length)">
                      {{ impactLevel(activeImpactNodes.length) }}
                    </span>
                  </div>
                </div>
                <div v-else class="telemetry-placeholder">
                  {{ t('landing.graph.telemetry.placeholder') }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ── Section: Problem & Solution (Goals & Values) ── -->
      <section id="goals" class="section section--goals">
        <div class="goals-grid">
          <div class="goals-col">
            <span class="section__eyebrow">{{ t('landing.challenge.eyebrow') }}</span>
            <h2 class="section__title">{{ t('landing.challenge.title') }}</h2>
            <div class="problem-card">
              <div class="problem-item">
                <span class="problem-num">01</span>
                <div>
                  <strong>{{ t('landing.challenge.items.overload.title') }}</strong>
                  <p>
                    {{ t('landing.challenge.items.overload.body') }}
                  </p>
                </div>
              </div>
              <div class="problem-item">
                <span class="problem-num">02</span>
                <div>
                  <strong>{{ t('landing.challenge.items.refactor.title') }}</strong>
                  <p>
                    {{ t('landing.challenge.items.refactor.body') }}
                  </p>
                </div>
              </div>
              <div class="problem-item">
                <span class="problem-num">03</span>
                <div>
                  <strong>{{ t('landing.challenge.items.agent.title') }}</strong>
                  <p>
                    {{ t('landing.challenge.items.agent.body') }}
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div class="goals-col">
            <span class="section__eyebrow">{{ t('landing.solution.eyebrow') }}</span>
            <h2 class="section__title">{{ t('landing.solution.title') }}</h2>
            <div class="solution-card">
              <div class="solution-item">
                <span class="solution-icon">✨</span>
                <div>
                  <strong>{{ t('landing.solution.items.maps.title') }}</strong>
                  <p>
                    {{ t('landing.solution.items.maps.body') }}
                  </p>
                </div>
              </div>
              <div class="solution-item">
                <span class="solution-icon">⚡</span>
                <div>
                  <strong>{{ t('landing.solution.items.impact.title') }}</strong>
                  <p>
                    {{ t('landing.solution.items.impact.body') }}
                  </p>
                </div>
              </div>
              <div class="solution-item">
                <span class="solution-icon">🔌</span>
                <div>
                  <strong>{{ t('landing.solution.items.mcp.title') }}</strong>
                  <p>
                    {{ t('landing.solution.items.mcp.body') }}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ── Features (Bento Grid aligned symmetrically) ── -->
      <section id="features" class="section">
        <header class="section__head">
          <span class="section__eyebrow">{{ t('landing.features.eyebrow') }}</span>
          <h2 class="section__title">{{ t('landing.features.title') }}</h2>
          <p class="section__sub">{{ t('landing.features.description') }}</p>
        </header>

        <div class="bento">
          <article
            v-for="(f, index) in features"
            :key="f.title"
            :id="`bento-card-${index}`"
            class="card"
            :class="[
              `card--${f.accent}`,
              f.span === 'wide' && index === 4 ? 'card--full' : `card--${f.span}`,
            ]"
            @mousemove="onMouseMoveBento($event, index)"
          >
            <span class="card__tag">{{ f.tag }}</span>
            <h3 class="card__title">{{ f.title }}</h3>
            <p class="card__body">{{ f.body }}</p>
            <span class="card__shine" aria-hidden="true"></span>

            <!-- Custom interactive micro-graphics inside bento cards -->
            <div class="card__micro-viz" aria-hidden="true">
              <span v-if="f.accent === 'blue'" class="viz-nodes">
                <span></span><span></span><span></span>
              </span>
              <span v-if="f.accent === 'green'" class="viz-radar">
                <span class="radar-ping"></span>
              </span>
              <span v-if="f.accent === 'cyan'" class="viz-sync">
                <span class="sync-dot sync-dot--1"></span>
                <span class="sync-dot sync-dot--2"></span>
              </span>
              <span v-if="f.accent === 'violet'" class="viz-diagram">
                <span class="box"></span>
                <span class="line"></span>
                <span class="box"></span>
              </span>
              <span v-if="f.accent === 'amber'" class="viz-import">
                <span class="arrow">↓</span>
              </span>
            </div>
          </article>
        </div>
      </section>

      <!-- ── How it works ── -->
      <section id="how" class="section">
        <header class="section__head">
          <span class="section__eyebrow">{{ t('landing.how.eyebrow') }}</span>
          <h2 class="section__title">{{ t('landing.how.title') }}</h2>
        </header>

        <div class="stepper-layout">
          <!-- Stepper triggers -->
          <div class="stepper-controls">
            <div
              v-for="(step, index) in steps"
              :key="step.n"
              :id="`step-control-${index}`"
              class="step-control"
              :class="{ 'step-control--active': activeStep === index }"
              @click="selectStep(index)"
            >
              <span class="step-control__n">{{ step.n }}</span>
              <div class="step-control__content">
                <h3 class="step-control__title">{{ step.title }}</h3>
                <p class="step-control__body">{{ step.body }}</p>
              </div>
            </div>
          </div>

          <!-- Dynamic Device Preview Screen -->
          <div class="stepper-preview">
            <div class="device-mockup">
              <div class="device-header">
                <span class="device-dot"></span>
                <span class="device-dot"></span>
                <span class="device-dot"></span>
                <span class="device-title">{{ t('landing.how.mock.client') }}</span>
              </div>
              <div class="device-body">
                <!-- Step 1: Import Mock -->
                <div v-if="activeStep === 0" class="mock-screen mock-screen--import">
                  <div class="import-area">
                    <div class="import-icon-wrap">
                      <svg
                        class="import-icon"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                      >
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                        <polyline points="17 8 12 3 7 8" />
                        <line x1="12" y1="3" x2="12" y2="15" />
                      </svg>
                    </div>
                    <h4>{{ t('landing.how.mock.dropFolder') }}</h4>
                    <p>{{ t('landing.how.mock.pasteGithub') }}</p>
                    <div class="mock-url-bar">
                      <span class="mock-url-prefix">https://github.com/</span>
                      <span class="mock-url-text">ThinhChauTran263/VibeGraph</span>
                    </div>
                    <button class="btn btn--primary btn--sm">{{ t('landing.how.mock.analyze') }}</button>
                  </div>
                </div>

                <!-- Step 2: Explore Mock -->
                <div v-if="activeStep === 1" class="mock-screen mock-screen--explore">
                  <div class="explore-grid">
                    <aside class="mock-sidebar">
                      <div class="sidebar-title">{{ t('landing.how.mock.projectStructure') }}</div>
                      <div class="sidebar-item sidebar-item--active">📁 src/main/java</div>
                      <div class="sidebar-item">📁 controller</div>
                      <div class="sidebar-item">📁 service</div>
                      <div class="sidebar-item">📁 repository</div>
                    </aside>
                    <div class="mock-canvas">
                      <svg class="mini-network" viewBox="0 0 200 200">
                        <line
                          x1="100"
                          y1="100"
                          x2="60"
                          y2="60"
                          stroke="rgba(255,255,255,0.15)"
                          stroke-width="1.5"
                        />
                        <line
                          x1="100"
                          y1="100"
                          x2="140"
                          y2="60"
                          stroke="rgba(255,255,255,0.15)"
                          stroke-width="1.5"
                        />
                        <line
                          x1="100"
                          y1="100"
                          x2="100"
                          y2="150"
                          stroke="rgba(255,255,255,0.15)"
                          stroke-width="1.5"
                        />
                        <circle cx="100" cy="100" r="10" fill="var(--vg-blue-bright)" />
                        <circle cx="60" cy="60" r="8" fill="var(--vg-cyan)" />
                        <circle cx="140" cy="60" r="8" fill="var(--vg-violet)" />
                        <circle cx="100" cy="150" r="8" fill="var(--vg-amber)" />
                      </svg>
                      <span class="mini-canvas-tip">{{ t('landing.how.mock.panZoom') }}</span>
                    </div>
                  </div>
                </div>

                <!-- Step 3: Analyze Mock -->
                <div v-if="activeStep === 2" class="mock-screen mock-screen--analyze">
                  <div class="analyze-layout">
                    <div class="alert-banner">
                      <span class="alert-dot alert-dot--danger"></span>
                      <div>
                        <strong>{{ t('landing.how.mock.blastTriggered') }}</strong>
                        <p>{{ t('landing.how.mock.downstreamAffected') }}</p>
                      </div>
                    </div>
                    <div class="mock-canvas">
                      <svg class="mini-network" viewBox="0 0 200 200">
                        <!-- highlighted connections -->
                        <line
                          x1="100"
                          y1="100"
                          x2="60"
                          y2="60"
                          stroke="var(--vg-danger)"
                          stroke-width="2.5"
                        />
                        <line
                          x1="100"
                          y1="100"
                          x2="140"
                          y2="60"
                          stroke="var(--vg-danger)"
                          stroke-width="2.5"
                        />
                        <line
                          x1="100"
                          y1="100"
                          x2="100"
                          y2="150"
                          stroke="rgba(255,255,255,0.15)"
                          stroke-width="1.5"
                        />
                        <circle cx="100" cy="100" r="10" fill="var(--vg-danger)" />
                        <circle cx="60" cy="60" r="8" fill="var(--vg-danger)" />
                        <circle cx="140" cy="60" r="8" fill="var(--vg-danger)" />
                        <circle cx="100" cy="150" r="8" fill="var(--vg-amber)" />
                      </svg>
                      <span class="mini-canvas-tip mini-canvas-tip--danger"
                        >{{ t('landing.how.mock.upstreamRiskHigh') }}</span
                      >
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ── Guide Section: Quick Start Guide ── -->
      <section id="guide" class="section section--guide">
        <header class="section__head">
          <span class="section__eyebrow">{{ t('landing.guide.eyebrow') }}</span>
          <h2 class="section__title">{{ t('landing.guide.title') }}</h2>
          <p class="section__sub">{{ t('landing.guide.description') }}</p>
        </header>

        <div class="guide-box">
          <div class="guide-tabs" role="tablist" :aria-label="t('landing.guide.tabsAria')">
            <button
              v-for="(g, index) in guideTabs"
              :key="g.title"
              class="guide-tab"
              :class="{ 'guide-tab--active': activeGuideTab === index }"
              @click="activeGuideTab = index"
              role="tab"
              :aria-selected="activeGuideTab === index"
            >
              <span class="guide-tab__num">{{ g.step }}</span>
              {{ g.title }}
            </button>
          </div>

          <div class="guide-content">
            <div v-if="activeGuideTab === 0" class="guide-pane">
              <h4>{{ t('landing.guide.step1.title') }}</h4>
              <p>{{ t('landing.guide.step1.body') }}</p>
              <div class="code-terminal">
                <div class="code-terminal-header">
                  <span>{{ t('landing.guide.step1.terminal') }}</span>
                </div>
                <pre><code># Install the CLI package when it is published
npm install -g vibegraph-cli

# Point the CLI at production and sign in in your browser
vibegraph config set-url https://vibegraph.tech
vibegraph login

# Push or watch a project
vibegraph push --root ./your-project
vibegraph watch --root ./your-project</code></pre>
              </div>
              <p class="text-sm text-dim">
                {{ t('landing.guide.step1.envLead') }}
                <RouterLink to="/docs">{{ t('landing.guide.step1.docsLink') }}</RouterLink>
                {{ t('landing.guide.step1.envTail') }}
              </p>
            </div>

            <div v-if="activeGuideTab === 1" class="guide-pane">
              <h4>{{ t('landing.guide.step2.title') }}</h4>
              <p>
                {{ t('landing.guide.step2.bodyLead') }} <code>https://vibegraph.tech</code>
                {{ t('landing.guide.step2.bodyTail') }}
              </p>
              <ul class="guide-list">
                <li>
                  📁 <strong>{{ t('landing.guide.step2.localTitle') }}</strong>:
                  {{ t('landing.guide.step2.localBody') }}
                </li>
                <li>
                  🗜️ <strong>{{ t('landing.guide.step2.archiveTitle') }}</strong>:
                  {{ t('landing.guide.step2.archiveBodyLead') }} <code>.zip</code>,
                  <code>.tar</code> {{ t('landing.guide.step2.archiveBodyOr') }}
                  <code>.tar.gz</code> {{ t('landing.guide.step2.archiveBodyTail') }}
                </li>
                <li>
                  🔗 <strong>GitHub</strong>: {{ t('landing.guide.step2.githubBody') }}
                </li>
              </ul>
              <p class="text-sm text-dim">
                {{ t('landing.guide.step2.note') }}
              </p>
            </div>

            <div v-if="activeGuideTab === 2" class="guide-pane">
              <h4>{{ t('landing.guide.step3.title') }}</h4>
              <p>
                {{ t('landing.guide.step3.bodyLead') }}
                <strong>Streamable HTTP</strong>{{ t('landing.guide.step3.bodyTail') }}
              </p>
              <div class="code-terminal">
                <div class="code-terminal-header">
                  <span>{{ t('landing.guide.step3.configTitle') }}</span>
                </div>
                <pre><code>{
  "mcpServers": {
    "vibegraph": {
      "url": "https://vibegraph.tech/mcp",
      "transport": "streamable-http",
      "headers": { "X-API-Key": "&lt;PROJECT_API_KEY&gt;" }
    }
  }
}</code></pre>
              </div>
              <p class="text-sm text-dim">
                {{ t('landing.guide.step3.noteLead') }}
                <code>"type": "streamable-http"</code>{{ t('landing.guide.step3.noteTail') }}
              </p>
            </div>
          </div>
        </div>
      </section>

      <!-- ── Section: Under the hood (the engine) ── -->
      <section id="engine" class="section">
        <header class="section__head">
          <span class="section__eyebrow">{{ t('landing.engine.eyebrow') }}</span>
          <h2 class="section__title">{{ t('landing.engine.title') }}</h2>
          <p class="section__sub">{{ t('landing.engine.description') }}</p>
        </header>
        <div class="nexus-card">
          <p>
            {{ t('landing.engine.bodyLead') }} <strong>Neo4j</strong>
            {{ t('landing.engine.bodyTail') }}
          </p>
          <ul class="guide-list">
            <li>
              🔒 <strong>{{ t('landing.engine.items.private.title') }}</strong>:
              {{ t('landing.engine.items.private.body') }}
            </li>
            <li>
              ⚡ <strong>{{ t('landing.engine.items.incremental.title') }}</strong>:
              {{ t('landing.engine.items.incremental.body') }}
            </li>
            <li>
              🤖 <strong>{{ t('landing.engine.items.mcp.title') }}</strong>:
              {{ t('landing.engine.items.mcp.body') }}
            </li>
          </ul>
        </div>
      </section>

      <!-- ── AI agents / MCP ── -->
      <section id="agents" class="section">
        <header class="section__head">
          <span class="section__eyebrow">{{ t('landing.agents.eyebrow') }}</span>
          <h2 class="section__title">{{ t('landing.agents.title') }}</h2>
          <p class="section__sub">{{ t('landing.agents.description') }}</p>
        </header>

        <!-- Terminal Playground Container -->
        <div class="terminal-playground">
          <div class="terminal-tabs">
            <button
              id="terminal-tab-impact"
              class="terminal-tab"
              :class="{ 'terminal-tab--active': activeTerminalTab === 'impact' }"
              @click="loadTerminalCommand('impact')"
            >
              /impact-analysis
            </button>
            <button
              id="terminal-tab-context"
              class="terminal-tab"
              :class="{ 'terminal-tab--active': activeTerminalTab === 'context' }"
              @click="loadTerminalCommand('context')"
            >
              /symbol-context
            </button>
            <button
              id="terminal-tab-plan"
              class="terminal-tab"
              :class="{ 'terminal-tab--active': activeTerminalTab === 'plan' }"
              @click="loadTerminalCommand('plan')"
            >
              /plan-change
            </button>
          </div>

          <div class="terminal-screen">
            <div class="terminal-screen-header">
              <span class="dot-win dot-win--red"></span>
              <span class="dot-win dot-win--yellow"></span>
              <span class="dot-win dot-win--green"></span>
              <span class="terminal-screen-title">{{ t('landing.agents.terminalTitle') }}</span>
            </div>

            <div class="terminal-screen-body">
              <div class="terminal-input-line">
                <span class="terminal-prompt">$</span>
                <span class="terminal-typed-input">{{ terminalInput }}</span>
                <span v-if="terminalTyping" class="terminal-cursor"></span>
              </div>

              <transition name="fade">
                <pre
                  v-if="terminalOutput"
                  class="terminal-output"
                ><code>{{ terminalOutput }}</code></pre>
              </transition>

              <div v-if="terminalTyping" class="terminal-loading">
                <span class="loading-spinner"></span>
                {{ t('landing.agents.running') }}
              </div>
            </div>
          </div>
        </div>

        <ul class="logo-wall" :aria-label="t('landing.agents.logoAria')">
          <li v-for="a in agents" :key="a.label">
            <LogoTile :src="a.src" :label="a.label" :boost="a.boost" />
          </li>
        </ul>
      </section>

      <!-- ── Stack ── -->
      <section id="stack" class="section section--stack">
        <header class="section__head">
          <span class="section__eyebrow">{{ t('landing.stack.eyebrow') }}</span>
          <h2 class="section__title">{{ t('landing.stack.title') }}</h2>
        </header>
        <ul class="logo-wall" :aria-label="t('landing.stack.logoAria')">
          <li v-for="tech in stack" :key="tech.label">
            <LogoTile :src="tech.src" :label="tech.label" :tone="tech.tone" />
          </li>
        </ul>
      </section>

      <!-- ── CTA band ── -->
      <section class="cta">
        <div class="cta__inner">
          <h2 class="cta__title">{{ t('landing.cta.title') }}</h2>
          <p class="cta__sub">{{ t('landing.cta.description') }}</p>
          <RouterLink class="btn btn--primary btn--lg" :to="primaryRoute">
            {{ primaryLabel }}
            <span class="btn__arrow" aria-hidden="true">→</span>
          </RouterLink>
        </div>
      </section>
    </main>

    <footer class="lp-footer">
      <div class="footer-top">
        <div class="footer-brand">
          <BrandMark :size="24" />
          <span class="lp-footer__note">{{ t('landing.footer.note') }}</span>
        </div>
        <div class="footer-links">
          <div class="footer-col">
            <h4>{{ t('landing.footer.product') }}</h4>
            <a href="#features">{{ t('landing.nav.features') }}</a>
            <a href="#how">{{ t('landing.nav.howItWorks') }}</a>
            <a href="#guide">{{ t('landing.footer.installation') }}</a>
            <RouterLink to="/docs">{{ publicCopy.landing.documentation }}</RouterLink>
          </div>
          <div class="footer-col">
            <h4>{{ t('landing.footer.resources') }}</h4>
            <a href="https://github.com/ThinhChauTran263/VibeGraph-com" target="_blank"
              >{{ t('landing.footer.githubRepo') }}</a
            >
            <a href="#engine">{{ t('landing.footer.engine') }}</a>
          </div>
        </div>
      </div>
      <div class="footer-bottom">
        <span>{{ t('landing.footer.copyright') }}</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.lp {
  position: relative;
  min-height: 100vh;
  overflow: clip;
  background:
    radial-gradient(120% 80% at 80% -10%, rgba(34, 211, 238, 0.08), transparent 60%),
    radial-gradient(90% 60% at 0% 0%, rgba(96, 165, 250, 0.1), transparent 55%), var(--vg-bg);
}

/* ── Atmosphere ── */
.lp__aurora {
  position: absolute;
  inset: -20% -10% auto -10%;
  height: 720px;
  background:
    radial-gradient(50% 60% at 30% 30%, rgba(59, 130, 246, 0.22), transparent 70%),
    radial-gradient(45% 55% at 75% 20%, rgba(34, 197, 94, 0.16), transparent 70%);
  filter: blur(20px);
  pointer-events: none;
  z-index: 0;
  animation: floatBackground 20s ease infinite alternate;
}

@keyframes floatBackground {
  0% {
    transform: translateY(0) scale(1);
  }
  100% {
    transform: translateY(30px) scale(1.05);
  }
}

.lp__grid-overlay {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(80% 60% at 50% 0%, #000 30%, transparent 80%);
  pointer-events: none;
  z-index: 0;
}

/* Float Glow Particles */
.lp__glow-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.particle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(96, 165, 250, 0.2) 0%, transparent 70%);
  filter: blur(10px);
}

.particle--1 {
  width: 150px;
  height: 150px;
  top: 15%;
  left: 10%;
  animation: floatOrb 12s infinite ease-in-out;
}

.particle--2 {
  width: 250px;
  height: 250px;
  bottom: 25%;
  right: 5%;
  animation: floatOrb 18s infinite ease-in-out -4s;
}

.particle--3 {
  width: 120px;
  height: 120px;
  top: 60%;
  left: 45%;
  animation: floatOrb 10s infinite ease-in-out -7s;
}

@keyframes floatOrb {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(25px, -35px) scale(1.1);
  }
}

/* Virtual Cursor Guide Tour style */
.virtual-cursor {
  position: fixed;
  pointer-events: none;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  transition: transform 0.1s ease;
}

.virtual-cursor-tag {
  background: var(--vg-cyan);
  color: #070b16;
  font-family: var(--vg-font-display);
  font-size: 9px;
  font-weight: bold;
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
  margin-top: 4px;
  margin-left: 12px;
  box-shadow: var(--vg-shadow);
  border: 1px solid #fff;
  white-space: nowrap;
}

.click-ripple {
  position: absolute;
  left: 0px;
  top: 0px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid var(--vg-cyan);
  transform: translate(-5px, -5px) scale(0);
  animation: rippleAnim 0.3s ease-out;
}

@keyframes rippleAnim {
  0% {
    transform: translate(-5px, -5px) scale(0.1);
    opacity: 1;
  }
  100% {
    transform: translate(-5px, -5px) scale(1.5);
    opacity: 0;
  }
}

main,
.lp-nav,
.lp-footer {
  position: relative;
  z-index: 1;
}

/* ── Nav ── */
.lp-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  transition:
    background-color var(--vg-dur) var(--vg-ease-out),
    border-color var(--vg-dur) var(--vg-ease-out),
    backdrop-filter var(--vg-dur);
  border-bottom: 1px solid transparent;
}

.lp-nav--scrolled {
  background: rgba(7, 11, 22, 0.72);
  backdrop-filter: blur(14px);
  border-bottom-color: var(--vg-border);
}

.lp-nav__inner {
  max-width: var(--vg-maxw);
  margin: 0 auto;
  padding: 0.9rem var(--vg-space-6);
  display: flex;
  align-items: center;
  gap: var(--vg-space-6);
}

.lp-nav__links {
  margin-left: auto;
  display: flex;
  gap: var(--vg-space-8);
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.lp-nav__links a {
  position: relative;
  transition: color var(--vg-dur-fast) var(--vg-ease-out);
}

.lp-nav__links a::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -4px;
  width: 100%;
  height: 1.5px;
  background: var(--vg-grad-brand);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform var(--vg-dur) var(--vg-ease-out);
}

.lp-nav__links a:hover {
  color: var(--vg-text);
}
.lp-nav__links a.lp-nav__link--active {
  color: var(--vg-text);
}
.lp-nav__links a.lp-nav__link--active::after {
  transform: scaleX(1);
}
.lp-nav__links a:hover::after {
  transform: scaleX(1);
}

/* ── Buttons (shared in this view) ── */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  font-family: var(--vg-font-body);
  font-weight: 600;
  border-radius: var(--vg-radius-pill);
  border: 1px solid transparent;
  cursor: pointer;
  white-space: nowrap;
  transition:
    transform var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur) var(--vg-ease-out),
    background-color var(--vg-dur-fast),
    border-color var(--vg-dur-fast),
    color var(--vg-dur-fast);
}

.btn--sm {
  padding: 0.5rem 1rem;
  font-size: var(--vg-text-sm);
}
.btn--lg {
  padding: 0.85rem 1.6rem;
  font-size: var(--vg-text-lg);
}

.btn--primary {
  background: var(--vg-grad-blue);
  color: #fff;
  box-shadow: var(--vg-glow-blue);
}
.btn--primary:hover {
  transform: translateY(-2px);
  box-shadow:
    0 0 0 1px rgba(96, 165, 250, 0.5),
    0 24px 60px -18px rgba(59, 130, 246, 0.7);
}
.btn--primary:active {
  transform: translateY(0);
}

.btn--ghost {
  background: rgba(148, 163, 184, 0.06);
  border-color: var(--vg-border-strong);
  color: var(--vg-text);
}
.btn--ghost:hover {
  background: rgba(148, 163, 184, 0.12);
  border-color: var(--vg-blue-bright);
}

.btn__arrow {
  transition: transform var(--vg-dur) var(--vg-ease-out);
}
.btn:hover .btn__arrow {
  transform: translateX(4px);
}

/* ── Hero ── */
.hero {
  max-width: var(--vg-maxw);
  margin: 0 auto;
  padding: clamp(2rem, 1rem + 3vw, 4rem) var(--vg-space-6) var(--vg-space-12);
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: var(--vg-space-8);
  align-items: center;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0.85rem;
  border-radius: var(--vg-radius-pill);
  border: 1px solid var(--vg-border-strong);
  background: rgba(15, 23, 42, 0.6);
  font-size: var(--vg-text-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--vg-text-muted);
}

.pill__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--vg-green-bright);
  box-shadow: 0 0 10px var(--vg-green);
  animation: pulse 2.4s var(--vg-ease-in-out) infinite;
}

.hero__title {
  margin: 1.25rem 0 0;
  font-size: var(--vg-text-hero);
  font-weight: 700;
}

.hero__title-grad {
  background: var(--vg-grad-brand);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero__lede {
  margin: 1.25rem 0 0;
  max-width: 34rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-lg);
}

.hero__cta {
  margin-top: 2rem;
  display: flex;
  flex-wrap: wrap;
  gap: var(--vg-space-3);
}

.hero__stats {
  list-style: none;
  margin: 2.75rem 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(4, auto);
  gap: var(--vg-space-8);
}
.hero__stats li {
  display: flex;
  flex-direction: column;
}
.hero__stat-value {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-xl);
  font-weight: 700;
  color: var(--vg-text);
}
.hero__stat-label {
  font-size: var(--vg-text-xs);
  color: var(--vg-text-dim);
  max-width: 9rem;
}

/* ── Hero visual (Interactive Graph Module) ── */
.hero__visual {
  will-change: transform;
  position: relative;
  aspect-ratio: 1;
  display: grid;
  place-items: center;
}

.orb {
  position: absolute;
  inset: 12%;
  border-radius: 50%;
  background:
    radial-gradient(circle at 35% 30%, rgba(96, 165, 250, 0.35), transparent 60%),
    radial-gradient(circle at 70% 75%, rgba(34, 197, 94, 0.28), transparent 60%);
  filter: blur(26px);
  animation: float 9s var(--vg-ease-in-out) infinite;
}

.interactive-graph-card {
  position: relative;
  width: 100%;
  max-width: 440px;
  border-radius: var(--vg-radius-xl);
  border: 1px solid var(--vg-border-strong);
  background: rgba(11, 17, 32, 0.85);
  box-shadow: var(--vg-shadow-lg);
  backdrop-filter: blur(12px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.graph-header {
  width: 100%;
  padding: 0.85rem var(--vg-space-4);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-bottom: 1px solid var(--vg-border);
  background: rgba(15, 23, 42, 0.5);
}

.graph-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--vg-blue-bright);
  box-shadow: 0 0 8px var(--vg-blue);
  animation: pulse 2s infinite;
}

.graph-header-text {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-xs);
  color: var(--vg-text-muted);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.graphmotif {
  position: relative;
  width: 100%;
  height: 320px;
}

/* Edges Animations */
.graphmotif__edges line {
  opacity: 0.25;
  transition:
    stroke var(--vg-dur) var(--vg-ease-out),
    stroke-width var(--vg-dur) var(--vg-ease-out),
    opacity var(--vg-dur) var(--vg-ease-out);
}

.graphmotif__edges line.edge--active {
  stroke-width: 3px;
  opacity: 1;
  stroke-dasharray: 4 4;
  animation: dashActive 10s linear infinite;
}

.graphmotif__edges line.edge--dimmed {
  opacity: 0.06;
}

.graphmotif__edges line.edge--highlighted {
  opacity: 0.8;
  stroke-width: 2.5px;
}

@keyframes dashActive {
  to {
    stroke-dashoffset: -120;
  }
}

/* Node Groups */
.node-group {
  cursor: pointer;
  /* Scale about the node's own centre, not the SVG viewBox origin (0,0).
     Without this the node jumps ~30px on hover, leaves the cursor, un-hovers
     and snaps back — an infinite hover jitter loop. */
  transform-box: fill-box;
  transform-origin: center;
  transition:
    transform var(--vg-dur) var(--vg-ease-out),
    opacity var(--vg-dur);
}

.node-group circle.n {
  stroke: #070b16;
  stroke-width: 3.5;
  transition:
    fill var(--vg-dur),
    stroke var(--vg-dur);
}

.node-group circle.n-outer {
  transform-box: fill-box;
  transform-origin: center;
  transition: transform var(--vg-dur) var(--vg-ease-out);
}

.node-group:hover {
  transform: scale(1.15);
}

.node-group:hover circle.n-outer {
  transform: scale(1.3);
}

.node-group--dimmed {
  opacity: 0.45;
}

.node-group--active {
  opacity: 1 !important;
}

.node-group--active circle.n {
  stroke: #fff !important;
}

.n-pulse {
  animation: ringPulse 2s cubic-bezier(0.25, 0.46, 0.45, 0.94) infinite;
  transform-origin: center;
  transform-box: fill-box;
}

@keyframes ringPulse {
  0% {
    transform: scale(0.9);
    opacity: 1;
  }
  100% {
    transform: scale(1.6);
    opacity: 0;
  }
}

/* Telemetry Screen Panel */
.telemetry-panel {
  width: 100%;
  background: rgba(7, 11, 22, 0.9);
  border-top: 1px solid var(--vg-border);
  padding: var(--vg-space-3) var(--vg-space-4);
  font-family: var(--vg-font-display);
}

.telemetry-inner {
  width: 100%;
  min-height: 52px;
  display: flex;
  align-items: center;
}

.telemetry-placeholder {
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
  text-align: center;
  width: 100%;
  letter-spacing: 0.02em;
}

.telemetry-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--vg-space-2) var(--vg-space-6);
  width: 100%;
}

.telemetry-cell {
  display: flex;
  flex-direction: column;
}

.telemetry-meta {
  font-size: 10px;
  color: var(--vg-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.telemetry-data {
  font-size: var(--vg-text-xs);
  font-weight: 500;
  color: var(--vg-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-accent {
  color: var(--vg-cyan) !important;
}

.telemetry-value--critical {
  color: var(--vg-danger) !important;
  font-weight: bold;
}

.telemetry-value--high {
  color: var(--vg-amber) !important;
  font-weight: bold;
}

.telemetry-value--safe {
  color: var(--vg-green-bright) !important;
}

/* ── Section base: keep content inside the page column ──
   Plain sections (#features, #how, #global, #guide, #agents, #stack …) had no
   width constraint, so on wide screens their heads/grids stretched edge-to-edge
   while the hero/goals/cta stayed at --vg-maxw. Constrain the content here while
   leaving the full-bleed band backgrounds (goals/global/guide) untouched. */
.section {
  padding-inline: var(--vg-space-6);
}
.section > .section__head,
.section > .bento,
.section > .stepper-layout,
.section > .guide-box {
  width: 100%;
  max-width: var(--vg-maxw);
  margin-inline: auto;
}

/* ── Section: Goals (Problem & Solution) ── */
.section--goals {
  background: rgba(15, 23, 42, 0.2);
  border-top: 1px solid var(--vg-border);
  border-bottom: 1px solid var(--vg-border);
  padding: var(--vg-space-16) var(--vg-space-6);
}

.goals-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--vg-space-12);
  max-width: var(--vg-maxw);
  margin: 0 auto;
}

/* Both column titles must occupy the same height so the two card lists start
   at the same Y. The left title wraps to two lines and the right to one;
   reserving two lines keeps the columns aligned. (Single-column on mobile is
   unaffected — min-height just sets a floor.) */
.section--goals .section__title {
  min-height: 2.2em;
}

.problem-card,
.solution-card {
  margin-top: var(--vg-space-6);
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-6);
}

.problem-item,
.solution-item {
  display: flex;
  gap: var(--vg-space-4);
  background: rgba(7, 11, 22, 0.4);
  padding: var(--vg-space-4);
  border-radius: var(--vg-radius);
  border: 1px solid var(--vg-border);
}

.problem-num {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-lg);
  font-weight: bold;
  color: var(--vg-danger);
  opacity: 0.7;
}

.solution-icon {
  font-size: var(--vg-text-xl);
  line-height: 1;
}

.problem-item strong,
.solution-item strong {
  display: block;
  font-size: var(--vg-text-base);
  color: var(--vg-text);
  margin-bottom: 0.25rem;
}

.problem-item p,
.solution-item p {
  margin: 0;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

/* ── Bento Grid ── */
.bento {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--vg-space-4);
}

.card {
  position: relative;
  overflow: hidden;
  padding: var(--vg-space-6);
  border-radius: var(--vg-radius-lg);
  border: 1px solid var(--vg-border);
  background: var(--vg-grad-surface);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 220px;
  transition:
    transform var(--vg-dur) var(--vg-ease-out),
    border-color var(--vg-dur) var(--vg-ease-out),
    box-shadow var(--vg-dur) var(--vg-ease-out);
}

/* Cursor Glow Spotlight */
.card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(
    300px circle at var(--mouse-x, -9999px) var(--mouse-y, -9999px),
    rgba(255, 255, 255, 0.05),
    transparent 50%
  );
  z-index: 1;
  pointer-events: none;
  transition: opacity 0.5s ease;
}

.card--wide {
  grid-column: span 2;
}
.card--tall {
  grid-row: span 2;
}
/* Aligns the bottom card symmetrically over 3 columns */
.card--full {
  grid-column: span 3;
}

.card:hover {
  border-color: var(--vg-border-strong);
  box-shadow: var(--vg-shadow-lg);
}

.card__tag {
  align-self: flex-start;
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-xs);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  padding: 0.25rem 0.6rem;
  border-radius: var(--vg-radius-pill);
  border: 1px solid var(--vg-border-strong);
  color: var(--vg-text-muted);
  z-index: 2;
}
.card__title {
  margin: 0.4rem 0 0;
  font-size: var(--vg-text-xl);
  z-index: 2;
}
.card__body {
  margin: 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-base);
  z-index: 2;
}

.card__shine {
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: var(--vg-accent, var(--vg-blue));
  opacity: 0.8;
  z-index: 2;
}
.card--blue {
  --vg-accent: var(--vg-blue-bright);
}
.card--green {
  --vg-accent: var(--vg-green);
}
.card--cyan {
  --vg-accent: var(--vg-cyan);
}
.card--violet {
  --vg-accent: var(--vg-violet);
}
.card--amber {
  --vg-accent: var(--vg-amber);
}

/* Micro-Viz Graphics Inside Bento Cards */
.card__micro-viz {
  position: absolute;
  bottom: var(--vg-space-4);
  right: var(--vg-space-6);
  opacity: 0.3;
  pointer-events: none;
  z-index: 1;
  transition:
    opacity var(--vg-dur),
    transform var(--vg-dur);
}

.card:hover .card__micro-viz {
  opacity: 0.65;
  transform: scale(1.05);
}

/* Blue node micro-viz */
.viz-nodes {
  display: flex;
  gap: 8px;
}
.viz-nodes span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--vg-blue-bright);
}
.viz-nodes span:nth-child(2) {
  background: var(--vg-cyan);
  animation: pulse 1.5s infinite alternate;
}

/* Green radar */
.viz-radar {
  position: relative;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1px solid var(--vg-green);
}
.radar-ping {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--vg-green-bright);
  animation: ringPulse 2s infinite linear;
}

/* Cyan sync */
.viz-sync {
  display: flex;
  gap: 12px;
}
.sync-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--vg-cyan);
}
.sync-dot--1 {
  animation: float 3s infinite ease-in-out;
}
.sync-dot--2 {
  animation: float 3s infinite ease-in-out -1.5s;
}

/* Violet UML class structure */
.viz-diagram {
  display: flex;
  align-items: center;
  gap: 4px;
}
.viz-diagram .box {
  width: 14px;
  height: 14px;
  border: 1px solid var(--vg-violet);
  background: rgba(167, 139, 250, 0.2);
}
.viz-diagram .line {
  width: 10px;
  height: 1px;
  background: var(--vg-violet);
}

/* Amber download arrow */
.viz-import {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-lg);
  color: var(--vg-amber);
  animation: float 2s infinite ease-in-out;
}

/* ── Stepper Layout ("How it works") ── */
.stepper-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--vg-space-12);
  align-items: center;
}

.stepper-controls {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-6);
}

.step-control {
  position: relative;
  padding: var(--vg-space-6);
  border-radius: var(--vg-radius-lg);
  border: 1px solid var(--vg-border);
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  gap: var(--vg-space-4);
  cursor: pointer;
  transition:
    border-color var(--vg-dur) var(--vg-ease-out),
    background var(--vg-dur);
}

.step-control:hover {
  border-color: var(--vg-border-strong);
  background: rgba(15, 23, 42, 0.55);
}

.step-control--active {
  border-color: var(--vg-blue-bright);
  background: rgba(59, 130, 246, 0.08);
}

.step-control__n {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-2xl);
  font-weight: 700;
  background: var(--vg-grad-brand);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  line-height: 1;
}

.step-control__content {
  display: flex;
  flex-direction: column;
}

.step-control__title {
  margin: 0;
  font-size: var(--vg-text-lg);
  color: var(--vg-text);
  font-weight: 600;
}

.step-control__body {
  margin: var(--vg-space-2) 0 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.5;
}

/* Stepper Device Preview Mockup */
.stepper-preview {
  display: flex;
  justify-content: center;
}

.device-mockup {
  width: 100%;
  max-width: 460px;
  border-radius: var(--vg-radius-xl);
  border: 1px solid var(--vg-border-strong);
  background: #090e1a;
  box-shadow: var(--vg-shadow-lg);
  overflow: hidden;
}

.device-header {
  padding: 0.75rem var(--vg-space-4);
  background: #0f1524;
  border-bottom: 1px solid var(--vg-border);
  display: flex;
  align-items: center;
  gap: 6px;
}

.device-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--vg-border-strong);
}

.device-dot:nth-child(1) {
  background: #ef4444;
}
.device-dot:nth-child(2) {
  background: #fbbf24;
}
.device-dot:nth-child(3) {
  background: #22c55e;
}

.device-title {
  margin-left: auto;
  font-family: var(--vg-font-display);
  font-size: 10px;
  color: var(--vg-text-dim);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.device-body {
  height: 270px;
  position: relative;
  background: #070b14;
}

.mock-screen {
  position: absolute;
  inset: 0;
  padding: var(--vg-space-6);
  display: flex;
  flex-direction: column;
  animation: fadeIn var(--vg-dur) var(--vg-ease-out);
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.98);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* Screen 1: Import Mockup */
.mock-screen--import {
  justify-content: center;
  align-items: center;
}

.import-area {
  text-align: center;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.import-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(96, 165, 250, 0.1);
  display: grid;
  place-items: center;
  color: var(--vg-blue-bright);
  margin-bottom: var(--vg-space-3);
  border: 1px dashed var(--vg-blue);
}

.import-icon {
  width: 20px;
  height: 20px;
}

.import-area h4 {
  margin: 0;
  font-size: var(--vg-text-base);
  font-weight: 600;
  color: var(--vg-text);
}

.import-area p {
  margin: 0.25rem 0 0.85rem;
  font-size: var(--vg-text-xs);
  color: var(--vg-text-dim);
}

.mock-url-bar {
  width: 100%;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid var(--vg-border);
  padding: 0.5rem 0.75rem;
  border-radius: var(--vg-radius);
  font-family: var(--vg-font-display);
  font-size: 11px;
  display: flex;
  margin-bottom: var(--vg-space-4);
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.5);
}

.mock-url-prefix {
  color: var(--vg-text-dim);
}

.mock-url-text {
  color: var(--vg-blue-bright);
}

/* Screen 2: Explore Mockup */
.mock-screen--explore {
  padding: 0;
}

.explore-grid {
  display: grid;
  grid-template-columns: 140px 1fr;
  height: 100%;
}

.mock-sidebar {
  border-right: 1px solid var(--vg-border);
  background: #0a0f1c;
  padding: var(--vg-space-3);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  font-family: var(--vg-font-display);
  font-size: 10px;
  color: var(--vg-text-dim);
}

.sidebar-title {
  font-weight: bold;
  margin-bottom: 0.25rem;
  color: var(--vg-text-muted);
}

.sidebar-item {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-item--active {
  background: rgba(96, 165, 250, 0.1);
  color: var(--vg-blue-bright);
}

.mock-canvas {
  position: relative;
  display: grid;
  place-items: center;
  background: radial-gradient(circle, #0c1224 0%, #070b14 100%);
  overflow: hidden;
}

.mini-network {
  width: 140px;
  height: 140px;
}

.mini-canvas-tip {
  position: absolute;
  bottom: 8px;
  right: 12px;
  font-family: var(--vg-font-display);
  font-size: 8px;
  color: var(--vg-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* Screen 3: Analyze Mockup */
.mock-screen--analyze {
  padding: 0;
}

.analyze-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.alert-banner {
  background: rgba(239, 68, 68, 0.08);
  border-bottom: 1px solid rgba(239, 68, 68, 0.2);
  padding: 0.65rem var(--vg-space-4);
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
  font-size: var(--vg-text-xs);
}

.alert-banner strong {
  color: var(--vg-danger);
}

.alert-banner p {
  margin: 0;
  font-size: 10px;
  color: var(--vg-text-muted);
}

.alert-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--vg-danger);
  box-shadow: 0 0 8px var(--vg-danger);
  animation: pulse 1s infinite alternate;
}

.mini-canvas-tip--danger {
  color: var(--vg-danger) !important;
  font-weight: bold;
}

/* ── Section: User Guide ── */
.section--guide {
  background: rgba(15, 23, 42, 0.3);
  padding-bottom: var(--vg-space-16);
}

.guide-box {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: var(--vg-space-8);
  border-radius: var(--vg-radius-xl);
  border: 1px solid var(--vg-border-strong);
  background: #090e1a;
  overflow: hidden;
  box-shadow: var(--vg-shadow-lg);
}

.guide-tabs {
  background: #0f1524;
  border-right: 1px solid var(--vg-border);
  display: flex;
  flex-direction: column;
}

.guide-tab {
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
  padding: var(--vg-space-6) var(--vg-space-4);
  border: none;
  background: transparent;
  color: var(--vg-text-muted);
  font-family: var(--vg-font-body);
  font-weight: 600;
  font-size: var(--vg-text-sm);
  text-align: left;
  cursor: pointer;
  transition:
    background var(--vg-dur),
    color var(--vg-dur-fast);
  border-left: 3px solid transparent;
}

.guide-tab:hover {
  background: rgba(255, 255, 255, 0.02);
  color: var(--vg-text);
}

.guide-tab--active {
  background: #090e1a;
  color: var(--vg-blue-bright);
  border-left-color: var(--vg-blue-bright);
}

.guide-tab__num {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-sm);
  color: var(--vg-text-dim);
}

.guide-tab--active .guide-tab__num {
  color: var(--vg-blue-bright);
}

.guide-content {
  padding: var(--vg-space-8);
}

.guide-pane h4 {
  margin: 0;
  font-size: var(--vg-text-lg);
  color: var(--vg-text);
}

.guide-pane p {
  margin: var(--vg-space-3) 0 var(--vg-space-6);
  color: var(--vg-text-muted);
  line-height: 1.6;
}

.code-terminal {
  background: #050810;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  overflow: hidden;
  box-shadow: inset 0 4px 12px rgba(0, 0, 0, 0.6);
  margin-bottom: var(--vg-space-4);
}

.code-terminal-header {
  background: #0c1224;
  padding: 0.45rem var(--vg-space-4);
  font-family: var(--vg-font-display);
  font-size: 10px;
  color: var(--vg-text-dim);
  border-bottom: 1px solid var(--vg-border);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.code-terminal pre {
  margin: 0;
  padding: var(--vg-space-4);
  overflow-x: auto;
}

.code-terminal code {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-sm);
  color: #a7f3d0; /* emerald code */
}

.guide-list {
  list-style: none;
  padding: 0;
  margin: var(--vg-space-4) 0 0;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
}

.guide-list li {
  position: relative;
  padding-left: var(--vg-space-6);
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.guide-list li::before {
  content: '→';
  position: absolute;
  left: 0;
  color: var(--vg-blue-bright);
  font-weight: bold;
}

.text-dim {
  color: var(--vg-text-dim) !important;
}

/* ── AI agents / MCP Section (Terminal Playground) ── */
.terminal-playground {
  max-width: 800px;
  margin: 0 auto var(--vg-space-12);
  border-radius: var(--vg-radius-xl);
  border: 1px solid var(--vg-border-strong);
  overflow: hidden;
  box-shadow: var(--vg-shadow-lg);
  background: #060a13;
}

.terminal-tabs {
  display: flex;
  background: #0b1120;
  border-bottom: 1px solid var(--vg-border);
}

.terminal-tab {
  flex: 1;
  padding: 0.85rem;
  border: none;
  background: transparent;
  color: var(--vg-text-dim);
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-xs);
  cursor: pointer;
  transition:
    background var(--vg-dur),
    color var(--vg-dur-fast);
  text-align: center;
}

.terminal-tab:hover {
  background: rgba(255, 255, 255, 0.02);
  color: var(--vg-text-muted);
}

.terminal-tab--active {
  background: #060a13;
  color: var(--vg-cyan);
  border-bottom: 2px solid var(--vg-cyan);
}

.terminal-screen {
  display: flex;
  flex-direction: column;
}

.terminal-screen-header {
  padding: 0.5rem var(--vg-space-4);
  background: #0f172a;
  border-bottom: 1px solid var(--vg-border);
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot-win {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot-win--red {
  background: #ff5f56;
}
.dot-win--yellow {
  background: #ffbd2e;
}
.dot-win--green {
  background: #27c93f;
}

.terminal-screen-title {
  margin-left: auto;
  font-family: var(--vg-font-display);
  font-size: 10px;
  color: var(--vg-text-dim);
  letter-spacing: 0.05em;
}

.terminal-screen-body {
  padding: var(--vg-space-4);
  min-height: 250px;
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-sm);
  line-height: 1.5;
  color: var(--vg-text);
  overflow-x: auto;
}

.terminal-input-line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--vg-cyan);
}

.terminal-prompt {
  user-select: none;
  color: var(--vg-blue-bright);
}

.terminal-typed-input {
  word-break: break-all;
}

.terminal-cursor {
  display: inline-block;
  width: 7px;
  height: 15px;
  background: var(--vg-cyan);
  animation: blink 0.8s infinite;
}

.terminal-output {
  margin: var(--vg-space-4) 0 0;
  color: #34d399; /* emerald output */
  white-space: pre-wrap;
  word-break: break-all;
  overflow: visible;
}

.terminal-loading {
  margin-top: var(--vg-space-4);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--vg-border);
  border-top-color: var(--vg-cyan);
  border-radius: 50%;
  animation: spin 1s infinite linear;
}

/* Fade transitions for JSON output */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* ── Logo wall (agents + stack) ── */
.logo-wall {
  list-style: none;
  margin: 0 auto;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: var(--vg-space-6);
  max-width: 920px;
}

/* ── CTA ── */
.cta {
  max-width: var(--vg-maxw);
  margin: var(--vg-space-section) auto 0;
  padding: 0 var(--vg-space-6);
}
.cta__inner {
  position: relative;
  overflow: hidden;
  text-align: center;
  padding: clamp(2.5rem, 2rem + 4vw, 5rem) var(--vg-space-6);
  border-radius: var(--vg-radius-xl);
  border: 1px solid var(--vg-border-strong);
  background:
    radial-gradient(70% 120% at 50% 0%, rgba(59, 130, 246, 0.22), transparent 70%),
    var(--vg-surface);
}
.cta__title {
  margin: 0;
  font-size: var(--vg-text-2xl);
}
.cta__sub {
  margin: 0.75rem auto 1.75rem;
  max-width: 32rem;
  color: var(--vg-text-muted);
}

/* ── Footer ── */
.lp-footer {
  max-width: var(--vg-maxw);
  margin: var(--vg-space-section) auto 0;
  padding: var(--vg-space-8) var(--vg-space-6) var(--vg-space-12);
  border-top: 1px solid var(--vg-border);
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-8);
}
.footer-top {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--vg-space-8);
}
.footer-brand {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
  max-width: 300px;
}
.lp-footer__note {
  color: var(--vg-text-dim);
  font-size: var(--vg-text-sm);
}
.footer-links {
  display: flex;
  gap: var(--vg-space-12);
}
.footer-col {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
}
.footer-col h4 {
  margin: 0 0 0.5rem;
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
  font-family: var(--vg-font-display);
}
.footer-col a {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  text-decoration: none;
  transition: color var(--vg-dur-fast);
}
.footer-col a:hover {
  color: var(--vg-blue-bright);
}
.footer-bottom {
  border-top: 1px solid var(--vg-border);
  padding-top: var(--vg-space-6);
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
  text-align: center;
}
.nexus-card {
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-lg);
  padding: var(--vg-space-6);
  margin: 0 auto;
  max-width: 800px;
}

/* ── Keyframes ── */
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(0.8);
  }
}
@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}
@keyframes bob {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

/* ── Responsive ── */
@media (max-width: 920px) {
  .hero {
    grid-template-columns: 1fr;
    gap: var(--vg-space-8);
  }
  .hero__visual {
    order: -1;
    max-width: 380px;
    margin: 0 auto;
    width: 100%;
  }
  .goals-grid {
    grid-template-columns: 1fr;
    gap: var(--vg-space-8);
  }
  .bento {
    grid-template-columns: repeat(2, 1fr);
  }
  .card--wide,
  .card--tall,
  .card--full {
    grid-column: auto;
    grid-row: auto;
  }
  .stepper-layout {
    grid-template-columns: 1fr;
    gap: var(--vg-space-8);
  }
  .stepper-preview {
    order: -1;
  }
  .guide-box {
    grid-template-columns: 1fr;
  }
  .guide-tabs {
    flex-direction: row;
    overflow-x: auto;
    border-right: none;
    border-bottom: 1px solid var(--vg-border);
  }
  .guide-tab {
    padding: var(--vg-space-4);
    border-left: none;
    border-bottom: 3px solid transparent;
  }
  .guide-tab--active {
    border-bottom-color: var(--vg-blue-bright);
  }
}

.lp-nav__actions {
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
}

@media (max-width: 600px) {
  .lp-nav__links {
    display: none;
  }
  .hero__stats {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--vg-space-6);
  }
  .bento {
    grid-template-columns: 1fr;
  }
}
</style>
