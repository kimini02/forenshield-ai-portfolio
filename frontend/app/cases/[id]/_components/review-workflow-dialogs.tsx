"use client"

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from "react"
import { AlertTriangle, Loader2, Search, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import type { AdminReviewer } from "@/lib/api/admin"
import { cn } from "@/lib/utils"

type DialogBaseProps = {
  processing: boolean
  onClose: () => void
}

export function ReviewRequestDialog({
  processing,
  rereview = false,
  onClose,
  onConfirm,
}: DialogBaseProps & {
  rereview?: boolean
  onConfirm: () => void
}) {
  const dialogRef = useDialogFocusTrap(onClose)
  const title = rereview ? "재검토 요청" : "검토 요청"

  return (
    <DialogFrame
      dialogRef={dialogRef}
      labelledBy="review-request-title"
      onClose={onClose}
      closeLabel={`${title} 닫기`}
    >
      <h3 id="review-request-title" className="text-lg font-bold text-foreground">
        {title}
      </h3>
      <p className="mt-3 text-sm font-semibold leading-6 text-muted-foreground">
        기관 관리자에게 검토를 요청합니다.
      </p>
      <div className="mt-6 flex justify-end gap-2">
        <Button type="button" variant="outline" className="h-10 font-bold" disabled={processing} onClick={onClose}>
          취소
        </Button>
        <Button
          type="button"
          variant="outline"
          className="h-10 border-slate-300 px-5 font-bold text-slate-700 hover:bg-slate-50"
          disabled={processing}
          onClick={onConfirm}
        >
          {processing ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : null}
          요청
        </Button>
      </div>
    </DialogFrame>
  )
}

export function ReviewerAssignmentDialog({
  reviewers,
  loading,
  defaultReviewerId,
  caseSummary,
  processing,
  onClose,
  onAssign,
}: DialogBaseProps & {
  reviewers: AdminReviewer[]
  loading: boolean
  defaultReviewerId?: string | null
  caseSummary: string
  onAssign: (reviewer: AdminReviewer) => void
}) {
  const dialogRef = useDialogFocusTrap(onClose)
  const [query, setQuery] = useState("")
  const [selectedId, setSelectedId] = useState(defaultReviewerId ?? "")
  const filteredReviewers = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return reviewers
    return reviewers.filter((reviewer) =>
      [reviewer.name, reviewer.department, reviewer.organizationName, ...(reviewer.specialties ?? [])]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalized))
    )
  }, [query, reviewers])
  const effectiveSelectedId =
    selectedId ||
    (defaultReviewerId && reviewers.some((item) => item.id === defaultReviewerId)
      ? defaultReviewerId
      : reviewers[0]?.id ?? "")
  const selectedReviewer =
    reviewers.find((reviewer) => reviewer.id === effectiveSelectedId) ?? null

  return (
    <DialogFrame
      dialogRef={dialogRef}
      labelledBy="reviewer-assignment-title"
      onClose={onClose}
      closeLabel="검토자 배정 닫기"
      wide
    >
      <h3 id="reviewer-assignment-title" className="text-lg font-bold text-foreground">
        검토자 배정
      </h3>
      <p className="mt-2 truncate text-sm font-semibold text-muted-foreground">{caseSummary}</p>

      <label className="relative mt-4 block">
        <span className="sr-only">검색</span>
        <Search
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
          aria-hidden="true"
        />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="검색"
          className="h-10 w-full rounded-md border border-slate-300 bg-background pl-9 pr-3 text-sm font-semibold outline-none focus:border-slate-400"
        />
      </label>

      <div className="mt-3 max-h-[360px] space-y-2 overflow-y-auto">
        {loading ? (
          <div className="flex h-28 items-center justify-center text-slate-500">
            <Loader2 className="size-5 animate-spin" aria-hidden="true" />
          </div>
        ) : (
          filteredReviewers.map((reviewer) => {
            const selected = reviewer.id === effectiveSelectedId
            const activeCaseCount = reviewer.activeCaseCount ?? 0
            return (
              <button
                key={reviewer.id}
                type="button"
                className={cn(
                  "w-full rounded-md border p-3 text-left transition-colors",
                  selected
                    ? "border-emerald-200 bg-emerald-50"
                    : "border-slate-200 bg-white hover:border-slate-300"
                )}
                onClick={() => setSelectedId(reviewer.id)}
              >
                <span className="flex items-start justify-between gap-3">
                  <span className="min-w-0">
                    <span className="block text-sm font-bold text-slate-900">{reviewer.name}</span>
                    <span className="mt-1 block truncate text-xs font-semibold text-slate-500">
                      {reviewer.organizationName} · {reviewer.department}
                    </span>
                  </span>
                  <span className="shrink-0 text-right text-xs font-bold">
                    <span className={activeCaseCount >= 4 ? "text-red-700" : "text-slate-600"}>
                      진행 중 {activeCaseCount}건
                    </span>
                    <span className="mt-1 block text-slate-500">평균 {reviewer.averageDays ?? 0}일</span>
                  </span>
                </span>
                <span className="mt-2 flex flex-wrap gap-1.5">
                  {(reviewer.specialties ?? []).map((specialty) => (
                    <span
                      key={specialty}
                      className="rounded-full border border-slate-200 bg-white px-2 py-0.5 text-[11px] font-bold text-slate-600"
                    >
                      {specialty}
                    </span>
                  ))}
                </span>
              </button>
            )
          })
        )}
      </div>

      <div className="mt-5 flex justify-end gap-2">
        <Button type="button" variant="outline" className="h-10 font-bold" disabled={processing} onClick={onClose}>
          취소
        </Button>
        <Button
          type="button"
          variant="outline"
          className="h-10 border-slate-300 px-5 font-bold text-slate-700 hover:bg-slate-50"
          disabled={processing || !selectedReviewer}
          onClick={() => {
            if (selectedReviewer) onAssign(selectedReviewer)
          }}
        >
          {processing ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : null}
          {selectedReviewer ? `${selectedReviewer.name}에게 배정` : "검토자 배정"}
        </Button>
      </div>
    </DialogFrame>
  )
}

