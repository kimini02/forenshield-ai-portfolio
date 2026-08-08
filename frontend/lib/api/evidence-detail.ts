import { apiDownloadWithMetadata, apiRequest, type ApiDownloadResult } from "@/lib/api/client"
import { features } from "@/lib/features"
import type { HlsPlayback } from "@/lib/hls-playback"
import { mockFetchCaseDetail, mockFetchEvidenceDetail } from "@/lib/mock/forensic-api"
import { decodeRouteParam } from "@/lib/route-params"
import type { ReviewStatus } from "@/lib/permissions"

export type { HlsPlayback, HlsStatus } from "@/lib/hls-playback"

export type EvidenceLifecycleStatus = "ACTIVE" | "EXCLUDED" | "REPLACED"
export type EvidenceRole = "PRIMARY" | "SUPPLEMENT"
export type AnalysisType = "DEEPFAKE" | "INTEGRITY" | "COMPARE"

export type TechnicalMetadata = {
  // Common
  extractionStatus: string

  // Video
  width?: number
  height?: number
  durationSec?: number
  fps?: number
  codec?: string

  // Audio
  sampleRate?: number
  channels?: number

  // Image
  deviceInfo?: string
  capturedAt?: string
}

export type EvidenceInfo = {
  evidenceId: number
  fileName: string
  displayLabel?: string | null
  originalFileName?: string | null
  caseName: string
  caseId?: string
  fileSize: number
  uploadedAt: string
  mediaType: string // Added mediaType
  fileType?: string
  lifecycleStatus?: EvidenceLifecycleStatus
  role?: EvidenceRole
  replacementEvidenceId?: number | null
  excludedReason?: string | null
  /** @deprecated Phase 3 이후 hlsPlayback 사용. mock 호환용 */
  previewUrl?: string | null
  /** @deprecated Phase 3 이후 hlsPlayback 사용. mock 호환용 */
  videoUrl?: string | null
  /** @deprecated mock 호환용 */
  fileUrl?: string | null
  streamUrl?: string | null
  overlayVideoUrl?: string | null
  technicalMetadata: TechnicalMetadata
}

export type IntegrityInfo = {
  hashAlgorithm: string
  originalHash: string
  chainValid: boolean
  isChainValid: boolean
  verificationStatus: string
}

export type ModuleResult = {
  moduleName: string
  detected: boolean
  score: number
  deepfakeScore?: number | null
  confidence?: number | null
  modelName?: string | null
  modelVersion?: string | null
  /** 모델 개발 시점의 검증 성능 (예: "AUC 0.97 · FaceForensics++ (c23)"). 이번 분석 측정값과 무관 */
  modelBenchmark?: string | null
  details: string
  /** 해당 모듈이 실측으로 보고한 의심 구간. 없으면 UI에 구간을 표시하지 않는다. */
  affectedSegments?: SuspiciousSegment[] | null
  /** 위변조 모듈별 오버레이 MP4 (연동 시) */
  overlayVideoUrl?: string | null
}

export type FrameScore = {
  timeSec?: number | null
  timestamp?: string | null
  score: number
}

export type FaceBBox = {
  x: number
  y: number
  w: number
  h: number
}

export type TamperBBox = FaceBBox & {
  /** region confidence from TruFor map (0..1) */
  score?: number | null
}

export type FrameRisk = {
  frameIndex: number
  timestampSec: number
  /** 0.0 ~ 1.0 */
  riskScore: number
  /** TruFor localization → connected-component boxes (video pixel space) */
  bboxes?: TamperBBox[] | null
}

/** Late fusion per-face score (frameRisks is max-collapsed per frame). */
export type PerFrameFaceScore = {
  frameIndex: number
  faceIndex: number
  /** 0.0 ~ 1.0 */
  riskScore: number
  bbox?: FaceBBox | null
}

/** TimeSformer 클립 단위 위험도 */
export type ClipRisk = {
  clipIndex: number
  startFrameIndex: number
  endFrameIndex: number
  startTimeSec: number
  endTimeSec: number
  /** 0.0 ~ 1.0 */
  riskScore: number
}

/** GMFlow 연속 프레임쌍 단위 motion anomaly */
export type PairRisk = {
  pairIndex: number
  frameIndexA: number
  frameIndexB: number
  timestampSec: number
  /** 0.0 ~ 1.0 (영상 내 상대값) */
  riskScore: number
  /** GMFlow raw flow magnitude mean */
  motionMagnitude?: number | null
}

export type SuspiciousSegment = {
  startTime: number
  endTime: number
  /** 0.0 ~ 1.0 */
  maxRiskScore: number
  reason: string
}

