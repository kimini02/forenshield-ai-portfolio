import type {
  ClipRisk,
  EvidenceDetailData,
  FrameRisk,
  FrameScore,
  ModelOverlayArtifact,
  ModuleTimelineKind,
  PairRisk,
  SuspiciousSegment,
} from "@/lib/api/evidence-detail"

import {
  buildDeepfakeTimelineTabs,
  buildForgeryTimelineTabs,
  type ForgeryTimelineTab,
} from "./module-timelines"

export type OverlayCategory = "deepfake" | "forgery"

export type DeepfakeOverlayModule = Exclude<ModuleTimelineKind, "forgery_spatial" | "forgery_temporal">

export type OverlayClipWindow = {
  startTimeSec: number
  endTimeSec: number
  riskScore: number
}

export type OverlaySpatialBBox = {
  /** normalized 0..1 relative to video frame */
  x: number
  y: number
  w: number
  h: number
  score: number
}

export type OverlaySpatialMarker = {
  timeSec: number
  score: number
  bboxes?: OverlaySpatialBBox[]
}

export type ModelOverlayOption = {
  id: string
  category: OverlayCategory
  label: string
  shortLabel: string
  overlayVideoUrl: string | null
  ready: boolean
  overlayBadge: string
  timelineCaption: string
  timelineScores: FrameScore[]
  /** TimeSformer 등 클립 구간 오버레이용 */
  clipWindows: OverlayClipWindow[]
  /** TruFor 등 프레임 시점 spatial marker */
  spatialMarkers: OverlaySpatialMarker[]
  detectionThreshold: number
  description: string
  pendingMessage: string
}

export type ResultMediaView = "original" | "overlay"

export const NO_HUMAN_FACE_OVERLAY_MESSAGE =
  "사람 얼굴이 검출되지 않아 딥페이크 오버레이를 생성하지 않았습니다. 원본 영상만 확인할 수 있습니다."

const DEEPFAKE_OVERLAY_ADVISORIES: Record<string, string> = {
  NO_HUMAN_FACE: NO_HUMAN_FACE_OVERLAY_MESSAGE,
  FACE_TOO_SMALL:
    "검출된 얼굴이 너무 작아 딥페이크 오버레이를 생성하지 않았습니다. 원본 영상만 확인할 수 있습니다.",
  INSUFFICIENT_FACE_SAMPLES:
    "분석에 쓸 수 있는 얼굴 프레임이 부족해 딥페이크 오버레이를 생성하지 않았습니다. 원본 영상만 확인할 수 있습니다.",
}

export function resolveDeepfakeOverlayAdvisory(errorCode: string | null | undefined): string | null {
  if (!errorCode) return null
  return DEEPFAKE_OVERLAY_ADVISORIES[errorCode] ?? null
}

export function isDeepfakeOverlayBlocked(data: EvidenceDetailData | null): boolean {
  return Boolean(resolveDeepfakeOverlayAdvisory(data?.analysisInfo.errorCode))
}

const DEEPFAKE_OVERLAY_META: Record<
  DeepfakeOverlayModule,
  { label: string; shortLabel: string; badge: string; description: string; pendingMessage: string }
> = {
  cnn: {
    label: "Xception",
    shortLabel: "Xception",
    badge: "얼굴 bbox · 위험도 컬러",
    description: "프레임별 얼굴 경계와 위험 점수를 영상 위에 표시합니다.",
    pendingMessage: "오버레이 탭을 열면 Xception 오버레이를 생성한 뒤 재생합니다.",
  },
  temporal: {
    label: "TimeSformer",
    shortLabel: "TimeSformer",
    badge: "상단 배너 · 화면 테두리",
    description: "시계열 이상이 감지된 클립 구간을 상단 배너와 화면 테두리로 표시합니다.",
    pendingMessage: "오버레이 탭을 열면 TimeSformer 오버레이를 생성한 뒤 재생합니다.",
  },
  optical: {
    label: "GMFlow",
    shortLabel: "GMFlow",
    badge: "상단 배너 · 화면 테두리",
    description: "optical flow 이상이 높은 프레임쌍 구간을 상단 배너와 화면 테두리로 표시합니다.",
    pendingMessage: "오버레이 탭을 열면 GMFlow 오버레이를 생성한 뒤 재생합니다.",
  },
}

