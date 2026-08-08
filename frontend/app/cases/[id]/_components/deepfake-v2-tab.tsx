"use client"

import { useState } from "react"
import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Info,
  Maximize2,
  Pause,
  Play,
  Volume2,
} from "lucide-react"

import { EvidenceHlsPlayer } from "@/components/evidence-hls-player"
import type {
  ClipRisk,
  EvidenceDetailData,
  FrameRisk,
  FrameScore,
  ModelScore,
  ModuleResult,
  ModuleTimeline,
  ModuleTimelineKind,
  PairRisk,
  PerFrameFaceScore,
  RepresentativeFrame,
  SuspiciousSegment,
} from "@/lib/api/evidence-detail"
import {
  resolveModelScoreThreshold,
} from "@/lib/api/analysis-result-ui"
import { formatDuration } from "@/lib/formatters"
import { cn } from "@/lib/utils"

type DeepfakeV2TabProps = {
  data: EvidenceDetailData
}

type DeepfakeTimelineTabKey = Exclude<ModuleTimelineKind, "forgery_spatial" | "forgery_temporal">
type ViewMode = "original" | "overlay"
type TimelineTabKey = DeepfakeTimelineTabKey

const DEFAULT_THRESHOLD = 0.6
const MODEL_SCORE_ORDER = ["deepfake", "deepfake_cnn", "deepfake_temporal", "deepfake_optical"] as const

const MODEL_SCORE_DISPLAY: Record<
  (typeof MODEL_SCORE_ORDER)[number],
  { title: string; role: string; shortRole: string }
> = {
  deepfake: {
    title: "Late Fusion",
    role: "Xception, TimeSformer, GMFlow 결과를 합산한 최종 판단",
    shortRole: "종합 판정",
  },
  deepfake_cnn: {
    title: "Xception",
    role: "얼굴 경계와 질감 패턴의 공간적 합성 흔적",
    shortRole: "프레임·공간 특징",
  },
  deepfake_temporal: {
    title: "TimeSformer",
    role: "프레임 흐름과 클립 단위 시간 일관성",
    shortRole: "클립·시계열",
  },
  deepfake_optical: {
    title: "GMFlow",
    role: "연속 프레임쌍의 움직임 벡터 불안정성",
    shortRole: "움직임 벡터",
  },
}

const TIMELINE_DISPLAY: Record<DeepfakeTimelineTabKey, { label: string; title: string; description: string }> = {
  cnn: {
    label: "Xception",
    title: "프레임별 위험도",
    description: "얼굴 경계와 질감 패턴에서 감지된 프레임 단위 조작 의심 신호입니다.",
  },
  temporal: {
    label: "TimeSformer",
    title: "클립별 위험도",
    description: "연속 프레임 흐름에서 감지된 클립 단위 시간 일관성 이상 신호입니다.",
  },
  optical: {
    label: "GMFlow",
    title: "프레임쌍 위험도",
    description: "연속 프레임쌍의 움직임 벡터에서 감지된 불안정 패턴입니다.",
  },
}

export function DeepfakeV2Tab({ data }: DeepfakeV2TabProps) {
  const { evidenceInfo, analysisInfo } = data
  const metadata = evidenceInfo.technicalMetadata

  const modelScore = getFusionModelScore(analysisInfo.modelScores ?? []) ?? getPrimaryModelScore(analysisInfo.moduleResults)
  const confidence = normalizePercent(analysisInfo.confidenceScore)
  const threshold = analysisInfo.detectionThreshold ?? DEFAULT_THRESHOLD
  const duration = formatDuration(metadata.durationSec)
  const verdict = getVerdict(modelScore, threshold)
  const summary = analysisInfo.summary?.trim() || "AI 탐지 근거가 아직 제공되지 않았습니다."
  const videoUrl = getPlayableVideoUrl(data)
  const hlsPlayback = data.hlsPlayback ?? null
  const overlayVideoUrl = analysisInfo.overlayVideoUrl ?? evidenceInfo.overlayVideoUrl ?? null
  const modelScoreCards = buildModelScoreCards(data, threshold)
  const timelineTabs = buildTimelineTabs(data, threshold)
  const faceRiskSummaries = buildFaceRiskSummaries(analysisInfo.perFrameFaceScores ?? [])
  const advisory = resolveDeepfakeAdvisory(analysisInfo.errorCode, analysisInfo.errorMessage)

  return (
    <div className="space-y-4">
      {advisory ? <DeepfakeAdvisoryBanner title={advisory.title} message={advisory.message} /> : null}
      <SummaryCards verdict={verdict} modelScore={modelScore} confidence={confidence} threshold={threshold} />
      <ModelScoreGrid cards={modelScoreCards} />
      {faceRiskSummaries.length > 0 ? <FaceRiskPanel faces={faceRiskSummaries} threshold={threshold} /> : null}

      <div className="grid items-stretch gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
        <VideoPlayerCard
          duration={duration}
          hlsPlayback={hlsPlayback}
          videoUrl={videoUrl}
          overlayVideoUrl={overlayVideoUrl}
        />
        <ModelInfoSidebar data={data} threshold={threshold} />
      </div>

      <ModuleTimelineTabs tabs={timelineTabs} />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-w-0 space-y-4">
          <RepresentativeFrames frames={analysisInfo.representativeFrames ?? []} />
        </div>
        <div className="min-w-0 space-y-4">
          <ReasoningNote summary={summary} />
        </div>
      </div>
    </div>
  )
}

