import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DocsView from '../DocsView.vue'
import i18n from '@/language'

describe('DocsView', () => {
  afterEach(() => vi.useRealTimers())

  function mountDocs() {
    return mount(DocsView, {
      global: {
        plugins: [i18n],
        stubs: {
          RouterLink: { template: '<a :href="to"><slot /></a>', props: ['to'] },
          LanguageSelector: true,
        },
      },
    })
  }

  it('documents the production CLI and MCP commands', () => {
    const wrapper = mountDocs()
    const text = wrapper.text()
    expect(text).toContain('npm install -g vibegraph-cli')
    expect(text).toContain('vibegraph login')
    expect(text).toContain('npm install -g vibegraph-cli@latest')
    expect(text).toContain('vibegraph --version')
    expect(text).toContain('vibegraph key change')
    expect(text).toContain('vibegraph push')
    expect(text).toContain('vibegraph push --dry-run')
    expect(text).toContain('vibegraph watch')
    expect(text).toContain('VibeGraph CLI command reference')
    expect(text).toContain('vibegraph projects create --path <backendPath>')
    expect(text).toContain('vibegraph ignore init')
    expect(text).not.toContain('vibegraph push --root')
    expect(text).not.toContain('vibegraph watch [--root')
    expect(text).not.toContain('vibegraph projects push <projectId> --root')
    expect(text).toContain('vibegraph mcp install cursor')
    expect(text).toContain('vibegraph mcp install generic --path ./mcp.json')
    expect(text).toContain('vibegraph mcp config generic')
    expect(text).toContain('vibegraph mcp config vscode')
    expect(text).toContain('vibegraph mcp doctor')
    expect(text).toContain('list_projects')
    expect(text).toContain('C:\\\\Program Files\\\\nodejs\\\\node.exe')
    expect(text).toContain('AppData\\\\Roaming\\\\npm\\\\node_modules\\\\vibegraph-cli')
    expect(text).toContain('https://api.vibegraph.tech/mcp')
    expect(text).toContain('PROJECT_API_KEY')
    expect(text).toContain('EEXIST')
    expect(text).toContain('Unknown command: vibegraph')
    expect(wrapper.find('.docs-nav').find('.docs-nav__inner').exists()).toBe(true)
    expect(wrapper.find('a[download][href="/docs/vibegraph-ai-guide.md"]').exists()).toBe(true)
    expect(wrapper.find('a[download][href="/docs/vibegraph-ai-guide.html"]').exists()).toBe(true)
  })

  it('documents the published npm package and production install command', () => {
    const text = mountDocs().text()
    expect(text).toContain('vibegraph-cli@0.1.1')
    expect(text).toContain('npm install -g vibegraph-cli')
  })

  it('contains seven numbered video placeholders without starting a recording', () => {
    const wrapper = mountDocs()
    const cards = wrapper.findAll('.video-card')
    expect(cards).toHaveLength(7)
    expect(cards[0]?.text()).toContain('VIDEO 1')
    expect(cards[6]?.text()).toContain('VIDEO 7')
    expect(wrapper.text()).toContain('will be recorded later')
  })

  it('documents four import paths and the complete MCP tool registry', () => {
    const wrapper = mountDocs()
    expect(wrapper.findAll('.import-card')).toHaveLength(4)
    expect(wrapper.findAll('.mcp-tool-card')).toHaveLength(18)
    expect(wrapper.text()).toContain('CLI Push')
    expect(wrapper.text()).toContain('Archive upload')
    expect(wrapper.text()).toContain('Public GitHub')
    expect(wrapper.text()).toContain('Local backend path')
    expect(wrapper.text()).toContain('See the import hand-off')
    expect(wrapper.text()).toContain('Illustrative import flow — no upload or analysis started')
  })

  it('plays the import walkthrough without starting an upload', async () => {
    vi.useFakeTimers()
    const wrapper = mountDocs()
    await wrapper.get('.import-demo .simulation-button').trigger('click')
    vi.advanceTimersByTime(8_000)
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.import-demo__step--active')).toHaveLength(4)
    expect(wrapper.text()).toContain('Graph becomes available to dashboard/MCP')
  })

  it('keeps the current section marked for navigation context', async () => {
    const wrapper = mountDocs()
    expect(wrapper.find('.docs-sidebar a.is-active').attributes('href')).toBe('#status')
  })

  it('runs an explicitly illustrative MCP conversation simulation', async () => {
    vi.useFakeTimers()
    const wrapper = mountDocs()
    expect(wrapper.text()).toContain('Illustrative simulation — no live MCP call')
    expect(wrapper.find('.chat-line--tool').exists()).toBe(false)

    await wrapper.get('.simulation-shell .simulation-button').trigger('click')
    vi.advanceTimersByTime(10_000)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('.chat-line--user').text()).toContain('OrderService.createOrder')
    expect(wrapper.get('.chat-line--tool').text()).toContain('get_impact_analysis')
    expect(wrapper.get('.chat-line--agent').text()).toContain('direct and transitive callers')
  })
})
