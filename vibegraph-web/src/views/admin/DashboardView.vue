<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminStore } from '@/stores/admin'
import { createHorizontalBarOption } from './dashboard-chart-utils'
import {
  MINUTE_MS,
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
  type ChartId,
  type ChartMode,
  type DashboardChart,
  type OnlineSample,
  type Period,
} from './dashboard-transforms'
import type {
  AdminDistributionPoint,
  AdminSecurityAlert,
  AdminSeriesPoint,
  AdminStorageSubject,
} from '@/types/api'

// F-M6 split: echarts (registration + VChart) loads as its own async chunk so the
// ~500 kB chart bundle stays out of this route chunk until a chart actually renders.
const VChart = defineAsyncComponent(() => import('./dashboard-echarts'))

const adminStore = useAdminStore()
const { locale, t } = useI18n({ useScope: 'global' })
const loading = ref(true)
const errorMsg = ref('')
const period = ref<Period>('month')
const chartModes = reactive<Record<ChartId, ChartMode>>({
  totalUsers: 'line',
  onlineUsers: 'line',
  credits: 'bar',
  storage: 'pie',
})
const chartUpdateOptions = {
  notMerge: true,
  replaceMerge: ['xAxis', 'yAxis', 'series'],
}

let pollInterval: ReturnType<typeof setInterval> | undefined
const ONLINE_DISPLAY_BUCKETS = 10
const POLL_INTERVAL_MS = 30 * 1000

const overview = computed(() => adminStore.overview)
const onlineSamples = computed<OnlineSample[]>(() => {
  const samplesByMinute = new Map<number, OnlineSample>()
  for (const point of overview.value?.onlineUserHistory ?? []) {
    const capturedAt = Date.parse(point.label)
    if (!Number.isFinite(capturedAt) || !Number.isFinite(point.value)) continue
    const minute = startOfMinute(capturedAt)
    samplesByMinute.set(minute, {
      ...point,
      label: formatTime24(minute),
      capturedAt: minute,
    })
  }
  return Array.from(samplesByMinute.values())
    .sort((a, b) => a.capturedAt - b.capturedAt)
    .slice(-ONLINE_DISPLAY_BUCKETS)
})
const userGrowth = computed(() => overview.value?.userGrowth ?? [])
const creditConsumption = computed(() => overview.value?.creditConsumption ?? [])
const storage = computed(() => overview.value?.storage ?? null)
const planDistribution = computed(() => overview.value?.planDistribution ?? [])
const topStorageUsers = computed(() => overview.value?.topStorageUsers ?? [])
const topStorageProjects = computed(() => overview.value?.topStorageProjects ?? [])
const securityAlerts = computed(() => overview.value?.securityAlerts ?? [])
const topStorageProjectChartOption = computed(() => {
  const activeLocale = locale.value
  return createHorizontalBarOption(
    topStorageProjects.value.map((project) => ({
      label: project.name,
      value: project.usedBytes,
    })),
    (value) => formatBytes(value, activeLocale),
  )
})
const planDistributionChartOption = computed(() => {
  const activeLocale = locale.value
  return createHorizontalBarOption(planDistribution.value, (value) =>
    formatNumber(value, activeLocale),
  )
})

const storagePercent = computed(() => {
  if (!storage.value?.totalBytes) return 0
  return Math.min(100, Math.round((storage.value.usedBytes / storage.value.totalBytes) * 100))
})

const totalUserPoints = computed<AdminDistributionPoint[]>(() => {
  return bucketSeries(userGrowth.value, period.value)
})

const onlineUserPoints = computed<AdminDistributionPoint[]>(() => {
  if (onlineSamples.value.length > 0) {
    return onlineSamples.value.map((sample) => ({
      label: formatTime24(sample.capturedAt),
      value: sample.value,
    }))
  }
  return [
    {
      label: t('admin.overview.charts.onlineUsers.onlineNow'),
      value: overview.value?.onlineUsers ?? 0,
    },
  ]
})

const creditPoints = computed<AdminDistributionPoint[]>(() => {
  return bucketSeries(creditConsumption.value, period.value)
})

const storagePoints = computed<AdminDistributionPoint[]>(() => {
  if (!storage.value) return []
  const used = storage.value.usedBytes
  const available = Math.max(storage.value.totalBytes - used, 0)
  return [
    { label: t('admin.overview.charts.storage.usedLabel'), value: used },
    { label: t('admin.overview.charts.storage.availableLabel'), value: available },
  ]
})

