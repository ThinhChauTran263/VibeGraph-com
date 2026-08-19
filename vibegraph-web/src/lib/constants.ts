/**
 * VibeGraph constants - colors, sizes, and configuration.
 */

import type { NodeType, EdgeType } from '@/types/graph'
import {
  NODE_SIZE_DEFAULT,
  NODE_SIZE_MIN,
  NODE_SIZE_MAX,
  NODE_SIZE_PROJECT,
  NODE_SIZE_PACKAGE,
  NODE_SIZE_FILE,
  NODE_SIZE_TYPE,
  NODE_SIZE_MEMBER,
  NODE_SIZE_ENDPOINT,
  FOCUS_OPACITY_ACTIVE,
  FOCUS_OPACITY_DIMMED,
} from '@/lib/runtimeConfig'

export const ALL_NODE_TYPES: readonly NodeType[] = Object.freeze([
  'Project',
  'File',
  'Class',
  'Interface',
  'Enum',
  'Record',
  'DBModel',
  'Method',
  'Constructor',
  'Field',
  'Annotation',
  'LocalVariable',
  'Route',
  'APIEndpoint',
  'External',
])

export const ALL_EDGE_TYPES: readonly EdgeType[] = Object.freeze([
  'OWNS',
  'CONTAINS',
  'DEFINES',
  'HAS_METHOD',
  'HAS_FIELD',
  'HAS_INNER',
  'HAS_RELATION',
  'EXTENDS',
  'IMPLEMENTS',
  'OVERRIDES',
  'IMPORTS',
  'TYPE_OF',
  'RETURNS',
  'PARAMETER_TYPE',
  'THROWS',
  'CALLS',
  'INSTANTIATES',
  'INJECTS',
  'HANDLES_ROUTE',
  'ANNOTATED_BY',
  'READS',
  'WRITES',
  'CATCHES',
  'STEP_IN_FLOW',
  'PUBLISHES_EVENT',
  'LISTENS_EVENT',
  'TRIGGERS',
  'RESOLVES_TO',
  'CALLS_DYNAMIC',
  'DISPATCH_CANDIDATES',
])

export const DEEP_LOAD_NODE_TYPES: ReadonlySet<NodeType> = new Set<NodeType>([
  'Project',
  'Field',
  'Annotation',
  'LocalVariable',
  'External',
])

export const DEEP_LOAD_EDGE_TYPES: ReadonlySet<EdgeType> = new Set<EdgeType>([
  'HAS_FIELD',
  'TYPE_OF',
  'RETURNS',
  'PARAMETER_TYPE',
  'THROWS',
  'INSTANTIATES',
  'ANNOTATED_BY',
  'READS',
  'WRITES',
  'CATCHES',
  'PUBLISHES_EVENT',
  'LISTENS_EVENT',
  'CALLS_DYNAMIC',
  'DISPATCH_CANDIDATES',
])
// Node colors by type - matches NodeType from graph.ts.
// Chosen for MAXIMUM distinctness: each type sits on a clearly different hue (and
// the frequent types are spread far apart) so they're recognizable at a glance.
export const NODE_COLORS: Record<NodeType, string> = {
  Method: '#2563EB', // blue
  Field: '#06B6D4', // cyan
  Constructor: '#5EEAD4', // pale teal (distinct from Field cyan)
  File: '#EF4444', // red
  Class: '#F59E0B', // amber / gold
  Annotation: '#65A30D', // olive green (far from Class amber & Interface green)
  DBModel: '#CA8A04', // mustard
  Record: '#7C2D12', // dark brown
  Interface: '#22C55E', // green
  Route: '#15803D', // dark green
  Package: '#9333EA', // purple
  Enum: '#C084FC', // light lilac (distinct from purple & pink)
  APIEndpoint: '#EC4899', // pink
  Project: '#4F46E5', // indigo
  LocalVariable: '#64748B', // slate
  External: '#9CA3AF', // gray
}

