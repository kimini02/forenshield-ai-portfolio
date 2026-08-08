"use client"

import { useEffect, useState, type MouseEvent } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Clock, ShieldCheck, Lock } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { SiteHeaderAuth } from "@/components/site-header-auth"
import {
  canExtendStepUpSession,
  clearStepUpToken,
  extendStepUpSession,
  getStepUpRemainingSeconds,
  isStepUpValid,
  STEP_UP_CHANGE_EVENT,
  STEP_UP_EXTEND_THRESHOLD_SECONDS,
} from "@/lib/api/step-up-auth"
import { ApiError } from "@/lib/api/client"
import { getSession, isReviewerSession, type AuthSession } from "@/lib/auth"
import { features } from "@/lib/features"
import { cn } from "@/lib/utils"

/** Step-up 1회 연장 시 추가되는 시간(분) — BE jwt.step-up-expiration-minutes 와 동일 */
const STEP_UP_EXTENSION_MINUTES = 15

const defaultNavItems = [
  { key: "dashboard", label: "대시보드", href: "/main" },
  { key: "cases", label: "사건 관리", href: "/mypage" },
  { key: "compare", label: "비교검증", href: "/compare" },
  { key: "reports", label: "보고서", href: "/reports" },
]

const adminNavItems = [
  { key: "admin-dashboard", label: "대시보드", href: "/admin" },
  { key: "admin-users", label: "계정 관리", href: "/admin/users" },
  { key: "admin-evidences", label: "증거 관리", href: "/admin/evidences" },
  { key: "admin-invite-codes", label: "생성코드", href: "/admin/invite-codes" },
  { key: "admin-logs", label: "로그", href: "/admin/logs" },
  { key: "admin-profile", label: "내 정보", href: "/admin/profile" },
]

type SiteHeaderProps = {
  minimal?: boolean
  variant?: "default" | "admin" | "minimal"
}

