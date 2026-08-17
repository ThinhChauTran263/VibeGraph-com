/**
 * F-M6 split (DashboardView, step 2): the echarts registration + VChart component,
 * isolated in their own module so DashboardView can load this via defineAsyncComponent.
 * Effect: the echarts bundle (~500 kB) leaves the DashboardView route chunk and becomes
 * a separately cached async chunk, loaded only when the dashboard actually renders.
 * `use([...])` runs at module load, i.e. before any chart renders.
 */
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
])

export default VChart
