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
// Tuned so the DEFAULT-VISIBLE structural edges (IMPORTS, CALLS, HANDLES_ROUTE,
// EXTENDS, IMPLEMENTS, OVERRIDES) sit on clearly different hues; the CPG-lite
// (hidden-by-default) ones fill the gaps.
export const EDGE_COLORS: Record<EdgeType, string> = {
  // ── Default-visible structural edges (must be unmistakable) ──
  CALLS: '#DC2626', // red
  IMPORTS: '#2563EB', // blue
  DEFINES: '#16A34A', // green
  CONTAINS: '#9333EA', // purple
  HAS_METHOD: '#0891B2', // cyan
  HAS_INNER: '#DB2777', // magenta
  EXTENDS: '#EA580C', // orange
  IMPLEMENTS: '#CA8A04', // gold
  OVERRIDES: '#7C3AED', // violet
  HANDLES_ROUTE: '#65A30D', // olive
  // ── CPG-lite (hidden by default) ──
  HAS_FIELD: '#0EA5E9', // sky
  TYPE_OF: '#14B8A6', // teal
  RETURNS: '#84CC16', // lime
  PARAMETER_TYPE: '#A16207', // brown
  THROWS: '#BE123C', // crimson
  INSTANTIATES: '#FB7185', // light rose
  INJECTS: '#0D9488', // deep teal
  ANNOTATED_BY: '#A3E635', // light lime
  READS: '#38BDF8', // light blue
  WRITES: '#FB923C', // light orange
  CATCHES: '#A78BFA', // light violet
  STEP_IN_FLOW: '#E879F9', // light fuchsia
  OWNS: '#6366F1', // indigo
}

// Filter baseline policy.
//
// The backend is the source of truth for aggregation. The frontend keeps every
// backend-emitted node/edge in the store and curates the DEFAULT view only
// through hidden filter sets.

export const BASELINE_NODE_TYPES: ReadonlySet<NodeType> = new Set<NodeType>([
  'Project',
  'Package',
  'File',
  'Class',
  'Interface',
  'Enum',
  'Record',
  'DBModel',
  'Method',
  'Constructor',
  'APIEndpoint',
])

export const DETAIL_NODE_TYPES: ReadonlySet<NodeType> = new Set<NodeType>([
  'Field',
  'Annotation',
  'LocalVariable',
  'Route',
  'External',
])

export const BASELINE_EDGE_TYPES: ReadonlySet<EdgeType> = new Set<EdgeType>([
  'CONTAINS',
  'DEFINES',
  'HAS_METHOD',
  'HAS_INNER',
  'IMPORTS',
  'CALLS',
  'INJECTS',
  'HANDLES_ROUTE',
  'EXTENDS',
  'IMPLEMENTS',
  'OVERRIDES',
  'STEP_IN_FLOW',
])

export const DETAIL_EDGE_TYPES: ReadonlySet<EdgeType> = new Set<EdgeType>([
  'OWNS',
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
])

// Backwards-compatible aliases for existing callers/tests.
export const STRUCTURAL_EDGE_TYPES: ReadonlySet<EdgeType> = BASELINE_EDGE_TYPES
export const CPG_LITE_EDGE_TYPES: ReadonlySet<EdgeType> = DETAIL_EDGE_TYPES

// Edge types hidden by default. The filter store initializes `hiddenEdgeTypes`
// from this set so the default graph stays readable while every type with a
// count > 0 remains revealable.
export const DEFAULT_HIDDEN_EDGE_TYPES: ReadonlySet<EdgeType> = DETAIL_EDGE_TYPES
export const DEFAULT_HIDDEN_NODE_TYPES: ReadonlySet<NodeType> = DETAIL_NODE_TYPES

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

const LOCAL_DEV_HOSTS = new Set(['localhost', '127.0.0.1'])

function currentBrowserHostname(): string | null {
  if (typeof window === 'undefined') return null
  return window.location.hostname
}

function trimTrailingSlash(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value
}

/**
 * Keep browser session cookies working in local dev regardless of whether the user
 * opens Vite as localhost or 127.0.0.1. Cookies are host-scoped, so a page opened
 * on 127.0.0.1 must call the backend on 127.0.0.1 too; calling localhost would
 * create a different cookie jar and /api/auth/me would return 401 after login.
 */
export function resolveLocalhostAwareUrl(
  configuredUrl: string | undefined,
  fallbackUrl: string,
  browserHostname = currentBrowserHostname(),
): string {
  const raw = configuredUrl?.trim() || fallbackUrl
  if (!browserHostname || !LOCAL_DEV_HOSTS.has(browserHostname)) {
    return trimTrailingSlash(raw)
  }

  try {
    const url = new URL(raw)
    if (LOCAL_DEV_HOSTS.has(url.hostname)) {
      url.hostname = browserHostname
    }
    return trimTrailingSlash(url.toString())
  } catch {
    return trimTrailingSlash(raw)
  }
}

// API base URL
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
