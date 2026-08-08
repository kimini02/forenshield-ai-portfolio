import { API_BASE_URL } from "@/lib/api/config"
import { features } from "@/lib/features"

export type AuthRole =
  | "user"
  | "admin"
  | "REVIEWER"
  | "INVESTIGATOR"
  | "ORG_ADMIN"
  | "ROLE_REVIEWER"
  | "ROLE_INVESTIGATOR"
  | "ROLE_ORG_ADMIN"
  | "ROLE_USER"
  | "ROLE_ADMIN"

export type AuthSession = {
  role: AuthRole
  userId: string
  loginId: string
  name: string
  /** Access JWT — 메모리만. Refresh JWT는 HttpOnly 쿠키. */
  token: string
}

/** 예전 sessionStorage 키 — 마이그레이션 시 제거용 */
const LEGACY_STORAGE_KEY = "veriforensics-auth"
const LEGACY_API_ORIGIN_KEY = "veriforensics-api-origin"
const MOCK_SESSION_STORAGE_KEY = "forenshield-mock-auth"
const SESSION_EXPIRES_AT_STORAGE_KEY = "forenshield-auth-session-expires-at"
const SESSION_EXPIRED_STORAGE_KEY = "forenshield-auth-session-expired"
const ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY = "forenshield-auth-access-expires-at"

/** Access JWT 만료 이만큼 전에 선제 refresh (ms) */
const ACCESS_REFRESH_LEAD_MS = 2 * 60 * 1000
/** HLS·UI 활동 touch 최소 간격 — 매 세그먼트마다 sessionStorage 쓰지 않도록 */
const SESSION_TOUCH_THROTTLE_MS = 30 * 1000

let memorySession: AuthSession | null = null
let redirectingToLogin = false
let authBootstrapped = false
let bootstrapPromise: Promise<void> | null = null
let lastSessionTouchAt = 0

export function mapBackendRole(role: string): AuthRole {
  if (role === "ROLE_ADMIN") return "admin"
  if (
    role === "ROLE_REVIEWER" ||
    role === "ROLE_INVESTIGATOR" ||
    role === "ROLE_ORG_ADMIN" ||
    role === "REVIEWER" ||
    role === "INVESTIGATOR" ||
    role === "ORG_ADMIN"
  ) {
    return role
  }
  return "user"
}

function purgeLegacySessionStorage() {
  if (typeof window === "undefined") return
  sessionStorage.removeItem(LEGACY_STORAGE_KEY)
  sessionStorage.removeItem(LEGACY_API_ORIGIN_KEY)
}

function readStoredMockSession(): AuthSession | null {
  if (!features.mockApi) return null
  if (typeof window === "undefined") return null

  try {
    const raw = sessionStorage.getItem(MOCK_SESSION_STORAGE_KEY)
    if (!raw) return null

    const parsed = JSON.parse(raw) as Partial<AuthSession>
    if (
      !parsed.token?.startsWith("mock-") ||
      !parsed.loginId ||
      !parsed.name ||
      !parsed.role ||
      !parsed.userId
    ) {
      sessionStorage.removeItem(MOCK_SESSION_STORAGE_KEY)
      return null
    }

    return {
      role: parsed.role,
      userId: parsed.userId,
      loginId: parsed.loginId,
      name: parsed.name,
      token: parsed.token,
    }
  } catch {
    sessionStorage.removeItem(MOCK_SESSION_STORAGE_KEY)
    return null
  }
}

function persistMockSession(session: AuthSession) {
  if (typeof window === "undefined") return
  sessionStorage.setItem(MOCK_SESSION_STORAGE_KEY, JSON.stringify(session))
}

function clearStoredMockSession() {
  if (typeof window === "undefined") return
  sessionStorage.removeItem(MOCK_SESSION_STORAGE_KEY)
}

function readSessionExpiresAt(): number | null {
  if (typeof window === "undefined") return null

  const raw = sessionStorage.getItem(SESSION_EXPIRES_AT_STORAGE_KEY)
  const expiresAt = Number(raw)
  return Number.isFinite(expiresAt) && expiresAt > 0 ? expiresAt : null
}

function writeSessionExpiresAt(expiresAt: number) {
  if (typeof window === "undefined") return
  sessionStorage.setItem(SESSION_EXPIRES_AT_STORAGE_KEY, String(expiresAt))
  sessionStorage.removeItem(SESSION_EXPIRED_STORAGE_KEY)
}

function clearSessionExpiry() {
  if (typeof window === "undefined") return
  sessionStorage.removeItem(SESSION_EXPIRES_AT_STORAGE_KEY)
  sessionStorage.removeItem(SESSION_EXPIRED_STORAGE_KEY)
  sessionStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY)
}

