/**
 * STOMP-over-SockJS WebSocket composable.
 *
 * Wraps `@stomp/stompjs` Client with a SockJS transport so the frontend can
 * subscribe to backend STOMP topics (e.g. project import status).
 *
 * Design notes:
 *   - `connect()` resolves once the STOMP CONNECTED frame arrives, and rejects
 *     on transport/STOMP errors so callers can surface a clear failure instead
 *     of hanging silently.
 *   - `subscribe()` may be called before `connect()` resolves; pending
 *     subscriptions are flushed on connect.
 *   - `sockjs-client` is imported lazily inside `connect()` so unit tests that
 *     inject a fake client never pull the transport into the module graph.
 */

import { ref, type Ref } from 'vue'
import { Client, type IMessage, type IStompSocket, type StompSubscription } from '@stomp/stompjs'
import { WS_URL } from '@/lib/constants'
import {
  WS_RECONNECT_DELAY_MS,
  WS_HEARTBEAT_INCOMING_MS,
  WS_HEARTBEAT_OUTGOING_MS,
} from '@/lib/runtimeConfig'

export type WebSocketStatus = 'disconnected' | 'connecting' | 'connected' | 'error'

export interface TopicSubscription {
  unsubscribe: () => void
}

export interface UseWebSocketOptions {
  /** Override the SockJS endpoint. Defaults to `WS_URL`. */
  url?: string
  /**
   * Override the STOMP client factory. Primarily a test seam so unit tests can
   * inject a fake client without a real socket. When omitted, a real
   * SockJS-backed client is built lazily.
   */
  clientFactory?: (url: string) => Client
}

export interface UseWebSocketReturn {
  status: Ref<WebSocketStatus>
  error: Ref<string | null>
  connect: () => Promise<void>
  disconnect: () => Promise<void>
  subscribe: <T>(topic: string, callback: (payload: T) => void) => TopicSubscription
}

interface PendingSubscription {
  topic: string
  handler: (message: IMessage) => void
  sub: StompSubscription | null
}

export function useWebSocket(options: UseWebSocketOptions = {}): UseWebSocketReturn {
  const url = options.url ?? WS_URL
  const status = ref<WebSocketStatus>('disconnected')
  const error = ref<string | null>(null)

  let client: Client | null = null
  let pending: PendingSubscription[] = []

  async function buildClient(): Promise<Client> {
    // Lazy import keeps the SockJS transport out of the test module graph.
    const sockjsModule = await import('sockjs-client')
    const SockJS = sockjsModule.default
    return new Client({
      webSocketFactory: () => new SockJS(url) as unknown as IStompSocket,
      reconnectDelay: WS_RECONNECT_DELAY_MS,
      heartbeatIncoming: WS_HEARTBEAT_INCOMING_MS,
      heartbeatOutgoing: WS_HEARTBEAT_OUTGOING_MS,
    })
  }

  function flushPending(activeClient: Client): void {
    for (const entry of pending) {
      entry.sub = activeClient.subscribe(entry.topic, entry.handler)
    }
    pending = []
  }

  function connect(): Promise<void> {
    if (client && status.value === 'connected') {
      return Promise.resolve()
    }
    status.value = 'connecting'
    error.value = null

    return new Promise<void>((resolve, reject) => {
      const wire = (built: Client): void => {
        client = built

        built.onConnect = () => {
          status.value = 'connected'
          flushPending(built)
          resolve()
        }

        built.onStompError = (frame) => {
          status.value = 'error'
          error.value = frame?.headers?.['message'] ?? 'STOMP protocol error.'
          reject(new Error(error.value ?? 'STOMP protocol error.'))
        }

        built.onWebSocketError = () => {
          status.value = 'error'
          error.value = 'WebSocket connection failed.'
          reject(new Error(error.value ?? 'WebSocket connection failed.'))
        }

        built.onWebSocketClose = () => {
          // Only downgrade to disconnected if we are not already flagged as errored.
          if (status.value !== 'error') {
            status.value = 'disconnected'
          }
        }

        built.activate()
      }

      // When a factory is provided (tests), wire synchronously so callers can
      // drive the connect lifecycle without an extra microtask gap. Otherwise
      // lazily import the SockJS transport first.
      if (options.clientFactory) {
        try {
          wire(options.clientFactory(url))
        } catch (err: unknown) {
          status.value = 'error'
          error.value = err instanceof Error ? err.message : 'Failed to initialize WebSocket.'
          reject(
            err instanceof Error
              ? err
              : new Error(error.value ?? 'Failed to initialize WebSocket.'),
          )
        }
        return
      }

      buildClient()
        .then(wire)
        .catch((err: unknown) => {
          status.value = 'error'
          error.value = err instanceof Error ? err.message : 'Failed to initialize WebSocket.'
          reject(
            err instanceof Error
              ? err
              : new Error(error.value ?? 'Failed to initialize WebSocket.'),
          )
        })
    })
  }

  function subscribe<T>(topic: string, callback: (payload: T) => void): TopicSubscription {
    const handler = (message: IMessage): void => {
      const parsed = parseBody<T>(message.body)
      if (parsed !== undefined) {
        callback(parsed)
      }
    }

    const entry: PendingSubscription = { topic, handler, sub: null }

    if (client && status.value === 'connected') {
      entry.sub = client.subscribe(topic, handler)
    } else {
      pending.push(entry)
    }

    return {
      unsubscribe: () => {
        if (entry.sub) {
          entry.sub.unsubscribe()
          entry.sub = null
        } else {
          pending = pending.filter((p) => p !== entry)
        }
      },
    }
  }

  async function disconnect(): Promise<void> {
    pending = []
    if (client) {
      const active = client
      client = null
      await active.deactivate()
    }
    if (status.value !== 'error') {
      status.value = 'disconnected'
    }
  }

  function parseBody<T>(body: string): T | undefined {
    try {
      return JSON.parse(body) as T
    } catch {
      error.value = 'Received a malformed WebSocket message.'
      return undefined
    }
  }

  return { status, error, connect, disconnect, subscribe }
}
