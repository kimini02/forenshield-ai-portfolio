import {
  formatScoreOutOf100,
  normalizeResultValue,
  type UiRiskSignal,
} from "@/lib/api/analysis-result-ui"
import type { EvidenceDetailData, RepresentativeFrame } from "@/lib/api/evidence-detail"
import { formatDuration } from "@/lib/formatters"

import {
  buildForgeryTimelineTabs,
  type ForgeryTimelineTab,
} from "./module-timelines"

export const FORGERY_SPATIAL_MODULE = "forgery_spatial"
export const FORGERY_TEMPORAL_MODULE = "forgery_temporal"

export const DEFAULT_FORGERY_THRESHOLDS = {
  spatial: 0.515,
  temporal: 0.173386,
} as const

export const MIN_SUSPICIOUS_SEGMENT_SEC = 1

export type ForgeryModuleKey = typeof FORGERY_SPATIAL_MODULE | typeof FORGERY_TEMPORAL_MODULE

/** moduleName·modelName 조합으로 spatial/temporal 구분 (deepfake temporal과 분리) */
export function resolveForgeryModuleKey(
  moduleName: string | null | undefined,
  modelName?: string | null
): ForgeryModuleKey | null {
  const module = (moduleName ?? "").trim().toLowerCase().replace(/[\s-]+/g, "_")
  const model = (modelName ?? "").trim().toLowerCase()

  if (module === FORGERY_SPATIAL_MODULE) return FORGERY_SPATIAL_MODULE
  if (module === FORGERY_TEMPORAL_MODULE) return FORGERY_TEMPORAL_MODULE
  if (module.includes("deepfake")) return null

  if (module.includes("spatial") || model.includes("trufor")) {
    return FORGERY_SPATIAL_MODULE
  }
  if (module.includes("forgery") || module.includes("tamper")) {
    if (model.includes("timesformer") || module.includes("temporal")) {
      return FORGERY_TEMPORAL_MODULE
    }
    if (model.includes("trufor")) return FORGERY_SPATIAL_MODULE
  }
  return null
}

export function forgeryModuleLabel(key: ForgeryModuleKey) {
  return key === FORGERY_SPATIAL_MODULE ? "TruFor (Spatial)" : "TimeSformer (Temporal)"
}

export function forgeryModuleThreshold(key: ForgeryModuleKey) {
  return key === FORGERY_SPATIAL_MODULE
    ? DEFAULT_FORGERY_THRESHOLDS.spatial
    : DEFAULT_FORGERY_THRESHOLDS.temporal
}

export function getForgerySpatialTimeline(
  data: EvidenceDetailData | null
): ForgeryTimelineTab | null {
  return buildForgeryTimelineTabs(data).find((tab) => tab.key === FORGERY_SPATIAL_MODULE) ?? null
}

export function getForgeryTemporalTimeline(
  data: EvidenceDetailData | null
): ForgeryTimelineTab | null {
  return buildForgeryTimelineTabs(data).find((tab) => tab.key === FORGERY_TEMPORAL_MODULE) ?? null
}

export type ForgeryScoreSummary = {
  spatialScore: number | null
  temporalScore: number | null
  spatialThreshold: number
  temporalThreshold: number
  highestScore: number
  overThresholdCount: number
  modelCount: number
}

export function getForgeryScoreSummary(data: EvidenceDetailData | null): ForgeryScoreSummary {
  const signals = buildForgeryResultTabSignals(data, 0.5)
  const spatial = signals.find((s) => s.label.startsWith("TruFor"))
  const temporal = signals.find((s) => s.label.startsWith("TimeSformer"))
  const spatialThreshold = spatial?.thresholdPercent
    ? spatial.thresholdPercent / 100
    : DEFAULT_FORGERY_THRESHOLDS.spatial
  const temporalThreshold = temporal?.thresholdPercent
    ? temporal.thresholdPercent / 100
    : DEFAULT_FORGERY_THRESHOLDS.temporal

  const spatialScore = spatial ? normalizeResultValue(spatial.score) : null
  const temporalScore = temporal ? normalizeResultValue(temporal.score) : null
  const scores = [spatialScore, temporalScore].filter((v): v is number => v != null)
  const highestScore = scores.length > 0 ? Math.max(...scores) : 0
  const overThresholdCount = [
    spatialScore != null && spatialScore >= spatialThreshold,
    temporalScore != null && temporalScore >= temporalThreshold,
  ].filter(Boolean).length

  return {
    spatialScore,
    temporalScore,
    spatialThreshold,
    temporalThreshold,
    highestScore,
    overThresholdCount,
    modelCount: signals.length,
  }
}

