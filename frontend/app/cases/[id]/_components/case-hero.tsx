"use client"

import { useEffect, useRef } from "react"
import { Check, ChevronDown, Clock3 } from "lucide-react"

import type { CaseDetailData, CaseReviewRound } from "@/lib/api/evidence-detail"
import { formatDateTime } from "@/lib/formatters"
import { cn } from "@/lib/utils"

type CaseHeroProps = {
  data: CaseDetailData
  getStatusLabel: (status: string) => string
  reviewerName?: string | null
  requesterName?: string | null
  viewerIsReviewer?: boolean
  reviewOpen: boolean
  onReviewOpenChange: (open: boolean) => void
}

const REVIEW_STEPS = ["요청 접수", "검토자 배정", "검토 진행", "검토 완료"]

export function CaseHero({
  data,
  getStatusLabel,
  reviewerName,
  requesterName,
  viewerIsReviewer = false,
  reviewOpen,
  onReviewOpenChange,
}: CaseHeroProps) {
  const reviewStatus = data.reviewStatus ?? "NONE"
  const hasReview = reviewStatus !== "NONE"
  const supplementRequested = isSupplementReviewStatus(reviewStatus)
  const showReviewChip = hasReview && !supplementRequested
  const runningCount = data.evidences.filter((evidence) =>
    ["PENDING", "PROCESSING", "RUNNING", "QUEUED"].includes(
      String(evidence.analysisStatus ?? "").toUpperCase()
    )
  ).length
  const analysisLabel =
    data.status === "PROCESSING" && runningCount > 0
      ? `분석 중 ${runningCount}건`
      : getStatusLabel(data.status)
  const analysisTone =
    data.status === "FAILED"
      ? "border-red-200 bg-red-50 text-red-700"
      : data.status === "COMPLETED" || data.status === "PROCESSING"
        ? "border-emerald-200 bg-emerald-50 text-emerald-700"
        : "border-amber-200 bg-amber-50 text-amber-700"

  return (
    <section className="flex scroll-mt-28 flex-wrap items-center justify-between gap-x-4 gap-y-2">
      <div className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-1">
        <h1
          id="case-heading"
          className="max-w-full scroll-mt-28 truncate text-2xl font-bold tracking-normal text-slate-950 dark:text-foreground"
        >
          {data.caseName}
        </h1>
        <p className="text-sm font-medium text-muted-foreground">
          {formatDateTime(data.createdAt)}
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-sm font-bold">
        {!hasReview ? <HeroChip value={analysisLabel} className={analysisTone} /> : null}
        {showReviewChip ? (
          <ReviewStatusChip
            data={data}
            reviewerName={reviewerName}
            requesterName={requesterName}
            viewerIsReviewer={viewerIsReviewer}
            open={reviewOpen}
            onOpenChange={onReviewOpenChange}
          />
        ) : null}
        <HeroChip value={`증거 ${data.evidences.length}개`} />
      </div>
    </section>
  )
}

function HeroChip({ value, className }: { value: string; className?: string }) {
  return (
    <span
      className={cn(
        "rounded-full border border-transparent bg-muted px-3 py-1 text-muted-foreground",
        className
      )}
    >
      {value}
    </span>
  )
}