function VideoPlayerCard({
  duration,
  hlsPlayback,
  videoUrl,
  overlayVideoUrl,
}: {
  duration: string
  hlsPlayback: EvidenceDetailData["hlsPlayback"]
  videoUrl: string | null
  overlayVideoUrl: string | null
}) {
  const [view, setView] = useState<ViewMode>("original")
  const canShowOverlay = Boolean(overlayVideoUrl)

  return (
    <section className="rounded-xl border border-border bg-card p-3 shadow-sm">
      <div className="relative aspect-video overflow-hidden rounded-lg bg-slate-950">
        {view === "overlay" && overlayVideoUrl ? (
          <video
            src={overlayVideoUrl}
            className="absolute inset-0 size-full object-cover"
            controls
            playsInline
            controlsList="nodownload"
            disablePictureInPicture
          />
        ) : hlsPlayback || videoUrl ? (
          <EvidenceHlsPlayer
            playback={hlsPlayback}
            objectFit="cover"
            showControls
          />
        ) : (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-muted/70 px-6 text-center">
            <p className="text-sm font-semibold text-foreground">영상을 재생할 수 없습니다.</p>
            <p className="mt-2 text-xs font-medium leading-5 text-muted-foreground">
              HLS 패키징이 완료되면 이 영역에서 암호화 스트림으로 재생됩니다.
            </p>
          </div>
        )}

        <div className="absolute right-3 top-3 flex rounded-full bg-black/45 p-1 backdrop-blur-sm">
          {(
            [
              ["original", "원본", true],
              ["overlay", "오버레이", canShowOverlay],
            ] as const
          ).map(([mode, label, enabled]) => (
            <button
              key={mode}
              type="button"
              onClick={() => {
                if (enabled) setView(mode)
              }}
              disabled={!enabled}
              className={cn(
                "rounded-full px-3 py-1 text-xs font-semibold transition-colors",
                view === mode ? "bg-emerald-500 text-white" : "text-white/80 hover:text-white",
                !enabled && "cursor-not-allowed text-white/35 hover:text-white/35"
              )}
            >
              {label}
            </button>
          ))}
        </div>

        {!hlsPlayback && !videoUrl ? (
          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 to-transparent px-4 pb-3 pt-8">
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-white/30">
              <div className="h-full w-[40%] rounded-full bg-emerald-400" />
            </div>
            <div className="mt-2 flex items-center gap-3 text-white">
              <Play className="size-4 fill-current" aria-hidden="true" />
              <Pause className="size-4" aria-hidden="true" />
              <span className="text-xs font-semibold">00:12 / {duration === "-" ? "00:03.000" : duration}</span>
              <Volume2 className="ml-auto size-4" aria-hidden="true" />
              <span className="text-xs font-semibold">1.0x</span>
              <Maximize2 className="size-4" aria-hidden="true" />
            </div>
          </div>
        ) : null}
      </div>
      {!canShowOverlay ? (
        <p className="mt-2 text-xs font-medium text-muted-foreground">
          오버레이는 AI 산출물이 제공될 때 활성화됩니다.
        </p>
      ) : null}
    </section>
  )
}

function resolveDeepfakeAdvisory(
  errorCode?: string | null,
  errorMessage?: string | null
): { title: string; message: string } | null {
  const message = errorMessage?.trim() || ""
  switch (errorCode) {
    case "NO_HUMAN_FACE":
      return {
        title: "딥페이크 판별 불가 (얼굴 미검출)",
        message:
          message ||
          "사람 얼굴이 검출되지 않아 딥페이크 판별을 수행할 수 없습니다. 위변조 등 후속 분석은 계속 진행합니다.",
      }
    case "FACE_TOO_SMALL":
      return {
        title: "딥페이크 판별 보류 (얼굴 너무 작음)",
        message:
          message ||
          "검출된 얼굴이 너무 작아 신뢰 가능한 딥페이크 판별을 보류합니다. 위변조 등 후속 분석은 계속 진행합니다.",
      }
    case "INSUFFICIENT_FACE_SAMPLES":
      return {
        title: "딥페이크 판별 보류 (얼굴 샘플 부족)",
        message:
          message ||
          "분석에 쓸 수 있는 얼굴 프레임이 부족하여 딥페이크 판별을 보류합니다. 위변조 등 후속 분석은 계속 진행합니다.",
      }
    case "TEMPORAL_MODULE_UNAVAILABLE":
      return {
        title: "시계열 모듈 제한 (CNN·광학 중심 판별)",
        message:
          message ||
          "TimeSformer 모듈을 사용할 수 없어 CNN·광학 흐름 중심으로 판별했습니다.",
      }
    default:
      if (message.includes("사람 얼굴")) {
        return { title: "딥페이크 판별 불가 (얼굴 미검출)", message }
      }
      return null
  }
}

function DeepfakeAdvisoryBanner({ title, message }: { title: string; message: string }) {
  return (
    <div
      role="status"
      className="flex gap-3 rounded-xl border border-amber-300/80 bg-amber-50 px-4 py-3 text-sm text-amber-950 dark:border-amber-500/40 dark:bg-amber-950/40 dark:text-amber-50"
    >
      <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-300" aria-hidden="true" />
      <div className="min-w-0 space-y-1">
        <p className="font-semibold">{title}</p>
        <p className="text-xs font-medium leading-5 opacity-90">{message}</p>
      </div>
    </div>
  )
}

function SummaryCards({
  verdict,
  modelScore,
  confidence,
  threshold,
}: {
  verdict: { label: string; tone: Tone }
  modelScore: number | null
  confidence: number | null
  threshold: number
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <SummaryCard title="자동 탐지 결과" value={verdict.label} tone={verdict.tone} icon />
      <SummaryCard
        title="모델 점수"
        value={formatNullable(modelScore, (value) => `${Math.round(value * 100)} / 100`)}
        sub="높을수록 조작 의심"
        tone={modelScore == null ? "neutral" : toneByScore(modelScore, threshold)}
        emphasize={modelScore != null && modelScore >= threshold}
      />
      <SummaryCard
        title="판정 임계값"
        value={`${Math.round(threshold * 100)} / 100`}
        sub="기준값 초과 시 의심"
        tone="neutral"
      />
      <SummaryCard
        title="모델 산출 확신도"
        value={formatNullable(confidence, (value) => `${value}%`)}
        sub="분석 모델이 보고한 확신도"
        tone="neutral"
      />
    </div>
  )
}

