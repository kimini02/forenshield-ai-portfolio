import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { AnalysisStatusBadge } from "@/components/analysis-status-badge"
import { ReadinessBadge } from "@/components/readiness-badge"
import type { UploadFileCardState } from "@/components/upload-panel/upload-file-card"

type UploadResultCardProps = {
  item: UploadFileCardState
  fileIndex: number
  successIndex: number
  canCancel: boolean
  onCancelAnalysisFile: (index: number) => void
}

export function UploadResultCard({
  item,
  fileIndex,
  successIndex,
  canCancel,
  onCancelAnalysisFile,
}: UploadResultCardProps) {
  if (item.status !== "success" || !item.result) return null

  const res = item.result

  return (
    <div
      className="animate-in fade-in slide-in-from-top-2 rounded-lg border border-primary/20 bg-primary/5 p-4 duration-500"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-1 gap-3">
          <div className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/20 text-primary">
            <span className="text-xs font-bold">{successIndex + 1}</span>
          </div>
          <div className="min-w-0">
            <h4 className="max-w-[200px] truncate text-sm font-semibold text-foreground sm:max-w-xs">
              {res.fileName}
            </h4>
            <p className="text-[10px] text-muted-foreground">
              증거 ID: {res.evidenceId}
              {res.caseName ? ` · ${res.caseName}` : ""}
            </p>
            <p className="mt-1 break-all font-mono text-[10px] text-muted-foreground">
              {res.hashAlgorithm}: {res.hashValue.slice(0, 16)}...
            </p>
          </div>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-2">
          {res.readiness && !item.analysisStatus ? (
            <ReadinessBadge tier={res.readiness.readinessTier} />
          ) : null}
          {item.analysisStatus === "PENDING" ? (
            <AnalysisStatusBadge status="PENDING" />
          ) : item.analysisStatus === "PROCESSING" ? (
            <AnalysisStatusBadge status="PROCESSING" />
          ) : item.analysisStatus === "COMPLETED" ? (
            <AnalysisStatusBadge status="COMPLETED" />
          ) : item.analysisStatus === "FAILED" ? (
            <AnalysisStatusBadge status="FAILED" />
          ) : (
            <Badge variant="outline" className="text-[10px]">
              업로드 완료
            </Badge>
          )}
          {canCancel && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="h-7 px-2 text-[11px] text-destructive hover:text-destructive"
              onClick={() => void onCancelAnalysisFile(fileIndex)}
            >
              중단
            </Button>
          )}
        </div>
      </div>
      {item.analysisStatus === "PROCESSING" && (
        <div className="mt-3 space-y-1.5">
          <div className="flex justify-between text-[10px] font-medium text-primary">
            <span>분석 중 ({item.analysisProgress ?? 0}%)</span>
          </div>
          <Progress value={item.analysisProgress ?? 0} className="h-2" />
        </div>
      )}
    </div>
  )
}
