<script setup lang="ts">
/**
 * ExplorerTreeNode - one row in the Explorer file tree (recursive).
 *
 * Folders toggle their children; files emit a select with the node id to focus.
 * Indentation is driven by `depth`. State (expanded set, selection) is owned by
 * the parent ExplorerPanel and threaded through as props/callbacks so the tree
 * stays a pure presentational component.
 */
import { computed } from 'vue'
import { NODE_COLORS } from '@/lib/constants'
import type { FileTreeNode } from '@/lib/fileTree'

const props = defineProps<{
  node: FileTreeNode
  depth: number
  expanded: Set<string>
  selectedNodeId: string | null
  isExpanded: (path: string) => boolean
}>()

const emit = defineEmits<{
  toggle: [path: string]
  select: [nodeId: string]
}>()

const open = computed(() => props.node.kind === 'folder' && props.isExpanded(props.node.path))
const isSelectedFile = computed(
  () => props.node.kind === 'file' && props.node.nodeId === props.selectedNodeId,
)
const fileColor = computed(() =>
  props.node.kind === 'file' ? (NODE_COLORS[props.node.nodeType] ?? '#94a3b8') : '#94a3b8',
)
// Tight per-level indent so deep package trees stay compact and don't waste
// left-margin space. Small base inset + 0.4rem per level.
const indent = computed(() => `${0.15 + props.depth * 0.4}rem`)

function onActivate(): void {
  if (props.node.kind === 'folder') {
    emit('toggle', props.node.path)
  } else {
    emit('select', props.node.nodeId)
  }
}
</script>

<template>
  <li class="tree-node" role="treeitem" :aria-expanded="node.kind === 'folder' ? open : undefined">
    <button
      class="tree-node__row"
      :class="{
        'tree-node__row--folder': node.kind === 'folder',
        'tree-node__row--selected': isSelectedFile,
      }"
      type="button"
      :style="{ paddingInlineStart: indent }"
      :title="node.path"
      @click="onActivate"
    >
      <template v-if="node.kind === 'folder'">
        <svg
          class="tree-node__chevron"
          :class="{ 'tree-node__chevron--open': open }"
          viewBox="0 0 24 24"
          width="14"
          height="14"
          aria-hidden="true"
        >
          <path d="M9 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <svg class="tree-node__icon tree-node__icon--folder" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
          <path
            d="M3 6.5A1.5 1.5 0 0 1 4.5 5h4l2 2h7A1.5 1.5 0 0 1 19 8.5v8A1.5 1.5 0 0 1 17.5 18h-13A1.5 1.5 0 0 1 3 16.5v-10Z"
            :fill="open ? 'rgba(96,165,250,0.28)' : 'rgba(148,163,184,0.16)'"
            stroke="#60a5fa"
            stroke-width="1.4"
            stroke-linejoin="round"
          />
        </svg>
      </template>
      <template v-else>
        <span class="tree-node__chevron tree-node__chevron--leaf" aria-hidden="true" />
        <svg class="tree-node__icon" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
          <path
            d="M6 3.5A1.5 1.5 0 0 1 7.5 2h6L19 7.5v13A1.5 1.5 0 0 1 17.5 22h-10A1.5 1.5 0 0 1 6 20.5v-17Z"
            fill="rgba(148,163,184,0.12)"
            :stroke="fileColor"
            stroke-width="1.4"
            stroke-linejoin="round"
          />
          <path d="M13.5 2v5.5H19" fill="none" :stroke="fileColor" stroke-width="1.4" stroke-linejoin="round" />
        </svg>
      </template>

      <span class="tree-node__label">{{ node.name }}</span>
    </button>

    <ul v-if="node.kind === 'folder' && open" class="tree-node__children" role="group">
      <ExplorerTreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :depth="depth + 1"
        :expanded="expanded"
        :selected-node-id="selectedNodeId"
        :is-expanded="isExpanded"
        @toggle="(path) => emit('toggle', path)"
        @select="(nodeId) => emit('select', nodeId)"
      />
    </ul>
  </li>
</template>

<style scoped>
.tree-node {
  list-style: none;
}

.tree-node__row {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  width: 100%;
  min-height: 1.75rem;
  padding-block: 0.25rem;
  padding-inline-end: 0.5rem;
  border: 0;
  border-radius: 0.375rem;
  background: transparent;
  color: #cbd5e1;
  cursor: pointer;
  text-align: left;
  font: inherit;
  transition:
    background 120ms ease,
    color 120ms ease;
}

.tree-node__row:hover,
.tree-node__row:focus-visible {
  background: rgba(96, 165, 250, 0.14);
  color: #e5e7eb;
  outline: none;
}

.tree-node__row--folder {
  font-weight: 600;
}

.tree-node__row--selected {
  background: rgba(37, 99, 235, 0.32);
  color: #bfdbfe;
}

.tree-node__chevron {
  flex: 0 0 auto;
  color: #94a3b8;
  transition: transform 150ms ease;
}

.tree-node__chevron--open {
  transform: rotate(90deg);
}

.tree-node__chevron--leaf {
  width: 14px;
  height: 14px;
}

.tree-node__icon {
  flex: 0 0 auto;
}

.tree-node__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node__children {
  margin: 0;
  padding: 0;
}
</style>
