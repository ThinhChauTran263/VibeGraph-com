/**
 * F-M6 split (DashboardView, step 1): pure, i18n-free helpers and the dashboard
 * chart model types, extracted from DashboardView.vue. Everything here is a pure
 * function of its arguments — no store, no i18n, no reactive state — so it is
 * unit-testable in isolation and safe to import from anywhere.
 */
import type { AdminDistributionPoint, AdminSeriesPoint } from '@/types/api'

export type ChartMode = 'line' | 'bar' | 'pie'
export type ChartId = 'totalUsers' | 'onlineUsers' | 'credits' | 'storage'
export type ChartTone = 'blue' | 'green' | 'cyan' | 'amber'
export type Period = 'day' | 'month' | 'quarter' | 'year'

export interface DashboardChart {
  id: ChartId
  title: string
  eyebrow: string
  value: string
  subtitle: string
  points: AdminDistributionPoint[]
  emptyText: string
  tone: ChartTone
  valueKind: 'number' | 'bytes'
}

export interface OnlineSample extends AdminSeriesPoint {
  capturedAt: number
}

export const MINUTE_MS = 60 * 1000

export function sumPoints(points: AdminSeriesPoint[] | AdminDistributionPoint[]): number {
  return points.reduce((sum, point) => sum + point.value, 0)
}

export function buildValueMap(
  points: AdminSeriesPoint[],
  keyForPoint: (point: AdminSeriesPoint) => string | undefined,
): Map<string, number> {
  return points.reduce((map, point) => {
    const key = keyForPoint(point)
    if (!key) return map
    map.set(key, (map.get(key) ?? 0) + point.value)
    return map
  }, new Map<string, number>())
}

export function latestSeriesYear(points: AdminSeriesPoint[]): number {
  const years = points
    .map((point) => extractYear(point.label))
    .filter((year): year is number => Number.isFinite(year))
  return years.length ? Math.max(...years) : new Date().getFullYear()
}

export function latestSeriesMonth(points: AdminSeriesPoint[]): { year: number; month: number } {
  const months = points
    .map((point) => extractDay(point.label) ?? extractMonth(point.label))
    .filter((month): month is { year: number; month: number } => Boolean(month))
    .sort((a, b) => (a.year === b.year ? a.month - b.month : a.year - b.year))
  const latest = months[months.length - 1]
  if (latest) return latest

  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1 }
}

export function extractYear(label: string): number | undefined {
  const match = label.match(/^(\d{4})/)
  if (!match?.[1]) return undefined
  return Number(match[1])
}

export function extractMonth(label: string): { year: number; month: number } | undefined {
  const match = label.match(/^(\d{4})-(\d{2})$/)
  if (!match?.[1] || !match[2]) return undefined
  return { year: Number(match[1]), month: Number(match[2]) }
}

export function extractDay(label: string): { year: number; month: number; day: number } | undefined {
  const match = label.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!match?.[1] || !match[2] || !match[3]) return undefined
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
}

export function extractQuarter(label: string): { year: number; quarter: number } | undefined {
  const match = label.match(/^(\d{4})-Q([1-4])$/i)
  if (!match?.[1] || !match[2]) return undefined
  return { year: Number(match[1]), quarter: Number(match[2]) }
}

export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

export function startOfMinute(value: number): number {
  return Math.floor(value / MINUTE_MS) * MINUTE_MS
}

export function niceAxisMax(value: number): number {
  if (value <= 0) return 5
  if (value <= 5) return 5
  if (value <= 10) return 10
  const magnitude = 10 ** Math.floor(Math.log10(value))
  return Math.ceil(value / magnitude) * magnitude
}

export function chartPalette(tone: ChartTone): string[] {
  switch (tone) {
    case 'green':
      return ['#4ade80', '#22d3ee', '#60a5fa', '#a78bfa']
    case 'cyan':
      return ['#22d3ee', '#60a5fa', '#818cf8', '#f59e0b']
    case 'amber':
      return ['#f59e0b', '#60a5fa', '#22d3ee', '#4ade80']
    case 'blue':
    default:
      return ['#60a5fa', '#818cf8', '#22d3ee', '#4ade80']
  }
}
