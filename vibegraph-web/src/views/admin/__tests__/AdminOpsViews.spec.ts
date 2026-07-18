import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { useAdminStore } from '@/stores/admin'
import PlansCreditsView from '../PlansCreditsView.vue'
import FeatureFlagsView from '../FeatureFlagsView.vue'
import SecurityView from '../SecurityView.vue'
import AuditView from '../AuditView.vue'

const capabilityMocks = vi.hoisted(() => ({
  contract: { value: false as boolean | null },
  refresh: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/lib/featureAvailability', () => ({
  featureAvailabilityContract: capabilityMocks.contract,
  refreshFeatureAvailability: capabilityMocks.refresh,
}))

const dialogStub = {
  template: '<div class="confirm-dialog" />',
  props: ['open', 'title', 'message', 'confirmLabel', 'tone', 'busy'],
}

type AdminStore = ReturnType<typeof useAdminStore>

function mountView(
  component: object,
  initialState: Record<string, unknown>,
  configureStore?: (store: AdminStore) => void,
) {
  const pinia = createTestingPinia({
    createSpy: vi.fn,
    stubActions: true,
    initialState: { admin: initialState },
  })
  const store = useAdminStore(pinia)
  configureStore?.(store)
  return mount(component, {
    global: {
      stubs: { AdminConfirmDialog: dialogStub },
      plugins: [pinia],
    },
  })
}

