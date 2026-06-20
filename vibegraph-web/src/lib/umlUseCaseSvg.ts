import type { UmlActor, UmlRelation, UmlUseCaseElement } from '@/lib/api'

/**
 * Minimal model the renderer needs. {@code UmlUseCaseResponse & { kind: 'uml' }} is structurally
 * assignable to this, so callers pass the loaded diagram straight through.
 */
export interface UmlUseCaseModel {
  systemName: string
  actors: UmlActor[]
  useCases: UmlUseCaseElement[]
  relations: UmlRelation[]
}

/**
 * Renders a standards-correct OMG UML 2.5.1 Use Case diagram as a standalone SVG string.
 *
 * <p>Unlike a Mermaid flowchart, this draws true UML notation: stick-figure actors outside a
 * named system boundary, oval use cases inside it, solid associations, dashed
 * {@code «include»}/{@code «extend»} dependencies with open arrowheads, and hollow-triangle actor
 * generalizations. Output is deterministic (no Date/random, stable sorts) so it is snapshot-safe,
 * and it carries explicit width/height/viewBox so it renders correctly at scale 1 (export mode).
 */
export function renderUmlUseCaseSvg(model: UmlUseCaseModel): string {
  const actors = model.actors ?? []
  const useCases = model.useCases ?? []
  const relations = model.relations ?? []

  // --- actor split: external systems on the right, humans on the left (input order kept) -------
  const left = actors.filter((a) => !isExternalSystem(a.name))
  const right = actors.filter((a) => isExternalSystem(a.name))
  const actorOrder = [...left, ...right]
  const actorIdx = new Map(actorOrder.map((a, i) => [a.id, i]))

  // --- use case ordering: cluster by primary associated actor to reduce crossings --------------
  const primaryOf = (ucId: string): number => {
    let best = Number.POSITIVE_INFINITY
    for (const r of relations) {
      if (r.type === REL_ASSOCIATION && r.to === ucId) {
        const idx = actorIdx.get(r.from)
        if (idx !== undefined && idx < best) best = idx
      }
    }
    return best
  }
  const actorSorted = useCases
    .map((uc, i) => ({ uc, i, p: primaryOf(uc.id) }))
    .sort((a, b) => (a.p !== b.p ? a.p - b.p : a.i - b.i))
    .map((e) => e.uc)

  // Pull each include/extend target to sit immediately after its source. A dependency target
  // (e.g. "Validate Tracking Number") usually has no actor association, so the actor-sort alone
  // banishes it to the bottom — far from its source, producing a long dashed line that crosses
  // other ellipses. Emitting it right after its source makes them adjacent grid cells (same row,
  // neighbouring column), so the dependency renders as a short near-horizontal line.
  const depTargets = new Map<string, string[]>()
  for (const r of relations) {
    if (r.type === REL_INCLUDE || r.type === REL_EXTEND) {
      const list = depTargets.get(r.from) ?? []
      list.push(r.to)
      depTargets.set(r.from, list)
    }
  }
  const byId = new Map(actorSorted.map((uc) => [uc.id, uc]))
  const emitted = new Set<string>()
  const ordered: UmlUseCaseElement[] = []
  const emit = (uc: UmlUseCaseElement): void => {
    if (emitted.has(uc.id)) return
    emitted.add(uc.id)
    ordered.push(uc)
    for (const targetId of depTargets.get(uc.id) ?? []) {
      const target = byId.get(targetId)
      if (target) emit(target)
    }
  }
  for (const uc of actorSorted) emit(uc)

  // --- row-major packing ----------------------------------------------------------------------
  // Fill ROW by ROW (not column by column) so the actor-sorted order flows top-to-bottom across
  // the whole grid. Each actor's goals then sit at the vertical band next to that actor (Guest
  // top, User middle, Admin lower), keeping association lines roughly horizontal instead of
  // cutting long diagonals across the boundary.
  const count = ordered.length
  const cols = count <= 7 ? 1 : count <= 14 ? 2 : 3
  const rows = Math.max(1, Math.ceil(count / cols))

  // index k -> row = floor(k/cols), col = k % cols
  const colOf = (k: number) => k % cols
  const rowOf = (k: number) => Math.floor(k / cols)

  const colW: number[] = new Array(cols).fill(UC_MIN_W)
  for (let k = 0; k < count; k++) {
    const c = colOf(k)
    const longest = wrapLabel(ordered[k]!.name, UC_MAX_CHARS, 2).reduce((m, ln) => Math.max(m, ln.length), 0)
    colW[c] = Math.max(colW[c]!, ellipseWidth('x'.repeat(longest)))
  }

  // --- geometry -------------------------------------------------------------------------------
  const bx0 = PAD + ACTOR_W + ACTOR_BOUNDARY_GAP
  const by0 = PAD
  const innerH = rows * UC_H + (rows - 1) * UC_GAP_Y
  const boundaryH = BOUNDARY_TITLE_H + BOUNDARY_PAD * 2 + innerH
  let innerW = 0
  for (let c = 0; c < cols; c++) innerW += colW[c]!
  innerW += (cols - 1) * UC_COL_GAP
  const boundaryW = Math.max(MIN_BOUNDARY_W, BOUNDARY_PAD * 2 + innerW)
  const boundaryRight = bx0 + boundaryW
  const boundaryBottom = by0 + boundaryH

  // column left x (cumulative)
  const colX: number[] = []
  {
    let x = bx0 + BOUNDARY_PAD
    for (let c = 0; c < cols; c++) {
      colX.push(x)
      x += colW[c]! + UC_COL_GAP
    }
  }

  // --- node boxes (id -> center+size) for edge endpoints --------------------------------------
  const boxes = new Map<string, Box>()
  const ucParts: string[] = []
  for (let k = 0; k < count; k++) {
    const uc = ordered[k]!
    const c = colOf(k)
    const r = rowOf(k)
    const w = colW[c]!
    const cx = colX[c]! + w / 2
    const cy = by0 + BOUNDARY_TITLE_H + BOUNDARY_PAD + UC_H / 2 + r * (UC_H + UC_GAP_Y)
    boxes.set(uc.id, { cx, cy, w, h: UC_H, ellipse: true })
    ucParts.push(useCaseSvg(cx, cy, w, uc.name))
  }

  // actors: align each actor with the vertical centroid of the use cases it connects to, so
  // association lines stay roughly horizontal instead of fanning out diagonally. Generalization
  // targets (parent actors) also count, keeping the Guest<-User<-Admin chain vertical. Actors are
  // then de-overlapped within their column (min ACTOR_GAP_Y apart) while preserving that order.
  const actorParts: string[] = []
  const centroidY = (actorId: string): number => {
    const ys: number[] = []
    for (const r of relations) {
      if (r.from === actorId) {
        const target = boxes.get(r.to)
        if (target) ys.push(target.cy)
      }
    }
    if (ys.length === 0) return by0 + boundaryH / 2
    return ys.reduce((s, v) => s + v, 0) / ys.length
  }
  const placeColumn = (group: UmlActor[], x: number) => {
    // desired y from centroid, sorted, then pushed apart to avoid overlap
    const desired = group
      .map((a) => ({ a, y: centroidY(a.id) }))
      .sort((p, q) => p.y - q.y)
    for (let i = 1; i < desired.length; i++) {
      const minY = desired[i - 1]!.y + ACTOR_GAP_Y
      if (desired[i]!.y < minY) desired[i]!.y = minY
    }
    for (const { a, y } of desired) {
      const top = y - ACTOR_BODY_MID
      boxes.set(a.id, { cx: x, cy: y, w: ACTOR_W, h: ACTOR_H, ellipse: false })
      actorParts.push(actorSvg(x, top, a.name))
    }
  }
  placeColumn(left, PAD + ACTOR_W / 2)
  const rightX = boundaryRight + ACTOR_BOUNDARY_GAP + ACTOR_W / 2
  placeColumn(right, rightX)

  // --- edges (painted before nodes so nodes cover the line ends) ------------------------------
  const edgeParts: string[] = []
  for (const r of relations) {
    const part = edgeSvg(r, boxes)
    if (part) edgeParts.push(part)
  }

  // --- bounds ---------------------------------------------------------------------------------
  const rightmost = right.length > 0 ? rightX + ACTOR_W / 2 : boundaryRight
  const totalW = rightmost + PAD
  // Actors may now extend below the boundary (centroid placement + de-overlap), so derive the
  // lowest extent from the actual placed actor boxes rather than a fixed formula.
  let lowestActor = boundaryBottom
  for (const a of [...left, ...right]) {
    const box = boxes.get(a.id)
    if (box) lowestActor = Math.max(lowestActor, box.cy - ACTOR_BODY_MID + ACTOR_H)
  }
  const totalH = Math.max(boundaryBottom, lowestActor) + PAD

  const boundary =
    `<rect x="${bx0}" y="${by0}" width="${boundaryW}" height="${boundaryH}" rx="6" ` +
    `fill="none" stroke="#555" stroke-width="1.5"/>` +
    `<text x="${bx0 + 14}" y="${by0 + 24}" font-size="15" font-weight="600" fill="#333">` +
    `${esc(model.systemName || 'System')}</text>`

  return (
    `<svg xmlns="http://www.w3.org/2000/svg" width="${Math.ceil(totalW)}" height="${Math.ceil(totalH)}" ` +
    `viewBox="0 0 ${Math.ceil(totalW)} ${Math.ceil(totalH)}" role="img" data-test="uml-usecase-svg" ` +
    `font-family="Segoe UI, Arial, sans-serif">` +
    DEFS +
    boundary +
    edgeParts.join('') +
    ucParts.join('') +
    actorParts.join('') +
    `</svg>`
  )
}

