// API 에러 메시지 추출 공통화.
// 화면에서 catch한 에러를 사용자에게 보여줄 문구로 바꾼다.

import { ApiError } from "@/lib/api/client"

// 알 수 없는 에러에서 사용자 표시용 메시지를 뽑는다.
export function getApiErrorMessage(error: unknown, fallback = "요청 처리 중 오류가 발생했습니다."): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error && error.message) return error.message
  return fallback
}

// 인증 만료/미인증(401) 여부.
export function isUnauthorizedError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

// Step-up 재인증 필요(403 STEP_UP_REQUIRED) 여부.
export function isStepUpRequiredError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    error.status === 403 &&
    error.errorCode === "STEP_UP_REQUIRED"
  )
}

// Rate limit(429) 여부.
export function isRateLimitError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 429 && error.errorCode === "RATE_LIMIT_EXCEEDED"
}

export function isLoginTemporarilyBlockedError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    error.status === 429 &&
    error.errorCode === "LOGIN_TEMPORARILY_BLOCKED"
  )
}

export function isLoginDayBlockedError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    error.status === 429 &&
    error.errorCode === "LOGIN_DAY_BLOCKED"
  )
}

export function getLoginErrorMessage(
  error: unknown,
  fallback = "로그인 요청에 실패했습니다. 백엔드 서버 상태를 확인해 주세요."
): string {
  if (isLoginDayBlockedError(error)) {
    return "횟수 제한으로 인해 24시간 동안 로그인이 제한됩니다."
  }
  if (isLoginTemporarilyBlockedError(error)) {
    return "로그인 시도 횟수를 초과했습니다. 3분 후 다시 시도해 주세요."
  }
  return getApiErrorMessage(error, fallback)
}