function ReviewStatusChip({
  data,
  reviewerName,
  requesterName,
  viewerIsReviewer,
  open,
  onOpenChange,
}: {
  data: CaseDetailData
  reviewerName?: string | null
  requesterName?: string | null
  viewerIsReviewer: boolean
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const triggerRef = useRef<HTMLButtonElement | null>(null)
  const wrapperRef = useRef<HTMLDivElement | null>(null)
  const popoverRef = useRef<HTMLDivElement | null>(null)
  const reviewStatus = data.reviewStatus ?? "NONE"
  const rounds = data.reviewRounds ?? []
  const supplementRequested = isSupplementReviewStatus(reviewStatus)
  const completed = reviewStatus === "REVIEW_COMPLETED" || reviewStatus === "REPORT_APPROVED"
  const currentStep =
    reviewStatus === "REVIEW_REQUESTED"
      ? 1
      : reviewStatus === "REVIEW_ASSIGNED"
        ? 2
        : supplementRequested
          ? 3
          : completed
            ? 4
            : 0
  const statusLabel =
    reviewStatus === "REVIEW_REQUESTED"
      ? "검토 · 배정대기"
      : reviewStatus === "REVIEW_ASSIGNED"
        ? `검토 진행 · ${viewerIsReviewer ? "내 담당" : reviewerName ?? ""}`
        : supplementRequested
          ? "보완 요청됨"
          : "검토 완료"
  const toneClassName =
    reviewStatus === "REVIEW_REQUESTED"
      ? "border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100"
      : supplementRequested
        ? "border-red-200 bg-red-50 text-red-700"
        : "border-emerald-200 bg-emerald-50 text-emerald-700"
  const latestRound = rounds.at(-1) ?? null
  const historyRounds =
    supplementRequested || completed ? rounds.slice(0, -1) : rounds
  const roundNumber =
    supplementRequested || completed
      ? Math.max(1, rounds.length)
      : rounds.length + 1

  useEffect(() => {
    if (!open) return

    const frame = window.requestAnimationFrame(() => popoverRef.current?.focus())

    function handlePointerDown(event: PointerEvent) {
      if (!wrapperRef.current?.contains(event.target as Node)) {
        onOpenChange(false)
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault()
        onOpenChange(false)
        triggerRef.current?.focus()
        return
      }

      if (event.key !== "Tab" || !popoverRef.current) return
      const focusable = Array.from(
        popoverRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input, textarea, select, details > summary, [tabindex]:not([tabindex="-1"])'
        )
      ).filter((element) => !element.hasAttribute("disabled"))
      if (focusable.length === 0) {
        event.preventDefault()
        popoverRef.current.focus()
        return
      }

      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener("pointerdown", handlePointerDown)
    document.addEventListener("keydown", handleKeyDown)
    return () => {
      window.cancelAnimationFrame(frame)
      document.removeEventListener("pointerdown", handlePointerDown)
      document.removeEventListener("keydown", handleKeyDown)
    }
  }, [onOpenChange, open])

  return (
    <div ref={wrapperRef} className="relative">
      <button
        id="case-review-chip"
        ref={triggerRef}
        type="button"
        aria-expanded={open}
        aria-controls="case-review-popover"
        className={cn(
          "inline-flex items-center gap-1 rounded-md border px-3 py-1 font-bold transition-colors",
          toneClassName
        )}
        onClick={() => onOpenChange(!open)}
      >
        {reviewStatus === "REVIEW_REQUESTED" ? (
          <Clock3 className="size-3.5 text-amber-700" aria-hidden="true" />
        ) : null}
        {statusLabel}
        <ChevronDown
          className={cn("size-3.5 transition-transform", open && "rotate-180")}
          aria-hidden="true"
        />
      </button>

      {open ? (
        <div
          id="case-review-popover"
          ref={popoverRef}
          role="dialog"
          tabIndex={-1}
          aria-label={statusLabel}
          className="absolute right-0 top-[calc(100%+0.5rem)] z-40 w-[280px] rounded-lg border border-slate-200 bg-white p-4 text-left shadow-xl outline-none dark:border-border dark:bg-card"
        >
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-bold text-slate-900 dark:text-foreground">{statusLabel}</p>
            {roundNumber > 1 ? (
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-600">
                {roundNumber}차
              </span>
            ) : null}
          </div>

          <ol className="mt-4 grid grid-cols-4" aria-label="검토 진행 상태">
            {REVIEW_STEPS.map((step, index) => {
              const state = index < currentStep ? "completed" : index === currentStep ? "current" : "pending"
              return (
                <li
                  key={step}
                  className="relative flex min-w-0 flex-col items-center text-center"
                  aria-current={state === "current" ? "step" : undefined}
                >
                  {index < REVIEW_STEPS.length - 1 ? (
                    <span
                      className={cn(
                        "absolute left-[calc(50%+14px)] top-3 h-px w-[calc(100%-28px)]",
                        index < currentStep ? "bg-emerald-200" : "bg-slate-200"
                      )}
                      aria-hidden="true"
                    />
                  ) : null}
                  <span
                    className={cn(
                      "relative z-10 flex size-6 items-center justify-center rounded-full border text-[11px] font-black",
                      state === "completed" && "border-emerald-600 bg-emerald-600 text-white",
                      state === "current" && "border-2 border-amber-600 bg-white text-amber-700",
                      state === "pending" && "border-slate-100 bg-slate-100 text-slate-400"
                    )}
                  >
                    {state === "completed" ? (
                      <Check className="size-3.5" strokeWidth={3} aria-hidden="true" />
                    ) : (
                      index + 1
                    )}
                    <span className="sr-only">
                      {state === "completed" ? "완료" : state === "current" ? "현재" : "미도달"}
                    </span>
                  </span>
                  <span
                    className={cn(
                      "mt-1.5 break-keep text-[10px] font-bold leading-4",
                      state === "completed" && "text-emerald-700",
                      state === "current" && "text-amber-700",
                      state === "pending" && "text-slate-400"
                    )}
                  >
                    {step}
                  </span>
                </li>
              )
            })}
          </ol>

          <div className="mt-4 border-t border-slate-100 pt-3 dark:border-border">
            <p className="text-sm font-bold text-slate-900 dark:text-foreground">
              {REVIEW_STEPS[Math.min(currentStep, REVIEW_STEPS.length - 1)]}
            </p>
            {reviewStatus === "REVIEW_REQUESTED" ? (
              <p className="mt-1.5 text-xs font-semibold leading-5 text-slate-500">
                기관 관리자가 검토자를 배정하면 알림을 보내드립니다. 지금 하실 일은 없습니다.
              </p>
            ) : reviewStatus === "REVIEW_ASSIGNED" ? (
              <p className="mt-1.5 text-xs font-semibold leading-5 text-slate-500">
                담당 {reviewerName ?? ""} · 시작 {formatMonthDay(data.reviewAssignedAt ?? data.reviewRequestedAt)}
              </p>
            ) : supplementRequested ? (
              <ReviewReasonDetails reason={latestRound?.reason ?? data.reviewerComment} />
            ) : null}
          </div>

          {historyRounds.length > 0 ? (
            <div className="mt-3 space-y-2 border-t border-slate-100 pt-3 dark:border-border">
              {historyRounds.map((round) => (
                <ReviewRoundHistory key={round.round} round={round} />
              ))}
            </div>
          ) : null}

          <p className="mt-3 border-t border-slate-100 pt-3 text-[11px] font-semibold text-slate-500 dark:border-border">
            요청 {formatMonthDayTime(data.reviewRequestedAt)} · {requesterName ?? ""}
          </p>
        </div>
      ) : null}
    </div>
  )
}

function ReviewReasonDetails({ reason }: { reason?: string | null }) {
  if (!reason) return null

  return (
    <details className="mt-1.5 text-xs font-semibold text-slate-500">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-2 [&::-webkit-details-marker]:hidden">
        <span className="min-w-0 truncate">{reason}</span>
        <span className="shrink-0 font-bold text-slate-700">상세 보기</span>
      </summary>
      <p className="mt-2 rounded-md bg-slate-50 p-2 leading-5 text-slate-600">{reason}</p>
    </details>
  )
}

function ReviewRoundHistory({ round }: { round: CaseReviewRound }) {
  const status = round.decision === "REVISION" ? "보완 요청됨" : "검토 완료"
  return (
    <details className="rounded-md bg-slate-50 p-2 text-[11px] font-semibold text-slate-600">
      <summary className="cursor-pointer list-none [&::-webkit-details-marker]:hidden">
        <span className="font-bold">[{round.round}차]</span> {status} · {round.reviewerName ?? ""} ·{" "}
        {formatMonthDay(round.decidedAt)}
        {round.reason ? (
          <span className="mt-1 block truncate">{`"${round.reason}"`}</span>
        ) : null}
        <span className="mt-1 block text-right font-bold text-slate-700">상세 보기</span>
      </summary>
      {round.reason ? <p className="mt-2 border-t border-slate-200 pt-2 leading-5">{round.reason}</p> : null}
    </details>
  )
}

function isSupplementReviewStatus(status: string) {
  return [
    "REVIEW_SUPPLEMENT_REQUESTED",
    "SUPPLEMENT_REQUESTED",
    "REVIEW_REVISION_REQUESTED",
    "REVISION_REQUESTED",
    "REVIEW_NEEDS_CHANGES",
  ].includes(status)
}

function formatMonthDay(value?: string | null) {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  return `${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`
}

function formatMonthDayTime(value?: string | null) {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  return `${formatMonthDay(value)} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`
}