// --- constants (scale-1 px) -------------------------------------------------------------------
const PAD = 40
const ACTOR_W = 96
const ACTOR_H = 96
const ACTOR_GAP_Y = 132
const ACTOR_BODY_MID = 40 // approx vertical center of the figure from its top anchor
const ACTOR_BOUNDARY_GAP = 110
const UC_MIN_W = 130
const UC_H = 56
const UC_PAD_X = 26
const UC_GAP_Y = 26
const UC_COL_GAP = 56
const BOUNDARY_PAD = 44
const BOUNDARY_TITLE_H = 34
const MIN_BOUNDARY_W = 220
const CHAR_W = 7.3
const UC_MAX_CHARS = 28
const UC_LINE_H = 16
const ACTOR_MAX_CHARS = 18
const ACTOR_LINE_H = 15

const REL_ASSOCIATION = 'association'
const REL_INCLUDE = 'include'
const REL_EXTEND = 'extend'
const REL_GENERALIZATION = 'generalization'

const DEFS =
  `<defs>` +
  `<marker id="uml-open-arrow" markerWidth="14" markerHeight="12" refX="11" refY="5" ` +
  `orient="auto-start-reverse" markerUnits="userSpaceOnUse">` +
  `<path d="M1,1 L11,5 L1,9" fill="none" stroke="black" stroke-width="1.2"/></marker>` +
  `<marker id="uml-triangle" markerWidth="18" markerHeight="16" refX="15" refY="7" ` +
  `orient="auto-start-reverse" markerUnits="userSpaceOnUse">` +
  `<path d="M1,1 L15,7 L1,13 Z" fill="white" stroke="black" stroke-width="1.2"/></marker>` +
  `</defs>`

