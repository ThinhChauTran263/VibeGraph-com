/**
 * Impact-analysis (blast radius) composable.
 *
 * Wraps `graphApi.getImpact` with explicit request state, frontend boundary
 * validation, and user-visible error mapping. State transitions are immutable
 * assignments — the composable never mutates the previous result object.
 *
 * Backend contract: GET /api/projects/{projectId}/graph/impact?nodeId=&depth=
 * (see GraphController / GraphServiceImpl). Allowed depths: 1, 2, 3, 5.
 */

import { computed, readonly, ref } from 'vue'
import {
  ApiError,
  graphApi,
  IMPACT_ALLOWED_DEPTHS,
  type ImpactAnalysisResponse,
  type ImpactDepth,
} from '@/lib/api'

export type ImpactStatus = 'idle' | 'loading' | 'success' | 'error'

const DEFAULT_DEPTH: ImpactDepth = 1

function isAllowedDepth(depth: number): depth is ImpactDepth {
  return (IMPACT_ALLOWED_DEPTHS as readonly number[]).includes(depth)
}

function mapError(err: unknown): string {
  if (err instanceof ApiError) {
    // Surface the backend message when present (e.g. node not found, bad depth);
    // otherwise fall back to the HTTP status text.
    return err.message || `Request failed (${err.status}).`
  }
  if (err instanceof Error && err.message) return err.message
  return 'Failed to load impact analysis.'
}

export function useImpactAnalysis() {
  const status = ref<ImpactStatus>('idle')
  const result = ref<ImpactAnalysisResponse | null>(null)
  const errorMessage = ref<string | null>(null)
  const selectedDepth = ref<ImpactDepth>(DEFAULT_DEPTH)

  const isLoading = computed(() => status.value === 'loading')
  const allowedDepths = IMPACT_ALLOWED_DEPTHS

  // Monotonic request token. Each in-flight request captures the current value;
  // when it settles, it only writes state if it is still the latest request.
  // `reset()` (e.g. node selection change) bumps this to invalidate any
  // in-flight request, preventing a stale response from overwriting the panel.
  let requestSeq = 0

  /**
   * Load the blast radius for `nodeId` within `projectId`.
   *
   * Performs basic frontend validation before the network call:
   * - `projectId` and `nodeId` must be non-blank.
   * - `depth` must be one of the backend-whitelisted depths.
   *
   * Returns the response on success, or `null` on validation/API failure.
   */
  async function loadImpact(
    projectId: string,
    nodeId: string,
    depth: number = selectedDepth.value,
  ): Promise<ImpactAnalysisResponse | null> {
    const trimmedProjectId = projectId?.trim() ?? ''
    const trimmedNodeId = nodeId?.trim() ?? ''

    if (!trimmedProjectId) {
      status.value = 'error'
      result.value = null
      errorMessage.value = 'A projectId is required to analyze impact.'
      return null
    }
    if (!trimmedNodeId) {
      status.value = 'error'
      result.value = null
      errorMessage.value = 'Select a node before running impact analysis.'
      return null
    }
    if (!isAllowedDepth(depth)) {
      status.value = 'error'
      result.value = null
      errorMessage.value = `Depth must be one of ${IMPACT_ALLOWED_DEPTHS.join(', ')}.`
      return null
    }

    selectedDepth.value = depth
    status.value = 'loading'
    errorMessage.value = null

    const seq = ++requestSeq

    try {
      const data = await graphApi.getImpact(trimmedProjectId, trimmedNodeId, depth)
      // A newer request (or a reset) superseded this one; drop the stale result.
      if (seq !== requestSeq) return null
      result.value = data
      status.value = 'success'
      return data
    } catch (err) {
      // Ignore errors from superseded/stale requests so they don't clobber the
      // state belonging to a newer request or a reset.
      if (seq !== requestSeq) return null
      result.value = null
      errorMessage.value = mapError(err)
      status.value = 'error'
      return null
    }
  }

  /** Reset the composable to its initial idle state (keeps selectedDepth). */
  function reset(): void {
    // Invalidate any in-flight request so its late response is ignored.
    requestSeq++
    status.value = 'idle'
    result.value = null
    errorMessage.value = null
  }

  return {
    status: readonly(status),
    result: readonly(result),
    errorMessage: readonly(errorMessage),
    selectedDepth,
    isLoading,
    allowedDepths,
    loadImpact,
    reset,
  }
}
