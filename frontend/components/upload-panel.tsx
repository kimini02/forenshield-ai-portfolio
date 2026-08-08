"use client"

import type React from "react"
import { useCallback, useEffect, useRef, useState } from "react"
import {
  AudioLines,
  Video,
  ImageIcon,
  FileSearch,
} from "lucide-react"
import { UploadActionBar } from "@/components/upload-panel/upload-action-bar"
import { UploadDropzone } from "@/components/upload-panel/upload-dropzone"
import { UploadFileList } from "@/components/upload-panel/upload-file-list"
import { UploadResultList } from "@/components/upload-panel/upload-result-list"
import { UploadStatusPanel } from "@/components/upload-panel/upload-status-panel"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { QualityWarningDialog } from "@/components/quality-warning-dialog"
import { ReadinessCheckOverlay } from "@/components/readiness-check-overlay"
import type { AnalysisStatus } from "@/lib/analysis-status"
import {
  cancelAnalysis,
  fetchAnalysisStatus,
  startEvidenceAnalysis,
  uploadEvidence,
  type UploadResult,
} from "@/lib/evidence-api"
import { getApiErrorMessage } from "@/lib/api/errors"
import { readinessTargetFromUpload } from "@/lib/readiness"
import { useAnalyzeWithReadiness } from "@/hooks/use-analyze-with-readiness"
import {
  clearMainUploadPanelSession,
  createPlaceholderFile,
  loadMainUploadPanelSession,
  saveMainUploadPanelSession,
} from "@/lib/main-upload-panel-storage"
import {
  fileFingerprint,
  type MetadataDisplayItem,
} from "@/lib/metadata-types"

type MediaKind = "all" | "audio" | "video" | "image"
type UploadStatus = "idle" | "uploading" | "completed" | "error" | "analyzing"

type FileUploadState = {
  file: File
  status: "pending" | "uploading" | "success" | "error"
  result?: UploadResult
  errorMessage?: string
  analysisStatus?: AnalysisStatus
  analysisProgress?: number
}

const acceptMap: Record<MediaKind, string> = {
  all: "audio/*,video/*,image/*",
  audio: "audio/*",
  video: "video/*",
  image: "image/*",
}

const tabs: { value: MediaKind; label: string; icon: React.ElementType }[] = [
  { value: "all", label: "전체", icon: FileSearch },
  { value: "image", label: "이미지", icon: ImageIcon },
  { value: "video", label: "영상", icon: Video },
  { value: "audio", label: "음성", icon: AudioLines },
]

type UploadPanelProps = {
  onMetadataChange?: (items: MetadataDisplayItem[]) => void
  onAnalyzeComplete?: (results: UploadResult[], startedCount: number) => void
}

function buildMetadataItems(fileStates: FileUploadState[]): MetadataDisplayItem[] {
  const items: MetadataDisplayItem[] = []

  fileStates.forEach((state, index) => {
    if (state.status === "pending" || state.status === "uploading") {
      items.push({
        id: `pending-${fileFingerprint(state.file)}-${index}`,
        phase: "pending",
        fileName: state.file.name,
        fileSize: state.file.size,
      })
      return
    }

    if (state.result) {
      items.push({
        id: `uploaded-${state.result.evidenceId}`,
        phase: "uploaded",
        upload: state.result,
        analysisStatus: state.analysisStatus ?? state.result.analysisStatus,
      })
    }
  })

  return items
}

function dedupeResultsByHash(results: UploadResult[]): UploadResult[] {
  const seen = new Set<string>()
  return results.filter((item) => {
    if (seen.has(item.hashValue)) return false
    seen.add(item.hashValue)
    return true
  })
}

function isStoppableAnalysis(item: FileUploadState): boolean {
  return (
    item.status === "success" &&
    !!item.result &&
    (item.analysisStatus === "PENDING" || item.analysisStatus === "PROCESSING")
  )
}

