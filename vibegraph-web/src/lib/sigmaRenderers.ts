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
import {
  SIGMA_LABEL_GROW_ZOOM,
  SIGMA_EDGE_LABEL_GROW_ZOOM,
  SIGMA_MIN_LABEL_ZOOM_SCALE,
  SIGMA_MAX_LABEL_ZOOM_SCALE,
  SIGMA_MIN_EDGE_LABEL_ZOOM_SCALE,
  SIGMA_MAX_EDGE_LABEL_ZOOM_SCALE,
} from './runtimeConfig'

// Current zoom level relative to the fit view (1 = fit, > 1 zoomed in). Updated by
// the camera handler and read live inside the label renderers so label size scales
// with zoom WITHOUT calling sigma.setSetting (which would schedule a full refresh
// and cause a visible "reload" flash on every zoom step).
let labelZoom = 1
export function setLabelZoom(zoom: number): void {
  labelZoom = Number.isFinite(zoom) && zoom > 0 ? zoom : 1
}

// Per-frame budget for how many edge type labels may actually be DRAWN (a rotate +
// fillText each). Off-screen labels are culled for free and never consume budget;
// this only caps the on-screen ones so a zoom level where many edges are visible
// can't stack hundreds of text draws into one frame. Reset at the start of every
// frame from Sigma's `beforeRender` (see useSigma). Drawing order is stable, so the
// same edges win the budget frame-to-frame — no flicker while the camera is still.
let edgeLabelBudget = Number.POSITIVE_INFINITY
export function resetEdgeLabelBudget(cap: number): void {
  edgeLabelBudget = cap
}

// Whether edge labels append the TARGET node's kind (e.g. "IMPORTS Class"), with
// the kind word tinted to that node's legend color. Toggled from the UI.
let showEdgeKind = true
export function setShowEdgeKind(show: boolean): void {
  showEdgeKind = show
}

/**
 * Piecewise label scale vs. zoom (relative to fit = 1), with a per-label-type grow
 * threshold:
 *   - [1 .. growZoom]  → 1 (constant on-screen size while casual zooming)
 *   - > growZoom       → grows linearly, capped
 *   - < 1 (zoomed out) → shrinks toward the floor, then Sigma hides small labels
 *
 * Node and edge labels pass different growZoom values: node labels grow early
 * (readability), while edge labels stay a FIXED size across the normal zoom range
 * and only enlarge once you zoom deep past their (higher) growZoom.
 */
function pieceWiseLabelScale(zoom: number, growZoom: number, floor: number, cap: number): number {
  if (zoom >= growZoom) return Math.min(zoom / growZoom, cap)
  if (zoom >= 1) return 1
  return Math.max(zoom, floor)
}

// Cache of edge-label text width measured at a reference font size, keyed by the
// label string. Edge labels are relationship TYPES ("has-method", "calls", …) — a
// tiny fixed set — so this cache reaches ~100% hit rate immediately. `measureText`
// is one of the most expensive per-glyph canvas calls; running it for every one of
// hundreds of edges on EVERY zoom/pan frame is a dominant source of jank. Text
// width scales linearly with font size for a fixed font, so we measure once at a
// reference size and multiply — turning a per-frame `measureText` storm into a Map
// lookup + one multiply.
const EDGE_LABEL_WIDTH_REF = 100
const edgeLabelWidthPerPx = new Map<string, number>()

