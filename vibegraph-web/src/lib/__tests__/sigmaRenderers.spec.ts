import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Settings } from 'sigma/settings'
import {
  drawEdgeTypeLabel,
  resetEdgeLabelBudget,
  setLabelZoom,
  setShowEdgeType,
  setShowEdgeKind,
} from '../sigmaRenderers'

function rendererContext() {
  const fillText = vi.fn()
  const context = {
    canvas: { clientWidth: 1000, clientHeight: 1000 },
    fillStyle: '',
    font: '',
    textAlign: 'left',
    measureText: vi.fn((text: string) => ({ width: text.length * 100 })),
    fillText,
    save: vi.fn(),
    translate: vi.fn(),
    rotate: vi.fn(),
    restore: vi.fn(),
  } as unknown as CanvasRenderingContext2D

  return { context, fillText }
}

const settings = {
  edgeLabelSize: 8,
  edgeLabelFont: 'sans-serif',
  edgeLabelWeight: '600',
  edgeLabelColor: { color: '#22c55e' },
} as Settings

function drawAtLength(
  context: CanvasRenderingContext2D,
  label: string,
  targetX: number,
  targetAttributes: Record<string, unknown> = {},
): void {
  drawEdgeTypeLabel(
    context,
    { label, color: '#22c55e', size: 1 } as Parameters<typeof drawEdgeTypeLabel>[1],
    { x: 0, y: 50, size: 5 } as Parameters<typeof drawEdgeTypeLabel>[2],
    { x: targetX, y: 50, size: 5, ...targetAttributes } as Parameters<typeof drawEdgeTypeLabel>[3],
    settings,
  )
}

describe('drawEdgeTypeLabel', () => {
  beforeEach(() => {
    setLabelZoom(1)
    setShowEdgeType(true)
    setShowEdgeKind(false)
    resetEdgeLabelBudget(10)
  })

  it('shrinks a label instead of hiding it when the edge is short', () => {
    const { context, fillText } = rendererContext()

    drawAtLength(context, 'SHORT_EDGE', 40)

    expect(fillText).toHaveBeenCalledWith('SHORT_EDGE', expect.any(Number), expect.any(Number))
    expect(context.font).toBe('600 3px sans-serif')
  })

  it('keeps the zoom-driven size when the edge is long enough', () => {
    const { context, fillText } = rendererContext()

    drawAtLength(context, 'LONG_EDGE', 200)

    expect(fillText).toHaveBeenCalledWith('LONG_EDGE', expect.any(Number), expect.any(Number))
    expect(context.font).toBe('600 8px sans-serif')
  })

  it('renders node kind independently when edge type labels are hidden', () => {
    const { context, fillText } = rendererContext()
    setShowEdgeType(false)
    setShowEdgeKind(true)

    drawAtLength(context, 'CALLS', 200, { nodeType: 'Class', color: '#60a5fa' })

    expect(fillText).toHaveBeenCalledWith('Class', expect.any(Number), expect.any(Number))
    expect(fillText).not.toHaveBeenCalledWith('CALLS', expect.any(Number), expect.any(Number))
  })
})