const dashboardCharts = computed<DashboardChart[]>(() => [
  {
    id: 'totalUsers',
    title: t('admin.overview.charts.totalUsers.title'),
    eyebrow: periodLabel(period.value),
    value: formatNumber(overview.value?.totalUsers),
    subtitle: t('admin.overview.charts.totalUsers.newUsersAcross', {
      count: sumPoints(totalUserPoints.value),
      formattedCount: formatNumber(sumPoints(totalUserPoints.value)),
      buckets: periodBucketText(period.value, totalUserPoints.value.length),
    }),
    points: totalUserPoints.value,
    emptyText: t('admin.overview.charts.totalUsers.empty'),
    tone: 'blue',
    valueKind: 'number',
  },
  {
    id: 'onlineUsers',
    title: t('admin.overview.charts.onlineUsers.title'),
    eyebrow: t('admin.overview.charts.onlineUsers.eyebrow'),
    value: formatNumber(overview.value?.onlineUsers),
    subtitle: t('admin.overview.charts.onlineUsers.samplesInWindow', {
      count: onlineSamples.value.length,
      minutes: ONLINE_DISPLAY_BUCKETS,
    }),
    points: onlineUserPoints.value,
    emptyText: t('admin.overview.charts.onlineUsers.empty'),
    tone: 'green',
    valueKind: 'number',
  },
  {
    id: 'credits',
    title: t('admin.overview.charts.credits.title'),
    eyebrow: periodLabel(period.value),
    value: formatNumber(sumPoints(creditPoints.value)),
    subtitle:
      sumPoints(creditPoints.value) > 0
        ? t('admin.overview.charts.credits.ledgerTotals', {
            buckets: periodBucketText(period.value, creditPoints.value.length),
          })
        : t('admin.overview.charts.credits.noneForPeriod'),
    points: creditPoints.value,
    emptyText: t('admin.overview.charts.credits.empty'),
    tone: 'cyan',
    valueKind: 'number',
  },
  {
    id: 'storage',
    title: t('admin.overview.charts.storage.title'),
    eyebrow:
      storage.value?.sourceLabel ||
      storage.value?.mountPath ||
      t('admin.overview.charts.storage.serverDisk'),
    value: storage.value
      ? t('admin.overview.charts.storage.usedPercent', { percent: storagePercent.value })
      : t('admin.overview.charts.storage.unavailable'),
    subtitle: storage.value
      ? t('admin.overview.charts.storage.usedOf', {
          used: formatBytes(storage.value.usedBytes),
          total: formatBytes(storage.value.totalBytes),
        })
      : t('admin.overview.charts.storage.unavailableEnvironment'),
    points: storagePoints.value,
    emptyText: t('admin.overview.charts.storage.unavailableEnvironment'),
    tone: 'amber',
    valueKind: 'bytes',
  },
])

