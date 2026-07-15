<script setup lang="ts">
import { computed, ref } from 'vue'
import { useGraphData } from '@/composables/useGraphData'
import { getEdgeColor, getNodeColor } from '@/lib/graphAdapter'
import { NODE_DETAIL_MAX_PROPERTIES, NODE_DETAIL_MAX_CONNECTIONS } from '@/lib/runtimeConfig'
import type { GraphEdge, GraphNode } from '@/types/graph'
import CodeViewerModal from '@/components/panels/CodeViewerModal.vue'
import FilePath from '@/components/ui/FilePath.vue'

interface NodeConnection {
  edge: GraphEdge
  node: GraphNode
}

export interface RelationHoverPayload {
  edgeId: string
  counterpartNodeId: string
}

const MAX_VISIBLE_PROPERTIES = NODE_DETAIL_MAX_PROPERTIES
const MAX_VISIBLE_CONNECTIONS = NODE_DETAIL_MAX_CONNECTIONS
const SENSITIVE_PROPERTY_KEY_PATTERN =
  /(secret|token|password|credential|auth|api[_-]?key|private[_-]?key)/i

const { selectedNode, filteredGraphData, clearSelection } = useGraphData()

// The id of the relation edge currently PINNED in the graph (clicked by the user).
// Drives the persistent "selected" styling on the matching connection item so the
// pinned relation stays visually marked after the pointer leaves it.
const props = withDefaults(
  defineProps<{
    pinnedEdgeId?: string | null
    projectId?: string
  }>(),
  { pinnedEdgeId: null, projectId: '' },
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'relationHover', payload: RelationHoverPayload | null): void
  (e: 'relationSelect', payload: RelationHoverPayload): void
}>()

function onClose(): void {
  clearSelection()
  emit('close')
}

function onRelationHover(connection: NodeConnection): void {
  emit('relationHover', { edgeId: connection.edge.id, counterpartNodeId: connection.node.id })
}

function onRelationLeave(): void {
  emit('relationHover', null)
}

function onRelationSelect(connection: NodeConnection): void {
  emit('relationSelect', { edgeId: connection.edge.id, counterpartNodeId: connection.node.id })
}

const nodeById = computed(
  () => new Map(filteredGraphData.value.nodes.map((node) => [node.id, node])),
)

// File nodes carry their path as BOTH fullName and filePath, which would render the
// same path twice in the meta block. Only show fullName when it adds information
// (i.e. differs from the file path, e.g. a class's qualified name).
const showFullName = computed(() => {
  const node = selectedNode.value
  return !!node && !!node.fullName && node.fullName !== node.filePath
})

// Location line: only attach a line number for code symbols. File nodes point at line 1 of
// themselves, so the ":1" is noise. The path itself is rendered collapsed via <FilePath>.
const locationLine = computed(() => {
  const node = selectedNode.value
  if (!node?.filePath) return null
  return node.type !== 'File' && typeof node.lineNumber === 'number' && node.lineNumber > 0
    ? node.lineNumber
    : null
})

// Any node backed by a source file can open the read-only code viewer. `codeNode` is the node
// whose source is shown — the selected node, or a counterpart from an Incoming/Outgoing relation.
const showCode = ref(false)
const codeNode = ref<GraphNode | null>(null)

const canViewSource = computed(() => !!selectedNode.value?.filePath)

// Counterparts that resolve to a real project source file. Packages/projects are directories and
// External imports live outside the source tree, so they have no readable file.
const NON_SOURCE_TYPES = new Set(['Package', 'Project', 'External'])
function canViewConnection(node: GraphNode): boolean {
  return !!node.filePath && !NON_SOURCE_TYPES.has(node.type)
}

function openCode(node: GraphNode | null): void {
  if (!node?.filePath) return
  codeNode.value = node
  showCode.value = true
}

function closeCode(): void {
  showCode.value = false
  codeNode.value = null
}

