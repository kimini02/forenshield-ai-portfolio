import {
  CheckCircle2,
  Expand,
  Info,
  Pause,
  Play,
  ShieldCheck,
  Volume2,
} from "lucide-react"

import type { EvidenceDetailData, ModuleResult } from "@/lib/api/evidence-detail"
import { formatDuration } from "@/lib/formatters"
import { cn } from "@/lib/utils"

type DeepfakeDetectionSummaryProps = {
  data: EvidenceDetailData
  riskLabel: string
}

type DeepfakeModelTabProps = {
  data: EvidenceDetailData
  riskLabel: string
  riskBadgeClassName: string
}

export function DeepfakeDetectionSummary({ data, riskLabel }: DeepfakeDetectionSummaryProps) {
  const { analysisInfo } = data
  const confidence = normalizePercent(analysisInfo.confidenceScore)
  const quality = confidence == null ? null : Math.min(100, confidence + 1)
  const modelScore = getPrimaryModelScore(analysisInfo.moduleResults)

  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h2 className="text-base font-semibold text-foreground">딥페이크 탐지 요약</h2>
      <div className="mt-4 grid gap-3 md:grid-cols-3 xl:grid-cols-5">
        <SummaryMetric
          label="자동 탐지 결과"
          value={getDetectionResultLabel(riskLabel)}
          tone={getRiskToneFromLabel(riskLabel)}
          icon={<ShieldCheck className="size-4" aria-hidden="true" />}
        />
        <SummaryMetric label="모델 탐지 점수" value={modelScore ?? "-"} hint="0 ~ 1 (높을수록 의심 ↑)" tone="teal" />
        <SummaryMetric label="판정 임계값" value="0.72" tone="default" />
        <SummaryMetric label="분석 신뢰도" value={confidence == null ? "-" : `${confidence}%`} tone="teal" />
        <SummaryMetric label="품질 점수" value={quality == null ? "-" : `${quality} / 100`} tone="teal" />
      </div>
      <p className="mt-3 flex items-center gap-2 text-xs font-medium text-muted-foreground">
        <Info className="size-3.5" aria-hidden="true" />
        자동 분석 결과이며, 단독으로 콘텐츠의 진위나 법적 사실을 확정하지 않습니다.
      </p>
    </section>
  )
}

export function DeepfakeModelTab({
  data,
  riskLabel,
}: DeepfakeModelTabProps) {
  const { evidenceInfo, analysisInfo } = data
  const modules = analysisInfo.moduleResults
  const metadata = evidenceInfo.technicalMetadata
  const duration = formatDuration(metadata.durationSec)

  return (
    <div className="space-y-4">
      <DeepfakeDetectionSummary data={data} riskLabel={riskLabel} />

      <section className="grid items-stretch overflow-hidden rounded-xl border border-border bg-card shadow-sm lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.45fr)]">
        <VideoPlayerPanel fileName={evidenceInfo.fileName} duration={duration} />
        <div className="flex flex-col border-t border-border lg:border-l lg:border-t-0">
          <FrameRiskPanel />
          <ModelAnalysisInfo
            metadata={metadata}
            modules={modules}
            completed={analysisInfo.status === "COMPLETED"}
          />
        </div>
      </section>

      <section className="grid gap-4 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
        <SuspiciousSection completed={analysisInfo.status === "COMPLETED"} />
        <DeepfakeScoreBreakdown modules={modules} riskLabel={riskLabel} />
      </section>
    </div>
  )
}

function SummaryMetric({
  label,
  value,
  tone,
  hint,
  icon,
}: {
  label: string
  value: string
  tone: "default" | "teal" | "green" | "orange" | "red" | "muted"
  hint?: string
  icon?: React.ReactNode
}) {
  const toneClassName = {
    default: "text-foreground",
    teal: "text-teal-600 dark:text-teal-300",
    green: "text-emerald-600 dark:text-emerald-300",
    orange: "text-orange-500 dark:text-orange-300",
    red: "text-red-500 dark:text-red-300",
    muted: "text-muted-foreground",
  }[tone]

  return (
    <div className="flex min-h-[96px] flex-col items-center justify-center rounded-lg border border-border bg-background/40 px-3 py-3 text-center">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p className={cn("mt-2 flex items-center justify-center gap-1.5 text-xl font-semibold", toneClassName)}>
        {value}
      </p>
      {icon ? <span className={cn("mt-1", toneClassName)}>{icon}</span> : null}
      {hint ? <p className="mt-1 text-[11px] font-medium text-muted-foreground">{hint}</p> : null}
    </div>
  )
}

