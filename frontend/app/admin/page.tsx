"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import {
  Users,
  Building2,
  Clock3,
  CheckCircle2,
  Loader2,
  ChevronRight,
} from "lucide-react"
import { AdminPageHeader } from "@/app/admin/_components/admin-page-header"
import { AdminUsageChart } from "@/app/admin/_components/admin-usage-chart"
import {
  fetchAdminDashboardOverview,
  type AdminDashboardOverview,
} from "@/lib/api/admin"
import type { AdminLog } from "@/app/admin/_types/admin"
import { getApiErrorMessage } from "@/lib/api/errors"

const menuItems = [
  {
    href: "/admin/users",
    title: "계정 관리",
    description: "사용자 계정 목록 조회",
    badge: (overview: AdminDashboardOverview) => `${overview.menuBadges.users}건`,
  },
  {
    href: "/admin/approvals",
    title: "승인 관리",
    description: "가입 승인 대기 건 처리",
    badge: (overview: AdminDashboardOverview) => `${overview.menuBadges.approvals}건`,
  },
  {
    href: "/admin/reviews",
    title: "검토 배정",
    description: "검토 요청 사건에 검토자 지정",
    badge: () => "배정",
  },
  {
    href: "/admin/statistics",
    title: "통계 분석",
    description: "분석 통계 및 리포트",
    badge: () => "조회",
  },
  {
    href: "/admin/logs",
    title: "로그 대시보드",
    description: "접속 및 활동 로그 확인",
    badge: (overview: AdminDashboardOverview) => `${overview.menuBadges.logs}건`,
  },
  {
    href: "/admin/invite-codes",
    title: "생성코드",
    description: "가입용 코드 발급 및 관리",
    badge: (overview: AdminDashboardOverview) => `${overview.menuBadges.inviteCodes}개`,
  },
]

function getEventBadge(action: string) {
  if (action.includes("로그인")) {
    return "border-emerald-200 bg-emerald-50 text-emerald-700"
  }
  if (action.includes("분석")) {
    return "border-violet-200 bg-violet-50 text-violet-700"
  }
  if (action.includes("가입")) {
    return "border-amber-200 bg-amber-50 text-amber-700"
  }
  if (action.includes("업로드")) {
    return "border-teal-200 bg-teal-50 text-teal-700"
  }
  return "border-slate-200 bg-slate-50 text-slate-600"
}

function formatLogTime(timestamp: string) {
  const parts = timestamp.split(" ")
  if (parts.length < 2) return timestamp
  return parts[1].slice(0, 5)
}

function formatLogUser(log: AdminLog) {
  return `${log.actor} (${log.actorId})`
}

export default function AdminDashboardPage() {
  const [overview, setOverview] = useState<AdminDashboardOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    async function load() {
      setLoading(true)
      setError("")
      try {
        const data = await fetchAdminDashboardOverview()
        setOverview(data)
      } catch (err) {
        setError(getApiErrorMessage(err, "대시보드 데이터를 불러오지 못했습니다."))
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
          title="관리자 대시보드"
          description="회원, 요청 건수, 로그 및 주요 현황입니다."
        />
        <div className="flex flex-1 items-center justify-center py-24">
          <Loader2 className="size-6 animate-spin text-slate-400" />
        </div>
      </>
    )
  }

  if (!overview) {
    return (
      <>
        <AdminPageHeader title="관리자 대시보드" />
        <div className="px-8 py-8">
          <p className="text-sm text-red-600">{error || "데이터를 불러오지 못했습니다."}</p>
        </div>
      </>
    )
  }

  const statCards = [
    {
      label: "총 가입자 수",
      value: `${overview.approvedUsers}명`,
      sub: "승인 완료",
      icon: Users,
      tone: "text-teal-600 bg-teal-50",
    },
    {
      label: "소속기관 수",
      value: `${overview.departmentCount}개`,
      sub: "등록 기관",
      icon: Building2,
      tone: "text-blue-600 bg-blue-50",
    },
    {
      label: "대기 건수",
      value: `${overview.stats.pendingUsers}건`,
      sub: "승인 대기 중",
      icon: Clock3,
      tone: "text-amber-600 bg-amber-50",
    },
    {
      label: "오늘 분석",
      value: `${overview.todayAnalysis}개`,
      sub: "금일 처리",
      icon: CheckCircle2,
      tone: "text-emerald-600 bg-emerald-50",
    },
  ]

  return (
    <>
      <AdminPageHeader
        title="관리자 대시보드"
        description="회원, 요청 건수, 로그 및 주요 현황입니다."
      />

      <div className="space-y-6 px-8 py-6">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {statCards.map((card) => {
            const Icon = card.icon
            return (
              <div
                key={card.label}
                className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
              >
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-sm text-slate-500">{card.label}</p>
                    <p className="mt-2 text-3xl font-bold text-slate-900">{card.value}</p>
                    <p className="mt-1 text-xs text-slate-400">{card.sub}</p>
                  </div>
                  <div className={`flex size-10 items-center justify-center rounded-lg ${card.tone}`}>
                    <Icon className="size-5" />
                  </div>
                </div>
              </div>
            )
          })}
        </div>

        <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
          <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">시스템 사용 현황</h2>
            <p className="mt-1 text-sm text-slate-500">
              최근 7일 분석 건수 및 가입 신청 추이
            </p>
            <div className="mt-4">
              <AdminUsageChart points={overview.trend} />
            </div>
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">관리 메뉴</h2>
            <div className="mt-4 space-y-2">
              {menuItems.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="flex items-center justify-between rounded-lg border border-slate-100 px-4 py-3 transition-colors hover:bg-slate-50"
                >
                  <div>
                    <p className="text-sm font-semibold text-slate-900">{item.title}</p>
                    <p className="text-xs text-slate-500">{item.description}</p>
                  </div>
                  <span className="rounded-full bg-teal-50 px-2.5 py-1 text-xs font-semibold text-teal-700">
                    {item.badge(overview)}
                  </span>
                </Link>
              ))}
            </div>
          </section>
        </div>

        <section className="rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
            <h2 className="text-base font-bold text-slate-900">최근 활동 로그</h2>
            <Link
              href="/admin/logs"
              className="flex items-center gap-1 text-sm font-medium text-teal-600 hover:text-teal-700"
            >
              전체 보기
              <ChevronRight className="size-4" />
            </Link>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50 text-left text-xs text-slate-500">
                  <th className="px-6 py-3 font-medium">이벤트</th>
                  <th className="px-6 py-3 font-medium">사용자</th>
                  <th className="px-6 py-3 font-medium">시간</th>
                  <th className="px-6 py-3 font-medium">상세</th>
                </tr>
              </thead>
              <tbody>
                {overview.recentLogs.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-6 py-10 text-center text-slate-400">
                      최근 활동 로그가 없습니다.
                    </td>
                  </tr>
                ) : (
                  overview.recentLogs.map((log) => (
                    <tr key={log.id} className="border-b border-slate-50 last:border-0">
                      <td className="px-6 py-3">
                        <span
                          className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium ${getEventBadge(log.action)}`}
                        >
                          {log.action}
                        </span>
                      </td>
                      <td className="px-6 py-3 text-slate-700">{formatLogUser(log)}</td>
                      <td className="px-6 py-3 font-mono text-slate-500">
                        {formatLogTime(log.timestamp)}
                      </td>
                      <td className="px-6 py-3 text-slate-500">{log.detail ?? "-"}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </>
  )
}
