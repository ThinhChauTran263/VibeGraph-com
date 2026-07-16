<script setup lang="ts">
/**
 * LoginView — email + password authentication.
 * On success, navigates to /dashboard. Shows inline error on failure.
 */
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/lib/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  if (!email.value.trim() || !password.value) {
    error.value = 'Please enter email and password.'
    return
  }

  loading.value = true
  try {
    await auth.login({ email: email.value.trim(), password: password.value })
    const raw = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const redirectTo = raw.startsWith('/') && !raw.startsWith('//') ? raw : '/dashboard'
    router.push(redirectTo)
  } catch (e) {
    if (e instanceof ApiError) {
      error.value = e.message
    } else {
      error.value = 'Unable to connect. Please try again.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <div class="auth-card">
      <h1 class="auth-card__title">Sign in to VibeGraph</h1>
      <p class="auth-card__subtitle">Analyze and visualize your Java codebase</p>

      <form class="auth-form" @submit.prevent="handleLogin" novalidate>
        <div class="auth-form__field">
          <label for="login-email" class="auth-form__label">Email</label>
          <input
            id="login-email"
            v-model="email"
            type="email"
            class="auth-form__input"
            placeholder="you@example.com"
            autocomplete="email"
            required
          />
        </div>

        <div class="auth-form__field">
          <label for="login-password" class="auth-form__label">Password</label>
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

        <button
          type="submit"
          class="auth-form__submit"
          :disabled="loading"
        >
          {{ loading ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>

      <p class="auth-card__footer">
        Don't have an account?
        <RouterLink :to="{ name: 'register' }" class="auth-link">Create one</RouterLink>
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
