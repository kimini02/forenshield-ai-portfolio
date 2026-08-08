"use client"

import { AlertTriangle, CheckCircle2 } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import type { EvidenceReadinessResponse } from "@/lib/evidence-api"
import {
  formatReadinessMetric,
  getQualityDialogSummary,
  hasBlockingReadiness,
  readinessTierBadgeClass,
  readinessTierLabel,
  type ReadinessCheckSummary,
  type ReadinessTier,
} from "@/lib/readiness"

type QualityWarningDialogProps = {
  open: boolean
  summaries: ReadinessCheckSummary[]
  worstTier: ReadinessTier
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

function ReadinessFrameMetricsTable({
  readiness,
}: {
  readiness: EvidenceReadinessResponse
}) {
  const metrics = readiness.frameMetrics
  if (!metrics) {
    return (
      <p className="mt-3 text-xs leading-relaxed text-muted-foreground break-keep">
        프레임 샘플링 지표를 불러오지 못했습니다. ffprobe 메타데이터 결과만 표시됩니다.
      </p>
    )
  }

  const rows = [
    {
      label: "Blur (선명도)",
      hint: "높을수록 선명",
      mean: metrics.blur?.mean,
      // 대표값은 표본 평균(mean). min은 등급 판정용으로 백엔드에만 쓰이며 UI에는 노출하지 않음.
      edge: metrics.blur?.mean,
      edgeLabel: "mean",
    },
    {
      label: "Blockiness",
      hint: "낮을수록 양호",
      mean: metrics.blockiness?.mean,
      edge: metrics.blockiness?.max,
      edgeLabel: "max",
    },
    {
      label: "FFT peak",
      hint: "낮을수록 양호",
      mean: metrics.fftPeak?.mean,
      edge: metrics.fftPeak?.max,
      edgeLabel: "max",
    },
  ]

  const hasAny = rows.some(
    (row) => row.mean != null || row.edge != null
  )
  if (!hasAny) return null

  return (
    <div className="mt-3 overflow-x-auto rounded-md border border-border/80 bg-background/60">
      <table className="w-full min-w-[280px] text-left text-xs">
        <thead>
          <tr className="border-b border-border/80 text-muted-foreground">
            <th className="px-3 py-2 font-medium">지표</th>
            <th className="px-3 py-2 font-medium">평균</th>
            <th className="px-3 py-2 font-medium">대표값</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label} className="border-b border-border/50 last:border-0">
              <td className="px-3 py-2">
                <span className="font-medium text-foreground">{row.label}</span>
                <span className="mt-0.5 block text-[11px] text-muted-foreground">
                  {row.hint}
                </span>
              </td>
              <td className="px-3 py-2 tabular-nums text-foreground">
                {formatReadinessMetric(row.mean)}
              </td>
              <td className="px-3 py-2 tabular-nums text-foreground">
                {row.edgeLabel} {formatReadinessMetric(row.edge)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {readiness.spatial?.worstRegion ? (
        <p className="border-t border-border/80 px-3 py-2 text-[11px] text-muted-foreground">
          최악 구역: {readiness.spatial.worstRegion}
          {readiness.spatial.worstRegionScore != null
            ? ` (점수 ${formatReadinessMetric(readiness.spatial.worstRegionScore)})`
            : null}
        </p>
      ) : null}
    </div>
  )
}

export function QualityWarningDialog({
  open,
  summaries,
  worstTier,
  loading = false,
  onConfirm,
  onCancel,
}: QualityWarningDialogProps) {
  if (!open) return null

  const ackItems = summaries.filter((item) => item.readiness.requiresAcknowledgement)
  const infoItems = summaries.filter(
    (item) =>
      !item.readiness.requiresAcknowledgement &&
      item.readiness.frameCheckStatus &&
      ["FAILED", "SKIPPED"].includes(item.readiness.frameCheckStatus)
  )
  const displayItems = ackItems.length > 0 ? ackItems : infoItems.length > 0 ? infoItems : summaries
  const blocking = hasBlockingReadiness(summaries)
  const summaryText = getQualityDialogSummary(worstTier, blocking)
  const HeaderIcon = worstTier === "GOOD" && !blocking ? CheckCircle2 : AlertTriangle
  const headerIconClass =
    worstTier === "GOOD" && !blocking
      ? "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300"
      : "bg-amber-500/15 text-amber-700 dark:text-amber-300"

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 py-6">
      <div
        role="alertdialog"
        aria-labelledby="quality-warning-title"
        aria-describedby="quality-warning-desc"
        className="max-h-[min(90vh,720px)] w-full max-w-2xl overflow-y-auto rounded-xl border border-border bg-card p-6 shadow-lg sm:p-7"
      >
        <div className="flex items-start gap-3">
          <div
            className={`mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full ${headerIconClass}`}
          >
            <HeaderIcon className="size-4" aria-hidden="true" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 id="quality-warning-title" className="text-lg font-semibold text-foreground">
                화질 안내
              </h2>
              <Badge variant="outline" className={readinessTierBadgeClass(worstTier)}>
                {readinessTierLabel(worstTier)}
              </Badge>
            </div>
            <p
              id="quality-warning-desc"
              className="mt-2 text-sm leading-relaxed text-pretty text-muted-foreground break-keep"
            >
              {summaryText}
            </p>
          </div>
        </div>

        <ul className="mt-5 space-y-4">
          {displayItems.map((item) => (
            <li
              key={item.evidenceId}
              className="rounded-lg border border-border bg-muted/20 p-4 sm:p-5"
            >
              <div className="mb-2 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <p className="min-w-0 text-sm font-medium leading-snug text-foreground break-keep">
                  {item.fileName}
                </p>
                <Badge
                  variant="outline"
                  className={`w-fit shrink-0 ${readinessTierBadgeClass(item.readiness.readinessTier)}`}
                >
                  {readinessTierLabel(item.readiness.readinessTier)}
                </Badge>
              </div>
              {item.readiness.frameCheckStatus &&
              ["FAILED", "SKIPPED"].includes(item.readiness.frameCheckStatus) ? (
                <p className="mb-2 text-xs leading-relaxed text-amber-800 break-keep dark:text-amber-200">
                  프레임 검사 {item.readiness.frameCheckStatus === "SKIPPED" ? "미실행" : "실패"}
                  {item.readiness.frameCheckMessage
                    ? `: ${item.readiness.frameCheckMessage}`
                    : " (ffprobe 결과만 반영됨)"}
                </p>
              ) : item.readiness.frameCheckStatus === "COMPLETED" ? (
                <p className="mb-2 text-xs text-muted-foreground">프레임 샘플링 검사 완료</p>
              ) : null}
              {item.readiness.confidenceCap < 100 ? (
                <p className="mb-2 text-xs text-muted-foreground">
                  예상 신뢰도 상한: {item.readiness.confidenceCap}%
                </p>
              ) : null}
              <ReadinessFrameMetricsTable readiness={item.readiness} />
              {item.readiness.reasons.length > 0 ? (
                <ul className="mt-3 list-disc space-y-1 pl-4 text-sm leading-relaxed text-muted-foreground break-keep">
                  {item.readiness.reasons.map((reason) => (
                    <li key={reason}>{reason}</li>
                  ))}
                </ul>
              ) : (
                <p className="mt-3 text-sm text-muted-foreground">추가 사유가 없습니다.</p>
              )}
            </li>
          ))}
        </ul>

        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <Button variant="outline" onClick={onCancel} disabled={loading}>
            {blocking ? "닫기" : "아니오"}
          </Button>
          {!blocking ? (
            <Button onClick={onConfirm} disabled={loading}>
              {loading ? "분석 요청 중..." : "예, 계속 분석"}
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  )
}
