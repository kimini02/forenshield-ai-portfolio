import type { CaseSummary, CaseStatus } from "@/app/mypage/_types/case"
import type { ListSort } from "@/lib/user-settings"

const STATUS_ORDER: Record<CaseStatus, number> = {
  PROCESSING: 0,
  PENDING: 1,
  FAILED: 2,
  COMPLETED: 3,
}

export function sortCases(cases: CaseSummary[], sort: ListSort): CaseSummary[] {
  const sorted = [...cases]

  if (sort === "status") {
    return sorted.sort((a, b) => {
      const statusDiff = STATUS_ORDER[a.status] - STATUS_ORDER[b.status]
      if (statusDiff !== 0) return statusDiff
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    })
  }

  return sorted.sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  )
}
