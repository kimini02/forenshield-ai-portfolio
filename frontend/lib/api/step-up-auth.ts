import { apiRequest } from "@/lib/api/client"

const STEP_UP_TOKEN_KEY = "forenshield-step-up-token" // sessionStorage: 토큰 문자열
const STEP_UP_EXPIRES_AT_KEY = "forenshield-step-up-expires-at" // sessionStorage: 만료 시간 (ms)

export const STEP_UP_HEADER = "X-Step-Up-Token" // API 헤더 이름
export const STEP_UP_CHANGE_EVENT = "step-up-change" // 헤더 카운트다운 배지 갱신용 이벤트

export type StepUpVerifyResponse = {
  success: boolean
  stepUpToken: string
  expiresIn: number
}

export type StepUpExtendResponse = {
  success: boolean
  expiresIn: number
}

/** Step-up 연장 가능 임계값(초) — 남은 시간이 이 값 이하일 때만 연장 */
export const STEP_UP_EXTEND_THRESHOLD_SECONDS = 5 * 60

function notifyStepUpChange() {
  if (typeof window === "undefined") return
  window.dispatchEvent(new Event(STEP_UP_CHANGE_EVENT))
}

function readExpiresAt(): number | null {
  if (typeof window === "undefined") return null
  const raw = sessionStorage.getItem(STEP_UP_EXPIRES_AT_KEY)
  if (!raw) return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}

export function getStepUpToken(): string | null {  // 저장된 step-up 토큰 읽기
  if (typeof window === "undefined") return null
  return sessionStorage.getItem(STEP_UP_TOKEN_KEY)
}

export function setStepUpToken(token: string, expiresInMs: number): void {  // 토큰 저장 + 만료 시각 = 지금 + expiresIn 기록 
  if (typeof window === "undefined") return
  sessionStorage.setItem(STEP_UP_TOKEN_KEY, token)
  sessionStorage.setItem(STEP_UP_EXPIRES_AT_KEY, String(Date.now() + expiresInMs))
  notifyStepUpChange()
}

export function clearStepUpToken(): void {  // 토큰 삭제 (만료·로그아웃 시)
  if (typeof window === "undefined") return
  sessionStorage.removeItem(STEP_UP_TOKEN_KEY)
  sessionStorage.removeItem(STEP_UP_EXPIRES_AT_KEY)
  notifyStepUpChange()
}

export function isStepUpValid(): boolean {  // 토큰이 있고 만료 전인지
  const token = getStepUpToken()
  if (!token) return false
  const expiresAt = readExpiresAt()
  return expiresAt !== null && Date.now() < expiresAt
}

export function getStepUpRemainingSeconds(): number {  // 남은 초 (헤더 카운트다운 배지용)
  const expiresAt = readExpiresAt()
  if (expiresAt === null) return 0
  return Math.max(0, Math.floor((expiresAt - Date.now()) / 1000))
}

export function canExtendStepUpSession(): boolean {
  const remaining = getStepUpRemainingSeconds()
  return remaining > 0 && remaining <= STEP_UP_EXTEND_THRESHOLD_SECONDS
}

export function extendStepUpExpiry(expiresInMs: number): void {
  const token = getStepUpToken()
  if (!token || expiresInMs <= 0) return
  if (typeof window === "undefined") return
  sessionStorage.setItem(STEP_UP_EXPIRES_AT_KEY, String(Date.now() + expiresInMs))
  notifyStepUpChange()
}

/** apiRequest stepUp 옵션용 — 유효할 때만 헤더 값 반환 */
export function resolveStepUpHeaderValue(): string | null {  // client.ts용. 유효할 때만 토큰 문자열 반환, 아니면 null
  if (!isStepUpValid()) return null
  return getStepUpToken()
}

// API 호출
export async function verifyStepUpPassword(password: string): Promise<StepUpVerifyResponse> {
  const response = await apiRequest<StepUpVerifyResponse>("/api/v1/auth/step-up/verify", {
    method: "POST",
    body: { password },
  })
  setStepUpToken(response.stepUpToken, response.expiresIn)
  return response
}

export async function extendStepUpSession(): Promise<StepUpExtendResponse> {
  const token = getStepUpToken()
  if (!token) {
    throw new Error("Step-up token is missing")
  }

  const response = await apiRequest<StepUpExtendResponse>("/api/v1/auth/step-up/extend", {
    method: "POST",
    stepUp: true,
  })
  extendStepUpExpiry(response.expiresIn)
  return response
}
