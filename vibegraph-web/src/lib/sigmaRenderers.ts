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
import type { NodeDisplayData, PartialButFor } from 'sigma/types'
import { HIGHLIGHT_LABEL_COLOR } from './constants'

type HoverData = PartialButFor<NodeDisplayData, 'x' | 'y' | 'size' | 'label' | 'color'>

const LABEL_GAP = 3
const FILE_LABEL_SIZE_BONUS = 4
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

  context.fillStyle = color
  context.fillText(data.label, data.x + data.size + LABEL_GAP, data.y + size / 3)

  // Reset shadow so we never leak it onto subsequently drawn elements.
  context.shadowBlur = 0
  context.shadowColor = 'transparent'
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