const FORGERY_OVERLAY_META = {
  spatial: {
    label: "TruFor (Spatial)",
    shortLabel: "TruFor",
    badge: "변조 영역 bbox · 위험도 컬러",
    description: "TruFor localization map에서 뽑은 변조 영역을 네모칸으로 추적합니다.",
    pendingMessage: "오버레이 탭을 열면 TruFor 오버레이를 생성한 뒤 재생합니다.",
  },
  temporal: {
    label: "TimeSformer (Temporal)",
    shortLabel: "TimeSformer",
    badge: "상단 배너 · 화면 테두리",
    description: "TimeSformer가 의심하는 시간축 클립 구간을 상단 배너와 화면 테두리로 표시합니다.",
    pendingMessage: "오버레이 탭을 열면 TimeSformer 오버레이를 생성한 뒤 재생합니다.",
  },
} as const

/** FE option id → BE/AI overlay module path segment */
export function overlayModuleApiPath(optionId: string): string | null {
  if (optionId === "deepfake:cnn") return "cnn"
  if (optionId === "deepfake:temporal") return "temporal"
  if (optionId === "deepfake:optical") return "optical"
  if (optionId === "forgery:forgery_spatial") return "forgery_spatial"
  if (optionId === "forgery:forgery_temporal") return "forgery_temporal"
  return null
}
export function buildModelOverlayOptions(data: EvidenceDetailData | null): ModelOverlayOption[] {
  if (!data) return []

  const artifactMap = resolveOverlayArtifactMap(data)
  const legacyCnnUrl =
    data.analysisInfo.overlayVideoUrl?.trim() ||
    data.evidenceInfo.overlayVideoUrl?.trim() ||
    null

  const deepfakeOptions = (["cnn", "temporal", "optical"] as const).map((module) =>
    buildDeepfakeOverlayOption(module, data, artifactMap, legacyCnnUrl)
  )

  const forgeryOptions = buildForgeryTimelineTabs(data).map((tab) =>
    buildForgeryOverlayOption(tab, data, artifactMap)
  )

  return [...deepfakeOptions, ...forgeryOptions]
}

export function getDefaultOverlaySelection(options: ModelOverlayOption[]): {
  category: OverlayCategory
  overlayId: string
} {
  const deepfakeReady = options.find((item) => item.category === "deepfake" && item.ready)
  if (deepfakeReady) {
    return { category: "deepfake", overlayId: deepfakeReady.id }
  }

  const forgeryReady = options.find((item) => item.category === "forgery" && item.ready)
  if (forgeryReady) {
    return { category: "forgery", overlayId: forgeryReady.id }
  }

  const firstDeepfake = options.find((item) => item.category === "deepfake")
  return {
    category: "deepfake",
    overlayId: firstDeepfake?.id ?? "deepfake:cnn",
  }
}

export function findOverlayOption(
  options: ModelOverlayOption[],
  overlayId: string | null | undefined
): ModelOverlayOption | null {
  if (!overlayId) return null
  return options.find((item) => item.id === overlayId) ?? null
}

function buildDeepfakeOverlayOption(
  module: DeepfakeOverlayModule,
  data: EvidenceDetailData,
  artifactMap: Map<string, ModelOverlayArtifact>,
  legacyCnnUrl: string | null
): ModelOverlayOption {
  const meta = DEEPFAKE_OVERLAY_META[module]
  const id = `deepfake:${module}`
  const timelineTab = buildDeepfakeTimelineTabs(data).find((tab) => tab.key === module)

  const artifact = artifactMap.get(id)
  const timeline = data.analysisInfo.moduleTimelines?.find((item) => item.module === module)
  const timelineUrl = normalizeUrl(timeline?.overlayVideoUrl)
  const overlayVideoUrl =
    normalizeUrl(artifact?.overlayVideoUrl) ??
    timelineUrl ??
    (module === "cnn" ? legacyCnnUrl : null)

  const clipWindows =
    module === "optical"
      ? extractOpticalWindows(timeline?.pairRisks, timeline?.suspiciousSegments)
      : extractClipWindows(timeline?.clipRisks, timeline?.suspiciousSegments)
  const metaSize = data.evidenceInfo.technicalMetadata
  const spatialMarkers = extractSpatialMarkers(timeline?.frameRisks, [], metaSize?.width, metaSize?.height)
  const timelineScores = timelineTab?.points ?? []
  const advisoryMessage = resolveDeepfakeOverlayAdvisory(data.analysisInfo.errorCode)
  const ready =
    !advisoryMessage &&
    (Boolean(overlayVideoUrl) ||
      artifact?.status === "ready" ||
      timelineScores.length > 0 ||
      clipWindows.length > 0 ||
      spatialMarkers.length > 0)

  return {
    id,
    category: "deepfake",
    label: meta.label,
    shortLabel: meta.shortLabel,
    overlayVideoUrl,
    ready,
    overlayBadge: meta.badge,
    timelineCaption: `${meta.label} 타임라인 위험도`,
    timelineScores,
    clipWindows,
    spatialMarkers,
    detectionThreshold: timelineTab?.threshold ?? 0.6,
    description: artifact?.description?.trim() || meta.description,
    pendingMessage: advisoryMessage ?? meta.pendingMessage,
  }
}

