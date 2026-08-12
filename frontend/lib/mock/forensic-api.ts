import type { CaseSummary, CaseStatus } from "@/app/mypage/_types/case"
import type { AnalysisStatus, EvidenceStatsResponse, MediaMetadata, UploadResult } from "@/lib/evidence-api"
import type {
  AnalysisType,
  CaseDetailData,
  CaseEvidenceSummary,
  CaseReviewRound,
  EvidenceDetailData,
  EvidenceLifecycleStatus,
  EvidenceRole,
  ClipRisk,
  FrameRisk,
  FrameScore,
  ModelScore,
  ModuleResult,
  ModuleTimeline,
  PairRisk,
  RepresentativeFrame,
  SuspiciousSegment,
  TechnicalMetadata,
} from "@/lib/api/evidence-detail"
import type { UpdateUserProfilePayload, UserProfile } from "@/lib/api/user"
import { getSession } from "@/lib/auth"
import {
  canViewCase,
  getAppUserFromSession,
  getMockUserByRole,
  mockUsers,
  type AiResult,
  type ReviewStatus,
} from "@/lib/permissions"

const MOCK_STORAGE_KEY = "veriforensics-mock-evidences"
const MOCK_PROFILE_KEY = "veriforensics-mock-profile"
const MOCK_ANALYSIS_DURATION_MS = 7000
const MOCK_PENDING_MS = 1200
const MOCK_VIDEO_URL = "/mock/sample-video.mp4"
const MOCK_VIDEO_URLS_BY_EVIDENCE_ID: Record<number, string> = {
  2024062701: "/mock/kakao-original-clean.mp4",
  2024062702: "/mock/deepfake-generated-portrait.mp4",
  2024062703: "/mock/kakao-tampered-suspect.mp4",
}
const uploadedMediaUrls = new Map<number, string>()

function rememberUploadedMediaUrl(evidenceId: number, file: File) {
  if (typeof URL === "undefined" || typeof URL.createObjectURL !== "function") return

  const previousUrl = uploadedMediaUrls.get(evidenceId)
  if (previousUrl) {
    URL.revokeObjectURL(previousUrl)
  }

  uploadedMediaUrls.set(evidenceId, URL.createObjectURL(file))
}

function getUploadedMediaUrl(evidenceId: number) {
  return uploadedMediaUrls.get(evidenceId) ?? null
}

type MockEvidenceRecord = UploadResult & {
  caseId?: string | null
  displayLabel?: string | null
  originalFileName?: string | null
  mediaType: "IMAGE" | "VIDEO" | "AUDIO" | "UNKNOWN"
  lifecycleStatus?: EvidenceLifecycleStatus
  role?: EvidenceRole
  replacementEvidenceId?: number | null
  excludedReason?: string | null
  analysisType?: AnalysisType
  analysisRequestedAt?: string
  analysisCompletedAt?: string
  analysisProgress?: number
  riskScore?: number
  confidenceScore?: number
  riskLevel?: "LOW" | "MEDIUM" | "HIGH"
  summary?: string
  moduleResults?: ModuleResult[]
}

type MockCaseRecord = {
  caseId: string
  caseName: string
  createdAt: string
  representativeEvidenceId?: number | null
  organizationId?: string | null
  department?: string | null
  createdBy?: string | null
  assigneeId?: string | null
  reviewerId?: string | null
  reviewStatus?: ReviewStatus | null
  reviewerComment?: string | null
  aiResult?: AiResult | null
  reportIssueStatus?: import("@/lib/report-issue-status").ReportIssueStatus | null
  reviewRequestedAt?: string | null
  reviewAssignedAt?: string | null
  reviewRounds?: CaseReviewRound[]
}

type MockStore = {
  cases: MockCaseRecord[]
  evidences: MockEvidenceRecord[]
}

const MOCK_FRAME_SCORE_PATTERNS: Record<number, number[]> = {
  2024062701: [0.04, 0.06, 0.08, 0.07, 0.11, 0.09, 0.12, 0.10, 0.13, 0.09, 0.08, 0.07],
  2024062702: [0.34, 0.48, 0.63, 0.76, 0.84, 0.91, 0.88, 0.79, 0.69, 0.82, 0.90, 0.86],
  2024062703: [0.22, 0.31, 0.46, 0.58, 0.73, 0.87, 0.81, 0.68, 0.77, 0.89, 0.74, 0.62],
}

const MOCK_REPRESENTATIVE_FRAME_TIMES: Record<number, Array<{ timeSec: number; frameNumber: number; score: number }>> = {
  2024062701: [
    { timeSec: 2, frameNumber: 60, score: 0.12 },
    { timeSec: 8, frameNumber: 240, score: 0.13 },
    { timeSec: 16, frameNumber: 480, score: 0.11 },
  ],
  2024062702: [
    { timeSec: 0.8, frameNumber: 24, score: 0.84 },
    { timeSec: 1.5, frameNumber: 45, score: 0.91 },
    { timeSec: 2.2, frameNumber: 66, score: 0.88 },
  ],
  2024062703: [
    { timeSec: 1.5, frameNumber: 45, score: 0.87 },
    { timeSec: 3, frameNumber: 90, score: 0.81 },
    { timeSec: 4.2, frameNumber: 126, score: 0.89 },
  ],
}

const sampleCaseDetails: CaseDetailData[] = [
  {
    caseId: "mock-deepfake-pair-20260627",
    caseName: "딥페이크 정상/의심 비교 사건",
    status: "COMPLETED",
    reportIssueStatus: "NOT_REQUIRED",
    createdAt: "2026-06-27T12:42:37",
    representativeEvidenceId: 2024062702,
    evidences: [
      {
        evidenceId: 2024062701,
        fileName: "KakaoTalk_Video_2026-06-27-12-42-37.mp4",
        mediaType: "VIDEO",
        analysisStatus: "COMPLETED",
      },
      {
        evidenceId: 2024062702,
        fileName: "Create_a_realistic_portrait_of.mp4",
        mediaType: "VIDEO",
        analysisStatus: "COMPLETED",
      },
    ],
  },
  {
    caseId: "mock-tamper-single-20260627",
    caseName: "영상 위변조 의심 단건 사건",
    status: "COMPLETED",
    reportIssueStatus: "NOT_REQUIRED",
    createdAt: "2026-06-27T12:42:56",
    representativeEvidenceId: 2024062703,
    evidences: [
      {
        evidenceId: 2024062703,
        fileName: "KakaoTalk_Video_2026-06-27-12-42-56.mp4",
        mediaType: "VIDEO",
        analysisStatus: "COMPLETED",
      },
    ],
  },
  {
    caseId: "c4b37830-3653-4b23-b17b-5241b3783038",
    caseName: "가세연 녹취록 딥페이크 의혹 사건",
    status: "PROCESSING",
    reportIssueStatus: "NOT_REQUIRED",
    createdAt: "2026-06-18T14:30:00",
    representativeEvidenceId: 20240187,
    evidences: [
      {
        evidenceId: 20240187,
        fileName: "suspect_video_01.mp4",
        mediaType: "VIDEO",
        analysisStatus: "PROCESSING",
      },
      {
        evidenceId: 20240186,
        fileName: "voice_reference_01.wav",
        mediaType: "AUDIO",
        analysisStatus: "PENDING",
      },
    ],
  },
  {
    caseId: "a1f90210-8821-4c11-9a02-1100aa220011",
    caseName: "CCTV 영상 위변조 검증 요청",
    status: "COMPLETED",
    reportIssueStatus: "NOT_REQUIRED",
    createdAt: "2026-06-15T09:12:00",
    representativeEvidenceId: 20240185,
    evidences: [
      {
        evidenceId: 20240185,
        fileName: "cctv_entrance_02.mov",
        mediaType: "VIDEO",
        analysisStatus: "COMPLETED",
      },
      {
        evidenceId: 20240184,
        fileName: "cctv_backup_02.mp4",
        mediaType: "VIDEO",
        analysisStatus: "COMPLETED",
      },
      {
        evidenceId: 20240183,
        fileName: "parking_lot_angle_b.mp4",
        mediaType: "VIDEO",
        analysisStatus: "COMPLETED",
      },
    ],
  },
  {
    caseId: "b7e44321-9912-4d22-8c13-2211bb331122",
    caseName: "음성 메일 증거 분석",
    status: "PROCESSING",
    reportIssueStatus: "NOT_REQUIRED",
    createdAt: "2026-06-17T11:45:00",
    representativeEvidenceId: 20240182,
    evidences: [
      {
        evidenceId: 20240182,
        fileName: "voice_mail_0617.wav",
        mediaType: "AUDIO",
        analysisStatus: "PENDING",
      },
    ],
  },
  {
    caseId: "d9c55632-aa23-4e33-9d24-3322cc442233",
    caseName: "인터뷰 클립 진위 확인",
    status: "FAILED",
    reportIssueStatus: "NOT_REQUIRED",
    createdAt: "2026-06-10T16:20:00",
    representativeEvidenceId: 20240181,
    evidences: [
      {
        evidenceId: 20240181,
        fileName: "interview_clip_03.mp4",
        mediaType: "VIDEO",
        analysisStatus: "FAILED",
      },
      {
        evidenceId: 20240180,
        fileName: "interview_original_backup.mp4",
        mediaType: "VIDEO",
        analysisStatus: "COMPLETED",
      },
    ],
  },
]

const REVIEW_QUEUE_SEED_COUNT = 50
const REVIEW_QUEUE_DEPARTMENTS = [
  "서울청 사이버수사팀",
  "서울청 디지털포렌식팀",
  "부산청 사이버범죄수사대",
  "대구청 디지털증거분석팀",
  "인천청 지능범죄수사팀",
  "경기남부청 사이버수사대",
  "대전청 형사기동수사팀",
  "광주청 여성청소년수사팀",
]
const REVIEW_QUEUE_CASE_NAMES = [
  "메신저 영상 위변조 의심 사건",
  "CCTV 제출 영상 진위 확인",
  "음성 통화 녹취 조작 의혹",
  "SNS 게시 영상 딥페이크 검토",
  "블랙박스 영상 원본성 확인",
  "인터뷰 클립 합성 여부 검증",
  "피해 신고 첨부 영상 분석",
  "라이브 방송 캡처 조작 의혹",
  "협박 메시지 음성 파일 검토",
  "압수 휴대폰 영상 증거 분석",
]
const REVIEW_QUEUE_STATUS_PATTERN: ReviewStatus[] = [
  "REVIEW_REQUESTED",
  "REVIEW_ASSIGNED",
  "REVIEW_ASSIGNED",
  "REVIEW_COMPLETED",
  "REVIEW_REQUESTED",
  "REVIEW_ASSIGNED",
  "REPORT_APPROVED",
  "REVIEW_REQUESTED",
  "REVIEW_ASSIGNED",
  "REVIEW_COMPLETED",
]

const MOCK_CASE_ID_ALIASES: Record<string, string> = {
  mock: "mock-deepfake-pair-20260627",
  demo: "mock-deepfake-pair-20260627",
  "mock-detail": "mock-deepfake-pair-20260627",
  "mock-case": "mock-deepfake-pair-20260627",
  "mock-review": "mock-review-queue-049",
  "mock-reviewer": "mock-review-queue-049",
  "review-demo": "mock-review-queue-049",
  "목업": "mock-deepfake-pair-20260627",
  "목업-상세": "mock-deepfake-pair-20260627",
}

function resolveMockCaseId(caseId: string) {
  const trimmed = caseId.trim()
  return MOCK_CASE_ID_ALIASES[trimmed] ?? MOCK_CASE_ID_ALIASES[trimmed.toLowerCase()] ?? trimmed
}

function reviewersForDepartment(department: string) {
  return mockUsers.filter((user) => user.role === "REVIEWER" && user.department === department)
}

function seedReviewerId(department: string, index: number, reviewStatus: ReviewStatus) {
  if (
    reviewStatus !== "REVIEW_ASSIGNED" &&
    reviewStatus !== "REVIEW_COMPLETED" &&
    reviewStatus !== "REPORT_APPROVED"
  ) {
    return null
  }

  const primaryReviewer = getMockUserByRole("REVIEWER")
  if (department === primaryReviewer.department) {
    return primaryReviewer.id
  }

  const departmentReviewers = reviewersForDepartment(department)
  return departmentReviewers[index % Math.max(1, departmentReviewers.length)]?.id ?? null
}

