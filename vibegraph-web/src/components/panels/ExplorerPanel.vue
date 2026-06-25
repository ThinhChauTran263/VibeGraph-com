<script setup lang="ts">
/**
 * ExplorerPanel - source file tree.
 *
 * Builds a folder/file tree from the project's graph nodes (their filePath) and
 * mirrors the source layout (e.g. src/main/java/...). Clicking a file emits the
 * matching graph node so the canvas can focus it + open the CodeInspector.
 */
import { computed, ref, watch } from 'vue'
import ExplorerTreeNode from '@/components/panels/ExplorerTreeNode.vue'
import LegendPanel from '@/components/panels/LegendPanel.vue'
import { buildFileTree, collectFolderPaths, deriveTreeRoot, filterFileTree } from '@/lib/fileTree'
import type { GraphNode, NodeType } from '@/types/graph'

const props = defineProps<{
  nodes: GraphNode[]
  selectedNodeId?: string | null
}>()

const emit = defineEmits<{
  select: [nodeId: string]
}>()

const query = ref('')
// Folder paths that are currently open. Folders default to expanded so the tree
// reads top-down like the screenshot; the user can collapse from there.
const expanded = ref<Set<string>>(new Set())

const fullTree = computed(() => buildFileTree(props.nodes, deriveTreeRoot(props.nodes)))
const allFolderPaths = computed(() => collectFolderPaths(fullTree.value))

const filtered = computed(() => filterFileTree(fullTree.value, query.value))
const tree = computed(() => filtered.value.tree)

const fileCount = computed(() => {
  let count = 0
  const stack = [...tree.value]
  while (stack.length) {
    const node = stack.pop()!
    if (node.kind === 'file') count += 1
    else stack.push(...node.children)
  }
  return count
})

const hasNodes = computed(() => fullTree.value.length > 0)

// Node-type counts for the legend, derived from the same nodes the tree is built
// from, so the legend reflects exactly what this project contains.
const nodeTypeCounts = computed(() => {
  const counts: Partial<Record<NodeType, number>> = {}
  for (const node of props.nodes) {
    counts[node.type] = (counts[node.type] ?? 0) + 1
  }
  return counts
})

const showLegend = ref(true)

// Expand every folder by default, and re-sync when the project graph changes.
watch(
  allFolderPaths,
  (paths) => {
    expanded.value = new Set(paths)
  },
  { immediate: true },
)

// When a search narrows the tree, force the matching folders open so results show.
watch(
  () => filtered.value.expand,
  (toExpand) => {
    if (toExpand.size === 0) return
    const next = new Set(expanded.value)
    for (const path of toExpand) next.add(path)
    expanded.value = next
  },
)

function isExpanded(path: string): boolean {
  return expanded.value.has(path)
}

function toggle(path: string): void {
  const next = new Set(expanded.value)
  if (next.has(path)) next.delete(path)
  else next.add(path)
  expanded.value = next
}

function expandAll(): void {
  expanded.value = new Set(allFolderPaths.value)
}

function collapseAll(): void {
  expanded.value = new Set()
}

function onSelect(nodeId: string): void {
  emit('select', nodeId)
}
</script>

<template>
  <section class="explorer-panel" aria-labelledby="explorer-panel-heading">
    <header class="explorer-panel__header">
      <h2 id="explorer-panel-heading">Explorer</h2>
      <div class="explorer-panel__actions">
        <button type="button" :disabled="!hasNodes" title="Expand all folders" @click="expandAll">
          Expand
        </button>
        <button type="button" :disabled="!hasNodes" title="Collapse all folders" @click="collapseAll">
          Collapse
        </button>
      </div>
    </header>

    <div class="explorer-panel__search" role="search">
      <label class="explorer-panel__search-label" for="explorer-search">Search files</label>
      <input
        id="explorer-search"
        v-model="query"
        class="explorer-panel__search-input"
        type="search"
        placeholder="Search files..."
        autocomplete="off"
        spellcheck="false"
      />
    </div>

    <ul v-if="tree.length > 0" class="explorer-panel__tree" role="tree" aria-label="Source files">
      <ExplorerTreeNode
        v-for="node in tree"
        :key="node.path"
        :node="node"
        :depth="0"
        :expanded="expanded"
        :selected-node-id="selectedNodeId ?? null"
        :is-expanded="isExpanded"
        @toggle="toggle"
        @select="onSelect"
      />
    </ul>

    <p v-else-if="!hasNodes" class="explorer-panel__empty">No source files in this graph.</p>
    <p v-else class="explorer-panel__empty">No files match "{{ query }}".</p>

    <div v-if="hasNodes" class="explorer-panel__legend">
      <button
        class="explorer-panel__legend-toggle"
        type="button"
        :aria-expanded="showLegend"
        @click="showLegend = !showLegend"
      >
        <span>Legend</span>
        <span class="explorer-panel__legend-chevron" :class="{ 'explorer-panel__legend-chevron--open': showLegend }" aria-hidden="true">›</span>
      </button>
      <LegendPanel v-show="showLegend" :node-stats="nodeTypeCounts" title="" />
    </div>

    <footer v-if="hasNodes" class="explorer-panel__footer">{{ fileCount }} files</footer>
  </section>
</template>

<style scoped>
.explorer-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 0.75rem;
  border: 1px solid rgba(96, 165, 250, 0.25);
  border-radius: 1rem;
  background: rgba(17, 24, 39, 0.94);
  color: #e5e7eb;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

.explorer-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.explorer-panel__header h2 {
  margin: 0;
  font-size: 1rem;
}

.explorer-panel__actions {
  display: flex;
  gap: 0.375rem;
}

.explorer-panel__actions button {
  border: 1px solid #374151;
  border-radius: 999px;
  padding: 0.25rem 0.625rem;
  background: rgba(31, 41, 55, 0.85);
  color: #d1d5db;
  cursor: pointer;
  font-size: 0.75rem;
}

.explorer-panel__actions button:hover:not(:disabled),
.explorer-panel__actions button:focus-visible:not(:disabled) {
  border-color: rgba(96, 165, 250, 0.5);
  background: rgba(37, 99, 235, 0.22);
}

.explorer-panel__actions button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.explorer-panel__search {
  margin-top: 0.75rem;
}

.explorer-panel__search-label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.explorer-panel__search-input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1px solid rgba(96, 165, 250, 0.3);
  border-radius: 0.625rem;
  background: rgba(15, 23, 42, 0.9);
  color: inherit;
  font: inherit;
}

.explorer-panel__search-input:focus-visible {
  outline: none;
  border-color: rgba(96, 165, 250, 0.7);
}

.explorer-panel__search-input::placeholder {
  color: #9ca3af;
}

.explorer-panel__tree {
  margin: 0.75rem 0 0;
  padding: 0;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.explorer-panel__empty {
  margin: 0.75rem 0 0;
  color: #9ca3af;
  font-size: 0.8125rem;
}

.explorer-panel__legend {
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.explorer-panel__legend-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0.25rem 0;
  border: 0;
  background: transparent;
  color: #93c5fd;
  cursor: pointer;
  font: inherit;
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.explorer-panel__legend-chevron {
  transition: transform 150ms ease;
}

.explorer-panel__legend-chevron--open {
  transform: rotate(90deg);
}

.explorer-panel__footer {
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
  color: #94a3b8;
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
}
</style>
