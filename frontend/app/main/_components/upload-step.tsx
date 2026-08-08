import type { DragEvent, ReactNode, RefObject } from "react"
import {
  AlertTriangle,
  CheckCircle2,
  FileVideo,
  History,
  ShieldCheck,
  UploadCloud,
  X,
} from "lucide-react"

import type { SelectedEvidence } from "./analysis-request-flow"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

type UploadStepProps = {
  evidences: SelectedEvidence[]
  activeEvidenceIndex: number
  caseName: string
  uploadMessage: { type: "success" | "error"; text: string } | null
  totalSizeLabel: string
  metadataPreview: ReactNode
  fileInputRef: RefObject<HTMLInputElement | null>
  onDrop: (event: DragEvent<HTMLDivElement>) => void
  onFileChange: (files: FileList | null) => void
  onCaseNameChange: (value: string) => void
  onRemoveFile: (index: number) => void
  onSelectEvidence: (index: number) => void
  onStart: () => void
}

export function UploadStep({
  evidences,
  activeEvidenceIndex,
  caseName,
  uploadMessage,
  totalSizeLabel,
  metadataPreview,
  fileInputRef,
  onDrop,
  onFileChange,
  onCaseNameChange,
  onRemoveFile,
  onSelectEvidence,
  onStart,
}: UploadStepProps) {
  const hasEvidence = evidences.length > 0

  return (
    <>
      <div className="pt-2">
        <div className="inline-flex items-center gap-2 rounded-full border border-cyan-200 bg-cyan-50 px-3 py-1 text-[11px] font-bold text-cyan-700 dark:border-cyan-500/25 dark:bg-cyan-500/10 dark:text-cyan-300">
          <span className="relative flex size-3 items-center justify-center">
            <span className="absolute size-3 rounded-full bg-cyan-400/30" />
            <span className="size-1.5 rounded-full bg-cyan-600" />
          </span>
          포렌식 분석 시스템 · 정상 운영 중
        </div>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-950 dark:text-foreground">
          디지털 미디어 위변조 분석 대시보드
        </h1>
        <p className="mt-4 max-w-2xl text-sm leading-6 text-slate-500 dark:text-muted-foreground">
          첨단 AI 모델을 활용하여 미디어 증거물의 무결성을 검증합니다. 업로드된 파일은 안전하게 분석되며, 결과 보고서를 즉시 확인할 수 있습니다.
        </p>
      </div>

      <div className="space-y-3">
        <h2 className="flex items-center gap-2 text-xl font-bold text-slate-950 dark:text-foreground">
          <History className="size-5 text-cyan-600" aria-hidden="true" />
          분석 기록 및 새 분석 시작
        </h2>
        <div className="h-px bg-slate-200 dark:bg-border" />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="rounded-xl border border-slate-200 bg-white p-7 shadow-sm dark:border-border dark:bg-card">
          <div>
            <h3 className="text-xl font-bold text-slate-950 dark:text-foreground">증거 파일 업로드</h3>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500 dark:text-muted-foreground">
              분석할 영상 파일을 업로드하면 SHA-256 해시와 메타데이터가 서버에 저장됩니다.
            </p>
          </div>

          <label htmlFor="caseName" className="mt-7 block text-sm font-bold text-slate-600 dark:text-muted-foreground">
            사건명 <span className="text-red-500">*</span>
          </label>
          <input
            id="caseName"
            value={caseName}
            onChange={(event) => onCaseNameChange(event.target.value)}
            placeholder="예: 2026-서울-0123 딥페이크 유포 사건"
            className="mt-2 h-11 w-full max-w-md rounded-lg border border-slate-200 bg-white px-4 text-sm font-medium text-slate-700 outline-none transition-colors placeholder:text-slate-400 focus:border-cyan-300 focus:ring-4 focus:ring-cyan-100 dark:border-border dark:bg-card dark:text-foreground dark:placeholder:text-muted-foreground"
          />
          <p className="mt-2 text-xs font-medium text-slate-500 dark:text-muted-foreground">
            업로드와 분석 시작에 사건명이 필요하며, 내 분석 기록에 등록됩니다.
          </p>

          <div
            role="button"
            tabIndex={0}
            onClick={() => fileInputRef.current?.click()}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                fileInputRef.current?.click()
              }
            }}
            onDragOver={(event) => event.preventDefault()}
            onDrop={onDrop}
            className="mt-6 flex min-h-56 cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 bg-slate-50/60 px-6 py-8 text-center transition-colors hover:border-cyan-300 hover:bg-cyan-50/40 dark:border-border dark:bg-muted/40 dark:hover:border-cyan-500/40 dark:hover:bg-cyan-500/10"
          >
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept="video/*"
              className="sr-only"
              onChange={(event) => onFileChange(event.target.files)}
            />

            <div className="flex size-14 items-center justify-center rounded-full bg-cyan-100 text-cyan-700 ring-1 ring-cyan-200 dark:bg-cyan-500/15 dark:text-cyan-300 dark:ring-cyan-500/25">
              <UploadCloud className="size-7" aria-hidden="true" />
            </div>
            <p className="mt-4 text-sm font-bold text-slate-700 dark:text-foreground">
              {hasEvidence ? "파일을 추가로 업로드하려면 클릭하세요" : "파일을 이곳에 드래그하거나 클릭하여 선택하세요"}
            </p>
            <p className="mt-1 text-xs font-medium text-slate-400 dark:text-muted-foreground">
              용량 제한 없음 · MP4, MOV 여러 개 선택 가능
            </p>
          </div>

          <section className="mt-5 rounded-xl border border-slate-200 bg-slate-50/70 p-4 dark:border-border dark:bg-muted/30">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <FileVideo className="size-4 text-cyan-600" aria-hidden="true" />
                <h4 className="text-sm font-bold text-slate-800 dark:text-foreground">선택된 파일</h4>
              </div>
              <span className="text-xs font-bold text-slate-400 dark:text-muted-foreground">
                {hasEvidence ? `${evidences.length}개 · ${totalSizeLabel}` : "0개"}
              </span>
            </div>

            {hasEvidence ? (
              <div className="mt-3 grid gap-2">
                {evidences.map((item, index) => (
                  <div
                    key={`${item.name}-${index}`}
                    role="button"
                    tabIndex={0}
                    onClick={() => onSelectEvidence(index)}
                    onKeyDown={(event) => {
                      if (event.key !== "Enter" && event.key !== " ") return
                      onSelectEvidence(index)
                    }}
                    className={cn(
                      "flex items-center justify-between gap-3 rounded-lg border bg-white dark:bg-card px-3 py-3 text-left transition-colors",
                      index === activeEvidenceIndex
                        ? "border-cyan-300 bg-cyan-50 dark:border-cyan-500/40 dark:bg-cyan-500/10"
                        : "border-slate-200 dark:border-border hover:border-cyan-200 hover:bg-white dark:hover:bg-card"
                    )}
                  >
                    <span className="flex min-w-0 items-center gap-3">
                      <span
                        className={cn(
                          "flex size-9 shrink-0 items-center justify-center rounded-lg",
                          index === activeEvidenceIndex
                            ? "bg-cyan-100 text-cyan-700 dark:bg-cyan-500/15 dark:text-cyan-300"
                            : "bg-slate-100 dark:bg-muted text-slate-500 dark:text-muted-foreground"
                        )}
                      >
                        <FileVideo className="size-4" aria-hidden="true" />
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate text-xs font-bold text-slate-700 dark:text-foreground">
                          {item.name}
                        </span>
                        <span className="text-[11px] font-semibold text-slate-400 dark:text-muted-foreground">
                          {item.sizeLabel} · {item.extension}
                        </span>
                      </span>
                    </span>
                    <button
                      type="button"
                      onClick={(event) => {
                        event.stopPropagation()
                        onRemoveFile(index)
                      }}
                      className="inline-flex size-7 shrink-0 items-center justify-center rounded text-slate-400 dark:text-muted-foreground transition-colors hover:bg-red-50 hover:text-red-500"
                      aria-label={`${item.name} 제거`}
                    >
                      <X className="size-3.5" aria-hidden="true" />
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="mt-3 rounded-lg border border-dashed border-slate-200 bg-white px-4 py-5 text-center text-xs font-semibold text-slate-400 dark:border-border dark:bg-card dark:text-muted-foreground">
                아직 선택된 영상 파일이 없습니다.
              </div>
            )}
          </section>

          {uploadMessage ? (
            <div
              className={cn(
                "mt-5 flex items-start gap-2 rounded-lg border px-4 py-3 text-xs font-bold",
                uploadMessage.type === "success"
                  ? "border-slate-200 bg-slate-50 text-slate-700 dark:border-border dark:bg-card dark:text-foreground"
                  : "border-red-200 bg-red-50 text-red-600 dark:border-red-500/25 dark:bg-red-500/10 dark:text-red-400"
              )}
            >
              {uploadMessage.type === "success" ? (
                <CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              ) : (
                <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              )}
              <span>{uploadMessage.text}</span>
            </div>
          ) : null}

          <div className="mt-6 flex items-center gap-2 text-xs font-medium text-slate-500 dark:text-muted-foreground">
            <ShieldCheck className="size-4 shrink-0" aria-hidden="true" />
            보안: 모든 데이터는 암호화되어 처리됩니다.
          </div>

          <Button
            onClick={onStart}
            disabled={!hasEvidence || !caseName.trim()}
            title={
              !hasEvidence
                ? "영상 파일 업로드가 완료되면 분석 요청을 시작할 수 있습니다."
                : !caseName.trim()
                  ? "사건명을 입력하면 분석 요청을 시작할 수 있습니다."
                  : undefined
            }
            className="mt-5 h-11 w-full rounded-lg bg-teal-600 text-sm font-bold hover:bg-teal-700"
          >
            분석 시작하기
          </Button>
        </div>

        <div className="space-y-5">{metadataPreview}</div>
      </div>
    </>
  )
}
