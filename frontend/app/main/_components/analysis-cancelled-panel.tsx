import Link from "next/link"
import { Ban } from "lucide-react"

import { MetadataRow } from "./media-metadata-preview"
import type { SelectedEvidence } from "./analysis-request-flow"
import { Button } from "@/components/ui/button"

type AnalysisCancelledPanelProps = {
  evidence: SelectedEvidence
  progress: number
  onRestart: () => void
}

export function AnalysisCancelledPanel({
  evidence,
  progress,
  onRestart,
}: AnalysisCancelledPanelProps) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-8 py-10 shadow-sm dark:border-border dark:bg-card">
      <div className="flex flex-col items-center text-center">
        <div className="flex size-16 items-center justify-center rounded-full bg-red-50 text-red-500 ring-1 ring-red-100 dark:bg-red-500/10 dark:ring-red-500/20">
          <Ban className="size-8" aria-hidden="true" />
        </div>
        <h1 className="mt-5 text-xl font-bold text-slate-950 dark:text-foreground">
          분석이 중단되었습니다
        </h1>
        <p className="mt-2 text-sm font-bold text-slate-400 dark:text-muted-foreground">
          {evidence.name}
        </p>
      </div>

      <div className="mx-auto mt-8 max-w-xl">
        <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-muted">
          <div
            className="h-full rounded-full bg-red-500 transition-all duration-200"
            style={{ width: `${progress}%` }}
          />
        </div>
        <p className="mt-5 text-center text-base font-bold text-red-500">{progress}%에서 중단</p>

        <div className="mt-6 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm font-bold text-red-600 dark:border-red-500/25 dark:bg-red-500/10 dark:text-red-300">
          분석 요청이 사용자에 의해 중단되었습니다. 업로드된 파일은 삭제되지 않으며,
          필요하면 같은 파일로 다시 분석을 시작할 수 있습니다.
        </div>

        <dl className="mt-5 rounded-xl border border-slate-200 bg-slate-50/70 p-4 dark:border-border dark:bg-muted/30">
          <MetadataRow label="파일명" value={evidence.name} />
          <MetadataRow label="파일 크기" value={evidence.sizeLabel} />
          <MetadataRow label="중단 단계" value={getCancelledStageLabel(progress)} accent />
        </dl>
      </div>

      <div className="mx-auto mt-6 grid max-w-xl gap-2 sm:grid-cols-2">
        <Button
          type="button"
          onClick={onRestart}
          className="h-10 rounded-lg bg-teal-600 text-sm font-bold hover:bg-teal-700"
        >
          다시 분석 시작
        </Button>
        <Button
          variant="outline"
          render={<Link href="/mypage" />}
          nativeButton={false}
          className="h-10 rounded-lg border-slate-200 text-sm font-bold text-slate-600 dark:border-border dark:text-muted-foreground"
        >
          분석 이력
        </Button>
      </div>
    </div>
  )
}

function getCancelledStageLabel(progress: number) {
  if (progress >= 90) return "리포트 생성 전 중단"
  if (progress >= 78) return "블록체인 앵커링 전 중단"
  if (progress >= 64) return "WORM 저장 전 중단"
  if (progress >= 48) return "디지털 서명 전 중단"
  if (progress >= 28) return "AI 위변조 분석 중단"
  if (progress >= 14) return "메타데이터 추출 중단"

  return "파일 해시 생성 중단"
}
