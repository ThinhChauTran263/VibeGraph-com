/**
 * Graph adapter - converts API response to Sigma.js / Graphology format.
 *
 * Sigma.js uses Graphology as its data model. This module converts the
 * backend GraphData DTO into a Graphology Graph instance ready for rendering.
 */

import Graph from 'graphology'
import type { GraphData, GraphNode, GraphEdge, NodeType, EdgeType } from '@/types/graph'
import { NODE_COLORS, EDGE_COLORS, NODE_SIZES } from './constants'

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
 * Convert backend GraphData to a Graphology Graph instance.
 * Assigns random initial positions; ForceAtlas2 will handle layout.
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
  return {
    label: node.name,
    x: Math.random() * 1000 - 500,
    y: Math.random() * 1000 - 500,
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
function getEdgeAttributes(edge: GraphEdge): SigmaEdgeAttributes {
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
 * Return node size based on type. Structural nodes are larger.
 */
function getNodeSize(nodeType: NodeType): number {
  switch (nodeType) {
    case 'File':
      return 6.5
    case 'Project':
    case 'Package':
      return 6
    case 'Class':
    case 'Interface':
    case 'Enum':
    case 'Record':
    case 'DBModel':
      return 5
    case 'Method':
    case 'Constructor':
    case 'Route':
    case 'APIEndpoint':
      return 4
    case 'Field':
    case 'Annotation':
    case 'External':
      return NODE_SIZES.min
    default:
      return NODE_SIZES.default
  }
}
