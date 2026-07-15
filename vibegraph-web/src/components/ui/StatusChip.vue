<script setup lang="ts">
defineProps<{
  status: string
  label: string
}>()
</script>

<template>
  <span :class="['status-chip', `status-${status}`]">
    {{ label }}
  </span>
</template>

<style scoped>
.status-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.25rem 0.75rem;
  border-radius: var(--vg-radius-pill);
  font-size: var(--vg-text-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  min-width: 80px;
  white-space: nowrap;
}

/* Base statuses */
.status-active,
.status-success,
.status-open {
  background-color: rgba(34, 197, 94, 0.15);
  color: var(--vg-green-bright);
  border: 1px solid rgba(34, 197, 94, 0.25);
}
.status-inactive,
.status-disabled {
  background-color: rgba(148, 163, 184, 0.12);
  color: var(--vg-text-dim);
  border: 1px solid var(--vg-border);
}
.status-pending {
  background-color: rgba(251, 191, 36, 0.15);
  color: var(--vg-amber);
  border: 1px solid rgba(251, 191, 36, 0.25);
}
.status-error,
.status-blocked,
.status-failed {
  background-color: rgba(239, 68, 68, 0.15);
  color: var(--vg-danger);
  border: 1px solid rgba(239, 68, 68, 0.25);
}
.status-closed {
  background-color: rgba(167, 139, 250, 0.15);
  color: var(--vg-violet);
  border: 1px solid rgba(167, 139, 250, 0.25);
}
</style>