onMounted(async () => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
  if (document.visibilityState === 'visible') {
    await loadOverview()
    startPolling()
  } else {
    loading.value = false
  }
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

function startPolling(): void {
  stopPolling()
  pollInterval = setInterval(() => {
    if (document.visibilityState === 'visible') void loadOverview()
  }, POLL_INTERVAL_MS)
}

function stopPolling(): void {
  if (!pollInterval) return
  clearInterval(pollInterval)
  pollInterval = undefined
}

function handleVisibilityChange(): void {
  if (document.visibilityState !== 'visible') {
    stopPolling()
    return
  }
  void loadOverview()
  startPolling()
}

async function loadOverview(): Promise<void> {
  try {
    await adminStore.fetchOverview()
    errorMsg.value = ''
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : t('admin.overview.errors.loadFailed')
  } finally {
    loading.value = false
  }
}

function formatNumber(value: number | undefined, activeLocale = locale.value): string {
  return new Intl.NumberFormat(activeLocale).format(value ?? 0)
}

function formatBytes(value: number | undefined, activeLocale = locale.value): string {
  const bytes = value ?? 0
  const formatUnit = (unitValue: number, unitKey: string) =>
    t(`admin.overview.units.${unitKey}`, {
      value: new Intl.NumberFormat(activeLocale, {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }).format(unitValue),
    })
  if (bytes >= 1_099_511_627_776) return formatUnit(bytes / 1_099_511_627_776, 'tb')
  if (bytes >= 1_073_741_824) return formatUnit(bytes / 1_073_741_824, 'gb')
  if (bytes >= 1_048_576) return formatUnit(bytes / 1_048_576, 'mb')
  if (bytes >= 1024) return formatUnit(bytes / 1024, 'kb')
  return t('admin.overview.units.bytes', { value: formatNumber(bytes, activeLocale) })
}

function formatChartValue(value: number, kind: DashboardChart['valueKind']): string {
  return kind === 'bytes' ? formatBytes(value) : formatNumber(value)
}

function periodBucketText(selectedPeriod: Period, bucketCount?: number): string {
  switch (selectedPeriod) {
    case 'year':
      return t('admin.overview.periodBuckets.year', { count: 5 })
    case 'quarter':
      return t('admin.overview.periodBuckets.quarter', { count: 4 })
    case 'day':
      return t('admin.overview.periodBuckets.day', { count: bucketCount ?? 0 })
    case 'month':
    default:
      return t('admin.overview.periodBuckets.month', { count: 12 })
  }
}

function periodLabel(selectedPeriod: Period): string {
  return t(`admin.overview.periods.${selectedPeriod}`)
}

function chartModeLabel(mode: ChartMode): string {
  return t(`admin.overview.chartModes.${mode}`)
}

function bucketSeries(
  points: AdminSeriesPoint[],
  selectedPeriod: Period,
): AdminDistributionPoint[] {
  const scoped = points.filter((point) => !point.period || point.period === selectedPeriod)
  const referenceYear =
    selectedPeriod === 'year' ? new Date().getFullYear() : latestSeriesYear(scoped)

  if (selectedPeriod === 'year') {
    const years = Array.from({ length: 5 }, (_, index) => referenceYear - 4 + index)
    const values = buildValueMap(scoped, (point) => extractYear(point.label)?.toString())
    return years.map((year) => ({
      label: year.toString(),
      value: values.get(year.toString()) ?? 0,
    }))
  }

  if (selectedPeriod === 'quarter') {
    const values = buildValueMap(scoped, (point) => {
      const quarter = extractQuarter(point.label)
      if (!quarter || quarter.year !== referenceYear) return undefined
      return quarter.quarter.toString()
    })
    return [1, 2, 3, 4].map((quarter) => ({
      label: t('admin.overview.chartLabels.quarter', { quarter }),
      value: values.get(quarter.toString()) ?? 0,
    }))
  }

  if (selectedPeriod === 'day') {
    const referenceMonth = latestSeriesMonth(scoped)
    const daysInReferenceMonth = daysInMonth(referenceMonth.year, referenceMonth.month)
    const values = buildValueMap(scoped, (point) => {
      const day = extractDay(point.label)
      if (!day || day.year !== referenceMonth.year || day.month !== referenceMonth.month)
        return undefined
      return day.day.toString()
    })

    return Array.from({ length: daysInReferenceMonth }, (_, index) => {
      const day = index + 1
      return { label: day.toString(), value: values.get(day.toString()) ?? 0 }
    })
  }

  const values = buildValueMap(scoped, (point) => {
    const month = extractMonth(point.label)
    if (!month || month.year !== referenceYear) return undefined
    return month.month.toString()
  })
  return [
    t('admin.overview.months.jan'),
    t('admin.overview.months.feb'),
    t('admin.overview.months.mar'),
    t('admin.overview.months.apr'),
    t('admin.overview.months.may'),
    t('admin.overview.months.jun'),
    t('admin.overview.months.jul'),
    t('admin.overview.months.aug'),
    t('admin.overview.months.sep'),
    t('admin.overview.months.oct'),
    t('admin.overview.months.nov'),
    t('admin.overview.months.dec'),
  ].map((label, index) => ({
    label,
    value: values.get((index + 1).toString()) ?? 0,
  }))
}

function chartOption(card: DashboardChart): Record<string, unknown> {
  const mode = chartModes[card.id]
  const labels = card.points.map((point) => point.label)
  const values = card.points.map((point) => point.value)
  const axisBounds = valueAxisBounds(card, values)
  const onlineWindow = onlineWindowBounds()
  const onlineSeries = card.id === 'onlineUsers' ? onlineSeriesData() : []
  const hasRotatedCategoryLabels = card.id !== 'onlineUsers' && period.value === 'day'
  const xAxisLabel = categoryAxisLabel(card, labels.length)
  const colors = chartPalette(card.tone)
  const textColor = '#c7d2fe'
  const mutedColor = '#7f8ea3'
  const gridLine = 'rgba(148, 163, 184, 0.12)'
  const valueFormatter = (value: unknown) => formatChartValue(Number(value ?? 0), card.valueKind)
  const onlineTooltipFormatter = (params: unknown) => formatOnlineTooltip(params, onlineSeries)

  const base = {
    color: colors,
    animationDuration: 350,
    backgroundColor: 'transparent',
    tooltip: {
      trigger: mode === 'pie' ? 'item' : 'axis',
      borderWidth: 0,
      backgroundColor: 'rgba(15, 23, 42, 0.96)',
      textStyle: { color: '#e5eefc', fontFamily: 'Inter, system-ui, sans-serif' },
      formatter: card.id === 'onlineUsers' ? onlineTooltipFormatter : undefined,
      valueFormatter: card.id === 'onlineUsers' && mode !== 'pie' ? undefined : valueFormatter,
    },
    textStyle: {
      color: textColor,
      fontFamily: 'Inter, system-ui, sans-serif',
      fontWeight: 600,
    },
  }

  if (mode === 'pie') {
    const isOnlinePie = card.id === 'onlineUsers'
    const dataPoints = pieDataPoints(card)
    return {
      ...base,
      legend: {
        type: 'scroll',
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
        icon: 'circle',
        itemWidth: 10,
        itemHeight: 10,
        textStyle: { color: textColor, fontSize: 11, fontWeight: 700 },
        formatter: (name: string) => {
          const point = dataPoints.find((item) => item.label === name)
          if (isOnlinePie) {
            return onlinePieLegendLabel(name, point?.value ?? 0)
          }
          return `${name}  ${formatChartValue(point?.value ?? 0, card.valueKind)}`
        },
      },
      series: [
        {
          name: card.title,
          type: 'pie',
          radius: ['46%', '70%'],
          center: ['50%', '43%'],
          minAngle: 6,
          avoidLabelOverlap: true,
          label: { show: false },
          labelLine: { show: false },
          data: dataPoints.map((point) => ({
            name: point.label,
            value: point.value,
            minutes: point.value,
            percent: Math.round((point.value / ONLINE_DISPLAY_BUCKETS) * 100),
          })),
        },
      ],
    }
  }

  if (mode === 'bar') {
    return {
      ...base,
      grid: {
        top: 20,
        right: 20,
        bottom: hasRotatedCategoryLabels ? 42 : 18,
        left: 12,
        containLabel: true,
      },
      xAxis: {
        type: card.id === 'onlineUsers' ? 'value' : 'category',
        min: card.id === 'onlineUsers' ? onlineWindow.start : undefined,
        max: card.id === 'onlineUsers' ? onlineWindow.end : undefined,
        interval: card.id === 'onlineUsers' ? 60 * 1000 : undefined,
        data: card.id === 'onlineUsers' ? undefined : labels,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: gridLine } },
        splitLine: { show: false },
        axisLabel: {
          ...xAxisLabel,
          color: mutedColor,
        },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: axisBounds.max,
        interval: axisBounds.interval,
        minInterval: card.valueKind === 'number' ? 1 : undefined,
        axisLabel: {
          color: mutedColor,
          formatter: valueFormatter,
        },
        splitLine: { lineStyle: { color: gridLine } },
      },
      series: [
        {
          name: card.title,
          type: 'bar',
          data:
            card.id === 'onlineUsers'
              ? onlineSeries.map((sample) => [sample.time, sample.value])
              : values,
          barMaxWidth: 26,
          itemStyle: {
            borderRadius: [8, 8, 0, 0],
          },
          label: {
            show: card.id !== 'onlineUsers' && labels.length <= 6,
            position: 'top',
            color: textColor,
            fontWeight: 800,
            formatter: (params: { value: number | [number, number] }) =>
              formatChartValue(
                Array.isArray(params.value) ? params.value[1] : params.value,
                card.valueKind,
              ),
          },
        },
      ],
    }
  }

  return {
    ...base,
    grid: {
      top: 18,
      right: 20,
      bottom: hasRotatedCategoryLabels ? 42 : 18,
      left: 12,
      containLabel: true,
    },
    xAxis: {
      type: card.id === 'onlineUsers' ? 'value' : 'category',
      min: card.id === 'onlineUsers' ? onlineWindow.start : undefined,
      max: card.id === 'onlineUsers' ? onlineWindow.end : undefined,
      interval: card.id === 'onlineUsers' ? 60 * 1000 : undefined,
      boundaryGap: false,
      data: card.id === 'onlineUsers' ? undefined : labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: gridLine } },
      splitLine: { show: false },
      axisLabel: {
        ...xAxisLabel,
        color: mutedColor,
      },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: axisBounds.max,
      interval: axisBounds.interval,
      minInterval: card.valueKind === 'number' ? 1 : undefined,
      axisLabel: {
        color: mutedColor,
        formatter: valueFormatter,
      },
      splitLine: { lineStyle: { color: gridLine } },
    },
    series: [
      {
        name: card.title,
        type: 'line',
        data:
          card.id === 'onlineUsers'
            ? onlineSeries.map((sample) => [sample.time, sample.value])
            : values,
        smooth: true,
        symbolSize: card.id === 'onlineUsers' ? 5 : 8,
        clip: card.id === 'onlineUsers' ? false : undefined,
        lineStyle: { width: card.id === 'onlineUsers' ? 2 : 3 },
        areaStyle: card.id === 'onlineUsers' ? undefined : { opacity: 0.16 },
        emphasis: { focus: 'series' },
      },
    ],
  }
}