interface Box {
  cx: number
  cy: number
  w: number
  h: number
  ellipse: boolean
}

function isExternalSystem(name: string): boolean {
  return /\b(system|service|gateway|api|server|provider|platform)\b/i.test(name ?? '')
}

function ellipseWidth(label: string): number {
  return Math.max(UC_MIN_W, Math.ceil(label.length * CHAR_W) + UC_PAD_X * 2)
}

function actorSvg(x: number, yTop: number, name: string): string {
  const headR = 11
  const cyHead = yTop + headR
  const bodyTop = cyHead + headR
  const bodyBot = bodyTop + 26
  const armY = bodyTop + 8
  const legY = bodyBot + 22
  const labelY = legY + 18
  const stroke = `stroke="black" stroke-width="1.5"`
  // Wrap long names onto up to two lines so external systems like "Carrier Tracking System"
  // are not cut off. Keeps the full name in <title> for hover.
  const lines = wrapLabel(name, ACTOR_MAX_CHARS, 2)
  const labelSvg = lines
    .map(
      (ln, i) =>
        `<text x="${x}" y="${labelY + i * ACTOR_LINE_H}" text-anchor="middle" font-size="13" ` +
        `fill="black">${esc(ln)}</text>`,
    )
    .join('')
  return (
    `<g><title>${esc(name)}</title>` +
    `<circle cx="${x}" cy="${cyHead}" r="${headR}" fill="white" ${stroke}/>` +
    `<line x1="${x}" y1="${bodyTop}" x2="${x}" y2="${bodyBot}" ${stroke}/>` +
    `<line x1="${x - 16}" y1="${armY}" x2="${x + 16}" y2="${armY}" ${stroke}/>` +
    `<line x1="${x}" y1="${bodyBot}" x2="${x - 13}" y2="${legY}" ${stroke}/>` +
    `<line x1="${x}" y1="${bodyBot}" x2="${x + 13}" y2="${legY}" ${stroke}/>` +
    labelSvg +
    `</g>`
  )
}