function SummaryCard({
  title,
  value,
  sub,
  tone,
  icon,
  emphasize,
}: {
  title: string
  value: string
  sub?: string
  tone: Tone
  icon?: boolean
  emphasize?: boolean
}) {
  return (
    <section
      className={cn(
        "flex min-h-[148px] flex-col rounded-xl border bg-card p-4 text-center shadow-sm",
        emphasize ? "border-red-200 dark:border-red-900/50" : "border-border"
      )}
    >
      <p className="flex items-center justify-center gap-1 text-xs font-bold text-muted-foreground">
        {title}
        <Info className="size-3 text-muted-foreground/60" aria-hidden="true" />
      </p>
      <div className="flex flex-1 items-center justify-center">
        <p className={cn("flex items-center justify-center gap-1.5 font-bold", TONE_TEXT[tone], icon ? "text-3xl leading-none" : "text-[2rem] leading-none")}>
          {icon ? <AlertTriangle className="size-7 shrink-0" aria-hidden="true" /> : null}
          {value}
        </p>
      </div>
      <p className="min-h-[16px] text-[11px] font-semibold text-muted-foreground">{sub ?? ""}</p>
    </section>
  )
}

type ModelScoreCard = {
  key: (typeof MODEL_SCORE_ORDER)[number]
  title: string
  role: string
  shortRole: string
  score: number | null
  threshold: number
  detected: boolean | null
  modelName: string | null
  modelVersion: string | null
}

function ModelScoreGrid({ cards }: { cards: ModelScoreCard[] }) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-base font-bold text-foreground">모델별 판단 점수</h3>
          <p className="mt-1 text-xs font-semibold text-muted-foreground">
            각 막대의 점선이 그 모델의 판정 기준입니다. 기준을 넘으면 해당 모듈이 탐지로 표시됩니다.
          </p>
        </div>
        <span className="rounded-full bg-red-50 px-3 py-1 text-xs font-bold text-red-700 dark:bg-red-500/10 dark:text-red-300">
          모듈별 기준선
        </span>
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => {
          const tone = card.score == null ? "neutral" : toneByScore(card.score, card.threshold)
          const detected = card.detected ?? (card.score != null ? card.score >= card.threshold : false)
          const scorePercent = card.score == null ? null : Math.round(card.score * 100)
          const thresholdPercent = Math.round(card.threshold * 100)

          return (
            <article
              key={card.key}
              className={cn(
                "flex min-h-[198px] flex-col rounded-lg border bg-background/35 p-4",
                detected ? "border-red-200 dark:border-red-900/50" : "border-border"
              )}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h4 className="truncate text-sm font-black text-foreground">{card.title}</h4>
                  <p className="mt-1 text-[11px] font-bold text-muted-foreground">{card.shortRole}</p>
                </div>
                <span className={cn("shrink-0 rounded-full px-2 py-1 text-[11px] font-bold", TONE_BADGE[tone])}>
                  {detected ? "탐지" : card.score == null ? "대기" : "정상"}
                </span>
              </div>

              <div className="mt-3 flex flex-1 items-end gap-3">
                <div className="min-w-0 flex-1">
                  <p className={cn("text-3xl font-black leading-none", TONE_TEXT[tone])}>
                    {scorePercent == null ? "-" : scorePercent}
                    <span className="ml-1 text-base font-bold text-muted-foreground">/ 100</span>
                  </p>
                  <p className="mt-2 text-[11px] font-bold text-muted-foreground">
                    기준 {thresholdPercent} 초과 시 탐지
                  </p>
                </div>
                <div className="flex h-20 shrink-0 items-stretch gap-1">
                  <div className="relative w-[42px]">
                    <div
                      aria-hidden="true"
                      className="absolute inset-x-0 flex items-center justify-end"
                      style={{ bottom: `${Math.max(0, Math.min(100, thresholdPercent))}%` }}
                    >
                      <span className="whitespace-nowrap text-[9px] font-bold leading-none text-slate-500">
                        기준 {thresholdPercent}
                      </span>
                      <span className="ml-0.5 w-2.5 shrink-0 border-t border-dashed border-slate-500/80" />
                    </div>
                  </div>
                  <div className="relative h-full w-10 overflow-hidden rounded-md border border-border bg-muted/40">
                    <div
                      aria-hidden="true"
                      className="absolute inset-x-0 border-t border-dashed border-slate-500/80"
                      style={{ bottom: `${Math.max(0, Math.min(100, thresholdPercent))}%` }}
                    />
                    <div
                      className={cn(
                        "absolute inset-x-1 bottom-0 rounded-sm",
                        detected ? "bg-red-600 dark:bg-red-500" : "bg-emerald-600 dark:bg-emerald-500"
                      )}
                      style={{ height: `${Math.max(2, scorePercent ?? 0)}%` }}
                    />
                  </div>
                </div>
              </div>

              <p className="mt-3 line-clamp-2 text-[11px] font-medium leading-4 text-muted-foreground">{card.role}</p>
              <p className="mt-2 truncate text-[11px] font-bold text-slate-400" title={card.modelVersion ?? undefined}>
                {card.modelName ?? card.title}
                {card.modelVersion ? ` · ${cleanModelVersion(card.modelVersion)}` : ""}
              </p>
            </article>
          )
        })}
      </div>
    </section>
  )
}

type FaceRiskSummary = {
  faceIndex: number
  maxScore: number
  sampleCount: number
}

function buildFaceRiskSummaries(scores: PerFrameFaceScore[]): FaceRiskSummary[] {
  if (scores.length === 0) return []
  const byFace = new Map<number, FaceRiskSummary>()
  for (const row of scores) {
    const score = normalizeProbability(row.riskScore)
    const current = byFace.get(row.faceIndex)
    if (!current) {
      byFace.set(row.faceIndex, {
        faceIndex: row.faceIndex,
        maxScore: score,
        sampleCount: 1,
      })
      continue
    }
    current.maxScore = Math.max(current.maxScore, score)
    current.sampleCount += 1
  }
  return [...byFace.values()].sort((a, b) => b.maxScore - a.maxScore || a.faceIndex - b.faceIndex)
}

