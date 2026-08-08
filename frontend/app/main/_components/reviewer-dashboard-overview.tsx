"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import {
  CheckCircle2,
  ClipboardCheck,
  Clock3,
  FileStack,
  RotateCcw,
} from "lucide-react"

import type { CaseSummary } from "@/app/mypage/_types/case"
import { Button } from "@/components/ui/button"
import { fetchMyAnalysisHistory } from "@/lib/api/mypage"
import { getApiErrorMessage } from "@/lib/api/errors"
import { reviewStatusLabelMap } from "@/lib/permissions"
import {
  getReviewerPriorityCases,
  sortReviewerCases,
  summarizeReviewerCases,
  type ReviewerDashboardStats,
} from "@/lib/reviewer-dashboard"
import { buildCaseDetailPath } from "@/lib/route-params"
import { cn } from "@/lib/utils"

type LoadStatus = "loading" | "success" | "error"

const FETCH_SIZE = 100

export function ReviewerDashboardOverview() {
  const [status, setStatus] = useState<LoadStatus>("loading")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [cases, setCases] = useState<CaseSummary[]>([])

  useEffect(() => {
    let cancelled = false

    async function loadAssignedCases() {
      setStatus("loading")
      setErrorMessage(null)

      try {
        const response = await fetchMyAnalysisHistory({
          sort: "newest",
          page: 0,
          size: FETCH_SIZE,
          status: "ALL",
        })
        if (cancelled) return
        setCases(response.content)
        setStatus("success")
      } catch (error) {
        if (cancelled) return
        setCases([])
        setStatus("error")
        setErrorMessage(
          getApiErrorMessage(error, "배정 사건 목록을 불러오지 못했습니다.")
        )
      }
    }

    void loadAssignedCases()
    return () => {
      cancelled = true
    }
  }, [])

  const stats = useMemo(() => summarizeReviewerCases(cases), [cases])
  const priorityCases = useMemo(() => getReviewerPriorityCases(cases), [cases])
  const recentCases = useMemo(() => sortReviewerCases(cases).slice(0, 5), [cases])

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-8 sm:px-8 lg:px-10">
      <ReviewerHeroPanel pendingCount={stats.pendingApproval} />
      <ReviewerStatsGrid status={status} stats={stats} />
      <div className="grid gap-5 lg:grid-cols-[1fr_360px]">
        <ReviewerPriorityPanel
          status={status}
          errorMessage={errorMessage}
          cases={priorityCases}
        />
        <ReviewerRecentPanel status={status} cases={recentCases} />
      </div>
    </main>
  )
}

function ReviewerHeroPanel({ pendingCount }: { pendingCount: number }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white px-6 py-8 shadow-sm sm:px-8 lg:px-9 dark:border-border dark:bg-card">
      <div className="grid items-center gap-8 md:grid-cols-[1fr_220px]">
        <div>
          <div className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-[11px] font-semibold text-amber-700 dark:bg-amber-500/10 dark:text-amber-300">
            <span className="size-1.5 rounded-full bg-amber-500" />
            검토자 전용 대시보드
          </div>
          <h1 className="text-2xl font-bold leading-tight tracking-tight text-slate-950 sm:text-3xl dark:text-foreground">
            배정된 사건
            <br />
            검토 현황
          </h1>
          <p className="mt-4 max-w-xl text-sm leading-6 text-slate-500 dark:text-muted-foreground">
            기관 관리자가 배정한 사건의 분석 결과를 검토하고 승인합니다.
            승인 대기 사건은 하단 바에서 최종 결정을 내릴 수 있습니다.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            <Button
              render={<Link href="/mypage" />}
              nativeButton={false}
              className="h-9 rounded-md bg-teal-600 px-4 text-xs font-bold hover:bg-teal-700"
            >
              <FileStack className="size-4" aria-hidden="true" />
              사건 관리 열기
            </Button>
            {pendingCount > 0 ? (
              <Button
                variant="outline"
                render={<Link href="/mypage" />}
                nativeButton={false}
                className="h-9 rounded-md border-amber-200 px-4 text-xs font-bold text-amber-700 dark:border-amber-500/30 dark:text-amber-300"
              >
                <ClipboardCheck className="size-4" aria-hidden="true" />
                승인 대기 {pendingCount}건
              </Button>
            ) : null}
          </div>
        </div>

        <div className="rounded-xl border border-amber-100 bg-amber-50 px-5 py-4 text-sm dark:border-amber-500/20 dark:bg-amber-500/10">
          <p className="font-bold text-amber-900 dark:text-amber-100">검토 절차</p>
          <ol className="mt-3 space-y-2 text-xs font-semibold leading-5 text-amber-800 dark:text-amber-200">
            <li>1. 배정 사건 선택</li>
            <li>2. 결과보기·무결성 확인</li>
            <li>3. 하단 바에서 검토 승인</li>
          </ol>
        </div>
      </div>
    </section>
  )
}

