import { FileSearch, Loader2, ShieldQuestion, UploadCloud } from "lucide-react"

import { Button } from "@/components/ui/button"

type UploadActionBarProps = {
  showNewCaseButton: boolean
  isBusy: boolean
  analyzableCount: number
  canAnalyze: boolean
  canUpload: boolean
  hasPendingFiles: boolean
  trimmedCaseName: string
  status: "idle" | "uploading" | "completed" | "error" | "analyzing"
  uploadButtonLabel: string
  pendingCount: number
  onStartNewCase: () => void
  onAnalyze: () => void
  onUpload: () => void
}

export function UploadActionBar({
  showNewCaseButton,
  isBusy,
  analyzableCount,
  canAnalyze,
  canUpload,
  hasPendingFiles,
  trimmedCaseName,
  status,
  uploadButtonLabel,
  pendingCount,
  onStartNewCase,
  onAnalyze,
  onUpload,
}: UploadActionBarProps) {
  return (
    <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <ShieldQuestion className="size-3.5" aria-hidden="true" />
        보안: 모든 데이터는 암호화되어 처리됩니다.
      </p>
      <div className="flex gap-2">
        {showNewCaseButton && (
          <Button variant="outline" onClick={onStartNewCase} disabled={isBusy}>
            새 사건 시작
          </Button>
        )}
        {analyzableCount > 0 && (
          <Button
            size="lg"
            disabled={!canAnalyze}
            onClick={onAnalyze}
            className="min-w-[120px] gap-2"
            title={!trimmedCaseName ? "분석을 시작하려면 사건명을 입력해 주세요." : undefined}
          >
            {status === "analyzing" ? (
              <>
                <Loader2 className="size-4 animate-spin" />
                품질 검사 중...
              </>
            ) : (
              <>
                <FileSearch className="size-4" aria-hidden="true" />
                분석 시작 ({analyzableCount})
              </>
            )}
          </Button>
        )}
        {hasPendingFiles && (
          <Button
            size="lg"
            disabled={!canUpload}
            onClick={onUpload}
            className="min-w-[120px] gap-2"
            title={!trimmedCaseName ? "사건명을 입력해 주세요." : undefined}
          >
            {status === "uploading" ? (
              <>
                <Loader2 className="size-4 animate-spin" />
                업로드 중...
              </>
            ) : (
              <>
                <UploadCloud className="size-4" aria-hidden="true" />
                {uploadButtonLabel}
                {pendingCount > 0 && ` (${pendingCount})`}
              </>
            )}
          </Button>
        )}
      </div>
    </div>
  )
}
