"use client"

import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import {
  analysisStatusBadgeClass,
  analysisStatusDotClass,
  type AnalysisStatus,
} from "@/lib/analysis-status"

type AnalysisToggleBadgeProps = {
  status: Extract<AnalysisStatus, "PENDING" | "PROCESSING">
  disabled?: boolean
  onCancel: () => void
  className?: string
}

export function AnalysisToggleBadge({
  status,
  disabled = false,
  onCancel,
  className,
}: AnalysisToggleBadgeProps) {
  const [hovered, setHovered] = useState(false)
  const label = hovered && !disabled ? "중단" : status === "PENDING" ? "분석 대기" : "분석 중"

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onCancel}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      className={cn(
        "inline-flex shrink-0 cursor-pointer items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] transition-colors",
        hovered && !disabled
          ? "border-destructive/50 bg-destructive/10 text-destructive"
          : analysisStatusBadgeClass(status),
        disabled && "cursor-not-allowed opacity-60",
        className
      )}
      title={hovered ? "클릭하면 분석을 중단하고 삭제합니다" : undefined}
    >
      <span
        className={cn(
          "size-1 rounded-full",
          hovered && !disabled ? "bg-destructive" : analysisStatusDotClass(status)
        )}
        aria-hidden="true"
      />
      {label}
    </button>
  )
}