function valueAxisBounds(
  card: DashboardChart,
  values: number[],
): { max?: number; interval?: number } {
  if (card.id === 'onlineUsers') {
    const max = niceAxisMax(Math.max(...values, overview.value?.onlineUsers ?? 0, 0))
    return { max, interval: max <= 10 ? 1 : undefined }
  }
  if (card.valueKind !== 'number') return {}
  return { max: niceAxisMax(Math.max(...values, 0)) }
}

function categoryAxisLabel(card: DashboardChart, labelCount: number): Record<string, unknown> {
  if (card.id === 'onlineUsers') {
    return {
      fontSize: 11,
      formatter: (value: number) => formatTimeShort24(value),
    }
  }

  if (period.value === 'day') {
    return {
      interval: Math.max(0, Math.ceil(labelCount / 8) - 1),
      fontSize: 10,
      rotate: 45,
      margin: 14,
      hideOverlap: true,
    }
  }

  if (period.value === 'month') {
    return {
      interval: 0,
      fontSize: 9,
      formatter: (_value: string, index: number) => String(index + 1),
      hideOverlap: false,
    }
  }

  return {
    interval: 0,
    fontSize: 12,
    hideOverlap: true,
  }
}

function onlineWindowBounds(): { start: number; end: number } {
  const latest = onlineSamples.value[onlineSamples.value.length - 1]?.capturedAt ?? Date.now()
  const end = startOfMinute(latest)
  return { start: end - (ONLINE_DISPLAY_BUCKETS - 1) * MINUTE_MS, end }
}

