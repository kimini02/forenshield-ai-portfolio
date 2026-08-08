"use client"

import { useRouter } from "next/navigation"
import { useEffect, useRef, useState, type DragEvent } from "react"

import { MediaMetadataPreviewContent } from "./media-metadata-preview"
import { UploadStep } from "./upload-step"
import { getApiErrorMessage } from "@/lib/api/errors"
import { uploadEvidence } from "@/lib/evidence-api"
import { formatDateTime, formatFileSize } from "@/lib/formatters"
import { buildCaseDetailPath } from "@/lib/route-params"

export type SelectedEvidence = {
  name: string
  sizeBytes: number
  sizeLabel: string
  extension: string
  mimeType: string
  previewUrl?: string
  uploadAtLabel: string
  resolutionLabel: string
  comment: string
  evidenceId?: number
  hashValue?: string
}

type UploadItem = {
  file: File
  display: SelectedEvidence
  evidenceId?: number
}

const SUPPORTED_VIDEO_EXTENSIONS = ["mp4", "mov"]

export function AnalysisRequestFlow() {
  const router = useRouter()
  const [uploadItems, setUploadItems] = useState<UploadItem[]>([])
  const [activeEvidenceIndex, setActiveEvidenceIndex] = useState(0)
  const [caseName, setCaseName] = useState("")
  const [uploadMessage, setUploadMessage] = useState<{
    type: "success" | "error"
    text: string
  } | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const uploadItemsRef = useRef<UploadItem[]>([])

  const evidences = uploadItems.map((item) => item.display)

  useEffect(() => {
    uploadItemsRef.current = uploadItems
  }, [uploadItems])

  useEffect(() => {
    return () => {
      revokeEvidencePreviewUrls(uploadItemsRef.current)
    }
  }, [])

  useEffect(() => {
    if (activeEvidenceIndex < evidences.length) return
    setActiveEvidenceIndex(Math.max(0, evidences.length - 1))
  }, [activeEvidenceIndex, evidences.length])

  function handleFileChange(files: FileList | null) {
    const selectedFiles = Array.from(files ?? [])
    if (selectedFiles.length === 0) return
    const nextItems: UploadItem[] = []

    try {
      for (const file of selectedFiles) {
        nextItems.push({
          file,
          display: buildEvidenceFromFile(file),
        })
      }
      setUploadItems((current) => {
        revokeEvidencePreviewUrls(current)
        return nextItems
      })
      setActiveEvidenceIndex(0)
      setUploadMessage({
        type: "success",
        text: `${nextItems.length}개 파일이 선택되었습니다. 사건명을 입력한 뒤 분석을 시작하세요.`,
      })
    } catch (error) {
      revokeEvidencePreviewUrls(nextItems)
      setUploadItems((current) => {
        revokeEvidencePreviewUrls(current)
        return []
      })
      setActiveEvidenceIndex(0)
      setUploadMessage({
        type: "error",
        text:
          error instanceof Error
            ? error.message
            : "파일 선택에 실패했습니다. 다시 시도해 주세요.",
      })
    }
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    handleFileChange(event.dataTransfer.files)
  }

  async function startAnalysis() {
    if (uploadItems.length === 0 || !caseName.trim()) return

    setUploadMessage(null)
    const trimmedCaseName = caseName.trim()

    try {
      const uploadedItems: UploadItem[] = []

      for (const item of uploadItems) {
        const result = await uploadEvidence(item.file, trimmedCaseName)
        uploadedItems.push({
          file: item.file,
          evidenceId: result.evidenceId,
          display: {
            ...item.display,
            evidenceId: result.evidenceId,
            hashValue: result.hashValue,
            uploadAtLabel: formatDateTimeLabel(Date.now()),
            resolutionLabel: formatMetadataResolution(result.metadata),
          },
        })
      }

      setUploadItems(uploadedItems)
      const firstEvidenceId = uploadedItems[0]?.evidenceId

      if (firstEvidenceId) {
        router.push(buildCaseDetailPath(trimmedCaseName, firstEvidenceId))
      } else {
        router.push("/mypage")
      }
    } catch (error) {
      const message = getApiErrorMessage(error, "분석 요청에 실패했습니다.")

      setUploadMessage({ type: "error", text: message })
    }
  }

  function removeEvidence(index: number) {
    setUploadItems((current) => {
      revokeEvidencePreviewUrls(current.filter((_, currentIndex) => currentIndex === index))
      const next = current.filter((_, currentIndex) => currentIndex !== index)
      if (next.length === 0) {
        setUploadMessage(null)
      } else {
        setUploadMessage({
          type: "success",
          text: `${next.length}개 파일이 선택되었습니다. 사건명을 입력한 뒤 분석을 시작하세요.`,
        })
      }
      return next
    })
  }

  function updateEvidenceComment(index: number, comment: string) {
    setUploadItems((current) =>
      current.map((item, currentIndex) =>
        currentIndex === index
          ? { ...item, display: { ...item.display, comment } }
          : item
      )
    )
  }

  return (
    <section id="new-analysis" className="mx-auto w-full max-w-6xl scroll-mt-28 space-y-5">
      <UploadStep
        evidences={evidences}
        activeEvidenceIndex={activeEvidenceIndex}
        caseName={caseName}
        uploadMessage={uploadMessage}
        totalSizeLabel={formatTotalSizeLabel(evidences)}
        metadataPreview={
          <MediaMetadataPreview
            evidences={evidences}
            activeIndex={activeEvidenceIndex}
            onNext={() =>
              setActiveEvidenceIndex((current) =>
                evidences.length === 0 ? 0 : (current + 1) % evidences.length
              )
            }
            onCommentChange={(comment) => updateEvidenceComment(activeEvidenceIndex, comment)}
          />
        }
        fileInputRef={fileInputRef}
        onDrop={handleDrop}
        onFileChange={handleFileChange}
        onCaseNameChange={setCaseName}
        onRemoveFile={removeEvidence}
        onSelectEvidence={setActiveEvidenceIndex}
        onStart={startAnalysis}
      />
    </section>
  )
}