function seedReviewRequestedAt(index: number) {
  const day = 27 - (index % 12)
  const hour = 9 + (index % 9)
  const minute = (index * 7) % 60
  return `2026-06-${String(day).padStart(2, "0")}T${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}:00`
}

function seedRiskScore(index: number) {
  return 22 + ((index * 13) % 73)
}

function seedAiResult(score: number): AiResult {
  if (score >= 80) return "위험"
  if (score >= 50) return "검토 필요"
  return "낮음"
}

function reviewQueueSeedCases(): MockCaseRecord[] {
  const investigator = getMockUserByRole("INVESTIGATOR")

  return Array.from({ length: REVIEW_QUEUE_SEED_COUNT }, (_, index) => {
    const sequence = index + 1
    const department = REVIEW_QUEUE_DEPARTMENTS[index % REVIEW_QUEUE_DEPARTMENTS.length]
    const reviewStatus = REVIEW_QUEUE_STATUS_PATTERN[index % REVIEW_QUEUE_STATUS_PATTERN.length]
    const requestedAt = seedReviewRequestedAt(index)
    const riskScore = seedRiskScore(index)

    return {
      caseId: `mock-review-queue-${String(sequence).padStart(3, "0")}`,
      caseName: `${REVIEW_QUEUE_CASE_NAMES[index % REVIEW_QUEUE_CASE_NAMES.length]} #${String(sequence).padStart(2, "0")}`,
      createdAt: requestedAt,
      representativeEvidenceId: 20300000 + sequence,
      organizationId: investigator.organizationId,
      department,
      createdBy: investigator.id,
      assigneeId: investigator.id,
      reviewerId: seedReviewerId(department, index, reviewStatus),
      reviewStatus,
      aiResult: seedAiResult(riskScore),
      reviewRequestedAt: requestedAt,
    }
  })
}

function findReviewQueueSeedCase(caseId: string) {
  const resolvedCaseId = resolveMockCaseId(caseId)
  return reviewQueueSeedCases().find(
    (item) =>
      item.caseId === resolvedCaseId ||
      item.caseName === resolvedCaseId ||
      caseKey(item.caseName) === resolvedCaseId
  )
}

function findReviewQueueSeedCaseByEvidenceId(evidenceId: number) {
  return reviewQueueSeedCases().find((item) => item.representativeEvidenceId === evidenceId)
}

function reviewQueueSeedEvidenceRecord(seedCase: MockCaseRecord): MockEvidenceRecord {
  const sequence = Number(seedCase.caseId.replace("mock-review-queue-", "")) || 1
  const riskScore = seedRiskScore(sequence - 1)
  const mediaType: MockEvidenceRecord["mediaType"] = "VIDEO"
  const extension = "mp4"
  const riskLevel: MockEvidenceRecord["riskLevel"] =
    riskScore >= 80 ? "HIGH" : riskScore >= 50 ? "MEDIUM" : "LOW"

  return {
    evidenceId: seedCase.representativeEvidenceId ?? 20300000 + sequence,
    fileName: `review_queue_${String(sequence).padStart(3, "0")}.${extension}`,
    caseId: seedCase.caseId,
    caseName: seedCase.caseName,
    displayLabel: "대표 증거",
    originalFileName: `review_queue_original_${String(sequence).padStart(3, "0")}.${extension}`,
    fileSize: 28_000_000 + sequence * 840_000,
    hashAlgorithm: "SHA-256",
    hashValue: String(sequence.toString(16)).padStart(64, "0"),
    metadata: { type: "video", codec: "h264", width: 1920, height: 1080, duration: 30 + sequence, fps: 29.97 },
    uploadedAt: seedCase.createdAt,
    mediaType,
    lifecycleStatus: "ACTIVE",
    role: "PRIMARY",
    replacementEvidenceId: null,
    excludedReason: null,
    analysisStatus: "COMPLETED",
    analysisRequestedAt: seedCase.createdAt,
    analysisCompletedAt: seedCase.createdAt,
    riskScore,
    confidenceScore: 82 + (sequence % 15),
    riskLevel,
    summary: analysisSummaryForType("DEEPFAKE", riskLevel),
    moduleResults: sampleModuleResults(riskScore),
  }
}

function delay(ms = 350) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function emptyStore(): MockStore {
  return { cases: [], evidences: [] }
}

function readStore(): MockStore {
  if (typeof window === "undefined") return emptyStore()

  const raw = localStorage.getItem(MOCK_STORAGE_KEY)
  if (!raw) return emptyStore()

  try {
    const parsed = JSON.parse(raw) as MockStore
    if (!parsed || !Array.isArray(parsed.evidences)) return emptyStore()
    const evidences = parsed.evidences.map((item) =>
      normalizeEvidenceRecord(updateAnalysisProgress(item))
    )
    return {
      cases: Array.isArray(parsed.cases) ? parsed.cases : inferCasesFromEvidences(evidences),
      evidences,
    }
  } catch {
    return emptyStore()
  }
}

function writeStore(store: MockStore) {
  if (typeof window === "undefined") return
  localStorage.setItem(MOCK_STORAGE_KEY, JSON.stringify({
    cases: store.cases,
    evidences: store.evidences.map(normalizeEvidenceRecord),
  }))
}

function normalizeEvidenceRecord(record: MockEvidenceRecord): MockEvidenceRecord {
  return {
    ...record,
    caseId: record.caseId ?? caseKey(record.caseName),
    displayLabel: record.displayLabel ?? null,
    originalFileName: record.originalFileName ?? record.fileName,
    lifecycleStatus: record.lifecycleStatus ?? "ACTIVE",
    role: record.role ?? "SUPPLEMENT",
    replacementEvidenceId: record.replacementEvidenceId ?? null,
    excludedReason: record.excludedReason ?? null,
  }
}

function defaultCaseAccessFields(): Pick<
  MockCaseRecord,
  | "organizationId"
  | "department"
  | "createdBy"
  | "assigneeId"
  | "reviewerId"
  | "reviewStatus"
  | "aiResult"
  | "reviewRequestedAt"
> {
  const currentUser = getAppUserFromSession(getSession()) ?? getMockUserByRole("INVESTIGATOR")

  return {
    organizationId: currentUser.organizationId,
    department: currentUser.department,
    createdBy: currentUser.id,
    assigneeId: currentUser.id,
    reviewerId: null,
    reviewStatus: "NONE",
    aiResult: null,
    reviewRequestedAt: null,
  }
}

function sampleCaseAccessFields(caseId: string): Pick<
  MockCaseRecord,
  | "organizationId"
  | "department"
  | "createdBy"
  | "assigneeId"
  | "reviewerId"
  | "reviewStatus"
  | "aiResult"
  | "reviewRequestedAt"
> {
  const investigator = getMockUserByRole("INVESTIGATOR")
  const reviewer = getMockUserByRole("REVIEWER")

  if (caseId === "mock-deepfake-pair-20260627") {
    return {
      organizationId: investigator.organizationId,
      department: investigator.department,
      createdBy: investigator.id,
      assigneeId: investigator.id,
      reviewerId: reviewer.id,
      reviewStatus: "REVIEW_ASSIGNED",
      aiResult: "위험",
      reviewRequestedAt: "2026-06-27T13:20:00",
    }
  }

  if (caseId === "mock-tamper-single-20260627") {
    return {
      organizationId: investigator.organizationId,
      department: investigator.department,
      createdBy: investigator.id,
      assigneeId: investigator.id,
      reviewerId: null,
      reviewStatus: "REVIEW_REQUESTED",
      aiResult: "위험",
      reviewRequestedAt: "2026-06-27T13:05:00",
    }
  }

  return {
    organizationId: investigator.organizationId,
    department: investigator.department,
    createdBy: investigator.id,
    assigneeId: investigator.id,
    reviewerId: null,
    reviewStatus: "NONE",
    aiResult: null,
    reviewRequestedAt: null,
  }
}

function inferCasesFromEvidences(evidences: MockEvidenceRecord[]): MockCaseRecord[] {
  const cases = new Map<string, MockCaseRecord>()

  for (const evidence of evidences) {
    const caseId = evidence.caseId ?? caseKey(evidence.caseName)
    if (cases.has(caseId)) continue

    cases.set(caseId, {
      caseId,
      caseName: evidence.caseName || "미분류 사건",
      createdAt: evidence.uploadedAt,
      representativeEvidenceId: evidence.evidenceId,
      ...defaultCaseAccessFields(),
    })
  }

  return Array.from(cases.values())
}

function sampleCaseRecord(sampleCase: CaseDetailData): MockCaseRecord {
  return {
    caseId: sampleCase.caseId,
    caseName: sampleCase.caseName,
    createdAt: sampleCase.createdAt,
    representativeEvidenceId:
      sampleCase.representativeEvidenceId ?? sampleCase.evidences[0]?.evidenceId ?? null,
    ...sampleCaseAccessFields(sampleCase.caseId),
  }
}

function findSampleCase(caseId: string) {
  const resolvedCaseId = resolveMockCaseId(caseId)
  return sampleCaseDetails.find(
    (item) =>
      item.caseId === resolvedCaseId ||
      item.caseName === resolvedCaseId ||
      caseKey(item.caseName) === resolvedCaseId
  )
}

function findCaseRecord(store: MockStore, caseId: string): MockCaseRecord | undefined {
  return store.cases.find((item) => item.caseId === caseId) ?? (
    findSampleCase(caseId) ? sampleCaseRecord(findSampleCase(caseId)!) : findReviewQueueSeedCase(caseId)
  )
}

function evidenceCaseId(record: MockEvidenceRecord) {
  return record.caseId ?? caseKey(record.caseName)
}

function displayLabelForIndex(index: number) {
  return `증거 ${index + 1}`
}

function nextEvidenceDisplayLabel(store: MockStore, caseId: string) {
  const storedCount = store.evidences.filter((item) => evidenceCaseId(item) === caseId).length
  const sampleCount = storedCount > 0 ? 0 : findSampleCase(caseId)?.evidences.length ?? 0
  return displayLabelForIndex(storedCount + sampleCount)
}

function createCaseId(caseName: string) {
  const trimmed = caseName.trim() || "미분류 사건"
  const slug = trimmed
    .toLowerCase()
    .replace(/[^a-z0-9가-힣]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 28) || "case"

  return `mock-case-${Date.now().toString(36)}-${slug}`
}

function normalizeCaseNameForCompare(caseName: string) {
  return caseName.trim().toLowerCase()
}

function materializeSampleCase(store: MockStore, caseId: string): MockStore {
  const sampleCase = findSampleCase(caseId)
  if (!sampleCase) return store
  if (store.evidences.some((item) => evidenceCaseId(item) === caseId)) return store

  return {
    cases: store.cases.some((item) => item.caseId === caseId)
      ? store.cases
      : [sampleCaseRecord(sampleCase), ...store.cases],
    evidences: [
      ...sampleCase.evidences.map((evidence, index) =>
        sampleEvidenceRecord(evidence, sampleCase, index)
      ),
      ...store.evidences,
    ],
  }
}

function materializeReviewQueueSeedCase(store: MockStore, caseId: string): MockStore {
  const seedCase = findReviewQueueSeedCase(caseId)
  if (!seedCase) return store
  const seedEvidence = reviewQueueSeedEvidenceRecord(seedCase)
  const hasSeedEvidence = store.evidences.some((item) => item.evidenceId === seedEvidence.evidenceId)

  return {
    cases: store.cases.some((item) => item.caseId === caseId)
      ? store.cases
      : [seedCase, ...store.cases],
    evidences: hasSeedEvidence
      ? store.evidences.map((item) => (item.evidenceId === seedEvidence.evidenceId ? seedEvidence : item))
      : [seedEvidence, ...store.evidences],
  }
}

function saveAfterProgressUpdate() {
  const store = readStore()
  writeStore(store)
  return store
}

