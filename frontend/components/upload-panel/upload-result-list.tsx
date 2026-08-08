import { AlertTriangle, CheckCircle2 } from "lucide-react"

import type { UploadFileCardState } from "@/components/upload-panel/upload-file-card"
import { UploadResultCard } from "@/components/upload-panel/upload-result-card"

type UploadResultListProps = {
  fileStates: UploadFileCardState[]
  displayedSuccessStates: UploadFileCardState[]
  hasAnalyzedFiles: boolean
  isBusy: boolean
  canCancelAnalysisFile: (item: UploadFileCardState) => boolean
  onCancelAnalysisFile: (index: number) => void
}

export function UploadResultList({
  fileStates,
  displayedSuccessStates,
  hasAnalyzedFiles,
  isBusy,
  canCancelAnalysisFile,
  onCancelAnalysisFile,
}: UploadResultListProps) {
  if (displayedSuccessStates.length === 0) return null

  return (
    <div className="space-y-3">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <CheckCircle2 className="size-4 text-primary" />
        {hasAnalyzedFiles
          ? `분석 현황 (${displayedSuccessStates.length}건)`
          : `업로드 완료 (${displayedSuccessStates.length}건)`}
      </h3>
      {fileStates.map((item, fileIndex) => {
        if (item.status !== "success" || !item.result) return null

        const successIndex = displayedSuccessStates.findIndex(
          (entry) => entry.result?.evidenceId === item.result!.evidenceId
        )

        return (
          <UploadResultCard
            key={item.result.evidenceId}
            item={item}
            fileIndex={fileIndex}
            successIndex={successIndex}
            canCancel={canCancelAnalysisFile(item) && !isBusy}
            onCancelAnalysisFile={onCancelAnalysisFile}
          />
        )
      })}
      <div className="mt-3 flex items-center gap-2 border-t border-primary/10 pt-3">
        <AlertTriangle className="size-3.5 text-amber-500" />
        <p className="text-[11px] text-muted-foreground">
          {hasAnalyzedFiles
            ? "분석 요청이 등록되었습니다. 분석은 큐에서 순차적으로 진행되며, 기록은 마이페이지와 상세 페이지에 보존됩니다."
            : "업로드가 완료되었습니다. 사건명을 입력한 뒤 분석 시작을 누르세요. 새 사건 시작은 현재 화면만 비웁니다."}
        </p>
      </div>
    </div>
  )
}
