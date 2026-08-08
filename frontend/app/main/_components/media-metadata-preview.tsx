import { Info, MessageSquareText } from "lucide-react"

import { MediaPreview, VideoPreviewDialog } from "./media-preview"
import type { SelectedEvidence } from "./analysis-request-flow"
import { cn } from "@/lib/utils"

type MetadataPreviewTab = "metadata" | "comment"

type MediaMetadataPreviewContentProps = {
  evidences: SelectedEvidence[]
  activeIndex: number
  activeTab: MetadataPreviewTab
  previewEvidence: SelectedEvidence | null
  onNext: () => void
  onCommentChange: (comment: string) => void
  onTabChange: (tab: MetadataPreviewTab) => void
  onOpenPreview: (evidence: SelectedEvidence) => void
  onClosePreview: () => void
}

export function MediaMetadataPreviewContent({
  evidences,
  activeIndex,
  activeTab,
  previewEvidence,
  onNext,
  onCommentChange,
  onTabChange,
  onOpenPreview,
  onClosePreview,
}: MediaMetadataPreviewContentProps) {
  const evidence = evidences[activeIndex] ?? null

  return (
    <>
      <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-border dark:bg-card">
        <div className="flex items-center justify-between gap-3 border-b border-slate-200 dark:border-border px-5 py-4">
          <div className="flex items-center gap-2">
            <Info className="size-4 text-teal-600" aria-hidden="true" />
            <h2 className="text-base font-bold text-slate-900 dark:text-foreground">파일 메타데이터</h2>
          </div>
          {evidences.length > 1 ? (
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-bold text-slate-400 dark:text-muted-foreground">
                {activeIndex + 1}/{evidences.length}
              </span>
              <button
                type="button"
                onClick={onNext}
                className="flex size-6 items-center justify-center rounded border border-slate-200 text-xs font-bold text-slate-500 transition-colors hover:border-teal-200 hover:bg-teal-50 hover:text-teal-700 dark:border-border dark:text-muted-foreground dark:hover:border-teal-500/30 dark:hover:bg-teal-500/10 dark:hover:text-teal-300"
                aria-label="다음 파일 메타데이터 보기"
              >
                &gt;
              </button>
            </div>
          ) : null}
        </div>
        <div className="px-5 py-5 text-sm font-medium text-slate-500 dark:text-muted-foreground">
          {evidence ? (
            <div className="space-y-4">
              <MediaPreview
                evidence={evidence}
                activeIndex={activeIndex}
                onOpen={() => onOpenPreview(evidence)}
              />

              <div className="grid grid-cols-2 rounded-lg border border-slate-200 bg-slate-50 p-1 dark:border-border dark:bg-muted/40">
                <button
                  type="button"
                  onClick={() => onTabChange("metadata")}
                  className={cn(
                    "h-8 rounded-md text-xs font-bold transition-colors",
                    activeTab === "metadata"
                      ? "bg-white dark:bg-card text-teal-700 shadow-sm"
                      : "text-slate-500 dark:text-muted-foreground hover:text-slate-800 dark:text-foreground"
                  )}
                >
                  메타데이터
                </button>
                <button
                  type="button"
                  onClick={() => onTabChange("comment")}
                  className={cn(
                    "h-8 rounded-md text-xs font-bold transition-colors",
                    activeTab === "comment"
                      ? "bg-white dark:bg-card text-teal-700 shadow-sm"
                      : "text-slate-500 dark:text-muted-foreground hover:text-slate-800 dark:text-foreground"
                  )}
                >
                  코멘트
                </button>
              </div>

              {activeTab === "metadata" ? (
                <dl className="space-y-3">
                  <MetadataRow label="파일명" value={evidence.name} />
                  <MetadataRow label="파일 형식" value={evidence.extension} />
                  <MetadataRow label="MIME 타입" value={evidence.mimeType} />
                  <MetadataRow label="파일 크기" value={evidence.sizeLabel} />
                  <MetadataRow label="해상도" value={evidence.resolutionLabel} />
                  <MetadataRow label="업로드 시간" value={evidence.uploadAtLabel} />
                  <MetadataRow label="SHA-256" value={evidence.hashValue ?? "분석 시작 시 생성"} accent />
                  <MetadataRow label="분석 모델" value="분석 요청 전" />
                </dl>
              ) : (
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-border dark:bg-muted/40">
                  <label
                    htmlFor="evidenceComment"
                    className="flex items-center gap-2 text-xs font-bold text-slate-700 dark:text-foreground"
                  >
                    <MessageSquareText className="size-4 text-teal-600" aria-hidden="true" />
                    파일 코멘트
                  </label>
                  <textarea
                    id="evidenceComment"
                    value={evidence.comment}
                    onChange={(event) => onCommentChange(event.target.value)}
                    placeholder="파일 확인 내용이나 분석 요청 메모를 입력하세요."
                    className="mt-3 min-h-28 w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-3 text-sm font-medium leading-6 text-slate-700 outline-none transition-colors placeholder:text-slate-400 focus:border-teal-300 focus:ring-4 focus:ring-teal-100 dark:border-border dark:bg-card dark:text-foreground dark:placeholder:text-muted-foreground"
                  />
                </div>
              )}
            </div>
          ) : (
            <p>파일을 선택하거나 업로드하면 메타데이터가 여기에 표시됩니다.</p>
          )}
        </div>
      </section>

      {previewEvidence ? (
        <VideoPreviewDialog evidence={previewEvidence} onClose={onClosePreview} />
      ) : null}
    </>
  )
}

export function MetadataRow({
  label,
  value,
  accent = false,
}: {
  label: string
  value: string
  accent?: boolean
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-slate-100 dark:border-border pb-3 last:border-b-0 last:pb-0">
      <dt className="shrink-0 text-xs font-bold text-slate-400 dark:text-muted-foreground">{label}</dt>
      <dd
        className={cn(
          "truncate text-right text-xs font-bold",
          accent ? "text-teal-600" : "text-slate-800 dark:text-foreground"
        )}
      >
        {value}
      </dd>
    </div>
  )
}
