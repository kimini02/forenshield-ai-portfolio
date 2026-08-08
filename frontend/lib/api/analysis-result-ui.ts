import type {
  EvidenceDetailData,
  FrameScore,
  ModelScore,
  ModuleResult,
  SuspiciousSegment,
} from "@/lib/api/evidence-detail"
import { formatDateTime, formatDuration } from "@/lib/formatters"

const TIMELINE_MODULE = "video_timeline"
const DEFAULT_HIGH_RISK_THRESHOLD = 0.6
const REVIEW_THRESHOLD = 0.3
/** GPU placeholder로 흔히 들어오는 미실행 모듈 점수 상한 (예: frame_edit 0.05) */
const MIN_EXECUTED_MODULE_SCORE = 0.1

/** fusion_v4_ts_gated.json module_thresholds + fusion T 와 맞춤 */
export const DEFAULT_MODULE_THRESHOLDS = {
  deepfake: 0.6051,
  deepfake_cnn: 0.78,
  deepfake_temporal: 0.5,
  deepfake_optical: 0.417,
} as const

const CANONICAL_MODEL_SCORE_KEYS = [
  "deepfake",
  "deepfake_cnn",
  "deepfake_temporal",
  "deepfake_optical",
] as const

type CanonicalModelScoreKey = (typeof CANONICAL_MODEL_SCORE_KEYS)[number]

export type UiSignalSegment = {
  label: string
  startSec: number
}

export type UiRiskSignal = {
  label: string
  /** 신호를 산출한 모델 식별 정보 (예: "Xception v2.4.1"). 백엔드 미제공 시 null */
  modelLabel: string | null
  /** 이 검사가 무엇을 확인하는지에 대한 한 줄 정의 (점수와 무관한 순수 설명) */
  definition: string
  badge: string
  score: number
  thresholdPercent: number
  tone: "danger" | "warning" | "neutral"
  /** 모듈이 실측으로 보고한 의심 구간만 담는다. 없으면 빈 배열 */
  segments: UiSignalSegment[]
}

export type UiTopRiskFrame = {
  time: string
  seconds: number
  score: number
  signal: string
}

export type UiSummaryAction = {
  text: string
  seekSec?: number
  tab?: "detection" | "frames"
}

export type UiMethodologyModel = {
  name: string
  version: string
  role: string
  /** 이번 분석에서 이 모델이 담당한 신호들의 최고 위험 점수 (0~1) */
  score: number | null
  /** 이 모델(또는 fusion) 판정 임계값 (0~1) */
  threshold: number
  /** 판정 임계값 초과 여부 */
  overThreshold: boolean
  /** 모델 개발 시점의 검증 성능. 백엔드 미제공 시 null */
  benchmark: string | null
}

export type UiModelSetting = {
  label: string
  value: string
}

export type UiMethodologyInfo = {
  models: UiMethodologyModel[]
  settings: UiModelSetting[]
  inputHash: string | null
  hashAlgorithm: string | null
}

const MODULE_LABELS: Record<string, string> = {
  deepfake: "딥페이크(얼굴 합성)",
  lip_sync: "립싱크 불일치",
  frame_edit: "프레임 편집 흔적",
  splicing: "구간 이어붙이기",
  re_encoding: "재인코딩 흔적",
}