/** AI 모듈 종류. cnn=Xception, temporal=TimeSformer(deepfake), optical=GMFlow, forgery_*=위변조 */
export type ModuleTimelineKind =
  | "cnn"
  | "temporal"
  | "optical"
  | "forgery_spatial"
  | "forgery_temporal"

/** 상세 UI용 모듈별 타임라인 묶음 (BE/FE 계약 확장) */
export type ModuleTimeline = {
  module: ModuleTimelineKind
  modelName: string
  modelVersion?: string | null
  /** 영상 전체 판정 점수 (0.0 ~ 1.0) */
  videoScore: number
  /** 판정 임계값 (0.0 ~ 1.0) */
  threshold: number
  detected: boolean
  frameRisks?: FrameRisk[] | null
  clipRisks?: ClipRisk[] | null
  pairRisks?: PairRisk[] | null
  suspiciousSegments?: SuspiciousSegment[] | null
  /** 모듈별 오버레이 MP4 (연동 시). 없으면 UI에서 준비중 표시 */
  overlayVideoUrl?: string | null
}

export type ModelScore = {
  moduleName: string
  detected?: boolean
  modelName: string
  score: number
  confidence?: number | null
  modelVersion?: string | null
}

export type RepresentativeFrame = {
  timeSec?: number | null
  timestamp?: string | null
  frameNumber?: number | null
  score?: number | null
  imageUrl?: string | null
  /** forgery / trufor / spatial 등 모듈 태그 (GPU visualization) */
  module?: string | null
  heatmapImageUrl?: string | null
}

export type AnalysisInfo = {
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED"
  /** 백엔드 queueStatus: WAITING / ANALYZING / COMPLETED / FAILED */
  queueStatus?: string | null
  /** 재현성 확인용 분석 실행 식별자 (예: ANL-20260703-1327) */
  analysisId?: string | null
  /** 위험 판정 임계값 (0.0 ~ 1.0). 없으면 UI 기본값 사용 */
  detectionThreshold?: number | null
  requestedAt: string | null
  completedAt: string | null
  riskScore: number | null
  confidenceScore: number | null
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | null
  summary: string
  errorCode?: string | null
  errorMessage?: string | null
  moduleResults: ModuleResult[]
  modelScores?: ModelScore[] | null
  evidenceItems?: string[] | null
  frameRisks?: FrameRisk[] | null
  suspiciousSegments?: SuspiciousSegment[] | null
  /** TimeSformer 클립 타임라인 */
  clipRisks?: ClipRisk[] | null
  /** GMFlow 프레임쌍 타임라인 */
  pairRisks?: PairRisk[] | null
  /** TimeSformer 클립 점수 기반 의심 구간 */
  temporalSuspiciousSegments?: SuspiciousSegment[] | null
  /** GMFlow optical motion 기반 의심 구간 */
  opticalSuspiciousSegments?: SuspiciousSegment[] | null
  /** 3모듈(cnn/temporal/optical) 통합 타임라인. 상세 차트용 */
  moduleTimelines?: ModuleTimeline[] | null
  frameScores?: FrameScore[] | null
  representativeFrames?: RepresentativeFrame[] | null
  /** @deprecated 모듈별 overlayVideoUrl·modelOverlayArtifacts 사용 권장. CNN(Xception) 레거시 */
  overlayVideoUrl?: string | null
  /** BE flat list (선택). moduleTimelines·moduleResults보다 우선하지 않음 */
  modelOverlayArtifacts?: ModelOverlayArtifact[] | null
  /** GPU visualization 산출물 (TruFor spatial MP4) */
  spatialOverlayVideoUrl?: string | null
  /** GPU visualization 산출물 (TimeSformer temporal MP4) */
  temporalOverlayVideoUrl?: string | null
  /** Face-level late fusion scores for multi-person detail / overlay */
  perFrameFaceScores?: PerFrameFaceScore[] | null
}

/** 모델별 시각화 오버레이 산출물 (BE/AI 연동 계약) */
export type ModelOverlayArtifact = {
  /** 예: deepfake:cnn, forgery:frame_edit */
  key: string
  category: "deepfake" | "forgery"
  label: string
  overlayVideoUrl?: string | null
  status?: "ready" | "pending" | "unsupported" | null
  description?: string | null
}

export type CocLog = {
  logId: number
  eventType: string
  userId: string
  description: string
  createdAt: string
  currentLogHash: string
}

export type SignatureInfo = {
  signatureStatus: string
  signatureAlgorithm: string
  signedAt: string
  signerCertificateSubject: string
  signatureValid: boolean
}