export function SiteHeader({
  minimal = false,
  variant = minimal ? "minimal" : "default",
}: SiteHeaderProps) {
  const pathname = usePathname()
  const [hash, setHash] = useState("")
  const [session, setSessionState] = useState<AuthSession | null>(() => getSession())
  const [stepUpRemainingSeconds, setStepUpRemainingSeconds] = useState(0)
  const [extendNoticeOpen, setExtendNoticeOpen] = useState(false)
  const [extendLoading, setExtendLoading] = useState(false)
  const [extendError, setExtendError] = useState<string | null>(null)
  const showNav = variant !== "minimal"
  const showAuth = variant !== "minimal"
  const activeKey = getActiveNavKey(pathname, hash)
  const brandHref = variant === "admin" ? "/admin" : "/main"
  const navItems =
    variant === "admin" || !isReviewerSession(session)
      ? variant === "admin"
        ? adminNavItems
        : defaultNavItems
      : defaultNavItems.filter((item) => item.key !== "compare")

  useEffect(() => {
    function syncSession() {
      setSessionState(getSession())
    }

    syncSession()
    window.addEventListener("auth-change", syncSession)
    return () => window.removeEventListener("auth-change", syncSession)
  }, [])

  useEffect(() => {
    function syncHash() {
      setHash(window.location.hash)
    }

    syncHash()
    window.addEventListener("hashchange", syncHash)
    window.addEventListener("popstate", syncHash)
    const interval = window.setInterval(syncHash, 150)

    return () => {
      window.removeEventListener("hashchange", syncHash)
      window.removeEventListener("popstate", syncHash)
      window.clearInterval(interval)
    }
  }, [pathname])

  useEffect(() => {
    if (features.mockApi) {
      setStepUpRemainingSeconds(0)
      return
    }

    function syncStepUpRemaining() {
      if (!isStepUpValid()) {
        setStepUpRemainingSeconds(0)
        return
      }

      const remaining = getStepUpRemainingSeconds()
      if (remaining <= 0) {
        clearStepUpToken()
        setStepUpRemainingSeconds(0)
        return
      }

      setStepUpRemainingSeconds(remaining)
    }

    syncStepUpRemaining()
    window.addEventListener(STEP_UP_CHANGE_EVENT, syncStepUpRemaining)
    const interval = window.setInterval(syncStepUpRemaining, 1000)

    return () => {
      window.removeEventListener(STEP_UP_CHANGE_EVENT, syncStepUpRemaining)
      window.clearInterval(interval)
    }
  }, [])

  function handleMainNavigation(event: MouseEvent<HTMLAnchorElement>, href: string) {
    if (!href.startsWith("/main")) return
    if (pathname !== "/main") return

    event.preventDefault()
    window.history.pushState(null, "", href)

    const nextHash = href.includes("#") ? `#${href.split("#")[1]}` : ""
    const nextView = nextHash === "#new-analysis" ? "analysis" : "dashboard"

    setHash(nextHash)

    function dispatchMainViewChange() {
      window.dispatchEvent(new CustomEvent("main-view-change", { detail: { view: nextView } }))
      window.dispatchEvent(new Event("hashchange"))
    }

    dispatchMainViewChange()
    window.setTimeout(dispatchMainViewChange, 0)
  }

  async function handleExtendStepUp() {
    if (!isStepUpValid()) return

    if (!canExtendStepUpSession()) {
      setExtendError(null)
      setExtendNoticeOpen(true)
      return
    }

    setExtendLoading(true)
    setExtendError(null)
    try {
      await extendStepUpSession()
      setExtendNoticeOpen(false)
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.message
          : error instanceof Error && error.message.trim()
            ? error.message
            : "세션 연장에 실패했습니다. 잠시 후 다시 시도해 주세요."
      setExtendError(message)
      setExtendNoticeOpen(true)
    } finally {
      setExtendLoading(false)
    }
  }

  return (
    <header className="sticky top-0 z-50 border-b border-slate-200 bg-[#f8fbfd]/95 backdrop-blur-sm dark:border-border dark:bg-background/90">
      <div className="mx-auto grid h-20 max-w-[1280px] grid-cols-[1fr_auto] items-center gap-4 px-4 sm:h-24 sm:px-6 lg:grid-cols-[1fr_auto_1fr] lg:px-8">
        <Link
          href={brandHref}
          onClick={(event) => handleMainNavigation(event, brandHref)}
          className="flex min-w-0 items-center gap-4"
        >
          <div className="flex size-14 shrink-0 items-center justify-center rounded-xl border border-sky-200 bg-sky-100 text-sky-700 shadow-sm dark:border-primary/30 dark:bg-primary/15 dark:text-primary">
            <ShieldCheck className="size-7" aria-hidden="true" />
          </div>
          <div className="flex flex-col leading-tight">
            <span className="font-mono text-base font-bold tracking-tight text-slate-900 dark:text-foreground">
              ForenShield AI
            </span>
            <span className="mt-1 text-xs font-medium text-slate-500 dark:text-muted-foreground">
              AI Forensic Evidence
            </span>
          </div>
        </Link>

        {showNav && (
          <nav
            className={cn(
              "hidden items-center",
              variant === "admin" ? "md:flex" : "lg:flex",
              variant === "admin" ? "gap-5" : "gap-6"
            )}
            aria-label="주 메뉴"
          >
            {navItems.map((item) => {
              const isActive = activeKey === item.key
              const className = cn(
                "whitespace-nowrap font-semibold transition-colors hover:text-slate-950 dark:hover:text-foreground",
                variant === "admin" ? "text-sm" : "text-base",
                isActive
                  ? "font-bold text-slate-950 dark:text-foreground"
                  : "text-slate-500 dark:text-muted-foreground"
              )

              return item.href.includes("#") ? (
                <a
                  key={item.label}
                  href={item.href}
                  className={className}
                  onClick={(event) => handleMainNavigation(event, item.href)}
                >
                  {item.label}
                </a>
              ) : (
                <Link
                  key={item.label}
                  href={item.href}
                  className={className}
                  onClick={(event) => handleMainNavigation(event, item.href)}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>
        )}

        <div className={cn("flex items-center justify-end gap-3", !showNav && "lg:col-start-3")}>
          <Badge
            variant="outline"
            className="hidden h-8 gap-1.5 rounded-full border-sky-200 bg-sky-50 px-4 text-sm font-bold text-sky-700 sm:flex dark:border-primary/30 dark:bg-primary/10 dark:text-primary"
          >
            <Lock className="size-4" aria-hidden="true" />
            {variant === "admin" ? "관리자 전용" : "내부망 전용"}
          </Badge>
          {showAuth && stepUpRemainingSeconds > 0 ? (
            <div className="hidden items-center gap-1.5 sm:flex">
              <Badge
                variant="outline"
                className="h-8 gap-1.5 rounded-full border-emerald-200 bg-emerald-50 px-3 text-xs font-bold text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300"
                title="Step-up 재인증 남은 시간"
              >
                <ShieldCheck className="size-3.5" aria-hidden="true" />
                {formatStepUpRemaining(stepUpRemainingSeconds)}
              </Badge>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={extendLoading}
                onClick={() => void handleExtendStepUp()}
                className="h-8 rounded-full border-emerald-200 bg-emerald-50 px-3 text-xs font-bold text-emerald-700 hover:bg-emerald-100 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300 dark:hover:bg-emerald-500/20"
              >
                {extendLoading ? "연장 중..." : "연장"}
              </Button>
            </div>
          ) : null}
          {showAuth && <SiteHeaderAuth />}
        </div>
      </div>

      {extendNoticeOpen ? (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/40 px-4">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="step-up-extend-notice-title"
            className="w-full max-w-sm rounded-xl border border-border bg-card p-5 shadow-lg"
          >
            <div className="flex items-start gap-3">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-amber-500/15 text-amber-600 dark:text-amber-300">
                <Clock className="size-5" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <h2 id="step-up-extend-notice-title" className="text-base font-bold text-foreground">
                  {extendError ? "연장 실패" : "아직 연장할 수 없습니다"}
                </h2>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">
                  {extendError ??
                    `민감 정보 조회 세션은 남은 시간이 ${Math.floor(
                      STEP_UP_EXTEND_THRESHOLD_SECONDS / 60
                    )}분 이하일 때만 ${STEP_UP_EXTENSION_MINUTES}분 연장할 수 있습니다.`}
                </p>
              </div>
            </div>
            <Button
              type="button"
              className="mt-5 w-full"
              onClick={() => {
                setExtendNoticeOpen(false)
                setExtendError(null)
              }}
            >
              확인
            </Button>
          </div>
        </div>
      ) : null}
    </header>
  )
}

function getActiveNavKey(pathname: string, hash: string) {
  if (pathname === "/admin") return "admin-dashboard"
  if (pathname.startsWith("/admin/users")) return "admin-users"
  if (pathname.startsWith("/admin/evidences")) return "admin-evidences"
  if (pathname.startsWith("/admin/invite-codes")) return "admin-invite-codes"
  if (pathname.startsWith("/admin/logs")) return "admin-logs"
  if (pathname.startsWith("/admin/profile")) return "admin-profile"
  if (pathname === "/mypage") return "cases"
  if (pathname.startsWith("/cases") || pathname.startsWith("/evidences")) return "cases"
  if (pathname.startsWith("/compare")) return "compare"
  if (pathname.startsWith("/reports")) return "reports"
  if (pathname !== "/main") return ""
  if (hash === "#new-analysis") return "analysis"

  return "dashboard"
}

function formatStepUpRemaining(totalSeconds: number) {
  if (totalSeconds >= 60) {
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}:${String(seconds).padStart(2, "0")}`
  }
  return `${totalSeconds}초`
}
