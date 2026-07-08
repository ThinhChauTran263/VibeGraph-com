// Node 22+/25 expose an experimental global `localStorage` getter that throws a
// SecurityError unless `--localstorage-file` is set, and under jsdom this getter
// shadows the usual `window.localStorage`. Any dependency that reads the bare
// `localStorage` global (e.g. Vue devtools-kit during `createPinia()`) then
// crashes. Replace it with a minimal in-memory Storage for the test environment.
class MemoryStorage implements Storage {
  private store = new Map<string, string>()

  get length(): number {
    return this.store.size
  }

  clear(): void {
    this.store.clear()
  }

  getItem(key: string): string | null {
    return this.store.has(key) ? (this.store.get(key) as string) : null
  }

  key(index: number): string | null {
    return [...this.store.keys()][index] ?? null
  }

  removeItem(key: string): void {
    this.store.delete(key)
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value))
  }
}

function installStorage(name: 'localStorage' | 'sessionStorage'): void {
  const storage = new MemoryStorage()
  Object.defineProperty(globalThis, name, { configurable: true, value: storage })
  if (typeof window !== 'undefined') {
    Object.defineProperty(window, name, { configurable: true, value: storage })
  }
}

installStorage('localStorage')
installStorage('sessionStorage')
