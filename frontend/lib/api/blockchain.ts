import { apiRequest } from "@/lib/api/client"
import { features } from "@/lib/features"
import { mockFetchEvidenceDetail } from "@/lib/mock/forensic-api"

export type AnalysisModelSnapshot = {
  name: string
  version: string
  identifier?: string | null
}

export type AnalysisModuleSnapshot = {
  module: string
  name: string
  version: string
}

export type BlockchainAnchorRecord = {
  anchorId: number
  anchorType: string
  status: string
  subjectHash: string
  transactionHash?: string | null
  blockNumber?: number | null
  network?: string | null
  anchoredAt?: string | null
  evidenceId?: number | null
  reportId?: number | null
  merkleBatchDate?: string | null
  merkleLeafCount?: number | null
  signature?: string | null
  signerCertHash?: string | null
  certVerified?: boolean | null
  offchainLogHash?: string | null
  offchainRefJson?: string | null
  analysisModelJson?: string | null
  analysisModulesJson?: string | null
  errorCode?: string | null
  message?: string | null
  transactionExplorerUrl?: string | null
}

export type BlockchainAnchorStatusResponse = {
  evidenceId: number
  evidenceHashAnchor?: BlockchainAnchorRecord | null
  reportHashAnchors?: BlockchainAnchorRecord[] | null
  latestMerkleRootAnchor?: BlockchainAnchorRecord | null
}

export async function fetchEvidenceBlockchainStatus(
  evidenceId: number
): Promise<BlockchainAnchorStatusResponse> {
  if (features.mockApi) {
    const detail = await mockFetchEvidenceDetail(evidenceId)
    const info = detail.blockchainInfo
    if (!info) {
      return {
        evidenceId,
        evidenceHashAnchor: null,
        reportHashAnchors: [],
        latestMerkleRootAnchor: null,
      }
    }
    return {
      evidenceId,
      evidenceHashAnchor: {
        anchorId: evidenceId,
        anchorType: info.anchorType,
        status: info.status,
        subjectHash: info.subjectHash ?? detail.integrityInfo.originalHash,
        transactionHash: info.transactionHash,
        blockNumber: null,
        network: info.network,
        anchoredAt: info.anchoredAt,
        evidenceId,
        reportId: null,
        merkleBatchDate: null,
        merkleLeafCount: null,
        signature: null,
        signerCertHash: null,
        certVerified: info.certVerified ?? null,
        offchainLogHash: null,
        offchainRefJson: null,
        errorCode: info.errorCode ?? null,
        message: info.verificationMessage ?? null,
        transactionExplorerUrl: info.transactionExplorerUrl ?? null,
      },
      reportHashAnchors: [],
      latestMerkleRootAnchor: null,
    }
  }

  return apiRequest<BlockchainAnchorStatusResponse>(`/api/v1/evidences/${evidenceId}/blockchain`)
}

export function parseOffchainRef(json?: string | null): Record<string, string> {
  if (!json) return {}
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>
    const result: Record<string, string> = {}
    for (const [key, value] of Object.entries(parsed)) {
      if (typeof value === "string" && value.length > 0) {
        result[key] = value
      }
    }
    return result
  } catch {
    return {}
  }
}

export function parseAnalysisModelJson(json?: string | null): AnalysisModelSnapshot | null {
  if (!json?.trim()) return null
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>
    const name = typeof parsed.name === "string" ? parsed.name : ""
    const version = typeof parsed.version === "string" ? parsed.version : ""
    if (!name && !version) return null
    return {
      name: name || version,
      version: version || name,
      identifier: typeof parsed.identifier === "string" ? parsed.identifier : null,
    }
  } catch {
    return null
  }
}

export function parseAnalysisModulesJson(json?: string | null): AnalysisModuleSnapshot[] {
  if (!json?.trim()) return []
  try {
    const parsed = JSON.parse(json) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((item): item is Record<string, unknown> => item != null && typeof item === "object")
      .map((item) => ({
        module: typeof item.module === "string" ? item.module : "",
        name: typeof item.name === "string" ? item.name : "",
        version: typeof item.version === "string" ? item.version : "",
      }))
      .filter((item) => item.module || item.name || item.version)
  } catch {
    return []
  }
}
