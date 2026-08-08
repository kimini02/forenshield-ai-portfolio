import {
  API_FETCH_CREDENTIALS,
  shouldRetryAfterUnauthorized,
} from "@/lib/api/interceptor"
import { resolveStepUpHeaderValue, STEP_UP_HEADER } from "@/lib/api/step-up-auth"
import {
  getToken,
  handleUnauthorizedResponse,
  isAuthApiPath,
  touchSessionExpiry,
} from "@/lib/auth"
import { API_BASE_URL } from "@/lib/api/config"

export type ApiErrorDetail = { field: string; reason: string }

export class ApiError extends Error {
  status: number
  errorCode?: string
  details?: ApiErrorDetail[]

  constructor(message: string, status: number, errorCode?: string, details?: ApiErrorDetail[]) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.errorCode = errorCode
    this.details = details
  }
}

type RequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE"
  body?: unknown
  auth?: boolean
  /** true면 유효한 Step-up 토큰을 X-Step-Up-Token 헤더로 첨부 */
  stepUp?: boolean
}

type FormRequestOptions = {
  method?: "POST" | "PATCH" | "PUT"
  body: FormData
  auth?: boolean
}

async function parseApiError(response: Response): Promise<ApiError> {
  let message = "요청 처리 중 오류가 발생했습니다."
  let errorCode: string | undefined
  let details: ApiErrorDetail[] | undefined

  try {
    const errorBody = (await response.json()) as {
      message?: string
      errorCode?: string
      error?: string
      details?: ApiErrorDetail[]
    }
    message = errorBody.message ?? message
    errorCode = errorBody.errorCode ?? errorBody.error
    details = errorBody.details
  } catch {
    // ignore parse errors
  }

  return new ApiError(message, response.status, errorCode, details)
}

function requireAccessToken(): string {
  const token = getToken()
  if (token) return token

  handleUnauthorizedResponse()
  throw new ApiError("로그인이 필요합니다.", 401, "UNAUTHORIZED")
}

function noteAuthenticatedActivity(path: string, auth: boolean) {
  if (!auth || isAuthApiPath(path)) return
  touchSessionExpiry()
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
  retried = false
): Promise<T> {
  const { method = "GET", body, auth = true, stepUp = false } = options
  const headers: Record<string, string> = {
    Accept: "application/json",
  }

  if (body !== undefined) {
    headers["Content-Type"] = "application/json"
  }

  if (auth) {
    const token = requireAccessToken()
    headers.Authorization = `Bearer ${token}`
  }

  if (stepUp) {
    const stepUpToken = resolveStepUpHeaderValue()
    if (stepUpToken) {
      headers[STEP_UP_HEADER] = stepUpToken
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    credentials: API_FETCH_CREDENTIALS,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    if (response.status === 401 && auth) {
      const shouldRetry = await shouldRetryAfterUnauthorized({
        path,
        retried,
        requiresAuth: auth,
      })
      if (shouldRetry) {
        return apiRequest<T>(path, options, true)
      }
    }

    throw await parseApiError(response)
  }

  noteAuthenticatedActivity(path, auth)

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export async function apiRequestForm<T>(
  path: string,
  options: FormRequestOptions,
  retried = false
): Promise<T> {
  const { method = "POST", body, auth = true } = options
  const headers: Record<string, string> = {
    Accept: "application/json",
  }

  if (auth) {
    const token = requireAccessToken()
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    credentials: API_FETCH_CREDENTIALS,
    body,
  })

  if (!response.ok) {
    if (response.status === 401 && auth) {
      const shouldRetry = await shouldRetryAfterUnauthorized({
        path,
        retried,
        requiresAuth: auth,
      })
      if (shouldRetry) {
        return apiRequestForm<T>(path, options, true)
      }
    }

    throw await parseApiError(response)
  }

  noteAuthenticatedActivity(path, auth)

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export type ApiDownloadResult = {
  blob: Blob
  reportHash: string | null
  contentDisposition: string | null
}

async function requestDownload(path: string, retried = false): Promise<ApiDownloadResult> {
  const token = requireAccessToken()

  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    credentials: API_FETCH_CREDENTIALS,
  })

  if (!response.ok) {
    if (response.status === 401) {
      const shouldRetry = await shouldRetryAfterUnauthorized({
        path,
        retried,
        requiresAuth: true,
      })
      if (shouldRetry) {
        return requestDownload(path, true)
      }
    }

    throw await parseApiError(response)
  }

  noteAuthenticatedActivity(path, true)
  return {
    blob: await response.blob(),
    reportHash: response.headers.get("X-Report-Hash"),
    contentDisposition: response.headers.get("Content-Disposition"),
  }
}

export async function apiDownload(path: string): Promise<Blob> {
  const result = await requestDownload(path)
  return result.blob
}

export async function apiDownloadWithMetadata(path: string): Promise<ApiDownloadResult> {
  return requestDownload(path)
}
