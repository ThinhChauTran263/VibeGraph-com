import { describe, expect, it } from 'vitest'

import type { UmlActor, UmlRelation, UmlUseCaseElement } from '@/lib/api'
import { renderUmlUseCaseSvg, type UmlUseCaseModel } from '@/lib/umlUseCaseSvg'

function actor(id: string, name: string): UmlActor {
  return { id, name, source: 's', confidence: 0.8 }
}

function uc(id: string, name: string): UmlUseCaseElement {
  return { id, name, domain: 'd', level: 'business', source: 's', sourceEndpoint: null, confidence: 0.8 }
}

function rel(from: string, to: string, type: string): UmlRelation {
  return { from, to, type, label: null, confidence: 0.8 }
}

/** Tracking-shaped model: 3 human actors + 1 external system, ~13 use cases, all four relation types. */
function fullModel(): UmlUseCaseModel {
  return {
    systemName: 'SPX Tracking System',
    actors: [
      actor('A_Guest', 'Guest'),
      actor('A_User', 'Registered User'),
      actor('A_Admin', 'Administrator'),
      actor('A_Carrier', 'Carrier Tracking System'),
    ],
    useCases: [
      uc('UC_Register', 'Register Account'),
      uc('UC_Login', 'Log In'),
      uc('UC_Dashboard', 'View Dashboard'),
      uc('UC_Profile', 'Manage Profile'),
      uc('UC_RegTrack', 'Register Tracking Number'),
      uc('UC_ViewTrack', 'View Tracking Details'),
      uc('UC_History', 'Review Tracking History'),
      uc('UC_Validate', 'Validate Tracking Number'),
      uc('UC_Sync', 'Synchronize Shipment Status'),
      uc('UC_Receive', 'Receive Shipment Status Update'),
      uc('UC_Users', 'Manage User Accounts'),
      uc('UC_Stats', 'Analyze Statistics'),
      uc('UC_Resources', 'Manage System Resources'),
    ],
    relations: [
      rel('A_Guest', 'UC_Register', 'association'),
      rel('A_Guest', 'UC_Login', 'association'),
      rel('A_User', 'UC_Dashboard', 'association'),
      rel('A_User', 'UC_Profile', 'association'),
      rel('A_User', 'UC_RegTrack', 'association'),
      rel('A_User', 'UC_ViewTrack', 'association'),
      rel('A_User', 'UC_History', 'association'),
      rel('A_Admin', 'UC_Users', 'association'),
      rel('A_Admin', 'UC_Stats', 'association'),
      rel('A_Admin', 'UC_Resources', 'association'),
      rel('A_Carrier', 'UC_Sync', 'association'),
      rel('A_Carrier', 'UC_Receive', 'association'),
      rel('A_User', 'A_Guest', 'generalization'),
      rel('A_Admin', 'A_User', 'generalization'),
      rel('UC_RegTrack', 'UC_Validate', 'include'),
      rel('UC_Receive', 'UC_Sync', 'include'),
      rel('UC_ViewTrack', 'UC_History', 'extend'),
    ],
  }
}

