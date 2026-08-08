import { Ban, FileVideo, Loader2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { SourceEvidenceCard } from "./compare-file-uploader"
import type { SourceEvidence, UploadedCompareFile } from "./compare-verification-flow"

type CompareProcessingPanelProps = {
  sourceEvidence: SourceEvidence
  compareFile: UploadedCompareFile | null
  progress: number
  onCancel: () => void
}

export function CompareProcessingPanel({
  sourceEvidence,
  compareFile,
  progress,
  onCancel,
}: CompareProcessingPanelProps) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white shadow-none dark:border-border dark:bg-card">
      <div className="border-b border-slate-200 px-4 py-5 dark:border-border sm:px-6">
        <h1 className="text-lg font-bold text-slate-950 dark:text-foreground">비교 처리 중</h1>
        <p className="mt-1 text-sm font-medium text-slate-500">
          해시 계산, 메타데이터 비교, 전자서명 확인을 순서대로 수행하고 있습니다.
        </p>
      </div>

      <div className="space-y-5 px-4 py-5 sm:px-6">
        <div className="grid items-stretch gap-3 lg:grid-cols-[minmax(0,1fr)_44px_minmax(0,1fr)]">
          <SourceEvidenceCard evidence={sourceEvidence} />

          <div className="hidden items-center justify-center lg:flex">
            <Loader2 className="size-5 animate-spin text-slate-400" aria-hidden="true" />
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4 dark:border-border dark:bg-background">
            <p className="text-xs font-bold text-slate-400">비교 대상 (제출본)</p>
            <div className="mt-3 flex min-w-0 items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-600 dark:bg-secondary dark:text-muted-foreground">
                <FileVideo className="size-5" aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-bold text-slate-950 dark:text-foreground">
                  비교 대상 파일
                </span>
                <span className="mt-1 block text-xs font-semibold text-slate-500">
                  {compareFile?.sizeLabel ?? "-"}
                </span>
              </span>
            </div>
          </div>
        </div>

        <div>
          <div className="flex items-center justify-between text-xs font-bold text-slate-500">
            <span>검증 진행률</span>
            <span className="tabular-nums">{progress}%</span>
          </div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-secondary">
            <div
              className="h-full rounded-full bg-slate-950 transition-all duration-300 dark:bg-foreground"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      </div>

      <div className="flex items-center justify-end border-t border-slate-200 px-4 py-4 dark:border-border sm:px-6">
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          className="h-10 w-full rounded-full border-slate-200 bg-white px-5 text-sm font-semibold text-red-500 hover:bg-red-50 hover:text-red-600 dark:border-border dark:bg-card sm:w-auto"
        >
          <Ban className="size-4" aria-hidden="true" />
          검증 중지
        </Button>
      </div>
    </section>
  )
}