/** 신호별 한 줄 정의. 점수·판정을 만들지 않는 순수 설명 사전이다. */
const SIGNAL_DEFINITIONS: Record<string, string> = {
  "딥페이크(얼굴 합성)": "얼굴 영역의 생성형 합성 패턴을 공간 특징 기준으로 탐지하는 검사입니다.",
  "얼굴 합성 흔적": "얼굴 주변 경계, 피부 질감, 배경 연결성이 자연스러운지 비교하는 검사입니다.",
  "프레임 흐름 불일치": "프레임 사이 표정과 움직임이 자연스럽게 이어지는지 확인하는 검사입니다.",
  "생성형 패턴 흔적": "생성 모델 특유의 질감·주파수 패턴이 남아 있는지 확인하는 검사입니다.",
  "압축 흔적 불일치": "구간별 압축 흔적이 주변 프레임과 다르게 나타나는지 확인하는 검사입니다.",
  "메타데이터 불일치": "파일 정보와 영상 특성이 서로 맞는지 확인하는 검사입니다.",
  "움직임·조명 불일치": "움직임 흐름과 조명 변화가 자연스럽게 이어지는지 확인하는 검사입니다.",
  "편집 흔적 의심": "프레임 삽입·삭제, 구간 연결 등 편집 흔적을 확인하는 검사입니다.",
  "립싱크 불일치": "음성과 입술 움직임의 동기화가 어긋나는지 확인하는 검사입니다.",
  "프레임 편집 흔적": "프레임 단위 삽입·삭제·합성 흔적을 확인하는 검사입니다.",
  "구간 이어붙이기": "서로 다른 영상 구간을 연결·절단한 흔적을 확인하는 검사입니다.",
  "재인코딩 흔적": "재압축·트랜스코딩으로 생기는 인코딩 특성 변화를 확인하는 검사입니다.",
  "음성 합성·편집 흔적": "스펙트럼 연속성과 발화 구간에서 합성·편집 패턴을 확인하는 검사입니다.",
}

const DEFAULT_SIGNAL_DEFINITION = "영상의 조작 의심 신호를 탐지하는 검사입니다."

export function getSignalDefinition(label: string) {
  return SIGNAL_DEFINITIONS[label] ?? DEFAULT_SIGNAL_DEFINITION
}

export function isAnalysisModule(moduleName: string) {
  return moduleName.toLowerCase() !== TIMELINE_MODULE
}

type ModuleDetailsMeta = {
  executed?: boolean
}

function parseModuleDetails(details: string | null | undefined): ModuleDetailsMeta {
  if (!details?.trim()) return {}
  try {
    const parsed = JSON.parse(details) as ModuleDetailsMeta
    return parsed && typeof parsed === "object" ? parsed : {}
  } catch {
    return {}
  }
}

/** GPU가 실제 실행·보고한 탐지 모듈만 UI에 노출합니다. */
export function isExecutedDetectionModule(module: ModuleResult) {
  if (!isAnalysisModule(module.moduleName)) return false

  const meta = parseModuleDetails(module.details)
  if (meta.executed === true) return true

  const score = normalizeResultValue(module.score)
  if (module.detected) return true
  if (score >= MIN_EXECUTED_MODULE_SCORE) return true
  return false
}

export function getDetectionModules(modules: ModuleResult[]) {
  return modules.filter((module) => isExecutedDetectionModule(module))
}

export function normalizeResultValue(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return 0
  if (value > 0 && value <= 1) return value
  return Math.max(0, Math.min(100, value)) / 100
}

export function formatScoreOutOf100(value: number | null | undefined) {
  if (value == null) return "-"
  const normalized = normalizeResultValue(value)
  return `${Math.round(normalized * 100)} / 100`
}

export function formatModuleLabel(moduleName: string) {
  const key = moduleName.toLowerCase()
  if (MODULE_LABELS[key]) return MODULE_LABELS[key]

  const normalized = key
  if (normalized.includes("gan") || normalized.includes("fingerprint")) return "생성형 패턴 흔적"
  if (normalized.includes("temporal") || normalized.includes("timeline") || normalized.includes("consistency") || normalized.includes("lip")) return "프레임 흐름 불일치"
  if (normalized.includes("boundary") || normalized.includes("synthesis") || normalized.includes("swap") || normalized.includes("face") || normalized.includes("deepfake")) return "얼굴 합성 흔적"
  if (normalized.includes("compression") || normalized.includes("artifact")) return "압축 흔적 불일치"
  if (normalized.includes("metadata")) return "메타데이터 불일치"
  if (normalized.includes("optical") || normalized.includes("flow") || normalized.includes("pose") || normalized.includes("motion")) return "움직임·조명 불일치"
  if (normalized.includes("copy") || normalized.includes("splice") || normalized.includes("edit")) return "편집 흔적 의심"
  if (normalized.includes("voice") || normalized.includes("audio")) return "음성 합성·편집 흔적"
  if (normalized.includes("xception")) return "Xception 딥페이크 탐지"
  return moduleName
}