/** 결과보기 > 위변조 탐지: TruFor + TimeSformer */
export function buildForgeryResultTabSignals(
  data: EvidenceDetailData | null,
  _fallbackThreshold: number
): UiRiskSignal[] {
  const tabs = buildForgeryTimelineTabs(data)
  const spatialTab = tabs.find((tab) => tab.key === FORGERY_SPATIAL_MODULE) ?? null
  const temporalTab = tabs.find((tab) => tab.key === FORGERY_TEMPORAL_MODULE) ?? null

  const signals: UiRiskSignal[] = []

  const spatialSignal = buildSignalForTab(data, FORGERY_SPATIAL_MODULE, spatialTab)
  if (spatialSignal) signals.push(spatialSignal)

  const temporalSignal = buildSignalForTab(data, FORGERY_TEMPORAL_MODULE, temporalTab)
  if (temporalSignal) signals.push(temporalSignal)

  return signals
}

function buildSignalForTab(
  data: EvidenceDetailData | null,
  key: ForgeryModuleKey,
  tab: ForgeryTimelineTab | null
): UiRiskSignal | null {
  const threshold = tab?.threshold ?? forgeryModuleThreshold(key)
  const modelScores = (data?.analysisInfo.modelScores ?? []).filter((score) => {
    return resolveForgeryModuleKey(score.moduleName, score.modelName) === key
  })
  const modules = (data?.analysisInfo.moduleResults ?? []).filter((module) => {
    return resolveForgeryModuleKey(module.moduleName, module.modelName) === key
  })

  const durationSec = estimateMediaDurationSec(
    data,
    key === FORGERY_SPATIAL_MODULE ? tab : getForgerySpatialTimeline(data),
    key === FORGERY_TEMPORAL_MODULE ? tab : getForgeryTemporalTimeline(data)
  )

  let scoreRaw: number | null = null
  let detected = false
  let modelName: string | null = null
  let modelVersion: string | null = null
  let rawSegments: Array<{ startTime: number; endTime: number; maxRiskScore?: number }> = []

  if (modelScores[0] != null) {
    scoreRaw = modelScores[0].score
    detected = Boolean(modelScores[0].detected)
    modelName = modelScores[0].modelName
    modelVersion = modelScores[0].modelVersion ?? null
    rawSegments = (tab?.segments ?? []).map((s) => ({
      startTime: s.startTime,
      endTime: s.endTime,
      maxRiskScore: s.maxRiskScore,
    }))
  } else if (modules[0] != null) {
    scoreRaw = modules[0].score
    detected = Boolean(modules[0].detected)
    modelName = modules[0].modelName ?? null
    modelVersion = modules[0].modelVersion ?? null
    rawSegments =
      modules[0].affectedSegments && modules[0].affectedSegments.length > 0
        ? modules[0].affectedSegments
        : (tab?.segments ?? []).map((s) => ({
            startTime: s.startTime,
            endTime: s.endTime,
            maxRiskScore: s.maxRiskScore,
          }))
  } else if (tab) {
    scoreRaw = tab.videoScore
    detected = tab.detected
    modelName = tab.modelName
    modelVersion = tab.modelVersion
    rawSegments = tab.segments.map((s) => ({
      startTime: s.startTime,
      endTime: s.endTime,
      maxRiskScore: s.maxRiskScore,
    }))
  } else {
    return null
  }

  if (rawSegments.length === 0 && tab && tab.points.length > 0) {
    const top = [...tab.points].sort(
      (a, b) => normalizeResultValue(b.score) - normalizeResultValue(a.score)
    )[0]
    const t = top.timeSec ?? 0
    rawSegments = [{ startTime: t, endTime: t, maxRiskScore: top.score }]
  }

  const segments = rawSegments
    .map((segment) => {
      const expanded = expandSegmentToMinDuration(
        segment.startTime,
        segment.endTime,
        MIN_SUSPICIOUS_SEGMENT_SEC,
        durationSec
      )
      return {
        label: `${formatDuration(expanded.startSec)} ~ ${formatDuration(expanded.endSec)}`,
        startSec: expanded.startSec,
      }
    })
    .slice(0, 3)

  const score = normalizeResultValue(scoreRaw)
  const modelLabel =
    modelName?.trim()
      ? modelVersion?.trim()
        ? `${modelName.trim()} ${modelVersion.trim()}`
        : modelName.trim()
      : null

  return {
    label: forgeryModuleLabel(key),
    modelLabel,
    definition:
      key === FORGERY_SPATIAL_MODULE
        ? "TruFor가 보고한 국소 위변조(픽셀·객체 단위) 의심 신호입니다."
        : "TimeSformer가 보고한 시간축 편집(클립) 위변조 의심 신호입니다.",
    badge: signalBadgeFromScore(score, detected, threshold),
    score,
    thresholdPercent: Math.round(threshold * 100),
    tone: signalToneFromScore(score, threshold),
    segments,
  }
}

