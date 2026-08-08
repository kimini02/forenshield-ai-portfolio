"use client"

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type Dispatch,
  type ReactNode,
  type RefObject,
  type SetStateAction,
} from "react"
import Link from "next/link"
import { useParams, useRouter, useSearchParams } from "next/navigation"
import {
  CategoryScale,
  Chart as ChartJS,
  Filler,
  Legend,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
  type ChartData,
  type ChartOptions,
  type Plugin,
} from "chart.js"
import { Line } from "react-chartjs-2"
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  Check,
  CheckCircle2,
  ChevronRight,
  Copy,
  Download,
  FileSearch,
  FileVideo,
  Flag,
  GitCompare,
  Home,
  Loader2,
  Maximize2,
  MessageSquareText,
  MoreVertical,
  Pause,
  Pencil,
  Play,
  Plus,
  ShieldCheck,
  Square,
  Trash2,
  UserRound,
  Volume2,
  VolumeX,
  X,
} from "lucide-react"

import { QualityWarningDialog } from "@/components/quality-warning-dialog"
import {
  ProtectedEvidencePlayer,
  type ProtectedSecurityEvent,
} from "@/components/protected-evidence-player"
import { StepUpGateDialogs } from "@/components/step-up-gate"
import { ReadinessCheckOverlay } from "@/components/readiness-check-overlay"
import { ReadinessMetricSection } from "@/components/readiness-metric-section"
import { ReadinessBadge } from "@/components/readiness-badge"
import { useAnalyzeWithReadiness } from "@/hooks/use-analyze-with-readiness"
import { isStepUpCancelledError, useStepUpGate } from "@/hooks/use-step-up-gate"
import { CaseHero } from "./_components/case-hero"
import {
  ReviewDecisionDialog,
  ReviewRequestDialog,
  ReviewerAssignmentDialog,
} from "./_components/review-workflow-dialogs"
import { DeepfakeV2Tab } from "./_components/deepfake-v2-tab"
import { ResultEvidenceMedia } from "./_components/result-evidence-media"
import { ResultFrameAnalysis } from "./_components/result-frame-analysis"
import { EvidenceSummaryCard } from "./_components/evidence-summary-card"
import { IntegrityTab } from "./_components/integrity-tab"
import { MetadataReportTab } from "./_components/metadata-report-tab"
import { ReportExportDialog } from "./_components/report-export-dialog"
import { SummaryTab } from "./_components/summary-tab"
import {
  buildProgressSteps,
  getCaseRiskClassName,
  getCaseRiskTone,
  getDisplayRiskLabel,
} from "./_lib/evidence-display"
import { getXceptionFrameScores } from "./_lib/module-timelines"
import {
  buildForgeryRepresentativeFrames,
  buildForgeryResultTabSignals,
  DEFAULT_FORGERY_THRESHOLDS,
  formatForgeryDualScoreSub,
  forgeryHighRiskGalleryCopy,
  FORGERY_SPATIAL_MODULE,
  getForgeryPriorityReviewRange,
  getForgeryScoreSummary,
} from "./_lib/forgery-ui"
import { VideoSeekThumbnail } from "./_components/video-seek-thumbnail"
import { SiteFooter } from "@/components/site-footer"
import { SiteHeader } from "@/components/site-header"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { AnalysisStatus } from "@/lib/analysis-status"
import {
  buildMethodologyInfo,
  buildRiskSignals,
  buildSummaryActions,
  formatModuleLabel,
  formatScoreOutOf100,
  getDetectionModules,
  getDetectionThreshold,
  getPriorityReviewRange,
  normalizeResultValue,
  type UiMethodologyModel,
  type UiRiskSignal,
  type UiSummaryAction,
} from "@/lib/api/analysis-result-ui"
import {
  type AnalysisModelSnapshot,
  type AnalysisModuleSnapshot,
  type BlockchainAnchorRecord,
  type BlockchainAnchorStatusResponse,
  fetchEvidenceBlockchainStatus,
  parseAnalysisModelJson,
  parseAnalysisModulesJson,
  parseOffchainRef,
} from "@/lib/api/blockchain"
import {
  type AnalysisType,
  fetchCaseDetail,
  recordEvidenceSecurityEvent,
  type CaseDetailData,
  type CaseEvidenceSummary,
  type EvidenceDetailData,
  type FrameScore,
  type RepresentativeFrame,
} from "@/lib/api/evidence-detail"
import {
  assignAdminCaseReviewer,
  fetchAdminReviewers,
  type AdminReviewer,
} from "@/lib/api/admin"
import {
  cancelCaseAnalysis,
  markEvidenceExcluded,
  recordCaseReviewDecision,
  requestCaseReview,
  setRepresentativeEvidence,
  startCaseAnalysis,
  updateCaseName,
  uploadEvidenceToCase,
} from "@/lib/api/case-workflow"
import {
  fetchAnalysisStatus,
  fetchEvidenceReadiness,
  type EvidenceReadinessResponse,
} from "@/lib/evidence-api"
import { ApiError } from "@/lib/api/client"
import { getApiErrorMessage, isUnauthorizedError } from "@/lib/api/errors"
import { getSession, isReviewerSession, type AuthSession } from "@/lib/auth"
import { verifyRegisteredCompare } from "@/lib/api/compare"
import { verifyEvidenceIntegrity } from "@/lib/api/evidence-verification"
import {
  getLatestCompareResultSummary,
  saveCompareResultSummary,
  type StoredCompareResultSummary,
} from "@/lib/compare-history"
import {
  canAssignReviewer,
  getAppUserFromSession,
  isAssignedReviewer,
  isCaseOwner,
  mockUsers,
  roleLabelMap,
  type AppUser,
} from "@/lib/permissions"
import { getAnalysisStatusLabel } from "@/lib/status-labels"
import { buildCaseDetailPath, decodeRouteParam } from "@/lib/route-params"
import { normalizeAnalysisStatus, normalizeEvidenceDetailForUi, normalizeScore } from "@/lib/api/normalize-analysis"
import { addAppNotification } from "@/lib/notifications"
import { readinessTargetFromCaseEvidence } from "@/lib/readiness"
import { cn } from "@/lib/utils"
import { formatDateTime, formatDateTimeWithSeconds, formatDuration } from "@/lib/formatters"

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Filler, Tooltip, Legend)

type ResultTab = "summary" | "deepfake" | "forgery" | "frames" | "models"
type WorkflowMessage = { type: "success" | "error" | "info"; text: string }
type AnalysisProgressOverrides = Record<number, { status: AnalysisStatus; progress: number }>

const RESULT_TABS: Array<{ value: ResultTab; label: string }> = [
  { value: "summary", label: "분석 요약" },
  { value: "deepfake", label: "딥페이크 탐지" },
  { value: "forgery", label: "위변조 탐지" },
  { value: "frames", label: "프레임 분석" },
  { value: "models", label: "분석 방법론" },
]

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (isUnauthorizedError(error)) {
      return "로그인이 만료되었습니다. 다시 로그인한 뒤 이용해 주세요."
    }

    if (error.status === 404 || error.errorCode === "CASE_NOT_FOUND") {
      return "현재 DB에서 이 사건을 찾을 수 없습니다. 분석이력에서 실제 등록된 사건을 다시 선택해 주세요."
    }
  }

  return getApiErrorMessage(error, fallback)
}

function normalizeStatus(status: string | null | undefined): AnalysisStatus {
  return normalizeAnalysisStatus(status)
}

function clampAnalysisProgress(progress: number | null | undefined) {
  if (!Number.isFinite(progress)) return 0
  return Math.max(0, Math.min(100, Math.round(progress ?? 0)))
}

function isDeepfakeAnalysisReady(evidences: CaseEvidenceSummary[]) {
  const activeEvidences = evidences.filter((evidence) => (evidence.lifecycleStatus ?? "ACTIVE") === "ACTIVE")
  return (
    activeEvidences.length > 0 &&
    activeEvidences.some(
      (evidence) => normalizeStatus(evidence.analysisStatus ?? "PENDING") === "COMPLETED"
    ) &&
    activeEvidences.every((evidence) =>
      ["COMPLETED", "FAILED"].includes(
        normalizeStatus(evidence.analysisStatus ?? "PENDING")
      )
    )
  )
}

function getFileExtension(fileName: string, mediaType?: string) {
  const extension = fileName.split(".").pop()
  if (extension) return extension.toUpperCase()
  return mediaType || "VIDEO"
}

function getStatusLabel(status: EvidenceDetailData["analysisInfo"]["status"]) {
  return getAnalysisStatusLabel(normalizeStatus(status))
}

function getCaseActorName(userId?: string | null) {
  if (!userId) return null
  return mockUsers.find((user) => user.id === userId)?.name ?? null
}

function getCaseStatusLabel(status: string) {
  if (status === "COMPLETED") return "분석 완료"
  if (status === "PROCESSING") return "분석 중"
  if (status === "FAILED") return "분석 실패"
  return "분석 대기"
}

function sortEvidences(evidences: CaseEvidenceSummary[]) {
  return [...evidences].sort((a, b) => a.evidenceId - b.evidenceId)
}

function isActiveEvidence(evidence: CaseEvidenceSummary) {
  return (evidence.lifecycleStatus ?? "ACTIVE") === "ACTIVE"
}

function getPreferredEvidenceId(evidences: CaseEvidenceSummary[], preferredEvidenceId: number | null) {
  const preferredEvidence = evidences.find((item) => item.evidenceId === preferredEvidenceId)
  if (preferredEvidence && isActiveEvidence(preferredEvidence)) return preferredEvidence.evidenceId

  return evidences.find(isActiveEvidence)?.evidenceId ?? evidences[0]?.evidenceId ?? null
}

type EvidenceStatusBucket = "pending" | "running" | "completed" | "inactive"
const PRIORITY_REVIEW_START_SEC = 15.8
const PRIORITY_REVIEW_END_SEC = 23.8
const PRIORITY_REVIEW_RANGE_LABEL = "00:15.800 ~ 00:23.800"
const PEAK_FRAME_TIME_LABEL = "00:19.800"
const ANALYSIS_STATUS_POLL_ERROR_TEXT =
  "분석 상태를 갱신하지 못했습니다. 잠시 후 새로고침하거나 다시 시도해 주세요."
const ANALYSIS_STATUS_POLL_TIMEOUT_TEXT =
  "분석 상태 확인 시간이 길어지고 있습니다. 현재 화면을 새로고침해 최신 상태를 확인해 주세요."

function getEvidenceBucket(evidence: CaseEvidenceSummary): EvidenceStatusBucket {
  if ((evidence.lifecycleStatus ?? "ACTIVE") !== "ACTIVE") return "inactive"
  if (isEvidenceAnalysisRunning(evidence)) return "running"
  if (normalizeStatus(evidence.analysisStatus ?? "PENDING") === "COMPLETED") return "completed"
  return "pending"
}

const RESULT_RISK_SIGNALS = [
  {
    label: "얼굴 경계 불연속",
    badge: "높은 위험 신호",
    score: 0.7,
    description: "프레임 간 얼굴 랜드마크와 주변 영역의 연결성을 분석했습니다.",
    basis: "얼굴 윤곽선과 주변 배경의 연결성이 낮게 측정됨",
    interval: PRIORITY_REVIEW_RANGE_LABEL,
    tone: "danger",
  },
  {
    label: "압축 아티팩트",
    badge: "검토 필요",
    score: 0.6,
    description: "얼굴 주변 영역의 압축 패턴이 배경 영역과 다르게 나타나는지 분석했습니다.",
    basis: "얼굴 주변 압축 패턴이 주변 영역보다 높게 나타남",
    interval: "00:00.400 ~ 00:08.400",
    tone: "warning",
  },
] as const

const RESULT_EXTRA_SIGNALS = [
  { label: "얼굴 질감 이상", score: 0.48, note: "뚜렷한 위험 신호는 제한적으로 관찰됨" },
  { label: "시간적 일관성 저하", score: 0.52, note: "일부 변화는 있으나 주요 위험 신호로 분류되지는 않음" },
  { label: "얼굴 움직임 이상 보조 신호", score: 0.43, note: "GMFlow 기반 참고 지표이며 단독 판단 근거로 사용하지 않음" },
] as const

const TOP_RISK_FRAMES = [
  { time: PEAK_FRAME_TIME_LABEL, seconds: 19.8, score: 82, signal: "얼굴 경계 불연속" },
  { time: "00:17.600", seconds: 17.6, score: 78, signal: "압축 패턴 이상" },
  { time: "00:21.400", seconds: 21.4, score: 74, signal: "얼굴 경계 불연속" },
  { time: "00:06.200", seconds: 6.2, score: 67, signal: "압축 아티팩트" },
  { time: "00:08.400", seconds: 8.4, score: 63, signal: "압축 아티팩트" },
] as const

export default function CaseDetailPage() {
  const { id } = useParams()
  const router = useRouter()
  const searchParams = useSearchParams()
  const caseId = decodeRouteParam(Array.isArray(id) ? id[0] : id)
  const initialEvidenceId = Number(searchParams.get("evidenceId"))
  const initialView = searchParams.get("view")
  const [caseData, setCaseData] = useState<CaseDetailData | null>(null)
  const [selectedEvidenceId, setSelectedEvidenceId] = useState<number | null>(null)
  const [evidenceDetail, setEvidenceDetail] = useState<EvidenceDetailData | null>(null)
  const [caseLoading, setCaseLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [caseRefreshKey, setCaseRefreshKey] = useState(0)
  const isInitialCaseLoad = useRef(true)
  const selectedEvidenceIdRef = useRef<number | null>(null)
  const reviewRequestPromptedForAnalysisRef = useRef<string | null>(null)
  const [showResultDashboard, setShowResultDashboard] = useState(false)
  const [showIntegrityDashboard, setShowIntegrityDashboard] = useState(false)
  const [session, setSession] = useState<AuthSession | null>(() => getSession())
  const [analysisProgressOverrides, setAnalysisProgressOverrides] = useState<AnalysisProgressOverrides>({})
  const [analysisPollingMessage, setAnalysisPollingMessage] = useState<WorkflowMessage | null>(null)
  const [reviewPopoverOpen, setReviewPopoverOpen] = useState(false)
  const [reviewRequestDialogOpen, setReviewRequestDialogOpen] = useState(false)
  const isReviewer = isReviewerSession(session)
  const currentUser = getAppUserFromSession(session)
  const canPromptReviewForCurrentCase =
    caseData != null &&
    currentUser?.role === "INVESTIGATOR" &&
    isCaseOwner(currentUser, caseData)
  const refreshCase = useCallback(() => {
    setCaseRefreshKey((key) => key + 1)
  }, [])
  const {
    dialogMode,
    loginId: stepUpLoginId,
    passwordLoading,
    passwordError,
    submitPassword,
    cancelPassword,
    closeSuccessDialog,
    ensureStepUp,
    fetchEvidenceDetailWithStepUp,
  } = useStepUpGate()

  const refreshEvidenceDetail = useCallback(
    async (evidenceId: number, options?: { silent?: boolean }) => {
      if (!Number.isFinite(evidenceId) || evidenceId <= 0) return

      const silent = options?.silent ?? false
      if (!silent) {
        setDetailLoading(true)
        setDetailError(null)
        setEvidenceDetail(null)
      }

      try {
        const result = await fetchEvidenceDetailWithStepUp(evidenceId)
        setEvidenceDetail(normalizeEvidenceDetailForUi(result))
      } catch (error) {
        if (!silent) {
          setEvidenceDetail(null)
          if (isStepUpCancelledError(error)) {
            setDetailError("민감 정보 조회를 위해 비밀번호 재인증이 필요합니다.")
          } else {
            setDetailError(getErrorMessage(error, "증거 상세 정보를 불러오지 못했습니다."))
          }
        }
      } finally {
        if (!silent) {
          setDetailLoading(false)
        }
      }
    },
    [fetchEvidenceDetailWithStepUp]
  )

  useEffect(() => {
    selectedEvidenceIdRef.current = selectedEvidenceId
  }, [selectedEvidenceId])

  useEffect(() => {
    function syncSession() {
      setSession(getSession())
    }

    syncSession()
    window.addEventListener("auth-change", syncSession)
    return () => window.removeEventListener("auth-change", syncSession)
  }, [])

  useEffect(() => {
    if (initialView === "result") {
      setShowIntegrityDashboard(false)
      setShowResultDashboard(true)
      return
    }

    if (initialView === "integrity") {
      setShowResultDashboard(false)
      setShowIntegrityDashboard(true)
    }
  }, [initialView])

  const trackedAnalysisIdsKey = useMemo(() => {
    if (!caseData) return ""

    const ids = new Set<number>()
    const evidenceById = new Map(caseData.evidences.map((evidence) => [evidence.evidenceId, evidence]))

    for (const evidence of caseData.evidences) {
      if (isEvidenceAnalysisRunning(evidence)) ids.add(evidence.evidenceId)
    }

    for (const [evidenceId, override] of Object.entries(analysisProgressOverrides)) {
      const numericEvidenceId = Number(evidenceId)
      const serverEvidence = evidenceById.get(numericEvidenceId)
      if (!serverEvidence || (serverEvidence.lifecycleStatus ?? "ACTIVE") !== "ACTIVE") continue

      const serverStatus = normalizeStatus(serverEvidence.analysisStatus ?? "PENDING")
      if (serverStatus === "COMPLETED" || serverStatus === "FAILED") continue

      if (override.status === "PROCESSING" || override.status === "PENDING") {
        ids.add(numericEvidenceId)
      }
    }

    return Array.from(ids)
      .filter((evidenceId) => Number.isFinite(evidenceId) && evidenceId > 0)
      .sort((a, b) => a - b)
      .join(",")
  }, [analysisProgressOverrides, caseData])

  useEffect(() => {
    let cancelled = false

    async function loadCaseDetail() {
      const showFullScreenLoader = isInitialCaseLoad.current
      if (showFullScreenLoader) {
        setCaseLoading(true)
      }
      setError(null)

      try {
        if (!caseId) return

        const result = await fetchCaseDetail(caseId)
        if (cancelled) return

        const currentSession = getSession()
        const currentUser = getAppUserFromSession(currentSession)
        if (isReviewerSession(currentSession) && result.reviewerId !== currentUser?.id) {
          throw new Error("배정된 검토 사건만 열람할 수 있습니다.")
        }

        const sorted = sortEvidences(result.evidences ?? [])
        setCaseData({ ...result, evidences: sorted })
        setSelectedEvidenceId((current) => {
          const preferredEvidenceId = Number.isFinite(initialEvidenceId) ? initialEvidenceId : current
          return getPreferredEvidenceId(sorted, preferredEvidenceId)
        })
      } catch (error) {
        if (!cancelled) {
          setError(getErrorMessage(error, "사건 정보를 불러오는 데 실패했습니다."))
        }
      } finally {
        if (!cancelled) {
          if (showFullScreenLoader) {
            setCaseLoading(false)
            isInitialCaseLoad.current = false
          }
        }
      }
    }

    loadCaseDetail()

    return () => {
      cancelled = true
    }
  }, [caseId, caseRefreshKey])

  useEffect(() => {
    if (!caseData) return

    setAnalysisProgressOverrides((current) => {
      let changed = false
      const next = { ...current }
      const activeIds = new Set(caseData.evidences.map((evidence) => evidence.evidenceId))

      for (const evidence of caseData.evidences) {
        const status = normalizeStatus(evidence.analysisStatus ?? "PENDING")
        const progress = clampAnalysisProgress(evidence.analysisProgress)

        if (status === "COMPLETED" || status === "FAILED") {
          if (next[evidence.evidenceId]) {
            delete next[evidence.evidenceId]
            changed = true
          }
          continue
        }

        if (status === "PROCESSING") {
          const previous = next[evidence.evidenceId]
          const nextProgress = Math.max(previous?.progress ?? 0, progress)
          if (!previous || previous.status !== "PROCESSING" || previous.progress !== nextProgress) {
            next[evidence.evidenceId] = { status: "PROCESSING", progress: nextProgress }
            changed = true
          }
        }
      }

      for (const evidenceId of Object.keys(next)) {
        if (!activeIds.has(Number(evidenceId))) {
          delete next[Number(evidenceId)]
          changed = true
        }
      }

      return changed ? next : current
    })
  }, [caseData])

  useEffect(() => {
    if (!trackedAnalysisIdsKey) {
      const promptPrefix = caseData ? `${caseData.caseId}|` : ""
      if (!reviewRequestPromptedForAnalysisRef.current?.startsWith(promptPrefix)) {
        setAnalysisPollingMessage(null)
      }
      return
    }

    const pollIds = trackedAnalysisIdsKey
      .split(",")
      .map((evidenceId) => Number(evidenceId))
      .filter((evidenceId) => Number.isFinite(evidenceId) && evidenceId > 0)

    if (pollIds.length === 0) return

    let cancelled = false
    let lastRefreshAt = 0
    let failedPollCount = 0
    let timeoutNotified = false
    const pollingStartedAt = Date.now()

    async function pollAnalysisStatuses() {
      const statuses = await Promise.all(
        pollIds.map((evidenceId) => fetchAnalysisStatus(evidenceId).catch(() => null))
      )

      if (cancelled) return

      const validStatuses = statuses.filter((status) => status != null)
      if (validStatuses.length === 0) {
        failedPollCount += 1
        if (failedPollCount >= 2) {
          setAnalysisPollingMessage({ type: "error", text: ANALYSIS_STATUS_POLL_ERROR_TEXT })
        }
        return
      }

      failedPollCount = 0
      const promptKey = caseData ? `${caseData.caseId}|${trackedAnalysisIdsKey}` : trackedAnalysisIdsKey
      if (reviewRequestPromptedForAnalysisRef.current !== promptKey) {
        setAnalysisPollingMessage(null)
      }
      setAnalysisProgressOverrides((current) => {
        let changed = false
        const next = { ...current }

        for (const statusUpdate of validStatuses) {
          const status = normalizeStatus(statusUpdate.status)
          const progress = clampAnalysisProgress(statusUpdate.progressPercent)

          if (status === "COMPLETED" || status === "FAILED") {
            const nextProgress = status === "COMPLETED" ? 100 : progress
            const previous = next[statusUpdate.evidenceId]
            if (!previous || previous.status !== status || previous.progress !== nextProgress) {
              next[statusUpdate.evidenceId] = { status, progress: nextProgress }
              changed = true
            }
            continue
          }

          if (status === "PROCESSING" || progress > 0) {
            const previous = next[statusUpdate.evidenceId]
            const nextProgress = Math.max(previous?.progress ?? 0, progress)
            if (!previous || previous.status !== "PROCESSING" || previous.progress !== nextProgress) {
              next[statusUpdate.evidenceId] = { status: "PROCESSING", progress: nextProgress }
              changed = true
            }
          }
        }

        return changed ? next : current
      })

      const hasTerminalStatus = statuses.some(
        (status) =>
          normalizeStatus(status?.status) === "COMPLETED" || normalizeStatus(status?.status) === "FAILED"
      )
      const allTrackedAnalysesTerminal =
        validStatuses.length === pollIds.length &&
        validStatuses.every((status) => {
          const analysisStatus = normalizeStatus(status.status)
          return analysisStatus === "COMPLETED" || analysisStatus === "FAILED"
        })
      const canPromptForReview =
        canPromptReviewForCurrentCase &&
        validStatuses.some((status) => normalizeStatus(status.status) === "COMPLETED")
      const now = Date.now()
      const selectedId = selectedEvidenceIdRef.current

      if (hasTerminalStatus) {
        for (const statusUpdate of validStatuses) {
          const status = normalizeStatus(statusUpdate.status)
          if (
            (status === "COMPLETED" || status === "FAILED") &&
            statusUpdate.evidenceId === selectedId
          ) {
            void refreshEvidenceDetail(statusUpdate.evidenceId, { silent: true })
            if (status === "COMPLETED") {
              window.setTimeout(() => {
                if (selectedEvidenceIdRef.current !== statusUpdate.evidenceId) return
                void refreshEvidenceDetail(statusUpdate.evidenceId, { silent: true })
              }, 3000)
            }
            break
          }
        }
      }

      if (hasTerminalStatus || now - lastRefreshAt >= 10000) {
        lastRefreshAt = now
        refreshCase()
      }

      if (
        allTrackedAnalysesTerminal &&
        canPromptForReview &&
        reviewRequestPromptedForAnalysisRef.current !== promptKey
      ) {
        reviewRequestPromptedForAnalysisRef.current = promptKey
        setAnalysisPollingMessage({ type: "info", text: "검토 요청을 진행해주세요" })
      }

      if (!timeoutNotified && now - pollingStartedAt > 60000 && !hasTerminalStatus) {
        timeoutNotified = true
        setAnalysisPollingMessage({ type: "error", text: ANALYSIS_STATUS_POLL_TIMEOUT_TEXT })
      }
    }

    void pollAnalysisStatuses()

    const interval = window.setInterval(() => {
      if (document.hidden) return
      void pollAnalysisStatuses()
    }, 2000)

    return () => {
      cancelled = true
      window.clearInterval(interval)
    }
  }, [
    canPromptReviewForCurrentCase,
    caseData,
    refreshCase,
    refreshEvidenceDetail,
    trackedAnalysisIdsKey,
  ])

  useEffect(() => {
    if (!caseData || !selectedEvidenceId) return

    const evidence = caseData.evidences.find((item) => item.evidenceId === selectedEvidenceId)
    if (!evidence) return

    const serverStatus = normalizeStatus(evidence.analysisStatus ?? "PENDING")
    if (serverStatus !== "COMPLETED" && serverStatus !== "FAILED") return

    const detailStatus = normalizeStatus(evidenceDetail?.analysisInfo.status ?? "PENDING")
    if (detailStatus === serverStatus) return

    void refreshEvidenceDetail(selectedEvidenceId, { silent: true })
  }, [caseData, evidenceDetail?.analysisInfo.status, refreshEvidenceDetail, selectedEvidenceId])

  useEffect(() => {
    if (!caseData || !Number.isFinite(initialEvidenceId)) return
    const initialEvidence = caseData.evidences.find((item) => item.evidenceId === initialEvidenceId)
    if (!initialEvidence || !isActiveEvidence(initialEvidence)) return

    setSelectedEvidenceId((current) => (current === initialEvidenceId ? current : initialEvidenceId))
  }, [caseData, initialEvidenceId])

  useEffect(() => {
    if (!selectedEvidenceId) {
      setEvidenceDetail(null)
      return
    }

    void refreshEvidenceDetail(selectedEvidenceId)
  }, [refreshEvidenceDetail, selectedEvidenceId])

  async function copyHash(hash: string) {
    try {
      await navigator.clipboard.writeText(hash)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1400)
    } catch {
      setCopied(false)
    }
  }

  function selectEvidence(evidenceId: number) {
    setSelectedEvidenceId(evidenceId)
    if (selectedEvidenceId !== evidenceId) {
      router.replace(buildCaseDetailPath(caseId, evidenceId), { scroll: false })
    }
  }

  function viewResult(evidenceId: number) {
    selectEvidence(evidenceId)
    setShowIntegrityDashboard(false)
    setShowResultDashboard(true)
    window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, behavior: "smooth" })
    })
  }

  function viewIntegrity(evidenceId: number) {
    selectEvidence(evidenceId)
    setShowResultDashboard(false)
    setShowIntegrityDashboard(true)
    window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, behavior: "smooth" })
    })
  }

  function startCompareVerification(evidenceId: number) {
    const query = new URLSearchParams({
      caseId,
      evidenceId: String(evidenceId),
    })
    router.push(`/compare?${query.toString()}`)
  }

  function viewCompareVerification(compareId: number) {
    router.push(`/compare/${compareId}`)
  }

  async function updateCaseSettings(
    caseName: string,
    representativeEvidenceId: number | null
  ): Promise<void> {
    if (!caseData) return

    let updatedCase = caseData
    if (caseName !== caseData.caseName) {
      updatedCase = await updateCaseName(caseData.caseId, caseName)
    }

    if (
      representativeEvidenceId !== null &&
      representativeEvidenceId !== updatedCase.representativeEvidenceId
    ) {
      await setRepresentativeEvidence(updatedCase.caseId, representativeEvidenceId)
      updatedCase = { ...updatedCase, representativeEvidenceId }
    }

    setCaseData(updatedCase)
    if (updatedCase.caseId !== caseId) {
      router.replace(
        buildCaseDetailPath(updatedCase.caseId, selectedEvidenceId ?? undefined),
        {
          scroll: false,
        }
      )
    }
  }

  return (
    <div className="min-h-screen bg-[#f6f8fa] text-slate-900 dark:bg-background dark:text-foreground">
      <SiteHeader />

      <main
        className={cn(
          "mx-auto flex w-full max-w-[1280px] flex-col gap-5 px-5 sm:px-8 lg:px-10",
          showResultDashboard ? "py-4" : "py-7"
        )}
      >
        {caseLoading && !caseData ? (
          <LoadingCard label="사건 상세 정보를 불러오는 중입니다..." />
        ) : error && !caseData ? (
          <ErrorState error={error} onBack={() => router.back()} />
        ) : caseData ? (
          <>
            {!showResultDashboard && !showIntegrityDashboard ? (
              <>
                <CaseBreadcrumb />
                <CaseHero
                  data={caseData}
                  getStatusLabel={getCaseStatusLabel}
                  reviewerName={getCaseActorName(caseData.reviewerId)}
                  requesterName={getCaseActorName(caseData.createdBy)}
                  viewerIsReviewer={isReviewer}
                  reviewOpen={reviewPopoverOpen}
                  onReviewOpenChange={setReviewPopoverOpen}
                />
              </>
            ) : null}

            <div className="relative">
              <div className="min-w-0 space-y-4">
                {showResultDashboard ? (
                  <CaseResultView
                    caseData={caseData}
                    evidenceDetail={evidenceDetail}
                    selectedEvidenceId={selectedEvidenceId}
                    detailLoading={detailLoading}
                    detailError={detailError}
                    currentSession={session}
                    onBack={() => setShowResultDashboard(false)}
                    onRefreshEvidenceDetail={(evidenceId) =>
                      void refreshEvidenceDetail(evidenceId, { silent: true })
                    }
                  />
                ) : showIntegrityDashboard ? (
                  <CaseIntegrityView
                    caseData={caseData}
                    evidenceDetail={evidenceDetail}
                    selectedEvidenceId={selectedEvidenceId}
                    detailLoading={detailLoading}
                    detailError={detailError}
                    currentSession={session}
                    onBack={() => setShowIntegrityDashboard(false)}
                  />
                ) : (
                  <CaseWorkflowPanel
                    caseData={caseData}
                    selectedEvidenceId={selectedEvidenceId}
                    evidenceDetail={evidenceDetail}
                    detailLoading={detailLoading}
                    detailError={detailError}
                    onSelectEvidence={selectEvidence}
                    onViewResult={viewResult}
                    onViewIntegrity={viewIntegrity}
                    onViewCompareResult={viewCompareVerification}
                    onStartCompare={startCompareVerification}
                    onUpdateCaseSettings={updateCaseSettings}
                    onRefresh={refreshCase}
                    analysisProgressOverrides={analysisProgressOverrides}
                    setAnalysisProgressOverrides={setAnalysisProgressOverrides}
                    analysisPollingMessage={analysisPollingMessage}
                    reviewRequestOpen={reviewRequestDialogOpen}
                    onReviewRequestOpenChange={setReviewRequestDialogOpen}
                    currentUserName={currentUser?.name ?? null}
                    currentUser={currentUser}
                    readOnly={isReviewer}
                    onReauthenticate={async () => {
                      await ensureStepUp()
                      if (selectedEvidenceId) {
                        await refreshEvidenceDetail(selectedEvidenceId)
                      }
                    }}
                  />
                )}
              </div>
            </div>
          </>
        ) : null}
      </main>

      {showResultDashboard ? null : <SiteFooter />}

      <StepUpGateDialogs
        mode={dialogMode}
        loginId={stepUpLoginId}
        loading={passwordLoading}
        error={passwordError}
        onSubmit={(password) => void submitPassword(password)}
        onCancel={cancelPassword}
        onSuccessClose={closeSuccessDialog}
      />
    </div>
  )
}

