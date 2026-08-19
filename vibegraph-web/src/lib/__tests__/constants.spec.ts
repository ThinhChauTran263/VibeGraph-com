import { describe, expect, it } from 'vitest'
import { EDGE_COLORS, resolveLocalhostAwareUrl } from '../constants'

describe('resolveLocalhostAwareUrl', () => {
  it('keeps localhost on localhost', () => {
    expect(
      resolveLocalhostAwareUrl('http://localhost:8080', 'http://localhost:8080', 'localhost'),
    ).toBe('http://localhost:8080')
  })

  it('rewrites localhost URLs to 127.0.0.1 when the browser host is 127.0.0.1', () => {
    expect(
      resolveLocalhostAwareUrl('http://localhost:8080/ws/graph-updates', 'fallback', '127.0.0.1'),
    ).toBe('http://127.0.0.1:8080/ws/graph-updates')
  })

  it('leaves non-local URLs untouched', () => {
    expect(resolveLocalhostAwareUrl('https://api.example.com/base', 'fallback', '127.0.0.1')).toBe(
      'https://api.example.com/base',
    )
  })
})

const GRAPH_BACKGROUND = '#0f172a'

function hexToRgb(hex: string): [number, number, number] {
  const value = Number.parseInt(hex.slice(1), 16)
  return [((value >> 16) & 0xff) / 255, ((value >> 8) & 0xff) / 255, (value & 0xff) / 255]
}

function linearize(channel: number): number {
  return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4)
}

function relativeLuminance(hex: string): number {
  const [redChannel, greenChannel, blueChannel] = hexToRgb(hex)
  const red = linearize(redChannel)
  const green = linearize(greenChannel)
  const blue = linearize(blueChannel)
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

function contrastRatio(foreground: string, background: string): number {
  const foregroundLuminance = relativeLuminance(foreground)
  const backgroundLuminance = relativeLuminance(background)
  const lighter = Math.max(foregroundLuminance, backgroundLuminance)
  const darker = Math.min(foregroundLuminance, backgroundLuminance)
  return (lighter + 0.05) / (darker + 0.05)
}

function toOklab(hex: string): [number, number, number] {
  const [redChannel, greenChannel, blueChannel] = hexToRgb(hex)
  const red = linearize(redChannel)
  const green = linearize(greenChannel)
  const blue = linearize(blueChannel)
  const l = 0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue
  const m = 0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue
  const s = 0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue
  const lRoot = Math.cbrt(l)
  const mRoot = Math.cbrt(m)
  const sRoot = Math.cbrt(s)

  return [
    0.2104542553 * lRoot + 0.793617785 * mRoot - 0.0040720468 * sRoot,
    1.9779984951 * lRoot - 2.428592205 * mRoot + 0.4505937099 * sRoot,
    0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.808675766 * sRoot,
  ]
}

function colorDistance(first: string, second: string): number {
  const [firstL, firstA, firstB] = toOklab(first)
  const [secondL, secondA, secondB] = toOklab(second)
  return Math.hypot(firstL - secondL, firstA - secondA, firstB - secondB)
}

describe('EDGE_COLORS', () => {
  it('assigns a unique color to every edge type', () => {
    const colors = Object.values(EDGE_COLORS)
    expect(new Set(colors).size).toBe(colors.length)
  })

  it('keeps every edge color visible on the graph background', () => {
    for (const color of Object.values(EDGE_COLORS)) {
      expect(contrastRatio(color, GRAPH_BACKGROUND)).toBeGreaterThanOrEqual(3)
    }
  })

  it('keeps edge colors perceptually separated', () => {
    const entries = Object.entries(EDGE_COLORS)
    const tooClose: string[] = []

    for (let firstIndex = 0; firstIndex < entries.length; firstIndex += 1) {
      for (let secondIndex = firstIndex + 1; secondIndex < entries.length; secondIndex += 1) {
        const [firstType, firstColor] = entries[firstIndex]!
        const [secondType, secondColor] = entries[secondIndex]!
        if (colorDistance(firstColor, secondColor) < 0.07) {
          tooClose.push(`${firstType}/${secondType}`)
        }
      }
    }

    expect(tooClose).toEqual([])
  })
})
