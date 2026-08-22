import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import BrandMark from '../BrandMark.vue'

function makeRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
}

describe('BrandMark', () => {
  it('links only the glyph when a destination is provided', async () => {
    const router = makeRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(BrandMark, {
      props: { glyphTo: '/', glyphAriaLabel: 'VibeGraph home' },
      global: { plugins: [router] },
    })

    const glyphLink = wrapper.get('a.brand__glyph-link')
    expect(glyphLink.attributes('href')).toBe('/')
    expect(glyphLink.attributes('aria-label')).toBe('VibeGraph home')
    expect(glyphLink.find('img.brand__glyph').exists()).toBe(true)
    expect(glyphLink.find('.brand__word').exists()).toBe(false)
    expect(wrapper.get('.brand__word').text()).toBe('VibeGraph')
  })

  it('renders no link when a destination is omitted', () => {
    const wrapper = mount(BrandMark)

    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.find('img.brand__glyph').exists()).toBe(true)
    expect(wrapper.get('.brand__word').text()).toBe('VibeGraph')
  })
})