function LoadingCard({ label }: { label: string }) {
  return (
    <div className="flex min-h-[46vh] flex-col items-center justify-center gap-4 rounded-xl border border-slate-200 bg-white shadow-sm dark:border-border dark:bg-card">
      <Loader2 className="size-10 animate-spin text-teal-600" />
      <p className="animate-pulse text-sm font-bold text-slate-500 dark:text-muted-foreground">{label}</p>
    </div>
  )
}

function ErrorState({ error, onBack }: { error: string; onBack: () => void }) {
  return (
    <div className="space-y-5">
      <Button
        type="button"
        variant="outline"
        className="h-10 rounded-lg border-slate-200 text-sm font-bold text-slate-600 dark:border-border dark:text-muted-foreground"
        onClick={onBack}
      >
        <ArrowLeft className="size-4" />
        뒤로 가기
      </Button>
      <Alert variant="destructive" className="mx-auto max-w-2xl">
        <AlertCircle className="size-4" />
        <AlertTitle>데이터 로드 오류</AlertTitle>
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    </div>
  )
}

function EmptyEvidenceState() {
  return (
    <div className="rounded-xl border border-dashed border-slate-200 bg-white px-5 py-14 text-center shadow-sm dark:border-border dark:bg-card">
      <FileSearch className="mx-auto size-8 text-slate-300" />
      <p className="mt-3 text-sm font-bold text-slate-500 dark:text-muted-foreground">
        상세 분석을 볼 증거 파일을 선택해 주세요.
      </p>
    </div>
  )
}

function CaseBreadcrumb() {
  return (
    <nav className="flex items-center gap-2 text-sm font-bold text-muted-foreground" aria-label="현재 위치">
      <Link href="/mypage" className="inline-flex items-center gap-2 transition-colors hover:text-foreground">
        <Home className="size-4" aria-hidden="true" />
        사건 관리
      </Link>
      <ChevronRight className="size-4 text-muted-foreground/50" aria-hidden="true" />
      <span className="text-foreground">사건 상세</span>
    </nav>
  )
}

type ResultMediaMode = "original" | "overlay"

function MockAnalysisOverlay() {
  return (
    <div className="pointer-events-none absolute inset-0">
      <div className="absolute left-[39%] top-[20%] h-[34%] w-[24%] rounded-[18%] border-2 border-red-700 bg-red-700/15 shadow-[0_0_24px_rgba(185,28,28,0.3)]" />
      <div className="absolute left-[43%] top-[38%] h-[7%] w-[16%] rounded-sm bg-yellow-300/55" />
      <div className="absolute left-[34%] top-[56%] h-[18%] w-[36%] rounded-md border border-red-700/40 bg-red-700/10" />
      <div className="absolute bottom-4 left-4 rounded-md bg-red-700/95 px-2.5 py-1 text-xs font-bold text-white">
        얼굴 경계 불연속 · 압축 흔적
      </div>
    </div>
  )
}

function EvidenceWatermarkOverlay({
  caseId,
  evidenceId,
  viewerName,
  viewerLoginId,
  compact = false,
  mode = "full",
}: {
  caseId?: string | null
  evidenceId?: number | string | null
  viewerName?: string | null
  viewerLoginId?: string | null
  compact?: boolean
  mode?: "full" | "review"
}) {
  const [timestamp, setTimestamp] = useState("열람 시간 확인 중")
  const evidenceLabel = evidenceId ? `EVD-${evidenceId}` : "EVD-미지정"
  const viewerLabel = [viewerName, viewerLoginId].filter(Boolean).join(" / ") || "열람자 미확인"
  const primaryText = `${evidenceLabel} · ${viewerLabel} · ${timestamp}`
  const centerText = caseId ? `${primaryText} · CASE ${caseId}` : primaryText
  const isReviewMode = mode === "review"

  // 열람 시작 시각으로 고정 — 추적 목적엔 시작 시각이면 충분하고, 매초 갱신은 시선만 끈다
  useEffect(() => {
    setTimestamp(formatDateTimeWithSeconds(new Date()))
  }, [])

  return (
    <div className="pointer-events-none absolute inset-0 z-10 overflow-hidden" aria-hidden="true">
      <div className="absolute inset-0 flex items-center justify-center">
        <span
          className={cn(
            "-rotate-[18deg] whitespace-nowrap font-mono font-bold tracking-wider",
            isReviewMode ? "text-white/10" : "text-white/[0.14]",
            compact || isReviewMode ? "text-xs sm:text-sm" : "text-sm sm:text-lg"
          )}
          style={{ textShadow: "0 1px 2px rgba(0,0,0,0.4)" }}
        >
          {centerText}
        </span>
      </div>
      <span className="absolute bottom-24 left-4 rounded-lg bg-black/45 px-2.5 py-1.5 font-mono text-[10px] font-bold text-white/65 backdrop-blur-md">
        {primaryText}
      </span>
    </div>
  )
}

