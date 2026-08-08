// 담당: 김민희
// 역할: 회원가입(계정 신청) 페이지 — 2단계(약관 동의 → 정보 입력) 오케스트레이션
// 참고: 내부망 포렌식 플랫폼 — 가입 신청 후 관리자 승인 시 로그인 가능.
//       이메일 인증은 하지 않고(승인·연락용), API 연동 완료.
"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { ShieldCheck, User, Lock, Mail, Landmark, Briefcase, Phone, KeyRound, Eye, EyeOff, Check, ChevronLeft } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ApiError } from "@/lib/api/client"
import { getApiErrorMessage } from "@/lib/api/errors"
import { checkUsername, signup, validateInviteCode } from "@/lib/signup-api"
import SignupAgreementStep, { type Agreements } from "./SignupAgreementStep"
import DepartmentAutocomplete from "./DepartmentAutocomplete"
import { ORG_TYPES, type OrgType } from "./organizationData"

function formatPhone(v: string) {
  const d = v.replace(/\D/g, "").slice(0, 11)
  if (d.length < 4) return d
  if (d.length < 8) return `${d.slice(0, 3)}-${d.slice(3)}`
  return `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}`
}

export default function SignupPage() {
  const [step, setStep] = useState<"agree" | "form">("agree")
  const [agree, setAgree] = useState<Agreements>({
    terms: false,
    privacy: false,
    security: false,
    log: false,
  })

  const [name, setName] = useState("")
  const [username, setUsername] = useState("")
  const [usernameStatus, setUsernameStatus] = useState<"" | "available" | "taken">("")
  const [password, setPassword] = useState("")
  const [passwordConfirm, setPasswordConfirm] = useState("")
  const [showPw, setShowPw] = useState(false)
  const [showPwc, setShowPwc] = useState(false)
  const [orgType, setOrgType] = useState<OrgType | "">("")
  const [dept, setDept] = useState("")
  const [position, setPosition] = useState("")
  const [email, setEmail] = useState("")
  const [phone, setPhone] = useState("")
  const [inviteCode, setInviteCode] = useState("")

  const [error, setError] = useState("")
  const [submitted, setSubmitted] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isCheckingUsername, setIsCheckingUsername] = useState(false)
  const [inviteStatus, setInviteStatus] = useState<"" | "valid" | "invalid" | "checking">("")
  const [successMessage, setSuccessMessage] = useState("")

  const emailValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  const phoneValid = /^01\d{8,9}$/.test(phone.replace(/\D/g, ""))
  const inviteValid = inviteStatus === "valid"

  const pwRules = [
    { label: "8자 이상", ok: password.length >= 8 },
    { label: "영문 대문자", ok: /[A-Z]/.test(password) },
    { label: "영문 소문자", ok: /[a-z]/.test(password) },
    { label: "숫자", ok: /\d/.test(password) },
    { label: "특수문자", ok: /[^A-Za-z0-9\s]/.test(password) },
    { label: "공백 없음", ok: password.length > 0 && !/\s/.test(password) },
  ]
  const passwordValid = pwRules.every((r) => r.ok)
  const passwordMatch = password.length > 0 && password === passwordConfirm

  useEffect(() => {
    const code = inviteCode.trim()
    if (!code) {
      setInviteStatus("")
      return
    }

    setInviteStatus("checking")
    const timer = window.setTimeout(async () => {
      try {
        const result = await validateInviteCode(code)
        setInviteStatus(result.valid ? "valid" : "invalid")
      } catch {
        setInviteStatus("invalid")
      }
    }, 400)

    return () => window.clearTimeout(timer)
  }, [inviteCode])

  async function handleCheckUsername() {
    if (!username.trim()) return

    setIsCheckingUsername(true)
    setError("")

    try {
      const result = await checkUsername(username.trim())
      setUsernameStatus(result.available ? "available" : "taken")
    } catch (err) {
      setUsernameStatus("")
      setError(getApiErrorMessage(err, "아이디 중복 확인에 실패했습니다."))
    } finally {
      setIsCheckingUsername(false)
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name) return setError("이름을 입력해 주세요.")
    if (usernameStatus !== "available") return setError("아이디 중복확인을 완료해 주세요.")
    if (!passwordValid) return setError("비밀번호 조건을 모두 충족해 주세요.")
    if (!passwordMatch) return setError("비밀번호가 일치하지 않습니다.")
    if (!orgType) return setError("기관 유형을 선택해 주세요.")
    if (!dept) return setError("소속 기관/부서를 입력해 주세요.")
    if (!position) return setError("직책 / 담당 업무를 입력해 주세요.")
    if (!emailValid) return setError("올바른 이메일을 입력해 주세요.")
    if (!phoneValid) return setError("올바른 연락처를 입력해 주세요.")
    if (inviteStatus === "checking") return setError("초대코드 확인 중입니다. 잠시 후 다시 시도해 주세요.")
    if (!inviteValid) return setError("유효한 초대코드를 입력해 주세요.")

    setError("")
    setIsSubmitting(true)

    try {
      const response = await signup({
        loginId: username.trim(),
        password,
        displayName: name.trim(),
        organizationType: orgType,
        department: dept.trim(),
        position: position.trim(),
        email: email.trim(),
        phone: phone.trim(),
        inviteCode: inviteCode.trim(),
        agreements: agree,
      })

      setSuccessMessage(response.message)
      setSubmitted(true)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.details?.[0]?.reason ?? getApiErrorMessage(err, "가입 신청에 실패했습니다. 백엔드 서버 상태를 확인해 주세요."))
      } else {
        setError(getApiErrorMessage(err, "가입 신청에 실패했습니다. 백엔드 서버 상태를 확인해 주세요."))
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  if (submitted) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background px-4">
        <div className="w-full max-w-sm space-y-4 rounded-xl border border-border bg-card p-8 text-center">
          <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-primary/15 text-primary ring-1 ring-primary/30">
            <Check className="size-6" aria-hidden="true" />
          </div>
          <h1 className="text-lg font-semibold text-foreground">가입 신청이 접수되었습니다</h1>
          <p className="text-sm text-muted-foreground">
            {successMessage || "관리자 승인이 완료되면 로그인할 수 있습니다."}
            <br />
            승인 결과는 등록하신 이메일로 안내됩니다.
          </p>
          <Button render={<Link href="/login" />} nativeButton={false} size="lg" className="w-full">
            로그인 화면으로
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 py-6">
      {step === "agree" ? (
        <SignupAgreementStep value={agree} onChange={setAgree} onNext={() => setStep("form")} />
      ) : (
        <div className="w-full max-w-md space-y-3">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setStep("agree")}
              className="text-muted-foreground transition-colors hover:text-foreground"
              aria-label="약관 동의로 돌아가기"
            >
              <ChevronLeft className="size-5" aria-hidden="true" />
            </button>
            <div className="flex size-8 items-center justify-center rounded-md bg-primary/15 text-primary ring-1 ring-primary/30">
              <ShieldCheck className="size-5" aria-hidden="true" />
            </div>
            <div>
              <h1 className="text-lg font-semibold leading-tight tracking-tight text-foreground">
                ForenShield AI
              </h1>
              <p className="text-xs text-muted-foreground">내부 사용자 계정 신청</p>
            </div>
          </div>

          <form className="space-y-2" onSubmit={handleSubmit}>
            <div className="overflow-hidden rounded-xl border border-border bg-card divide-y divide-border">
              <Row icon={User}>
                <input
                  type="text"
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value)
                    setUsernameStatus("")
                  }}
                  placeholder="아이디"
                  className={fieldClass}
                />
                {usernameStatus === "available" ? (
                  <span className="flex shrink-0 items-center gap-1 text-xs font-medium text-primary">
                    <Check className="size-3.5" aria-hidden="true" /> 사용 가능
                  </span>
                ) : (
                  <button
                    type="button"
                    onClick={handleCheckUsername}
                    disabled={isCheckingUsername || !username.trim()}
                    className="shrink-0 rounded-md border border-border px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-50"
                  >
                    {isCheckingUsername ? "확인 중..." : "중복확인"}
                  </button>
                )}
              </Row>
              {usernameStatus === "taken" && (
                <Row>
                  <span className="text-xs text-destructive">이미 사용 중인 아이디입니다.</span>
                </Row>
              )}

              <Row icon={Lock}>
                <input
                  type={showPw ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="비밀번호"
                  className={fieldClass}
                />
                <EyeToggle on={showPw} toggle={() => setShowPw((v) => !v)} />
              </Row>

              <Row icon={Lock}>
                <input
                  type={showPwc ? "text" : "password"}
                  value={passwordConfirm}
                  onChange={(e) => setPasswordConfirm(e.target.value)}
                  placeholder="비밀번호 확인"
                  className={fieldClass}
                />
                {passwordConfirm.length > 0 && passwordMatch && (
                  <Check className="size-4 shrink-0 text-primary" aria-hidden="true" />
                )}
                <EyeToggle on={showPwc} toggle={() => setShowPwc((v) => !v)} />
              </Row>
            </div>

            {password.length > 0 && (
              <div className="flex flex-wrap gap-x-3 gap-y-1 px-1">
                {pwRules.map((rule) => (
                  <span
                    key={rule.label}
                    className={`flex items-center gap-1 text-xs ${
                      rule.ok ? "text-primary" : "text-muted-foreground/60"
                    }`}
                  >
                    <Check className="size-3" aria-hidden="true" />
                    {rule.label}
                  </span>
                ))}
              </div>
            )}

            <div className="overflow-hidden rounded-xl border border-border bg-card divide-y divide-border">
              <Row icon={User}>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="이름"
                  className={fieldClass}
                />
              </Row>
              <Row icon={Landmark}>
                <select
                  value={orgType}
                  onChange={(e) => {
                    setOrgType(e.target.value as OrgType)
                    setDept("")
                  }}
                  className={`${fieldClass} ${orgType ? "text-foreground" : "text-muted-foreground"}`}
                >
                  <option value="" disabled>
                    기관 유형 선택
                  </option>
                  {ORG_TYPES.map((t) => (
                    <option key={t.value} value={t.value} className="text-foreground">
                      {t.label}
                    </option>
                  ))}
                </select>
              </Row>
            </div>

            <DepartmentAutocomplete orgType={orgType} value={dept} onChange={setDept} />

            <div className="overflow-hidden rounded-xl border border-border bg-card divide-y divide-border">
              <Row icon={Briefcase}>
                <input
                  type="text"
                  value={position}
                  onChange={(e) => setPosition(e.target.value)}
                  placeholder="직책 / 담당 업무"
                  className={fieldClass}
                />
              </Row>
              <Row icon={Mail}>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="이메일 (예: kim@example.go.kr)"
                  className={fieldClass}
                />
              </Row>
              <Row icon={Phone}>
                <input
                  type="tel"
                  inputMode="numeric"
                  value={phone}
                  onChange={(e) => setPhone(formatPhone(e.target.value))}
                  placeholder="연락처 (010-0000-0000)"
                  className={fieldClass}
                />
              </Row>
              <Row icon={KeyRound}>
                <input
                  type="text"
                  value={inviteCode}
                  onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
                  placeholder="초대코드 (기관 발급)"
                  className={fieldClass}
                />
                {inviteCode.length > 0 &&
                  (inviteStatus === "checking" ? (
                    <span className="shrink-0 text-xs text-muted-foreground">확인 중…</span>
                  ) : inviteValid ? (
                    <span className="flex shrink-0 items-center gap-1 text-xs font-medium text-primary">
                      <Check className="size-3.5" aria-hidden="true" /> 확인됨
                    </span>
                  ) : inviteStatus === "invalid" ? (
                    <span className="shrink-0 text-xs text-destructive">유효하지 않음</span>
                  ) : null)}
              </Row>
            </div>

            {error && <p className="px-1 text-sm text-destructive">{error}</p>}

            <Button
              type="submit"
              size="lg"
              className="h-11 w-full text-base"
              disabled={isSubmitting}
            >
              {isSubmitting ? "신청 중..." : "가입 신청"}
            </Button>
          </form>

          <p className="text-center text-sm text-muted-foreground">
            이미 계정이 있으신가요?{" "}
            <Link href="/login" className="text-primary underline-offset-4 hover:underline">
              로그인
            </Link>
          </p>
        </div>
      )}
    </div>
  )
}

const fieldClass =
  "min-w-0 flex-1 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"

function Row({ icon: Icon, children }: { icon?: React.ElementType; children: React.ReactNode }) {
  return (
    <div className="flex h-12 items-center gap-3 px-4">
      {Icon ? (
        <Icon className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
      ) : (
        <span className="size-5 shrink-0" />
      )}
      {children}
    </div>
  )
}

function EyeToggle({ on, toggle }: { on: boolean; toggle: () => void }) {
  return (
    <button
      type="button"
      onClick={toggle}
      className="shrink-0 text-muted-foreground transition-colors hover:text-foreground"
      aria-label={on ? "비밀번호 숨기기" : "비밀번호 표시"}
    >
      {on ? <Eye className="size-4" aria-hidden="true" /> : <EyeOff className="size-4" aria-hidden="true" />}
    </button>
  )
}
