"use client"

import { useState } from "react"
import { Download, ExternalLink, FileCheck2, Loader2, ShieldCheck } from "lucide-react"

import { Button } from "@/components/ui/button"
import { downloadEvidenceReport, type EvidenceDetailData } from "@/lib/api/evidence-detail"
import { getApiErrorMessage } from "@/lib/api/errors"
import { formatDateTime, formatDuration, formatFileSize } from "@/lib/formatters"
import { cn } from "@/lib/utils"

type MetadataReportTabProps = {
  data: EvidenceDetailData
  extension: string
  reportReady: boolean
  reviewApproved?: boolean
}

export function MetadataReportTab({
  data,
  extension,
  reportReady,
  reviewApproved = false,
}: MetadataReportTabProps) {
  const { evidenceInfo, analysisInfo } = data
  const metadata = evidenceInfo.technicalMetadata
  const [pdfActionLoading, setPdfActionLoading] = useState(false)
  const [pdfActionError, setPdfActionError] = useState<string | null>(null)
  const [reportHash, setReportHash] = useState<string | null>(data.reportInfo?.reportHash ?? null)
  const fileName = `analysis-report-${evidenceInfo.evidenceId}.pdf`

  async function handleDownload() {
    if (!reportReady || !reviewApproved || pdfActionLoading) return
    setPdfActionLoading(true)
    setPdfActionError(null)

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

  async function handlePreview() {
    if (!reportReady || pdfActionLoading) return
    const previewWindow = window.open("", "_blank", "noopener")
    setPdfActionLoading(true)
    setPdfActionError(null)

    try {
      const result = await downloadEvidenceReport(evidenceInfo.evidenceId, { preview: true })
      setReportHash(result.reportHash)
      const objectUrl = URL.createObjectURL(result.blob)
      if (previewWindow) {
        previewWindow.location.href = objectUrl
      } else {
        window.open(objectUrl, "_blank", "noopener")
      }
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000)
    } catch (error) {
      previewWindow?.close()
      setPdfActionError(getApiErrorMessage(error, "PDF 미리보기를 열지 못했습니다."))
    } finally {
      setPdfActionLoading(false)
    }
  }

  return (
    <div className="space-y-5">
      <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
        <div className="flex items-start gap-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600 dark:bg-blue-950/30 dark:text-blue-300">
            <FileCheck2 className="size-5" aria-hidden="true" />
          </span>
          <div>
            <h3 className="text-lg font-bold text-foreground">분석 대상 메타데이터</h3>
            <p className="mt-1 text-sm font-semibold text-muted-foreground">
              상세 화면과 동일한 백엔드 증거 정보를 표시합니다.
            </p>
          </div>
        </div>

        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          <InfoTableCard
            title="파일 기본정보"
            rows={[
              ["파일명", evidenceInfo.fileName],
              ["파일 유형", evidenceInfo.fileType || extension || "확인되지 않음"],
              ["파일 크기", formatFileSize(evidenceInfo.fileSize, { zeroLabel: "확인되지 않음" })],
              ["재생 시간", metadata.durationSec != null ? formatDuration(metadata.durationSec) : "확인되지 않음"],
              ["해상도", formatResolution(data)],
              ["프레임레이트", metadata.fps != null ? `${metadata.fps} fps` : "확인되지 않음"],
            ]}
          />
          <InfoTableCard
            title="추출 메타데이터"
            rows={[
              ["업로드 일시", formatDateTime(evidenceInfo.uploadedAt)],
              ["증거번호", `EVD-${evidenceInfo.evidenceId}`],
              ["촬영 일시", metadata.capturedAt ? formatDateTime(metadata.capturedAt) : "확인되지 않음"],
              ["코덱", metadata.codec || "확인되지 않음"],
              ["오디오 샘플레이트", metadata.sampleRate != null ? `${metadata.sampleRate} Hz` : "확인되지 않음"],
              ["메타데이터 추출 상태", metadata.extractionStatus || "확인되지 않음"],
            ]}
          />
        </div>
      </section>

      <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-teal-50 text-teal-600 dark:bg-teal-950/30 dark:text-teal-300">
              <ShieldCheck className="size-5" aria-hidden="true" />
            </span>
            <div>
              <h3 className="text-lg font-bold text-foreground">실제 PDF 보고서</h3>
              <p className="mt-1 text-sm font-semibold text-muted-foreground">
                저장된 실제 분석 결과로 PDF를 미리보기·다운로드합니다.
              </p>
            </div>
          </div>
          <span
            className={cn(
              "rounded-full px-3 py-1 text-xs font-bold",
              !reportReady
                ? "bg-slate-100 text-slate-600 dark:bg-muted dark:text-muted-foreground"
                : reviewApproved
                  ? "bg-emerald-50 text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-300"
                  : "bg-amber-50 text-amber-700 dark:bg-amber-950/30 dark:text-amber-300"
            )}
          >
            {!reportReady ? "분석 미완료" : reviewApproved ? "최종 발행 가능" : "검토 승인 대기"}
          </span>
        </div>

        <dl className="mt-5 grid gap-3 rounded-xl border border-border bg-muted/20 p-4 text-sm md:grid-cols-2">
          <SummaryLine label="PDF 상태" value={reviewApproved ? "최종 발행 가능" : "검토 승인 대기"} />
          <SummaryLine label="분석 완료일" value={formatDateTime(analysisInfo.completedAt)} />
          <SummaryLine label="검증번호" value={data.reportInfo?.verificationCode ?? "PDF 생성 후 확인할 수 있습니다."} mono />
          <SummaryLine label="보고서 파일" value={data.reportInfo?.reportFileName ?? fileName} mono />
          <div className="md:col-span-2">
            <SummaryLine label="PDF SHA-256" value={reportHash ?? "PDF 생성 후 확인할 수 있습니다."} mono />
          </div>
        </dl>

        <div className="mt-4 rounded-xl border border-dashed border-border bg-muted/10 px-4 py-4 text-sm font-semibold leading-6 text-muted-foreground">
          {reviewApproved
            ? "보고서 번호, 실제 검증코드와 QR은 최종 발행 PDF에 생성됩니다. 발행정보와 PDF 동일성은 QR 검증 페이지에서 확인할 수 있습니다."
            : "보고서 번호, 검증코드, 최종 PDF 해시와 QR은 검토 승인 후 발행 과정에서 생성됩니다."}
        </div>

        {pdfActionError ? (
          <p className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-600">
            {pdfActionError}
          </p>
        ) : null}

        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={!reportReady || pdfActionLoading}
            onClick={handlePreview}
            className="h-11 font-bold"
          >
            {pdfActionLoading ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <ExternalLink className="size-4" aria-hidden="true" />}
            PDF 미리보기
          </Button>
          <Button
            type="button"
            disabled={!reportReady || !reviewApproved || pdfActionLoading}
            onClick={handleDownload}
            className="h-11 bg-teal-600 font-bold hover:bg-teal-700 disabled:bg-muted disabled:text-muted-foreground"
          >
            {pdfActionLoading ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Download className="size-4" aria-hidden="true" />}
            {reviewApproved ? "최종 PDF 다운로드" : "검토 승인 대기"}
          </Button>
        </div>
      </section>
    </div>
  )
}

function InfoTableCard({ title, rows }: { title: string; rows: Array<[string, string]> }) {
  return (
    <article className="overflow-hidden rounded-xl border border-border bg-background/40">
      <h4 className="border-b border-border bg-muted/20 px-4 py-3 text-sm font-bold text-foreground">{title}</h4>
      <dl className="divide-y divide-border">
        {rows.map(([label, value]) => (
          <div key={label} className="grid grid-cols-[140px_minmax(0,1fr)] gap-3 px-4 py-3 text-sm">
            <dt className="font-semibold text-muted-foreground">{label}</dt>
            <dd className="min-w-0 break-all text-right font-bold text-foreground">{value}</dd>
          </div>
        ))}
      </dl>
    </article>
  )
}

function SummaryLine({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs font-semibold text-muted-foreground">{label}</dt>
      <dd className={cn("mt-1 break-all font-bold text-foreground", mono && "font-mono text-xs")}>{value}</dd>
    </div>
  )
}

function formatResolution(data: EvidenceDetailData) {
  const width = data.evidenceInfo.technicalMetadata?.width
  const height = data.evidenceInfo.technicalMetadata?.height
  if (width == null || height == null) return "확인되지 않음"
  return `${width} × ${height}`
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
