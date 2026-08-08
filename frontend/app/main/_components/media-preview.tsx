import { Maximize2, Play, X } from "lucide-react"

import type { SelectedEvidence } from "./analysis-request-flow"
import { cn } from "@/lib/utils"

type MediaPreviewProps = {
  evidence: SelectedEvidence
  activeIndex: number
  onOpen: () => void
}

export function MediaPreview({ evidence, activeIndex, onOpen }: MediaPreviewProps) {
  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-slate-950 shadow-sm dark:border-border">
      <button
        type="button"
        onClick={onOpen}
        className="relative block aspect-video w-full overflow-hidden text-left"
        aria-label={`${evidence.name} 영상 크게 보기`}
      >
        {evidence.previewUrl ? (
          <video
            src={evidence.previewUrl}
            className="absolute inset-0 size-full object-contain"
            muted
            playsInline
            preload="metadata"
            controls={false}
          />
        ) : (
          <>
            <div
              className={cn(
                "absolute inset-0",
                activeIndex % 3 === 0 &&
                  "bg-[linear-gradient(135deg,#334155_0%,#0f766e_42%,#111827_100%)]",
                activeIndex % 3 === 1 &&
                  "bg-[linear-gradient(135deg,#1e293b_0%,#475569_44%,#0f172a_100%)]",
                activeIndex % 3 === 2 &&
                  "bg-[linear-gradient(135deg,#0f172a_0%,#164e63_46%,#111827_100%)]"
              )}
            />
            <div className="absolute left-5 top-5 h-16 w-24 rounded-sm border border-white/25 bg-white/10" />
            <div className="absolute right-6 top-6 h-28 w-16 rounded-sm border border-white/20 bg-slate-900/25" />
            <div className="absolute bottom-12 left-6 h-16 w-24 rounded-full border border-white/20 bg-emerald-200/15 blur-[1px]" />
          </>
        )}
        <div className="absolute inset-x-0 top-0 flex items-center justify-between bg-black/35 px-3 py-2">
          <span className="truncate text-[11px] font-bold text-white/90">{evidence.name}</span>
          <span className="text-[11px] font-bold text-white/75">{evidence.uploadAtLabel}</span>
        </div>
        <div className="absolute inset-x-0 bottom-0 bg-slate-950/75 px-3 py-2">
          <div className="flex items-center justify-between gap-3 text-white">
            <div className="flex items-center gap-2">
              <Play className="size-4" aria-hidden="true" />
              <span className="text-[10px] font-bold">클릭해서 재생</span>
            </div>
            <div className="flex items-center gap-3 text-white/80">
              <Maximize2 className="size-4" aria-hidden="true" />
            </div>
          </div>
        </div>
      </button>
    </div>
  )
}

type VideoPreviewDialogProps = {
  evidence: SelectedEvidence
  onClose: () => void
}

export function VideoPreviewDialog({ evidence, onClose }: VideoPreviewDialogProps) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4 py-6 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label={`${evidence.name} 영상 미리보기`}
      onClick={onClose}
    >
      <div
        className="w-full max-w-5xl overflow-hidden rounded-xl border border-white/10 bg-slate-950 shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between gap-3 border-b border-white/10 px-4 py-3 text-white">
          <div className="min-w-0">
            <p className="truncate text-sm font-bold">{evidence.name}</p>
            <p className="mt-0.5 text-xs font-semibold text-white/55">
              {evidence.sizeLabel} · {evidence.extension}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex size-9 shrink-0 items-center justify-center rounded-lg text-white/70 transition-colors hover:bg-white/10 hover:text-white"
            aria-label="영상 미리보기 닫기"
          >
            <X className="size-5" aria-hidden="true" />
          </button>
        </div>
        <div className="bg-black">
          {evidence.previewUrl ? (
            <video
              src={evidence.previewUrl}
              className="max-h-[78vh] w-full bg-black"
              controls
              autoPlay
              playsInline
            />
          ) : (
            <div className="flex aspect-video items-center justify-center text-sm font-bold text-white/60">
              미리보기 가능한 영상이 없습니다.
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
