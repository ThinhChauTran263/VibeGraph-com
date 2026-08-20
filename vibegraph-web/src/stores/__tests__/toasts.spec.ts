import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useToasts } from '../toasts'

describe('useToasts', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('pushes a toast and auto-dismisses after its duration', () => {
    const toasts = useToasts()
    const id = toasts.push({ kind: 'success', title: 'Done', durationMs: 1000 })

    expect(toasts.toasts).toHaveLength(1)
    expect(toasts.toasts[0]?.id).toBe(id)

    vi.advanceTimersByTime(1001)
    expect(toasts.toasts).toHaveLength(0)
  })

  it('keeps sticky toasts (durationMs 0) until dismissed', () => {
    const toasts = useToasts()
    const id = toasts.push({ kind: 'info', title: 'Importing svc', durationMs: 0 })

    vi.advanceTimersByTime(60_000)
    expect(toasts.toasts).toHaveLength(1)

    toasts.dismiss(id)
    expect(toasts.toasts).toHaveLength(0)
  })

  it('updates a toast in place and starts the dismiss clock when given a duration', () => {
    const toasts = useToasts()
    const id = toasts.push({ kind: 'info', title: 'Importing svc', durationMs: 0 })

    toasts.update(id, { message: 'still working' })
    expect(toasts.toasts[0]?.message).toBe('still working')
    expect(toasts.toasts).toHaveLength(1)

    toasts.update(id, { kind: 'success', title: 'svc ready', durationMs: 1000 })
    vi.advanceTimersByTime(1001)
    expect(toasts.toasts).toHaveLength(0)
  })
})
