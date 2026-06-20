import { ref } from 'vue'

/**
 * Monotonic "the graph data changed" signal, shared app-wide.
 *
 * Bumped whenever the project graph is (re)loaded or patched via WebSocket. Derived
 * views (e.g. the Mermaid diagrams) cache their last result against the version they
 * were built from; when {@link graphVersion} moves past that, the cached result is
 * considered stale and is re-fetched on next access. This keeps tab/kind switches
 * instant while still guaranteeing freshness after the underlying graph changes.
 */
export const graphVersion = ref(0)

/** Increment the shared graph version. Call after any graph data mutation. */
export function bumpGraphVersion(): void {
  graphVersion.value += 1
}
