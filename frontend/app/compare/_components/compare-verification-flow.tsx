"use client"

import { useEffect, useRef, useState, type DragEvent } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { Check } from "lucide-react"

import { CompareFileUploader } from "./compare-file-uploader"
import { CompareProcessingPanel } from "./compare-processing-panel"
import { CompareResultPanel } from "./compare-result-panel"
import { SourceEvidenceSelector } from "./source-evidence-selector"
import { StepUpGateDialogs } from "@/components/step-up-gate"
import {
  cancelCompareVerification,
  downloadCompareReport,
  fetchCompareOriginal,
  fetchCompareOriginals,
  verifyCompare,
  type CompareOriginal,
  type CompareResult,
} from "@/lib/api/compare"
import { getApiErrorMessage } from "@/lib/api/errors"
import { saveCompareResultSummary } from "@/lib/compare-history"
import { fetchCaseDetail, type CaseDetailData } from "@/lib/api/evidence-detail"
import { fetchMyAnalysisHistory } from "@/lib/api/mypage"
import { getSession, isReviewerSession } from "@/lib/auth"
import { features } from "@/lib/features"
import { useStepUpGate } from "@/hooks/use-step-up-gate"
import type { HlsPlayback } from "@/lib/hls-playback"
import { formatFileSize as formatSharedFileSize } from "@/lib/formatters"
import { getAnalysisStatusLabel } from "@/lib/status-labels"
import { cn } from "@/lib/utils"

type CompareStep = "source" | "upload" | "processing" | "result"

export type SourceEvidence = {
  id: number
  caseId: string
  displayLabel: string
  name: string
  analysisStatus: string
  isCompareReady: boolean
  dateLabel: string
  sizeLabel: string
  codecLabel: string
  durationLabel: string
  hashLabel: string
  thumbnailUrl?: string | null
  hlsStatus?: string | null
}

export type SourceCase = {
  id: string
  title: string
  department: string
  updatedAtLabel: string
  evidences: SourceEvidence[]
}

export type UploadedCompareFile = {
  file: File
  name: string
  previewUrl: string
  sizeLabel: string
}

const EMPTY_CASE: SourceCase = {
  id: "",
  title: "사건을 선택하세요",
  department: "-",
  updatedAtLabel: "-",
  evidences: [],
}

const EMPTY_EVIDENCE: SourceEvidence = {
  id: 0,
  caseId: "",
  displayLabel: "기준 증거",
  name: "기준 증거를 선택하세요",
  analysisStatus: "PENDING",
  isCompareReady: false,
  dateLabel: "-",
  sizeLabel: "-",
  codecLabel: "-",
  durationLabel: "-",
  hashLabel: "-",
}