function mediaTypeFromFile(file: File): MockEvidenceRecord["mediaType"] {
  if (file.type.startsWith("image/")) return "IMAGE"
  if (file.type.startsWith("video/")) return "VIDEO"
  if (file.type.startsWith("audio/")) return "AUDIO"

  const lower = file.name.toLowerCase()
  if (/\.(jpg|jpeg|png|gif|webp|bmp)$/.test(lower)) return "IMAGE"
  if (/\.(mp4|mov|avi|mkv|webm)$/.test(lower)) return "VIDEO"
  if (/\.(mp3|wav|aac|flac|ogg|m4a)$/.test(lower)) return "AUDIO"
  return "UNKNOWN"
}

function metadataFor(file: File, mediaType: MockEvidenceRecord["mediaType"]): MediaMetadata {
  const sizeFactor = Math.max(1, Math.round(file.size / 1024 / 1024))

  if (mediaType === "IMAGE") {
    return {
      type: "image",
      codec: file.type.split("/")[1] || "jpeg",
      width: 1280 + (sizeFactor % 4) * 160,
      height: 720 + (sizeFactor % 3) * 120,
    }
  }

  if (mediaType === "VIDEO") {
    return {
      type: "video",
      codec: file.type.split("/")[1] || "h264",
      width: 1920,
      height: 1080,
      duration: 18 + sizeFactor * 3,
      fps: 29.97,
    }
  }

  if (mediaType === "AUDIO") {
    return {
      type: "audio",
      codec: file.type.split("/")[1] || "aac",
      duration: 12 + sizeFactor * 4,
      sampleRate: 44100,
      channels: 2,
    }
  }

  return {
    type: "unknown",
  }
}

function technicalMetadataFor(record: MockEvidenceRecord): TechnicalMetadata {
  const metadata = typeof record.metadata === "string" ? null : record.metadata

  return {
    extractionStatus: "COMPLETED",
    width: metadata?.width,
    height: metadata?.height,
    durationSec: metadata?.duration,
    fps: metadata?.fps,
    codec: metadata?.codec,
    sampleRate: metadata?.sampleRate,
    channels: metadata?.channels,
    deviceInfo: record.mediaType === "IMAGE" ? "Mock forensic camera profile" : undefined,
    capturedAt: record.uploadedAt,
  }
}

async function sha256(file: File): Promise<string> {
  if (typeof crypto === "undefined" || !crypto.subtle) {
    return fallbackHash(file)
  }

  let buffer: ArrayBuffer
  try {
    buffer = await file.arrayBuffer()
  } catch (error) {
    if (isLocalFileReadError(error)) {
      return fallbackHash(file)
    }
    throw error
  }

  const digest = await crypto.subtle.digest("SHA-256", buffer)
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("")
}

function isLocalFileReadError(error: unknown) {
  if (!(error instanceof Error)) return false

  return (
    error.name === "NotReadableError" ||
    error.message.includes("requested file could not be read") ||
    error.message.includes("permission problems")
  )
}

function fallbackHash(file: File) {
  const source = `${file.name}:${file.size}:${file.lastModified}`
  let hash = 0

  for (let i = 0; i < source.length; i += 1) {
    hash = (hash << 5) - hash + source.charCodeAt(i)
    hash |= 0
  }

  return Math.abs(hash).toString(16).padStart(64, "0").slice(0, 64)
}

function nextEvidenceId(records: MockEvidenceRecord[]) {
  const maxId = records.reduce((max, item) => Math.max(max, item.evidenceId), 1000)
  return maxId + 1
}

function updateAnalysisProgress(record: MockEvidenceRecord): MockEvidenceRecord {
  if (!record.analysisRequestedAt) return record
  if (record.analysisStatus === "FAILED" || record.analysisStatus === "COMPLETED") {
    return record
  }

  const elapsed = Date.now() - new Date(record.analysisRequestedAt).getTime()
  if (elapsed >= MOCK_ANALYSIS_DURATION_MS) {
    return {
      ...record,
      analysisStatus: "COMPLETED",
      analysisProgress: 100,
      analysisCompletedAt: record.analysisCompletedAt ?? new Date().toISOString(),
      ...buildAnalysisResult(record),
    }
  }

  if (elapsed < MOCK_PENDING_MS) {
    return {
      ...record,
      analysisStatus: "PENDING",
      analysisProgress: 0,
    }
  }

  return {
    ...record,
    analysisStatus: "PROCESSING",
    analysisProgress: Math.min(
      96,
      Math.round(((elapsed - MOCK_PENDING_MS) / (MOCK_ANALYSIS_DURATION_MS - MOCK_PENDING_MS)) * 100)
    ),
  }
}

function buildAnalysisResult(record: MockEvidenceRecord) {
  const seed = record.hashValue
    .slice(0, 8)
    .split("")
    .reduce((sum, char) => sum + char.charCodeAt(0), 0)
  const riskScore = 28 + (seed % 58)
  const confidenceScore = 82 + (seed % 14)
  const riskLevel: MockEvidenceRecord["riskLevel"] =
    riskScore >= 70 ? "HIGH" : riskScore >= 45 ? "MEDIUM" : "LOW"

  return {
    riskScore,
    confidenceScore,
    riskLevel,
    summary: analysisSummaryForType(record.analysisType ?? "DEEPFAKE", riskLevel),
    moduleResults: moduleResultsFor(record.mediaType, riskScore),
  }
}

function analysisSummaryForType(
  analysisType: AnalysisType,
  riskLevel: MockEvidenceRecord["riskLevel"] = "LOW"
) {
  if (analysisType === "INTEGRITY") {
    return riskLevel === "HIGH"
      ? "해시, 메타데이터, 프레임 연속성 기준에서 원본성 훼손 가능성이 높게 관측되었습니다."
      : riskLevel === "MEDIUM"
        ? "일부 무결성 지표에서 추가 확인이 필요한 변화가 감지되었습니다."
        : "등록 해시와 무결성 기록이 안정적으로 일치합니다."
  }

  if (analysisType === "COMPARE") {
    return riskLevel === "HIGH"
      ? "기준 증거와 비교 대상 증거 사이의 시각적/메타데이터 차이가 크게 확인되었습니다."
      : riskLevel === "MEDIUM"
        ? "기준 증거와 일부 구간 차이가 있어 검토가 필요합니다."
        : "기준 증거와 비교 대상 증거의 핵심 지표가 대체로 일치합니다."
  }

  return riskLevel === "HIGH"
    ? "얼굴 경계부와 생성형 질감 패턴에서 딥페이크 가능성이 높은 흔적이 발견되었습니다."
    : riskLevel === "MEDIUM"
      ? "일부 프레임에서 합성 가능성이 관찰되어 추가 검토가 권장됩니다."
      : "주요 딥페이크 탐지 모듈에서 조작 정황이 낮게 판독되었습니다."
}

function moduleResultsFor(mediaType: MockEvidenceRecord["mediaType"], riskScore: number): ModuleResult[] {
  const normalized = riskScore / 100

  if (mediaType === "AUDIO") {
    return [
      {
        moduleName: "VOICE_SYNTHESIS_DETECTOR",
        detected: riskScore >= 55,
        score: normalized,
        details: "스펙트럼 연속성과 발화 구간의 합성 패턴을 비교했습니다.",
      },
      {
        moduleName: "AUDIO_EDIT_TRACE",
        detected: riskScore >= 65,
        score: Math.max(0.12, normalized - 0.16),
        details: "무음 구간, 노이즈 바닥, 압축 흔적의 불연속성을 확인했습니다.",
      },
    ]
  }

  if (mediaType === "IMAGE") {
    return [
      {
        moduleName: "FACE_SWAP_DETECTOR",
        detected: riskScore >= 52,
        score: normalized,
        details: "얼굴 윤곽, 피부 질감, 조명 방향의 일관성을 분석했습니다.",
      },
      {
        moduleName: "ELA_METADATA_CHECK",
        detected: riskScore >= 62,
        score: Math.max(0.18, normalized - 0.12),
        details: "EXIF 정보와 압축 노이즈 패턴을 교차 검증했습니다.",
      },
    ]
  }

  return [
    {
      moduleName: "TEMPORAL_FACE_ANALYSIS",
      detected: riskScore >= 52,
      score: normalized,
      details: "프레임 간 얼굴 랜드마크와 시선 움직임의 연속성을 분석했습니다.",
    },
    {
      moduleName: "FACE_SYNTHESIS_DETECTOR",
      detected: riskScore >= 55,
      score: Math.min(1, normalized + 0.03),
      details: "얼굴 경계와 피부 질감의 생성형 합성 패턴을 분석했습니다.",
    },
    {
      moduleName: "GAN_FINGERPRINT",
      detected: riskScore >= 60,
      score: Math.max(0.15, normalized - 0.05),
      details: "생성 모델 특유의 주파수 지문을 탐색했습니다.",
    },
    {
      moduleName: "OPTICAL_ARTIFACT",
      detected: riskScore >= 58,
      score: Math.max(0.18, normalized - 0.08),
      details: "조명 반사와 얼굴 음영 방향의 일관성을 확인했습니다.",
    },
    {
      moduleName: "COMPRESSION_TRACE_CHECK",
      detected: riskScore >= 64,
      score: Math.max(0.2, normalized - 0.1),
      details: "프레임별 압축 흔적과 메타데이터의 일관성을 검증했습니다.",
    },
    {
      moduleName: "METADATA_CONSISTENCY",
      detected: riskScore >= 68,
      score: Math.max(0.12, normalized - 0.14),
      details: "영상 메타데이터와 프레임 특성의 일치 여부를 확인했습니다.",
    },
  ]
}

function caseKey(caseName?: string | null) {
  const normalized = (caseName || "미분류 사건").trim() || "미분류 사건"
  let hash = 0

  for (let i = 0; i < normalized.length; i += 1) {
    hash = (hash << 5) - hash + normalized.charCodeAt(i)
    hash |= 0
  }

  return `case-${Math.abs(hash).toString(16).padStart(8, "0")}`
}

function statusPriority(status?: AnalysisStatus): number {
  if (status === "FAILED") return 4
  if (status === "PROCESSING") return 3
  if (status === "PENDING") return 2
  if (status === "COMPLETED") return 1
  return 0
}

function summarizeCaseStatus(records: MockEvidenceRecord[]): CaseStatus {
  if (records.length === 0) return "PENDING"

  const statuses = records.map((record) => record.analysisStatus)

  // 사건 목록은 처리중 / 실패 / 완료 3개 상태만 노출한다.
  // 증거 단위의 대기(PENDING)는 사건 단위에서 처리중으로 합친다.
  if (statuses.some((status) => status === "FAILED")) return "FAILED"
  if (statuses.some((status) => status === "PROCESSING" || status === "PENDING")) {
    return "PROCESSING"
  }
  return "COMPLETED"
}

function maxRiskScore(records: MockEvidenceRecord[]) {
  const scores = records
    .map((record) => record.riskScore)
    .filter((score): score is number => typeof score === "number")

  if (scores.length === 0) return null
  return Math.max(...scores)
}

function latestCompletedAnalysisAt(records: MockEvidenceRecord[]) {
  const completedTimes = records
    .map((record) => record.analysisCompletedAt)
    .filter((value): value is string => Boolean(value))
    .map((value) => new Date(value).getTime())
    .filter((time) => !Number.isNaN(time))

  if (completedTimes.length === 0) return null
  return new Date(Math.max(...completedTimes)).toISOString()
}

function reopenAssignedReview(caseRecord: MockCaseRecord): MockCaseRecord {
  if (!caseRecord.reviewerId) return caseRecord

  return {
    ...caseRecord,
    reviewStatus: "REVIEW_ASSIGNED",
    reviewerComment: null,
    reviewRequestedAt: new Date().toISOString(),
  }
}

function findRecord(evidenceId: number): MockEvidenceRecord {
  const store = saveAfterProgressUpdate()
  const record = store.evidences.find((item) => item.evidenceId === evidenceId)

  if (!record) {
    throw new Error("mock 증거 데이터를 찾을 수 없습니다.")
  }

  return record
}

