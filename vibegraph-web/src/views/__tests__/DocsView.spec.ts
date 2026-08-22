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
    expect(text).toContain('vibegraph key change')
    expect(text).toContain('vibegraph push --root ./your-project')
    expect(text).toContain('vibegraph mcp install cursor')
    expect(text).toContain('https://vibegraph.tech/mcp')
    expect(text).toContain('PROJECT_API_KEY')
    expect(wrapper.find('.docs-nav').find('.docs-nav__inner').exists()).toBe(true)
  })

  it('discloses the npm publication gate instead of claiming the package is available', () => {
    const text = mountDocs().text()
    expect(text).toContain('returned HTTP 404 from the npm registry')
    expect(text).toContain('npm install -g ./vibegraph-cli')
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