function buildForgeryOverlayOption(
  tab: ForgeryTimelineTab,
  data: EvidenceDetailData,
  artifactMap: Map<string, ModelOverlayArtifact>
): ModelOverlayOption {
  const moduleKey = normalizeForgeryKey(tab.key)
  const id = `forgery:${moduleKey}`
  const artifact = artifactMap.get(id)
  const moduleResult = (data.analysisInfo.moduleResults ?? []).find(
    (module) => normalizeForgeryKey(module.moduleName) === moduleKey
  )

  const timeline = (data.analysisInfo.moduleTimelines ?? []).find(
    (item) => normalizeForgeryKey(String(item.module ?? "")) === moduleKey
  )
  const isSpatial = moduleKey === "forgery_spatial"
  const meta = isSpatial ? FORGERY_OVERLAY_META.spatial : FORGERY_OVERLAY_META.temporal
  const topLevelUrl = isSpatial
    ? normalizeUrl(data.analysisInfo.spatialOverlayVideoUrl)
    : normalizeUrl(data.analysisInfo.temporalOverlayVideoUrl)
  const overlayVideoUrl =
    normalizeUrl(artifact?.overlayVideoUrl) ??
    normalizeUrl(timeline?.overlayVideoUrl) ??
    topLevelUrl ??
    normalizeUrl(moduleResult?.overlayVideoUrl)

  const clipWindows = isSpatial
    ? []
    : extractClipWindows(timeline?.clipRisks, timeline?.suspiciousSegments ?? tab.segments)
  const metaSize = data.evidenceInfo.technicalMetadata
  const spatialMarkers = isSpatial
    ? extractSpatialMarkers(timeline?.frameRisks, tab.points, metaSize?.width, metaSize?.height)
    : []
  const timelineScores = tab.points
  const ready =
    Boolean(overlayVideoUrl) ||
    artifact?.status === "ready" ||
    timelineScores.length > 0 ||
    clipWindows.length > 0 ||
    spatialMarkers.length > 0

  return {
    id,
    category: "forgery",
    label: tab.label || meta.label,
    shortLabel: meta.shortLabel,
    overlayVideoUrl,
    ready,
    overlayBadge: meta.badge,
    timelineCaption: `${tab.label || meta.label} 구간 위험도`,
    timelineScores,
    clipWindows,
    spatialMarkers,
    detectionThreshold: tab.threshold,
    description: artifact?.description?.trim() || meta.description,
    pendingMessage: meta.pendingMessage,
  }
}

function resolveOverlayArtifactMap(data: EvidenceDetailData): Map<string, ModelOverlayArtifact> {
  const map = indexOverlayArtifacts(data.analysisInfo.modelOverlayArtifacts)

  const upsert = (key: string, category: OverlayCategory, label: string, overlayVideoUrl: string | null) => {
    if (!overlayVideoUrl || map.has(key)) return
    map.set(key, {
      key,
      category,
      label,
      overlayVideoUrl,
      status: "ready",
    })
  }

  upsert(
    "forgery:forgery_spatial",
    "forgery",
    FORGERY_OVERLAY_META.spatial.label,
    normalizeUrl(data.analysisInfo.spatialOverlayVideoUrl)
  )
  upsert(
    "forgery:forgery_temporal",
    "forgery",
    FORGERY_OVERLAY_META.temporal.label,
    normalizeUrl(data.analysisInfo.temporalOverlayVideoUrl)
  )

  for (const timeline of data.analysisInfo.moduleTimelines ?? []) {
    const url = normalizeUrl(timeline.overlayVideoUrl)
    if (!url) continue
    if (timeline.module === "cnn") upsert("deepfake:cnn", "deepfake", "Xception", url)
    if (timeline.module === "temporal") upsert("deepfake:temporal", "deepfake", "TimeSformer", url)
    if (timeline.module === "optical") upsert("deepfake:optical", "deepfake", "GMFlow", url)
    if (timeline.module === "forgery_spatial") {
      upsert("forgery:forgery_spatial", "forgery", FORGERY_OVERLAY_META.spatial.label, url)
    }
    if (timeline.module === "forgery_temporal") {
      upsert("forgery:forgery_temporal", "forgery", FORGERY_OVERLAY_META.temporal.label, url)
    }
  }

  return map
}

