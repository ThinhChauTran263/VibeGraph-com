<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BrandMark from '@/components/ui/BrandMark.vue'
import LanguageSelector from '@/components/ui/LanguageSelector.vue'
import { publicSiteCopy } from '@/content/publicSite'

const { locale } = useI18n({ useScope: 'global' })
const copy = computed(
  () => publicSiteCopy[locale.value as 'en-US' | 'vi-VN'] ?? publicSiteCopy['en-US'],
)

type SimulationPhase = 'idle' | 'typing' | 'tool' | 'result'

const selectedExample = ref(0)
const simulationPhase = ref<SimulationPhase>('idle')
const typedPrompt = ref('')
const typedImportCommand = ref('')
const importPhase = ref(-1)
const importPathIndex = ref(0)
const activeSection = ref('status')
let simulationTimer: ReturnType<typeof setTimeout> | undefined
let typingTimer: ReturnType<typeof setInterval> | undefined
let importTimer: ReturnType<typeof setInterval> | undefined
let importTypingTimer: ReturnType<typeof setInterval> | undefined
let importLoopTimer: ReturnType<typeof setTimeout> | undefined
let activeSectionFrame: number | undefined
let autoDemoObserver: IntersectionObserver | undefined
const codeAnimationCleanups: Array<() => void> = []
let importDemoVisible = false
let mcpDemoVisible = false

const activeExample = computed(() => copy.value.docs.mcpExamples[selectedExample.value])
const activeImportCard = computed(() => copy.value.docs.importCards[importPathIndex.value])
const visibleCommands = computed(() => copy.value.docs.commands.filter(([command]) =>
  !command.includes('projects create --path') && !command.includes('projects import-local'),
))
const docsSectionIds = ['status', 'install', 'downloads', 'import', 'push', 'commands', 'mcp', 'mcp-tools', 'keys', 'troubleshooting', 'videos']

// Keep the demos readable: a human-paced typewriter is easier to follow than a rapid stream.
const IMPORT_TYPING_INTERVAL_MS = 44
const PROMPT_TYPING_INTERVAL_MS = 46
const CODE_TYPING_INTERVAL_MS = 34
const DEMO_LOOP_PAUSE_MS = 3_500

function stopMcpSimulation() {
  if (simulationTimer) clearTimeout(simulationTimer)
  if (typingTimer) clearInterval(typingTimer)
  simulationTimer = undefined
  typingTimer = undefined
}

function stopImportWalkthrough() {
  if (importTimer) clearInterval(importTimer)
  if (importTypingTimer) clearInterval(importTypingTimer)
  if (importLoopTimer) clearTimeout(importLoopTimer)
  importTimer = undefined
  importTypingTimer = undefined
  importLoopTimer = undefined
}

function stopSimulation() {
  stopMcpSimulation()
  stopImportWalkthrough()
}

function runImportWalkthrough() {
  stopImportWalkthrough()
  importPhase.value = 0
  typedImportCommand.value = ''
  const command = activeImportCard.value?.[2] ?? ''
  const reducedMotion = typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reducedMotion) {
    typedImportCommand.value = command
    importPhase.value = copy.value.docs.importDemoSteps.length - 1
    return
  }
  let cursor = 0
  importTypingTimer = setInterval(() => {
    cursor += 1
    typedImportCommand.value = command.slice(0, cursor)
    if (cursor < command.length) return
    if (importTypingTimer) clearInterval(importTypingTimer)
    importTypingTimer = undefined
    importTimer = setInterval(() => {
      const lastStep = copy.value.docs.importDemoSteps.length - 1
      if (importPhase.value >= lastStep - 1) {
        importPhase.value = lastStep
        if (importTimer) clearInterval(importTimer)
        importTimer = undefined
        if (importDemoVisible) {
          importLoopTimer = setTimeout(runImportWalkthrough, DEMO_LOOP_PAUSE_MS)
        }
        return
      }
      importPhase.value += 1
    }, 850)
  }, IMPORT_TYPING_INTERVAL_MS)
}

function selectImportPath(index: number) {
  importPathIndex.value = index
  runImportWalkthrough()
}

function runSimulation(index = selectedExample.value) {
  stopMcpSimulation()
  selectedExample.value = index
  const example = copy.value.docs.mcpExamples[index]
  if (!example) return

  typedPrompt.value = ''
  simulationPhase.value = 'typing'
  const reducedMotion = typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches

  if (reducedMotion) {
    typedPrompt.value = example[1]
    simulationPhase.value = 'tool'
    simulationTimer = setTimeout(() => {
      simulationPhase.value = 'result'
      if (mcpDemoVisible) {
        simulationTimer = setTimeout(() => runSimulation(index), DEMO_LOOP_PAUSE_MS)
      }
    }, 250)
    return
  }

  let cursor = 0
  typingTimer = setInterval(() => {
    cursor += 1
    typedPrompt.value = example[1].slice(0, cursor)
    if (cursor >= example[1].length) {
      if (typingTimer) clearInterval(typingTimer)
      typingTimer = undefined
      simulationPhase.value = 'tool'
      simulationTimer = setTimeout(() => {
        simulationPhase.value = 'result'
        if (mcpDemoVisible) {
          simulationTimer = setTimeout(() => runSimulation(index), DEMO_LOOP_PAUSE_MS)
        }
      }, 650)
    }
  }, PROMPT_TYPING_INTERVAL_MS)
}