export async function mockUploadEvidence(file: File, caseName?: string): Promise<UploadResult> {
  await delay()

  const store = readStore()
  const normalizedCaseName = caseName?.trim() || "미분류 사건"
  const targetCaseId = caseKey(normalizedCaseName)
  const cases = store.cases.some((item) => item.caseId === targetCaseId)
    ? store.cases
    : [
        {
          caseId: targetCaseId,
          caseName: normalizedCaseName,
          createdAt: new Date().toISOString(),
          representativeEvidenceId: null,
        },
        ...store.cases,
      ]
  const hashValue = await sha256(file)
  const existing = store.evidences.find(
    (item) => item.hashValue === hashValue && evidenceCaseId(item) === targetCaseId
  )

  if (existing) {
    const updated = {
      ...existing,
      caseId: targetCaseId,
      caseName: normalizedCaseName || existing.caseName,
    }
    writeStore({
      cases,
      evidences: store.evidences.map((item) =>
        item.evidenceId === existing.evidenceId ? updated : item
      ),
    })
    return updated
  }

  const mediaType = mediaTypeFromFile(file)
  const record: MockEvidenceRecord = {
    evidenceId: nextEvidenceId(store.evidences),
    fileName: file.name,
    caseId: targetCaseId,
    caseName: normalizedCaseName,
    displayLabel: nextEvidenceDisplayLabel({ ...store, cases }, targetCaseId),
    originalFileName: file.name,
    fileSize: file.size,
    hashAlgorithm: "SHA-256",
    hashValue,
    metadata: metadataFor(file, mediaType),
    uploadedAt: new Date().toISOString(),
    mediaType,
    lifecycleStatus: "ACTIVE",
    role: "SUPPLEMENT",
  }

  const nextCases = cases.map((item) => {
    if (item.caseId !== targetCaseId) return item

    const nextCase =
      item.representativeEvidenceId == null
        ? { ...item, representativeEvidenceId: record.evidenceId }
        : item
    return reopenAssignedReview(nextCase)
  })

  writeStore({
    cases: nextCases,
    evidences: [record, ...store.evidences],
  })

  return record
}

export async function mockCreateCase(caseName: string): Promise<CaseDetailData> {
  await delay(180)

  const trimmed = caseName.trim()
  if (!trimmed) throw new Error("사건명을 입력해 주세요.")

  const store = readStore()
  const normalizedName = normalizeCaseNameForCompare(trimmed)
  const duplicated =
    store.cases.some((item) => normalizeCaseNameForCompare(item.caseName) === normalizedName) ||
    sampleCaseDetails.some((item) => normalizeCaseNameForCompare(item.caseName) === normalizedName)

  if (duplicated) {
    throw new Error("이미 등록된 사건명입니다. 다른 사건명을 입력해 주세요.")
  }

  const record: MockCaseRecord = {
    caseId: createCaseId(trimmed),
    caseName: trimmed,
    createdAt: new Date().toISOString(),
    representativeEvidenceId: null,
    ...defaultCaseAccessFields(),
  }

  writeStore({
    ...store,
    cases: [record, ...store.cases],
  })

  return {
    caseId: record.caseId,
    caseName: record.caseName,
    status: "PENDING",
    reportIssueStatus: "NOT_REQUIRED",
    createdAt: record.createdAt,
    representativeEvidenceId: null,
    createdBy: record.createdBy ?? null,
    assigneeId: record.assigneeId ?? null,
    reviewerId: record.reviewerId ?? null,
    evidences: [],
  }
}

export async function mockUploadEvidenceToCase(caseId: string, file: File): Promise<UploadResult> {
  await delay()

  const store = materializeReviewQueueSeedCase(materializeSampleCase(readStore(), caseId), caseId)
  const targetCase = findCaseRecord(store, caseId)
  if (!targetCase) throw new Error("mock 사건 데이터를 찾을 수 없습니다.")

  const hashValue = await sha256(file)
  const mediaType = mediaTypeFromFile(file)
  const record: MockEvidenceRecord = {
    evidenceId: nextEvidenceId(store.evidences),
    fileName: file.name,
    caseId,
    caseName: targetCase.caseName,
    displayLabel: nextEvidenceDisplayLabel(store, caseId),
    originalFileName: file.name,
    fileSize: file.size,
    hashAlgorithm: "SHA-256",
    hashValue,
    metadata: metadataFor(file, mediaType),
    uploadedAt: new Date().toISOString(),
    mediaType,
    lifecycleStatus: "ACTIVE",
    role: "SUPPLEMENT",
  }

  const representativeRecord =
    targetCase.representativeEvidenceId == null
      ? null
      : store.evidences.find((item) => item.evidenceId === targetCase.representativeEvidenceId)
  const shouldSetRepresentative =
    targetCase.representativeEvidenceId == null ||
    (representativeRecord?.lifecycleStatus ?? "ACTIVE") !== "ACTIVE"

  const cases = store.cases.some((item) => item.caseId === caseId)
    ? store.cases.map((item) => {
        if (item.caseId !== caseId) return item

        const nextCase = shouldSetRepresentative
          ? { ...item, representativeEvidenceId: record.evidenceId }
          : item
        return reopenAssignedReview(nextCase)
      })
    : [reopenAssignedReview({ ...targetCase, representativeEvidenceId: record.evidenceId }), ...store.cases]

  writeStore({
    cases,
    evidences: [record, ...store.evidences],
  })
  rememberUploadedMediaUrl(record.evidenceId, file)

  return record
}

export async function mockMarkEvidenceExcluded(evidenceId: number, reason: string): Promise<void> {
  await delay(160)

  const store = readStore()
  let nextStore = store
  const target = store.evidences.find((item) => item.evidenceId === evidenceId)
  if (!target) {
    for (const sampleCase of sampleCaseDetails) {
      if (sampleCase.evidences.some((item) => item.evidenceId === evidenceId)) {
        nextStore = materializeSampleCase(store, sampleCase.caseId)
        break
      }
    }
  }

  const targetRecord = nextStore.evidences.find((record) => record.evidenceId === evidenceId)
  const targetCaseId = targetRecord ? evidenceCaseId(targetRecord) : null
  const fallbackRepresentative =
    targetCaseId == null
      ? null
      : nextStore.evidences.find(
          (record) =>
            evidenceCaseId(record) === targetCaseId &&
            record.evidenceId !== evidenceId &&
            (record.lifecycleStatus ?? "ACTIVE") === "ACTIVE"
        ) ?? null

  writeStore({
    ...nextStore,
    cases: nextStore.cases.map((record) =>
      targetCaseId != null &&
      record.caseId === targetCaseId &&
      record.representativeEvidenceId === evidenceId
        ? { ...record, representativeEvidenceId: fallbackRepresentative?.evidenceId ?? null }
        : record
    ),
    evidences: nextStore.evidences.map((record) =>
      record.evidenceId === evidenceId
        ? {
            ...record,
            lifecycleStatus: "EXCLUDED",
            role: "SUPPLEMENT",
            excludedReason: reason.trim() || "사용자 요청으로 사용 제외 처리되었습니다.",
          }
        : fallbackRepresentative && record.evidenceId === fallbackRepresentative.evidenceId
          ? { ...record, role: "PRIMARY" }
        : record
    ),
  })
}

export async function mockReplaceEvidence(
  caseId: string,
  oldEvidenceId: number,
  file: File,
  reason: string
): Promise<UploadResult> {
  await delay()

  const store = materializeReviewQueueSeedCase(materializeSampleCase(readStore(), caseId), caseId)
  const targetCase = findCaseRecord(store, caseId)
  if (!targetCase) throw new Error("mock 사건 데이터를 찾을 수 없습니다.")

  const hashValue = await sha256(file)
  const mediaType = mediaTypeFromFile(file)
  const record: MockEvidenceRecord = {
    evidenceId: nextEvidenceId(store.evidences),
    fileName: file.name,
    caseId,
    caseName: targetCase.caseName,
    displayLabel: nextEvidenceDisplayLabel(store, caseId),
    originalFileName: file.name,
    fileSize: file.size,
    hashAlgorithm: "SHA-256",
    hashValue,
    metadata: metadataFor(file, mediaType),
    uploadedAt: new Date().toISOString(),
    mediaType,
    lifecycleStatus: "ACTIVE",
    role: "SUPPLEMENT",
  }

  const cases = store.cases.some((item) => item.caseId === caseId)
    ? store.cases.map((item) =>
        item.caseId === caseId ? reopenAssignedReview(item) : item
      )
    : [reopenAssignedReview(targetCase), ...store.cases]

  writeStore({
    cases,
    evidences: [
      record,
      ...store.evidences.map((item) =>
        item.evidenceId === oldEvidenceId
          ? {
              ...item,
              lifecycleStatus: "REPLACED" as const,
              replacementEvidenceId: record.evidenceId,
              excludedReason: reason.trim() || "새 증거로 대체 등록되었습니다.",
            }
          : item
      ),
    ],
  })
  rememberUploadedMediaUrl(record.evidenceId, file)

  return record
}

export async function mockSetRepresentativeEvidence(caseId: string, evidenceId: number): Promise<void> {
  await delay(140)

  const store = materializeSampleCase(readStore(), caseId)
  const targetCase = findCaseRecord(store, caseId)
  if (!targetCase) throw new Error("mock 사건 데이터를 찾을 수 없습니다.")

  writeStore({
    cases: store.cases.some((item) => item.caseId === caseId)
      ? store.cases.map((item) =>
          item.caseId === caseId ? { ...item, representativeEvidenceId: evidenceId } : item
        )
      : [{ ...targetCase, representativeEvidenceId: evidenceId }, ...store.cases],
    evidences: store.evidences.map((item) =>
      evidenceCaseId(item) === caseId
        ? { ...item, role: item.evidenceId === evidenceId ? "PRIMARY" : "SUPPLEMENT" }
        : item
    ),
  })
}

export async function mockSetEvidenceRole(evidenceId: number, role: EvidenceRole): Promise<void> {
  await delay(120)

  const store = readStore()
  writeStore({
    ...store,
    evidences: store.evidences.map((item) =>
      item.evidenceId === evidenceId ? { ...item, role } : item
    ),
  })
}

export async function mockStartCaseAnalysis({
  caseId,
  analysisType,
  evidenceIds,
  baseEvidenceId,
  targetEvidenceId,
}: {
  caseId: string
  analysisType: AnalysisType
  evidenceIds: number[]
  baseEvidenceId?: number | null
  targetEvidenceId?: number | null
}) {
  const ids =
    analysisType === "COMPARE"
      ? [baseEvidenceId, targetEvidenceId].filter((id): id is number => typeof id === "number")
      : evidenceIds
  const store = materializeSampleCase(readStore(), caseId)
  writeStore(store)
  const targetCase = findCaseRecord(store, caseId)

  return mockStartEvidenceAnalysis(ids, targetCase?.caseName || "미분류 사건", analysisType)
}

export async function mockFetchEvidenceStats(): Promise<EvidenceStatsResponse> {
  await delay(180)

  const store = saveAfterProgressUpdate()
  let completedCount = 0
  let inProgressCount = 0
  let deepfakeDetectedCount = 0

  for (const item of store.evidences) {
    const status = item.analysisStatus
    if (status === "COMPLETED") {
      completedCount += 1
      if ((item.riskLevel ?? "LOW") !== "LOW") {
        deepfakeDetectedCount += 1
      }
    } else if (status === "PROCESSING" || status === "PENDING") {
      inProgressCount += 1
    }
  }

  return {
    totalAnalysisCount: store.evidences.length,
    deepfakeDetectedCount,
    completedCount,
    inProgressCount,
  }
}

export async function mockFetchAnalysisTrend(
  days = 7
): Promise<import("@/lib/evidence-api").AnalysisTrendResponse> {
  await delay(160)

  const store = saveAfterProgressUpdate()
  const today = new Date()
  const points: import("@/lib/evidence-api").AnalysisTrendPoint[] = []

  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const date = new Date(today)
    date.setDate(today.getDate() - offset)
    const key = date.toISOString().slice(0, 10)
    const completedCount = store.evidences.filter((item) => {
      if (item.analysisStatus !== "COMPLETED" || !item.analysisCompletedAt) {
        return false
      }
      return item.analysisCompletedAt.slice(0, 10) === key
    }).length

    points.push({ date: key, completedCount })
  }

  return { days, points }
}