export function getDetectionThreshold(data: EvidenceDetailData | null) {
  const threshold = data?.analysisInfo.detectionThreshold
  if (threshold != null && Number.isFinite(threshold) && threshold > 0 && threshold <= 1) {
    return threshold
  }
  return DEFAULT_HIGH_RISK_THRESHOLD
}

/** 모델 카드/막대용 모듈별 판정 임계값. timeline.threshold 우선, 없으면 운영 기본값. */
export function resolveModelScoreThreshold(
  key: CanonicalModelScoreKey | string,
  data: EvidenceDetailData | null,
  fusionThreshold?: number
): number {
  const fusionT =
    fusionThreshold != null && Number.isFinite(fusionThreshold)
      ? fusionThreshold
      : getDetectionThreshold(data)

  if (key === "deepfake") return fusionT

  const timelineKey =
    key === "deepfake_cnn"
      ? "cnn"
      : key === "deepfake_temporal"
        ? "temporal"
        : key === "deepfake_optical"
          ? "optical"
          : null

  if (timelineKey) {
    const timeline = data?.analysisInfo.moduleTimelines?.find((item) => item.module === timelineKey)
    const fromTimeline = timeline?.threshold
    if (fromTimeline != null && Number.isFinite(fromTimeline) && fromTimeline > 0 && fromTimeline <= 1) {
      return fromTimeline
    }
  }

  const fallback = DEFAULT_MODULE_THRESHOLDS[key as keyof typeof DEFAULT_MODULE_THRESHOLDS]
  return fallback ?? fusionT
}

function riskTone(score: number, threshold: number): UiRiskSignal["tone"] {
  if (score >= threshold) return "danger"
  if (score >= REVIEW_THRESHOLD) return "warning"
  return "neutral"
}

function riskBadge(score: number, detected: boolean, threshold: number) {
  if (score >= threshold || detected) return "우선 확인"
  if (score >= REVIEW_THRESHOLD) return "추가 검토"
  return "낮음"
}

function formatSegmentRange(segment: SuspiciousSegment) {
  return `${formatDuration(segment.startTime)} ~ ${formatDuration(segment.endTime)}`
}

function primarySuspiciousSegment(data: EvidenceDetailData | null) {
  const segments = data?.analysisInfo.suspiciousSegments ?? []
  if (segments.length === 0) return null
  return [...segments].sort((a, b) => b.maxRiskScore - a.maxRiskScore)[0]
}

function moduleModelLabel(module: ModuleResult) {
  const name = module.modelName?.trim()
  if (!name) return null
  const version = module.modelVersion?.trim()
  return version ? `${name} ${version}` : name
}

/** 모듈이 실측으로 보고한 구간만 UI 세그먼트로 변환한다. 추정 구간은 만들지 않는다. */
function moduleSegments(module: ModuleResult): UiSignalSegment[] {
  return (module.affectedSegments ?? []).map((segment) => ({
    label: formatSegmentRange(segment),
    startSec: segment.startTime,
  }))
}

export function buildRiskSignals(data: EvidenceDetailData | null): {
  primary: UiRiskSignal[]
  extra: UiRiskSignal[]
} {
  const threshold = getDetectionThreshold(data)
  const modules = getDetectionModules(data?.analysisInfo.moduleResults ?? [])
    .map((module) => ({
      module,
      score: normalizeResultValue(module.score),
    }))
    .sort((a, b) => b.score - a.score)

  if (modules.length === 0) {
    return { primary: [], extra: [] }
  }

  const signals = modules.map(({ module, score }) => {
    const label = formatModuleLabel(module.moduleName)
    return {
      label,
      modelLabel: moduleModelLabel(module),
      definition: getSignalDefinition(label),
      badge: riskBadge(score, module.detected, threshold),
      score,
      thresholdPercent: Math.round(threshold * 100),
      tone: riskTone(score, threshold),
      segments: moduleSegments(module),
    }
  })

  const primary = signals.filter((signal) => signal.score >= REVIEW_THRESHOLD || signal.tone === "danger")
  const extra = signals.filter((signal) => signal.score < REVIEW_THRESHOLD && signal.tone !== "danger")

  return {
    primary: primary.slice(0, 4),
    extra,
  }
}