let sectionObserver: IntersectionObserver | undefined

function updateActiveSection() {
  if (window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 4) {
    activeSection.value = docsSectionIds[docsSectionIds.length - 1] ?? 'videos'
    return
  }
  const marker = 110
  let current = docsSectionIds[0] ?? 'status'
  for (const id of docsSectionIds) {
    const section = document.getElementById(id)
    if (!section) continue
    if (section.getBoundingClientRect().top <= marker) {
      current = id
    }
  }
  activeSection.value = current
}

function scheduleActiveSectionUpdate() {
  if (activeSectionFrame) cancelAnimationFrame(activeSectionFrame)
  activeSectionFrame = requestAnimationFrame(() => {
    activeSectionFrame = undefined
    updateActiveSection()
  })
}

function setupCodeAnimations() {
  if (typeof IntersectionObserver === 'undefined') return
  if (typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  document.querySelectorAll<HTMLElement>('.docs-content pre').forEach((pre) => {
    const code = pre.querySelector('code')
    const source = code?.textContent ?? ''
    if (!code || !source.trim()) return
    let timer: ReturnType<typeof setInterval> | undefined
    let played = false
    const play = () => {
      if (played) return
      played = true
      let cursor = 0
      code.textContent = ''
      pre.dataset.typeCode = 'typing'
      timer = setInterval(() => {
        cursor = Math.min(source.length, cursor + 1)
        code.textContent = source.slice(0, cursor)
        if (cursor >= source.length) {
          if (timer) clearInterval(timer)
          timer = undefined
          delete pre.dataset.typeCode
        }
      }, CODE_TYPING_INTERVAL_MS)
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        play()
        observer.disconnect()
      }
    }, { threshold: 0.22 })
    observer.observe(pre)
    codeAnimationCleanups.push(() => {
      observer.disconnect()
      if (timer) clearInterval(timer)
      code.textContent = source
      delete pre.dataset.typeCode
    })
  })
}

onMounted(() => {
  updateActiveSection()
  window.addEventListener('scroll', scheduleActiveSectionUpdate, { passive: true })
  window.addEventListener('resize', scheduleActiveSectionUpdate)

  if (typeof IntersectionObserver !== 'undefined') {
    sectionObserver = new IntersectionObserver(() => scheduleActiveSectionUpdate(), { threshold: [0, 0.1] })
    for (const id of docsSectionIds) {
      const section = document.getElementById(id)
      if (section) sectionObserver.observe(section)
    }
    autoDemoObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.target.classList.contains('import-demo')) {
          importDemoVisible = entry.isIntersecting
          if (entry.isIntersecting) runImportWalkthrough()
          else stopImportWalkthrough()
        }
        if (entry.target.classList.contains('simulation-shell')) {
          mcpDemoVisible = entry.isIntersecting
          if (entry.isIntersecting) runSimulation()
          else stopMcpSimulation()
        }
      }
    }, { threshold: 0.18 })
    const importDemo = document.querySelector('.import-demo')
    const mcpDemo = document.querySelector('.simulation-shell')
    if (importDemo) autoDemoObserver.observe(importDemo)
    if (mcpDemo) autoDemoObserver.observe(mcpDemo)
  }
  setupCodeAnimations()
})

onBeforeUnmount(() => {
  stopSimulation()
  if (activeSectionFrame) cancelAnimationFrame(activeSectionFrame)
  window.removeEventListener('scroll', scheduleActiveSectionUpdate)
  window.removeEventListener('resize', scheduleActiveSectionUpdate)
  sectionObserver?.disconnect()
  autoDemoObserver?.disconnect()
  codeAnimationCleanups.splice(0).forEach((cleanup) => cleanup())
})
</script>

