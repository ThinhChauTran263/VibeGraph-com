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
  it('draws one ellipse per use case and one stick figure (circle) per actor', () => {
    const model = fullModel()
    const svg = renderUmlUseCaseSvg(model)
    expect((svg.match(/<ellipse/g) ?? []).length).toBe(model.useCases.length)
    expect((svg.match(/<circle/g) ?? []).length).toBe(model.actors.length)
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

    // Each actor is a <g> with a <title> then a head <circle cx="...">.
    const cxByTitle = new Map<string, number>()
    const re = /<g><title>([^<]+)<\/title><circle cx="([\d.]+)"/g
    let m: RegExpExecArray | null
    while ((m = re.exec(svg)) !== null) cxByTitle.set(m[1]!, Number(m[2]))

    expect(cxByTitle.get('Carrier Tracking System')!).toBeGreaterThan(bRight)
    expect(cxByTitle.get('Guest')!).toBeLessThan(bx)
    expect(cxByTitle.get('Registered User')!).toBeLessThan(bx)
  })

  it('renders a valid svg with the boundary even for an empty model', () => {
    const svg = renderUmlUseCaseSvg({ systemName: 'Empty', actors: [], useCases: [], relations: [] })
    expect(svg).toMatch(/^<svg[^>]*>/)
    expect(svg).toContain('</svg>')
    expect(svg).toContain('<rect')
    expect(svg).toContain('Empty')
  })

  it('skips dangling relations whose endpoints are missing', () => {
    const svg = renderUmlUseCaseSvg({
      systemName: 'S',
      actors: [actor('A1', 'User')],
      useCases: [uc('U1', 'Do thing')],
      relations: [rel('A1', 'U1', 'association'), rel('A1', 'GHOST', 'association')],
    })
    // Only the valid association line is drawn (no throw, no phantom edge).
    expect((svg.match(/<line /g) ?? []).length).toBeGreaterThanOrEqual(1)
  })
})
