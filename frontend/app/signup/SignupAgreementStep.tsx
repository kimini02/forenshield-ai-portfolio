// 담당: 김민희
// 역할: 약관/보안 서약 동의 단계 (회원가입 1단계)
"use client"

import { useState } from "react"
import Link from "next/link"
import { ShieldCheck, Check } from "lucide-react"
import { Button } from "@/components/ui/button"
import AgreementModal from "./AgreementModal"
import SecurityPledgeModal from "./SecurityPledgeModal"

export type Agreements = {
  terms: boolean // 내부 시스템 이용약관 (필수)
  privacy: boolean // 개인정보 수집 및 이용 (필수)
  security: boolean // 증거자료 보안 서약 (필수)
  log: boolean // 비식별 로그 활용 (선택)
}

// 보안 서약(security)을 제외한 약관 전문 — '보기' 클릭 시 모달로 표시
const AGREEMENT_DOCS: Record<"terms" | "privacy" | "log", { title: string; clauses: string[] }> = {
  terms: {
    title: "내부 시스템 이용약관",
    clauses: [
      "본 시스템은 관리자 승인을 받은 내부 사용자에 한해 디지털 포렌식 분석 목적으로만 제공됩니다.",
      "사용자는 부여된 권한 범위 내에서만 시스템을 이용하며, 권한을 초과한 접근을 시도하지 않습니다.",
      "시스템 이용 중 발생하는 모든 활동은 기록되며, 부적절한 이용 시 계정이 제한될 수 있습니다.",
      "시스템이 제공하는 분석 결과는 참고 자료이며, 최종 판단의 책임은 사용자에게 있습니다.",
      "본 약관은 내부 정책에 따라 사전 고지 후 변경될 수 있습니다.",
    ],
  },
  privacy: {
    title: "개인정보 수집 및 이용 동의",
    clauses: [
      "수집 항목: 이름, 아이디, 비밀번호, 소속 부서, 직책/담당 업무, 이메일.",
      "수집 목적: 계정 식별, 관리자 승인 심사, 권한 관리 및 승인 결과 연락.",
      "보유 기간: 계정 유지 기간 동안 보관하며, 계정 삭제 시 관련 규정에 따라 지체 없이 파기합니다.",
      "사용자는 개인정보 제공에 동의하지 않을 권리가 있으나, 이 경우 계정 신청이 제한될 수 있습니다.",
    ],
  },
  log: {
    title: "비식별 로그 활용 동의 (선택)",
    clauses: [
      "분석 정확도 및 시스템 품질 개선을 위해 비식별 처리된 이용 로그를 활용할 수 있습니다.",
      "활용되는 로그에는 개인을 식별할 수 있는 정보가 포함되지 않습니다.",
      "본 항목은 선택 사항이며, 동의하지 않아도 시스템 이용에는 제한이 없습니다.",
    ],
  },
}

export default function SignupAgreementStep({
  value,
  onChange,
  onNext,
}: {
  value: Agreements
  onChange: (next: Agreements) => void
  onNext: () => void
}) {
  // 어떤 약관 '보기'가 열렸는지 (null이면 닫힘)
  const [openKey, setOpenKey] = useState<keyof Agreements | null>(null)
  const doc = openKey && openKey !== "security" ? AGREEMENT_DOCS[openKey] : null

  const allChecked = value.terms && value.privacy && value.security && value.log
  const requiredOk = value.terms && value.privacy && value.security

  const toggleAll = () => {
    const v = !allChecked
    onChange({ terms: v, privacy: v, security: v, log: v })
  }
  const toggle = (key: keyof Agreements) => onChange({ ...value, [key]: !value[key] })

  return (
    <div className="w-full max-w-md space-y-4">
      {/* 헤더 */}
      <div className="flex items-center gap-2">
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

      {/* 약관 카드 */}
      <div className="rounded-xl border border-border bg-card p-5">
        {/* 전체 동의 */}
        <button type="button" onClick={toggleAll} className="flex w-full items-center gap-3 text-left">
          <CheckCircle on={allChecked} size="lg" />
          <span className="text-base font-semibold text-foreground">전체 동의하기</span>
        </button>
        <p className="mt-2 pl-9 text-xs leading-relaxed text-muted-foreground">
          내부 시스템 이용약관, 개인정보 수집·이용, 증거자료 보안 서약(필수) 및 비식별 로그 활용(선택)
          동의를 포함합니다.
        </p>

        <div className="my-4 border-t border-border" />

        {/* 항목 */}
        <div className="space-y-4">
          <AgreeRow
            checked={value.terms}
            onToggle={() => toggle("terms")}
            required
            label="내부 시스템 이용약관"
            onView={() => setOpenKey("terms")}
          />
          <AgreeRow
            checked={value.privacy}
            onToggle={() => toggle("privacy")}
            required
            label="개인정보 수집 및 이용"
            onView={() => setOpenKey("privacy")}
          />
          <AgreeRow
            checked={value.security}
            onToggle={() => toggle("security")}
            required
            label="증거자료 보안 서약"
            onView={() => setOpenKey("security")}
          />
          <AgreeRow
            checked={value.log}
            onToggle={() => toggle("log")}
            label="분석 결과 개선을 위한 비식별 로그 활용 동의"
            onView={() => setOpenKey("log")}
          />
        </div>
      </div>

      <Button
        type="button"
        size="lg"
        disabled={!requiredOk}
        onClick={onNext}
        className="h-12 w-full text-base"
      >
        다음
      </Button>

      <p className="text-center text-sm text-muted-foreground">
        이미 계정이 있으신가요?{" "}
        <Link href="/login" className="text-primary underline-offset-4 hover:underline">
          로그인
        </Link>
      </p>

      {/* 증거자료 보안 서약 모달 */}
      <SecurityPledgeModal open={openKey === "security"} onClose={() => setOpenKey(null)} />

      {/* 그 외 약관(이용약관·개인정보·로그) 모달 */}
      <AgreementModal
        open={!!doc}
        title={doc?.title ?? ""}
        clauses={doc?.clauses ?? []}
        onClose={() => setOpenKey(null)}
      />
    </div>
  )
}

// 동의 항목 행 (체크 + 라벨 + 보기)
function AgreeRow({
  checked,
  onToggle,
  label,
  required,
  onView,
}: {
  checked: boolean
  onToggle: () => void
  label: string
  required?: boolean
  onView: () => void
}) {
  return (
    <div className="flex items-center justify-between gap-2">
      <button type="button" onClick={onToggle} className="flex flex-1 items-center gap-2.5 text-left">
        <CheckCircle on={checked} />
        <span className="text-sm text-foreground">
          <span className={required ? "font-medium text-primary" : "text-muted-foreground"}>
            {required ? "[필수]" : "[선택]"}
          </span>{" "}
          {label}
        </span>
      </button>
      <button
        type="button"
        onClick={onView}
        className="shrink-0 text-xs text-muted-foreground underline underline-offset-2 transition-colors hover:text-foreground"
      >
        보기
      </button>
    </div>
  )
}

// 원형 체크 아이콘
function CheckCircle({ on, size = "md" }: { on: boolean; size?: "md" | "lg" }) {
  const s = size === "lg" ? "size-6" : "size-5"
  const i = size === "lg" ? "size-4" : "size-3.5"
  return (
    <span
      className={`flex ${s} shrink-0 items-center justify-center rounded-full transition-colors ${
        on ? "bg-primary text-primary-foreground" : "border border-border text-muted-foreground/40"
      }`}
    >
      <Check className={i} aria-hidden="true" />
    </span>
  )
}