function readAccessTokenExpiresAt(): number | null {
  if (typeof window === "undefined") return null
  const raw = sessionStorage.getItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY)
  const expiresAt = Number(raw)
  return Number.isFinite(expiresAt) && expiresAt > 0 ? expiresAt : null
}

function writeAccessTokenExpiresAt(expiresAt: number) {
  if (typeof window === "undefined") return
  sessionStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY, String(expiresAt))
}

function setAccessTokenExpiry(accessTokenExpiresIn?: number) {
  if (typeof window === "undefined") return
  if (
    typeof accessTokenExpiresIn !== "number" ||
    !Number.isFinite(accessTokenExpiresIn) ||
    accessTokenExpiresIn <= 0
  ) {
    sessionStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY)
    return
  }
  writeAccessTokenExpiresAt(Date.now() + accessTokenExpiresIn)
}

function markSessionExpired() {
  if (typeof window === "undefined") return
  sessionStorage.setItem(SESSION_EXPIRED_STORAGE_KEY, "true")
}

function isSessionExpired() {
  if (typeof window === "undefined") return false
  if (sessionStorage.getItem(SESSION_EXPIRED_STORAGE_KEY) === "true") return true

  const expiresAt = readSessionExpiresAt()
  return expiresAt !== null && expiresAt <= Date.now()
}

function sessionIdleTimeoutMs(accessTokenExpiresIn?: number) {
  const configuredTimeoutMs = features.authSessionTimeoutMinutes * 60 * 1000
  const backendTimeoutMs =
    typeof accessTokenExpiresIn === "number" &&
    Number.isFinite(accessTokenExpiresIn) &&
    accessTokenExpiresIn > 0
      ? accessTokenExpiresIn
      : configuredTimeoutMs

  // access JWT 수명보다 프론트 유휴 타임아웃이 길어지지 않게 캡한다.
  return Math.min(backendTimeoutMs, configuredTimeoutMs)
}

function setSessionExpiry(accessTokenExpiresIn?: number) {
  if (typeof window === "undefined") return
  // 로그인·refresh 직후 유휴 타임아웃 시작점
  writeSessionExpiresAt(Date.now() + sessionIdleTimeoutMs(accessTokenExpiresIn))
  setAccessTokenExpiry(accessTokenExpiresIn)
}

/**
 * 인증 API 성공 등 활동 시 유휴 만료 시각을 지금 + N분으로 연장한다.
 * auth-change는 쏘지 않는다 — 매 API마다 헤더 알림/프로필 재조회로 무한 루프가 난다.
 * AuthProvider 타이머는 만료 시점에 expiresAt을 다시 읽어 연장을 반영한다.
 */
export function touchSessionExpiry() {
  if (typeof window === "undefined") return
  if (!memorySession || isMockAuthSession(memorySession)) return
  if (isSessionExpired()) return

  lastSessionTouchAt = Date.now()
  writeSessionExpiresAt(Date.now() + sessionIdleTimeoutMs())
}

/**
 * HLS·포인터 등 고빈도 활동용 — 기본 30초에 한 번만 유휴 만료를 연장한다.
 */
export function touchSessionExpiryThrottled(minIntervalMs = SESSION_TOUCH_THROTTLE_MS) {
  if (typeof window === "undefined") return
  const now = Date.now()
  if (now - lastSessionTouchAt < minIntervalMs) return
  touchSessionExpiry()
}

function notifyAuthChange() {
  if (typeof window === "undefined") return
  window.dispatchEvent(new Event("auth-change"))
}

if (typeof window !== "undefined") {
  purgeLegacySessionStorage()
}

export function getSession(): AuthSession | null {
  if (typeof window === "undefined") return null
  if (!memorySession) {
    memorySession = readStoredMockSession()
  }
  return memorySession
}

export function getToken(): string | null {
  if (typeof window !== "undefined" && !memorySession) {
    memorySession = readStoredMockSession()
  }
  return memorySession?.token ?? null
}

export function isReviewerRole(role?: AuthRole | string | null): boolean {
  return role === "REVIEWER" || role === "ROLE_REVIEWER"
}

export function isReviewerSession(session: AuthSession | null): boolean {
  return isReviewerRole(session?.role)
}

export function isMockAuthSession(session: AuthSession | null = memorySession): boolean {
  return Boolean(session?.token.startsWith("mock-"))
}

// 로그인 및 refresh 응답을 한 곳에서 세션으로 변환하는 함수
// login-form, tryRefreshSession 이 이걸 씀
export function applyLoginResponse(response: {
  userId: number
  loginId: string
  name: string
  role: string
  token: string
  accessToken?: string
  accessTokenExpiresIn?: number
}) {
  const accessToken = response.accessToken ?? response.token
  if (!accessToken.startsWith("mock-")) {
    setSessionExpiry(response.accessTokenExpiresIn)
  }

  setSession({
    role: mapBackendRole(response.role),
    userId: String(response.userId),
    loginId: response.loginId,
    name: response.name,
    token: accessToken,
  })
}

