import { apiDownload, apiRequest } from "@/lib/api/client"
import { features } from "@/lib/features"
import { mockAssignReviewerToCase } from "@/lib/mock/forensic-api"
import { mockUsers, normalizeUserRole, type UserRole } from "@/lib/permissions"
import {
  MOCK_ADMIN_LOGS,
  MOCK_ADMIN_PROFILE,
  MOCK_ADMIN_USERS,
  MOCK_INVITE_CODES,
  getLogDepartments,
} from "@/app/admin/_data/mock-admin"
import type {
  AdminEvidence,
  AdminEvidenceDetail,
  AdminLog,
  AdminProfile,
  AdminUser,
  EvidenceFileType,
  EvidenceStatus,
  InviteCode,
  LogCategory,
  UserStatus,
} from "@/app/admin/_types/admin"
import type { OrgType } from "@/app/signup/organizationData"

type AdminUserPageResponse = {
  items: AdminUser[]
  total: number
  page: number
  size: number
}

type AdminUserStatusResponse = {
  userId: string
  status: UserStatus
}

export type AdminReviewer = {
  id: string
  name: string
  department?: string | null
  organizationId?: string | null
  organizationName?: string | null
  organizationType?: string | null
  specialties?: string[]
  activeCaseCount?: number
  averageDays?: number
}

type AdminReviewerListResponse = {
  reviewers: AdminReviewer[]
}

type FetchAdminReviewersOptions = {
  department?: string | null
  uploaderId?: string | null
}

export type AdminDashboardStats = {
  pendingUsers: number
  totalUsers: number
  todayLogs: number
  unusedInviteCodes: number
  cocLogs: number
}

export type AdminUsageTrendPoint = {
  date: string
  label: string
  analysis: number
  signups: number
}

export type AdminDashboardOverview = {
  stats: AdminDashboardStats
  approvedUsers: number
  departmentCount: number
  todayAnalysis: number
  recentLogs: AdminLog[]
  trend: AdminUsageTrendPoint[]
  menuBadges: {
    users: number
    approvals: number
    logs: number
    inviteCodes: number
  }
}

export type AdminWeeklyAnalysisPoint = {
  label: string
  date: string
  requestedCount: number
  completedCount: number
}

export type AdminRiskDistribution = {
  safeCount: number
  cautionCount: number
  dangerCount: number
}

export type AdminAnalysisStats = {
  weeklyTotalCount: number
  deepfakeDetectionRate: number
  averageAnalysisMinutes: number
  weeklyPoints: AdminWeeklyAnalysisPoint[]
  riskDistribution: AdminRiskDistribution
}

function mockAdminAnalysisStats(): AdminAnalysisStats {
  return {
    weeklyTotalCount: 147,
    deepfakeDetectionRate: 15.6,
    averageAnalysisMinutes: 3.2,
    weeklyPoints: [
      { label: "월", date: "2026-06-09", requestedCount: 14, completedCount: 12 },
      { label: "화", date: "2026-06-10", requestedCount: 18, completedCount: 15 },
      { label: "수", date: "2026-06-11", requestedCount: 16, completedCount: 14 },
      { label: "목", date: "2026-06-12", requestedCount: 22, completedCount: 18 },
      { label: "금", date: "2026-06-13", requestedCount: 20, completedCount: 17 },
      { label: "토", date: "2026-06-14", requestedCount: 12, completedCount: 10 },
      { label: "일", date: "2026-06-15", requestedCount: 10, completedCount: 8 },
    ],
    riskDistribution: {
      safeCount: 98,
      cautionCount: 32,
      dangerCount: 17,
    },
  }
}

function buildLastNDays(days: number) {
  const result: { date: string; label: string }[] = []
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const date = new Date(today)
    date.setDate(today.getDate() - offset)
    const iso = date.toISOString().slice(0, 10)
    const label = `${String(date.getMonth() + 1).padStart(2, "0")}/${String(date.getDate()).padStart(2, "0")}`
    result.push({ date: iso, label })
  }

  return result
}

