export type AppNotification = {
  id: string
  title: string
  description: string
  createdAt: string
  href?: string
  read?: boolean
}

type NewAppNotification = Omit<AppNotification, "id" | "createdAt" | "read">

const STORAGE_KEY = "forenshield.notifications.v1"
const NOTIFICATION_TTL_MS = 24 * 60 * 60 * 1000
export const APP_NOTIFICATION_EVENT = "forenshield:notifications"

const defaultNotifications: AppNotification[] = [
  {
    id: "default-review-assignment",
    title: "검토 배정 기준 변경",
    description: "사건 생성 직후부터 담당 검토자를 배정할 수 있습니다.",
    createdAt: new Date().toISOString(),
  },
  {
    id: "default-analysis-status",
    title: "분석 상태 안내",
    description: "증거별 분석 완료 여부는 사건 상세에서 확인하세요.",
    createdAt: new Date().toISOString(),
  },
]

function canUseStorage() {
  return typeof window !== "undefined" && typeof window.localStorage !== "undefined"
}

function notifyChange() {
  if (typeof window === "undefined") return
  window.dispatchEvent(new CustomEvent(APP_NOTIFICATION_EVENT))
}

function isFreshNotification(notification: AppNotification) {
  const createdAt = new Date(notification.createdAt).getTime()
  if (Number.isNaN(createdAt)) return false
  return Date.now() - createdAt < NOTIFICATION_TTL_MS
}

export function getAppNotifications(): AppNotification[] {
  if (!canUseStorage()) return []

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return defaultNotifications.filter(isFreshNotification)

    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return defaultNotifications.filter(isFreshNotification)

    const validNotifications = parsed.filter((item): item is AppNotification => {
      return (
        item &&
        typeof item.id === "string" &&
        typeof item.title === "string" &&
        typeof item.description === "string" &&
        typeof item.createdAt === "string"
      )
    })

    const freshNotifications = validNotifications.filter(isFreshNotification)
    if (freshNotifications.length !== validNotifications.length) {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(freshNotifications.slice(0, 20)))
    }

    return freshNotifications
  } catch {
    return defaultNotifications.filter(isFreshNotification)
  }
}

export function setAppNotifications(notifications: AppNotification[]) {
  if (!canUseStorage()) return

  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(notifications.slice(0, 20)))
    notifyChange()
  } catch {
    // Notification storage is best effort only.
  }
}

export function addAppNotification(notification: NewAppNotification) {
  const next: AppNotification = {
    ...notification,
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: new Date().toISOString(),
    read: false,
  }

  setAppNotifications([next, ...getAppNotifications()])
}

export function markAllAppNotificationsRead() {
  setAppNotifications(getAppNotifications().map((item) => ({ ...item, read: true })))
}

export function removeAppNotification(notificationId: string) {
  setAppNotifications(getAppNotifications().filter((item) => item.id !== notificationId))
}
