import { describe, expect, it } from 'vitest'
import { resolveLocalhostAwareUrl } from '../constants'

describe('resolveLocalhostAwareUrl', () => {
  it('keeps localhost on localhost', () => {
    expect(
      resolveLocalhostAwareUrl('http://localhost:8080', 'http://localhost:8080', 'localhost'),
    ).toBe('http://localhost:8080')
  })

  it('rewrites localhost URLs to 127.0.0.1 when the browser host is 127.0.0.1', () => {
    expect(
      resolveLocalhostAwareUrl('http://localhost:8080/ws/graph-updates', 'fallback', '127.0.0.1'),
    ).toBe('http://127.0.0.1:8080/ws/graph-updates')
  })

  it('leaves non-local URLs untouched', () => {
    expect(
      resolveLocalhostAwareUrl('https://api.example.com/base', 'fallback', '127.0.0.1'),
    ).toBe('https://api.example.com/base')
  })
})
