"use client"

import { useState, type RefObject } from "react"
import { FileVideo, Play } from "lucide-react"

import {
  buildTopRiskFrames,
  formatScoreOutOf100,
  normalizeResultValue,
} from "@/lib/api/analysis-result-ui"
import type { EvidenceDetailData, FrameScore, RepresentativeFrame } from "@/lib/api/evidence-detail"
import { formatDuration } from "@/lib/formatters"
import { cn } from "@/lib/utils"

import {
  buildForgeryRepresentativeFrames,
  forgeryHighRiskGalleryCopy,
  formatForgeryThresholdLabel,
  FORGERY_TEMPORAL_MODULE,
} from "../_lib/forgery-ui"
import {
  buildDeepfakeTimelineTabs,
  buildForgeryTimelineTabs,
  summarizeFrameScores,
  type DeepfakeTimelineTab,
  type ForgeryTimelineTab,
} from "../_lib/module-timelines"
import { FrameRiskChart } from "./frame-risk-chart"
import { VideoSeekThumbnail } from "./video-seek-thumbnail"

type ResultFrameAnalysisProps = {
  evidenceDetail: EvidenceDetailData
  detectionThreshold: number
  representativeFrames: RepresentativeFrame[]
  videoRef?: RefObject<HTMLVideoElement | null>
  onSeek: (seconds: number) => void
}

type AnalysisCategory = "deepfake" | "forgery"