function useCaseSvg(cx: number, cy: number, w: number, full: string): string {
  const rx = w / 2
  const ry = UC_H / 2
  // Wrap long names onto up to two lines so nothing is cut off (e.g. "Receive Shipment Status
  // Update"). The full name is also kept in <title> for hover.
  const lines = wrapLabel(full, UC_MAX_CHARS, 2)
  const startY = lines.length === 1 ? cy + 4 : cy - 4
  const labelSvg = lines
    .map(
      (ln, i) =>
        `<text x="${cx}" y="${startY + i * UC_LINE_H}" text-anchor="middle" font-size="14" ` +
        `fill="black">${esc(ln)}</text>`,
    )
    .join('')
  return (
    `<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="white" stroke="black" stroke-width="1.5">` +
    `<title>${esc(full)}</title></ellipse>` +
    labelSvg
  )
}

function edgeSvg(rel: UmlRelation, boxes: Map<string, Box>): string {
  const a = boxes.get(rel.from)
  const b = boxes.get(rel.to)
  if (!a || !b) return '' // dangling relation -> skip defensively
  const [x1, y1] = exitPoint(a, b)
  const [x2, y2] = exitPoint(b, a)
  const mx = (x1 + x2) / 2
  const my = (y1 + y2) / 2
  const base = `x1="${round(x1)}" y1="${round(y1)}" x2="${round(x2)}" y2="${round(y2)}" stroke="black" stroke-width="1.3"`
  switch (rel.type) {
    case REL_INCLUDE:
    case REL_EXTEND: {
      const stereo = rel.type === REL_INCLUDE ? '«include»' : '«extend»'
      // When the edge is near-vertical the midpoint sits on top of an ellipse, so push the label
      // sideways (and use start-anchored text) to keep it off the node. Otherwise center it above.
      const nearVertical = Math.abs(x2 - x1) < 40
      const lx = nearVertical ? mx + 8 : mx
      const ly = nearVertical ? my : my - 4
      const anchor = nearVertical ? 'start' : 'middle'
      return (
        `<line ${base} stroke-dasharray="6 4" marker-end="url(#uml-open-arrow)"/>` +
        `<text x="${round(lx)}" y="${round(ly)}" text-anchor="${anchor}" font-size="11" ` +
        `font-style="italic" fill="#333">${stereo}</text>`
      )
    }
    case REL_GENERALIZATION:
      return `<line ${base} marker-end="url(#uml-triangle)"/>`
    case REL_ASSOCIATION:
    default:
      return `<line ${base}/>`
  }
}

/** Point on the border of {@code from} along the line toward {@code to}'s center. */
function exitPoint(from: Box, to: Box): [number, number] {
  const dx = to.cx - from.cx
  const dy = to.cy - from.cy
  if (dx === 0 && dy === 0) return [from.cx, from.cy]
  if (from.ellipse) {
    const rx = from.w / 2
    const ry = from.h / 2
    const t = 1 / Math.sqrt((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry))
    return [from.cx + dx * t, from.cy + dy * t]
  }
  // rectangle border clamp
  const hw = from.w / 2
  const hh = from.h / 2
  const scale = Math.min(hw / Math.abs(dx || 1e-6), hh / Math.abs(dy || 1e-6))
  return [from.cx + dx * scale, from.cy + dy * scale]
}

function round(n: number): number {
  return Math.round(n * 100) / 100
}

function truncate(s: string, max: number): string {
  const v = s ?? ''
  return v.length > max ? v.slice(0, max - 1) + '…' : v
}

/**
 * Wrap a label into at most {@code maxLines} lines of roughly {@code perLine} chars, breaking on
 * spaces. The final line is truncated with an ellipsis if the text still overflows.
 */
function wrapLabel(s: string, perLine: number, maxLines: number): string[] {
  const text = (s ?? '').trim()
  if (text.length <= perLine) return [text]
  const words = text.split(/\s+/)
  const lines: string[] = []
  let cur = ''
  for (const w of words) {
    const candidate = cur ? `${cur} ${w}` : w
    if (candidate.length > perLine && cur) {
      lines.push(cur)
      cur = w
      if (lines.length === maxLines - 1) break
    } else {
      cur = candidate
    }
  }
  const consumed = lines.join(' ').split(/\s+/).filter(Boolean).length
  const rest = words.slice(consumed).join(' ') || cur
  lines.push(truncate(rest, perLine))
  return lines.slice(0, maxLines)
}

function esc(s: string): string {
  return (s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
