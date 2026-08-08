"use client"

import { Loader2 } from "lucide-react"

import {
  buildDefaultReadinessMetricItems,
  READINESS_THRESHOLDS_VERSION,
  type ReadinessMetricVerdict,
  type UiReadinessMetricItem,
} from "@/lib/readiness"
import { cn } from "@/lib/utils"

type ReadinessMetricPanelProps = {
  metrics?: UiReadinessMetricItem[]
  loading?: boolean
  statusMessage?: string | null
  className?: string
}

const VERDICT_BADGE_CLASS: Record<ReadinessMetricVerdict, string> = {
  good: "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  caution: "border-amber-500/30 bg-amber-500/10 text-amber-800 dark:text-amber-200",
  poor: "border-orange-500/30 bg-orange-500/10 text-orange-800 dark:text-orange-200",
  unknown: "border-slate-200 bg-slate-100 text-slate-500 dark:border-border dark:bg-muted",
}

export function ReadinessMetricPanel({
  metrics,
  loading = false,
  statusMessage = null,
  className,
}: ReadinessMetricPanelProps) {
  const displayMetrics =
    metrics && metrics.length > 0 ? metrics : buildDefaultReadinessMetricItems()

  return (
    <section
      className={cn(
        "rounded-xl border border-slate-100 bg-white p-5 dark:border-border dark:bg-card",
        className
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-bold text-slate-950 dark:text-foreground">화질 사전 검사</h3>
          <p className="mt-1 text-sm font-semibold text-slate-500">
            Blur, Blockiness, FFT Peak 측정 결과입니다. 기준은 ForenShield 내부 프로파일(
            {READINESS_THRESHOLDS_VERSION})을 따릅니다.
          </p>
        </div>
        {loading ? (
          <Loader2 className="size-5 shrink-0 animate-spin text-slate-400" aria-hidden="true" />
        ) : null}
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        {displayMetrics.map((metric) => (
          <div
            key={metric.key}
            className="rounded-xl border border-slate-100 bg-slate-50/70 px-4 py-3.5 dark:border-border dark:bg-background"
          >
            <div className="flex items-start justify-between gap-2">
              <p className="text-xs font-semibold text-slate-400">{metric.label}</p>
              <span
                className={cn(
                  "shrink-0 rounded-full border px-2 py-0.5 text-[11px] font-bold",
                  VERDICT_BADGE_CLASS[metric.verdict]
                )}
              >
                {metric.verdictLabel}
              </span>
            </div>
            <p className="mt-1 font-mono text-xl font-bold tracking-tight text-slate-950 dark:text-foreground">
              {metric.value}
            </p>
            <p className="mt-1 text-[11px] font-semibold text-slate-400">{metric.thresholdLabel}</p>
            <p className="mt-2 text-xs font-medium leading-relaxed text-slate-500">
              {metric.verdictExplanation}
            </p>
          </div>
        ))}
      </div>

      {statusMessage ? (
        <p className="mt-4 text-sm font-medium leading-relaxed text-amber-700 dark:text-amber-300">
          {statusMessage}
        </p>
      ) : null}
    </section>
  )
}
