import { apiRequest } from "@/lib/api/client"
import { API_BASE_URL } from "@/lib/api/config"
import { getSession, getToken, isMockAuthSession } from "@/lib/auth"

export type LoginRequest = {
  loginId: string
  password: string
}

export type LoginResponse = {
  success: boolean
  token: string
  accessToken?: string
  accessTokenExpiresIn?: number
  userId: number
  loginId: string
  name: string
  role:
    | "ROLE_USER"
    | "ROLE_ADMIN"
    | "ROLE_REVIEWER"
    | "ROLE_INVESTIGATOR"
    | "ROLE_ORG_ADMIN"
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  return apiRequest<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: request,
    auth: false,
  })
}

export async function logoutApi(): Promise<void> {
  if (isMockAuthSession(getSession())) {
    return
  }

  const token = getToken()
  const headers: Record<string, string> = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  try {
    await fetch(`${API_BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
      headers,
    })
  } catch (error) {
    if (error instanceof TypeError) {
      return
    }

    throw error
  }
}

export function resolveAccessToken(response: LoginResponse): string {
  return response.accessToken ?? response.token
}