function aggregateUsageTrend(
  analysisLogs: AdminLog[],
  signupLogs: AdminLog[],
  days: number
): AdminUsageTrendPoint[] {
  const buckets = buildLastNDays(days)
  const analysisByDate = new Map(buckets.map((bucket) => [bucket.date, 0]))
  const signupsByDate = new Map(buckets.map((bucket) => [bucket.date, 0]))

  for (const log of analysisLogs) {
    const date = log.timestamp.slice(0, 10)
    if (analysisByDate.has(date)) {
      analysisByDate.set(date, (analysisByDate.get(date) ?? 0) + 1)
    }
  }

  for (const log of signupLogs) {
    if (!log.action.includes("가입")) continue
    const date = log.timestamp.slice(0, 10)
    if (signupsByDate.has(date)) {
      signupsByDate.set(date, (signupsByDate.get(date) ?? 0) + 1)
    }
  }

  return buckets.map((bucket) => ({
    date: bucket.date,
    label: bucket.label,
    analysis: analysisByDate.get(bucket.date) ?? 0,
    signups: signupsByDate.get(bucket.date) ?? 0,
  }))
}

function countTodayAnalysis(logs: AdminLog[]) {
  const today = new Date().toISOString().slice(0, 10)
  return logs.filter(
    (log) => log.timestamp.startsWith(today) && log.category === "ANALYSIS"
  ).length
}

type AdminLogPageResponse = {
  items: AdminLog[]
  total: number
  page: number
  size: number
  departments: string[]
}

type AdminEvidencePageResponse = {
  items: AdminEvidence[]
  total: number
  page: number
  size: number
}

export type UpdateAdminUserPayload = {
  displayName: string
  email: string
  organizationType?: OrgType | string
  department: string
  role?: AdminUser["role"]
  status?: UserStatus
}

export type UpdateAdminProfilePayload = {
  username: string
  displayName: string
  email: string
  department: string
  phone?: string
}

const USE_MOCK_ADMIN = features.mockApi

const mockAdminUsers = MOCK_ADMIN_USERS.map((user) => ({ ...user }))

function patchMockAdminUser(userId: string, patch: Partial<AdminUser>) {
  const index = mockAdminUsers.findIndex((user) => user.id === userId)
  if (index === -1) {
    return { ...mockAdminUsers[0], ...patch }
  }
  mockAdminUsers[index] = { ...mockAdminUsers[index], ...patch }
  return mockAdminUsers[index]
}

type BackendPageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

function mapPage<T>(response: BackendPageResponse<T>) {
  return {
    items: response.content ?? [],
    total: response.totalElements ?? 0,
    page: response.page ?? 0,
    size: response.size ?? 10,
  }
}

function mapAdminProfile(profile: AdminProfile): AdminProfile {
  return {
    ...profile,
    phone: profile.phone ?? "",
  }
}

function normalizeAdminUserRole(role?: string | null): UserRole | undefined {
  const normalized = normalizeUserRole(role)
  return normalized === "UNKNOWN" ? undefined : normalized
}

function mapAdminUser(user: AdminUser & { role?: string | null }): AdminUser {
  return {
    ...user,
    role: normalizeAdminUserRole(user.role),
  }
}

function mapInviteCode(code: InviteCode): InviteCode {
  return {
    ...code,
    expiresAt: code.expiresAt ?? "",
    usedBy: code.usedBy ?? undefined,
  }
}

