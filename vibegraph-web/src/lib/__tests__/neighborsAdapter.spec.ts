import { describe, it, expect } from 'vitest'

import { neighborsToFragment, EXPAND_MAX_NEIGHBORS } from '../neighborsAdapter'
import type { NodeDetailResponse } from '../api'

function nodeDto(id: string, type = 'Class'): NodeDetailResponse['node'] {
  return {
    id,
    type,
    name: id,
    fullName: `com.example.${id}`,
    filePath: `${id}.java`,
    lineNumber: 3,
  }
}

function detail(overrides: Partial<NodeDetailResponse> = {}): NodeDetailResponse {
  return {
    node: nodeDto('Center'),
    incoming: [],
    outgoing: [],
    ...overrides,
  }
}

describe('neighborsToFragment', () => {
  it('includes the center node plus each connected node', () => {
    const fragment = neighborsToFragment(
      detail({
        incoming: [
          { otherNode: nodeDto('Caller'), relationshipType: 'CALLS', direction: 'INCOMING' },
        ],
        outgoing: [
          { otherNode: nodeDto('Dep'), relationshipType: 'INJECTS', direction: 'OUTGOING' },
        ],
      }),
    )

    expect(fragment.nodes.map((n) => n.id)).toEqual(['Center', 'Caller', 'Dep'])
  })

  it('orients an INCOMING edge from the other node into the center', () => {
    const fragment = neighborsToFragment(
      detail({
        incoming: [
          { otherNode: nodeDto('Caller'), relationshipType: 'CALLS', direction: 'INCOMING' },
        ],
      }),
    )

    expect(fragment.edges).toHaveLength(1)
    expect(fragment.edges[0]).toMatchObject({ source: 'Caller', target: 'Center', type: 'CALLS' })
    expect(fragment.edges[0]!.id).toBe('Caller|CALLS|Center')
  })

  it('orients an OUTGOING edge from the center to the other node', () => {
    const fragment = neighborsToFragment(
      detail({
        outgoing: [
          { otherNode: nodeDto('Dep'), relationshipType: 'INJECTS', direction: 'OUTGOING' },
        ],
      }),
    )

    expect(fragment.edges[0]).toMatchObject({ source: 'Center', target: 'Dep', type: 'INJECTS' })
    expect(fragment.edges[0]!.id).toBe('Center|INJECTS|Dep')
  })

  it('produces stable edge ids so re-expansion does not duplicate edges', () => {
    const d = detail({
      outgoing: [{ otherNode: nodeDto('Dep'), relationshipType: 'INJECTS', direction: 'OUTGOING' }],
    })
    expect(neighborsToFragment(d).edges[0]!.id).toBe(neighborsToFragment(d).edges[0]!.id)
  })

  it('maps nullable backend fields to safe defaults', () => {
    const fragment = neighborsToFragment({
      node: { id: 'X', type: 'Method', name: 'X', fullName: 'X', filePath: '', lineNumber: null },
      incoming: [],
      outgoing: [],
    })
    expect(fragment.nodes[0]).toMatchObject({ id: 'X', lineNumber: 0, properties: {} })
  })

  it('skips a connection whose counterpart has no id', () => {
    const fragment = neighborsToFragment(
      detail({
        outgoing: [
          {
            otherNode: { id: '', type: 'Class', name: '', fullName: '', filePath: '' },
            relationshipType: 'CALLS',
            direction: 'OUTGOING',
          },
        ],
      }),
    )
    expect(fragment.nodes.map((n) => n.id)).toEqual(['Center'])
    expect(fragment.edges).toHaveLength(0)
  })

  it('does not truncate a small neighborhood', () => {
    const fragment = neighborsToFragment(
      detail({
        outgoing: [
          { otherNode: nodeDto('Dep'), relationshipType: 'INJECTS', direction: 'OUTGOING' },
        ],
      }),
    )
    expect(fragment.truncated).toBe(false)
    expect(fragment.totalNeighbors).toBe(1)
  })

  it('caps a high-degree hub to the neighbor limit and flags truncation', () => {
    const outgoing = Array.from({ length: EXPAND_MAX_NEIGHBORS + 50 }, (_, i) => ({
      otherNode: nodeDto(`n${String(i).padStart(4, '0')}`),
      relationshipType: 'CALLS',
      direction: 'OUTGOING',
    }))
    const fragment = neighborsToFragment(detail({ outgoing }))

    // center + exactly limit neighbors.
    expect(fragment.nodes).toHaveLength(EXPAND_MAX_NEIGHBORS + 1)
    expect(fragment.edges).toHaveLength(EXPAND_MAX_NEIGHBORS)
    expect(fragment.truncated).toBe(true)
    expect(fragment.totalNeighbors).toBe(EXPAND_MAX_NEIGHBORS + 50)
  })

  it('caps deterministically across repeated calls', () => {
    const outgoing = Array.from({ length: EXPAND_MAX_NEIGHBORS + 20 }, (_, i) => ({
      otherNode: nodeDto(`n${String(i).padStart(4, '0')}`),
      relationshipType: 'CALLS',
      direction: 'OUTGOING',
    }))
    const a = neighborsToFragment(detail({ outgoing })).nodes.map((n) => n.id)
    const b = neighborsToFragment(detail({ outgoing })).nodes.map((n) => n.id)
    expect(a).toEqual(b)
  })
})
