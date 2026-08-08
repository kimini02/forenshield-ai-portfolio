"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Loader2 } from "lucide-react"
import { useAdminToast } from "@/app/admin/_components/admin-toast-provider"
import type { AdminProfile } from "@/app/admin/_types/admin"
import {
  fetchAdminProfile,
  updateAdminPassword,
  updateAdminProfile,
} from "@/lib/api/admin"
import { getApiErrorMessage } from "@/lib/api/errors"

const inputClassName =
  "h-9 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"

const emptyProfile: AdminProfile = {
  username: "",
  displayName: "",
  email: "",
  department: "",
  phone: "",
  role: "",
}

export default function AdminProfilePage() {
  const [profile, setProfile] = useState<AdminProfile>(emptyProfile)
  const [initialProfile, setInitialProfile] = useState<AdminProfile>(emptyProfile)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [changingPassword, setChangingPassword] = useState(false)
  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [passwordError, setPasswordError] = useState("")
  const [profileError, setProfileError] = useState("")
  const { toast } = useAdminToast()

  useEffect(() => {
    async function loadProfile() {
      setLoading(true)
      try {
        const response = await fetchAdminProfile()
        setProfile(response)
        setInitialProfile(response)
      } catch (error) {
        const message = getApiErrorMessage(error, "프로필을 불러오지 못했습니다.")
        toast({ title: "조회 실패", description: message })
      } finally {
        setLoading(false)
      }
    }

    void loadProfile()
  }, [toast])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setProfileError("")

    if (!/^[a-z0-9_]{4,20}$/.test(profile.username)) {
      setProfileError("아이디는 4~20자의 영문 소문자, 숫자, 밑줄(_)만 사용할 수 있습니다.")
      return
    }

    setSaving(true)
    try {
      const updated = await updateAdminProfile({
        username: profile.username,
        displayName: profile.displayName,
        email: profile.email,
        department: profile.department,
        phone: profile.phone,
      })
      setProfile(updated)
      setInitialProfile(updated)
      toast({
        title: "저장 완료",
        description: "관리자 개인정보가 저장되었습니다.",
      })
    } catch (error) {
      const message = getApiErrorMessage(error, "프로필 저장 중 오류가 발생했습니다.")
      setProfileError(message)
    } finally {
      setSaving(false)
    }
  }

  async function handlePasswordChange(e: React.FormEvent) {
    e.preventDefault()
    setPasswordError("")

    if (newPassword.length < 8) {
      setPasswordError("새 비밀번호는 8자 이상이어야 합니다.")
      return
    }
    if (newPassword !== confirmPassword) {
      setPasswordError("새 비밀번호가 일치하지 않습니다.")
      return
    }

    setChangingPassword(true)
    try {
      await updateAdminPassword(currentPassword, newPassword)
      toast({
        title: "비밀번호 변경 완료",
        description: "관리자 비밀번호가 변경되었습니다.",
      })
      setCurrentPassword("")
      setNewPassword("")
      setConfirmPassword("")
    } catch (error) {
      const message = getApiErrorMessage(error, "비밀번호 변경 중 오류가 발생했습니다.")
      setPasswordError(message)
    } finally {
      setChangingPassword(false)
    }
  }

  if (loading) {
    return (
      <main className="mx-auto flex max-w-3xl items-center justify-center px-4 py-24">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-3xl space-y-8 px-4 py-8 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          관리자 개인정보 수정
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          기본 정보와 비밀번호를 분리해서 관리합니다.
        </p>
      </div>

      <form
        onSubmit={handleSubmit}
        className="space-y-5 rounded-xl border border-border bg-card p-6"
      >
        <div className="space-y-1.5">
          <label htmlFor="username" className="text-sm font-medium text-foreground">
            아이디
          </label>
          <input
            id="username"
            value={profile.username}
            onChange={(e) => setProfile({ ...profile, username: e.target.value })}
            className={`${inputClassName} font-mono`}
            required
          />
          <p className="text-xs text-muted-foreground">
            4~20자, 영문 소문자·숫자·밑줄(_)
          </p>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="displayName" className="text-sm font-medium text-foreground">
            이름
          </label>
          <input
            id="displayName"
            value={profile.displayName}
            onChange={(e) => setProfile({ ...profile, displayName: e.target.value })}
            className={inputClassName}
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="email" className="text-sm font-medium text-foreground">
            이메일
          </label>
          <input
            id="email"
            type="email"
            value={profile.email}
            onChange={(e) => setProfile({ ...profile, email: e.target.value })}
            className={inputClassName}
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="department" className="text-sm font-medium text-foreground">
            소속
          </label>
          <input
            id="department"
            value={profile.department}
            onChange={(e) => setProfile({ ...profile, department: e.target.value })}
            className={inputClassName}
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="phone" className="text-sm font-medium text-foreground">
            연락처
          </label>
          <input
            id="phone"
            value={profile.phone}
            onChange={(e) => setProfile({ ...profile, phone: e.target.value })}
            className={inputClassName}
          />
        </div>

        <div className="space-y-1.5">
          <label className="text-sm font-medium text-foreground">역할</label>
          <input value={profile.role} disabled className={inputClassName} />
        </div>

        {profileError && (
          <p role="alert" className="text-sm text-destructive">
            {profileError}
          </p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => setProfile(initialProfile)}
          >
            초기화
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? "저장 중..." : "저장"}
          </Button>
        </div>
      </form>

      <form
        onSubmit={handlePasswordChange}
        className="space-y-5 rounded-xl border border-border bg-card p-6"
      >
        <div>
          <h2 className="text-base font-semibold text-foreground">비밀번호 변경</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            본인 확인 후 새 비밀번호로 변경합니다. 다른 사용자 비밀번호는 계정 관리에서
            재설정하세요.
          </p>
        </div>

        <Separator />

        <div className="space-y-1.5">
          <label htmlFor="current-password" className="text-sm font-medium text-foreground">
            현재 비밀번호
          </label>
          <input
            id="current-password"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            className={inputClassName}
            required
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="new-password" className="text-sm font-medium text-foreground">
            새 비밀번호
          </label>
          <input
            id="new-password"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="8자 이상"
            className={inputClassName}
            required
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="confirm-password" className="text-sm font-medium text-foreground">
            새 비밀번호 확인
          </label>
          <input
            id="confirm-password"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className={inputClassName}
            required
          />
        </div>

        {passwordError && (
          <p role="alert" className="text-sm text-destructive">
            {passwordError}
          </p>
        )}

        <div className="flex justify-end">
          <Button type="submit" disabled={changingPassword}>
            {changingPassword ? "변경 중..." : "비밀번호 변경"}
          </Button>
        </div>
      </form>
    </main>
  )
}
