<script setup lang="ts">
import { onMounted } from 'vue'
import { useAccountStore } from '@/stores/account'
import QuotaMeter from '@/components/ui/QuotaMeter.vue'

const accountStore = useAccountStore()

onMounted(async () => {
  if (!accountStore.usage) {
    await accountStore.fetchUsage()
  }
})
</script>

<template>
  <div class="usage-view">
    <h2>Usage & Plan</h2>
    
    <div v-if="accountStore.usage" class="usage-card">
      <div class="plan-header">
        <h3>{{ accountStore.usage.planName }}</h3>
      </div>
      
      <div class="quota-section">
        <h4>Source Storage</h4>
        <QuotaMeter 
          :used="accountStore.usage.sourceStorageUsed" 
          :total="accountStore.usage.sourceStorageLimit" 
          unit="MB" 
        />
      </div>

      <div class="quota-section">
        <h4>Credits</h4>
        <QuotaMeter 
          :used="accountStore.usage.creditsUsed" 
          :total="accountStore.usage.creditsLimit" 
          unit="" 
        />
      </div>
    </div>
    <div v-else class="loading">
      Loading usage data...
    </div>
  </div>
</template>

<style scoped>
.usage-view {
  max-width: 800px;
  margin: 0 auto;
}
.usage-card {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  box-shadow: var(--vg-shadow-sm);
  padding: 2rem;
  margin-top: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}
.plan-header h3 {
  margin: 0;
  color: var(--vg-blue-bright);
  font-family: var(--vg-font-display);
}
.quota-section h4 {
  margin: 0 0 1rem 0;
  color: var(--vg-text-muted);
  font-size: 1rem;
}
.loading {
  color: var(--vg-text-dim);
}
</style>

