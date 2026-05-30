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
  | 'Method'
  | 'Field'
  | 'Annotation'
  | 'Route'
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
