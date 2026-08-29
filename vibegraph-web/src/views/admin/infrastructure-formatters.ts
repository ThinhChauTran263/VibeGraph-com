export type MonitorSeriesKey = 'all' | 'cpu' | 'ram' | 'net' | 'disk'
export type MonitorChartKey =
  | 'cpuPercent'
  | 'memoryPercent'
  | 'diskPercent'
  | 'networkBytesPerSecond'

export function isAvailable(status?: string | null): boolean {
  return !['UNAVAILABLE', 'UNKNOWN', 'WARMING_UP'].includes(status ?? '')
}

export function bytes(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  if (value >= 1024 ** 3) return (value / 1024 ** 3).toFixed(1) + ' GB'
  if (value >= 1024 ** 2) return Math.round(value / 1024 ** 2) + ' MB'
  if (value >= 1024) return Math.round(value / 1024) + ' KB'
  return Math.round(value) + ' B'
}

export function preciseBytes(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  if (value >= 1024 ** 3) return (value / 1024 ** 3).toFixed(2) + ' GB'
  return bytes(value)
}

export function rate(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  if (value >= 1024 ** 2) return (value / 1024 ** 2).toFixed(1) + ' MB/s'
  if (value >= 1024) return (value / 1024).toFixed(1) + ' KB/s'
  return Math.round(value) + ' B/s'
}

export function metricRate(value: number | null | undefined, status?: string | null): string {
  return isAvailable(status) ? rate(value) : 'UNAVAILABLE'
}

export function percent(value: number | null | undefined): string {
  return value === null || value === undefined || !Number.isFinite(value)
    ? '—'
    : value.toFixed(1) + '%'
}

export function duration(value: number): string {
  return value >= 1000 ? (value / 1000).toFixed(1) + 's' : Math.round(value) + 'ms'
}

export function measurementLabel(value?: string | null): string {
  if (!value) return 'UNKNOWN'
  return value === 'OBSERVED' ? 'HOST-CORRELATED' : value.split('_').join(' ')
}

export function operationStorage(value: number | null | undefined, type?: string | null): string {
  if (value !== null && value !== undefined && value > 0) return '+' + preciseBytes(value)
  if (type === 'API' || type === 'MCP') return 'read only'
  return 'not measured'
}

export function chartPoints(
  samples: readonly {
    cpuPercent: number
    memoryPercent: number
    diskPercent: number | null
    networkBytesPerSecond: number | null
  }[],
  key: MonitorChartKey,
  diskStatus: string | undefined,
  networkStatus: string | undefined,
): string {
  if (samples.length < 2) return ''
  if (key === 'diskPercent' && !isAvailable(diskStatus)) return ''
  if (key === 'networkBytesPerSecond' && !isAvailable(networkStatus)) return ''
  const networkScale = Math.max(
    ...samples
      .map((item) => item.networkBytesPerSecond)
      .filter((value): value is number => value !== null && Number.isFinite(value)),
    1,
  )
  const width = 720
  const height = 210
  return samples
    .map((sample, index) => {
      const raw = sample[key]
      if (raw === null || !Number.isFinite(raw)) return null
      const value = key === 'networkBytesPerSecond' ? (raw / networkScale) * 35 : raw
      const x = (index / (samples.length - 1)) * width
      const y = height - (Math.max(0, Math.min(100, value)) / 100) * height + 12
      return x.toFixed(1) + ',' + y.toFixed(1)
    })
    .filter((point): point is string => point !== null)
    .join(' ')
}
