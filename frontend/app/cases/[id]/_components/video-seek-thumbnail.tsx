"use client"

import { useEffect, useState, type RefObject } from "react"

type VideoSeekThumbnailProps = {
  videoRef: RefObject<HTMLVideoElement | null>
  timeSec: number
  imageUrl?: string | null
  heatmapImageUrl?: string | null
  label?: string
}

/**
 * Prefer server image/heatmap; otherwise capture a frame from the playing evidence video.
 */
export function VideoSeekThumbnail({
  videoRef,
  timeSec,
  imageUrl,
  heatmapImageUrl,
  label = "대표 프레임",
}: VideoSeekThumbnailProps) {
  const remote = imageUrl?.trim() || heatmapImageUrl?.trim() || null
  const [captured, setCaptured] = useState<string | null>(null)

  useEffect(() => {
    if (remote) {
      setCaptured(null)
      return
    }

    const source = videoRef.current
    if (!source) return

    let cancelled = false
    const video = document.createElement("video")
    video.muted = true
    video.playsInline = true
    video.preload = "auto"
    video.crossOrigin = source.crossOrigin || "anonymous"

    const src = source.currentSrc || source.src
    if (!src) return
    video.src = src

    const cleanup = () => {
      video.pause()
      video.removeAttribute("src")
      video.load()
    }

    const capture = () => {
      if (cancelled) return
      try {
        const canvas = document.createElement("canvas")
        canvas.width = Math.max(1, video.videoWidth || 640)
        canvas.height = Math.max(1, video.videoHeight || 360)
        const ctx = canvas.getContext("2d")
        if (!ctx) return
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
        const dataUrl = canvas.toDataURL("image/jpeg", 0.82)
        if (!cancelled) setCaptured(dataUrl)
      } catch {
        // CORS / HLS may block canvas — leave placeholder
      } finally {
        cleanup()
      }
    }

    const onSeeked = () => capture()
    const onLoaded = () => {
      const duration = Number.isFinite(video.duration) ? video.duration : timeSec
      const target = Math.max(0, Math.min(timeSec, Math.max(0, duration - 0.05)))
      try {
        video.currentTime = target
      } catch {
        cleanup()
      }
    }

    video.addEventListener("loadeddata", onLoaded, { once: true })
    video.addEventListener("seeked", onSeeked, { once: true })
    video.addEventListener(
      "error",
      () => {
        cleanup()
      },
      { once: true }
    )

    return () => {
      cancelled = true
      video.removeEventListener("loadeddata", onLoaded)
      video.removeEventListener("seeked", onSeeked)
      cleanup()
    }
  }, [remote, timeSec, videoRef])

  const src = remote || captured

  if (src) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img src={src} alt={label} className="size-full object-cover" />
    )
  }

  return (
    <div className="flex size-full items-center justify-center text-xs font-bold text-white/45">{label}</div>
  )
}
