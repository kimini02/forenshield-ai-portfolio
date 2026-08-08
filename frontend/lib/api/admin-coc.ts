import { apiRequest } from "@/lib/api/client"
import { features } from "@/lib/features"

export type CocChainStatus = "VALID" | "BROKEN"

export type CocChainEvent = {
  logId: number
  eventType: string
  /** 한글 이벤트 라벨. 백엔드 미제공 시 프론트에서 eventType으로 매핑 */
  label: string
  actor: string
  createdAt: string
  currentLogHash: string
  /** 직전 로그 해시와의 연결 검증 결과. 첫 이벤트는 항상 true */
  chainValid: boolean
  detail?: string | null
}

export type CocChainDetail = {
  evidenceId: number
  caseId: string
  caseName: string
  eventCount: number
  lastEventLabel: string
  lastEventAt: string
  status: CocChainStatus
  events: CocChainEvent[]
}

export type CocChainsResponse = {
  totalCount: number
  validCount: number
  brokenCount: number
  chains: CocChainDetail[]
  /** 백엔드 미연동 상태에서 화면 확인용 샘플 데이터를 반환했는지 여부 */
  sample?: boolean
}

/**
 * 관리자 CoC 감사: 증거별 보관 체인 목록 + 이벤트 타임라인.
 * 백엔드 계약(제안): GET /api/v1/admin/coc/chains
 * 백엔드가 아직 이 API를 제공하지 않으면 샘플 데이터로 대체하고 sample=true로 표시한다.
 */
export async function fetchAdminCocChains(): Promise<CocChainsResponse> {
  if (features.mockApi) {
    await delay(350)
    return buildSampleChains()
  }

  return apiRequest<CocChainsResponse>("/api/v1/admin/coc/chains")
}

export const COC_EVENT_LABELS: Record<string, string> = {
  EVIDENCE_UPLOADED: "증거 등록",
  HASH_CREATED: "해시 생성",
  METADATA_EXTRACTED: "메타데이터 추출",
  ANALYSIS_REQUESTED: "분석 요청",
  ANALYSIS_COMPLETED: "AI 분석 완료",
  REVIEW_APPROVED: "검토 승인",
  REPORT_CREATED: "보고서 생성",
  REPORT_SIGNED: "전자서명",
  BLOCKCHAIN_ANCHORED: "블록체인 앵커",
  EVIDENCE_VIEWED: "증거 열람",
}