const MOCK_ADMIN_EVIDENCES: AdminEvidenceDetail[] = [
  {
    id: "EVD-2024-0184",
    fileName: "interview_record.mp4",
    fileType: "VIDEO",
    caseNumber: "CASE-2024-0612",
    caseName: "강남구 인터뷰 영상 위변조 의심",
    uploaderUsername: "lee_forensic",
    uploaderName: "이포렌",
    department: "디지털포렌식 2팀",
    hashValue: "a4f3b2c1d9e8f7a6bb29c7a0ef531bd2f6f58ca903ad77ab6e8759ca0b3f1102",
    fileSize: 445 * 1024 * 1024,
    uploadedAt: "2024-06-12 09:14",
    status: "UPLOADED",
    analysisStatus: "COMPLETED",
    mimeType: "video/mp4",
    hashAlgorithm: "SHA-256",
    metadata: {
      width: 1920,
      height: 1080,
      durationSec: 1902,
      fps: 29.97,
      codec: "H.264 / AAC",
      extractionStatus: "COMPLETED",
    },
    analysisHistory: [
      {
        id: "ANL-2024-0441",
        status: "COMPLETED",
        requestedAt: "2024-06-12 09:20",
        completedAt: "2024-06-12 09:34",
      },
    ],
    custodyLogs: [
      {
        id: "COC-001",
        timestamp: "2024-06-12 09:14",
        category: "COC",
        actor: "lee_forensic",
        action: "증거 업로드",
        detail: "SHA-256 해시 생성",
      },
    ],
  },
  {
    id: "EVD-2024-0180",
    fileName: "bodycam_reference.mp4",
    fileType: "VIDEO",
    caseNumber: "CASE-2024-0610",
    caseName: "잠실 현장 바디캠 증거 검증",
    uploaderUsername: "park_invest",
    uploaderName: "박수사",
    department: "현장수사 지원팀",
    hashValue: "9d2f5a0c8b1e43d7ddf91b37e0cd7364650be8ad73df6d26fcd2a17190c88b41",
    fileSize: 1840 * 1024 * 1024,
    uploadedAt: "2024-06-10 18:45",
    status: "UPLOADED",
    analysisStatus: "ANALYZING",
    mimeType: "video/mp4",
    hashAlgorithm: "SHA-256",
    metadata: {
      width: 1920,
      height: 1080,
      durationSec: 2538,
      fps: 30,
      codec: "H.265 / AAC",
      extractionStatus: "COMPLETED",
    },
    analysisHistory: [
      {
        id: "ANL-2024-0437",
        status: "ANALYZING",
        requestedAt: "2024-06-10 19:00",
      },
    ],
    custodyLogs: [
      {
        id: "COC-002",
        timestamp: "2024-06-10 18:45",
        category: "COC",
        actor: "park_invest",
        action: "증거 업로드",
      },
    ],
  },
  {
    id: "EVD-2024-0174",
    fileName: "cctv_court_entry.mp4",
    fileType: "VIDEO",
    caseNumber: "CASE-2024-0608",
    caseName: "법원 출입구 CCTV 원본성 확인",
    uploaderUsername: "choi_audit",
    uploaderName: "최감사",
    department: "영상증거 분석팀",
    hashValue: "6a0d7c2e41bf883c91ff305d8e2c59f0e1036c1bcf06bbd17de0ab7f318aa203",
    fileSize: 2140 * 1024 * 1024,
    uploadedAt: "2024-06-08 16:10",
    status: "UPLOADED",
    analysisStatus: "QUEUED",
    mimeType: "video/mp4",
    hashAlgorithm: "SHA-256",
    metadata: {
      width: 1280,
      height: 720,
      durationSec: 4324,
      fps: 24,
      codec: "H.264 / AAC",
      extractionStatus: "COMPLETED",
    },
    analysisHistory: [
      {
        id: "ANL-2024-0429",
        status: "QUEUED",
        requestedAt: "2024-06-08 16:30",
      },
    ],
    custodyLogs: [
      {
        id: "COC-003",
        timestamp: "2024-06-08 16:10",
        category: "COC",
        actor: "choi_audit",
        action: "증거 업로드",
      },
    ],
  },
]

function paginate<T>(items: T[], page: number, size: number) {
  const start = page * size
  return items.slice(start, start + size)
}

function matchesText(value: string | undefined, search: string) {
  return (value ?? "").toLowerCase().includes(search.toLowerCase())
}

async function withMockFallback<T>(request: () => Promise<T>, fallback: () => T): Promise<T> {
  try {
    return await request()
  } catch (error) {
    if (USE_MOCK_ADMIN) return fallback()
    throw error
  }
}

export async function fetchAdminDashboardStats(): Promise<AdminDashboardStats> {
  return withMockFallback(
    () => apiRequest<AdminDashboardStats>("/api/v1/admin/dashboard/stats"),
    () => ({
      pendingUsers: mockAdminUsers.filter((user) => user.status === "PENDING").length,
      totalUsers: mockAdminUsers.length,
      todayLogs: MOCK_ADMIN_LOGS.length,
      unusedInviteCodes: MOCK_INVITE_CODES.filter((code) => code.status === "UNUSED").length,
      cocLogs: MOCK_ADMIN_LOGS.filter((log) => log.category === "COC").length,
    })
  )
}

