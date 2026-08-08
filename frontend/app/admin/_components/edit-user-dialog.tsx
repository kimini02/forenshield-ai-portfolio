"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import type { AdminUser, UserStatus } from "@/app/admin/_types/admin"
import DepartmentAutocomplete from "@/app/signup/DepartmentAutocomplete"
import { ORG_TYPES, type OrgType } from "@/app/signup/organizationData"
import { roleLabelMap, type UserRole } from "@/lib/permissions"

const inputClassName =
  "h-9 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"

const statusLabels: Record<UserStatus, string> = {
  APPROVED: "활성",
  SUSPENDED: "비활성",
  PENDING: "승인 대기",
  REJECTED: "거부",
}

function getAvailableStatuses(status: UserStatus): UserStatus[] {
  if (status === "PENDING") return ["PENDING", "APPROVED", "REJECTED"]
  if (status === "APPROVED") return ["APPROVED", "SUSPENDED"]
  return [status, "APPROVED"]
}

export type UserEditPayload = {
  displayName: string
  email: string
  organizationType?: OrgType
  department: string
  role?: UserRole
  status?: UserStatus
  newPassword?: string
}

type EditUserDialogProps = {
  user: AdminUser | null
  open: boolean
  loading: boolean
  onSave: (payload: UserEditPayload) => void
  onCancel: () => void
}