export function buildTopRiskFrames(
  data: EvidenceDetailData | null,
  frameScores: FrameScore[]
): UiTopRiskFrame[] {
  if (frameScores.length === 0) return []

  const topModule =
    getDetectionModules(data?.analysisInfo.moduleResults ?? []).sort(
      (a, b) => normalizeResultValue(b.score) - normalizeResultValue(a.score)
    )[0]?.moduleName ?? "deepfake"

  return [...frameScores]
    .sort((a, b) => normalizeResultValue(b.score) - normalizeResultValue(a.score))
    .slice(0, 5)
    .map((frame, index) => {
      const seconds = frame.timeSec ?? index
      const score = Math.round(normalizeResultValue(frame.score) * 100)
      return {
        time: frame.timestamp ?? formatDuration(seconds),
        seconds,
        score,
        signal: formatModuleLabel(topModule),
      }
    })
}

/** 핵심 요약: 숫자 나열 대신 "무엇을 먼저 확인할지"를 행동 단위로 안내한다. */
export function buildSummaryActions(
  data: EvidenceDetailData | null,
  frameScores: FrameScore[]
): UiSummaryAction[] {
  const threshold = getDetectionThreshold(data)
  const thresholdPercent = Math.round(threshold * 100)
  const riskScore = normalizeResultValue(data?.analysisInfo.riskScore)
  const modules = getDetectionModules(data?.analysisInfo.moduleResults ?? [])
  const topModule = [...modules].sort(
    (a, b) => normalizeResultValue(b.score) - normalizeResultValue(a.score)
  )[0]
  const reviewRange = getPriorityReviewRange(data, frameScores)
  const highRiskFrameCount = frameScores.filter(
    (frame) => normalizeResultValue(frame.score) >= threshold
  ).length

  if (riskScore >= threshold) {
    const actions: UiSummaryAction[] = []
    if (reviewRange) {
      actions.push({
        text: `위험이 가장 높은 ${reviewRange.label} 구간을 먼저 확인하세요.`,
        seekSec: reviewRange.startSec,
      })
    }
    if (topModule) {
      const label = formatModuleLabel(topModule.moduleName)
      const percent = Math.round(normalizeResultValue(topModule.score) * 100)
      actions.push({
        text: `${label} 신호가 가장 강하게 측정되었습니다 (${percent}점).`,
        tab: "detection",
      })
    }
    if (frameScores.length > 0) {
      actions.push({
        text: `프레임 ${frameScores.length}개 중 ${highRiskFrameCount}개가 임계값 ${thresholdPercent}점을 초과했습니다.`,
        tab: "frames",
      })
    }
    if (actions.length === 0) {
      actions.push({ text: "분석 점수와 탐지 신호 기준으로 조작 의심 구간이 확인되었습니다." })
    }
    return actions
  }

  if (riskScore >= REVIEW_THRESHOLD) {
    const actions: UiSummaryAction[] = [
      { text: "일부 분석 지표가 검토 기준에 근접해 추가 확인이 필요합니다." },
    ]
    if (topModule) {
      actions.push({
        text: `${formatModuleLabel(topModule.moduleName)} 신호를 우선 검토하세요.`,
        tab: "detection",
      })
    }
    if (reviewRange) {
      actions.push({
        text: `${reviewRange.label} 구간의 프레임 위험도가 상대적으로 높습니다.`,
        seekSec: reviewRange.startSec,
      })
    }
    return actions
  }

  if (data?.analysisInfo.status === "COMPLETED") {
    return [
      { text: "주요 분석 지표에서 높은 조작 의심 신호는 확인되지 않았습니다." },
      { text: "자동 분석 결과이므로 최종 판단은 원본 자료와 검토 의견을 함께 확인해야 합니다." },
    ]
  }

  const segment = primarySuspiciousSegment(data)
  if (segment?.reason) {
    return [
      { text: segment.reason },
      {
        text: `${formatSegmentRange(segment)} 구간에서 최고 위험 점수 ${Math.round(normalizeResultValue(segment.maxRiskScore) * 100)}점이 측정되었습니다.`,
        seekSec: segment.startTime,
      },
    ]
  }

  return [{ text: "분석이 완료되었으나 표시할 요약 정보가 아직 없습니다." }]
}

