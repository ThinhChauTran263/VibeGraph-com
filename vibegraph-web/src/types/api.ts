// src/types/api.ts

export interface UserProfile {
  id: string
  email: string
  displayName: string
  role: string
  status: 'active' | 'blocked' | 'deactivated'
  safeReason?: string
}

export interface UserUsage {
  planId: string
  planName: string
  sourceStorageUsed: number
  sourceStorageLimit: number
  creditsUsed: number
  creditsLimit: number
  apiKeyLimit: number
  apiKeysDisabled: boolean
}

export interface Project {
  id: string
  name: string
  status: string
  lastAnalyzedAt: string | null
}

export interface ApiKey {
  id: string
  name: string
  secret?: string // Only present when created
  createdAt: string
  disabled: boolean
}

export interface AdminOverview {
  totalUsers: number
  onlineUsers: number
  totalProjects: number
}

export interface ReportMessage {
  id: string
  senderId: string
  senderName: string
  content: string
  createdAt: string
  isAdmin: boolean
}

export interface Report {
  id: string
  subject: string
  status: 'open' | 'closed'
  messages: ReportMessage[]
  createdAt: string
  updatedAt: string
}