function onlineSeriesData(): { time: number; value: number | null; label: string }[] {
  const window = onlineWindowBounds()
  const samples = [...onlineSamples.value].sort((a, b) => a.capturedAt - b.capturedAt)
  let lastKnownValue: number | null = null

  return Array.from({ length: ONLINE_DISPLAY_BUCKETS }, (_, index) => {
    const bucketStart = window.start + index * MINUTE_MS
    const bucketEnd = bucketStart + MINUTE_MS
    const bucketSamples = samples.filter(
      (sample) => sample.capturedAt >= bucketStart && sample.capturedAt < bucketEnd,
    )
    const latestSample = bucketSamples[bucketSamples.length - 1]
    if (latestSample) {
      lastKnownValue = latestSample.value
    }

    return {
      time: bucketStart,
      value: lastKnownValue,
      label: latestSample ? formatTime24(latestSample.capturedAt) : formatTimeShort24(bucketStart),
    }
  })
}

function pieDataPoints(card: DashboardChart): AdminDistributionPoint[] {
  if (card.id === 'onlineUsers') {
    const grouped = new Map<number, number>()
    for (const sample of onlineSeriesData()) {
      if (sample.value === null) continue
      grouped.set(sample.value, (grouped.get(sample.value) ?? 0) + 1)
    }

    const points = Array.from(grouped.entries()).map(([onlineCount, sampleCount]) => ({
      label: t('admin.overview.charts.onlineUsers.onlineCount', {
        count: onlineCount,
        formattedCount: formatNumber(onlineCount),
      }),
      value: sampleCount,
    }))
    return points.length ? points : [{ label: t('admin.overview.charts.noData'), value: 1 }]
  }

  const piePoints = card.points.filter((point) => point.value > 0)
  return piePoints.length ? piePoints : [{ label: t('admin.overview.charts.noData'), value: 1 }]
}

function onlinePieLegendLabel(name: string, minutes: number): string {
  const percent = Math.round((minutes / ONLINE_DISPLAY_BUCKETS) * 100)
  return `${name} · ${t('admin.overview.charts.onlineUsers.minuteAbbreviation', { count: minutes })} · ${percent}%`
}

function chartRenderKey(card: DashboardChart): string {
  const onlineTail = onlineSamples.value[onlineSamples.value.length - 1]?.capturedAt ?? 0
  const realtimeKey =
    card.id === 'onlineUsers' ? `-${onlineSamples.value.length}-${onlineTail}` : ''
  return `${card.id}-${chartModes[card.id]}-${period.value}-${locale.value}${realtimeKey}`
}

function formatOnlineTooltip(
  params: unknown,
  series: { time: number; value: number | null; label: string }[],
): string {
  const item = Array.isArray(params) ? params[0] : params
  const payload = item as
    | {
        dataIndex?: number
        data?:
          | [number, number]
          | [number, number, number]
          | { name?: string; value?: number; minutes?: number; percent?: number }
        percent?: number
        name?: string
      }
    | undefined
  const data = payload?.data
  if (data && !Array.isArray(data)) {
    const minutes = data.minutes ?? Number(data.value ?? 0)
    const percent =
      data.percent ?? payload?.percent ?? Math.round((minutes / ONLINE_DISPLAY_BUCKETS) * 100)
    return `${data.name ?? payload?.name ?? t('admin.overview.charts.onlineUsers.title')}<br/><strong>${t('admin.overview.charts.onlineUsers.minuteAbbreviation', { count: minutes })}</strong> ${t('admin.overview.charts.onlineUsers.ofLastWindow', { minutes: ONLINE_DISPLAY_BUCKETS })}&nbsp;&nbsp;<strong>${percent}%</strong>`
  }
  const sample = typeof payload?.dataIndex === 'number' ? series[payload.dataIndex] : undefined
  const time = data?.[0] ?? sample?.time ?? Date.now()
  const value = data?.[2] ?? data?.[1] ?? sample?.value ?? 0
  const label = sample?.label ?? formatTime24(time)
  return `${label}<br/>${t('admin.overview.charts.onlineUsers.title')}&nbsp;&nbsp;<strong>${formatNumber(value)}</strong>`
}