export function ReviewDecisionDialog({
  decision,
  caseSummary,
  analystName,
  unreadEvidenceLabels,
  processing,
  onClose,
  onSubmit,
}: DialogBaseProps & {
  decision: "APPROVED" | "REVISION"
  caseSummary: string
  analystName?: string | null
  unreadEvidenceLabels: string[]
  onSubmit: (reason: string) => void
}) {
  const dialogRef = useDialogFocusTrap(onClose)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const isRevision = decision === "REVISION"
  const title = isRevision ? "보완 요청" : "검토 승인"
  const errorId = "review-decision-error"
  const reasonHintId = "review-decision-reason-hint"
  const submitDisabled = processing || (isRevision && !reason.trim())
  const describedBy = [
    isRevision && !reason.trim() ? reasonHintId : null,
    error ? errorId : null,
  ]
    .filter(Boolean)
    .join(" ") || undefined
  const submitLabel = isRevision ? "보완 요청 보내기" : "승인 확정"

  function handleSubmit() {
    if (isRevision && !reason.trim()) {
      setError("사유 입력 필수")
      return
    }
    onSubmit(reason.trim())
  }

  return (
    <DialogFrame
      dialogRef={dialogRef}
      labelledBy="review-decision-title"
      onClose={onClose}
      closeLabel={`${title} 닫기`}
    >
      <h3 id="review-decision-title" className="text-lg font-bold text-foreground">
        {title}
      </h3>
      <p className="mt-2 truncate text-sm font-semibold text-muted-foreground">{caseSummary}</p>
      {!isRevision && unreadEvidenceLabels.length > 0 ? (
        <div className="mt-4 flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm font-semibold leading-5 text-amber-700">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <p>
            아직 열람하지 않은 증거가 {unreadEvidenceLabels.length}건 있습니다 ({unreadEvidenceLabels.join(", ")})
          </p>
        </div>
      ) : null}
      {isRevision ? (
        <p className="mt-4 text-sm font-semibold text-muted-foreground">
          분석관 {analystName ?? ""}에게 반려됩니다
        </p>
      ) : null}
      <label htmlFor="review-decision-reason" className="mt-4 block text-sm font-bold text-foreground">
        {isRevision ? "사유" : "의견 (선택)"}
      </label>
      <textarea
        id="review-decision-reason"
        value={reason}
        aria-invalid={Boolean(error)}
        aria-describedby={describedBy}
        onChange={(event) => {
          setReason(event.target.value)
          if (error) setError("")
        }}
        className={cn(
          "mt-2 h-28 w-full resize-none rounded-md border bg-background px-3 py-2.5 text-sm font-medium outline-none",
          error ? "border-red-300 focus:border-red-400" : "border-slate-300 focus:border-slate-400"
        )}
      />
      {error ? (
        <p id={errorId} role="alert" className="mt-2 text-xs font-bold text-red-700">
          {error}
        </p>
      ) : null}
      {isRevision && !reason.trim() ? (
        <p id={reasonHintId} className="mt-2 text-xs font-bold text-red-700">
          사유를 입력해야 보낼 수 있습니다
        </p>
      ) : null}
      <p className="mt-4 text-xs font-semibold leading-5 text-muted-foreground">
        확정 후에는 취소할 수 없으며, 검토 기록에 영구 저장됩니다.
      </p>
      <div className="mt-5 flex justify-end gap-2">
        <Button type="button" variant="outline" className="h-10 font-bold" disabled={processing} onClick={onClose}>
          취소
        </Button>
        <Button
          type="button"
          variant={isRevision ? "outline" : "default"}
          className={cn(
            "h-10 px-5 font-bold",
            isRevision
              ? "border-red-200 text-red-700 hover:bg-red-50"
              : "bg-emerald-700 text-white hover:bg-emerald-800"
          )}
          disabled={submitDisabled}
          onClick={handleSubmit}
        >
          {processing ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : null}
          {submitLabel}
        </Button>
      </div>
    </DialogFrame>
  )
}