export function getPriorityReviewRange(data: EvidenceDetailData | null, frameScores: FrameScore[]) {
  const segment = primarySuspiciousSegment(data)
  if (segment) {
    return {
      startSec: segment.startTime,
      endSec: segment.endTime,
      label: formatSegmentRange(segment),
    }
  }

  const threshold = getDetectionThreshold(data)
  const highRiskFrames = frameScores
    .filter((frame) => normalizeResultValue(frame.score) >= threshold)
    .sort((a, b) => (a.timeSec ?? 0) - (b.timeSec ?? 0))

  if (highRiskFrames.length === 0) return null

  const startSec = highRiskFrames[0].timeSec ?? 0
  const endSec = highRiskFrames[highRiskFrames.length - 1].timeSec ?? startSec
  return {
    startSec,
    endSec,
    label: `${formatDuration(startSec)} ~ ${formatDuration(endSec)}`,
  }
}

/** modelScores.moduleName → 화면 표시용 모델명·역할 매핑 (BE 계약: deepfake_cnn 등) */
const MODEL_SCORE_DISPLAY: Record<string, { name: string; role: string }> = {
  deepfake: { name: "Late Fusion", role: "3개 모델 종합 판정" },
  deepfake_cnn: { name: "Xception", role: "얼굴 합성·공간 특징 탐지" },
  deepfake_temporal: { name: "TimeSformer", role: "프레임 시계열 일관성 분석" },
  deepfake_optical: { name: "GMFlow", role: "광류 기반 움직임 보조 신호" },
}

/** 모델 개발 시점의 고정 벤치마크 (분석 데이터가 아닌 모델 카드 참조값) */
const MODEL_BENCHMARKS: Record<string, string> = {
  Xception: "AUC 0.97 · FaceForensics++ (c23)",
  TimeSformer: "정확도 0.91 · 내부 시계열 검증 세트",
  GMFlow: "광류 보조 신호 · 단독 판정에 사용하지 않음",
}

/** "timesformer/v1.1.0-celeb1k" → "v1.1.0-celeb1k" 로 모델명 접두어 제거 */
function cleanModelVersion(version: string | null | undefined) {
  const trimmed = version?.trim()
  if (!trimmed) return "-"
  return trimmed.includes("/") ? (trimmed.split("/").pop() || trimmed) : trimmed
}

function buildMethodologyModels(data: EvidenceDetailData | null, threshold: number): UiMethodologyModel[] {
  const modelScores = (data?.analysisInfo.modelScores ?? []).filter(
    (model) =>
      model.moduleName?.toLowerCase() !== TIMELINE_MODULE && !isForgeryLaneModelScore(model)
  )
  const visibleModelScores = modelScores

  if (visibleModelScores.length > 0) {
    return buildCanonicalModelScoreMethodologyModels(visibleModelScores, data, threshold)
  }

  const modelMap = new Map<
    string,
    {
      name: string
      version: string
      roles: Set<string>
      score: number
      threshold: number
      overThreshold: boolean
      benchmark: string | null
    }
  >()

  for (const detectionModule of getDetectionModules(data?.analysisInfo.moduleResults ?? [])) {
    if (isForgeryLaneModuleName(detectionModule.moduleName, detectionModule.modelName)) continue
    const name = detectionModule.modelName?.trim()
    if (!name) continue
    const version = cleanModelVersion(detectionModule.modelVersion)
    const key = `${name}::${version}`
    const score = normalizeResultValue(detectionModule.score)
    const moduleThreshold = resolveModelScoreThreshold(
      getCanonicalModelScoreKey({
        moduleName: detectionModule.moduleName ?? "",
        modelName: detectionModule.modelName ?? name,
        score: score,
      }),
      data,
      threshold
    )
    const entry =
      modelMap.get(key) ??
      {
        name,
        version,
        roles: new Set<string>(),
        score: 0,
        threshold: moduleThreshold,
        overThreshold: false,
        benchmark:
          detectionModule.modelBenchmark?.trim() || MODEL_BENCHMARKS[name] || null,
      }

    entry.roles.add(formatModuleLabel(detectionModule.moduleName))
    entry.score = Math.max(entry.score, score)
    entry.threshold = moduleThreshold
    entry.overThreshold =
      entry.overThreshold || detectionModule.detected || score >= moduleThreshold
    modelMap.set(key, entry)
  }

  return [...modelMap.values()].map((entry) => ({
    name: entry.name,
    version: entry.version,
    role: [...entry.roles].join(" · "),
    score: entry.score,
    threshold: entry.threshold,
    overThreshold: entry.overThreshold,
    benchmark: entry.benchmark,
  }))
}

