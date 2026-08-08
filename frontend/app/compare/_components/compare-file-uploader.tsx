import type { DragEvent, RefObject } from "react"
import { AlertTriangle, ArrowLeft, ArrowRight, ArrowRightLeft, FileVideo, UploadCloud, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import type { SourceEvidence, UploadedCompareFile } from "./compare-verification-flow"
import { SourceEvidenceMediaPreview } from "./source-evidence-media-preview"

type CompareFileUploaderProps = {
  sourceEvidence: SourceEvidence
  compareFile: UploadedCompareFile | null
  fileInputRef: RefObject<HTMLInputElement | null>
  onDrop: (event: DragEvent<HTMLDivElement>) => void
  onFileChange: (files: FileList | null) => void
  onRemoveFile: () => void
  onBack: () => void
  onStart: () => void
  compareError: string | null
}

export function CompareFileUploader({
  sourceEvidence,
  compareFile,
  fileInputRef,
  onDrop,
  onFileChange,
  onRemoveFile,
  onBack,
  onStart,
  compareError,
}: CompareFileUploaderProps) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white shadow-none dark:border-border dark:bg-card">
      <div className="border-b border-slate-200 px-4 py-5 dark:border-border sm:px-6">
        <h1 className="text-lg font-bold text-slate-950 dark:text-foreground">비교 대상 파일 업로드</h1>
        <p className="mt-1 text-sm font-medium text-slate-500">
          기준 증거와 비교할 파일을 업로드하세요. 기준 증거는 변경되지 않습니다.
        </p>
      </div>

      <div className="space-y-4 px-4 py-5 sm:px-6">
        {compareError ? (
          <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-xs font-bold text-red-600 dark:border-red-500/25 dark:bg-red-500/10 dark:text-red-300">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {compareError}
          </div>
        ) : null}

        <div className="grid items-stretch gap-3 lg:grid-cols-[minmax(0,1fr)_44px_minmax(0,1fr)]">
          <SourceEvidenceCard evidence={sourceEvidence} />

          <div className="hidden items-center justify-center text-slate-400 lg:flex">
            <ArrowRightLeft className="size-5" aria-hidden="true" />
          </div>

          {compareFile ? (
            <div className="flex flex-col rounded-xl border border-slate-200 bg-white p-4 dark:border-border dark:bg-card">
              <p className="text-xs font-bold text-slate-400">비교 대상 (제출본)</p>
              <div className="mt-3 flex min-w-0 flex-1 items-start gap-3">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-600 dark:bg-secondary dark:text-muted-foreground">
                  <FileVideo className="size-5" aria-hidden="true" />
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-sm font-bold text-slate-950 dark:text-foreground">
                    비교 대상 파일
                  </span>
                  <span className="mt-1 block text-xs font-semibold text-slate-500">{compareFile.sizeLabel}</span>
                </span>
              </div>
              <button
                type="button"
                onClick={onRemoveFile}
                className="mt-3 inline-flex w-fit items-center gap-1.5 text-xs font-bold text-slate-500 transition-colors hover:text-red-500"
              >
                <X className="size-3.5" aria-hidden="true" />
                파일 제거
              </button>
            </div>
          ) : (
            <div
              role="button"
              tabIndex={0}
              onClick={() => fileInputRef.current?.click()}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") fileInputRef.current?.click()
              }}
              onDragOver={(event) => event.preventDefault()}
              onDrop={onDrop}
              className={cn(
                "flex min-h-44 cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed",
                "border-slate-300 bg-slate-50/60 px-6 py-8 text-center transition-colors",
                "hover:border-slate-400 hover:bg-slate-50 dark:border-border dark:bg-background dark:hover:bg-secondary/40"
              )}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept="video/*"
                className="sr-only"
                onChange={(event) => onFileChange(event.target.files)}
              />
              <span className="flex size-11 items-center justify-center rounded-full bg-white text-slate-500 ring-1 ring-slate-200 dark:bg-card dark:ring-border">
                <UploadCloud className="size-6" aria-hidden="true" />
              </span>
              <p className="mt-3 text-sm font-bold text-slate-700 dark:text-foreground">
                파일을 드래그하거나 클릭해서 업로드
              </p>
              <p className="mt-1 text-xs font-medium text-slate-400">MP4, MOV 권장</p>
            </div>
          )}
        </div>
      </div>

      <div className="flex flex-col items-stretch justify-between gap-3 border-t border-slate-200 px-4 py-4 dark:border-border sm:flex-row sm:items-center sm:px-6">
        <Button
          type="button"
          variant="outline"
          onClick={onBack}
          className="h-10 w-full rounded-full border-slate-200 bg-white px-5 text-sm font-semibold text-slate-700 hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground sm:w-auto"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          기준 증거 다시 선택
        </Button>
        <Button
          type="button"
          onClick={onStart}
          disabled={!compareFile}
          className="h-10 w-full rounded-full bg-slate-950 px-6 text-sm font-semibold text-white hover:bg-slate-800 dark:bg-foreground dark:text-background sm:w-auto"
        >
          비교 검증 시작
          <ArrowRight className="size-4" aria-hidden="true" />
        </Button>
      </div>
    </section>
  )
}

export function SourceEvidenceCard({ evidence }: { evidence: SourceEvidence }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4 dark:border-border dark:bg-background">
      <p className="text-xs font-bold text-slate-400">기준 증거 (원본)</p>
      <div className="mt-3 flex min-w-0 items-start gap-3">
        <SourceEvidenceMediaPreview evidence={evidence} className="h-14 w-24 shrink-0" compact />
        <span className="min-w-0">
          <span className="block truncate text-sm font-bold text-slate-950 dark:text-foreground">
            비교검증 기준 증거
          </span>
          <span className="mt-1 block font-mono text-xs font-medium text-slate-400">EVD-{evidence.id}</span>
        </span>
      </div>
    </div>
  )
}
