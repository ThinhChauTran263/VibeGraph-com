/**
 * Multi-node focus for Data Flow Analysis.
 *
 * Where {@link createSelectionFocusReducers} spotlights a single node and its
 * neighborhood, these helpers spotlight an entire traced chain: every node in the
 * flow plus the edges connecting consecutive steps stay bright and labelled, and
 * everything else is handed to the ghost background layer (hidden in the
 * interactive Sigma so it can never paint over a foreground edge).
 */

import type Graph from 'graphology'
import type { FocusPartition, FocusReducers } from './focusMode'
import { relatedEdgeLabelColor } from './focusMode'

const FLOW_PRIMARY_SIZE_MULTIPLIER = 1.25
const FLOW_NODE_SIZE_MULTIPLIER = 1.08
const FLOW_EDGE_SIZE_MULTIPLIER = 1.4
const Z_FLOW_PRIMARY = 3
const Z_FLOW_NODE = 2
const Z_FLOW_EDGE = 2

function scaleSize(size: unknown, multiplier: number): unknown {
  return typeof size === 'number' ? Math.round(size * multiplier * 100) / 100 : size
}

/**
 * Partition the graph into the flow (foreground) and everything else
 * (background). The background set is what the ghost canvas redraws.
 */
export function partitionFlowGraph(
  nodeIds: Set<string>,
  edgeIds: Set<string>,
  graph: Graph,
): FocusPartition {
  const foregroundNodes = new Set<string>()
  const backgroundNodes = new Set<string>()
  graph.forEachNode((node) => {
    if (nodeIds.has(node)) foregroundNodes.add(node)
    else backgroundNodes.add(node)
  })

  const foregroundEdges = new Set<string>()
  const backgroundEdges = new Set<string>()
  graph.forEachEdge((edge, _attributes, source, target) => {
    // An edge is part of the flow if it was traced, or it directly connects two
    // flow nodes (so highlighting stays continuous even if an exact edge id drifts).
    if (edgeIds.has(edge) || (nodeIds.has(source) && nodeIds.has(target))) {
      foregroundEdges.add(edge)
    } else {
      backgroundEdges.add(edge)
    }
  })

  return { foregroundNodes, backgroundNodes, foregroundEdges, backgroundEdges }
}

/**
 * Build Sigma reducers that highlight a traced flow. `primaryId` (the selected
 * step) is enlarged the most so the active step stands out within the chain.
 */
export function createFlowFocusReducers(
  nodeIds: Set<string>,
  edgeIds: Set<string>,
  graph: Graph,
  primaryId: string | null = null,
): FocusReducers {
  return {
    nodeReducer: (node, attributes) => {
      if (!nodeIds.has(node)) {
        return { ...attributes, hidden: true }
      }
      const isPrimary = node === primaryId
      return {
        ...attributes,
        hidden: false,
        highlighted: isPrimary,
        forceLabel: true,
        size: scaleSize(
          attributes.size,
          isPrimary ? FLOW_PRIMARY_SIZE_MULTIPLIER : FLOW_NODE_SIZE_MULTIPLIER,
        ),
        zIndex: isPrimary ? Z_FLOW_PRIMARY : Z_FLOW_NODE,
      }
    },
    edgeReducer: (edge, attributes) => {
      const source = graph.source(edge)
      const target = graph.target(edge)
      const inFlow = edgeIds.has(edge) || (nodeIds.has(source) && nodeIds.has(target))
      if (!inFlow) {
        return { ...attributes, hidden: true }
      }
      return {
        ...attributes,
        hidden: false,
        forceLabel: true,
        size:
          typeof attributes.size === 'number'
            ? attributes.size * FLOW_EDGE_SIZE_MULTIPLIER
            : attributes.size,
        labelColor: relatedEdgeLabelColor(attributes),
        zIndex: Z_FLOW_EDGE,
      }
    },
  }
}
