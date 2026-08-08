import Link from "next/link"
import { Ban } from "lucide-react"

import type { SelectedEvidence } from "./analysis-request-flow"
import { Button } from "@/components/ui/button"
import { AnalysisProgress } from "@/components/analysis-progress"

type AnalysisProgressPanelProps = {
  evidence: SelectedEvidence
  progress: number
  errorMessage: string | null
  onCancel: () => void
}

export function AnalysisProgressPanel({
  evidence,
  progress,
  errorMessage,
  onCancel,
}: AnalysisProgressPanelProps) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-8 py-10 shadow-sm dark:border-border dark:bg-card">
      <AnalysisProgress fileName={evidence.name} progress={progress} />

      {errorMessage ? (
        <p className="mx-auto mt-4 max-w-xl text-center text-sm font-semibold text-red-500">
          {errorMessage}
        </p>
      ) : null}

      <div className="mx-auto mt-4 grid max-w-xl gap-2 sm:grid-cols-2">
        <Button
          className="h-10 rounded-lg bg-teal-600 text-sm font-bold hover:bg-teal-700"
          render={<Link href="/mypage" />}
          nativeButton={false}
        >
          분석 이력
        </Button>
        <Button
          type="button"
          variant="destructive"
          onClick={onCancel}
          className="h-10 rounded-lg text-sm font-bold"
        >
          <Ban className="size-4" aria-hidden="true" />
          분석 중단
        </Button>
      </div>
    </div>
  )
}