export async function mockStartEvidenceAnalysis(
  evidenceIds: number[],
  caseName: string,
  analysisType: AnalysisType = "DEEPFAKE"
) {
  await delay(260)

  const now = new Date().toISOString()
  const requestedIds = new Set(evidenceIds)
  const store = readStore()
  const evidences = store.evidences.map((record) => {
    if (!requestedIds.has(record.evidenceId)) return record
    if ((record.lifecycleStatus ?? "ACTIVE") !== "ACTIVE") return record

    return {
      ...record,
      caseName,
      analysisRequestedAt: now,
      analysisCompletedAt: undefined,
      analysisStatus: "PENDING" as const,
      analysisType,
      analysisProgress: 0,
      riskScore: undefined,
      confidenceScore: undefined,
      riskLevel: undefined,
      summary: analysisSummaryForType(analysisType),
      moduleResults: undefined,
    }
  })

  writeStore({ ...store, evidences })

  return {
    success: true,
    message: "mock 분석 요청이 등록되었습니다.",
    caseName,
    startedCount: evidenceIds.length,
    evidenceIds,
  }
}

export async function mockFetchAnalysisStatus(evidenceId: number) {
  await delay(120)

  const record = findRecord(evidenceId)
  return {
    evidenceId,
    analysisRequestId: evidenceId + 5000,
    status: record.analysisStatus ?? "PENDING",
    progressPercent: record.analysisProgress ?? 0,
  }
}

export async function mockCancelAnalysis(evidenceId: number): Promise<void> {
  await delay(160)

  const store = readStore()
  writeStore({
    ...store,
    evidences: store.evidences.map((record) => {
      if (record.evidenceId !== evidenceId) return record

      return {
        ...record,
        analysisRequestedAt: undefined,
        analysisCompletedAt: undefined,
        analysisStatus: undefined,
        analysisProgress: undefined,
      }
    }),
  })
}

export async function mockAssignReviewerToCase(caseId: string, reviewerId: string): Promise<void> {
  await delay(180)

  const reviewer = mockUsers.find((user) => user.id === reviewerId && user.role === "REVIEWER")
  if (!reviewer) {
    throw new Error("검토자 계정을 찾을 수 없습니다.")
  }

  const store = materializeReviewQueueSeedCase(materializeSampleCase(readStore(), caseId), caseId)
  const targetCase = findCaseRecord(store, caseId)
  if (!targetCase) {
    throw new Error("검토 배정할 사건을 찾을 수 없습니다.")
  }

  const caseDepartment = targetCase.department?.trim().toLowerCase()
  const reviewerDepartment = reviewer.department.trim().toLowerCase()
  const caseOrganizationId = targetCase.organizationId?.trim().toLowerCase()
  const reviewerOrganizationId = reviewer.organizationId.trim().toLowerCase()
  if (
    !caseDepartment ||
    caseDepartment !== reviewerDepartment ||
    caseOrganizationId !== reviewerOrganizationId
  ) {
    throw new Error("사건 담당 분석관과 같은 기관/부서의 검토자만 배정할 수 있습니다.")
  }

  const cases = store.cases.some((item) => item.caseId === caseId)
    ? store.cases.map((item) =>
        item.caseId === caseId
          ? {
              ...item,
              reviewerId,
              reviewStatus: "REVIEW_ASSIGNED" as const,
              reviewRequestedAt: item.reviewRequestedAt ?? new Date().toISOString(),
              reviewAssignedAt: new Date().toISOString(),
            }
          : item
      )
    : [
        {
          ...targetCase,
          reviewerId,
          reviewStatus: "REVIEW_ASSIGNED" as const,
          reviewRequestedAt: targetCase.reviewRequestedAt ?? new Date().toISOString(),
          reviewAssignedAt: new Date().toISOString(),
        },
        ...store.cases,
      ]

  writeStore({
    ...store,
    cases,
  })
}

export async function mockRecordCaseReviewDecision(
  caseId: string,
  decision: "APPROVED" | "REVISION",
  memo?: string
): Promise<CaseDetailData> {
  await delay(180)

  const resolvedCaseId = resolveMockCaseId(caseId)
  const store = materializeReviewQueueSeedCase(materializeSampleCase(readStore(), resolvedCaseId), resolvedCaseId)
  const targetCase = findCaseRecord(store, resolvedCaseId)
  if (!targetCase) {
    throw new Error("검토 결정할 사건을 찾을 수 없습니다.")
  }

  const reviewStatus: ReviewStatus =
    decision === "APPROVED" ? "REPORT_APPROVED" : "REVIEW_SUPPLEMENT_REQUESTED"
  const reviewerComment = memo?.trim() || null
  const previousRounds = targetCase.reviewRounds ?? []
  const reviewerName =
    mockUsers.find((user) => user.id === targetCase.reviewerId)?.name ?? null
  const reviewRound: CaseReviewRound = {
    round: previousRounds.length + 1,
    decision,
    reviewerId: targetCase.reviewerId ?? null,
    reviewerName,
    requestedAt: targetCase.reviewRequestedAt ?? null,
    assignedAt: targetCase.reviewAssignedAt ?? null,
    decidedAt: new Date().toISOString(),
    reason: reviewerComment,
  }
  const cases = store.cases.some((item) => item.caseId === resolvedCaseId)
    ? store.cases.map((item) =>
        item.caseId === resolvedCaseId
          ? {
              ...item,
              reviewStatus,
              reportIssueStatus: decision === "APPROVED" ? "PENDING" as const : "NOT_REQUIRED" as const,
              reviewerComment,
              reviewRounds: [...previousRounds, reviewRound],
            }
          : item
      )
    : [
        {
          ...targetCase,
          reviewStatus,
          reportIssueStatus: decision === "APPROVED" ? "PENDING" as const : "NOT_REQUIRED" as const,
          reviewerComment,
          reviewRounds: [...previousRounds, reviewRound],
        },
        ...store.cases,
      ]

  writeStore({ ...store, cases })
  return mockFetchCaseDetail(resolvedCaseId)
}

export async function mockRequestCaseReview(
  caseId: string,
  _memo?: string
): Promise<CaseDetailData> {
  await delay(180)
  void _memo

  const resolvedCaseId = resolveMockCaseId(caseId)
  const store = materializeReviewQueueSeedCase(
    materializeSampleCase(readStore(), resolvedCaseId),
    resolvedCaseId
  )
  const targetCase = findCaseRecord(store, resolvedCaseId)
  if (!targetCase) {
    throw new Error("검토 요청할 사건을 찾을 수 없습니다.")
  }

  const reviewStatus: ReviewStatus = "REVIEW_REQUESTED"
  const reviewRequestedAt = new Date().toISOString()
  const cases = store.cases.some((item) => item.caseId === resolvedCaseId)
    ? store.cases.map((item) =>
        item.caseId === resolvedCaseId
          ? { ...item, reviewStatus, reviewRequestedAt, reviewAssignedAt: null }
          : item
      )
    : [
        { ...targetCase, reviewStatus, reviewRequestedAt, reviewAssignedAt: null },
        ...store.cases,
      ]

  writeStore({ ...store, cases })
  return mockFetchCaseDetail(resolvedCaseId)
}

export async function mockFetchMyAnalysisHistory(options?: {
  sort?: "newest" | "status"
  page?: number
  size?: number
  status?: "ALL" | CaseStatus
  q?: string
}) {
  await delay(220)

  const store = saveAfterProgressUpdate()
  const grouped = new Map<string, MockEvidenceRecord[]>()
  const caseMeta = new Map<string, MockCaseRecord>()

  for (const storeCase of store.cases) {
    caseMeta.set(storeCase.caseId, storeCase)
    grouped.set(storeCase.caseId, [])
  }

  for (const record of store.evidences) {
    const key = evidenceCaseId(record)
    grouped.set(key, [...(grouped.get(key) ?? []), record])
  }

  for (const sampleCase of sampleCaseDetails) {
    if (grouped.has(sampleCase.caseId)) continue
    caseMeta.set(sampleCase.caseId, sampleCaseRecord(sampleCase))
    grouped.set(
      sampleCase.caseId,
      sampleCase.evidences.map((evidence, index) =>
        sampleEvidenceRecord(evidence, sampleCase, index)
      )
    )
  }

  for (const seedCase of reviewQueueSeedCases()) {
    if (!caseMeta.has(seedCase.caseId)) {
      caseMeta.set(seedCase.caseId, seedCase)
    }

    const records = grouped.get(seedCase.caseId)
    if (!records || records.length === 0) {
      grouped.set(seedCase.caseId, [reviewQueueSeedEvidenceRecord(seedCase)])
    }
  }

  const content: CaseSummary[] = Array.from(grouped.entries()).map(([id, records]) => {
    const meta = caseMeta.get(id)
    const sorted = [...records].sort(
      (a, b) => new Date(a.uploadedAt).getTime() - new Date(b.uploadedAt).getTime()
    )
    const representativeEvidence =
      records.find((item) => item.evidenceId === meta?.representativeEvidenceId) ??
      sorted.find((item) => (item.lifecycleStatus ?? "ACTIVE") === "ACTIVE") ??
      sorted[0]

    const caseStatus = summarizeCaseStatus(records)
    const rawReviewStatus = meta?.reviewStatus ?? "NONE"
    const reviewStatus =
      caseStatus === "COMPLETED" && rawReviewStatus === "NONE"
        ? "REVIEW_REQUESTED"
        : rawReviewStatus
    const reviewRequestedAt =
      meta?.reviewRequestedAt ??
      (reviewStatus === "REVIEW_REQUESTED"
        ? latestCompletedAnalysisAt(records) ?? meta?.createdAt ?? sorted[0]?.uploadedAt ?? new Date().toISOString()
        : null)

    return {
      caseId: id,
      caseName: meta?.caseName || sorted[0]?.caseName || "미분류 사건",
      status: caseStatus,
      createdAt: meta?.createdAt ?? sorted[0]?.uploadedAt ?? new Date().toISOString(),
      evidenceCount: records.length,
      organizationId: meta?.organizationId ?? defaultCaseAccessFields().organizationId,
      department: meta?.department ?? defaultCaseAccessFields().department,
      createdBy: meta?.createdBy ?? defaultCaseAccessFields().createdBy,
      assigneeId: meta?.assigneeId ?? defaultCaseAccessFields().assigneeId,
      reviewerId: meta?.reviewerId ?? null,
      reviewStatus,
      aiResult: meta?.aiResult ?? null,
      reviewRequestedAt,
      representativeFileName: representativeEvidence?.fileName,
      representativeEvidenceId: representativeEvidence?.evidenceId ?? null,
      representativeEvidenceLabel: representativeEvidence?.displayLabel ?? null,
      riskScore: maxRiskScore(records),
    }
  })

  const currentUser = getAppUserFromSession(getSession())
  const visibleContent = currentUser
    ? content.filter((item) => canViewCase(currentUser, item))
    : content

  visibleContent.sort((a, b) => {
    if (options?.sort === "status") {
      const statusOrder: Record<CaseStatus, number> = {
        PROCESSING: 0,
        PENDING: 1,
        FAILED: 2,
        COMPLETED: 3,
      }
      const statusDiff = statusOrder[a.status] - statusOrder[b.status]
      if (statusDiff !== 0) return statusDiff
    }

    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  })

  const statusFilter =
    options?.status && options.status !== "ALL" ? options.status : null
  const keyword = options?.q?.trim().toLowerCase() ?? ""
  const filteredContent = visibleContent.filter((item) => {
    if (statusFilter && item.status !== statusFilter) return false
    if (!keyword) return true
    const caseName = item.caseName.toLowerCase()
    const label = (item.representativeEvidenceLabel ?? "").toLowerCase()
    const evidenceId = item.representativeEvidenceId
      ? `evd-${item.representativeEvidenceId}`
      : ""
    return (
      caseName.includes(keyword) ||
      label.includes(keyword) ||
      evidenceId.includes(keyword)
    )
  })

  const page = options?.page ?? 0
  const size = options?.size ?? 10
  const start = page * size

  return {
    content: filteredContent.slice(start, start + size),
    page,
    size,
    totalElements: filteredContent.length,
    totalPages: Math.max(1, Math.ceil(filteredContent.length / size)),
  }
}

