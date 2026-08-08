export type AnalysisStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED"

export const analysisStatusLabel: Record<AnalysisStatus, string> = {
  PENDING: "분석 대기",
  PROCESSING: "분석 중",
  COMPLETED: "분석 완료",
  FAILED: "분석 실패",
}

export function analysisStatusBadgeClass(status: AnalysisStatus): string {
  switch (status) {
    case "PENDING":
      return "border-amber-500/40 bg-amber-500/10 text-amber-600 dark:text-amber-400"
    case "PROCESSING":
      return "border-blue-500/40 bg-blue-500/10 text-blue-600 dark:text-blue-400"
    case "COMPLETED":
      return "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
    case "FAILED":
      return "border-destructive/40 bg-destructive/10 text-destructive"
  }
}

export function analysisStatusDotClass(status: AnalysisStatus): string {
  switch (status) {
    case "PENDING":
      return "bg-amber-500"
    case "PROCESSING":
      return "bg-blue-500"
    case "COMPLETED":
      return "bg-emerald-500"
    case "FAILED":
      return "bg-destructive"
  }
}
