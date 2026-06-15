/**
 * Color mixing helpers for graph focus dimming.
 *
 * Sigma's WebGL `line` edge program does not reliably honor low-alpha rgba
 * colors (hundreds of overlapping near-transparent edges either fail to apply
 * the alpha or accumulate into a bright "spaghetti" web). So instead of relying
 * on alpha we pre-compute OPAQUE hex colors by mixing each element's original
 * color toward the dark canvas background. This keeps the unrelated graph
 * visible as a faint, hue-preserving ghost layer — darkened and desaturated,
 * never white and never pure black.
 */

// The graph canvas background (see GraphCanvas.vue `.graph-canvas`). Dimmed
// colors are mixed toward this so they recede into the background instead of
// going fully black.
export const GRAPH_BACKGROUND_COLOR = '#0f172a'

// When an element's color can't be parsed we still want a faint COLORED ghost
// (not pure background), so fall back to a neutral slate before mixing.
export const DIM_FALLBACK_SOURCE = '#64748b' // slate-500

interface Rgb {
  r: number
  g: number
  b: number
}

function clampChannel(value: number): number {
  return Math.max(0, Math.min(255, Math.round(value)))
}

function channelToHex(value: number): string {
  return clampChannel(value).toString(16).padStart(2, '0')
}

/**
 * Parse a `#rgb` or `#rrggbb` hex string into RGB channels. Returns null for
 * anything we can't confidently parse (named colors, rgba(), malformed input).
 */
export function parseHexColor(color: string): Rgb | null {
  const hex = color.trim().toLowerCase()

  const long = /^#([0-9a-f]{6})$/.exec(hex)
  if (long) {
    const channels = long[1]
    if (!channels) return null
    return {
      r: parseInt(channels.slice(0, 2), 16),
      g: parseInt(channels.slice(2, 4), 16),
      b: parseInt(channels.slice(4, 6), 16),
    }
  }

  const short = /^#([0-9a-f]{3})$/.exec(hex)
  if (short) {
    const channels = short[1]
    if (!channels) return null
    const r = channels[0]
    const g = channels[1]
    const b = channels[2]
    if (!r || !g || !b) return null
    return {
      r: parseInt(`${r}${r}`, 16),
      g: parseInt(`${g}${g}`, 16),
      b: parseInt(`${b}${b}`, 16),
    }
  }

  return null
}

function rgbToHex({ r, g, b }: Rgb): string {
  return `#${channelToHex(r)}${channelToHex(g)}${channelToHex(b)}`
}

/**
 * Return `original` if it is a parseable hex color, otherwise `fallback`.
 * Guards the dimming pipeline against undefined/named/rgba inputs so a dimmed
 * element can never accidentally keep a bright or unknown color.
 */
export function safeColor(original: unknown, fallback: string): string {
  if (typeof original === 'string' && parseHexColor(original)) return original
  return fallback
}

/**
 * Linearly blend `original` toward `background`. `weight` is the proportion of
 * background (0 = original color, 1 = pure background). Both colors must be hex;
 * if either can't be parsed the original string is returned unchanged.
 */
export function mixColor(original: string, background: string, weight: number): string {
  const w = Math.max(0, Math.min(1, weight))
  const from = parseHexColor(original)
  const to = parseHexColor(background)
  if (!from || !to) return original

  return rgbToHex({
    r: from.r * (1 - w) + to.r * w,
    g: from.g * (1 - w) + to.g * w,
    b: from.b * (1 - w) + to.b * w,
  })
}

/**
 * Darken a color toward the graph background by `amount` (0 = unchanged,
 * 1 = fully background). Preserves the original hue at reduced intensity, so a
 * dimmed amber node stays a faint amber rather than turning black. Unparseable
 * inputs fall back to a neutral slate ghost (never white, never pure black).
 */
export function dimColor(original: unknown, amount: number): string {
  const source = safeColor(original, DIM_FALLBACK_SOURCE)
  return mixColor(source, GRAPH_BACKGROUND_COLOR, amount)
}
