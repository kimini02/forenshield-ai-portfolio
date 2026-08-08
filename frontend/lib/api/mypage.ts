import { apiRequest } from "@/lib/api/client"
import type { CaseStatus, CaseSummary } from "@/app/mypage/_types/case"
import type { ListSort } from "@/lib/user-settings"
import { features } from "@/lib/features"
import { mockFetchMyAnalysisHistory } from "@/lib/mock/forensic-api"
import { getSession, isMockAuthSession } from "@/lib/auth"

type AnalysisHistoryResponse = {
  content: CaseSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export async function fetchMyAnalysisHistory(options?: {
  sort?: ListSort
  page?: number
  size?: number
  status?: "ALL" | CaseStatus
  q?: string
}): Promise<AnalysisHistoryResponse> {
  if (features.mockApi || isMockAuthSession(getSession())) {
    return mockFetchMyAnalysisHistory(options)
  }

  const sort = options?.sort ?? "newest"
  const page = options?.page ?? 0
  const size = options?.size ?? 10

  const params = new URLSearchParams({
    sort,
    page: String(page),
    size: String(size),
  })

  if (options?.status && options.status !== "ALL") {
    params.set("status", options.status)
  }

  const keyword = options?.q?.trim()
  if (keyword) {
    params.set("q", keyword)
  }

  return apiRequest<AnalysisHistoryResponse>(`/api/v1/mypage/analysis-history?${params}`)
}
