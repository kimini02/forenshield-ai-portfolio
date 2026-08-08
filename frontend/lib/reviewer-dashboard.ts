import type { CaseSummary } from "@/app/mypage/_types/case"
import type { ReviewStatus } from "@/lib/permissions"

const PENDING_REVIEW_STATUSES = new Set<ReviewStatus>(["REVIEW_ASSIGNED"])

const APPROVED_REVIEW_STATUSES = new Set<ReviewStatus>([
  "REVIEW_COMPLETED",
  "REPORT_APPROVED",
])

const REVISION_REVIEW_STATUSES = new Set<ReviewStatus>([
  "REVIEW_SUPPLEMENT_REQUESTED",
  "SUPPLEMENT_REQUESTED",
  "REVIEW_REVISION_REQUESTED",
  "REVISION_REQUESTED",
  "REVIEW_NEEDS_CHANGES",
])

export type ReviewerDashboardStats = {
  totalAssigned: number
  pendingApproval: number
  approved: number
  revisionRequested: number
}

export function summarizeReviewerCases(cases: CaseSummary[]): ReviewerDashboardStats {
  let pendingApproval = 0
  let approved = 0
  let revisionRequested = 0

  for (const item of cases) {
    const status = item.reviewStatus ?? "NONE"
    if (PENDING_REVIEW_STATUSES.has(status)) pendingApproval += 1
    if (APPROVED_REVIEW_STATUSES.has(status)) approved += 1
    if (REVISION_REVIEW_STATUSES.has(status)) revisionRequested += 1
  }

  return {
    totalAssigned: cases.length,
    pendingApproval,
    approved,
    revisionRequested,
  }
}

export function sortReviewerCases(cases: CaseSummary[]) {
  return [...cases].sort((left, right) => {
    const leftTime = Date.parse(left.reviewRequestedAt ?? left.createdAt)
    const rightTime = Date.parse(right.reviewRequestedAt ?? right.createdAt)
    return rightTime - leftTime
  })
}

export function getReviewerPriorityCases(cases: CaseSummary[]) {
  return sortReviewerCases(cases).filter((item) =>
    PENDING_REVIEW_STATUSES.has(item.reviewStatus ?? "NONE")
  )
}
