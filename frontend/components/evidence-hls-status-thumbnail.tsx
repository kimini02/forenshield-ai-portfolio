import { FileVideo, Loader2 } from "lucide-react"

import { getHlsStatusMessage, type HlsStatus } from "@/lib/hls-playback"
import { cn } from "@/lib/utils"

type EvidenceHlsStatusThumbnailProps = {
  hlsStatus?: HlsStatus | string | null
  active?: boolean
  className?: string
  showPlayIcon?: boolean
}

export function EvidenceHlsStatusThumbnail({
  hlsStatus,
  active = false,
  className,
  showPlayIcon = true,
}: EvidenceHlsStatusThumbnailProps) {
  const status = hlsStatus ?? "PENDING"
  const isReady = status === "READY"
  const isFailed = status === "FAILED"
  const isPackaging = status === "PACKAGING" || status === "PENDING"

  return (
    <span
      className={cn(
        "relative flex aspect-video w-24 shrink-0 overflow-hidden rounded-md border border-border bg-slate-900",
        active && "ring-2 ring-teal-400 ring-offset-2 ring-offset-background",
        className
      )}
    >
      <span
        className={cn(
          "absolute inset-0 bg-gradient-to-br",
          isFailed
            ? "from-slate-950 via-red-950/40 to-slate-900"
            : isReady
              ? "from-slate-950 via-slate-800 to-teal-900"
              : "from-slate-950 via-slate-800 to-slate-700"
        )}
      />
      <span className="absolute inset-0 flex flex-col items-center justify-center px-1 text-center">
        {isPackaging ? (
          <Loader2 className="size-4 animate-spin text-white/70" aria-hidden="true" />
        ) : (
          <FileVideo className="size-4 text-white/60" aria-hidden="true" />
        )}
        <span className="mt-1 line-clamp-2 text-[9px] font-bold leading-tight text-white/55">
          {isReady ? "재생 가능" : getHlsStatusMessage(status).replace(/\.$/, "")}
        </span>
      </span>
      {showPlayIcon && isReady ? (
        <span className="absolute left-1/2 top-1/2 flex size-7 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-white/90 text-slate-950 shadow-sm">
          <span className="ml-0.5 size-0 border-y-[5px] border-l-[8px] border-y-transparent border-l-slate-950" />
        </span>
      ) : null}
    </span>
  )
}
