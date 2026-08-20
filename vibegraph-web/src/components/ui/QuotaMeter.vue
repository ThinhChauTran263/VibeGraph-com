<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatFileSize } from '@/lib/archiveUpload'

const { t } = useI18n({ useScope: 'global' })
const props = defineProps<{
  usedBytes: number
  totalBytes: number
}>()

const remainingBytes = computed(() => Math.max(0, props.totalBytes - props.usedBytes))
const percentage = computed(() => {
  if (props.totalBytes === 0) return 0
  return Math.min(100, (props.usedBytes / props.totalBytes) * 100)
})
</script>

<template>
  <div class="quota-meter">
    <div class="quota-info">
      <span class="quota-used"
        >{{ formatFileSize(usedBytes) }} / {{ formatFileSize(totalBytes) }}
        {{ t('user.quota.used') }}</span
      >
      <span class="quota-remaining">{{ formatFileSize(remainingBytes) }}
        {{ t('user.quota.remaining') }}</span
      >
    </div>
    <div
      v-if="totalBytes > 0"
      class="progress-track"
      role="progressbar"
      :aria-label="t('user.usage.sourceStorage')"
      aria-valuemin="0"
      :aria-valuemax="totalBytes"
      :aria-valuenow="Math.min(usedBytes, totalBytes)"
      :aria-valuetext="`${formatFileSize(usedBytes)} ${t('user.quota.used')}`"
    >
      <div
        class="progress-fill"
        :class="{ 'progress-warning': percentage >= 80, 'progress-danger': percentage >= 100 }"
        :style="{ width: `${percentage}%` }"
      ></div>
    </div>
    <p v-else class="quota-unavailable" role="status">{{ t('user.quota.unavailable') }}</p>
  </div>
</template>

<style scoped>
.quota-meter {
  width: 100%;
  min-width: 0;
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
