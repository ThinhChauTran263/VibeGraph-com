import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAccountStore } from '../account'

describe('Account Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('initializes with default state', () => {
    const store = useAccountStore()
    expect(store.profile).toBeNull()
    expect(store.usage).toBeNull()
    expect(store.projects).toEqual([])
    expect(store.apiKeys).toEqual([])
  })

  it('createApiKey adds a new key to the list', async () => {
    const store = useAccountStore()
    await store.createApiKey('My New Key')
    expect(store.apiKeys.length).toBe(1)
    expect(store.apiKeys[0]!.name).toBe('My New Key')
    expect(store.apiKeys[0]!.secret).toBeDefined()
  })

  it('disableApiKey sets disabled to true', async () => {
    const store = useAccountStore()
    await store.createApiKey('To Disable')
    const keyId = store.apiKeys[0]!.id
    await store.disableApiKey(keyId)
    expect(store.apiKeys[0]!.disabled).toBe(true)
  })
})
