import { describe, expect, it } from 'vitest'
import en from '@/language/locales/en-US.json'
import vi from '@/language/locales/vi-VN.json'

function keyPaths(value: unknown, prefix = ''): string[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return prefix ? [prefix] : []
  return Object.entries(value).flatMap(([key, child]) =>
    keyPaths(child, prefix ? `${prefix}.${key}` : key),
  )
}

function messagesByPath(value: unknown, prefix = ''): Record<string, string> {
  if (typeof value === 'string') return { [prefix]: value }
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return Object.assign(
    {},
    ...Object.entries(value).map(([key, child]) =>
      messagesByPath(child, prefix ? `${prefix}.${key}` : key),
    ),
  )
}

function placeholders(message: string): string[] {
  return [...message.matchAll(/\{([A-Za-z0-9_]+)\}/g)].map((match) => match[1] ?? '').sort()
}

describe('locale messages', () => {
  it('keeps English and Vietnamese message trees in sync', () => {
    expect(keyPaths(vi)).toEqual(keyPaths(en))
  })

  it('keeps interpolation placeholders in sync', () => {
    const englishMessages = messagesByPath(en)
    const vietnameseMessages = messagesByPath(vi)

    for (const [path, message] of Object.entries(englishMessages)) {
      expect(placeholders(vietnameseMessages[path] ?? ''), path).toEqual(placeholders(message))
    }
  })

  it('includes the user dashboard and registration message contract', () => {
    const paths = new Set(keyPaths(en))
    expect(paths.has('common.view')).toBe(true)
    expect(paths.has('auth.registerTitle')).toBe(true)
    expect(paths.has('user.layout.openNavigation')).toBe(true)
    expect(paths.has('user.overview.quickActions')).toBe(true)
    expect(paths.has('user.projects.emptyTitle')).toBe(true)
    expect(paths.has('user.apiKeys.secretCopy')).toBe(true)
    expect(paths.has('user.reports.submit')).toBe(true)
    expect(paths.has('user.notifications.emptyTitle')).toBe(true)
  })

  it('includes every Admin Console namespace required by Task 2C', () => {
    const paths = new Set(keyPaths(en))
    const requiredPaths = [
      'admin.layout.nav.overview',
      'admin.overview.title',
      'admin.users.title',
      'admin.userDetail.header.kicker',
      'admin.security.title',
      'admin.audit.title',
      'admin.plansCredits.title',
      'admin.system.title',
      'admin.announcements.title',
      'admin.reports.title',
      'admin.settings.title',
      'admin.dialogs.cancel',
    ]

    for (const path of requiredPaths) expect(paths.has(path), path).toBe(true)
  })
})
