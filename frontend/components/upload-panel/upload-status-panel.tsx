import { Progress } from "@/components/ui/progress"

type UploadStatusPanelProps = {
  isUploading: boolean
  progress: number
  error: string
}

export function UploadStatusPanel({
  isUploading,
  progress,
  error,
}: UploadStatusPanelProps) {
  if (!isUploading && !error) return null

  return (
    <>
      {isUploading && (
        <div className="space-y-2">
          <div className="flex justify-between text-xs font-medium">
            <span className="text-primary">서버에 파일 업로드 중...</span>
            <span>{Math.round(progress)}%</span>
          </div>
          <Progress value={progress} className="h-2" />
        </div>
      )}

      {error && (
        <p className="text-sm text-destructive">{error}</p>
      )}
    </>
  )
}
