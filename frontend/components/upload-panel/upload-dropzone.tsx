import type { DragEvent, KeyboardEvent, RefObject } from "react"
import { UploadCloud } from "lucide-react"

import { cn } from "@/lib/utils"

type UploadDropzoneProps = {
  inputRef: RefObject<HTMLInputElement | null>
  accept: string
  isBusy: boolean
  isDragging: boolean
  onClick: () => void
  onKeyDown: (event: KeyboardEvent<HTMLDivElement>) => void
  onDragOver: (event: DragEvent<HTMLDivElement>) => void
  onDragLeave: () => void
  onDrop: (event: DragEvent<HTMLDivElement>) => void
  onFileChange: (files: FileList | null) => void
}

export function UploadDropzone({
  inputRef,
  accept,
  isBusy,
  isDragging,
  onClick,
  onKeyDown,
  onDragOver,
  onDragLeave,
  onDrop,
  onFileChange,
}: UploadDropzoneProps) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={onKeyDown}
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
      className={cn(
        "flex cursor-pointer flex-col items-center justify-center gap-4 rounded-lg border-2 border-dashed px-6 py-12 text-center transition-colors",
        isDragging
          ? "border-primary bg-primary/10"
          : "border-border bg-background/40 hover:border-primary/50 hover:bg-accent/40",
        isBusy && "cursor-not-allowed opacity-60"
      )}
    >
      <div className="flex size-14 items-center justify-center rounded-full bg-primary/15 text-primary ring-1 ring-primary/30">
        <UploadCloud className="size-7" aria-hidden="true" />
      </div>
      <div className="space-y-1">
        <p className="text-sm font-medium text-foreground">
          파일을 이곳에 드래그하거나{" "}
          <span className="text-primary underline underline-offset-4">
            클릭하여 선택
          </span>
          하세요
        </p>
        <p className="text-xs text-muted-foreground">
          용량 제한 없음 · MP4, MOV, WAV, MP3, JPG, PNG 지원
        </p>
      </div>
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={accept}
        className="sr-only"
        onChange={(event) => onFileChange(event.target.files)}
        disabled={isBusy}
      />
    </div>
  )
}
