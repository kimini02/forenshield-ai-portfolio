"use client"

import { useEffect, useState } from "react"

import { DashboardOverview } from "@/app/main/_components/dashboard-overview"
import { ReviewerDashboardOverview } from "@/app/main/_components/reviewer-dashboard-overview"
import { getSession, isReviewerSession, type AuthSession } from "@/lib/auth"

export function MainDashboardRouter() {
  const [session, setSession] = useState<AuthSession | null>(() => getSession())

  useEffect(() => {
    function syncSession() {
      setSession(getSession())
    }

    syncSession()
    window.addEventListener("auth-change", syncSession)
    return () => window.removeEventListener("auth-change", syncSession)
  }, [])

  if (isReviewerSession(session)) {
    return <ReviewerDashboardOverview />
  }

  return <DashboardOverview />
}