export type BlockchainInfo = {
  status: string
  anchorType: string
  subjectHash?: string | null
  transactionHash?: string | null
  anchoredAt?: string | null
  network?: string | null
  hashValid?: boolean | null
  certVerified?: boolean | null
  errorCode?: string | null
  verificationMessage?: string | null
  transactionExplorerUrl?: string | null
}

export type ReportInfo = {
  reportId: number
  reportFileName?: string | null
  verificationCode?: string | null
  reportHash?: string | null
  publicationStatus?: string | null
  createdAt?: string | null
}

export type EvidenceDetailData = {
  evidenceInfo: EvidenceInfo
  integrityInfo: IntegrityInfo
  signatureInfo?: SignatureInfo | null
  blockchainInfo?: BlockchainInfo | null
  analysisInfo: AnalysisInfo
  reportInfo?: ReportInfo | null
  cocLogs: CocLog[]
  hlsPlayback?: HlsPlayback | null
}

export type CaseEvidenceSummary = {
  evidenceId: number
  fileName: string
  displayLabel?: string | null
  originalFileName?: string | null
  mediaType: string
  analysisStatus: string
  analysisProgress?: number | null
  riskScore?: number | null
  confidenceScore?: number | null
  riskLevel?: "LOW" | "MEDIUM" | "HIGH" | null
  lifecycleStatus?: EvidenceLifecycleStatus
  role?: EvidenceRole
  replacementEvidenceId?: number | null
  excludedReason?: string | null
  /** @deprecated mock 호환용 */
  thumbnailUrl?: string | null
  /** @deprecated mock 호환용 */
  previewUrl?: string | null
  /** @deprecated mock 호환용 */
  videoUrl?: string | null
  /** @deprecated mock 호환용 */
  fileUrl?: string | null
  hlsStatus?: string | null
}

export type CaseDetailData = {
  caseId: string
  caseName: string
  status: string
  createdAt: string
  representativeEvidenceId?: number | null
  organizationId?: string | null
  createdBy?: string | null
  assigneeId?: string | null
  reviewerId?: string | null
  reviewStatus?: ReviewStatus | null
  reviewRequestedAt?: string | null
  reviewAssignedAt?: string | null
  reviewerComment?: string | null
  reviewRounds?: CaseReviewRound[]
  evidences: CaseEvidenceSummary[]
}

export type CaseReviewRound = {
  round: number
  decision: "APPROVED" | "REVISION"
  reviewerId?: string | null
  reviewerName?: string | null
  requestedAt?: string | null
  assignedAt?: string | null
  decidedAt: string
  reason?: string | null
}

export async function fetchEvidenceDetail(evidenceId: number): Promise<EvidenceDetailData> {
  if (features.mockApi) {
    return mockFetchEvidenceDetail(evidenceId)
  }

  return apiRequest<EvidenceDetailData>(`/api/v1/evidences/${evidenceId}/detail`, {
    stepUp: true,
  })
}

export async function downloadEvidenceReport(
  evidenceId: number,
  options: { preview?: boolean } = {}
): Promise<ApiDownloadResult> {
  if (features.mockApi) {
    const response = await fetch("/mock/report-sample.pdf")
    if (!response.ok) {
      throw new Error("샘플 PDF를 불러오지 못했습니다.")
    }
    return {
      blob: await response.blob(),
      reportHash: response.headers.get("X-Report-Hash"),
      contentDisposition: response.headers.get("Content-Disposition"),
    }
  }

  const params = new URLSearchParams()
  if (options.preview) params.set("preview", "true")
  const query = params.toString()

  return apiDownloadWithMetadata(`/api/v1/evidences/${evidenceId}/reports/pdf${query ? `?${query}` : ""}`)
}

export type EvidenceSecurityEventPayload = {
  eventType: "PRINT_SCREEN" | "SCREEN_CAPTURE_SHORTCUT"
  detail?: string
  mediaMode?: string
  pagePath?: string
  clientTimestamp?: string
}

export async function recordEvidenceSecurityEvent(
  evidenceId: number,
  payload: EvidenceSecurityEventPayload
): Promise<void> {
  if (features.mockApi) return

  await apiRequest<void>(`/api/v1/evidences/${evidenceId}/access-events`, {
    method: "POST",
    body: {
      eventType: "CAPTURE_ATTEMPT",
      source: payload.eventType,
      caseKey: payload.pagePath,
    },
  })
}

export async function fetchCaseDetail(caseId: string): Promise<CaseDetailData> {
  if (features.mockApi) {
    return mockFetchCaseDetail(caseId)
  }

  const caseKey = decodeRouteParam(caseId)
  const params = new URLSearchParams({ caseKey })
  return apiRequest<CaseDetailData>(`/api/v1/cases?${params}`)
}
