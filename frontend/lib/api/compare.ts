import { apiDownload, apiRequest, apiRequestForm } from "@/lib/api/client"
import { getMockCompareIdForEvidence, getMockEvidenceIdFromCompareId } from "@/lib/compare-history"
import { features } from "@/lib/features"

export type CompareVerdict = "ORIGINAL_MATCH" | "TAMPERED" | "INCONCLUSIVE"
export type CompareItemResult = "MATCH" | "MISMATCH" | "SKIPPED"

export type CompareSummary = {
  matchCount: number
  mismatchCount: number
  skippedCount: number
  verdictLabel: string
}

export type CompareItem = {
  itemKey: string
  label: string
  originalValue: string
  candidateValue: string
  result: CompareItemResult
}

export type CompareSignatureStatus = "VALID" | "INVALID" | "UNSIGNED"

export type CompareSignatureInfo = {
  originalStatus: CompareSignatureStatus
  candidateStatus: CompareSignatureStatus
  algorithm?: string | null
  signedBy?: string | null
  signedAt?: string | null
}

export type CompareBlockchainStatus = "MATCH" | "MISMATCH" | "NOT_ANCHORED"

export type CompareBlockchainInfo = {
  status: CompareBlockchainStatus
  network?: string | null
  txHash?: string | null
  blockNumber?: number | null
  anchoredAt?: string | null
  anchoredHash?: string | null
}

export type CompareResult = {
  compareId: number
  originalEvidenceId: number
  candidateFileName: string
  verdict: CompareVerdict
  summary: CompareSummary
  items: CompareItem[]
  signature?: CompareSignatureInfo | null
  blockchain?: CompareBlockchainInfo | null
  createdAt: string
}

export type CompareOriginal = {
  evidenceId: number
  compareId?: number | null
  fileName: string
  fileSize: number
  sha256: string
  caseName?: string | null
  caseNumber?: string | null
  fileType?: string | null
  mimeType?: string | null
  uploadedAt: string
}

export type CompareOriginalPage = {
  content: CompareOriginal[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function fetchCompareOriginals(options?: {
  search?: string
  page?: number
  size?: number
}): Promise<CompareOriginalPage> {
  const params = new URLSearchParams({
    page: String(options?.page ?? 0),
    size: String(options?.size ?? 100),
  })
  const search = options?.search?.trim()
  if (search) params.set("search", search)

  return apiRequest<CompareOriginalPage>(`/api/v1/compare/originals?${params}`)
}

export function fetchCompareOriginal(evidenceId: number): Promise<CompareOriginal> {
  return apiRequest<CompareOriginal>(`/api/v1/compare/originals/${evidenceId}`)
}

export function verifyRegisteredCompare(
  originalEvidenceId: number,
  candidateEvidenceId: number
): Promise<CompareResult> {
  return apiRequest<CompareResult>("/api/v1/compare/verify-registered", {
    method: "POST",
    body: { originalEvidenceId, candidateEvidenceId },
  })
}

export async function verifyCompare(evidenceId: number, file: File, requestId?: string): Promise<CompareResult> {
  if (features.mockApi) {
    await delay(1400)
    return buildMockCompareResult({
      compareId: getMockCompareIdForEvidence(evidenceId),
      evidenceId,
      candidateFileName: file.name,
    })
  }

  const formData = new FormData()
  formData.append("file", file)

  const query = new URLSearchParams({ evidenceId: String(evidenceId) })
  if (requestId) {
    query.set("requestId", requestId)
  }

  return apiRequestForm<CompareResult>(
    `/api/v1/compare/verify?${query.toString()}`,
    {
      body: formData,
    }
  )
}

export async function cancelCompareVerification(requestId: string): Promise<void> {
  if (features.mockApi) return

  return apiRequest<void>(
    `/api/v1/compare/cancel?requestId=${encodeURIComponent(requestId)}`,
    {
      method: "POST",
    }
  )
}

export async function fetchCompareResult(compareId: number): Promise<CompareResult> {
  if (features.mockApi) {
    await delay(300)
    return buildMockCompareResult({ compareId })
  }

  return apiRequest<CompareResult>(`/api/v1/compare/${compareId}`)
}

export async function downloadCompareReport(
  compareId: number,
  options?: { preview?: boolean }
): Promise<Blob> {
  const query = options?.preview ? "?preview=true" : ""
  return apiDownload(`/api/v1/compare/${compareId}/reports/pdf${query}`)
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function buildMockCompareResult({
  compareId,
  evidenceId = getMockEvidenceIdFromCompareId(compareId) ?? 2024062713,
  candidateFileName = "submitted_video_final.mp4",
}: {
  compareId: number
  evidenceId?: number
  candidateFileName?: string
}): CompareResult {
  const items: CompareItem[] = [
    {
      itemKey: "sha256",
      label: "SHA-256",
      originalValue: "a3f81c09d2e47b16f8c05a913e2d84c7715f0b6a8d94e21c3b7f6a0d5e8c92d4",
      candidateValue: "7b02ff41c8a9d3e6b5f21c08a7d94e3f612c8b0a5d7e94f1c3a6b8d20e5f13c9",
      result: "MISMATCH",
    },
    { itemKey: "fileSize", label: "파일 크기", originalValue: "23.4 MB", candidateValue: "24.1 MB", result: "MISMATCH" },
    { itemKey: "codec", label: "비디오 코덱", originalValue: "H.264", candidateValue: "H.265", result: "MISMATCH" },
    { itemKey: "duration", label: "재생 시간", originalValue: "00:28.600", candidateValue: "00:28.600", result: "MATCH" },
    { itemKey: "resolution", label: "해상도", originalValue: "1920x1080", candidateValue: "1920x1080", result: "MATCH" },
    { itemKey: "frameRate", label: "프레임레이트", originalValue: "29.97 fps", candidateValue: "29.97 fps", result: "MATCH" },
    { itemKey: "container", label: "컨테이너", originalValue: "MP4", candidateValue: "MP4", result: "MATCH" },
    { itemKey: "audioCodec", label: "오디오 코덱", originalValue: "AAC", candidateValue: "AAC", result: "MATCH" },
    { itemKey: "gps", label: "GPS 정보", originalValue: "", candidateValue: "", result: "SKIPPED" },
  ]
  const matchCount = items.filter((item) => item.result === "MATCH").length
  const mismatchCount = items.filter((item) => item.result === "MISMATCH").length
  const skippedCount = items.filter((item) => item.result === "SKIPPED").length

  return {
    compareId,
    originalEvidenceId: evidenceId,
    candidateFileName,
    verdict: "TAMPERED",
    summary: {
      matchCount,
      mismatchCount,
      skippedCount,
      verdictLabel: "원본과 차이 확인",
    },
    items,
    signature: {
      originalStatus: "VALID",
      candidateStatus: "INVALID",
      algorithm: "RSA-SHA256",
      signedBy: "ForenShield Evidence CA",
      signedAt: "2026-06-27T12:42:00+09:00",
    },
    blockchain: {
      status: "MATCH",
      network: "ForenShield Chain",
      txHash: "0x8f3a2c91b7d4e60a5c18f2b9e73d40c6a1f58b02d9e47c3a6b80f15d2e9c74a3",
      blockNumber: 1842907,
      anchoredAt: "2026-06-27T12:43:10+09:00",
      anchoredHash: "a3f81c09d2e47b16f8c05a913e2d84c7715f0b6a8d94e21c3b7f6a0d5e8c92d4",
    },
    createdAt: new Date().toISOString(),
  }
}
