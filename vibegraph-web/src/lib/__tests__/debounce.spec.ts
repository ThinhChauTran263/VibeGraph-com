import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

import { debounce } from '../debounce'

describe('debounce', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('runs once after the wait with the latest args when called in a burst', () => {
    const fn = vi.fn()
    const d = debounce(fn, 200)

    d(1)
    d(2)
    d(3)
    expect(fn).not.toHaveBeenCalled()

    vi.advanceTimersByTime(200)
    expect(fn).toHaveBeenCalledTimes(1)
    expect(fn).toHaveBeenCalledWith(3)
  })

  it('cancel() prevents a pending invocation', () => {
    const fn = vi.fn()
    const d = debounce(fn, 200)

    d('x')
    d.cancel()
    vi.advanceTimersByTime(500)

    expect(fn).not.toHaveBeenCalled()
  })

  it('restarts the timer on each call (trailing edge)', () => {
    const fn = vi.fn()
    const d = debounce(fn, 200)

    d('a')
    vi.advanceTimersByTime(150)
    d('b')
    vi.advanceTimersByTime(150)
    // 300ms elapsed total but only 150ms since the last call -> not yet fired.
    expect(fn).not.toHaveBeenCalled()

    vi.advanceTimersByTime(50)
    expect(fn).toHaveBeenCalledExactlyOnceWith('b')
  })

  it('cancel() is safe when nothing is pending', () => {
    const fn = vi.fn()
    const d = debounce(fn, 100)
    expect(() => d.cancel()).not.toThrow()
  })
})
