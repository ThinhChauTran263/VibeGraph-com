/**
 * Custom Sigma node label / hover renderers.
 *
 * Sigma's stock `drawDiscNodeHover` paints a solid white rounded-rectangle
 * behind the label (fillStyle = "#FFF"). On our dark canvas that white box is
 * visually dominant and hides the surrounding graph. These renderers instead:
 *   - draw a thin white ring/outline around the hovered node (kept on purpose)
 *   - draw the label as text only, in HIGHLIGHT_LABEL_COLOR, over a soft dark
 *     halo (shadow) so it stays readable WITHOUT an opaque white rectangle.
 */

import type { Settings } from 'sigma/settings'
import type { EdgeDisplayData, NodeDisplayData, PartialButFor } from 'sigma/types'
import { HIGHLIGHT_LABEL_COLOR } from './constants'

type HoverData = PartialButFor<NodeDisplayData, 'x' | 'y' | 'size' | 'label' | 'color'>
type EdgeData = PartialButFor<EdgeDisplayData, 'label' | 'color' | 'size'>
type EndpointData = PartialButFor<NodeDisplayData, 'x' | 'y' | 'size'>

// Gap between the bottom of the node disc and the top of the label text.
const LABEL_GAP = 4
const FILE_LABEL_SIZE_BONUS = 2
// Soft halo ring around the focused node: a thin, semi-transparent white stroke
// with a gentle outward glow instead of a hard 2px opaque outline. Reads as a
// soft spotlight rather than a technical selection marker.
const RING_WIDTH = 1.25
const RING_COLOR = 'rgba(255, 255, 255, 0.55)'
const RING_GLOW_COLOR = 'rgba(255, 255, 255, 0.35)'
const RING_GLOW_BLUR = 8
const RING_GAP = 3

/**
 * Draw a node label as text only (no background box). Used both as the default
 * label renderer and inside the hover renderer. A dark text-shadow halo keeps it
 * legible over bright or busy regions of the graph.
 */
export function drawTextOnlyNodeLabel(
  context: CanvasRenderingContext2D,
  data: HoverData,
  settings: Settings,
  color: string,
): void {
  if (!data.label) return

  const nodeType = (data as HoverData & { nodeType?: unknown }).nodeType
  const size = settings.labelSize + (nodeType === 'File' ? FILE_LABEL_SIZE_BONUS : 0)
  const font = settings.labelFont
  const weight = settings.labelWeight

  context.font = `${weight} ${size}px ${font}`

  // Dark halo so light text stays readable on light/cluttered backgrounds.
  context.shadowOffsetX = 0
  context.shadowOffsetY = 0
  context.shadowBlur = 4
  context.shadowColor = 'rgba(0, 0, 0, 0.85)'

  // Center the label horizontally and place it BELOW the node disc (instead of
  // to the right), so the name reads cleanly under each node and never overlaps
  // the node or its edges.
  context.textAlign = 'center'
  context.textBaseline = 'top'
  context.fillStyle = color
  context.fillText(data.label, data.x, data.y + data.size + LABEL_GAP)

  // Reset shadow + text alignment so we never leak them onto subsequently drawn
  // elements (Sigma reuses the same canvas context for every item).
  context.shadowBlur = 0
  context.shadowColor = 'transparent'
  context.textAlign = 'left'
  context.textBaseline = 'alphabetic'
}

/**
 * Default (non-hover) label renderer: text only, in the configured label color.
 */
export function drawDefaultNodeLabel(
  context: CanvasRenderingContext2D,
  data: HoverData,
  settings: Settings,
): void {
  const color = settings.labelColor.attribute
    ? (data[settings.labelColor.attribute as keyof HoverData] as string) ||
      settings.labelColor.color ||
      '#000'
    : settings.labelColor.color
  drawTextOnlyNodeLabel(context, data, settings, color ?? '#000')
}

/**
 * Hover renderer: draw a soft, semi-transparent halo ring around the node and
 * the label text in the highlight color. No white label box, no hard outline.
 */
export function drawHighlightNodeHover(
  context: CanvasRenderingContext2D,
  data: HoverData,
  settings: Settings,
): void {
  // Soft halo ring around the focused node (semi-transparent + outward glow).
  context.save()
  context.beginPath()
  context.arc(data.x, data.y, data.size + RING_GAP, 0, Math.PI * 2)
  context.closePath()
  context.lineWidth = RING_WIDTH
  context.strokeStyle = RING_COLOR
  context.shadowBlur = RING_GLOW_BLUR
  context.shadowColor = RING_GLOW_COLOR
  context.stroke()
  context.restore()

  drawTextOnlyNodeLabel(context, data, settings, HIGHLIGHT_LABEL_COLOR)
}

/**
 * Edge type label renderer. Behaves like Sigma's built-in straight-edge label
 * drawer EXCEPT it never truncates: if the full label text does not fit along
 * the visible edge it is HIDDEN entirely (no "DEFI…" ellipsis). This keeps the
 * canvas clean — a relationship label is shown only when it can be read in full.
 */
export function drawEdgeTypeLabel(
  context: CanvasRenderingContext2D,
  edgeData: EdgeData,
  sourceData: EndpointData,
  targetData: EndpointData,
  settings: Settings,
): void {
  const label = edgeData.label
  if (!label) return

  const size = settings.edgeLabelSize
  const font = settings.edgeLabelFont
  const weight = settings.edgeLabelWeight
  const color = settings.edgeLabelColor.attribute
    ? ((edgeData as Record<string, unknown>)[settings.edgeLabelColor.attribute] as string) ||
      settings.edgeLabelColor.color ||
      '#000'
    : settings.edgeLabelColor.color

  context.fillStyle = color ?? '#000'
  context.font = `${weight} ${size}px ${font}`

  // Positions, offset by node radii so the measured length is the visible span.
  const sSize = sourceData.size
  const tSize = targetData.size
  let sx = sourceData.x
  let sy = sourceData.y
  let tx = targetData.x
  let ty = targetData.y
  let dx = tx - sx
  let dy = ty - sy
  let d = Math.sqrt(dx * dx + dy * dy)
  if (d < sSize + tSize) return

  sx += (dx * sSize) / d
  sy += (dy * sSize) / d
  tx -= (dx * tSize) / d
  ty -= (dy * tSize) / d
  const cx = (sx + tx) / 2
  const cy = (sy + ty) / 2
  dx = tx - sx
  dy = ty - sy
  d = Math.sqrt(dx * dx + dy * dy)

  // The whole label must fit within the visible edge span; otherwise hide it.
  const textLength = context.measureText(label).width
  if (textLength > d) return

  let angle: number
  if (dx > 0) {
    angle = dy > 0 ? Math.acos(dx / d) : Math.asin(dy / d)
  } else {
    angle = dy > 0 ? Math.acos(dx / d) + Math.PI : Math.asin(dx / d) + Math.PI / 2
  }

  context.save()
  context.translate(cx, cy)
  context.rotate(angle)
  context.fillText(label, -textLength / 2, (edgeData.size ?? 1) / 2 + size)
  context.restore()
}
