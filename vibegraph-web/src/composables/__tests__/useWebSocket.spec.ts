import { describe, it, expect, vi } from 'vitest'
import type { Client, IMessage, StompSubscription } from '@stomp/stompjs'
import { useWebSocket } from '../useWebSocket'

/**
 * A minimal fake STOMP Client that records subscriptions and lets the test
 * drive the connect lifecycle and deliver messages. Only the surface used by
 * `useWebSocket` is implemented; cast to `Client` for the factory seam.
 */
interface SubscriptionRecord {
  topic: string
  callback: (message: IMessage) => void
}

class FakeStompClient {
  onConnect: (() => void) | undefined
  onStompError: ((frame: { headers: Record<string, string> }) => void) | undefined
  onWebSocketError: (() => void) | undefined
  onWebSocketClose: (() => void) | undefined

  activated = false
  deactivated = false
  subscriptions: SubscriptionRecord[] = []
  private counter = 0

  activate(): void {
    this.activated = true
  }

  async deactivate(): Promise<void> {
    this.deactivated = true
  }

  subscribe(topic: string, callback: (message: IMessage) => void): StompSubscription {
    const record: SubscriptionRecord = { topic, callback }
    this.subscriptions.push(record)
    const id = `sub-${++this.counter}`
    return {
      id,
      unsubscribe: () => {
        this.subscriptions = this.subscriptions.filter((s) => s !== record)
      },
    }
  }

  /** Test helper: simulate the broker CONNECTED frame. */
  fireConnect(): void {
    this.onConnect?.()
  }

  /** Test helper: simulate a transport close that drops broker subscriptions. */
  fireClose(): void {
    this.subscriptions = []
    this.onWebSocketClose?.()
  }

  /** Test helper: deliver a message body to a subscribed topic. */
  deliver(topic: string, body: string): void {
    for (const s of this.subscriptions.filter((s) => s.topic === topic)) {
      s.callback({ body } as IMessage)
    }
  }
}

function setup() {
  const fake = new FakeStompClient()
  const ws = useWebSocket({
    url: 'http://test/ws',
    clientFactory: () => fake as unknown as Client,
  })
  return { fake, ws }
}

describe('useWebSocket', () => {
  it('starts disconnected', () => {
    const { ws } = setup()
    expect(ws.status.value).toBe('disconnected')
    expect(ws.error.value).toBeNull()
  })

  it('resolves connect() once the STOMP CONNECTED frame arrives', async () => {
    const { fake, ws } = setup()
    const p = ws.connect()
    expect(ws.status.value).toBe('connecting')
    expect(fake.activated).toBe(true)

    fake.fireConnect()
    await p

    expect(ws.status.value).toBe('connected')
  })

  it('reuses the in-flight connection when connect() is called repeatedly', async () => {
    const { fake, ws } = setup()

    const first = ws.connect()
    const second = ws.connect()

    expect(second).toBe(first)
    expect(fake.activated).toBe(true)
    fake.fireConnect()
    await expect(Promise.all([first, second])).resolves.toEqual([undefined, undefined])
  })

  it('subscribe() parses the JSON body and invokes the callback', async () => {
    const { fake, ws } = setup()
    const p = ws.connect()
    fake.fireConnect()
    await p

    const received: Array<{ projectId: string; progress: number }> = []
    ws.subscribe<{ projectId: string; progress: number }>('/topic/projects/x/status', (payload) => {
      received.push(payload)
    })

    fake.deliver('/topic/projects/x/status', JSON.stringify({ projectId: 'x', progress: 42 }))

    expect(received).toHaveLength(1)
    expect(received[0]).toEqual({ projectId: 'x', progress: 42 })
  })

  it('flushes subscriptions registered before connect resolves', async () => {
    const { fake, ws } = setup()
    // Subscribe BEFORE connect.
    const seen: string[] = []
    ws.subscribe<{ msg: string }>('/topic/pending', (p) => seen.push(p.msg))

    const connectPromise = ws.connect()
    fake.fireConnect()
    await connectPromise

    fake.deliver('/topic/pending', JSON.stringify({ msg: 'hello' }))
    expect(seen).toEqual(['hello'])
  })

  it('replays desired subscriptions after the STOMP client reconnects', async () => {
    const { fake, ws } = setup()
    const seen: number[] = []
    const subscription = ws.subscribe<{ sequence: number }>('/topic/reports/report-1', (event) => {
      seen.push(event.sequence)
    })
    expect(subscription.active.value).toBe(false)

    const connectPromise = ws.connect()
    fake.fireConnect()
    await connectPromise
    expect(subscription.active.value).toBe(true)
    fake.deliver('/topic/reports/report-1', JSON.stringify({ sequence: 1 }))

    fake.fireClose()
    expect(ws.status.value).toBe('disconnected')
    expect(subscription.active.value).toBe(false)

    fake.fireConnect()
    expect(subscription.active.value).toBe(true)
    fake.deliver('/topic/reports/report-1', JSON.stringify({ sequence: 2 }))

    expect(seen).toEqual([1, 2])
    expect(fake.subscriptions.filter((entry) => entry.topic === '/topic/reports/report-1')).toHaveLength(
      1,
    )
  })

  it('does not invoke the callback on a malformed (non-JSON) body', async () => {
    const { fake, ws } = setup()
    const p = ws.connect()
    fake.fireConnect()
    await p

    const cb = vi.fn<(payload: unknown) => void>()
    ws.subscribe('/topic/bad', cb)
    fake.deliver('/topic/bad', 'not-json{')

    expect(cb).not.toHaveBeenCalled()
    expect(ws.error.value).toMatch(/malformed/i)
  })

  it('rejects connect() and sets error status on STOMP error', async () => {
    const { fake, ws } = setup()
    const p = ws.connect()
    fake.onStompError?.({ headers: { message: 'boom' } })

    await expect(p).rejects.toThrow(/boom/)
    expect(ws.status.value).toBe('error')
    expect(ws.error.value).toBe('boom')
  })

  it('rejects connect() and sets error status on WebSocket error', async () => {
    const { fake, ws } = setup()
    const p = ws.connect()
    fake.onWebSocketError?.()

    await expect(p).rejects.toThrow(/WebSocket connection failed/)
    expect(ws.status.value).toBe('error')
  })

  it('deactivates the client on disconnect()', async () => {
    const { fake, ws } = setup()
    const p = ws.connect()
    fake.fireConnect()
    await p

    await ws.disconnect()
    expect(fake.deactivated).toBe(true)
    expect(ws.status.value).toBe('disconnected')
  })

  it('unsubscribe() removes the subscription so later messages are ignored', async () => {
    const { fake, ws } = setup()
    const p = ws.connect()
    fake.fireConnect()
    await p

    const seen: number[] = []
    const sub = ws.subscribe<{ n: number }>('/topic/n', (x) => seen.push(x.n))
    fake.deliver('/topic/n', JSON.stringify({ n: 1 }))
    sub.unsubscribe()
    fake.deliver('/topic/n', JSON.stringify({ n: 2 }))

    expect(seen).toEqual([1])
  })
})
