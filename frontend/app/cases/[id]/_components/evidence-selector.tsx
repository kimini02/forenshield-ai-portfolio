import { ChevronRight, Play } from "lucide-react"

import { EvidenceHlsStatusThumbnail } from "@/components/evidence-hls-status-thumbnail"
import { Badge } from "@/components/ui/badge"
import type { CaseEvidenceSummary } from "@/lib/api/evidence-detail"
import { cn } from "@/lib/utils"

type EvidenceSelectorProps = {
  evidences: CaseEvidenceSummary[]
  selectedEvidenceId: number | null
  onSelect: (id: number) => void
  getStatusLabel: (status: string) => string
  normalizeStatus: (status: string) => string
}

export function EvidenceSelector({
  evidences,
  selectedEvidenceId,
  onSelect,
  getStatusLabel,
  normalizeStatus,
}: EvidenceSelectorProps) {
  return (
    <aside className="rounded-xl border border-border bg-card p-4 shadow-sm xl:sticky xl:top-28">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-bold text-foreground">증거 파일</h2>
        <Badge variant="secondary" className="rounded-full px-3 font-bold">
          {evidences.length}개
        </Badge>
      </div>

      {evidences.length === 0 ? (
        <div className="mt-5 rounded-lg border border-dashed border-border px-4 py-10 text-center text-xs font-bold text-muted-foreground">
          연결된 증거가 없습니다.
        </div>
      ) : (
        <div className="mt-5 space-y-3">
          {evidences.map((evidence) => {
            const active = evidence.evidenceId === selectedEvidenceId
            const completed = normalizeStatus(evidence.analysisStatus) === "COMPLETED"
            return (
              <button
                key={evidence.evidenceId}
                type="button"
                onClick={() => onSelect(evidence.evidenceId)}
                className={cn(
                  "group w-full overflow-hidden rounded-lg border text-left transition-colors",
                  active
                    ? "border-teal-400 bg-teal-50 shadow-sm dark:bg-teal-950/20"
                    : "border-border bg-card hover:bg-muted/30"
                )}
              >
                <div className="flex gap-3 p-3">
                  <EvidenceThumbnail evidence={evidence} active={active} />
                  <div className="min-w-0 flex-1 py-1">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-foreground">
                          {evidence.fileName}
                        </p>
                        <p className="mt-1 font-mono text-[11px] font-bold text-muted-foreground">EVD-{evidence.evidenceId}</p>
                      </div>
                      <ChevronRight className={cn("mt-0.5 size-4 shrink-0", active ? "text-teal-600" : "text-muted-foreground/40")} />
                    </div>
                    <div className="mt-3 flex items-center justify-between gap-2">
                      <span className={cn("inline-flex items-center gap-1.5 text-xs font-bold", completed ? "text-teal-600" : "text-muted-foreground")}>
                        <span className={cn("size-2 rounded-full", completed ? "bg-teal-500" : "bg-muted-foreground/30")} />
                        {completed ? "분석 완료" : getStatusLabel(evidence.analysisStatus)}
                      </span>
                      <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] font-semibold text-muted-foreground">
                        VIDEO
                      </span>
                    </div>
                  </div>
                </div>
              </button>
            )
          })}
        </div>
      )}
    </aside>
  )
}

function EvidenceThumbnail({ evidence, active }: { evidence: CaseEvidenceSummary; active: boolean }) {
  if (evidence.thumbnailUrl) {
    return (
      <span
        className={cn(
          "relative flex aspect-video w-24 shrink-0 overflow-hidden rounded-md border border-border bg-muted",
          active && "ring-2 ring-teal-400 ring-offset-2 ring-offset-background"
        )}
      >
        <img
          src={evidence.thumbnailUrl}
          alt={`${evidence.fileName} 썸네일`}
          className="size-full object-cover"
        />
        <span className="absolute left-1/2 top-1/2 flex size-8 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-background/90 text-foreground shadow-sm">
          <Play className="ml-0.5 size-4 fill-current" aria-hidden="true" />
        </span>
      </span>
    )
  }

  if (evidence.mediaType === "VIDEO") {
    return <EvidenceHlsStatusThumbnail hlsStatus={evidence.hlsStatus} active={active} />
  }

  const evidenceId = evidence.evidenceId
  const tone = evidenceId % 3
  const backgroundClass =
    tone === 0
      ? "from-slate-950 via-slate-800 to-teal-900"
      : tone === 1
        ? "from-slate-950 via-blue-950 to-slate-700"
        : "from-slate-900 via-slate-700 to-emerald-900"

  return (
    <span
      className={cn(
        "relative flex aspect-video w-24 shrink-0 overflow-hidden rounded-md bg-gradient-to-br shadow-inner",
        backgroundClass,
        active && "ring-2 ring-teal-400 ring-offset-2 ring-offset-background"
      )}
    >
      <span className="absolute inset-x-2 top-2 h-1 rounded-full bg-white/15" />
      <span className="absolute bottom-2 left-2 right-2 grid grid-cols-4 gap-1">
        <span className="h-1 rounded-full bg-white/25" />
        <span className="h-1 rounded-full bg-white/15" />
        <span className="h-1 rounded-full bg-white/20" />
        <span className="h-1 rounded-full bg-white/10" />
      </span>
      <span className="absolute left-1/2 top-1/2 flex size-8 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-white/90 text-slate-900 shadow-sm">
        <Play className="ml-0.5 size-4 fill-current" aria-hidden="true" />
      </span>
    </span>
  )
}
