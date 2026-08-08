"use client"

import { useEffect, useState } from "react"
import { BarChart3, Loader2 } from "lucide-react"
import { AdminPageHeader } from "@/app/admin/_components/admin-page-header"
import { AdminWeeklyBarChart } from "@/app/admin/_components/admin-weekly-bar-chart"
import { AdminRiskDonutChart } from "@/app/admin/_components/admin-risk-donut-chart"
import { fetchAdminAnalysisStats, type AdminAnalysisStats } from "@/lib/api/admin"
import { getApiErrorMessage } from "@/lib/api/errors"

export default function AdminStatisticsPage() {
  const [stats, setStats] = useState<AdminAnalysisStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    async function load() {
      setLoading(true)
      setError("")
      try {
        const data = await fetchAdminAnalysisStats()
        setStats(data)
      } catch (err) {
        setError(getApiErrorMessage(err, "통계 데이터를 불러오지 못했습니다."))
      } finally {
        setLoading(false)
      }
    }

    void load()
  }, [])

  if (loading) {
    return (
      <>
        <AdminPageHeader
          title="통계 분석"
          description="시스템 분석 현황 및 탐지 통계"
        />
        <div className="flex flex-1 items-center justify-center py-24">
          <Loader2 className="size-6 animate-spin text-slate-400" />
        </div>
      </>
    )
  }

  if (!stats) {
    return (
      <>
        <AdminPageHeader title="통계 분석" />
        <div className="px-8 py-8">
          <p className="text-sm text-red-600">{error || "통계를 불러오지 못했습니다."}</p>
        </div>
      </>
    )
  }

  const summaryCards = [
    {
      label: "이번 주 전체 분석",
      sub: "완료 분석 (COMPLETED)",
      value: `${stats.weeklyTotalCount}건`,
      icon: BarChart3,
    },
    {
      label: "딥페이크 탐지율",
      sub: "이번 주 완료 분석 기준",
      value: `${stats.deepfakeDetectionRate}%`,
      icon: BarChart3,
    },
    {
      label: "평균 분석 시간",
      sub: "이번 주 완료 분석 기준",
      value: `${stats.averageAnalysisMinutes}분`,
      icon: BarChart3,
    },
  ]

  return (
    <>
      <AdminPageHeader
        title="통계 분석"
        description="시스템 분석 현황 및 탐지 통계"
      />

      <div className="space-y-6 px-8 py-6">
        <div className="grid gap-4 md:grid-cols-3">
          {summaryCards.map((card) => {
            const Icon = card.icon
            return (
              <div
                key={card.label}
                className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm"
              >
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-sm text-slate-500">{card.label}</p>
                    <p className="mt-3 text-3xl font-bold text-teal-600">{card.value}</p>
                    <p className="mt-1 text-xs text-slate-400">{card.sub}</p>
                  </div>
                  <div className="flex size-10 items-center justify-center rounded-lg bg-teal-50 text-teal-600">
                    <Icon className="size-5" />
                  </div>
                </div>
              </div>
            )
          })}
        </div>

        <div className="grid gap-6 xl:grid-cols-[1.35fr_1fr]">
          <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">주간 분석 현황 (이번 주)</h2>
            <p className="mt-1 text-sm text-slate-500">일별 분석 요청 및 완료 건수</p>
            <div className="mt-4">
              <AdminWeeklyBarChart points={stats.weeklyPoints} />
            </div>
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">위험도 분포</h2>
            <p className="mt-1 text-sm text-slate-500">완료된 분석 결과 기준</p>
            <div className="mt-4">
              <AdminRiskDonutChart distribution={stats.riskDistribution} />
            </div>
          </section>
        </div>
      </div>
    </>
  )
}
