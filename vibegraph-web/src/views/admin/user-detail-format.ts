/**
 * F-M6 split (UserDetailDrawer, step 1): quota/status/api-key formatting helpers extracted
 * from UserDetailDrawer.vue. Pure functions take their data as arguments; i18n-dependent
 * formatters receive an explicit `t`/locale instead of closing over component state, so every
 * branch is unit-testable without mounting the drawer.
 */
import type { AdminUserResponse, ApiKey } from '@/types/api'

/** Minimal i18n surface the formatters need (vue-i18n's `t` satisfies it). */
export type TranslateFn = (key: string, params?: Record<string, unknown>) => string

export function usedMb(u: AdminUserResponse): number {
  return u.usedMb ?? Math.round((u.usedBytes ?? 0) / (1024 * 1024))
}

export function quotaMb(u: AdminUserResponse): number {
  return u.quotaMb ?? Math.round((u.quotaBytes ?? 0) / (1024 * 1024))
}

export function storagePercent(u: AdminUserResponse): number {
  const quota = quotaMb(u)
  if (!quota) return 0
  return Math.min(100, Math.round((usedMb(u) / quota) * 100))
}

export function userStatus(u: AdminUserResponse): string {
  if (u.blocked) return 'blocked'
  if (u.deactivated) return 'deactivated'
  return 'active'
}

export function userStatusLabel(t: TranslateFn, u: AdminUserResponse): string {
  return t(`admin.userDetail.status.${userStatus(u)}`)
}

export function formatDate(
  localeValue: string,
  t: TranslateFn,
  value: string | null | undefined,
): string {
  if (!value) return t('admin.userDetail.fallback.emptyValue')
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return t('admin.userDetail.fallback.emptyValue')
  return new Intl.DateTimeFormat(localeValue, {
    month: 'short',
    day: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

export function isExpired(key: ApiKey): boolean {
  if (!key.expiresAt) return false
  const expiresAt = new Date(key.expiresAt).getTime()
  return Number.isFinite(expiresAt) && expiresAt <= Date.now()
}

export function apiKeyStatusLabel(t: TranslateFn, key: ApiKey): string {
  if (key.deletedAt) return t('admin.userDetail.apiKeys.status.deleted')
  if (key.locked) return t('admin.userDetail.apiKeys.status.locked')
  if (key.disabled) return t('admin.userDetail.apiKeys.status.disabled')
  if (isExpired(key)) return t('admin.userDetail.apiKeys.status.expired')
  return t('admin.userDetail.apiKeys.status.active')
}

export function apiKeyStatus(key: ApiKey): string {
  if (key.deletedAt) return 'disabled'
  if (key.locked) return 'blocked'
  if (key.disabled) return 'disabled'
  if (isExpired(key)) return 'pending'
  return 'active'
}

export function projectLabel(t: TranslateFn, key: ApiKey): string {
  return key.project?.name ?? t('admin.userDetail.apiKeys.noRepository')
}

export function lockedMeta(t: TranslateFn, localeValue: string, key: ApiKey): string {
  const parts = []
  if (key.lockedBy) parts.push(t('admin.userDetail.apiKeys.lockedBy', { actor: key.lockedBy }))
  if (key.lockedAt)
    parts.push(t('admin.userDetail.apiKeys.lockedOn', { date: formatDate(localeValue, t, key.lockedAt) }))
  return parts.length
    ? t('admin.userDetail.apiKeys.locked', { details: parts.join(' ') })
    : t('admin.userDetail.apiKeys.lockedByAdministrator')
}