<template>
  <div class="docs-page">
    <header class="docs-nav">
      <div class="docs-nav__inner">
        <BrandMark
          class="docs-brand"
          :size="28"
          glyph-to="/"
          glyph-aria-label="VibeGraph landing page"
          wordmark-to="/dashboard"
          wordmark-aria-label="VibeGraph dashboard"
        />
        <nav :aria-label="copy.docs.navLabel">
          <a href="#install">Install</a><a href="#mcp">MCP</a><a href="#videos">Videos</a>
        </nav>
        <div class="docs-actions">
          <a class="download-link" href="/docs/vibegraph-ai-guide.md" download>MD</a>
          <a class="download-link" href="/docs/vibegraph-ai-guide.html" download>HTML</a>
          <LanguageSelector /><RouterLink class="back-link" to="/">{{ copy.docs.back }}</RouterLink>
        </div>
      </div>
    </header>
    <main id="docs-main" class="docs-layout">
      <aside class="docs-sidebar" :aria-label="copy.docs.onPage">
        <strong>{{ copy.docs.titleLabel }}</strong
        ><a href="#status" :class="{ 'is-active': activeSection === 'status' }" :aria-current="activeSection === 'status' ? 'location' : undefined">{{ copy.docs.sideLinks[0] }}</a
          ><a href="#install" :class="{ 'is-active': activeSection === 'install' }" :aria-current="activeSection === 'install' ? 'location' : undefined">{{ copy.docs.sideLinks[1] }}</a
          ><a href="#downloads" :class="{ 'is-active': activeSection === 'downloads' }" :aria-current="activeSection === 'downloads' ? 'location' : undefined">{{ copy.docs.sideLinks[2] }}</a
          ><a href="#import" :class="{ 'is-active': activeSection === 'import' }" :aria-current="activeSection === 'import' ? 'location' : undefined">{{ copy.docs.importNav }}</a
          ><a href="#push" :class="{ 'is-active': activeSection === 'push' }" :aria-current="activeSection === 'push' ? 'location' : undefined">{{ copy.docs.sideLinks[3] }}</a
          ><a href="#commands" :class="{ 'is-active': activeSection === 'commands' }" :aria-current="activeSection === 'commands' ? 'location' : undefined">{{ copy.docs.sideLinks[4] }}</a
          ><a href="#mcp" :class="{ 'is-active': activeSection === 'mcp' }" :aria-current="activeSection === 'mcp' ? 'location' : undefined">{{ copy.docs.sideLinks[5] }}</a
          ><a href="#mcp-tools" :class="{ 'is-active': activeSection === 'mcp-tools' }" :aria-current="activeSection === 'mcp-tools' ? 'location' : undefined">{{ copy.docs.sideLinks[6] }}</a
          ><a href="#keys" :class="{ 'is-active': activeSection === 'keys' }" :aria-current="activeSection === 'keys' ? 'location' : undefined">{{ copy.docs.sideLinks[7] }}</a
          ><a href="#troubleshooting" :class="{ 'is-active': activeSection === 'troubleshooting' }" :aria-current="activeSection === 'troubleshooting' ? 'location' : undefined">{{ copy.docs.sideLinks[8] }}</a
          ><a href="#videos" :class="{ 'is-active': activeSection === 'videos' }" :aria-current="activeSection === 'videos' ? 'location' : undefined">{{ copy.docs.sideLinks[9] }}</a>
      </aside>
      <article class="docs-content">
        <p class="eyebrow">{{ copy.docs.eyebrow }}</p>
        <h1>{{ copy.docs.title }}</h1>
        <p class="intro">{{ copy.docs.intro }}</p>

        <section id="status" class="notice">
          <strong>{{ copy.docs.releaseTitle }}</strong>
          <p>{{ copy.docs.releaseBody }}</p>
        </section>

        <section id="install" class="doc-section">
          <h2>{{ copy.docs.installTitle }}</h2>
          <p>{{ copy.docs.installLead }}</p>
          <pre><code>npm install -g vibegraph-cli
vibegraph config set-url https://api.vibegraph.tech
vibegraph login
vibegraph key list</code></pre>
          <p>{{ copy.docs.installBody }}</p>
          <h3>{{ copy.docs.updateTitle }}</h3>
          <p>{{ copy.docs.updateBody }}</p>
          <pre><code>vibegraph --version