function formatTime24(value: number): string {
  return new Date(value).toLocaleTimeString(locale.value, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

function formatTimeShort24(value: number): string {
  return new Date(value).toLocaleTimeString(locale.value, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function trendLabel(points: AdminDistributionPoint[]): string {
  if (points.length === 0) return t('admin.overview.trend.waitingForSamples')
  const total = sumPoints(points)
  if (total === 0) return t('admin.overview.trend.noNewUsers')
  return t('admin.overview.trend.newUsersInView', {
    count: total,
    formattedCount: formatNumber(total),
  })
}

function alertLabel(alert: AdminSecurityAlert): string {
  return [alert.type, alert.severity].filter(Boolean).join(' - ')
}

function subjectKey(item: AdminStorageSubject): string {
  return `${item.id}-${item.name}`
}
</script>

<template>
  <div class="dashboard-view">
    <div class="page-title">
      <div>
        <h2>{{ t('admin.overview.title') }}</h2>
        <p>{{ t('admin.overview.subtitle') }}</p>
      </div>
      <div class="poll-status" aria-live="polite">
        <span class="status-dot" :class="{ active: !errorMsg }"></span>
        {{
          errorMsg
            ? t('admin.overview.polling.unavailable')
            : t('admin.overview.polling.everySeconds', { seconds: 30 })
        }}
      </div>
    </div>

    <div v-if="errorMsg" class="notice error">{{ errorMsg }}</div>
    <div v-if="loading" class="notice">{{ t('admin.overview.loading') }}</div>

    <section
      v-if="overview"
      class="summary-grid"
      :aria-label="t('admin.overview.accessibility.summaryMetrics')"
    >
      <article class="summary-card">
        <span>{{ t('admin.overview.summary.totalUsers.title') }}</span>
        <strong>{{ formatNumber(overview.totalUsers) }}</strong>
        <small>{{ trendLabel(totalUserPoints) }}</small>
      </article>
      <article class="summary-card">
        <span>{{ t('admin.overview.summary.onlineUsers.title') }}</span>
        <strong class="text-success">{{ formatNumber(overview.onlineUsers) }}</strong>
        <small>{{ t('admin.overview.summary.onlineUsers.subtitle') }}</small>
      </article>
      <article class="summary-card">
        <span>{{ t('admin.overview.summary.importedRepositories.title') }}</span>
        <strong>{{ formatNumber(overview.totalProjects) }}</strong>
        <small>{{ t('admin.overview.summary.importedRepositories.subtitle') }}</small>
      </article>
      <article class="summary-card">
        <span>{{ t('admin.overview.summary.securityAlerts.title') }}</span>
        <strong>{{ formatNumber(securityAlerts.length || overview.blockedUsers) }}</strong>
        <small>{{ t('admin.overview.summary.securityAlerts.subtitle') }}</small>
      </article>
    </section>

    <section
      v-if="overview"
      class="analytics-section"
      :aria-label="t('admin.overview.accessibility.analyticsCharts')"
    >
      <div class="section-heading">
        <div>
          <h3>{{ t('admin.overview.analytics.title') }}</h3>
          <p>{{ t('admin.overview.analytics.subtitle') }}</p>
        </div>
        <div
          class="segmented"
          role="group"
          :aria-label="t('admin.overview.accessibility.chartAggregation')"
        >
          <button :class="{ active: period === 'day' }" type="button" @click="period = 'day'">
            {{ t('admin.overview.periods.day') }}
          </button>
          <button :class="{ active: period === 'month' }" type="button" @click="period = 'month'">
            {{ t('admin.overview.periods.month') }}
          </button>
          <button
            :class="{ active: period === 'quarter' }"
            type="button"
            @click="period = 'quarter'"
          >
            {{ t('admin.overview.periods.quarter') }}
          </button>
          <button :class="{ active: period === 'year' }" type="button" @click="period = 'year'">
            {{ t('admin.overview.periods.year') }}
          </button>
        </div>
      </div>

      <div class="chart-grid">
        <article
          v-for="card in dashboardCharts"
          :key="card.id"
          class="chart-card"
          :class="`chart-card--${card.tone}`"
        >
          <div class="chart-card__top">
            <div>
              <span class="chart-card__eyebrow">{{ card.eyebrow }}</span>
              <h4>{{ card.title }}</h4>
            </div>
            <div
              class="chart-switch"
              :aria-label="t('admin.overview.accessibility.chartType', { chartTitle: card.title })"
              role="group"
            >
              <button
                v-for="mode in ['line', 'bar', 'pie'] as ChartMode[]"
                :key="mode"
                type="button"
                :class="{ active: chartModes[card.id] === mode }"
                @click="chartModes[card.id] = mode"
              >
                {{ chartModeLabel(mode) }}
              </button>
            </div>
          </div>

          <div class="chart-card__metric">
            <strong>{{ card.value }}</strong>
            <span>{{ card.subtitle }}</span>
          </div>

          <div class="chart-box">
            <VChart
              v-if="card.points.length"
              :key="chartRenderKey(card)"
              class="echart"
              :option="chartOption(card)"
              :update-options="chartUpdateOptions"
              :autoresize="true"
            />
            <p v-else class="empty-state">{{ card.emptyText }}</p>
          </div>
        </article>
      </div>
    </section>

    <section
      v-if="overview"
      class="detail-grid"
      :aria-label="t('admin.overview.accessibility.supportingDetails')"
    >
      <article class="panel">
        <div class="panel-header">
          <h3>{{ t('admin.overview.details.topStorageProjects.title') }}</h3>
          <span>{{
            t('admin.overview.details.topStorageProjects.count', {
              count: topStorageProjects.length,
            })
          }}</span>
        </div>
        <p v-if="topStorageProjects.length === 0" class="empty-state">
          {{ t('admin.overview.details.topStorageProjects.empty') }}
        </p>
        <VChart
          v-else
          class="support-chart"
          :option="topStorageProjectChartOption"
          :update-options="chartUpdateOptions"
          :autoresize="true"
        />
      </article>

      <article class="panel">
        <div class="panel-header">
          <h3>{{ t('admin.overview.details.topStorageUsers.title') }}</h3>
          <span>{{
            t('admin.overview.details.topStorageUsers.count', {
              count: topStorageUsers.length,
            })
          }}</span>
        </div>
        <p v-if="topStorageUsers.length === 0" class="empty-state">
          {{ t('admin.overview.details.topStorageUsers.empty') }}
        </p>
        <div v-else class="compact-list">
          <div v-for="item in topStorageUsers" :key="subjectKey(item)" class="compact-row">
            <span>
              <strong>{{ item.name }}</strong>
              <small v-if="item.ownerEmail">{{ item.ownerEmail }}</small>
            </span>
            <b>{{ formatBytes(item.usedBytes) }}</b>
          </div>
        </div>
      </article>

      <article class="panel panel--wide" data-test="plan-distribution-panel">
        <div class="panel-header">
          <h3>{{ t('admin.overview.details.planDistribution.title') }}</h3>
          <span>{{
            t('admin.overview.details.planDistribution.count', {
              count: planDistribution.length,
            })
          }}</span>
        </div>
        <VChart
          v-if="planDistribution.length"
          class="support-chart support-chart--wide"
          :option="planDistributionChartOption"
          :update-options="chartUpdateOptions"
          :autoresize="true"
        />
        <p v-else class="empty-state">
          {{ t('admin.overview.details.planDistribution.empty') }}
        </p>
      </article>

      <article class="panel panel--wide" data-test="security-alerts-panel">
        <div class="panel-header">
          <h3>{{ t('admin.overview.details.securityAlerts.title') }}</h3>
          <span>{{
            t('admin.overview.details.securityAlerts.count', {
              count: securityAlerts.length || overview?.blockedUsers || 0,
            })
          }}</span>
        </div>
        <div v-if="securityAlerts.length" class="alerts alerts--wide">
          <div v-for="alert in securityAlerts" :key="alert.id || alert.summary" class="alert-row">
            <span>{{ alertLabel(alert) }}</span>
            <strong>{{ alert.summary }}</strong>
            <small>{{
              alert.createdAt
                ? new Date(alert.createdAt).toLocaleString(locale)
                : t('admin.overview.details.securityAlerts.noTimestamp')
            }}</small>
          </div>
        </div>
        <p v-else class="empty-state">
          {{
            t('admin.overview.details.securityAlerts.empty', {
              count: overview?.blockedUsers ?? 0,
              formattedCount: formatNumber(overview?.blockedUsers),
            })
          }}
        </p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-6);
}

.page-title,
.section-heading,
.panel-header,
.chart-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--vg-space-4);
}

