"use client"

import { useEffect, useRef, useState } from "react"
import Link from "next/link"
import {
  CheckCircle2,
  Clock3,
  FileVideo,
  FileCheck2,
  GitCompare,
  History,
  Layers,
  UploadCloud,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { AnalysisStatusBadge } from "@/components/analysis-status-badge"
import { CaseCreateDialog, canRegisterCase } from "@/app/mypage/_components/case-create-dialog"
import type { AnalysisStatus } from "@/lib/analysis-status"
import {
  fetchAnalysisTrend,
  fetchEvidenceStats,
  fetchRecentAnalyses,
  type AnalysisTrendPoint,
  type EvidenceStatsResponse,
  type RecentAnalysisItem,
} from "@/lib/evidence-api"
import { cn } from "@/lib/utils"
import { buildCaseDetailPath } from "@/lib/route-params"
import { getSession, type AuthSession } from "@/lib/auth"

const trustItems = [
  {
    icon: History,
    label: "CoC 감사 추적",
    className:
      "border-cyan-100 bg-cyan-50 text-cyan-700 dark:border-cyan-500/20 dark:bg-cyan-500/10 dark:text-cyan-300",
  },
  {
    icon: CheckCircle2,
    label: "SHA-256 해시 검증",
    className:
      "border-emerald-100 bg-emerald-50 text-emerald-700 dark:border-emerald-500/20 dark:bg-emerald-500/10 dark:text-emerald-300",
  },
  {
    icon: Layers,
    label: "영상 딥페이크 분석",
    className:
      "border-amber-100 bg-amber-50 text-amber-700 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-300",
  },
]

const toneClassName = {
  teal: {
    value: "text-teal-600 dark:text-teal-300",
    icon: "bg-teal-50 text-teal-600 ring-teal-100 dark:bg-teal-500/10 dark:text-teal-300 dark:ring-teal-500/20",
    dot: "bg-teal-500",
  },
  red: {
    value: "text-red-500 dark:text-red-400",
    icon: "bg-red-50 text-red-500 ring-red-100 dark:bg-red-500/10 dark:text-red-400 dark:ring-red-500/20",
    dot: "bg-red-500",
  },
  green: {
    value: "text-emerald-600 dark:text-emerald-300",
    icon: "bg-emerald-50 text-emerald-600 ring-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-300 dark:ring-emerald-500/20",
    dot: "bg-emerald-500",
  },
  orange: {
    value: "text-orange-500 dark:text-orange-300",
    icon: "bg-orange-50 text-orange-500 ring-orange-100 dark:bg-orange-500/10 dark:text-orange-300 dark:ring-orange-500/20",
    dot: "bg-orange-500",
  },
}

type Tone = keyof typeof toneClassName
type MainViewChangeEvent = CustomEvent<{ view: "dashboard" | "analysis" }>
type LoadStatus = "loading" | "success" | "error"

type DashboardDataState = {
  statsStatus: LoadStatus
  historyStatus: LoadStatus
  trendStatus: LoadStatus
  stats: EvidenceStatsResponse | null
  history: RecentAnalysisItem[]
  trendPoints: AnalysisTrendPoint[]
}

export function DashboardOverview() {
  const [view, setView] = useState<"dashboard" | "analysis">("dashboard")
  const [session, setSession] = useState<AuthSession | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [dataState, setDataState] = useState<DashboardDataState>({
    statsStatus: "loading",
    historyStatus: "loading",
    trendStatus: "loading",
    stats: null,
    history: [],
    trendPoints: [],
  })
  const locationRef = useRef("")

  useEffect(() => {
    function syncSession() {
      setSession(getSession())
    }

    syncSession()
    window.addEventListener("auth-change", syncSession)
    return () => window.removeEventListener("auth-change", syncSession)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function loadDashboardData() {
      const [statsResult, historyResult, trendResult] = await Promise.allSettled([
        fetchEvidenceStats(),
        fetchRecentAnalyses(5),
        fetchAnalysisTrend(7),
      ])

      if (cancelled) return

      setDataState({
        statsStatus: statsResult.status === "fulfilled" ? "success" : "error",
        historyStatus: historyResult.status === "fulfilled" ? "success" : "error",
        trendStatus: trendResult.status === "fulfilled" ? "success" : "error",
        stats: statsResult.status === "fulfilled" ? statsResult.value : null,
        history: historyResult.status === "fulfilled" ? historyResult.value.items : [],
        trendPoints: trendResult.status === "fulfilled" ? trendResult.value.points : [],
      })
    }

    loadDashboardData()

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    function syncViewWithHash() {
      locationRef.current = window.location.href
      const shouldOpenCreate = window.location.hash === "#new-analysis"
      setView("dashboard")

      if (shouldOpenCreate) {
        setCreateOpen(true)
        window.requestAnimationFrame(() => window.scrollTo({ top: 0 }))
      }
    }

    function syncViewWithHeader(event: Event) {
      const nextView = (event as MainViewChangeEvent).detail?.view ?? "dashboard"
      setView("dashboard")

      if (nextView === "analysis") {
        setCreateOpen(true)
        window.requestAnimationFrame(() => window.scrollTo({ top: 0 }))
        return
      }

      window.requestAnimationFrame(() => window.scrollTo({ top: 0 }))
    }

    syncViewWithHash()
    window.addEventListener("hashchange", syncViewWithHash)
    window.addEventListener("popstate", syncViewWithHash)
    window.addEventListener("main-view-change", syncViewWithHeader)
    const interval = window.setInterval(() => {
      if (locationRef.current === window.location.href) return
      syncViewWithHash()
    }, 150)

    return () => {
      window.removeEventListener("hashchange", syncViewWithHash)
      window.removeEventListener("popstate", syncViewWithHash)
      window.removeEventListener("main-view-change", syncViewWithHeader)
      window.clearInterval(interval)
    }
  }, [])

  function openAnalysis() {
    setView("dashboard")
    setCreateOpen(true)
    window.history.replaceState(null, "", window.location.pathname)
    window.requestAnimationFrame(() => window.scrollTo({ top: 0 }))
  }

  const canCreateCase = canRegisterCase(session)
  const existingCaseNames = dataState.history
    .map((item) => item.caseName)
    .filter((caseName): caseName is string => Boolean(caseName))

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-8 sm:px-8 lg:px-10">
      {view === "dashboard" ? (
        <>
          <HeroPanel onStartAnalysis={openAnalysis} canStartAnalysis={canCreateCase} />
          <StatsGrid
            status={dataState.statsStatus}
            stats={dataState.stats}
          />
          <div className="grid gap-5 lg:grid-cols-[1fr_360px]">
            <WeeklyChart
              status={dataState.trendStatus}
              points={dataState.trendPoints}
            />
            <RecentPanel
              status={dataState.historyStatus}
              analyses={dataState.history}
              onStartAnalysis={openAnalysis}
              canStartAnalysis={canCreateCase}
            />
          </div>
        </>
      ) : (
        null
      )}
      <CaseCreateDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        session={session}
        existingCaseNames={existingCaseNames}
      />
    </main>
  )
}