npm uninstall -g vibegraph-cli
npm install -g vibegraph-cli@latest
vibegraph --version</code></pre>
        </section>

        <section id="downloads" class="doc-section download-section">
          <h2>{{ copy.docs.downloadsTitle }}</h2>
          <p>{{ copy.docs.downloadsLead }}</p>
          <div class="download-cards">
            <a class="download-card" href="/docs/vibegraph-ai-guide.md" download>
              <span>01 / MARKDOWN</span><strong>{{ copy.docs.downloadsMd }}</strong><small>For AI context, repositories and plain-text review.</small>
            </a>
            <a class="download-card" href="/docs/vibegraph-ai-guide.html" download>
              <span>02 / HTML</span><strong>{{ copy.docs.downloadsHtml }}</strong><small>For offline reading, sharing and a visual handoff.</small>
            </a>
          </div>
          <p class="doc-note">{{ copy.docs.downloadsNote }}</p>
        </section>

        <section id="import" class="doc-section">
          <h2>{{ copy.docs.importTitle }}</h2>
          <p>{{ copy.docs.importLead }}</p>
          <div class="import-card-grid">
            <article v-for="card in copy.docs.importCards" :key="card[0]" class="import-card">
              <span class="import-card__label">{{ card[0] }}</span>
              <h3>{{ card[1] }}</h3>
              <pre><code>{{ card[2] }}</code></pre>
              <p>{{ card[3] }}</p>
            </article>
          </div>
          <p class="doc-note">{{ copy.docs.importNote }}</p>
          <div class="import-demo" aria-live="polite">
            <div class="import-demo__sources" role="tablist" :aria-label="copy.docs.importTitle">
              <button
                v-for="(card, index) in copy.docs.importCards"
                :key="card[0]"
                class="import-demo__source"
                :class="{ 'import-demo__source--active': index === importPathIndex }"
                type="button"
                role="tab"
                :aria-selected="index === importPathIndex"
                @click="selectImportPath(index)"
              >
                {{ card[0] }}
              </button>
            </div>
            <div class="import-demo__header">
              <div>
                <h3>{{ copy.docs.importDemoTitle }}</h3>
                <p>{{ copy.docs.importDemoLead }}</p>
              </div>
            </div>
            <div class="simulation-disclaimer">{{ copy.docs.importDemoDisclaimer }}</div>
            <div class="import-demo__source-preview">
              <strong>{{ activeImportCard?.[1] }}</strong>
              <code>{{ importPhase >= 0 ? typedImportCommand : activeImportCard?.[2] }}<span v-if="importPhase === 0" class="typing-caret">▌</span></code>
            </div>
            <div class="import-demo__rail">
              <div
                v-for="(step, index) in copy.docs.importDemoSteps"
                :key="step"
                class="import-demo__step"
                :class="{ 'import-demo__step--active': index <= importPhase }"
              >
                <span>{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ step }}</strong>
              </div>
            </div>
            <p v-if="importPhase < 0" class="simulation-empty">{{ copy.docs.importDemoIdle }}</p>
            <p v-else class="simulation-status">{{ copy.docs.importDemoSteps[importPhase] }}</p>
          </div>
        </section>

        <section id="push" class="doc-section">
          <h2>{{ copy.docs.pushTitle }}</h2>
          <p>{{ copy.docs.pushLead }}</p>
          <h3>{{ copy.docs.firstPushTitle }}</h3>
          <p>{{ copy.docs.firstPushBody }}</p>
          <pre><code>cd &lt;LOCAL_PROJECT_FOLDER&gt;
vibegraph login
vibegraph key status
vibegraph push --dry-run
vibegraph push</code></pre>
          <h3>{{ copy.docs.updatePushTitle }}</h3>
          <p>{{ copy.docs.updatePushBody }}</p>
          <pre><code>cd &lt;LOCAL_PROJECT_FOLDER&gt;
vibegraph push
vibegraph watch</code></pre>
          <p>{{ copy.docs.pushBody }}</p>
          <h3>{{ copy.docs.pushMcpTitle }}</h3>
          <p>{{ copy.docs.pushMcpBody }}</p>
          <pre><code>vibegraph mcp install cursor
vibegraph mcp doctor
# Edit code, then update VibeGraph again:
vibegraph push</code></pre>
        </section>

        <section id="commands" class="doc-section">
          <h2>{{ copy.docs.commandsTitle }}</h2>
          <p>{{ copy.docs.commandsBody }}</p>
          <div class="command-list">
            <article v-for="command in visibleCommands" :key="command[0]" class="command-item">
              <code>{{ command[0] }}</code>
              <p>{{ command[1] }}</p>
            </article>
          </div>
        </section>

        <section id="mcp" class="doc-section">
          <h2>{{ copy.docs.mcpTitle }}</h2>
          <p>{{ copy.docs.mcpLead }}</p>
          <h3>{{ copy.docs.autoMcpTitle }}</h3>
          <p>{{ copy.docs.autoMcpBody }}</p>
          <pre><code>vibegraph mcp install cursor
vibegraph mcp install vscode
vibegraph mcp install generic --path ./mcp.json</code></pre>
          <h3>{{ copy.docs.generatedMcpTitle }}</h3>
          <p>{{ copy.docs.generatedMcpBody }}</p>
          <pre><code>vibegraph mcp config cursor
