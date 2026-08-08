"use client"

import { useCallback, useEffect, useState } from "react"
import {
  fetchUserSettings,
  updateUserSettings,
} from "@/lib/api/user-settings"
import { getSession, isMockAuthSession } from "@/lib/auth"
import { features } from "@/lib/features"
import {
  DEFAULT_USER_SETTINGS,
  fromApiUserSettings,
  getUserSettings,
  setUserSettings,
  SETTINGS_CHANGE_EVENT,
  toApiUserSettings,
  type UserSettings,
} from "@/lib/user-settings"

export function useUserSettings() {
  const [settings, setSettings] = useState<UserSettings>(DEFAULT_USER_SETTINGS)

  useEffect(() => {
    let cancelled = false

    function syncSettings() {
      setSettings(getUserSettings())
    }

    async function syncServerSettings() {
      syncSettings()
      const session = getSession()
      if (!session || features.mockApi || isMockAuthSession(session)) return

      try {
        const serverSettings = await fetchUserSettings()
        if (cancelled) return
        setUserSettings(fromApiUserSettings(serverSettings, getUserSettings()))
      } catch {
        // 로컬 설정을 유지하고 다음 로그인/새로고침에서 다시 동기화합니다.
      }
    }

    void syncServerSettings()
    window.addEventListener(SETTINGS_CHANGE_EVENT, syncSettings)
    window.addEventListener("auth-change", syncServerSettings)

    return () => {
      cancelled = true
      window.removeEventListener(SETTINGS_CHANGE_EVENT, syncSettings)
      window.removeEventListener("auth-change", syncServerSettings)
    }
  }, [])

  const updateSettings = useCallback((partial: Partial<UserSettings>) => {
    setUserSettings(partial)

    const session = getSession()
    const serverPatch = toApiUserSettings(partial)
    if (
      !session ||
      features.mockApi ||
      isMockAuthSession(session) ||
      Object.keys(serverPatch).length === 0
    ) {
      return
    }

    void updateUserSettings(serverPatch)
      .catch(() => {
        // 낙관적으로 적용한 화면 설정은 유지하고 서버 재동기화를 기다립니다.
      })
  }, [])

  return { settings, updateSettings }
}
