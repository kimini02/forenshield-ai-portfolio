"use client"

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode, type RefObject } from "react"
import { FileVideo, Loader2 } from "lucide-react"

import {
  ProtectedEvidencePlayer,
  type ProtectedSecurityEvent,
} from "@/components/protected-evidence-player"
import type { EvidenceDetailData } from "@/lib/api/evidence-detail"
import {
  fetchOverlayJobStatus,
  requestOverlayGeneration,
  type OverlayJobStatusResponse,
} from "@/lib/evidence-api"
import type { HlsPlayback } from "@/lib/hls-playback"
import { cn } from "@/lib/utils"

import {
  buildModelOverlayOptions,
  findOverlayOption,
  getDefaultOverlaySelection,
  isDeepfakeOverlayBlocked,
  overlayModuleApiPath,
  resolveDeepfakeOverlayAdvisory,
  type ModelOverlayOption,
  type OverlayCategory,
  type ResultMediaView,
} from "../_lib/model-overlays"
import { ModelOverlayLayer } from "./model-overlay-layer"

type ResultEvidenceMediaProps = {
  evidenceDetail: EvidenceDetailData
  selectedEvidenceId: number | null
  hlsPlayback: HlsPlayback | null
  videoRef: RefObject<HTMLVideoElement | null>
  onSecurityEvent: (event: ProtectedSecurityEvent) => void
  onSeek?: (seconds: number) => void
  onMediaContextChange?: (context: string) => void
  onOverlayReady?: () => void
  renderHeatStrip: (props: {
    scores: import("@/lib/api/evidence-detail").FrameScore[]
    caption: string
    onSeek?: (seconds: number) => void
  }) => ReactNode
  renderWatermark: ReactNode
}

type OverlayJobUiState = {
  jobId: number
  module: string
  status: string
  progress: number
  errorMessage?: string | null
}