function VideoPlayerPanel({ fileName, duration }: { fileName: string; duration: string }) {
  return (
    <div className="flex h-full min-h-[480px] flex-col p-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-base font-semibold text-foreground">영상 플레이어</h3>
        <div className="flex rounded-full bg-muted/50 p-1">
          {["원본", "오버레이"].map((label, index) => (
            <span
              key={label}
              className={cn(
                "rounded-full px-3 py-1 text-xs font-semibold",
                index === 0 ? "bg-teal-50 text-teal-600 dark:bg-teal-950/30 dark:text-teal-300" : "text-muted-foreground"
              )}
            >
              {label}
            </span>
          ))}
        </div>
      </div>

      <div className="mt-4 min-h-[360px] flex-1 overflow-hidden rounded-lg border border-border bg-slate-950">
        <div className="relative h-full min-h-[360px] bg-[linear-gradient(135deg,#111827,#1f2937_45%,#111827)]">
          <div className="absolute left-4 top-4 rounded bg-black/35 px-2 py-1 font-mono text-xs font-medium text-white">
            {formatMockTimestamp()}
          </div>
          <div className="absolute inset-x-8 top-8 h-1 rounded-full bg-white/10" />
          <div className="absolute inset-x-8 bottom-10 grid grid-cols-5 gap-3">
            {Array.from({ length: 5 }).map((_, index) => (
              <span key={index} className="h-1.5 rounded-full bg-white/20" />
            ))}
          </div>
          <div className="absolute bottom-4 left-4 text-xs font-semibold text-white/80">CAM01</div>
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex size-16 items-center justify-center rounded-full bg-white/90 text-slate-900 shadow">
              <Play className="ml-1 size-7 fill-current" aria-hidden="true" />
            </div>
          </div>
          <p className="sr-only">{fileName} 영상 미리보기</p>
        </div>
      </div>

      <div className="mt-3 flex items-center gap-3 rounded-lg border border-border bg-background/60 px-3 py-2">
        <button type="button" className="text-muted-foreground transition-colors hover:text-foreground" aria-label="재생">
          <Play className="size-4 fill-current" aria-hidden="true" />
        </button>
        <button type="button" className="text-muted-foreground transition-colors hover:text-foreground" aria-label="일시정지">
          <Pause className="size-4" aria-hidden="true" />
        </button>
        <span className="text-xs font-medium text-muted-foreground">00:12 / {duration === "-" ? "00:30" : duration}</span>
        <div className="h-1.5 min-w-0 flex-1 rounded-full bg-muted">
          <div className="h-full w-2/5 rounded-full bg-teal-500" />
        </div>
        <Volume2 className="size-4 text-muted-foreground" aria-hidden="true" />
        <span className="text-xs font-semibold text-muted-foreground">1.0x</span>
        <Expand className="size-4 text-muted-foreground" aria-hidden="true" />
      </div>
    </div>
  )
}

function FrameRiskPanel() {
  return (
    <div className="border-b border-border p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="flex items-center gap-1.5 text-base font-semibold text-foreground">
          프레임별 위험도 (모델 점수)
          <Info className="size-3.5 text-muted-foreground" aria-hidden="true" />
        </h3>
        <div className="flex flex-wrap items-center gap-3 text-[11px] font-medium text-muted-foreground">
          <LegendDot className="bg-teal-500" label="낮음 (0~0.3)" />
          <LegendDot className="bg-orange-400" label="보통 (0.3~0.6)" />
          <LegendDot className="bg-red-500" label="높음 (0.6~1.0)" />
          <LegendDot className="bg-slate-300" label="분석 불가" />
        </div>
      </div>

      <div className="mt-4 rounded-lg border border-dashed border-border bg-muted/20 px-4 py-10 text-center">
        <p className="text-sm font-medium text-muted-foreground">프레임별 분석 결과가 없습니다.</p>
        <p className="mt-1 text-xs font-medium text-muted-foreground">
          백엔드에서 프레임별 모델 점수가 제공되면 이 영역에 차트로 표시됩니다.
        </p>
      </div>
    </div>
  )
}

function ModelAnalysisInfo({
  metadata,
  modules,
  completed,
}: {
  metadata: EvidenceDetailData["evidenceInfo"]["technicalMetadata"]
  modules: ModuleResult[]
  completed: boolean
}) {
  const rowsLeft = [
    ["분석 모델", modules[0]?.moduleName?.replace(/_/g, " ") || "DeepScan Video"],
    ["모델 버전", "v2.4.1"],
    ["분석 작업 ID", completed ? "AJ-" : "-"],
    ["분석 프레임 수", "-"],
    ["유효 프레임 수", "-"],
  ]
  const rowsRight = [
    ["얼굴 검출 프레임 수", "-"],
    ["프레임 추출 간격", "5프레임"],
    ["입력 해상도", metadata.width && metadata.height ? `${metadata.width} × ${metadata.height}` : "-"],
    ["판정 임계값", "0.72"],
    ["처리 시간", "-"],
  ]

  return (
    <div className="p-4">
      <h3 className="text-base font-semibold text-foreground">모델 분석 정보</h3>
      <div className="mt-3 grid gap-x-8 md:grid-cols-2">
        <InfoRows rows={rowsLeft} />
        <InfoRows rows={rowsRight} />
      </div>
    </div>
  )
}