/**
 * 가장 의심되는 구간 1개 (TruFor spatial 기준).
 * 최고 점수 시점을 중심으로 하며 최소 1초로 확장.
 */
export function getForgeryPriorityReviewRange(data: EvidenceDetailData | null) {
  const spatialTab = getForgerySpatialTimeline(data)
  if (!spatialTab) return null

  const temporalTab = getForgeryTemporalTimeline(data)
  const durationSec = estimateMediaDurationSec(data, spatialTab, temporalTab)

  const candidates: Array<{ start: number; end: number; score: number }> = []

  for (const segment of spatialTab.segments) {
    candidates.push({
      start: segment.startTime,
      end: segment.endTime,
      score: normalizeResultValue(segment.maxRiskScore),
    })
  }
  for (const point of spatialTab.points) {
    const t = point.timeSec ?? 0
    candidates.push({
      start: t,
      end: t,
      score: normalizeResultValue(point.score),
    })
  }

  if (candidates.length === 0) return null

  const top = [...candidates].sort((a, b) => b.score - a.score)[0]
  const expanded = expandSegmentToMinDuration(
    top.start,
    top.end,
    MIN_SUSPICIOUS_SEGMENT_SEC,
    durationSec
  )

  return {
    startSec: expanded.startSec,
    endSec: expanded.endSec,
    label: `${formatDuration(expanded.startSec)} ~ ${formatDuration(expanded.endSec)}`,
    source: "TruFor spatial",
  }
}

export function expandSegmentToMinDuration(
  startSec: number,
  endSec: number,
  minSec: number,
  maxDurationSec: number | null
) {
  let start = Math.max(0, Math.min(startSec, endSec))
  let end = Math.max(startSec, endSec)
  if (end - start < minSec) {
    const mid = (start + end) / 2
    start = mid - minSec / 2
    end = mid + minSec / 2
  }

  if (maxDurationSec != null && maxDurationSec > 0) {
    if (end > maxDurationSec) {
      const shift = end - maxDurationSec
      end = maxDurationSec
      start = Math.max(0, start - shift)
    }
    if (start < 0) {
      end = Math.min(maxDurationSec, end - start)
      start = 0
    }
    if (end - start < minSec && maxDurationSec >= minSec) {
      if (start <= 0) {
        start = 0
        end = Math.min(maxDurationSec, minSec)
      } else {
        end = maxDurationSec
        start = Math.max(0, end - minSec)
      }
    }
  } else {
    if (start < 0) {
      end -= start
      start = 0
    }
  }

  return {
    startSec: Number(start.toFixed(3)),
    endSec: Number(end.toFixed(3)),
  }
}

function estimateMediaDurationSec(
  data: EvidenceDetailData | null,
  spatialTab: ForgeryTimelineTab | null,
  temporalTab: ForgeryTimelineTab | null
) {
  const metaDuration = data?.evidenceInfo?.technicalMetadata?.durationSec
  if (typeof metaDuration === "number" && Number.isFinite(metaDuration) && metaDuration > 0) {
    return metaDuration
  }

  let max = 0
  for (const tab of [spatialTab, temporalTab]) {
    if (!tab) continue
    for (const point of tab.points) {
      max = Math.max(max, point.timeSec ?? 0)
    }
    for (const segment of tab.segments) {
      max = Math.max(max, segment.endTime)
    }
  }
  return max > 0 ? max + 0.5 : null
}