export function updateAccessToken(token: string) {
  if (!memorySession || !token) return
  memorySession = { ...memorySession, token }
  notifyAuthChange()
}

export function isAuthApiPath(path: string): boolean {
  return (
    path.startsWith("/api/auth/login") ||
    path.startsWith("/api/auth/refresh") ||
    path.startsWith("/api/auth/logout")
  )
}

let refreshPromise: Promise<boolean> | null = null

// 이전에는 refresh 응답에서 accessToken만 반환했으나 이제는 accessToken과 refreshToken 모두 반환
// 전체 세션 복구 코드
export async function tryRefreshSession(): Promise<boolean> {
  if (typeof window === "undefined" || !features.authRefresh) return false
  if (isSessionExpired()) {
    expireSession()
    return false
  }

  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
          method: "POST",
          credentials: "include",
        })
        if (!response.ok) return false

        const data = (await response.json()) as {
          accessToken?: string
          token?: string
          userId?: number
          loginId?: string
          name?: string
          role?: string
          accessTokenExpiresIn?: number
        }

        const accessToken = data.accessToken ?? data.token
        if (!accessToken || data.userId == null || !data.loginId || !data.name || !data.role) {
          return false
        }

        applyLoginResponse({
          userId: data.userId,
          loginId: data.loginId,
          name: data.name,
          role: data.role,
          token: accessToken,
          accessToken,
          accessTokenExpiresIn: data.accessTokenExpiresIn,
        })
        return true
      } catch {
        return false
      }
    })().finally(() => {
      refreshPromise = null
    })
  }

  return refreshPromise
}

// 정책이 허용된 경우에만 새로고침 후 HttpOnly refresh 쿠키로 세션 복구 시도
export async function bootstrapAuthSession(): Promise<void> {
  if (typeof window === "undefined") return
  if (authBootstrapped) return
  if (bootstrapPromise) {
    await bootstrapPromise
    return
  }

  bootstrapPromise = (async () => {
    purgeLegacySessionStorage()
    if (!features.mockApi) {
      clearStoredMockSession()
    } else if (!memorySession) {
      memorySession = readStoredMockSession()
    }
    if (!memorySession) {
      await tryRefreshSession()
    }
  })().finally(() => {
    authBootstrapped = true
    bootstrapPromise = null
  })

  await bootstrapPromise
}

export function isAuthBootstrapped(): boolean {
  return authBootstrapped
}

export function getSessionExpiresAt(): number | null {
  return readSessionExpiresAt()
}

/** Access JWT 만료 시각(epoch ms). 선제 refresh 스케줄에 사용. */
export function getAccessTokenExpiresAt(): number | null {
  return readAccessTokenExpiresAt()
}

/** Access 만료 시각 기준 선제 refresh까지 남은 ms. 이미 지났거나 없으면 null. */
export function getMsUntilProactiveRefresh(): number | null {
  const accessExpiresAt = readAccessTokenExpiresAt()
  if (accessExpiresAt == null) return null
  const refreshAt = accessExpiresAt - ACCESS_REFRESH_LEAD_MS
  const remaining = refreshAt - Date.now()
  return remaining
}

export function setSession(session: AuthSession) {
  memorySession = session
  redirectingToLogin = false
  if (typeof window !== "undefined") {
    if (isMockAuthSession(session)) {
      clearSessionExpiry()
    } else {
      sessionStorage.removeItem(SESSION_EXPIRED_STORAGE_KEY)
    }
  }
  if (isMockAuthSession(session)) {
    persistMockSession(session)
  } else {
    clearStoredMockSession()
  }
  notifyAuthChange()
}

export function clearSession() {
  memorySession = null
  redirectingToLogin = false
  purgeLegacySessionStorage()
  clearStoredMockSession()
  clearSessionExpiry()
  notifyAuthChange()
}

/** 로그인 이후 정해진 세션 시간이 끝났을 때 refresh 재발급 없이 로그인 화면으로 이동한다. */
export function expireSession() {
  if (typeof window === "undefined" || redirectingToLogin) return

  redirectingToLogin = true
  memorySession = null
  purgeLegacySessionStorage()
  clearStoredMockSession()
  markSessionExpired()
  notifyAuthChange()
  if (window.location.pathname !== "/login") {
    window.location.replace("/login")
  }
}

/** 401 응답 시 세션 정리 후 로그인 화면으로 이동 (중복 리다이렉트 방지) */
export function handleUnauthorizedResponse() {
  expireSession()
}
