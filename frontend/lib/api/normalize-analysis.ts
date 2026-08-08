import type {
  AnalysisInfo,
  ClipRisk,
  EvidenceDetailData,
  FrameScore,
  ModelOverlayArtifact,
  ModelScore,
  ModuleResult,
  ModuleTimeline,
  ModuleTimelineKind,
  PairRisk,
  PerFrameFaceScore,
  RepresentativeFrame,
  SuspiciousSegment,
} from "@/lib/api/evidence-detail"

const VIDEO_TIMELINE_MODULE = "video_timeline"
const ANALYSIS_SUMMARY_FALLBACK = "분석 결과 요약이 아직 제공되지 않았습니다."

type UiFlags = {
  useMockFrames: boolean
  useMockDetectionSignals: boolean
  useMockModelInsights: boolean
  /** 실 API가 모듈별 타임라인(clip/pair)을 주지 않아 Xception 프레임만 표시하는 상태 */
  useMockTimelines: boolean
  hasUnknownAnalysisStatus: boolean
  analysisStatusLabel: string
}

export type NormalizedEvidenceDetail = EvidenceDetailData & {
  ui: UiFlags
}

type AnalysisStatus = AnalysisInfo["status"]
type RiskLevel = AnalysisInfo["riskLevel"]

export function normalizeScore(value: unknown): number | null {
  if (value == null) return null

  const parsed =
    typeof value === "number"
      ? value
      : typeof value === "string"
        ? Number(value.trim().replace("%", ""))
        : Number.NaN

  if (!Number.isFinite(parsed)) return null

  const outOf100 = parsed >= 0 && parsed <= 1 ? parsed * 100 : parsed
  return Math.max(0, Math.min(100, Number(outOf100.toFixed(2))))
}

export function normalizeAnalysisStatus(value: unknown): AnalysisStatus {
  const normalized = typeof value === "string" ? value.trim().toUpperCase() : ""

  if (normalized === "PENDING" || normalized === "WAITING" || normalized === "QUEUED") return "PENDING"
  if (normalized === "PROCESSING" || normalized === "ANALYZING" || normalized === "RUNNING") return "PROCESSING"
  if (normalized === "COMPLETED" || normalized === "COMPLETE" || normalized === "DONE" || normalized === "SUCCESS") {
    return "COMPLETED"
  }
  if (normalized === "FAILED" || normalized === "FAIL" || normalized === "ERROR") return "FAILED"

  return "PENDING"
}

function isUnknownAnalysisStatus(value: unknown) {
  if (value == null || value === "") return false
  const normalized = typeof value === "string" ? value.trim().toUpperCase() : ""
  return ![
    "PENDING",
    "WAITING",
    "QUEUED",
    "PROCESSING",
    "ANALYZING",
    "RUNNING",
    "COMPLETED",
    "COMPLETE",
    "DONE",
    "SUCCESS",
    "FAILED",
    "FAIL",
    "ERROR",
  ].includes(normalized)
}

function normalizeRiskLevel(value: unknown, score: number | null): RiskLevel {
  const normalized = typeof value === "string" ? value.trim().toUpperCase() : ""

  if (["HIGH", "DANGER", "RISK", "위험"].includes(normalized)) return "HIGH"
  if (["MEDIUM", "MIDDLE", "REVIEW", "WARNING", "주의", "검토"].includes(normalized)) return "MEDIUM"
  if (["LOW", "NORMAL", "SAFE", "정상", "낮음"].includes(normalized)) return "LOW"

  if (score == null) return null
  if (score >= 70) return "HIGH"
  if (score >= 45) return "MEDIUM"
  return "LOW"
}

function normalizeTimeSec(value: unknown, fallback: number | null = null) {
  const parsed = typeof value === "number" ? value : Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return fallback
  return parsed
}

function normalizeText(value: unknown): string
function normalizeText(value: unknown, fallback: null): string | null
function normalizeText(value: unknown, fallback: string): string
function normalizeText(value: unknown, fallback: string | null | undefined = "") {
  return typeof value === "string" && value.trim() ? value.trim() : fallback
}

function scoreOrZero(value: unknown) {
  return normalizeScore(value) ?? 0
}

function readFrameRisksFromTimelineModule(modules: ModuleResult[]): FrameScore[] {
  const timeline = modules.find((module) => String(module.moduleName ?? "").toLowerCase() === VIDEO_TIMELINE_MODULE)
  if (!timeline?.details?.trim()) return []

  try {
    const parsed = JSON.parse(timeline.details) as { frameRisks?: Array<Record<string, unknown>> }
    const risks = parsed.frameRisks ?? []
    if (!Array.isArray(risks) || risks.length === 0) return []

    return risks.map((risk, index) => ({
      timeSec: normalizeTimeSec(risk.timestampSec, index),
      score: scoreOrZero(risk.riskScore),
    }))
  } catch {
    return []
  }
}

