import type React from "react"
import { AudioLines, FileSearch, ImageIcon, Video, X } from "lucide-react"

import type { AnalysisStatus } from "@/lib/analysis-status"
import type { UploadResult } from "@/lib/evidence-api"
import { formatFileSize } from "@/lib/formatters"

type MediaKind = "all" | "audio" | "video" | "image"

export type UploadFileCardState = {
  file: File
  status: "pending" | "uploading" | "success" | "error"
  result?: UploadResult
  errorMessage?: string
  analysisStatus?: AnalysisStatus
  analysisProgress?: number
}

type UploadFileCardProps = {
  item: UploadFileCardState
  index: number
  isBusy: boolean
  onRemoveFile: (index: number) => void
}

const kindIcon: Record<MediaKind, React.ElementType> = {
  all: FileSearch,
  audio: AudioLines,
  video: Video,
  image: ImageIcon,
}

export function UploadFileCard({
  item,
  index,
  isBusy,
  onRemoveFile,
}: UploadFileCardProps) {
  const Icon = kindIcon[kindFromType(item.file.type)]

  return (
    <li
      className="flex items-center gap-3 rounded-lg border border-border bg-background/50 px-3 py-2.5"
    >
      <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-secondary text-muted-foreground">
        <Icon className="size-4" aria-hidden="true" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-foreground">
          {item.file.name}
        </p>
        <p className="font-mono text-xs text-muted-foreground">
          {formatBytes(item.file.size)} · {getFileStatusLabel(item)}
        </p>
      </div>
      {!isBusy && item.status !== "success" && (
        <button
          type="button"
          onClick={() => onRemoveFile(index)}
          className="flex size-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          title="목록에서 제거"
        >
          <X className="size-4" aria-hidden="true" />
        </button>
      )}
    </li>
  )
}

function formatBytes(bytes: number) {
  return formatFileSize(bytes, {
    zeroLabel: "0 B",
    maxUnit: "GB",
    trimTrailingZero: true,
  })
}

function kindFromType(type: string): MediaKind {
  if (type.startsWith("audio")) return "audio"
  if (type.startsWith("video")) return "video"
  if (type.startsWith("image")) return "image"
  return "all"
}

function getFileStatusLabel(item: UploadFileCardState): string {
  if (item.status === "pending") return "업로드 대기 중"
  if (item.status === "uploading") return "업로드 진행 중..."
  if (item.status === "error") return item.errorMessage ?? "업로드 실패"
  if (item.analysisStatus === "PENDING") return "분석 대기"
  if (item.analysisStatus === "PROCESSING") return `분석 중 (${item.analysisProgress ?? 0}%)`
  if (item.analysisStatus === "COMPLETED") return "분석 완료"
  if (item.analysisStatus === "FAILED") return "분석 실패"
  return "업로드 완료"
}