function MediaMetadataPreview({
  evidences,
  activeIndex,
  onNext,
  onCommentChange,
}: {
  evidences: SelectedEvidence[]
  activeIndex: number
  onNext: () => void
  onCommentChange: (comment: string) => void
}) {
  const [activeTab, setActiveTab] = useState<"metadata" | "comment">("metadata")
  const [previewEvidence, setPreviewEvidence] = useState<SelectedEvidence | null>(null)

  return (
    <MediaMetadataPreviewContent
      evidences={evidences}
      activeIndex={activeIndex}
      activeTab={activeTab}
      previewEvidence={previewEvidence}
      onNext={onNext}
      onCommentChange={onCommentChange}
      onTabChange={setActiveTab}
      onOpenPreview={setPreviewEvidence}
      onClosePreview={() => setPreviewEvidence(null)}
    />
  )
}

function formatBytes(bytes: number) {
  return formatFileSize(bytes, {
    zeroLabel: "0 B",
    maxUnit: "GB",
  })
}

function buildEvidenceFromFile(file: File): SelectedEvidence {
  validateVideoFile(file)

  return {
    name: file.name,
    sizeBytes: file.size,
    sizeLabel: formatBytes(file.size),
    extension: getFileExtension(file.name),
    mimeType: file.type || "video/*",
    previewUrl: URL.createObjectURL(file),
    uploadAtLabel: formatDateTimeLabel(Date.now()),
    resolutionLabel: "분석 전",
    comment: "업로드된 영상입니다. 분석 요청 후 코멘트를 남길 수 있습니다.",
  }
}

function revokeEvidencePreviewUrls(items: UploadItem[]) {
  for (const item of items) {
    if (item.display.previewUrl) {
      URL.revokeObjectURL(item.display.previewUrl)
    }
  }
}

function formatTotalSizeLabel(evidences: SelectedEvidence[]) {
  return formatBytes(evidences.reduce((sum, item) => sum + item.sizeBytes, 0))
}

function validateVideoFile(file: File) {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? ""
  const isSupportedExtension = SUPPORTED_VIDEO_EXTENSIONS.includes(extension)
  const isVideoMimeType = file.type.startsWith("video/") || file.type === ""

  if (!isSupportedExtension || !isVideoMimeType) {
    throw new Error("지원하지 않는 파일 형식입니다. MP4 또는 MOV 영상 파일만 업로드할 수 있습니다.")
  }

}

function getFileExtension(fileName: string) {
  const extension = fileName.split(".").pop()
  return extension ? extension.toUpperCase() : "UNKNOWN"
}

function formatDateTimeLabel(value: number) {
  return formatDateTime(value)
}

function formatMetadataResolution(
  metadata: import("@/lib/evidence-api").MediaMetadata | string | null
) {
  if (!metadata || typeof metadata === "string") return "분석 전"
  if (metadata.width && metadata.height) {
    return `${metadata.width.toLocaleString("ko-KR")} x ${metadata.height.toLocaleString("ko-KR")}`
  }
  return "분석 전"
}
