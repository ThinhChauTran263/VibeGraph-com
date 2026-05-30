/**
 * Color utility helpers.
 */
import { NODE_COLORS, EDGE_COLORS } from './constants'
import type { NodeType, EdgeType } from '@/types/graph'

export function getNodeColor(nodeType: string): string {
  return (NODE_COLORS as Record<string, string>)[nodeType as NodeType] || '#888888'
}

export function getEdgeColor(edgeType: string): string {
  return (EDGE_COLORS as Record<string, string>)[edgeType as EdgeType] || '#666666'
}

/**
 * Apply opacity to a hex color. Accepts #rgb or #rrggbb and returns rgba().
 */
export function withOpacity(hexColor: string, opacity: number): string {
  const hex = hexColor.replace('#', '')
  const expanded =
    hex.length === 3
      ? hex
          .split('')
          .map((c) => c + c)
          .join('')
      : hex
  if (expanded.length !== 6) return hexColor
  const r = parseInt(expanded.slice(0, 2), 16)
  const g = parseInt(expanded.slice(2, 4), 16)
  const b = parseInt(expanded.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${opacity})`
}

/**
 * Dim a color (for focus mode).
 */
export function dimColor(hexColor: string): string {
  return withOpacity(hexColor, 0.15)
}
