"use client"

import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import {
  analysisStatusBadgeClass,
  analysisStatusDotClass,
  analysisStatusLabel,
  type AnalysisStatus,
} from "@/lib/analysis-status"

type AnalysisStatusBadgeProps = {
  status: AnalysisStatus
  className?: string
}

export function AnalysisStatusBadge({ status, className }: AnalysisStatusBadgeProps) {
  return (
    <Badge
      variant="outline"
      className={cn(
        "shrink-0 gap-1.5 text-[10px]",
        analysisStatusBadgeClass(status),
        className
      )}
    >
      <span
        className={cn("size-1 rounded-full", analysisStatusDotClass(status))}
        aria-hidden="true"
      />
      {analysisStatusLabel[status]}
    </Badge>
  )
}
