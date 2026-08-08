import type { AiResult, ReviewStatus } from "@/lib/permissions"

export type CaseStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED"

export interface CaseSummary {
  caseId: string
  caseName: string
  status: CaseStatus
  createdAt: string
  evidenceCount: number
  organizationId?: string | null
  organizationName?: string | null
  organizationType?: string | null
  department?: string | null
  createdBy?: string | null
  assigneeId?: string | null
  reviewerId?: string | null
  reviewStatus?: ReviewStatus | null
  aiResult?: AiResult | null
  reviewRequestedAt?: string | null
  representativeFileName?: string
  representativeEvidenceId?: number | null
  representativeEvidenceLabel?: string | null
  riskScore?: number | null
}