export function ResultFrameAnalysis({
  evidenceDetail,
  detectionThreshold,
  representativeFrames,
  videoRef,
  onSeek,
}: ResultFrameAnalysisProps) {
  const [category, setCategory] = useState<AnalysisCategory>("deepfake")
  const deepfakeTabs = buildDeepfakeTimelineTabs(evidenceDetail, detectionThreshold)
  const forgeryTabs = buildForgeryTimelineTabs(evidenceDetail, detectionThreshold)
  const [deepfakeKey, setDeepfakeKey] = useState(deepfakeTabs[0]?.key ?? "cnn")
  const [forgeryKey, setForgeryKey] = useState(forgeryTabs[0]?.key ?? "")

  const activeDeepfakeTab = deepfakeTabs.find((tab) => tab.key === deepfakeKey) ?? deepfakeTabs[0]
  const activeForgeryTab = forgeryTabs.find((tab) => tab.key === forgeryKey) ?? forgeryTabs[0]
  const forgeryRepresentativeFrames = buildForgeryRepresentativeFrames(evidenceDetail, {
    moduleKey: activeForgeryTab?.key,
    maxFrames: 2,
  })
  const forgeryGalleryCopy = forgeryHighRiskGalleryCopy(activeForgeryTab?.key)

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-lg font-bold text-slate-950 dark:text-foreground">프레임 분석</h3>
          <p className="mt-1 text-sm font-semibold text-slate-500">
            딥페이크 모델(Xception·TimeSformer·GMFlow)과 위변조 모델을 분리해 확인합니다.
          </p>
        </div>
      </div>

      <div className="mt-4 grid grid-cols-2 rounded-lg bg-slate-100 p-1 text-sm font-bold dark:bg-secondary">
        {(
          [
            ["deepfake", "딥페이크"],
            ["forgery", "위변조"],
          ] as const
        ).map(([value, label]) => (
          <button
            key={value}
            type="button"
            onClick={() => setCategory(value)}
            className={cn(
              "rounded-md px-3 py-2 transition-colors",
              category === value
                ? "bg-white text-slate-950 shadow-sm dark:bg-card dark:text-foreground"
                : "text-slate-500 hover:text-slate-700 dark:text-muted-foreground"
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {category === "deepfake" ? (
        <DeepfakeFrameAnalysis
          tabs={deepfakeTabs}
          activeTab={activeDeepfakeTab}
          activeKey={activeDeepfakeTab?.key ?? "cnn"}
          onSelectTab={setDeepfakeKey}
          detectionThreshold={detectionThreshold}
          representativeFrames={representativeFrames}
          evidenceDetail={evidenceDetail}
          onSeek={onSeek}
        />
      ) : (
        <ForgeryFrameAnalysis
          tabs={forgeryTabs}
          activeTab={activeForgeryTab}
          activeKey={activeForgeryTab?.key ?? ""}
          onSelectTab={setForgeryKey}
          representativeFrames={forgeryRepresentativeFrames}
          galleryCopy={forgeryGalleryCopy}
          videoRef={videoRef}
          onSeek={onSeek}
        />
      )}
    </section>
  )
}

function DeepfakeFrameAnalysis({
  tabs,
  activeTab,
  activeKey,
  onSelectTab,
  detectionThreshold,
  representativeFrames,
  evidenceDetail,
  onSeek,
}: {
  tabs: DeepfakeTimelineTab[]
  activeTab?: DeepfakeTimelineTab
  activeKey: string
  onSelectTab: (key: DeepfakeTimelineTab["key"]) => void
  detectionThreshold: number
  representativeFrames: RepresentativeFrame[]
  evidenceDetail: EvidenceDetailData
  onSeek: (seconds: number) => void
}) {
  const scores = activeTab?.points ?? []
  const summary = summarizeFrameScores(scores, detectionThreshold)
  const topRiskFrames = buildTopRiskFrames(evidenceDetail, scores)

  return (
    <div className="mt-5 space-y-4">
      <div className="grid grid-cols-3 rounded-lg bg-slate-50 p-1 text-xs font-bold dark:bg-background">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => onSelectTab(tab.key)}
            className={cn(
              "rounded-md px-2 py-2 transition-colors",
              activeKey === tab.key
                ? "bg-white text-slate-950 shadow-sm dark:bg-card dark:text-foreground"
                : "text-slate-500 hover:text-slate-700 dark:text-muted-foreground"
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab ? (
        <>
          <p className="text-xs font-semibold text-slate-500">{activeTab.description}</p>

          {scores.length > 0 ? (
            <>
              <MetricGrid
                summary={summary}
                scores={scores}
                detectionThreshold={detectionThreshold}
                unitLabel={activeTab.unitLabel}
              />

              <div className="rounded-xl border border-slate-100 bg-white p-5 dark:border-border dark:bg-card">
                <FrameRiskChart
                  scores={scores}
                  threshold={activeTab.threshold}
                  title={activeTab.title}
                  emptyMessage={`${activeTab.label} 타임라인 데이터가 없습니다.`}
                />
              </div>

              {topRiskFrames.length > 0 ? (
                <TopRiskFrameList
                  frames={topRiskFrames}
                  representativeFrames={representativeFrames}
                  onSeek={onSeek}
                />
              ) : null}
            </>
          ) : (
            <EmptyTimelineMessage
              title={`${activeTab.label} 타임라인 데이터가 없습니다.`}
              description="백엔드가 moduleTimelines 또는 frameRisks/clipRisks/pairRisks를 제공하면 이 영역에 차트가 표시됩니다."
            />
          )}
        </>
      ) : (
        <EmptyTimelineMessage
          title="딥페이크 모델 타임라인이 없습니다."
          description="분석이 완료된 뒤 모듈별 타임라인이 제공되면 표시됩니다."
        />
      )}
    </div>
  )
}

function ForgeryFrameAnalysis({
  tabs,
  activeTab,
  activeKey,
  onSelectTab,
  representativeFrames,
  galleryCopy,
  videoRef,
  onSeek,
}: {
  tabs: ForgeryTimelineTab[]
  activeTab?: ForgeryTimelineTab
  activeKey: string
  onSelectTab: (key: string) => void
  representativeFrames: RepresentativeFrame[]
  galleryCopy: { title: string; description: string; empty: string }
  videoRef?: RefObject<HTMLVideoElement | null>
  onSeek: (seconds: number) => void
}) {
  if (tabs.length === 0) {
    return (
      <EmptyTimelineMessage
        title="위변조 프레임 분석 데이터가 없습니다."
        description="GPU worker가 forgery_spatial(TruFor) 또는 forgery_temporal(TimeSformer) moduleTimelines를 내면 모델별로 표시됩니다."
      />
    )
  }

  const moduleThreshold = activeTab?.threshold ?? 0.515
  const scores = activeTab?.points ?? []
  const summary = summarizeFrameScores(scores, moduleThreshold)
  const isTemporal = activeKey === FORGERY_TEMPORAL_MODULE

  return (
    <div className="mt-5 space-y-4">
      <div
        className={cn(
          "grid gap-1 rounded-lg bg-slate-50 p-1 text-xs font-bold dark:bg-background",
          tabs.length >= 3 ? "grid-cols-3" : tabs.length === 2 ? "grid-cols-2" : "grid-cols-1"
        )}
      >
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => onSelectTab(tab.key)}
            className={cn(
              "rounded-md px-2 py-2 transition-colors",
              activeKey === tab.key
                ? "bg-white text-slate-950 shadow-sm dark:bg-card dark:text-foreground"
                : "text-slate-500 hover:text-slate-700 dark:text-muted-foreground"
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab ? (
        <>
          <p className="text-xs font-semibold text-slate-500">{activeTab.description}</p>

          <div className="grid gap-3 sm:grid-cols-2">
            <MetricCard
              label="모듈 점수"
              value={formatScoreOutOf100(activeTab.videoScore)}
              sub={activeTab.detected ? "기준 초과" : formatForgeryThresholdLabel(moduleThreshold)}
              tone={activeTab.videoScore >= moduleThreshold ? "danger" : "neutral"}
            />
            <MetricCard
              label="의심 구간"
              value={`${activeTab.segments.length}개`}
              sub={activeTab.segments.length > 0 ? "구간별 maxRiskScore 제공" : "구간 데이터 없음"}
            />
          </div>

          {scores.length > 0 ? (
            <>
              <MetricGrid
                summary={summary}
                scores={scores}
                detectionThreshold={moduleThreshold}
                unitLabel={isTemporal ? "클립" : "프레임"}
              />
              <div className="rounded-xl border border-slate-100 bg-white p-5 dark:border-border dark:bg-card">
                <FrameRiskChart
                  scores={scores}
                  threshold={moduleThreshold}
                  title={`${activeTab.label} ${isTemporal ? "클립별" : "프레임별"} 위험도`}
                />
              </div>
              <SegmentList segments={activeTab.segments} onSeek={onSeek} />
            </>
          ) : (
            <EmptyTimelineMessage
              title={`${activeTab.label} 프레임 데이터가 없습니다.`}
              description="해당 모듈이 frameRisks 또는 clipRisks를 보고하면 시간축 차트가 표시됩니다."
            />
          )}

          <div className="rounded-xl border border-slate-100 bg-white p-5 dark:border-border dark:bg-card">
            <h4 className="text-sm font-bold text-slate-950 dark:text-foreground">{galleryCopy.title}</h4>
            <p className="mt-0.5 text-xs font-semibold text-slate-500">{galleryCopy.description}</p>
            {representativeFrames.length > 0 ? (
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {representativeFrames.slice(0, 2).map((frame, index) => (
                  <RepresentativeThumb
                    key={`${activeKey}-${frame.timestamp ?? frame.timeSec ?? index}`}
                    frame={frame}
                    videoRef={videoRef}
                  />
                ))}
              </div>
            ) : (
              <p className="mt-3 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-6 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
                {galleryCopy.empty}
              </p>
            )}
          </div>
        </>
      ) : null}
    </div>
  )
}

function MetricGrid({
  summary,
  scores,
  detectionThreshold,
  unitLabel,
}: {
  summary: ReturnType<typeof summarizeFrameScores>
  scores: FrameScore[]
  detectionThreshold: number
  unitLabel: string
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <MetricCard
        label="최고 위험"
        value={summary.peakValue}
        sub={summary.peakLabel}
        tone={
          summary.peak != null && normalizeResultValue(summary.peak.score) >= detectionThreshold
            ? "danger"
            : "neutral"
        }
      />
      <MetricCard label="평균 위험도" value={summary.avgValue} sub={`전체 ${unitLabel} 평균`} />
      <MetricCard
        label="임계값 초과"
        value={`${summary.highRiskCount} / ${scores.length}`}
        sub={`위험 점수 ${Math.round(detectionThreshold * 100)}점 이상`}
        tone={summary.highRiskCount > 0 ? "danger" : "neutral"}
      />
      <MetricCard label="표본 수" value={`${scores.length}${unitLabel}`} sub="분석 단위 기준" />
    </div>
  )
}

function MetricCard({
  label,
  value,
  sub,
  tone = "neutral",
}: {
  label: string
  value: string
  sub?: string
  tone?: "danger" | "neutral"
}) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white px-4 py-3.5 dark:border-border dark:bg-card">
      <p className="text-xs font-semibold text-slate-400">{label}</p>
      <p
        className={cn(
          "mt-1 truncate text-lg font-bold tracking-tight text-slate-950 dark:text-foreground",
          tone === "danger" && "text-red-700"
        )}
      >
        {value}
      </p>
      {sub ? <p className="mt-0.5 text-xs font-medium text-slate-500">{sub}</p> : null}
    </div>
  )
}

function TopRiskFrameList({
  frames,
  representativeFrames,
  onSeek,
}: {
  frames: ReturnType<typeof buildTopRiskFrames>
  representativeFrames: RepresentativeFrame[]
  onSeek: (seconds: number) => void
}) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white p-5 dark:border-border dark:bg-card">
      <div>
        <h4 className="text-sm font-bold text-slate-950 dark:text-foreground">상위 위험 프레임</h4>
        <p className="mt-0.5 text-xs font-semibold text-slate-500">행을 선택하면 영상이 해당 지점으로 이동합니다.</p>
      </div>
      <div className="mt-2 divide-y divide-slate-100 dark:divide-border">
        {frames.map((frame, index) => {
          const representative = representativeFrames.find(
            (item) =>
              (item.timeSec != null && Math.abs(item.timeSec - frame.seconds) < 0.35) ||
              item.timestamp === frame.time
          )
          return (
            <button
              key={frame.time}
              type="button"
              onClick={() => onSeek(frame.seconds)}
              className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left transition-colors hover:bg-slate-50 dark:hover:bg-secondary/40"
            >
              <span className="w-4 shrink-0 text-xs font-bold text-slate-400">{index + 1}</span>
              <span className="h-11 w-[74px] shrink-0 overflow-hidden rounded-md bg-slate-100 dark:bg-secondary">
                {representative?.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={representative.imageUrl}
                    alt={`${frame.time} 프레임 미리보기`}
                    className="size-full object-cover"
                  />
                ) : (
                  <span className="flex size-full items-center justify-center">
                    <FileVideo className="size-4 text-slate-300" aria-hidden="true" />
                  </span>
                )}
              </span>
              <span className="font-mono text-sm font-semibold text-slate-950 dark:text-foreground">{frame.time}</span>
              <span className="shrink-0 text-sm font-bold text-red-700">{frame.score} / 100</span>
              <span className="truncate text-sm font-semibold text-slate-600 dark:text-muted-foreground">
                {frame.signal}
              </span>
              <Play className="ml-auto size-3.5 shrink-0 text-teal-700 dark:text-teal-300" aria-hidden="true" />
            </button>
          )
        })}
      </div>
    </div>
  )
}