vibegraph mcp config vscode
vibegraph mcp config generic</code></pre>
          <h3>{{ copy.docs.localStdioTitle }}</h3>
          <p>{{ copy.docs.localStdioBody }}</p>
          <pre><code>{
  "mcpServers": {
    "vibegraph": {
      "command": "C:\\Program Files\\nodejs\\node.exe",
      "args": [
        "C:\\Users\\&lt;USERNAME&gt;\\AppData\\Roaming\\npm\\node_modules\\vibegraph-cli\\bin\\vibegraph.js",
        "mcp-proxy",
        "--stdio"
      ]
    }
  }
}</code></pre>
          <p>{{ copy.docs.mcpBody }}</p>
          <h3>{{ copy.docs.manualTitle }}</h3>
          <pre><code>{
  "mcpServers": {
    "vibegraph": {
      "url": "https://api.vibegraph.tech/mcp",
      "transport": "streamable-http",
      "headers": { "X-API-Key": "&lt;PROJECT_API_KEY&gt;" }
    }
  }
}</code></pre>
          <p>{{ copy.docs.manualBody }}</p>
        </section>

        <section id="mcp-tools" class="doc-section">
          <h2>{{ copy.docs.mcpToolsTitle }}</h2>
          <p>{{ copy.docs.mcpToolsLead }}</p>
          <div class="mcp-tool-list">
            <article v-for="tool in copy.docs.mcpTools" :key="tool[0]" class="mcp-tool-card">
              <code>{{ tool[0] }}</code>
              <p>{{ tool[1] }}</p>
              <small>{{ tool[2] }}</small>
            </article>
          </div>
          <h3>{{ copy.docs.mcpExamplesTitle }}</h3>
          <p>{{ copy.docs.mcpExamplesLead }}</p>
          <div class="simulation-shell" aria-live="polite">
            <div class="simulation-toolbar">
              <div class="simulation-tabs" role="tablist" :aria-label="copy.docs.mcpExamplesTitle">
                <button
                  v-for="(example, index) in copy.docs.mcpExamples"
                  :key="example[0]"
                  class="simulation-tab"
                  :class="{ 'simulation-tab--active': index === selectedExample }"
                  type="button"
                  role="tab"
                  :aria-selected="index === selectedExample"
                  @click="runSimulation(index)"
                >
                  {{ example[0] }}
                </button>
              </div>
            </div>
            <div class="simulation-window">
              <div class="simulation-window__bar"><i></i><i></i><i></i><span>agent-session / illustrative</span></div>
              <div class="simulation-disclaimer">{{ copy.docs.simulationDisclaimer }}</div>
              <div v-if="simulationPhase !== 'idle'" class="chat-line chat-line--user">
                <strong>User</strong><p>{{ typedPrompt }}<span v-if="simulationPhase === 'typing'" class="typing-caret">▌</span></p>
              </div>
              <div v-if="simulationPhase === 'tool' || simulationPhase === 'result'" class="chat-line chat-line--tool">
                <strong>MCP → {{ activeExample?.[2] }}</strong><p>{{ copy.docs.simulationToolStatus }}</p>
              </div>
              <div v-if="simulationPhase === 'result'" class="chat-line chat-line--agent">
                <strong>Agent</strong><p>{{ activeExample?.[3] }}</p>
              </div>
              <div v-if="simulationPhase === 'idle'" class="simulation-empty">{{ copy.docs.simulationIdle }}</div>
              <div v-else-if="simulationPhase === 'typing'" class="simulation-status">{{ copy.docs.simulationTyping }}</div>
            </div>
          </div>
        </section>

        <section id="keys" class="doc-section">
          <h2>{{ copy.docs.keysTitle }}</h2>
          <pre><code>vibegraph key list
vibegraph key change
vibegraph auth status
vibegraph auth clear</code></pre>
          <p>{{ copy.docs.keysBody }}</p>
          <p>{{ copy.docs.keyMeaning }}</p>
          <h3>{{ copy.docs.creditTitle }}</h3>
          <p>{{ copy.docs.creditBody }}</p>
        </section>

        <section id="troubleshooting" class="doc-section">
          <h2>{{ copy.docs.troubleshootingTitle }}</h2>
          <p>{{ copy.docs.troubleshootingBody }}</p>
          <div class="troubleshooting-list">
            <article
              v-for="item in copy.docs.troubleshooting"
              :key="item[0]"
              class="troubleshooting-item"
            >
              <h3>{{ item[0] }}</h3>
              <p>{{ item[1] }}</p>
              <pre><code>{{ item[2] }}</code></pre>
            </article>
          </div>
        </section>

        <section id="videos" class="doc-section">
          <h2>{{ copy.docs.videosTitle }}</h2>
          <p>{{ copy.docs.videosBody }}</p>
          <div class="video-grid">
            <article v-for="video in copy.docs.videos" :key="video[0]" class="video-card">
              <span>VIDEO {{ video[0] }}</span>
              <h3>{{ video[1] }}</h3>
              <code>{{ video[2] }}</code>
              <p>{{ copy.docs.videoFooter }}</p>
            </article>
          </div>
        </section>
      </article>
    </main>
  </div>
</template>

