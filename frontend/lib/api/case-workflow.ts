import type { AnalysisType, CaseDetailData, EvidenceRole } from "@/lib/api/evidence-detail"
import { apiRequest, apiRequestForm } from "@/lib/api/client"
import {
  mockCreateCase,
  mockCancelAnalysis,
  mockMarkEvidenceExcluded,
  mockReplaceEvidence,
  mockSetEvidenceRole,
  mockSetRepresentativeEvidence,
  mockStartCaseAnalysis,
  mockUploadEvidenceToCase,
} from "@/lib/mock/forensic-api"
import {
  cancelAnalysis,
  startEvidenceAnalysis,
  uploadEvidence,
  type FileUploadResponse,
  type StartAnalysisResponse,
  type UploadResult,
} from "@/lib/evidence-api"
import { features } from "@/lib/features"

export type StartCaseAnalysisPayload = {
  caseId: string
  caseName: string
  analysisType: AnalysisType
  evidenceIds: number[]
  baseEvidenceId?: number | null
  targetEvidenceId?: number | null
}

export async function createCase(caseName: string): Promise<CaseDetailData> {
  if (features.mockApi) {
    return mockCreateCase(caseName)
  }

  const data = await apiRequest<CaseDetailData>("/api/v1/cases", {
    method: "POST",
    body: { caseName: caseName.trim() },
  })

  return {
    ...data,
    evidences: data.evidences ?? [],
  }
}

export async function uploadEvidenceToCase(
  caseId: string,
  caseName: string,
  file: File
): Promise<UploadResult> {
  if (features.mockApi) {
    return mockUploadEvidenceToCase(caseId, file)
  }

  return uploadEvidence(file, caseName)
}

export async function markEvidenceExcluded(evidenceId: number, reason: string): Promise<void> {
  if (features.mockApi) {
    return mockMarkEvidenceExcluded(evidenceId, reason)
  }

  await apiRequest<void>(`/api/v1/evidences/${evidenceId}/exclude`, {
    method: "PATCH",
    body: { reason: reason.trim() },
  })
}

export async function replaceEvidenceInCase(
  caseId: string,
  oldEvidenceId: number,
  file: File,
  reason: string
): Promise<UploadResult> {
  if (features.mockApi) {
    return mockReplaceEvidence(caseId, oldEvidenceId, file, reason)
  }

  const formData = new FormData()
  formData.append("file", file)
  if (reason.trim()) {
    formData.append("reason", reason.trim())
  }

  const data = await apiRequestForm<FileUploadResponse>(
    `/api/v1/evidences/${oldEvidenceId}/replace`,
    { body: formData }
  )

  return {
    evidenceId: data.evidenceId,
    fileName: data.fileName,
    caseName: data.caseName,
    fileSize: data.fileSize,
    hashAlgorithm: data.hashAlgorithm,
    hashValue: data.hashValue,
    metadata: data.metadata,
    readiness: data.readiness ?? null,
    uploadedAt: new Date().toISOString(),
  }
}

export async function setRepresentativeEvidence(caseId: string, evidenceId: number): Promise<void> {
  if (features.mockApi) {
    return mockSetRepresentativeEvidence(caseId, evidenceId)
  }

  const params = new URLSearchParams({ caseKey: caseId })
  await apiRequest<void>(`/api/v1/cases/representative?${params}`, {
    method: "PATCH",
    body: { evidenceId },
  })
}

export async function setEvidenceRole(evidenceId: number, role: EvidenceRole): Promise<void> {
  if (features.mockApi) {
    return mockSetEvidenceRole(evidenceId, role)
  }

  await apiRequest<void>(`/api/v1/evidences/${evidenceId}/role`, {
    method: "PATCH",
    body: { role },
  })
}

export async function updateCaseName(
  caseId: string,
  caseName: string
): Promise<CaseDetailData> {
  if (features.mockApi) {
    const current = await import("@/lib/mock/forensic-api").then(
      ({ mockFetchCaseDetail }) => mockFetchCaseDetail(caseId)
    )
    return { ...current, caseId: caseName.trim(), caseName: caseName.trim() }
  }

  const params = new URLSearchParams({ caseKey: caseId })
  return apiRequest<CaseDetailData>(`/api/v1/cases?${params}`, {
    method: "PATCH",
    body: { caseName: caseName.trim() },
  })
}

export type StartCaseAnalysisOptions = {
  acknowledgeQualityWarning?: boolean
}

export async function startCaseAnalysis(
  payload: StartCaseAnalysisPayload,
  options: StartCaseAnalysisOptions = {}
): Promise<StartAnalysisResponse> {
  if (features.mockApi) {
    return mockStartCaseAnalysis(payload)
  }

  return startEvidenceAnalysis(payload.evidenceIds, payload.caseName, {
    acknowledgeQualityWarning: options.acknowledgeQualityWarning,
  })
}

export async function cancelCaseAnalysis(evidenceId: number): Promise<void> {
  if (features.mockApi) {
    return mockCancelAnalysis(evidenceId)
  }

  return cancelAnalysis(evidenceId)
}

export async function recordCaseReviewDecision(
  caseId: string,
  decision: "APPROVED" | "REVISION",
  memo?: string
): Promise<CaseDetailData> {
  if (features.mockApi) {
    return import("@/lib/mock/forensic-api").then(({ mockRecordCaseReviewDecision }) =>
      mockRecordCaseReviewDecision(caseId, decision, memo)
    )
  }

  const params = new URLSearchParams({ caseKey: caseId })
  return apiRequest<CaseDetailData>(`/api/v1/cases/review-decision?${params}`, {
    method: "POST",
    body: {
      decision,
      memo: memo?.trim() || undefined,
    },
  })
}

export async function requestCaseReview(
  caseId: string,
  memo?: string
): Promise<CaseDetailData> {
  if (features.mockApi) {
    return import("@/lib/mock/forensic-api").then(({ mockRequestCaseReview }) =>
      mockRequestCaseReview(caseId, memo)
    )
  }

  const params = new URLSearchParams({ caseKey: caseId })
  const trimmedMemo = memo?.trim()
  return apiRequest<CaseDetailData>(`/api/v1/cases/review-request?${params}`, {
    method: "POST",
    ...(trimmedMemo ? { body: { memo: trimmedMemo } } : {}),
  })
}
