import type { AnalysisStatus } from "@/lib/analysis-status"
import type { UploadResult } from "@/lib/evidence-api"

export type MetadataDisplayItem =
  | {
      id: string
      phase: "pending"
      fileName: string
      fileSize: number
    }
  | {
      id: string
      phase: "uploaded"
      upload: UploadResult
      analysisStatus?: AnalysisStatus
    }

export function fileFingerprint(file: File): string {
  return `${file.name}|${file.size}`
}
