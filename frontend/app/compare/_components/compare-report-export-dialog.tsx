"use client"

import { useEffect, useState } from "react"
import { AlertCircle, Download, Loader2, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { downloadCompareReport, type CompareResult } from "@/lib/api/compare"
import { getApiErrorMessage } from "@/lib/api/errors"
import { cn } from "@/lib/utils"

type CompareReportExportDialogProps = {
  open: boolean
  onClose: () => void
  result: CompareResult
  isDownloading: boolean
  reportApproved: boolean
  downloadError?: string | null
  onDownload: () => void
}

export function CompareReportExportDialog({
  open,
  onClose,
  result,
  isDownloading,
  reportApproved,
  downloadError,
  onDownload,
}: CompareReportExportDialogProps) {
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [previewReloadKey, setPreviewReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    let objectUrl: string | null = null

    if (!open || !reportApproved) {
      return
    }

    async function loadBackendPdfPreview() {
      setPreviewLoading(true)
      setPreviewError(null)
      setPreviewUrl(null)

      try {
        const blob = await downloadCompareReport(result.compareId, { preview: true })
        if (cancelled) return

        objectUrl = URL.createObjectURL(blob)
        setPreviewUrl(objectUrl)
      } catch (error) {
        if (!cancelled) {
          setPreviewError(getApiErrorMessage(error, "비교검증 PDF 미리보기를 불러오지 못했습니다."))
        }
      } finally {
        if (!cancelled) setPreviewLoading(false)
      }
    }

    void loadBackendPdfPreview()

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [open, previewReloadKey, reportApproved, result.compareId])

  if (!open) return null

  function handleClose() {
    setPreviewError(null)
    setPreviewUrl(null)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 px-4 py-6">
      <section
        role="dialog"
        aria-modal="true"
        aria-label="비교검증 PDF 보고서"
        className="grid max-h-[92vh] w-full max-w-6xl overflow-y-auto rounded-2xl border border-border bg-card shadow-2xl lg:grid-cols-[320px_minmax(0,1fr)] lg:grid-rows-[minmax(0,1fr)] lg:overflow-hidden"
      >
        <div className="flex min-h-0 flex-col border-b border-border p-6 lg:border-b-0 lg:border-r">
          <div className="flex items-start justify-between gap-3">
            <h2 className="text-lg font-bold text-foreground">비교검증 PDF</h2>
            <button
              type="button"
              onClick={handleClose}
              aria-label="닫기"
              className="flex size-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground lg:hidden"
            >
              <X className="size-4" aria-hidden="true" />
            </button>
          </div>

          <div className="mt-5 space-y-5">
            <InfoBlock label="보고서 유형" value="비교검증 보고서" helper="저장된 실제 비교 결과로 생성됩니다." />
            <InfoBlock label="비교검증 ID" value={String(result.compareId)} mono />
            <InfoBlock label="기준 증거" value={`EVD-${result.originalEvidenceId}`} mono />
            <InfoBlock label="비교 대상" value={result.candidateFileName} />
            <div className="flex items-center justify-between gap-3">
              <p className="text-xs font-bold text-muted-foreground">검증 결과</p>
              <span
                className={cn(
                  "rounded-full px-3 py-1 text-xs font-bold",
                  result.verdict === "TAMPERED"
                    ? "bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-300"
                    : "bg-teal-50 text-teal-700 dark:bg-teal-950/30 dark:text-teal-300"
                )}
              >
                {result.summary.verdictLabel}
              </span>
            </div>
          </div>

          <div className="mt-auto pt-6">
            {!reportApproved ? (
              <p className="mb-2 rounded-lg bg-amber-50 px-3 py-2 text-center text-xs font-semibold leading-5 text-amber-700 dark:bg-amber-950/30 dark:text-amber-300">
                검토자가 승인한 뒤 비교검증 PDF를 열고 다운로드할 수 있습니다.
              </p>
            ) : downloadError ? (
              <p className="mb-2 rounded-lg bg-red-50 px-3 py-2 text-center text-xs font-semibold leading-5 text-red-700">
                {downloadError}
              </p>
            ) : null}
            <Button
              type="button"
              disabled={isDownloading || previewLoading || !reportApproved}
              onClick={onDownload}
              className="h-11 w-full bg-teal-600 text-base font-bold text-white hover:bg-teal-700 disabled:bg-muted disabled:text-muted-foreground"
            >
              {isDownloading ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <Download className="size-4" aria-hidden="true" />
              )}
              {isDownloading || previewLoading ? "PDF 준비 중" : reportApproved ? "PDF 다운로드" : "검토 승인 대기"}
            </Button>
          </div>
        </div>

        <div className="relative min-h-0 overflow-y-auto bg-slate-200/70 p-6 dark:bg-slate-900/40 lg:p-8">
          <button
            type="button"
            onClick={handleClose}
            aria-label="닫기"
            className="absolute right-4 top-4 hidden size-8 items-center justify-center rounded-md bg-white/70 text-slate-500 shadow-sm transition-colors hover:bg-white hover:text-slate-900 lg:flex"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
          <p className="mb-4 pr-12 text-center text-sm font-bold text-slate-600">
            PDF 미리보기
          </p>

          {!reportApproved ? (
            <EmptyPreview message="검토 승인 후 실제 비교검증 PDF를 확인할 수 있습니다." />
          ) : previewLoading ? (
            <LoadingPreview />
          ) : previewError ? (
            <ErrorPreview message={previewError} onRetry={() => setPreviewReloadKey((key) => key + 1)} />
          ) : previewUrl ? (
            <div className="relative overflow-hidden rounded-sm bg-white shadow-xl">
              <iframe
                title="PDF 미리보기"
                src={`${previewUrl}#toolbar=0&navpanes=0`}
                className="h-[min(72vh,920px)] min-h-[680px] w-full border-0 bg-white"
              />
            </div>
          ) : (
            <EmptyPreview message="표시할 PDF가 없습니다." />
          )}
        </div>
      </section>
    </div>
  )
}