export function ResultEvidenceMedia({
  evidenceDetail,
  selectedEvidenceId,
  hlsPlayback,
  videoRef,
  onSecurityEvent,
  onSeek,
  onMediaContextChange,
  onOverlayReady,
  renderHeatStrip,
  renderWatermark,
}: ResultEvidenceMediaProps) {
  const overlayOptions = useMemo(() => buildModelOverlayOptions(evidenceDetail), [evidenceDetail])
  const defaultSelection = useMemo(() => getDefaultOverlaySelection(overlayOptions), [overlayOptions])
  const deepfakeOverlayBlocked = isDeepfakeOverlayBlocked(evidenceDetail)
  const deepfakeAdvisoryMessage = resolveDeepfakeOverlayAdvisory(evidenceDetail.analysisInfo.errorCode)

  const [mediaView, setMediaView] = useState<ResultMediaView>("original")
  const [overlayCategory, setOverlayCategory] = useState<OverlayCategory>(defaultSelection.category)
  const [selectedOverlayId, setSelectedOverlayId] = useState(defaultSelection.overlayId)
  const [jobByModule, setJobByModule] = useState<Record<string, OverlayJobUiState>>({})
  const [generateError, setGenerateError] = useState<string | null>(null)
  const [isRequesting, setIsRequesting] = useState(false)
  const autoRequestKeyRef = useRef<string | null>(null)

  useEffect(() => {
    setMediaView("original")
    setJobByModule({})
    setGenerateError(null)
    setIsRequesting(false)
    autoRequestKeyRef.current = null
    setOverlayCategory(defaultSelection.category)
    setSelectedOverlayId(defaultSelection.overlayId)
  }, [selectedEvidenceId, defaultSelection.category, defaultSelection.overlayId])

  const activeOverlay =
    findOverlayOption(overlayOptions, selectedOverlayId) ??
    overlayOptions.find((item) => item.category === overlayCategory) ??
    overlayOptions[0] ??
    null

  const activeModulePath = activeOverlay ? overlayModuleApiPath(activeOverlay.id) : null
  const activeJob = activeModulePath ? jobByModule[activeModulePath] : undefined
  const isGenerating =
    Boolean(activeJob) &&
    (activeJob?.status === "QUEUED" || activeJob?.status === "PROCESSING")
  const isJobFailed = activeJob?.status === "FAILED"
  const isFailed = isJobFailed || Boolean(generateError)

  useEffect(() => {
    if (!onMediaContextChange) return
    if (mediaView === "original") {
      onMediaContextChange("original")
      return
    }
    onMediaContextChange(`overlay:${activeOverlay?.id ?? selectedOverlayId}`)
  }, [mediaView, activeOverlay?.id, selectedOverlayId, onMediaContextChange])

  // Clear prior module request errors when the selected overlay module changes.
  useEffect(() => {
    setGenerateError(null)
    setIsRequesting(false)
  }, [activeModulePath])

  const applyJobStatus = useCallback((status: OverlayJobStatusResponse) => {
    setJobByModule((current) => ({
      ...current,
      [status.module]: {
        jobId: status.overlayJobId,
        module: status.module,
        status: status.status,
        progress: status.progressPercent ?? 0,
        errorMessage: status.errorMessage,
      },
    }))
  }, [])

  const clearModuleAttempt = useCallback((modulePath: string | null) => {
    if (!modulePath) return
    setGenerateError(null)
    setIsRequesting(false)
    autoRequestKeyRef.current = null
    setJobByModule((current) => {
      if (!(modulePath in current)) return current
      const next = { ...current }
      delete next[modulePath]
      return next
    })
  }, [])

  const handleGenerateOverlay = useCallback(async () => {
    if (!selectedEvidenceId || !activeModulePath || !activeOverlay) return
    if (activeOverlay.overlayVideoUrl) return
    if (activeOverlay.category === "deepfake" && deepfakeOverlayBlocked) return

    const modulePath = activeModulePath
    const requestKey = `${selectedEvidenceId}:${modulePath}`
    if (autoRequestKeyRef.current === requestKey) return

    setGenerateError(null)
    setIsRequesting(true)
    autoRequestKeyRef.current = requestKey
    try {
      const status = await requestOverlayGeneration(selectedEvidenceId, modulePath)
      applyJobStatus(status)
      if (status.status === "COMPLETED") {
        onOverlayReady?.()
      }
    } catch (error) {
      if (autoRequestKeyRef.current === requestKey) {
        autoRequestKeyRef.current = null
      }
      setGenerateError(error instanceof Error ? error.message : "오버레이 생성 요청에 실패했습니다.")
    } finally {
      if (autoRequestKeyRef.current === requestKey || autoRequestKeyRef.current == null) {
        setIsRequesting(false)
      }
    }
  }, [
    activeModulePath,
    activeOverlay,
    applyJobStatus,
    deepfakeOverlayBlocked,
    onOverlayReady,
    selectedEvidenceId,
  ])

  function enterOverlayView() {
    // Re-entering overlay tab clears a prior failure so generation runs again.
    if (isFailed) clearModuleAttempt(activeModulePath)
    setMediaView("overlay")
  }

  function selectOverlayOption(overlayId: string) {
    const option = findOverlayOption(overlayOptions, overlayId)
    const modulePath = option ? overlayModuleApiPath(option.id) : null
    const previousJob = modulePath ? jobByModule[modulePath] : undefined
    if (previousJob?.status === "FAILED" || (modulePath === activeModulePath && generateError)) {
      clearModuleAttempt(modulePath)
    }
    setSelectedOverlayId(overlayId)
  }

  useEffect(() => {
    const evidenceId = selectedEvidenceId
    if (!evidenceId) return

    const activeJobs = Object.values(jobByModule).filter(
      (job) => job.status === "QUEUED" || job.status === "PROCESSING"
    )
    if (activeJobs.length === 0) return

    let cancelled = false
    const poll = async () => {
      for (const job of activeJobs) {
        try {
          const status = await fetchOverlayJobStatus(evidenceId, job.jobId)
          if (cancelled) return
          applyJobStatus(status)
          if (status.status === "COMPLETED") {
            onOverlayReady?.()
          }
        } catch {
          // keep previous progress; next tick retries
        }
      }
    }

    void poll()
    const timer = window.setInterval(() => {
      if (document.hidden) return
      void poll()
    }, 1500)

    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [applyJobStatus, jobByModule, onOverlayReady, selectedEvidenceId])

  const canLivePreview = Boolean(
    activeOverlay &&
      (activeOverlay.clipWindows.length > 0 ||
        activeOverlay.spatialMarkers.length > 0 ||
        (activeOverlay.id === "deepfake:cnn" && activeOverlay.timelineScores.length > 0))
  )

  // Overlay tab / model selection: request baked MP4 when missing.
  // Live CSS preview can show immediately when timeline risks exist.
  useEffect(() => {
    if (mediaView !== "overlay") return
    if (!selectedEvidenceId || !activeModulePath || !activeOverlay) return
    if (activeOverlay.overlayVideoUrl) return
    if (activeOverlay.category === "deepfake" && deepfakeOverlayBlocked) return
    if (isRequesting || isGenerating) return
    if (activeJob?.status === "FAILED") return
    if (activeJob?.status === "COMPLETED") return
    if (generateError) return

    void handleGenerateOverlay()
  }, [
    activeJob?.status,
    activeModulePath,
    activeOverlay,
    deepfakeOverlayBlocked,
    generateError,
    handleGenerateOverlay,
    isGenerating,
    isRequesting,
    mediaView,
    selectedEvidenceId,
  ])

  const categoryOptions = overlayOptions.filter((item) => item.category === overlayCategory)
  const useOverlaySrc = mediaView === "overlay"
  const activeOverlayUrl = useOverlaySrc ? activeOverlay?.overlayVideoUrl ?? null : null
  const useOverlayMp4 = useOverlaySrc && Boolean(activeOverlayUrl)
  const hasHlsOriginal =
    hlsPlayback?.hlsStatus === "READY" &&
    Boolean(hlsPlayback.streamToken) &&
    Boolean(hlsPlayback.manifestPath)
  // Until baked MP4 exists, keep playing the original HLS (including while generating).
  const showResultPlayer = useOverlayMp4 || hasHlsOriginal || Boolean(hlsPlayback)
  const playerSurfaceKey = useOverlayMp4
    ? `overlay-${activeOverlay?.id ?? "none"}-${activeOverlayUrl}`
    : `hls-${hlsPlayback?.streamToken ?? hlsPlayback?.hlsStatus ?? "pending"}`

  const heatScores = useOverlaySrc
    ? activeOverlay?.timelineScores ?? []
    : overlayOptions.find((item) => item.id === "deepfake:cnn")?.timelineScores ?? []

  const heatCaption = useOverlaySrc
    ? activeOverlay?.timelineCaption ?? "타임라인 위험도"
    : "Xception 타임라인 위험도"

  const showDeepfakeAdvisory =
    useOverlaySrc && activeOverlay?.category === "deepfake" && Boolean(deepfakeAdvisoryMessage)

  const showProgressModal =
    useOverlaySrc &&
    !activeOverlayUrl &&
    !canLivePreview &&
    !showDeepfakeAdvisory &&
    (isRequesting || isGenerating || isFailed)

  const progressPercent = Math.max(0, Math.min(100, activeJob?.progress ?? (isRequesting ? 2 : 0)))
  const failureMessage =
    generateError ||
    activeJob?.errorMessage ||
    "오버레이 생성에 실패했습니다. 원본으로 돌아간 뒤 다시 오버레이를 눌러 주세요."

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-none lg:sticky lg:top-4 lg:self-start dark:border-border dark:bg-card">
      <div className="mb-3 space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-base font-bold text-slate-950 dark:text-foreground">증거 영상</h2>
            <p className="mt-0.5 text-xs font-semibold text-slate-500">
              원본과 모델별 오버레이를 같은 위치에서 비교합니다.
            </p>
          </div>
          <div className="flex rounded-full bg-slate-950/80 p-1 backdrop-blur-sm">
            {(
              [
                ["original", "원본"],
                ["overlay", "오버레이"],
              ] as const
            ).map(([mode, label]) => (
              <button
                key={mode}
                type="button"
                className={cn(
                  "rounded-full px-3 py-1 text-xs font-bold transition-colors",
                  mediaView === mode ? "bg-teal-500 text-white" : "text-white/80 hover:text-white"
                )}
                onClick={() => {
                  if (mode === "overlay") enterOverlayView()
                  else setMediaView("original")
                }}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        {mediaView === "overlay" ? (
          <ModelOverlayPicker
            overlayCategory={overlayCategory}
            selectedOverlayId={activeOverlay?.id ?? selectedOverlayId}
            options={overlayOptions}
            categoryOptions={categoryOptions}
            deepfakeOverlayBlocked={deepfakeOverlayBlocked}
            hasGeneratedUrl={(id) => Boolean(findOverlayOption(overlayOptions, id)?.overlayVideoUrl)}
            onCategoryChange={(category) => {
              setOverlayCategory(category)
              const firstInCategory = overlayOptions.find((item) => item.category === category)
              if (firstInCategory) selectOverlayOption(firstInCategory.id)
            }}
            onSelectOverlay={selectOverlayOption}
          />
        ) : null}
      </div>

      <div className="relative aspect-video overflow-hidden rounded-lg bg-slate-950">
        {showResultPlayer ? (
          <ProtectedEvidencePlayer
            key={`result-player-${selectedEvidenceId ?? "none"}-${playerSurfaceKey}`}
            src={useOverlayMp4 ? activeOverlayUrl : null}
            playback={useOverlayMp4 ? null : hlsPlayback}
            fallbackOpenUrl={useOverlayMp4 ? activeOverlayUrl : null}
            videoRef={videoRef}
            objectFit="cover"
            onSecurityEvent={onSecurityEvent}
          >
            {!useOverlayMp4 ? renderWatermark : null}
            {/* Live CSS preview only — baked MP4 already has GPU labels. */}
            {useOverlaySrc && activeOverlay && !useOverlayMp4 ? (
              <div className="absolute left-4 top-4 z-20 max-w-[70%] space-y-1">
                <div className="rounded-md bg-black/55 px-2.5 py-1 text-xs font-bold text-white">
                  {activeOverlay.label} 오버레이
                </div>
                <div className="rounded-md bg-black/45 px-2.5 py-0.5 text-[10px] font-semibold text-white/85">
                  {activeOverlay.overlayBadge}
                </div>
              </div>
            ) : null}
          </ProtectedEvidencePlayer>
        ) : (
          <div className="absolute inset-0 flex flex-col items-center justify-center text-sm font-bold text-white/60">
            <FileVideo className="mb-3 size-8" aria-hidden="true" />
            미리보기 가능한 영상이 없습니다.
          </div>
        )}

        {useOverlaySrc && !useOverlayMp4 && activeOverlay && canLivePreview ? (
          <ModelOverlayLayer option={activeOverlay} videoRef={videoRef} />
        ) : null}

        {showProgressModal ? (
          <div className="absolute inset-0 z-40 flex items-center justify-center bg-black/55 p-4 backdrop-blur-[2px]">
            <div
              role="status"
              aria-live="polite"
              className="w-full max-w-[280px] rounded-xl border border-white/15 bg-slate-950/90 px-4 py-4 shadow-xl"
            >
              {isFailed ? (
                <div className="space-y-3 text-center">
                  <p className="text-sm font-bold text-white">오버레이 생성 실패</p>
                  <p className="text-xs font-semibold leading-5 text-white/75">{failureMessage}</p>
                  <button
                    type="button"
                    className="rounded-md bg-white/10 px-3 py-1.5 text-xs font-bold text-white transition-colors hover:bg-white/20"
                    onClick={() => setMediaView("original")}
                  >
                    원본으로 돌아가기
                  </button>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex items-center gap-2 text-sm font-bold text-white">
                    <Loader2 className="size-4 shrink-0 animate-spin text-teal-300" aria-hidden="true" />
                    <span>{activeOverlay?.label ?? "모델"} 오버레이 생성 중</span>
                  </div>
                  <p className="text-[11px] font-semibold leading-4 text-white/70">
                    완료되면 오버레이 영상으로 전환됩니다.
                  </p>
                  <div className="flex items-center justify-between text-[11px] font-semibold text-white/85">
                    <span>진행률</span>
                    <span>{progressPercent}%</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-white/15">
                    <div
                      className="h-full rounded-full bg-teal-400 transition-[width] duration-300"
                      style={{ width: `${Math.max(4, progressPercent)}%` }}
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
        ) : null}
      </div>

      {showDeepfakeAdvisory ? (
        <p className="mt-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold leading-5 text-slate-700 dark:border-border dark:bg-background dark:text-muted-foreground">
          {deepfakeAdvisoryMessage}
        </p>
      ) : useOverlaySrc && activeOverlay && !activeOverlay.ready && !activeOverlayUrl && !showProgressModal ? (
        <p className="mt-2 rounded-lg border border-dashed border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold leading-5 text-amber-800 dark:border-amber-900/40 dark:bg-amber-950/20 dark:text-amber-200">
          {activeOverlay.pendingMessage}
        </p>
      ) : useOverlaySrc && canLivePreview ? (
        <p className="mt-2 rounded-lg border border-teal-200 bg-teal-50 px-3 py-2 text-xs font-semibold leading-5 text-teal-800 dark:border-teal-900/40 dark:bg-teal-950/20 dark:text-teal-200">
          타임라인 점수 기반 라이브 오버레이를 표시 중입니다.
          {activeModulePath ? " 오버레이 생성이 완료되면 자동 전환됩니다." : ""}
        </p>
      ) : null}

      {heatScores.length > 0 ? (
        renderHeatStrip({
          scores: heatScores,
          caption: heatCaption,
          onSeek,
        })
      ) : (
        <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs font-semibold leading-5 text-slate-500 dark:bg-background">
          위험 신호가 높은 구간은 프레임 분석 탭에서 모델별로 확인할 수 있습니다.
        </p>
      )}

      <div className="mt-4 border-t border-slate-100 pt-3 dark:border-border">
        <p className="text-[11px] font-bold text-slate-400">분석 유의사항</p>
        <ul className="mt-1.5 space-y-1 text-xs font-medium leading-5 text-slate-500">
          <li>본 결과는 AI 기반 조작 의심 신호 분석이며, 조작 여부를 확정하지 않습니다.</li>
          <li>오버레이는 요청 시에만 생성되며, 생성 전에는 원본 영상만 표시됩니다.</li>
          <li>최종 판단은 원본 자료, 사건 맥락, 전문가 검토 결과와 함께 이루어져야 합니다.</li>
        </ul>
      </div>
    </section>
  )
}

function ModelOverlayPicker({
  overlayCategory,
  selectedOverlayId,
  options,
  categoryOptions,
  deepfakeOverlayBlocked,
  hasGeneratedUrl,
  onCategoryChange,
  onSelectOverlay,
}: {
  overlayCategory: OverlayCategory
  selectedOverlayId: string
  options: ModelOverlayOption[]
  categoryOptions: ModelOverlayOption[]
  deepfakeOverlayBlocked: boolean
  hasGeneratedUrl: (overlayId: string) => boolean
  onCategoryChange: (category: OverlayCategory) => void
  onSelectOverlay: (overlayId: string) => void
}) {
  const deepfakeCount = options.filter((item) => item.category === "deepfake").length
  const forgeryCount = options.filter((item) => item.category === "forgery").length

  return (
    <div className="space-y-2 rounded-lg border border-slate-100 bg-slate-50/80 p-3 dark:border-border dark:bg-background/60">
      <div className="grid grid-cols-2 gap-1 rounded-md bg-white p-1 text-xs font-bold shadow-sm dark:bg-card">
        {(
          [
            ["deepfake", `딥페이크 (${deepfakeCount})`],
            ["forgery", `위변조 (${forgeryCount})`],
          ] as const
        ).map(([category, label]) => (
          <button
            key={category}
            type="button"
            onClick={() => onCategoryChange(category)}
            className={cn(
              "rounded px-2 py-1.5 transition-colors",
              overlayCategory === category
                ? "bg-teal-500 text-white"
                : "text-slate-500 hover:bg-slate-50 dark:text-muted-foreground dark:hover:bg-secondary/40"
            )}
          >
            {label}
          </button>
        ))}
      </div>

      <div
        className={cn(
          "grid gap-1",
          categoryOptions.length >= 3 ? "grid-cols-3" : categoryOptions.length === 2 ? "grid-cols-2" : "grid-cols-1"
        )}
      >
        {categoryOptions.map((option) => {
          const generated = hasGeneratedUrl(option.id)
          return (
            <button
              key={option.id}
              type="button"
              onClick={() => onSelectOverlay(option.id)}
              className={cn(
                "flex flex-col items-start rounded-md border px-2.5 py-2 text-left transition-colors",
                selectedOverlayId === option.id
                  ? "border-teal-500 bg-teal-50 dark:border-teal-400 dark:bg-teal-950/30"
                  : "border-slate-200 bg-white hover:border-slate-300 dark:border-border dark:bg-card"
              )}
            >
              <span className="flex w-full items-center justify-between gap-2">
                <span className="text-xs font-bold text-slate-950 dark:text-foreground">{option.shortLabel}</span>
                <span
                  className={cn(
                    "size-1.5 shrink-0 rounded-full",
                    generated
                      ? "bg-teal-500"
                      : option.ready
                        ? "bg-amber-400"
                        : "bg-slate-300 dark:bg-slate-600"
                  )}
                  title={
                    generated
                      ? "오버레이 준비됨"
                      : option.category === "deepfake" && deepfakeOverlayBlocked
                        ? "오버레이 없음"
                        : option.ready
                          ? "생성 대기 · 클릭 시 생성"
                          : "데이터 대기"
                  }
                />
              </span>
              <span className="mt-0.5 line-clamp-2 text-[10px] font-medium leading-4 text-slate-500">
                {option.overlayBadge}
              </span>
            </button>
          )
        })}
      </div>

      {categoryOptions.length === 0 ? (
        <p className="text-center text-xs font-semibold text-slate-400">
          {overlayCategory === "forgery"
            ? "위변조 모듈 결과가 없습니다."
            : "딥페이크 모듈 타임라인이 없습니다."}
        </p>
      ) : null}
    </div>
  )
}