function FaceRiskPanel({ faces, threshold }: { faces: FaceRiskSummary[]; threshold: number }) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-base font-bold text-foreground">얼굴별 위험도</h3>
          <p className="mt-1 text-xs font-semibold text-muted-foreground">
            탐지된 얼굴마다 Late Fusion 점수를 계산합니다. 영상 요약은 이 중 최고점을 사용합니다.
          </p>
        </div>
        <span className="rounded-full bg-muted px-3 py-1 text-xs font-bold text-muted-foreground">
          {faces.length}명 탐지
        </span>
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {faces.map((face) => {
          const tone = toneByScore(face.maxScore, threshold)
          return (
            <article key={face.faceIndex} className="rounded-lg border border-border bg-background/35 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h4 className="text-sm font-black text-foreground">얼굴 {face.faceIndex + 1}</h4>
                  <p className="mt-1 text-[11px] font-bold text-muted-foreground">{face.sampleCount}개 샘플</p>
                </div>
                <span className={cn("rounded-full px-2 py-1 text-[11px] font-bold", TONE_BADGE[tone])}>
                  {face.maxScore >= threshold ? "위험" : face.maxScore >= 0.3 ? "주의" : "정상"}
                </span>
              </div>
              <p className={cn("mt-3 text-3xl font-black leading-none", TONE_TEXT[tone])}>
                {Math.round(face.maxScore * 100)}
                <span className="ml-1 text-base font-bold text-muted-foreground">/ 100</span>
              </p>
            </article>
          )
        })}
      </div>
    </section>
  )
}

type ModuleTimelineTab = {
  key: TimelineTabKey
  label: string
  title: string
  description: string
  modelName: string
  modelVersion: string | null
  videoScore: number | null
  threshold: number
  detected: boolean
  points: FrameScore[]
  segments: SuspiciousSegment[]
  emptyTitle: string
}

function ModuleTimelineTabs({ tabs }: { tabs: ModuleTimelineTab[] }) {
  const [activeKey, setActiveKey] = useState<TimelineTabKey>("cnn")
  const activeTab = tabs.find((tab) => tab.key === activeKey) ?? tabs[0]

  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h3 className="text-base font-bold text-foreground">모듈별 타임라인 근거</h3>
          <p className="mt-1 text-xs font-semibold text-muted-foreground">
            각 모델이 실제로 본 단위에 맞춰 프레임, 클립, 프레임쌍 위험도를 나눠 표시합니다.
          </p>
        </div>
        <div className="grid grid-cols-3 rounded-lg bg-muted p-1 text-xs font-bold">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveKey(tab.key)}
              className={cn(
                "h-9 rounded-md px-3 transition-colors",
                activeTab.key === tab.key
                  ? "bg-card text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
        <FrameRiskGraph
          frameScores={activeTab.points}
          title={activeTab.title}
          description={activeTab.description}
          threshold={activeTab.threshold}
          emptyTitle={activeTab.emptyTitle}
          emptyDescription="백엔드가 해당 모듈의 타임라인을 빈 배열로 내려준 경우입니다. 데이터가 제공되면 같은 위치에 차트가 표시됩니다."
        />
        <SuspiciousSegmentPanel tab={activeTab} />
      </div>
    </section>
  )
}

function SuspiciousSegmentPanel({ tab }: { tab: ModuleTimelineTab }) {
  const scoreTone = tab.videoScore == null ? "neutral" : toneByScore(tab.videoScore, tab.threshold)

  return (
    <aside className="rounded-xl border border-border bg-background/35 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h4 className="truncate text-sm font-black text-foreground">{tab.label}</h4>
          <p className="mt-1 truncate text-xs font-semibold text-muted-foreground">
            {tab.modelName}
            {tab.modelVersion ? ` · ${cleanModelVersion(tab.modelVersion)}` : ""}
          </p>
        </div>
        <span className={cn("shrink-0 rounded-full px-2 py-1 text-[11px] font-bold", TONE_BADGE[scoreTone])}>
          {tab.detected ? "탐지" : tab.videoScore == null ? "정보 없음" : "정상"}
        </span>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2">
        <div className="rounded-lg border border-border bg-card px-3 py-2">
          <p className="text-[11px] font-bold text-muted-foreground">모듈 점수</p>
          <p className={cn("mt-1 text-lg font-black", TONE_TEXT[scoreTone])}>
            {tab.videoScore == null ? "-" : `${Math.round(tab.videoScore * 100)} / 100`}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-card px-3 py-2">
          <p className="text-[11px] font-bold text-muted-foreground">임계값</p>
          <p className="mt-1 text-lg font-black text-foreground">{Math.round(tab.threshold * 100)} / 100</p>
        </div>
      </div>

      <div className="mt-4">
        <h5 className="text-xs font-black text-foreground">의심 구간</h5>
        {tab.segments.length > 0 ? (
          <div className="mt-2 space-y-2">
            {tab.segments.map((segment, index) => {
              const score = normalizeProbability(segment.maxRiskScore)
              const tone = toneByScore(score, tab.threshold)

              return (
                <article key={`${segment.startTime}-${segment.endTime}-${index}`} className="rounded-lg border border-border bg-card p-3">
                  <div className="flex items-center justify-between gap-3">
                    <p className="font-mono text-xs font-black text-foreground">
                      {formatSeconds(segment.startTime)} - {formatSeconds(segment.endTime)}
                    </p>
                    <span className={cn("rounded-full px-2 py-0.5 text-[11px] font-bold", TONE_BADGE[tone])}>
                      {Math.round(score * 100)}
                    </span>
                  </div>
                  <p className="mt-2 text-xs font-medium leading-5 text-muted-foreground">
                    {formatSegmentReason(segment.reason)}
                  </p>
                </article>
              )
            })}
          </div>
        ) : (
          <div className="mt-2 rounded-lg border border-dashed border-border bg-muted/20 px-3 py-6 text-center">
            <p className="text-xs font-semibold text-muted-foreground">표시할 의심 구간이 없습니다.</p>
          </div>
        )}
      </div>
    </aside>
  )
}

