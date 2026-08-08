import type {
  ApiUserSettings,
  UpdateApiUserSettings,
} from "@/lib/api/user-settings"

export const SETTINGS_STORAGE_KEY = "veriforensics-user-settings"
export const SETTINGS_CHANGE_EVENT = "settings-change"

export type ThemeMode = "light" | "dark" | "system"
export type DateFormat = "kr" | "us" | "iso"
export type ListSort = "newest" | "status"

export type UserSettings = {
  theme: ThemeMode
  dateFormat: DateFormat
  analysisCompleteNotification: boolean
  listSort: ListSort
}

export const DEFAULT_USER_SETTINGS: UserSettings = {
  theme: "system",
  dateFormat: "kr",
  analysisCompleteNotification: true,
  listSort: "newest",
}

function normalizeStoredSettings(value: unknown): Partial<UserSettings> {
  if (!value || typeof value !== "object") return {}

  const stored = value as Record<string, unknown>
  const dateFormat =
    stored.dateFormat === "ko-full" || stored.dateFormat === "ko-date"
      ? "kr"
      : stored.dateFormat

  return {
    ...(stored.theme === "light" || stored.theme === "dark" || stored.theme === "system"
      ? { theme: stored.theme }
      : {}),
    ...(dateFormat === "kr" || dateFormat === "us" || dateFormat === "iso"
      ? { dateFormat }
      : {}),
    ...(typeof stored.analysisCompleteNotification === "boolean"
      ? { analysisCompleteNotification: stored.analysisCompleteNotification }
      : {}),
    ...(stored.listSort === "newest" || stored.listSort === "status"
      ? { listSort: stored.listSort }
      : {}),
  }
}

export function getUserSettings(): UserSettings {
  if (typeof window === "undefined") return DEFAULT_USER_SETTINGS

  const raw = localStorage.getItem(SETTINGS_STORAGE_KEY)
  if (!raw) return DEFAULT_USER_SETTINGS

  try {
    return { ...DEFAULT_USER_SETTINGS, ...normalizeStoredSettings(JSON.parse(raw)) }
  } catch {
    return DEFAULT_USER_SETTINGS
  }
}

export function setUserSettings(partial: Partial<UserSettings>) {
  const next = { ...getUserSettings(), ...partial }
  localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(next))
  window.dispatchEvent(new Event(SETTINGS_CHANGE_EVENT))

  if (partial.theme !== undefined) {
    applyTheme(partial.theme)
  }
}

export function fromApiUserSettings(
  settings: ApiUserSettings,
  localSettings: UserSettings = getUserSettings()
): UserSettings {
  const themeMap: Record<ApiUserSettings["themeMode"], ThemeMode> = {
    LIGHT: "light",
    DARK: "dark",
    SYSTEM: "system",
  }
  const dateFormatMap: Record<ApiUserSettings["dateDisplayFormat"], DateFormat> = {
    KR: "kr",
    US: "us",
    ISO: "iso",
  }

  return {
    ...localSettings,
    theme: themeMap[settings.themeMode],
    dateFormat: dateFormatMap[settings.dateDisplayFormat],
    analysisCompleteNotification: settings.analysisCompleteNotificationEnabled,
    listSort: settings.listSortMode === "STATUS" ? "status" : "newest",
  }
}

export function toApiUserSettings(
  settings: Partial<UserSettings>
): UpdateApiUserSettings {
  const themeMap: Record<ThemeMode, ApiUserSettings["themeMode"]> = {
    light: "LIGHT",
    dark: "DARK",
    system: "SYSTEM",
  }
  const dateFormatMap: Record<DateFormat, ApiUserSettings["dateDisplayFormat"]> = {
    kr: "KR",
    us: "US",
    iso: "ISO",
  }

  return {
    ...(settings.theme !== undefined ? { themeMode: themeMap[settings.theme] } : {}),
    ...(settings.dateFormat !== undefined
      ? { dateDisplayFormat: dateFormatMap[settings.dateFormat] }
      : {}),
    ...(settings.analysisCompleteNotification !== undefined
      ? {
          analysisCompleteNotificationEnabled:
            settings.analysisCompleteNotification,
        }
      : {}),
    ...(settings.listSort !== undefined
      ? { listSortMode: settings.listSort === "status" ? "STATUS" : "NEWEST" }
      : {}),
  }
}

export function resolveIsDark(theme: ThemeMode): boolean {
  if (theme === "dark") return true
  if (theme === "light") return false
  return window.matchMedia("(prefers-color-scheme: dark)").matches
}

export function applyTheme(theme: ThemeMode) {
  const isDark = resolveIsDark(theme)
  document.documentElement.classList.toggle("dark", isDark)
  document.documentElement.style.colorScheme = isDark ? "dark" : "light"
}