/** TruFor / forgery TimeSformer — 딥페이크 방법론 카드에서 제외 (deepfake_temporal 제외) */
function isForgeryLaneModuleName(moduleName?: string | null, modelName?: string | null) {
  const module = (moduleName ?? "").trim().toLowerCase().replace(/[\s-]+/g, "_")
  const model = (modelName ?? "").trim().toLowerCase()
  if (module.startsWith("deepfake")) return false
  if (module === "forgery_spatial" || module === "forgery_temporal") return true
  if (module.includes("forgery") || module.includes("tamper")) return true
  if (model.includes("trufor")) return true
  return false
}

function isForgeryLaneModelScore(model: ModelScore) {
  return isForgeryLaneModuleName(model.moduleName, model.modelName)
}

function buildCanonicalModelScoreMethodologyModels(
  modelScores: ModelScore[],
  data: EvidenceDetailData | null,
  threshold: number
): UiMethodologyModel[] {
  const grouped = new Map<
    CanonicalModelScoreKey,
    {
      score: number | null
      overThreshold: boolean
      versions: Set<string>
      roles: Set<string>
    }
  >()

  for (const model of modelScores) {
    const key = getCanonicalModelScoreKey(model)
    const score = normalizeResultValue(model.score)
    const moduleThreshold = resolveModelScoreThreshold(key, data, threshold)
    const entry =
      grouped.get(key) ??
      {
        score: null,
        overThreshold: false,
        versions: new Set<string>(),
        roles: new Set<string>(),
      }

    entry.score = entry.score == null ? score : Math.max(entry.score, score)
    entry.overThreshold =
      entry.overThreshold || Boolean(model.detected) || score >= moduleThreshold
    entry.roles.add(formatModuleLabel(model.moduleName))
    if (model.modelVersion?.trim()) entry.versions.add(cleanModelVersion(model.modelVersion))
    grouped.set(key, entry)
  }

  return CANONICAL_MODEL_SCORE_KEYS.map((key) => {
    const display = MODEL_SCORE_DISPLAY[key]
    const entry = grouped.get(key)
    const roles = entry ? [...entry.roles].filter((role) => role !== display.name) : []
    const moduleThreshold = resolveModelScoreThreshold(key, data, threshold)

    return {
      name: display.name,
      version: entry && entry.versions.size > 0 ? [...entry.versions].join(" · ") : "-",
      role: roles.length > 0 ? `${display.role} · ${roles.join(" · ")}` : display.role,
      score: entry?.score ?? null,
      threshold: moduleThreshold,
      overThreshold: entry?.overThreshold ?? false,
      benchmark: MODEL_BENCHMARKS[display.name] ?? null,
    }
  })
}

