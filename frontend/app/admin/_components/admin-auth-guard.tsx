"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { bootstrapAuthSession, getSession, type AuthSession } from "@/lib/auth"
import { normalizeUserRole } from "@/lib/permissions"

export function AdminAuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const [session, setAuthSession] = useState<AuthSession | null>(null)

  useEffect(() => {
    let cancelled = false

    async function syncSession() {
      await bootstrapAuthSession()
      if (cancelled) return

      const current = getSession()

      if (!current) {
        router.replace("/login")
        return
      }

      if (normalizeUserRole(current.role) !== "ORG_ADMIN") {
        router.replace("/main")
        return
      }

      setAuthSession(current)
    }

    const onAuthChange = () => {
      void syncSession()
    }

    void syncSession()
    window.addEventListener("auth-change", onAuthChange)
    return () => {
      cancelled = true
      window.removeEventListener("auth-change", onAuthChange)
    }
  }, [router])

  if (!session || normalizeUserRole(session.role) !== "ORG_ADMIN") {
    return null
  }

  return <>{children}</>
}
