import { describe, expect, it } from 'vitest'
import {
  buildValueMap,
  chartPalette,
  daysInMonth,
  extractDay,
  extractMonth,
  extractQuarter,
  extractYear,
  latestSeriesMonth,
  latestSeriesYear,
  niceAxisMax,
  startOfMinute,
  sumPoints,
} from '../dashboard-transforms'

/**
 * F-M6 split companion: dashboard-transforms is pure and i18n-free, so every
 * branch gets a direct input->output pin here (the view-level spec only drives
 * the 'month' aggregation path).
 */
describe('dashboard-transforms', () => {
  it('sumPoints adds series values', () => {
    expect(sumPoints([{ label: 'a', value: 2 }, { label: 'b', value: 3 }])).toBe(5)
    expect(sumPoints([])).toBe(0)
  })

  it('buildValueMap sums values per key and skips undefined keys', () => {
    const map = buildValueMap(
      [
        { label: '2026', value: 4 },
        { label: '2026', value: 6 },
        { label: 'not-a-year', value: 99 },
      ],
      (point) => extractYear(point.label)?.toString(),
    )
    expect(map.get('2026')).toBe(10)
    expect(map.size).toBe(1)
  })

  it('extract helpers parse or reject label shapes', () => {
    expect(extractYear('2026')).toBe(2026)
    expect(extractYear('abc')).toBeUndefined()
    expect(extractMonth('2026-07')).toEqual({ year: 2026, month: 7 })
    expect(extractMonth('2026-7')).toBeUndefined()
    expect(extractDay('2026-07-17')).toEqual({ year: 2026, month: 7, day: 17 })
    expect(extractDay('2026-07')).toBeUndefined()
    expect(extractQuarter('2026-Q3')).toEqual({ year: 2026, quarter: 3 })
    expect(extractQuarter('2026-Q5')).toBeUndefined()
  })

  it('latestSeriesYear picks the newest parseable year, falling back to now', () => {
    expect(
      latestSeriesYear([
        { label: '2022', value: 1 },
        { label: '2025', value: 1 },
        { label: 'garbage', value: 1 },
      ]),
    ).toBe(2025)
    expect(latestSeriesYear([])).toBe(new Date().getFullYear())
  })

  it('latestSeriesMonth prefers day labels, then months, then the current month', () => {
    // Day-shaped labels pass through extractDay intact (day field included) —
    // pins the real behaviour, callers only read year/month.
    expect(latestSeriesMonth([{ label: '2026-07-17', value: 1 }])).toEqual({
      year: 2026,
      month: 7,
      day: 17,
    })
    expect(
      latestSeriesMonth([
        { label: '2026-02', value: 1 },
        { label: '2026-06', value: 1 },
      ]),
    ).toEqual({ year: 2026, month: 6 })
    const fallback = latestSeriesMonth([])
    expect(fallback.year).toBe(new Date().getFullYear())
  })

  it('daysInMonth handles a leap February', () => {
    expect(daysInMonth(2024, 2)).toBe(29)
    expect(daysInMonth(2026, 2)).toBe(28)
    expect(daysInMonth(2026, 7)).toBe(31)
  })

  it('startOfMinute floors to the minute boundary', () => {
    const minute = 60_000
    expect(startOfMinute(minute * 5 + 30_000)).toBe(minute * 5)
  })

  it('niceAxisMax rounds up to a clean ceiling', () => {
    expect(niceAxisMax(0)).toBe(5)
    expect(niceAxisMax(3)).toBe(5)
    expect(niceAxisMax(7)).toBe(10)
    expect(niceAxisMax(42)).toBe(50)
    expect(niceAxisMax(1234)).toBe(2000)
  })

  it('chartPalette returns four colors per tone', () => {
    for (const tone of ['blue', 'green', 'cyan', 'amber'] as const) {
      expect(chartPalette(tone)).toHaveLength(4)
    }
  })
})