function getStoppableAnalysisEvidenceIds(fileStates: FileUploadState[]): number[] {
  return fileStates
    .filter(isStoppableAnalysis)
    .map((item) => item.result!.evidenceId)
}

export function UploadPanel({ onMetadataChange, onAnalyzeComplete }: UploadPanelProps) {
  const [kind, setKind] = useState<MediaKind>("all")
  const [isDragging, setIsDragging] = useState(false)
  const [fileStates, setFileStates] = useState<FileUploadState[]>([])
  const [caseName, setCaseName] = useState("")
  const inputRef = useRef<HTMLInputElement>(null)

  const [status, setStatus] = useState<UploadStatus>("idle")
  const [progress, setProgress] = useState(0)
  const [globalError, setGlobalError] = useState("")
  const [hasUploadedOnce, setHasUploadedOnce] = useState(false)
  const [isCancelling, setIsCancelling] = useState(false)
  const [hydrated, setHydrated] = useState(false)
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

  useEffect(() => {
    const session = loadMainUploadPanelSession()
    if (session) {
      setCaseName(session.caseName)
      setHasUploadedOnce(session.hasUploadedOnce)
      if (session.entries.length > 0) {
        setFileStates(
          session.entries.map((entry) => ({
            file: createPlaceholderFile(entry.result),
            status: "success" as const,
            result: entry.result,
            analysisStatus: entry.analysisStatus,
            analysisProgress: entry.analysisProgress,
          }))
        )
        setStatus("completed")

        const evidenceIds = session.entries
          .filter(
            (entry) =>
              entry.analysisStatus === "PENDING" ||
              entry.analysisStatus === "PROCESSING"
          )
          .map((entry) => entry.result.evidenceId)

        if (evidenceIds.length > 0) {
          void (async () => {
            const statuses = await Promise.all(
              evidenceIds.map((id) => fetchAnalysisStatus(id).catch(() => null))
            )

            setFileStates((prev) =>
              prev.map((entry) => {
                if (!entry.result) return entry
                const statusUpdate = statuses.find(
                  (item) => item?.evidenceId === entry.result!.evidenceId
                )
                if (!statusUpdate) return entry

                return {
                  ...entry,
                  analysisStatus: statusUpdate.status,
                  analysisProgress: statusUpdate.progressPercent,
                  result: {
                    ...entry.result,
                    analysisStatus: statusUpdate.status,
                  },
                }
              })
            )
          })()
        }
      }
    }
    setHydrated(true)
  }, [])

  useEffect(() => {
    onMetadataChange?.(buildMetadataItems(fileStates))
  }, [fileStates, onMetadataChange])

  const stoppableEvidenceIds = getStoppableAnalysisEvidenceIds(fileStates)
  const pollingKey = stoppableEvidenceIds.join(",")

  useEffect(() => {
    if (!hydrated) return

    const entries = fileStates
      .filter((item) => item.status === "success" && item.result)
      .map((item) => ({
        result: item.result!,
        analysisStatus: item.analysisStatus,
        analysisProgress: item.analysisProgress,
      }))

    saveMainUploadPanelSession({
      caseName,
      entries,
      hasUploadedOnce,
    })
  }, [fileStates, caseName, hasUploadedOnce, hydrated])

  useEffect(() => {
    if (!pollingKey) return

    const pollStatuses = async () => {
      const evidenceIds = pollingKey
        .split(",")
        .map(Number)
        .filter((id) => Number.isFinite(id) && id > 0)
      if (evidenceIds.length === 0) return

      const statuses = await Promise.all(
        evidenceIds.map((id) =>
          fetchAnalysisStatus(id).catch(() => null)
        )
      )

      setFileStates((prev) =>
        prev.map((entry) => {
          if (!entry.result) return entry
          const statusUpdate = statuses.find(
            (item) => item?.evidenceId === entry.result!.evidenceId
          )
          if (!statusUpdate) return entry

          return {
            ...entry,
            analysisStatus: statusUpdate.status,
            analysisProgress: statusUpdate.progressPercent,
            result: {
              ...entry.result,
              analysisStatus: statusUpdate.status,
            },
          }
        })
      )
    }

    void pollStatuses()
    const interval = setInterval(() => {
      void pollStatuses()
    }, 1500)

    return () => clearInterval(interval)
  }, [pollingKey])

  const isBusy = status === "uploading" || isCheckingReadiness || isCancelling
  const hasPendingFiles = fileStates.some((item) => item.status === "pending")
  const trimmedCaseName = caseName.trim()
  const canUpload =
    hasPendingFiles &&
    trimmedCaseName.length > 0 &&
    status !== "uploading" &&
    status !== "analyzing"

  const addFiles = useCallback((list: FileList | null) => {
    if (!list) return

    const existingKeys = new Set(fileStates.map((item) => fileFingerprint(item.file)))
    const rejected: string[] = []
    const incoming: FileUploadState[] = []

    for (const file of Array.from(list)) {
      const key = fileFingerprint(file)
      if (existingKeys.has(key)) {
        rejected.push(file.name)
        continue
      }
      existingKeys.add(key)
      incoming.push({ file, status: "pending" })
    }

    if (rejected.length > 0) {
      setGlobalError(
        `이미 목록에 있는 동일 파일은 추가할 수 없습니다: ${rejected.join(", ")}`
      )
    } else if (incoming.length > 0) {
      setGlobalError("")
    }

    if (incoming.length === 0) return

    setFileStates((prev) => [...prev, ...incoming])

    if (status === "completed" || status === "error") {
      setStatus("idle")
      setProgress(0)
    }
  }, [fileStates, status])

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      setIsDragging(false)
      addFiles(e.dataTransfer.files)
    },
    [addFiles],
  )

  const removeFile = (index: number) => {
    setFileStates((prev) => prev.filter((_, i) => i !== index))
    if (fileStates.length <= 1) {
      setStatus("idle")
      setProgress(0)
      setGlobalError("")
    }
  }

  const cancelAnalysisFile = async (index: number) => {
    const target = fileStates[index]
    if (!isStoppableAnalysis(target) || !target.result) return

    const confirmed = window.confirm(
      `"${target.file.name}" 분석을 중단하시겠습니까?\n원본 파일은 유지됩니다.`
    )
    if (!confirmed) return

    setIsCancelling(true)
    setGlobalError("")

    try {
      await cancelAnalysis(target.result.evidenceId)
      setFileStates((prev) =>
        prev.map((entry, idx) => {
          if (idx !== index || !entry.result) return entry
          return {
            ...entry,
            analysisStatus: undefined,
            analysisProgress: undefined,
            result: {
              ...entry.result,
              analysisStatus: undefined,
            },
          }
        })
      )
    } catch (error) {
      setGlobalError(getApiErrorMessage(error, "분석 중단에 실패했습니다."))
    } finally {
      setIsCancelling(false)
    }
  }

  const handleUpload = async () => {
    const pendingIndices = fileStates
      .map((item, index) => ({ item, index }))
      .filter(({ item }) => item.status === "pending")

    if (pendingIndices.length === 0) return
    if (!trimmedCaseName) {
      setGlobalError("사건명을 입력해 주세요.")
      return
    }

    setStatus("uploading")
    setProgress(0)
    setGlobalError("")

    setFileStates((prev) =>
      prev.map((entry) =>
        entry.status === "pending"
          ? { ...entry, status: "uploading" as const, errorMessage: undefined }
          : entry
      )
    )

    const completed: UploadResult[] = []
    let hasError = false

    for (let i = 0; i < pendingIndices.length; i++) {
      const { item, index } = pendingIndices[i]
      try {
        const result = await uploadEvidence(item.file, trimmedCaseName)
        completed.push(result)

        setFileStates((prev) =>
          prev.map((entry, idx) =>
            idx === index ? { ...entry, status: "success", result } : entry
          )
        )
      } catch (error) {
        hasError = true
        const message = getApiErrorMessage(error, "파일 업로드에 실패했습니다.")

        setFileStates((prev) =>
          prev.map((entry, idx) =>
            idx === index ? { ...entry, status: "error", errorMessage: message } : entry
          )
        )
      }

      setProgress(Math.round(((i + 1) / pendingIndices.length) * 100))
    }

    if (completed.length > 0) {
      setHasUploadedOnce(true)
    }

    setStatus(hasError && completed.length === 0 ? "error" : "completed")
    if (hasError && completed.length === 0) {
      setGlobalError("모든 파일 업로드에 실패했습니다.")
    }
  }

  const analyzableResults = fileStates.filter(
    (item) => item.status === "success" && item.result && !item.analysisStatus
  )

  const canAnalyze =
    analyzableResults.length > 0 && trimmedCaseName.length > 0 && !isBusy

  const applyAnalysisStarted = (
    uniqueResults: UploadResult[],
    response: Awaited<ReturnType<typeof startEvidenceAnalysis>>
  ) => {
    const startedIds = new Set(response.evidenceIds)

    setFileStates((prev) =>
      prev.map((entry) => {
        if (!entry.result || !startedIds.has(entry.result.evidenceId)) {
          return entry
        }
        return {
          ...entry,
          analysisStatus: "PENDING" as const,
          analysisProgress: 0,
          result: {
            ...entry.result,
            caseName: trimmedCaseName,
            analysisStatus: "PENDING" as const,
          },
        }
      })
    )

    if (response.startedCount > 0) {
      const analyzedResults = uniqueResults.map((item) => ({
        ...item,
        caseName: trimmedCaseName,
        uploadedAt: new Date().toISOString(),
        analysisStatus: "PENDING" as const,
      }))
      onAnalyzeComplete?.(analyzedResults, response.startedCount)
    }

    setStatus("completed")
  }

  const requestAnalysis = async (
    evidenceIds: number[],
    uniqueResults: UploadResult[],
    acknowledgeQualityWarning: boolean
  ) => {
    return startEvidenceAnalysis(evidenceIds, trimmedCaseName, {
      acknowledgeQualityWarning: acknowledgeQualityWarning || undefined,
    })
  }

  const handleAnalyze = async () => {
    const targets = fileStates.filter(
      (item) => item.status === "success" && item.result && !item.analysisStatus
    )
    if (targets.length === 0) return

    if (!trimmedCaseName) {
      setGlobalError("분석을 시작하려면 사건명을 입력해 주세요.")
      return
    }

    const uniqueResults = dedupeResultsByHash(
      targets.map((item) => item.result!).filter(Boolean)
    )
    const evidenceIds = uniqueResults.map((item) => item.evidenceId)

    setGlobalError("")

    await startAnalysisWithReadiness({
      targets: uniqueResults.map(readinessTargetFromUpload),
      runAnalyze: (ack) => requestAnalysis(evidenceIds, uniqueResults, ack),
      onSuccess: (response) => {
        applyAnalysisStarted(uniqueResults, response)
      },
      onError: (error) => {
        setGlobalError(getApiErrorMessage(error, "분석 요청에 실패했습니다."))
        setStatus("completed")
      },
    })
  }

  const startNewCase = () => {
    if (fileStates.length > 0) {
      const confirmed = window.confirm(
        "현재 화면의 작업 목록만 비우고 새 사건을 시작합니다.\n이미 업로드되었거나 분석 요청된 기록은 마이페이지와 상세 페이지에 보존됩니다."
      )
      if (!confirmed) return
    }

    clearMainUploadPanelSession()
    setFileStates([])
    setCaseName("")
    setStatus("idle")
    setProgress(0)
    setGlobalError("")
    setHasUploadedOnce(false)
  }

  const uploadButtonLabel = hasUploadedOnce ? "추가 업로드" : "업로드 시작"
  const pendingCount = fileStates.filter((item) => item.status === "pending").length
  const hasAnalyzedFiles = fileStates.some((item) => item.analysisStatus)
  const displayedSuccessStates = fileStates.filter(
    (item) => item.status === "success" && item.result
  )

  return (
    <section
      id="analyze"
      className="rounded-xl border border-border bg-card p-5 sm:p-6"
      aria-label="파일 업로드"
    >
      <div className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold text-card-foreground">
            증거 파일 업로드
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            분석할 음성·영상·이미지 파일을 업로드하면 SHA-256 해시와 메타데이터가 서버에 저장됩니다.
          </p>
        </div>
        <Tabs value={kind} onValueChange={(v) => setKind(v as MediaKind)}>
          <TabsList className="bg-secondary">
            {tabs.map((t) => (
              <TabsTrigger key={t.value} value={t.value} className="gap-1.5 text-xs">
                <t.icon className="size-3.5" aria-hidden="true" />
                {t.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </div>

      <div className="mb-4 space-y-1.5">
        <Label htmlFor="caseName" className="text-xs text-muted-foreground">
          사건명 <span className="text-destructive">*</span>
        </Label>
        <Input
          id="caseName"
          value={caseName}
          onChange={(e) => {
            setCaseName(e.target.value)
            if (globalError.includes("사건명")) {
              setGlobalError("")
            }
          }}
          placeholder="예: 2026-서울-0123 딥페이크 유포 사건"
          disabled={isBusy}
          required
          aria-required="true"
          className="h-9 max-w-md"
        />
        <p className="text-[11px] text-muted-foreground">
          업로드와 분석 시작에 사건명이 필수이며, 내 분석 기록에 등록됩니다.
        </p>
      </div>

      <UploadDropzone
        inputRef={inputRef}
        accept={acceptMap[kind]}
        isBusy={isBusy}
        isDragging={isDragging}
        onClick={() => !isBusy && inputRef.current?.click()}
        onKeyDown={(e) => {
          if ((e.key === "Enter" || e.key === " ") && !isBusy) {
            inputRef.current?.click()
          }
        }}
        onDragOver={(e) => {
          e.preventDefault()
          if (!isBusy) setIsDragging(true)
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={onDrop}
        onFileChange={addFiles}
      />

      {fileStates.length > 0 && (
        <div className="mt-5 space-y-4">
          <UploadFileList
            fileStates={fileStates}
            isBusy={isBusy}
            onRemoveFile={removeFile}
          />

          <UploadStatusPanel
            isUploading={status === "uploading"}
            progress={progress}
            error={globalError}
          />

          <UploadResultList
            fileStates={fileStates}
            displayedSuccessStates={displayedSuccessStates}
            hasAnalyzedFiles={hasAnalyzedFiles}
            isBusy={isBusy}
            canCancelAnalysisFile={isStoppableAnalysis}
            onCancelAnalysisFile={cancelAnalysisFile}
          />
        </div>
      )}

      <UploadActionBar
        showNewCaseButton={status === "completed" || fileStates.length > 0}
        isBusy={isBusy}
        analyzableCount={analyzableResults.length}
        canAnalyze={canAnalyze}
        canUpload={canUpload}
        hasPendingFiles={hasPendingFiles}
        trimmedCaseName={trimmedCaseName}
        status={isCheckingReadiness || qualityDialogOpen ? "analyzing" : status}
        uploadButtonLabel={uploadButtonLabel}
        pendingCount={pendingCount}
        onStartNewCase={startNewCase}
        onAnalyze={handleAnalyze}
        onUpload={handleUpload}
      />

      <ReadinessCheckOverlay open={isCheckingReadiness} phase={readinessCheckPhase} />

      <QualityWarningDialog
        open={qualityDialogOpen}
        summaries={qualityDialogSummaries}
        worstTier={qualityDialogWorstTier}
        loading={qualityDialogLoading}
        onConfirm={() => void confirmQualityDialog()}
        onCancel={() => {
          cancelQualityDialog()
          setStatus("completed")
        }}
      />
    </section>
  )
}
