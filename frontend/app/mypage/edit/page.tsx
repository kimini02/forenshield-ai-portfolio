"use client"

import * as React from "react"
import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { SiteHeader } from "@/components/site-header"
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Button } from "@/components/ui/button"
import { useToast } from "@/components/ui/use-toast"
import { User, ShieldCheck, Lock, ArrowLeft, Loader2 } from "lucide-react"
import { fetchMyProfile, updateMyProfile } from "@/lib/api/user"
import { ApiError } from "@/lib/api/client"
import { getApiErrorMessage, isUnauthorizedError } from "@/lib/api/errors"
import { getSession, setSession } from "@/lib/auth"

export default function EditProfilePage() {
  const router = useRouter()
  const { toast } = useToast()
  const [isLoading, setIsLoading] = useState(false)
  const [isFetching, setIsFetching] = useState(true)

  const [formData, setFormData] = useState({
    username: "",
    email: "",
    department: "",
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  })

  const [errors, setErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    let cancelled = false

    async function loadProfile() {
      setIsFetching(true)
      try {
        const profile = await fetchMyProfile()
        if (cancelled) return
        setFormData((prev) => ({
          ...prev,
          username: profile.loginId,
          email: profile.email,
          department: profile.department,
        }))
      } catch (error) {
        if (cancelled) return
        if (isUnauthorizedError(error)) {
          toast({
            variant: "destructive",
            title: "로그인 필요",
            description: "개인정보를 수정하려면 로그인해 주세요.",
          })
          router.push("/login")
          return
        }
        toast({
          variant: "destructive",
          title: "불러오기 실패",
          description: "프로필 정보를 불러오지 못했습니다.",
        })
      } finally {
        if (!cancelled) {
          setIsFetching(false)
        }
      }
    }

    loadProfile()
    return () => {
      cancelled = true
    }
  }, [router, toast])

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { id, value } = e.target
    setFormData((prev) => ({ ...prev, [id]: value }))
    if (errors[id]) {
      setErrors((prev) => {
        const newErrors = { ...prev }
        delete newErrors[id]
        return newErrors
      })
    }
  }

  const validate = () => {
    const newErrors: Record<string, string> = {}
    if (!formData.username.trim()) newErrors.username = "사용자 이름을 입력해 주세요."
    if (!formData.currentPassword) newErrors.currentPassword = "현재 비밀번호를 입력해 주세요."

    if (formData.newPassword) {
      if (formData.newPassword.length < 8) {
        newErrors.newPassword = "비밀번호는 최소 8자 이상이어야 합니다."
      }
      if (formData.newPassword !== formData.confirmPassword) {
        newErrors.confirmPassword = "새 비밀번호가 일치하지 않습니다."
      }
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return

    setIsLoading(true)
    try {
      const updatedProfile = await updateMyProfile({
        loginId: formData.username.trim(),
        currentPassword: formData.currentPassword,
        newPassword: formData.newPassword || undefined,
      })

      const session = getSession()
      if (session) {
        setSession({
          ...session,
          userId: String(updatedProfile.userId),
          loginId: updatedProfile.loginId,
          name: updatedProfile.name,
        })
      }

      toast({
        title: "수정 완료",
        description: "개인정보가 성공적으로 변경되었습니다.",
      })
      router.push("/mypage")
    } catch (error) {
      if (error instanceof ApiError) {
        const fieldErrors: Record<string, string> = {}
        if (error.errorCode === "INVALID_PASSWORD") {
          fieldErrors.currentPassword = error.message
        } else if (error.errorCode === "DUPLICATE_LOGIN_ID") {
          fieldErrors.username = error.message
        } else if (error.errorCode === "PASSWORD_TOO_SHORT") {
          fieldErrors.newPassword = error.message
        }

        if (Object.keys(fieldErrors).length > 0) {
          setErrors((prev) => ({ ...prev, ...fieldErrors }))
        }
      }

      toast({
        variant: "destructive",
        title: "수정 실패",
        description:
          getApiErrorMessage(error, "정보를 업데이트하는 중 오류가 발생했습니다."),
      })
    } finally {
      setIsLoading(false)
    }
  }

  if (isFetching) {
    return (
      <div className="min-h-screen bg-slate-50/50 dark:bg-background">
        <SiteHeader />
        <main className="mx-auto flex max-w-2xl items-center justify-center px-4 py-24 text-sm text-muted-foreground">
          <Loader2 className="mr-2 size-4 animate-spin" />
          프로필 정보를 불러오는 중...
        </main>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-50/50 dark:bg-background">
      <SiteHeader />

      <main className="mx-auto max-w-2xl px-4 py-12">
        <Button
          variant="ghost"
          size="sm"
          className="mb-6 gap-2 text-muted-foreground hover:text-foreground"
          onClick={() => router.back()}
        >
          <ArrowLeft className="size-4" />
          뒤로 가기
        </Button>

        <Card className="border-border shadow-sm">
          <CardHeader className="space-y-1">
            <div className="flex items-center gap-2">
              <div className="flex size-8 items-center justify-center rounded-full bg-primary/10 text-primary">
                <User className="size-4" />
              </div>
              <CardTitle className="text-xl">개인정보 수정</CardTitle>
            </div>
            <CardDescription>
              계정 정보를 업데이트하거나 비밀번호를 변경할 수 있습니다.
            </CardDescription>
          </CardHeader>

          <form onSubmit={handleSubmit}>
            <CardContent className="space-y-6">
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="username">사용자 이름</Label>
                  <Input
                    id="username"
                    value={formData.username}
                    onChange={handleChange}
                    className={errors.username ? "border-destructive focus-visible:ring-destructive" : ""}
                  />
                  {errors.username && <p className="text-xs text-destructive">{errors.username}</p>}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="email" className="opacity-70">
                    이메일 (수정 불가)
                  </Label>
                  <div className="relative">
                    <Input id="email" value={formData.email} disabled className="bg-muted/50 pr-10" />
                    <Lock className="absolute right-3 top-2.5 size-4 text-muted-foreground" />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="department" className="opacity-70">
                    소속 부서 (수정 불가)
                  </Label>
                  <div className="relative">
                    <Input
                      id="department"
                      value={formData.department}
                      disabled
                      className="bg-muted/50 pr-10"
                    />
                    <Lock className="absolute right-3 top-2.5 size-4 text-muted-foreground" />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    소속 부서 변경은 관리자에게 요청해 주세요.
                  </p>
                </div>
              </div>

              <div className="h-px bg-border" />

              <div className="space-y-4">
                <div className="flex items-center gap-2 text-sm font-semibold text-muted-foreground">
                  <ShieldCheck className="size-4" />
                  보안 설정
                </div>

                <div className="space-y-2">
                  <Label htmlFor="currentPassword">현재 비밀번호 확인</Label>
                  <Input
                    id="currentPassword"
                    type="password"
                    placeholder="본인 확인을 위해 입력해 주세요"
                    value={formData.currentPassword}
                    onChange={handleChange}
                    className={errors.currentPassword ? "border-destructive focus-visible:ring-destructive" : ""}
                  />
                  {errors.currentPassword && (
                    <p className="text-xs text-destructive">{errors.currentPassword}</p>
                  )}
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="newPassword">새 비밀번호 (선택)</Label>
                    <Input
                      id="newPassword"
                      type="password"
                      placeholder="변경할 경우에만 입력"
                      value={formData.newPassword}
                      onChange={handleChange}
                      className={errors.newPassword ? "border-destructive focus-visible:ring-destructive" : ""}
                    />
                    {errors.newPassword && <p className="text-xs text-destructive">{errors.newPassword}</p>}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="confirmPassword">새 비밀번호 확인</Label>
                    <Input
                      id="confirmPassword"
                      type="password"
                      placeholder="한 번 더 입력"
                      value={formData.confirmPassword}
                      onChange={handleChange}
                      className={errors.confirmPassword ? "border-destructive focus-visible:ring-destructive" : ""}
                    />
                    {errors.confirmPassword && (
                      <p className="text-xs text-destructive">{errors.confirmPassword}</p>
                    )}
                  </div>
                </div>
              </div>
            </CardContent>

            <CardFooter className="flex justify-between gap-4 bg-slate-50/50 p-6 dark:bg-muted/20">
              <Button type="button" variant="outline" onClick={() => router.back()}>
                취소
              </Button>
              <Button type="submit" className="min-w-[120px]" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <Loader2 className="mr-2 size-4 animate-spin" />
                    처리 중...
                  </>
                ) : (
                  "수정 완료"
                )}
              </Button>
            </CardFooter>
          </form>
        </Card>
      </main>
    </div>
  )
}
