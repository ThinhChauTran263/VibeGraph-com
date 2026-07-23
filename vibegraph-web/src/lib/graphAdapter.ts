/**
 * Graph adapter - converts API response to Sigma.js / Graphology format.
 *
 * Sigma.js uses Graphology as its data model. This module converts the
 * backend GraphData DTO into a Graphology Graph instance ready for rendering.
 */

import Graph from 'graphology'
import type { GraphData, GraphNode, GraphEdge, NodeType, EdgeType } from '@/types/graph'
import { NODE_COLORS, EDGE_COLORS, NODE_SIZES, NODE_SIZE_BY_TYPE } from './constants'
import { SIGMA_EDGE_SIZE } from './runtimeConfig'

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
  layer: string
  packageName: string
  community: string
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
  weight?: number
  occurrences?: number
}

/**
 * Deterministic 32-bit FNV-1a hash -> float in [0, 1). Used to seed a node's
 * initial position from its stable id so the layout is reproducible.
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
 * Deterministic initial position in a wider square, derived from the node id.
 * Spreads nodes out enough that ForceAtlas2 gets an initial 2D cloud instead of
 * starting from a pile around the origin.
 */
function seededPosition(id: string): { x: number; y: number } {
  return {
    x: seededUnit(`${id}#x`) * 800 - 400,
    y: seededUnit(`${id}#y`) * 800 - 400,
  }
}

/**
 * Convert backend GraphData to a Graphology Graph instance.
 * Assigns deterministic initial positions (seeded from node id) so ForceAtlas2
 * converges to the same layout on every load.
 */
export function apiToGraphology(data: GraphData): Graph {
  const graph = new Graph({ multi: true, type: 'directed' })
  const nodeKeys = new Set(data.nodes.map((node) => node.id))

  graph.import({
    nodes: data.nodes.map((node) => ({
      key: node.id,
      attributes: getNodeAttributes(node),
    })),
    edges: data.edges
      .filter((edge) => nodeKeys.has(edge.source) && nodeKeys.has(edge.target))
      .map((edge) => ({
        key: edge.id,
        source: edge.source,
        target: edge.target,
        attributes: getEdgeAttributes(edge),
      })),
  })

  return graph
}

/**
 * Build Sigma node attributes from a GraphNode.
 */
function getNodeAttributes(node: GraphNode): SigmaNodeAttributes {
  const { x, y } = seededPosition(node.id)
  const packageName = extractPackageName(node)
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
    layer: getLayer(node),
    packageName,
    community: packageName,
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
    size: SIGMA_EDGE_SIZE,
    edgeType: edge.type,
    weight: edge.weight,
    occurrences: edge.occurrences,
  }
}

function stringProperty(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0 ? value : undefined
}

function getLayer(node: GraphNode): string {
  return stringProperty(node.properties.layer) ?? stringProperty(node.properties.springLayer) ?? ''
}

function extractPackageName(node: GraphNode): string {
  const explicit = stringProperty(node.properties.packageName)
  if (explicit) return explicit

  const fromFullName = packageNameFromFullName(node.fullName, node.type)
  if (fromFullName) return fromFullName

  return packageNameFromFilePath(node.filePath) ?? ''
}

function packageNameFromFullName(fullName: string, nodeType: NodeType): string | undefined {
  if (!fullName || fullName.includes('/') || fullName.includes(' ')) return undefined
  if (nodeType === 'File') return undefined
  const withoutParams = fullName.replace(/\(.*/, '')
  const parts = withoutParams.split('.').filter(Boolean)
  if (parts.length < 2) return undefined

  if (nodeType === 'Package') return withoutParams
  if (
    nodeType === 'Method' ||
    nodeType === 'Constructor' ||
    nodeType === 'Field' ||
    nodeType === 'LocalVariable'
  ) {
    return parts.length > 2 ? parts.slice(0, -2).join('.') : undefined
  }
  return parts.slice(0, -1).join('.')
}

function packageNameFromFilePath(filePath: string): string | undefined {
  if (!filePath) return undefined
  const normalized = filePath.replace(/\\/g, '/')
  const sourceRoot = normalized.match(/(?:^|\/)src\/(?:main|test)\/(?:java|kotlin)\/(.+)\/[^/]+$/)
  if (sourceRoot?.[1]) return sourceRoot[1].replace(/\//g, '.')

  const dirs = normalized.split('/').slice(0, -1).filter(Boolean)
  return dirs.length > 0 ? dirs.join('.') : undefined
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
