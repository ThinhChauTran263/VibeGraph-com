<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CliAuthorizationForm from '@/components/auth/CliAuthorizationForm.vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import { ApiError, type Project } from '@/lib/api'
import { cliAuthorizationApi } from '@/lib/cliAuthorization'
import { useAuthStore } from '@/stores/auth'
import type { ApiKey } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const requestId = typeof route.query.request === 'string' ? route.query.request : ''
const secretKey = `vibegraph.cli.authorization.${requestId}`
const projects = ref<Project[]>([])
const selectedProjectId = ref('')
const mode = ref<'KEY' | 'EXISTING' | 'NEW'>('KEY')
const apiKeys = ref<ApiKey[]>([])
const projectName = ref('')
const loading = ref(true)
const accountConfirmed = ref(false)
const approving = ref(false)
const completedProject = ref('')
const error = ref('')
const authorizationPath = route.fullPath.split('#')[0] ?? route.path
const preferredApiKeyId = typeof route.query.key === 'string' ? route.query.key : ''
let refreshTimer: ReturnType<typeof setInterval> | undefined

const apiKeyOptions = computed(() =>
  apiKeys.value.length
    ? apiKeys.value.map((key) => ({
        value: key.id,
        label: `${key.keyPrefix}  /  ${key.project?.name || key.name}`,
      }))
    : [{ value: '', label: 'No active revealable keys' }],
)

const projectOptions = computed(() =>
  projects.value.length
    ? projects.value.map((project) => ({ value: project.id, label: project.name }))
    : [{ value: '', label: 'No projects yet' }],
)

const accountInitial = computed(() => {
  const value = auth.user?.displayName || auth.user?.email || 'V'
  return value.trim().charAt(0).toUpperCase()
})

const canApprove = computed(() => {
  if (approving.value || completedProject.value) return false
  if (mode.value === 'KEY') return Boolean(selectedApiKeyId.value)
  return mode.value === 'EXISTING'
    ? Boolean(selectedProjectId.value)
    : Boolean(projectName.value.trim())
})

const selectedApiKeyId = ref('')

onMounted(async () => {
  captureBrowserSecret()
  if (!requestId || !sessionStorage.getItem(secretKey)) {
    error.value = 'This CLI authorization link is incomplete or no longer available.'
    loading.value = false
    return
  }
  sessionStorage.setItem('vibegraph.cli.pendingRoute', authorizationPath)
  await auth.fetchCurrentUser()
  if (!auth.user) {
    await router.replace({ name: 'login', query: { redirect: authorizationPath } })
    return
  }
  loading.value = false
})

async function continueWithAccount(): Promise<void> {
  if (!auth.user || accountConfirmed.value) return
  loading.value = true
  error.value = ''
  try {
    const [availableProjects, availableKeys] = await Promise.all([
      cliAuthorizationApi.projects(),
      cliAuthorizationApi.keys(),
    ])
    projects.value = availableProjects
    applyKeys(availableKeys)
    selectedProjectId.value = projects.value[0]?.id ?? ''
    if (!apiKeys.value.length) mode.value = projects.value.length ? 'EXISTING' : 'NEW'
    accountConfirmed.value = true
    startKeyRefresh()
  } catch (cause) {
    error.value = apiMessage(cause)
  } finally {
    loading.value = false
  }
}

async function useAnotherAccount(): Promise<void> {
  await auth.logout()
  await router.replace({ name: 'login', query: { redirect: authorizationPath } })
}

function startKeyRefresh(): void {
  refreshTimer = setInterval(() => {
    void refreshKeys()
  }, 7000)
  window.addEventListener('focus', refreshKeys)
  document.addEventListener('visibilitychange', refreshOnVisible)
}

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  window.removeEventListener('focus', refreshKeys)
  document.removeEventListener('visibilitychange', refreshOnVisible)
})

async function refreshKeys(): Promise<void> {
  if (completedProject.value || document.visibilityState === 'hidden') return
  try {
    applyKeys(await cliAuthorizationApi.keys())
  } catch (cause) {
    error.value = apiMessage(cause)
  }
}

function refreshOnVisible(): void {
  if (document.visibilityState === 'visible') void refreshKeys()
}

function applyKeys(availableKeys: ApiKey[]): void {
  const now = Date.now()
  const next = availableKeys.filter((key) => {
    const expiresAt = key.expiresAt ? Date.parse(key.expiresAt) : Number.POSITIVE_INFINITY
    return (
      key.disabledAt == null &&
      key.deletedAt == null &&
      key.disabled !== true &&
      key.revealable === true &&
      key.project != null &&
      (!Number.isFinite(expiresAt) || expiresAt > now)
    )
  })
  const previous = selectedApiKeyId.value
  apiKeys.value = next
  if (previous && !next.some((key) => key.id === previous)) {
    selectedApiKeyId.value = next[0]?.id ?? ''
    if (previous === preferredApiKeyId || previous) {
      error.value =
        'The selected API key was deleted, rotated, disabled, or expired. Choose another key.'
    }
  } else if (!previous) {
    const preferred = next.find((key) => key.id === preferredApiKeyId)
    selectedApiKeyId.value = preferred?.id ?? next[0]?.id ?? ''
    if (preferredApiKeyId && !preferred) {
      error.value =
        'The previously selected API key was deleted, rotated, disabled, or expired. Choose another key.'
    }
  }
}

