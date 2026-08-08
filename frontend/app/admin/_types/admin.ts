import type { UserRole } from "@/lib/permissions"
import type { OrgType } from "@/app/signup/organizationData"

export type UserStatus = "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED"

export interface AdminUser {
  id: string
  username: string
  displayName: string
  email: string
  organizationName?: string
  organizationType?: OrgType | string | null
  department: string
  role?: UserRole
  joinedAt: string
  status: UserStatus
}

export type InviteCodeStatus = "UNUSED" | "USED" | "EXPIRED"

export interface InviteCode {
  id: string
  code: string
  createdAt: string
  expiresAt: string
  status: InviteCodeStatus
  usedBy?: string
}

export type LogCategory = "AUTH" | "ANALYSIS" | "ADMIN" | "COC" | "SECURITY"

export interface AdminLog {
  id: string
  timestamp: string
  category: LogCategory
  actor: string
  actorId: string
  actorName?: string | null
  department: string
  action: string
  detail?: string
}

export interface AdminProfile {
  username: string
  displayName: string
  email: string
  department: string
  phone: string
  role: string
}

export type EvidenceStatus = "UPLOADED" | "DELETED"
export type EvidenceFileType = "IMAGE" | "VIDEO" | "AUDIO"
export type EvidenceAnalysisStatus =
  | "NONE"
  | "PENDING"
  | "PROCESSING"
  | "QUEUED"
  | "ANALYZING"
  | "COMPLETED"
  | "FAILED"

export interface AdminEvidence {
  id: string
  fileName: string
  fileType: EvidenceFileType
  caseNumber?: string
  caseName?: string
  uploaderUsername: string
  uploaderName: string
  department: string
  hashValue: string
  fileSize: number
  uploadedAt: string
  status: EvidenceStatus
  analysisStatus: EvidenceAnalysisStatus
}

export interface AdminEvidenceMetadata {
  width?: number
  height?: number
  durationSec?: number
  fps?: number
  codec?: string
  sampleRate?: number
  channels?: number
  deviceInfo?: string
  extractionStatus?: string
}

export interface AdminEvidenceAnalysis {
  id: string
  status: EvidenceAnalysisStatus
  requestedAt: string
  completedAt?: string
}

export interface AdminEvidenceCustodyLog {
  id: string
  timestamp: string
  category: LogCategory
  actor: string
  action: string
  detail?: string
}

export interface AdminEvidenceDetail extends AdminEvidence {
  mimeType: string
  hashAlgorithm: string
  deletedAt?: string
  metadata?: AdminEvidenceMetadata | null
  analysisHistory: AdminEvidenceAnalysis[]
  custodyLogs: AdminEvidenceCustodyLog[]
}
