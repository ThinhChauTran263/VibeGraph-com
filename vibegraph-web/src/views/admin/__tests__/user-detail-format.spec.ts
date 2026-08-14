import { describe, expect, it, vi } from 'vitest'
import type { AdminUserResponse, ApiKey } from '@/types/api'
import {
  apiKeyStatus,
  apiKeyStatusLabel,
  formatDate,
  isExpired,
  lockedMeta,
  projectLabel,
  quotaMb,
  storagePercent,
  usedMb,
  userStatus,
  userStatusLabel,
} from '../user-detail-format'

/**
 * F-M6 split companion: user-detail-format is pure, so every branch gets a direct
 * input->output pin here (the drawer-level spec exercises the same code through the UI).
 */

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const makeUser = (overrides: Partial<AdminUserResponse> = {}): AdminUserResponse => ({
  id: 'usr-1',
  email: 'a@b.c',
  displayName: 'A',
  role: 'USER',
  deactivated: false,
  deactivationReason: null,
  deactivationReasonSafe: null,
  blocked: false,
  blockedReason: null,
  blockedReasonSafe: null,
  planCode: 'FREE',
  storageQuotaOverrideBytes: null,
  creditQuotaOverride: null,
  quotaBytes: 500 * 1024 * 1024,
  usedBytes: 100 * 1024 * 1024,
  apiKeyCreationDisabled: false,
  ...overrides,
})

const makeKey = (overrides: Partial<ApiKey> = {}): ApiKey =>
  ({
    id: 'key-1',
    keyPrefix: 'vg-x',
    name: 'CLI',
    project: null,
    createdAt: '2026-01-01T00:00:00Z',
    lastUsedAt: null,
    expiresAt: null,
    disabledAt: null,
    disabled: false,
    ...overrides,
  }) as ApiKey

describe('user-detail-format', () => {
  it('usedMb/quotaMb prefer the Mb fields and fall back to byte math', () => {
    expect(usedMb(makeUser({ usedMb: 7, usedBytes: 999 * 1024 * 1024 }))).toBe(7)
    expect(usedMb(makeUser({ usedBytes: 3 * 1024 * 1024 }))).toBe(3)
    expect(quotaMb(makeUser({ quotaMb: 11, quotaBytes: 1 }))).toBe(11)
    expect(quotaMb(makeUser({ quotaBytes: 2 * 1024 * 1024 }))).toBe(2)
  })

  it('storagePercent caps at 100 and is 0 without a quota', () => {
    expect(storagePercent(makeUser())).toBe(20) // 100 / 500 MiB
    expect(
      storagePercent(makeUser({ usedBytes: 600 * 1024 * 1024, quotaBytes: 500 * 1024 * 1024 })),
    ).toBe(100)
    expect(storagePercent(makeUser({ quotaBytes: 0, usedBytes: 1024 }))).toBe(0)
  })

  it('userStatus priority: blocked beats deactivated beats active', () => {
    expect(userStatus(makeUser())).toBe('active')
    expect(userStatus(makeUser({ deactivated: true }))).toBe('deactivated')
    expect(userStatus(makeUser({ blocked: true, deactivated: true }))).toBe('blocked')
    expect(userStatusLabel(t, makeUser({ blocked: true }))).toBe('admin.userDetail.status.blocked')
  })

  it('formatDate falls back for empty or unparseable values', () => {
    expect(formatDate('en-US', t, null)).toBe('admin.userDetail.fallback.emptyValue')
    expect(formatDate('en-US', t, 'not-a-date')).toBe('admin.userDetail.fallback.emptyValue')
    expect(formatDate('en-US', t, '2026-07-17T13:05:00Z')).toContain('2026')
  })

  it('isExpired only for past, parseable expiry dates', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 17))
    try {
      expect(isExpired(makeKey({ expiresAt: null }))).toBe(false)
      expect(isExpired(makeKey({ expiresAt: '2026-07-16T00:00:00Z' }))).toBe(true)
      expect(isExpired(makeKey({ expiresAt: '2026-07-18T00:00:00Z' }))).toBe(false)
      expect(isExpired(makeKey({ expiresAt: 'garbage' }))).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('apiKeyStatus/Label precedence: deleted > locked > disabled > expired > active', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 17))
    try {
      const deleted = makeKey({ deletedAt: '2026-01-02T00:00:00Z', locked: true, disabled: true })
      expect(apiKeyStatus(deleted)).toBe('disabled')
      expect(apiKeyStatusLabel(t, deleted)).toBe('admin.userDetail.apiKeys.status.deleted')
      expect(apiKeyStatus(makeKey({ locked: true }))).toBe('blocked')
      expect(apiKeyStatus(makeKey({ disabled: true }))).toBe('disabled')
      expect(apiKeyStatus(makeKey({ expiresAt: '2026-07-01T00:00:00Z' }))).toBe('pending')
      expect(apiKeyStatus(makeKey())).toBe('active')
    } finally {
      vi.useRealTimers()
    }
  })

  it('projectLabel uses the repository name or the fallback', () => {
    expect(projectLabel(t, makeKey())).toBe('admin.userDetail.apiKeys.noRepository')
    expect(
      projectLabel(t, makeKey({ project: { id: 'p', name: 'VibeGraph', sourceType: 'GITHUB', status: 'READY' } })),
    ).toBe('VibeGraph')
  })

  it('lockedMeta composes actor/date details or the administrator fallback', () => {
    expect(lockedMeta(t, 'en-US', makeKey({ locked: true }))).toBe(
      'admin.userDetail.apiKeys.lockedByAdministrator',
    )
    const meta = lockedMeta(
      t,
      'en-US',
      makeKey({ locked: true, lockedBy: 'admin@x.com', lockedAt: '2026-07-01T00:00:00Z' }),
    )
    expect(meta).toContain('admin.userDetail.apiKeys.lockedBy')
    expect(meta).toContain('admin.userDetail.apiKeys.lockedOn')
  })
})
