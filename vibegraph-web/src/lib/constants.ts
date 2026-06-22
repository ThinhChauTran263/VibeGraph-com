/**
 * VibeGraph constants - colors, sizes, and configuration.
 */

import type { NodeType, EdgeType } from '@/types/graph'
import {
  NODE_SIZE_DEFAULT,
  NODE_SIZE_MIN,
  NODE_SIZE_MAX,
  FOCUS_OPACITY_ACTIVE,
  FOCUS_OPACITY_DIMMED,
} from '@/lib/runtimeConfig'
// Node colors by type - matches NodeType from graph.ts
export const NODE_COLORS: Record<NodeType, string> = {
  Project: '#6366F1',      // indigo
  Package: '#8B5CF6',      // purple
  File: '#EF4444',         // red
  Class: '#F59E0B',        // amber
  DBModel: '#D6D35F',      // muted yellow - persistence/domain model
  Interface: '#22C55E',    // green
  Enum: '#A855F7',         // violet
  Record: '#D97706',       // orange-brown - Java record
  Method: '#3B82F6',       // blue
  Constructor: '#06B6D4',  // cyan - constructor member
  Field: '#06B6D4',        // cyan
  Annotation: '#F97316',   // orange
  LocalVariable: '#64748B', // slate-500 - body-level local/parameter (deep CPG)
  Route: '#10B981',        // emerald
  APIEndpoint: '#22C55E',  // green - HTTP endpoint
  External: '#94A3B8',     // slate-400 - library/JDK or unresolved stub
}

// Edge colors by relationship type - matches EdgeType from graph.ts
export const EDGE_COLORS: Record<EdgeType, string> = {
  OWNS: '#6366F1',         // indigo
  CONTAINS: '#8B5CF6',     // purple
  DEFINES: '#22C55E',      // green
  HAS_METHOD: '#06B6D4',   // cyan
  HAS_FIELD: '#0EA5E9',    // sky
  HAS_INNER: '#A855F7',    // violet
  EXTENDS: '#F97316',      // orange
  IMPLEMENTS: '#EC4899',   // pink
  OVERRIDES: '#F43F5E',    // rose
  IMPORTS: '#3B82F6',      // blue
  TYPE_OF: '#14B8A6',      // teal
  RETURNS: '#10B981',      // emerald
  PARAMETER_TYPE: '#84CC16', // lime
  THROWS: '#EF4444',       // red
  CALLS: '#DC2626',        // red-600
  INSTANTIATES: '#FB7185', // rose-400 - object creation (new X())
  INJECTS: '#0D9488',      // teal-600
  HANDLES_ROUTE: '#059669', // emerald-600
  ANNOTATED_BY: '#F59E0B', // amber
  READS: '#38BDF8',        // sky-400 - data-flow read (deep CPG)
  WRITES: '#FB923C',       // orange-400 - data-flow write (deep CPG)
  CATCHES: '#A78BFA',      // violet-400 - exception caught (deep CPG)
  STEP_IN_FLOW: '#E879F9', // fuchsia-400 - inferred execution-flow step
}

// CPG-lite exposure policy (Phase 1).
//
// The backend parser emits both high-level STRUCTURAL relationships and deeper
// CPG-lite (type/dependency) relationships. Rather than DROPPING the CPG-lite
// edges at the data boundary (which made them impossible to ever reveal), we keep
// every backend-emitted edge in the store and curate VISIBILITY through the
// filter state:
//   - STRUCTURAL_EDGE_TYPES are visible by default (readable architecture view).
//   - CPG_LITE_EDGE_TYPES are hidden by default but fully revealable via the Edge
//     Types "Show all" button / advanced filter.
//
// Only relationship types the parser actually emits are listed. OWNS exists in
// the schema/contract enum but is NOT currently produced by any parser visitor,
// so it is intentionally absent from both sets (it would never have a count > 0).
// As of Phase 2, the parser additionally emits CONTAINS (Package hierarchy),
// OVERRIDES, ANNOTATED_BY, and INSTANTIATES.

// Default-VISIBLE structural relationships (architecture graph).
export const STRUCTURAL_EDGE_TYPES: ReadonlySet<EdgeType> = new Set<EdgeType>([
  'CONTAINS',
  'DEFINES',
  'HAS_METHOD',
  'HAS_INNER',
  'EXTENDS',
  'IMPLEMENTS',
  'OVERRIDES',
  'IMPORTS',
  'CALLS',
  'HANDLES_ROUTE',
])

// Default-HIDDEN CPG-lite relationships (type / parameter / return / throws /
// field-ownership / injection / instantiation / annotation metadata). Emitted by
// the backend, revealed via "Show all".
export const CPG_LITE_EDGE_TYPES: ReadonlySet<EdgeType> = new Set<EdgeType>([
  'HAS_FIELD',
  'TYPE_OF',
  'RETURNS',
  'PARAMETER_TYPE',
  'THROWS',
  'INSTANTIATES',
  'INJECTS',
  'ANNOTATED_BY',
  // Phase 3 deep CPG (body-level data-flow): default-hidden, revealed via "Show all".
  'READS',
  'WRITES',
  'CATCHES',
  // Phase 4 inferred execution flow: default-hidden, revealed via "Show all".
  'STEP_IN_FLOW',
])

// Edge types hidden by default. The filter store initializes `hiddenEdgeTypes`
// from this set so the default graph stays readable while every type with a
// count > 0 remains revealable.
export const DEFAULT_HIDDEN_EDGE_TYPES: ReadonlySet<EdgeType> = CPG_LITE_EDGE_TYPES

// Node types hidden by default. `LocalVariable` (deep CPG) is only present when
// the backend deep-cpg flag is enabled; it is hidden by default to keep the graph
// readable and revealed via the Node Types "Show all" button.
export const DEFAULT_HIDDEN_NODE_TYPES: ReadonlySet<NodeType> = new Set<NodeType>([
  'LocalVariable',
])

// Default node sizes (sourced from env via runtimeConfig)
export const NODE_SIZES = {
  default: NODE_SIZE_DEFAULT,
  min: NODE_SIZE_MIN,
  max: NODE_SIZE_MAX,
}

// Focus mode opacity (sourced from env via runtimeConfig)
export const FOCUS_OPACITY = {
  active: FOCUS_OPACITY_ACTIVE,
  dimmed: FOCUS_OPACITY_DIMMED,
}

// Label color for a hovered / clicked / focused node. The default Sigma hover
// renderer paints a solid white label box; we override the renderer (see
// useSigma.ts) to draw text only in this highlight color over a dark halo so the
// label stays readable on the dark canvas without an opaque white rectangle.
export const HIGHLIGHT_LABEL_COLOR = '#facc15' // amber-400 / yellow

// Default node label color on the dark canvas.
export const DEFAULT_LABEL_COLOR = '#e5e7eb' // gray-200

// API base URL
export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// WebSocket URL - SockJS endpoint for STOMP. Must match the backend
// `/ws/graph-updates` registration. SockJS requires an http(s):// URL (not ws://).
export const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws/graph-updates'
