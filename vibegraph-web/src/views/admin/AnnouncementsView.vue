<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAdminStore } from '@/stores/admin'
import type { AdminAnnouncementRequest } from '@/types/api'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'

const adminStore = useAdminStore()
const loading = ref(true)
const saving = ref(false)
const errorMsg = ref('')
const pendingDisableId = ref<string | null>(null)
const pendingDeleteId = ref<string | null>(null)
const editingId = ref<string | null>(null)

const typeOptions = [
  { value: 'MAINTENANCE', label: 'Maintenance' },
  { value: 'PLAN_CHANGE', label: 'Plan changes' },
  { value: 'DISK_WARNING', label: 'Disk warnings' },
  { value: 'CLI_UPDATE', label: 'CLI updates' },
  { value: 'SECURITY', label: 'Security notices' },
  { value: 'GENERAL', label: 'General notices' },
] as const

const severityOptions = [
  { value: 'INFO', label: 'Info' },
  { value: 'WARNING', label: 'Warning' },
  { value: 'CRITICAL', label: 'Critical' },
] as const

const targetOptions = [
  { value: 'ALL', label: 'All users' },
  { value: 'USER', label: 'Users only' },
  { value: 'ADMIN', label: 'Admins only' },
] as const

const form = ref<AdminAnnouncementRequest>({
  type: 'GENERAL',
  severity: 'INFO',
  target: 'ALL',
  title: '',
  body: '',
  startsAt: null,
  endsAt: null,
  dismissible: true,
  active: true,
})

onMounted(loadAnnouncements)

function resetForm(): void {
  form.value = {
    type: 'GENERAL',
    severity: 'INFO',
    target: 'ALL',
    title: '',
    body: '',
    startsAt: null,
    endsAt: null,
    dismissible: true,
    active: true,
  }
  editingId.value = null
}

async function loadAnnouncements(): Promise<void> {
  try {
    await adminStore.fetchAnnouncements()
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to load announcements.'
  } finally {
    loading.value = false
  }
}

async function submitAnnouncement(): Promise<void> {
  const title = form.value.title.trim()
  const body = form.value.body.trim()
  if (!title || !body) {
    errorMsg.value = 'Title and message are required.'
    return
  }
  const duplicate = adminStore.announcements.some(
    (item) => item.title.trim().toLowerCase() === title.toLowerCase() && item.id !== editingId.value,
  )
  if (duplicate) {
    errorMsg.value = 'An announcement with this title already exists.'
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      title,
      body,
      startsAt: toInstant(form.value.startsAt),
      endsAt: toInstant(form.value.endsAt),
    }
    if (editingId.value) await adminStore.updateAnnouncement(editingId.value, payload)
    else await adminStore.createAnnouncement(payload)
    resetForm()
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to save announcement.'
  } finally {
    saving.value = false
  }
}