function getCanonicalModelScoreKey(model: ModelScore): CanonicalModelScoreKey {
  const moduleName = model.moduleName?.trim().toLowerCase() ?? ""
  const modelName = model.modelName?.trim().toLowerCase() ?? ""

  if (["deepfake", "late_fusion", "fusion", "late fusion"].includes(moduleName)) return "deepfake"
  if (["deepfake_cnn", "cnn"].includes(moduleName)) return "deepfake_cnn"
  if (["deepfake_temporal", "temporal"].includes(moduleName)) return "deepfake_temporal"
  if (["deepfake_optical", "optical"].includes(moduleName)) return "deepfake_optical"

  if (
    moduleName.includes("lip") ||
    (moduleName.includes("frame") && !moduleName.includes("forgery")) ||
    moduleName.includes("splice")
  ) {
    return "deepfake_temporal"
  }

  if (
    moduleName.includes("flow") ||
    moduleName.includes("motion") ||
    moduleName.includes("pose") ||
    moduleName.includes("optical")
  ) {
    return "deepfake_optical"
  }

  if (
    moduleName.includes("face") ||
    moduleName.includes("boundary") ||
    moduleName.includes("gan") ||
    moduleName.includes("fingerprint") ||
    moduleName.includes("compression") ||
    moduleName.includes("artifact") ||
    moduleName.includes("encoding")
  ) {
    return "deepfake_cnn"
  }

  if (modelName.includes("timesformer")) return "deepfake_temporal"
  if (modelName.includes("gmflow") || modelName.includes("flow")) return "deepfake_optical"
  if (modelName.includes("xception")) return "deepfake_cnn"
  return "deepfake_cnn"
}

/**
 * 분석 방법론 탭: 실제 실행된 모델과 재현에 필요한 파라미터만 담는다.
 * 백엔드가 제공하지 않은 값은 "-"로 표시하고 UI에서 만들어내지 않는다.
 * modelScores(4모델 계약)가 있으면 그것을 우선 사용하고, 없으면 moduleResults로 대체한다.
 */
export function buildMethodologyInfo(
  data: EvidenceDetailData | null,
  frameScores: FrameScore[]
): UiMethodologyInfo {
  const metadata = data?.evidenceInfo.technicalMetadata
  const threshold = getDetectionThreshold(data)
  const models = buildMethodologyModels(data, threshold)

  const analyzedFrameCount =
    frameScores.length > 0 ? frameScores.length : parseAnalyzedFrameCount(data)
  const analyzedAt = data?.analysisInfo.completedAt

  const settings: UiModelSetting[] = [
    { label: "분석 ID", value: data?.analysisInfo.analysisId?.trim() || "-" },
    { label: "분석 일시", value: analyzedAt ? formatDateTime(analyzedAt) : "-" },
    { label: "Fusion 판정 임계값", value: `${Math.round(threshold * 100)} / 100` },
    {
      label: "모듈별 임계값",
      value: models
        .filter((model) => model.name !== "Late Fusion")
        .map((model) => `${model.name} ${Math.round(model.threshold * 100)}`)
        .join(" · ") || "-",
    },
    {
      label: "입력 해상도",
      value: metadata?.width && metadata?.height ? `${metadata.width} x ${metadata.height}` : "-",
    },
    { label: "분석 프레임 수", value: analyzedFrameCount != null ? `${analyzedFrameCount}개` : "-" },
    { label: "영상 길이", value: metadata?.durationSec != null ? formatDuration(metadata.durationSec) : "-" },
    { label: "프레임레이트", value: metadata?.fps != null ? `${metadata.fps} fps` : "-" },
    { label: "코덱", value: metadata?.codec?.trim() || "-" },
  ]

  return {
    models,
    settings,
    inputHash: data?.integrityInfo.originalHash?.trim() || null,
    hashAlgorithm: data?.integrityInfo.hashAlgorithm?.trim() || null,
  }
}

function parseAnalyzedFrameCount(data: EvidenceDetailData | null): number | null {
  const frameRisks = data?.analysisInfo.frameRisks ?? []
  if (frameRisks.length > 0) return frameRisks.length

  const items = data?.analysisInfo.evidenceItems ?? []
  for (const line of items) {
    const match = line.match(/over\s+(\d+)\s+frames?/i)
    if (match) return Number(match[1])
  }

  const summary = data?.analysisInfo.summary?.trim()
  if (summary) {
    const match = summary.match(/over\s+(\d+)\s+frames?/i)
    if (match) return Number(match[1])
  }

  return null
}