type ReviewerStatItem = {
  label: string
  value: string
  unit: string
  icon: typeof FileStack
  tone: "teal" | "amber" | "green" | "slate"
}

function buildReviewerStats(stats: ReviewerDashboardStats): ReviewerStatItem[] {
  return [
    {
      label: "배정 사건",
      value: String(stats.totalAssigned),
      unit: "건",
      icon: FileStack,
      tone: "teal",
    },
    {
      label: "승인 대기",
      value: String(stats.pendingApproval),
      unit: "건",
      icon: Clock3,
      tone: "amber",
    },
    {
      label: "승인 완료",
      value: String(stats.approved),
      unit: "건",
      icon: CheckCircle2,
      tone: "green",
    },
    {
      label: "재검토",
      value: String(stats.revisionRequested),
      unit: "건",
      icon: RotateCcw,
      tone: "slate",
    },
  ]
}

const reviewerToneClassName = {
  teal: {
    value: "text-teal-600 dark:text-teal-300",
    icon: "bg-teal-50 text-teal-600 ring-teal-100 dark:bg-teal-500/10 dark:text-teal-300 dark:ring-teal-500/20",
  },
  amber: {
    value: "text-amber-600 dark:text-amber-300",
    icon: "bg-amber-50 text-amber-600 ring-amber-100 dark:bg-amber-500/10 dark:text-amber-300 dark:ring-amber-500/20",
  },
  green: {
    value: "text-emerald-600 dark:text-emerald-300",
    icon: "bg-emerald-50 text-emerald-600 ring-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-300 dark:ring-emerald-500/20",
  },
  slate: {
    value: "text-slate-600 dark:text-slate-300",
    icon: "bg-slate-50 text-slate-600 ring-slate-100 dark:bg-slate-500/10 dark:text-slate-300 dark:ring-slate-500/20",
  },
} as const

function ReviewerStatsGrid({
  status,
  stats,
}: {
  status: LoadStatus
  stats: ReviewerDashboardStats
}) {
  const items = buildReviewerStats(stats)

  if (status === "loading") {
    return (
      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="검토 현황 로딩">
        {Array.from({ length: 4 }).map((_, index) => (
          <div
            key={index}
            className="h-28 animate-pulse rounded-xl border border-slate-200 bg-white dark:border-border dark:bg-card"
          />
        ))}
      </section>
    )
  }

  return (
    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="검토 현황">
      {items.map((item) => {
        const tone = reviewerToneClassName[item.tone]
        return (
          <article
            key={item.label}
            className="rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm dark:border-border dark:bg-card"
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-xs font-bold text-slate-500 dark:text-muted-foreground">
                  {item.label}
                </p>
                <p className={cn("mt-2 text-3xl font-bold tracking-tight", tone.value)}>
                  {item.value}
                  <span className="ml-1 text-base font-bold">{item.unit}</span>
                </p>
              </div>
              <span
                className={cn(
                  "flex size-10 items-center justify-center rounded-xl ring-1 ring-inset",
                  tone.icon
                )}
              >
                <item.icon className="size-5" aria-hidden="true" />
              </span>
            </div>
          </article>
        )
      })}
    </section>
  )
}

