import { API_BASE_URL } from "@/lib/api/config"
import { getToken, touchSessionExpiryThrottled } from "@/lib/auth"
import { resolveStepUpHeaderValue, STEP_UP_HEADER } from "@/lib/api/step-up-auth"

export type HlsStatus = "PENDING" | "PACKAGING" | "READY" | "FAILED"

export type HlsPlayback = {
  manifestPath: string
  hlsStatus: HlsStatus | string
  streamToken: string
  expiresIn: number
}

export function isHlsReady(playback: HlsPlayback | null | undefined): playback is HlsPlayback {
  return playback?.hlsStatus === "READY" && Boolean(playback.streamToken) && Boolean(playback.manifestPath)
}

export function buildHlsManifestUrl(playback: HlsPlayback): string {
  const path = playback.manifestPath.startsWith("/")
    ? playback.manifestPath
    : `/${playback.manifestPath}`
  const params = new URLSearchParams({ streamToken: playback.streamToken })
  return `${API_BASE_URL}${path}?${params.toString()}`
}

export function getHlsStatusMessage(status: HlsStatus | string | null | undefined): string {
  switch (status) {
    case "PENDING":
      return "재생 준비 중입니다."
    case "PACKAGING":
      return "영상 패키징 중입니다. 잠시 후 다시 시도해 주세요."
    case "FAILED":
      return "재생 준비에 실패했습니다."
    case "READY":
      return "재생 정보를 불러오는 중입니다."
    default:
      return "재생을 준비할 수 없습니다."
  }
}

/** hls.js xhrSetup — manifest·key·segment 요청에 JWT·step-up 헤더 부착 */
export function applyHlsRequestHeaders(xhr: XMLHttpRequest, url: string): void {
  // HLS 재생도 세션 사용으로 보고 유휴 만료를 스로틀 연장한다.
  touchSessionExpiryThrottled()

  const token = getToken()
  if (token) {
    xhr.setRequestHeader("Authorization", `Bearer ${token}`)
  }

  if (url.includes("/hls/key")) {
    const stepUpToken = resolveStepUpHeaderValue()
    if (stepUpToken) {
      xhr.setRequestHeader(STEP_UP_HEADER, stepUpToken)
    }
  }
}