function CaseResultView({
  caseData,
  evidenceDetail,
  selectedEvidenceId,
  detailLoading,
  detailError,
  currentSession,
  onBack,
  onRefreshEvidenceDetail,
}: {
  caseData: CaseDetailData
  evidenceDetail: EvidenceDetailData | null
  selectedEvidenceId: number | null
  detailLoading: boolean
  detailError: string | null
  currentSession: AuthSession | null
  onBack: () => void
  onRefreshEvidenceDetail?: (evidenceId: number) => void
}) {
  const [mediaContext, setMediaContext] = useState("original")
  const [resultTab, setResultTab] = useState<ResultTab>("summary")
  const [reportDialogOpen, setReportDialogOpen] = useState(false)
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const lastSecurityEventRef = useRef<{ key: string; recordedAt: number } | null>(null)
  const selectedEvidence =
    caseData.evidences.find((evidence) => evidence.evidenceId === selectedEvidenceId) ??
    caseData.evidences[0] ??
    null
  const riskTone = evidenceDetail ? getCaseRiskTone(evidenceDetail) : "red"
  const resultVerdict = getManipulationSuspicionLabel(riskTone)
  const riskScore = formatResultScore(evidenceDetail?.analysisInfo.riskScore ?? null)
  const confidenceScore = formatResultScore(evidenceDetail?.analysisInfo.confidenceScore ?? null)
  const riskScoreLabel = riskScore ? `${riskScore} / 100` : "- / 100"
  const confidenceScoreLabel = confidenceScore ? `${confidenceScore}%` : "-"
  const resultEvidenceIdLabel = selectedEvidence ? `EVD-${selectedEvidence.evidenceId}` : caseData.caseId
  const analyzedAt = evidenceDetail?.analysisInfo.completedAt ?? evidenceDetail?.analysisInfo.requestedAt ?? caseData.createdAt
  const hlsPlayback = evidenceDetail?.hlsPlayback ?? null
  const frameScores = getXceptionFrameScores(evidenceDetail)
  const detectionThreshold = getDetectionThreshold(evidenceDetail)
  const summaryActions = buildSummaryActions(evidenceDetail, frameScores)
  const { primary: primaryRiskSignals, extra: extraRiskSignals } = buildRiskSignals(evidenceDetail)
  const allRiskSignals = [...primaryRiskSignals, ...extraRiskSignals]
  const deepfakeRiskSignals = allRiskSignals.filter((signal) => !isForgeryRiskSignal(signal))
  const forgeryRiskSignals = buildForgeryResultTabSignals(evidenceDetail, detectionThreshold)
  const forgeryScoreSummary = getForgeryScoreSummary(evidenceDetail)
  const forgeryPriorityRange = getForgeryPriorityReviewRange(evidenceDetail)
  const forgeryRepresentativeFrames = buildForgeryRepresentativeFrames(evidenceDetail, {
    moduleKey: FORGERY_SPATIAL_MODULE,
  })
  const forgeryGalleryCopy = forgeryHighRiskGalleryCopy(FORGERY_SPATIAL_MODULE)
  const detectionModules = getDetectionModules(evidenceDetail?.analysisInfo.moduleResults ?? []).sort(
    (a, b) => normalizeResultValue(b.score) - normalizeResultValue(a.score)
  )
  const overThresholdSignalCount = detectionModules.filter(
    (module) => normalizeResultValue(module.score) >= detectionThreshold
  ).length
  const priorityReviewRange = getPriorityReviewRange(evidenceDetail, frameScores)
  const representativeFrames = evidenceDetail?.analysisInfo.representativeFrames ?? []

  const reportSecurityEvent = useCallback((event: ProtectedSecurityEvent) => {
    if (!selectedEvidenceId) return

    const now = Date.now()
    const eventKey = `${selectedEvidenceId}:${mediaContext}:${event.eventType}`
    const lastEvent = lastSecurityEventRef.current
    if (lastEvent?.key === eventKey && now - lastEvent.recordedAt < 5000) {
      return
    }

    lastSecurityEventRef.current = { key: eventKey, recordedAt: now }
    void recordEvidenceSecurityEvent(selectedEvidenceId, {
      eventType: event.eventType,
      detail: event.detail,
      mediaMode: mediaContext,
      pagePath: `${window.location.pathname}${window.location.search}`,
      clientTimestamp: new Date().toISOString(),
    }).catch(() => undefined)
  }, [mediaContext, selectedEvidenceId])
  const forgeryHighestScore = forgeryScoreSummary.highestScore
  const forgeryOverThresholdCount = forgeryScoreSummary.overThresholdCount
  const methodology = buildMethodologyInfo(evidenceDetail, frameScores)
  const forgeryMethodologyItems = buildForgeryMethodologyItems(forgeryRiskSignals)

  function seekResultVideo(seconds: number) {
    requestAnimationFrame(() => {
      const video = videoRef.current
      if (!video) return
      video.currentTime = seconds
      void video.play().catch(() => undefined)
    })
  }


  return (
    <section className="space-y-8 rounded-xl bg-[#f6f8fa] px-0 py-1 text-slate-950 dark:bg-background dark:text-foreground">
      <header className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-start">
          <Button
            type="button"
            variant="outline"
            className="h-10 shrink-0 rounded-lg border-slate-200 bg-white px-4 text-sm font-semibold text-slate-950 shadow-none hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground"
            onClick={onBack}
          >
            <ArrowLeft className="size-4" aria-hidden="true" />
            증거 관리
          </Button>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <h1 className="truncate text-2xl font-bold tracking-normal text-slate-950 dark:text-foreground">
                AI 조작 분석 결과
              </h1>
            </div>
            <p className="mt-1 text-sm font-medium text-slate-500">
              {resultEvidenceIdLabel} · 분석 완료 {formatDateTime(analyzedAt)}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={!evidenceDetail || caseData.reviewStatus !== "REPORT_APPROVED"}
            onClick={() => setReportDialogOpen(true)}
            className="h-10 rounded-lg border-slate-200 bg-white px-4 text-sm font-semibold text-slate-950 shadow-none hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground"
          >
            <Download className="size-4" aria-hidden="true" />
            PDF 다운로드
          </Button>
        </div>
      </header>

      {evidenceDetail ? (
        <ReportExportDialog
          open={reportDialogOpen}
          onClose={() => setReportDialogOpen(false)}
          data={evidenceDetail}
          reviewApproved={caseData.reviewStatus === "REPORT_APPROVED"}
        />
      ) : null}

      {detailLoading ? (
        <LoadingCard label="분석 결과를 불러오는 중입니다..." />
      ) : detailError ? (
        <Alert variant="destructive">
          <AlertCircle className="size-4" />
          <AlertTitle>분석 결과 로드 오류</AlertTitle>
          <AlertDescription>{detailError}</AlertDescription>
        </Alert>
      ) : evidenceDetail ? (
        <>
          <div className="grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:items-start">
            <ResultEvidenceMedia
              evidenceDetail={evidenceDetail}
              selectedEvidenceId={selectedEvidenceId}
              hlsPlayback={hlsPlayback}
              videoRef={videoRef}
              onSecurityEvent={reportSecurityEvent}
              onSeek={seekResultVideo}
              onMediaContextChange={setMediaContext}
              onOverlayReady={() => {
                if (selectedEvidenceId) {
                  onRefreshEvidenceDetail?.(selectedEvidenceId)
                }
              }}
              renderHeatStrip={({ scores, caption, onSeek: seek }) => (
                <FrameRiskHeatStrip scores={scores} onSeek={seek} caption={caption} />
              )}
              renderWatermark={
                <EvidenceWatermarkOverlay
                  caseId={caseData.caseId}
                  evidenceId={selectedEvidence?.evidenceId ?? selectedEvidenceId}
                  viewerName={currentSession?.name ?? null}
                  viewerLoginId={currentSession?.loginId ?? null}
                />
              }
            />

            <section className="flex flex-col rounded-xl border border-slate-200 bg-white shadow-none dark:border-border dark:bg-card">
            <div className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4 dark:border-border">
              <div className="flex min-w-0 items-start gap-3">
                {riskTone === "red" ? (
                  <AlertTriangle className="mt-0.5 size-5 shrink-0 text-red-700 dark:text-red-400" aria-hidden="true" />
                ) : riskTone === "orange" ? (
                  <AlertCircle className="mt-0.5 size-5 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
                ) : (
                  <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-teal-600 dark:text-teal-300" aria-hidden="true" />
                )}
                <div className="min-w-0">
                  <p
                    className={cn(
                      "text-base font-bold",
                      riskTone === "red"
                        ? "text-red-700 dark:text-red-400"
                        : riskTone === "orange"
                          ? "text-amber-600 dark:text-amber-400"
                          : "text-teal-700 dark:text-teal-300"
                    )}
                  >
                    {resultVerdict}
                  </p>
                  <p className="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-xs font-semibold text-slate-500">
                    <span>기준 초과 신호 {overThresholdSignalCount}개</span>
                    {priorityReviewRange ? (
                      <>
                        <span aria-hidden="true">·</span>
                        <button
                          type="button"
                          onClick={() => seekResultVideo(priorityReviewRange.startSec)}
                          className="inline-flex items-center gap-1 font-bold text-teal-700 hover:underline dark:text-teal-300"
                        >
                          의심 구간 {priorityReviewRange.label}
                          <Play className="size-3" aria-hidden="true" />
                        </button>
                      </>
                    ) : null}
                  </p>
                </div>
              </div>
              <div className="text-right">
                <p
                  className={cn(
                    "text-xl font-bold leading-none",
                    riskTone === "red" ? "text-red-700 dark:text-red-400" : "text-slate-950 dark:text-foreground"
                  )}
                >
                  {riskScoreLabel}
                </p>
                <p className="mt-1 text-[11px] font-semibold text-slate-400">종합 위험 점수</p>
              </div>
            </div>
            <div className="relative grid shrink-0 grid-cols-5 border-b border-slate-200 text-center text-sm font-medium text-slate-500 dark:border-border">
              {RESULT_TABS.map(({ value: tab, label }) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setResultTab(tab)}
                  className={cn(
                    "py-4 transition-colors hover:text-slate-950 dark:hover:text-foreground",
                    resultTab === tab && "font-semibold text-slate-950 dark:text-foreground"
                  )}
                >
                  {label}
                </button>
              ))}
              <span
                aria-hidden="true"
                className="pointer-events-none absolute bottom-[-1px] z-10 h-0.5 bg-slate-950 transition-[left] duration-300 ease-out dark:bg-foreground"
                style={{
                  left: `${Math.max(0, RESULT_TABS.findIndex((tab) => tab.value === resultTab)) * 20}%`,
                  width: "20%",
                }}
              />
            </div>
            <div className="p-5">
              {resultTab === "summary" ? (
                <div className="space-y-5">
                  <div className="grid gap-3 sm:grid-cols-3">
                    <FrameMetricCard
                      label="종합 위험 점수"
                      value={riskScoreLabel}
                      sub={resultVerdict}
                      tone={riskTone === "red" ? "danger" : "neutral"}
                    />
                    <FrameMetricCard
                      label="모델 산출 확신도"
                      value={confidenceScoreLabel}
                      sub="분석 모델이 보고한 확신도"
                    />
                    <FrameMetricCard
                      label="기준 초과 신호"
                      value={`${overThresholdSignalCount} / ${detectionModules.length}개`}
                      sub={`위험 점수 ${Math.round(detectionThreshold * 100)}점 이상`}
                      tone={overThresholdSignalCount > 0 ? "danger" : "neutral"}
                    />
                  </div>

                  <ModelConsensusCard
                    models={methodology.models}
                    thresholdPercent={Math.round(detectionThreshold * 100)}
                    summary={sanitizeAnalysisSummaryForUi(evidenceDetail.analysisInfo.summary)}
                  />

                  <TrustChecklistCard data={evidenceDetail} />

                  <ReadinessMetricSection
                    evidenceId={evidenceDetail.evidenceInfo.evidenceId}
                    analysisCompleted={evidenceDetail.analysisInfo.status === "COMPLETED"}
                  />

                  <section className="rounded-xl border border-slate-100 bg-slate-50/70 p-6 dark:border-border dark:bg-background">
                    <h3 className="text-lg font-bold text-slate-950 dark:text-foreground">확인 순서</h3>
                    <p className="mt-2 text-sm font-semibold text-slate-500">
                      분석 결과를 검토할 때 먼저 볼 것부터 순서대로 안내합니다.
                    </p>
                    <ol className="mt-6 space-y-4">
                      {summaryActions.map((action, index) => (
                        <li
                          key={action.text}
                          className="flex gap-4 text-base font-semibold leading-7 text-slate-700 dark:text-muted-foreground"
                        >
                          <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-white text-sm font-bold text-slate-500 ring-1 ring-slate-200 dark:bg-card dark:ring-border">
                            {index + 1}
                          </span>
                          <span className="min-w-0">
                            {action.text}
                            {action.seekSec != null ? (
                              <button
                                type="button"
                                onClick={() => seekResultVideo(action.seekSec as number)}
                                className="ml-2 inline-flex items-center gap-1 text-sm font-bold text-teal-700 hover:underline dark:text-teal-300"
                              >
                                <Play className="size-3.5" aria-hidden="true" />
                                구간 재생
                              </button>
                            ) : action.tab ? (
                              <button
                                type="button"
                                onClick={() => setResultTab(action.tab === "frames" ? "frames" : "deepfake")}
                                className="ml-2 inline-flex items-center gap-1 text-sm font-bold text-teal-700 hover:underline dark:text-teal-300"
                              >
                                {action.tab === "frames" ? "프레임 분석 보기" : "딥페이크 탐지 보기"}
                                <ChevronRight className="size-3.5" aria-hidden="true" />
                              </button>
                            ) : null}
                          </span>
                        </li>
                      ))}
                    </ol>
                  </section>

                </div>
              ) : resultTab === "deepfake" ? (
                <section>
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <h3 className="text-lg font-bold text-slate-950 dark:text-foreground">딥페이크 탐지</h3>
                      <p className="mt-1 text-sm font-semibold text-slate-500">
                        얼굴 합성, 시계열 일관성, 움직임 보조 신호를 모델별로 확인합니다.
                      </p>
                    </div>
                    <span className="shrink-0 rounded-full bg-teal-50 px-3 py-1 text-xs font-bold text-teal-700">
                      모델 {methodology.models.length}개
                    </span>
                  </div>

                  {methodology.models.length > 0 ? (
                    <section className="mt-5 overflow-hidden rounded-xl border border-slate-100 bg-white dark:border-border dark:bg-card">
                      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-5 py-3.5 dark:border-border">
                        <h4 className="text-sm font-bold text-slate-950 dark:text-foreground">딥페이크 모델별 판단 점수</h4>
                        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-bold text-slate-500 dark:bg-secondary">
                          모듈별 기준선 · 초과 시 탐지
                        </span>
                      </div>
                      <MethodologyModelChart models={methodology.models} />
                    </section>
                  ) : null}

                  {deepfakeRiskSignals.length > 0 ? (
                    <ul className="mt-5 space-y-3">
                      {deepfakeRiskSignals.map((signal, index) => (
                        <RiskSignalCard
                          key={`${signal.label}-${index}`}
                          signal={signal}
                          delayMs={index * 120}
                          onSeek={seekResultVideo}
                        />
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-5 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
                      딥페이크 탐지 신호가 아직 제공되지 않았습니다.
                    </p>
                  )}
                </section>
              ) : resultTab === "forgery" ? (
                <section>
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <h3 className="text-lg font-bold text-slate-950 dark:text-foreground">위변조 탐지</h3>
                      <p className="mt-1 text-sm font-semibold text-slate-500">
                        TruFor(국소)와 TimeSformer(시간축) 위변조 신호를 함께 확인합니다.
                      </p>
                    </div>
                    <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-500 dark:bg-secondary">
                      세부 모델 {forgeryScoreSummary.modelCount}개
                    </span>
                  </div>

                  <div className="mt-5 grid gap-3 sm:grid-cols-2">
                    <FrameMetricCard
                      label="위변조 최고 점수"
                      value={
                        forgeryScoreSummary.modelCount > 0
                          ? formatScoreOutOf100(forgeryHighestScore)
                          : "-"
                      }
                      sub={formatForgeryDualScoreSub(forgeryScoreSummary)}
                      tone={
                        forgeryHighestScore >=
                        Math.min(
                          forgeryScoreSummary.spatialThreshold,
                          forgeryScoreSummary.temporalThreshold
                        )
                          ? "danger"
                          : "neutral"
                      }
                    />
                    <FrameMetricCard
                      label="기준 초과 항목"
                      value={`${forgeryOverThresholdCount} / ${forgeryScoreSummary.modelCount}개`}
                      sub={`TruFor ${Math.round(forgeryScoreSummary.spatialThreshold * 100)} · TimeSformer ${Math.round(forgeryScoreSummary.temporalThreshold * 100)}`}
                      tone={forgeryOverThresholdCount > 0 ? "danger" : "neutral"}
                    />
                    <FrameMetricCard
                      label="의심 구간"
                      value={forgeryPriorityRange ? forgeryPriorityRange.label : "-"}
                      sub={
                        forgeryPriorityRange
                          ? `${forgeryPriorityRange.source ?? "위변조"} · 최소 1초`
                          : "임계값 초과 구간 없음"
                      }
                    />
                    <FrameMetricCard
                      label="시각 증거"
                      value={forgeryRepresentativeFrames.length > 0 ? "제공됨" : "대기"}
                      sub="고위험 프레임 시점 · 마스크 연동 시 표시"
                    />
                  </div>

                  {forgeryRiskSignals.length > 0 ? (
                    <ul className="mt-5 space-y-3">
                      {forgeryRiskSignals.map((signal, index) => (
                        <RiskSignalCard
                          key={`${signal.label}-${index}`}
                          signal={signal}
                          delayMs={index * 120}
                          onSeek={seekResultVideo}
                        />
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-5 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
                      위변조(TruFor / TimeSformer) 결과가 아직 제공되지 않았습니다.
                      <br />
                      GPU worker가 forgery_spatial · forgery_temporal 점수를 내면 이 영역에 표시됩니다.
                    </p>
                  )}

                  <section className="mt-5">
                      <div>
                        <h4 className="text-sm font-bold text-slate-950 dark:text-foreground">
                          {forgeryGalleryCopy.title}
                        </h4>
                        <p className="mt-0.5 text-xs font-semibold text-slate-500">
                          {forgeryGalleryCopy.description}
                        </p>
                      </div>
                      {forgeryRepresentativeFrames.length > 0 ? (
                        <div className="mt-3 grid gap-3 sm:grid-cols-2">
                          {forgeryRepresentativeFrames.slice(0, 2).map((frame, index) => (
                            <RepresentativeFrameDetailCard
                              key={`${frame.timestamp ?? frame.timeSec ?? index}-forgery`}
                              frame={frame}
                              index={index}
                              videoRef={videoRef}
                            />
                          ))}
                        </div>
                      ) : (
                        <p className="mt-3 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-6 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
                          {forgeryGalleryCopy.empty}
                        </p>
                      )}
                    </section>
                </section>
              ) : resultTab === "frames" ? (
                <ResultFrameAnalysis
                  evidenceDetail={evidenceDetail}
                  detectionThreshold={detectionThreshold}
                  representativeFrames={representativeFrames}
                  videoRef={videoRef}
                  onSeek={seekResultVideo}
                />
              ) : (
                <section>
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <h3 className="text-lg font-bold text-slate-950 dark:text-foreground">분석 방법론</h3>
                      <p className="mt-1 text-sm font-medium text-slate-500">
                        어떤 모델과 설정으로 분석했는지, 재현에 필요한 정보를 제공합니다.
                      </p>
                    </div>
                  </div>

                  <section className="mt-5 overflow-hidden rounded-xl border border-slate-100 bg-white dark:border-border dark:bg-card">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-5 py-3.5 dark:border-border">
                      <h4 className="text-sm font-bold text-slate-950 dark:text-foreground">딥페이크 모델 방법론</h4>
                      <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-bold text-slate-500 dark:bg-secondary">
                        판정 기준 {Math.round(detectionThreshold * 100)} / 100
                      </span>
                    </div>
                    {methodology.models.length > 0 ? (
                      <div className="divide-y divide-slate-50 dark:divide-border">
                        {methodology.models.map((model) => (
                          <div key={`${model.name}-${model.version}`} className="px-5 py-3.5">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <p className="text-sm font-bold text-slate-950 dark:text-foreground">
                                {model.name}
                                <span className="ml-1.5 font-mono text-xs font-semibold text-slate-400">
                                  {model.version}
                                </span>
                              </p>
                              <span
                                className={cn(
                                  "rounded-full px-2.5 py-1 text-[11px] font-bold",
                                  model.score != null && model.overThreshold
                                    ? "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-400"
                                    : "bg-slate-100 text-slate-500 dark:bg-secondary dark:text-muted-foreground"
                                )}
                              >
                                {model.score == null
                                  ? "정보 없음"
                                  : model.overThreshold
                                    ? `기준 ${Math.round(model.threshold * 100)} 초과`
                                    : `기준 ${Math.round(model.threshold * 100)} 미만`}
                              </span>
                            </div>
                            <p className="mt-1 text-xs font-semibold text-slate-500">분석 목적: {model.role}</p>
                            <p className="mt-0.5 text-xs font-medium text-slate-400">
                              검증 성능: {model.benchmark ?? "정보 없음"}
                            </p>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="px-5 py-6 text-center text-sm font-semibold text-slate-400">
                        모델 식별 정보가 아직 제공되지 않았습니다. 백엔드가 모델명·버전을 보고하면 이 영역에 표시됩니다.
                      </p>
                    )}
                  </section>

                  <section className="mt-4 overflow-hidden rounded-xl border border-slate-100 bg-white dark:border-border dark:bg-card">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-5 py-3.5 dark:border-border">
                      <h4 className="text-sm font-bold text-slate-950 dark:text-foreground">위변조 모델 방법론</h4>
                      <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-bold text-slate-500 dark:bg-secondary">
                        세부 검사 {forgeryMethodologyItems.length}개
                      </span>
                    </div>
                    <div className="divide-y divide-slate-50 dark:divide-border">
                      {forgeryMethodologyItems.map((item) => (
                        <div key={item.name} className="px-5 py-3.5">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <p className="text-sm font-bold text-slate-950 dark:text-foreground">
                              {item.name}
                              {item.version ? (
                                <span className="ml-1.5 font-mono text-xs font-semibold text-slate-400">
                                  {item.version}
                                </span>
                              ) : null}
                            </p>
                            <span
                              className={cn(
                                "rounded-full px-2.5 py-1 text-[11px] font-bold",
                                item.score != null && item.overThreshold
                                  ? "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-400"
                                  : "bg-slate-100 text-slate-500 dark:bg-secondary dark:text-muted-foreground"
                              )}
                            >
                              {item.score == null
                                ? "정보 없음"
                                : item.overThreshold
                                  ? `기준 ${Math.round(item.threshold * 100)} 초과`
                                  : `기준 ${Math.round(item.threshold * 100)} 미만`}
                            </span>
                          </div>
                          <p className="mt-1 text-xs font-semibold text-slate-500">분석 목적: {item.role}</p>
                          <p className="mt-0.5 text-xs font-medium text-slate-400">표시 데이터: {item.output}</p>
                        </div>
                      ))}
                    </div>
                  </section>

                  <section className="mt-4 overflow-hidden rounded-xl border border-slate-100 bg-white dark:border-border dark:bg-card">
                    <h4 className="border-b border-slate-100 px-5 py-3.5 text-sm font-bold text-slate-950 dark:border-border dark:text-foreground">
                      재현 정보
                    </h4>
                    <div className="grid gap-x-8 gap-y-2.5 px-5 py-4 sm:grid-cols-2">
                      {methodology.settings.map((item) => (
                        <div key={item.label} className="grid grid-cols-[96px_minmax(0,1fr)] items-start gap-4">
                          <span className="whitespace-nowrap text-sm font-medium text-slate-500">{item.label}</span>
                          <span className="min-w-0 break-keep text-right text-sm font-bold leading-6 text-slate-950 dark:text-foreground">
                            {item.value}
                          </span>
                        </div>
                      ))}
                    </div>
                    {methodology.inputHash ? (
                      <div className="border-t border-slate-100 px-5 py-3.5 dark:border-border">
                        <p className="text-xs font-semibold text-slate-400">
                          입력 파일 해시 ({methodology.hashAlgorithm ?? "해시"}) · 무결성 검증 탭과 동일한 값입니다.
                        </p>
                        <p className="mt-1 break-all font-mono text-xs font-semibold text-slate-600 dark:text-muted-foreground">
                          {methodology.inputHash}
                        </p>
                      </div>
                    ) : null}
                  </section>

                  <p className="mt-4 text-xs font-medium leading-5 text-slate-400">
                    위 정보와 동일한 파일·모델·임계값으로 분석하면 같은 결과를 재현할 수 있습니다. AI 분석 점수는
                    참고 소견이며, 저해상도·높은 압축률·얼굴 가림 환경에서는 정확도가 낮아질 수 있습니다.
                  </p>
                </section>
              )}
            </div>
          </section>
          </div>
        </>
      ) : (
        <EmptyEvidenceState />
      )}
    </section>
  )
}

function CaseIntegrityView({
  caseData,
  evidenceDetail,
  selectedEvidenceId,
  detailLoading,
  detailError,
  currentSession,
  onBack,
}: {
  caseData: CaseDetailData
  evidenceDetail: EvidenceDetailData | null
  selectedEvidenceId: number | null
  detailLoading: boolean
  detailError: string | null
  currentSession?: AuthSession | null
  onBack: () => void
}) {
  const selectedEvidence =
    caseData.evidences.find((evidence) => evidence.evidenceId === selectedEvidenceId) ??
    caseData.evidences[0] ??
    null
  const title =
    selectedEvidence?.originalFileName ??
    selectedEvidence?.fileName ??
    selectedEvidence?.displayLabel ??
    caseData.caseName
  const originalHash = evidenceDetail?.integrityInfo.originalHash ?? "-"
  const chainValid = evidenceDetail?.integrityInfo.chainValid ?? false
  const cocLogs = evidenceDetail?.cocLogs ?? []
  const signatureInfo = evidenceDetail?.signatureInfo ?? null
  const signatureSigned = (signatureInfo?.signatureStatus ?? "").toUpperCase() === "SIGNED"
  const signatureValid = signatureInfo?.signatureValid ?? false
  const blockchainInfo = evidenceDetail?.blockchainInfo ?? null
  const evidenceIdForBlockchain =
    selectedEvidenceId ?? evidenceDetail?.evidenceInfo.evidenceId ?? selectedEvidence?.evidenceId ?? null
  const [blockchainStatus, setBlockchainStatus] = useState<BlockchainAnchorStatusResponse | null>(null)
  const [blockchainLoading, setBlockchainLoading] = useState(false)
  const [blockchainError, setBlockchainError] = useState<string | null>(null)
  const blockchainAnchors = blockchainStatus ? buildBlockchainAnchorsFromStatus(blockchainStatus) : []
  const primaryAnchor = blockchainStatus?.evidenceHashAnchor ?? null
  const blockchainAnchored =
    (primaryAnchor?.status ?? blockchainInfo?.status ?? "").toUpperCase() === "ANCHORED"
  const [integrityTab, setIntegrityTab] = useState<"original" | "signature" | "blockchain" | "coc">("original")
  const [openTransactionId, setOpenTransactionId] = useState<string | null>(null)

  useEffect(() => {
    const evidenceId = evidenceIdForBlockchain
    if (evidenceId == null) {
      setBlockchainStatus(null)
      setBlockchainError(null)
      return
    }

    let cancelled = false
    async function loadBlockchain() {
      setBlockchainLoading(true)
      setBlockchainError(null)
      try {
        const status = await fetchEvidenceBlockchainStatus(evidenceId)
        if (!cancelled) {
          setBlockchainStatus(status)
        }
      } catch (error) {
        if (!cancelled) {
          setBlockchainStatus(null)
          setBlockchainError(error instanceof Error ? error.message : "블록체인 앵커 정보를 불러오지 못했습니다.")
        }
      } finally {
        if (!cancelled) {
          setBlockchainLoading(false)
        }
      }
    }

    void loadBlockchain()
    return () => {
      cancelled = true
    }
  }, [evidenceIdForBlockchain])

  return (
    <section className="space-y-6 rounded-xl bg-[#f6f8fa] px-0 py-1 text-slate-950 dark:bg-background dark:text-foreground">
      <header className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-start">
          <Button
            type="button"
            variant="outline"
            className="h-10 shrink-0 rounded-lg border-slate-200 bg-white px-4 text-sm font-semibold text-slate-950 shadow-none hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground"
            onClick={onBack}
          >
            <ArrowLeft className="size-4" aria-hidden="true" />
            증거 관리
          </Button>
          <div className="min-w-0">
            <h1 className="truncate text-2xl font-bold tracking-normal text-slate-950 dark:text-foreground">
              무결성 검증 결과
            </h1>
            <p className="mt-1 text-sm font-medium text-slate-500">
              {title} · 원본 해시, 블록체인 기록, 증거 이력을 확인합니다.
            </p>
          </div>
        </div>
      </header>

      {detailLoading ? (
        <LoadingCard label="무결성 검증 결과를 불러오는 중입니다..." />
      ) : detailError ? (
        <Alert variant="destructive">
          <AlertCircle className="size-4" />
          <AlertTitle>무결성 결과 로드 오류</AlertTitle>
          <AlertDescription>{detailError}</AlertDescription>
        </Alert>
      ) : evidenceDetail ? (
        <>
          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <IntegrityStatusCard
              label="원본 해시"
              value={chainValid ? "일치" : "확인 필요"}
              description={evidenceDetail.integrityInfo.hashAlgorithm}
              tone={chainValid ? "safe" : "danger"}
            />
            <IntegrityStatusCard
              label="전자서명"
              value={signatureInfo ? (signatureValid ? "유효" : "확인 필요") : "미서명"}
              description={signatureInfo?.signatureAlgorithm || "발급기관 서명"}
              tone={signatureInfo ? (signatureValid ? "safe" : "danger") : "neutral"}
            />
            <IntegrityStatusCard
              label="블록체인"
              value={
                primaryAnchor || blockchainInfo
                  ? getBlockchainStatusLabel(primaryAnchor?.status ?? blockchainInfo?.status ?? "")
                  : "미앵커"
              }
              description={primaryAnchor?.network || blockchainInfo?.network || "블록체인 앵커링"}
              tone={
                blockchainAnchored
                  ? "safe"
                  : (primaryAnchor?.status ?? blockchainInfo?.status ?? "").toUpperCase() === "FAILED"
                    ? "danger"
                    : "neutral"
              }
            />
            <IntegrityStatusCard
              label="CoC 이력"
              value={`${cocLogs.length}건`}
              description="증거 처리 기록"
              tone="neutral"
            />
          </section>

          <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-none dark:border-border dark:bg-card">
            <div className="relative grid grid-cols-4 border-b border-slate-200 text-center text-sm font-medium text-slate-500 dark:border-border">
              <button
                type="button"
                onClick={() => setIntegrityTab("original")}
                className={cn(
                  "py-4 transition-colors hover:text-slate-950 dark:hover:text-foreground",
                  integrityTab === "original" && "font-semibold text-slate-950 dark:text-foreground"
                )}
              >
                원본 검증
              </button>
              <button
                type="button"
                onClick={() => setIntegrityTab("signature")}
                className={cn(
                  "py-4 transition-colors hover:text-slate-950 dark:hover:text-foreground",
                  integrityTab === "signature" && "font-semibold text-slate-950 dark:text-foreground"
                )}
              >
                전자서명
              </button>
              <button
                type="button"
                onClick={() => setIntegrityTab("blockchain")}
                className={cn(
                  "py-4 transition-colors hover:text-slate-950 dark:hover:text-foreground",
                  integrityTab === "blockchain" && "font-semibold text-slate-950 dark:text-foreground"
                )}
              >
                블록체인
              </button>
              <button
                type="button"
                onClick={() => setIntegrityTab("coc")}
                className={cn(
                  "py-4 transition-colors hover:text-slate-950 dark:hover:text-foreground",
                  integrityTab === "coc" && "font-semibold text-slate-950 dark:text-foreground"
                )}
              >
                증거 이력 ({cocLogs.length})
              </button>

              {/* 활성 탭으로 부드럽게 슬라이드하는 밑줄 */}
              <span
                aria-hidden="true"
                className="pointer-events-none absolute bottom-[-1px] z-10 h-0.5 bg-slate-950 transition-[left] duration-300 ease-out dark:bg-foreground"
                style={{
                  left: `${(integrityTab === "original" ? 0 : integrityTab === "signature" ? 1 : integrityTab === "blockchain" ? 2 : 3) * 25}%`,
                  width: "25%",
                }}
              />
            </div>

            <div className="p-5">
              {integrityTab === "original" ? (
                <>
                  <h2 className="text-lg font-bold text-slate-950 dark:text-foreground">원본 검증</h2>
                  <p className="mt-1 text-sm font-semibold text-slate-500">원본 해시와 현재 해시를 대조합니다.</p>
                  <div className="mt-4 space-y-3">
                    <IntegrityInfoRow label="해시 알고리즘" value={evidenceDetail.integrityInfo.hashAlgorithm} />
                    <IntegrityInfoRow label="원본 SHA-256" value={shortHash(originalHash)} mono copyValue={originalHash} />
                    <IntegrityInfoRow label="현재 SHA-256" value={shortHash(originalHash)} mono copyValue={originalHash} />
                    <IntegrityInfoRow
                      label="검증 결과"
                      value={chainValid ? "원본 해시 일치" : "확인 필요"}
                      accent={chainValid ? "safe" : "danger"}
                    />
                  </div>
                </>
              ) : integrityTab === "signature" ? (
                <>
                  <h2 className="text-lg font-bold text-slate-950 dark:text-foreground">전자서명</h2>
                  <p className="mt-1 text-sm font-semibold text-slate-500">
                    발급기관의 전자서명으로 증거의 진본성을 확인합니다.
                  </p>
                  {signatureInfo ? (
                    <div className="mt-4 space-y-3">
                      <IntegrityInfoRow
                        label="서명 상태"
                        value={signatureSigned ? "서명됨" : "미서명"}
                        accent={signatureSigned ? "safe" : "danger"}
                      />
                      <IntegrityInfoRow
                        label="서명 유효성"
                        value={signatureValid ? "유효" : "확인 필요"}
                        accent={signatureValid ? "safe" : "danger"}
                      />
                      <IntegrityInfoRow label="서명 알고리즘" value={signatureInfo.signatureAlgorithm || "-"} />
                      <IntegrityInfoRow
                        label="서명 시각"
                        value={signatureInfo.signedAt ? formatDateTime(signatureInfo.signedAt) : "-"}
                      />
                      <IntegrityInfoRow
                        label="서명자 인증서"
                        value={signatureInfo.signerCertificateSubject || "-"}
                      />
                    </div>
                  ) : (
                    <p className="mt-5 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
                      전자서명 정보가 없습니다.
                    </p>
                  )}
                </>
              ) : integrityTab === "blockchain" ? (
                <>
                  <h2 className="text-lg font-bold text-slate-950 dark:text-foreground">블록체인 앵커링</h2>
                  <p className="mt-1 text-sm font-semibold text-slate-500">
                    증거·보고서·Merkle Root 해시를 Fabric에 앵커링한 기록입니다. 원본 파일은 온체인에 저장되지 않습니다.
                  </p>
                  {blockchainLoading ? (
                    <LoadingCard label="블록체인 앵커 정보를 불러오는 중입니다..." />
                  ) : blockchainError ? (
                    <Alert variant="destructive" className="mt-4">
                      <AlertCircle className="size-4" />
                      <AlertTitle>블록체인 조회 오류</AlertTitle>
                      <AlertDescription>{blockchainError}</AlertDescription>
                    </Alert>
                  ) : blockchainAnchors.length > 0 ? (
                    <div className="mt-4 space-y-4">
                      <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-4 dark:border-border dark:bg-background">
                        <div className="grid gap-3 md:grid-cols-3">
                          <div>
                            <p className="text-xs font-bold text-slate-400">최종 상태</p>
                            <p
                              className={cn(
                                "mt-1 text-base font-bold",
                                blockchainAnchored ? "text-teal-700" : "text-slate-950 dark:text-foreground"
                              )}
                            >
                              {getBlockchainStatusLabel(
                                primaryAnchor?.status ?? blockchainInfo?.status ?? "NOT_ANCHORED"
                              )}
                            </p>
                          </div>
                          <div>
                            <p className="text-xs font-bold text-slate-400">앵커 수</p>
                            <p className="mt-1 text-base font-bold text-slate-950 dark:text-foreground">
                              {blockchainAnchors.length}건
                            </p>
                          </div>
                          <div>
                            <p className="text-xs font-bold text-slate-400">네트워크</p>
                            <p className="mt-1 text-base font-bold text-slate-950 dark:text-foreground">
                              {primaryAnchor?.network || blockchainInfo?.network || "-"}
                            </p>
                          </div>
                        </div>
                        {primaryAnchor?.certVerified != null || blockchainInfo?.hashValid != null ? (
                          <div className="mt-3 grid gap-3 border-t border-slate-200/80 pt-3 md:grid-cols-2 dark:border-border">
                            {primaryAnchor?.certVerified != null ? (
                              <div>
                                <p className="text-xs font-bold text-slate-400">서명 검증 (원장)</p>
                                <p
                                  className={cn(
                                    "mt-1 text-sm font-bold",
                                    primaryAnchor.certVerified ? "text-teal-700" : "text-rose-600"
                                  )}
                                >
                                  {primaryAnchor.certVerified ? "certVerified = true" : "certVerified = false"}
                                </p>
                              </div>
                            ) : null}
                            {blockchainInfo?.hashValid != null ? (
                              <div>
                                <p className="text-xs font-bold text-slate-400">원본 해시 일치</p>
                                <p
                                  className={cn(
                                    "mt-1 text-sm font-bold",
                                    blockchainInfo.hashValid ? "text-teal-700" : "text-rose-600"
                                  )}
                                >
                                  {blockchainInfo.hashValid ? "일치" : "불일치"}
                                </p>
                              </div>
                            ) : null}
                          </div>
                        ) : null}
                      </div>

                      <div className="space-y-3">
                        {blockchainAnchors.map((anchor) => (
                          <BlockchainAnchorCard
                            key={anchor.id}
                            anchor={anchor}
                            isOpen={openTransactionId === anchor.id}
                            onToggle={() =>
                              setOpenTransactionId((current) => (current === anchor.id ? null : anchor.id))
                            }
                          />
                        ))}
                      </div>
                    </div>
                  ) : (
                    <p className="mt-5 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
                      블록체인 앵커링 정보가 없습니다.
                    </p>
                  )}
                </>
              ) : (
                <>
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <h2 className="text-lg font-bold text-slate-950 dark:text-foreground">CoC 로그</h2>
                      <p className="mt-1 text-sm font-semibold text-slate-500">증거 등록 이후의 처리 이력</p>
                    </div>
                    <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">
                      {cocLogs.length}건
                    </span>
                  </div>
                  {cocLogs.length > 0 ? (
                    <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[11px] font-bold text-slate-400">
                      <span className="inline-flex items-center gap-1.5">
                        <span className="size-2 rounded-full bg-blue-500" /> 진행
                      </span>
                      <span className="inline-flex items-center gap-1.5">
                        <span className="size-2 rounded-full bg-emerald-500" /> 완료·검증
                      </span>
                      <span className="inline-flex items-center gap-1.5">
                        <span className="size-2 rounded-full bg-red-700" /> 실패
                      </span>
                    </div>
                  ) : null}
                  {cocLogs.length > 0 ? (
                    <ol className="mt-6 space-y-0">
                      {cocLogs.map((log, index) => {
                        const actor = getCocActor(log.userId, currentSession)

                        return (
                          <li key={log.logId} className="relative flex gap-4 pb-6 last:pb-0">
                            {index < cocLogs.length - 1 ? (
                              <span
                                className="absolute left-[13px] top-7 -bottom-0 w-px bg-slate-200 dark:bg-border"
                                aria-hidden="true"
                              />
                            ) : null}
                            <span
                              className="relative z-10 mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-white shadow-sm ring-1 ring-slate-200 dark:bg-card dark:ring-border"
                              aria-hidden="true"
                            >
                              <span className={cn("size-2.5 rounded-full", getCocEventDotClass(log))} />
                            </span>
                            <div className="min-w-0 flex-1 rounded-xl border border-slate-100 bg-slate-50/70 px-4 py-3 transition-colors hover:border-slate-200 dark:border-border dark:bg-background">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <div className="flex items-center gap-2">
                                  <p className="text-sm font-bold text-slate-950 dark:text-foreground">
                                    {getCocEventLabel(log.eventType)}
                                  </p>
                                  <span
                                    className={cn(
                                      "rounded-full px-2 py-0.5 text-[10px] font-bold",
                                      actor.roleClass
                                    )}
                                  >
                                    {actor.role}
                                  </span>
                                </div>
                                <time className="text-xs font-semibold text-slate-400">
                                  {formatDateTime(log.createdAt)}
                                </time>
                              </div>
                              {log.description ? (
                                <p className="mt-1.5 text-xs font-medium leading-5 text-slate-500">{log.description}</p>
                              ) : null}
                              <div className="mt-2.5 flex flex-wrap items-center gap-2">
                                <span className="inline-flex items-center gap-1.5 rounded-md bg-white px-2 py-1 ring-1 ring-slate-100 dark:bg-card dark:ring-border">
                                  <UserRound className="size-3 text-slate-400" aria-hidden="true" />
                                  <span className="flex min-w-0 flex-col leading-tight">
                                    <span className="truncate text-[11px] font-bold text-slate-600">
                                      {actor.label}
                                    </span>
                                    {actor.detail ? (
                                      <span className="truncate text-[10px] font-semibold text-slate-400">
                                        {actor.detail}
                                      </span>
                                    ) : null}
                                  </span>
                                </span>
                                <span className="inline-flex max-w-full items-center gap-1.5 rounded-md bg-white px-2 py-1 ring-1 ring-slate-100 dark:bg-card dark:ring-border">
                                  <span className="text-[10px] font-bold uppercase tracking-wide text-slate-300">hash</span>
                                  <span className="truncate font-mono text-[11px] font-semibold text-slate-400">
                                    {shortHash(log.currentLogHash)}
                                  </span>
                                </span>
                              </div>
                            </div>
                          </li>
                        )
                      })}
                    </ol>
                  ) : (
                    <p className="mt-5 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
                      기록된 증거 이력이 없습니다.
                    </p>
                  )}
                </>
              )}
            </div>
          </section>
        </>
      ) : (
        <EmptyEvidenceState />
      )}
    </section>
  )
}

function IntegrityStatusCard({
  label,
  value,
  description,
  tone,
}: {
  label: string
  value: string
  description: string
  tone: "safe" | "danger" | "neutral"
}) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-border dark:bg-card">
      <p className="text-sm font-bold text-slate-500">{label}</p>
      <p
        className={cn(
          "mt-4 text-2xl font-bold",
          tone === "safe" && "text-teal-700",
          tone === "danger" && "text-red-700",
          tone === "neutral" && "text-slate-950 dark:text-foreground"
        )}
      >
        {value}
      </p>
      <p className="mt-2 text-sm font-semibold text-slate-500">{description}</p>
    </div>
  )
}

function IntegrityInfoRow({
  label,
  value,
  mono = false,
  accent,
  copyValue,
}: {
  label: string
  value: string
  mono?: boolean
  accent?: "safe" | "danger"
  copyValue?: string | null
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-slate-100 pb-3 last:border-b-0 last:pb-0 dark:border-border">
      <span className="shrink-0 text-sm font-bold text-slate-500">{label}</span>
      <span className="flex min-w-0 items-center justify-end gap-2">
        <span
          className={cn(
            "min-w-0 truncate text-right text-sm font-bold text-slate-950 dark:text-foreground",
            mono && "font-mono text-xs",
            accent === "safe" && "text-teal-700 dark:text-teal-400",
            accent === "danger" && "text-red-700 dark:text-red-400"
          )}
        >
          {value}
        </span>
        {copyValue ? <CopyIconButton value={copyValue} label={label} /> : null}
      </span>
    </div>
  )
}

type BlockchainAnchorItem = {
  id: string
  title: string
  target: string
  status: string
  statusRaw: string
  subjectHash: string
  transactionId: string | null
  anchoredAt: string | null
  network: string
  channel: string
  chaincode: string
  blockNumber: string | null
  signature: string | null
  signerCertHash: string | null
  certVerified: boolean | null
  offchainLogHash: string | null
  offchainRefJson: string | null
  errorCode: string | null
  reportId: number | null
  merkleBatchDate: string | null
  merkleLeafCount: number | null
  analysisModel: AnalysisModelSnapshot | null
  analysisModules: AnalysisModuleSnapshot[]
  verificationResult: string
  verificationTone: "safe" | "danger" | "neutral"
}

function BlockchainAnchorCard({
  anchor,
  isOpen,
  onToggle,
}: {
  anchor: BlockchainAnchorItem
  isOpen: boolean
  onToggle: () => void
}) {
  const offchainRef = parseOffchainRef(anchor.offchainRefJson)
  const statusTone =
    anchor.statusRaw === "ANCHORED"
      ? "bg-emerald-50 text-teal-700"
      : anchor.statusRaw === "FAILED"
        ? "bg-rose-50 text-rose-700"
        : "bg-slate-100 text-slate-600"

  return (
    <article className="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-border dark:bg-card">
      <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-bold text-slate-950 dark:text-foreground">{anchor.title}</h3>
            <span className={cn("rounded-full px-2.5 py-1 text-[11px] font-bold", statusTone)}>
              {anchor.status}
            </span>
            {anchor.certVerified != null ? (
              <span
                className={cn(
                  "rounded-full px-2.5 py-1 text-[11px] font-bold",
                  anchor.certVerified ? "bg-emerald-50 text-teal-700" : "bg-rose-50 text-rose-700"
                )}
              >
                {anchor.certVerified ? "서명 검증됨" : "서명 미검증"}
              </span>
            ) : null}
            {anchor.analysisModel ? (
              <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-[11px] font-bold text-indigo-700 dark:bg-indigo-950/40 dark:text-indigo-300">
                {anchor.analysisModel.identifier ?? anchor.analysisModel.version}
              </span>
            ) : null}
          </div>
          <p className="mt-1 text-xs font-semibold text-slate-500">{anchor.target}</p>
          <p className="mt-2 font-mono text-xs font-semibold text-slate-400">
            {anchor.transactionId ? shortHash(anchor.transactionId) : "TX 없음"}
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          className="h-9 shrink-0 rounded-lg border-slate-200 bg-white px-3 text-xs font-bold text-slate-700 shadow-none hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground"
          onClick={onToggle}
        >
          상세 보기
          <ChevronRight className={cn("size-3.5 transition-transform", isOpen && "rotate-90")} aria-hidden="true" />
        </Button>
      </div>

      {isOpen ? (
        <div className="border-t border-slate-100 bg-slate-50/80 p-4 dark:border-border dark:bg-background">
          <div className="grid gap-x-8 gap-y-3 md:grid-cols-2">
            <IntegrityInfoRow
              label="TxID"
              value={anchor.transactionId ? shortHash(anchor.transactionId) : "-"}
              mono
              copyValue={anchor.transactionId ?? undefined}
            />
            <IntegrityInfoRow
              label="앵커 해시 (subjectHash)"
              value={shortHash(anchor.subjectHash)}
              mono
              copyValue={anchor.subjectHash}
            />
            <IntegrityInfoRow label="Channel" value={anchor.channel} />
            <IntegrityInfoRow label="Chaincode" value={anchor.chaincode} />
            <IntegrityInfoRow label="Block Number" value={anchor.blockNumber ?? "-"} />
            <IntegrityInfoRow
              label="Timestamp"
              value={anchor.anchoredAt ? formatDateTime(anchor.anchoredAt) : "-"}
            />
            <IntegrityInfoRow label="Network" value={anchor.network} />
            <IntegrityInfoRow
              label="검증 결과"
              value={anchor.verificationResult}
              accent={
                anchor.verificationTone === "safe"
                  ? "safe"
                  : anchor.verificationTone === "danger"
                    ? "danger"
                    : undefined
              }
            />
            {anchor.certVerified != null ? (
              <IntegrityInfoRow
                label="certVerified"
                value={anchor.certVerified ? "true" : "false"}
                accent={anchor.certVerified ? "safe" : undefined}
              />
            ) : null}
            {anchor.signerCertHash ? (
              <IntegrityInfoRow
                label="signerCertHash"
                value={shortHash(anchor.signerCertHash)}
                mono
                copyValue={anchor.signerCertHash}
              />
            ) : null}
            {anchor.signature ? (
              <IntegrityInfoRow
                label="signature"
                value={shortHash(anchor.signature)}
                mono
                copyValue={anchor.signature}
              />
            ) : null}
            {anchor.offchainLogHash ? (
              <IntegrityInfoRow
                label="offchainLogHash"
                value={shortHash(anchor.offchainLogHash)}
                mono
                copyValue={anchor.offchainLogHash}
              />
            ) : null}
            {anchor.reportId != null ? (
              <IntegrityInfoRow label="reportId" value={String(anchor.reportId)} />
            ) : null}
            {anchor.merkleBatchDate ? (
              <IntegrityInfoRow label="merkleBatchDate" value={anchor.merkleBatchDate} />
            ) : null}
            {anchor.merkleLeafCount != null ? (
              <IntegrityInfoRow label="merkleLeafCount" value={String(anchor.merkleLeafCount)} />
            ) : null}
            {anchor.errorCode ? (
              <IntegrityInfoRow label="errorCode" value={anchor.errorCode} />
            ) : null}
            {offchainRef.manifestStoragePath ? (
              <IntegrityInfoRow
                label="manifestStoragePath"
                value={offchainRef.manifestStoragePath}
                copyValue={offchainRef.manifestStoragePath}
              />
            ) : null}
            {offchainRef.originalStoragePath ? (
              <IntegrityInfoRow
                label="originalStoragePath"
                value={offchainRef.originalStoragePath}
                copyValue={offchainRef.originalStoragePath}
              />
            ) : null}
            {offchainRef.reportStoragePath ? (
              <IntegrityInfoRow
                label="reportStoragePath"
                value={offchainRef.reportStoragePath}
                copyValue={offchainRef.reportStoragePath}
              />
            ) : null}
            {offchainRef.custodyLogBundleRef ? (
              <IntegrityInfoRow
                label="custodyLogBundleRef"
                value={offchainRef.custodyLogBundleRef}
                copyValue={offchainRef.custodyLogBundleRef}
              />
            ) : null}
          </div>
          {anchor.analysisModel || anchor.analysisModules.length > 0 ? (
            <div className="mt-4 border-t border-slate-200/80 pt-4 dark:border-border">
              <p className="text-xs font-bold text-slate-400">AI·위변조 분석 모델 스냅샷 (원장)</p>
              {anchor.analysisModel ? (
                <div className="mt-3 grid gap-3 md:grid-cols-3">
                  <IntegrityInfoRow label="모델명" value={anchor.analysisModel.name} />
                  <IntegrityInfoRow label="버전" value={anchor.analysisModel.version} mono />
                  {anchor.analysisModel.identifier ? (
                    <IntegrityInfoRow
                      label="식별자"
                      value={anchor.analysisModel.identifier}
                      mono
                      copyValue={anchor.analysisModel.identifier}
                    />
                  ) : null}
                </div>
              ) : null}
              {anchor.analysisModules.length > 0 ? (
                <div className="mt-3 overflow-hidden rounded-lg border border-slate-200 bg-white dark:border-border dark:bg-card">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-50 text-[11px] font-bold uppercase tracking-wide text-slate-400 dark:bg-background">
                      <tr>
                        <th className="px-3 py-2">모듈</th>
                        <th className="px-3 py-2">이름</th>
                        <th className="px-3 py-2">버전</th>
                      </tr>
                    </thead>
                    <tbody>
                      {anchor.analysisModules.map((module) => (
                        <tr
                          key={`${module.module}-${module.version}`}
                          className="border-t border-slate-100 dark:border-border"
                        >
                          <td className="px-3 py-2 font-mono font-semibold text-slate-600 dark:text-foreground">
                            {module.module || "-"}
                          </td>
                          <td className="px-3 py-2 font-semibold text-slate-700 dark:text-foreground">
                            {module.name || "-"}
                          </td>
                          <td className="px-3 py-2 font-mono font-semibold text-slate-500">
                            {module.version || "-"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}
    </article>
  )
}

function CopyIconButton({ value, label }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1400)
    } catch {
      setCopied(false)
    }
  }

  return (
    <button
      type="button"
      onClick={handleCopy}
      aria-label={label ? `${label} 복사` : "복사"}
      title={copied ? "복사됨" : "복사"}
      className="flex size-6 shrink-0 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-muted"
    >
      {copied ? (
        <Check className="size-3.5 text-emerald-600" aria-hidden="true" />
      ) : (
        <Copy className="size-3.5" aria-hidden="true" />
      )}
    </button>
  )
}

function CaseWorkflowPanel({
  caseData,
  selectedEvidenceId,
  evidenceDetail,
  detailLoading,
  detailError,
  onSelectEvidence,
  onViewResult,
  onViewIntegrity,
  onViewCompareResult,
  onStartCompare,
  onUpdateCaseSettings,
  onRefresh,
  analysisProgressOverrides,
  setAnalysisProgressOverrides,
  analysisPollingMessage,
  reviewRequestOpen,
  onReviewRequestOpenChange,
  currentUserName,
  currentUser = null,
  readOnly = false,
  onReauthenticate,
}: {
  caseData: CaseDetailData
  selectedEvidenceId: number | null
  evidenceDetail: EvidenceDetailData | null
  detailLoading: boolean
  detailError: string | null
  onSelectEvidence: (evidenceId: number) => void
  onViewResult: (evidenceId: number) => void
  onViewIntegrity: (evidenceId: number) => void
  onViewCompareResult: (compareId: number) => void
  onStartCompare: (evidenceId: number) => void
  onUpdateCaseSettings: (
    caseName: string,
    representativeEvidenceId: number | null
  ) => Promise<void>
  onRefresh: () => void
  analysisProgressOverrides: AnalysisProgressOverrides
  setAnalysisProgressOverrides: Dispatch<SetStateAction<AnalysisProgressOverrides>>
  analysisPollingMessage: WorkflowMessage | null
  reviewRequestOpen: boolean
  onReviewRequestOpenChange: (open: boolean) => void
  currentUserName?: string | null
  currentUser?: AppUser | null
  readOnly?: boolean
  onReauthenticate: () => Promise<void>
}) {
  const uploadInputRef = useRef<HTMLInputElement>(null)
  const [actionMode, setActionMode] = useState<"idle" | "analyze" | "exclude" | "replace">("idle")
  const [analysisType, setAnalysisType] = useState<AnalysisType>("DEEPFAKE")
  const [selectedAnalysisIds, setSelectedAnalysisIds] = useState<number[]>([])
  const [baseEvidenceId, setBaseEvidenceId] = useState<number | null>(null)
  const [targetEvidenceId, setTargetEvidenceId] = useState<number | null>(null)
  const [excludeReason, setExcludeReason] = useState("잘못 업로드된 증거로 사용제외 처리")
  const [infoTab, setInfoTab] = useState<"metadata" | "comment">("metadata")
  const [actionMenuOpen, setActionMenuOpen] = useState(false)
  const [editCaseOpen, setEditCaseOpen] = useState(false)
  const [caseNameDraft, setCaseNameDraft] = useState(caseData.caseName)
  const [representativeDraftId, setRepresentativeDraftId] = useState<number | null>(
    caseData.representativeEvidenceId ?? null
  )
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const [analystCommentsByEvidence, setAnalystCommentsByEvidence] = useState<Record<number, string>>({})
  const [reviewerCommentsByEvidence, setReviewerCommentsByEvidence] = useState<Record<number, string>>({})
  const [message, setMessage] = useState<WorkflowMessage | null>(null)
  const [assignmentOpen, setAssignmentOpen] = useState(false)
  const [decisionDialog, setDecisionDialog] = useState<"APPROVED" | "REVISION" | null>(null)
  const [reviewers, setReviewers] = useState<AdminReviewer[]>([])
  const [reviewersLoading, setReviewersLoading] = useState(false)
  const [isWorking, setIsWorking] = useState(false)
  const [reviewedEvidenceIds, setReviewedEvidenceIds] = useState<number[]>([])
  const [readinessByEvidenceId, setReadinessByEvidenceId] = useState<
    Record<number, EvidenceReadinessResponse>
  >({})
  const {
    isCheckingReadiness,
    readinessCheckPhase,
    qualityDialogOpen,
    qualityDialogLoading,
    qualityDialogSummaries,
    qualityDialogWorstTier,
    startAnalysisWithReadiness,
    confirmQualityDialog,
    cancelQualityDialog,
  } = useAnalyzeWithReadiness()

  const analysisBusy = isWorking || isCheckingReadiness || qualityDialogOpen
  const [selectedCompareResult, setSelectedCompareResult] = useState<StoredCompareResultSummary | null>(null)
  const [statusFilter, setStatusFilter] = useState<EvidenceStatusBucket | "all">("all")

  const evidences = useMemo(
    () =>
      caseData.evidences.map((evidence) => {
        const override = analysisProgressOverrides[evidence.evidenceId]
        if (!override) return evidence

        const serverStatus = normalizeStatus(evidence.analysisStatus ?? "PENDING")
        if (serverStatus === "COMPLETED" || serverStatus === "FAILED") return evidence

        const serverProgress = clampAnalysisProgress(evidence.analysisProgress)
        return {
          ...evidence,
          analysisStatus: override.status,
          analysisProgress: Math.max(serverProgress, override.progress),
        }
      }),
    [analysisProgressOverrides, caseData.evidences]
  )
  const activeEvidences = evidences.filter((item) => (item.lifecycleStatus ?? "ACTIVE") === "ACTIVE")
  const bucketCounts = evidences.reduce(
    (counts, evidence) => {
      counts[getEvidenceBucket(evidence)] += 1
      return counts
    },
    { pending: 0, running: 0, completed: 0, inactive: 0 } as Record<EvidenceStatusBucket, number>
  )
  const filteredEvidences = useMemo(
    () =>
      statusFilter === "all"
        ? evidences
        : evidences.filter((evidence) => getEvidenceBucket(evidence) === statusFilter),
    [evidences, statusFilter]
  )
  const selectedEvidence =
    evidences.find((item) => item.evidenceId === selectedEvidenceId) ?? evidences[0] ?? null
  const selectedEvidenceActive = (selectedEvidence?.lifecycleStatus ?? "ACTIVE") === "ACTIVE"
  const selectedEvidenceStatus = normalizeStatus(selectedEvidence?.analysisStatus ?? "PENDING")
  const selectedEvidenceRunning = selectedEvidence
    ? isEvidenceAnalysisRunning(selectedEvidence)
    : false
  const selectedEvidenceProgress = selectedEvidenceRunning
    ? clampAnalysisProgress(selectedEvidence?.analysisProgress)
    : selectedEvidenceStatus === "COMPLETED"
      ? 100
      : 0
  const selectedEvidenceRunningCopy = getRunningAnalysisCopy(
    analysisType,
    selectedEvidenceStatus,
    selectedEvidenceProgress
  )
  const selectedEvidenceCompleted = selectedEvidenceActive && selectedEvidenceStatus === "COMPLETED"
  const selectedEvidenceAnalysisSelectable = selectedEvidence
    ? !readOnly && isEvidenceSelectableForAnalysisType(selectedEvidence, analysisType)
    : false
  const selectedEvidenceRepresentative =
    selectedEvidence != null && caseData.representativeEvidenceId === selectedEvidence.evidenceId
  const selectedEvidenceExcludable = selectedEvidence ? isEvidenceExcludable(selectedEvidence) : false
  const selectableAnalysisEvidences =
    readOnly || analysisType === "COMPARE"
      ? []
      : evidences.filter((evidence) =>
          isEvidenceSelectableForAnalysisType(evidence, analysisType)
        )
  const selectedAnalysisIdSet = new Set(selectedAnalysisIds)
  const selectedAnalysisCount = selectedAnalysisIds.filter((id) =>
    selectableAnalysisEvidences.some((evidence) => evidence.evidenceId === id)
  ).length
  const allSelectableAnalysisSelected =
    selectableAnalysisEvidences.length > 0 &&
    selectableAnalysisEvidences.every((evidence) => selectedAnalysisIdSet.has(evidence.evidenceId))
  const showSelectedEvidenceResultAction = selectedAnalysisCount === 0 && selectedEvidenceCompleted
  const selectedHlsPlayback = evidenceDetail?.hlsPlayback ?? null
  const selectedMetadata = evidenceDetail?.evidenceInfo.technicalMetadata ?? null
  const selectedAnalystComment = selectedEvidence
    ? analystCommentsByEvidence[selectedEvidence.evidenceId] ?? ""
    : ""
  const selectedReviewerComment = selectedEvidence
    ? reviewerCommentsByEvidence[selectedEvidence.evidenceId] ?? ""
    : ""
  const analystName =
    (!readOnly ? currentUserName : null) ??
    getCaseActorName(caseData.assigneeId ?? caseData.createdBy)
  const reviewerName = getCaseActorName(caseData.reviewerId)
  const compareLabel =
    readOnly && !selectedCompareResult
      ? "결과 없음"
      : !selectedCompareResult && !selectedEvidenceCompleted
      ? "분석 전"
      : getCompareVerificationLabel(selectedCompareResult)
  const compareActionLabel = selectedCompareResult ? "상세" : readOnly ? "" : selectedEvidenceCompleted ? "분석" : ""
  const compareTextClassName = !selectedCompareResult
    ? "text-muted-foreground"
    : selectedCompareResult.verdict === "ORIGINAL_MATCH"
      ? "text-emerald-600"
      : selectedCompareResult.verdict === "TAMPERED" || selectedCompareResult.mismatchCount > 0
        ? "text-red-700 dark:text-red-400"
        : "text-amber-600"
  const isReviewerMode = currentUser?.role === "REVIEWER"
  const isAdminMode = currentUser?.role === "ORG_ADMIN"
  const caseReviewStatus = caseData.reviewStatus ?? "NONE"
  const supplementRequested = isSupplementReviewStatus(caseReviewStatus)
  const deepfakeAnalysisReady = isDeepfakeAnalysisReady(evidences)
  const showReviewRequestAction =
    currentUser?.role === "INVESTIGATOR" &&
    isCaseOwner(currentUser, caseData) &&
    caseReviewStatus === "NONE"
  const reviewRequestAllowed =
    showReviewRequestAction && deepfakeAnalysisReady
  const canRequestRereview =
    currentUser?.role === "INVESTIGATOR" &&
    isCaseOwner(currentUser, caseData) &&
    supplementRequested
  const showSupplementBanner = canRequestRereview
  const showAssignmentAction =
    isAdminMode &&
    caseReviewStatus === "REVIEW_REQUESTED" &&
    canAssignReviewer(currentUser, caseData)
  const showDecisionActions =
    isReviewerMode &&
    caseReviewStatus === "REVIEW_ASSIGNED" &&
    isAssignedReviewer(currentUser, caseData)
  const showEvidenceActionFooter = !readOnly || selectedEvidenceCompleted || isReviewerMode
  const latestSupplementRound =
    [...(caseData.reviewRounds ?? [])].reverse().find((round) => round.decision === "REVISION") ?? null
  const supplementReason = latestSupplementRound?.reason ?? caseData.reviewerComment ?? ""
  const supplementReviewerName = latestSupplementRound?.reviewerName ?? reviewerName ?? ""
  const supplementRequestedAt = latestSupplementRound?.decidedAt ?? null
  const unreadReviewEvidences = activeEvidences.filter(
    (evidence) => !reviewedEvidenceIds.includes(evidence.evidenceId)
  )
  const unreadReviewEvidenceLabels = unreadReviewEvidences.map(
    (evidence) => `증거 ${activeEvidences.findIndex((item) => item.evidenceId === evidence.evidenceId) + 1}`
  )
  const reviewedEvidenceCount = activeEvidences.length - unreadReviewEvidenceLabels.length
  const caseReviewSummary = `${caseData.caseName} · 증거 ${activeEvidences.length}개`

  useEffect(() => {
    if (!isReviewerMode || !selectedEvidence || !selectedEvidenceActive) return

    setReviewedEvidenceIds((current) =>
      current.includes(selectedEvidence.evidenceId) ? current : [...current, selectedEvidence.evidenceId]
    )
  }, [isReviewerMode, selectedEvidence?.evidenceId, selectedEvidenceActive])

  useEffect(() => {
    if (!selectedEvidence) {
      setSelectedCompareResult(null)
      return
    }

    setSelectedCompareResult(getLatestCompareResultSummary(selectedEvidence.evidenceId))
  }, [selectedEvidence])

  useEffect(() => {
    if (!assignmentOpen) return
    let cancelled = false
    setReviewersLoading(true)

    void fetchAdminReviewers({ uploaderId: caseData.createdBy })
      .then((items) => {
        if (!cancelled) setReviewers(items)
      })
      .catch((error) => {
        if (!cancelled) {
          setReviewers([])
          setMessage({
            type: "error",
            text: getApiErrorMessage(error, "검토자 배정"),
          })
        }
      })
      .finally(() => {
        if (!cancelled) setReviewersLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [assignmentOpen, caseData.createdBy])

  useEffect(() => {
    if (!selectedEvidence) return

    const evidenceId = selectedEvidence.evidenceId
    let cancelled = false

    void fetchEvidenceReadiness(evidenceId)
      .then((readiness) => {
        if (cancelled) return
        setReadinessByEvidenceId((current) =>
          current[evidenceId] ? current : { ...current, [evidenceId]: readiness }
        )
      })
      .catch(() => {})

    return () => {
      cancelled = true
    }
  }, [selectedEvidence?.evidenceId])

  useEffect(() => {
    if (message?.type !== "success") return

    const timer = window.setTimeout(() => {
      setMessage((current) => (current?.type === "success" ? null : current))
    }, 2200)

    return () => window.clearTimeout(timer)
  }, [message])

  useEffect(() => {
    const selectableIds = new Set(selectableAnalysisEvidences.map((evidence) => evidence.evidenceId))
    setSelectedAnalysisIds((current) => {
      const next = current.filter((id) => selectableIds.has(id))
      return next.length === current.length ? current : next
    })
  }, [evidences])

  useEffect(() => {
    if (statusFilter === "running" && bucketCounts.running === 0) {
      setStatusFilter("all")
    }
  }, [statusFilter, bucketCounts.running])

  useEffect(() => {
    if (selectedAnalysisCount === 0) return

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setSelectedAnalysisIds([])
      }
    }

    window.addEventListener("keydown", handleEscape)
    return () => window.removeEventListener("keydown", handleEscape)
  }, [selectedAnalysisCount])

  async function runAction(
    action: () => Promise<void>,
    successText: string,
    options: { showSuccess?: boolean; refresh?: boolean } = {}
  ) {
    setIsWorking(true)
    setMessage(null)

    try {
      await action()
      if (options.showSuccess !== false) {
        setMessage({ type: "success", text: successText })
      }
      if (options.refresh !== false) {
        onRefresh()
      }
      return true
    } catch (error) {
      setMessage({
        type: "error",
        text: error instanceof Error ? error.message : "작업 처리 중 오류가 발생했습니다.",
      })
      return false
    } finally {
      setIsWorking(false)
    }
  }

  async function handleUploadFiles(files: FileList | null) {
    if (readOnly) return

    const selectedFiles = Array.from(files ?? [])
    if (selectedFiles.length === 0) return

    await runAction(async () => {
      let firstEvidenceId: number | null = null
      for (const file of selectedFiles) {
        const result = await uploadEvidenceToCase(caseData.caseId, caseData.caseName, file)
        if (result.readiness) {
          setReadinessByEvidenceId((current) => ({
            ...current,
            [result.evidenceId]: result.readiness!,
          }))
        }
        firstEvidenceId ??= result.evidenceId
      }
      if (firstEvidenceId) onSelectEvidence(firstEvidenceId)
    }, "", { showSuccess: false })

    if (uploadInputRef.current) uploadInputRef.current.value = ""
  }

  function toggleAnalysisEvidence(evidenceId: number) {
    if (readOnly) return

    const target = evidences.find((evidence) => evidence.evidenceId === evidenceId)
    if (!target || !isEvidenceSelectableForAnalysisType(target, analysisType)) return

    setSelectedAnalysisIds((current) =>
      current.includes(evidenceId)
        ? current.filter((id) => id !== evidenceId)
        : [...current, evidenceId]
    )
  }

  function toggleAllSelectableAnalysis() {
    if (readOnly) return

    if (allSelectableAnalysisSelected) {
      setSelectedAnalysisIds([])
      return
    }

    setSelectedAnalysisIds(selectableAnalysisEvidences.map((evidence) => evidence.evidenceId))
  }

  async function handleStartAnalysis() {
    if (readOnly) return

    if (activeEvidences.length === 0) {
      setMessage({ type: "error", text: "분석 가능한 활성 증거가 없습니다." })
      return
    }

    if (analysisType === "COMPARE") {
      if (!baseEvidenceId || !targetEvidenceId || baseEvidenceId === targetEvidenceId) {
        setMessage({ type: "error", text: "비교검증은 서로 다른 기준 증거와 비교 대상 증거를 선택해야 합니다." })
        return
      }
    }

    const selectedSelectableAnalysisIds = selectedAnalysisIds.filter((id) =>
      selectableAnalysisEvidences.some((evidence) => evidence.evidenceId === id)
    )
    const fallbackAnalysisIds =
      selectedEvidence && isEvidenceSelectableForAnalysis(selectedEvidence)
        ? [selectedEvidence.evidenceId]
        : selectableAnalysisEvidences.length === 1
          ? [selectableAnalysisEvidences[0].evidenceId]
          : []

    if (analysisType !== "COMPARE" && selectedSelectableAnalysisIds.length === 0 && fallbackAnalysisIds.length === 0) {
      setMessage({ type: "error", text: "분석할 증거를 1개 이상 선택해 주세요." })
      return
    }

    const targetIds =
      analysisType === "COMPARE"
        ? [baseEvidenceId, targetEvidenceId].filter((id): id is number => typeof id === "number")
        : selectedSelectableAnalysisIds.length > 0
          ? selectedSelectableAnalysisIds
          : fallbackAnalysisIds

    const targetEvidenceSummaries = targetIds
      .map((id) => evidences.find((evidence) => evidence.evidenceId === id))
      .filter((evidence): evidence is CaseEvidenceSummary => evidence != null)

    setMessage(null)

    if (analysisType === "COMPARE" && baseEvidenceId && targetEvidenceId) {
      await runAction(async () => {
        const result = await verifyRegisteredCompare(baseEvidenceId, targetEvidenceId)
        saveCompareResultSummary(result)
        setActionMode("idle")
        onViewCompareResult(result.compareId)
      }, "등록된 증거 간 비교검증이 완료되었습니다.", { refresh: false })
      return
    }

    if (analysisType === "INTEGRITY") {
      const verified = await runAction(async () => {
        await Promise.all(targetIds.map((evidenceId) => verifyEvidenceIntegrity(evidenceId)))
      }, "무결성 검증이 완료되었습니다.", { refresh: false })

      if (verified && targetIds[0]) {
        onSelectEvidence(targetIds[0])
        setSelectedAnalysisIds([])
        setActionMode("idle")
        onViewIntegrity(targetIds[0])
      }
      return
    }

    await startAnalysisWithReadiness({
      targets: targetEvidenceSummaries.map(readinessTargetFromCaseEvidence),
      onReadinessChecked: (summaries) => {
        setReadinessByEvidenceId((current) => {
          const next = { ...current }
          for (const item of summaries) {
            next[item.evidenceId] = item.readiness
          }
          return next
        })
      },
      runAnalyze: (ack) =>
        startCaseAnalysis(
          {
            caseId: caseData.caseId,
            caseName: caseData.caseName,
            analysisType,
            evidenceIds: targetIds,
            baseEvidenceId,
            targetEvidenceId,
          },
          { acknowledgeQualityWarning: ack || undefined }
        ),
      onSuccess: () => {
        setAnalysisProgressOverrides((current) => {
          const next = { ...current }
          for (const evidenceId of targetIds) {
            next[evidenceId] = {
              status: "PROCESSING",
              progress: current[evidenceId]?.progress ?? 0,
            }
          }
          return next
        })
        if (targetIds[0]) onSelectEvidence(targetIds[0])
        setSelectedAnalysisIds([])
        setActionMode("idle")
        const notificationParams = new URLSearchParams(window.location.search)
        if (targetIds[0]) notificationParams.set("evidenceId", String(targetIds[0]))
        notificationParams.set("view", "result")
        addAppNotification({
          title: `${getAnalysisTypeLabel(analysisType)} 요청 접수`,
          description: `${caseData.caseName} 사건의 증거 ${targetIds.length}개가 분석 대기열에 등록되었습니다.`,
          href: `${window.location.pathname}?${notificationParams.toString()}`,
        })
        onRefresh()
      },
      onError: (error) => {
        setMessage({
          type: "error",
          text: getErrorMessage(error, "분석 요청에 실패했습니다."),
        })
      },
    })
  }

  async function handleCancelSelectedAnalysis() {
    if (readOnly) return

    if (!selectedEvidence || !selectedEvidenceRunning) return

    const confirmed = window.confirm(
      `${formatEvidenceTitle(selectedEvidence)} 분석을 중단하시겠습니까?\n원본 증거와 등록 이력은 유지됩니다.`
    )
    if (!confirmed) return

    await runAction(async () => {
      await cancelCaseAnalysis(selectedEvidence.evidenceId)
      onSelectEvidence(selectedEvidence.evidenceId)
    }, "", { showSuccess: false })
  }

  function handleViewIntegrityCheck() {
    if (!selectedEvidence) return
    onViewIntegrity(selectedEvidence.evidenceId)
  }

  function handleStartCompareVerification() {
    if (!selectedEvidence || !selectedEvidenceActive) return
    if (!selectedEvidenceCompleted) {
      setMessage({ type: "info", text: "딥페이크 분석 완료 후 비교검증에 사용할 수 있습니다." })
      return
    }
    onStartCompare(selectedEvidence.evidenceId)
  }

  async function handleSaveCaseSettings() {
    if (readOnly) return

    const nextName = caseNameDraft.trim()
    if (!nextName) {
      setMessage({ type: "error", text: "사건명을 입력해 주세요." })
      return
    }

    const saved = await runAction(
      () => onUpdateCaseSettings(nextName, representativeDraftId),
      "사건 정보가 수정되었습니다. 원본 증거와 CoC 기록은 변경되지 않습니다.",
      { refresh: false }
    )
    if (saved) setEditCaseOpen(false)
  }

  async function handleConfirmDeleteEvidence() {
    if (readOnly) return

    if (!selectedEvidence || !selectedEvidenceActive) return
    if (selectedEvidenceRepresentative && activeEvidences.length > 1) {
      setDeleteConfirmOpen(false)
      setMessage({
        type: "error",
        text: "대표는 사용제외할 수 없습니다. 대표 증거를 먼저 변경해 주세요.",
      })
      return
    }

    const nextActiveEvidence = activeEvidences.find((evidence) => evidence.evidenceId !== selectedEvidence.evidenceId)

    await runAction(async () => {
      await markEvidenceExcluded(selectedEvidence.evidenceId, "화면에서 사용제외 처리")
      if (nextActiveEvidence) onSelectEvidence(nextActiveEvidence.evidenceId)
    }, `${formatEvidenceTitle(selectedEvidence)}가 사용제외 처리되었습니다.`)

    setDeleteConfirmOpen(false)
  }

  async function handleReviewDecision(nextDecision: "APPROVED" | "REVISION", reason: string) {
    if (isWorking) return

    setIsWorking(true)
    setMessage(null)
    try {
      const updated = await recordCaseReviewDecision(
        caseData.caseId,
        nextDecision,
        reason
      )
      setDecisionDialog(null)
      setMessage({
        type: "success",
        text: nextDecision === "APPROVED" ? "검토가 승인되었습니다" : "보완 요청을 보냈습니다",
      })
      addAppNotification({
        title: nextDecision === "APPROVED" ? "검토가 승인되었습니다" : "보완 요청을 보냈습니다",
        description: caseData.caseName,
        href: window.location.pathname,
      })
      if (updated.reviewStatus !== caseData.reviewStatus) {
        onRefresh()
      }
    } catch (error) {
      setMessage({
        type: "error",
        text: getApiErrorMessage(error, "검토 결과를 저장하지 못했습니다."),
      })
    } finally {
      setIsWorking(false)
    }
  }

  async function handleRequestReview() {
    if (
      isWorking ||
      (!reviewRequestAllowed && !canRequestRereview)
    ) {
      return
    }

    setIsWorking(true)
    setMessage(null)
    try {
      await requestCaseReview(caseData.caseId)
      onReviewRequestOpenChange(false)
      setMessage({
        type: "success",
        text: canRequestRereview ? "재검토 요청" : "검토 요청",
      })
      addAppNotification({
        title: canRequestRereview ? "재검토 요청" : "검토 요청",
        description: caseData.caseName,
        href: window.location.pathname,
      })
      onRefresh()
    } catch (error) {
      setMessage({
        type: "error",
        text: getApiErrorMessage(error, "검토 요청에 실패했습니다."),
      })
    } finally {
      setIsWorking(false)
    }
  }

  async function handleAssignReviewer(reviewer: AdminReviewer) {
    if (isWorking || !showAssignmentAction) return

    setIsWorking(true)
    setMessage(null)
    try {
      await assignAdminCaseReviewer(caseData.caseId, reviewer.id, caseData.createdBy)
      setAssignmentOpen(false)
      setMessage({ type: "success", text: `${reviewer.name}에게 배정` })
      addAppNotification({
        title: "검토자 배정",
        description: `${caseData.caseName} · ${reviewer.name}`,
        href: window.location.pathname,
      })
      onRefresh()
    } catch (error) {
      setMessage({
        type: "error",
        text: getApiErrorMessage(error, "검토자 배정"),
      })
    } finally {
      setIsWorking(false)
    }
  }

  const visibleMessage = analysisPollingMessage ?? message

  return (
    <>
      {showSupplementBanner ? (
        <section
          aria-label="보완 요청됨"
          className="flex min-h-12 flex-col gap-3 border-l-[3px] border-red-600 bg-red-50 px-4 py-2.5 sm:flex-row sm:items-center sm:justify-between"
        >
          <div className="flex min-w-0 items-center gap-2 text-sm font-semibold text-red-700">
            <Flag className="size-4 shrink-0" aria-hidden="true" />
            <p className="min-w-0 truncate">
              보완 요청됨 — {supplementReason ? `“${supplementReason}”` : ""}
              {supplementReviewerName ? ` · 검토자 ${supplementReviewerName}` : ""}
              {supplementRequestedAt ? ` · ${formatReviewEventDate(supplementRequestedAt)}` : ""}
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            className="h-9 shrink-0 border-red-200 bg-white px-3 text-sm font-bold text-red-700 hover:bg-red-50"
            disabled={isWorking}
            onClick={() => onReviewRequestOpenChange(true)}
          >
            보완 완료 · 재검토 요청
          </Button>
        </section>
      ) : null}

      <section className="relative rounded-xl border border-border bg-card p-3 shadow-sm sm:p-5">
      {!readOnly ? (
        <input
          ref={uploadInputRef}
          type="file"
          multiple
          accept="video/*"
          className="sr-only"
          onChange={(event) => void handleUploadFiles(event.target.files)}
        />
      ) : null}
      {visibleMessage ? (
        <div
          className={cn(
            "mt-4 flex items-center gap-2 rounded-lg border px-4 py-3 text-sm font-bold",
            visibleMessage.type === "success"
              ? "border-slate-200 bg-slate-50 text-slate-700"
              : visibleMessage.type === "info"
                ? "border-slate-200 bg-slate-50 text-slate-700"
                : "border-red-700/25 bg-red-50 text-red-700"
          )}
        >
          {visibleMessage.type === "success" ? (
            <CheckCircle2 className="size-4" aria-hidden="true" />
          ) : (
            <AlertCircle className="size-4" aria-hidden="true" />
          )}
          {visibleMessage.text}
        </div>
      ) : null}

      <div className="mt-3 sm:mt-4 xl:mt-0">
        {evidences.length === 0 ? (
          readOnly ? (
            <div className="rounded-xl border border-dashed border-border px-5 py-12 text-center text-sm font-bold text-muted-foreground">
              아직 등록된 증거가 없습니다.
            </div>
          ) : (
            <button
              type="button"
              className="flex w-full items-center justify-center rounded-xl border border-dashed border-border px-5 py-12 text-center text-sm font-bold text-muted-foreground transition-colors hover:border-slate-300 hover:bg-muted/30 hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
              disabled={isWorking}
              onClick={() => uploadInputRef.current?.click()}
            >
              아직 등록된 증거가 없습니다. 증거 영상을 업로드하세요.
            </button>
          )
        ) : (
          <div className="flex flex-col gap-4 lg:flex-row lg:gap-5">
            <div className="flex flex-col bg-white dark:bg-card lg:w-72 lg:shrink-0 lg:border-r lg:border-slate-200/80 lg:pr-4 lg:dark:border-border xl:w-64">
              <div className="relative flex items-center justify-between gap-3 px-2 pb-2 pt-1">
                <div className="flex items-baseline gap-2">
                  <h2 className="text-[22px] font-bold text-foreground">증거</h2>
                  <span className="text-base font-bold text-muted-foreground">{evidences.length}개</span>
                </div>
                {!readOnly ? (
                  <div className="relative">
                    <button
                      type="button"
                      className="flex size-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
                      disabled={isWorking}
                      aria-label="증거 작업 더보기"
                      aria-expanded={actionMenuOpen}
                      onClick={() => setActionMenuOpen((open) => !open)}
                    >
                      <MoreVertical className="size-4" aria-hidden="true" />
                    </button>
                    {actionMenuOpen ? (
                      <div className="absolute right-0 top-10 z-30 w-48 overflow-hidden rounded-xl border border-slate-200 bg-white p-1 text-sm font-bold shadow-[0_18px_40px_rgba(15,23,42,0.16)] ring-1 ring-slate-950/5 dark:border-border dark:bg-card">
                        <button
                          type="button"
                          className="flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2.5 text-left text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
                          onClick={() => {
                            setActionMenuOpen(false)
                            setCaseNameDraft(caseData.caseName)
                            setRepresentativeDraftId(caseData.representativeEvidenceId ?? selectedEvidence?.evidenceId ?? null)
                            setEditCaseOpen(true)
                            setDeleteConfirmOpen(false)
                          }}
                        >
                          사건 수정
                          <Pencil className="size-4" aria-hidden="true" />
                        </button>
                        <button
                          type="button"
                          className="flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2.5 text-left text-red-700 transition-colors hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
                          disabled={!selectedEvidence || isWorking || !selectedEvidenceExcludable}
                          onClick={() => {
                            setActionMenuOpen(false)
                            if (selectedEvidenceRepresentative && activeEvidences.length > 1) {
                              setMessage({
                                type: "error",
                                text: "대표는 사용제외할 수 없습니다. 대표 증거를 먼저 변경해 주세요.",
                              })
                              return
                            }
                            setDeleteConfirmOpen(true)
                          }}
                        >
                          사용제외
                          <Trash2 className="size-4" aria-hidden="true" />
                        </button>
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </div>

              {!readOnly && selectableAnalysisEvidences.length > 0 ? (
                <div className="mb-2 flex items-center justify-between gap-2 px-2">
                  <button
                    type="button"
                    className="inline-flex h-7 items-center gap-1 rounded-md px-1.5 text-[12px] font-bold text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={isWorking}
                    onClick={toggleAllSelectableAnalysis}
                  >
                    <span
                      className={cn(
                        "flex size-3.5 items-center justify-center rounded-[4px] border",
                        allSelectableAnalysisSelected
                          ? "border-foreground bg-foreground text-background"
                          : "border-slate-300 bg-white"
                      )}
                      aria-hidden="true"
                    >
                      {allSelectableAnalysisSelected ? <Check className="size-2.5" strokeWidth={3} /> : null}
                    </span>
                    전체 선택
                  </button>
                  {selectedAnalysisCount > 0 ? (
                    <span className="text-[12px] font-bold text-muted-foreground">
                      {selectedAnalysisCount}개 선택
                    </span>
                  ) : null}
                </div>
              ) : null}

              <div className="flex max-h-[384px] min-h-[220px] flex-col overflow-y-auto overscroll-contain pr-1">
                {filteredEvidences.length === 0 ? (
                  <p className="flex min-h-[220px] items-center justify-center px-3 text-center text-[13px] font-bold text-muted-foreground">
                    해당 상태의 증거가 없습니다.
                  </p>
                ) : (
                  filteredEvidences.map((evidence) => (
                    <EvidenceListRow
                      key={evidence.evidenceId}
                      evidence={evidence}
                      active={selectedEvidence?.evidenceId === evidence.evidenceId}
                      representative={caseData.representativeEvidenceId === evidence.evidenceId}
                      disabled={(evidence.lifecycleStatus ?? "ACTIVE") !== "ACTIVE"}
                      running={getEvidenceBucket(evidence) === "running"}
                      analysisSelectable={
                        !readOnly && isEvidenceSelectableForAnalysisType(evidence, analysisType)
                      }
                      analysisSelected={selectedAnalysisIdSet.has(evidence.evidenceId)}
                      selectionMode={selectedAnalysisCount > 0}
                      onToggleAnalysisSelect={() => toggleAnalysisEvidence(evidence.evidenceId)}
                      onViewResult={() => onViewResult(evidence.evidenceId)}
                      onSelect={() => {
                        if ((evidence.lifecycleStatus ?? "ACTIVE") !== "ACTIVE") return
                        onSelectEvidence(evidence.evidenceId)
                        setActionMode("idle")
                        setActionMenuOpen(false)
                        setEditCaseOpen(false)
                        setDeleteConfirmOpen(false)
                      }}
                    />
                  ))
                )}
              </div>

            </div>

            {selectedEvidence ? (
              <div className="flex min-w-0 flex-1 flex-col lg:pb-4">
                <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
                  <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-1">
                    <h3 className="truncate text-lg font-bold text-foreground">
                      {formatEvidenceTitle(selectedEvidence)}
                    </h3>
                    <span className="font-mono text-[13px] font-bold text-muted-foreground">
                      EVD-{selectedEvidence.evidenceId}
                    </span>
                    {(selectedEvidence.lifecycleStatus ?? "ACTIVE") !== "ACTIVE" ? (
                      <span className={cn("rounded-full px-2 py-0.5 text-[13px] font-bold", getLifecycleClassName(selectedEvidence.lifecycleStatus ?? "ACTIVE"))}>
                        {getLifecycleLabel(selectedEvidence.lifecycleStatus ?? "ACTIVE")}
                      </span>
                    ) : null}
                  </div>
                  <div className="flex w-full items-center gap-2 sm:w-auto">
                    <div className="relative grid w-full grid-cols-2 rounded-full bg-muted/60 p-1 text-[13px] font-bold sm:w-auto">
                      <span
                        className={cn(
                          "absolute inset-y-1 left-1 w-[calc(50%-0.25rem)] rounded-full border border-border bg-card shadow-sm transition-transform duration-200 ease-out",
                          infoTab === "comment" && "translate-x-full"
                        )}
                        aria-hidden="true"
                      />
                      <button
                        type="button"
                        onClick={() => setInfoTab("metadata")}
                        className={cn(
                          "relative z-10 rounded-full px-3 py-1.5 transition-colors duration-200 sm:px-4",
                          infoTab === "metadata" ? "text-foreground" : "text-muted-foreground hover:text-foreground"
                        )}
                      >
                        메타데이터
                      </button>
                      <button
                        type="button"
                        onClick={() => setInfoTab("comment")}
                        className={cn(
                          "relative z-10 rounded-full px-3 py-1.5 transition-colors duration-200 sm:px-4",
                          infoTab === "comment" ? "text-foreground" : "text-muted-foreground hover:text-foreground"
                        )}
                      >
                        코멘트
                      </button>
                    </div>
                  </div>
                </div>

                <div className="mt-3 grid min-h-[430px] grid-cols-1 gap-5 xl:grid-cols-[minmax(0,58%)_minmax(0,1fr)] xl:items-start">
                  <div className="relative aspect-video w-full overflow-hidden rounded-lg bg-slate-950">
                {detailLoading && !selectedHlsPlayback ? (
                  <div className="flex size-full items-center justify-center text-[15px] font-bold text-white/70">
                    <Loader2 className="mr-2 size-4 animate-spin" aria-hidden="true" />
                    영상 정보를 불러오는 중
                  </div>
                ) : selectedHlsPlayback || evidenceDetail ? (
                  <ProtectedEvidencePlayer
                    playback={selectedHlsPlayback}
                    objectFit="contain"
                    onReauthenticate={onReauthenticate}
                  >
                    <EvidenceWatermarkOverlay
                      caseId={caseData.caseId}
                      evidenceId={selectedEvidence.evidenceId}
                      viewerName={currentUserName}
                      compact
                    />
                  </ProtectedEvidencePlayer>
                ) : (
                  <div className="flex size-full flex-col items-center justify-center text-[15px] font-bold text-white/60">
                    <FileVideo className="mb-3 size-8" aria-hidden="true" />
                    미리보기 가능한 영상이 없습니다.
                  </div>
                )}
                  </div>

                  <div className="min-h-0 min-w-0 xl:h-[430px] xl:overflow-y-auto xl:border-l xl:border-border xl:pl-5">
            {infoTab === "metadata" ? (
              <div>
                {readinessByEvidenceId[selectedEvidence.evidenceId] ? (
                  <div className="mb-3">
                    <ReadinessBadge
                      tier={readinessByEvidenceId[selectedEvidence.evidenceId].readinessTier}
                    />
                  </div>
                ) : null}
              <div className="min-h-0 xl:min-h-[372px]">
                <dl className="space-y-3">
                  <CaseMetadataRow label="파일 유형" value={selectedEvidence.mediaType || "-"} />
                  <CaseMetadataRow
                    label="해상도"
                    value={
                      selectedMetadata?.width && selectedMetadata?.height
                        ? `${selectedMetadata.width} x ${selectedMetadata.height}`
                        : "-"
                    }
                  />
                  <CaseMetadataRow
                    label="재생 시간"
                    value={selectedMetadata?.durationSec != null ? formatDuration(selectedMetadata.durationSec) : "-"}
                  />
                  <CaseMetadataRow
                    label="프레임레이트"
                    value={selectedMetadata?.fps != null ? `${selectedMetadata.fps} fps` : "-"}
                  />
                  <CaseMetadataRow label="코덱" value={selectedMetadata?.codec || "-"} />
                  <CaseMetadataRow
                    label="원본 파일명"
                    value={selectedEvidence.originalFileName ?? selectedEvidence.fileName}
                  />
                </dl>

                {selectedEvidenceActive ? (
                  <div className="mt-4 space-y-4 border-t border-border pt-4">
                    <button
                      type="button"
                      className="group flex w-full items-center justify-between gap-4 text-left"
                      onClick={handleViewIntegrityCheck}
                    >
                      <span className="inline-flex shrink-0 items-center gap-1.5 text-[15px] font-bold text-muted-foreground">
                        <ShieldCheck className="size-4" aria-hidden="true" />
                        무결성 검증
                      </span>
                      <span className="flex min-w-0 items-center gap-1 text-[15px] font-bold">
                        <span className="text-emerald-600">해시값 일치</span>
                        <span className="text-muted-foreground transition-colors group-hover:text-foreground">· 상세</span>
                        <ChevronRight
                          className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5"
                          aria-hidden="true"
                        />
                      </span>
                    </button>

                    <button
                      type="button"
                      className="group flex w-full items-center justify-between gap-4 text-left disabled:cursor-not-allowed disabled:opacity-50"
                      disabled={isWorking || (readOnly && !selectedCompareResult)}
                      onClick={
                        selectedCompareResult
                          ? () => onViewCompareResult(selectedCompareResult.compareId)
                          : handleStartCompareVerification
                      }
                    >
                      <span className="inline-flex shrink-0 items-center gap-1.5 text-[15px] font-bold text-muted-foreground">
                        <GitCompare className="size-4" aria-hidden="true" />
                        비교검증
                      </span>
                      <span className="flex min-w-0 items-center gap-1 text-[15px] font-bold">
                        <span className={compareTextClassName}>{compareLabel}</span>
                        {compareActionLabel ? (
                          <span className="text-muted-foreground transition-colors group-hover:text-foreground">
                            · {compareActionLabel}
                          </span>
                        ) : null}
                        <ChevronRight
                          className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5"
                          aria-hidden="true"
                        />
                      </span>
                    </button>

                  </div>
                ) : null}
              </div>
              </div>
            ) : (
              <div className="min-h-0 space-y-3 xl:min-h-[372px]">
                <div>
                  <label
                    htmlFor="caseAnalystComment"
                    className="flex items-center gap-2 text-sm font-bold text-foreground"
                  >
                    <MessageSquareText className="size-4 text-slate-500" aria-hidden="true" />
                    {analystName ?? "분석관"}
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-bold text-slate-600">
                      분석관
                    </span>
                  </label>
                  <textarea
                    id="caseAnalystComment"
                    value={selectedAnalystComment}
                    readOnly={isReviewerMode}
                    onChange={(event) => {
                      if (isReviewerMode) return

                      setAnalystCommentsByEvidence((current) => ({
                        ...current,
                        [selectedEvidence.evidenceId]: event.target.value,
                      }))
                    }}
                    className="mt-2 h-[92px] w-full resize-none overflow-y-auto rounded-lg border border-border bg-background px-3 py-2.5 text-sm font-medium leading-5 text-foreground outline-none transition-colors placeholder:text-muted-foreground focus:border-slate-300 read-only:bg-muted/30"
                  />
                </div>

                <div>
                  <label
                    htmlFor="caseReviewerComment"
                    className="flex items-center gap-2 text-sm font-bold text-foreground"
                  >
                    <MessageSquareText className="size-4 text-slate-500" aria-hidden="true" />
                    {reviewerName ?? "검토관"}
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-bold text-slate-600">
                      검토관
                    </span>
                  </label>
                  <textarea
                    id="caseReviewerComment"
                    value={selectedReviewerComment}
                    readOnly={!isReviewerMode}
                    onChange={(event) => {
                      if (!isReviewerMode) return
                      setReviewerCommentsByEvidence((current) => ({
                        ...current,
                        [selectedEvidence.evidenceId]: event.target.value,
                      }))
                    }}
                    className="mt-2 h-[92px] w-full resize-none overflow-y-auto rounded-lg border border-border bg-background px-3 py-2.5 text-sm font-medium leading-5 text-foreground outline-none transition-colors placeholder:text-muted-foreground focus:border-slate-300 read-only:bg-muted/30"
                  />
                </div>
              </div>
            )}

                  </div>
                </div>

                {detailError ? (
                  <p className="mt-3 rounded-lg border border-border bg-muted/30 px-3 py-2 text-[13px] font-bold text-muted-foreground">
                    상세 정보를 불러오지 못해 목록 기준 정보만 표시 중입니다.
                  </p>
                ) : null}

                {readOnly && !selectedEvidenceCompleted ? (
                  <p className="mt-4 text-[13px] font-bold text-muted-foreground">
                    검토 가능한 분석 결과가 아직 없습니다.
                  </p>
                ) : null}
              </div>
            ) : null}
          </div>
        )}
      </div>

      {showEvidenceActionFooter ? (
        <div className="-mx-3 mt-4 flex flex-col items-stretch gap-3 border-t border-slate-200/80 px-3 pt-4 dark:border-border sm:-mx-5 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between sm:px-5 xl:mt-0">
            <div className="flex w-full flex-wrap items-center gap-2 sm:flex-1">
              {!readOnly ? (
                <>
                  <Button
                    type="button"
                    variant="ghost"
                    className="h-10 w-full rounded-lg px-3 text-sm font-bold text-muted-foreground hover:text-foreground sm:w-auto"
                    disabled={isWorking}
                    onClick={() => uploadInputRef.current?.click()}
                  >
                    <Plus className="size-4" aria-hidden="true" />
                    증거 추가
                  </Button>
                  {selectedAnalysisCount > 0 ? (
                    <Button
                      type="button"
                      variant="ghost"
                      className="h-10 w-full rounded-lg px-3 text-sm font-bold text-muted-foreground hover:text-foreground sm:w-auto"
                      disabled={isWorking}
                      onClick={() => setSelectedAnalysisIds([])}
                    >
                      선택 해제 {selectedAnalysisCount}
                    </Button>
                  ) : null}
                </>
              ) : isReviewerMode ? (
                <p className="px-3 text-sm font-bold text-muted-foreground">
                  증거 {activeEvidences.length}개 중 {reviewedEvidenceCount}개 열람
                </p>
              ) : null}
            </div>
            <div className="ml-0 flex w-full shrink-0 flex-col items-stretch justify-end gap-3 sm:ml-auto sm:w-auto sm:flex-row sm:items-center">
              {isReviewerMode ? (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    className="h-11 w-full rounded-full border-slate-300 px-5 text-sm font-bold text-slate-700 hover:bg-slate-50 sm:w-auto"
                    disabled={isWorking || !selectedEvidenceCompleted}
                    onClick={() => {
                      if (selectedEvidence) onViewResult(selectedEvidence.evidenceId)
                    }}
                  >
                    결과보기
                  </Button>
                  {showDecisionActions ? (
                    <>
                      <Button
                        type="button"
                        variant="outline"
                        className="h-11 w-full rounded-full border-red-200 px-5 text-sm font-bold text-red-700 hover:bg-red-50 sm:w-auto"
                        disabled={isWorking}
                        onClick={() => setDecisionDialog("REVISION")}
                      >
                        보완 요청
                      </Button>
                      <Button
                        type="button"
                        className="h-11 w-full rounded-full bg-emerald-700 px-5 text-sm font-bold text-white hover:bg-emerald-800 sm:w-auto"
                        disabled={isWorking}
                        onClick={() => setDecisionDialog("APPROVED")}
                      >
                        검토 승인
                      </Button>
                    </>
                  ) : null}
                </>
              ) : null}
              {showAssignmentAction ? (
                <Button
                  type="button"
                  variant="outline"
                  className="h-11 w-full rounded-full border-slate-300 px-5 text-sm font-bold text-slate-700 hover:bg-slate-50 sm:w-auto"
                  disabled={isWorking}
                  onClick={() => setAssignmentOpen(true)}
                >
                  검토자 배정
                </Button>
              ) : null}
              {reviewRequestAllowed && !isReviewerMode && !selectedEvidenceRunning ? (
                <Button
                  type="button"
                  variant="outline"
                  className="h-10 w-full rounded-lg border-slate-300 bg-white px-4 text-sm font-bold text-slate-700 shadow-none hover:bg-slate-50 sm:w-auto dark:bg-transparent dark:text-slate-200 dark:hover:bg-slate-800"
                  disabled={isWorking}
                  onClick={() => onReviewRequestOpenChange(true)}
                >
                  검토 요청
                </Button>
              ) : null}
              {!isReviewerMode && selectedEvidenceRunning ? (
                <div
                  className="flex w-full min-w-0 flex-col gap-2 sm:min-w-[360px]"
                  aria-label={`AI 분석 진행률 ${selectedEvidenceProgress}%`}
                  aria-live="polite"
                >
                  <div
                    key={`${selectedEvidenceRunningCopy.title}-${selectedEvidenceRunningCopy.detail}`}
                    className="min-h-[42px] animate-in fade-in slide-in-from-bottom-1 duration-500"
                  >
                    <p className="text-sm font-bold text-foreground">{selectedEvidenceRunningCopy.title}</p>
                    <p className="mt-1 text-xs font-semibold text-muted-foreground">
                      {selectedEvidenceRunningCopy.detail}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                      <div
                        className="h-full rounded-full bg-foreground transition-all duration-500"
                        style={{ width: `${selectedEvidenceProgress}%` }}
                      />
                    </div>
                    <span className="w-10 shrink-0 text-right text-sm font-bold text-foreground">
                      {selectedEvidenceProgress}%
                    </span>
                    <button
                      type="button"
                      className="flex size-7 shrink-0 items-center justify-center bg-transparent text-foreground transition-opacity hover:opacity-70 disabled:cursor-not-allowed disabled:opacity-40"
                      disabled={isWorking}
                      aria-label="분석 중단"
                      title="분석 중단"
                      onClick={() => void handleCancelSelectedAnalysis()}
                    >
                      <Square className="size-3.5 fill-current" strokeWidth={0} aria-hidden="true" />
                    </button>
                  </div>
                </div>
              ) : null}
              {!isReviewerMode && !selectedEvidenceRunning ? (
                <Button
                  type="button"
                  className="h-11 w-full rounded-full bg-foreground px-6 text-sm font-bold text-background hover:bg-foreground/90 sm:w-auto"
                  disabled={
                    analysisBusy ||
                    (!showSelectedEvidenceResultAction &&
                      selectedAnalysisCount === 0 &&
                      !selectedEvidenceAnalysisSelectable &&
                      selectableAnalysisEvidences.length !== 1)
                  }
                  onClick={() => {
                    if (showSelectedEvidenceResultAction && selectedEvidence) {
                      onViewResult(selectedEvidence.evidenceId)
                      return
                    }
                    void handleStartAnalysis()
                  }}
                >
                  {analysisBusy ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : null}
                  {isCheckingReadiness
                    ? "품질 검사 중..."
                    : selectedAnalysisCount > 1
                      ? "전체 분석하기"
                      : showSelectedEvidenceResultAction
                        ? "결과보기"
                        : "분석하기"}
                </Button>
              ) : null}
            </div>
        </div>
      ) : null}

      {reviewRequestOpen ? (
        <ReviewRequestDialog
          processing={isWorking}
          rereview={canRequestRereview}
          onClose={() => onReviewRequestOpenChange(false)}
          onConfirm={() => void handleRequestReview()}
        />
      ) : null}

      {assignmentOpen ? (
        <ReviewerAssignmentDialog
          reviewers={reviewers}
          loading={reviewersLoading}
          defaultReviewerId={caseData.reviewerId ?? latestSupplementRound?.reviewerId}
          caseSummary={caseReviewSummary}
          processing={isWorking}
          onClose={() => setAssignmentOpen(false)}
          onAssign={(reviewer) => void handleAssignReviewer(reviewer)}
        />
      ) : null}

      {decisionDialog ? (
        <ReviewDecisionDialog
          decision={decisionDialog}
          caseSummary={caseReviewSummary}
          analystName={analystName}
          unreadEvidenceLabels={unreadReviewEvidenceLabels}
          processing={isWorking}
          onClose={() => setDecisionDialog(null)}
          onSubmit={(reason) => void handleReviewDecision(decisionDialog, reason)}
        />
      ) : null}

      {!readOnly && editCaseOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 px-4 py-6">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="editCaseTitle"
            className="max-h-[92vh] w-full max-w-5xl overflow-y-auto rounded-2xl border border-border bg-card p-6 shadow-2xl"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <h3 id="editCaseTitle" className="text-2xl font-bold text-foreground">
                  사건 수정
                </h3>
                <p className="mt-2 text-sm font-bold text-muted-foreground">
                  사건 표시명과 대표 증거를 수정합니다. 원본 증거와 CoC 기록은 변경되지 않습니다.
                </p>
              </div>
              <button
                type="button"
                className="flex size-9 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted/70 hover:text-foreground"
                aria-label="사건 수정 닫기"
                onClick={() => setEditCaseOpen(false)}
              >
                <X className="size-5" aria-hidden="true" />
              </button>
            </div>

            <div className="mt-6 rounded-xl border border-border bg-muted/20 p-5">
              <div className="flex items-start gap-4">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-teal-600 text-base font-bold text-white">
                  1
                </span>
                <div className="min-w-0 flex-1">
                  <h4 className="text-lg font-bold text-foreground">사건 정보</h4>
                  <p className="mt-1 text-sm font-bold text-muted-foreground">
                    목록과 상세 화면에 표시될 사건명을 입력합니다.
                  </p>
                  <label htmlFor="caseNameEdit" className="mt-5 block text-sm font-bold text-foreground">
                    사건명
                  </label>
                  <input
                    id="caseNameEdit"
                    value={caseNameDraft}
                    onChange={(event) => setCaseNameDraft(event.target.value)}
                    className="mt-2 h-12 w-full rounded-lg border border-border bg-card px-4 text-base font-bold text-foreground outline-none transition-colors focus:border-teal-300 focus:ring-4 focus:ring-teal-100"
                  />
                </div>
              </div>
            </div>

            <div className="mt-4 rounded-xl border border-border bg-muted/20 p-5">
              <div className="flex items-start gap-4">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-teal-600 text-base font-bold text-white">
                  2
                </span>
                <div className="min-w-0 flex-1">
                  <h4 className="text-lg font-bold text-foreground">대표 증거 지정</h4>
                  <p className="mt-1 text-sm font-bold text-muted-foreground">
                    사건 목록과 상단 요약에 기준으로 보여줄 증거를 선택합니다.
                  </p>
                  <div className="mt-5 grid gap-3 sm:grid-cols-2">
                    {evidences.map((evidence) => {
                      const lifecycle = evidence.lifecycleStatus ?? "ACTIVE"
                      const disabled = lifecycle !== "ACTIVE"
                      return (
                        <label
                          key={evidence.evidenceId}
                          className={cn(
                            "flex cursor-pointer items-center gap-3 rounded-xl border bg-card p-4 transition-colors",
                            representativeDraftId === evidence.evidenceId
                              ? "border-teal-500 bg-teal-50"
                              : "border-border hover:border-teal-200",
                            disabled && "cursor-not-allowed opacity-50"
                          )}
                        >
                          <input
                            type="radio"
                            name="representativeEvidence"
                            checked={representativeDraftId === evidence.evidenceId}
                            disabled={disabled}
                            onChange={() => setRepresentativeDraftId(evidence.evidenceId)}
                            className="size-4 accent-teal-600"
                          />
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-base font-bold text-foreground">
                              {formatEvidenceTitle(evidence)}
                            </span>
                            <span className="mt-1 block font-mono text-xs font-bold text-muted-foreground">
                              EVD-{evidence.evidenceId}
                            </span>
                          </span>
                          <span className={cn("rounded-full px-2.5 py-1 text-xs font-bold", getLifecycleClassName(lifecycle))}>
                            {getLifecycleLabel(lifecycle)}
                          </span>
                        </label>
                      )
                    })}
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                className="h-11 px-6 font-bold"
                onClick={() => setEditCaseOpen(false)}
              >
                취소
              </Button>
              <Button
                type="button"
                className="h-11 bg-teal-600 px-7 font-bold text-white hover:bg-teal-700"
                onClick={handleSaveCaseSettings}
              >
                수정 완료
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {!readOnly && deleteConfirmOpen && selectedEvidence ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 px-4">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="deleteEvidenceTitle"
            className="w-full max-w-md rounded-2xl border border-red-700/20 bg-card p-5 shadow-2xl"
          >
            <div className="flex items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-red-50 text-red-700">
                <AlertCircle className="size-5" aria-hidden="true" />
              </span>
              <div>
                <h3 id="deleteEvidenceTitle" className="text-lg font-bold text-foreground">
                  사용제외 전 확인
                </h3>
                <p className="mt-1 text-sm font-bold leading-6 text-muted-foreground">
                  {formatEvidenceTitle(selectedEvidence)}는 원본 파일을 삭제하지 않고, 사건 기록에서 사용제외 상태로 표시됩니다.
                </p>
              </div>
            </div>
            <div className="mt-5 grid grid-cols-2 gap-2">
              <Button
                type="button"
                variant="outline"
                className="h-11 font-bold"
                disabled={isWorking}
                onClick={() => setDeleteConfirmOpen(false)}
              >
                취소
              </Button>
              <Button
                type="button"
                variant="destructive"
                className="h-11 font-bold"
                disabled={isWorking || !selectedEvidenceExcludable}
                onClick={() => void handleConfirmDeleteEvidence()}
              >
                사용제외
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      <ReadinessCheckOverlay open={isCheckingReadiness} phase={readinessCheckPhase} />
      <QualityWarningDialog
        open={qualityDialogOpen}
        summaries={qualityDialogSummaries}
        worstTier={qualityDialogWorstTier}
        loading={qualityDialogLoading}
        onConfirm={() => void confirmQualityDialog()}
        onCancel={cancelQualityDialog}
      />
      </section>
    </>
  )
}

function EvidenceManagementCard({
  evidence,
  representative,
  active,
  onSelect,
}: {
  evidence: CaseEvidenceSummary
  representative: boolean
  active: boolean
  onSelect: () => void
}) {
  const lifecycle = evidence.lifecycleStatus ?? "ACTIVE"

  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        "rounded-xl border bg-background/50 p-4 text-left transition-colors",
        active ? "border-teal-400 bg-teal-50 shadow-sm" : "border-border hover:bg-muted/30",
        lifecycle !== "ACTIVE" && "opacity-70"
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-lg font-bold text-foreground">{formatEvidenceTitle(evidence)}</p>
          <p className="mt-1 font-mono text-xs font-bold text-muted-foreground">EVD-{evidence.evidenceId}</p>
        </div>
        <span className={cn("rounded-full px-2.5 py-1 text-xs font-bold", getLifecycleClassName(lifecycle))}>
          {getLifecycleLabel(lifecycle)}
        </span>
      </div>
      <div className="mt-3 flex flex-wrap gap-2 text-xs font-bold">
        {representative ? (
          <span className="rounded-full bg-amber-100 px-2.5 py-1 text-amber-700">대표 증거</span>
        ) : null}
        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600">{getRoleLabel(evidence.role)}</span>
        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600">{evidence.mediaType}</span>
        <span className={cn("rounded-full px-2.5 py-1", getEvidenceAnalysisBadgeClassName(evidence))}>
          {getEvidenceAnalysisLabel(evidence)}
        </span>
      </div>
      {evidence.replacementEvidenceId ? (
        <p className="mt-3 text-xs font-bold text-muted-foreground">
          대체 증거: EVD-{evidence.replacementEvidenceId}
        </p>
      ) : null}
      {evidence.excludedReason ? (
        <p className="mt-2 text-xs font-bold text-red-700">{evidence.excludedReason}</p>
      ) : null}
      <details className="mt-3 text-xs font-semibold text-muted-foreground">
        <summary className="cursor-pointer">원본 파일 정보</summary>
        <p className="mt-2 truncate">{evidence.originalFileName ?? evidence.fileName}</p>
      </details>
    </button>
  )
}

function EvidenceListRow({
  evidence,
  active,
  representative,
  disabled,
  running,
  analysisSelectable,
  analysisSelected,
  selectionMode,
  onSelect,
  onToggleAnalysisSelect,
  onViewResult,
}: {
  evidence: CaseEvidenceSummary
  active: boolean
  representative: boolean
  disabled: boolean
  running: boolean
  analysisSelectable: boolean
  analysisSelected: boolean
  selectionMode: boolean
  onSelect: () => void
  onToggleAnalysisSelect: () => void
  onViewResult?: () => void
}) {
  const lifecycle = evidence.lifecycleStatus ?? "ACTIVE"
  const status = normalizeStatus(evidence.analysisStatus ?? "PENDING")
  const completed = lifecycle === "ACTIVE" && !running && status === "COMPLETED"
  const statusLabel =
    lifecycle !== "ACTIVE" ? getLifecycleLabel(lifecycle) : running ? "분석 중" : getEvidenceAnalysisLabel(evidence)
  const statusClassName = getEvidenceRowStatusClassName(evidence, running)
  const checkboxVisible = selectionMode || analysisSelected

  function handleActivate() {
    if (selectionMode) {
      if (analysisSelectable) onToggleAnalysisSelect()
      return
    }
    onSelect()
  }

  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      onClick={handleActivate}
      onKeyDown={(event) => {
        if (disabled) return
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault()
          handleActivate()
        }
      }}
      aria-disabled={disabled}
      className={cn(
        "group flex h-16 w-full shrink-0 cursor-pointer items-center gap-3 border-l-2 px-3 text-left transition-colors",
        analysisSelected
          ? "border-l-teal-600 bg-teal-50/70 hover:bg-teal-50 dark:bg-teal-950/25 dark:hover:bg-teal-950/35"
          : active && !selectionMode
            ? "border-l-slate-950 bg-slate-50/80 dark:border-l-foreground dark:bg-background/70"
            : "border-l-transparent hover:bg-slate-50/70 dark:hover:bg-background/50",
        disabled && "cursor-not-allowed opacity-55 hover:border-l-transparent hover:bg-transparent"
      )}
    >
      <span className="relative flex size-[18px] shrink-0 items-center justify-center">
        {running ? (
          <Loader2 className="size-4 animate-spin text-muted-foreground" aria-hidden="true" />
        ) : analysisSelectable ? (
          <button
            type="button"
            role="checkbox"
            aria-checked={analysisSelected}
            aria-label={`${formatEvidenceTitle(evidence)} 선택`}
            onClick={(event) => {
              event.stopPropagation()
              onToggleAnalysisSelect()
            }}
            className={cn(
              "absolute inset-0 flex items-center justify-center rounded-[5px] border transition-opacity",
              analysisSelected
                ? "border-teal-600 bg-teal-600 text-white"
                : "border-slate-300 bg-white hover:border-slate-400 dark:border-border dark:bg-background",
              checkboxVisible ? "opacity-100" : "opacity-0 group-hover:opacity-100 group-focus-within:opacity-100"
            )}
          >
            {analysisSelected ? <Check className="size-3" strokeWidth={3} aria-hidden="true" /> : null}
          </button>
        ) : null}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5">
          <span className="truncate text-[15px] font-bold text-foreground">{formatEvidenceTitle(evidence)}</span>
          {representative ? (
            <span className="shrink-0 rounded-full border border-border px-1.5 py-px text-[11px] font-bold text-muted-foreground">
              대표
            </span>
          ) : null}
        </span>
        <span className="block truncate whitespace-nowrap font-mono text-[13px] font-semibold text-muted-foreground">
          EVD-{evidence.evidenceId}
        </span>
      </span>
      {completed && onViewResult ? (
        <button
          type="button"
          className="group/result flex shrink-0 items-center gap-0.5 text-[13px] font-bold"
          aria-label={`${formatEvidenceTitle(evidence)} 결과보기`}
          onClick={(event) => {
            event.stopPropagation()
            onViewResult()
          }}
        >
          <span className={statusClassName}>{statusLabel}</span>
          <ChevronRight
            className="size-3.5 text-muted-foreground transition-colors group-hover/result:text-foreground"
            aria-hidden="true"
          />
        </button>
      ) : (
        <span className={cn("shrink-0 text-[13px] font-bold", statusClassName)}>{statusLabel}</span>
      )}
    </div>
  )
}

function CaseMetadataRow({
  label,
  value,
  accent = false,
}: {
  label: string
  value: string
  accent?: boolean
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-border pb-3 last:border-b-0 last:pb-0">
      <dt className="shrink-0 text-sm font-bold text-muted-foreground">{label}</dt>
      <dd
        className={cn(
          "min-w-0 truncate text-right text-sm font-bold",
          accent ? "text-teal-600" : "text-foreground"
        )}
      >
        {value}
      </dd>
    </div>
  )
}

function EvidenceRadioGroup({
  title,
  evidences,
  value,
  onChange,
}: {
  title: string
  evidences: CaseEvidenceSummary[]
  value: number | null
  onChange: (value: number) => void
}) {
  return (
    <div className="rounded-xl border border-border bg-card p-3">
      <p className="text-sm font-bold text-foreground">{title}</p>
      <div className="mt-3 grid gap-2">
        {evidences.map((evidence) => (
          <label
            key={evidence.evidenceId}
            className={cn(
              "flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors",
              value === evidence.evidenceId ? "border-blue-400 bg-blue-50" : "border-border hover:bg-muted/30"
            )}
          >
            <input
              type="radio"
              checked={value === evidence.evidenceId}
              onChange={() => onChange(evidence.evidenceId)}
              className="size-4 accent-blue-600"
            />
            <span className="min-w-0">
              <span className="block truncate text-sm font-bold text-foreground">{formatEvidenceTitle(evidence)}</span>
              <span className="font-mono text-xs font-semibold text-muted-foreground">EVD-{evidence.evidenceId}</span>
            </span>
          </label>
        ))}
      </div>
    </div>
  )
}

function formatEvidenceTitle(evidence: CaseEvidenceSummary) {
  return evidence.displayLabel || `EVD-${evidence.evidenceId}`
}

function ResultScoreBar({ label, value }: { label: string; value: number }) {
  const percent = Math.round(normalizeResultValue(value) * 100)

  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between gap-3 text-sm font-bold">
        <span className="truncate text-muted-foreground">{label}</span>
        <span className="text-foreground">{percent}</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-slate-100">
        <div
          className={cn(
            "h-full rounded-full",
            percent >= 60 ? "bg-red-700" : percent >= 30 ? "bg-amber-400" : "bg-emerald-500"
          )}
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  )
}

function ResultBreakdownRow({
  label,
  description,
  value,
}: {
  label: string
  description: string
  value: number
}) {
  const percent = Math.round(normalizeResultValue(value) * 100)

  return (
    <div>
      <div className="mb-2 flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-base font-semibold text-slate-950 dark:text-foreground">{label}</p>
          <p className="mt-0.5 text-sm text-slate-500">{description}</p>
        </div>
        <span className="shrink-0 text-xl font-bold text-slate-950 dark:text-foreground">{percent}</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-muted">
        <div className="h-full rounded-full bg-red-700" style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}

function RiskSignalCard({
  signal,
  delayMs = 0,
  onSeek,
}: {
  signal: UiRiskSignal
  delayMs?: number
  onSeek?: (seconds: number, mode?: ResultMediaMode) => void
}) {
  const value = normalizeResultValue(signal.score)
  const percent = Math.round(value * 100)
  const [barWidth, setBarWidth] = useState(0)
  const isDanger = signal.tone === "danger"
  const badgeClassName = isDanger
    ? "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-400"
    : signal.tone === "warning"
      ? "bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400"
      : "bg-slate-100 text-slate-500 dark:bg-secondary dark:text-muted-foreground"

  useEffect(() => {
    setBarWidth(0)
    let frame = 0
    const timer = window.setTimeout(() => {
      frame = window.requestAnimationFrame(() => setBarWidth(percent))
    }, delayMs)

    return () => {
      window.clearTimeout(timer)
      window.cancelAnimationFrame(frame)
    }
  }, [delayMs, percent])

  return (
    <li className="rounded-xl border border-slate-100 bg-slate-50/70 p-4 transition-colors hover:border-slate-200 dark:border-border dark:bg-background">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <p className="text-sm font-bold text-slate-950 dark:text-foreground">{signal.label}</p>
          {signal.modelLabel ? (
            <span className="font-mono text-[11px] font-semibold text-slate-400">{signal.modelLabel}</span>
          ) : null}
          <span className={cn("rounded-full px-2.5 py-1 text-[11px] font-bold", badgeClassName)}>
            {signal.badge}
          </span>
        </div>
        <span className="font-mono text-sm font-bold text-slate-950 dark:text-foreground">
          {formatScoreOutOf100(value)}
          <span className="ml-1.5 font-sans text-[11px] font-semibold text-slate-400">
            기준 {signal.thresholdPercent}
          </span>
        </span>
      </div>
      <p className="mt-2 text-xs font-semibold leading-5 text-slate-500">{signal.definition}</p>
      <div className="relative mt-3 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div
          className={cn(
            "h-full rounded-full transition-[width] duration-700 ease-out",
            isDanger ? "bg-red-700 dark:bg-red-500" : signal.tone === "warning" ? "bg-amber-400" : "bg-slate-300 dark:bg-slate-600"
          )}
          style={{ width: `${barWidth}%` }}
        />
        <span
          aria-hidden="true"
          className="absolute inset-y-0 w-px bg-slate-400/80 dark:bg-slate-500"
          style={{ left: `${signal.thresholdPercent}%` }}
        />
      </div>
      {signal.segments.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {signal.segments.map((segment) => (
            <button
              key={`${segment.label}-${segment.startSec}`}
              type="button"
              onClick={() => onSeek?.(segment.startSec)}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-bold text-slate-700 transition-colors hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground dark:hover:bg-secondary/40"
            >
              <Play className="size-3 text-teal-600 dark:text-teal-300" aria-hidden="true" />
              {segment.label}
            </button>
          ))}
        </div>
      ) : null}
    </li>
  )
}

function RepresentativeFrameDetailCard({
  frame,
  index,
  videoRef,
}: {
  frame: RepresentativeFrame
  index: number
  videoRef?: RefObject<HTMLVideoElement | null>
}) {
  const score = frame.score == null ? null : Math.round(normalizeResultValue(frame.score) * 100)
  const tone = score == null ? null : getDetectionTone(score / 100)
  const timeLabel =
    frame.timestamp ?? (frame.timeSec != null ? formatDuration(frame.timeSec) : `프레임 ${index + 1}`)
  const timeSec = frame.timeSec ?? 0

  return (
    <div className="overflow-hidden rounded-xl border border-slate-100 bg-slate-50/70 dark:border-border dark:bg-background">
      <div className="relative aspect-video bg-slate-950">
        {videoRef ? (
          <VideoSeekThumbnail
            videoRef={videoRef}
            timeSec={timeSec}
            imageUrl={frame.imageUrl}
            heatmapImageUrl={frame.heatmapImageUrl}
            label="대표 프레임"
          />
        ) : frame.imageUrl || frame.heatmapImageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={(frame.imageUrl || frame.heatmapImageUrl) ?? undefined}
            alt={`대표 프레임 ${index + 1}`}
            className="size-full object-cover"
          />
        ) : (
          <div className="flex size-full items-center justify-center text-xs font-bold text-white/45">
            대표 프레임
          </div>
        )}

        <div className="absolute left-3 top-3 rounded-md bg-black/55 px-2 py-1 text-xs font-bold text-white">
          {timeLabel}
        </div>
      </div>
      <div className="flex items-center justify-between gap-3 px-4 py-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-bold text-slate-950 dark:text-foreground">
            {frame.frameNumber != null ? `프레임 ${frame.frameNumber}` : `대표 ${index + 1}`}
          </p>
          <p className="mt-0.5 text-xs font-semibold text-slate-500">
            {frame.heatmapImageUrl && !frame.imageUrl ? "히트맵" : "원본 프레임"}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <span className="font-mono text-sm font-bold text-slate-950 dark:text-foreground">
            {score == null ? "-" : `${score} / 100`}
          </span>
          {tone ? (
            <span className={cn("rounded-full px-2 py-0.5 text-[11px] font-bold", tone.badgeClass)}>{tone.level}</span>
          ) : null}
        </div>
      </div>
    </div>
  )
}

function SummaryMetricRow({
  label,
  value,
  tone,
}: {
  label: string
  value: string
  tone: "danger" | "safe" | "neutral"
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-slate-100 pb-3 last:border-b-0 last:pb-0 dark:border-border">
      <span className="text-sm font-bold text-slate-500">{label}</span>
      <span
        className={cn(
          "rounded-full px-3 py-1 text-sm font-bold",
          tone === "danger" && "bg-red-50 text-red-700",
          tone === "safe" && "bg-emerald-50 text-emerald-700",
          tone === "neutral" && "bg-slate-100 text-slate-700"
        )}
      >
        {value}
      </span>
    </div>
  )
}

function FrameStatCard({
  label,
  value,
  sub,
  tone,
}: {
  label: string
  value: string
  sub?: string
  tone?: "danger"
}) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50/70 px-4 py-3 dark:border-border dark:bg-background">
      <p className="text-xs font-bold text-slate-500">{label}</p>
      <p
        className={cn(
          "mt-1.5 text-xl font-bold text-slate-950 dark:text-foreground",
          tone === "danger" && "text-red-700"
        )}
      >
        {value}
      </p>
      {sub ? <p className="mt-0.5 text-xs font-semibold text-slate-400">{sub}</p> : null}
    </div>
  )
}

const MODEL_BAR_COLORS = [
  { bar: "bg-emerald-800 dark:bg-emerald-600", label: "text-emerald-800 dark:text-emerald-300" },
  { bar: "bg-emerald-600 dark:bg-emerald-500", label: "text-emerald-600 dark:text-emerald-300" },
  { bar: "bg-emerald-400 dark:bg-emerald-400", label: "text-emerald-500 dark:text-emerald-200" },
  { bar: "bg-teal-300 dark:bg-teal-300", label: "text-teal-500 dark:text-teal-200" },
] as const

function MethodologyModelChart({
  models,
}: {
  models: UiMethodologyModel[]
}) {
  const [animated, setAnimated] = useState(false)

  useEffect(() => {
    setAnimated(false)
    const frame = requestAnimationFrame(() => {
      requestAnimationFrame(() => setAnimated(true))
    })
    return () => cancelAnimationFrame(frame)
  }, [models])

  return (
    <div className="px-6 pb-3 pt-8">
      <div className="relative h-40 border-b border-slate-200 dark:border-border">
        <div className="mx-auto flex h-full max-w-[560px] items-end justify-center gap-2 px-1 sm:gap-3">
          {models.map((model, index) => {
            const percent = model.score == null ? null : Math.round(model.score * 100)
            const thresholdPercent = Math.round(model.threshold * 100)
            const thresholdBottom = Math.max(0, Math.min(100, thresholdPercent))
            const color = MODEL_BAR_COLORS[index % MODEL_BAR_COLORS.length]
            const over = model.score != null && model.overThreshold
            return (
              <div
                key={`bar-${model.name}-${model.version}`}
                className="relative flex h-full w-[108px] shrink-0 flex-col items-end justify-end pr-1"
              >
                <div
                  aria-hidden="true"
                  className="pointer-events-none absolute inset-x-0 z-10 flex items-center"
                  style={{ bottom: `${thresholdBottom}%` }}
                >
                  <span className="w-[46px] shrink-0 text-right text-[9px] font-bold leading-none text-slate-500">
                    기준 {thresholdPercent}
                  </span>
                  <span className="mx-0.5 w-2.5 shrink-0 border-t border-dashed border-slate-500/80 dark:border-slate-400" />
                  <span className="w-12 shrink-0 border-t-[1.5px] border-dashed border-slate-500/80 dark:border-slate-400" />
                </div>
                <div className="flex h-full w-12 flex-col items-center justify-end">
                  <span
                    className={cn(
                      "mb-1 text-xs font-bold transition-opacity duration-500",
                      animated ? "opacity-100" : "opacity-0",
                      over ? "text-red-700 dark:text-red-300" : color.label
                    )}
                    style={{ transitionDelay: `${index * 140 + 350}ms` }}
                  >
                    {percent ?? "-"}
                  </span>
                  <div
                    className={cn(
                      "w-12 rounded-t-[3px] transition-[height] duration-700 ease-out",
                      over ? "bg-red-600 dark:bg-red-500" : color.bar
                    )}
                    style={{
                      height: animated ? `${Math.max(2, percent ?? 0)}%` : "0%",
                      transitionDelay: `${index * 140}ms`,
                    }}
                  />
                </div>
              </div>
            )
          })}
        </div>
      </div>
      <div className="mx-auto flex max-w-[560px] justify-center gap-2 px-1 pt-2 sm:gap-3">
        {models.map((model, index) => (
          <div
            key={`label-${model.name}-${model.version}`}
            title={`${model.name} · 기준 ${Math.round(model.threshold * 100)} 초과 시 탐지`}
            className="w-[108px] shrink-0 text-center"
          >
            <span
              className={cn(
                "block whitespace-nowrap text-[11px] font-bold leading-tight",
                MODEL_BAR_COLORS[index % MODEL_BAR_COLORS.length].label
              )}
            >
              {model.name}
            </span>
            <span className="mt-0.5 block text-[10px] font-semibold text-slate-400">
              {model.overThreshold ? "기준 초과 · 탐지" : "기준 미만"}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

function MiniFrameRiskChart({ scores }: { scores: FrameScore[] }) {
  if (scores.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
        프레임별 위험 점수가 없습니다. 분석 서버가 프레임 데이터를 제공하면 표시됩니다.
      </p>
    )
  }

  const items = scores.slice(0, 36).map((item) => ({
    value: normalizeResultValue(item.score),
    timeSec: item.timeSec ?? null,
  }))
  const peakIndex = items.reduce((peak, item, index) => (item.value > items[peak].value ? index : peak), 0)
  const labels = items.map((item, index) => formatSecondsForViewer(item.timeSec ?? index))
  const riskScores = items.map((item) => Math.round(item.value * 100))
  const thresholdScores = items.map(() => 60)
  const peakScores = items.map((_, index) => (index === peakIndex ? riskScores[index] : null))
  const tickIndexSet = new Set([0, 0.25, 0.5, 0.75, 1].map((ratio) => Math.round((items.length - 1) * ratio)))

  const data: ChartData<"line", (number | null)[], string> = {
    labels,
    datasets: [
      {
        label: "위험 점수",
        data: riskScores,
        borderColor: "#64748b",
        backgroundColor: "rgba(100, 116, 139, 0.08)",
        borderWidth: 2.5,
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHitRadius: 10,
        pointBackgroundColor: "#64748b",
        pointBorderColor: "#ffffff",
        tension: 0.35,
        fill: true,
        segment: {
          borderColor(context) {
            const startValue = Number(context.p0?.parsed?.y)
            const endValue = Number(context.p1?.parsed?.y)
            return startValue >= 60 || endValue >= 60 ? "#b91c1c" : "#64748b"
          },
        },
      },
      {
        label: "임계값 60 / 100",
        data: thresholdScores,
        borderColor: "rgba(185, 28, 28, 0.4)",
        borderWidth: 1.5,
        borderDash: [8, 8],
        pointRadius: 0,
        pointHitRadius: 0,
        tension: 0,
      },
      {
        label: "최고 위험 프레임",
        data: peakScores,
        borderColor: "transparent",
        backgroundColor: "#b91c1c",
        pointBackgroundColor: "#b91c1c",
        pointBorderColor: "#ffffff",
        pointBorderWidth: 4,
        pointRadius: 8,
        pointHoverRadius: 9,
        showLine: false,
      },
    ],
  }

  const peakLabelPlugin: Plugin<"line"> = {
    id: "frameRiskPeakLabel",
    afterDatasetsDraw(chart) {
      const peakDatasetIndex = chart.data.datasets.findIndex((dataset) => dataset.label === "최고 위험 프레임")
      if (peakDatasetIndex < 0) return

      const peakDataset = chart.data.datasets[peakDatasetIndex]
      const peakDataIndex = peakDataset.data.findIndex((value) => typeof value === "number")
      const peakValue = peakDataset.data[peakDataIndex]
      const peakPoint = chart.getDatasetMeta(peakDatasetIndex).data[peakDataIndex]
      const peakValueNumber = Number(peakValue)
      if (!Number.isFinite(peakValueNumber) || !peakPoint) return

      const { x, y } = peakPoint.tooltipPosition(true)
      const xPosition = Number(x)
      const yPosition = Number(y)
      if (!Number.isFinite(xPosition) || !Number.isFinite(yPosition)) return
      const { ctx, chartArea } = chart
      ctx.save()
      ctx.font = "700 14px system-ui, -apple-system, BlinkMacSystemFont, sans-serif"
      ctx.fillStyle = "#b91c1c"
      ctx.textAlign = "center"
      ctx.textBaseline = "bottom"
      ctx.fillText(
        `${Math.round(peakValueNumber)} / 100`,
        xPosition,
        Math.max(chartArea.top + 18, yPosition - 18)
      )
      ctx.restore()
    },
  }

  const options: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: 650,
      easing: "easeOutQuart",
    },
    interaction: {
      intersect: false,
      mode: "index",
    },
    layout: {
      padding: {
        top: 28,
        right: 14,
        bottom: 0,
        left: 4,
      },
    },
    scales: {
      x: {
        border: {
          display: false,
        },
        grid: {
          display: false,
          drawTicks: false,
        },
        ticks: {
          color: "#94a3b8",
          font: {
            size: 12,
            weight: 700,
          },
          maxRotation: 0,
          autoSkip: false,
          callback(_value, index) {
            return tickIndexSet.has(index) ? labels[index] : ""
          },
        },
      },
      y: {
        min: 0,
        max: 100,
        border: {
          display: false,
        },
        grid: {
          color: "rgba(148, 163, 184, 0.22)",
          drawTicks: false,
        },
        ticks: {
          stepSize: 20,
          color: "#94a3b8",
          font: {
            size: 12,
            weight: 700,
          },
          padding: 12,
          callback(value) {
            const numericValue = Number(value)
            return numericValue === 0 || numericValue === 60 || numericValue === 100 ? String(numericValue) : ""
          },
        },
      },
    },
    plugins: {
      legend: {
        display: false,
      },
      tooltip: {
        backgroundColor: "rgba(15, 23, 42, 0.92)",
        borderColor: "rgba(255, 255, 255, 0.14)",
        borderWidth: 1,
        displayColors: false,
        padding: 10,
        callbacks: {
          title(items) {
            return items[0]?.label ?? ""
          },
          label(context) {
            const value = typeof context.raw === "number" ? context.raw : Number(context.parsed.y)
            if (context.dataset.label === "임계값 60 / 100") return "임계값: 60 / 100"
            return `${context.dataset.label}: ${Math.round(value)} / 100`
          },
        },
      },
    },
  }

  return (
    <div>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs font-semibold text-slate-500 dark:text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <span className="h-0.5 w-3.5 rounded-full bg-red-700" />
          위험 점수
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-3.5 border-t-2 border-dashed border-red-700/35" />
          임계값 60 / 100
        </span>
        <span className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-full bg-red-700" />
          최고 위험 프레임
        </span>
      </div>
      <div className="relative mt-3 h-56 rounded-lg bg-slate-50 px-3 py-4 dark:bg-background sm:px-5">
        <Line data={data} options={options} plugins={[peakLabelPlugin]} aria-label="프레임별 위험도 선 그래프" />
      </div>
    </div>
  )
}

function FrameMetricCard({
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

function ModelConsensusCard({
  models,
  thresholdPercent,
  summary,
}: {
  models: UiMethodologyModel[]
  thresholdPercent: number
  summary?: string | null
}) {
  const scored = models.filter((model) => model.score != null)
  const overCount = scored.filter((model) => model.overThreshold).length
  if (models.length === 0) return null

  return (
    <section className="rounded-xl border border-slate-100 bg-white p-5 dark:border-border dark:bg-card">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-base font-bold text-slate-950 dark:text-foreground">모델 판정 합의</h3>
        <span
          className={cn(
            "rounded-full px-3 py-1 text-xs font-bold",
            overCount > 0
              ? "bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-300"
              : "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-300"
          )}
        >
          {scored.length}개 모델 중 {overCount}개 기준({thresholdPercent}) 초과
        </span>
      </div>

      <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        {models.map((model) => {
          const percent = model.score != null ? Math.round(model.score * 100) : null
          return (
            <div
              key={`${model.name}-${model.version}`}
              className="rounded-lg border border-slate-100 bg-slate-50/60 px-3 py-2.5 dark:border-border dark:bg-background"
            >
              <div className="flex items-baseline justify-between gap-2">
                <p className="min-w-0 truncate text-sm font-bold text-slate-950 dark:text-foreground">{model.name}</p>
                {percent != null ? (
                  <span
                    className={cn(
                      "shrink-0 font-mono text-sm font-black",
                      model.overThreshold ? "text-red-600" : "text-emerald-600"
                    )}
                  >
                    {percent}
                  </span>
                ) : (
                  <span className="shrink-0 text-xs font-semibold text-slate-400">-</span>
                )}
              </div>
              <div className="mt-2 h-1 overflow-hidden rounded-full bg-slate-200/70 dark:bg-slate-800">
                <div
                  className={cn("h-full rounded-full", model.overThreshold ? "bg-red-500" : "bg-emerald-500")}
                  style={{ width: `${percent ?? 0}%` }}
                />
              </div>
              <p className="mt-1.5 text-[11px] font-semibold text-slate-400">
                {percent == null ? "점수 미제공" : model.overThreshold ? "기준 초과 — 우선 확인" : "기준 미만"}
              </p>
            </div>
          )
        })}
      </div>

      {summary ? (
        <p className="mt-4 border-t border-slate-100 pt-3.5 text-sm font-medium leading-6 text-slate-600 dark:border-border dark:text-muted-foreground">
          <strong className="font-bold text-slate-800 dark:text-foreground">종합 소견</strong> — {summary}
        </p>
      ) : null}
    </section>
  )
}

function TrustChecklistCard({ data }: { data: EvidenceDetailData }) {
  const chainValid = data.integrityInfo.chainValid
  const signature = data.signatureInfo ?? null
  const signatureOk = Boolean(signature?.signatureValid)
  const blockchain = data.blockchainInfo ?? null
  const blockchainOk = (blockchain?.status ?? "").toUpperCase() === "ANCHORED"
  const cocCount = data.cocLogs?.length ?? 0

  const items = [
    { label: "무결성 해시", ok: chainValid, value: chainValid ? "원본 일치" : "확인 필요" },
    { label: "전자서명", ok: signatureOk, value: signature ? (signatureOk ? "유효" : "확인 필요") : "미서명" },
    {
      label: "블록체인 앵커링",
      ok: blockchainOk,
      value: blockchain ? getBlockchainStatusLabel(blockchain.status) : "미앵커",
    },
    { label: "보관 이력(CoC)", ok: cocCount > 0, value: cocCount > 0 ? `${cocCount}건 기록` : "기록 없음" },
  ]

  return (
    <section className="rounded-xl border border-slate-100 bg-white p-5 dark:border-border dark:bg-card">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-base font-bold text-slate-950 dark:text-foreground">증거 신뢰성 체크</h3>
        <span className="text-xs font-semibold text-slate-400">무결성 · 서명 · 블록체인 · 보관 이력</span>
      </div>
      <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        {items.map((item) => (
          <div
            key={item.label}
            className="flex items-center gap-2.5 rounded-lg border border-slate-100 bg-slate-50/60 px-3 py-2.5 dark:border-border dark:bg-background"
          >
            {item.ok ? (
              <CheckCircle2 className="size-4 shrink-0 text-emerald-500" aria-hidden="true" />
            ) : (
              <AlertCircle className="size-4 shrink-0 text-amber-500" aria-hidden="true" />
            )}
            <div className="min-w-0">
              <p className="text-[11px] font-semibold text-slate-400">{item.label}</p>
              <p className="truncate text-sm font-bold text-slate-950 dark:text-foreground">{item.value}</p>
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}

function FrameRiskHeatStrip({
  scores,
  onSeek,
  caption = "타임라인 위험도",
}: {
  scores: FrameScore[]
  onSeek?: (seconds: number) => void
  caption?: string
}) {
  const items = scores.slice(0, 60)

  return (
    <div className="mt-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs font-semibold text-slate-400">
          {caption} · 구간을 누르면 해당 지점으로 이동합니다
        </p>
        <div className="flex items-center gap-3 text-[11px] font-semibold text-slate-400">
          <span className="flex items-center gap-1">
            <span className="size-2 rounded-[3px] bg-slate-200 dark:bg-secondary" />
            60 미만
          </span>
          <span className="flex items-center gap-1">
            <span className="size-2 rounded-[3px] bg-red-700/25" />
            60+
          </span>
          <span className="flex items-center gap-1">
            <span className="size-2 rounded-[3px] bg-red-700/60" />
            70+
          </span>
          <span className="flex items-center gap-1">
            <span className="size-2 rounded-[3px] bg-red-700" />
            80+
          </span>
        </div>
      </div>
      <div className="mt-2">
        <div className="flex h-4 gap-0.5 overflow-hidden rounded-md">
          {items.map((frame, index) => {
          const value = normalizeResultValue(frame.score)
          const timeLabel = frame.timeSec != null ? formatDuration(frame.timeSec) : `프레임 ${index + 1}`
          const seekSeconds = frame.timeSec
          return (
            <button
              key={`${index}-${frame.score}`}
              type="button"
              title={`${timeLabel} · 위험 점수 ${formatScoreOutOf100(frame.score)}`}
              aria-label={`${timeLabel} 위험 점수 ${formatScoreOutOf100(frame.score)}`}
              onClick={seekSeconds != null && onSeek ? () => onSeek(seekSeconds) : undefined}
              className={cn(
                "min-w-0 flex-1 rounded-[2px] transition-opacity hover:opacity-70",
                value >= 0.8
                  ? "bg-red-700"
                  : value >= 0.7
                    ? "bg-red-700/60"
                    : value >= 0.6
                      ? "bg-red-700/25"
                      : "bg-slate-200 dark:bg-secondary"
              )}
            />
          )
          })}
        </div>
      </div>
    </div>
  )
}

function getFrameDurationSeconds(scores: FrameScore[], peakFrame: FrameScore | null) {
  const times = scores
    .map((frame) => frame.timeSec)
    .filter((time): time is number => typeof time === "number" && Number.isFinite(time))
  return Math.max(...times, peakFrame?.timeSec ?? 0, 0)
}

function getVideoPositionLabel(ratio: number) {
  if (ratio < 0.33) return "영상 초반부"
  if (ratio < 0.66) return "영상 중반부"
  return "영상 후반부"
}

function formatSecondsForViewer(seconds: number) {
  if (!Number.isFinite(seconds)) return "-"
  const rounded = Math.round(seconds * 10) / 10
  return `${Number.isInteger(rounded) ? rounded.toFixed(0) : rounded.toFixed(1)}초`
}

function ResultDetailSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-xl border border-border bg-muted/20 p-4">
      <h4 className="text-base font-bold text-foreground">{title}</h4>
      <div className="mt-4">{children}</div>
    </section>
  )
}

function ResultFrameImage({ src, label }: { src?: string | null; label: string }) {
  return (
    <div className="overflow-hidden rounded-md border border-border bg-slate-950">
      {src ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt={label} className="aspect-video w-full object-cover" />
      ) : (
        <div className="flex aspect-video items-center justify-center text-[11px] font-bold text-white/50">
          {label}
        </div>
      )}
    </div>
  )
}

function ResultInfoLine({
  label,
  value,
  mono = false,
}: {
  label: string
  value: string
  mono?: boolean
}) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-border pb-2 last:border-b-0 last:pb-0">
      <span className="shrink-0 text-muted-foreground">{label}</span>
      <span className={cn("min-w-0 truncate text-right font-bold text-foreground", mono && "font-mono text-xs")}>
        {value}
      </span>
    </div>
  )
}

function EmptyResultText({ text }: { text: string }) {
  return <p className="text-sm font-bold text-muted-foreground">{text}</p>
}

function buildResultBreakdownRows(data: EvidenceDetailData | null) {
  const bars = buildResultDetectionBars(data)

  return [
    { label: "Forensic", description: "Pixel-level analysis", value: bars[0]?.value ?? 1 },
    { label: "Intent", description: "Logical consistency", value: bars[1]?.value ?? 0.2 },
    { label: "Subjective", description: "Social perception", value: bars[2]?.value ?? 0.4 },
  ]
}

function getManipulationSuspicionLabel(tone: ReturnType<typeof getCaseRiskTone>) {
  if (tone === "green") return "조작 의심 낮음"
  if (tone === "orange") return "조작 의심 보통"
  return "조작 의심 높음"
}

type DetailModuleResult = EvidenceDetailData["analysisInfo"]["moduleResults"][number]

const FORGERY_METHOD_BASELINES = [
  {
    id: "trufor" as const,
    name: "TruFor",
    fallbackVersion: "videocof-v2",
    role: "국소 삭제·객체 삽입·영역 변조 등 공간(픽셀) 위변조 카테고리를 봅니다.",
    output: "국소 위변조 점수, 의심 구간, 히트맵·마스크",
    match: (signal: UiRiskSignal) => {
      const text = `${signal.label} ${signal.modelLabel ?? ""}`.toLowerCase()
      return text.includes("trufor") || text.includes("spatial")
    },
  },
  {
    id: "timesformer" as const,
    name: "timesformer-forgery",
    fallbackVersion: "forgery-v1.9-hardneg",
    role: "Cut splicing·frame drop/dup/insert 등 시간축 편집 카테고리를 봅니다.",
    output: "시간축 위변조 점수, 의심 구간",
    match: (signal: UiRiskSignal) => {
      const text = `${signal.label} ${signal.modelLabel ?? ""}`.toLowerCase()
      return text.includes("timesformer") || text.includes("temporal")
    },
  },
]

function isForgeryKeywordText(value: string | null | undefined) {
  const normalized = value?.toLowerCase() ?? ""
  if (!normalized) return false
  if (normalized.includes("trufor") || normalized.includes("forgery") || normalized.includes("tamper")) {
    return true
  }
  if (normalized.includes("timesformer") && normalized.includes("temporal")) return true
  if (normalized.includes("frame_edit") || normalized.includes("frame edit") || normalized.includes("splic")) return true
  if (normalized.includes("re_encoding") || normalized.includes("re-encoding") || normalized.includes("reencoding")) {
    return true
  }
  if (normalized.includes("localization") || normalized.includes("국소 위변조")) return true
  return false
}

function isForgeryRiskSignal(signal: UiRiskSignal) {
  return isForgeryKeywordText(signal.label) || isForgeryKeywordText(signal.modelLabel)
}

function formatForgeryModuleLabel(moduleName: string) {
  const normalized = moduleName.toLowerCase()
  if (normalized.includes("splic")) return "구간 이어붙이기"
  if (normalized.includes("re_encoding") || normalized.includes("re-encoding") || normalized.includes("reencoding")) {
    return "재인코딩 흔적"
  }
  if (normalized.includes("forgery") || normalized.includes("tamper") || normalized.includes("localization") || normalized.includes("mask")) {
    return "국소 위변조 탐지"
  }
  if (normalized.includes("frame") || normalized.includes("edit")) return "프레임 편집 흔적"
  return formatModuleLabel(moduleName)
}

function formatForgeryDefinition(label: string) {
  if (label.includes("TruFor") || label.includes("Spatial") || label.includes("국소")) {
    return "국소 삭제·객체 삽입·영역 변조 등 공간(픽셀) 위변조 카테고리를 봅니다."
  }
  if (label.includes("TimeSformer") || label.includes("Temporal") || label.includes("이어붙이기")) {
    return "Cut splicing·frame drop/dup/insert 등 시간축 편집 카테고리를 봅니다."
  }
  if (label.includes("재인코딩")) return "재압축이나 변환 과정에서 생기는 인코딩 특성 변화를 확인합니다."
  return "프레임 삽입·삭제·합성처럼 시간축 편집으로 생기는 위변조 흔적을 확인합니다."
}

function formatModuleModelName(module: DetailModuleResult) {
  const name = module.modelName?.trim()
  if (!name) return null
  const version = module.modelVersion?.trim()
  return version ? `${name} ${version}` : name
}

function signalToneFromScore(score: number, threshold: number): UiRiskSignal["tone"] {
  if (score >= threshold) return "danger"
  if (score >= 0.3) return "warning"
  return "neutral"
}

function signalBadgeFromScore(score: number, detected: boolean, threshold: number) {
  if (score >= threshold || detected) return "우선 확인"
  if (score >= 0.3) return "추가 검토"
  return "낮음"
}

function buildForgeryRiskSignals(data: EvidenceDetailData | null, threshold: number): UiRiskSignal[] {
  const modelScores = (data?.analysisInfo.modelScores ?? []).filter(
    (score) => score.moduleName?.toLowerCase() === "forgery_spatial"
  )
  const modules = (data?.analysisInfo.moduleResults ?? []).filter(
    (module) => module.moduleName?.toLowerCase() === "forgery_spatial"
  )

  const sources =
    modelScores.length > 0
      ? modelScores.map((score) => ({
          moduleName: score.moduleName,
          score: score.score,
          detected: Boolean(score.detected),
          modelName: score.modelName,
          modelVersion: score.modelVersion,
          affectedSegments: null as EvidenceDetailData["analysisInfo"]["moduleResults"][number]["affectedSegments"],
        }))
      : modules

  if (sources.length === 0) {
    return (data?.analysisInfo.moduleResults ?? [])
      .filter((module) => isForgeryKeywordText(module.moduleName))
      .map((module) => {
        const score = normalizeResultValue(module.score)
        const label = formatForgeryModuleLabel(module.moduleName)
        return {
          label,
          modelLabel: formatModuleModelName(module),
          definition: formatForgeryDefinition(label),
          badge: signalBadgeFromScore(score, module.detected, threshold),
          score,
          thresholdPercent: Math.round(threshold * 100),
          tone: signalToneFromScore(score, threshold),
          segments: (module.affectedSegments ?? []).map((segment) => ({
            label: `${formatDuration(segment.startTime)} ~ ${formatDuration(segment.endTime)}`,
            startSec: segment.startTime,
          })),
        }
      })
      .sort((a, b) => normalizeResultValue(b.score) - normalizeResultValue(a.score))
  }

  return sources
    .map((source) => {
      const score = normalizeResultValue(source.score)
      const moduleName = source.moduleName ?? "forgery_spatial"
      const label =
        moduleName.toLowerCase() === "forgery_spatial"
          ? "TruFor (Spatial)"
          : formatForgeryModuleLabel(moduleName)
      const modelLabel =
        source.modelName?.trim()
          ? source.modelVersion?.trim()
            ? `${source.modelName.trim()} ${source.modelVersion.trim()}`
            : source.modelName.trim()
          : null
      return {
        label,
        modelLabel,
        definition: formatForgeryDefinition(label),
        badge: signalBadgeFromScore(score, source.detected, threshold),
        score,
        thresholdPercent: Math.round(threshold * 100),
        tone: signalToneFromScore(score, threshold),
        segments: (source.affectedSegments ?? []).map((segment) => ({
          label: `${formatDuration(segment.startTime)} ~ ${formatDuration(segment.endTime)}`,
          startSec: segment.startTime,
        })),
      }
    })
    .sort((a, b) => normalizeResultValue(b.score) - normalizeResultValue(a.score))
}

function extractForgeryRawVersion(modelLabel: string | null | undefined) {
  const trimmed = modelLabel?.trim()
  if (!trimmed) return null
  const parts = trimmed.split(/\s+/).filter(Boolean)
  // "TruFor trufor-videocof-v2-..." → last token is the checkpoint/version id
  return parts.length > 1 ? parts[parts.length - 1] : parts[0]
}

/** 긴 체크포인트 id를 방법론 카드용 짧은 버전으로 축약 */
function shortenForgeryMethodologyVersion(
  methodId: "trufor" | "timesformer",
  modelLabel: string | null | undefined,
  fallback: string
) {
  let raw = extractForgeryRawVersion(modelLabel)
  if (!raw) return fallback

  raw = raw.replace(/-\d{8}-\d{4}$/, "")

  if (methodId === "trufor") {
    const videocof = raw.match(/videocof-v[\w.]+/i)
    if (videocof) return videocof[0]
    return raw.replace(/^trufor-/i, "") || fallback
  }

  // timesformer-forgery-v1.9-hardneg → forgery-v1.9-hardneg
  return raw.replace(/^timesformer-/i, "") || fallback
}

function buildForgeryMethodologyItems(signals: UiRiskSignal[]) {
  return FORGERY_METHOD_BASELINES.map((method) => {
    const signal = signals.find((item) => method.match(item)) ?? null
    const defaultThreshold =
      method.id === "trufor"
        ? DEFAULT_FORGERY_THRESHOLDS.spatial
        : DEFAULT_FORGERY_THRESHOLDS.temporal
    const threshold =
      signal?.thresholdPercent != null && signal.thresholdPercent > 0
        ? signal.thresholdPercent / 100
        : defaultThreshold
    const score = signal != null ? normalizeResultValue(signal.score) : null
    const overThreshold = score != null && score >= threshold
    const shortVersion = shortenForgeryMethodologyVersion(
      method.id,
      signal?.modelLabel,
      method.fallbackVersion
    )

    return {
      name: method.name,
      version: shortVersion,
      role: method.role,
      output: signal != null ? `${method.output}, 이번 분석 점수` : method.output,
      available: signal != null,
      score,
      threshold,
      overThreshold,
    }
  })
}

function buildResultSummaryParagraph(data: EvidenceDetailData | null, verdict: string, score: number) {
  if (data?.analysisInfo.summary) {
    return data.analysisInfo.summary
  }

  if (Number.isFinite(score) && score > 0) {
    return `AI 기반 분석 결과 ${verdict} 신호가 확인되었으며, 위험 점수는 ${Math.round(score)} / 100입니다.`
  }

  return "분석이 완료되었습니다."
}

function buildResultDetectionBars(data: EvidenceDetailData | null) {
  const modules = getDetectionModules(data?.analysisInfo.moduleResults ?? [])
  if (modules.length > 0) {
    return modules.slice(0, 4).map((module) => ({
      label: formatModuleLabel(module.moduleName),
      value: normalizeResultValue(module.score),
    }))
  }

  return []
}

function findModuleByKeywords(modules: EvidenceDetailData["analysisInfo"]["moduleResults"], keywords: string[]) {
  return modules.find((module) => {
    const name = module.moduleName.toLowerCase()
    return keywords.some((keyword) => name.includes(keyword))
  })
}

function sanitizeAnalysisSummaryForUi(summary: string | null | undefined) {
  if (!summary?.trim()) return ""

  // Deepfake late-fusion narrative only — drop forgery lane / TruFor skip notes from 종합 소견.
  return summary
    .replace(/\s*위변조\(TruFor\)[\s\S]*$/i, "")
    .replace(/\s*Forgery\s+spatial[\s\S]*$/i, "")
    .replace(/\s*Forgery\s+temporal[\s\S]*$/i, "")
    .replace(/\s*\(TruFor produced no finite frame scores\)/gi, "")
    .replace(/\s{2,}/g, " ")
    .trim()
}

function getDetectionTone(value: number): { level: string; badgeClass: string; barClass: string } {
  if (value >= 0.6) return { level: "높음", badgeClass: "bg-red-50 text-red-700", barClass: "bg-red-700" }
  if (value >= 0.3) return { level: "보통", badgeClass: "bg-amber-100 text-amber-700", barClass: "bg-amber-500" }
  return { level: "낮음", badgeClass: "bg-emerald-100 text-emerald-700", barClass: "bg-emerald-500" }
}

function buildDetectionContext(moduleName: string, index: number, frameScores: FrameScore[]) {
  const label = formatModuleLabel(moduleName)
  const normalizedName = moduleName.toLowerCase()
  const basis =
    normalizedName.includes("boundary") || normalizedName.includes("face")
      ? "얼굴 윤곽선과 주변 배경의 연결성이 낮게 측정됨"
      : normalizedName.includes("compression") || normalizedName.includes("artifact")
        ? "얼굴 주변 압축 패턴이 주변 영역보다 높게 나타남"
        : normalizedName.includes("timeline") || normalizedName.includes("temporal")
          ? "프레임 간 움직임 흐름이 일부 구간에서 불연속적으로 측정됨"
          : normalizedName.includes("metadata")
            ? "파일 메타데이터와 프레임 특성 간 차이가 확인됨"
            : `${label} 점수가 기준값보다 높게 측정됨`

  return {
    basis,
    interval: buildDetectionInterval(index, frameScores),
  }
}

function buildDetectionInterval(index: number, frameScores: FrameScore[]) {
  if (frameScores.length === 0) return "대표 구간 없음"

  const scoredFrames = frameScores
    .map((frame, frameIndex) => ({
      timeSec: frame.timeSec ?? frameIndex,
      score: normalizeResultValue(frame.score),
    }))
    .sort((a, b) => b.score - a.score)
  const target = scoredFrames[index % scoredFrames.length]
  const maxTime = Math.max(...scoredFrames.map((frame) => frame.timeSec), target.timeSec)
  const windowSec = Math.max(1, Math.min(4, Math.round(maxTime / 8) || 1))
  const start = Math.max(0, target.timeSec - windowSec)
  const end = Math.min(Math.max(target.timeSec + windowSec, start + 1), Math.max(maxTime, target.timeSec + 1))

  return `${formatDuration(start)} - ${formatDuration(end)}`
}

function formatResultScore(score: number | null) {
  const normalized = normalizeScore(score)
  if (normalized == null) return null
  return String(Math.round(normalized))
}

function shortHash(hash: string) {
  if (!hash || hash === "-") return "-"
  if (hash.length <= 18) return hash
  return `${hash.slice(0, 10)}...${hash.slice(-8)}`
}

function buildBlockchainAnchorsFromStatus(
  status: BlockchainAnchorStatusResponse
): BlockchainAnchorItem[] {
  const anchors: BlockchainAnchorItem[] = []

  if (status.evidenceHashAnchor) {
    anchors.push(
      mapBlockchainRecordToItem(status.evidenceHashAnchor, {
        id: `evidence-${status.evidenceHashAnchor.anchorId}`,
        title: "증거 등록 앵커",
        target: "원본 SHA-256 (EVIDENCE_HASH)",
      })
    )
  }

  for (const reportAnchor of status.reportHashAnchors ?? []) {
    anchors.push(
      mapBlockchainRecordToItem(reportAnchor, {
        id: `report-${reportAnchor.anchorId}`,
        title: "보고서 앵커",
        target: reportAnchor.reportId != null
          ? `PDF 보고서 해시 (reportId=${reportAnchor.reportId})`
          : "PDF 보고서 해시 (REPORT_HASH)",
      })
    )
  }

  if (status.latestMerkleRootAnchor) {
    anchors.push(
      mapBlockchainRecordToItem(status.latestMerkleRootAnchor, {
        id: `merkle-${status.latestMerkleRootAnchor.anchorId}`,
        title: "Merkle Root 앵커",
        target: status.latestMerkleRootAnchor.merkleBatchDate
          ? `일별 CoC Merkle Root (${status.latestMerkleRootAnchor.merkleBatchDate})`
          : "일별 CoC Merkle Root",
      })
    )
  }

  return anchors
}

function mapBlockchainRecordToItem(
  record: BlockchainAnchorRecord,
  meta: { id: string; title: string; target: string }
): BlockchainAnchorItem {
  const statusRaw = (record.status ?? "").toUpperCase()
  const verification = resolveAnchorVerification(record)

  return {
    id: meta.id,
    title: meta.title,
    target: meta.target,
    status: getBlockchainStatusLabel(record.status),
    statusRaw,
    subjectHash: record.subjectHash || "-",
    transactionId: record.transactionHash ?? null,
    anchoredAt: record.anchoredAt ?? null,
    network: record.network || "hyperledger-fabric-forenshield",
    channel: "forenshield-evidence",
    chaincode: "anchor",
    blockNumber: record.blockNumber != null ? String(record.blockNumber) : null,
    signature: record.signature ?? null,
    signerCertHash: record.signerCertHash ?? null,
    certVerified: record.certVerified ?? null,
    offchainLogHash: record.offchainLogHash ?? null,
    offchainRefJson: record.offchainRefJson ?? null,
    errorCode: record.errorCode ?? null,
    reportId: record.reportId ?? null,
    merkleBatchDate: record.merkleBatchDate ?? null,
    merkleLeafCount: record.merkleLeafCount ?? null,
    analysisModel: parseAnalysisModelJson(record.analysisModelJson),
    analysisModules: parseAnalysisModulesJson(record.analysisModulesJson),
    verificationResult: verification.label,
    verificationTone: verification.tone,
  }
}

function resolveAnchorVerification(record: BlockchainAnchorRecord): {
  label: string
  tone: "safe" | "danger" | "neutral"
} {
  const status = (record.status ?? "").toUpperCase()
  if (status === "FAILED") {
    return {
      label: record.errorCode ? `앵커링 실패 (${record.errorCode})` : "앵커링 실패",
      tone: "danger",
    }
  }
  if (status === "PENDING") {
    return { label: "앵커링 진행 중", tone: "neutral" }
  }
  if (status !== "ANCHORED") {
    return { label: "미앵커", tone: "neutral" }
  }
  if (record.certVerified === true) {
    return { label: "앵커 기록·서명 검증 일치", tone: "safe" }
  }
  if (record.certVerified === false) {
    return { label: "앵커 기록 있음 · 서명 미검증", tone: "danger" }
  }
  return { label: "앵커 기록 확인", tone: "safe" }
}

function getBlockchainStatusLabel(status: string) {
  const s = (status ?? "").toUpperCase()
  if (s === "ANCHORED") return "앵커링 완료"
  if (s === "PENDING") return "앵커링 진행 중"
  if (s === "FAILED") return "앵커링 실패"
  if (s === "NOT_ANCHORED") return "미앵커"
  return status || "-"
}

function getAnchorTypeLabel(anchorType: string) {
  const t = (anchorType ?? "").toUpperCase()
  if (t === "EVIDENCE_HASH") return "증거 해시"
  if (t === "REPORT_HASH") return "보고서 해시"
  if (t === "MERKLE_ROOT") return "머클 루트 (일괄)"
  return anchorType || "-"
}

function getCocEventLabel(eventType: string) {
  const labels: Record<string, string> = {
    UPLOAD: "증거 등록",
    EVIDENCE_UPLOADED: "증거 파일 업로드",
    EVIDENCE_REGISTERED: "증거 등록",
    EVIDENCE_DELETED: "증거 삭제",
    EVIDENCE_VIEWED: "증거 열람",
    HASH_CREATED: "해시 생성",
    HASH_GENERATED: "해시 생성",
    FILE_HASH_CREATED: "파일 해시 생성",
    METADATA_EXTRACTED: "메타데이터 추출",
    INTEGRITY_VERIFIED: "무결성 검증",
    QUALITY_WARNING_ACKNOWLEDGED: "화질 안내 확인",
    ANALYSIS_REQUESTED: "분석 요청",
    ANALYSIS_STARTED: "분석 시작",
    FRAME_ANALYSIS_STARTED: "프레임 분석 시작",
    ANALYSIS_COMPLETED: "분석 완료",
    ANALYSIS_FAILED: "분석 실패",
    ANALYSIS_CANCELLED: "분석 중단",
    ANALYSIS_COPY_CREATED: "분석용 사본 생성",
    ANALYSIS_COPY_VERIFIED: "분석용 사본 검증",
    ANALYSIS_COPY_DELETED: "분석용 사본 삭제",
    COMPARE_VERIFICATION_STARTED: "비교검증 시작",
    COMPARE_VERIFICATION_COMPLETED: "비교검증 완료",
    REPORT_GENERATED: "보고서 생성",
    REPORT_CREATED: "보고서 생성",
    REPORT_DOWNLOADED: "보고서 다운로드",
    EVIDENCE_HLS_PACKAGED: "HLS 패키징",
    ERROR_OCCURRED: "오류 발생",
  }

  return labels[normalizeCocEventType(eventType)] ?? eventType
}

// 점 색상 = 현재 상태. 실패=빨강, 완료·검증=초록, 진행 중=파랑
function getCocEventDotClass(log: EvidenceDetailData["cocLogs"][number]) {
  const text = normalizeCocEventText(`${log.eventType} ${log.description ?? ""}`)
  if (/(FAILED|FAILURE|ERROR|BROKEN|INVALID|MISMATCH|실패|오류|에러|불일치)/.test(text)) {
    return "bg-red-700"
  }
  if (
    /(COMPLETED|CREATED|GENERATED|EXTRACTED|VERIFIED|UPLOADED|REGISTERED|ACKNOWLEDGED|PACKAGED|DOWNLOADED|VIEWED|DELETED|APPROVED|COPIED|STORED|SAVED|ANCHORED|VALID|MATCH|SUCCESS|완료|생성|검증|등록|업로드|추출|확인|일치|저장|패키징|다운로드|열람|삭제)/.test(
      text
    )
  ) {
    return "bg-emerald-500"
  }
  return "bg-blue-500"
}

function normalizeCocEventType(eventType: string) {
  return (eventType ?? "").trim().toUpperCase()
}

function normalizeCocEventText(value: string) {
  return value.trim().toUpperCase()
}

type CocActorDisplay = {
  label: string
  detail?: string
  role: string
  roleClass: string
}

// 행위자(userId)를 역할 라벨로 변환 — 백엔드가 실명 필드를 주기 전에는 현재 세션과 매칭해 사람이 읽기 좋은 이름으로 표시한다.
function getCocActor(userId: string, session?: AuthSession | null): CocActorDisplay {
  const rawUserId = (userId || "").trim()
  const id = rawUserId.toLowerCase()

  const currentUser = getAppUserFromSession(session ?? null)
  const matchesCurrentUser =
    Boolean(currentUser) &&
    Boolean(rawUserId) &&
    (rawUserId === session?.loginId || rawUserId === session?.userId || rawUserId === currentUser?.id)

  if (matchesCurrentUser && currentUser) {
    return {
      label: `${currentUser.name} · ${roleLabelMap[currentUser.role]}`,
      detail: `ID ${rawUserId}`,
      role: roleLabelMap[currentUser.role],
      roleClass: getCocRoleClass(currentUser.role),
    }
  }

  if (id === "mock-user") {
    return {
      label: "강팀장 · 분석관",
      detail: "서울경찰청 사이버수사팀",
      role: "분석관",
      roleClass: "bg-blue-100 text-blue-700",
    }
  }
  if (id === "mock-system") {
    return {
      label: "ForenShield 시스템",
      detail: "자동 처리",
      role: "시스템",
      roleClass: "bg-slate-100 text-slate-600",
    }
  }
  if (id.includes("admin") || id.includes("관리자"))
    return { label: rawUserId, detail: "관리자 계정", role: "관리자", roleClass: "bg-violet-100 text-violet-700" }
  if (id.includes("review") || id.includes("검토"))
    return { label: rawUserId, detail: "검토자 계정", role: "검토자", roleClass: "bg-amber-100 text-amber-700" }
  if (id.includes("ai") || id.includes("worker")) {
    const workerLabel = rawUserId.match(/\d+/)?.[0]
    return {
      label: "AI 분석 엔진",
      detail: workerLabel ? `Worker ${workerLabel}` : "자동 분석",
      role: "AI 엔진",
      roleClass: "bg-teal-100 text-teal-700",
    }
  }
  if (id.includes("system"))
    return { label: "ForenShield 시스템", detail: "자동 처리", role: "시스템", roleClass: "bg-slate-100 text-slate-600" }
  return { label: rawUserId || "-", role: "분석관", roleClass: "bg-blue-100 text-blue-700" }
}

function getCocRoleClass(role: string) {
  if (role === "ORG_ADMIN") return "bg-violet-100 text-violet-700"
  if (role === "REVIEWER") return "bg-amber-100 text-amber-700"
  return "bg-blue-100 text-blue-700"
}

function getLifecycleLabel(status: string) {
  if (status === "EXCLUDED") return "사용제외"
  if (status === "REPLACED") return "대체됨"
  return "활성"
}

function getLifecycleClassName(status: string) {
  if (status === "EXCLUDED") return "bg-slate-100 text-slate-600"
  if (status === "REPLACED") return "bg-orange-100 text-orange-700"
  return "bg-emerald-100 text-emerald-700"
}

function getRoleLabel(role?: string | null) {
  if (role === "PRIMARY") return "주요 증거"
  return "보충 증거"
}

function getEvidenceStatusLabel(status: string) {
  if (status === "COMPLETED") return "분석 완료"
  if (status === "PROCESSING") return "처리 중"
  if (status === "FAILED") return "실패"
  return "미분석"
}

function getEvidenceAnalysisLabel(evidence: CaseEvidenceSummary) {
  const status = normalizeStatus(evidence.analysisStatus ?? "PENDING")
  if (isEvidenceAnalysisRunning(evidence)) {
    return status === "PENDING" ? "분석 대기" : "분석 중"
  }

  if (status === "COMPLETED") return getEvidenceRiskVerdictLabel(evidence) ?? "분석 완료"
  return getEvidenceStatusLabel(status)
}

function isSupplementReviewStatus(status?: string | null) {
  return (
    status === "REVIEW_SUPPLEMENT_REQUESTED" ||
    status === "SUPPLEMENT_REQUESTED" ||
    status === "REVIEW_REVISION_REQUESTED" ||
    status === "REVISION_REQUESTED" ||
    status === "REVIEW_NEEDS_CHANGES"
  )
}

function formatReviewEventDate(value?: string | null) {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  return `${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`
}

function getEvidenceRiskVerdictLabel(evidence: CaseEvidenceSummary): string | null {
  const riskLevel = evidence.riskLevel
  const riskScore = evidence.riskScore ?? null

  if (riskLevel == null && riskScore == null) return null
  if (riskLevel === "HIGH" || (riskScore != null && riskScore >= 70)) return "위험"
  if (riskLevel === "MEDIUM" || (riskScore != null && riskScore >= 45)) return "주의"
  return "정상"
}

function getEvidenceAnalysisBadgeClassName(evidence: CaseEvidenceSummary) {
  const status = normalizeStatus(evidence.analysisStatus ?? "PENDING")

  if (isEvidenceAnalysisRunning(evidence)) return "bg-blue-50 text-blue-700"
  if (status === "FAILED") return "bg-red-50 text-red-700"
  if (status === "PENDING") return "bg-slate-100 text-slate-400"

  const verdict = getEvidenceRiskVerdictLabel(evidence)
  if (verdict === "위험") return "bg-red-50 text-red-700"
  if (verdict === "주의") return "bg-amber-50 text-amber-700"
  if (verdict == null) return "bg-slate-100 text-slate-500"
  return "bg-emerald-50 text-emerald-700"
}

function getEvidenceRowStatusClassName(evidence: CaseEvidenceSummary, running: boolean) {
  const lifecycle = evidence.lifecycleStatus ?? "ACTIVE"
  if (lifecycle !== "ACTIVE") return "text-slate-400"
  if (running) return "text-muted-foreground"

  const status = normalizeStatus(evidence.analysisStatus ?? "PENDING")
  if (status === "FAILED") return "text-red-700"
  if (status !== "COMPLETED") return "text-muted-foreground"

  const verdict = getEvidenceRiskVerdictLabel(evidence)
  if (verdict === "위험") return "text-red-700"
  if (verdict === "주의") return "text-amber-600"
  return "text-emerald-600"
}

function getCompareVerificationLabel(result: StoredCompareResultSummary | null) {
  if (!result) return "미검증"
  if (result.verdict === "ORIGINAL_MATCH") return "일치"
  if (result.verdict === "TAMPERED") return "불일치"
  if (result.mismatchCount > 0) return "불일치"
  return result.verdictLabel || "판정 보류"
}

function isEvidenceAnalysisRunning(evidence: CaseEvidenceSummary) {
  const lifecycle = evidence.lifecycleStatus ?? "ACTIVE"
  const status = normalizeStatus(evidence.analysisStatus ?? "PENDING")

  // PROCESSING(=백엔드 ANALYZING)만 분석 중. PENDING+progress 0은 미요청/대기.
  return lifecycle === "ACTIVE" && status === "PROCESSING"
}

function isEvidenceSelectableForAnalysis(evidence: CaseEvidenceSummary) {
  const lifecycle = evidence.lifecycleStatus ?? "ACTIVE"
  const status = normalizeStatus(evidence.analysisStatus ?? "PENDING")

  return lifecycle === "ACTIVE" && (status === "PENDING" || status === "FAILED")
}

function isEvidenceSelectableForAnalysisType(
  evidence: CaseEvidenceSummary,
  analysisType: AnalysisType
) {
  if (analysisType === "DEEPFAKE") {
    return isEvidenceSelectableForAnalysis(evidence)
  }
  if (analysisType === "INTEGRITY") {
    return (evidence.lifecycleStatus ?? "ACTIVE") === "ACTIVE"
  }
  return false
}

function isEvidenceExcludable(evidence: CaseEvidenceSummary) {
  const lifecycle = evidence.lifecycleStatus ?? "ACTIVE"
  const status = normalizeStatus(evidence.analysisStatus ?? "PENDING")

  return lifecycle === "ACTIVE" && status === "PENDING"
}

function getAnalysisTypeLabel(type: AnalysisType) {
  if (type === "INTEGRITY") return "위변조/무결성 검증"
  if (type === "COMPARE") return "비교검증"
  return "딥페이크 탐지"
}

function getRunningAnalysisCopy(type: AnalysisType, status: AnalysisStatus, progress: number) {
  const currentProgress = Math.max(0, Math.min(100, progress))

  if (type === "INTEGRITY") {
    return currentProgress < 55
      ? {
          title: "무결성 검증 중",
          detail: "원본 해시와 파일 메타데이터의 일치 여부를 확인하고 있습니다.",
        }
      : {
          title: "해시 체인 확인 중",
          detail: "CoC 기록과 증거 해시 연결 상태를 대조하고 있습니다.",
        }
  }

  if (type === "COMPARE") {
    return currentProgress < 55
      ? {
          title: "비교검증 진행 중",
          detail: "기준 증거와 비교 대상의 시각적 특징을 맞춰 보고 있습니다.",
        }
      : {
          title: "기준 증거 대조 중",
          detail: "프레임별 차이와 불일치 구간을 계산하고 있습니다.",
        }
  }

  // Deepfake / forgery analysis stages (aligned with GPU worker progressPercent).
  if (currentProgress < 8) {
    return {
      title: "AI 분석 준비 중",
      detail:
        status === "PENDING"
          ? "분석 작업을 등록하고 원본 파일 정보를 확인하고 있습니다."
          : "영상을 준비하고 처리 순서를 맞추고 있습니다.",
    }
  }

  if (currentProgress < 18) {
    return {
      title: "AI 분석 중",
      detail: "영상을 내려받고 모델 추론을 준비하고 있습니다.",
    }
  }

  if (currentProgress < 36) {
    return {
      title: "AI 분석 중",
      detail: "얼굴 영역을 검출하고 Xception(CNN)으로 분석하고 있습니다.",
    }
  }

  if (currentProgress < 58) {
    return {
      title: "AI 분석 중",
      detail: "TimeSformer로 프레임 간 시간적 일관성을 대조하고 있습니다.",
    }
  }

  if (currentProgress < 78) {
    return {
      title: "AI 분석 중",
      detail: "GMFlow로 광학 흐름 이상을 확인하고 있습니다.",
    }
  }

  if (currentProgress < 84) {
    return {
      title: "위험 신호 계산 중",
      detail: "모듈 결과를 융합해 최종 위험도를 계산하고 있습니다.",
    }
  }

  if (currentProgress < 90) {
    return {
      title: "위변조 분석 중",
      detail: "TruFor로 국소 위변조 신호를 확인하고 있습니다.",
    }
  }

  return {
    title: "결과 정리 중",
    detail: "대표 프레임과 탐지 결과를 사건 증거 정보에 반영하고 있습니다.",
  }
}

const TAB_VALUES = ["summary", "deepfake", "integrity", "report"]

function EvidenceWorkspace({
  data,
  evidences,
  selectedEvidenceId,
  onSelectEvidence,
  copied,
  onCopyHash,
  hideSummaryCard = false,
}: {
  data: EvidenceDetailData
  evidences: CaseEvidenceSummary[]
  selectedEvidenceId: number | null
  onSelectEvidence: (evidenceId: number) => void
  copied: boolean
  onCopyHash: () => void
  hideSummaryCard?: boolean
}) {
  const { evidenceInfo, analysisInfo } = data
  const riskTone = getCaseRiskTone(data)
  const riskClassName = getCaseRiskClassName(riskTone)
  const displayRiskLabel = getDisplayRiskLabel(data)
  const extension = getFileExtension(evidenceInfo.fileName, evidenceInfo.mediaType)
  const progressSteps = buildProgressSteps(data)
  const reportReady = analysisInfo.status === "COMPLETED"
  // 내용 전환은 클릭으로만. hover는 파란 밑줄(인디케이터)만 따라가게 별도 상태로 관리.
  const [activeTab, setActiveTab] = useState("summary")
  const [hoveredTab, setHoveredTab] = useState<string | null>(null)
  const litTab = hoveredTab ?? activeTab
  const litIndex = Math.max(0, TAB_VALUES.indexOf(litTab))
  const tabClass = (value: string) =>
    cn(
      "z-10 h-full min-w-0 rounded-none px-5 text-base font-medium outline-none transition-colors after:hidden focus-visible:ring-0 focus-visible:outline-none",
      litTab === value ? "text-blue-600 dark:text-blue-400" : "text-muted-foreground"
    )

  return (
    <section className="min-w-0 space-y-4">
      {hideSummaryCard ? null : (
        <EvidenceSummaryCard
          data={data}
          extension={extension}
          riskLabel={displayRiskLabel}
          statusLabel={getStatusLabel(analysisInfo.status)}
          riskBadgeClassName={riskClassName.badge}
          riskTextClassName={riskClassName.text}
          evidences={evidences}
          selectedEvidenceId={selectedEvidenceId}
          onSelectEvidence={onSelectEvidence}
        />
      )}

      <Tabs value={activeTab} onValueChange={setActiveTab} className="gap-4">
        <section className="overflow-hidden rounded-xl border border-border bg-card shadow-sm">
          <TabsList
            variant="line"
            onMouseLeave={() => setHoveredTab(null)}
            className="relative !grid h-16 w-full grid-cols-4 rounded-none border-b-0 bg-card p-0 after:absolute after:inset-x-0 after:bottom-0 after:h-[3px] after:bg-border after:content-['']"
          >
            <TabsTrigger
              value="summary"
              onMouseEnter={() => setHoveredTab("summary")}
              className={tabClass("summary")}
            >
              분석 요약
            </TabsTrigger>
            <TabsTrigger
              value="deepfake"
              onMouseEnter={() => setHoveredTab("deepfake")}
              className={tabClass("deepfake")}
            >
              딥페이크 탐지
            </TabsTrigger>
            <TabsTrigger
              value="integrity"
              onMouseEnter={() => setHoveredTab("integrity")}
              className={tabClass("integrity")}
            >
              무결성 검증
            </TabsTrigger>
            <TabsTrigger
              value="report"
              onMouseEnter={() => setHoveredTab("report")}
              className={tabClass("report")}
            >
              메타데이터/보고서
            </TabsTrigger>

            {/* 활성/hover 탭으로 부드럽게 슬라이드하는 파란 밑줄 */}
            <span
              aria-hidden="true"
              className="pointer-events-none absolute bottom-0 z-20 h-[3px] bg-blue-500 transition-[left] duration-300 ease-out"
              style={{ left: `${litIndex * 25}%`, width: "25%" }}
            />
          </TabsList>

          <div className="p-4">
            <TabsContent value="summary" className="space-y-5">
              <SummaryTab
                data={data}
                riskLabel={displayRiskLabel}
                riskSoftClassName={riskClassName.soft}
                progressSteps={progressSteps}
              />
            </TabsContent>

            <TabsContent value="deepfake" className="space-y-5">
              <DeepfakeV2Tab data={data} />
            </TabsContent>

            <TabsContent value="integrity" className="space-y-5">
              <IntegrityTab data={data} copied={copied} onCopyHash={onCopyHash} />
            </TabsContent>

            <TabsContent value="report" className="space-y-5">
              <MetadataReportTab data={data} extension={extension} reportReady={reportReady} />
            </TabsContent>
          </div>
        </section>
      </Tabs>
    </section>
  )
}