// Edge colors by relationship type - matches EdgeType from graph.ts.
// Saturated, unique colors keep thin lines legible on the dark canvas without
// washing out into white. Frequently co-visible types use different hues.
export const EDGE_COLORS: Record<EdgeType, string> = {
  // ── Default-visible structural edges (must be unmistakable) ──
  CALLS: '#F6515C', // coral red
  IMPORTS: '#3D8DF5', // vivid blue
  DEFINES: '#25D05E', // emerald green
  CONTAINS: '#ABC431', // olive
  HAS_METHOD: '#008C6A', // deep teal
  HAS_INNER: '#F320A6', // hot pink
  HAS_RELATION: '#F5BE3D', // gold
  EXTENDS: '#C48931', // amber
  IMPLEMENTS: '#F36E20', // orange
  OVERRIDES: '#8163E3', // indigo
  HANDLES_ROUTE: '#00879A', // dark cyan
  // ── CPG-lite (hidden by default) ──
  HAS_FIELD: '#51B4F6', // sky blue
  TYPE_OF: '#31A2C4', // blue teal
  RETURNS: '#5CD6A1', // mint green
  PARAMETER_TYPE: '#F69851', // peach orange
  THROWS: '#E92A0C', // red orange
  INSTANTIATES: '#DA6CA7', // rose
  INJECTS: '#7DE363', // leaf green
  ANNOTATED_BY: '#F6E551', // yellow
  READS: '#20F320', // vivid green
  WRITES: '#D04377', // berry
  CATCHES: '#AE6CDA', // violet
  STEP_IN_FLOW: '#51F6C4', // turquoise
  PUBLISHES_EVENT: '#F651E5', // magenta
  LISTENS_EVENT: '#B4F320', // lime
  TRIGGERS: '#C45831', // burnt orange
  RESOLVES_TO: '#C547EB', // purple
  CALLS_DYNAMIC: '#317AC4', // strong blue
  DISPATCH_CANDIDATES: '#C4DA6C', // yellow green
  OWNS: '#D0A825', // mustard
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
// As of Phase 2+, the parser additionally emits CONTAINS (Package hierarchy),
// OVERRIDES, INSTANTIATES, and guarded HAS_RELATION domain edges. Annotation
// usages are node metadata, not ANNOTATED_BY graph edges.

// Default-VISIBLE structural relationships (architecture graph).
export const STRUCTURAL_EDGE_TYPES: ReadonlySet<EdgeType> = new Set<EdgeType>([
  'CONTAINS',
  'DEFINES',
  'HAS_METHOD',
  'HAS_INNER',
  'HAS_RELATION',
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
  'PUBLISHES_EVENT',
  'LISTENS_EVENT',
  'TRIGGERS',
  'RESOLVES_TO',
  'CALLS_DYNAMIC',
  'DISPATCH_CANDIDATES',
])

// Edge types hidden by default. The filter store initializes `hiddenEdgeTypes`
// from this set so the default graph stays readable while every type with a
// count > 0 remains revealable.
export const DEFAULT_HIDDEN_EDGE_TYPES: ReadonlySet<EdgeType> = new Set<EdgeType>([
  'HAS_FIELD',
  'RETURNS',
  'TYPE_OF',
  'PARAMETER_TYPE',
  'THROWS',
  'INSTANTIATES',
  'ANNOTATED_BY',
  'READS',
  'WRITES',
  'CATCHES',
  'PUBLISHES_EVENT',
  'LISTENS_EVENT',
  'TRIGGERS',
  'CALLS_DYNAMIC',
  'DISPATCH_CANDIDATES',
])

// Node types hidden by default so the architecture graph stays readable, matching
// the density of comparable tools. These low-signal leaf/structural types add most
// of the on-screen clutter (Field/External/Annotation alone are ~40% of nodes on a
// typical project) without conveying architecture. All remain in the data and are
// revealed via the Node Types "Show all" button or by toggling each type.
//   - LocalVariable: deep-CPG detail (only present with the backend deep-cpg flag)
//   - Field / Annotation: member-level noise that clutters every class
//   - External: third-party symbols outside the project
//   - Project: structural container (the Explorer tree already shows it)
export const DEFAULT_HIDDEN_NODE_TYPES: ReadonlySet<NodeType> = new Set<NodeType>([
  'Field',
  'LocalVariable',
  'Annotation',
  'Project',
  'External',
])

// Default node sizes (sourced from env via runtimeConfig)
export const NODE_SIZES = {
  default: NODE_SIZE_DEFAULT,
  min: NODE_SIZE_MIN,
  max: NODE_SIZE_MAX,
}

// Per-type rendered node radius, sourced from env via runtimeConfig.
//
// Sizes encode the containment hierarchy so the graph reads at a glance:
// the wider a node's scope (and the rarer the type), the larger it renders;
// the deeper / more numerous the type, the smaller it stays so dense member
// nodes never drown out the architecture.
//
//   Project (8) > Package (7) > File (6) > Type decls (5)
//     > Method/Constructor + Route/APIEndpoint (4) > Field/Annotation/External/LocalVariable (3)
export const NODE_SIZE_BY_TYPE: Record<NodeType, number> = {
  Project: NODE_SIZE_PROJECT,
  Package: NODE_SIZE_PACKAGE,
  File: NODE_SIZE_FILE,
  Class: NODE_SIZE_TYPE,
  Interface: NODE_SIZE_TYPE,
  Enum: NODE_SIZE_TYPE,
  Record: NODE_SIZE_TYPE,
  DBModel: NODE_SIZE_TYPE,
  Method: NODE_SIZE_MEMBER,
  Constructor: NODE_SIZE_MEMBER,
  Route: NODE_SIZE_ENDPOINT,
  APIEndpoint: NODE_SIZE_ENDPOINT,
  Field: NODE_SIZE_MIN,
  Annotation: NODE_SIZE_MIN,
  External: NODE_SIZE_MIN,
  LocalVariable: NODE_SIZE_MIN,
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
export function resolveLocalhostAwareUrl(
  value: string | undefined,
  fallback: string,
  browserHost = typeof window !== 'undefined' ? window.location.hostname : 'localhost',
): string {
  const url = value || fallback
  if (browserHost !== '127.0.0.1') return url

  try {
    const parsed = new URL(url)
    if (parsed.hostname === 'localhost') {
      parsed.hostname = '127.0.0.1'
      return parsed.toString().replace(/\/$/, '')
    }
  } catch {
    return url
  }

  return url
}

export const API_BASE_URL = resolveLocalhostAwareUrl(
  import.meta.env.VITE_API_URL,
  'http://localhost:8080',
)

// WebSocket URL - SockJS endpoint for STOMP. Must match the backend
// `/ws/graph-updates` registration. SockJS requires an http(s):// URL (not ws://).
export const WS_URL = resolveLocalhostAwareUrl(
  import.meta.env.VITE_WS_URL,
  'http://localhost:8080/ws/graph-updates',
)
