/**
 * Graph data types for VibeGraph frontend.
 *
 * Source of truth: VibeGraph-specs-2month/neo4j-schema.md
 * Mirrors backend DTOs: graph/dto/response/{NodeDto, EdgeDto, GraphDataResponse}.java
 *
 * IMPORTANT: This file is the FE half of the BE/FE contract.
 * Do not add fields here without adding them to the BE DTOs in the same change.
 */

export type NodeType =
  | 'Project'
  | 'Package'
  | 'File'
  | 'Class'
  | 'Interface'
  | 'Enum'
  | 'Record'
  | 'DBModel'
  | 'Method'
  | 'Constructor'
  | 'Field'
  | 'Annotation'
  | 'Route'
  | 'APIEndpoint'
  | 'External'

export type EdgeType =
  | 'OWNS'
  | 'CONTAINS'
  | 'DEFINES'
  | 'HAS_METHOD'
  | 'HAS_FIELD'
  | 'HAS_INNER'
  | 'EXTENDS'
  | 'IMPLEMENTS'
  | 'OVERRIDES'
  | 'IMPORTS'
  | 'TYPE_OF'
  | 'RETURNS'
  | 'PARAMETER_TYPE'
  | 'THROWS'
  | 'CALLS'
  | 'INJECTS'
  | 'HANDLES_ROUTE'
  | 'ANNOTATED_BY'

export interface GraphNode {
  id: string
  type: NodeType
  name: string
  fullName: string
  filePath: string
  lineNumber: number
  /** Schema-specific fields (visibility, springLayer, kind, httpMethod, ...) — see neo4j-schema.md §2. */
  properties: Record<string, unknown>
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  type: EdgeType
  confidence?: number
  lineNumber?: number
}

export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
  nodeStats: Record<NodeType, number>
  edgeStats: Record<EdgeType, number>
}

/**
 * Realtime graph-update events delivered over the STOMP topic
 * `/topic/projects/{projectId}/updates`.
 *
 * CONTRACT (Sprint 2): kept in sync with the backend producer records
 * `graph/websocket/{GraphUpdateEvent, GraphChangeSet, GraphRemoval}.java`
 * (T36). The broadcast producer is implemented; the automatic file-change
 * trigger (T25 file watcher) is still pending, so events are not yet emitted
 * on real source edits. The consumer validates payloads defensively at the
 * boundary regardless.
 */
export interface GraphFullUpdateEvent {
  type: 'FULL_UPDATE'
  projectId: string
  graph: GraphData
}

export interface GraphIncrementalEvent {
  type: 'INCREMENTAL'
  projectId: string
  added?: { nodes?: GraphNode[]; edges?: GraphEdge[] }
  modified?: { nodes?: GraphNode[]; edges?: GraphEdge[] }
  removed?: { nodeIds?: string[]; edgeIds?: string[] }
}

export type GraphUpdateEvent = GraphFullUpdateEvent | GraphIncrementalEvent
