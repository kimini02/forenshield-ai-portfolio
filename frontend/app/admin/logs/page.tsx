"use client"

import { useCallback, useEffect, useState } from "react"
import { Download, Loader2, Search } from "lucide-react"
import { AdminPageHeader } from "@/app/admin/_components/admin-page-header"
import { useAdminToast } from "@/app/admin/_components/admin-toast-provider"
import type { AdminLog, LogCategory } from "@/app/admin/_types/admin"
import { exportAdminLogsCsv, fetchAdminLogs } from "@/lib/api/admin"
import { getApiErrorMessage } from "@/lib/api/errors"
import { Button } from "@/components/ui/button"

const PAGE_SIZE = 10

type LogFilterTab = "ALL" | "LOGIN" | "ANALYSIS" | "UPLOAD" | "SIGNUP" | "SECURITY"

const filterTabs: { key: LogFilterTab; label: string }[] = [
  { key: "ALL", label: "전체" },
  { key: "LOGIN", label: "로그인" },
  { key: "ANALYSIS", label: "분석요청" },
  { key: "UPLOAD", label: "파일업로드" },
  { key: "SIGNUP", label: "가입요청" },
  { key: "SECURITY", label: "보안감지" },
]

function getFilterParams(tab: LogFilterTab): {
  category?: LogCategory | "ALL"
  search?: string
} {
  switch (tab) {
    case "LOGIN":
      return { category: "AUTH", search: "로그인" }
    case "ANALYSIS":
      return { category: "ANALYSIS" }
    case "UPLOAD":
      return { category: "COC", search: "업로드" }
    case "SIGNUP":
      return { category: "AUTH", search: "가입" }
    case "SECURITY":
      return { category: "SECURITY" }
    default:
      return { category: "ALL" }
  }
}

function formatLogId(id: string) {
  const numeric = id.replace(/\D/g, "")
  return `LOG-${numeric.padStart(5, "0")}`
}

function getTypeBadge(action: string, category: LogCategory) {
  const label = action
  if (action.includes("로그인") || action.includes("로그아웃")) {
    return { label, className: "border-sky-200 bg-sky-50 text-sky-700" }
  }
  if (category === "SECURITY" || action.includes("캡처") || action.includes("캡쳐") || action.includes("보안")) {
    return { label, className: "border-red-200 bg-red-50 text-red-700" }
  }
  if (category === "ANALYSIS" || action.includes("분석")) {
    return { label, className: "border-violet-200 bg-violet-50 text-violet-700" }
  }
  if (action.includes("가입")) {
    return { label, className: "border-amber-200 bg-amber-50 text-amber-700" }
  }
  if (action.includes("업로드")) {
    return { label, className: "border-emerald-200 bg-emerald-50 text-emerald-700" }
  }
  return { label, className: "border-slate-200 bg-slate-50 text-slate-600" }
}

function formatUserCell(log: AdminLog) {
  const secondaryLabel = log.actorName?.trim() || `ID ${log.actorId}`

  return (
    <div>
      <p className="font-medium text-slate-900">{log.actor}</p>
      <p className="text-xs text-slate-500">{secondaryLabel}</p>
    </div>
  )
}

export default function AdminLogsPage() {
  const [tab, setTab] = useState<LogFilterTab>("ALL")
  const [search, setSearch] = useState("")
  const [page, setPage] = useState(0)
  const [logs, setLogs] = useState<AdminLog[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [exporting, setExporting] = useState(false)
  const { toast } = useAdminToast()

  const loadLogs = useCallback(async () => {
    setLoading(true)
    try {
      const filter = getFilterParams(tab)
      const response = await fetchAdminLogs({
        category: filter.category,
        search: search.trim() || filter.search,
        page,
        size: PAGE_SIZE,
      })
      setLogs(response.items)
      setTotal(response.total)
    } catch (error) {
      const message = getApiErrorMessage(error, "로그 목록을 불러오지 못했습니다.")
      toast({ title: "조회 실패", description: message })
    } finally {
      setLoading(false)
    }
  }, [page, search, tab, toast])

  useEffect(() => {
    void loadLogs()
  }, [loadLogs])

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  async function handleExportCsv() {
    setExporting(true)
    try {
      const filter = getFilterParams(tab)
      const blob = await exportAdminLogsCsv({
        category: filter.category,
        search: search.trim() || filter.search,
      })
      const url = URL.createObjectURL(blob)
      const link = document.createElement("a")
      link.href = url
      link.download = `admin-logs-${new Date().toISOString().slice(0, 10)}.csv`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      toast({
        title: "CSV보내기 완료",
        description: "현재 필터 조건의 로그가 다운로드되었습니다.",
      })
    } catch (error) {
      const message = getApiErrorMessage(error, "CSV보내기에 실패했습니다.")
      toast({ title: "보내기 실패", description: message })
    } finally {
      setExporting(false)
    }
  }

  return (
    <>
      <AdminPageHeader
        title="로그 관리"
        description="시스템 접속 및 활동 감사 로그"
        action={
          <Button
            variant="outline"
            disabled={exporting || loading}
            onClick={() => void handleExportCsv()}
          >
            {exporting ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Download className="size-4" />
            )}
            로그보내기
          </Button>
        }
      />

      <div className="px-8 py-6">
        <div className="mb-4 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="relative max-w-md flex-1">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" />
            <input
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              placeholder="사용자, 이벤트, 내용 검색..."
              className="h-10 w-full rounded-lg border border-slate-200 bg-white pr-3 pl-10 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20"
            />
          </div>
          <div className="flex flex-wrap gap-2">
            {filterTabs.map((item) => (
              <button
                key={item.key}
                type="button"
                onClick={() => {
                  setTab(item.key)
                  setPage(0)
                }}
                className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
                  tab === item.key
                    ? "bg-teal-600 text-white"
                    : "border border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>

        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <table className="w-full min-w-[960px] text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50 text-left text-xs text-slate-500">
                <th className="px-5 py-3 font-medium">로그 ID</th>
                <th className="px-5 py-3 font-medium">유형</th>
                <th className="px-5 py-3 font-medium">사용자</th>
                <th className="px-5 py-3 font-medium">시각</th>
                <th className="px-5 py-3 font-medium">상세 내용</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={5} className="py-16 text-center">
                    <Loader2 className="mx-auto size-5 animate-spin text-slate-400" />
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-16 text-center text-slate-400">
                    조건에 맞는 로그가 없습니다.
                  </td>
                </tr>
              ) : (
                logs.map((log) => {
                  const badge = getTypeBadge(log.action, log.category)
                  return (
                    <tr key={log.id} className="border-b border-slate-50 last:border-0">
                      <td className="px-5 py-4 font-mono text-xs text-slate-500">
                        {formatLogId(log.id)}
                      </td>
                      <td className="px-5 py-4">
                        <span
                          className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium ${badge.className}`}
                        >
                          {badge.label}
                        </span>
                      </td>
                      <td className="px-5 py-4">{formatUserCell(log)}</td>
                      <td className="px-5 py-4 font-mono text-xs text-slate-500">
                        {log.timestamp}
                      </td>
                      <td className="px-5 py-4 text-slate-600">{log.detail ?? log.action}</td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>

        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <span>
            총 {total}건 · {page + 1}/{totalPages} 페이지
          </span>
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              disabled={page <= 0 || loading}
              onClick={() => setPage((p) => p - 1)}
            >
              이전
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={page + 1 >= totalPages || loading}
              onClick={() => setPage((p) => p + 1)}
            >
              다음
            </Button>
          </div>
        </div>
      </div>
    </>
  )
}
