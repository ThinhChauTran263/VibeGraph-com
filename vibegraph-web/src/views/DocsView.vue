<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BrandMark from '@/components/ui/BrandMark.vue'
import LanguageSelector from '@/components/ui/LanguageSelector.vue'
import { publicSiteCopy } from '@/content/publicSite'

const { locale } = useI18n({ useScope: 'global' })
const copy = computed(
  () => publicSiteCopy[locale.value as 'en-US' | 'vi-VN'] ?? publicSiteCopy['en-US'],
)
</script>

<template>
  <div class="docs-page">
    <header class="docs-nav">
      <div class="docs-nav__inner">
        <BrandMark
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
          <LanguageSelector /><RouterLink class="back-link" to="/">{{ copy.docs.back }}</RouterLink>
        </div>
      </div>
    </header>
    <main id="docs-main" class="docs-layout">
      <aside class="docs-sidebar" :aria-label="copy.docs.onPage">
        <strong>{{ copy.docs.titleLabel }}</strong
        ><a href="#status">{{ copy.docs.sideLinks[0] }}</a
        ><a href="#install">{{ copy.docs.sideLinks[1] }}</a
        ><a href="#push">{{ copy.docs.sideLinks[2] }}</a
        ><a href="#commands">{{ copy.docs.sideLinks[3] }}</a
        ><a href="#mcp">{{ copy.docs.sideLinks[4] }}</a
        ><a href="#keys">{{ copy.docs.sideLinks[5] }}</a
        ><a href="#troubleshooting">{{ copy.docs.sideLinks[6] }}</a
        ><a href="#videos">{{ copy.docs.sideLinks[7] }}</a>
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
            <article v-for="command in copy.docs.commands" :key="command[0]" class="command-item">
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
  .command-item {
    grid-template-columns: 1fr;
    gap: 0.45rem;
  }
  .doc-section {
    padding-top: 3rem;
  }
}
</style>