describe('Admin operations views', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    capabilityMocks.contract.value = false
    capabilityMocks.refresh.mockResolvedValue(undefined)
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

  it('labels system controls as configuration-only without a runtime capability contract', async () => {
    const wrapper = mountView(FeatureFlagsView, { featureFlags: [] })
    await flushPromises()

    expect(wrapper.text()).toContain('Configuration only')
    expect(wrapper.text()).toContain('not yet propagated to user-facing runtime capability state')
    expect(wrapper.text()).toContain('Import methods')
    expect(wrapper.text()).toContain('CLI push')
    expect(wrapper.text()).toContain('API key creation')
    expect(wrapper.text()).toContain('Registration')
    expect(wrapper.text()).toContain('Gen use case')
    expect(wrapper.text()).toContain('MCP global and child tools')
  })

  it('shows real runtime propagation state and collapses dense control groups', async () => {
    capabilityMocks.contract.value = true
    const wrapper = mountView(FeatureFlagsView, { featureFlags: [] })
    await flushPromises()

    expect(wrapper.text()).toContain('Runtime connected')
    const mcpToggle = wrapper
      .findAll('button.group-toggle')
      .find((button) => button.text().includes('MCP global and child tools'))
    expect(mcpToggle).toBeDefined()
    expect(mcpToggle?.attributes('aria-expanded')).toBe('true')
    expect(mcpToggle?.attributes('aria-controls')).toBe('system-group-mcp-global-and-child-tools')
    expect(wrapper.find('#system-group-mcp-global-and-child-tools').exists()).toBe(true)
    const mcpGlobalSwitch = wrapper.get('input[aria-label="Toggle All MCP tools"]')
    await mcpGlobalSwitch.setValue(false)
    await flushPromises()
    expect(capabilityMocks.refresh).toHaveBeenCalledTimes(2)

    await mcpToggle?.trigger('click')

    expect(mcpToggle?.attributes('aria-expanded')).toBe('false')
    expect(wrapper.text()).not.toContain('Impact Analysis')
  })

  it('keeps telemetry retry warnings separate from IP policy mutation success', async () => {
    const wrapper = mountView(
      SecurityView,
      {
        securityEvents: [],
        requestEvents: [],
        topUsers: [],
        topIps: [],
        ipBlocks: [],
      },
      (store) => {
        vi.mocked(store.fetchSecurityData).mockResolvedValue(['request events'])
        vi.mocked(store.fetchRequestEvents).mockResolvedValue(undefined)
        vi.mocked(store.createIpBlock).mockResolvedValue({ refreshFailed: false })
      },
    )
    await flushPromises()

    expect(wrapper.text()).toContain('Some monitoring panels are unavailable')
    expect(wrapper.text()).toContain('Retry request events')
    await wrapper.get('input[placeholder="203.0.113.42"]').setValue('203.0.113.42')
    await wrapper
      .get('textarea[placeholder="Access temporarily restricted due to unusual request volume."]')
      .setValue('Unusual request volume.')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('IP block policy created successfully')
    expect(wrapper.text()).toContain('Some monitoring panels are unavailable')
    const retry = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Retry request events'))
    await retry?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('Some monitoring panels are unavailable')
  })

  it('starts live security updates on mount and cleans them up on unmount', async () => {
    const wrapper = mountView(
      SecurityView,
      {
        securityEvents: [],
        requestEvents: [],
        topUsers: [],
        topIps: [],
        ipBlocks: [],
        securityLiveStatus: 'reconnecting',
      },
      (store) => {
        vi.mocked(store.fetchSecurityData).mockResolvedValue([])
        vi.mocked(store.fetchUsers).mockResolvedValue(undefined)
      },
    )
    const store = useAdminStore()
    await flushPromises()

    expect(wrapper.text()).toContain('Reconnecting')
    expect(store.startSecurityStream).toHaveBeenCalledTimes(1)

    wrapper.unmount()

    expect(store.stopSecurityStream).toHaveBeenCalledTimes(1)
  })

  it('masks API key references in request event labels', async () => {
    const wrapper = mountView(SecurityView, {
      securityEvents: [],
      requestEvents: [
        {
          id: 'event-secret',
          userId: null,
          userDisplayName: null,
          userEmail: null,
          apiKeyRef: 'key-id:vbg_rawsupersecretvalue',
          ipAddress: '203.0.113.42',
          route: '/mcp',
          method: 'POST',
          status: 200,
          eventType: 'REQUEST',
          occurredAt: '2026-07-19T10:00:00Z',
        },
      ],
      topUsers: [],
      topIps: [],
      ipBlocks: [],
      securityLiveStatus: 'connected',
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Live connected')
    expect(wrapper.text()).toContain('vbg_raws****')
    expect(wrapper.text()).not.toContain('supersecretvalue')
  })

  it('renders request telemetry and IP block policies from store state', async () => {
    const wrapper = mountView(SecurityView, {
      securityEvents: [],
      requestEvents: [
        {
          id: 'event-1',
          userId: 'user-1',
          userDisplayName: 'Thinh Tran',
          userEmail: 'thinh@example.com',
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

  it('keeps all request events scrollable and falls back to user id when identity is missing', async () => {
    const requestEvents = Array.from({ length: 12 }, (_, index) => ({
      id: `event-${index}`,
      userId: 'legacy-user-long-id',
      userDisplayName: null,
      userEmail: null,
      apiKeyRef: null,
      ipAddress: '203.0.113.42',
      route: `/api/projects/${index}`,
      method: 'GET',
      status: 200,
      eventType: 'REQUEST',
      occurredAt: '2026-07-17T10:00:00Z',
    }))
    const wrapper = mountView(SecurityView, {
      securityEvents: [],
      requestEvents,
      topUsers: [],
      topIps: [],
      ipBlocks: [],
    })
    await flushPromises()

    expect(wrapper.findAll('.request-events tbody tr')).toHaveLength(12)
    expect(wrapper.text()).toContain('12 events')
    expect(wrapper.text()).toContain('legacy-u...')
    expect(wrapper.text()).toContain('legacy-u... / No API key')
  })

  it('groups top users by user and API key and expands suspicious networks safely', async () => {
    const wrapper = mountView(SecurityView, {
      securityEvents: [],
      requestEvents: [],
      topUsers: [
        {
          userId: 'user-1',
          userDisplayName: 'Thinh Tran',
          userEmail: 'thinh@example.com',
          apiKeyRef: 'key-1:vbg_ab12secret',
          ipAddress: null,
          minuteBucket: '2026-07-18T10:01:00Z',
          requestsPerMinute: 34,
        },
        {
          userId: 'user-1',
          userDisplayName: 'Thinh Tran',
          userEmail: 'thinh@example.com',
          apiKeyRef: 'key-1:vbg_ab12secret',
          ipAddress: null,
          minuteBucket: '2026-07-18T10:02:00Z',
          requestsPerMinute: 6,
        },
        {
          userId: 'user-1',
          userDisplayName: 'Thinh Tran',
          userEmail: 'thinh@example.com',
          apiKeyRef: 'key-2:vbg_cd34secret',
          ipAddress: null,
          minuteBucket: '2026-07-18T10:02:00Z',
          requestsPerMinute: 33,
        },
        {
          userId: 'user-1',
          userDisplayName: 'Thinh Tran',
          userEmail: 'thinh@example.com',
          apiKeyRef: null,
          ipAddress: null,
          minuteBucket: '2026-07-18T10:03:00Z',
          requestsPerMinute: 12,
        },
      ],
      topIps: [
        {
          ipAddress: '172.18.0.1',
          totalRequests: 234,
          uniqueUsers: 2,
          uniqueApiKeys: 1,
          minuteBucket: '2026-07-18T10:03:00Z',
          breakdown: [
            {
              userId: 'user-1',
              userDisplayName: 'VibeGraph Admin',
              userEmail: 'admin@example.com',
              apiKeyRef: null,
              requests: 194,
            },
            {
              userId: 'user-2',
              userDisplayName: 'VibeGraph User',
              userEmail: 'user@example.com',
              apiKeyRef: 'key-1:vbg_ab12secret',
              requests: 40,
            },
          ],
        },
      ],
      ipBlocks: [],
    })
    await flushPromises()

    const rankPanels = wrapper.findAll('.rank-grid article')
    const userRows = rankPanels[0]?.findAll('ol > li') ?? []
    expect(userRows).toHaveLength(3)
    expect(userRows[0]?.text()).toContain('Thinh Tran / vbg_ab12****')
    expect(userRows[0]?.text()).toContain('40')
    expect(userRows[1]?.text()).toContain('Thinh Tran / vbg_cd34****')
    expect(userRows[2]?.text()).toContain('Thinh Tran / No API key')
    expect(rankPanels[0]?.text()).not.toContain('2 keys')
    for (const legacyLabel of ['Ses' + 'sion', 'W' + 'eb']) {
      expect(rankPanels[0]?.text()).not.toContain(legacyLabel)
    }

    expect(rankPanels[1]?.text()).toContain('Suspicious Networks')
    const networkToggle = rankPanels[1]?.get('button.network-toggle')
    expect(networkToggle?.attributes('aria-expanded')).toBe('false')
    expect(networkToggle?.text()).toContain('172.18.0.1')
    expect(networkToggle?.text()).toContain('234 requests')
    expect(networkToggle?.text()).toContain('2 users')
    expect(networkToggle?.text()).toContain('1 API key')
    expect(networkToggle?.text()).toContain('Shared network')

    await networkToggle?.trigger('click')

    expect(networkToggle?.attributes('aria-expanded')).toBe('true')
    expect(rankPanels[1]?.text()).toContain('VibeGraph Admin / No API key')
    expect(rankPanels[1]?.text()).toContain('194')
    expect(rankPanels[1]?.text()).toContain('VibeGraph User / vbg_ab12****')
    expect(rankPanels[1]?.text()).toContain('40')
    expect(wrapper.text()).not.toContain('vbg_ab12secret')
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
