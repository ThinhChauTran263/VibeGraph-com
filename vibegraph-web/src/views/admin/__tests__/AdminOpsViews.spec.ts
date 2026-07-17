import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import PlansCreditsView from '../PlansCreditsView.vue'
import FeatureFlagsView from '../FeatureFlagsView.vue'
import SecurityView from '../SecurityView.vue'
import AuditView from '../AuditView.vue'

const dialogStub = {
  template: '<div class="confirm-dialog" />',
  props: ['open', 'title', 'message', 'confirmLabel', 'tone', 'busy'],
}

function mountView(component: object, initialState: Record<string, unknown>) {
  return mount(component, {
    global: {
      stubs: { AdminConfirmDialog: dialogStub },
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          stubActions: true,
          initialState: { admin: initialState },
        }),
      ],
    },
  })
}

describe('Admin operations views', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('renders plan storage in MB and exposes reset actions', async () => {
    const wrapper = mountView(PlansCreditsView, {
      plans: [
        {
          code: 'PRO',
          name: 'Pro',
          storageLimitMb: 512,
          apiKeyLimit: 5,
          monthlyCreditLimit: 500,
          contactSalesRequired: false,
        },
      ],
      pricingRules: [],
    })
    await flushPromises()

    expect(wrapper.text()).toContain('512 MB')
    expect(wrapper.text()).toContain('Reset form')
    expect(wrapper.text()).toContain('Reset')
  })

  it('renders canonical global flags and MCP child controls', async () => {
    const wrapper = mountView(FeatureFlagsView, { featureFlags: [] })
    await flushPromises()

    expect(wrapper.text()).toContain('Registration')
    expect(wrapper.text()).toContain('Project analyze')
    expect(wrapper.text()).toContain('Use case generation')
    expect(wrapper.text()).toContain('All MCP tools')
    expect(wrapper.text()).toContain('Impact Analysis')
  })

  it('renders request telemetry and IP block policies from store state', async () => {
    const wrapper = mountView(SecurityView, {
      securityEvents: [],
      requestEvents: [
        {
          id: 'event-1',
          userId: 'user-1',
          apiKeyRef: null,
          ipAddress: '203.0.113.42',
          route: '/api/projects',
          method: 'POST',
          status: 429,
          eventType: 'RATE_LIMITED',
          occurredAt: '2026-07-17T10:00:00Z',
        },
      ],
      topUsers: [],
      topIps: [],
      ipBlocks: [
        {
          id: 'block-1',
          ipAddress: '203.0.113.42',
          safeReason: 'Unusual request volume.',
          expiresAt: null,
          createdBy: 'admin-1',
          createdAt: '2026-07-17T09:00:00Z',
          updatedAt: '2026-07-17T09:00:00Z',
          active: true,
        },
      ],
    })
    await flushPromises()

    expect(wrapper.text()).toContain('RATE_LIMITED')
    expect(wrapper.text()).toContain('203.0.113.42')
    expect(wrapper.text()).toContain('Unusual request volume.')
  })

  it('renders audit rows, detail data, and retention value', async () => {
    const wrapper = mountView(AuditView, {
      auditLogs: [
        {
          id: 'audit-1',
          action: 'USER_BLOCKED',
          actorUserId: 'admin-1',
          targetUserId: 'user-1',
          targetType: 'USER',
          targetId: 'user-1',
          outcome: 'SUCCESS',
          ipAddress: '127.0.0.1',
          details: '{}',
          createdAt: '2026-07-17T10:00:00Z',
        },
      ],
      auditLogDetail: {
        id: 'audit-1',
        action: 'USER_BLOCKED',
        actorUserId: 'admin-1',
        targetUserId: 'user-1',
        targetType: 'USER',
        targetId: 'user-1',
        outcome: 'SUCCESS',
        ipAddress: '127.0.0.1',
        details: '{"reason":"abuse"}',
        createdAt: '2026-07-17T10:00:00Z',
      },
      auditRetention: { retentionDays: 120, updatedBy: 'admin-1', updatedAt: null },
      auditPagination: {
        totalElements: 1,
        totalPages: 1,
        pageNumber: 0,
        pageSize: 50,
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('USER_BLOCKED')
    expect(wrapper.text()).toContain('Retention policy')
    expect((wrapper.find('#audit-retention-days').element as HTMLInputElement).value).toBe('120')
    expect(wrapper.text()).toContain('Redacted details')
  })
})