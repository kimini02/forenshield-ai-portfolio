"use client"

import Hls from "hls.js"
import { useEffect, useState } from "react"

import { API_FETCH_CREDENTIALS } from "@/lib/api/interceptor"
import {
  applyHlsRequestHeaders,
  buildHlsManifestUrl,
  type HlsPlayback,
} from "@/lib/hls-playback"

type UseHlsPlaybackOptions = {
  enabled?: boolean
  onError?: () => void
}

export function useHlsPlayback(
  playback: HlsPlayback | null | undefined,
  options: UseHlsPlaybackOptions = {}
) {
  const { enabled = true, onError } = options
  const [videoElement, setVideoElement] = useState<HTMLVideoElement | null>(null)
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)

  const ready =
    enabled &&
    playback?.hlsStatus === "READY" &&
    Boolean(playback.streamToken) &&
    Boolean(playback.manifestPath)

  useEffect(() => {
    if (!ready || !playback || !videoElement) {
      setLoading(false)
      if (!ready) setFailed(false)
      return
    }

    if (!Hls.isSupported()) {
      setFailed(true)
      onError?.()
      return
    }

    const manifestUrl = buildHlsManifestUrl(playback)
    let cancelled = false
    const hls = new Hls({
      enableWorker: true,
      xhrSetup: (xhr, url) => {
        xhr.withCredentials = API_FETCH_CREDENTIALS === "include"
        applyHlsRequestHeaders(xhr, url)
      },
    })

    setFailed(false)
    setLoading(true)

    function handleFailure() {
      if (cancelled) return
      setLoading(false)
      setFailed(true)
      onError?.()
    }

    hls.attachMedia(videoElement)
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      if (!cancelled) setLoading(false)
    })
    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (cancelled || !data.fatal) return
      handleFailure()
    })
    hls.loadSource(manifestUrl)

    return () => {
      cancelled = true
      hls.destroy()
      videoElement.removeAttribute("src")
      videoElement.load()
    }
  }, [ready, playback, videoElement, onError])

  return {
    ready,
    loading,
    failed,
    setVideoElement,
  }
}
