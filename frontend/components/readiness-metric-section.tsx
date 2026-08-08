"use client"

import { useEffect, useRef, useState } from "react"

import { ReadinessMetricPanel } from "@/components/readiness-metric-panel"
import {
  fetchEvidenceReadiness,
  runEvidenceReadinessCheck,
  type EvidenceReadinessResponse,
} from "@/lib/evidence-api"
import {
  buildReadinessMetricItems,
  getReadinessFrameCheckNote,
} from "@/lib/readiness"

type ReadinessMetricSectionProps = {
  evidenceId: number
  analysisCompleted?: boolean
  className?: string
}

export function ReadinessMetricSection({
  evidenceId,
  analysisCompleted = false,
  className,
}: ReadinessMetricSectionProps) {
  const [readiness, setReadiness] = useState<EvidenceReadinessResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const refreshAttemptedRef = useRef<number | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadReadiness() {
      setLoading(true)

      try {
        let next = await fetchEvidenceReadiness(evidenceId)

        const needsFrameRefresh =
          analysisCompleted &&
          next.frameCheckStatus !== "COMPLETED" &&
          refreshAttemptedRef.current !== evidenceId

        if (needsFrameRefresh) {
          refreshAttemptedRef.current = evidenceId
          try {
            next = await runEvidenceReadinessCheck(evidenceId)
          } catch {
            // 저장된 스냅샷 유지
          }
        }

        if (!cancelled) setReadiness(next)
      } catch {
        if (!cancelled) setReadiness(null)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void loadReadiness()

    return () => {
      cancelled = true
    }
  }, [analysisCompleted, evidenceId])

  return (
    <ReadinessMetricPanel
      className={className}
      metrics={buildReadinessMetricItems(readiness)}
      loading={loading}
      statusMessage={getReadinessFrameCheckNote(readiness)}
    />
  )
}
