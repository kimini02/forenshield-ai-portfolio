import {
  getSession,
  getToken,
  handleUnauthorizedResponse,
  isAuthApiPath,
  isMockAuthSession,
  tryRefreshSession,
} from "@/lib/auth"

export const API_FETCH_CREDENTIALS: RequestCredentials = "include"

export type UnauthorizedContext = {
  path: string
  retried: boolean
  requiresAuth?: boolean
}

export async function shouldRetryAfterUnauthorized({
  path,
  retried,
  requiresAuth = true,
}: UnauthorizedContext): Promise<boolean> {
  if (isMockAuthSession(getSession())) {
    return false
  }

  if (!requiresAuth || retried || isAuthApiPath(path)) {
    handleUnauthorizedResponse()
    return false
  }

  const refreshed = await tryRefreshSession()
  if (!refreshed) {
    handleUnauthorizedResponse()
    return false
  }

  return true
}

export function applyBearerAuth(headers: Headers): void {
  const token = getToken()
  if (token) {
    headers.set("Authorization", `Bearer ${token}`)
  }
}

export function buildAuthHeaders(extra?: HeadersInit): HeadersInit {
  const headers = new Headers(extra)
  applyBearerAuth(headers)
  return headers
}
