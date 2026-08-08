import type { CaseStatus, CaseSummary } from "@/app/mypage/_types/case"
import type { AuthSession } from "@/lib/auth"

export type UserRole = "ORG_ADMIN" | "INVESTIGATOR" | "REVIEWER"
export type NormalizedUserRole = UserRole | "UNKNOWN"

export type ReviewStatus =
  | "NONE"
  | "REVIEW_REQUESTED"
  | "REVIEW_ASSIGNED"
  | "REVIEW_COMPLETED"
  | "REPORT_APPROVED"
  | "REVIEW_SUPPLEMENT_REQUESTED"
  | "SUPPLEMENT_REQUESTED"
  | "REVIEW_REVISION_REQUESTED"
  | "REVISION_REQUESTED"
  | "REVIEW_NEEDS_CHANGES"

export type AiResult = "낮음" | "검토 필요" | "위험"

export type AppUser = {
  id: string
  name: string
  organizationId: string
  organizationName: string
  department: string
  role: UserRole
}

export type CaseAccessItem = {
  organizationId?: string | null
  createdBy?: string | null
  assigneeId?: string | null
  reviewerId?: string | null
  reviewStatus?: ReviewStatus | null
  status?: CaseStatus | string | null
}

export const roleLabelMap: Record<UserRole, string> = {
  ORG_ADMIN: "관리자",
  INVESTIGATOR: "분석관",
  REVIEWER: "검토자",
}

export function getRoleLabel(role?: string | null, fallback = "역할 미지정") {
  const normalized = normalizeUserRole(role)
  if (normalized !== "UNKNOWN") {
    return roleLabelMap[normalized]
  }
  const raw = role?.trim()
  return raw || fallback
}

export const reviewStatusLabelMap: Record<ReviewStatus, string> = {
  NONE: "배정대기",
  REVIEW_REQUESTED: "배정대기",
  REVIEW_ASSIGNED: "검토중",
  REVIEW_COMPLETED: "승인",
  REPORT_APPROVED: "승인",
  REVIEW_SUPPLEMENT_REQUESTED: "재검토",
  SUPPLEMENT_REQUESTED: "재검토",
  REVIEW_REVISION_REQUESTED: "재검토",
  REVISION_REQUESTED: "재검토",
  REVIEW_NEEDS_CHANGES: "재검토",
}

export const mockUsers: AppUser[] = [
  {
    id: "user-001",
    name: "김민희",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "서울청 사이버수사팀",
    role: "INVESTIGATOR",
  },
  {
    id: "user-002",
    name: "박검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "서울청 사이버수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-004",
    name: "김검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "서울청 사이버수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-005",
    name: "이검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "서울청 사이버수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-006",
    name: "오검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "서울청 디지털포렌식팀",
    role: "REVIEWER",
  },
  {
    id: "user-007",
    name: "한검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "서울청 디지털포렌식팀",
    role: "REVIEWER",
  },
  {
    id: "user-008",
    name: "문검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "서울청 디지털포렌식팀",
    role: "REVIEWER",
  },
  {
    id: "user-009",
    name: "서검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "부산청 사이버범죄수사대",
    role: "REVIEWER",
  },
  {
    id: "user-010",
    name: "남검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "부산청 사이버범죄수사대",
    role: "REVIEWER",
  },
  {
    id: "user-011",
    name: "신검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "부산청 사이버범죄수사대",
    role: "REVIEWER",
  },
  {
    id: "user-012",
    name: "권검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "대구청 디지털증거분석팀",
    role: "REVIEWER",
  },
  {
    id: "user-013",
    name: "백검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "대구청 디지털증거분석팀",
    role: "REVIEWER",
  },
  {
    id: "user-014",
    name: "윤검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "대구청 디지털증거분석팀",
    role: "REVIEWER",
  },
  {
    id: "user-015",
    name: "임검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "인천청 지능범죄수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-016",
    name: "장검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "인천청 지능범죄수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-017",
    name: "조검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "인천청 지능범죄수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-018",
    name: "최검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "경기남부청 사이버수사대",
    role: "REVIEWER",
  },
  {
    id: "user-019",
    name: "강검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "경기남부청 사이버수사대",
    role: "REVIEWER",
  },
  {
    id: "user-020",
    name: "배검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "경기남부청 사이버수사대",
    role: "REVIEWER",
  },
  {
    id: "user-021",
    name: "양검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "대전청 형사기동수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-022",
    name: "송검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "대전청 형사기동수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-023",
    name: "홍검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "대전청 형사기동수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-024",
    name: "류검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "광주청 여성청소년수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-025",
    name: "민검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "광주청 여성청소년수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-026",
    name: "진검토",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "광주청 여성청소년수사팀",
    role: "REVIEWER",
  },
  {
    id: "user-003",
    name: "이관리",
    organizationId: "org-police-seoul",
    organizationName: "서울경찰청",
    department: "관리자실",
    role: "ORG_ADMIN",
  },
]