export function CompareVerificationFlow() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const preselectedCaseId = searchParams.get("caseId") ?? ""
  const preselectedEvidenceParam = searchParams.get("evidenceId")
  const preselectedEvidenceId = preselectedEvidenceParam ? Number(preselectedEvidenceParam) : null
  const hasPreselectedEvidence =
    Boolean(preselectedCaseId) &&
    preselectedEvidenceId != null &&
    Number.isFinite(preselectedEvidenceId) &&
    preselectedEvidenceId > 0
  const [step, setStep] = useState<CompareStep>("source")
  const [sourceCases, setSourceCases] = useState<SourceCase[]>([])
  const [selectedCaseId, setSelectedCaseId] = useState("")
  const [selectedEvidenceId, setSelectedEvidenceId] = useState<number | null>(null)
  const [evidenceQuery, setEvidenceQuery] = useState("")
  const [compareFile, setCompareFile] = useState<UploadedCompareFile | null>(null)
  const [compareResult, setCompareResult] = useState<CompareResult | null>(null)
  const [sourceError, setSourceError] = useState<string | null>(null)
  const [compareError, setCompareError] = useState<string | null>(null)
  const [isLoadingCases, setIsLoadingCases] = useState(true)
  const [isLoadingEvidences, setIsLoadingEvidences] = useState(false)
  const [progress, setProgress] = useState(0)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const compareRequestRef = useRef(0)
  const activeCompareRequestTokenRef = useRef<string | null>(null)
  const comparePreviewUrlRef = useRef<string | null>(null)
  const isReviewer = isReviewerSession(getSession())
  const [selectedHlsPlayback, setSelectedHlsPlayback] = useState<HlsPlayback | null>(null)
  const {
    dialogMode,
    loginId: stepUpLoginId,
    passwordLoading,
    passwordError,
    submitPassword,
    cancelPassword,
    closeSuccessDialog,
    fetchEvidenceDetailWithStepUp,
  } = useStepUpGate()

  useEffect(() => {
    let cancelled = false

    async function loadInitialCases() {
      setIsLoadingCases(true)
      setSourceError(null)

      try {
        if (!features.mockApi) {
          const response = await fetchCompareOriginals({ page: 0, size: 100 })
          if (cancelled) return

          let originals = response.content
          if (
            hasPreselectedEvidence &&
            preselectedEvidenceId != null &&
            !originals.some((original) => original.evidenceId === preselectedEvidenceId)
          ) {
            try {
              const preselectedOriginal = await fetchCompareOriginal(preselectedEvidenceId)
              originals = [preselectedOriginal, ...originals]
            } catch {
              // 목록에 없거나 접근할 수 없는 증거는 아래 선택 검증에서 안내합니다.
            }
          }

          const cases = mapCompareOriginalsToSourceCases(originals)
          const evidenceCase = cases.find((sourceCase) =>
            sourceCase.evidences.some(
              (evidence) => evidence.id === preselectedEvidenceId
            )
          )
          const firstCaseId =
            cases.some((sourceCase) => sourceCase.id === preselectedCaseId)
              ? preselectedCaseId
              : evidenceCase?.id ?? cases[0]?.id ?? ""
          const firstCase =
            cases.find((sourceCase) => sourceCase.id === firstCaseId) ?? EMPTY_CASE
          const preferredEvidenceId = getPreferredEvidenceId(
            firstCase.evidences,
            hasPreselectedEvidence ? preselectedEvidenceId : null
          )

          setSourceCases(cases)
          setSelectedCaseId(firstCaseId)
          setSelectedEvidenceId(preferredEvidenceId)

          if (hasPreselectedEvidence && preferredEvidenceId === preselectedEvidenceId) {
            setStep("upload")
          } else if (hasPreselectedEvidence) {
            setSourceError("비교검증 기준으로 사용할 수 있는 증거를 찾지 못했습니다.")
          }
          return
        }

        const response = await fetchMyAnalysisHistory({ sort: "newest", page: 0, size: 50 })
        if (cancelled) return

        const cases = response.content.map((sourceCase) => ({
          id: sourceCase.caseId,
          title: sourceCase.caseName,
          department: `${getCaseStatusLabel(sourceCase.status)} · 증거 ${sourceCase.evidenceCount}개`,
          updatedAtLabel: formatDateTimeLabel(sourceCase.createdAt),
          evidences: [],
        }))

        const firstCaseId = preselectedCaseId || cases[0]?.id || ""
        setSourceCases(cases)
        setSelectedCaseId(firstCaseId)

        if (firstCaseId) {
          try {
            const detail = await fetchCaseDetail(firstCaseId)
            if (cancelled) return

            const hydratedCase = mapCaseDetailToSourceCase(detail)
            setSourceCases((current) =>
              current.some((sourceCase) => sourceCase.id === hydratedCase.id)
                ? current.map((sourceCase) =>
                    sourceCase.id === hydratedCase.id ? hydratedCase : sourceCase
                  )
                : [hydratedCase, ...current]
            )
            const preferredEvidenceId =
              getPreferredEvidenceId(
                hydratedCase.evidences,
                hasPreselectedEvidence ? preselectedEvidenceId : null
              )

            setSelectedEvidenceId(preferredEvidenceId)

            if (hasPreselectedEvidence && preferredEvidenceId === preselectedEvidenceId) {
              setStep("upload")
            } else if (hasPreselectedEvidence) {
              setSourceError("딥페이크 분석이 완료된 증거만 비교검증 기준으로 사용할 수 있습니다.")
            }
          } catch (detailError) {
            if (cancelled) return
            setSourceError(
              getApiErrorMessage(detailError, "선택한 사건의 증거 목록을 불러오지 못했습니다. 다른 사건을 선택해 주세요.")
            )
          }
        }
      } catch (error) {
        if (cancelled) return
        setSourceCases([])
        setSelectedCaseId("")
        setSelectedEvidenceId(null)
        setSourceError(getApiErrorMessage(error, "사건 목록을 불러오지 못했습니다. 로그인 상태와 백엔드 연결을 확인해 주세요."))
      } finally {
        if (!cancelled) setIsLoadingCases(false)
      }
    }

    loadInitialCases()

    return () => {
      cancelled = true
    }
  }, [hasPreselectedEvidence, preselectedCaseId, preselectedEvidenceId])

  useEffect(() => {
    if (!selectedEvidenceId) {
      setSelectedHlsPlayback(null)
      return
    }

    let cancelled = false
    void fetchEvidenceDetailWithStepUp(selectedEvidenceId)
      .then((detail) => {
        if (!cancelled) setSelectedHlsPlayback(detail.hlsPlayback ?? null)
      })
      .catch(() => {
        if (!cancelled) setSelectedHlsPlayback(null)
      })

    return () => {
      cancelled = true
    }
  }, [selectedEvidenceId, fetchEvidenceDetailWithStepUp])

  useEffect(() => {
    if (step !== "processing") return

    setProgress(12)
    const interval = window.setInterval(() => {
      setProgress((current) => Math.min(92, current + 8))
    }, 260)

    return () => window.clearInterval(interval)
  }, [step])

  useEffect(() => {
    return () => {
      if (!comparePreviewUrlRef.current) return

      URL.revokeObjectURL(comparePreviewUrlRef.current)
      comparePreviewUrlRef.current = null
    }
  }, [])

  function handleFileChange(files: FileList | null) {
    const file = files?.item(0)
    if (!file) return

    const previewUrl = URL.createObjectURL(file)
    revokeComparePreviewUrl()
    comparePreviewUrlRef.current = previewUrl

    setCompareFile({
      file,
      name: file.name,
      previewUrl,
      sizeLabel: formatFileSize(file.size),
    })
    setCompareError(null)
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    handleFileChange(event.dataTransfer.files)
  }

  async function startCompare() {
    if (!compareFile || selectedEvidenceId === null || !selectedEvidence.isCompareReady) return

    const requestId = compareRequestRef.current + 1
    const requestToken =
      typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    compareRequestRef.current = requestId
    activeCompareRequestTokenRef.current = requestToken
    setProgress(0)
    setCompareError(null)
    setCompareResult(null)
    setStep("processing")

    try {
      const result = await verifyCompare(selectedEvidenceId, compareFile.file, requestToken)
      if (compareRequestRef.current !== requestId) return
      saveCompareResultSummary(result)
      setCompareResult(result)
      setProgress(100)
      window.setTimeout(() => router.push(`/compare/${result.compareId}`), 250)
    } catch (error) {
      if (compareRequestRef.current !== requestId) return
      setStep("upload")
      setCompareError(getApiErrorMessage(error, "비교 검증 요청에 실패했습니다."))
    } finally {
      if (compareRequestRef.current === requestId) {
        activeCompareRequestTokenRef.current = null
      }
    }
  }

  function cancelCompare() {
    const requestToken = activeCompareRequestTokenRef.current
    if (requestToken) {
      void cancelCompareVerification(requestToken).catch(() => undefined)
    }

    compareRequestRef.current += 1
    activeCompareRequestTokenRef.current = null
    setProgress(0)
    setStep("upload")
    setCompareError("비교 검증이 중단되었습니다.")
  }

  function resetCompare() {
    compareRequestRef.current += 1
    activeCompareRequestTokenRef.current = null
    setStep("source")
    setProgress(0)
    setSelectedCaseId(sourceCases[0]?.id ?? "")
    setSelectedEvidenceId(getPreferredEvidenceId(sourceCases[0]?.evidences ?? []))
    setEvidenceQuery("")
    clearCompareFile()
    setCompareResult(null)
    setCompareError(null)
  }

  function clearCompareFile() {
    revokeComparePreviewUrl()
    setCompareFile(null)
  }

  function revokeComparePreviewUrl() {
    if (!comparePreviewUrlRef.current) return

    URL.revokeObjectURL(comparePreviewUrlRef.current)
    comparePreviewUrlRef.current = null
  }

  async function selectCase(caseId: string) {
    const nextCase = sourceCases.find((sourceCase) => sourceCase.id === caseId)
    if (!nextCase) return

    setSelectedCaseId(caseId)
    setSelectedEvidenceId(getPreferredEvidenceId(nextCase.evidences))
    setEvidenceQuery("")
    setSourceError(null)

    if (nextCase.evidences.length > 0) return

    setIsLoadingEvidences(true)

    try {
      const detail = await fetchCaseDetail(caseId)
      const hydratedCase = mapCaseDetailToSourceCase(detail)
      setSourceCases((current) =>
        current.map((sourceCase) =>
          sourceCase.id === hydratedCase.id ? hydratedCase : sourceCase
        )
      )
      setSelectedEvidenceId(getPreferredEvidenceId(hydratedCase.evidences))
    } catch (error) {
      setSourceError(getApiErrorMessage(error, "선택한 사건의 증거 목록을 불러오지 못했습니다."))
    } finally {
      setIsLoadingEvidences(false)
    }
  }

  const selectedCase =
    sourceCases.find((sourceCase) => sourceCase.id === selectedCaseId) ?? sourceCases[0] ?? EMPTY_CASE
  const compareReadyEvidences = selectedCase.evidences.filter((evidence) => evidence.isCompareReady)
  const selectedEvidence =
    compareReadyEvidences.find((evidence) => evidence.id === selectedEvidenceId) ??
    EMPTY_EVIDENCE
  const filteredEvidences = compareReadyEvidences.filter((evidence) => {
    const searchValue =
      `${evidence.id} ${evidence.displayLabel} ${evidence.dateLabel}`.toLowerCase()
    return searchValue.includes(evidenceQuery.toLowerCase())
  })

  if (isReviewer) {
    return (
      <section className="rounded-xl border border-slate-200 bg-white px-4 py-7 text-center shadow-sm dark:border-border dark:bg-card sm:p-8">
        <p className="text-lg font-bold text-slate-950 dark:text-foreground">비교검증 열람 전용</p>
        <p className="mx-auto mt-2 max-w-xl text-sm font-semibold leading-6 text-slate-500 dark:text-muted-foreground">
          검토자는 새 비교검증을 실행할 수 없습니다. 배정된 사건 상세 화면에서 저장된 비교검증 결과만 열람할 수
          있습니다.
        </p>
        <button
          type="button"
          className="mt-5 inline-flex h-10 w-full items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground sm:w-auto"
          onClick={() => router.push("/mypage")}
        >
          배정 사건으로 이동
        </button>
      </section>
    )
  }

  return (
    <section className="w-full space-y-4">
      <StepUpGateDialogs
        mode={dialogMode}
        loginId={stepUpLoginId}
        loading={passwordLoading}
        error={passwordError}
        onSubmit={(password) => void submitPassword(password)}
        onCancel={cancelPassword}
        onSuccessClose={closeSuccessDialog}
      />
      <StepIndicator currentStep={step} />

      {step === "source" ? (
        <SourceEvidenceSelector
          evidenceQuery={evidenceQuery}
          selectedCaseId={selectedCaseId}
          selectedEvidenceId={selectedEvidenceId}
          selectedCase={selectedCase}
          cases={sourceCases}
          evidences={filteredEvidences}
          isLoadingCases={isLoadingCases}
          isLoadingEvidences={isLoadingEvidences}
          sourceError={sourceError}
          onEvidenceQueryChange={setEvidenceQuery}
          onSelectCase={selectCase}
          onSelectEvidence={setSelectedEvidenceId}
          onUnavailableEvidenceSelect={showAnalysisRequiredAlert}
          hlsPlayback={selectedHlsPlayback}
          onNext={() => {
            if (!selectedEvidence.isCompareReady) {
              setSourceError("딥페이크 분석이 완료된 증거만 비교검증 기준으로 사용할 수 있습니다.")
              return
            }

            setStep("upload")
          }}
        />
      ) : step === "upload" ? (
        <CompareFileUploader
          sourceEvidence={selectedEvidence}
          compareFile={compareFile}
          fileInputRef={fileInputRef}
          onDrop={handleDrop}
          onFileChange={handleFileChange}
          onRemoveFile={clearCompareFile}
          onBack={() => setStep("source")}
          onStart={startCompare}
          compareError={compareError}
        />
      ) : step === "processing" ? (
        <CompareProcessingPanel
          sourceEvidence={selectedEvidence}
          compareFile={compareFile}
          progress={progress}
          onCancel={cancelCompare}
        />
      ) : (
        <ResultStep result={compareResult} onReset={resetCompare} />
      )}
    </section>
  )
}

