import { computed, readonly, ref } from 'vue'
import { ApiError, api } from '@/lib/api'

export type FeatureKey =
  | 'import.local'
  | 'import.archive'
  | 'import.github'
  | 'cli.push'
  | 'api_keys.create.global'
  | 'project.analyze'
  | 'mcp.enabled'
  | 'usecase.generate'

export interface FeatureAvailability {
  key: FeatureKey
  enabled: boolean
  reason: string | null
}

interface SessionCapabilitiesResponse {
  features?: Partial<Record<FeatureKey, boolean | { enabled: boolean; reason?: string | null }>>
}

const features = ref<Partial<Record<FeatureKey, FeatureAvailability>>>({})
const contractAvailable = ref<boolean | null>(null)
const compatibilityFeatures = new Set<FeatureKey>([
  'import.local',
  'import.archive',
  'import.github',
])

function unavailable(key: FeatureKey): FeatureAvailability {
  return {
    key,
    enabled: false,
    reason: 'This control is unavailable until the capability contract is connected.',
  }
}

function withoutContract(key: FeatureKey): FeatureAvailability {
  if (compatibilityFeatures.has(key)) {
    return {
      key,
      enabled: true,
      reason: 'Available in compatibility mode while the capability contract is unavailable.',
    }
  }
  return unavailable(key)
}

export async function refreshFeatureAvailability(): Promise<void> {
  try {
    const response = await api.get<SessionCapabilitiesResponse>('/api/account/session-state')
    if (!response.features) {
      features.value = {}
      contractAvailable.value = false
      return
    }
    const next: Partial<Record<FeatureKey, FeatureAvailability>> = {}
    for (const [key, value] of Object.entries(response.features)) {
      if (typeof value === 'boolean')
        next[key as FeatureKey] = {
          key: key as FeatureKey,
          enabled: value,
          reason: value ? null : 'Disabled by an administrator.',
        }
      else if (value)
        next[key as FeatureKey] = {
          key: key as FeatureKey,
          enabled: value.enabled,
          reason: value.reason ?? (value.enabled ? null : 'Disabled by an administrator.'),
        }
    }
    features.value = next
    contractAvailable.value = true
  } catch (error) {
    if (error instanceof ApiError && [404, 405, 501].includes(error.status)) {
      features.value = {}
      contractAvailable.value = false
      return
    }
    throw error
  }
}

export function useFeatureAvailability(key: FeatureKey) {
  return computed(() => {
    if (contractAvailable.value === null) {
      return { key, enabled: false, reason: 'Checking feature availability.' }
    }
    return contractAvailable.value === false
      ? withoutContract(key)
      : (features.value[key] ?? unavailable(key))
  })
}

export const featureAvailabilityContract = readonly(contractAvailable)
