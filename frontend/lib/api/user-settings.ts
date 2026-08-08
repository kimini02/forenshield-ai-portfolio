import { apiRequest } from "@/lib/api/client"

export type ApiDateDisplayFormat = "ISO" | "KR" | "US"
export type ApiListViewMode = "TABLE" | "CARD"
export type ApiListSortMode = "NEWEST" | "STATUS"
export type ApiThemeMode = "LIGHT" | "DARK" | "SYSTEM"

export type ApiUserSettings = {
  dateDisplayFormat: ApiDateDisplayFormat
  analysisCompleteNotificationEnabled: boolean
  listViewMode: ApiListViewMode
  listSortMode: ApiListSortMode
  themeMode: ApiThemeMode
  updatedAt?: string | null
}

export type UpdateApiUserSettings = Partial<
  Pick<
    ApiUserSettings,
    | "dateDisplayFormat"
    | "analysisCompleteNotificationEnabled"
    | "listViewMode"
    | "listSortMode"
    | "themeMode"
  >
>

export function fetchUserSettings(): Promise<ApiUserSettings> {
  return apiRequest<ApiUserSettings>("/api/v1/users/me/settings")
}

export function updateUserSettings(
  settings: UpdateApiUserSettings
): Promise<ApiUserSettings> {
  return apiRequest<ApiUserSettings>("/api/v1/users/me/settings", {
    method: "PATCH",
    body: settings,
  })
}