.page-title__eyebrow,
.chart-card__eyebrow {
  display: block;
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-title h2,
.section-heading h3,
.panel h3,
.chart-card h4 {
  margin: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  letter-spacing: 0;
}

.page-title h2 {
  margin-top: 0;
}

.page-title p,
.section-heading p {
  margin: var(--vg-space-1) 0 0;
  color: var(--vg-text-muted);
}

.poll-status,
.panel-header span {
  color: var(--vg-text-dim);
  font-size: var(--vg-text-sm);
  font-weight: 700;
  white-space: nowrap;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  margin-right: 6px;
  background: var(--vg-danger);
}

.status-dot.active {
  background: var(--vg-green-bright);
}

.notice,
.panel,
.summary-card,
.chart-card,
.analytics-section {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
}

.notice {
  padding: var(--vg-space-4);
  color: var(--vg-text-muted);
}

.notice.error {
  color: var(--vg-danger);
  border-color: rgba(239, 68, 68, 0.4);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--vg-space-3);
}

.summary-card {
  min-width: 0;
  padding: var(--vg-space-4);
}

.summary-card span,
.summary-card small {
  display: block;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}

.summary-card strong {
  display: block;
  margin: var(--vg-space-2) 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  font-size: 2rem;
}

.text-success {
  color: var(--vg-green-bright) !important;
}

.analytics-section {
  padding: var(--vg-space-4);
}

