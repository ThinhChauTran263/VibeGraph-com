/**
 * Live online-users channel for the admin dashboard.
 *
 * Subscribes to the STOMP topic the backend `OnlineUserHistoryService` pushes
 * on every sampler tick. A connection failure is non-fatal: the dashboard keeps
 * its REST polling fallback while `status` is not `connected`.
 */

import type { Ref } from 'vue'
import { useAdminStore } from '@/stores/admin'
import type { AdminOnlineUsersEvent } from '@/types/api'
import {
  useWebSocket,
  type TopicSubscription,
  type UseWebSocketReturn,
  type WebSocketStatus,
} from '@/composables/useWebSocket'

/** Must match `OnlineUserHistoryService.ONLINE_USERS_TOPIC` on the backend. */
export const ADMIN_ONLINE_USERS_TOPIC = '/topic/admin/online-users'

export interface UseAdminRealtimeOptions {
  /** Test seam: inject a fake transport instead of opening a real socket. */
  ws?: UseWebSocketReturn
}

export interface UseAdminRealtimeReturn {
  status: Readonly<Ref<WebSocketStatus>>
  start: () => void
  stop: () => void
}

export function useAdminRealtime(options: UseAdminRealtimeOptions = {}): UseAdminRealtimeReturn {
  const adminStore = useAdminStore()
  const ws = options.ws ?? useWebSocket()
  let subscription: TopicSubscription | null = null
  let started = false

  function start(): void {
    if (started) return
    started = true
    subscription = ws.subscribe<AdminOnlineUsersEvent>(ADMIN_ONLINE_USERS_TOPIC, (event) => {
      adminStore.applyOnlineUsersEvent(event)
    })
    ws.connect().catch(() => {
      // Non-fatal: the dashboard falls back to REST polling while disconnected.
    })
  }

  function stop(): void {
    if (!started) return
    started = false
    subscription?.unsubscribe()
    subscription = null
    void ws.disconnect()
  }

  return { status: ws.status, start, stop }
}
