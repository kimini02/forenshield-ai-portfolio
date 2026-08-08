import { API_BASE_URL } from "@/lib/api/config"
import { API_FETCH_CREDENTIALS } from "@/lib/api/interceptor"
import { ApiError, apiRequest } from "@/lib/api/client"
import { features } from "@/lib/features"

export type ReportVerifyStatus = "PENDING" | "VALID" | "WARNING" | "INVALID"

export type ReportVerification = {
  status: ReportVerifyStatus
  valid: boolean
  message: string
  reportNo: string
  verificationCode?: string | null
  reportType?: "ANALYSIS" | "COMPARE" | string | null
  revision?: number | null
  publicationStatus?: string | null
  issuedAt?: string | null
  queriedAt?: string | null
  pdfSignatureApplied?: boolean | null
  evidenceId: number
  reportFileName: string
  createdAt: string
  reportHash: string
  hashMatched: boolean
  storedFileIntact?: boolean
  signatureValid?: boolean | null
  signatureStatus?: string | null
  signatureAlgorithm?: string | null
  signerCertificateSubject?: string | null
  evidenceManifestSignatureValid?: boolean | null
  evidenceManifestSignatureStatus?: string | null
  evidenceManifestSignatureAlgorithm?: string | null
  evidenceManifestSignerCertificateSubject?: string | null
  blockchainMatched: boolean | null
  blockchainStatus: string
  blockchainTxHash?: string | null
  blockchainNetwork?: string | null
  blockchainAnchoredAt?: string | null
}

export type PublicReportAccessIssue = {
  reportId: number
  reportNo: string
  accessCode: string
  enabled: boolean
  publicViewUrl: string
  issuedAt: string
  expiresAt: string
}

export type PublicReportView = {
  reportId: number
  reportNo: string
  reportType: "ANALYSIS" | "COMPARE" | string
  evidenceId: number
  compareId?: number | null
  reportFileName: string
  reportHash: string
  fileSize: number
  createdAt: string
  expiresAt: string
  downloadPath: string
}

/**
 * 공개 검증 API. 로그인 없이 접근하므로 인증 헤더를 붙이지 않는다.
 * 목업 모드에서는 토큰 문자열로 상태를 분기한다:
 *  - "invalid" 포함 → INVALID, "warning" 포함 → WARNING
 *  - "notfound" 포함 → 404 오류, "pending" 또는 "expired" 포함 → 미발행 안내
 */
export type ReportVerificationLookup = {
  token?: string
  code?: string
}

export type ReportFileHashVerification = {
  status: "MATCH" | "MISMATCH" | "WARNING"
  matched: boolean
  storedFileIntact: boolean
  message: string
  reportNo: string
  submittedHash: string
  registeredHash: string
}

export type ReportFileHashVerificationRequest = ReportVerificationLookup & {
  fileHash: string
}

export async function fetchReportVerification(lookup: ReportVerificationLookup): Promise<ReportVerification> {
  const token = lookup.token?.trim() ?? ""
  const code = lookup.code?.trim() ?? ""

  if (features.mockApi) {
    await delay(450)
    return buildMockVerification(token || code)
  }

  const query = new URLSearchParams()
  if (token) query.set("token", token)
  if (code) query.set("code", code)

  return apiRequest<ReportVerification>(`/api/v1/public/reports/verify?${query.toString()}`, {
    auth: false,
  })
}

export async function verifyReportFileHash(
  request: ReportFileHashVerificationRequest
): Promise<ReportFileHashVerification> {
  if (features.mockApi) {
    await delay(500)
    const lookupValue = `${request.token ?? ""}${request.code ?? ""}`.toLowerCase()
    const matched = !lookupValue.includes("mismatch") && !lookupValue.includes("invalidfile")
    return {
      status: matched ? "MATCH" : "MISMATCH",
      matched,
      storedFileIntact: true,
      message: matched
        ? "선택한 PDF가 발급 시 등록된 최종 파일과 일치합니다."
        : "선택한 PDF가 발급 시 등록된 최종 파일과 일치하지 않습니다.",
      reportNo: "RPT-2026-0703-0012",
      submittedHash: request.fileHash,
      registeredHash: matched
        ? request.fileHash
        : "a3f81c09d2e47b16f8c05a913e2d84c7715f0b6a8d94e21c3b7f6a0d5e8c92d4",
    }
  }

  return apiRequest<ReportFileHashVerification>("/api/v1/public/reports/verify-file-hash", {
    method: "POST",
    auth: false,
    body: request,
  })
}

