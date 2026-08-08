"use client"

import type { CaseSummary } from "@/app/mypage/_types/case"
import { CaseHistoryList } from "@/app/mypage/_components/case-history-list"
import { useUserSettings } from "@/hooks/use-user-settings"

export function CaseHistorySection({
  cases,
}: {
  cases: CaseSummary[]
}) {
  const { settings } = useUserSettings()

  return (
    <CaseHistoryList cases={cases} dateFormat={settings.dateFormat} />
  )
}
