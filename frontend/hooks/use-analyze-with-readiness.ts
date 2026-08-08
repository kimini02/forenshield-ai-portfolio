"use client"

import { useCallback, useRef, useState } from "react"

import type { StartAnalysisResponse } from "@/lib/evidence-api"
import {
  fetchStoredReadinessForAnalysis,
  hasBlockingReadiness,
  isVideoEvidence,
  needsVideoFrameReadinessRefresh,
  refreshVideoFrameReadiness,
  shouldShowQualityDialog,
  worstReadinessTier,
  type ReadinessCheckPhase,
  type ReadinessCheckSummary,
  type ReadinessCheckTarget,
  type ReadinessTier,
} from "@/lib/readiness"

const FRAME_SAMPLING_MIN_MS = 3000

function hasVideoTarget(targets: ReadinessCheckTarget[]): boolean {
  return targets.some((target) => isVideoEvidence(target))
}

function summariesIncludeVideo(summaries: ReadinessCheckSummary[]): boolean {
  return summaries.some((target) => isVideoEvidence(target))
}

type PendingAnalysis = {
  runAnalyze: (acknowledgeQualityWarning: boolean) => Promise<StartAnalysisResponse>
  onSuccess: (response: StartAnalysisResponse) => void
  onError: (error: unknown) => void
}

export type StartAnalysisWithReadinessOptions = {
  targets: ReadinessCheckTarget[]
  runAnalyze: (acknowledgeQualityWarning: boolean) => Promise<StartAnalysisResponse>
  onSuccess: (response: StartAnalysisResponse) => void
  onError: (error: unknown) => void
  onReadinessChecked?: (summaries: ReadinessCheckSummary[]) => void
}

function delay(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

async function withMinDuration<T>(promise: Promise<T>, ms: number): Promise<T> {
  if (ms <= 0) return promise
  const [result] = await Promise.all([promise, delay(ms)])
  return result
}

function summariesToTargets(summaries: ReadinessCheckSummary[]): ReadinessCheckTarget[] {
  return summaries.map(({ evidenceId, fileName, metadata }) => ({
    evidenceId,
    fileName,
    metadata,
  }))
}

export function useAnalyzeWithReadiness() {
  const [isCheckingReadiness, setIsCheckingReadiness] = useState(false)
  const [readinessCheckPhase, setReadinessCheckPhase] = useState<ReadinessCheckPhase>(null)
  const [qualityDialogOpen, setQualityDialogOpen] = useState(false)
  const [qualityDialogLoading, setQualityDialogLoading] = useState(false)
  const [qualityDialogSummaries, setQualityDialogSummaries] = useState<ReadinessCheckSummary[]>([])
  const [qualityDialogWorstTier, setQualityDialogWorstTier] = useState<ReadinessTier>("GOOD")
  const pendingRef = useRef<PendingAnalysis | null>(null)

  const closeQualityDialog = useCallback(() => {
    setQualityDialogOpen(false)
    setQualityDialogLoading(false)
    setQualityDialogSummaries([])
    pendingRef.current = null
  }, [])

  const resetReadinessCheckUi = useCallback(() => {
    setIsCheckingReadiness(false)
    setReadinessCheckPhase(null)
  }, [])

  const startAnalysisWithReadiness = useCallback(
    async (options: StartAnalysisWithReadinessOptions) => {
      const analyzingVideo = hasVideoTarget(options.targets)
      setIsCheckingReadiness(true)
      setReadinessCheckPhase(analyzingVideo ? "frameSampling" : "metadata")

      try {
        let summaries = await fetchStoredReadinessForAnalysis(options.targets)
        options.onReadinessChecked?.(summaries)

        if (summariesIncludeVideo(summaries)) {
          setReadinessCheckPhase("frameSampling")
          const refreshPromise = needsVideoFrameReadinessRefresh(summaries)
            ? refreshVideoFrameReadiness(summariesToTargets(summaries))
            : Promise.resolve(summaries)
          summaries = await withMinDuration(refreshPromise, FRAME_SAMPLING_MIN_MS)
          options.onReadinessChecked?.(summaries)
        }

        if (shouldShowQualityDialog(summaries)) {
          pendingRef.current = {
            runAnalyze: options.runAnalyze,
            onSuccess: options.onSuccess,
            onError: options.onError,
          }
          setQualityDialogSummaries(summaries)
          setQualityDialogWorstTier(worstReadinessTier(summaries))
          setQualityDialogOpen(true)
          return
        }

        const response = await options.runAnalyze(false)
        options.onSuccess(response)
      } catch (error) {
        options.onError(error)
      } finally {
        resetReadinessCheckUi()
      }
    },
    [resetReadinessCheckUi]
  )

  const confirmQualityDialog = useCallback(async () => {
    const pending = pendingRef.current
    if (!pending) return

    if (hasBlockingReadiness(qualityDialogSummaries)) {
      closeQualityDialog()
      resetReadinessCheckUi()
      return
    }

    setIsCheckingReadiness(true)
    setReadinessCheckPhase("aiAnalysis")
    setQualityDialogLoading(true)

    try {
      let summaries = qualityDialogSummaries

      if (needsVideoFrameReadinessRefresh(summaries)) {
        summaries = await refreshVideoFrameReadiness(summariesToTargets(summaries))
        setQualityDialogSummaries(summaries)
      }

      if (hasBlockingReadiness(summaries)) {
        setQualityDialogWorstTier(worstReadinessTier(summaries))
        setQualityDialogLoading(false)
        resetReadinessCheckUi()
        return
      }

      const response = await pending.runAnalyze(true)
      closeQualityDialog()
      pending.onSuccess(response)
    } catch (error) {
      closeQualityDialog()
      pending.onError(error)
    } finally {
      setQualityDialogLoading(false)
      resetReadinessCheckUi()
    }
  }, [closeQualityDialog, qualityDialogSummaries, resetReadinessCheckUi])

  const cancelQualityDialog = useCallback(() => {
    closeQualityDialog()
    resetReadinessCheckUi()
  }, [closeQualityDialog, resetReadinessCheckUi])

  return {
    isCheckingReadiness,
    readinessCheckPhase,
    qualityDialogOpen,
    qualityDialogLoading,
    qualityDialogSummaries,
    qualityDialogWorstTier,
    startAnalysisWithReadiness,
    confirmQualityDialog,
    cancelQualityDialog,
  }
}