function sampleModuleResults(risk: number): ModuleResult[] {
  const base = risk / 100
  const detected = base >= 0.5

  return [
    {
      moduleName: "FACE_SWAP_DETECTOR",
      detected,
      score: base,
      details: "얼굴 교체(GAN) 합성 흔적을 분석했습니다.",
    },
    {
      moduleName: "LIP_SYNC_ANALYZER",
      detected: base - 0.03 >= 0.5,
      score: Math.max(0, base - 0.03),
      details: "입 모양과 음성의 동기화 정도를 분석했습니다.",
    },
    {
      moduleName: "FREQUENCY_ARTIFACT",
      detected: base + 0.02 >= 0.5,
      score: Math.min(1, base + 0.02),
      details: "주파수 영역의 합성 아티팩트를 측정했습니다.",
    },
    {
      moduleName: "TEMPORAL_CONSISTENCY",
      detected: base - 0.06 >= 0.5,
      score: Math.max(0, base - 0.06),
      details: "프레임 간 표정·랜드마크 이동의 연속성을 확인했습니다.",
    },
    {
      moduleName: "GAN_FINGERPRINT",
      detected: base + 0.04 >= 0.5,
      score: Math.min(1, base + 0.04),
      details: "생성 모델 특유의 주파수 지문을 탐색했습니다.",
    },
    {
      moduleName: "COMPRESSION_TRACE_CHECK",
      detected: base - 0.1 >= 0.5,
      score: Math.max(0, base - 0.1),
      details: "프레임별 압축 흔적과 메타데이터의 일관성을 검증했습니다.",
    },
  ]
}

function detectedModuleResults(kind: "deepfake" | "tampered" | "clean"): ModuleResult[] {
  if (kind === "deepfake") {
    return [
      {
        moduleName: "FACE_SYNTHESIS_DETECTOR",
        detected: true,
        score: 0.91,
        details: "얼굴 경계와 피부 질감에서 생성형 합성 패턴이 강하게 감지되었습니다.",
      },
      {
        moduleName: "TEMPORAL_CONSISTENCY",
        detected: true,
        score: 0.84,
        details: "프레임 간 표정 변화와 랜드마크 이동이 자연 영상 대비 불연속적으로 나타났습니다.",
      },
      {
        moduleName: "OPTICAL_ARTIFACT",
        detected: true,
        score: 0.79,
        details: "조명 반사와 얼굴 음영 방향의 일관성이 낮아 딥페이크 가능성을 높였습니다.",
      },
      {
        moduleName: "GAN_FINGERPRINT",
        detected: true,
        score: 0.86,
        details: "생성 모델 특유의 주파수 지문(fingerprint)이 검출되었습니다.",
      },
      {
        moduleName: "LIP_SYNC_ANALYZER",
        detected: true,
        score: 0.71,
        details: "입 모양과 음성 동기화에서 부자연스러운 지연이 관측되었습니다.",
      },
      {
        moduleName: "EYE_BLINK_PATTERN",
        detected: false,
        score: 0.44,
        details: "눈 깜빡임 주기가 정상 범위에 가깝지만 일부 구간에서 불규칙했습니다.",
      },
      {
        moduleName: "HEAD_POSE_CONSISTENCY",
        detected: false,
        score: 0.29,
        details: "머리 자세와 얼굴 정렬의 일관성은 대체로 유지되었습니다.",
      },
    ]
  }

  if (kind === "tampered") {
    return [
      {
        moduleName: "EDIT_BOUNDARY_DETECTOR",
        detected: true,
        score: 0.88,
        details: "일부 구간에서 압축 블록과 경계선 패턴이 주변 프레임과 다르게 관측되었습니다.",
      },
      {
        moduleName: "TIMELINE_DISCONTINUITY",
        detected: true,
        score: 0.81,
        details: "프레임 진행 간 미세한 시간축 불연속성이 확인되어 편집 흔적으로 분류했습니다.",
      },
      {
        moduleName: "METADATA_CONSISTENCY",
        detected: true,
        score: 0.74,
        details: "영상 메타데이터와 프레임 특성 사이에 일부 불일치가 있어 위변조 의심으로 판단했습니다.",
      },
      {
        moduleName: "COPY_MOVE_FORGERY",
        detected: true,
        score: 0.77,
        details: "동일 패턴이 복제·이동된 흔적(copy-move)이 일부 영역에서 탐지되었습니다.",
      },
      {
        moduleName: "NOISE_RESIDUAL_ANALYSIS",
        detected: false,
        score: 0.48,
        details: "노이즈 잔차 분포가 일부 구간에서 주변과 달랐으나 임계값 이하였습니다.",
      },
      {
        moduleName: "SPLICE_DETECTOR",
        detected: false,
        score: 0.31,
        details: "이질적 영상 소스가 이어 붙은 스플라이싱 흔적은 뚜렷하지 않았습니다.",
      },
    ]
  }

  return [
    {
      moduleName: "DEEPFAKE_DETECTOR",
      detected: false,
      score: 0.08,
      details: "얼굴 합성, 표정 변조, 생성형 질감 흔적이 낮게 측정되었습니다.",
    },
    {
      moduleName: "TAMPER_ANALYZER",
      detected: false,
      score: 0.06,
      details: "프레임 경계, 압축 패턴, 시간축 흐름에서 편집 흔적이 발견되지 않았습니다.",
    },
    {
      moduleName: "OPTICAL_CONSISTENCY",
      detected: false,
      score: 0.05,
      details: "조명, 반사, 움직임 일관성이 정상 범위로 분석되었습니다.",
    },
    {
      moduleName: "GAN_FINGERPRINT",
      detected: false,
      score: 0.09,
      details: "생성 모델 특유의 주파수 지문이 검출되지 않았습니다.",
    },
    {
      moduleName: "LIP_SYNC_ANALYZER",
      detected: false,
      score: 0.07,
      details: "입 모양과 음성의 동기화가 자연스럽게 유지되었습니다.",
    },
    {
      moduleName: "METADATA_CONSISTENCY",
      detected: false,
      score: 0.04,
      details: "메타데이터와 프레임 특성이 서로 일치합니다.",
    },
  ]
}

function sampleVerdictOverride(evidenceId: number): Partial<MockEvidenceRecord> | null {
  if (evidenceId === 2024062701) {
    return {
      riskScore: 8,
      confidenceScore: 96,
      riskLevel: "LOW",
      summary: "AI 분석 결과 딥페이크 및 영상 위변조 흔적이 낮은 정상 영상으로 판정되었습니다.",
      moduleResults: detectedModuleResults("clean"),
    }
  }

  if (evidenceId === 2024062702) {
    return {
      riskScore: 92,
      confidenceScore: 94,
      riskLevel: "HIGH",
      summary: "AI 분석 결과 얼굴 합성 및 생성형 질감 패턴이 확인되어 딥페이크 의심 영상으로 판정되었습니다.",
      moduleResults: detectedModuleResults("deepfake"),
    }
  }

  if (evidenceId === 2024062703) {
    return {
      riskScore: 86,
      confidenceScore: 91,
      riskLevel: "HIGH",
      summary: "AI 분석 결과 프레임 경계와 시간축 불연속성이 확인되어 위변조 의심 영상으로 판정되었습니다.",
      moduleResults: detectedModuleResults("tampered"),
    }
  }

  return null
}

function buildMockFrameScores(record: MockEvidenceRecord): FrameScore[] {
  if (record.mediaType !== "VIDEO" || record.analysisStatus !== "COMPLETED") return []

  const pattern =
    MOCK_FRAME_SCORE_PATTERNS[record.evidenceId] ??
    (() => {
      const length = 14
      const base = record.riskScore == null ? 0.2 : record.riskScore / 100
      const peakIndex = Math.round(length * 0.62)
      return Array.from({ length }, (_, index) => {
        const wave = Math.sin(index * 0.9) * 0.14 + Math.sin(index * 0.42 + 0.6) * 0.1
        const peak = Math.exp(-((index - peakIndex) ** 2) / 8) * 0.22
        const calmEnds = index < 2 || index > length - 3 ? -0.1 : 0
        return Number(Math.max(0.05, Math.min(0.96, base * 0.8 + wave + peak + calmEnds)).toFixed(2))
      })
    })()

  return pattern.map((score, index) => ({
    timeSec: Number((index * 2.2).toFixed(1)),
    score,
  }))
}

function buildMockRepresentativeFrames(record: MockEvidenceRecord): RepresentativeFrame[] {
  if (record.mediaType !== "VIDEO" || record.analysisStatus !== "COMPLETED") return []

  const frames = MOCK_REPRESENTATIVE_FRAME_TIMES[record.evidenceId]
  if (!frames) return []

  return frames.map((frame, index) => {
    const key = `${record.evidenceId}-${String(index + 1).padStart(2, "0")}`

    return {
      ...frame,
      imageUrl: `/mock/frames/${key}.jpg`,
    }
  })
}

// 사건 상세 샘플의 증거를 증거 상세용 mock 레코드로 변환한다.
// (실제 업로드 store에 없는 샘플 증거를 클릭해도 상세 화면이 뜨도록)
function sampleEvidenceRecord(
  evidence: CaseEvidenceSummary,
  sampleCase: CaseDetailData,
  index = sampleCase.evidences.findIndex((item) => item.evidenceId === evidence.evidenceId)
): MockEvidenceRecord {
  const status = (evidence.analysisStatus as AnalysisStatus) ?? "PENDING"
  const completed = status === "COMPLETED"
  const tail = String(evidence.evidenceId).slice(-4)
  const risk = 6 + (evidence.evidenceId % 17)
  const override = completed ? sampleVerdictOverride(evidence.evidenceId) : null

  return {
    evidenceId: evidence.evidenceId,
    fileName: evidence.fileName,
    caseId: sampleCase.caseId,
    caseName: sampleCase.caseName,
    displayLabel: evidence.displayLabel ?? displayLabelForIndex(Math.max(0, index)),
    originalFileName: evidence.originalFileName ?? evidence.fileName,
    fileSize: 120_000_000 + (evidence.evidenceId % 60) * 1_000_000,
    hashAlgorithm: "SHA-256",
    hashValue: `a3f2b8c1d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0${tail}`,
    metadata: null,
    uploadedAt: sampleCase.createdAt,
    mediaType: (evidence.mediaType as MockEvidenceRecord["mediaType"]) || "UNKNOWN",
    lifecycleStatus: evidence.lifecycleStatus ?? "ACTIVE",
    role: evidence.role ?? (sampleCase.representativeEvidenceId === evidence.evidenceId ? "PRIMARY" : "SUPPLEMENT"),
    replacementEvidenceId: evidence.replacementEvidenceId ?? null,
    excludedReason: evidence.excludedReason ?? null,
    analysisStatus: status,
    analysisRequestedAt: status === "PENDING" ? undefined : sampleCase.createdAt,
    analysisCompletedAt:
      status === "COMPLETED" || status === "FAILED" ? sampleCase.createdAt : undefined,
    riskScore: completed ? risk : undefined,
    confidenceScore: completed ? 90 + (evidence.evidenceId % 9) : undefined,
    riskLevel: completed ? "LOW" : undefined,
    summary: completed
      ? "AI 분석 결과 위변조 가능성이 낮은 정상 영상으로 판정되었습니다."
      : undefined,
    moduleResults: completed ? sampleModuleResults(risk) : undefined,
    ...override,
  }
}

function offsetIsoTime(value: string | undefined, minutes: number) {
  const date = new Date(value ?? new Date().toISOString())
  if (Number.isNaN(date.getTime())) return value ?? new Date().toISOString()
  date.setMinutes(date.getMinutes() + minutes)
  return date.toISOString()
}

