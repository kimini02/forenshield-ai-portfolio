"use client"

import { Badge } from "@/components/ui/badge"
import { readinessTierBadgeClass, readinessTierLabel, type ReadinessTier } from "@/lib/readiness"
import { cn } from "@/lib/utils"

type ReadinessBadgeProps = {
  tier: ReadinessTier
  className?: string
  title?: string
}

export function ReadinessBadge({ tier, className, title }: ReadinessBadgeProps) {
  return (
    <Badge
      variant="outline"
      title={title ?? `화질 적합성: ${readinessTierLabel(tier)}`}
      className={cn("shrink-0 text-[10px]", readinessTierBadgeClass(tier), className)}
    >
      화질 {readinessTierLabel(tier)}
    </Badge>
  )
}
