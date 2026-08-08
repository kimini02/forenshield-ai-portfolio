import type { AnalysisStatus } from "@/lib/analysis-status"
import type { EvidenceDetailData } from "@/lib/api/evidence-detail"
import { getAnalysisStatusLabel, getRiskLabel, getRiskTone } from "@/lib/status-labels"

export type CaseRiskTone = "green" | "orange" | "red"

export function getCaseRiskTone(data: EvidenceDetailData): CaseRiskTone {
  const { analysisInfo } = data

  if (analysisInfo.status === "FAILED") return "red"

  const tone = getRiskToneFromAnalysis(data)
  if (tone === "danger") return "red"
  if (tone === "caution") return "orange"
  return "green"
}

export function getCaseRiskClassName(tone: CaseRiskTone) {
  if (tone === "red") {
    return {
      badge: "border-red-200 bg-red-50 text-red-600",
      text: "text-red-500",
      soft: "bg-red-50 text-red-600",
      bar: "bg-red-500",
    }
  }

  if (tone === "orange") {
    return {
      badge: "border-orange-200 bg-orange-50 text-orange-600",
      text: "text-orange-500",
      soft: "bg-orange-50 text-orange-600",
      bar: "bg-orange-500",
    }
  }

  return {
    badge: "border-emerald-200 bg-emerald-50 text-emerald-600",
    text: "text-emerald-600",
    soft: "bg-emerald-50 text-emerald-600",
    bar: "bg-teal-500",
  }
}

export function getDisplayRiskLabel(data: EvidenceDetailData): string {
  const { analysisInfo } = data

  if (analysisInfo.status === "FAILED") return "분석 실패"
  if (analysisInfo.status !== "COMPLETED") {
    return getAnalysisStatusLabel(analysisInfo.status)
  }
  const riskLevelLabel = getRiskLevelLabel(analysisInfo.riskLevel)
  if (riskLevelLabel) return riskLevelLabel

  if (analysisInfo.riskScore == null) return "분석 근거 없음"

  return getRiskLabel(normalizeScore(analysisInfo.riskScore))
}

function getRiskToneFromAnalysis(data: EvidenceDetailData) {
  const { analysisInfo } = data

  if (analysisInfo.riskLevel === "HIGH") return "danger"
  if (analysisInfo.riskLevel === "MEDIUM") return "caution"
  if (analysisInfo.riskLevel === "LOW") return "normal"

  return getRiskTone(normalizeScore(analysisInfo.riskScore ?? 0))
}

function getRiskLevelLabel(riskLevel: EvidenceDetailData["analysisInfo"]["riskLevel"]) {
  if (riskLevel === "HIGH") return "위험"
  if (riskLevel === "MEDIUM") return "주의"
  if (riskLevel === "LOW") return "정상"
  return null
}

function normalizeScore(score: number) {
  if (score > 0 && score <= 1) return Math.round(score * 100)
  return score
}

export function getSummaryPlaceholder(status: AnalysisStatus): string {
  if (status === "PENDING") return "분석 대기 중입니다."
  if (status === "PROCESSING") return "분석이 진행 중입니다."
  if (status === "FAILED") return "분석에 실패했습니다."
  return "분석 근거 없음"
}

export function buildProgressSteps(data: EvidenceDetailData) {
  const { evidenceInfo, analysisInfo } = data

  return [
    { title: "파일 업로드", time: evidenceInfo.uploadedAt, done: true },
    { title: "무결성 검증", time: evidenceInfo.uploadedAt, done: true },
    { title: "프레임 분석", time: analysisInfo.requestedAt, done: Boolean(analysisInfo.requestedAt) },
    { title: "위험도 탐지", time: analysisInfo.completedAt, done: analysisInfo.status === "COMPLETED" },
    { title: "품질 평가", time: analysisInfo.completedAt, done: analysisInfo.status === "COMPLETED" },
    { title: "분석 완료", time: analysisInfo.completedAt, done: analysisInfo.status === "COMPLETED" },
  ]
}
