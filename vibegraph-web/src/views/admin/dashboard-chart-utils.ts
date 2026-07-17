import type { EChartsOption } from 'echarts'
import type { AdminDistributionPoint, AdminSeriesPoint } from '@/types/api'

export type ChartPeriod = 'day' | 'month' | 'quarter' | 'year'

const MONTH_LABELS = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
]
export function buildPeriodSeries(
  points: AdminSeriesPoint[],
  period: ChartPeriod,
  now = new Date(),
): AdminDistributionPoint[] {
  const scoped = points.filter((point) => !point.period || point.period === period)

  if (period === 'year') {
    const currentYear = now.getFullYear()
    const values = buildValueMap(scoped, (point) => extractYear(point.label)?.toString())
    return Array.from({ length: 5 }, (_, index) => {
      const year = currentYear - 4 + index
      return { label: year.toString(), value: values.get(year.toString()) ?? 0 }
    })
  }

  if (period === 'quarter') {
    const referenceYear = latestSeriesYear(scoped, now.getFullYear())
    const values = buildValueMap(scoped, (point) => {
      const quarter = extractQuarter(point.label)
      if (!quarter || quarter.year !== referenceYear) return undefined
      return quarter.quarter.toString()
    })
    return [1, 2, 3, 4].map((quarter) => ({
      label: `Q${quarter}`,
      value: values.get(quarter.toString()) ?? 0,
    }))
  }

  if (period === 'day') {
    const reference = latestSeriesMonth(scoped, now)
    const values = buildValueMap(scoped, (point) => {
      const day = extractDay(point.label)
      if (!day || day.year !== reference.year || day.month !== reference.month) return undefined
      return day.day.toString()
    })
    return Array.from({ length: daysInMonth(reference.year, reference.month) }, (_, index) => {
      const day = index + 1
      return { label: day.toString(), value: values.get(day.toString()) ?? 0 }
    })
  }

  const referenceYear = latestSeriesYear(scoped, now.getFullYear())
  const values = buildValueMap(scoped, (point) => {
    const month = extractMonth(point.label)
    if (!month || month.year !== referenceYear) return undefined
    return month.month.toString()
  })
  return MONTH_LABELS.map((label, index) => ({
    label,
    value: values.get((index + 1).toString()) ?? 0,
  }))
}

export function createHorizontalBarOption(
  points: AdminDistributionPoint[],
  valueFormatter: (value: number) => string = formatNumber,
): EChartsOption {
  const ordered = [...points].sort((a, b) => b.value - a.value).slice(0, 6).reverse()
  return {
    animationDuration: 320,
    color: ['#60a5fa'],
    grid: { top: 10, right: 20, bottom: 10, left: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      borderWidth: 0,
      backgroundColor: 'rgba(7, 11, 22, 0.96)',
      textStyle: { color: '#e8edf6' },
      valueFormatter: (value: unknown) => valueFormatter(Number(value ?? 0)),
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: niceAxisMax(Math.max(...ordered.map((point) => point.value), 0)),
      minInterval: 1,
      axisLabel: { color: '#9fb0c7', formatter: valueFormatter },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } },
    },
    yAxis: {
      type: 'category',
      data: ordered.map((point) => point.label),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        color: '#c8d4e5',
        fontSize: 11,
        width: 112,
        overflow: 'truncate',
      },
    },
    series: [
      {
        type: 'bar',
        data: ordered.map((point) => point.value),
        barMaxWidth: 20,
        itemStyle: { borderRadius: [0, 5, 5, 0] },
      },
    ],
  }
}

export function formatNumber(value: number | undefined): string {
  return new Intl.NumberFormat().format(value ?? 0)
}

function buildValueMap(
  points: AdminSeriesPoint[],
  keyForPoint: (point: AdminSeriesPoint) => string | undefined,
): Map<string, number> {
  return points.reduce((map, point) => {
    const key = keyForPoint(point)
    if (key) map.set(key, (map.get(key) ?? 0) + point.value)
    return map
  }, new Map<string, number>())
}

function latestSeriesYear(points: AdminSeriesPoint[], fallback: number): number {
  const years = points
    .map((point) => extractYear(point.label))
    .filter((year): year is number => Number.isFinite(year))
  return years.length ? Math.max(...years) : fallback
}

function latestSeriesMonth(
  points: AdminSeriesPoint[],
  fallback: Date,
): { year: number; month: number } {
  const months = points
    .map((point) => extractDay(point.label) ?? extractMonth(point.label))
    .filter((month): month is { year: number; month: number } => Boolean(month))
    .sort((a, b) => (a.year === b.year ? a.month - b.month : a.year - b.year))
  return months[months.length - 1] ?? {
    year: fallback.getFullYear(),
    month: fallback.getMonth() + 1,
  }
}

function extractYear(label: string): number | undefined {
  const match = label.match(/^(\d{4})/)
  return match?.[1] ? Number(match[1]) : undefined
}

function extractMonth(label: string): { year: number; month: number } | undefined {
  const match = label.match(/^(\d{4})-(\d{2})$/)
  return match?.[1] && match[2]
    ? { year: Number(match[1]), month: Number(match[2]) }
    : undefined
}

function extractDay(label: string): { year: number; month: number; day: number } | undefined {
  const match = label.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  return match?.[1] && match[2] && match[3]
    ? { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
    : undefined
}

function extractQuarter(label: string): { year: number; quarter: number } | undefined {
  const match = label.match(/^(\d{4})-Q([1-4])$/i)
  return match?.[1] && match[2]
    ? { year: Number(match[1]), quarter: Number(match[2]) }
    : undefined
}

function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

function niceAxisMax(value: number): number {
  if (value <= 5) return 5
  if (value <= 10) return 10
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const normalized = value / magnitude
  const step = normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return step * magnitude
}
