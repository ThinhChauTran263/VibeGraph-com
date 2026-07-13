<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  used: number
  total: number
  unit: string
}>()

const remaining = computed(() => Math.max(0, props.total - props.used))
const percentage = computed(() => {
  if (props.total === 0) return 0
  return Math.min(100, (props.used / props.total) * 100)
})
</script>

<template>
  <div class="quota-meter">
    <div class="quota-info">
      <span class="quota-used">{{ used }}{{ unit }} / {{ total }}{{ unit }} used</span>
      <span class="quota-remaining">{{ remaining }}{{ unit }} remaining</span>
    </div>
    <div class="progress-track">
      <div 
        class="progress-fill" 
        :class="{ 'progress-warning': percentage >= 80, 'progress-danger': percentage >= 100 }"
        :style="{ width: `${percentage}%` }"
      ></div>
    </div>
  </div>
</template>

<style scoped>
.quota-meter {
  min-width: 250px;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
  font-family: var(--vg-font-body);
}
.quota-info {
  display: flex;
  justify-content: space-between;
  font-size: var(--vg-text-sm);
}
.quota-used {
  color: var(--vg-text);
  font-weight: 500;
}
.quota-remaining {
  color: var(--vg-text-muted);
}
.progress-track {
  height: 8px;
  background-color: var(--vg-surface-3);
  border-radius: var(--vg-radius-pill);
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--vg-grad-blue);
  border-radius: var(--vg-radius-pill);
  transition: width var(--vg-dur) var(--vg-ease-out);
}
.progress-warning {
  background: linear-gradient(135deg, var(--vg-amber), #f59e0b);
}
.progress-danger {
  background: linear-gradient(135deg, var(--vg-danger), #dc2626);
}
</style>