function buildCocLogs(record: MockEvidenceRecord): EvidenceDetailData["cocLogs"] {
  const requestedAt = record.analysisRequestedAt ?? record.uploadedAt
  const completedAt = record.analysisCompletedAt ?? offsetIsoTime(requestedAt, 2)
  const logs: EvidenceDetailData["cocLogs"] = [
    {
      logId: record.evidenceId * 10 + 1,
      eventType: "UPLOAD",
      userId: "mock-user",
      description: "증거 파일이 mock 저장소에 등록되었습니다.",
      createdAt: record.uploadedAt,
      currentLogHash: record.hashValue,
    },
    {
      logId: record.evidenceId * 10 + 2,
      eventType: "HASH_CREATED",
      userId: "mock-system",
      description: "원본 파일의 SHA-256 해시가 생성되었습니다.",
      createdAt: offsetIsoTime(record.uploadedAt, 0),
      currentLogHash: `${record.hashValue.slice(0, 52)}c0ffee`,
    },
    {
      logId: record.evidenceId * 10 + 3,
      eventType: "INTEGRITY_VERIFIED",
      userId: "mock-system",
      description: "해시 체인과 원본성 검증이 완료되었습니다.",
      createdAt: offsetIsoTime(record.uploadedAt, 1),
      currentLogHash: `${record.hashValue.slice(0, 52)}b10cc0`,
    },
  ]

  if (record.analysisRequestedAt) {
    logs.push(
      {
        logId: record.evidenceId * 10 + 4,
        eventType: "ANALYSIS_REQUESTED",
        userId: "mock-user",
        description: "AI 위변조 분석 요청이 mock 큐에 등록되었습니다.",
        createdAt: requestedAt,
        currentLogHash: `${record.hashValue.slice(0, 48)}feedface`,
      },
      {
        logId: record.evidenceId * 10 + 5,
        eventType: "FRAME_ANALYSIS_STARTED",
        userId: "ai-worker-01",
        description: "프레임 단위 위변조 탐지와 모델 분석이 시작되었습니다.",
        createdAt: offsetIsoTime(requestedAt, 1),
        currentLogHash: `${record.hashValue.slice(0, 50)}a11a1a`,
      }
    )
  }

  if (record.analysisStatus === "COMPLETED" || record.analysisStatus === "FAILED") {
    logs.push(
      {
        logId: record.evidenceId * 10 + 6,
        eventType: record.analysisStatus === "COMPLETED" ? "ANALYSIS_COMPLETED" : "ANALYSIS_FAILED",
        userId: "ai-worker-01",
        description:
          record.analysisStatus === "COMPLETED"
            ? "AI 분석 결과와 판정 요약이 생성되었습니다."
            : "AI 분석 처리 중 오류 상태가 기록되었습니다.",
        createdAt: completedAt,
        currentLogHash: `${record.hashValue.slice(0, 50)}d0c0de`,
      },
      {
        logId: record.evidenceId * 10 + 7,
        eventType: "REPORT_GENERATED",
        userId: "mock-system",
        description: "검증 보고서와 다운로드 가능한 PDF 기록이 생성되었습니다.",
        createdAt: offsetIsoTime(completedAt, 1),
        currentLogHash: `${record.hashValue.slice(0, 50)}5afe00`,
      }
    )
  }

  return logs
}


/**
 * 목업 모듈 결과에 실제 API 계약과 동일한 필드를 채운다.
 * - modelName / modelVersion: 모듈을 수행한 탐지 모델 식별 정보
 * - affectedSegments: detected 모듈에만, 프레임 위험도에서 임계값을 넘은 실측 구간을 배분
 */
const MOCK_MODULE_MODEL_INFO: Array<{
  keywords: string[]
  modelName: string
  modelVersion: string
  modelBenchmark: string | null
}> = [
  {
    keywords: ["face", "swap", "synthesis", "deepfake", "gan"],
    modelName: "Xception",
    modelVersion: "v2.4.1",
    modelBenchmark: "AUC 0.97 · FaceForensics++ (c23)",
  },
  {
    keywords: ["temporal", "tamper", "lip", "frame", "timeline"],
    modelName: "TimeSformer",
    modelVersion: "v1.9.0",
    modelBenchmark: "정확도 0.91 · 내부 시계열 검증 세트",
  },
  {
    keywords: ["optical", "motion", "flow"],
    modelName: "GMFlow",
    modelVersion: "v1.2.3",
    modelBenchmark: "광류 보조 신호 · 단독 판정에 사용하지 않음",
  },
  {
    keywords: ["compression", "metadata", "ela", "audio", "voice"],
    modelName: "ForenShield-Integrity",
    modelVersion: "v1.0.5",
    modelBenchmark: "정확도 0.89 · 내부 무결성 검증 세트",
  },
]

function mockModelInfoFor(moduleName: string) {
  const key = moduleName.toLowerCase()
  return (
    MOCK_MODULE_MODEL_INFO.find((entry) => entry.keywords.some((keyword) => key.includes(keyword))) ?? {
      modelName: "ForenShield-Detector",
      modelVersion: "v1.0.0",
      modelBenchmark: null,
    }
  )
}

function highRiskSegmentsFrom(frameScores: FrameScore[], threshold: number): SuspiciousSegment[] {
  const segments: SuspiciousSegment[] = []
  let current: { start: number; end: number; peak: number } | null = null

  for (const frame of frameScores) {
    const timeSec = frame.timeSec ?? null
    if (timeSec == null) continue
    const score = frame.score > 1 ? frame.score / 100 : frame.score
    if (score >= threshold) {
      if (current) {
        current.end = timeSec
        current.peak = Math.max(current.peak, score)
      } else {
        current = { start: timeSec, end: timeSec, peak: score }
      }
    } else if (current) {
      segments.push({ startTime: current.start, endTime: current.end, maxRiskScore: current.peak, reason: "" })
      current = null
    }
  }
  if (current) {
    segments.push({ startTime: current.start, endTime: current.end, maxRiskScore: current.peak, reason: "" })
  }
  return segments
}

function enrichModuleResults(
  modules: ModuleResult[],
  frameScores: FrameScore[],
  threshold: number
): ModuleResult[] {
  const segments = highRiskSegmentsFrom(frameScores, threshold)
  let detectedIndex = 0

  return modules.map((module) => {
    const modelInfo = mockModelInfoFor(module.moduleName)
    const enriched: ModuleResult = {
      ...module,
      modelName: module.modelName ?? modelInfo.modelName,
      modelVersion: module.modelVersion ?? modelInfo.modelVersion,
      modelBenchmark: module.modelBenchmark ?? modelInfo.modelBenchmark,
    }
    if (module.detected && segments.length > 0) {
      enriched.affectedSegments = [segments[detectedIndex % segments.length]]
      detectedIndex += 1
    }
    return enriched
  })
}

type MockTimelineData = {
  modelScores: ModelScore[]
  moduleTimelines: ModuleTimeline[]
  clipRisks: ClipRisk[]
  pairRisks: PairRisk[]
  temporalSuspiciousSegments: SuspiciousSegment[]
  opticalSuspiciousSegments: SuspiciousSegment[]
}

const EMPTY_TIMELINE_DATA: MockTimelineData = {
  modelScores: [],
  moduleTimelines: [],
  clipRisks: [],
  pairRisks: [],
  temporalSuspiciousSegments: [],
  opticalSuspiciousSegments: [],
}

/**
 * 실제 AI 계약(Late Fusion + cnn/temporal/optical)과 동일한 형태의 타임라인 목데이터.
 * 모든 riskScore는 0~1 raw 스케일이며, normalize-analysis.ts가 UI용으로 변환한다.
 */
function buildMockTimelineData(record: MockEvidenceRecord, frameScores: FrameScore[]): MockTimelineData {
  if (record.mediaType !== "VIDEO" || record.analysisStatus !== "COMPLETED" || frameScores.length === 0) {
    return EMPTY_TIMELINE_DATA
  }

  const overall = (record.riskScore ?? 0) / 100

  // 1) Xception (cnn): 프레임별 점수 그대로
  const frameRisks: FrameRisk[] = frameScores.map((frame, index) => ({
    frameIndex: index,
    timestampSec: frame.timeSec ?? index,
    riskScore: frame.score,
  }))
  const xceptionScore = Math.max(...frameScores.map((frame) => frame.score))
  const cnnSegments = highRiskSegmentsFrom(frameScores, 0.6).map((segment) => ({
    ...segment,
    reason: "프레임 fake 확률이 임계값을 초과했습니다.",
  }))

  // 2) TimeSformer (temporal): 프레임 3개를 한 클립으로 묶어 평균
  const clipSize = 3
  const clipRisks: ClipRisk[] = []
  for (let start = 0; start < frameScores.length; start += clipSize) {
    const endIndex = Math.min(start + clipSize - 1, frameScores.length - 1)
    const slice = frameScores.slice(start, endIndex + 1)
    const avg = slice.reduce((sum, frame) => sum + frame.score, 0) / slice.length
    clipRisks.push({
      clipIndex: clipRisks.length,
      startFrameIndex: start,
      endFrameIndex: endIndex,
      startTimeSec: frameScores[start].timeSec ?? start,
      endTimeSec: frameScores[endIndex].timeSec ?? endIndex,
      riskScore: Number(Math.min(1, avg * 0.92).toFixed(4)),
    })
  }
  const temporalScore = clipRisks.length > 0 ? Math.max(...clipRisks.map((clip) => clip.riskScore)) : 0
  const temporalSegments: SuspiciousSegment[] = clipRisks
    .filter((clip) => clip.riskScore >= 0.5)
    .map((clip) => ({
      startTime: clip.startTimeSec,
      endTime: clip.endTimeSec,
      maxRiskScore: clip.riskScore,
      reason: "클립 시계열 점수가 임계값을 초과했습니다.",
    }))

  // 3) GMFlow (optical): 연속 프레임쌍의 움직임 변화. 보조 신호라 videoScore는 낮게
  const pairRisks: PairRisk[] = []
  for (let index = 0; index < frameScores.length - 1; index += 1) {
    const motion = Math.abs(frameScores[index + 1].score - frameScores[index].score)
    pairRisks.push({
      pairIndex: index,
      frameIndexA: index,
      frameIndexB: index + 1,
      timestampSec: frameScores[index].timeSec ?? index,
      riskScore: Number(Math.min(1, motion * 2.4).toFixed(4)),
      motionMagnitude: Number((motion * 3).toFixed(3)),
    })
  }
  const opticalScore = Number(Math.max(0, Math.min(1, overall * 0.7)).toFixed(4))
  const opticalSegments: SuspiciousSegment[] = pairRisks
    .filter((pair) => pair.riskScore >= 0.5)
    .map((pair) => ({
      startTime: pair.timestampSec,
      endTime: pair.timestampSec + 0.1,
      maxRiskScore: pair.riskScore,
      reason: "프레임쌍 움직임 이상이 관찰되었습니다.",
    }))

  const modelScores: ModelScore[] = [
    {
      moduleName: "deepfake",
      modelName: "Late Fusion",
      modelVersion: "late-fusion/v1.0",
      score: overall,
      detected: overall >= 0.5,
    },
    {
      moduleName: "deepfake_cnn",
      modelName: "Xception",
      modelVersion: "xception/v2.4.1-ff++",
      score: xceptionScore,
      detected: xceptionScore >= 0.5,
    },
    {
      moduleName: "deepfake_temporal",
      modelName: "TimeSformer",
      modelVersion: "timesformer/v1.1.0-celeb1k",
      score: temporalScore,
      detected: temporalScore >= 0.5,
    },
    {
      moduleName: "deepfake_optical",
      modelName: "GMFlow",
      modelVersion: "gmflow/v1.2.3",
      score: opticalScore,
      detected: opticalScore >= 0.5,
    },
  ]

  const moduleTimelines: ModuleTimeline[] = [
    {
      module: "cnn",
      modelName: "Xception",
      modelVersion: "xception/v2.4.1-ff++",
      videoScore: xceptionScore,
      threshold: 0.5,
      detected: xceptionScore >= 0.5,
      frameRisks,
      clipRisks: [],
      pairRisks: [],
      suspiciousSegments: cnnSegments,
    },
    {
      module: "temporal",
      modelName: "TimeSformer",
      modelVersion: "timesformer/v1.1.0-celeb1k",
      videoScore: temporalScore,
      threshold: 0.5,
      detected: temporalScore >= 0.5,
      frameRisks: [],
      clipRisks,
      pairRisks: [],
      suspiciousSegments: temporalSegments,
    },
    {
      module: "optical",
      modelName: "GMFlow",
      modelVersion: "gmflow/v1.2.3",
      videoScore: opticalScore,
      threshold: 0.5,
      detected: opticalScore >= 0.5,
      frameRisks: [],
      clipRisks: [],
      pairRisks,
      suspiciousSegments: opticalSegments,
    },
  ]

  return {
    modelScores,
    moduleTimelines,
    clipRisks,
    pairRisks,
    temporalSuspiciousSegments: temporalSegments,
    opticalSuspiciousSegments: opticalSegments,
  }
}

