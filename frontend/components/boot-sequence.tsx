"use client"

import { useEffect, useRef, useState } from "react"
import { Check, ChevronRight, ShieldCheck } from "lucide-react"

import { cn } from "@/lib/utils"

type AccessPhase = "checking" | "verified"

export function BootSequence({
  userName,
  roleLabel,
  onDone,
}: {
  userName: string
  roleLabel: string
  onDone: () => void
}) {
  const doneRef = useRef(false)
  const [activeStep, setActiveStep] = useState(0)
  const [phase, setPhase] = useState<AccessPhase>("checking")
  const steps = [
    { label: "계정 확인", detail: `${userName} · ${roleLabel}` },
    { label: "보안 세션", detail: "안전한 연결 수립" },
    { label: "접근 권한", detail: `${roleLabel} 업무 권한 적용` },
    { label: "작업공간", detail: "사건 및 분석 환경 준비" },
  ]

  function finish() {
    if (doneRef.current) return
    doneRef.current = true
    onDone()
  }

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      const timer = window.setTimeout(finish, 250)
      return () => window.clearTimeout(timer)
    }

    const timers = [
      window.setTimeout(() => setActiveStep(1), 450),
      window.setTimeout(() => setActiveStep(2), 900),
      window.setTimeout(() => setActiveStep(3), 1350),
      window.setTimeout(() => setPhase("verified"), 1800),
      window.setTimeout(finish, 2250),
    ]

    return () => timers.forEach((timer) => window.clearTimeout(timer))
    // onDone은 부모 렌더마다 새 함수가 될 수 있으므로 최초 마운트에서만 시퀀스를 예약한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const verified = phase !== "checking"
  const progress = verified ? 100 : ((activeStep + 1) / steps.length) * 100

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center overflow-hidden bg-[#f6f8fa] px-6 text-slate-900 dark:bg-[#f6f8fa] dark:text-slate-900"
      role="status"
      aria-live="polite"
      aria-atomic="true"
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-70"
        style={{
          backgroundImage:
            "linear-gradient(rgba(14,165,233,0.045) 1px, transparent 1px), linear-gradient(90deg, rgba(14,165,233,0.045) 1px, transparent 1px)",
          backgroundSize: "48px 48px",
        }}
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-1/2 h-px bg-gradient-to-r from-transparent via-sky-200/70 to-transparent"
      />

      <section className="relative w-full max-w-[560px]" aria-label="접속 인증 진행 상황">
        <header className="mb-9 flex items-center justify-between border-b border-slate-200/80 pb-5">
          <div className="flex items-center gap-3">
            <span
              className={cn(
                "flex size-10 items-center justify-center rounded-xl border bg-white shadow-sm transition-colors duration-300",
                verified
                  ? "border-teal-200 text-teal-600"
                  : "border-sky-200 text-sky-700"
              )}
            >
              {verified ? (
                <Check className="size-5 access-check-in" strokeWidth={2.6} aria-hidden="true" />
              ) : (
                <ShieldCheck className="size-5" aria-hidden="true" />
              )}
            </span>
            <div>
              <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-teal-600">
                ForenShield AI
              </p>
              <h1 className="mt-1 text-lg font-bold tracking-tight text-slate-900">
                {verified ? "접속이 확인되었습니다" : "보안 접속을 준비하고 있습니다"}
              </h1>
            </div>
          </div>
          <span className="hidden text-xs font-semibold tabular-nums text-slate-400 sm:block">
            {Math.round(progress)}%
          </span>
        </header>

        <ol className="space-y-3.5" aria-label="접속 준비 단계">
          {steps.map((step, index) => {
            const complete = verified || index < activeStep
            const current = !verified && index === activeStep

            return (
              <li
                key={step.label}
                className={cn(
                  "flex min-h-9 items-center gap-3 transition-[opacity,transform] duration-300",
                  !complete && !current && "opacity-35",
                  current && "translate-x-1"
                )}
              >
                <span className="flex size-5 shrink-0 items-center justify-center">
                  {complete ? (
                    <Check className="size-4 text-teal-600" strokeWidth={2.8} aria-hidden="true" />
                  ) : current ? (
                    <ChevronRight className="size-4 access-current-step text-teal-600" strokeWidth={2.8} aria-hidden="true" />
                  ) : (
                    <span className="size-1.5 rounded-full bg-slate-300" aria-hidden="true" />
                  )}
                </span>
                <p className={cn("min-w-0 flex-1 text-[15px]", current ? "font-bold text-slate-900" : "font-semibold text-slate-600")}>
                  {step.label}
                  <span className="mx-2 text-slate-300" aria-hidden="true">—</span>
                  <span className={cn("font-medium", current ? "text-slate-600" : "text-slate-500")}>
                    {step.detail}
                  </span>
                </p>
              </li>
            )
          })}
        </ol>

        <div className="mt-8 h-1 overflow-hidden rounded-full bg-sky-100" aria-hidden="true">
          <div
            className="h-full rounded-full bg-teal-600 transition-[width] duration-500 ease-out"
            style={{ width: `${progress}%` }}
          />
        </div>

        <div className="mt-4 flex items-center justify-between gap-3">
          <p className="text-[10px] font-bold uppercase tracking-[0.28em] text-slate-400">
            {verified ? "Secure session ready" : "Secure session initializing"}
          </p>
          <button
            type="button"
            onClick={finish}
            className="rounded-md px-2 py-1 text-xs font-semibold text-slate-400 transition-colors hover:text-teal-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-500"
          >
            바로 이동
          </button>
        </div>
      </section>

      <style>{`
        @keyframes access-check-in {
          from { opacity: 0; transform: scale(0.72); }
          to { opacity: 1; transform: scale(1); }
        }
        @keyframes access-current-step {
          0%, 100% { transform: translateX(0); opacity: 0.65; }
          50% { transform: translateX(3px); opacity: 1; }
        }
        .access-check-in { animation: access-check-in 220ms ease-out; }
        .access-current-step { animation: access-current-step 900ms ease-in-out infinite; }
        @media (prefers-reduced-motion: reduce) {
          .access-check-in, .access-current-step { animation: none; }
        }
      `}</style>
    </div>
  )
}
