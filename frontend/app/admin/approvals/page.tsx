"use client"

import { useCallback, useEffect, useState } from "react"
import { CheckCircle, Loader2, XCircle } from "lucide-react"
import { AdminPageHeader } from "@/app/admin/_components/admin-page-header"
import { useAdminToast } from "@/app/admin/_components/admin-toast-provider"
import type { AdminUser } from "@/app/admin/_types/admin"
import {
  approveAdminUser,
  fetchAdminUsers,
  rejectAdminUser,
} from "@/lib/api/admin"
import { getApiErrorMessage } from "@/lib/api/errors"
import { getRoleLabel, roleLabelMap, type UserRole } from "@/lib/permissions"
import { Button } from "@/components/ui/button"

const HISTORY_PAGE_SIZE = 8

function ResultBadge({ status }: { status: AdminUser["status"] }) {
  if (status === "APPROVED") {
    return (
      <span className="inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700">
        승인됨
      </span>
    )
  }
  return (
    <span className="inline-flex rounded-full border border-rose-200 bg-rose-50 px-2.5 py-0.5 text-xs font-medium text-rose-700">
      거부됨
    </span>
  )
}

export default function AdminApprovalsPage() {
  const [pendingUsers, setPendingUsers] = useState<AdminUser[]>([])
  const [historyUsers, setHistoryUsers] = useState<AdminUser[]>([])
  const [historyTotal, setHistoryTotal] = useState(0)
  const [historyPage, setHistoryPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [processingId, setProcessingId] = useState<string | null>(null)
  const [selectedRoleByUser, setSelectedRoleByUser] = useState<Record<string, UserRole>>({})
  const { toast } = useAdminToast()

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [pending, allUsers] = await Promise.all([
        fetchAdminUsers({ status: "PENDING", page: 0, size: 50 }),
        fetchAdminUsers({ page: 0, size: 200 }),
      ])

      setPendingUsers(pending.items)
      setSelectedRoleByUser((current) => {
        const next = { ...current }
        for (const user of pending.items) {
          next[user.id] = user.role ?? "INVESTIGATOR"
        }
        return next
      })
      const history = allUsers.items
        .filter((user) => user.status === "APPROVED" || user.status === "REJECTED")
        .sort((a, b) => b.joinedAt.localeCompare(a.joinedAt))
      const start = historyPage * HISTORY_PAGE_SIZE
      setHistoryUsers(history.slice(start, start + HISTORY_PAGE_SIZE))
      setHistoryTotal(history.length)
    } catch (error) {
      const message = getApiErrorMessage(error, "승인 목록을 불러오지 못했습니다.")
      toast({ title: "조회 실패", description: message })
    } finally {
      setLoading(false)
    }
  }, [historyPage, toast])

  useEffect(() => {
    void loadData()
  }, [loadData])

  const historyPages = Math.max(1, Math.ceil(historyTotal / HISTORY_PAGE_SIZE))

  async function handleAction(userId: string, action: "APPROVE" | "REJECT") {
    setProcessingId(`${userId}-${action}`)
    try {
      if (action === "APPROVE") {
        await approveAdminUser(userId, selectedRoleByUser[userId] ?? "INVESTIGATOR")
      } else {
        await rejectAdminUser(userId)
      }
      const role = selectedRoleByUser[userId] ?? "INVESTIGATOR"
      toast({
        title: action === "APPROVE" ? "승인 완료" : "거부 완료",
        description:
          action === "APPROVE"
            ? `가입 요청이 ${roleLabelMap[role]} 역할로 승인되었습니다.`
            : "가입 요청이 거부되었습니다.",
      })
      await loadData()
    } catch (error) {
      const message = getApiErrorMessage(error, "요청 처리 중 오류가 발생했습니다.")
      toast({ title: "처리 실패", description: message })
    } finally {
      setProcessingId(null)
    }
  }

  return (
    <>
      <AdminPageHeader
        title="사용자 승인"
        description="가입 신청을 검토하고 역할을 지정해 승인 또는 거부합니다."
      />

      <div className="space-y-6 px-8 py-6">
        <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="mb-4 flex items-center gap-2">
            <h2 className="text-base font-bold text-slate-900">승인 대기</h2>
            <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-700">
              {pendingUsers.length}건
            </span>
          </div>

          {loading ? (
            <div className="flex justify-center py-12">
              <Loader2 className="size-5 animate-spin text-slate-400" />
            </div>
          ) : pendingUsers.length === 0 ? (
            <p className="rounded-lg border border-dashed border-slate-200 py-12 text-center text-sm text-slate-400">
              승인 대기 중인 요청이 없습니다.
            </p>
          ) : (
            <div className="space-y-4">
              {pendingUsers.map((user) => (
                <div
                  key={user.id}
                  className="flex flex-col gap-4 rounded-xl border border-slate-100 bg-slate-50/60 p-5 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="flex-1">
                    <div className="mb-2 flex items-center gap-2">
                      <span className="rounded-full border border-amber-200 bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700">
                        대기 중
                      </span>
                    </div>
                    <h3 className="text-lg font-bold text-slate-900">{user.displayName}</h3>
                    <p className="text-sm text-slate-500">{user.email}</p>
                    <p className="mt-1 text-sm text-slate-600">
                      {user.department || "소속 미입력"}
                    </p>
                    <div className="mt-3 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">
                      시스템 사용 신청 · ID: {user.username}
                    </div>
                    <p className="mt-3 text-xs text-slate-400">신청일: {user.joinedAt}</p>
                  </div>
                  <div className="w-full shrink-0 space-y-3 lg:w-64">
                    <label className="block">
                      <span className="mb-1 block text-xs font-semibold text-slate-500">
                        승인 역할
                      </span>
                      <select
                        value={selectedRoleByUser[user.id] ?? user.role ?? "INVESTIGATOR"}
                        onChange={(event) =>
                          setSelectedRoleByUser((current) => ({
                            ...current,
                            [user.id]: event.target.value as UserRole,
                          }))
                        }
                        className="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20"
                      >
                        <option value="INVESTIGATOR">{roleLabelMap.INVESTIGATOR}</option>
                        <option value="REVIEWER">{roleLabelMap.REVIEWER}</option>
                        <option value="ORG_ADMIN">{roleLabelMap.ORG_ADMIN}</option>
                      </select>
                    </label>
                    <div className="flex gap-2">
                      <Button
                        className="flex-1 bg-teal-600 hover:bg-teal-700"
                        disabled={processingId !== null}
                        onClick={() => void handleAction(user.id, "APPROVE")}
                      >
                        {processingId === `${user.id}-APPROVE` ? (
                          <Loader2 className="size-4 animate-spin" />
                        ) : (
                          <CheckCircle className="size-4" />
                        )}
                        승인
                      </Button>
                      <Button
                        className="flex-1"
                        variant="outline"
                        disabled={processingId !== null}
                        onClick={() => void handleAction(user.id, "REJECT")}
                      >
                        {processingId === `${user.id}-REJECT` ? (
                          <Loader2 className="size-4 animate-spin" />
                        ) : (
                          <XCircle className="size-4" />
                        )}
                        거부
                      </Button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-100 px-6 py-4">
            <h2 className="text-base font-bold text-slate-900">처리 이력</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50 text-left text-xs text-slate-500">
                  <th className="px-6 py-3 font-medium">신청자</th>
                  <th className="px-6 py-3 font-medium">소속</th>
                  <th className="px-6 py-3 font-medium">역할</th>
                  <th className="px-6 py-3 font-medium">신청일</th>
                  <th className="px-6 py-3 font-medium">처리일</th>
                  <th className="px-6 py-3 font-medium">승인자</th>
                  <th className="px-6 py-3 font-medium">결과</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={7} className="py-12 text-center">
                      <Loader2 className="mx-auto size-5 animate-spin text-slate-400" />
                    </td>
                  </tr>
                ) : historyUsers.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-slate-400">
                      처리 이력이 없습니다.
                    </td>
                  </tr>
                ) : (
                  historyUsers.map((user) => (
                    <tr key={user.id} className="border-b border-slate-50 last:border-0">
                      <td className="px-6 py-4">
                        <p className="font-medium text-slate-900">{user.displayName}</p>
                        <p className="text-xs text-slate-500">{user.email}</p>
                      </td>
                      <td className="px-6 py-4 text-slate-700">{user.department || "-"}</td>
                      <td className="px-6 py-4 text-slate-700">
                        {getRoleLabel(user.role)}
                      </td>
                      <td className="px-6 py-4 text-slate-500">{user.joinedAt}</td>
                      <td className="px-6 py-4 text-slate-500">{user.joinedAt}</td>
                      <td className="px-6 py-4 text-slate-700">관리자</td>
                      <td className="px-6 py-4">
                        <ResultBadge status={user.status} />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between border-t border-slate-100 px-6 py-3 text-sm text-slate-500">
            <span>
              {historyPage + 1}/{historyPages} 페이지
            </span>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                disabled={historyPage <= 0 || loading}
                onClick={() => setHistoryPage((p) => p - 1)}
              >
                이전
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={historyPage + 1 >= historyPages || loading}
                onClick={() => setHistoryPage((p) => p + 1)}
              >
                다음
              </Button>
            </div>
          </div>
        </section>
      </div>
    </>
  )
}