function editAnnouncement(id: string): void {
  const item = adminStore.announcements.find((announcement) => announcement.id === id)
  if (!item) return
  editingId.value = id
  form.value = {
    type: typeOptions.some((option) => option.value === item.type)
      ? (item.type as (typeof typeOptions)[number]['value'])
      : 'GENERAL',
    severity: severityOptions.some((option) => option.value === item.severity)
      ? (item.severity as (typeof severityOptions)[number]['value'])
      : 'INFO',
    target: targetOptions.some((option) => option.value === item.target)
      ? (item.target as (typeof targetOptions)[number]['value'])
      : 'ALL',
    title: item.title,
    body: item.body,
    startsAt: toDateTimeLocal(item.startsAt),
    endsAt: toDateTimeLocal(item.endsAt),
    dismissible: item.dismissible,
    active: item.active,
  }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function disablePending(): Promise<void> {
  if (!pendingDisableId.value) return
  saving.value = true
  try {
    await adminStore.disableAnnouncement(pendingDisableId.value)
    pendingDisableId.value = null
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to disable announcement.'
  } finally {
    saving.value = false
  }
}

async function deletePending(): Promise<void> {
  if (!pendingDeleteId.value) return
  saving.value = true
  try {
    await adminStore.deleteAnnouncement(pendingDeleteId.value)
    pendingDeleteId.value = null
    if (editingId.value) resetForm()
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to delete announcement.'
  } finally {
    saving.value = false
  }
}

function typeLabel(value: string): string {
  return typeOptions.find((option) => option.value === value)?.label ?? value
}

function toInstant(value: string | null | undefined): string | null {
  if (!value) return null
  return new Date(value).toISOString()
}

function toDateTimeLocal(value: string | null | undefined): string | null {
  if (!value) return null
  const date = new Date(value)
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}
</script>

<template>
  <div class="admin-page">
    <div class="page-title">
      <div>
        <h2>Announcements</h2>
        <p>
          Manage in-app notices for maintenance, plan changes, storage warnings, and CLI updates.
        </p>
      </div>
      <span v-if="errorMsg" class="api-state unavailable">API error</span>
    </div>

    <div v-if="errorMsg" class="notice error">{{ errorMsg }}</div>
    <div v-if="loading" class="notice">Loading announcements...</div>

    <section class="panel composer-panel" :aria-label="editingId ? 'Edit announcement' : 'Create announcement'">
      <form class="composer" @submit.prevent="submitAnnouncement">
        <label class="field">
          <span>Type</span>
          <select id="announcement-type" v-model="form.type" name="announcementType">
            <option v-for="option in typeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>Severity</span>
          <select id="announcement-severity" v-model="form.severity" name="announcementSeverity">
            <option v-for="option in severityOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>Target</span>
          <select id="announcement-target" v-model="form.target" name="announcementTarget">
            <option v-for="option in targetOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="field title-field">
          <span>Title</span>
          <input
            id="announcement-title"
            v-model="form.title"
            name="announcementTitle"
            required
            maxlength="160"
            placeholder="Maintenance window"
          />
        </label>
        <label class="field full">
          <span>Message</span>
          <textarea
            id="announcement-body"
            v-model="form.body"
            name="announcementBody"
            required
            rows="4"
            maxlength="2000"
            placeholder="Write a plain-text announcement"
          ></textarea>
        </label>
        <label class="field schedule-field">
          <span>Starts at</span>
          <input
            id="announcement-starts-at"
            v-model="form.startsAt"
            name="announcementStartsAt"
            type="datetime-local"
          />
        </label>
        <label class="field schedule-field">
          <span>Ends at</span>
          <input
            id="announcement-ends-at"
            v-model="form.endsAt"
            name="announcementEndsAt"
            type="datetime-local"
          />
        </label>
        <div class="form-actions">
          <div class="toggle-options">
            <label class="compact-switch" :class="{ active: form.dismissible }">
              <input
                id="announcement-dismissible"
                v-model="form.dismissible"
                name="announcementDismissible"
                type="checkbox"
              />
              <span class="toggle-track" aria-hidden="true"><span></span></span>
              <strong>Dismissible</strong>
            </label>
            <label class="compact-switch" :class="{ active: form.active }">
              <input
                id="announcement-active"
                v-model="form.active"
                name="announcementActive"
                type="checkbox"
              />
              <span class="toggle-track" aria-hidden="true"><span></span></span>
              <strong>Active</strong>
            </label>
          </div>
          <button type="submit" :disabled="saving">
            {{ saving ? 'Saving...' : editingId ? 'Save changes' : 'Create notice' }}
          </button>
          <button v-if="editingId" type="button" class="secondary-action" :disabled="saving" @click="resetForm">
            Cancel edit
          </button>
        </div>
      </form>
    </section>

    <section class="panel">
      <h3>Published notices</h3>
      <p v-if="adminStore.announcements.length === 0" class="empty-state">No announcements yet.</p>
      <div v-else class="announcement-list">
        <article v-for="item in adminStore.announcements" :key="item.id" class="announcement-row">
          <div class="announcement-copy">
            <div class="announcement-meta">
              <span class="severity" :class="item.severity.toLowerCase()">{{ item.severity }}</span>
              <span>{{ typeLabel(item.type) }}</span>
              <span>{{ item.target }}</span>
            </div>
            <strong class="announcement-title">{{ item.title }}</strong>
            <p>{{ item.body }}</p>
            <small>
              {{
                item.startsAt
                  ? `Starts ${new Date(item.startsAt).toLocaleString()}`
                  : 'Starts immediately'
              }}
              <span aria-hidden="true"> / </span>
              {{ item.endsAt ? `Ends ${new Date(item.endsAt).toLocaleString()}` : 'No end date' }}
            </small>
          </div>
          <div class="row-actions">
            <button type="button" class="secondary-action" @click="editAnnouncement(item.id)">Edit</button>
            <button v-if="item.active" type="button" @click="pendingDisableId = item.id">Disable</button>
            <span v-else class="status-chip">Disabled</span>
            <button type="button" class="delete-action" @click="pendingDeleteId = item.id">Delete</button>
          </div>
        </article>
      </div>
    </section>

    <AdminConfirmDialog
      :open="Boolean(pendingDisableId)"
      title="Disable announcement"
      message="This announcement will stop showing in the app for its target audience."
      confirm-label="Disable"
      tone="danger"
      :busy="saving"
      @cancel="pendingDisableId = null"
      @confirm="disablePending"
    />
    <AdminConfirmDialog
      :open="Boolean(pendingDeleteId)"
      title="Delete announcement"
      message="Delete this announcement permanently? Existing user notification history may no longer be available."
      confirm-label="Delete announcement"
      tone="danger"
      :busy="saving"
      @cancel="pendingDeleteId = null"
      @confirm="deletePending"
    />
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}

