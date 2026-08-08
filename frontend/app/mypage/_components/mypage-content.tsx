"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import {
  ChevronLeft,
  ChevronRight,
  Loader2,
  Search,
} from "lucide-react"
import type { CaseStatus, CaseSummary } from "@/app/mypage/_types/case"
import { CaseCreateDialog, canRegisterCase } from "@/app/mypage/_components/case-create-dialog"
import { CaseHistorySection } from "@/app/mypage/_components/case-history-section"
import { fetchMyAnalysisHistory } from "@/lib/api/mypage"
import { isUnauthorizedError } from "@/lib/api/errors"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { getSession, type AuthSession } from "@/lib/auth"
import { getAppUserFromSession, isReviewer } from "@/lib/permissions"
import { useUserSettings } from "@/hooks/use-user-settings"

const statusFilters: Array<{ label: string; value: "ALL" | CaseStatus }> = [
  { label: "전체", value: "ALL" },
  { label: "등록 대기", value: "PENDING" },
  { label: "처리 중", value: "PROCESSING" },
  { label: "분석 완료", value: "COMPLETED" },
  { label: "오류", value: "FAILED" },
]

const CASE_LIST_PAGE_SIZE = 10
const SEARCH_DEBOUNCE_MS = 300

export function MyPageContent() {
  const [cases, setCases] = useState<CaseSummary[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [serverTotalPages, setServerTotalPages] = useState(1)
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [query, setQuery] = useState("")
  const [debouncedQuery, setDebouncedQuery] = useState("")
  const [statusFilter, setStatusFilter] = useState<"ALL" | CaseStatus>("ALL")
  const [currentPage, setCurrentPage] = useState(1)
  const [session, setSession] = useState<AuthSession | null>(() => getSession())
  const [createOpen, setCreateOpen] = useState(false)
  const { settings } = useUserSettings()
  const pageSize = CASE_LIST_PAGE_SIZE
  const sort = settings.listSort
  const requestPage = currentPage - 1
  const requestSize = pageSize

  useEffect(() => {
    function syncSession() {
      setSession(getSession())
    }

    syncSession()
    window.addEventListener("auth-change", syncSession)
    return () => window.removeEventListener("auth-change", syncSession)
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim())
    }, SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [query])

  useEffect(() => {
    let cancelled = false

    async function loadCases() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const response = await fetchMyAnalysisHistory({
          sort,
          page: requestPage,
          size: requestSize,
          status: statusFilter,
          q: debouncedQuery,
        })
        if (cancelled) return
        const nextServerTotalPages = Math.max(1, response.totalPages)
        setCases(response.content)
        setTotalCount(response.totalElements)
        setServerTotalPages(nextServerTotalPages)
        if (requestPage + 1 > nextServerTotalPages) {
          setCurrentPage(nextServerTotalPages)
        }
      } catch (error) {
        if (cancelled) return
        if (isUnauthorizedError(error)) {
          setErrorMessage("사건 목록을 보려면 로그인이 필요합니다.")
        } else {
          setErrorMessage("사건 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.")
        }
        setCases([])
        setTotalCount(0)
        setServerTotalPages(1)
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    loadCases()
    return () => {
      cancelled = true
    }
  }, [requestPage, requestSize, sort, statusFilter, debouncedQuery])

  const currentUser = getAppUserFromSession(session)
  const canCreateCase = canRegisterCase(session)
  const isReviewerView = currentUser ? isReviewer(currentUser) : false
  const permissionMessage = session && !currentUser
    ? "권한 정보를 확인할 수 없습니다. 다시 로그인해 주세요."
    : null

  const totalPages = serverTotalPages
  const currentVisiblePage = Math.min(currentPage, totalPages)
  const pageStart = totalCount === 0 ? 0 : (currentVisiblePage - 1) * pageSize + 1
  const pageEnd = Math.min((currentVisiblePage - 1) * pageSize + cases.length, totalCount)

  return (
    <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <p className="text-sm font-bold text-teal-600">Case Management</p>
          <h1 className="mt-1 text-3xl font-bold tracking-normal text-foreground">
            사건 관리
          </h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
            {isReviewerView
              ? "배정된 사건의 분석 결과와 검토 상태를 확인합니다."
              : "사건을 먼저 등록하고, 사건 상세에서 증거를 추가한 뒤 필요한 분석을 실행합니다."}
          </p>
        </div>
        {canCreateCase ? (
          <Button
            className="h-9 rounded-lg bg-teal-600 px-4 text-sm font-bold hover:bg-teal-700"
            onClick={() => setCreateOpen(true)}
          >
            사건 등록
          </Button>
        ) : null}
      </div>

      <CaseCreateDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        session={session}
        existingCaseNames={cases.map((item) => item.caseName)}
      />

      <section className="overflow-hidden rounded-xl border border-border bg-card">
        <div className="border-b border-border px-5 py-4">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h2 className="text-base font-bold text-card-foreground">
                사건 목록
              </h2>
              <p className="mt-0.5 text-xs text-muted-foreground">
                총 {totalCount}건 · {cases.length}건 표시
              </p>
            </div>
            <label className="relative block w-full lg:w-80">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <input
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value)
                  setCurrentPage(1)
                }}
                placeholder="사건명 또는 증거 ID 검색"
                className="h-10 w-full rounded-lg border border-border bg-background pl-9 pr-3 text-sm font-medium outline-none transition-colors placeholder:text-muted-foreground focus:border-teal-300 focus:ring-4 focus:ring-teal-100"
              />
            </label>
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            {statusFilters.map((filter) => {
              const selected = statusFilter === filter.value

              return (
                <button
                  key={filter.value}
                  type="button"
                  onClick={() => {
                    setStatusFilter(filter.value)
                    setCurrentPage(1)
                  }}
                  className={cn(
                    "h-9 rounded-full border px-4 text-sm font-bold transition-colors",
                    selected
                      ? "border-teal-600 bg-teal-600 text-white"
                      : "border-border bg-background text-muted-foreground hover:border-teal-200 hover:bg-teal-50 hover:text-teal-700"
                  )}
                  aria-pressed={selected}
                >
                  {filter.label}
                </button>
              )
            })}
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center gap-2 px-5 py-16 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" />
            사건 목록을 불러오는 중...
          </div>
        ) : errorMessage || permissionMessage ? (
          <div className="space-y-4 px-5 py-16 text-center">
            <p className="text-sm text-muted-foreground">{errorMessage ?? permissionMessage}</p>
            {(errorMessage ?? permissionMessage ?? "").includes("로그인") && (
              <Button render={<Link href="/login" />} nativeButton={false}>
                로그인하기
              </Button>
            )}
          </div>
        ) : (
          <>
            <CaseHistorySection cases={cases} />
            {totalCount > 0 ? (
              <div className="flex flex-col gap-3 border-t border-border px-5 py-4 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
                <span>
                  {pageStart}-{pageEnd} / {totalCount}건 · {currentVisiblePage}/{totalPages} 페이지
                </span>
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={currentVisiblePage <= 1}
                    onClick={() => setCurrentPage(Math.max(1, currentVisiblePage - 1))}
                  >
                    <ChevronLeft className="size-3.5" aria-hidden="true" />
                    이전
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={currentVisiblePage >= totalPages}
                    onClick={() => setCurrentPage(Math.min(totalPages, currentVisiblePage + 1))}
                  >
                    다음
                    <ChevronRight className="size-3.5" aria-hidden="true" />
                  </Button>
                </div>
              </div>
            ) : null}
          </>
        )}
      </section>
    </main>
  )
}
