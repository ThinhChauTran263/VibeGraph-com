/**
 * Realtime graph-update consumer (T60, FE side).
 *
 * Subscribes to the STOMP topic `/topic/projects/{projectId}/updates`, validates
 * each payload at the boundary, and patches the graph store immutably via
 * `applyGraphUpdate`. Resubscribes when the watched project changes and tears
 * the subscription/connection down on scope dispose (component unmount).
 *
 * Note: this owns a SEPARATE `useWebSocket` instance from the archive-import
 * status flow, so it does not affect that behavior.
 *
 * The backend producer (FileChangeBroadcaster, wired to the file watcher) is implemented
 * and emits FULL_UPDATE/INCREMENTAL events on real source edits for locally-watched
 * projects; the consumer is also exercised by unit tests against a mocked transport.
 */

import { onScopeDispose, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { useGraphStore } from '@/stores/graph'
import { useWebSocket, type TopicSubscription, type UseWebSocketReturn } from '@/composables/useWebSocket'
import { applyGraphUpdate, parseGraphUpdateEvent } from '@/lib/graphPatch'
import { bumpGraphVersion } from '@/lib/graphVersion'
import type { GraphUpdateEvent } from '@/types/graph'

export interface UseGraphRealtimeOptions {
  /** Test seam: inject a WebSocket instance instead of creating a real one. */
  ws?: UseWebSocketReturn
  /** Connect the transport automatically when a project is set. Default true. */
  autoConnect?: boolean
  /**
   * Invoked after the store has been patched with a validated event. Lets the canvas apply the
   * same change in place to the live Sigma graph (no full rebuild → camera/zoom/layout preserved).
   */
  onPatched?: (event: GraphUpdateEvent) => void
}

export function useGraphRealtime(
  projectId: MaybeRefOrGetter<string | null | undefined>,
  options: UseGraphRealtimeOptions = {},
) {
  const store = useGraphStore()
  const ws = options.ws ?? useWebSocket()
  const autoConnect = options.autoConnect !== false

  /** Last consumer-side error (malformed payload). Distinct from `ws.error`. */
  const lastError = ref<string | null>(null)

  let subscription: TopicSubscription | null = null
  // The project this consumer is currently bound to. Used as a stale-event
  // guard so a late message for a previous project is never applied.
  let currentProjectId: string | null = null

  function handleEvent(payload: unknown): void {
    const event = parseGraphUpdateEvent(payload)
    if (!event) {
      lastError.value = 'Received a malformed graph update.'
      return
    }
    // Stale guard: ignore events whose projectId is not the one we watch now.
    if (event.projectId !== currentProjectId) return
    if (event.type === 'FULL_UPDATE') {
      // The payload is capped at the WebSocket boundary; carry its truncation meta into the
      // shared payloadMeta flow so the Safe Mode banner / renderInfo stay truthful.
      store.payloadMeta = event.graph.meta ?? null
    }
    store.graphData = applyGraphUpdate(store.graphData, event)
    // A live graph change invalidates derived diagram caches.
    bumpGraphVersion()
    // Let the canvas mirror this change in place on the live Sigma graph.
    options.onPatched?.(event)
  }

  function teardownSubscription(): void {
    if (subscription) {
      subscription.unsubscribe()
      subscription = null
    }
  }

  function subscribeToProject(pid: string): void {
    currentProjectId = pid
    // `subscribe` tolerates being called before `connect()` resolves; the
    // pending subscription is flushed on connect.
    subscription = ws.subscribe(`/topic/projects/${pid}/updates`, handleEvent)
    if (autoConnect) {
      void ws.connect().catch((err: unknown) => {
        lastError.value = err instanceof Error ? err.message : 'WebSocket connection failed.'
      })
    }
  }

  const stopWatch = watch(
    () => toValue(projectId),
    (pid) => {
      // Project changed (or first run): drop the old topic before binding the new one.
      teardownSubscription()
      currentProjectId = null
      lastError.value = null
      if (pid) subscribeToProject(pid)
    },
    { immediate: true },
  )

  function stop(): void {
    stopWatch()
    teardownSubscription()
    currentProjectId = null
    if (autoConnect) void ws.disconnect()
  }

  onScopeDispose(stop)

  return {
    status: ws.status,
    error: ws.error,
    lastError,
    stop,
  }
}