/** RQ-ADMIN-150 · GET /api/v1/admin/dashboard/analysis-stats */
export async function fetchAdminAnalysisStats(): Promise<AdminAnalysisStats> {
  return withMockFallback(
    () => apiRequest<AdminAnalysisStats>("/api/v1/admin/dashboard/analysis-stats"),
    () => mockAdminAnalysisStats()
  )
}

export async function fetchAdminUsageTrend(days = 7): Promise<AdminUsageTrendPoint[]> {
  return withMockFallback(
    async () => {
      const [analysisLogs, authLogs] = await Promise.all([
        fetchAdminLogs({ category: "ANALYSIS", page: 0, size: 200 }),
        fetchAdminLogs({ category: "AUTH", page: 0, size: 200 }),
      ])
      return aggregateUsageTrend(analysisLogs.items, authLogs.items, days)
    },
    () => aggregateUsageTrend(MOCK_ADMIN_LOGS, MOCK_ADMIN_LOGS, days)
  )
}

export async function fetchAdminDashboardOverview(): Promise<AdminDashboardOverview> {
  return withMockFallback(
    async () => {
      const [stats, approvedPage, allUsersPage, recentLogs, trend, analysisLogs] =
        await Promise.all([
          fetchAdminDashboardStats(),
          fetchAdminUsers({ status: "APPROVED", page: 0, size: 1 }),
          fetchAdminUsers({ page: 0, size: 1 }),
          fetchAdminLogs({ page: 0, size: 5 }),
          fetchAdminUsageTrend(7),
          fetchAdminLogs({ category: "ANALYSIS", page: 0, size: 200 }),
        ])

      return {
        stats,
        approvedUsers: approvedPage.total,
        departmentCount: recentLogs.departments.length,
        todayAnalysis: countTodayAnalysis(analysisLogs.items),
        recentLogs: recentLogs.items,
        trend,
        menuBadges: {
          users: allUsersPage.total,
          approvals: stats.pendingUsers,
          logs: stats.todayLogs,
          inviteCodes: stats.unusedInviteCodes,
        },
      }
    },
    () => {
      const stats = {
        pendingUsers: mockAdminUsers.filter((user) => user.status === "PENDING").length,
        totalUsers: mockAdminUsers.length,
        todayLogs: MOCK_ADMIN_LOGS.length,
        unusedInviteCodes: MOCK_INVITE_CODES.filter((code) => code.status === "UNUSED").length,
        cocLogs: MOCK_ADMIN_LOGS.filter((log) => log.category === "COC").length,
      }

      return {
        stats,
        approvedUsers: mockAdminUsers.filter((user) => user.status === "APPROVED").length,
        departmentCount: getLogDepartments(MOCK_ADMIN_LOGS).length,
        todayAnalysis: countTodayAnalysis(MOCK_ADMIN_LOGS),
        recentLogs: MOCK_ADMIN_LOGS.slice(0, 5),
        trend: aggregateUsageTrend(MOCK_ADMIN_LOGS, MOCK_ADMIN_LOGS, 7),
        menuBadges: {
          users: mockAdminUsers.length,
          approvals: stats.pendingUsers,
          logs: stats.todayLogs,
          inviteCodes: stats.unusedInviteCodes,
        },
      }
    }
  )
}

export async function fetchAdminUsers(options?: {
  search?: string
  status?: UserStatus | "ALL"
  page?: number
  size?: number
}): Promise<AdminUserPageResponse> {
  const params = new URLSearchParams()
  const search = options?.search?.trim()
  const page = options?.page ?? 0
  const size = options?.size ?? 10

  if (search) {
    params.set("search", search)
  }
  if (options?.status && options.status !== "ALL") {
    params.set("status", options.status)
  }
  params.set("page", String(page))
  params.set("size", String(size))

  const query = params.toString()
  return withMockFallback(
    async () => {
      const data = await apiRequest<BackendPageResponse<AdminUser>>(
        `/api/v1/admin/users?${query}`
      )
      const pageData = mapPage(data)
      return {
        ...pageData,
        items: pageData.items.map(mapAdminUser),
      }
    },
    () => {
      const filtered = mockAdminUsers.filter((user) => {
        const statusMatched =
          !options?.status || options.status === "ALL" || user.status === options.status
        const searchMatched =
          !search ||
          matchesText(user.username, search) ||
          matchesText(user.displayName, search) ||
          matchesText(user.email, search)

        return statusMatched && searchMatched
      })

      return {
        items: paginate(filtered, page, size),
        total: filtered.length,
        page,
        size,
      }
    }
  )
}