export async function issuePublicReportAccess(reportId: number): Promise<PublicReportAccessIssue> {
  if (features.mockApi) {
    await delay(350)
    const origin = typeof window === "undefined" ? "" : window.location.origin
    return {
      reportId,
      reportNo: "RPT-2026-0703-0012",
      accessCode: "RV-3K9P-82MA",
      enabled: true,
      publicViewUrl: `${origin}/public-report?code=RV-3K9P-82MA`,
      issuedAt: "2026-07-07T15:30:00+09:00",
      expiresAt: "2026-07-14T15:30:00+09:00",
    }
  }

  return apiRequest<PublicReportAccessIssue>(`/api/v1/reports/${reportId}/public-access`, {
    method: "POST",
  })
}

export async function fetchPublicReportView(code: string): Promise<PublicReportView> {
  const accessCode = code.trim()

  if (features.mockApi) {
    await delay(450)
    return buildMockPublicReportView(accessCode)
  }

  const query = new URLSearchParams({ code: accessCode })
  return apiRequest<PublicReportView>(`/api/v1/public/reports/view?${query.toString()}`, {
    auth: false,
  })
}

export async function downloadPublicReportPdf(code: string): Promise<Blob> {
  const accessCode = code.trim()
  const query = new URLSearchParams({ code: accessCode })
  const response = await fetch(`${API_BASE_URL}/api/v1/public/reports/view/pdf?${query.toString()}`, {
    headers: {
      Accept: "application/pdf",
    },
    credentials: API_FETCH_CREDENTIALS,
  })

  if (!response.ok) {
    let message = "보고서 PDF를 불러오지 못했습니다."
    let errorCode: string | undefined
    try {
      const errorBody = (await response.json()) as { message?: string; errorCode?: string; error?: string }
      message = errorBody.message ?? message
      errorCode = errorBody.errorCode ?? errorBody.error
    } catch {
      // ignore parse errors
    }
    throw new ApiError(message, response.status, errorCode)
  }

  return response.blob()
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function buildMockVerification(token: string): ReportVerification {
  const normalized = token.toLowerCase()

  if (normalized.includes("notfound") || normalized.includes("expired")) {
    if (normalized.includes("expired")) {
      return buildPendingVerification()
    }
    throw new ApiError("등록되지 않은 검증 정보입니다.", 404, "REPORT_VERIFICATION_NOT_FOUND")
  }

  const base: ReportVerification = {
    status: "VALID",
    valid: true,
    message: "발행 등록정보를 조회했습니다. PDF 파일 자체는 아직 검사하지 않았습니다.",
    reportNo: "RPT-2026-0703-0012",
    verificationCode: "VF-8F3K-29QX",
    reportType: "ANALYSIS",
    revision: 1,
    publicationStatus: "ISSUED",
    issuedAt: "2026-07-03T13:28:00+09:00",
    queriedAt: new Date().toISOString(),
    pdfSignatureApplied: false,
    evidenceId: 2024062716,
    reportFileName: "ForenShield_Report_EVD-2024062716.pdf",
    createdAt: "2026-07-03T13:28:00+09:00",
    reportHash: "a3f81c09d2e47b16f8c05a913e2d84c7715f0b6a8d94e21c3b7f6a0d5e8c92d4",
    hashMatched: true,
    storedFileIntact: true,
    signatureValid: true,
    signatureStatus: "SIGNED",
    signatureAlgorithm: "SHA256withRSA",
    signerCertificateSubject: "CN=ForenShield Evidence Authority, O=ForenShield, C=KR",
    evidenceManifestSignatureValid: true,
    evidenceManifestSignatureStatus: "VALID",
    evidenceManifestSignatureAlgorithm: "SHA256withRSA",
    evidenceManifestSignerCertificateSubject: "CN=ForenShield Evidence Authority, O=ForenShield, C=KR",
    blockchainMatched: true,
    blockchainStatus: "ANCHORED",
    blockchainTxHash: "0x8f3a2c91b7d4e60a5c18f2b9e73d40c6a1f58b02d9e47c3a6b80f15d2e9c74a3",
    blockchainNetwork: "ForenShield Chain",
    blockchainAnchoredAt: "2026-07-03T13:29:10+09:00",
  }

  if (normalized.includes("invalid")) {
    return {
      ...base,
      status: "INVALID",
      valid: false,
      message: "발급 기록과 일치하지 않는 보고서입니다.",
      hashMatched: false,
      reportHash: "7b02ff41c8a9d3e6b5f21c08a7d94e3f612c8b0a5d7e94f1c3a6b8d20e5f13c9",
      signatureValid: false,
      signatureStatus: "TAMPERED",
      evidenceManifestSignatureValid: false,
      evidenceManifestSignatureStatus: "INVALID",
    }
  }

  if (normalized.includes("warning")) {
    return {
      ...base,
      status: "WARNING",
      valid: true,
      message: "일부 항목을 자동으로 확인하지 못했습니다.",
      blockchainMatched: false,
      blockchainStatus: "NOT_ANCHORED",
      blockchainTxHash: null,
      blockchainNetwork: null,
      blockchainAnchoredAt: null,
    }
  }

  if (normalized.includes("pending") || normalized.includes("unissued")) {
    return buildPendingVerification()
  }

  return base
}

function buildPendingVerification(): ReportVerification {
  return {
    status: "PENDING",
    valid: false,
    message: "아직 발행되지 않은 보고서입니다. 검토 승인과 발행 등록이 완료된 후 다시 확인해 주세요.",
    reportNo: "",
    verificationCode: null,
    reportType: null,
    revision: null,
    publicationStatus: "DRAFT",
    issuedAt: null,
    queriedAt: new Date().toISOString(),
    pdfSignatureApplied: false,
    evidenceId: 0,
    reportFileName: "",
    createdAt: "",
    reportHash: "",
    hashMatched: false,
    storedFileIntact: false,
    signatureValid: null,
    signatureStatus: "NOT_ISSUED",
    signatureAlgorithm: null,
    signerCertificateSubject: null,
    evidenceManifestSignatureValid: null,
    evidenceManifestSignatureStatus: "NOT_ISSUED",
    evidenceManifestSignatureAlgorithm: null,
    evidenceManifestSignerCertificateSubject: null,
    blockchainMatched: null,
    blockchainStatus: "NOT_ISSUED",
    blockchainTxHash: null,
    blockchainNetwork: null,
    blockchainAnchoredAt: null,
  }
}

function buildMockPublicReportView(code: string): PublicReportView {
  const normalized = code.toLowerCase()

  if (normalized.includes("notfound")) {
    throw new ApiError("등록되지 않은 열람코드입니다.", 404, "REPORT_ACCESS_NOT_FOUND")
  }
  if (normalized.includes("expired")) {
    throw new ApiError("만료된 열람코드입니다.", 410, "REPORT_ACCESS_EXPIRED")
  }

  return {
    reportId: 12,
    reportNo: "RPT-2026-0703-0012",
    reportType: "ANALYSIS",
    evidenceId: 2024062716,
    compareId: null,
    reportFileName: "ForenShield_Report_EVD-2024062716.pdf",
    reportHash: "a3f81c09d2e47b16f8c05a913e2d84c7715f0b6a8d94e21c3b7f6a0d5e8c92d4",
    fileSize: 2500000,
    createdAt: "2026-07-03T13:28:00+09:00",
    expiresAt: "2026-07-14T15:30:00+09:00",
    downloadPath: "/api/v1/public/reports/view/pdf?code=RV-3K9P-82MA",
  }
}
