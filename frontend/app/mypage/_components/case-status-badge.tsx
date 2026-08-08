import { Badge } from "@/components/ui/badge"
import type { CaseStatus } from "@/app/mypage/_types/case"
import { getAnalysisStatusLabel } from "@/lib/status-labels"

const statusConfig: Record<
  CaseStatus,
  { label: string; className: string; dot: string }
> = {
  PENDING: {
    label: getAnalysisStatusLabel("PENDING"),
    className: "border-amber-500/40 bg-amber-500/10 text-amber-600",
    dot: "bg-amber-500",
  },
  PROCESSING: {
    label: "처리 중",
    className: "border-primary/40 bg-primary/10 text-primary",
    dot: "bg-primary",
  },
  COMPLETED: {
    label: getAnalysisStatusLabel("COMPLETED"),
    className: "border-chart-4/40 bg-chart-4/10 text-chart-4",
    dot: "bg-chart-4",
  },
  FAILED: {
    label: getAnalysisStatusLabel("FAILED"),
    className: "border-destructive/40 bg-destructive/10 text-destructive",
    dot: "bg-destructive",
  },
}

export function CaseStatusBadge({ status }: { status: CaseStatus }) {
  const config = statusConfig[status]

  return (
    <Badge variant="outline" className={`gap-1.5 ${config.className}`}>
      <span className={`size-1.5 rounded-full ${config.dot}`} aria-hidden="true" />
      {config.label}
    </Badge>
  )
}
