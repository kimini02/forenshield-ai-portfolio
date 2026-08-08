"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import {
  LayoutDashboard,
  Users,
  Ticket,
  ScrollText,
  UserCog,
  FileStack,
} from "lucide-react"
import { cn } from "@/lib/utils"

const navItems = [
  { href: "/admin", label: "대시보드", icon: LayoutDashboard, exact: true },
  { href: "/admin/users", label: "계정 관리", icon: Users },
  { href: "/admin/evidences", label: "증거 관리", icon: FileStack },
  { href: "/admin/invite-codes", label: "생성코드", icon: Ticket },
  { href: "/admin/logs", label: "로그", icon: ScrollText },
  { href: "/admin/profile", label: "내 정보", icon: UserCog },
]

export function AdminNav() {
  const pathname = usePathname()

  return (
    <nav
      aria-label="관리자 메뉴"
      className="border-b border-border bg-card/50"
    >
      <div className="mx-auto flex max-w-7xl gap-1 overflow-x-auto px-4 sm:px-6 lg:px-8">
        {navItems.map((item) => {
          const isActive = item.exact
            ? pathname === item.href
            : pathname.startsWith(item.href)
          const Icon = item.icon

          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-3 text-sm font-medium transition-colors",
                isActive
                  ? "border-primary text-primary"
                  : "border-transparent text-muted-foreground hover:border-border hover:text-foreground"
              )}
            >
              <Icon className="size-4" aria-hidden="true" />
              {item.label}
            </Link>
          )
        })}
      </div>
    </nav>
  )
}
