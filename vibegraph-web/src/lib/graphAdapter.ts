/**
 * Graph adapter - converts API response to Sigma.js / Graphology format.
 *
 * Sigma.js uses Graphology as its data model. This module converts the
 * backend GraphData DTO into a Graphology Graph instance ready for rendering.
 */

import Graph from 'graphology'
import type { GraphData, GraphNode, GraphEdge, NodeType, EdgeType } from '@/types/graph'
import { NODE_COLORS, EDGE_COLORS, NODE_SIZES, NODE_SIZE_BY_TYPE } from './constants'

export interface SigmaNodeAttributes {
  label: string
  x: number
  y: number
  size: number
  color: string
  type: string
  nodeType: NodeType
  fullName: string
  filePath: string
  lineNumber: number
}

export interface SigmaEdgeAttributes {
  label: string
  color: string
  // Per-edge label text color (same as the edge-type color) so Sigma's edge
  // label renderer paints each label in its relationship hue, matching the Edge
  // Types legend. Sigma reads this via `edgeLabelColor: { attribute: 'labelColor' }`.
  labelColor: string
  size: number
  edgeType: EdgeType
}

/**
 * Priority used to pick the single representative edge type when several
 * relationships connect the SAME pair of nodes. Between any two nodes the graph
 * draws exactly ONE line in exactly ONE color, so when both (say) IMPORTS and
 * EXTENDS exist we keep the more meaningful structural relationship. Types not
 * listed here default to 0 (lowest). The Node Detail panel still lists every
 * individual relationship — only the on-canvas line is collapsed.
 */
const EDGE_TYPE_PRIORITY: Partial<Record<EdgeType, number>> = {
  // STEP_IN_FLOW shares (caller->callee) pairs with CALLS. It is hidden by default
  // (filtered out before this adapter), so the default canvas shows CALLS. When the
  // user reveals it via "Show all", the higher priority makes the pair render as the
  // inferred flow step rather than the raw call.
  STEP_IN_FLOW: 10,
  CONTAINS: 9,
  EXTENDS: 8,
  IMPLEMENTS: 7,
  OVERRIDES: 6,
  DEFINES: 5,
  HANDLES_ROUTE: 4,
  HAS_METHOD: 3,
  CALLS: 2,
  IMPORTS: 1,
}

function edgeTypePriority(type: EdgeType): number {
  return EDGE_TYPE_PRIORITY[type] ?? 0
}

/** Order-independent key for the pair of nodes an edge connects. */
function nodePairKey(source: string, target: string): string {
  return source < target ? `${source}\u0000${target}` : `${target}\u0000${source}`
}

/**
 * Deterministic 32-bit FNV-1a hash → float in [0, 1). Used to seed a node's
 * initial position from its stable id so the layout is REPRODUCIBLE: the same
 * project always converges to the same picture instead of a different random
 * hairball on every load (ForceAtlas2 is sensitive to its starting positions).
 */
function seededUnit(str: string): number {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return (h >>> 0) / 4294967295
}

/**
 * Deterministic initial position on a disc, derived from the node id. Replaces
 * random seeding so ForceAtlas2 starts from the same layout every time.
 */
function seededPosition(id: string): { x: number; y: number } {
  const angle = seededUnit(id) * 2 * Math.PI
  const radius = seededUnit(`${id}#r`) * 500
  return { x: Math.cos(angle) * radius, y: Math.sin(angle) * radius }
}

/**
 * Convert backend GraphData to a Graphology Graph instance.
 * Assigns deterministic initial positions (seeded from node id) so ForceAtlas2
 * converges to the same layout on every load.
 */
export function apiToGraphology(data: GraphData): Graph {
  const graph = new Graph({ multi: false, type: 'directed' })

  for (const node of data.nodes) {
    const attrs = getNodeAttributes(node)
    graph.addNode(node.id, attrs)
  }

  // Collapse every relationship between a pair of nodes to a SINGLE edge. Two
  // nodes are connected by at most one straight line; when multiple relationship
  // types exist between the same pair (e.g. IMPORTS + EXTENDS, or A->B and B->A),
  // the highest-priority type wins and defines the line's color/label. This keeps
  // the canvas readable (no overlapping parallel labels) while the Node Detail
  // panel still shows the full relationship list.
  const bestByPair = new Map<string, GraphEdge>()
  for (const edge of data.edges) {
    if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target)) continue
    const key = nodePairKey(edge.source, edge.target)
    const existing = bestByPair.get(key)
    if (!existing || edgeTypePriority(edge.type) > edgeTypePriority(existing.type)) {
      bestByPair.set(key, edge)
    }
  }

  for (const edge of bestByPair.values()) {
    graph.addEdgeWithKey(edge.id, edge.source, edge.target, getEdgeAttributes(edge))
  }

  return graph
}

/**
 * Build Sigma node attributes from a GraphNode.
 */
function getNodeAttributes(node: GraphNode): SigmaNodeAttributes {
  const { x, y } = seededPosition(node.id)
  return {
    label: node.name,
    x,
    y,
    size: getNodeSize(node.type),
    color: getNodeColor(node.type),
    type: 'circle',
    nodeType: node.type,
    fullName: node.fullName,
    filePath: node.filePath,
    lineNumber: node.lineNumber,
  }
}

/**
 * Build Sigma edge attributes from a GraphEdge.
 */
export function getEdgeAttributes(edge: GraphEdge): SigmaEdgeAttributes {
  const color = getEdgeColor(edge.type)
  return {
    label: edge.type,
    color,
    labelColor: color,
    size: 1,
    edgeType: edge.type,
  }
}

/**
 * Return node color based on type.
 */
export function getNodeColor(nodeType: NodeType): string {
  return NODE_COLORS[nodeType] ?? '#888888'
}

/**
 * Return edge color based on type.
 */
export function getEdgeColor(edgeType: EdgeType): string {
  return EDGE_COLORS[edgeType] ?? '#666666'
}

/**
 * Return node size based on type. Larger = wider structural scope (Project,
 * Package, File), smaller = deeper / more numerous detail (Field, LocalVariable).
 * Values come from {@link NODE_SIZE_BY_TYPE} (env-tunable via runtimeConfig); an
 * unknown type falls back to the default radius.
 */
export function getNodeSize(nodeType: NodeType): number {
  return NODE_SIZE_BY_TYPE[nodeType] ?? NODE_SIZES.default
}