export async function approveAdminUser(
  userId: string,
  role?: AdminUser["role"]
): Promise<AdminUserStatusResponse> {
  return withMockFallback(
    () =>
      apiRequest<AdminUserStatusResponse>(`/api/v1/admin/users/${userId}/approve`, {
        method: "POST",
        body: role ? { role } : undefined,
      }),
    () => {
      const user = patchMockAdminUser(userId, {
        status: "APPROVED",
        ...(role ? { role } : {}),
      })
      return { userId: user.id, status: user.status }
    }
  )
}

export async function rejectAdminUser(userId: string): Promise<AdminUserStatusResponse> {
  return withMockFallback(
    () =>
      apiRequest<AdminUserStatusResponse>(`/api/v1/admin/users/${userId}/reject`, {
        method: "POST",
      }),
    () => {
      const user = patchMockAdminUser(userId, { status: "REJECTED" })
      return { userId: user.id, status: user.status }
    }
  )
}

export async function updateAdminUser(
  userId: string,
  payload: UpdateAdminUserPayload
): Promise<AdminUser> {
  const requestPayload = {
    displayName: payload.displayName,
    email: payload.email,
    organizationType: payload.organizationType,
    department: payload.department,
    role: payload.role,
  }

  return withMockFallback(
    () =>
      apiRequest<AdminUser>(`/api/v1/admin/users/${userId}`, {
        method: "PATCH",
        body: requestPayload,
      }).then(mapAdminUser),
    () => patchMockAdminUser(userId, payload)
  )
}

export async function suspendAdminUser(
  userId: string
): Promise<AdminUserStatusResponse> {
  return withMockFallback(
    () =>
      apiRequest<AdminUserStatusResponse>(`/api/v1/admin/users/${userId}/suspend`, {
        method: "POST",
      }),
    () => {
      const user = patchMockAdminUser(userId, { status: "SUSPENDED" })
      return { userId: user.id, status: user.status }
    }
  )
}

export async function reactivateAdminUser(
  userId: string
): Promise<AdminUserStatusResponse> {
  return withMockFallback(
    () =>
      apiRequest<AdminUserStatusResponse>(`/api/v1/admin/users/${userId}/reactivate`, {
        method: "POST",
      }),
    () => {
      const user = patchMockAdminUser(userId, { status: "APPROVED" })
      return { userId: user.id, status: user.status }
    }
  )
}

export async function resetAdminUserPassword(userId: string, newPassword: string): Promise<void> {
  await withMockFallback(
    () =>
      apiRequest<void>(`/api/v1/admin/users/${userId}/password`, {
        method: "PATCH",
        body: { newPassword },
      }),
    () => undefined
  )
}

export async function deleteAdminUser(userId: string): Promise<void> {
  await withMockFallback(
    () =>
      apiRequest<void>(`/api/v1/admin/users/${userId}`, {
        method: "DELETE",
      }),
    () => {
      patchMockAdminUser(userId, { status: "SUSPENDED" })
      return undefined
    }
  )
}

export async function fetchAdminReviewers(
  options: FetchAdminReviewersOptions = {}
): Promise<AdminReviewer[]> {
  const { department, uploaderId } = options
  const params = new URLSearchParams()
  if (department && department !== "ALL") {
    params.set("department", department)
  }
  if (uploaderId) {
    params.set("uploaderId", uploaderId)
  }
  const query = params.toString()

  return withMockFallback(
    async () => {
      const data = await apiRequest<AdminReviewerListResponse>(
        `/api/v1/admin/reviewers${query ? `?${query}` : ""}`
      )
      return data.reviewers ?? []
    },
    () => {
      const uploader = uploaderId ? mockUsers.find((user) => user.id === uploaderId) : null
      const departmentFilter = uploader?.department ?? department
      const organizationIdFilter = uploader?.organizationId ?? null

      return mockUsers
        .filter((user) => user.role === "REVIEWER")
        .filter(
          (user) =>
            !departmentFilter ||
            departmentFilter === "ALL" ||
            user.department === departmentFilter
        )
        .filter(
          (user) => !organizationIdFilter || user.organizationId === organizationIdFilter
        )
        .map((user, index) => ({
          id: user.id,
          name: user.name,
          department: user.department,
          organizationId: user.organizationId,
          organizationName: user.organizationName,
          specialties:
            index % 2 === 0
              ? ["영상 포렌식", "딥페이크 탐지"]
              : ["위변조 분석", "영상 포렌식"],
          activeCaseCount: index % 2 === 0 ? 3 : 4,
          averageDays: index % 2 === 0 ? 1.8 : 2.4,
        }))
    }
  )
}