function HeroPanel({
  onStartAnalysis,
  canStartAnalysis,
}: {
  onStartAnalysis: () => void
  canStartAnalysis: boolean
}) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white px-6 py-8 shadow-sm sm:px-8 lg:px-9 dark:border-border dark:bg-card">
      <div className="grid items-center gap-8 md:grid-cols-[1fr_220px]">
        <div>
          <div className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-teal-50 px-3 py-1 text-[11px] font-semibold text-teal-700 dark:bg-teal-500/10 dark:text-teal-300">
            <span className="size-1.5 rounded-full bg-teal-500" />
            디지털 포렌식 증거 검증 플랫폼
          </div>
          <h1 className="text-2xl font-bold leading-tight tracking-tight text-slate-950 sm:text-3xl dark:text-foreground">
            디지털 미디어 파일
            <br />
            분석 대시보드
          </h1>
          <p className="mt-4 max-w-xl text-sm leading-6 text-slate-500 dark:text-muted-foreground">
            업로드된 영상 파일의 딥페이크 여부를 AI로 분석하고,
            디지털 서명과 체인 오브 커스터디로 증거 무결성을 보장합니다.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            {canStartAnalysis ? (
              <Button
                onClick={onStartAnalysis}
                className="h-9 rounded-md bg-teal-600 px-4 text-xs font-bold hover:bg-teal-700"
              >
                <UploadCloud className="size-4" aria-hidden="true" />
                분석 시작하기
              </Button>
            ) : null}
            <Button
              variant="outline"
              render={<Link href="/compare" />}
              nativeButton={false}
              className="h-9 rounded-md border-slate-200 px-4 text-xs font-bold text-slate-600 dark:border-border dark:text-muted-foreground"
            >
              <GitCompare className="size-4" aria-hidden="true" />
              비교 검증
            </Button>
          </div>
        </div>

        <div className="flex flex-col gap-3">
          {trustItems.map((item) => (
            <div
              key={item.label}
              className={cn(
                "flex items-center gap-3 rounded-lg border px-4 py-3 text-xs font-bold shadow-sm",
                item.className
              )}
            >
              <span className="flex size-7 items-center justify-center rounded-md bg-white/80 dark:bg-white/10">
                <item.icon className="size-4" aria-hidden="true" />
              </span>
              {item.label}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

type DashboardStatItem = {
  label: string
  value: string
  unit: string
  icon: typeof FileCheck2
  tone: Tone
}

function buildDashboardStats(stats: EvidenceStatsResponse | null): DashboardStatItem[] {
  return [
    {
      label: "총 분석 건수",
      value: String(stats?.totalAnalysisCount ?? 0),
      unit: "건",
      icon: FileCheck2,
      tone: "teal",
    },
    {
      label: "딥페이크 의심",
      value: String(stats?.deepfakeDetectedCount ?? 0),
      unit: "건",
      icon: FileVideo,
      tone: "red",
    },
    {
      label: "검증 완료",
      value: String(stats?.completedCount ?? 0),
      unit: "건",
      icon: CheckCircle2,
      tone: "green",
    },
    {
      label: "처리 중",
      value: String(stats?.inProgressCount ?? 0),
      unit: "건",
      icon: Clock3,
      tone: "orange",
    },
  ]
}

function StatsGrid({
  status,
  stats,
}: {
  status: LoadStatus
  stats: EvidenceStatsResponse | null
}) {
  if (status === "loading") {
    return (
      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="분석 통계 로딩">
        {Array.from({ length: 4 }).map((_, index) => (
          <article
            key={index}
            className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-border dark:bg-card"
          >
            <div className="h-4 w-24 animate-pulse rounded bg-slate-100 dark:bg-muted" />
            <div className="mt-6 h-8 w-14 animate-pulse rounded bg-slate-100 dark:bg-muted" />
            <div className="mt-3 h-3 w-8 animate-pulse rounded bg-slate-100 dark:bg-muted" />
          </article>
        ))}
      </section>
    )
  }

  if (status === "error") {
    return (
      <section
        className="rounded-xl border border-amber-200 bg-amber-50 dark:border-amber-500/25 dark:bg-amber-500/10 px-5 py-4 text-sm font-semibold text-amber-800 dark:text-amber-200"
        aria-label="분석 통계 오류"
      >
        통계 데이터를 불러오지 못했습니다. 최근 분석 이력은 계속 확인할 수 있습니다.
      </section>
    )
  }

  const dashboardStats = buildDashboardStats(stats)

  return (
    <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="분석 통계">
      {dashboardStats.map((stat) => {
        const tone = toneClassName[stat.tone]

        return (
          <article
            key={stat.label}
            className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-border dark:bg-card"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-medium text-slate-500 dark:text-muted-foreground">{stat.label}</p>
                <p className={cn("mt-4 text-3xl font-bold leading-none", tone.value)}>
                  {stat.value}
                </p>
                <p className="mt-2 text-xs font-medium text-slate-500 dark:text-muted-foreground">{stat.unit}</p>
              </div>
              <span className={cn("flex size-8 items-center justify-center rounded-md ring-1", tone.icon)}>
                <stat.icon className="size-4" aria-hidden="true" />
              </span>
            </div>
          </article>
        )
      })}
    </section>
  )
}

function formatTrendDateLabel(isoDate: string) {
  const [, month, day] = isoDate.split("-")
  return `${month}/${day}`
}

function WeeklyChart({
  status,
  points,
}: {
  status: LoadStatus
  points: AnalysisTrendPoint[]
}) {
  const chartPoints = points.map((point) => ({
    date: formatTrendDateLabel(point.date),
    value: point.completedCount,
  }))

  if (status === "loading") {
    return (
      <section
        className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-border dark:bg-card"
        aria-label="최근 7일 분석 현황 로딩"
      >
        <div className="mb-4 h-4 w-40 animate-pulse rounded bg-slate-100 dark:bg-muted" />
        <div className="h-[220px] animate-pulse rounded-lg bg-slate-100 dark:bg-muted" />
      </section>
    )
  }

  if (status === "error" || chartPoints.length === 0) {
    return (
      <section
        className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-border dark:bg-card"
        aria-label="최근 7일 분석 현황"
      >
        <div className="mb-4">
          <h2 className="text-sm font-bold text-slate-900 dark:text-foreground">최근 7일 분석 현황</h2>
          <p className="text-xs text-slate-500 dark:text-muted-foreground">일별 처리 건수</p>
        </div>
        <div className="flex h-[220px] items-center justify-center rounded-lg border border-dashed border-slate-200 text-xs font-semibold text-slate-400 dark:border-border dark:text-muted-foreground">
          {status === "error"
            ? "차트 데이터를 불러오지 못했습니다."
            : "최근 7일간 완료된 분석이 없습니다."}
        </div>
      </section>
    )
  }

  const maxValue = Math.max(1, ...chartPoints.map((point) => point.value))
  const width = 680
  const height = 210
  const padding = { top: 18, right: 18, bottom: 36, left: 34 }
  const graphWidth = width - padding.left - padding.right
  const graphHeight = height - padding.top - padding.bottom
  const pointsOnChart = chartPoints.map((point, index) => {
    const x =
      padding.left +
      (chartPoints.length === 1
        ? graphWidth / 2
        : (index / (chartPoints.length - 1)) * graphWidth)
    const y = padding.top + graphHeight - (point.value / maxValue) * graphHeight
    return { ...point, x, y }
  })
  const line = pointsOnChart.map((point) => `${point.x},${point.y}`).join(" ")
  const area = `${padding.left},${padding.top + graphHeight} ${line} ${
    padding.left + graphWidth
  },${padding.top + graphHeight}`

  const yTicks = Array.from(new Set([0, Math.ceil(maxValue / 2), maxValue])).sort(
    (a, b) => a - b
  )

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-border dark:bg-card" aria-label="최근 7일 분석 현황">
      <div className="mb-4">
        <h2 className="text-sm font-bold text-slate-900 dark:text-foreground">최근 7일 분석 현황</h2>
        <p className="text-xs text-slate-500 dark:text-muted-foreground">일별 처리 건수</p>
      </div>
      <div className="overflow-hidden rounded-lg">
        <svg viewBox={`0 0 ${width} ${height}`} className="h-[220px] w-full" role="img" aria-label="최근 7일 분석 건수 선 그래프">
          <defs>
            <linearGradient id="chartFill" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor="#14b8a6" stopOpacity="0.16" />
              <stop offset="100%" stopColor="#14b8a6" stopOpacity="0" />
            </linearGradient>
          </defs>
          {yTicks.map((tick) => {
            const y = padding.top + graphHeight - (tick / maxValue) * graphHeight
            return (
              <g key={tick}>
                <text x={padding.left - 12} y={y + 4} textAnchor="end" className="fill-slate-400 text-[10px]">
                  {tick}
                </text>
              </g>
            )
          })}
          <polygon points={area} fill="url(#chartFill)" />
          <polyline points={line} fill="none" stroke="#0f9f94" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
          {pointsOnChart.map((point) => (
            <g key={point.date}>
              <circle cx={point.x} cy={point.y} r="4" fill="#0f9f94" stroke="#ffffff" strokeWidth="2" />
              <text x={point.x} y={height - 10} textAnchor="middle" className="fill-slate-400 text-[10px]">
                {point.date}
              </text>
            </g>
          ))}
        </svg>
      </div>
    </section>
  )
}

function RecentPanel({
  status,
  analyses,
  onStartAnalysis,
  canStartAnalysis,
}: {
  status: LoadStatus
  analyses: RecentAnalysisItem[]
  onStartAnalysis: () => void
  canStartAnalysis: boolean
}) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-border dark:bg-card" aria-label="최근 분석">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-bold text-slate-900 dark:text-foreground">최근 분석 이력</h2>
        <Link href="/mypage" className="text-xs font-bold text-teal-600 hover:text-teal-700">
          전체 보기 →
        </Link>
      </div>
      <RecentAnalysisList status={status} analyses={analyses} />
      {canStartAnalysis ? (
        <Button
          onClick={onStartAnalysis}
          className="mt-4 h-9 w-full rounded-md bg-teal-50 text-xs font-bold text-teal-700 hover:bg-teal-100 dark:bg-teal-500/10 dark:text-teal-300 dark:hover:bg-teal-500/20"
        >
          사건 등록 →
        </Button>
      ) : null}
    </section>
  )
}