function ReviewerPriorityPanel({
  status,
  errorMessage,
  cases,
}: {
  status: LoadStatus
  errorMessage: string | null
  cases: CaseSummary[]
}) {
  return (
    <section
      className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-border dark:bg-card"
      aria-label="승인 대기 사건"
    >
      <div className="mb-4">
        <h2 className="text-sm font-bold text-slate-900 dark:text-foreground">승인 대기 사건</h2>
        <p className="text-xs text-slate-500 dark:text-muted-foreground">
          하단 바에서 검토 승인 또는 보완 요청을 진행하세요.
        </p>
      </div>

      {status === "loading" ? (
        <div className="space-y-3" aria-label="승인 대기 사건 로딩">
          {Array.from({ length: 3 }).map((_, index) => (
            <div
              key={index}
              className="h-16 animate-pulse rounded-lg border border-slate-200 dark:border-border"
            />
          ))}
        </div>
      ) : status === "error" ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-6 text-center text-xs font-semibold text-amber-800 dark:border-amber-500/25 dark:bg-amber-500/10 dark:text-amber-200">
          {errorMessage ?? "승인 대기 사건을 불러오지 못했습니다."}
        </div>
      ) : cases.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-xs font-medium text-slate-500 dark:border-border dark:text-muted-foreground">
          현재 승인 대기 중인 사건이 없습니다.
        </div>
      ) : (
        <ul className="space-y-3">
          {cases.slice(0, 6).map((item) => (
            <li key={item.caseId}>
              <ReviewerCaseLink item={item} emphasize />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function ReviewerRecentPanel({
  status,
  cases,
}: {
  status: LoadStatus
  cases: CaseSummary[]
}) {
  return (
    <section
      className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-border dark:bg-card"
      aria-label="최근 배정 사건"
    >
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-bold text-slate-900 dark:text-foreground">최근 배정 사건</h2>
        <Link href="/mypage" className="text-xs font-bold text-teal-600 hover:text-teal-700">
          전체 보기 →
        </Link>
      </div>

      {status === "loading" ? (
        <div className="space-y-3" aria-label="최근 배정 사건 로딩">
          {Array.from({ length: 3 }).map((_, index) => (
            <div
              key={index}
              className="h-14 animate-pulse rounded-lg border border-slate-200 dark:border-border"
            />
          ))}
        </div>
      ) : cases.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-xs font-medium text-slate-500 dark:border-border dark:text-muted-foreground">
          아직 배정된 사건이 없습니다.
        </div>
      ) : (
        <ul className="space-y-3">
          {cases.map((item) => (
            <li key={item.caseId}>
              <ReviewerCaseLink item={item} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function ReviewerCaseLink({
  item,
  emphasize = false,
}: {
  item: CaseSummary
  emphasize?: boolean
}) {
  const reviewStatus = item.reviewStatus ?? "NONE"
  const reviewLabel = reviewStatusLabelMap[reviewStatus]

  return (
    <Link
      href={buildCaseDetailPath(item.caseId)}
      className={cn(
        "flex items-center justify-between gap-3 rounded-lg border px-4 py-3 transition-colors",
        emphasize
          ? "border-amber-200 bg-amber-50/50 hover:border-amber-300 hover:bg-amber-50 dark:border-amber-500/30 dark:bg-amber-500/10 dark:hover:bg-amber-500/15"
          : "border-slate-200 hover:border-teal-200 hover:bg-teal-50/40 dark:border-border dark:hover:border-teal-500/30 dark:hover:bg-teal-500/10"
      )}
    >
      <div className="min-w-0">
        <p className="truncate text-xs font-bold text-slate-700 dark:text-foreground">
          {item.caseName}
        </p>
        <p className="mt-1 text-[11px] text-slate-400 dark:text-muted-foreground">
          증거 {item.evidenceCount}건
          {item.aiResult ? ` · AI ${item.aiResult}` : ""}
        </p>
      </div>
      <span
        className={cn(
          "shrink-0 rounded-full px-2 py-1 text-[10px] font-bold",
          reviewStatus === "REVIEW_ASSIGNED"
            ? "bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-200"
            : reviewStatus === "REPORT_APPROVED" || reviewStatus === "REVIEW_COMPLETED"
              ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-200"
              : "bg-slate-100 text-slate-600 dark:bg-slate-500/20 dark:text-slate-200"
        )}
      >
        {reviewLabel}
      </span>
    </Link>
  )
}