const propertyEntries = computed(() => {
  if (!selectedNode.value) return []
  return Object.entries(selectedNode.value.properties)
    .filter(([key, value]) => {
      return (
        !SENSITIVE_PROPERTY_KEY_PATTERN.test(key) &&
        (value === null || ['string', 'number', 'boolean'].includes(typeof value))
      )
    })
    .slice(0, MAX_VISIBLE_PROPERTIES)
})

const incomingConnections = computed<NodeConnection[]>(() => {
  const node = selectedNode.value
  if (!node) return []

  return filteredGraphData.value.edges
    .filter((edge) => edge.target === node.id)
    .slice(0, MAX_VISIBLE_CONNECTIONS)
    .map((edge) => ({ edge, node: nodeById.value.get(edge.source) ?? null }))
    .filter((connection): connection is NodeConnection => Boolean(connection.node))
})

const outgoingConnections = computed<NodeConnection[]>(() => {
  const node = selectedNode.value
  if (!node) return []

  return filteredGraphData.value.edges
    .filter((edge) => edge.source === node.id)
    .slice(0, MAX_VISIBLE_CONNECTIONS)
    .map((edge) => ({ edge, node: nodeById.value.get(edge.target) ?? null }))
    .filter((connection): connection is NodeConnection => Boolean(connection.node))
})
</script>

<template>
  <aside
    class="node-detail-panel"
    :aria-labelledby="selectedNode ? 'node-detail-heading' : 'node-detail-empty-heading'"
  >
    <template v-if="selectedNode">
      <header class="node-detail-panel__header">
        <div>
          <p class="node-detail-panel__eyebrow">Node detail</p>
          <h2 id="node-detail-heading">{{ selectedNode.name }}</h2>
        </div>
        <button
          class="node-detail-panel__close"
          type="button"
          aria-label="Close node details"
          @click="onClose"
        >
          ×
        </button>
      </header>

      <div class="node-detail-panel__meta">
        <span class="node-detail-panel__badge">{{ selectedNode.type }}</span>
        <span v-if="showFullName">{{ selectedNode.fullName }}</span>
        <FilePath v-if="selectedNode.filePath" :path="selectedNode.filePath" :line="locationLine" />
      </div>

      <button
        v-if="canViewSource"
        type="button"
        class="node-detail-panel__view-source"
        @click="openCode(selectedNode)"
      >
        <span class="node-detail-panel__view-source-icon" aria-hidden="true">{ }</span>
        View source code
      </button>

      <section
        v-if="propertyEntries.length > 0"
        class="node-detail-panel__section"
        aria-labelledby="node-properties-heading"
      >
        <h3 id="node-properties-heading">Properties</h3>
        <dl class="node-detail-panel__properties">
          <template v-for="[key, value] in propertyEntries" :key="key">
            <dt>{{ key }}</dt>
            <dd>{{ value }}</dd>
          </template>
        </dl>
      </section>

      <section class="node-detail-panel__section" aria-labelledby="incoming-heading">
        <h3 id="incoming-heading">Incoming ({{ incomingConnections.length }})</h3>
        <ul v-if="incomingConnections.length > 0" class="node-detail-panel__connections">
          <li
            v-for="connection in incomingConnections"
            :key="connection.edge.id"
            class="node-detail-panel__connection-item"
          >
            <button
              type="button"
              class="node-detail-panel__connection"
              :class="{
                'node-detail-panel__connection--pinned': connection.edge.id === props.pinnedEdgeId,
              }"
              :aria-pressed="connection.edge.id === props.pinnedEdgeId"
              @mouseenter="onRelationHover(connection)"
              @mouseleave="onRelationLeave"
              @focus="onRelationHover(connection)"
              @blur="onRelationLeave"
              @click="onRelationSelect(connection)"
            >
              <span
                class="node-detail-panel__connection-accent"
                :style="{ backgroundColor: getNodeColor(connection.node.type) }"
                aria-hidden="true"
              />
              <span class="node-detail-panel__connection-body">
                <span class="node-detail-panel__connection-name">{{ connection.node.name }}</span>
                <span
                  class="node-detail-panel__connection-type"
                  :style="{ color: getEdgeColor(connection.edge.type) }"
                >
                  ← {{ connection.edge.type }}
                </span>
              </span>
            </button>
            <button
              v-if="canViewConnection(connection.node)"
              type="button"
              class="node-detail-panel__connection-code"
              :aria-label="`View source of ${connection.node.name}`"
              title="View source"
              @click="openCode(connection.node)"
            >
              <span aria-hidden="true">{ }</span>
            </button>
          </li>
        </ul>
        <p v-else class="node-detail-panel__empty-list">No incoming edges.</p>
      </section>

      <section class="node-detail-panel__section" aria-labelledby="outgoing-heading">
        <h3 id="outgoing-heading">Outgoing ({{ outgoingConnections.length }})</h3>
        <ul v-if="outgoingConnections.length > 0" class="node-detail-panel__connections">
          <li
            v-for="connection in outgoingConnections"
            :key="connection.edge.id"
            class="node-detail-panel__connection-item"
          >
            <button
              type="button"
              class="node-detail-panel__connection"
              :class="{
                'node-detail-panel__connection--pinned': connection.edge.id === props.pinnedEdgeId,
              }"
              :aria-pressed="connection.edge.id === props.pinnedEdgeId"
              @mouseenter="onRelationHover(connection)"
              @mouseleave="onRelationLeave"
              @focus="onRelationHover(connection)"
              @blur="onRelationLeave"
              @click="onRelationSelect(connection)"
            >
              <span
                class="node-detail-panel__connection-accent"
                :style="{ backgroundColor: getNodeColor(connection.node.type) }"
                aria-hidden="true"
              />
              <span class="node-detail-panel__connection-body">
                <span class="node-detail-panel__connection-name">{{ connection.node.name }}</span>
                <span
                  class="node-detail-panel__connection-type"
                  :style="{ color: getEdgeColor(connection.edge.type) }"
                >
                  → {{ connection.edge.type }}
                </span>
              </span>
            </button>
            <button
              v-if="canViewConnection(connection.node)"
              type="button"
              class="node-detail-panel__connection-code"
              :aria-label="`View source of ${connection.node.name}`"
              title="View source"
              @click="openCode(connection.node)"
            >
              <span aria-hidden="true">{ }</span>
            </button>
          </li>
        </ul>
        <p v-else class="node-detail-panel__empty-list">No outgoing edges.</p>
      </section>

      <CodeViewerModal
        v-if="showCode && codeNode"
        :project-id="props.projectId"
        :node="codeNode"
        @close="closeCode"
      />
    </template>

    <div v-else class="node-detail-panel__empty">
      <h2 id="node-detail-empty-heading">Node detail</h2>
      <p>Select a node to inspect details.</p>
    </div>
  </aside>
