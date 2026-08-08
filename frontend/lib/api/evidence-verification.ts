import { apiRequest } from "@/lib/api/client"

export type IntegrityCheck = {
  checkType: string
  valid: boolean
  errorCode?: string | null
  message: string
}

export type IntegrityVerifyResponse = {
  evidenceId: number
  valid: boolean
  checks: IntegrityCheck[]
}

export function verifyEvidenceIntegrity(
  evidenceId: number
): Promise<IntegrityVerifyResponse> {
  return apiRequest<IntegrityVerifyResponse>(
    `/api/v1/evidences/${evidenceId}/integrity/verify`
  )
}