function extractClipWindows(
  clipRisks: ClipRisk[] | null | undefined,
  suspiciousSegments: SuspiciousSegment[] | null | undefined = []
): OverlayClipWindow[] {
  const fromClips = (clipRisks ?? []).map((risk) => ({
    startTimeSec: risk.startTimeSec,
    endTimeSec: risk.endTimeSec,
    riskScore: risk.riskScore,
  }))
  if (fromClips.length > 0) return fromClips

  return (suspiciousSegments ?? []).map((segment) => ({
    startTimeSec: segment.startTime,
    endTimeSec: segment.endTime,
    riskScore: segment.maxRiskScore,
  }))
}

/** GMFlow pairRisk → short time windows for banner/border preview sync. */
function extractOpticalWindows(
  pairRisks: PairRisk[] | null | undefined,
  suspiciousSegments: SuspiciousSegment[] | null | undefined = []
): OverlayClipWindow[] {
  const fromPairs = (pairRisks ?? []).map((risk) => {
    const start = Math.max(0, Number(risk.timestampSec) || 0)
    // Cover the frame pair (~2 frames); keep readable even when FPS is low.
    const end = Math.max(start + 0.2, start + 0.5)
    return {
      startTimeSec: start,
      endTimeSec: end,
      riskScore: risk.riskScore,
    }
  })
  if (fromPairs.length > 0) return fromPairs

  return extractClipWindows(null, suspiciousSegments)
}

function extractSpatialMarkers(
  frameRisks: FrameRisk[] | null | undefined,
  fallbackPoints: FrameScore[] = [],
  videoWidth?: number | null,
  videoHeight?: number | null
): OverlaySpatialMarker[] {
  if (frameRisks && frameRisks.length > 0) {
    const vw = videoWidth && videoWidth > 0 ? videoWidth : 0
    const vh = videoHeight && videoHeight > 0 ? videoHeight : 0
    return frameRisks.map((risk) => ({
      timeSec: risk.timestampSec,
      score: risk.riskScore,
      bboxes: normalizeRiskBboxes(risk.bboxes, vw, vh, risk.riskScore),
    }))
  }
  return fallbackPoints
    .filter((point) => point.timeSec != null)
    .map((point) => ({
      timeSec: point.timeSec as number,
      score: point.score,
    }))
}

function normalizeRiskBboxes(
  bboxes: FrameRisk["bboxes"],
  videoWidth: number,
  videoHeight: number,
  fallbackScore: number
): OverlaySpatialBBox[] | undefined {
  if (!bboxes?.length || videoWidth <= 0 || videoHeight <= 0) return undefined
  const out: OverlaySpatialBBox[] = []
  for (const box of bboxes) {
    const w = Number(box.w)
    const h = Number(box.h)
    if (!(w > 0) || !(h > 0)) continue
    out.push({
      x: Math.max(0, Math.min(1, Number(box.x) / videoWidth)),
      y: Math.max(0, Math.min(1, Number(box.y) / videoHeight)),
      w: Math.max(0.01, Math.min(1, w / videoWidth)),
      h: Math.max(0.01, Math.min(1, h / videoHeight)),
      score: Number(box.score ?? fallbackScore) || 0,
    })
  }
  return out.length ? out : undefined
}

function indexOverlayArtifacts(artifacts: ModelOverlayArtifact[] | null | undefined) {
  const map = new Map<string, ModelOverlayArtifact>()
  for (const artifact of artifacts ?? []) {
    const key = artifact.key?.trim()
    if (key) map.set(key, artifact)
  }
  return map
}

function normalizeUrl(value: string | null | undefined) {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

function normalizeForgeryKey(moduleName: string) {
  return moduleName.trim().toLowerCase().replace(/[\s-]+/g, "_")
}