export function cocEventLabel(eventType: string, provided?: string | null) {
  if (provided?.trim()) return provided.trim()
  return COC_EVENT_LABELS[eventType] ?? eventType
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function hashOf(seed: string) {
  let hash = 0x811c9dc5
  let out = ""
  for (let index = 0; index < 32; index += 1) {
    hash ^= seed.charCodeAt(index % seed.length) || index + 1
    hash = Math.imul(hash, 0x01000193) >>> 0
    out += (hash & 0xff).toString(16).padStart(2, "0")
  }
  return out
}

type SampleEventSeed = [eventType: string, actor: string, createdAt: string, chainValid?: boolean]

function buildSampleChain(
  evidenceId: number,
  caseId: string,
  caseName: string,
  seeds: SampleEventSeed[]
): CocChainDetail {
  const events: CocChainEvent[] = seeds.map(([eventType, actor, createdAt, chainValid = true], index) => ({
    logId: evidenceId * 100 + index + 1,
    eventType,
    label: cocEventLabel(eventType),
    actor,
    createdAt,
    currentLogHash: hashOf(`${evidenceId}:${eventType}:${index}`),
    chainValid: index === 0 ? true : chainValid,
  }))
  const broken = events.some((event) => !event.chainValid)
  const last = events[events.length - 1]

  return {
    evidenceId,
    caseId,
    caseName,
    eventCount: events.length,
    lastEventLabel: last.label,
    lastEventAt: last.createdAt,
    status: broken ? "BROKEN" : "VALID",
    events,
  }
}

function buildSampleChains(): CocChainsResponse {
  const chains: CocChainDetail[] = [
    buildSampleChain(20300031, "mock-review-queue-031", "블랙박스 영상 원본성 확인 #31", [
      ["EVIDENCE_UPLOADED", "김보민", "2026-07-08T14:02:00+09:00"],
      ["HASH_CREATED", "시스템", "2026-07-08T14:02:00+09:00"],
      ["METADATA_EXTRACTED", "시스템", "2026-07-08T14:03:00+09:00"],
      ["ANALYSIS_REQUESTED", "김보민", "2026-07-08T14:05:00+09:00"],
      ["ANALYSIS_COMPLETED", "시스템", "2026-07-08T14:31:00+09:00"],
      ["REPORT_CREATED", "분석관", "2026-07-09T17:35:00+09:00", false],
      ["REPORT_SIGNED", "시스템", "2026-07-09T17:36:00+09:00"],
    ]),
    buildSampleChain(20300025, "mock-review-queue-025", "블랙박스 영상 원본성 확인 #25", [
      ["EVIDENCE_UPLOADED", "김보민", "2026-07-10T09:22:00+09:00"],
      ["HASH_CREATED", "시스템", "2026-07-10T09:22:00+09:00"],
      ["METADATA_EXTRACTED", "시스템", "2026-07-10T09:22:00+09:00"],
      ["ANALYSIS_REQUESTED", "김보민", "2026-07-10T09:23:00+09:00"],
      ["ANALYSIS_COMPLETED", "시스템", "2026-07-10T09:24:00+09:00"],
      ["REVIEW_APPROVED", "윤형진", "2026-07-10T09:24:00+09:00"],
      ["REPORT_CREATED", "김보민", "2026-07-10T09:25:00+09:00"],
      ["REPORT_SIGNED", "시스템", "2026-07-10T09:25:00+09:00"],
      ["BLOCKCHAIN_ANCHORED", "시스템", "2026-07-10T09:25:00+09:00"],
    ]),
    buildSampleChain(20300024, "mock-review-queue-024", "블랙박스 영상 원본성 확인 #24", [
      ["EVIDENCE_UPLOADED", "홍길동", "2026-07-09T16:40:00+09:00"],
      ["HASH_CREATED", "시스템", "2026-07-09T16:40:00+09:00"],
      ["METADATA_EXTRACTED", "시스템", "2026-07-09T16:41:00+09:00"],
      ["ANALYSIS_COMPLETED", "시스템", "2026-07-09T17:02:00+09:00"],
      ["EVIDENCE_VIEWED", "윤형진", "2026-07-10T08:40:00+09:00"],
      ["REVIEW_APPROVED", "윤형진", "2026-07-10T08:41:00+09:00"],
    ]),
    buildSampleChain(20300019, "mock-review-queue-019", "합성 의심 제보 영상 검증 #19", [
      ["EVIDENCE_UPLOADED", "홍길동", "2026-07-07T11:12:00+09:00"],
      ["HASH_CREATED", "시스템", "2026-07-07T11:12:00+09:00"],
      ["ANALYSIS_COMPLETED", "시스템", "2026-07-07T11:40:00+09:00"],
      ["REPORT_CREATED", "홍길동", "2026-07-07T13:05:00+09:00"],
      ["REPORT_SIGNED", "시스템", "2026-07-07T13:05:00+09:00"],
      ["BLOCKCHAIN_ANCHORED", "시스템", "2026-07-07T13:06:00+09:00"],
    ]),
    buildSampleChain(20300012, "mock-review-queue-012", "면접 영상 위변조 신고 #12", [
      ["EVIDENCE_UPLOADED", "김보민", "2026-07-04T10:30:00+09:00"],
      ["HASH_CREATED", "시스템", "2026-07-04T10:30:00+09:00"],
      ["METADATA_EXTRACTED", "시스템", "2026-07-04T10:31:00+09:00"],
      ["ANALYSIS_COMPLETED", "시스템", "2026-07-04T10:58:00+09:00"],
      ["EVIDENCE_VIEWED", "관리자", "2026-07-05T09:15:00+09:00"],
    ]),
  ]

  const brokenCount = chains.filter((chain) => chain.status === "BROKEN").length

  return {
    totalCount: chains.length,
    validCount: chains.length - brokenCount,
    brokenCount,
    chains: [...chains].sort((a, b) => {
      if (a.status !== b.status) return a.status === "BROKEN" ? -1 : 1
      return new Date(b.lastEventAt).getTime() - new Date(a.lastEventAt).getTime()
    }),
  }
}
