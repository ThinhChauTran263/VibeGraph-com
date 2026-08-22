import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DocsView from '../DocsView.vue'
import i18n from '@/language'

describe('DocsView', () => {
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
})