function InfoBlock({
  label,
  value,
  helper,
  mono = false,
}: {
  label: string
  value: string
  helper?: string
  mono?: boolean
}) {
  return (
    <div>
      <p className="text-xs font-bold text-muted-foreground">{label}</p>
      <div className="mt-2 rounded-lg border border-border bg-muted/20 px-3 py-2.5">
        <p className={cn("break-all text-sm font-bold text-foreground", mono && "font-mono text-xs")}>{value}</p>
        {helper ? <p className="mt-1 text-xs font-medium leading-5 text-muted-foreground">{helper}</p> : null}
      </div>
    </div>
  )
}

function LoadingPreview() {
  return (
    <div className="flex min-h-[68vh] flex-col items-center justify-center rounded-sm bg-white shadow-xl">
      <Loader2 className="size-8 animate-spin text-teal-600" aria-hidden="true" />
      <p className="mt-4 text-sm font-bold text-slate-700">PDF가 준비 중입니다.</p>
    </div>
  )
}

function ErrorPreview({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="flex min-h-[68vh] flex-col items-center justify-center rounded-sm bg-white px-6 text-center shadow-xl">
      <AlertCircle className="size-10 text-red-600" aria-hidden="true" />
      <p className="mt-4 text-base font-bold text-slate-950">PDF를 표시하지 못했습니다.</p>
      <p className="mt-2 max-w-md text-sm font-semibold leading-6 text-slate-500">{message}</p>
      <Button type="button" variant="outline" className="mt-5 h-10 rounded-lg px-4 font-bold" onClick={onRetry}>
        다시 불러오기
      </Button>
    </div>
  )
}

function EmptyPreview({ message }: { message: string }) {
  return (
    <div className="flex min-h-[68vh] flex-col items-center justify-center rounded-sm bg-white px-6 text-center shadow-xl">
      <AlertCircle className="size-10 text-slate-400" aria-hidden="true" />
      <p className="mt-4 text-base font-bold text-slate-950">{message}</p>
    </div>
  )
}
