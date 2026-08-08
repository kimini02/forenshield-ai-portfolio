"use client"

import { useEffect, useRef, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { Bell, ChevronDown, LogOut, Moon, Sun, UserCog, User } from "lucide-react"
import { useUserSettings } from "@/hooks/use-user-settings"
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type ApiNotification,
} from "@/lib/api/notifications"
import { fetchMyProfile } from "@/lib/api/user"
import { logoutApi } from "@/lib/auth-api"
import { clearStepUpToken } from "@/lib/api/step-up-auth"
import { clearSession, getSession, isMockAuthSession, type AuthSession } from "@/lib/auth"
import { getAppUserFromSession, roleLabelMap } from "@/lib/permissions"
import {
  APP_NOTIFICATION_EVENT,
  getAppNotifications,
  markAllAppNotificationsRead,
  removeAppNotification,
  type AppNotification,
} from "@/lib/notifications"
import { cn } from "@/lib/utils"

const themeOptions: { value: "light" | "dark"; label: string; icon: typeof Sun }[] = [
  { value: "light", label: "라이트", icon: Sun },
  { value: "dark", label: "다크", icon: Moon },
]

function mapApiNotification(notification: ApiNotification): AppNotification {
  return {
    id: String(notification.notificationId),
    title: notification.title,
    description: notification.message,
    createdAt: notification.createdAt,
    read: notification.read,
    href:
      notification.referenceId != null
        ? `/evidences/${notification.referenceId}`
        : undefined,
  }
}