function mapFrameRisksToFrameScores(detail: EvidenceDetailData): FrameScore[] {
  // Backend: analysisInfo.frameRisks[] = { timestampSec, riskScore } (0~1)
  const risks = detail.analysisInfo.frameRisks ?? []
  if (Array.isArray(risks) && risks.length > 0) {
    return risks.map((r) => ({
      timeSec: normalizeTimeSec(r.timestampSec, r.frameIndex),
      score: scoreOrZero(r.riskScore),
    }))
  }

  const fromTimeline = readFrameRisksFromTimelineModule(detail.analysisInfo.moduleResults ?? [])
  if (fromTimeline.length > 0) return fromTimeline

  return normalizeFrameScores(detail.analysisInfo.frameScores ?? [])
}

function normalizeFrameScores(frames: FrameScore[]): FrameScore[] {
  return frames
    .map((frame, index) => ({
      timeSec: normalizeTimeSec(frame.timeSec, index),
      timestamp: normalizeText(frame.timestamp, null),
      score: scoreOrZero(frame.score),
    }))
    .filter((frame) => frame.timeSec != null || frame.timestamp != null)
}

function normalizeModuleResults(modules: ModuleResult[]): ModuleResult[] {
  return modules.map((module, index) => {
    const moduleName = normalizeText(module.moduleName, `module_${index + 1}`)
    return {
      ...module,
      moduleName,
      detected: Boolean(module.detected),
      score: scoreOrZero(module.score),
      deepfakeScore: module.deepfakeScore == null ? null : normalizeScore(module.deepfakeScore),
      confidence: module.confidence == null ? null : normalizeScore(module.confidence),
      modelName: normalizeText(module.modelName, null),
      modelVersion: normalizeText(module.modelVersion, null),
      details: typeof module.details === "string" ? module.details : "",
      overlayVideoUrl: normalizeText(module.overlayVideoUrl, null),
    }
  })
}

function normalizeModelScores(models: ModelScore[]): ModelScore[] {
  return models.map((model, index) => {
    const moduleName = normalizeText(model.moduleName, `model_${index + 1}`)
    return {
      ...model,
      moduleName,
      modelName: normalizeText(model.modelName, moduleName),
      detected: Boolean(model.detected),
      score: scoreOrZero(model.score),
      confidence: model.confidence == null ? null : normalizeScore(model.confidence),
      modelVersion: normalizeText(model.modelVersion, null),
    }
  })
}

function normalizeClipRisks(clips: ClipRisk[] | null | undefined): ClipRisk[] {
  if (!Array.isArray(clips)) return []
  return clips
    .map((clip, index) => {
      const startTimeSec = normalizeTimeSec(clip.startTimeSec, index) ?? index
      const endTimeSec = normalizeTimeSec(clip.endTimeSec, startTimeSec) ?? startTimeSec
      return {
        clipIndex: typeof clip.clipIndex === "number" ? clip.clipIndex : index,
        startFrameIndex: Math.max(0, Math.round(Number(clip.startFrameIndex) || 0)),
        endFrameIndex: Math.max(0, Math.round(Number(clip.endFrameIndex) || 0)),
        startTimeSec,
        endTimeSec: Math.max(endTimeSec, startTimeSec),
        score: scoreOrZero(clip.riskScore),
      }
    })
    .map((clip) => ({
      clipIndex: clip.clipIndex,
      startFrameIndex: clip.startFrameIndex,
      endFrameIndex: clip.endFrameIndex,
      startTimeSec: clip.startTimeSec,
      endTimeSec: clip.endTimeSec,
      // scoreOrZero는 0~100으로 변환하므로 다시 0~1 스케일로 저장해 다른 타임라인과 단위를 맞춘다
      riskScore: clip.score / 100,
    }))
}

function normalizePairRisks(pairs: PairRisk[] | null | undefined): PairRisk[] {
  if (!Array.isArray(pairs)) return []
  return pairs.map((pair, index) => ({
    pairIndex: typeof pair.pairIndex === "number" ? pair.pairIndex : index,
    frameIndexA: Math.max(0, Math.round(Number(pair.frameIndexA) || 0)),
    frameIndexB: Math.max(0, Math.round(Number(pair.frameIndexB) || 0)),
    timestampSec: normalizeTimeSec(pair.timestampSec, index) ?? index,
    riskScore: scoreOrZero(pair.riskScore) / 100,
    motionMagnitude:
      pair.motionMagnitude == null || !Number.isFinite(Number(pair.motionMagnitude))
        ? null
        : Number(pair.motionMagnitude),
  }))
}

