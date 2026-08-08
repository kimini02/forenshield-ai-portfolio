import type { ReactNode } from "react"
import type { LucideIcon } from "lucide-react"
import { CheckCircle2, Download, ExternalLink, FileText, Fingerprint, ShieldCheck } from "lucide-react"

import { Button } from "@/components/ui/button"
import { ReadinessMetricSection } from "@/components/readiness-metric-section"
import type { EvidenceDetailData } from "@/lib/api/evidence-detail"
import { formatDateTime } from "@/lib/formatters"
import { cn } from "@/lib/utils"

type ProgressStep = {
  title: string
  time?: string | null
  done: boolean
}

type SummaryTabProps = {
  data: EvidenceDetailData
  riskLabel: string
  riskSoftClassName: string
  progressSteps: ProgressStep[]
}

export function SummaryTab({
  data,
  riskLabel,
  riskSoftClassName,
  progressSteps,
}: SummaryTabProps) {
  const { evidenceInfo, integrityInfo, analysisInfo } = data
  const reportStatus = analysisInfo.status === "COMPLETED" ? "생성 완료" : "생성 전"
  const confidence = formatScorePercent(analysisInfo.confidenceScore)
  const qualityScore = confidence == null ? "-" : `${confidence} / 100`
  const shortHash = integrityInfo.originalHash
    ? `${integrityInfo.originalHash.slice(0, 12)}...${integrityInfo.originalHash.slice(-8)}`
    : "-"

  return (
    <>
      <div className="grid gap-4 xl:grid-cols-3">
        <CompactPanel title="판정 요약" icon={ShieldCheck}>
          <InfoLine label="위험 등급" value={riskLabel} pillClassName={riskSoftClassName} />
          <InfoLine label="분석 신뢰도" value={confidence == null ? "-" : `${confidence}%`} pillClassName="bg-emerald-50 text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-300" />
          <InfoLine label="품질 점수" value={qualityScore} pillClassName="bg-emerald-50 text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-300" />
          <RiskGauge value={confidence ?? 0} riskLabel={riskLabel} />
        </CompactPanel>

        <CompactPanel title="무결성 검증" icon={Fingerprint}>
          <InfoLine label="해시 알고리즘" value={integrityInfo.hashAlgorithm || "SHA-256"} />
          <InfoLine label="해시 값" value={shortHash} valueClassName="max-w-[160px] truncate font-mono text-xs" />
          <InfoLine label="전자서명" value={integrityInfo.chainValid ? "유효" : "미검증"} pillClassName="bg-emerald-50 text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-300" />
          <InfoLine label="검증 상태" value={integrityInfo.verificationStatus || "완료"} pillClassName="bg-emerald-50 text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-300" />
          <Button
            type="button"
            variant="outline"
            className="mt-auto h-10 w-full border-border bg-card text-sm font-semibold text-foreground hover:bg-muted/40"
            onClick={() => {
              // 무결성 검증 탭으로 이동(라디오 탭 트리거를 텍스트로 찾아 클릭)
              document.querySelectorAll<HTMLElement>('[role="tab"]').forEach((tab) => {
                if (tab.textContent?.includes("무결성")) tab.click()
              })
            }}
          >
            검증 상세 보기
            <ExternalLink className="size-4" aria-hidden="true" />
          </Button>
        </CompactPanel>

        <CompactPanel title="메타데이터/보고서" icon={FileText} quiet>
          <InfoLine label="보고서 상태" value={reportStatus} pillClassName="bg-emerald-50 text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-300" />
          <InfoLine label="보고서 생성" value={formatDateTime(analysisInfo.completedAt)} valueClassName="text-foreground" />
          <InfoLine
            label="보고서 파일"
            value={`report_EVD-${evidenceInfo.evidenceId}.pdf`}
            valueClassName="max-w-[170px] truncate font-mono text-xs text-blue-600 underline"
          />
          <Button
            type="button"
            variant="outline"
            className="mt-auto h-10 w-full border-blue-200 bg-card text-sm font-semibold text-blue-600 hover:bg-blue-50 hover:text-blue-700 dark:border-blue-900/60 dark:hover:bg-blue-950/30"
          >
            <Download className="size-4" aria-hidden="true" />
            보고서 다운로드
          </Button>
        </CompactPanel>
      </div>

      <ReadinessMetricSection
        evidenceId={evidenceInfo.evidenceId}
        analysisCompleted={analysisInfo.status === "COMPLETED"}
      />

      <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
        <h3 className="text-base font-semibold text-foreground">분석 진행 요약</h3>
        <ProgressTimeline steps={progressSteps} />
      </section>
    </>
  )
}