function measureEdgeLabelWidth(
  context: CanvasRenderingContext2D,
  label: string,
  weight: string,
  font: string,
  size: number,
): number {
  let perPx = edgeLabelWidthPerPx.get(label)
  if (perPx === undefined) {
    context.font = `${weight} ${EDGE_LABEL_WIDTH_REF}px ${font}`
    perPx = context.measureText(label).width / EDGE_LABEL_WIDTH_REF
    edgeLabelWidthPerPx.set(label, perPx)
  }
  return perPx * size
}

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
  const scale = pieceWiseLabelScale(labelZoom, SIGMA_LABEL_GROW_ZOOM, SIGMA_MIN_LABEL_ZOOM_SCALE, SIGMA_MAX_LABEL_ZOOM_SCALE)
  const size = settings.labelSize * scale + (nodeType === 'File' ? FILE_LABEL_SIZE_BONUS : 0)
  const font = settings.labelFont
  const weight = settings.labelWeight

  context.font = `${weight} ${size}px ${font}`

  // Center the label horizontally and place it BELOW the node disc so the name
  // reads cleanly under each node and never overlaps the node or its edges.
  context.textAlign = 'center'
  context.textBaseline = 'top'
  const tx = data.x
  const ty = data.y + data.size + LABEL_GAP

  // Readable halo WITHOUT canvas shadowBlur. shadowBlur runs a per-glyph gaussian
  // blur that is extremely expensive when hundreds of labels repaint every frame
  // during zoom/pan (the main source of zoom jank). A rounded dark stroke behind
  // the fill gives the same "text on a dark halo" legibility at a fraction of the
  // cost.
  context.lineJoin = 'round'
  context.lineWidth = 3
  context.strokeStyle = 'rgba(0, 0, 0, 0.85)'
  context.strokeText(data.label, tx, ty)
  context.fillStyle = color
  context.fillText(data.label, tx, ty)

  // Reset text alignment so we never leak it onto subsequently drawn elements
  // (Sigma reuses the same canvas context for every item).
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

  const size =
    settings.edgeLabelSize *
    pieceWiseLabelScale(
      labelZoom,
      SIGMA_EDGE_LABEL_GROW_ZOOM,
      SIGMA_MIN_EDGE_LABEL_ZOOM_SCALE,
      SIGMA_MAX_EDGE_LABEL_ZOOM_SCALE,
    )
  const font = settings.edgeLabelFont
  const weight = settings.edgeLabelWeight
  const color = settings.edgeLabelColor.attribute
    ? ((edgeData as Record<string, unknown>)[settings.edgeLabelColor.attribute] as string) ||
      settings.edgeLabelColor.color ||
      '#000'
    : settings.edgeLabelColor.color

  context.fillStyle = color ?? '#000'

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

  // Viewport cull: the label is drawn at the edge midpoint, so if that midpoint is
  // off-screen the label is invisible anyway. Skipping it here means the per-frame
  // edge-label cost scales with the number of edges ON SCREEN (a handful when
  // zoomed in) instead of all forced edges in the graph — which is what lets edge
  // labels render every frame (smooth zoom, no motion "reload", live size scaling)
  // without the jank that forced us to pause them before. Coords are CSS pixels,
  // matching the canvas' logical (client) size.
  const vw = context.canvas.clientWidth
  const vh = context.canvas.clientHeight
  if (vw > 0 && vh > 0) {
    // Half the longest expected label plus a little slack, in px. Edge type names
    // are short (~≤20 chars); ~12× the font size comfortably covers a label whose
    // center sits just past the edge of the viewport.
    const margin = size * 12
    if (cx < -margin || cx > vw + margin || cy < -margin || cy > vh + margin) return
  }

  // Optional target-node kind suffix (e.g. "DEFINES / Method"), tinted to the target
  // node's legend color and placed on the TARGET side of the edge so the coloured
  // word sits next to the node it matches.
  const kindRaw = (targetData as EndpointData & { nodeType?: unknown }).nodeType
  const kind = showEdgeKind && typeof kindRaw === 'string' && kindRaw ? kindRaw : ''
  const sep = kind ? ' / ' : ''
  const kindColor = (targetData.color as string | undefined) ?? '#cbd5e1'

  // The whole label (edge type + separator + kind) must fit within the visible edge
  // span; otherwise hide it. Widths come from the linear-scaled cache.
  const typeWidth = measureEdgeLabelWidth(context, label, weight, font, size)
  const sepWidth = sep ? measureEdgeLabelWidth(context, sep, weight, font, size) : 0
  const kindWidth = kind ? measureEdgeLabelWidth(context, kind, weight, font, size) : 0
  const textLength = typeWidth + sepWidth + kindWidth
  if (textLength > d) return

  // This label will actually be drawn: spend one unit of the per-frame budget. When
  // exhausted (a zoom level with many visible edges), skip the rest this frame.
  if (edgeLabelBudget <= 0) return
  edgeLabelBudget--

  // Set the actual draw font only after the (cheap) fit check passes.
  context.font = `${weight} ${size}px ${font}`

  let angle: number
  if (dx > 0) {
    angle = dy > 0 ? Math.acos(dx / d) : Math.asin(dy / d)
  } else {
    angle = dy > 0 ? Math.acos(dx / d) + Math.PI : Math.asin(dx / d) + Math.PI / 2
  }

  context.save()
  context.translate(cx, cy)
  context.rotate(angle)
  context.textAlign = 'left'
  const baseline = (edgeData.size ?? 1) / 2 + size
  const startX = -textLength / 2

  if (!kind) {
    context.fillStyle = color ?? '#000'
    context.fillText(label, startX, baseline)
  } else if (Math.cos(angle) * dx + Math.sin(angle) * dy >= 0) {
    // Text's +x (right) points toward the target node → kind word on the right.
    context.fillStyle = color ?? '#000'
    context.fillText(label, startX, baseline)
    context.fillText(sep, startX + typeWidth, baseline)
    context.fillStyle = kindColor
    context.fillText(kind, startX + typeWidth + sepWidth, baseline)
  } else {
    // +x points toward the source → put the kind word on the LEFT so it still sits
    // next to the target node.
    context.fillStyle = kindColor
    context.fillText(kind, startX, baseline)
    context.fillStyle = color ?? '#000'
    context.fillText(sep, startX + kindWidth, baseline)
    context.fillText(label, startX + kindWidth + sepWidth, baseline)
  }
  context.restore()
}