const MODULE_TIMELINE_KINDS: ModuleTimelineKind[] = [
  "cnn",
  "temporal",
  "optical",
  "forgery_spatial",
  "forgery_temporal",
]

function normalizeModuleKind(value: unknown): ModuleTimelineKind | null {
  const normalized = typeof value === "string" ? value.trim().toLowerCase() : ""
  if (MODULE_TIMELINE_KINDS.includes(normalized as ModuleTimelineKind)) {
    return normalized as ModuleTimelineKind
  }
  if (["deepfake_cnn", "xception"].includes(normalized)) return "cnn"
  if (["deepfake_temporal", "timesformer"].includes(normalized)) return "temporal"
  if (["deepfake_optical", "gmflow"].includes(normalized)) return "optical"
  if (normalized === "trufor" || normalized === "spatial") return "forgery_spatial"
  if (normalized === "forgery_temporal" || normalized === "forgery-temporal") return "forgery_temporal"
  return null
}

/** BE/GPU 0~1 또는 0~100 스케일을 0~1로 통일 */
function normalizeUnitScore(value: unknown, fallback = 0): number {
  if (value == null || !Number.isFinite(Number(value))) return fallback
  const parsed = Number(value)
  if (parsed >= 0 && parsed <= 1) return parsed
  return Math.max(0, Math.min(1, parsed / 100))
}

function normalizeModelOverlayArtifacts(
  artifacts: ModelOverlayArtifact[] | null | undefined
): ModelOverlayArtifact[] {
  if (!Array.isArray(artifacts)) return []
  return artifacts
    .map((artifact, index) => {
      const key = normalizeText(artifact.key, `overlay_${index + 1}`)
      const category: "deepfake" | "forgery" =
        artifact.category === "forgery" ? "forgery" : "deepfake"
      return {
        key,
        category,
        label: normalizeText(artifact.label, key),
        overlayVideoUrl: normalizeText(artifact.overlayVideoUrl, null),
        status: artifact.status ?? (artifact.overlayVideoUrl ? "ready" : "pending"),
        description: normalizeText(artifact.description, null),
      }
    })
    .filter((artifact) => Boolean(artifact.key))
}

function normalizeModuleTimelines(timelines: ModuleTimeline[] | null | undefined): ModuleTimeline[] {
  if (!Array.isArray(timelines)) return []
  return timelines
    .map((timeline): ModuleTimeline | null => {
      const moduleKind = normalizeModuleKind(timeline.module)
      if (!moduleKind) return null
      return {
        module: moduleKind,
        modelName: normalizeText(timeline.modelName, moduleKind),
        modelVersion: normalizeText(timeline.modelVersion, null),
        videoScore: normalizeUnitScore(timeline.videoScore),
        threshold: normalizeUnitScore(timeline.threshold, 0.5),
        detected: Boolean(timeline.detected),
        frameRisks: (timeline.frameRisks ?? []).map((risk, index) => ({
          frameIndex: Math.max(0, Math.round(Number(risk.frameIndex) || index)),
          timestampSec: normalizeTimeSec(risk.timestampSec, index) ?? index,
          riskScore: normalizeUnitScore(risk.riskScore),
        })),
        clipRisks: normalizeClipRisks(timeline.clipRisks),
        pairRisks: normalizePairRisks(timeline.pairRisks),
        suspiciousSegments: normalizeSuspiciousSegments(timeline.suspiciousSegments ?? []),
        overlayVideoUrl: normalizeText(timeline.overlayVideoUrl, null),
      }
    })
    .filter((timeline): timeline is ModuleTimeline => timeline != null)
}

function clamp01(value: number) {
  return Math.max(0, Math.min(1, value))
}

function normalizeSuspiciousSegments(segments: SuspiciousSegment[]): SuspiciousSegment[] {
  return segments
    .map((segment) => {
      const startTime = normalizeTimeSec(segment.startTime, 0) ?? 0
      const endTime = normalizeTimeSec(segment.endTime, startTime) ?? startTime
      return {
        startTime,
        endTime: Math.max(endTime, startTime),
        maxRiskScore: normalizeUnitScore(segment.maxRiskScore),
        reason: normalizeText(segment.reason, "의심 구간으로 표시되었습니다."),
      }
    })
    .filter((segment) => segment.endTime >= segment.startTime)
}

function normalizeRepresentativeFrames(frames: RepresentativeFrame[]): RepresentativeFrame[] {
  return frames.map((frame, index) => ({
    ...frame,
    timeSec: normalizeTimeSec(frame.timeSec, null),
    timestamp: normalizeText(frame.timestamp, null),
    frameNumber:
      typeof frame.frameNumber === "number" && Number.isFinite(frame.frameNumber)
        ? frame.frameNumber
        : index + 1,
    score: frame.score == null ? null : normalizeScore(frame.score),
    imageUrl: normalizeText(frame.imageUrl, null),
    module: normalizeText(frame.module, null),
    heatmapImageUrl: normalizeText(frame.heatmapImageUrl, null),
  }))
}

