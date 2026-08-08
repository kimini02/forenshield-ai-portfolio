"use client"

import { AlertCircle, FileVideo, KeyRound, Loader2, Maximize2, Pause, Play, Volume2, VolumeX } from "lucide-react"
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react"

import { useHlsPlayback } from "@/hooks/use-hls-playback"
import { getHlsStatusMessage, type HlsPlayback } from "@/lib/hls-playback"
import { cn } from "@/lib/utils"

export type ProtectedSecurityEvent = {
  eventType: "PRINT_SCREEN" | "SCREEN_CAPTURE_SHORTCUT"
  detail: string
}

type ProtectedEvidencePlayerProps = {
  /** 직접 URL 재생 (오버레이 등) */
  src?: string | null
  /** HLS 암호화 재생 (원본 증거) */
  playback?: HlsPlayback | null
  /** 직접 재생 실패 시 새 탭 열기 링크 */
  fallbackOpenUrl?: string | null
  videoRef?: { current: HTMLVideoElement | null }
  objectFit?: "cover" | "contain"
  children?: ReactNode
  onSecurityEvent?: (event: ProtectedSecurityEvent) => void
  onReauthenticate?: () => Promise<void>
}

export function ProtectedEvidencePlayer({
  src,
  playback,
  fallbackOpenUrl,
  videoRef,
  objectFit = "cover",
  children,
  onSecurityEvent,
  onReauthenticate,
}: ProtectedEvidencePlayerProps) {
  const playerRef = useRef<HTMLDivElement | null>(null)
  const internalVideoRef = useRef<HTMLVideoElement | null>(null)
  const captureAlertTimerRef = useRef<number | undefined>(undefined)
  const [playing, setPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [muted, setMuted] = useState(false)
  const [captureAlert, setCaptureAlert] = useState(false)
  const [srcLoadFailed, setSrcLoadFailed] = useState(false)
  const [reauthenticating, setReauthenticating] = useState(false)

  const useHls = Boolean(playback) && !src

  const { ready: hlsReady, loading: hlsLoading, failed: hlsFailed, setVideoElement: bindHlsVideo } =
    useHlsPlayback(useHls ? playback : null, { enabled: useHls })

  const hasSource = useHls ? hlsReady : Boolean(src)
  const loadFailed = useHls ? hlsFailed : srcLoadFailed
  const progress = duration > 0 ? Math.min(100, Math.max(0, (currentTime / duration) * 100)) : 0

  const showCaptureAlert = useCallback(
    (event: ProtectedSecurityEvent) => {
      setCaptureAlert(true)
      window.clearTimeout(captureAlertTimerRef.current)
      captureAlertTimerRef.current = window.setTimeout(() => setCaptureAlert(false), 4000)
      onSecurityEvent?.(event)
    },
    [onSecurityEvent]
  )

  useEffect(() => {
    if (!useHls) {
      bindHlsVideo(null)
    }
  }, [useHls, bindHlsVideo])

  useEffect(() => {
    setSrcLoadFailed(false)
    setPlaying(false)
    setCurrentTime(0)
    setDuration(0)
    if (!useHls) {
      const video = internalVideoRef.current
      if (video) {
        video.load()
      }
    }
  }, [src, playback?.streamToken, playback?.hlsStatus, useHls])

  useEffect(() => {
    function handleKeyUp(event: KeyboardEvent) {
      if (event.key !== "PrintScreen") return
      showCaptureAlert({
        eventType: "PRINT_SCREEN",
        detail: "PrintScreen 키 입력 감지",
      })
      void navigator.clipboard
        ?.writeText("ForenShield AI: 증거 화면 캡처가 감지되어 열람 기록이 남습니다.")
        .catch(() => undefined)
    }

    function handleKeyDown(event: KeyboardEvent) {
      const key = event.key.toLowerCase()
      const isMacScreenshotShortcut =
        event.metaKey && event.shiftKey && (key === "3" || key === "4" || key === "5")
      const isBrowserScreenshotShortcut = event.ctrlKey && event.shiftKey && key === "s"

      if (!isMacScreenshotShortcut && !isBrowserScreenshotShortcut) return

      showCaptureAlert({
        eventType: "SCREEN_CAPTURE_SHORTCUT",
        detail: isMacScreenshotShortcut ? "macOS 화면 캡처 단축키 감지" : "브라우저 화면 캡처 단축키 감지",
      })
    }

    window.addEventListener("keydown", handleKeyDown)
    window.addEventListener("keyup", handleKeyUp)
    return () => {
      window.removeEventListener("keydown", handleKeyDown)
      window.removeEventListener("keyup", handleKeyUp)
      window.clearTimeout(captureAlertTimerRef.current)
    }
  }, [showCaptureAlert])

  function setVideoElement(element: HTMLVideoElement | null) {
    internalVideoRef.current = element
    if (useHls) {
      bindHlsVideo(element)
    }
    if (videoRef) {
      videoRef.current = element
    }
  }

  function togglePlay() {
    const video = internalVideoRef.current
    if (!video) return

    if (video.paused) {
      void video.play()
    } else {
      video.pause()
    }
  }

  function seekTo(value: string) {
    const video = internalVideoRef.current
    if (!video) return

    const nextTime = Number(value)
    video.currentTime = nextTime
    setCurrentTime(nextTime)
  }

  function toggleMuted() {
    const video = internalVideoRef.current
    if (!video) return

    video.muted = !video.muted
    setMuted(video.muted)
  }

  async function handleReauthenticate() {
    if (!onReauthenticate || reauthenticating) return
    setReauthenticating(true)
    try {
      await onReauthenticate()
    } catch {
      // The step-up dialog owns its cancellation and error state.
    } finally {
      setReauthenticating(false)
    }
  }

  const controlButtonClassName =
    "flex size-6 shrink-0 items-center justify-center rounded-full text-white transition-colors hover:bg-white/15 active:bg-white/20 sm:size-7"

  if (useHls && !hlsReady && !hlsFailed) {
    return (
      <div className="relative flex size-full flex-col items-center justify-center bg-slate-950 px-4 text-center text-sm font-medium text-white/70">
        <FileVideo className="mb-3 size-8 text-white/40" aria-hidden="true" />
        <p>{getHlsStatusMessage(playback?.hlsStatus)}</p>
      </div>
    )
  }

  if (!hasSource || loadFailed) {
    return (
      <div className="relative flex size-full flex-col items-center justify-center bg-slate-950 px-4 text-center text-sm font-bold text-white/60">
        <FileVideo className="mb-3 size-8" aria-hidden="true" />
        {loadFailed ? (
          useHls ? (
            <>
              <p>세션이 만료되어 재생할 수 없습니다</p>
              <p className="mt-2 text-xs font-semibold text-white/45">
                재인증 후 자동으로 이어서 재생됩니다
              </p>
              {onReauthenticate ? (
                <button
                  type="button"
                  className="mt-4 inline-flex h-9 items-center gap-2 rounded-md border border-white/25 px-3 text-xs font-bold text-white transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={reauthenticating}
                  onClick={() => void handleReauthenticate()}
                >
                  {reauthenticating ? (
                    <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
                  ) : (
                    <KeyRound className="size-3.5" aria-hidden="true" />
                  )}
                  비밀번호 재인증
                </button>
              ) : null}
            </>
          ) : (
            <>
              미리보기 가능한 영상을 불러오지 못했습니다.
              {fallbackOpenUrl ? (
                <a
                  href={fallbackOpenUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-3 text-xs font-bold text-teal-300 underline underline-offset-2 hover:text-teal-200"
                >
                  새 탭에서 열기
                </a>
              ) : null}
            </>
          )
        ) : (
          "미리보기 가능한 영상이 없습니다."
        )}
      </div>
    )
  }

  return (
    <div ref={playerRef} className="relative size-full overflow-hidden bg-slate-950">
      <video
        key={useHls ? playback?.streamToken ?? "hls" : src ?? "direct"}
        ref={setVideoElement}
        src={useHls ? undefined : (src ?? undefined)}
        playsInline
        preload={useHls ? "metadata" : "auto"}
        controlsList="nodownload"
        disablePictureInPicture
        className={cn("absolute inset-0 size-full", objectFit === "cover" ? "object-cover" : "object-contain")}
        onClick={togglePlay}
        onDoubleClick={() => requestProtectedFullscreen(playerRef.current)}
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onError={() => {
          if (!useHls) {
            setPlaying(false)
            setSrcLoadFailed(true)
          }
        }}
        onLoadedMetadata={(event) => setDuration(event.currentTarget.duration || 0)}
        onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
        onVolumeChange={(event) => setMuted(event.currentTarget.muted)}
      />
      {children}
      {hlsLoading ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/35">
          <Loader2 className="size-8 animate-spin text-white/80" aria-hidden="true" />
        </div>
      ) : null}
      {captureAlert ? (
        <div className="absolute left-1/2 top-3 z-40 flex -translate-x-1/2 items-center gap-2 whitespace-nowrap rounded-full bg-red-600/90 px-4 py-2 text-xs font-bold text-white shadow-lg">
          <AlertCircle className="size-4" aria-hidden="true" />
          화면 캡처가 감지되었습니다 · 열람 기록이 남습니다
        </div>
      ) : null}
      <div className="absolute inset-x-0 bottom-0 z-30 bg-gradient-to-t from-black/85 via-black/45 to-transparent px-2 pb-1.5 pt-8 text-white sm:px-2.5 sm:pb-2 sm:pt-10">
        <div className="relative mb-1 h-3 sm:h-3.5">
          <input
            type="range"
            min={0}
            max={duration || 0}
            step="0.05"
            value={Math.min(currentTime, duration || 0)}
            onChange={(event) => seekTo(event.target.value)}
            className="absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 cursor-pointer appearance-none rounded-full bg-transparent accent-red-700 [&::-moz-range-thumb]:size-2.5 [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:border-0 [&::-moz-range-thumb]:bg-white [&::-webkit-slider-thumb]:size-2.5 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-white [&::-webkit-slider-thumb]:shadow"
            style={{
              background: `linear-gradient(to right, #dc2626 0%, #dc2626 ${progress}%, rgba(255,255,255,0.32) ${progress}%, rgba(255,255,255,0.32) 100%)`,
            }}
            aria-label="재생 위치"
          />
        </div>
        <div className="flex items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-0.5 rounded-full bg-black/55 px-1 py-0.5 shadow-lg backdrop-blur-md">
            <button
              type="button"
              className="flex size-7 shrink-0 items-center justify-center rounded-full bg-white text-slate-950 shadow-sm transition-transform hover:scale-105 active:scale-95 sm:size-8"
              aria-label={playing ? "일시정지" : "재생"}
              onClick={togglePlay}
            >
              {playing ? (
                <Pause className="size-3 fill-current sm:size-3.5" aria-hidden="true" />
              ) : (
                <Play className="ml-0.5 size-3 fill-current sm:size-3.5" aria-hidden="true" />
              )}
            </button>
            <button
              type="button"
              className={controlButtonClassName}
              aria-label={muted ? "음소거 해제" : "음소거"}
              onClick={toggleMuted}
            >
              {muted ? (
                <VolumeX className="size-3 sm:size-3.5" aria-hidden="true" />
              ) : (
                <Volume2 className="size-3 sm:size-3.5" aria-hidden="true" />
              )}
            </button>
            <span className="whitespace-nowrap rounded-full bg-white/10 px-1.5 py-0.5 font-mono text-[9px] font-bold tabular-nums text-white shadow-inner sm:text-[10px]">
              {formatVideoClock(currentTime)} / {formatVideoClock(duration)}
            </span>
          </div>
          <div className="flex shrink-0 items-center rounded-full bg-black/55 px-1 py-0.5 shadow-lg backdrop-blur-md">
            <button
              type="button"
              className={controlButtonClassName}
              aria-label="워터마크 포함 확대"
              onClick={() => requestProtectedFullscreen(playerRef.current)}
            >
              <Maximize2 className="size-3 sm:size-3.5" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function requestProtectedFullscreen(element: HTMLElement | null) {
  if (!element) return

  const fullscreenDocument = document as Document & {
    webkitFullscreenElement?: Element | null
    webkitExitFullscreen?: () => Promise<void> | void
  }
  const fullscreenElement = document.fullscreenElement ?? fullscreenDocument.webkitFullscreenElement
  if (fullscreenElement === element) {
    const exitFullscreen = document.exitFullscreen ?? fullscreenDocument.webkitExitFullscreen
    void exitFullscreen?.call(fullscreenDocument)
    return
  }

  const fullscreenTarget = element as HTMLElement & {
    webkitRequestFullscreen?: () => Promise<void> | void
  }
  const requestFullscreen = fullscreenTarget.requestFullscreen ?? fullscreenTarget.webkitRequestFullscreen
  void requestFullscreen?.call(fullscreenTarget)
}

function formatVideoClock(value: number) {
  if (!Number.isFinite(value) || value <= 0) return "0:00"

  const minutes = Math.floor(value / 60)
  const seconds = Math.floor(value % 60)
  return `${minutes}:${String(seconds).padStart(2, "0")}`
}