export function normalizeUserRole(role?: string | null): NormalizedUserRole {
  const normalized = (role ?? "").trim().toUpperCase()
  if (normalized === "ORG_ADMIN" || normalized === "ROLE_ORG_ADMIN" || normalized === "ADMIN" || normalized === "ROLE_ADMIN") {
    return "ORG_ADMIN"
  }
  if (normalized === "INVESTIGATOR" || normalized === "ROLE_INVESTIGATOR") {
    return "INVESTIGATOR"
  }
  if (normalized === "REVIEWER" || normalized === "ROLE_REVIEWER") {
    return "REVIEWER"
  }
  // BE UserRole is only ROLE_USER | ROLE_ADMIN. Map general users to investigator so case registration is allowed.
  if (normalized === "USER" || normalized === "ROLE_USER") {
    return "INVESTIGATOR"
  }
  return "UNKNOWN"
}

export function getMockUserByRole(role: UserRole) {
  return mockUsers.find((user) => user.role === role) ?? mockUsers[0]
}

function getReadableSessionName(name: string | null | undefined) {
  const trimmed = name?.trim()
  if (!trimmed) return null
  if (/^\d+$/.test(trimmed)) return null
  return trimmed
}

export function getAppUserFromSession(session: AuthSession | null): AppUser | null {
  if (!session) return null

  const role = normalizeUserRole(session.role)
  if (role === "UNKNOWN") return null
  const shouldUseDemoUser = session.token.startsWith("mock-")
  const mappedMockUser =
    shouldUseDemoUser && session.loginId === "1111"
      ? getMockUserByRole("INVESTIGATOR")
      : shouldUseDemoUser && session.loginId === "5555"
        ? getMockUserByRole("REVIEWER")
        : shouldUseDemoUser && (session.loginId === "3333" || session.loginId === "9999")
          ? getMockUserByRole("ORG_ADMIN")
          : null
  const base = mappedMockUser ?? getMockUserByRole(role)

  return {
    ...base,
    id: mappedMockUser ? base.id : String(session.userId || base.id),
    name: getReadableSessionName(session.name) ?? base.name,
    role,
  }
}

export function isOrgAdmin(user: AppUser) {
  return user.role === "ORG_ADMIN"
}

export function isInvestigator(user: AppUser) {
  return user.role === "INVESTIGATOR"
}

export function isReviewer(user: AppUser) {
  return user.role === "REVIEWER"
}

function normalizeOrganizationId(organizationId?: string | null) {
  const normalized = organizationId?.trim().toLowerCase()
  if (!normalized) return null
  // FE mock seed uses org-police-seoul while BE returns org-police (OrgType).
  return normalized.replace(/-seoul$/, "")
}

export function isSameOrganization(user: AppUser, caseItem: CaseAccessItem) {
  const caseOrg = normalizeOrganizationId(caseItem.organizationId ?? user.organizationId)
  const userOrg = normalizeOrganizationId(user.organizationId)
  if (!caseOrg || !userOrg) return true
  return caseOrg === userOrg
}

export function isCaseOwner(user: AppUser, caseItem: CaseAccessItem) {
  return caseItem.createdBy === user.id || caseItem.assigneeId === user.id
}

export function isAssignedReviewer(user: AppUser, caseItem: CaseAccessItem) {
  return caseItem.reviewerId === user.id
}

export function canViewCase(user: AppUser | null | undefined, caseItem: CaseAccessItem) {
  if (!user) return false
  if (!isSameOrganization(user, caseItem)) return false
  if (isOrgAdmin(user)) return true
  if (isCaseOwner(user, caseItem)) return true
  if (isAssignedReviewer(user, caseItem)) return true
  return false
}

export function canCreateCase(user: AppUser | null | undefined) {
  if (!user) return false
  return isOrgAdmin(user) || isInvestigator(user)
}

export function canUploadEvidence(user: AppUser | null | undefined, caseItem: CaseAccessItem) {
  if (!user) return false
  if (!canViewCase(user, caseItem)) return false
  return isOrgAdmin(user) || isCaseOwner(user, caseItem)
}

export function canRequestAnalysis(user: AppUser | null | undefined, caseItem: CaseAccessItem) {
  if (!user) return false
  if (!canViewCase(user, caseItem)) return false
  return isOrgAdmin(user) || isCaseOwner(user, caseItem)
}

export function canRequestReview(user: AppUser | null | undefined, caseItem: CaseAccessItem) {
  if (!user) return false
  if (!canViewCase(user, caseItem)) return false
  return (
    (isOrgAdmin(user) || isCaseOwner(user, caseItem)) &&
    caseItem.status === "COMPLETED" &&
    (caseItem.reviewStatus ?? "NONE") === "NONE"
  )
}

export function canAssignReviewer(user: AppUser | null | undefined, caseItem: CaseAccessItem) {
  if (!user) return false
  if (!canViewCase(user, caseItem)) return false
  const reviewStatus = caseItem.reviewStatus ?? "NONE"
  return (
    isOrgAdmin(user) &&
    caseItem.status === "COMPLETED" &&
    (reviewStatus === "NONE" || reviewStatus === "REVIEW_REQUESTED")
  )
}

export function canApproveReport(user: AppUser | null | undefined, caseItem: CaseAccessItem) {
  if (!user) return false
  if (!canViewCase(user, caseItem)) return false
  return isOrgAdmin(user) || isAssignedReviewer(user, caseItem)
}

export function canDeleteCase(user: AppUser | null | undefined, caseItem: CaseAccessItem) {
  if (!user) return false
  if (!canViewCase(user, caseItem)) return false
  return isOrgAdmin(user)
}

export function getVisibleCases(user: AppUser | null, cases: CaseSummary[]) {
  if (!user) return []
  return cases.filter((caseItem) => canViewCase(user, caseItem))
}
