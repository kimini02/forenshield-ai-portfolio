"use client"

import { useEffect } from "react"
import {
  applyTheme,
  getUserSettings,
  SETTINGS_CHANGE_EVENT,
} from "@/lib/user-settings"

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    function syncTheme() {
      applyTheme(getUserSettings().theme)
    }

    syncTheme()
    window.addEventListener(SETTINGS_CHANGE_EVENT, syncTheme)

    const media = window.matchMedia("(prefers-color-scheme: dark)")
    media.addEventListener("change", syncTheme)

    return () => {
      window.removeEventListener(SETTINGS_CHANGE_EVENT, syncTheme)
      media.removeEventListener("change", syncTheme)
    }
  }, [])

  return children
}
