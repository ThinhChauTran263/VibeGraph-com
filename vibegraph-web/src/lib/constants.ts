/**
 * VibeGraph constants - colors, sizes, and configuration.
 */

import type { NodeType, EdgeType } from '@/types/graph'

// Node colors by type - matches NodeType from graph.ts
export const NODE_COLORS: Record<NodeType, string> = {
  Project: '#6366F1',      // indigo
  Package: '#8B5CF6',      // purple
  File: '#EF4444',         // red
  Class: '#F59E0B',        // amber
  Interface: '#22C55E',    // green
  Enum: '#A855F7',         // violet
  Method: '#3B82F6',       // blue
  Field: '#06B6D4',        // cyan
  Annotation: '#F97316',   // orange
  Route: '#10B981',        // emerald
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
  INJECTS: '#0D9488',      // teal-600
  HANDLES_ROUTE: '#059669', // emerald-600
  ANNOTATED_BY: '#F59E0B', // amber
}

// Default node sizes
export const NODE_SIZES = {
  default: 5,
  min: 3,
  max: 20,
}

// Focus mode opacity
export const FOCUS_OPACITY = {
  active: 1.0,
  dimmed: 0.1,
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
