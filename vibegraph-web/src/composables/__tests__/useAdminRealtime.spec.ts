import { describe, expect, it, beforeEach, vi } from 'vitest'
import { ref } from 'vue'
import type { Ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAdminRealtime, ADMIN_ONLINE_USERS_TOPIC } from '../useAdminRealtime'
import { useAdminStore } from '@/stores/admin'
import type { UseWebSocketReturn, WebSocketStatus } from '@/composables/useWebSocket'

interface FakeWsState {
  status: Ref<WebSocketStatus>
  captured: { topic: string; cb: (payload: unknown) => void } | null
  unsubscribed: boolean
}

function makeFakeWs(): { ws: UseWebSocketReturn; state: FakeWsState } {
  const state: FakeWsState = {
    status: ref<WebSocketStatus>('disconnected'),
    captured: null,
    unsubscribed: false,
  }
  const ws: UseWebSocketReturn = {
    status: state.status,
    error: ref<string | null>(null),
    connect: vi.fn(async () => {
      state.status.value = 'connected'
    }),
    disconnect: vi.fn(async () => {}),
    subscribe: <T>(topic: string, cb: (payload: T) => void) => {
      state.captured = { topic, cb: cb as (payload: unknown) => void }
      return {
        active: ref(true),
        unsubscribe: () => {
          state.unsubscribed = true
        },
      }
    },
  }
  return { ws, state }
}

describe('useAdminRealtime', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('subscribes to the admin topic and applies pushed snapshots', async () => {
    const { ws, state } = makeFakeWs()
    const store = useAdminStore()
    store.overview = {
      totalUsers: 10,
      onlineUsers: 1,
      totalProjects: 0,
      totalReports: 0,
      openReports: 0,
      blockedUsers: 0,
      timestamp: null,
      onlineUserHistory: [],
    }
    const realtime = useAdminRealtime({ ws })

    realtime.start()
    await Promise.resolve()

    expect(state.captured?.topic).toBe(ADMIN_ONLINE_USERS_TOPIC)
    state.captured?.cb({
      onlineUsers: 7,
      capturedAt: '2026-07-17T13:05:30Z',
      samples: [{ label: '2026-07-17T13:05:00Z', value: 7, period: 'minute' }],
    })
    expect(store.overview?.onlineUsers).toBe(7)
    expect(store.overview?.onlineUserHistory).toHaveLength(1)
  })

  it('treats a failed connect as non-fatal', async () => {
    const { ws, state } = makeFakeWs()
    ;(ws.connect as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new Error('WebSocket connection failed.'),
    )
    const realtime = useAdminRealtime({ ws })

    realtime.start()
    await Promise.resolve()
    await Promise.resolve()

    expect(state.status.value).toBe('disconnected')
    expect(realtime.status.value).toBe('disconnected')
  })

  it('unsubscribes and disconnects on stop', async () => {
    const { ws, state } = makeFakeWs()
    const realtime = useAdminRealtime({ ws })

    realtime.start()
    await Promise.resolve()
    realtime.stop()

    expect(state.unsubscribed).toBe(true)
    expect(ws.disconnect).toHaveBeenCalledTimes(1)
  })
})
