import { apiRequest } from "@/lib/api/client"

export type ApiNotificationType =
  | "ANALYSIS_COMPLETED"
  | "ANALYSIS_FAILED"
  | "BLOCKCHAIN_ANCHOR"
  | "SECURITY_ALERT"

export type ApiNotification = {
  notificationId: number
  type: ApiNotificationType
  title: string
  message: string
  referenceType?: string | null
  referenceId?: number | null
  read: boolean
  createdAt: string
}

export type ApiNotificationList = {
  notifications: ApiNotification[]
  unreadCount: number
}

export function fetchNotifications(limit = 20): Promise<ApiNotificationList> {
  const params = new URLSearchParams({ limit: String(limit) })
  return apiRequest<ApiNotificationList>(`/api/v1/notifications?${params}`)
}

export function markNotificationRead(notificationId: number): Promise<ApiNotification> {
  return apiRequest<ApiNotification>(`/api/v1/notifications/${notificationId}/read`, {
    method: "PATCH",
  })
}

export function markAllNotificationsRead(): Promise<{ markedCount: number }> {
  return apiRequest<{ markedCount: number }>("/api/v1/notifications/read-all", {
    method: "PATCH",
  })
}