export function EditUserDialog({
  user,
  open,
  loading,
  onSave,
  onCancel,
}: EditUserDialogProps) {
  const [displayName, setDisplayName] = useState("")
  const [email, setEmail] = useState("")
  const [organizationType, setOrganizationType] = useState<OrgType | "">("")
  const [department, setDepartment] = useState("")
  const [role, setRole] = useState<UserRole>("INVESTIGATOR")
  const [status, setStatus] = useState<UserStatus>("APPROVED")
  const [resetPassword, setResetPassword] = useState(false)
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [error, setError] = useState("")

  useEffect(() => {
    if (!user) return
    setDisplayName(user.displayName)
    setEmail(user.email)
    setOrganizationType(resolveOrganizationType(user))
    setDepartment(user.department)
    setRole(user.role ?? "INVESTIGATOR")
    setStatus(user.status)
    setResetPassword(false)
    setNewPassword("")
    setConfirmPassword("")
    setError("")
  }, [user])

  if (!open || !user) return null

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError("")

    if (resetPassword) {
      if (newPassword.length < 8) {
        setError("비밀번호는 8자 이상이어야 합니다.")
        return
      }
      if (newPassword !== confirmPassword) {
        setError("새 비밀번호가 일치하지 않습니다.")
        return
      }
    }
    if (!organizationType) {
      setError("기관 유형을 선택해 주세요.")
      return
    }
    if (!department.trim()) {
      setError("소속 기관/부서를 선택해 주세요.")
      return
    }

    onSave({
      displayName,
      email,
      organizationType,
      department,
      role,
      status,
      newPassword: resetPassword ? newPassword : undefined,
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <form
        onSubmit={handleSubmit}
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl border border-border bg-card p-6 shadow-lg"
      >
        <h2 className="text-lg font-semibold text-foreground">계정 정보 수정</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          <span className="font-mono font-medium text-foreground">{user.username}</span>{" "}
          계정의 정보를 수정합니다.
        </p>

        <div className="mt-5 space-y-4">
          <div className="space-y-1.5">
            <label className="text-sm font-medium text-foreground">아이디</label>
            <input value={user.username} disabled className={inputClassName} />
          </div>

          <div className="space-y-1.5">
            <label htmlFor="edit-displayName" className="text-sm font-medium text-foreground">
              이름
            </label>
            <input
              id="edit-displayName"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className={inputClassName}
              required
            />
          </div>

          <div className="space-y-1.5">
            <label htmlFor="edit-email" className="text-sm font-medium text-foreground">
              이메일
            </label>
            <input
              id="edit-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={inputClassName}
              required
            />
          </div>

          <div className="space-y-1.5">
            <label htmlFor="edit-organizationType" className="text-sm font-medium text-foreground">
              기관 유형
            </label>
            <select
              id="edit-organizationType"
              value={organizationType}
              onChange={(e) => {
                setOrganizationType(e.target.value as OrgType)
                setDepartment("")
              }}
              className={inputClassName}
              required
            >
              <option value="" disabled>
                기관 유형 선택
              </option>
              {ORG_TYPES.map((organization) => (
                <option key={organization.value} value={organization.value}>
                  {organization.label}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <span className="text-sm font-medium text-foreground">소속 기관/부서</span>
            <DepartmentAutocomplete
              orgType={organizationType}
              value={department}
              onChange={setDepartment}
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label htmlFor="edit-role" className="text-sm font-medium text-foreground">
                역할
              </label>
              <select
                id="edit-role"
                value={role}
                onChange={(e) => setRole(e.target.value as UserRole)}
                className={inputClassName}
              >
                <option value="INVESTIGATOR">{roleLabelMap.INVESTIGATOR}</option>
                <option value="REVIEWER">{roleLabelMap.REVIEWER}</option>
                <option value="ORG_ADMIN">{roleLabelMap.ORG_ADMIN}</option>
              </select>
            </div>

            <div className="space-y-1.5">
              <label htmlFor="edit-status" className="text-sm font-medium text-foreground">
                상태
              </label>
              <select
                id="edit-status"
                value={status}
                onChange={(e) => setStatus(e.target.value as UserStatus)}
                className={inputClassName}
              >
                {getAvailableStatuses(user.status).map((availableStatus) => (
                  <option key={availableStatus} value={availableStatus}>
                    {statusLabels[availableStatus]}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <Separator className="my-5" />

        <div className="space-y-3">
          <label className="flex items-center gap-2 text-sm font-medium text-foreground">
            <input
              type="checkbox"
              checked={resetPassword}
              onChange={(e) => setResetPassword(e.target.checked)}
              className="size-4 rounded border-border"
            />
            비밀번호 재설정
          </label>
          <p className="text-xs text-muted-foreground">
            관리자가 사용자 비밀번호를 직접 재설정합니다. (API 연동 시 CoC 로그에 기록)
          </p>

          {resetPassword && (
            <div className="space-y-3 rounded-lg border border-border bg-muted/30 p-4">
              <div className="space-y-1.5">
                <label htmlFor="edit-new-password" className="text-sm font-medium text-foreground">
                  새 비밀번호
                </label>
                <input
                  id="edit-new-password"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="8자 이상"
                  className={inputClassName}
                  required={resetPassword}
                />
              </div>
              <div className="space-y-1.5">
                <label
                  htmlFor="edit-confirm-password"
                  className="text-sm font-medium text-foreground"
                >
                  새 비밀번호 확인
                </label>
                <input
                  id="edit-confirm-password"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className={inputClassName}
                  required={resetPassword}
                />
              </div>
            </div>
          )}
        </div>

        {error && (
          <p role="alert" className="mt-4 text-sm text-destructive">
            {error}
          </p>
        )}

        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onCancel} disabled={loading}>
            취소
          </Button>
          <Button type="submit" disabled={loading}>
            {loading ? "저장 중..." : "저장"}
          </Button>
        </div>
      </form>
    </div>
  )
}

const ORG_TYPE_VALUES = new Set<string>(ORG_TYPES.map((organization) => organization.value))

function resolveOrganizationType(user: AdminUser): OrgType | "" {
  const explicit = user.organizationType?.trim().toUpperCase()
  if (explicit && ORG_TYPE_VALUES.has(explicit)) {
    return explicit as OrgType
  }

  const scope = `${user.organizationName ?? ""} ${user.department ?? ""}`.toLowerCase()
  if (scope.includes("검찰") || scope.includes("prosecution")) return "PROSECUTION"
  if (scope.includes("국과수") || scope.includes("감정") || scope.includes("nfs")) return "NFS"
  if (scope.includes("경찰") || scope.includes("police")) return "POLICE"
  if (scope.includes("감사") || scope.includes("보안") || scope.includes("audit")) return "PUBLIC_SECURITY"
  return ""
}