function StepIndicator({ currentStep }: { currentStep: CompareStep }) {
  const steps = [
    { key: "source", label: "기준 증거" },
    { key: "upload", label: "파일 업로드" },
    { key: "result", label: "결과" },
  ] as const
  const currentIndex = currentStep === "source" ? 0 : currentStep === "result" ? 2 : 1

  return (
    <ol className="flex flex-wrap items-center gap-2 text-xs font-semibold text-slate-400 dark:text-muted-foreground">
      {steps.map((step, index) => {
        const isActive = index === currentIndex
        const isDone = index < currentIndex

        return (
          <li key={step.key} className="flex items-center gap-2">
            {index > 0 ? <span className="w-8 border-t border-slate-300 dark:border-border" aria-hidden="true" /> : null}
            <span className={cn("flex items-center gap-1.5", (isActive || isDone) && "text-slate-950 dark:text-foreground")}>
              <span
                className={cn(
                  "flex size-5 items-center justify-center rounded-full text-[11px] font-bold",
                  isActive
                    ? "bg-slate-950 text-white dark:bg-foreground dark:text-background"
                    : isDone
                      ? "bg-slate-200 text-slate-600 dark:bg-secondary dark:text-foreground"
                      : "border border-slate-300 text-slate-400 dark:border-border"
                )}
              >
                {isDone ? <Check className="size-3" aria-hidden="true" /> : index + 1}
              </span>
              {step.label}
            </span>
          </li>
        )
      })}
    </ol>
  )
}