export async function assignAdminCaseReviewer(
  caseId: string,
  reviewerId: string,
  uploaderId?: string | null
): Promise<void> {
  await withMockFallback(
    () =>
      apiRequest<void>(`/api/v1/cases/${encodeURIComponent(caseId)}/reviewer`, {
        method: "PATCH",
        body: {
          reviewerId,
          ...(uploaderId ? { uploaderId } : {}),
        },
      }),
    () => mockAssignReviewerToCase(caseId, reviewerId)
  )
}

export async function fetchAdminInviteCodes(): Promise<InviteCode[]> {
  return withMockFallback(
    async () => {
      const data = await apiRequest<InviteCode[]>("/api/v1/admin/invite-codes")
      return data.map(mapInviteCode)
    },
    () => MOCK_INVITE_CODES
  )
}

export async function createAdminInviteCode(expiresInDays = 30): Promise<InviteCode> {
  return withMockFallback(
    async () => {
      const data = await apiRequest<InviteCode>("/api/v1/admin/invite-codes", {
        method: "POST",
        body: { expiresInDays },
      })
      return mapInviteCode(data)
    },
    () => ({
      id: `mock-${Date.now()}`,
      code: "VF-MOCK-0001",
      createdAt: new Date().toISOString().slice(0, 10),
      expiresAt: new Date(Date.now() + expiresInDays * 24 * 60 * 60 * 1000)
        .toISOString()
        .slice(0, 10),
      status: "UNUSED",
    })
  )
}

export async function fetchAdminLogs(options?: {
  category?: LogCategory | "ALL"
  department?: string
  search?: string
  page?: number
  size?: number
}): Promise<AdminLogPageResponse> {
  const params = new URLSearchParams()
  const page = options?.page ?? 0
  const size = options?.size ?? 8

  if (options?.category && options.category !== "ALL") {
    params.set("category", options.category)
  }
  if (options?.department && options.department !== "ALL") {
    params.set("department", options.department)
  }
  if (options?.search?.trim()) {
    params.set("search", options.search.trim())
  }
  params.set("page", String(page))
  params.set("size", String(size))

  return withMockFallback(
    async () => {
      const data = await apiRequest<BackendPageResponse<AdminLog> & { departments?: string[] }>(
        `/api/v1/admin/logs?${params.toString()}`
      )
      return {
        ...mapPage(data),
        departments: data.departments ?? [],
      }
    },
    () => {
      const search = options?.search?.trim() ?? ""
      const filtered = MOCK_ADMIN_LOGS.filter((log) => {
        const categoryMatched =
          !options?.category || options.category === "ALL" || log.category === options.category
        const departmentMatched =
          !options?.department || options.department === "ALL" || log.department === options.department
        const searchMatched =
          !search ||
          matchesText(log.actor, search) ||
          matchesText(log.action, search) ||
          matchesText(log.detail, search)

        return categoryMatched && departmentMatched && searchMatched
      })

      return {
        items: paginate(filtered, page, size),
        total: filtered.length,
        page,
        size,
        departments: getLogDepartments(MOCK_ADMIN_LOGS),
      }
    }
  )
}

function buildAdminLogQueryParams(options?: {
  category?: LogCategory | "ALL"
  department?: string
  search?: string
}) {
  const params = new URLSearchParams({ format: "csv" })

  if (options?.category && options.category !== "ALL") {
    params.set("category", options.category)
  }
  if (options?.department && options.department !== "ALL") {
    params.set("department", options.department)
  }
  if (options?.search?.trim()) {
    params.set("search", options.search.trim())
  }

  return params
}