.page-title,
.panel-header,
.announcement-row {
  display: flex;
  justify-content: space-between;
  gap: var(--vg-space-4);
}

h2,
h3 {
  margin: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  letter-spacing: 0;
}

p,
.empty-state,
small {
  color: var(--vg-text-muted);
}

.panel-header p {
  margin: var(--vg-space-1) 0 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

.api-state {
  color: var(--vg-green-bright);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}

.api-state.unavailable,
.notice.error {
  color: var(--vg-danger);
}

.notice,
.panel {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  padding: var(--vg-space-4);
}

.composer {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--vg-space-3);
}

.field {
  grid-column: span 3;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.composer .full {
  grid-column: 1 / -1;
}

.title-field {
  grid-column: span 3;
}

.schedule-field {
  grid-column: span 3;
}

.form-actions {
  grid-column: span 6;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--vg-space-3);
  align-self: end;
}

.toggle-options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--vg-space-3);
  align-items: center;
  min-height: 2.75rem;
}

.compact-switch {
  position: relative;
  min-height: 2.5rem;
  display: inline-grid;
  grid-template-columns: 1.9rem minmax(0, auto);
  align-items: center;
  gap: var(--vg-space-2);
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-text);
  cursor: pointer;
  white-space: nowrap;
}

.compact-switch input {
  position: absolute;
  inset: 0;
  width: 100%;
  min-height: 0;
  height: 100%;
  margin: 0;
  opacity: 0;
  pointer-events: none;
}

.compact-switch strong {
  display: block;
  font-size: var(--vg-text-xs);
  line-height: 1;
}

.compact-switch.active {
  border-color: transparent;
  background: transparent;
}

.toggle-track {
  width: 1.78rem;
  height: 1rem;
  display: inline-flex;
  align-items: center;
  padding: 0.12rem;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.28);
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out);
}

.toggle-track span {
  width: 0.76rem;
  height: 0.76rem;
  border-radius: 999px;
  background: white;
  transform: translateX(0);
  transition: transform var(--vg-dur-fast) var(--vg-ease-out);
}

.compact-switch.active .toggle-track {
  background: var(--vg-blue);
}

