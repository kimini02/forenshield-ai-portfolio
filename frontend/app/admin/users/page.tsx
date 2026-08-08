"use client"

import { useCallback, useEffect, useState } from "react"
import {
  Loader2,
  Search,
  User,
  MoreVertical,
  Pencil,
  UserX,
} from "lucide-react"
import { AdminPageHeader } from "@/app/admin/_components/admin-page-header"
import { useAdminToast } from "@/app/admin/_components/admin-toast-provider"
import { DeleteUserDialog } from "@/app/admin/_components/delete-user-dialog"
import {
  EditUserDialog,
  type UserEditPayload,
} from "@/app/admin/_components/edit-user-dialog"
import type { AdminUser, UserStatus } from "@/app/admin/_types/admin"
import {
  approveAdminUser,
  fetchAdminUsers,
  reactivateAdminUser,
  rejectAdminUser,
  resetAdminUserPassword,
  suspendAdminUser,
  updateAdminUser,
} from "@/lib/api/admin"
import { getApiErrorMessage } from "@/lib/api/errors"
import { getRoleLabel } from "@/lib/permissions"
import { Button } from "@/components/ui/button"

const PAGE_SIZE = 10

function formatUserId(username: string) {
  const digits = username.replace(/\D/g, "")
  if (digits.length >= 4) {
    return `EMP-${digits.slice(-6).padStart(6, "0")}`
  }
  return username.toUpperCase()
}