export async function exportAdminLogsCsv(options?: {
  category?: LogCategory | "ALL"
  department?: string
  search?: string
}): Promise<Blob> {
  const params = buildAdminLogQueryParams(options)
  return withMockFallback(
    () => apiDownload(`/api/v1/admin/logs/export?${params.toString()}`),
    () => {
      const csv = ["timestamp,category,actor,department,action,detail"]
        .concat(
          MOCK_ADMIN_LOGS.map((log) =>
            [log.timestamp, log.category, log.actor, log.department, log.action, log.detail ?? ""]
              .map((value) => `"${value.replaceAll("\"", "\"\"")}"`)
              .join(",")
          )
        )
        .join("\n")

      return new Blob([csv], { type: "text/csv;charset=utf-8" })
    }
  )
}

export async function fetchAdminProfile(): Promise<AdminProfile> {
  return withMockFallback(
    async () => mapAdminProfile(await apiRequest<AdminProfile>("/api/v1/admin/me")),
    () => MOCK_ADMIN_PROFILE
  )
}

export async function updateAdminProfile(payload: UpdateAdminProfilePayload): Promise<AdminProfile> {
  return withMockFallback(
    async () =>
      mapAdminProfile(
        await apiRequest<AdminProfile>("/api/v1/admin/me", {
          method: "PATCH",
          body: payload,
        })
      ),
    () => ({ ...MOCK_ADMIN_PROFILE, ...payload })
  )
}

export async function updateAdminPassword(
  currentPassword: string,
  newPassword: string
): Promise<void> {
  await withMockFallback(
    () =>
      apiRequest<void>("/api/v1/admin/me/password", {
        method: "PATCH",
        body: { currentPassword, newPassword },
      }),
    () => undefined
  )
}

export async function fetchAdminEvidences(options?: {
  search?: string
  fileType?: EvidenceFileType | "ALL"
  status?: EvidenceStatus | "ALL"
  page?: number
  size?: number
}): Promise<AdminEvidencePageResponse> {
  const params = new URLSearchParams()
  const page = options?.page ?? 0
  const size = options?.size ?? 10

  if (options?.search?.trim()) {
    params.set("search", options.search.trim())
  }
  if (options?.fileType && options.fileType !== "ALL") {
    params.set("fileType", options.fileType)
  }
  if (options?.status && options.status !== "ALL") {
    params.set("status", options.status)
  }
  params.set("page", String(page))
  params.set("size", String(size))

  return withMockFallback(
    async () => {
      const data = await apiRequest<BackendPageResponse<AdminEvidence>>(
        `/api/v1/admin/evidences?${params.toString()}`
      )
      return mapPage(data)
    },
    () => {
      const search = options?.search?.trim() ?? ""
      const filtered = MOCK_ADMIN_EVIDENCES.filter((evidence) => {
        const fileTypeMatched =
          !options?.fileType || options.fileType === "ALL" || evidence.fileType === options.fileType
        const statusMatched =
          !options?.status || options.status === "ALL" || evidence.status === options.status
        const searchMatched =
          !search ||
          matchesText(evidence.fileName, search) ||
          matchesText(evidence.caseName, search) ||
          matchesText(evidence.hashValue, search)

        return fileTypeMatched && statusMatched && searchMatched
      })

      return {
        items: paginate(filtered, page, size),
        total: filtered.length,
        page,
        size,
      }
    }
  )
}

export async function fetchAdminEvidenceDetail(evidenceId: string): Promise<AdminEvidenceDetail> {
  return withMockFallback(
    () => apiRequest<AdminEvidenceDetail>(`/api/v1/admin/evidences/${evidenceId}`),
    () =>
      MOCK_ADMIN_EVIDENCES.find((evidence) => evidence.id === evidenceId) ??
      MOCK_ADMIN_EVIDENCES[0]
  )
}

export async function deleteAdminEvidence(evidenceId: string, reason: string): Promise<void> {
  await withMockFallback(
    () =>
      apiRequest<void>(`/api/v1/admin/evidences/${evidenceId}`, {
        method: "DELETE",
        body: { reason },
      }),
    () => undefined
  )
}
