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
  size: number
  edgeType: EdgeType
}

/**
 * Convert backend GraphData to a Graphology Graph instance.
 * Assigns random initial positions; ForceAtlas2 will handle layout.
 */
export function apiToGraphology(data: GraphData): Graph {
  const graph = new Graph({ multi: true, type: 'directed' })

  for (const node of data.nodes) {
    const attrs = getNodeAttributes(node)
    graph.addNode(node.id, attrs)
  }

  for (const edge of data.edges) {
    // Skip edges referencing nodes not in the graph
    if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target)) continue
    const attrs = getEdgeAttributes(edge)
    // Edge ids are deterministic (`source|type|target`). Parallel edges of the
    // same type between the same pair (e.g. two PARAMETER_TYPE edges) collapse to
    // the same key, so suffix duplicates to keep the multigraph key unique.
    let key = edge.id
    if (graph.hasEdge(key)) {
      let suffix = 2
      while (graph.hasEdge(`${key}#${suffix}`)) suffix++
      key = `${key}#${suffix}`
    }
    graph.addEdgeWithKey(key, edge.source, edge.target, attrs)
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
  return {
    label: edge.type,
    color: getEdgeColor(edge.type),
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
    case 'Project':
      return NODE_SIZES.max
    case 'Package':
      return 12
    case 'File':
      return 10
    case 'Class':
    case 'Interface':
    case 'Enum':
      return 8
    case 'Method':
    case 'Route':
      return NODE_SIZES.default
    case 'Field':
    case 'Annotation':
      return NODE_SIZES.min
    case 'External':
      return NODE_SIZES.min
    default:
      return NODE_SIZES.default
  }
}
