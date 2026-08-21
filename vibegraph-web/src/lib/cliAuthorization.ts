import { accountApi, api, projectApi, type Project } from '@/lib/api'
import type { ApiKey } from '@/types/api'

export interface CliApprovalResult {
  status: string
  projectId: string
  projectName: string
  expiresAt: string
}

export interface CliApprovalInput {
  browserSecret: string
  projectMode: 'KEY' | 'EXISTING' | 'NEW'
  projectId?: string
  projectName?: string
  apiKeyId?: string
}

export const cliAuthorizationApi = {
  projects(): Promise<Project[]> {
    return projectApi.list()
  },

  keys(): Promise<ApiKey[]> {
    return accountApi.listApiKeys()
  },

  approve(requestId: string, input: CliApprovalInput): Promise<CliApprovalResult> {
    return api.post<CliApprovalResult>(
      `/api/cli/device/${encodeURIComponent(requestId)}/approve`,
      input,
    )
  },
}