export function SiteHeaderAuth() {
  const router = useRouter()
  const { settings, updateSettings } = useUserSettings()
  const [session, setSession] = useState<AuthSession | null>(() => getSession())
  const [department, setDepartment] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const [notificationOpen, setNotificationOpen] = useState(false)
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const menuRef = useRef<HTMLDivElement>(null)
  const notificationRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    let lastProfileKey: string | null = null

    async function loadProfile(currentSession: AuthSession | null) {
      if (!currentSession) {
        if (!cancelled) setDepartment(null)
        return
      }

      if (isMockAuthSession(currentSession)) {
        const appUser = getAppUserFromSession(currentSession)
        if (!cancelled) {
          setDepartment(appUser ? `${appUser.organizationName} · ${appUser.department}` : null)
        }
        return
      }

      try {
        const profile = await fetchMyProfile()
        if (!cancelled) setDepartment(profile.department)
      } catch {
        if (!cancelled) setDepartment(null)
      }
    }

    function syncAuthState() {
      const currentSession = getSession()
      setSession(currentSession)

      const profileKey = currentSession
        ? `${currentSession.userId}:${currentSession.token}`
        : ""
      if (profileKey === lastProfileKey) return
      lastProfileKey = profileKey
      void loadProfile(currentSession)
    }

    syncAuthState()

    window.addEventListener("auth-change", syncAuthState)
    window.addEventListener("storage", syncAuthState)

    return () => {
      cancelled = true
      window.removeEventListener("auth-change", syncAuthState)
      window.removeEventListener("storage", syncAuthState)
    }
  }, [])

  useEffect(() => {
    if (!open && !notificationOpen) return

    function handlePointerDown(event: MouseEvent) {
      const target = event.target as Node

      if (
        !menuRef.current?.contains(target) &&
        !notificationRef.current?.contains(target)
      ) {
        setOpen(false)
        setNotificationOpen(false)
      }
    }

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false)
        setNotificationOpen(false)
      }
    }

    document.addEventListener("mousedown", handlePointerDown)
    document.addEventListener("keydown", handleEscape)

    return () => {
      document.removeEventListener("mousedown", handlePointerDown)
      document.removeEventListener("keydown", handleEscape)
    }
  }, [notificationOpen, open])

  useEffect(() => {
    let cancelled = false

    async function syncNotifications() {
      const currentSession = getSession()
      if (!currentSession) {
        if (!cancelled) setNotifications([])
        return
      }

      if (isMockAuthSession(currentSession)) {
        if (!cancelled) setNotifications(getAppNotifications())
        return
      }

      try {
        const response = await fetchNotifications(20)
        if (!cancelled) {
          setNotifications(response.notifications.map(mapApiNotification))
        }
      } catch {
        // 기존 목록을 유지하고 다음 알림 열기/상태 변경 때 다시 조회합니다.
      }
    }

    void syncNotifications()

    // 알림은 전용 이벤트·storage·마운트 시에만 동기화한다.
    // auth-change마다 재조회하면 API 성공 → touch/auth 순환이 생길 수 있다.
    window.addEventListener(APP_NOTIFICATION_EVENT, syncNotifications)
    window.addEventListener("storage", syncNotifications)

    return () => {
      cancelled = true
      window.removeEventListener(APP_NOTIFICATION_EVENT, syncNotifications)
      window.removeEventListener("storage", syncNotifications)
    }
  }, [])

  async function handleLogout() {
    try {
      if (!isMockAuthSession(session)) {
        await logoutApi()
      }
    } finally {
      clearStepUpToken()
      clearSession()
      setOpen(false)
      setSession(null)
      router.push("/login")
    }
  }

  if (!session) {
    return null
  }

  const currentUser = getAppUserFromSession(session)
  const roleLabel = currentUser ? roleLabelMap[currentUser.role] : null
  const displayName = currentUser
    ? `${currentUser.name} · ${roleLabel}`
    : session.name
  const affiliationLabel = currentUser
    ? `${currentUser.organizationName} · ${currentUser.department}`
    : department ?? "소속 부서 미등록"
  const unreadCount = notifications.filter((item) => !item.read).length

  function handleToggleNotifications() {
    const nextOpen = !notificationOpen
    setNotificationOpen(nextOpen)
    setOpen(false)

    if (nextOpen && !isMockAuthSession(session)) {
      void fetchNotifications(20)
        .then((response) => {
          setNotifications(response.notifications.map(mapApiNotification))
        })
        .catch(() => undefined)
    }
  }

  function handleReadAllNotifications() {
    if (isMockAuthSession(session)) {
      markAllAppNotificationsRead()
      setNotifications(getAppNotifications())
      return
    }

    setNotifications((current) => current.map((item) => ({ ...item, read: true })))
    void markAllNotificationsRead().catch(() => {
      void fetchNotifications(20)
        .then((response) => {
          setNotifications(response.notifications.map(mapApiNotification))
        })
        .catch(() => undefined)
    })
  }

  function handleOpenNotification(notification: AppNotification) {
    if (isMockAuthSession(session)) {
      removeAppNotification(notification.id)
      setNotifications(getAppNotifications())
    } else if (!notification.read) {
      setNotifications((current) =>
        current.map((item) =>
          item.id === notification.id ? { ...item, read: true } : item
        )
      )
      void markNotificationRead(Number(notification.id)).catch(() => undefined)
    }
    setNotificationOpen(false)
  }

  return (
    <div className="flex items-center gap-3">
      <div ref={notificationRef} className="relative">
        <button
          type="button"
          aria-haspopup="dialog"
          aria-expanded={notificationOpen}
          aria-label="알림"
          onClick={handleToggleNotifications}
          className={cn(
            "relative inline-flex size-9 items-center justify-center rounded-lg text-teal-700 transition-colors hover:bg-teal-50 hover:text-teal-800 dark:text-teal-300 dark:hover:bg-teal-950/30 dark:hover:text-teal-200",
            notificationOpen && "bg-teal-50 text-teal-800 ring-2 ring-teal-200 dark:bg-teal-950/30 dark:text-teal-200"
          )}
        >
          <Bell className="size-4" aria-hidden="true" />
          {unreadCount > 0 ? (
            <span className="absolute -right-1 -top-1 inline-flex min-w-4 items-center justify-center rounded-full bg-teal-600 px-1 text-[10px] font-bold leading-4 text-white ring-2 ring-background">
              {unreadCount > 9 ? "9+" : unreadCount}
            </span>
          ) : null}
        </button>

        {notificationOpen ? (
          <div
            role="dialog"
            aria-label="알림 목록"
            className="absolute right-0 z-50 mt-2 w-[320px] overflow-hidden rounded-xl border border-border bg-popover shadow-lg"
          >
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <div>
                <p className="text-sm font-bold text-popover-foreground">알림</p>
                <p className="text-xs text-muted-foreground">{notifications.length}개 알림</p>
              </div>
              <button
                type="button"
                onClick={handleReadAllNotifications}
                className="text-xs font-semibold text-teal-600 hover:text-teal-700"
              >
                읽음 처리
              </button>
            </div>

            <div className="max-h-80 overflow-y-auto py-1">
              {notifications.length === 0 ? (
                <div className="px-4 py-8 text-center text-sm font-medium text-muted-foreground">
                  새 알림이 없습니다.
                </div>
              ) : (
                notifications.map((item, index) => {
                  const content = (
                    <>
                      <div className="flex items-center justify-between gap-3">
                        <span className="text-[11px] font-bold text-teal-600/80">알림 {index + 1}</span>
                        {!item.read ? <span className="size-2 rounded-full bg-teal-600" /> : null}
                      </div>
                      <p className="mt-1 text-sm font-bold text-popover-foreground">{item.title}</p>
                      <p className="mt-1 text-xs leading-5 text-muted-foreground">{item.description}</p>
                    </>
                  )

                  return item.href ? (
                    <Link
                      key={item.id}
                      href={item.href}
                      onClick={() => handleOpenNotification(item)}
                      className="block border-b border-border/70 px-4 py-3 transition-colors last:border-b-0 hover:bg-muted"
                    >
                      {content}
                    </Link>
                  ) : (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => handleOpenNotification(item)}
                      className="block w-full border-b border-border/70 px-4 py-3 text-left transition-colors last:border-b-0 hover:bg-muted"
                    >
                      {content}
                    </button>
                  )
                })
              )}
            </div>
          </div>
        ) : null}
      </div>

      <div ref={menuRef} className="relative">
        <button
          type="button"
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label="계정 메뉴"
          onClick={() => setOpen((prev) => !prev)}
          className={cn(
            "inline-flex h-9 items-center gap-1.5 rounded-lg px-2.5 text-sm font-bold transition-colors",
            open
              ? "bg-slate-100 text-slate-900 dark:bg-muted dark:text-foreground"
              : "text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-muted-foreground dark:hover:bg-muted dark:hover:text-foreground"
          )}
        >
          <User className="size-4" aria-hidden="true" />
          <span className="hidden max-w-40 truncate sm:inline">{displayName}</span>
          <ChevronDown
            className={cn("size-3.5 transition-transform", open && "rotate-180")}
            aria-hidden="true"
          />
        </button>

        {open && (
          <div
            role="menu"
            aria-label="계정 메뉴"
            className="absolute right-0 z-50 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded-xl border border-border bg-popover p-4 shadow-lg"
          >
            <div className="flex items-start gap-3 border-b border-border pb-3">
              <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                <User className="size-4" aria-hidden="true" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                  <p className="max-w-full truncate text-sm font-bold text-popover-foreground">
                    {currentUser?.name ?? session.name}
                  </p>
                  {roleLabel ? (
                    <span className="shrink-0 rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-bold text-primary">
                      {roleLabel}
                    </span>
                  ) : null}
                </div>
                <p className="mt-1 text-xs leading-5 text-muted-foreground">
                  {affiliationLabel}
                </p>
              </div>
            </div>

            <div className="space-y-2 py-3">
              <p className="text-xs font-medium text-muted-foreground">화면 테마</p>
              <div className="grid grid-cols-2 gap-1.5">
                {themeOptions.map((option) => {
                  const Icon = option.icon
                  const isActive = settings.theme === option.value

                  return (
                    <button
                      key={option.value}
                      type="button"
                      role="menuitemradio"
                      aria-checked={isActive}
                      onClick={() => updateSettings({ theme: option.value })}
                      className={cn(
                        "inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border text-sm font-medium transition-colors",
                        isActive
                          ? "border-primary bg-primary/10 text-primary"
                          : "border-border text-muted-foreground hover:bg-muted"
                      )}
                    >
                      <Icon className="size-4" aria-hidden="true" />
                      {option.label}
                    </button>
                  )
                })}
              </div>
            </div>

            <div className="border-t border-border pt-3">
              <Link
                href="/mypage/edit"
                role="menuitem"
                onClick={() => setOpen(false)}
                className="flex h-9 items-center gap-2 rounded-lg px-2.5 text-sm font-medium text-foreground transition-colors hover:bg-muted"
              >
                <UserCog className="size-4" aria-hidden="true" />
                개인정보 수정
              </Link>
              <button
                type="button"
                role="menuitem"
                onClick={handleLogout}
                className="flex h-9 w-full items-center gap-2 rounded-lg px-2.5 text-sm font-medium text-destructive transition-colors hover:bg-destructive/10"
              >
                <LogOut className="size-4" aria-hidden="true" />
                로그아웃
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