/** 고위험 프레임/클립 — 선택 모듈의 임계값 초과 시점만(최대 maxFrames). 없으면 빈 배열. */
export function buildForgeryRepresentativeFrames(
  data: EvidenceDetailData | null,
  options?: { moduleKey?: ForgeryModuleKey | string | null; maxFrames?: number }
): RepresentativeFrame[] {
  const maxFrames = options?.maxFrames ?? 2
  const moduleKey =
    options?.moduleKey === FORGERY_TEMPORAL_MODULE
      ? FORGERY_TEMPORAL_MODULE
      : FORGERY_SPATIAL_MODULE
  const tab =
    moduleKey === FORGERY_TEMPORAL_MODULE
      ? getForgeryTemporalTimeline(data)
      : getForgerySpatialTimeline(data)
  if (!tab || tab.points.length === 0) return []

  const threshold = tab.threshold
  const overThreshold = tab.points.filter(
    (point) => normalizeResultValue(point.score) >= threshold
  )
  if (overThreshold.length === 0) return []

  const topPoints = [...overThreshold]
    .sort((a, b) => normalizeResultValue(b.score) - normalizeResultValue(a.score))
    .slice(0, maxFrames)

  const apiFrames = data?.analysisInfo.representativeFrames ?? []

  return topPoints.map((point, index) => {
    const timeSec = point.timeSec ?? 0
    const matched =
      moduleKey === FORGERY_SPATIAL_MODULE ? findClosestApiFrame(apiFrames, timeSec) : null
    const imageUrl =
      matched?.imageUrl?.trim() ||
      matched?.heatmapImageUrl?.trim() ||
      null
    const heatmapImageUrl = matched?.heatmapImageUrl?.trim() || null

    return {
      timeSec,
      timestamp: point.timestamp ?? formatDuration(timeSec),
      frameNumber: matched?.frameNumber ?? index,
      score: normalizeResultValue(point.score),
      imageUrl,
      heatmapImageUrl,
      module: matched?.module ?? moduleKey,
    }
  })
}

export function forgeryHighRiskGalleryCopy(moduleKey: ForgeryModuleKey | string | null | undefined) {
  if (moduleKey === FORGERY_TEMPORAL_MODULE) {
    return {
      title: "고위험 클립",
      description: "TimeSformer clipRisks 중 임계값 초과 상위 시점입니다(최대 2개). 서버 이미지가 없으면 영상에서 캡처합니다.",
      empty: "임계값을 넘긴 고위험 클립이 없습니다.",
    }
  }
  return {
    title: "고위험 프레임",
    description: "TruFor frameRisks 중 임계값 초과 상위 시점입니다(최대 2개). 서버 이미지가 없으면 영상에서 캡처합니다.",
    empty: "임계값을 넘긴 고위험 프레임이 없습니다.",
  }
}

function findClosestApiFrame(frames: RepresentativeFrame[], timeSec: number) {
  const withMedia = frames.filter(
    (frame) => Boolean(frame.imageUrl?.trim() || frame.heatmapImageUrl?.trim())
  )
  if (withMedia.length === 0) return null

  const preferred = withMedia.filter((frame) => {
    const mod = String(frame.module ?? "").toLowerCase()
    return (
      !mod ||
      mod.includes("forgery") ||
      mod.includes("trufor") ||
      mod.includes("spatial") ||
      mod.includes("timesformer")
    )
  })
  const pool = preferred.length > 0 ? preferred : withMedia

  let best = pool[0]
  let bestDelta = Number.POSITIVE_INFINITY
  for (const frame of pool) {
    const t = frame.timeSec
    if (t == null || !Number.isFinite(t)) continue
    const delta = Math.abs(t - timeSec)
    if (delta < bestDelta) {
      best = frame
      bestDelta = delta
    }
  }
  // 시점이 너무 멀면(2초 이상) 미디어만 있는 프레임은 쓰지 않음 — UI 캡처에 맡김
  if (bestDelta > 2) return null
  return best
}

function signalToneFromScore(score: number, threshold: number): UiRiskSignal["tone"] {
  if (score >= threshold) return "danger"
  if (score >= threshold * 0.6) return "warning"
  return "neutral"
}

function signalBadgeFromScore(score: number, detected: boolean, threshold: number) {
  if (score >= threshold || detected) return "우선 확인"
  if (score >= threshold * 0.6) return "추가 검토"
  return "낮음"
}

export function formatForgeryThresholdLabel(threshold: number) {
  return `모델 기준 ${Math.round(threshold * 100)}점`
}

export function formatForgeryDualScoreSub(summary: ForgeryScoreSummary) {
  const parts: string[] = []
  if (summary.spatialScore != null) {
    parts.push(`TruFor ${formatScoreOutOf100(summary.spatialScore)}`)
  }
  if (summary.temporalScore != null) {
    parts.push(`TimeSformer ${formatScoreOutOf100(summary.temporalScore)}`)
  }
  return parts.length > 0 ? parts.join(" · ") : "TruFor · TimeSformer"
}
