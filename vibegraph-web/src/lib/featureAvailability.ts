import { computed, readonly, ref } from 'vue'
import { api } from '@/lib/api'
import type { FeatureCapability } from '@/types/api'

export type FeatureKey =
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
  features?: Partial<Record<FeatureKey, boolean | FeatureCapability>>
}

const features = ref<Partial<Record<FeatureKey, FeatureAvailability>>>({})
const contractAvailable = ref<boolean | null>(null)
function unavailable(key: FeatureKey): FeatureAvailability {
  return {
    key,
    enabled: false,
    reason: 'This control is unavailable until the capability contract is connected.',
  }
}

function withoutContract(key: FeatureKey): FeatureAvailability {
  return {
    key,
    enabled: false,
    reason: 'This control is blocked until the account capability contract is available.',
  }
}

let refreshRequestId = 0

export async function refreshFeatureAvailability(): Promise<void> {
  const requestId = ++refreshRequestId
  try {
    const response = await api.get<SessionCapabilitiesResponse>('/api/account/session-state')
    if (requestId !== refreshRequestId) return
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
    if (requestId !== refreshRequestId) return
    features.value = next
    contractAvailable.value = true
  } catch (error) {
    if (requestId !== refreshRequestId) return
    features.value = {}
    contractAvailable.value = false
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
