"use client"

import { useEffect, useState } from "react"
import { AlertCircle, Download, Loader2, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { downloadEvidenceReport, type EvidenceDetailData } from "@/lib/api/evidence-detail"
import { getApiErrorMessage } from "@/lib/api/errors"
import { getReportIssueUi, type ReportIssueStatus } from "@/lib/report-issue-status"
import { cn } from "@/lib/utils"

 export function ReportExportDialog({
  open,
  onClose,
  data,
  reviewApproved,
  reportIssueStatus,
  downloadEnabled,
}: {
  open: boolean
  onClose: () => void
  data: EvidenceDetailData
  reviewApproved: boolean
  reportIssueStatus: ReportIssueStatus
  downloadEnabled: boolean
}) {
  const [pdfActionLoading, setPdfActionLoading] = useState(false)
  const [pdfActionError, setPdfActionError] = useState<string | null>(null)
  const [pdfPreviewLoading, setPdfPreviewLoading] = useState(false)
  const [pdfPreviewError, setPdfPreviewError] = useState<string | null>(null)
  const [pdfPreviewUrl, setPdfPreviewUrl] = useState<string | null>(null)
  const [pdfPreviewReloadKey, setPdfPreviewReloadKey] = useState(0)
  const [reportHash, setReportHash] = useState<string | null>(data.reportInfo?.reportHash ?? null)

  const { evidenceInfo } = data
  const fileName = `analysis-report-${evidenceInfo.evidenceId}.pdf`
  const reportIssueUi = getReportIssueUi(reportIssueStatus)

  useEffect(() => {
    let cancelled = false
    let objectUrl: string | null = null

    if (!open || !downloadEnabled) {
      return
    }

    async function loadBackendPdfPreview() {
      setPdfPreviewLoading(true)
      setPdfPreviewError(null)
      setPdfPreviewUrl(null)

      try {
        const result = await downloadEvidenceReport(evidenceInfo.evidenceId, { preview: true })
        if (cancelled) return

        setReportHash(result.reportHash)
        objectUrl = URL.createObjectURL(result.blob)
        setPdfPreviewUrl(objectUrl)
      } catch (error) {
        if (!cancelled) {
          setPdfPreviewError(getApiErrorMessage(error, "PDF 미리보기를 불러오지 못했습니다."))
        }
      } finally {
        if (!cancelled) setPdfPreviewLoading(false)
      }
    }

    void loadBackendPdfPreview()

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [downloadEnabled, evidenceInfo.evidenceId, open, pdfPreviewReloadKey])

  if (!open) return null

  async function handleDownload() {
    if (!downloadEnabled || pdfActionLoading || pdfPreviewLoading) return

    setPdfActionError(null)
    setPdfActionLoading(true)

    try {
      const result = await downloadEvidenceReport(evidenceInfo.evidenceId)
      setReportHash(result.reportHash)
      downloadBlob(result.blob, fileName)
    } catch (error) {
      setPdfActionError(getApiErrorMessage(error, "PDF 다운로드에 실패했습니다."))
    } finally {
      setPdfActionLoading(false)
    }
  }

  function handleClose() {
    setPdfActionError(null)
    setPdfPreviewError(null)
    setPdfPreviewUrl(null)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 px-4 py-6">
      <section
        role="dialog"
        aria-modal="true"
        aria-label="보고서 PDF 다운로드"
        className="grid max-h-[92vh] w-full max-w-6xl overflow-y-auto rounded-2xl border border-border bg-card shadow-2xl lg:grid-cols-[330px_minmax(0,1fr)] lg:grid-rows-[minmax(0,1fr)] lg:overflow-hidden"
      >
        <div className="flex min-h-0 flex-col border-b border-border p-6 lg:border-b-0 lg:border-r">
          <div className="flex items-start justify-between gap-3">
            <h2 className="text-lg font-bold text-foreground">AI 분석 보고서 PDF</h2>
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
            <InfoBlock
              label="보고서 유형"
              value="AI 분석 종합 보고서"
              helper="저장된 실제 분석 결과로 생성됩니다."
            />
            <InfoBlock
              label="대상 증거"
              value={`EVD-${evidenceInfo.evidenceId}`}
              helper={evidenceInfo.originalFileName ?? evidenceInfo.fileName}
              mono
            />
            <InfoBlock
              label="PDF SHA-256"
              value={reportHash ?? "PDF 생성 후 확인할 수 있습니다."}
              mono
            />
            <InfoBlock
              label="검증번호"
              value={data.reportInfo?.verificationCode ?? "PDF 생성 후 확인할 수 있습니다."}
              mono
            />
            <div className="flex items-center justify-between gap-3">
              <p className="text-xs font-bold text-muted-foreground">검토 상태</p>
              <span
                className={cn(
                  "rounded-full px-3 py-1 text-xs font-bold",
                  reviewApproved
                    ? "bg-emerald-50 text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-300"
                    : "bg-amber-50 text-amber-700 dark:bg-amber-950/30 dark:text-amber-300"
                )}
              >
                {reviewApproved ? "검토 승인 완료" : "검토 승인 대기"}
              </span>
            </div>
          </div>

          <div className="mt-auto pt-6">
            {!downloadEnabled ? (
              <p className="mb-2 rounded-xl bg-muted/40 px-3 py-2 text-center text-xs font-semibold leading-5 text-muted-foreground">
                {reportIssueUi.description}
              </p>
            ) : null}
            {pdfActionError ? (
              <p className="mb-2 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs font-bold leading-5 text-red-600">
                {pdfActionError}
              </p>
            ) : null}
            <Button
              type="button"
              disabled={!downloadEnabled || pdfActionLoading || pdfPreviewLoading}
              onClick={handleDownload}
              className="h-11 w-full bg-teal-600 text-base font-bold text-white hover:bg-teal-700 disabled:bg-muted disabled:text-muted-foreground"
            >
              {pdfActionLoading ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <Download className="size-4" aria-hidden="true" />
              )}
              {pdfActionLoading || pdfPreviewLoading ? "PDF 준비 중" : "PDF 다운로드"}
            </Button>
          </div>
        </div>

        <div className="relative min-h-0 overflow-y-auto bg-slate-200/80 p-5 dark:bg-slate-900/40 lg:p-8">
          <button
            type="button"
            onClick={handleClose}
            aria-label="닫기"
            className="absolute right-4 top-4 hidden size-9 items-center justify-center rounded-xl bg-white/80 text-slate-500 shadow-sm transition-colors hover:bg-white hover:text-slate-900 lg:flex"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
          <p className="mb-4 pr-12 text-center text-sm font-bold text-slate-600">
            PDF 미리보기
          </p>

          <BackendPdfPreview
            loading={pdfPreviewLoading}
            error={pdfPreviewError}
            url={pdfPreviewUrl}
            onRetry={() => setPdfPreviewReloadKey((key) => key + 1)}
          />
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
  helper?: string | null
  mono?: boolean
}) {
  return (
    <div>
      <p className="text-xs font-bold text-muted-foreground">{label}</p>
      <div className="mt-2 rounded-xl border border-border bg-muted/30 px-3 py-3">
        <p className={cn("break-all text-sm font-bold text-foreground", mono && "font-mono text-xs")}>{value}</p>
        {helper ? <p className="mt-1 break-all text-xs font-medium text-muted-foreground">{helper}</p> : null}
      </div>
    </div>
  )
}

function BackendPdfPreview({
  loading,
  error,
  url,
  onRetry,
}: {
  loading: boolean
  error: string | null
  url: string | null
  onRetry: () => void
}) {
  if (loading) {
    return (
      <div className="flex min-h-[68vh] flex-col items-center justify-center rounded-sm bg-white shadow-xl">
        <Loader2 className="size-8 animate-spin text-teal-600" aria-hidden="true" />
        <p className="mt-4 text-sm font-bold text-slate-700">PDF가 준비 중입니다.</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex min-h-[68vh] flex-col items-center justify-center rounded-sm bg-white px-6 text-center shadow-xl">
        <AlertCircle className="size-10 text-red-600" aria-hidden="true" />
        <p className="mt-4 text-base font-bold text-slate-950">PDF를 표시하지 못했습니다.</p>
        <p className="mt-2 max-w-md text-sm font-semibold leading-6 text-slate-500">{error}</p>
        <Button type="button" variant="outline" className="mt-5 h-10 rounded-lg px-4 font-bold" onClick={onRetry}>
          다시 불러오기
        </Button>
      </div>
    )
  }

  if (!url) {
    return (
      <div className="flex min-h-[68vh] flex-col items-center justify-center rounded-sm bg-white px-6 text-center shadow-xl">
        <AlertCircle className="size-10 text-slate-400" aria-hidden="true" />
        <p className="mt-4 text-base font-bold text-slate-950">표시할 PDF가 없습니다.</p>
      </div>
    )
  }

  return (
    <div className="relative overflow-hidden rounded-sm bg-white shadow-xl">
      <iframe
        title="PDF 미리보기"
        src={`${url}#toolbar=0&navpanes=0`}
        className="h-[min(72vh,920px)] min-h-[680px] w-full border-0 bg-white"
      />
    </div>
  )
}

function downloadBlob(blob: Blob, fileName: string) {
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement("a")
  link.href = objectUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
}
