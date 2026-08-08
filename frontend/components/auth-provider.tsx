"use client"

import { useEffect, useState } from "react"
import {
  bootstrapAuthSession,
  expireSession,
  getMsUntilProactiveRefresh,
  getSession,
  getSessionExpiresAt,
  isMockAuthSession,
  touchSessionExpiryThrottled,
  tryRefreshSession,
} from "@/lib/auth"
import { features } from "@/lib/features"

type AuthProviderProps = {
  children: React.ReactNode
}

/** 앱 시작 시 refresh 쿠키로 액세스 JWT(메모리) 복구 + 유휴/선제 refresh 타이머 */
export function AuthProvider({ children }: AuthProviderProps) {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    void bootstrapAuthSession().finally(() => setReady(true))
  }, [])

  // FE 유휴 세션 만료 타이머
  useEffect(() => {
    let timeoutId: number | undefined

    function scheduleSessionExpiry() {
      if (timeoutId) {
        window.clearTimeout(timeoutId)
        timeoutId = undefined
      }

      const session = getSession()
      const expiresAt = getSessionExpiresAt()
      if (!session || isMockAuthSession(session) || !expiresAt) return

      const remainingMs = expiresAt - Date.now()
      if (remainingMs <= 0) {
        expireSession()
        return
      }

      // 타이머가 울릴 때 expiresAt을 다시 읽어, touchSessionExpiry로 연장된 경우를 반영한다.
      timeoutId = window.setTimeout(() => {
        const latestExpiresAt = getSessionExpiresAt()
        if (latestExpiresAt && latestExpiresAt > Date.now()) {
          scheduleSessionExpiry()
          return
        }
        expireSession()
      }, remainingMs)
    }

    scheduleSessionExpiry()
    window.addEventListener("auth-change", scheduleSessionExpiry)

    return () => {
      if (timeoutId) window.clearTimeout(timeoutId)
      window.removeEventListener("auth-change", scheduleSessionExpiry)
    }
  }, [])

  // Access JWT 만료 2분 전 선제 refresh
  useEffect(() => {
    if (!features.authRefresh) return

    let timeoutId: number | undefined

    function scheduleProactiveRefresh() {
      if (timeoutId) {
        window.clearTimeout(timeoutId)
        timeoutId = undefined
      }

      const session = getSession()
      if (!session || isMockAuthSession(session)) return

      const remainingMs = getMsUntilProactiveRefresh()
      if (remainingMs == null) return

      const delayMs = Math.max(0, remainingMs)
      timeoutId = window.setTimeout(() => {
        void tryRefreshSession().finally(() => {
          scheduleProactiveRefresh()
        })
      }, delayMs)
    }

    scheduleProactiveRefresh()
    window.addEventListener("auth-change", scheduleProactiveRefresh)

    return () => {
      if (timeoutId) window.clearTimeout(timeoutId)
      window.removeEventListener("auth-change", scheduleProactiveRefresh)
    }
  }, [])

  // 포인터·키보드 등 실제 조작 시 유휴 연장 (30초 스로틀)
  useEffect(() => {
    function onUserActivity() {
      touchSessionExpiryThrottled()
    }

    function onVisibilityChange() {
      if (document.visibilityState === "visible") onUserActivity()
    }

    window.addEventListener("pointerdown", onUserActivity, { passive: true })
    window.addEventListener("keydown", onUserActivity)
    document.addEventListener("visibilitychange", onVisibilityChange)

    return () => {
      window.removeEventListener("pointerdown", onUserActivity)
      window.removeEventListener("keydown", onUserActivity)
      document.removeEventListener("visibilitychange", onVisibilityChange)
    }
  }, [])

  if (!ready) {
    return null
  }

  return <>{children}</>
}
