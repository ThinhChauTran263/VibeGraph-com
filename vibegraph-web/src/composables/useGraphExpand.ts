/**
 * Lazy graph expansion: pull a node's neighbors on demand and merge them into the displayed
 * graph, instead of loading the entire project graph up front.
 *
 * Reuses the same tested, immutable merge as the realtime consumer (`applyGraphUpdate` with an
 * INCREMENTAL event), so node/edge de-duplication, stats recomputation, and dangling-edge pruning
 * all behave identically. A monotonic sequence guard discards results from a superseded project so
 * a late expand response can never inject neighbors into the wrong graph.
 */

import { ref } from 'vue'
import { useGraphStore } from '@/stores/graph'
import { graphApi } from '@/lib/api'
import { neighborsToFragment } from '@/lib/neighborsAdapter'
import { applyGraphUpdate } from '@/lib/graphPatch'

/** Hops supported by the backend `/neighbors` endpoint for expansion. */
export type ExpandHops = 1 | 2

export function useGraphExpand() {
  const store = useGraphStore()

  const expanding = ref(false)
  const lastError = ref<string | null>(null)
  // True when the most recent expansion hit EXPAND_MAX_NEIGHBORS and dropped neighbors.
  const lastTruncated = ref(false)
  // Bumped on every project change so a late response for an old project is ignored.
  let projectSeq = 0

  /** Invalidate any in-flight expansion (call when the active project changes). */
  function reset(): void {
    projectSeq += 1
    expanding.value = false
    lastError.value = null
    lastTruncated.value = false
  }

  /**
   * Expand a node's 1- or 2-hop neighborhood and merge it into {@code store.graphData}.
   * Returns the number of NEW nodes added (0 when everything was already present or on error).
   */
  async function expandNode(projectId: string, nodeId: string, hops: ExpandHops = 1): Promise<number> {
    if (!projectId || !nodeId) return 0
    const seq = projectSeq
    expanding.value = true
    lastError.value = null
    try {
      const detail = await graphApi.getNeighbors(projectId, nodeId, hops)
      // Stale guard: project changed (or reset) while the request was in flight.
      if (seq !== projectSeq) return 0

      const fragment = neighborsToFragment(detail)
      lastTruncated.value = fragment.truncated
      const before = store.graphData.nodes.length
      store.graphData = applyGraphUpdate(store.graphData, {
        type: 'INCREMENTAL',
        projectId,
        added: { nodes: fragment.nodes, edges: fragment.edges },
      })
      return Math.max(0, store.graphData.nodes.length - before)
    } catch (err) {
      lastError.value = err instanceof Error ? err.message : 'Failed to expand node.'
      return 0
    } finally {
      if (seq === projectSeq) expanding.value = false
    }
  }

  return { expanding, lastError, lastTruncated, expandNode, reset }
}
