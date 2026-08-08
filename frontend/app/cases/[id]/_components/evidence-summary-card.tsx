"use client"

import { useState } from "react"
import {
  AlertCircle,
  CheckCircle2,
  ChevronDown,
  ClipboardCheck,
  FileBadge,
  FileText,
  Files,
  LockKeyhole,
  Play,
  Star,
  Video,
} from "lucide-react"
import type { LucideIcon } from "lucide-react"

import { EvidenceHlsStatusThumbnail } from "@/components/evidence-hls-status-thumbnail"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import type { CaseEvidenceSummary, EvidenceDetailData } from "@/lib/api/evidence-detail"
import { formatDateTime, formatDuration, formatFileSize as formatBytes } from "@/lib/formatters"
import { cn } from "@/lib/utils"

type EvidenceSummaryCardProps = {
  data: EvidenceDetailData
  extension: string
  riskLabel: string
  statusLabel: string
  riskBadgeClassName: string
  riskTextClassName: string
  evidences?: CaseEvidenceSummary[]
  selectedEvidenceId?: number | null
  onSelectEvidence?: (evidenceId: number) => void
}

export function EvidenceSummaryCard({
  data,
  extension,
  riskLabel,
  statusLabel,
  riskBadgeClassName,
  riskTextClassName,
  evidences = [],
  selectedEvidenceId,
  onSelectEvidence,
}: EvidenceSummaryCardProps) {
  const { evidenceInfo, analysisInfo } = data
  const [evidenceMenuOpen, setEvidenceMenuOpen] = useState(false)
  const { technicalMetadata } = evidenceInfo
  const riskScore = formatScore(analysisInfo.riskScore)
  const confidenceScore = formatScore(analysisInfo.confidenceScore)
  const resolution = technicalMetadata.width && technicalMetadata.height
    ? `${technicalMetadata.width} × ${technicalMetadata.height}`
    : "-"
  const duration = formatDuration(technicalMetadata.durationSec)
  const softAdvisory = resolveSoftCompleteAdvisory(analysisInfo.errorCode, analysisInfo.errorMessage)

  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="min-w-0">
          <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline" className={cn("rounded-full px-4 text-xs font-semibold", riskBadgeClassName)}>
                {riskLabel}
              </Badge>
              <Badge variant="secondary" className="rounded-full px-4 text-xs font-semibold">
                {statusLabel}
              </Badge>
              <Badge variant="outline" className="rounded-full px-4 text-xs font-semibold">
                {extension}
              </Badge>
            </div>

            {evidences.length > 1 && onSelectEvidence ? (
              <Button
                type="button"
                variant="outline"
                size="lg"
                aria-expanded={evidenceMenuOpen}
                onClick={() => setEvidenceMenuOpen((open) => !open)}
                className="h-10 rounded-full border-slate-300 px-4 text-sm font-bold text-slate-700 shadow-sm hover:bg-slate-50 dark:border-border dark:text-muted-foreground"
              >
                <Files className="size-4" aria-hidden="true" />
                다른 증거 보기
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600 dark:bg-muted dark:text-muted-foreground">
                  {evidences.length}개
                </span>
                <ChevronDown
                  className={cn("size-4 transition-transform", evidenceMenuOpen && "rotate-180")}
                  aria-hidden="true"
                />
              </Button>
            ) : null}
          </div>

          {evidenceMenuOpen && evidences.length > 1 && onSelectEvidence ? (
            <div className="mb-4 rounded-xl border border-slate-200 bg-slate-50 p-3 shadow-inner dark:border-border dark:bg-muted/20">
              <div className="mb-2 flex items-center justify-between gap-3">
                <p className="text-sm font-bold text-foreground">사건 내 증거 목록</p>
                <p className="text-xs font-semibold text-muted-foreground">선택하면 상세 화면이 바뀝니다</p>
              </div>
              <div className="grid gap-2 lg:grid-cols-2">
                {evidences.map((evidence) => {
                  const active = evidence.evidenceId === selectedEvidenceId

                  return (
                    <button
                      key={evidence.evidenceId}
                      type="button"
                      onClick={() => {
                        onSelectEvidence(evidence.evidenceId)
                        setEvidenceMenuOpen(false)
                      }}
                      className={cn(
                        "flex min-h-[82px] items-center gap-3 rounded-lg border bg-card p-3 text-left transition-colors",
                        active
                          ? "border-teal-400 bg-teal-50 text-teal-950 shadow-sm dark:bg-teal-950/25 dark:text-teal-100"
                          : "border-slate-200 hover:border-slate-300 hover:bg-white dark:border-border dark:hover:bg-muted/30"
                      )}
                    >
                      <EvidenceMiniPreview evidence={evidence} active={active} />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-base font-bold text-foreground">{evidence.fileName}</span>
                        <span className="mt-1 flex flex-wrap items-center gap-2 text-xs font-semibold text-muted-foreground">
                          <span className="font-mono">EVD-{evidence.evidenceId}</span>
                          <span className="rounded-full bg-muted px-2 py-0.5">{formatEvidenceStatus(evidence.analysisStatus)}</span>
                        </span>
                      </span>
                      {active ? (
                        <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-teal-100 px-3 py-1 text-xs font-bold text-teal-700 dark:bg-teal-950/60 dark:text-teal-200">
                          <CheckCircle2 className="size-3.5" aria-hidden="true" />
                          보는 중
                        </span>
                      ) : (
                        <ChevronDown className="size-4 -rotate-90 text-muted-foreground" aria-hidden="true" />
                      )}
                    </button>
                  )
                })}
              </div>
            </div>
          ) : null}

          <div className="flex min-w-0 items-center gap-2">
            <h2 className="truncate text-2xl font-semibold tracking-normal text-foreground sm:text-[28px]">
              {evidenceInfo.fileName}
            </h2>
            <Star className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-sm">
            <MetaInline icon={LockKeyhole} label="증거번호" value={`EVD-${evidenceInfo.evidenceId}`} />
            <MetaDivider />
            <MetaInline icon={ClipboardCheck} label="업로드 일시" value={formatDateTime(evidenceInfo.uploadedAt)} />
            <MetaDivider />
            <MetaInline icon={FileBadge} label="파일 유형" value={evidenceInfo.mediaType || "VIDEO"} />
            <MetaDivider />
            <MetaInline icon={FileText} label="파일 크기" value={formatBytes(evidenceInfo.fileSize)} />
            <MetaDivider />
            <MetaInline icon={Video} label="해상도" value={resolution} />
            <MetaDivider />
            <MetaInline icon={Play} label="재생 시간" value={duration} />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-2">
          <MetricCard
            label="최종 위험도"
            value={riskScore ?? "-"}
            suffix={riskScore == null ? "" : "/ 100"}
            badge={riskScore == null ? "분석 대기" : riskLabel}
            valueClassName={riskTextClassName}
            badgeClassName={riskBadgeClassName}
          />
          <MetricCard
            label="신뢰도"
            value={confidenceScore ?? "-"}
            suffix={confidenceScore == null ? "" : "%"}
            badge={confidenceScore == null ? "분석 대기" : "높음"}
            valueClassName={confidenceScore == null ? "text-muted-foreground" : "text-emerald-600"}
          />
        </div>
      </div>

      {analysisInfo.status === "FAILED" ? (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700 dark:border-red-900/60 dark:bg-red-950/20 dark:text-red-300">
          <div className="flex items-start gap-2">
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <div>
          <p className="font-semibold">실패 사유</p>
              <p className="mt-1 leading-5">{analysisInfo.summary || "분석 처리 중 오류가 발생했습니다."}</p>
            </div>
          </div>
        </div>
      ) : null}

      {analysisInfo.status === "COMPLETED" && softAdvisory ? (
        <div className="mt-4 rounded-lg border border-amber-300/80 bg-amber-50 px-4 py-3 text-sm font-bold text-amber-950 dark:border-amber-500/40 dark:bg-amber-950/40 dark:text-amber-50">
          <div className="flex items-start gap-2">
            <AlertCircle className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-300" aria-hidden="true" />
            <div>
              <p className="font-semibold">{softAdvisory.title}</p>
              <p className="mt-1 text-xs font-medium leading-5 opacity-90">{softAdvisory.message}</p>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  )
}

// 헤더 메타 정보는 박스 그리드 대신 한 줄 인라인으로 노출한다(목표 UI와 동일, 시선 이동 최소화).
function MetaInline({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
      <Icon className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      <span className="text-muted-foreground">{label}</span>
      <span className="font-semibold text-foreground">{value}</span>
    </span>
  )
}

function MetaDivider() {
  return <span className="hidden h-3.5 w-px bg-border sm:inline-block" aria-hidden="true" />
}

function MetricCard({
  label,
  value,
  suffix,
  badge,
  valueClassName,
  badgeClassName,
}: {
  label: string
  value: string
  suffix: string
  badge: string
  valueClassName: string
  badgeClassName?: string
}) {
  return (
    <div className="flex min-h-[132px] flex-col items-center justify-center rounded-lg border border-border bg-background/40 px-4 py-4 text-center">
      <p className="text-xs font-semibold text-muted-foreground">{label}</p>
      <div className="mt-4 flex items-end justify-center gap-1">
        <span className={cn("text-4xl font-semibold leading-none", valueClassName)}>{value}</span>
        {suffix ? (
          <span className="pb-1 text-base font-semibold text-muted-foreground">{suffix}</span>
        ) : null}
      </div>
      <span className={cn(
        "mt-4 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-300",
        badgeClassName
      )}>
        {badge}
      </span>
    </div>
  )
}

function resolveSoftCompleteAdvisory(
  errorCode?: string | null,
  errorMessage?: string | null
): { title: string; message: string } | null {
  const message = errorMessage?.trim() || ""
  switch (errorCode) {
    case "NO_HUMAN_FACE":
      return {
        title: "딥페이크 판별 불가 · 위변조 분석 진행",
        message:
          message ||
          "사람 얼굴이 없어 딥페이크 점수는 보류했습니다. 위변조 탐지는 이어서 확인하세요.",
      }
    case "FACE_TOO_SMALL":
      return {
        title: "딥페이크 판별 보류 · 위변조 분석 진행",
        message:
          message ||
          "얼굴이 너무 작아 딥페이크 판별을 보류했습니다. 위변조 탐지는 이어서 확인하세요.",
      }
    case "INSUFFICIENT_FACE_SAMPLES":
      return {
        title: "딥페이크 판별 보류 · 위변조 분석 진행",
        message:
          message ||
          "얼굴 샘플이 부족해 딥페이크 판별을 보류했습니다. 위변조 탐지는 이어서 확인하세요.",
      }
    case "TEMPORAL_MODULE_UNAVAILABLE":
      return {
        title: "시계열 모듈 제한",
        message: message || "TimeSformer를 사용할 수 없어 CNN·광학 중심으로 판별했습니다.",
      }
    default:
      return null
  }
}

function formatScore(score: number | null) {
  if (score == null) return null
  const normalized = score > 0 && score <= 1 ? score * 100 : score
  return String(Math.round(normalized))
}

function formatEvidenceStatus(status: string) {
  if (status === "COMPLETED") return "분석 완료"
  if (status === "PROCESSING") return "처리 중"
  if (status === "FAILED") return "실패"
  return "대기"
}

function EvidenceMiniPreview({ evidence, active }: { evidence: CaseEvidenceSummary; active: boolean }) {
  if (evidence.thumbnailUrl) {
    return (
      <span
        className={cn(
          "relative flex aspect-video w-24 shrink-0 overflow-hidden rounded-md border border-border bg-slate-900",
          active && "ring-2 ring-teal-400 ring-offset-2 ring-offset-background"
        )}
      >
        <img src={evidence.thumbnailUrl} alt="" className="size-full object-cover" />
        <span className="absolute left-1/2 top-1/2 flex size-7 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-white/90 text-slate-950 shadow-sm">
          <Play className="ml-0.5 size-3.5 fill-current" aria-hidden="true" />
        </span>
      </span>
    )
  }

  if (evidence.mediaType === "VIDEO") {
    return <EvidenceHlsStatusThumbnail hlsStatus={evidence.hlsStatus} active={active} />
  }

  return (
    <span
      className={cn(
        "relative flex aspect-video w-24 shrink-0 overflow-hidden rounded-md border border-border bg-slate-900",
        active && "ring-2 ring-teal-400 ring-offset-2 ring-offset-background"
      )}
    >
      <span className="size-full bg-gradient-to-br from-slate-950 via-slate-800 to-teal-900" />
      <span className="absolute left-1/2 top-1/2 flex size-7 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-white/90 text-slate-950 shadow-sm">
        <Play className="ml-0.5 size-3.5 fill-current" aria-hidden="true" />
      </span>
    </span>
  )
}