describe('renderUmlUseCaseSvg', () => {
  it('draws one ellipse per use case and a stick figure for each human actor', () => {
    const model = fullModel()
    const svg = renderUmlUseCaseSvg(model)
    expect((svg.match(/<ellipse/g) ?? []).length).toBe(model.useCases.length)
    // 3 human actors get a stick-figure head circle; the 1 external system is a «system» box.
    expect((svg.match(/<circle/g) ?? []).length).toBe(3)
  })

  it('declares both UML arrowhead markers', () => {
    const svg = renderUmlUseCaseSvg(fullModel())
    expect(svg).toContain('id="uml-open-arrow"')
    expect(svg).toContain('id="uml-triangle"')
  })

  it('renders include/extend as dashed dependencies with stereotype labels', () => {
    const svg = renderUmlUseCaseSvg(fullModel())
    expect(svg).toContain('«include»')
    expect(svg).toContain('«extend»')
    expect(svg).toContain('stroke-dasharray')
    expect(svg).toContain('marker-end="url(#uml-open-arrow)"')
  })

  it('renders actor generalization with the hollow-triangle marker', () => {
    const svg = renderUmlUseCaseSvg(fullModel())
    expect(svg).toContain('marker-end="url(#uml-triangle)"')
  })

  it('renders the system boundary title and a numeric viewBox', () => {
    const svg = renderUmlUseCaseSvg(fullModel())
    expect(svg).toContain('SPX Tracking System')
    expect(svg).toMatch(/^<svg[^>]*viewBox="0 0 \d+ \d+"/)
    expect(svg).toContain('data-test="uml-usecase-svg"')
  })

  it('is deterministic for the same model', () => {
    const a = renderUmlUseCaseSvg(fullModel())
    const b = renderUmlUseCaseSvg(fullModel())
    expect(a).toBe(b)
  })

  it('matches the snapshot', () => {
    expect(renderUmlUseCaseSvg(fullModel())).toMatchSnapshot()
  })

  it('escapes XML-special characters in labels', () => {
    const svg = renderUmlUseCaseSvg({
      systemName: 'A & B <x>',
      actors: [actor('A1', 'Tom & "Jerry"')],
      useCases: [uc('U1', 'Do <stuff> & things')],
      relations: [rel('A1', 'U1', 'association')],
    })
    expect(svg).toContain('A &amp; B &lt;x&gt;')
    expect(svg).toContain('Do &lt;stuff&gt; &amp; things')
    expect(svg).toContain('Tom &amp; &quot;Jerry&quot;')
  })

  it('wraps a long use-case label across two lines and keeps the full text in a title', () => {
    const long = 'Reconcile Outstanding Shipment Discrepancies Across Carriers'
    const svg = renderUmlUseCaseSvg({
      systemName: 'S',
      actors: [],
      useCases: [uc('U1', long)],
      relations: [],
    })
    // Wrapped onto >1 line (more than one <text> for the single use case) and full text on hover.
    expect((svg.match(/<text /g) ?? []).length).toBeGreaterThanOrEqual(3) // boundary title + >=2 label lines
    expect(svg).toContain(`<title>${long}</title>`)
  })

  it('places external-system actors right of the boundary and humans left of it', () => {
    const svg = renderUmlUseCaseSvg(fullModel())
    const boundary = /<rect x="([\d.]+)"[^>]*width="([\d.]+)"/.exec(svg)
    expect(boundary).not.toBeNull()
    const bx = Number(boundary![1])
    const bRight = bx + Number(boundary![2])

    // Human actors are a <g> with a <title> then a head <circle cx="...">.
    const humanX = new Map<string, number>()
    const human = /<g><title>([^<]+)<\/title><circle cx="([\d.]+)"/g
    let m: RegExpExecArray | null
    while ((m = human.exec(svg)) !== null) humanX.set(m[1]!, Number(m[2]))

    // The external system is a <g> with a <title> then a <rect x="..."> box of known width.
    const sys = /<g><title>([^<]+)<\/title><rect x="([\d.]+)"/.exec(svg)
    expect(sys).not.toBeNull()
    const sysCx = Number(sys![2]) + 120 / 2 // SYS_ACTOR_W

    expect(sys![1]).toBe('Carrier Tracking System')
    expect(sysCx).toBeGreaterThan(bRight)
    expect(humanX.get('Guest')!).toBeLessThan(bx)
    expect(humanX.get('Registered User')!).toBeLessThan(bx)
  })

  it('draws external-system actors as a «system» box, humans as stick figures', () => {
    const svg = renderUmlUseCaseSvg(fullModel())
    // 3 human actors keep the stick-figure head circle; the external system has none.
    expect((svg.match(/<circle/g) ?? []).length).toBe(3)
    // The external system is rendered as a «system» stereotype box.
    expect(svg).toContain('«system»')
  })

  it('renders a valid svg with the boundary even for an empty model', () => {
    const svg = renderUmlUseCaseSvg({ systemName: 'Empty', actors: [], useCases: [], relations: [] })
    expect(svg).toMatch(/^<svg[^>]*>/)
    expect(svg).toContain('</svg>')
    expect(svg).toContain('<rect')
    expect(svg).toContain('Empty')
  })

  it('shows an explanatory note when no use cases and no actors are detected', () => {
    const svg = renderUmlUseCaseSvg({ systemName: 'Empty', actors: [], useCases: [], relations: [] })
    expect(svg).toContain('No business use cases detected')
    expect(svg).toContain('This project exposes no API endpoints to infer use cases from')
    // No ellipses / actor figures in a truly empty model.
    expect((svg.match(/<ellipse/g) ?? []).length).toBe(0)
    expect((svg.match(/<circle/g) ?? []).length).toBe(0)
  })

  it('does not show the empty note when at least one use case exists', () => {
    const svg = renderUmlUseCaseSvg({
      systemName: 'S',
      actors: [actor('A1', 'User')],
      useCases: [uc('U1', 'Do thing')],
      relations: [rel('A1', 'U1', 'association')],
    })
    expect(svg).not.toContain('No business use cases detected')
  })

  it('skips dangling relations whose endpoints are missing', () => {
    const svg = renderUmlUseCaseSvg({
      systemName: 'S',
      actors: [actor('A1', 'User')],
      useCases: [uc('U1', 'Do thing')],
      relations: [rel('A1', 'U1', 'association'), rel('A1', 'GHOST', 'association')],
    })
    // Exactly one association edge is drawn (a straight actor->use case line); the dangling
    // A1->GHOST relation is skipped without throwing or drawing a phantom edge. Edge lines use
    // stroke-width 1.3 (stick-figure body lines use 1.5), so match the edge stroke specifically.
    expect((svg.match(/<line [^>]*stroke-width="1.3"/g) ?? []).length).toBe(1)
  })

  it('draws each association as a straight line directly from actor to use case', () => {
    // Regression guard for both the "đường ziczac" bus and the orthogonal "plumbing" routing: with a
    // single use-case column, every association is one straight <line> (not a multi-segment polyline
    // sharing a corridor), so it can never be misread as use-case-to-use-case flow.
    const actors = [actor('A1', 'User')]
    const useCases = Array.from({ length: 5 }, (_, i) => uc(`U${i}`, `Goal ${i}`))
    const relations = useCases.map((u) => rel('A1', u.id, 'association'))
    const svg = renderUmlUseCaseSvg({ systemName: 'S', actors, useCases, relations })

    // Five associations -> five straight edge lines (stroke-width 1.3), zero orthogonal polylines.
    const edgeLines = svg.match(/<line [^>]*stroke-width="1.3"[^>]*\/>/g) ?? []
    expect(edgeLines.length).toBe(5)
    expect((svg.match(/<polyline /g) ?? []).length).toBe(0)
    // Every line starts at the single actor (shared x1) and fans out to each ellipse — a clean fan
    // from one point, not a vertical bus threading through the nodes.
    const lineStartXs = edgeLines.map((m) => /x1="([^"]+)"/.exec(m)![1])
    expect(new Set(lineStartXs).size).toBe(1)
  })

  it('draws low-confidence inferred use cases and edges faintly', () => {
    // An inferred shared-service include carries low confidence; the renderer marks both the
    // ellipse and its association line as dashed/translucent so a reader can tell guessed from
    // certain elements.
    const svg = renderUmlUseCaseSvg({
      systemName: 'S',
      actors: [actor('A1', 'User'), actor('A2', 'Admin')],
      useCases: [
        uc('U1', 'Manage Orders'),
        uc('U2', 'Manage Products'),
        { ...uc('UC_Validate', 'Validate Input'), confidence: 0.5 },
      ],
      relations: [
        rel('A1', 'U1', 'association'),
        rel('A2', 'U2', 'association'),
        { ...rel('U1', 'UC_Validate', 'include'), confidence: 0.5 },
        { ...rel('U2', 'UC_Validate', 'include'), confidence: 0.5 },
      ],
    })
    // The inferred use case ellipse is dashed + translucent.
    expect(svg).toMatch(/<ellipse[^>]*stroke-dasharray="5 4" opacity="0.7"[^>]*><title>Validate Input/)
  })
})
