"use client"

import { FileVideo, Loader2 } from "lucide-react"
import { type ReactNode } from "react"

import { useHlsPlayback } from "@/hooks/use-hls-playback"
import { getHlsStatusMessage, type HlsPlayback } from "@/lib/hls-playback"
import { cn } from "@/lib/utils"

type EvidenceHlsPlayerProps = {
  playback: HlsPlayback | null | undefined
  objectFit?: "cover" | "contain"
  className?: string
  videoRef?: { current: HTMLVideoElement | null }
  showControls?: boolean
  onError?: () => void
  children?: ReactNode
}

export function EvidenceHlsPlayer({
  playback,
  objectFit = "contain",
  className,
  videoRef,
  showControls = true,
  onError,
  children,
}: EvidenceHlsPlayerProps) {
  const { ready, loading, failed, setVideoElement } = useHlsPlayback(playback, { onError })
  const statusMessage = ready ? null : getHlsStatusMessage(playback?.hlsStatus)

  function attachVideo(element: HTMLVideoElement | null) {
    setVideoElement(element)
    if (videoRef) {
      videoRef.current = element
    }
  }

  if (failed) {
    return (
      <div
        className={cn(
          "relative flex size-full flex-col items-center justify-center bg-slate-950 px-4 text-center text-sm font-bold text-white/60",
          className
        )}
      >
        <FileVideo className="mb-3 size-8" aria-hidden="true" />
        <p>세션이 만료되어 재생할 수 없습니다</p>
        <p className="mt-2 text-xs font-semibold text-white/45">
          재인증 후 자동으로 이어서 재생됩니다
        </p>
      </div>
    )
  }

  if (!ready) {
    return (
      <div
        className={cn(
          "relative flex size-full flex-col items-center justify-center bg-slate-950 px-4 text-center text-sm font-medium text-white/70",
          className
        )}
      >
        <FileVideo className="mb-3 size-8 text-white/40" aria-hidden="true" />
        <p>{statusMessage}</p>
      </div>
    )
  }

  return (
    <div className={cn("relative size-full overflow-hidden bg-slate-950", className)}>
      <video
        ref={attachVideo}
        playsInline
        preload="metadata"
        controls={showControls}
        controlsList="nodownload"
        disablePictureInPicture
        className={cn(
          "absolute inset-0 size-full",
          objectFit === "cover" ? "object-cover" : "object-contain"
        )}
      />
      {loading ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/35">
          <Loader2 className="size-8 animate-spin text-white/80" aria-hidden="true" />
        </div>
      ) : null}
      {children}
    </div>
  )
}
