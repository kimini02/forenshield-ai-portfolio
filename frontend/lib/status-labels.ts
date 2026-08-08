// 분석 상태 / 위험도 라벨 공통화.
// 화면마다 "처리 중", "위험" 같은 문자열을 새로 만들지 말고 여기서 가져온다.

import type { AnalysisStatus } from "@/lib/analysis-status"

// 분석 요청 상태 라벨 (PENDING/PROCESSING/COMPLETED/FAILED)
export function getAnalysisStatusLabel(status: AnalysisStatus): string {
  switch (status) {
    case "PENDING":
      return "분석 대기"
    case "PROCESSING":
      return "분석 중"
    case "COMPLETED":
      return "완료"
    case "FAILED":
      return "실패"
    default:
      return status
  }
}

export type RiskTone = "normal" | "caution" | "danger"

// 위험도 기준선: 70점 이상 위험, 40~69 주의, 그 미만 정상.
export function getRiskTone(score: number): RiskTone {
  if (score >= 70) return "danger"
  if (score >= 40) return "caution"
  return "normal"
}

export function getRiskLabel(score: number): string {
  switch (getRiskTone(score)) {
    case "danger":
      return "위험"
    case "caution":
      return "주의"
    default:
      return "정상"
  }
}