function StatusPill({ status }: { status: UserStatus }) {
  if (status === "APPROVED") {
    return (
      <span className="inline-flex items-center gap-1.5 text-sm text-emerald-700">
        <span className="size-2 rounded-full bg-emerald-500" />
        활성
      </span>
    )
  }
  if (status === "SUSPENDED") {
    return (
      <span className="inline-flex items-center gap-1.5 text-sm text-red-600">
        <span className="size-2 rounded-full bg-red-500" />
        정지
      </span>
    )
  }
  if (status === "PENDING") {
    return (
      <span className="inline-flex items-center gap-1.5 text-sm text-amber-700">
        <span className="size-2 rounded-full bg-amber-500" />
        대기
      </span>
    )
  }
  return (
    <span className="inline-flex items-center gap-1.5 text-sm text-slate-500">
      <span className="size-2 rounded-full bg-slate-400" />
      반려
    </span>
  )
}

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [processingId, setProcessingId] = useState<string | null>(null)
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState<"ALL" | UserStatus>("APPROVED")
  const [page, setPage] = useState(0)
  const [deleteTarget, setDeleteTarget] = useState<AdminUser | null>(null)
  const [editTarget, setEditTarget] = useState<AdminUser | null>(null)
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null)
  const { toast } = useAdminToast()

  const loadUsers = useCallback(async () => {
    setLoading(true)
    try {
      const response = await fetchAdminUsers({
        search,
        status: statusFilter,
        page,
        size: PAGE_SIZE,
      })
      setUsers(response.items)
      setTotal(response.total)
    } catch (error) {
      const message = getApiErrorMessage(error, "계정 목록을 불러오지 못했습니다.")
      toast({ title: "조회 실패", description: message })
    } finally {
      setLoading(false)
    }
  }, [page, search, statusFilter, toast])

  useEffect(() => {
    void loadUsers()
  }, [loadUsers])

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  async function handleEdit(payload: UserEditPayload) {
    if (!editTarget) return
    setProcessingId(`${editTarget.id}-EDIT`)
    try {
      await updateAdminUser(editTarget.id, {
        displayName: payload.displayName,
        email: payload.email,
        organizationType: payload.organizationType,
        department: payload.department,
        role: payload.role,
        status: payload.status,
      })
      if (payload.status && payload.status !== editTarget.status) {
        if (editTarget.status === "PENDING" && payload.status === "APPROVED") {
          await approveAdminUser(editTarget.id, payload.role)
        } else if (editTarget.status === "PENDING" && payload.status === "REJECTED") {
          await rejectAdminUser(editTarget.id)
        } else if (editTarget.status === "APPROVED" && payload.status === "SUSPENDED") {
          await suspendAdminUser(editTarget.id)
        } else if (
          (editTarget.status === "SUSPENDED" || editTarget.status === "REJECTED") &&
          payload.status === "APPROVED"
        ) {
          await reactivateAdminUser(editTarget.id)
        } else {
          throw new Error("백엔드에서 지원하지 않는 계정 상태 변경입니다.")
        }
      }
      if (payload.newPassword) {
        await resetAdminUserPassword(editTarget.id, payload.newPassword)
      }
      toast({
        title: "계정 정보 수정 완료",
        description: `${editTarget.displayName} 계정이 수정되었습니다.`,
      })
      setEditTarget(null)
      await loadUsers()
    } catch (error) {
      const message = getApiErrorMessage(error, "계정 수정 중 오류가 발생했습니다.")
      toast({ title: "수정 실패", description: message })
    } finally {
      setProcessingId(null)
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return
    setProcessingId(`${deleteTarget.id}-DELETE`)
    try {
      await suspendAdminUser(deleteTarget.id)
      toast({
        title: "계정 비활성 처리 완료",
        description: `${deleteTarget.displayName} 계정의 로그인과 업무 처리가 제한됩니다.`,
      })
      setDeleteTarget(null)
      await loadUsers()
    } catch (error) {
      const message = getApiErrorMessage(error, "계정 비활성 처리 중 오류가 발생했습니다.")
      toast({ title: "처리 실패", description: message })
    } finally {
      setProcessingId(null)
    }
  }

  return (
    <>
      <AdminPageHeader
        title="계정 관리"
        description="시스템에 등록된 사용자 계정을 관리합니다."
      />

      <div className="px-8 py-6">
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative max-w-md flex-1">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" />
            <input
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              placeholder="이름, ID, 이메일 검색..."
              className="h-10 w-full rounded-lg border border-slate-200 bg-white pr-3 pl-10 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20"
            />
          </div>
          <div className="flex items-center gap-3">
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as "ALL" | UserStatus)
                setPage(0)
              }}
              className="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20"
            >
              <option value="APPROVED">활성</option>
              <option value="SUSPENDED">정지</option>
              <option value="ALL">전체</option>
            </select>
            <span className="text-sm text-slate-500">
              총 <strong className="text-slate-900">{total}</strong>명
            </span>
          </div>
        </div>

        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <table className="w-full min-w-[900px] text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50 text-left text-xs text-slate-500">
                <th className="px-5 py-3 font-medium">사용자</th>
                <th className="px-5 py-3 font-medium">소속</th>
                <th className="px-5 py-3 font-medium">역할</th>
                <th className="px-5 py-3 font-medium">상태</th>
                <th className="px-5 py-3 font-medium">가입일</th>
                <th className="px-5 py-3 font-medium text-right">관리</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-16 text-center">
                    <Loader2 className="mx-auto size-5 animate-spin text-slate-400" />
                  </td>
                </tr>
              ) : users.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-16 text-center text-slate-400">
                    조건에 맞는 계정이 없습니다.
                  </td>
                </tr>
              ) : (
                users.map((user) => (
                  <tr key={user.id} className="border-b border-slate-50 last:border-0">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="flex size-9 items-center justify-center rounded-full bg-slate-100 text-slate-500">
                          <User className="size-4" />
                        </div>
                        <div>
                          <p className="font-semibold text-slate-900">{user.displayName}</p>
                          <p className="text-xs text-slate-500">
                            {formatUserId(user.username)} · {user.email}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-4 text-slate-700">
                      <div>
                        <p>{user.department || "-"}</p>
                        {user.organizationName ? (
                          <p className="mt-0.5 text-xs text-slate-400">{user.organizationName}</p>
                        ) : null}
                      </div>
                    </td>
                    <td className="px-5 py-4 text-slate-700">
                      {getRoleLabel(user.role)}
                    </td>
                    <td className="px-5 py-4">
                      <StatusPill status={user.status} />
                    </td>
                    <td className="px-5 py-4 text-slate-500">{user.joinedAt}</td>
                    <td className="px-5 py-4">
                      <div className="flex items-center justify-end gap-2">
                        <div className="relative">
                          <button
                            type="button"
                            className="rounded-lg border border-slate-200 p-2 text-slate-500 hover:bg-slate-50"
                            onClick={() =>
                              setMenuOpenId((current) => (current === user.id ? null : user.id))
                            }
                          >
                            <MoreVertical className="size-4" />
                          </button>
                          {menuOpenId === user.id ? (
                            <div className="absolute right-0 z-10 mt-1 w-36 rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
                              <button
                                type="button"
                                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-slate-50"
                                onClick={() => {
                                  setEditTarget(user)
                                  setMenuOpenId(null)
                                }}
                              >
                                <Pencil className="size-3.5" />
                                수정
                              </button>
                              {user.status === "APPROVED" ? (
                                <button
                                  type="button"
                                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-600 hover:bg-red-50"
                                  onClick={() => {
                                    setDeleteTarget(user)
                                    setMenuOpenId(null)
                                  }}
                                >
                                  <UserX className="size-3.5" />
                                  비활성 처리
                                </button>
                              ) : null}
                            </div>
                          ) : null}
                        </div>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <span>
            {page + 1}/{totalPages} 페이지
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

      <EditUserDialog
        user={editTarget}
        open={editTarget !== null}
        loading={processingId?.endsWith("-EDIT") ?? false}
        onSave={handleEdit}
        onCancel={() => setEditTarget(null)}
      />

      <DeleteUserDialog
        username={deleteTarget?.username ?? ""}
        open={deleteTarget !== null}
        loading={processingId?.endsWith("-DELETE") ?? false}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </>
  )
}