function RecentAnalysisList({
  status,
  analyses,
}: {
  status: LoadStatus
  analyses: RecentAnalysisItem[]
}) {
  if (status === "loading") {
    return (
      <div className="space-y-3" aria-label="최근 분석 이력 로딩">
        {Array.from({ length: 3 }).map((_, index) => (
          <div
            key={index}
            className="rounded-lg border border-slate-200 px-4 py-3 dark:border-border"
          >
            <div className="h-4 w-36 animate-pulse rounded bg-slate-100 dark:bg-muted" />
            <div className="mt-2 h-3 w-24 animate-pulse rounded bg-slate-100 dark:bg-muted" />
          </div>
        ))}
      </div>
    )
  }

  if (status === "error") {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-500/25 dark:bg-amber-500/10 px-4 py-6 text-center text-xs font-semibold text-amber-800 dark:text-amber-200">
        최근 분석 이력을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
      </div>
    )
  }

  if (analyses.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-xs font-medium text-slate-500 dark:border-border dark:text-muted-foreground">
        아직 분석 이력이 없습니다.
      </div>
    )
  }

  return (
    <ul className="space-y-3">
      {analyses.slice(0, 4).map((analysis) => {
        const href = analysis.caseId
          ? buildCaseDetailPath(analysis.caseId, analysis.evidenceId)
          : "/mypage"

        return (
          <li key={analysis.analysisRequestId}>
            <Link
              href={href}
              className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-4 py-3 transition-colors hover:border-teal-200 hover:bg-teal-50/40 dark:border-border dark:hover:border-teal-500/30 dark:hover:bg-teal-500/10"
            >
              <div className="min-w-0">
                <p className="truncate text-xs font-bold text-slate-700 dark:text-foreground">{analysis.fileName}</p>
                <p className="mt-1 text-[11px] text-slate-400 dark:text-muted-foreground">
                  {formatDashboardDate(analysis.requestedAt)}
                  {typeof analysis.riskScore === "number" ? ` · 위험도 ${Math.round(analysis.riskScore)}` : ""}
                </p>
              </div>
              <AnalysisStatusBadge
                status={normalizeDashboardStatus(analysis.status)}
                className="hidden sm:inline-flex"
              />
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

function normalizeDashboardStatus(status: RecentAnalysisItem["status"]): AnalysisStatus {
  if (status === "PROCESSING" || status === "COMPLETED" || status === "FAILED") {
    return status
  }

  return "PENDING"
}

function formatDashboardDate(value: string) {
  return new Date(value).toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })
}