</template>

<style scoped>
.node-detail-panel {
  width: 100%;
  border: 1px solid rgba(96, 165, 250, 0.25);
  border-radius: 1rem;
  padding: 1rem;
  background: rgba(17, 24, 39, 0.94);
  color: #e5e7eb;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

.node-detail-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.node-detail-panel__eyebrow,
.node-detail-panel__header h2,
.node-detail-panel__meta,
.node-detail-panel__section h3,
.node-detail-panel__empty h2,
.node-detail-panel__empty p,
.node-detail-panel__empty-list {
  margin: 0;
}

.node-detail-panel__eyebrow,
.node-detail-panel__section h3,
.node-detail-panel__empty h2 {
  color: #93c5fd;
  font-size: 0.8125rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.node-detail-panel__header h2 {
  margin-top: 0.25rem;
  font-size: 1.125rem;
  overflow-wrap: anywhere;
}

.node-detail-panel__close {
  border: 1px solid #374151;
  border-radius: 999px;
  width: 2rem;
  height: 2rem;
  background: rgba(31, 41, 55, 0.85);
  color: #d1d5db;
  cursor: pointer;
}

.node-detail-panel__meta,
.node-detail-panel__properties,
.node-detail-panel__connections {
  margin-top: 0.75rem;
}

.node-detail-panel__meta {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  color: #9ca3af;
  font-size: 0.8125rem;
  overflow-wrap: anywhere;
}

.node-detail-panel__badge {
  align-self: flex-start;
  border-radius: 999px;
  padding: 0.1875rem 0.5rem;
  background: rgba(37, 99, 235, 0.22);
  color: #bfdbfe;
}

.node-detail-panel__view-source {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.875rem;
  padding: 0.5rem 0.85rem;
  border: 1px solid rgba(96, 165, 250, 0.45);
  border-radius: 0.625rem;
  background: rgba(37, 99, 235, 0.18);
  color: #bfdbfe;
  font: inherit;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color 150ms ease,
    background-color 150ms ease,
    color 150ms ease;
}

.node-detail-panel__view-source:hover,
.node-detail-panel__view-source:focus-visible {
  border-color: rgba(96, 165, 250, 0.8);
  background: rgba(37, 99, 235, 0.32);
  color: #f8fafc;
  outline: none;
}

.node-detail-panel__view-source-icon {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 700;
  color: #93c5fd;
}

.node-detail-panel__section {
  margin-top: 1.25rem;
}

.node-detail-panel__properties {
  display: grid;
  grid-template-columns: minmax(7rem, 0.4fr) 1fr;
  gap: 0.5rem;
  font-size: 0.8125rem;
}

.node-detail-panel__properties dt {
  color: #9ca3af;
}

.node-detail-panel__properties dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.node-detail-panel__connections {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0;
  list-style: none;
}

/* Connection row: the relation button grows; an optional code-view button sits beside it. */
.node-detail-panel__connection-item {
  display: flex;
  align-items: stretch;
  gap: 0.4rem;
}

.node-detail-panel__connection-item .node-detail-panel__connection {
  flex: 1 1 auto;
  min-width: 0;
  width: auto;
}

.node-detail-panel__connection-code {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.4rem;
  border: 1px solid rgba(55, 65, 81, 0.85);
  border-radius: 0.625rem;
  background: rgba(31, 41, 55, 0.72);
  color: #93c5fd;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.8rem;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    border-color 120ms ease,
    background-color 120ms ease,
    color 120ms ease;
}

.node-detail-panel__connection-code:hover,
.node-detail-panel__connection-code:focus-visible {
  border-color: rgba(96, 165, 250, 0.85);
  background: rgba(37, 99, 235, 0.22);
  color: #f8fafc;
  outline: none;
}

.node-detail-panel__connection-code:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

.node-detail-panel__connection {
  display: flex;
  align-items: stretch;
  gap: 0.625rem;
  width: 100%;
  border: 1px solid rgba(55, 65, 81, 0.85);
  border-radius: 0.625rem;
  padding: 0.625rem;
  background: rgba(31, 41, 55, 0.72);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 120ms ease,
    background-color 120ms ease,
    transform 120ms ease;
}

.node-detail-panel__connection:hover,
.node-detail-panel__connection:focus-visible {
  border-color: rgba(96, 165, 250, 0.85);
  background: rgba(37, 99, 235, 0.16);
  transform: translateX(2px);
}

.node-detail-panel__connection:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

.node-detail-panel__connection--pinned,
.node-detail-panel__connection--pinned:hover {
  border-color: #60a5fa;
  background: rgba(37, 99, 235, 0.28);
  transform: translateX(2px);
}

.node-detail-panel__connection-accent {
  flex: 0 0 auto;
  width: 0.25rem;
  border-radius: 999px;
}

.node-detail-panel__connection-body {
  display: flex;
  flex-direction: column;
  gap: 0.1875rem;
  min-width: 0;
}

.node-detail-panel__connection-name {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.node-detail-panel__connection-type,
.node-detail-panel__empty,
.node-detail-panel__empty-list {
  font-size: 0.8125rem;
}

.node-detail-panel__empty,
.node-detail-panel__empty-list {
  color: #9ca3af;
}
</style>
