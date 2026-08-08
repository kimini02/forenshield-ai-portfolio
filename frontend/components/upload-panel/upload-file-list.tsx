import {
  UploadFileCard,
  type UploadFileCardState,
} from "@/components/upload-panel/upload-file-card"

type UploadFileListProps = {
  fileStates: UploadFileCardState[]
  isBusy: boolean
  onRemoveFile: (index: number) => void
}

export function UploadFileList({
  fileStates,
  isBusy,
  onRemoveFile,
}: UploadFileListProps) {
  return (
    <ul className="space-y-2" aria-label="업로드된 파일 목록">
      {fileStates.map((item, index) => (
        <UploadFileCard
          key={`${item.file.name}-${index}`}
          item={item}
          index={index}
          isBusy={isBusy}
          onRemoveFile={onRemoveFile}
        />
      ))}
    </ul>
  )
}