<style scoped>
.docs-page {
  min-height: 100dvh;
  background:
    radial-gradient(circle at 10% 0%, rgba(59, 130, 246, 0.12), transparent 28rem), var(--vg-bg);
}
.docs-nav {
  position: sticky;
  top: 0;
  z-index: 5;
  width: 100%;
  border-bottom: 1px solid var(--vg-border);
  background: rgba(7, 11, 22, 0.72);
  backdrop-filter: blur(14px);
}
.docs-nav__inner {
  max-width: var(--vg-maxw);
  margin: 0 auto;
  padding: 0.9rem var(--vg-space-6);
  display: flex;
  align-items: center;
  gap: var(--vg-space-6);
}
.docs-brand {
  margin-left: -0.75rem;
}
.docs-nav nav {
  display: flex;
  gap: var(--vg-space-8);
  margin-left: auto;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}
.docs-nav nav a,
.back-link {
  transition: color var(--vg-dur-fast) var(--vg-ease-out);
}
.docs-nav nav a:hover,
.back-link:hover {
  color: var(--vg-text);
}
.docs-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.download-link {
  color: var(--vg-text-muted);
  font: 700 0.68rem var(--vg-font-mono);
  letter-spacing: 0.06em;
}
.download-link:hover { color: var(--vg-green-bright); }
.back-link {
  color: var(--vg-text-muted);
  font-size: 0.85rem;
}
.docs-layout {
  max-width: var(--vg-maxw);
  margin: 0 auto;
  padding: 3rem 1.5rem 7rem;
  display: grid;
  grid-template-columns: 220px minmax(0, 760px);
  gap: 4rem;
}
.docs-sidebar,
.docs-content {
  min-width: 0;
}
.docs-sidebar {
  position: sticky;
  top: 5.5rem;
  align-self: start;
  display: grid;
  gap: 0.8rem;
  color: var(--vg-text-dim);
  font-size: 0.84rem;
}
.docs-sidebar strong {
  color: var(--vg-text);
  margin-bottom: 0.3rem;
}
.docs-sidebar a {
  overflow-wrap: anywhere;
}
.docs-sidebar a:hover {
  color: var(--vg-green-bright);
}
.docs-sidebar a.is-active {
  color: var(--vg-green-bright);
  font-weight: 700;
}
.eyebrow {
  margin: 0 0 1rem;
  color: var(--vg-green-bright);
  font: 600 0.72rem/1.2 var(--vg-font-mono);
  letter-spacing: 0.12em;
}
.docs-content h1 {
  margin: 0;
  font-size: clamp(2.4rem, 5vw, 4.5rem);
  letter-spacing: -0.06em;
}
.intro {
  max-width: 680px;
  margin: 1.25rem 0 3rem;
  color: var(--vg-text-muted);
  font-size: 1.1rem;
  line-height: 1.75;
}
.notice {
  padding: 1.2rem;
  border: 1px solid rgba(251, 191, 36, 0.45);
  border-radius: var(--vg-radius);
  background: rgba(251, 191, 36, 0.08);
  color: var(--vg-text-muted);
}
.notice strong {
  color: var(--vg-amber);
}
.notice p {
  margin: 0.6rem 0 0;
  line-height: 1.7;
}
.doc-section {
  padding-top: 4rem;
  scroll-margin-top: 5.5rem;
}
.doc-section h2 {
  margin: 0 0 0.7rem;
  font-size: 2rem;
}
.doc-section h3 {
  margin-top: 2rem;
}
.doc-section p {
  color: var(--vg-text-muted);
  line-height: 1.75;
}
.doc-section code,
.video-card code {
  font-family: var(--vg-font-mono);
  color: var(--vg-green-bright);
}
.doc-section pre {
  margin: 1rem 0;
  padding: 1.2rem;
  overflow-x: auto;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: #050810;
  font: 0.84rem/1.7 var(--vg-font-mono);
}
.doc-section pre[data-type-code='typing']::after {
  content: '▌';
  color: var(--vg-green-bright);
  animation: caret-blink 0.8s steps(1) infinite;
}
.download-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin: 1.35rem 0;
}
.download-card {
  display: grid;
  gap: 0.65rem;
  min-width: 0;
  padding: 1.1rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: rgba(39, 47, 66, 0.2);
  transition: transform var(--vg-dur-fast) var(--vg-ease-out), border-color var(--vg-dur-fast) var(--vg-ease-out), background var(--vg-dur-fast) var(--vg-ease-out);
}
.download-card:hover {
  transform: translateY(-2px);
  border-color: var(--vg-green-bright);
  background: rgba(126, 247, 166, 0.07);
}
.download-card span {
  color: var(--vg-green-bright);
  font: 700 0.7rem var(--vg-font-mono);
  letter-spacing: 0.08em;
}
.download-card strong {
  color: var(--vg-text);
  font-size: 1.05rem;
}
.download-card small {
  color: var(--vg-text-dim);
  line-height: 1.55;
}
.video-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 1.5rem;
}
.video-card {
  padding: 1.1rem;
  border: 1px dashed var(--vg-border-strong);
  border-radius: var(--vg-radius);
  background: rgba(39, 47, 66, 0.25);
}
.video-card > span {
  color: var(--vg-green-bright);
  font: 700 0.7rem var(--vg-font-mono);
  letter-spacing: 0.1em;
}
.video-card h3 {
  margin: 0.7rem 0;
  font-size: 1rem;
}
.video-card code {
  display: block;
  overflow-wrap: anywhere;
  font-size: 0.75rem;
}
.video-card p {
  margin-bottom: 0;
  font-size: 0.82rem;
}
.troubleshooting-list {
  display: grid;
  gap: 1rem;
  margin-top: 1.5rem;
}
.troubleshooting-item {
  padding: 1rem 1.1rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: rgba(39, 47, 66, 0.2);
}
.troubleshooting-item h3 {
  margin: 0;
  font-size: 1rem;
}
.troubleshooting-item p {
  margin: 0.65rem 0;
}
.troubleshooting-item pre {
  margin: 0.75rem 0 0;
}
.command-list {
  display: grid;
  gap: 0.7rem;
  margin-top: 1.4rem;
}
.command-item {
  display: grid;
  grid-template-columns: minmax(180px, 260px) 1fr;
  gap: 1rem;
  align-items: start;
  padding: 0.9rem 1rem;
  border-bottom: 1px solid var(--vg-border);
}
.command-item code {
  overflow-wrap: anywhere;
}
.command-item p {
  margin: 0;
  font-size: 0.9rem;
}
.import-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 1.4rem;
}
.import-card {
  padding: 1.1rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: rgba(39, 47, 66, 0.2);
}
.import-card__label {
  color: var(--vg-amber);
  font: 700 0.72rem var(--vg-font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.import-card h3 {
  margin: 0.7rem 0;
  font-size: 1.05rem;
}
.import-card pre {
  margin: 0.8rem 0;
}
.import-card p {
  margin-bottom: 0;
  font-size: 0.9rem;
}
.doc-note {
  padding: 0.8rem 1rem;
  border-left: 2px solid var(--vg-amber);
  background: rgba(251, 191, 36, 0.07);
}
.import-demo {
  margin-top: 1.4rem;
  padding: 1rem 1.1rem 1.1rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius);
  background: rgba(5, 8, 16, 0.52);
}
.import-demo__sources {
  display: flex;
  gap: 0.5rem;
  overflow-x: auto;
  padding-bottom: 0.8rem;
  margin-bottom: 0.9rem;
  border-bottom: 1px solid var(--vg-border);
}
.import-demo__source {
  flex: 0 0 auto;
  border: 1px solid var(--vg-border-strong);
  border-radius: 999px;
  padding: 0.45rem 0.7rem;
  color: var(--vg-text-dim);
  background: transparent;
  font: 700 0.68rem var(--vg-font-mono);
  cursor: pointer;
}
.import-demo__source:hover,
.import-demo__source--active {
  color: var(--vg-text);
  border-color: var(--vg-amber);
  background: rgba(251, 191, 36, 0.1);
}
.import-demo__source-preview {
  display: grid;
  gap: 0.5rem;
  margin-top: 0.85rem;
  padding: 0.8rem;
  border-left: 2px solid var(--vg-amber);
  background: rgba(251, 191, 36, 0.05);
}
.import-demo__source-preview strong { font-size: 0.9rem; }
.import-demo__source-preview code {
  white-space: pre-wrap;
  color: var(--vg-green-bright);
  font: 0.75rem/1.6 var(--vg-font-mono);
}
.import-demo__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}
.import-demo__header h3 { margin: 0; }
.import-demo__header p { margin: 0.4rem 0 0; font-size: 0.88rem; }
.import-demo__rail {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.55rem;
  margin-top: 1rem;
}
.import-demo__step {
  display: grid;
  gap: 0.45rem;
  min-height: 4.4rem;
  padding: 0.7rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text-dim);
  background: rgba(39, 47, 66, 0.18);
  transition: border-color var(--vg-dur-fast) var(--vg-ease-out), color var(--vg-dur-fast) var(--vg-ease-out), background var(--vg-dur-fast) var(--vg-ease-out);
}
.import-demo__step span { color: var(--vg-text-dim); font: 700 0.7rem var(--vg-font-mono); }
.import-demo__step strong { font-size: 0.82rem; line-height: 1.35; }
.import-demo__step--active { border-color: var(--vg-green-bright); color: var(--vg-text); background: rgba(126, 247, 166, 0.08); }
.import-demo__step--active span { color: var(--vg-green-bright); }
.mcp-tool-list,
.mcp-example-list {
  display: grid;
  gap: 0.8rem;
  margin-top: 1.4rem;
}
.mcp-tool-card,
.mcp-example-card {
  padding: 1rem 1.1rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: rgba(39, 47, 66, 0.2);
}
.simulation-shell {
  margin-top: 1.4rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius);
  overflow: hidden;
  background: rgba(5, 8, 16, 0.6);
}
.simulation-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.8rem;
  border-bottom: 1px solid var(--vg-border);
  background: rgba(39, 47, 66, 0.24);
}
.simulation-tabs {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex-wrap: wrap;
}
.simulation-tab {
  border: 1px solid var(--vg-border-strong);
  border-radius: 999px;
  padding: 0.45rem 0.7rem;
  color: var(--vg-text-muted);
  background: transparent;
  font: 600 0.72rem var(--vg-font-mono);
  cursor: pointer;
}
.simulation-tab:hover,
.simulation-tab--active {
  color: var(--vg-text);
  border-color: var(--vg-green-bright);
  background: rgba(126, 247, 166, 0.1);
}
.simulation-window { padding: 1rem 1.1rem 1.1rem; }
.simulation-window__bar {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-bottom: 0.8rem;
  color: var(--vg-text-dim);
  font: 0.7rem var(--vg-font-mono);
}
.simulation-window__bar i {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
  background: var(--vg-border-strong);
}
.simulation-window__bar i:first-child { background: #fb7185; }
.simulation-window__bar i:nth-child(2) { background: var(--vg-amber); }
.simulation-window__bar i:nth-child(3) { background: var(--vg-green-bright); }
.simulation-window__bar span { margin-left: 0.35rem; }
.simulation-disclaimer {
  display: inline-flex;
  padding: 0.3rem 0.55rem;
  border: 1px dashed rgba(251, 191, 36, 0.5);
  border-radius: 999px;
  color: var(--vg-amber);
  font: 0.68rem var(--vg-font-mono);
}
.simulation-empty,
.simulation-status {
  margin-top: 1rem;
  padding: 1.2rem;
  border: 1px dashed var(--vg-border);
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text-dim);
  text-align: center;
  font-size: 0.85rem;
}
.simulation-status { color: var(--vg-blue); }
.typing-caret { color: var(--vg-green-bright); animation: caret-blink 0.8s steps(1) infinite; }
@keyframes caret-blink { 50% { opacity: 0; } }
.mcp-tool-card {
  display: grid;
  grid-template-columns: minmax(210px, 260px) 1fr;
  gap: 0.45rem 1rem;
}
.mcp-tool-card code {
  grid-row: span 2;
}
.mcp-tool-card p,
.mcp-tool-card small {
  margin: 0;
}
.mcp-tool-card small {
  color: var(--vg-text-dim);
  line-height: 1.55;
}
.mcp-example-card > span {
  color: var(--vg-green-bright);
  font: 700 0.72rem var(--vg-font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.chat-line {
  margin-top: 0.8rem;
  padding: 0.7rem 0.8rem;
  border-left: 2px solid var(--vg-border-strong);
  background: rgba(5, 8, 16, 0.45);
}
.chat-line strong {
  display: block;
  color: var(--vg-text);
  font: 600 0.72rem var(--vg-font-mono);
}
.chat-line p {
  margin: 0.25rem 0 0;
  font-size: 0.9rem;
}
.chat-line--user { border-color: var(--vg-amber); }
.chat-line--tool { border-color: var(--vg-blue); }
.chat-line--agent { border-color: var(--vg-green-bright); }
@media (max-width: 860px) {
  .docs-layout {
    grid-template-columns: 1fr;
    gap: 2rem;
  }
  .docs-sidebar {
    position: static;
    display: flex;
    flex-wrap: wrap;
    gap: 0.6rem 1rem;
  }
  .docs-sidebar strong {
    width: 100%;
  }
  .docs-nav nav {
    display: none;
  }
  .docs-actions {
    margin-left: auto;
  }
}
@media (max-width: 560px) {
  .docs-nav__inner,
  .docs-layout {
    padding-inline: 1rem;
  }
  .back-link {
    display: none;
  }
  .video-grid {
    grid-template-columns: 1fr;
  }
  .download-cards {
    grid-template-columns: 1fr;
  }
  .import-card-grid {
    grid-template-columns: 1fr;
  }
  .command-item {
    grid-template-columns: 1fr;
    gap: 0.45rem;
  }
  .mcp-tool-card {
    grid-template-columns: 1fr;
  }
  .simulation-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .import-demo__header {
    flex-direction: column;
  }
  .import-demo__rail {
    grid-template-columns: 1fr 1fr;
  }
  .mcp-tool-card code {
    grid-row: auto;
  }
  .doc-section {
    padding-top: 3rem;
  }
}
</style>
