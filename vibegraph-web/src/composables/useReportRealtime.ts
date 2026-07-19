import { computed, onScopeDispose, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue'
import {
  useWebSocket,
  type TopicSubscription,
  type UseWebSocketReturn,
} from '@/composables/useWebSocket'
import type { ReportRealtimeEvent } from '@/types/api'

export interface UseReportRealtimeOptions {
  ws?: UseWebSocketReturn
  autoConnect?: boolean
  onEvent?: (event: ReportRealtimeEvent) => void
}

export function useReportRealtime(
  reportId: MaybeRefOrGetter<string | null | undefined>,
  options: UseReportRealtimeOptions = {},
) {
  const ws = options.ws ?? useWebSocket()
  const autoConnect = options.autoConnect !== false
  const lastError = ref<string | null>(null)

  const subscription = shallowRef<TopicSubscription | null>(null)
  const active = computed(() => subscription.value?.active.value ?? false)
  let currentReportId: string | null = null

  function teardownSubscription(): void {
    if (subscription.value) {
      subscription.value.unsubscribe()
      subscription.value = null
    }
  }

  function handleEvent(event: ReportRealtimeEvent): void {
    if (!event || event.reportId !== currentReportId) return
    options.onEvent?.(event)
  }

  function subscribeToReport(id: string): void {
    currentReportId = id
    subscription.value = ws.subscribe<ReportRealtimeEvent>(`/topic/reports/${id}`, handleEvent)
    if (autoConnect) {
      void ws.connect().catch((err: unknown) => {
        lastError.value = err instanceof Error ? err.message : 'WebSocket connection failed.'
      })
    }
  }

  const stopWatch = watch(
    () => toValue(reportId),
    (id) => {
      teardownSubscription()
      currentReportId = null
      lastError.value = null
      if (id) subscribeToReport(id)
    },
    { immediate: true },
  )

  function stop(): void {
    stopWatch()
    teardownSubscription()
    currentReportId = null
    if (autoConnect) void ws.disconnect()
  }

  onScopeDispose(stop)

  return {
    status: ws.status,
    active,
    error: ws.error,
    lastError,
    stop,
  }
}
