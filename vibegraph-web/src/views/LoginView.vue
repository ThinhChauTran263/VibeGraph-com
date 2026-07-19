<script setup lang="ts">
/**
 * LoginView — email + password authentication.
 * On success, navigates to the role-appropriate dashboard. Shows inline error on failure.
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import BrandMark from '@/components/ui/BrandMark.vue'
import LanguageSelector from '@/components/ui/LanguageSelector.vue'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/lib/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n({ useScope: 'global' })

const email = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  if (!email.value.trim() || !password.value) {
    error.value = t('auth.missingCredentials')
    return
  }

  loading.value = true
  try {
    await auth.login({ email: email.value.trim(), password: password.value })
    const raw = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.push(resolvePostLoginRedirect(raw, auth.user?.role))
  } catch (e) {
    if (e instanceof ApiError) {
      error.value = e.message
    } else {
      error.value = t('auth.connectionError')
    }
  } finally {
    loading.value = false
  }
}

function resolvePostLoginRedirect(rawRedirect: string, role?: string): string {
  const isAdmin = role?.toUpperCase() === 'ADMIN'
  const fallback = isAdmin ? '/admin' : '/dashboard'
  if (!rawRedirect.startsWith('/') || rawRedirect.startsWith('//')) return fallback
  if (isAdmin)
    return rawRedirect === '/admin' || rawRedirect.startsWith('/admin/') ? rawRedirect : fallback
  return rawRedirect === '/admin' || rawRedirect.startsWith('/admin/') ? fallback : rawRedirect
}
</script>

<template>
  <main class="auth-page">
    <header class="auth-page__header">
      <RouterLink class="auth-brand" :to="{ name: 'home' }" :aria-label="t('auth.homeAria')">
        <BrandMark :size="30" :show-wordmark="true" />
      </RouterLink>
      <LanguageSelector />
    </header>

    <div class="auth-card">
      <h1 class="auth-card__title">{{ t('auth.signInTitle') }}</h1>
      <p class="auth-card__subtitle">{{ t('auth.signInSubtitle') }}</p>

      <form class="auth-form" @submit.prevent="handleLogin" novalidate>
        <div class="auth-form__field">
          <label for="login-email" class="auth-form__label">{{ t('auth.email') }}</label>
          <input
            id="login-email"
            v-model="email"
            type="email"
            class="auth-form__input"
            :placeholder="t('auth.emailPlaceholder')"
            autocomplete="email"
            required
          />
        </div>

        <div class="auth-form__field">
          <label for="login-password" class="auth-form__label">{{ t('auth.password') }}</label>
          <input
            id="login-password"
            v-model="password"
            type="password"
            class="auth-form__input"
            placeholder="••••••••"
            autocomplete="current-password"
            required
          />
        </div>

        <div v-if="error" class="auth-form__error" role="alert">
          {{ error }}
        </div>

        <button type="submit" class="auth-form__submit" :disabled="loading">
          {{ loading ? t('auth.signingIn') : t('auth.signIn') }}
        </button>
      </form>

      <p class="auth-card__footer">
        {{ t('auth.noAccount') }}
        <RouterLink :to="{ name: 'register' }" class="auth-link">{{ t('auth.createAccount') }}</RouterLink>
      </p>
    </div>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  padding: var(--vg-space-5);
  background: var(--vg-bg);
  overflow-x: hidden;
}

.auth-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
}

.auth-brand {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0 var(--vg-space-2);
  text-decoration: none;
  border-radius: var(--vg-radius-sm);
}

.auth-card {
  align-self: center;
  justify-self: center;
  box-sizing: border-box;
  width: min(100%, 400px);
  max-width: 400px;
  padding: var(--vg-space-8);
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  box-shadow: var(--vg-shadow-lg);
}

.auth-card__title {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-xl);
  font-weight: 600;
  color: var(--vg-text);
  margin: 0 0 var(--vg-space-2);
  text-align: center;
}

.auth-card__subtitle {
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
  text-align: center;
  margin: 0 0 var(--vg-space-6);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}

.auth-form__field {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-1);
}

.auth-form__label {
  font-size: var(--vg-text-sm);
  font-weight: 500;
  color: var(--vg-text-muted);
}

.auth-form__input {
  box-sizing: border-box;
  width: 100%;
  padding: 0.6rem 0.75rem;
  font-size: var(--vg-text-base);
  font-family: var(--vg-font-body);
  color: var(--vg-text);
  background: var(--vg-bg-elev);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  outline: none;
  transition: border-color var(--vg-dur-fast) var(--vg-ease-out);
}

.auth-form__input:focus {
  border-color: var(--vg-blue);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}

.auth-form__input::placeholder {
  color: var(--vg-text-dim);
}

.auth-form__error {
  padding: var(--vg-space-2) var(--vg-space-3);
  font-size: var(--vg-text-sm);
  color: var(--vg-danger);
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: var(--vg-radius-sm);
}

.auth-form__submit {
  box-sizing: border-box;
  width: 100%;
  padding: 0.65rem 1rem;
  font-size: var(--vg-text-base);
  font-weight: 600;
  font-family: var(--vg-font-body);
  color: #fff;
  background: var(--vg-grad-blue);
  border: none;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  transition:
    opacity var(--vg-dur-fast) var(--vg-ease-out),
    transform var(--vg-dur-fast) var(--vg-ease-out);
}

.auth-form__submit:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
}

.auth-form__submit:active:not(:disabled) {
  transform: translateY(0);
}

.auth-form__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.auth-card__footer {
  margin: var(--vg-space-6) 0 0;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
  text-align: center;
}

.auth-link {
  color: var(--vg-blue-bright);
  font-weight: 500;
  transition: color var(--vg-dur-fast);
}

.auth-link:hover {
  color: var(--vg-cyan);
}

@media (max-width: 520px) {
  .auth-page {
    padding: var(--vg-space-4);
  }

  .auth-card {
    align-self: start;
    margin-top: var(--vg-space-6);
    padding: var(--vg-space-5);
  }

  .auth-card__title {
    font-size: var(--vg-text-lg);
  }
}
</style>
