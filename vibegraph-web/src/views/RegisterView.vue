<script setup lang="ts">
/**
 * RegisterView — create a new VibeGraph account.
 * On success, auto-logs-in and navigates to /dashboard.
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/lib/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n({ useScope: 'global' })

const displayName = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const loading = ref(false)

function validate(): string | null {
  if (!displayName.value.trim()) return t('auth.displayNameRequired')
  if (!email.value.trim()) return t('auth.emailRequired')
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) return t('auth.validEmail')
  if (password.value.length < 8) return t('auth.passwordMinimum')
  if (password.value !== confirmPassword.value) return t('auth.passwordMismatch')
  return null
}

async function handleRegister() {
  error.value = ''
  const validationError = validate()
  if (validationError) {
    error.value = validationError
    return
  }

  loading.value = true
  try {
    await auth.register({
      email: email.value.trim(),
      password: password.value,
      displayName: displayName.value.trim(),
    })
    const raw = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const redirectTo = raw.startsWith('/') && !raw.startsWith('//') ? raw : '/dashboard'
    router.push(redirectTo)
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
</script>

<template>
  <main class="auth-page">
    <div class="auth-card">
      <h1 class="auth-card__title">{{ t('auth.registerTitle') }}</h1>
      <p class="auth-card__subtitle">{{ t('auth.registerSubtitle') }}</p>

      <form class="auth-form" @submit.prevent="handleRegister" novalidate>
        <div class="auth-form__field">
          <label for="reg-name" class="auth-form__label">{{ t('auth.displayName') }}</label>
          <input
            id="reg-name"
            v-model="displayName"
            type="text"
            class="auth-form__input"
            :placeholder="t('auth.displayNamePlaceholder')"
            autocomplete="name"
            required
          />
        </div>

        <div class="auth-form__field">
          <label for="reg-email" class="auth-form__label">{{ t('auth.email') }}</label>
          <input
            id="reg-email"
            v-model="email"
            type="email"
            class="auth-form__input"
            :placeholder="t('auth.emailPlaceholder')"
            autocomplete="email"
            required
          />
        </div>

        <div class="auth-form__field">
          <label for="reg-password" class="auth-form__label">{{ t('auth.password') }}</label>
          <input
            id="reg-password"
            v-model="password"
            type="password"
            class="auth-form__input"
            :placeholder="t('auth.passwordPlaceholder')"
            autocomplete="new-password"
            required
            minlength="8"
          />
        </div>

        <div class="auth-form__field">
          <label for="reg-confirm" class="auth-form__label">{{ t('auth.confirmPassword') }}</label>
          <input
            id="reg-confirm"
            v-model="confirmPassword"
            type="password"
            class="auth-form__input"
            :placeholder="t('auth.confirmPasswordPlaceholder')"
            autocomplete="new-password"
            required
          />
        </div>

        <div v-if="error" class="auth-form__error" role="alert">
          {{ error }}
        </div>

        <button type="submit" class="auth-form__submit" :disabled="loading">
          {{ loading ? t('auth.registering') : t('auth.register') }}
        </button>
      </form>

      <p class="auth-card__footer">
        {{ t('auth.hasAccount') }}
        <RouterLink :to="{ name: 'login' }" class="auth-link">{{ t('auth.signInLink') }}</RouterLink>
      </p>
    </div>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--vg-space-4);
  background: var(--vg-bg);
}

.auth-card {
  width: 100%;
  max-width: 400px;
  padding: var(--vg-space-8);
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-lg);
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
</style>
