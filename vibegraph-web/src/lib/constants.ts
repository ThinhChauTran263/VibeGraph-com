/**
 * VibeGraph constants - colors, sizes, and configuration.
 */

// Node colors by type
export const NODE_COLORS: Record<string, string> = {
  Method: '#3B82F6',       // blue
  File: '#EF4444',         // red
  APIEndpoint: '#10B981',  // bright green
  Class: '#F59E0B',        // yellow/amber
  DBModel: '#D97706',      // dark yellow
  Interface: '#22C55E',    // green
  Constructor: '#06B6D4',  // cyan
  Enum: '#8B5CF6',         // purple
  Record: '#F97316',       // orange
}

// Edge colors by relationship type
export const EDGE_COLORS: Record<string, string> = {
  DEFINES: '#22C55E',      // green
  CALLS: '#EF4444',        // red
  IMPORTS: '#3B82F6',      // blue
  EXTENDS: '#F97316',      // orange
  IMPLEMENTS: '#EC4899',   // pink
  HAS_METHOD: '#06B6D4',   // cyan
  HANDLES_ROUTE: '#059669', // dark green
  HAS_FIELD: '#6366F1',    // indigo
  DEPENDS_ON: '#8B5CF6',   // purple
  ANNOTATED_BY: '#F59E0B', // amber
  INJECTS: '#14B8A6',      // teal
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

// API base URL
export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// WebSocket URL
export const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws'