.compact-switch.active .toggle-track span {
  transform: translateX(0.78rem);
}

input:not([type='checkbox']),
select,
textarea {
  width: 100%;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg);
  color: var(--vg-text);
  padding: var(--vg-space-3);
  font: inherit;
  letter-spacing: 0;
  text-transform: none;
}

select {
  min-height: 2.75rem;
  appearance: none;
  padding-right: 2.6rem;
  background-image:
    linear-gradient(45deg, transparent 50%, var(--vg-text-muted) 50%),
    linear-gradient(135deg, var(--vg-text-muted) 50%, transparent 50%);
  background-position:
    calc(100% - 1.1rem) 50%,
    calc(100% - 0.78rem) 50%;
  background-repeat: no-repeat;
  background-size:
    0.38rem 0.38rem,
    0.38rem 0.38rem;
}

input:focus,
select:focus,
textarea:focus {
  outline: none;
  border-color: var(--vg-blue);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.16);
}

button {
  align-self: center;
  border: 1px solid var(--vg-blue);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-blue);
  color: white;
  padding: var(--vg-space-2) var(--vg-space-3);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.form-actions button {
  width: 11.5rem;
  min-height: 2.75rem;
}
.secondary-action {
  border-color: var(--vg-border);
  background: transparent;
  color: var(--vg-text);
}
.delete-action {
  border-color: color-mix(in srgb, var(--vg-danger) 45%, var(--vg-border));
  background: color-mix(in srgb, var(--vg-danger) 10%, transparent);
  color: var(--vg-danger);
}
.row-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--vg-space-2);
}
.row-actions button,
.row-actions .status-chip {
  min-height: 2.25rem;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
  margin-top: var(--vg-space-4);
}

.announcement-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  background: rgba(2, 6, 23, 0.3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  padding: var(--vg-space-3);
}

.announcement-copy {
  min-width: 0;
}

.announcement-title {
  display: block;
  color: var(--vg-text);
  font-size: var(--vg-text-lg);
  line-height: 1.25;
}

.announcement-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--vg-space-2);
  margin-bottom: var(--vg-space-2);
}

.announcement-meta span {
  min-height: 1.5rem;
  display: inline-flex;
  align-items: center;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 999px;
  padding: 0 var(--vg-space-2);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 800;
}

.announcement-meta .severity.info {
  border-color: rgba(96, 165, 250, 0.28);
  color: var(--vg-blue-bright);
}

.announcement-meta .severity.warning {
  border-color: rgba(245, 158, 11, 0.3);
  color: var(--vg-warning, #f59e0b);
}

.announcement-meta .severity.critical {
  border-color: rgba(239, 68, 68, 0.34);
  color: var(--vg-danger);
}

.announcement-row p {
  margin: var(--vg-space-2) 0 var(--vg-space-1);
  line-height: 1.5;
}

.status-chip {
  min-width: 6rem;
  min-height: 2.25rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.08);
  color: var(--vg-text-muted);
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .composer {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .field,
  .title-field,
  .schedule-field {
    grid-column: span 1;
  }

  .composer .full,
  .form-actions {
    grid-column: 1 / -1;
  }

  .form-actions {
    grid-template-columns: minmax(0, 1fr) auto auto;
  }

  .toggle-options {
    grid-template-columns: repeat(2, minmax(10rem, 1fr));
    grid-column: span 1;
  }
}

@media (max-width: 760px) {
  .page-title,
  .panel-header,
  .announcement-row {
    flex-direction: column;
  }
  .announcement-row {
    grid-template-columns: 1fr;
  }

  .composer {
    grid-template-columns: 1fr;
  }

  .field,
  .title-field {
    grid-column: auto;
  }

  .form-actions {
    align-items: stretch;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .toggle-options {
    grid-template-columns: 1fr;
    grid-column: auto;
  }

  .form-actions button {
    width: 100%;
  }
  .row-actions {
    justify-content: flex-start;
  }
}
</style>