function FrameRiskGraph({
  frameScores,
  title = "프레임별 위험도 그래프",
  description,
  threshold = DEFAULT_THRESHOLD,
  emptyTitle = "프레임별 분석 결과가 없습니다.",
  emptyDescription = "백엔드에서 프레임별 모델 점수가 제공되면 이 영역에 차트로 표시됩니다.",
}: {
  frameScores: FrameScore[]
  title?: string
  description?: string
  threshold?: number
  emptyTitle?: string
  emptyDescription?: string
}) {
  const bars = buildRiskBars(frameScores, threshold)
  const thresholdTop = `${Math.max(0, Math.min(100, (1 - threshold) * 100))}%`

  return (
    <section className="rounded-xl border border-border bg-background/35 p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-base font-bold text-foreground">{title}</h3>
          {description ? <p className="mt-1 text-xs font-semibold text-muted-foreground">{description}</p> : null}
        </div>
        <div className="flex flex-wrap items-center gap-3 text-[11px] font-medium text-muted-foreground">
          <LegendDot className="bg-slate-300 dark:bg-slate-600" label="낮음 (0~30)" />
          <LegendDot className="bg-amber-400" label="보통 (30~60)" />
          <LegendDot className="bg-red-700 dark:bg-red-500" label="높음 (60~100)" />
        </div>
      </div>

      {bars.length > 0 ? (
        <>
          <div className="mt-4 grid grid-cols-[30px_minmax(0,1fr)] gap-2">
            <div className="flex h-40 flex-col justify-between pt-1 text-[11px] font-medium text-muted-foreground">
              <span>1.0</span>
              <span>0.5</span>
              <span>0</span>
            </div>
            <div className="relative h-40">
              <div className="pointer-events-none absolute inset-x-0 z-20 h-0" style={{ top: thresholdTop }}>
                <div className="absolute inset-x-0 -top-px border-t-4 border-white/95 dark:border-slate-950/90" />
                <div className="absolute inset-x-0 top-0 border-t border-dashed border-red-500" />
                <span className="absolute -top-4 right-0 rounded bg-white px-1.5 text-[11px] font-bold text-red-600 shadow-sm ring-1 ring-red-100 dark:bg-slate-950 dark:ring-red-950/60">
                  임계값 {Math.round(threshold * 100)}
                </span>
              </div>
              <div className="absolute inset-x-0 bottom-0 flex h-full items-end gap-px px-2">
                {bars.map((bar, index) => (
                  <span
                    key={`${bar.time}-${index}`}
                    className={cn("min-w-[3px] flex-1 rounded-[1px]", bar.className)}
                    style={{ height: `${bar.height}%` }}
                    aria-hidden="true"
                  />
                ))}
              </div>
            </div>
          </div>
          <div className="ml-[38px] mt-2 flex justify-between text-[11px] font-medium text-muted-foreground">
            <span>{formatFrameTime(frameScores[0])}</span>
            <span>{formatFrameTime(frameScores[Math.floor(frameScores.length / 4)])}</span>
            <span>{formatFrameTime(frameScores[Math.floor(frameScores.length / 2)])}</span>
            <span>{formatFrameTime(frameScores[Math.floor((frameScores.length * 3) / 4)])}</span>
            <span>{formatFrameTime(frameScores[frameScores.length - 1])}</span>
          </div>
        </>
      ) : (
        <EmptyState
          title={emptyTitle}
          description={emptyDescription}
        />
      )}
    </section>
  )
}

function ReasoningNote({ summary }: { summary: string }) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h3 className="flex items-center gap-1.5 text-base font-bold text-foreground">
        판정 임계값
        <Info className="size-3.5 text-muted-foreground/60" aria-hidden="true" />
        <span className="text-muted-foreground">/ 탐지 근거 설명</span>
      </h3>
      <p className="mt-4 text-sm font-medium leading-7 text-muted-foreground">{summary}</p>
      <button
        type="button"
        className="mt-5 h-10 w-full rounded-md border border-border text-sm font-semibold text-muted-foreground hover:bg-muted/40"
      >
        자세히 보기
      </button>
    </section>
  )
}

function RepresentativeFrames({ frames }: { frames: RepresentativeFrame[] }) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h3 className="text-base font-bold text-foreground">대표 프레임 <span className="text-sm font-semibold text-muted-foreground">(의심 구간)</span></h3>
      <p className="mt-1 text-xs font-semibold text-muted-foreground">모델 점수가 높았던 주요 의심 프레임입니다.</p>
      {frames.length > 0 ? (
        <>
          <div className="mt-4 flex items-center gap-2">
            <ChevronLeft className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
            <div className="grid min-w-0 flex-1 gap-4 md:grid-cols-3">
              {frames.map((frame, index) => (
                <article key={`${formatFrameTime(frame)}-${index}`} className="min-w-0 rounded-lg border border-border bg-background/40 p-3">
                  <p className="truncate text-[11px] font-semibold text-muted-foreground">
                    {formatFrameTime(frame)}
                    {frame.frameNumber != null ? (
                      <span className="text-muted-foreground/70"> (프레임 {frame.frameNumber})</span>
                    ) : null}
                  </p>
                  <div className="mt-2">
                    <FrameImage src={frame.imageUrl} label="대표 프레임 이미지" />
                  </div>
                  <p className="mt-2 text-xs font-bold text-foreground">
                    점수 {frame.score == null ? "-" : normalizeProbability(frame.score).toFixed(2)}
                  </p>
                </article>
              ))}
            </div>
            <ChevronRight className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
          </div>
          <button
            type="button"
            className="mx-auto mt-4 flex h-9 items-center rounded-md border border-border px-4 text-xs font-semibold text-muted-foreground hover:bg-muted/40"
          >
            전체 프레임 보기
          </button>
        </>
      ) : (
        <EmptyState
          title="대표 프레임 결과가 없습니다."
          description="AI가 대표 프레임 이미지를 제공하면 이 영역에 표시됩니다."
        />
      )}
    </section>
  )
}