function SegmentList({
  segments,
  onSeek,
}: {
  segments: ForgeryTimelineTab["segments"]
  onSeek: (seconds: number) => void
}) {
  if (segments.length === 0) return null

  return (
    <div className="rounded-xl border border-slate-100 bg-white p-5 dark:border-border dark:bg-card">
      <h4 className="text-sm font-bold text-slate-950 dark:text-foreground">의심 구간 목록</h4>
      <div className="mt-3 space-y-2">
        {segments.map((segment, index) => (
          <button
            key={`${segment.startTime}-${segment.endTime}-${index}`}
            type="button"
            onClick={() => onSeek(segment.startTime)}
            className="flex w-full items-center justify-between gap-3 rounded-lg border border-slate-100 px-3 py-2 text-left transition-colors hover:bg-slate-50 dark:border-border dark:hover:bg-secondary/40"
          >
            <span className="font-mono text-sm font-semibold text-slate-950 dark:text-foreground">
              {formatDuration(segment.startTime)} ~ {formatDuration(segment.endTime)}
            </span>
            <span className="text-sm font-bold text-red-700">
              {Math.round(normalizeResultValue(segment.maxRiskScore) * 100)} / 100
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}

function RepresentativeThumb({
  frame,
  videoRef,
}: {
  frame: RepresentativeFrame
  videoRef?: RefObject<HTMLVideoElement | null>
}) {
  const timeSec = frame.timeSec ?? 0
  return (
    <article className="rounded-lg border border-slate-100 bg-slate-50 p-3 dark:border-border dark:bg-background">
      <p className="text-xs font-semibold text-slate-500">
        {frame.timeSec != null ? formatDuration(frame.timeSec) : frame.timestamp ?? "-"}
      </p>
      <div className="mt-2 aspect-video overflow-hidden rounded-md bg-slate-200 dark:bg-secondary">
        {videoRef ? (
          <VideoSeekThumbnail
            videoRef={videoRef}
            timeSec={timeSec}
            imageUrl={frame.imageUrl}
            heatmapImageUrl={frame.heatmapImageUrl}
            label="고위험 시점"
          />
        ) : frame.imageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={frame.imageUrl} alt="대표 프레임" className="size-full object-cover" />
        ) : (
          <div className="flex size-full items-center justify-center">
            <FileVideo className="size-5 text-slate-400" aria-hidden="true" />
          </div>
        )}
      </div>
    </article>
  )
}

function EmptyTimelineMessage({ title, description }: { title: string; description: string }) {
  return (
    <p className="mt-5 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
      {title}
      <br />
      <span className="mt-2 inline-block text-xs font-medium leading-5 text-slate-400">{description}</span>
    </p>
  )
}