function ResultStep({ result, onReset }: { result: CompareResult | null; onReset: () => void }) {
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const [isDownloading, setIsDownloading] = useState(false)

  async function handleDownloadReport() {
    if (!result) return

    setDownloadError(null)
    setIsDownloading(true)

    try {
      const blob = await downloadCompareReport(result.compareId)
      const url = URL.createObjectURL(blob)
      const link = document.createElement("a")
      link.href = url
      link.download = `compare-report-${result.compareId}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (error) {
      setDownloadError(getApiErrorMessage(error, "PDF 리포트 다운로드에 실패했습니다."))
    } finally {
      setIsDownloading(false)
    }
  }

  return (
    <CompareResultPanel
      result={result}
      downloadError={downloadError}
      isDownloading={isDownloading}
      reportApproved={false}
      onReset={onReset}
      onDownloadReport={handleDownloadReport}
    />
  )
}

function mapCaseDetailToSourceCase(caseDetail: CaseDetailData): SourceCase {
  const activeEvidences = caseDetail.evidences.filter(
    (evidence) => (evidence.lifecycleStatus ?? "ACTIVE") === "ACTIVE"
  )

  return {
    id: caseDetail.caseId,
    title: caseDetail.caseName,
    department: getCaseStatusLabel(caseDetail.status),
    updatedAtLabel: formatDateTimeLabel(caseDetail.createdAt),
    evidences: activeEvidences.map((evidence, index) => ({
      id: evidence.evidenceId,
      caseId: caseDetail.caseId,
      displayLabel: evidence.displayLabel || `기준 증거 ${index + 1}`,
      name: evidence.fileName,
      analysisStatus: evidence.analysisStatus,
      isCompareReady: isCompareReadyStatus(evidence.analysisStatus),
      dateLabel: getCaseStatusLabel(evidence.analysisStatus),
      sizeLabel: "-",
      codecLabel: getMediaTypeLabel(evidence.mediaType),
      durationLabel: "-",
      hashLabel: "-",
      thumbnailUrl: evidence.thumbnailUrl,
      hlsStatus: evidence.hlsStatus,
    })),
  }
}

function mapCompareOriginalsToSourceCases(originals: CompareOriginal[]): SourceCase[] {
  const cases = new Map<string, SourceCase>()

  for (const original of originals) {
    const caseId =
      original.caseName?.trim() ||
      original.caseNumber?.trim() ||
      `evidence-${original.evidenceId}`
    const current = cases.get(caseId)
    const evidence: SourceEvidence = {
      id: original.evidenceId,
      caseId,
      displayLabel: original.fileName,
      name: original.fileName,
      analysisStatus: "COMPLETED",
      isCompareReady: true,
      dateLabel: formatDateTimeLabel(original.uploadedAt),
      sizeLabel: formatFileSize(original.fileSize),
      codecLabel: original.fileType || original.mimeType || "-",
      durationLabel: "-",
      hashLabel: original.sha256 || "-",
    }

    if (current) {
      current.evidences.push(evidence)
      continue
    }

    cases.set(caseId, {
      id: caseId,
      title: original.caseName?.trim() || original.caseNumber?.trim() || original.fileName,
      department: original.caseNumber?.trim() || "등록 원본",
      updatedAtLabel: formatDateTimeLabel(original.uploadedAt),
      evidences: [evidence],
    })
  }

  return Array.from(cases.values())
}

function getPreferredEvidenceId(evidences: SourceEvidence[], preferredEvidenceId?: number | null) {
  if (preferredEvidenceId != null) {
    const preferredEvidence = evidences.find((evidence) => evidence.id === preferredEvidenceId)
    if (preferredEvidence?.isCompareReady) return preferredEvidence.id
  }

  return evidences.find((evidence) => evidence.isCompareReady)?.id ?? null
}

function isCompareReadyStatus(status: string) {
  return status === "COMPLETED"
}

function showAnalysisRequiredAlert() {
  window.alert("딥페이크 분석 완료 후 비교검증에 사용할 수 있습니다.")
}

function getCaseStatusLabel(status: string) {
  if (status === "PENDING") return "대기"
  if (status === "PROCESSING" || status === "COMPLETED" || status === "FAILED") {
    return getAnalysisStatusLabel(status)
  }
  return status
}

function getMediaTypeLabel(mediaType: string) {
  const labels: Record<string, string> = {
    VIDEO: "영상",
    AUDIO: "음성",
    IMAGE: "이미지",
    UNKNOWN: "알 수 없음",
  }

  return labels[mediaType] ?? mediaType
}

function formatDateTimeLabel(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date)
}

function formatFileSize(size: number) {
  return formatSharedFileSize(size, {
    minUnit: "MB",
    maxUnit: "GB",
  })
}