function captureBrowserSecret(): void {
  const fragment = new URLSearchParams(window.location.hash.slice(1))
  const secret = fragment.get('secret')
  if (requestId && secret) sessionStorage.setItem(secretKey, secret)
  if (window.location.hash) history.replaceState(history.state, '', authorizationPath)
}

async function approve(): Promise<void> {
  const browserSecret = sessionStorage.getItem(secretKey)
  if (!browserSecret || !canApprove.value) return
  approving.value = true
  error.value = ''
  try {
    const result = await cliAuthorizationApi.approve(requestId, {
      browserSecret,
      projectMode: mode.value,
      ...(mode.value === 'KEY'
        ? { apiKeyId: selectedApiKeyId.value }
        : mode.value === 'EXISTING'
          ? { projectId: selectedProjectId.value }
          : { projectName: projectName.value.trim() }),
    })
    completedProject.value = result.projectName || result.projectId
    clearPendingAuthorization()
  } catch (cause) {
    error.value = apiMessage(cause)
  } finally {
    approving.value = false
  }
}

function clearPendingAuthorization(): void {
  sessionStorage.removeItem(secretKey)
  sessionStorage.removeItem('vibegraph.cli.pendingRoute')
}

function apiMessage(cause: unknown): string {
  return cause instanceof ApiError
    ? cause.message
    : 'VibeGraph could not authorize this CLI request.'
}
</script>

<template>
  <main class="cli-auth">
    <section class="cli-auth__card" aria-labelledby="cli-auth-title">
      <BrandMark
        :size="38"
        :show-wordmark="true"
        glyph-to="/"
        glyph-aria-label="VibeGraph landing page"
        wordmark-to="/dashboard"
        wordmark-aria-label="VibeGraph dashboard"
      />
      <div class="cli-auth__eyebrow">Secure device authorization</div>
      <h1 id="cli-auth-title">
        {{
          completedProject
            ? 'Sign in successful'
            : accountConfirmed
              ? 'Connect VibeGraph CLI'
              : 'Approve sign in'
        }}
      </h1>
      <p class="cli-auth__intro">
        {{
          completedProject
            ? 'You can close this tab and return to your terminal.'
            : accountConfirmed
              ? 'Choose the project key this installation may push and query through MCP. The credential stays project-bound and can be revoked from your account.'
              : 'VibeGraph CLI is requesting permission to connect to your account.'
        }}
      </p>

      <div v-if="loading" class="cli-auth__state" role="status">
        Checking your VibeGraph account...
      </div>
      <div v-else-if="completedProject" class="cli-auth__success" role="status">
        <div class="cli-auth__success-icon" aria-hidden="true">&#10003;</div>
        <div class="cli-auth__account" aria-label="Connected account">
          <span>Account</span>
          <strong>{{ auth.user?.displayName || auth.user?.email }}</strong>
          <small>{{ auth.user?.email }}</small>
        </div>
        <p>
          Connected to <strong>{{ completedProject }}</strong
          >.
        </p>
        <span>You're all set. You can close this tab and return to your terminal.</span>
      </div>
      <div v-else-if="!accountConfirmed" class="cli-auth__confirm">
        <div class="cli-auth__confirm-icon" aria-hidden="true">{{ accountInitial }}</div>
        <p class="cli-auth__confirm-question">Continue with this account?</p>
        <div class="cli-auth__account" aria-label="Signed-in account">
          <span>Account</span>
          <strong>{{ auth.user?.displayName || auth.user?.email }}</strong>
          <small>{{ auth.user?.email }}</small>
        </div>
        <div v-if="error" class="cli-auth__error" role="alert">{{ error }}</div>
        <button class="cli-auth__approve" type="button" @click="continueWithAccount">
          Continue
        </button>
        <button class="cli-auth__switch" type="button" @click="useAnotherAccount">
          Sign in with another account <span aria-hidden="true">&#8250;</span>
        </button>
      </div>
      <CliAuthorizationForm
        v-else
        v-model:mode="mode"
        v-model:selected-api-key-id="selectedApiKeyId"
        v-model:selected-project-id="selectedProjectId"
        v-model:project-name="projectName"
        :api-key-options="apiKeyOptions"
        :project-options="projectOptions"
        :api-keys-available="Boolean(apiKeys.length)"
        :projects-available="Boolean(projects.length)"
        :can-approve="canApprove"
        :approving="approving"
        :error="error"
        @submit="approve"
      />
    </section>
  </main>
</template>

<style scoped src="./cli-authorize-view.css"></style>
