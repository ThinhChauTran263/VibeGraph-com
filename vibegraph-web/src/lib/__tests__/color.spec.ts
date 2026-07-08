import { describe, expect, it } from 'vitest'
import {
  DIM_FALLBACK_SOURCE,
  GRAPH_BACKGROUND_COLOR,
  dimColor,
  mixColor,
  parseHexColor,
  safeColor,
} from '../color'

function channels(color: string): { r: number; g: number; b: number } {
  const match = /^#([0-9a-f]{6})$/.exec(color.trim().toLowerCase())
  if (!match || !match[1]) throw new Error(`not a 6-digit hex: ${color}`)
  const hex = match[1]
  return {
    r: parseInt(hex.slice(0, 2), 16),
    g: parseInt(hex.slice(2, 4), 16),
    b: parseInt(hex.slice(4, 6), 16),
  }
}

function isBright(color: string): boolean {
  const { r, g, b } = channels(color)
  return r > 150 && g > 150 && b > 150
}

function isNearBlack(color: string): boolean {
  const { r, g, b } = channels(color)
  return r <= 18 && g <= 18 && b <= 18
}

function preservesHue(color: string): boolean {
  const { r, g, b } = channels(color)
  return Math.max(r, g, b) - Math.min(r, g, b) > 6
}

describe('parseHexColor', () => {
  it('parses #rrggbb', () => {
    expect(parseHexColor('#10B981')).toEqual({ r: 0x10, g: 0xb9, b: 0x81 })
  })

  it('parses #rgb shorthand', () => {
    expect(parseHexColor('#abc')).toEqual({ r: 0xaa, g: 0xbb, b: 0xcc })
  })

  it('is case- and whitespace-insensitive', () => {
    expect(parseHexColor('  #FFFFFF  ')).toEqual({ r: 255, g: 255, b: 255 })
  })

  it('returns null for non-hex inputs', () => {
    expect(parseHexColor('rgba(255,0,0,0.5)')).toBeNull()
    expect(parseHexColor('red')).toBeNull()
    expect(parseHexColor('#12')).toBeNull()
    expect(parseHexColor('#1234')).toBeNull()
    expect(parseHexColor('')).toBeNull()
  })
})

describe('safeColor', () => {
  it('keeps a parseable hex color', () => {
    expect(safeColor('#F59E0B', DIM_FALLBACK_SOURCE)).toBe('#F59E0B')
  })

  it('falls back when the input is not a parseable hex', () => {
    expect(safeColor(undefined, DIM_FALLBACK_SOURCE)).toBe(DIM_FALLBACK_SOURCE)
    expect(safeColor('rgba(0,0,0,0.2)', DIM_FALLBACK_SOURCE)).toBe(DIM_FALLBACK_SOURCE)
    expect(safeColor(42, DIM_FALLBACK_SOURCE)).toBe(DIM_FALLBACK_SOURCE)
  })
})

describe('mixColor', () => {
  it('returns the original color at weight 0', () => {
    expect(mixColor('#10B981', GRAPH_BACKGROUND_COLOR, 0)).toBe('#10b981')
  })

  it('returns the background at weight 1', () => {
    expect(mixColor('#10B981', GRAPH_BACKGROUND_COLOR, 1)).toBe(GRAPH_BACKGROUND_COLOR)
  })

  it('blends linearly halfway between the two colors', () => {
    // #000000 mixed 50% toward #ffffff is mid-grey.
    expect(mixColor('#000000', '#ffffff', 0.5)).toBe('#808080')
  })

  it('clamps out-of-range weights into [0, 1]', () => {
    expect(mixColor('#000000', '#ffffff', -1)).toBe('#000000')
    expect(mixColor('#000000', '#ffffff', 5)).toBe('#ffffff')
  })

  it('returns the original string unchanged when either color is unparseable', () => {
    expect(mixColor('red', GRAPH_BACKGROUND_COLOR, 0.5)).toBe('red')
  })
})

describe('dimColor', () => {
  it('darkens a bright color without keeping it bright', () => {
    const dimmed = dimColor('#F59E0B', 0.78) // amber
    expect(dimmed).toBe('#423523')
    expect(isBright(dimmed)).toBe(false)
  })

  it('never collapses a dimmed color to pure/near black', () => {
    for (const source of ['#F59E0B', '#3B82F6', '#EF4444', '#10B981', '#22C55E', '#93c5fd']) {
      const dimmed = dimColor(source, 0.86)
      expect(isNearBlack(dimmed)).toBe(false)
    }
  })

  it('preserves some of the original hue (the dimmed ghost stays colored)', () => {
    for (const source of ['#F59E0B', '#3B82F6', '#EF4444', '#10B981', '#22C55E', '#93c5fd']) {
      const dimmed = dimColor(source, 0.86)
      expect(preservesHue(dimmed)).toBe(true)
    }
  })

  it('falls back to a colored slate ghost for unparseable inputs (never white, never black)', () => {
    const dimmed = dimColor(undefined, 0.78)
    expect(isBright(dimmed)).toBe(false)
    expect(isNearBlack(dimmed)).toBe(false)
    expect(preservesHue(dimmed)).toBe(true)
  })
})
