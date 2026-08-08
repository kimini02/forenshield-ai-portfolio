import type { AnalysisStatus } from "@/lib/analysis-status"
import { getAnalysisStatusLabel } from "@/lib/status-labels"

export type VideoModuleDetails = {
  deepfakeDetected?: boolean
  deepfakeScore?: number
  frameEditDetected?: boolean
  frameEditScore?: number
  lipSyncDetected?: boolean
  lipSyncScore?: number
}

export function parseVideoModuleDetails(raw: string): VideoModuleDetails | null {
  try {
    return JSON.parse(raw) as VideoModuleDetails
  } catch {
    return null
  }
}

export function resolveModelLabel(summary: string): string {
  if (/xception/i.test(summary)) return "Xception (test)"
  return "GPU Worker"
}

export function formatModuleDetailsText(raw: string): string | null {
  const details = parseVideoModuleDetails(raw)
  if (!details) return null

  const parts: string[] = []
  if (details.deepfakeScore != null) {
    parts.push(
      `Deepfake ${(details.deepfakeScore * 100).toFixed(1)}%` +
        (details.deepfakeDetected ? " (탐지)" : "")
    )
  }
  if (details.frameEditScore != null && details.frameEditScore > 0) {
    parts.push(`Frame edit ${(details.frameEditScore * 100).toFixed(1)}%`)
  }
  if (details.lipSyncScore != null && details.lipSyncScore > 0) {
    parts.push(`Lip sync ${(details.lipSyncScore * 100).toFixed(1)}%`)
  }

  return parts.length > 0 ? parts.join(" · ") : null
}

export function getAnalysisEvidenceMessage(status: AnalysisStatus): string {
  if (status === "PENDING") return "분석 대기"
  if (status === "PROCESSING") return "분석 중"
  if (status === "COMPLETED") return "분석 근거 없음"
  if (status === "FAILED") return "분석 실패로 근거 데이터를 표시할 수 없습니다."
  return "현재 AI 분석 결과를 사용할 수 없습니다."
}