function InfoRows({ rows }: { rows: string[][] }) {
  return (
    <div>
      {rows.map(([label, value]) => (
        <div key={label} className="flex items-center justify-between gap-4 border-b border-border py-2 text-sm">
          <span className="text-xs font-medium text-muted-foreground">{label}</span>
          <span className="text-right font-medium text-foreground">{value}</span>
        </div>
      ))}
    </div>
  )
}

function SuspiciousSection({ completed }: { completed: boolean }) {
  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="flex items-center gap-1.5 text-base font-semibold text-foreground">
        주요 의심 구간
        <Info className="size-3.5 text-muted-foreground" aria-hidden="true" />
      </h3>
      <div className="mt-4 flex min-h-[104px] items-center gap-4 rounded-lg border border-teal-100 bg-teal-50/40 px-5 py-4 dark:border-teal-900/50 dark:bg-teal-950/20">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-full border border-teal-200 bg-card text-teal-600 dark:border-teal-800 dark:text-teal-300">
          <CheckCircle2 className="size-6" aria-hidden="true" />
        </span>
        <p className="text-sm font-medium text-muted-foreground">
          {completed
            ? "판정 임계값을 초과한 연속 의심 구간이 발견되지 않았습니다."
            : "분석이 완료되면 주요 의심 구간을 확인할 수 있습니다."}
        </p>
      </div>
    </section>
  )
}

function DeepfakeScoreBreakdown({ modules, riskLabel }: { modules: ModuleResult[]; riskLabel: string }) {
  const items = buildBreakdownItems(modules, riskLabel)

  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="flex items-center gap-1.5 text-base font-semibold text-foreground">
        딥페이크 세부 항목별 점수
        <Info className="size-3.5 text-muted-foreground" aria-hidden="true" />
      </h3>
      <div className="mt-4 grid gap-3 md:grid-cols-5">
        {items.map((item) => (
          <div key={item.label} className="rounded-lg border border-border bg-background/40 px-3 py-4 text-center">
            <p className="min-h-10 text-xs font-medium text-muted-foreground">{item.label}</p>
            <p className={cn("mt-2 text-lg font-semibold", item.toneClassName)}>{item.value}</p>
            <p className={cn("mt-1 text-xs font-semibold", item.toneClassName)}>{item.badge}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

function LegendDot({ className, label }: { className: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={cn("size-2 rounded-full", className)} aria-hidden="true" />
      {label}
    </span>
  )
}

function buildBreakdownItems(modules: ModuleResult[], riskLabel: string) {
  const labels = [
    "얼굴 경계 불연속",
    "시간축 일관성 저하",
    "질감 · 주파수 이상",
    "조명 · 색상 불일치",
    "오디오 · 립싱크 불일치",
  ]

  return labels.map((label, index) => {
    const resultModule = modules[index]
    if (!resultModule) {
      return {
        label,
        value: index === labels.length - 1 ? "해당 없음" : "-",
        badge: index === labels.length - 1 ? "오디오 트랙이 없습니다." : "분석 결과 없음",
        toneClassName: "text-muted-foreground",
      }
    }

    const score = normalizeProbability(resultModule.score)
    const badge = getScoreBadge(score, riskLabel)

    return {
      label,
      value: score.toFixed(2),
      badge,
      toneClassName: badge === "높음"
        ? "text-red-500 dark:text-red-300"
        : badge === "보통"
          ? "text-orange-500 dark:text-orange-300"
          : "text-teal-600 dark:text-teal-300",
    }
  })
}

function getPrimaryModelScore(modules: ModuleResult[]) {
  if (!modules[0]) return null
  return normalizeProbability(modules[0].score).toFixed(2)
}

function normalizeProbability(score: number) {
  if (score > 1) return Math.min(1, score / 100)
  return Math.max(0, score)
}

function normalizePercent(score: number | null) {
  if (score == null) return null
  const normalized = score > 0 && score <= 1 ? score * 100 : score
  return Math.round(normalized)
}

function getScoreBadge(score: number, fallbackLabel: string) {
  if (score >= 0.6) return "높음"
  if (score >= 0.3) return "보통"
  if (fallbackLabel === "분석 근거 없음") return "분석 결과 없음"
  return "낮음"
}

function getDetectionResultLabel(riskLabel: string) {
  if (riskLabel === "위험") return "조작 의심 높음"
  if (riskLabel === "주의") return "조작 의심 보통"
  if (riskLabel === "정상") return "조작 의심 낮음"
  return riskLabel
}

function getRiskToneFromLabel(riskLabel: string) {
  if (riskLabel === "위험") return "red"
  if (riskLabel === "주의") return "orange"
  if (riskLabel === "정상") return "green"
  return "muted"
}

function formatMockTimestamp() {
  return "2026-06-15 02:31:45"
}
