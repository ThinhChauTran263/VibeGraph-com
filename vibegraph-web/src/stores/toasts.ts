import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastKind = 'info' | 'success' | 'error'

export interface ToastActionRoute {
  name: string
  params?: Record<string, string>
}

export interface Toast {
  id: number
  kind: ToastKind
  title: string
  message?: string
  /** Auto-dismiss delay in ms; 0 keeps the toast until dismissed. */
  durationMs: number
  actionLabel?: string
  actionRoute?: ToastActionRoute
}

export interface ToastInput {
  kind: ToastKind
  title: string
  message?: string
  durationMs?: number
  actionLabel?: string
  actionRoute?: ToastActionRoute
}

const DEFAULT_DURATION_MS = 6000

/**
 * Lightweight toast system (no external library, per project rules).
 *
 * `push` returns the toast id so callers can `update` the same toast in
 * place (e.g. flip it into a finite-duration toast later) or `dismiss` it.
 */
export const useToasts = defineStore('toasts', () => {
  const toasts = ref<Toast[]>([])
  const timers = new Map<number, ReturnType<typeof setTimeout>>()
  let nextId = 1

  function dismiss(id: number): void {
    const timer = timers.get(id)
    if (timer) {
      clearTimeout(timer)
      timers.delete(id)
    }
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  function schedule(toast: Toast, durationMs: number): void {
    const existing = timers.get(toast.id)
    if (existing) clearTimeout(existing)
    timers.set(
      toast.id,
      setTimeout(() => dismiss(toast.id), durationMs),
    )
  }

  function push(input: ToastInput): number {
    const toast: Toast = {
      id: nextId++,
      kind: input.kind,
      title: input.title,
      message: input.message,
      durationMs: input.durationMs ?? DEFAULT_DURATION_MS,
      actionLabel: input.actionLabel,
      actionRoute: input.actionRoute,
    }
    toasts.value = [...toasts.value, toast]
    if (toast.durationMs > 0) schedule(toast, toast.durationMs)
    return toast.id
  }

  function update(id: number, patch: Partial<Omit<Toast, 'id'>>): void {
    const toast = toasts.value.find((item) => item.id === id)
    if (!toast) return
    Object.assign(toast, patch)
    // Switching a sticky toast to a finite duration starts its dismiss clock.
    const nextDuration = patch.durationMs
    if (typeof nextDuration === 'number' && nextDuration > 0) {
      toast.durationMs = nextDuration
      schedule(toast, nextDuration)
    }
  }

  return { toasts, push, update, dismiss }
})
