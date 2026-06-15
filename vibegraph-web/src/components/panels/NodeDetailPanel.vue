<script setup lang="ts">
import { computed } from 'vue'
import { useGraphData } from '@/composables/useGraphData'
import { getEdgeColor, getNodeColor } from '@/lib/graphAdapter'
import type { GraphEdge, GraphNode } from '@/types/graph'

interface NodeConnection {
  edge: GraphEdge
  node: GraphNode
}

export interface RelationHoverPayload {
  edgeId: string
  counterpartNodeId: string
}

const MAX_VISIBLE_PROPERTIES = 12
const MAX_VISIBLE_CONNECTIONS = 50
const SENSITIVE_PROPERTY_KEY_PATTERN = /(secret|token|password|credential|auth|api[_-]?key|private[_-]?key)/i

const { selectedNode, filteredGraphData, clearSelection } = useGraphData()

// The id of the relation edge currently PINNED in the graph (clicked by the user).
// Drives the persistent "selected" styling on the matching connection item so the
// pinned relation stays visually marked after the pointer leaves it.
const props = defineProps<{
  pinnedEdgeId?: string | null
}>()

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

const nodeById = computed(() => new Map(filteredGraphData.value.nodes.map((node) => [node.id, node])))

const propertyEntries = computed(() => {
  if (!selectedNode.value) return []
  return Object.entries(selectedNode.value.properties)
    .filter(([key, value]) => {
      return !SENSITIVE_PROPERTY_KEY_PATTERN.test(key) && (value === null || ['string', 'number', 'boolean'].includes(typeof value))
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
  <aside class="node-detail-panel" :aria-labelledby="selectedNode ? 'node-detail-heading' : 'node-detail-empty-heading'">
    <template v-if="selectedNode">
      <header class="node-detail-panel__header">
        <div>
          <p class="node-detail-panel__eyebrow">Node detail</p>
          <h2 id="node-detail-heading">{{ selectedNode.name }}</h2>
        </div>
        <button class="node-detail-panel__close" type="button" aria-label="Close node details" @click="onClose">
          ×
        </button>
      </header>

      <div class="node-detail-panel__meta">
        <span class="node-detail-panel__badge">{{ selectedNode.type }}</span>
        <span>{{ selectedNode.fullName }}</span>
        <span>{{ selectedNode.filePath }}:{{ selectedNode.lineNumber }}</span>
      </div>

      <section v-if="propertyEntries.length > 0" class="node-detail-panel__section" aria-labelledby="node-properties-heading">
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
          <li v-for="connection in incomingConnections" :key="connection.edge.id">
            <button
              type="button"
              class="node-detail-panel__connection"
              :class="{ 'node-detail-panel__connection--pinned': connection.edge.id === props.pinnedEdgeId }"
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
                <span class="node-detail-panel__connection-type" :style="{ color: getEdgeColor(connection.edge.type) }">
                  ← {{ connection.edge.type }}
                </span>
              </span>
            </button>
          </li>
        </ul>
        <p v-else class="node-detail-panel__empty-list">No incoming edges.</p>
      </section>

      <section class="node-detail-panel__section" aria-labelledby="outgoing-heading">
        <h3 id="outgoing-heading">Outgoing ({{ outgoingConnections.length }})</h3>
        <ul v-if="outgoingConnections.length > 0" class="node-detail-panel__connections">
          <li v-for="connection in outgoingConnections" :key="connection.edge.id">
            <button
              type="button"
              class="node-detail-panel__connection"
              :class="{ 'node-detail-panel__connection--pinned': connection.edge.id === props.pinnedEdgeId }"
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
                <span class="node-detail-panel__connection-type" :style="{ color: getEdgeColor(connection.edge.type) }">
                  → {{ connection.edge.type }}
                </span>
              </span>
            </button>
          </li>
        </ul>
        <p v-else class="node-detail-panel__empty-list">No outgoing edges.</p>
      </section>
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
  transition: border-color 120ms ease, background-color 120ms ease, transform 120ms ease;
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