function normalizePerFrameFaceScores(scores: PerFrameFaceScore[] | null | undefined): PerFrameFaceScore[] {
  if (!Array.isArray(scores)) return []
  return scores.map((row, index) => ({
    frameIndex: Math.max(0, Math.round(Number(row.frameIndex) || index)),
    faceIndex: Math.max(0, Math.round(Number(row.faceIndex) || 0)),
    riskScore: scoreOrZero(row.riskScore) / 100,
    bbox:
      row.bbox &&
      Number.isFinite(Number(row.bbox.x)) &&
      Number.isFinite(Number(row.bbox.y)) &&
      Number.isFinite(Number(row.bbox.w)) &&
      Number.isFinite(Number(row.bbox.h))
        ? {
            x: Math.round(Number(row.bbox.x)),
            y: Math.round(Number(row.bbox.y)),
            w: Math.round(Number(row.bbox.w)),
            h: Math.round(Number(row.bbox.h)),
          }
        : null,
  }))
}

function normalizeEvidenceItems(items: string[] | null | undefined) {
  return Array.isArray(items)
    ? items.map((item) => normalizeText(item)).filter(Boolean)
    : []
}

export function normalizeEvidenceDetailForUi(detail: EvidenceDetailData): NormalizedEvidenceDetail {
  const rawStatus = detail.analysisInfo.status
  const hasUnknownAnalysisStatus = isUnknownAnalysisStatus(rawStatus)
  const status = normalizeAnalysisStatus(rawStatus)
  const riskScore = normalizeScore(detail.analysisInfo.riskScore)
  const confidenceScore = normalizeScore(detail.analysisInfo.confidenceScore)
  const frameScores = mapFrameRisksToFrameScores(detail)
  const moduleResults = normalizeModuleResults(detail.analysisInfo.moduleResults ?? [])
  const modelScores = normalizeModelScores(detail.analysisInfo.modelScores ?? [])
  const suspiciousSegments = normalizeSuspiciousSegments(detail.analysisInfo.suspiciousSegments ?? [])
  const representativeFrames = normalizeRepresentativeFrames(detail.analysisInfo.representativeFrames ?? [])
  const evidenceItems = normalizeEvidenceItems(detail.analysisInfo.evidenceItems)
  const clipRisks = normalizeClipRisks(detail.analysisInfo.clipRisks)
  const pairRisks = normalizePairRisks(detail.analysisInfo.pairRisks)
  const temporalSuspiciousSegments = normalizeSuspiciousSegments(
    detail.analysisInfo.temporalSuspiciousSegments ?? []
  )
  const opticalSuspiciousSegments = normalizeSuspiciousSegments(
    detail.analysisInfo.opticalSuspiciousSegments ?? []
  )
  const moduleTimelines = normalizeModuleTimelines(detail.analysisInfo.moduleTimelines)
  const modelOverlayArtifacts = normalizeModelOverlayArtifacts(detail.analysisInfo.modelOverlayArtifacts)
  const spatialOverlayVideoUrl = normalizeText(detail.analysisInfo.spatialOverlayVideoUrl, null)
  const temporalOverlayVideoUrl = normalizeText(detail.analysisInfo.temporalOverlayVideoUrl, null)
  const perFrameFaceScores = normalizePerFrameFaceScores(detail.analysisInfo.perFrameFaceScores)

  const useMockFrames = frameScores.length === 0
  const useMockDetectionSignals = moduleResults.length === 0
  const useMockModelInsights = modelScores.length === 0
  const useMockTimelines = moduleTimelines.length === 0

  return {
    ...detail,
    analysisInfo: {
      ...detail.analysisInfo,
      status,
      riskScore,
      confidenceScore,
      riskLevel: normalizeRiskLevel(detail.analysisInfo.riskLevel, riskScore),
      summary: normalizeText(detail.analysisInfo.summary, ANALYSIS_SUMMARY_FALLBACK),
      evidenceItems,
      moduleResults,
      modelScores,
      suspiciousSegments,
      clipRisks,
      pairRisks,
      temporalSuspiciousSegments,
      opticalSuspiciousSegments,
      moduleTimelines,
      frameScores,
      representativeFrames,
      modelOverlayArtifacts,
      spatialOverlayVideoUrl,
      temporalOverlayVideoUrl,
      perFrameFaceScores,
    },
    ui: {
      useMockFrames,
      useMockDetectionSignals,
      useMockModelInsights,
      useMockTimelines,
      hasUnknownAnalysisStatus,
      analysisStatusLabel: hasUnknownAnalysisStatus ? "상태 확인 필요" : "",
    },
  }
}