function DialogFrame({
  dialogRef,
  labelledBy,
  onClose,
  closeLabel,
  wide = false,
  children,
}: {
  dialogRef: RefObject<HTMLDivElement | null>
  labelledBy: string
  onClose: () => void
  closeLabel: string
  wide?: boolean
  children: ReactNode
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 px-4 py-6">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        tabIndex={-1}
        className={cn(
          "relative max-h-[90vh] w-full overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 shadow-2xl outline-none dark:border-border dark:bg-card",
          wide ? "max-w-2xl" : "max-w-md"
        )}
      >
        <button
          type="button"
          className="absolute right-3 top-3 flex size-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted/70 hover:text-foreground"
          aria-label={closeLabel}
          onClick={onClose}
        >
          <X className="size-4" aria-hidden="true" />
        </button>
        {children}
      </div>
    </div>
  )
}

function useDialogFocusTrap(onClose: () => void) {
  const dialogRef = useRef<HTMLDivElement | null>(null)
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    const previousFocus = document.activeElement as HTMLElement | null
    dialog.focus()

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault()
        onCloseRef.current()
        return
      }
      if (event.key !== "Tab" || !dialog) return

      const focusable = Array.from(
        dialog.querySelectorAll<HTMLElement>(
          'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'
        )
      )
      if (focusable.length === 0) {
        event.preventDefault()
        dialog.focus()
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

    document.addEventListener("keydown", handleKeyDown)
    return () => {
      document.removeEventListener("keydown", handleKeyDown)
      previousFocus?.focus()
    }
  }, [])

  return dialogRef
}
