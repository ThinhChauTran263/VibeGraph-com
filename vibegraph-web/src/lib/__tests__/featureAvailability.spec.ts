import { beforeEach, describe, expect, it, vi } from 'vitest'
const apiMocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    api: { ...actual.api, get: apiMocks.get },
  }
})

async function loadFeatureAvailability() {
  vi.resetModules()
  return import('@/lib/featureAvailability')
}

describe('featureAvailability', () => {
  beforeEach(() => {
    apiMocks.get.mockReset()
  })

  it('fails closed for every import method when the capability contract is absent', async () => {
    apiMocks.get.mockResolvedValueOnce({ id: 'user-1' })
    const { refreshFeatureAvailability, useFeatureAvailability } = await loadFeatureAvailability()
    const local = useFeatureAvailability('import.local')
    const archive = useFeatureAvailability('import.archive')
    const github = useFeatureAvailability('import.github')

    await refreshFeatureAvailability()

    for (const feature of [local, archive, github]) {
      expect(feature.value.enabled).toBe(false)
      expect(feature.value.reason).toContain('capability contract')
      expect(feature.value.reason).not.toContain('compatibility mode')
    }
  })

  it('uses explicit capability values without inventing missing features', async () => {
    apiMocks.get.mockResolvedValueOnce({
      features: {
        'import.local': { enabled: true },
        'import.archive': { enabled: false, reason: 'Archive imports are paused.' },
      },
    })
    const { refreshFeatureAvailability, useFeatureAvailability } = await loadFeatureAvailability()
    const local = useFeatureAvailability('import.local')
    const archive = useFeatureAvailability('import.archive')
    const github = useFeatureAvailability('import.github')

    await refreshFeatureAvailability()

    expect(local.value).toMatchObject({ enabled: true, reason: null })
    expect(archive.value).toMatchObject({ enabled: false, reason: 'Archive imports are paused.' })
    expect(github.value.enabled).toBe(false)
  })
})