function FrameImage({ src, label }: { src?: string | null; label: string }) {
  if (!src) {
    return (
      <div className="flex aspect-square items-center justify-center rounded-md border border-dashed border-border bg-muted/35 px-2 text-center text-[10px] font-semibold text-muted-foreground">
        이미지 없음
      </div>
    )
  }

  return (
    <img
      src={src}
      alt={label}
      className="aspect-square rounded-md object-cover"
    />
  )
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="mt-4 flex min-h-[140px] flex-col items-center justify-center rounded-lg border border-dashed border-border bg-muted/20 px-4 text-center">
      <p className="text-sm font-semibold text-foreground">{title}</p>
      <p className="mt-2 text-xs font-medium leading-5 text-muted-foreground">{description}</p>
    </div>
  )
}

function PerItemScores({ modules }: { modules: ModuleResult[] }) {
  const rows = buildScoreRows(modules)

  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h3 className="flex items-center gap-1.5 text-base font-bold text-foreground">
        탐지 항목별 점수
        <Info className="size-3.5 text-muted-foreground/60" aria-hidden="true" />
      </h3>
      {rows.length > 0 ? (
        <div className="mt-4 space-y-3">
          {rows.map((row) => (
            <div key={row.label} className="grid grid-cols-[minmax(96px,1.15fr)_minmax(64px,0.85fr)_76px] items-center gap-2 text-xs">
              <span className="min-w-0 truncate font-medium text-muted-foreground" title={row.label}>{row.label}</span>
              <div className="relative h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-900">
                <div className="absolute inset-0 bg-[repeating-linear-gradient(90deg,rgba(255,255,255,0.95)_0_7px,transparent_7px_11px)] dark:bg-[repeating-linear-gradient(90deg,rgba(255,255,255,0.26)_0_7px,transparent_7px_11px)]" />
                <div className="absolute inset-y-0 left-0 rounded-full bg-white shadow-sm ring-1 ring-slate-200 dark:bg-slate-50 dark:ring-white/40" style={{ width: `${row.value * 100}%` }} />
              </div>
              <span className="flex min-w-0 items-center justify-end gap-1.5">
                <span className="font-bold text-foreground">{row.value.toFixed(2)}</span>
                <span className={cn("shrink-0 rounded-full px-1.5 py-0.5 text-[11px] font-bold", TONE_BADGE[row.tone])}>{row.level}</span>
              </span>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState
          title="탐지 항목별 점수가 없습니다."
          description="AI 모듈별 점수가 제공되면 항목별 막대 점수로 표시됩니다."
        />
      )}

    </section>
  )
}

function ModelInfoSidebar({ data, threshold }: { data: EvidenceDetailData; threshold: number }) {
  const { evidenceInfo, analysisInfo } = data
  const metadata = evidenceInfo.technicalMetadata
  const frameCount = analysisInfo.frameScores?.length
  const modelNames = [
    ...new Set(
      analysisInfo.moduleResults
        .map((module) => module.modelName?.trim())
        .filter((name): name is string => Boolean(name))
    ),
  ]
  const modelVersions = [
    ...new Set(
      analysisInfo.moduleResults
        .map((module) => module.modelVersion?.trim())
        .filter((version): version is string => Boolean(version))
    ),
  ]
  const rows: Array<[string, string]> = [
    ["분석 모델", modelNames.length > 0 ? modelNames.join(" · ") : "-"],
    ["모델 버전", modelVersions.length > 0 ? modelVersions.join(" · ") : "-"],
    ["분석 ID", analysisInfo.analysisId?.trim() || "-"],
    ["입력 해상도", metadata.width && metadata.height ? `${metadata.width} × ${metadata.height}` : "-"],
    ["분석 프레임 수", frameCount ? String(frameCount) : "-"],
    ["영상 길이", metadata.durationSec != null ? formatDuration(metadata.durationSec) : "-"],
    ["프레임레이트", metadata.fps != null ? `${metadata.fps} fps` : "-"],
    ["코덱", metadata.codec?.trim() || "-"],
    ["판정 임계값", `${Math.round(threshold * 100)} / 100`],
  ]

  return (
    <aside className="flex h-full flex-col rounded-xl border border-border bg-card p-5 shadow-sm">
      <h3 className="text-base font-bold text-foreground">딥페이크 모델 분석 정보</h3>
      <dl className="mt-4 flex flex-1 flex-col justify-between">
        {rows.map(([label, value]) => (
          <div key={label} className="flex min-w-0 items-center justify-between gap-3 border-b border-border py-2 last:border-0">
            <dt className="shrink-0 text-xs font-medium text-muted-foreground">{label}</dt>
            <dd className="min-w-0 break-words text-right text-xs font-bold text-foreground">{value}</dd>
          </div>
        ))}
      </dl>
    </aside>
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

// ---- tone helpers (초록/주황/빨강 3색 + 중립) ----
type Tone = "red" | "amber" | "green" | "neutral"

const TONE_TEXT: Record<Tone, string> = {
  red: "text-red-500",
  amber: "text-amber-500",
  green: "text-emerald-600 dark:text-emerald-400",
  neutral: "text-foreground",
}

const TONE_BAR: Record<Tone, string> = {
  red: "bg-red-500",
  amber: "bg-amber-400",
  green: "bg-emerald-500",
  neutral: "bg-slate-400",
}

const TONE_BADGE: Record<Tone, string> = {
  red: "bg-red-50 text-red-600 dark:bg-red-950/30 dark:text-red-300",
  amber: "bg-amber-50 text-amber-600 dark:bg-amber-950/30 dark:text-amber-300",
  green: "bg-emerald-50 text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-300",
  neutral: "bg-muted text-muted-foreground",
}

function getVerdict(score: number | null, threshold: number): { label: string; tone: Tone } {
  if (score == null) return { label: "분석 근거 없음", tone: "neutral" }
  if (score >= threshold) return { label: "위험", tone: "red" }
  if (score >= 0.3) return { label: "주의", tone: "amber" }
  return { label: "정상", tone: "green" }
}

function toneByScore(score: number, threshold = DEFAULT_THRESHOLD): Tone {
  if (score >= threshold) return "red"
  if (score >= 0.3) return "amber"
  return "green"
}

function levelByScore(score: number): { level: string; tone: Tone } {
  if (score >= 0.6) return { level: "높음", tone: "red" }
  if (score >= 0.3) return { level: "보통", tone: "amber" }
  return { level: "낮음", tone: "green" }
}

function buildModelScoreCards(data: EvidenceDetailData, defaultThreshold: number): ModelScoreCard[] {
  const scores = data.analysisInfo.modelScores ?? []

  return MODEL_SCORE_ORDER.map((key) => {
    const score = findModelScore(scores, key)
    const display = MODEL_SCORE_DISPLAY[key]
    const normalizedScore = score ? normalizeProbability(score.score) : null
    const threshold = resolveModelScoreThreshold(key, data, defaultThreshold)

    return {
      key,
      title: display.title,
      role: display.role,
      shortRole: display.shortRole,
      score: normalizedScore,
      threshold,
      detected: score?.detected ?? (normalizedScore == null ? null : normalizedScore >= threshold),
      modelName: score?.modelName?.trim() || display.title,
      modelVersion: score?.modelVersion?.trim() || null,
    }
  })
}

function buildTimelineTabs(data: EvidenceDetailData, defaultThreshold: number): ModuleTimelineTab[] {
  const timelines = data.analysisInfo.moduleTimelines ?? []

  return (["cnn", "temporal", "optical"] as const).map((key) => {
    const timeline = timelines.find((item) => item.module === key)
    const modelScore = findModelScore(data.analysisInfo.modelScores ?? [], modelScoreKeyForTimeline(key))
    const display = TIMELINE_DISPLAY[key]
    const threshold = normalizeThreshold(timeline?.threshold, defaultThreshold)
    const videoScore =
      timeline?.videoScore != null
        ? normalizeProbability(timeline.videoScore)
        : modelScore?.score != null
          ? normalizeProbability(modelScore.score)
          : null
    const points = buildTimelinePoints(key, data, timeline)
    const segments = buildTimelineSegments(key, data, timeline)

    return {
      key,
      label: display.label,
      title: display.title,
      description: display.description,
      modelName: timeline?.modelName?.trim() || modelScore?.modelName?.trim() || display.label,
      modelVersion: timeline?.modelVersion?.trim() || modelScore?.modelVersion?.trim() || null,
      videoScore,
      threshold,
      detected: timeline?.detected ?? modelScore?.detected ?? (videoScore != null ? videoScore >= threshold : false),
      points,
      segments,
      emptyTitle: `${display.label} 타임라인 데이터가 없습니다.`,
    }
  })
}

function buildTimelinePoints(
  key: TimelineTabKey,
  data: EvidenceDetailData,
  timeline?: ModuleTimeline
): FrameScore[] {
  if (key === "cnn") {
    const risks = nonEmpty(timeline?.frameRisks) ? timeline?.frameRisks : data.analysisInfo.frameRisks
    const points = frameRisksToFrameScores(risks ?? [])
    return points.length > 0 ? points : data.analysisInfo.frameScores ?? []
  }

  if (key === "temporal") {
    const risks = nonEmpty(timeline?.clipRisks) ? timeline?.clipRisks : data.analysisInfo.clipRisks
    return clipRisksToFrameScores(risks ?? [])
  }

  const risks = nonEmpty(timeline?.pairRisks) ? timeline?.pairRisks : data.analysisInfo.pairRisks
  return pairRisksToFrameScores(risks ?? [])
}

function buildTimelineSegments(
  key: TimelineTabKey,
  data: EvidenceDetailData,
  timeline?: ModuleTimeline
): SuspiciousSegment[] {
  if (nonEmpty(timeline?.suspiciousSegments)) return timeline?.suspiciousSegments ?? []

  if (key === "cnn") return data.analysisInfo.suspiciousSegments ?? []
  if (key === "temporal") return data.analysisInfo.temporalSuspiciousSegments ?? []
  return data.analysisInfo.opticalSuspiciousSegments ?? []
}

function frameRisksToFrameScores(risks: FrameRisk[]): FrameScore[] {
  return risks.map((risk) => ({
    timeSec: risk.timestampSec,
    score: risk.riskScore,
  }))
}

function clipRisksToFrameScores(risks: ClipRisk[]): FrameScore[] {
  return risks.map((risk) => ({
    timeSec: Number(((risk.startTimeSec + risk.endTimeSec) / 2).toFixed(2)),
    timestamp: `${formatSeconds(risk.startTimeSec)}-${formatSeconds(risk.endTimeSec)}`,
    score: risk.riskScore,
  }))
}

function pairRisksToFrameScores(risks: PairRisk[]): FrameScore[] {
  return risks.map((risk) => ({
    timeSec: risk.timestampSec,
    score: risk.riskScore,
  }))
}

function findModelScore(scores: ModelScore[], moduleName: string) {
  const normalized = normalizeModelScoreModuleName(moduleName)
  return scores.find((score) => normalizeModelScoreModuleName(score.moduleName) === normalized)
}

function modelScoreKeyForTimeline(key: TimelineTabKey) {
  if (key === "cnn") return "deepfake_cnn"
  if (key === "temporal") return "deepfake_temporal"
  return "deepfake_optical"
}

function normalizeModelScoreModuleName(moduleName: string | null | undefined) {
  const normalized = moduleName?.trim().toLowerCase() ?? ""
  if (["late_fusion", "fusion", "late fusion"].includes(normalized)) return "deepfake"
  if (["cnn", "xception"].includes(normalized)) return "deepfake_cnn"
  if (["temporal", "timesformer"].includes(normalized)) return "deepfake_temporal"
  if (["optical", "gmflow"].includes(normalized)) return "deepfake_optical"
  return normalized
}

function normalizeThreshold(value: number | null | undefined, fallback: number) {
  if (value == null || !Number.isFinite(Number(value))) return fallback
  return normalizeProbability(Number(value))
}

function nonEmpty<T>(items: T[] | null | undefined): items is T[] {
  return Array.isArray(items) && items.length > 0
}

function cleanModelVersion(version: string) {
  const trimmed = version.trim()
  if (!trimmed) return "-"
  if (!trimmed.includes("/")) return trimmed
  const parts = trimmed.split("/").filter(Boolean)
  return parts[parts.length - 1] ?? trimmed
}

function formatSeconds(value: number) {
  const normalized = Math.max(0, Number.isFinite(value) ? value : 0)
  const minutes = Math.floor(normalized / 60)
  const seconds = Math.floor(normalized % 60)
  const tenth = Math.floor((normalized % 1) * 10)
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}.${tenth}`
}

function formatSegmentReason(reason: string) {
  const normalized = reason.trim()
  const knownReasons: Record<string, string> = {
    "High CNN frame-level fake probability cluster": "프레임 단위 얼굴 합성 의심 점수가 높은 구간입니다.",
    "High temporal clip-level fake probability cluster": "클립 단위 시간 흐름에서 불일치가 높은 구간입니다.",
    "High optical flow motion anomaly cluster": "연속 프레임 움직임 벡터가 불안정한 구간입니다.",
  }
  return knownReasons[normalized] ?? (normalized || "의심 구간으로 표시되었습니다.")
}

function buildRiskBars(frameScores: FrameScore[], threshold: number) {
  return densifyFrameScores(frameScores, 48)
    .filter((frame) => typeof frame.score === "number")
    .map((frame) => {
      const score = normalizeProbability(frame.score)
      const height = Math.min(96, Math.max(8, score * 100))
      const className = riskBarClassName(score, threshold)
      return { time: formatFrameTime(frame), height, className }
    })
}

function riskBarClassName(score: number, threshold = DEFAULT_THRESHOLD) {
  if (score >= threshold) return "bg-red-700 dark:bg-red-500"
  if (score >= 0.3) return "bg-amber-400"
  return "bg-slate-300 dark:bg-slate-600"
}

function densifyFrameScores(frameScores: FrameScore[], targetCount: number) {
  if (frameScores.length <= 1 || frameScores.length >= targetCount) return frameScores

  const result: FrameScore[] = []

  for (let index = 0; index < targetCount; index += 1) {
    const position = (index / Math.max(1, targetCount - 1)) * (frameScores.length - 1)
    const leftIndex = Math.floor(position)
    const rightIndex = Math.min(frameScores.length - 1, leftIndex + 1)
    const ratio = position - leftIndex
    const left = frameScores[leftIndex]
    const right = frameScores[rightIndex]
    const leftScore = normalizeProbability(left.score)
    const rightScore = normalizeProbability(right.score)
    const score = leftScore + (rightScore - leftScore) * ratio
    const leftTime = left.timeSec ?? leftIndex
    const rightTime = right.timeSec ?? rightIndex

    result.push({
      timeSec: Number((leftTime + (rightTime - leftTime) * ratio).toFixed(1)),
      score,
    })
  }

  return result
}

function buildScoreRows(modules: ModuleResult[]) {
  return modules
    .map((item) => ({ item, score: getModuleScore(item) }))
    .filter((row): row is { item: ModuleResult; score: number } => row.score != null)
    .map(({ item, score }) => {
      const value = normalizeProbability(score)
      return {
        label: getModuleLabel(item.moduleName),
        value,
        ...levelByScore(value),
      }
    })
}

function getPrimaryModelScore(modules: ModuleResult[]) {
  const primary =
    modules.find((module) => module.moduleName.toLowerCase().includes("deepfake") && getModuleScore(module) != null) ??
    modules.find((module) => getModuleScore(module) != null)
  const score = primary ? getModuleScore(primary) : null
  return score == null ? null : normalizeProbability(score)
}

function getFusionModelScore(scores: ModelScore[]) {
  const score = findModelScore(scores, "deepfake")
  return score == null ? null : normalizeProbability(score.score)
}

function getModuleScore(module: ModuleResult) {
  if (typeof module.deepfakeScore === "number") return module.deepfakeScore
  if (typeof module.score === "number") return module.score
  return null
}

function normalizePercent(value: number | null) {
  if (value == null) return null
  return Math.round(value <= 1 ? value * 100 : value)
}

function normalizeProbability(value: number) {
  if (value > 1) return Math.min(1, value / 100)
  return Math.max(0, value)
}

function formatNullable(value: number | null, formatter: (value: number) => string) {
  return value == null ? "-" : formatter(value)
}

function getPlayableVideoUrl(data: EvidenceDetailData) {
  const evidence = data.evidenceInfo
  const analysis = data.analysisInfo
  return (
    evidence.videoUrl ??
    evidence.streamUrl ??
    evidence.fileUrl ??
    evidence.previewUrl ??
    analysis.overlayVideoUrl ??
    evidence.overlayVideoUrl ??
    null
  )
}

function formatFrameTime(frame?: Pick<FrameScore, "timeSec" | "timestamp"> | Pick<RepresentativeFrame, "timeSec" | "timestamp">) {
  if (!frame) return "-"
  if (frame.timestamp) return frame.timestamp
  if (frame.timeSec == null) return "-"

  const minutes = Math.floor(frame.timeSec / 60)
  const seconds = Math.floor(frame.timeSec % 60)
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`
}

function getModuleLabel(moduleName: string) {
  const normalizedName = moduleName.toLowerCase()
  if (normalizedName.includes("lip") || normalizedName.includes("audio")) return "오디오·립싱크 불일치"
  if (normalizedName.includes("frame") || normalizedName.includes("temporal")) return "시간축 일관성 저하"
  if (normalizedName.includes("compress") || normalizedName.includes("artifact")) return "압축 아티팩트"
  if (normalizedName.includes("light") || normalizedName.includes("color")) return "조명·색상 불일치"
  if (normalizedName.includes("meta")) return "메타데이터 기반 이상"
  if (normalizedName.includes("face") || normalizedName.includes("deepfake")) return "얼굴 경계 불연속"
  return moduleName
}
