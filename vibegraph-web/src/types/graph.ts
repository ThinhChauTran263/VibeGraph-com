/**
 * Graph data types for VibeGraph frontend.
 */

export type NodeType =
  | 'Method'
  | 'File'
  | 'APIEndpoint'
  | 'Class'
  | 'DBModel'
  | 'Interface'
  | 'Constructor'
  | 'Enum'
  | 'Record'

export type EdgeType =
  | 'DEFINES'
  | 'CALLS'
  | 'IMPORTS'
  | 'EXTENDS'
  | 'IMPLEMENTS'
  | 'HAS_METHOD'
  | 'HAS_FIELD'
  | 'HANDLES_ROUTE'
  | 'DEPENDS_ON'
  | 'ANNOTATED_BY'
  | 'INJECTS'
  | 'TYPE_OF'

export interface GraphNode {
  id: string
  name: string
  type: NodeType
  fullName: string
  filePath: string
  lineNumber: number
  visibility?: string
  springLayer?: string
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
}
