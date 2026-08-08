import type { CompareResult, CompareVerdict } from "@/lib/api/compare"
import { getSession, isMockAuthSession } from "@/lib/auth"
import { features } from "@/lib/features"

const STORAGE_KEY = "forenshield-compare-results"
const MOCK_COMPARE_ID_OFFSET = 9_000_000_000

export type StoredCompareResultSummary = {
  compareId: number
  originalEvidenceId: number
  verdict: CompareVerdict
  verdictLabel: string
  matchCount: number
  mismatchCount: number
  createdAt: string
}

function readCompareSummaries(): StoredCompareResultSummary[] {
  if (typeof window === "undefined") return []

  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function writeCompareSummaries(summaries: StoredCompareResultSummary[]) {
  if (typeof window === "undefined") return
  localStorage.setItem(STORAGE_KEY, JSON.stringify(summaries.slice(0, 30)))
}

export function getMockCompareIdForEvidence(evidenceId: number) {
  return MOCK_COMPARE_ID_OFFSET + evidenceId
}

export function getMockEvidenceIdFromCompareId(compareId: number) {
  return compareId >= MOCK_COMPARE_ID_OFFSET ? compareId - MOCK_COMPARE_ID_OFFSET : null
}

function getMockCompareSummary(evidenceId: number): StoredCompareResultSummary | null {
  if (!features.mockApi && !isMockAuthSession(getSession())) return null

  return {
    compareId: getMockCompareIdForEvidence(evidenceId),
    originalEvidenceId: evidenceId,
    verdict: "TAMPERED",
    verdictLabel: "비교검증 결과 확인",
    matchCount: 5,
    mismatchCount: 3,
    createdAt: "2026-07-04T10:04:00+09:00",
  }
}

export function saveCompareResultSummary(result: CompareResult) {
  const summary: StoredCompareResultSummary = {
    compareId: result.compareId,
    originalEvidenceId: result.originalEvidenceId,
    verdict: result.verdict,
    verdictLabel: result.summary.verdictLabel,
    matchCount: result.summary.matchCount,
    mismatchCount: result.summary.mismatchCount,
    createdAt: result.createdAt,
  }
  const existing = readCompareSummaries().filter(
    (item) => item.compareId !== result.compareId,
  )

  writeCompareSummaries([summary, ...existing])
}

export function getLatestCompareResultSummary(evidenceId: number) {
  return readCompareSummaries()
    .filter((item) => item.originalEvidenceId === evidenceId)
    .sort(
      (first, second) =>
        new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime(),
    )[0] ?? getMockCompareSummary(evidenceId)
}
