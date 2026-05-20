/**
 * Color utility helpers.
 */
import { NODE_COLORS, EDGE_COLORS } from './constants'

export function getNodeColor(nodeType: string): string {
  return NODE_COLORS[nodeType] || '#888888'
}

export function getEdgeColor(edgeType: string): string {
  return EDGE_COLORS[edgeType] || '#666666'
}

/**
 * Apply opacity to a hex color.
 */
export function withOpacity(hexColor: string, opacity: number): string {
  // TODO: Convert hex to rgba with opacity
  return hexColor
}

/**
 * Dim a color (for focus mode).
 */
export function dimColor(hexColor: string): string {
  return withOpacity(hexColor, 0.15)
}
