"use client"

import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import {
  LayoutDashboard,
  Users,
  UserCheck,
  BarChart3,
  Ticket,
  ScrollText,
  ShieldCheck,
  LogOut,
  ClipboardCheck,
  History,
} from "lucide-react"
import { logoutApi } from "@/lib/auth-api"
import { clearSession, getSession, isMockAuthSession } from "@/lib/auth"
import { cn } from "@/lib/utils"

const navItems = [
  { href: "/admin", label: "메인 대시보드", icon: LayoutDashboard, exact: true },
  { href: "/admin/users", label: "계정 관리", icon: Users },
  { href: "/admin/approvals", label: "사용자 승인", icon: UserCheck },
  { href: "/admin/reviews", label: "검토 배정", icon: ClipboardCheck },
  { href: "/admin/statistics", label: "통계 분석", icon: BarChart3 },
  { href: "/admin/logs", label: "로그 관리", icon: ScrollText },
  { href: "/admin/coc", label: "CoC 감사", icon: History },
  { href: "/admin/invite-codes", label: "생성코드", icon: Ticket },
]

type AdminShellProps = {
  children: React.ReactNode
}

export function AdminShell({ children }: AdminShellProps) {
  const pathname = usePathname()
  const router = useRouter()
  const session = getSession()

  async function handleLogout() {
    try {
      if (!isMockAuthSession(session)) {
        await logoutApi()
      }
    } finally {
      clearSession()
      router.replace("/login")
    }
  }

  function isActive(href: string, exact?: boolean) {
    if (exact) return pathname === href
    return pathname === href || pathname.startsWith(`${href}/`)
  }

  return (
    <div className="flex min-h-screen bg-[#f4f7f9]">
      <aside className="flex w-[240px] shrink-0 flex-col border-r border-slate-200 bg-white">
        <div className="border-b border-slate-100 px-5 py-5">
          <div className="flex items-center gap-2.5">
            <div className="flex size-9 items-center justify-center rounded-lg bg-teal-600 text-white">
              <ShieldCheck className="size-5" />
            </div>
            <div>
              <p className="text-sm font-bold tracking-tight text-slate-900">ForenShield AI</p>
              <p className="text-[10px] font-semibold tracking-[0.14em] text-slate-400">
                ADMIN CONSOLE
              </p>
            </div>
          </div>
        </div>

        <div className="mx-4 mt-4 rounded-xl border border-slate-100 bg-slate-50 px-3 py-3">
          <p className="text-sm font-semibold text-slate-900">{session?.name ?? "관리자"}</p>
          <p className="mt-0.5 truncate text-xs text-slate-500">
            {session?.loginId ? `${session.loginId}@forenshield.com` : "admin@forenshield.com"}
          </p>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-4">
          {navItems.map((item) => {
            const Icon = item.icon
            const active = isActive(item.href, item.exact)
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                  active
                    ? "bg-teal-600 text-white shadow-sm"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                )}
              >
                <Icon className="size-4 shrink-0" />
                {item.label}
              </Link>
            )
          })}
        </nav>

        <div className="border-t border-slate-100 p-3">
          <button
            type="button"
            onClick={handleLogout}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
          >
            <LogOut className="size-4" />
            로그아웃
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">{children}</div>
    </div>
  )
}
