"use client"

import { useEffect, useRef, useState, type FormEvent } from "react"
import { CheckCircle2, Lock } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

export type StepUpDialogMode = "closed" | "password" | "success"

type StepUpGateDialogsProps = {
  mode: StepUpDialogMode
  loginId: string
  loading?: boolean
  error?: string | null
  onSubmit: (password: string) => void
  onCancel: () => void
  onSuccessClose: () => void
}

export function StepUpGateDialogs({
  mode,
  loginId,
  loading = false,
  error = null,
  onSubmit,
  onCancel,
  onSuccessClose,
}: StepUpGateDialogsProps) {
  const [password, setPassword] = useState("")
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (mode !== "password") {
      setPassword("")
      return
    }

    const timer = window.setTimeout(() => inputRef.current?.focus(), 0)
    return () => window.clearTimeout(timer)
  }, [mode])

  useEffect(() => {
    if (mode !== "success") return
    const timer = window.setTimeout(onSuccessClose, 1500)
    return () => window.clearTimeout(timer)
  }, [mode, onSuccessClose])

  if (mode === "closed") return null

  if (mode === "success") {
    return (
      <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 px-4">
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="step-up-success-title"
          className="w-full max-w-sm rounded-xl border border-border bg-card p-6 text-center shadow-lg"
        >
          <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-300">
            <CheckCircle2 className="size-6" aria-hidden="true" />
          </div>
          <h2 id="step-up-success-title" className="mt-4 text-lg font-semibold text-foreground">
            인증되었습니다!
          </h2>
          <p className="mt-2 text-sm text-muted-foreground">
            15분 동안 민감 정보를 조회할 수 있습니다.
          </p>
          <Button type="button" className="mt-5 w-full" onClick={onSuccessClose}>
            확인
          </Button>
        </div>
      </div>
    )
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!password.trim() || loading) return
    onSubmit(password)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="step-up-password-title"
        className="w-full max-w-md rounded-xl border border-border bg-card p-6 shadow-lg"
      >
        <div className="flex items-start gap-3">
          <div className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full bg-sky-500/15 text-sky-700 dark:text-sky-300">
            <Lock className="size-4" aria-hidden="true" />
          </div>
          <div className="min-w-0 flex-1">
            <h2 id="step-up-password-title" className="text-lg font-semibold text-foreground">
              비밀번호 재확인
            </h2>
            <p className="mt-2 text-sm text-muted-foreground">
              민감한 증거 상세 정보를 조회하려면 로그인 비밀번호를 다시 입력해 주세요.
            </p>
          </div>
        </div>

        <form className="mt-5 space-y-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label htmlFor="step-up-login-id">로그인 ID</Label>
            <Input id="step-up-login-id" value={loginId} readOnly disabled className="bg-muted/40" />
          </div>

          <div className="space-y-2">
            <Label htmlFor="step-up-password">비밀번호</Label>
            <Input
              ref={inputRef}
              id="step-up-password"
              type="password"
              autoComplete="current-password"
              value={password}
              disabled={loading}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="outline" onClick={onCancel} disabled={loading}>
              취소
            </Button>
            <Button type="submit" disabled={loading || !password.trim()}>
              {loading ? "확인 중..." : "확인"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