.section-heading {
  align-items: center;
  margin-bottom: var(--vg-space-4);
}

.segmented,
.chart-switch {
  display: inline-flex;
  min-height: 2.5rem;
  padding: 3px;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: rgba(7, 11, 22, 0.44);
}

.segmented button,
.chart-switch button {
  min-width: 3.65rem;
  border: 0;
  border-radius: calc(var(--vg-radius-sm) - 3px);
  background: transparent;
  color: var(--vg-text-muted);
  padding: var(--vg-space-2) var(--vg-space-3);
  cursor: pointer;
  font: inherit;
  font-size: var(--vg-text-sm);
  font-weight: 800;
  text-transform: capitalize;
}

.segmented button.active,
.chart-switch button.active {
  background: rgba(59, 130, 246, 0.18);
  color: var(--vg-blue-bright);
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-4);
}

.chart-card {
  min-width: 0;
  padding: var(--vg-space-4);
  overflow: hidden;
}

.chart-card__top {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
}

.chart-card__top > div:first-child {
  min-width: 0;
}

.chart-card h4 {
  margin-top: var(--vg-space-1);
  max-width: 100%;
  font-size: clamp(1.25rem, 1.6vw, 1.55rem);
  line-height: 1.15;
  overflow-wrap: normal;
}

.chart-card__metric {
  display: flex;
  align-items: baseline;
  gap: var(--vg-space-3);
  flex-wrap: wrap;
  margin: var(--vg-space-4) 0;
}

.chart-card__metric strong {
  flex: 0 0 auto;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  font-size: clamp(1.65rem, 2vw, 2.1rem);
  line-height: 1;
}

.chart-card__metric span {
  min-width: 12rem;
  flex: 1 1 12rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.35;
}

.chart-box {
  min-height: 17rem;
  padding: var(--vg-space-2);
  background: var(--vg-bg);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
}

.echart {
  width: 100%;
  height: 16rem;
}

.support-chart {
  width: 100%;
  height: 15rem;
}

.support-chart--wide {
  height: 14rem;
}

.chart-card--blue {
  --chart-color: #60a5fa;
}

.chart-card--green {
  --chart-color: #4ade80;
}

.chart-card--cyan {
  --chart-color: #22d3ee;
}

.chart-card--amber {
  --chart-color: #f59e0b;
}

.mini-bars,
.alerts,
.compact-list {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
}

.mini-bars--inline {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  align-items: stretch;
}

.bar-item {
  display: grid;
  grid-template-columns: minmax(5rem, 9rem) minmax(7rem, 1fr) minmax(4rem, 7rem);
  align-items: center;
  gap: var(--vg-space-3);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

.mini-bars--inline .bar-item {
  grid-template-columns: 1fr;
  align-content: start;
  gap: var(--vg-space-2);
  min-height: 6.25rem;
  padding: var(--vg-space-3);
  background: var(--vg-bg);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
}

.bar-item span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-item strong {
  color: var(--vg-text);
  text-align: right;
  white-space: nowrap;
}

.mini-bars--inline .bar-item strong {
  text-align: left;
  font-size: var(--vg-text-xl);
}

.bar-track {
  height: 0.85rem;
  background: rgba(15, 23, 42, 0.85);
  border-radius: 999px;
  overflow: hidden;
}

.bar-track div {
  height: 100%;
  background: var(--vg-blue);
  border-radius: 999px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-4);
}

.panel {
  min-width: 0;
  padding: var(--vg-space-4);
}

.panel--wide {
  grid-column: 1 / -1;
}

.panel-header {
  margin-bottom: var(--vg-space-4);
}

.empty-state {
  margin: 0;
  color: var(--vg-text-muted);
}

.alert-row,
.compact-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--vg-space-4);
  padding: var(--vg-space-3);
  background: var(--vg-bg);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
}

.alert-row {
  display: grid;
  grid-template-columns: minmax(8rem, 12rem) minmax(0, 1fr) minmax(8rem, 12rem);
}

.alerts--wide .alert-row {
  grid-template-columns: minmax(11rem, 14rem) minmax(18rem, 1fr) minmax(11rem, 14rem);
  align-items: center;
}

.alert-row span,
.alert-row small,
.compact-row small {
  color: var(--vg-text-muted);
}

.compact-row span {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.compact-row strong,
.compact-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-row b {
  white-space: nowrap;
}

.alert-row strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 1180px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .page-title,
  .section-heading,
  .chart-card__top,
  .chart-card__metric {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .chart-card__top {
    display: flex;
  }

  .chart-card__metric span {
    min-width: 0;
    flex: none;
  }

  .segmented,
  .chart-switch {
    width: 100%;
  }

  .segmented button,
  .chart-switch button {
    flex: 1;
    min-width: 0;
  }

  .bar-item,
  .alert-row {
    grid-template-columns: 1fr;
  }

  .alerts--wide .alert-row {
    grid-template-columns: 1fr;
  }

  .bar-item strong {
    text-align: left;
  }
}
</style>