function buildEvidenceDetail(
  record: MockEvidenceRecord,
  caseId: string
): EvidenceDetailData {
  const status = record.analysisStatus ?? "PENDING"
  const completed = status === "COMPLETED"
  const playableVideoUrl =
    record.mediaType === "VIDEO"
      ? getUploadedMediaUrl(record.evidenceId) ??
        MOCK_VIDEO_URLS_BY_EVIDENCE_ID[record.evidenceId] ??
        MOCK_VIDEO_URL
      : null
  const representativeFrames = buildMockRepresentativeFrames(record)
  const frameScores = buildMockFrameScores(record)
  const timelineData = buildMockTimelineData(record, frameScores)

  return {
    evidenceInfo: {
      evidenceId: record.evidenceId,
      fileName: record.fileName,
      displayLabel: record.displayLabel ?? null,
      originalFileName: record.originalFileName ?? record.fileName,
      caseName: record.caseName || "미분류 사건",
      caseId,
      fileSize: record.fileSize,
      uploadedAt: record.uploadedAt,
      mediaType: record.mediaType,
      fileType: record.mediaType,
      lifecycleStatus: record.lifecycleStatus ?? "ACTIVE",
      role: record.role ?? "SUPPLEMENT",
      replacementEvidenceId: record.replacementEvidenceId ?? null,
      excludedReason: record.excludedReason ?? null,
      previewUrl: playableVideoUrl,
      videoUrl: playableVideoUrl,
      fileUrl: playableVideoUrl,
      streamUrl: playableVideoUrl,
      technicalMetadata: technicalMetadataFor(record),
    },
    integrityInfo: {
      hashAlgorithm: record.hashAlgorithm,
      originalHash: record.hashValue,
      chainValid: true,
      isChainValid: true,
      verificationStatus: "VERIFIED",
    },
    signatureInfo: {
      signatureStatus: "SIGNED",
      signatureAlgorithm: "SHA256withRSA",
      signedAt: offsetIsoTime(record.uploadedAt, 0),
      signerCertificateSubject: "CN=ForenShield Evidence Authority, O=ForenShield, C=KR",
      signatureValid: true,
    },
    blockchainInfo: {
      status: "ANCHORED",
      anchorType: "EVIDENCE_HASH",
      subjectHash: record.hashValue,
      transactionHash: `0x${record.hashValue.slice(0, 40)}`,
      anchoredAt: offsetIsoTime(record.uploadedAt, 1),
      network: "ForenShield Private Chain",
    },
    analysisInfo: {
      status,
      analysisId: `ANL-${record.evidenceId}`,
      detectionThreshold: 0.6,
      requestedAt: record.analysisRequestedAt ?? null,
      completedAt: record.analysisCompletedAt ?? null,
      riskScore: completed ? record.riskScore ?? 0 : null,
      confidenceScore: completed ? record.confidenceScore ?? 0 : null,
      riskLevel: completed ? record.riskLevel ?? "LOW" : null,
      summary: completed ? record.summary ?? "" : "분석 큐에서 결과 생성을 준비 중입니다.",
      moduleResults: completed ? enrichModuleResults(record.moduleResults ?? [], frameScores, 0.6) : [],
      modelScores: timelineData.modelScores,
      clipRisks: timelineData.clipRisks,
      pairRisks: timelineData.pairRisks,
      temporalSuspiciousSegments: timelineData.temporalSuspiciousSegments,
      opticalSuspiciousSegments: timelineData.opticalSuspiciousSegments,
      moduleTimelines: timelineData.moduleTimelines,
      frameScores,
      representativeFrames,
    },
    cocLogs: buildCocLogs(record),
    hlsPlayback:
      record.mediaType === "VIDEO"
        ? {
            manifestPath: `/api/v1/evidences/${record.evidenceId}/hls/master.m3u8`,
            hlsStatus: "READY",
            streamToken: `mock-stream-${record.evidenceId}`,
            expiresIn: 900,
          }
        : null,
  }
}

export async function mockFetchEvidenceDetail(evidenceId: number): Promise<EvidenceDetailData> {
  await delay(220)

  const store = saveAfterProgressUpdate()
  const stored = store.evidences.find((item) => item.evidenceId === evidenceId)
  if (stored) {
    return buildEvidenceDetail(stored, evidenceCaseId(stored))
  }

  for (const sampleCase of sampleCaseDetails) {
    const sampleEvidence = sampleCase.evidences.find(
      (item) => item.evidenceId === evidenceId
    )
    if (sampleEvidence) {
      return buildEvidenceDetail(
        sampleEvidenceRecord(sampleEvidence, sampleCase),
        sampleCase.caseId
      )
    }
  }

  const seedCase = findReviewQueueSeedCaseByEvidenceId(evidenceId)
  if (seedCase) {
    const seedEvidence = reviewQueueSeedEvidenceRecord(seedCase)
    return buildEvidenceDetail(seedEvidence, seedCase.caseId)
  }

  throw new Error("mock 증거 데이터를 찾을 수 없습니다.")
}

export async function mockFetchCaseDetail(caseId: string): Promise<CaseDetailData> {
  await delay(220)

  const resolvedCaseId = resolveMockCaseId(caseId)
  const store = materializeReviewQueueSeedCase(saveAfterProgressUpdate(), resolvedCaseId)
  const records = store.evidences.filter((record) => evidenceCaseId(record) === resolvedCaseId)
  const storedCase = store.cases.find((item) => item.caseId === resolvedCaseId)

  if (records.length === 0) {
    const sampleCase = findSampleCase(resolvedCaseId)
    if (sampleCase) {
      const sampleRecords = sampleCase.evidences.map((evidence, index) =>
        sampleEvidenceRecord(evidence, sampleCase, index)
      )
      return {
        ...sampleCase,
        ...sampleCaseAccessFields(sampleCase.caseId),
        evidences: sampleRecords.map((record, index) => mapRecordToCaseEvidence(record, index)),
      }
    }
    if (storedCase) {
      return {
        caseId: resolvedCaseId,
        caseName: storedCase.caseName,
        status: "PENDING",
        createdAt: storedCase.createdAt,
        representativeEvidenceId: storedCase.representativeEvidenceId ?? null,
        createdBy: storedCase.createdBy ?? defaultCaseAccessFields().createdBy,
        assigneeId: storedCase.assigneeId ?? defaultCaseAccessFields().assigneeId,
        reviewerId: storedCase.reviewerId ?? null,
        reviewStatus: storedCase.reviewStatus ?? "NONE",
        reviewRequestedAt: storedCase.reviewRequestedAt ?? null,
        reviewAssignedAt: storedCase.reviewAssignedAt ?? null,
        reviewerComment: storedCase.reviewerComment ?? null,
        reviewRounds: storedCase.reviewRounds ?? [],
        reportIssueStatus: storedCase.reportIssueStatus ?? "NOT_REQUIRED",
        evidences: [],
      }
    }
    throw new Error("mock 사건 데이터를 찾을 수 없습니다.")
  }

  const sorted = [...records].sort(
    (a, b) => new Date(a.uploadedAt).getTime() - new Date(b.uploadedAt).getTime()
  )
  const evidences: CaseEvidenceSummary[] = sorted
    .sort((a, b) => statusPriority(b.analysisStatus) - statusPriority(a.analysisStatus))
    .map((record, index) => mapRecordToCaseEvidence(record, index))
  const caseRecord = findCaseRecord(store, caseId)

  return {
    caseId: resolvedCaseId,
    caseName: storedCase?.caseName || sorted[0]?.caseName || "미분류 사건",
    status: summarizeCaseStatus(records),
    createdAt: storedCase?.createdAt ?? sorted[0]?.uploadedAt ?? new Date().toISOString(),
    representativeEvidenceId:
      storedCase?.representativeEvidenceId ??
      sorted.find((item) => (item.lifecycleStatus ?? "ACTIVE") === "ACTIVE")?.evidenceId ??
      sorted[0]?.evidenceId ??
      null,
    createdBy: caseRecord?.createdBy ?? defaultCaseAccessFields().createdBy,
    assigneeId: caseRecord?.assigneeId ?? defaultCaseAccessFields().assigneeId,
    reviewerId: caseRecord?.reviewerId ?? null,
    reviewStatus: caseRecord?.reviewStatus ?? "NONE",
    reviewRequestedAt: caseRecord?.reviewRequestedAt ?? null,
    reviewAssignedAt: caseRecord?.reviewAssignedAt ?? null,
    reviewerComment: caseRecord?.reviewerComment ?? null,
    reviewRounds: caseRecord?.reviewRounds ?? [],
    reportIssueStatus: caseRecord?.reportIssueStatus ?? "NOT_REQUIRED",
    evidences,
  }
}

function mapRecordToCaseEvidence(record: MockEvidenceRecord, index: number): CaseEvidenceSummary {
  const videoUrl =
    record.mediaType === "VIDEO"
      ? getUploadedMediaUrl(record.evidenceId) ??
        MOCK_VIDEO_URLS_BY_EVIDENCE_ID[record.evidenceId] ??
        MOCK_VIDEO_URL
      : null

  return {
    evidenceId: record.evidenceId,
    fileName: record.fileName,
    displayLabel: record.displayLabel ?? displayLabelForIndex(index),
    originalFileName: record.originalFileName ?? record.fileName,
    mediaType: record.mediaType,
    analysisStatus: record.analysisStatus ?? "PENDING",
    analysisProgress: record.analysisProgress ?? null,
    riskScore: record.riskScore ?? null,
    confidenceScore: record.confidenceScore ?? null,
    riskLevel: record.riskLevel ?? null,
    lifecycleStatus: record.lifecycleStatus ?? "ACTIVE",
    role: record.role ?? "SUPPLEMENT",
    replacementEvidenceId: record.replacementEvidenceId ?? null,
    excludedReason: record.excludedReason ?? null,
    previewUrl: videoUrl,
    videoUrl,
    fileUrl: videoUrl,
    hlsStatus: record.mediaType === "VIDEO" ? "READY" : null,
  }
}

function defaultProfile(): UserProfile {
  const session = getSession()
  const appUser = getAppUserFromSession(session)
  const loginId = session?.loginId ?? "hong_gildong"

  return {
    userId: Number(session?.userId) || 1001,
    loginId,
    email: `${loginId}@local.dev`,
    name: appUser?.name ?? session?.name ?? "홍길동",
    department: appUser?.department ?? "디지털포렌식센터",
    role: appUser?.role ?? "INVESTIGATOR",
    status: "APPROVED",
    darkMode: false,
    createdAt: "2026-01-02T09:00:00",
  }
}

function readProfile(): UserProfile {
  if (typeof window === "undefined") return defaultProfile()

  const raw = localStorage.getItem(MOCK_PROFILE_KEY)
  if (!raw) return defaultProfile()

  try {
    const defaults = defaultProfile()
    const saved = JSON.parse(raw) as Partial<UserProfile>
    return {
      ...defaults,
      ...saved,
      email: saved.email?.trim() || defaults.email,
      department: saved.department?.trim() || defaults.department,
    }
  } catch {
    return defaultProfile()
  }
}

function writeProfile(profile: UserProfile) {
  if (typeof window === "undefined") return
  localStorage.setItem(MOCK_PROFILE_KEY, JSON.stringify(profile))
}

export async function mockFetchMyProfile(): Promise<UserProfile> {
  await delay(200)
  return readProfile()
}

export async function mockUpdateMyProfile(
  payload: UpdateUserProfilePayload
): Promise<UserProfile> {
  await delay(260)

  const next: UserProfile = {
    ...readProfile(),
    loginId: payload.loginId,
  }

  writeProfile(next)
  return next
}