function CompactPanel({
  title,
  icon: Icon,
  children,
  className,
  quiet,
}: {
  title: string
  icon: LucideIcon
  children: ReactNode
  className?: string
  quiet?: boolean
}) {
  return (
    <section className={cn("flex flex-col rounded-xl border border-border bg-card p-5 shadow-sm", className)}>
      <h3 className="flex items-center gap-2 text-base font-semibold text-foreground">
        <Icon className={cn("size-5", quiet ? "text-teal-500" : "text-teal-600")} aria-hidden="true" />
        {title}
      </h3>
      <div className="mt-5 flex flex-1 flex-col">{children}</div>
    </section>
  )
}

function InfoLine({
  label,
  value,
  pillClassName,
  valueClassName,
}: {
  label: string
  value: string
  pillClassName?: string
  valueClassName?: string
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-border py-3 last:border-0">
      <span className="shrink-0 whitespace-nowrap text-sm font-medium text-muted-foreground">
        {label}
      </span>
      <span
        className={cn(
          "min-w-0 whitespace-nowrap rounded-full px-3 py-1 text-right text-sm font-semibold text-foreground",
          pillClassName,
          valueClassName
        )}
      >
        {value}
      </span>
    </div>
  )
}

// 신뢰도(0~100)만큼 반원 호를 채운다. 낮음→높음. 중앙에 방패 아이콘.
function RiskGauge({ value, riskLabel }: { value: number; riskLabel: string }) {
  const pct = Math.min(100, Math.max(0, value)) / 100
  const arcLen = Math.PI * 50 // 반원 호 길이 ≈ 157.08

  return (
    <div className="mt-6 flex flex-col items-center">
      <div className="relative w-44">
        <svg viewBox="0 0 120 70" className="w-44">
          <path
            d="M10,60 A50,50 0 0 1 110,60"
            fill="none"
            strokeWidth="9"
            strokeLinecap="round"
            className="stroke-slate-200 dark:stroke-muted"
          />
          <path
            d="M10,60 A50,50 0 0 1 110,60"
            fill="none"
            strokeWidth="9"
            strokeLinecap="round"
            strokeDasharray={`${arcLen * pct} ${arcLen}`}
            className="stroke-emerald-500 transition-all"
          />
        </svg>
        <ShieldCheck className="absolute bottom-1 left-1/2 size-7 -translate-x-1/2 text-emerald-600" aria-hidden="true" />
      </div>
      <div className="mt-1 flex w-44 justify-between px-3 text-xs font-medium text-muted-foreground">
        <span>낮음</span>
        <span>높음</span>
      </div>
      <p className="sr-only">위험 등급: {riskLabel}</p>
    </div>
  )
}

function ProgressTimeline({ steps }: { steps: ProgressStep[] }) {
  return (
    <div className="mt-6 grid gap-4 lg:grid-cols-6">
      {steps.map((step, index) => (
        <div key={step.title} className="relative flex flex-col items-center text-center">
          {index < steps.length - 1 ? (
            <span className="absolute left-1/2 top-4 hidden h-0.5 w-full bg-emerald-400 lg:block" />
          ) : null}
          <span
            className={cn(
              "relative z-10 flex size-8 items-center justify-center rounded-full border-2",
              step.done ? "border-emerald-500 bg-emerald-500 text-white" : "border-border bg-card text-muted-foreground"
            )}
          >
            <CheckCircle2 className="size-5" aria-hidden="true" />
          </span>
          <p className="mt-3 text-sm font-semibold text-foreground">{step.title}</p>
          <p className="mt-1 text-xs font-medium text-muted-foreground">{formatDateTime(step.time)}</p>
        </div>
      ))}
    </div>
  )
}

function formatScorePercent(score: number | null) {
  if (score == null) return null
  const normalized = score > 0 && score <= 1 ? score * 100 : score
  return Math.round(normalized)
}
