"use client"

import {
  Suspense,
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type ReactNode,
} from "react"
import { useSearchParams } from "next/navigation"
import {
  AlertCircle,
  Check,
  CheckCircle2,
  Copy,
  FileCheck2,
  FileUp,
  Link2,
  Loader2,
  PenLine,
  QrCode,
  RefreshCw,
  ShieldCheck,
  XCircle,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { ApiError } from "@/lib/api/client"
import { getApiErrorMessage } from "@/lib/api/errors"
import {
  fetchReportVerification,
  verifyReportFileHash,
  type ReportFileHashVerification,
  type ReportVerification,
  type ReportVerificationLookup,
  type ReportVerifyStatus,
} from "@/lib/api/public-report"
import { formatDateTime } from "@/lib/formatters"
import { cn } from "@/lib/utils"

export default function PublicReportVerifyPage() {
  return (
    <Suspense fallback={<VerifyShell><VerifyLoading /></VerifyShell>}>
      <VerifyPageBody />
    </Suspense>
  )
}

function VerifyPageBody() {
  const searchParams = useSearchParams()
  const token = searchParams.get("token")?.trim() ?? ""
  const [result, setResult] = useState<ReportVerification | null>(null)
  const [isLoading, setIsLoading] = useState(Boolean(token))
  const [errorKind, setErrorKind] = useState<"notFound" | "server" | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [retryKey, setRetryKey] = useState(0)
  const shouldVerify = Boolean(token)

  useEffect(() => {
    if (!shouldVerify) {
      return
    }
    let cancelled = false

    async function verify() {
      setIsLoading(true)
      setErrorKind(null)
      setErrorMessage(null)
      setResult(null)

      try {
        const data = await fetchReportVerification({ token })
        if (!cancelled) setResult(data)
      } catch (error) {
        if (cancelled) return
        if (error instanceof ApiError && (error.status === 404 || error.status === 400)) {
          setErrorKind("notFound")
        } else {
          setErrorKind("server")
        }
        setErrorMessage(getApiErrorMessage(error, "검증 정보를 확인하지 못했습니다."))
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void verify()

    return () => {
      cancelled = true
    }
  }, [shouldVerify, token, retryKey])

  return (
    <VerifyShell>
      {!shouldVerify ? (
        <VerifyLinkGuide />
      ) : isLoading ? (
        <VerifyLoading />
      ) : result?.status === "PENDING" ? (
        <VerifyEmptyState
          icon={<PenLine className="size-9 text-slate-400" aria-hidden="true" />}
          title="아직 발행되지 않은 보고서입니다"
          description={
            result.message ??
            "검토 승인과 발행 등록이 완료된 후 QR 검증 정보를 확인할 수 있습니다."
          }
        />
      ) : errorKind === "notFound" ? (
        <VerifyEmptyState
          icon={<QrCode className="size-9 text-slate-400" aria-hidden="true" />}
          title="아직 발행되지 않은 보고서입니다"
          description={
            errorMessage ??
            "검증 토큰이 아직 발행되지 않았거나 더 이상 사용할 수 없습니다.\n보고서의 QR 코드를 다시 확인해 주세요."
          }
        />
      ) : errorKind === "server" ? (
        <VerifyEmptyState
          icon={<AlertCircle className="size-9 text-slate-400" aria-hidden="true" />}
          title="일시적으로 확인할 수 없습니다"
          description={errorMessage ?? "잠시 후 다시 시도해 주세요."}
          action={
            <Button
              type="button"
              variant="outline"
              className="h-10 rounded-lg px-4 font-bold"
              onClick={() => setRetryKey((key) => key + 1)}
            >
              <RefreshCw className="size-4" aria-hidden="true" />
              다시 확인
            </Button>
          }
        />
      ) : result ? (
        <VerifyResult
          result={result}
          lookup={{ token }}
        />
      ) : null}
    </VerifyShell>
  )
}

function VerifyLinkGuide() {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 dark:border-border dark:bg-card">
      <div className="text-center">
        <ShieldCheck className="mx-auto size-10 text-teal-600 dark:text-teal-300" aria-hidden="true" />
        <h1 className="mt-3 text-lg font-bold text-slate-950 dark:text-foreground">보고서 발행정보 조회</h1>
        <p className="mt-2 text-sm font-semibold leading-6 text-slate-500 dark:text-muted-foreground">
          보고서에 인쇄된 QR 코드를 스캔하거나 토큰이 포함된 전체 검증 링크를 열어 주세요.
        </p>
      </div>
      <p className="mt-5 rounded-lg bg-slate-50 px-3 py-3 text-center font-mono text-xs font-semibold text-slate-500 dark:bg-background/60 dark:text-muted-foreground">
        /verify?token=vrf_...
      </p>
    </section>
  )
}

function VerifyShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-[#f6f8fa] text-slate-900 dark:bg-background dark:text-foreground">
      <main className="mx-auto flex w-full max-w-[560px] flex-col gap-3 px-5 py-8 sm:py-16">
        <header className="flex items-center justify-center gap-2 pb-2">
          <ShieldCheck className="size-5 text-teal-600 dark:text-teal-300" aria-hidden="true" />
          <p className="text-sm font-bold text-slate-700 dark:text-foreground">
            ForenShield 보고서 진위 확인
          </p>
        </header>

        {children}

        <p className="px-1 pt-1 text-center text-[11px] font-medium leading-5 text-slate-400">
          이 페이지는 QR 코드로 연결된 보고서의 공식 발행 기록을 확인하며, 보고서 내용은 제공하지 않습니다.
        </p>
      </main>
    </div>
  )
}

function VerifyLoading() {
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4 rounded-xl border border-slate-200 bg-white p-8 dark:border-border dark:bg-card">
      <Loader2 className="size-8 animate-spin text-slate-400" aria-hidden="true" />
      <p className="text-sm font-semibold text-slate-500 dark:text-muted-foreground">
        검증 정보를 확인하는 중입니다.
      </p>
    </div>
  )
}

function VerifyEmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon: ReactNode
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4 rounded-xl border border-slate-200 bg-white p-8 text-center dark:border-border dark:bg-card">
      {icon}
      <div className="space-y-2">
        <p className="text-base font-bold text-slate-950 dark:text-foreground">{title}</p>
        <p className="whitespace-pre-line text-sm font-semibold leading-6 text-slate-500 dark:text-muted-foreground">
          {description}
        </p>
      </div>
      {action}
    </div>
  )
}

const VERDICT_DISPLAY: Record<
  Exclude<ReportVerifyStatus, "PENDING">,
  { title: string; fallbackMessage: string; icon: ReactNode; text: string }
> = {
  VALID: {
    title: "발행 등록정보 조회 완료",
    fallbackMessage: "발행 등록정보를 조회했습니다. PDF 파일 자체는 아직 검사하지 않았습니다.",
    icon: <CheckCircle2 className="size-11 text-teal-600 dark:text-teal-300" aria-hidden="true" />,
    text: "text-teal-700 dark:text-teal-300",
  },
  WARNING: {
    title: "발행 상태 확인 필요",
    fallbackMessage: "발행 등록정보를 확인했지만 최신 발행본 여부를 추가로 확인해야 합니다.",
    icon: <AlertCircle className="size-11 text-amber-600 dark:text-amber-400" aria-hidden="true" />,
    text: "text-amber-600 dark:text-amber-400",
  },
  INVALID: {
    title: "발행 기록 검증 실패",
    fallbackMessage: "발행 기록에 문제가 있습니다. 발급 기관에 문의해 주세요.",
    icon: <XCircle className="size-11 text-red-700 dark:text-red-400" aria-hidden="true" />,
    text: "text-red-700 dark:text-red-400",
  },
}

type CheckTone = "ok" | "warning" | "danger" | "muted"

const CHECK_BADGE_CLASS: Record<CheckTone, string> = {
  ok: "bg-teal-50 text-teal-700 dark:bg-teal-500/10 dark:text-teal-300",
  warning: "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300",
  danger: "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-400",
  muted: "bg-slate-100 text-slate-500 dark:bg-secondary dark:text-muted-foreground",
}

function VerifyResult({
  result,
  lookup,
}: {
  result: ReportVerification
  lookup: ReportVerificationLookup
}) {
  const verdictStatus: Exclude<ReportVerifyStatus, "PENDING"> = result.status === "PENDING"
    ? "WARNING"
    : result.status
  const verdict = VERDICT_DISPLAY[verdictStatus]
  const reportType = result.reportType === "COMPARE" ? "비교검증 보고서" : "AI 기술분석 보고서"
  const publicationStatus = getPublicationStatusLabel(result.publicationStatus)
  const blockchainDisplay = getBlockchainDisplay(result)

  return (
    <div className="flex flex-col gap-2.5">
      <section className="rounded-xl border border-slate-200 bg-white p-7 text-center dark:border-border dark:bg-card">
        <div className="flex justify-center">{verdict.icon}</div>
        <h1 className={cn("mt-3 text-xl font-bold", verdict.text)}>{verdict.title}</h1>
        <p className="mt-2 text-sm font-semibold leading-6 text-slate-500 dark:text-muted-foreground">
          {getRecordSummary(result, verdict.fallbackMessage)}
        </p>
        <p className="mt-3 font-mono text-xs font-semibold text-slate-400">{result.reportNo}</p>
      </section>

      <section className="rounded-xl border border-sky-200 bg-sky-50 p-4 dark:border-sky-500/30 dark:bg-sky-500/10">
        <div className="flex items-start gap-3">
          <FileCheck2 className="mt-0.5 size-5 shrink-0 text-sky-700 dark:text-sky-300" aria-hidden="true" />
          <div>
            <p className="text-sm font-bold text-sky-950 dark:text-sky-200">
              등록정보 조회 완료 - PDF 파일 미제출
            </p>
            <p className="mt-1 text-xs font-semibold leading-5 text-sky-800/80 dark:text-sky-200/80">
              현재 화면은 QR 토큰으로 발행 등록부만 조회했습니다. 보유한 PDF가 등록된 최종 발행본과 같은지는 아래 파일 대조를 실행해야 확인할 수 있습니다.
            </p>
          </div>
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-4 dark:border-border dark:bg-card">
        <h2 className="text-sm font-bold text-slate-950 dark:text-foreground">공개 발행정보</h2>
        <dl className="mt-3 space-y-2">
          <InfoRow label="발행기관" value="ForenShield AI" />
          <InfoRow label="보고서 번호" value={result.reportNo} />
          <InfoRow label="보고서 종류" value={reportType} />
          <InfoRow label="리비전" value={`rev.${result.revision ?? 1}`} />
          <InfoRow label="발행 상태" value={publicationStatus} />
          <InfoRow label="발행 일시" value={formatDateTime(result.issuedAt ?? result.createdAt)} />
          {result.queriedAt ? <InfoRow label="조회 시각" value={formatDateTime(result.queriedAt)} /> : null}
        </dl>
      </section>

      <CheckCard
        icon={<Link2 className="size-4" aria-hidden="true" />}
        title="최종 PDF SHA-256 등록값"
        badge="등록됨"
        tone="ok"
        note="발행 시 계산해 등록한 최종 PDF 해시입니다. 아래 파일 대조 전에는 사용자가 가진 PDF와의 일치 여부를 의미하지 않습니다."
      >
        <HashLine label="등록된 최종 PDF SHA-256" value={result.reportHash} />
      </CheckCard>

      <CheckCard
        icon={<ShieldCheck className="size-4" aria-hidden="true" />}
        title="보고서 PDF 해시 블록체인 등록"
        badge={blockchainDisplay.badge}
        tone={blockchainDisplay.tone}
        note={blockchainDisplay.note}
      >
        <div className="mt-3 space-y-2">
          {result.blockchainNetwork ? <InfoRow label="네트워크" value={blockchainDisplay.networkLabel} /> : null}
          {result.blockchainAnchoredAt ? (
            <InfoRow label="등록 시각" value={formatDateTime(result.blockchainAnchoredAt)} />
          ) : null}
          {result.blockchainTxHash ? (
            <HashLine label="블록체인 트랜잭션 ID" value={result.blockchainTxHash} />
          ) : null}
        </div>
      </CheckCard>

      <ReportFileVerification lookup={lookup} />

      <p className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-[11px] font-semibold leading-5 text-slate-500 dark:border-border dark:bg-card dark:text-muted-foreground">
        이 조회 결과는 기술적 발행·동일성 확인을 위한 정보이며, 보고서 내용의 정확성, 증거능력, 증명력 또는 법적 효력을 자동으로 보장하지 않습니다.
      </p>
    </div>
  )
}

function getBlockchainDisplay(result: ReportVerification): {
  badge: string
  tone: CheckTone
  note: string
  networkLabel: string
} {
  const status = result.blockchainStatus?.trim().toUpperCase() ?? "NOT_ANCHORED"
  const network = result.blockchainNetwork?.trim() ?? ""
  const simulated = network.toLowerCase().includes("simulated")
  const networkLabel = simulated ? `${network} (개발 검증용)` : network

  if (status === "ANCHORED" && result.blockchainMatched === false) {
    return {
      badge: "등록 해시 불일치",
      tone: "danger",
      note: "블록체인에 등록된 해시와 현재 발행 보고서 해시가 일치하지 않습니다. 발급 기관의 확인이 필요합니다.",
      networkLabel,
    }
  }
  if (status === "ANCHORED" && simulated) {
    return {
      badge: "개발 검증용 기록",
      tone: "warning",
      note: "로컬 시뮬레이션 네트워크의 기록입니다. 실제 외부 원장 등록으로 해석하지 않습니다.",
      networkLabel,
    }
  }
  if (status === "ANCHORED") {
    return {
      badge: "등록 확인",
      tone: "ok",
      note: "발행된 최종 PDF의 SHA-256 해시가 표시된 블록체인 네트워크에 등록되어 있습니다.",
      networkLabel,
    }
  }
  if (status === "PENDING") {
    return {
      badge: "등록 대기",
      tone: "warning",
      note: "보고서 PDF 해시의 블록체인 등록이 아직 완료되지 않았습니다.",
      networkLabel,
    }
  }
  if (status === "FAILED") {
    return {
      badge: "등록 실패",
      tone: "danger",
      note: "보고서 PDF 해시의 블록체인 등록에 실패했습니다. PDF 동일성은 아래 SHA-256 파일 대조로 별도 확인할 수 있습니다.",
      networkLabel,
    }
  }
  return {
    badge: "미등록",
    tone: "muted",
    note: "보고서 PDF 해시의 블록체인 등록 기록이 없습니다. PDF 동일성은 아래 SHA-256 파일 대조로 확인할 수 있습니다.",
    networkLabel,
  }
}

function getPublicationStatusLabel(status?: string | null) {
  const normalized = status?.trim().toUpperCase()
  if (normalized === "SUPERSEDED") return "대체됨 - 최신본 확인 필요"
  if (normalized === "ISSUED") return "발행됨"
  if (normalized === "DRAFT") return "초안"
  return status?.trim() || "발행 상태 미확인"
}

function getRecordSummary(result: ReportVerification, fallbackMessage: string) {
  if (result.status === "VALID") {
    return result.message?.trim() || fallbackMessage
  }
  if (result.status === "WARNING") {
    return result.message?.trim() || fallbackMessage
  }
  return result.message?.trim() || fallbackMessage
}

type FileCheckProgress = "idle" | "hashing" | "verifying" | "done" | "error"

function ReportFileVerification({ lookup }: { lookup: ReportVerificationLookup }) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [progress, setProgress] = useState<FileCheckProgress>("idle")
  const [fileName, setFileName] = useState("")
  const [fileHash, setFileHash] = useState("")
  const [verification, setVerification] = useState<ReportFileHashVerification | null>(null)
  const [errorMessage, setErrorMessage] = useState("")
  const isProcessing = progress === "hashing" || progress === "verifying"

  async function inspectFile(file: File) {
    if (!file.name.toLowerCase().endsWith(".pdf")) {
      setProgress("error")
      setFileName(file.name)
      setFileHash("")
      setVerification(null)
      setErrorMessage("PDF 파일만 검사할 수 있습니다.")
      return
    }

    setProgress("hashing")
    setFileName(file.name)
    setFileHash("")
    setVerification(null)
    setErrorMessage("")

    try {
      const hash = await calculateFileSha256(file)
      setFileHash(hash)
      setProgress("verifying")
      const checked = await verifyReportFileHash({ ...lookup, fileHash: hash })
      setVerification(checked)
      setProgress("done")
    } catch (error) {
      setProgress("error")
      setErrorMessage(getApiErrorMessage(error, "PDF 파일을 검사하지 못했습니다."))
    }
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (file) void inspectFile(file)
  }

  function chooseFile() {
    if (fileInputRef.current) fileInputRef.current.value = ""
    fileInputRef.current?.click()
  }

  const tone: CheckTone = verification
    ? verification.status === "MATCH"
      ? "ok"
      : verification.status === "WARNING"
        ? "warning"
        : "danger"
    : progress === "error"
      ? "danger"
      : "muted"
  const badge = verification
    ? verification.status === "MATCH"
      ? "등록 발행본 일치"
      : verification.status === "WARNING"
        ? "추가 확인 필요"
        : "불일치"
    : isProcessing
      ? "검사 중"
      : progress === "error"
        ? "검사 실패"
        : "선택 전"

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 dark:border-border dark:bg-card">
      <div className="flex items-center justify-between gap-3">
        <p className="flex items-center gap-1.5 text-sm font-bold text-slate-950 dark:text-foreground">
          <FileUp className="size-4 text-teal-600 dark:text-teal-300" aria-hidden="true" />
          내 PDF 파일 대조
        </p>
        <span className={cn("shrink-0 rounded-full px-2.5 py-1 text-[11px] font-bold", CHECK_BADGE_CLASS[tone])}>
          {badge}
        </span>
      </div>

      <p className="mt-1.5 text-xs font-semibold leading-5 text-slate-500 dark:text-muted-foreground">
        PDF는 서버로 전송하지 않습니다. 이 브라우저에서 SHA-256을 계산해 발행 시 등록된 해시와 비교합니다.
      </p>

      <input
        ref={fileInputRef}
        type="file"
        accept="application/pdf,.pdf"
        className="hidden"
        onChange={handleFileChange}
      />

      {isProcessing ? (
        <div className="mt-3 flex items-center gap-3 rounded-lg bg-slate-50 px-3 py-3 dark:bg-background/60">
          <Loader2 className="size-5 shrink-0 animate-spin text-teal-600 dark:text-teal-300" aria-hidden="true" />
          <div className="min-w-0">
            <p className="truncate text-xs font-bold text-slate-700 dark:text-foreground">{fileName}</p>
            <p className="mt-0.5 text-[11px] font-semibold text-slate-400">
              {progress === "hashing" ? "PDF 해시를 계산하고 있습니다." : "등록된 최종 해시와 비교하고 있습니다."}
            </p>
          </div>
        </div>
      ) : verification ? (
        <div className="mt-3">
          <p className={cn(
            "rounded-lg px-3 py-2.5 text-xs font-bold leading-5",
            verification.status === "MATCH"
              ? "bg-teal-50 text-teal-800 dark:bg-teal-500/10 dark:text-teal-300"
              : verification.status === "WARNING"
                ? "bg-amber-50 text-amber-800 dark:bg-amber-500/10 dark:text-amber-300"
                : "bg-red-50 text-red-800 dark:bg-red-500/10 dark:text-red-300"
          )}>
            {verification.message}
          </p>
          <HashLine label="선택한 PDF 해시 (SHA-256)" value={fileHash} />
          {!verification.matched ? (
            <HashLine label="등록된 최종 PDF 해시 (SHA-256)" value={verification.registeredHash} />
          ) : null}
        </div>
      ) : progress === "error" ? (
        <p className="mt-3 rounded-lg bg-red-50 px-3 py-2.5 text-xs font-bold leading-5 text-red-700 dark:bg-red-500/10 dark:text-red-300">
          {errorMessage}
        </p>
      ) : null}

      <Button
        type="button"
        variant="outline"
        className="mt-3 h-10 w-full rounded-lg font-bold"
        disabled={isProcessing}
        onClick={chooseFile}
      >
        <FileUp className="size-4" aria-hidden="true" />
        {verification || progress === "error" ? "다른 PDF 검사" : "PDF 선택"}
      </Button>
    </section>
  )
}

async function calculateFileSha256(file: File) {
  if (!globalThis.crypto?.subtle) {
    throw new Error("이 브라우저에서는 로컬 파일 해시 계산을 지원하지 않습니다.")
  }
  const digest = await globalThis.crypto.subtle.digest("SHA-256", await file.arrayBuffer())
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("")
}

function CheckCard({
  icon,
  title,
  badge,
  tone,
  note,
  children,
}: {
  icon: ReactNode
  title: string
  badge: string
  tone: CheckTone
  note: string
  children?: ReactNode
}) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 dark:border-border dark:bg-card">
      <div className="flex items-center justify-between gap-3">
        <p className="flex items-center gap-1.5 text-sm font-bold text-slate-950 dark:text-foreground">
          <span className="text-teal-600 dark:text-teal-300">{icon}</span>
          {title}
        </p>
        <span className={cn("shrink-0 rounded-full px-2.5 py-1 text-[11px] font-bold", CHECK_BADGE_CLASS[tone])}>
          {badge}
        </span>
      </div>
      <p className="mt-1.5 text-xs font-semibold leading-5 text-slate-500 dark:text-muted-foreground">{note}</p>
      {children}
    </section>
  )
}

function HashLine({ label, value }: { label: string; value: string }) {
  const [expanded, setExpanded] = useState(false)
  const [copied, setCopied] = useState(false)
  const trimmed = value?.trim() ?? ""

  if (!trimmed) return null

  async function copyValue() {
    try {
      await navigator.clipboard.writeText(trimmed)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1200)
    } catch {
      // 클립보드 접근이 차단된 환경에서는 무시
    }
  }

  return (
    <div className="mt-2.5 rounded-lg bg-slate-50 px-3 py-2.5 dark:bg-background/60">
      <p className="text-[10px] font-bold text-slate-400">{label}</p>
      <div className="mt-1 flex items-start justify-between gap-2">
        <p
          className={cn(
            "min-w-0 font-mono text-xs font-semibold text-slate-600 dark:text-muted-foreground",
            expanded ? "break-all" : "truncate"
          )}
        >
          {expanded ? trimmed : `${trimmed.slice(0, 14)}…${trimmed.slice(-10)}`}
        </p>
        <span className="flex shrink-0 items-center gap-1">
          <button
            type="button"
            onClick={copyValue}
            aria-label="해시값 복사"
            className="rounded-md p-1 text-slate-400 transition-colors hover:bg-slate-200/60 hover:text-slate-600 dark:hover:bg-secondary"
          >
            {copied ? (
              <Check className="size-3.5 text-teal-600 dark:text-teal-300" aria-hidden="true" />
            ) : (
              <Copy className="size-3.5" aria-hidden="true" />
            )}
          </button>
          <button
            type="button"
            onClick={() => setExpanded((current) => !current)}
            className="rounded-md px-1.5 py-1 text-[11px] font-bold text-teal-700 transition-colors hover:bg-slate-200/60 dark:text-teal-300 dark:hover:bg-secondary"
          >
            {expanded ? "접기" : "전체 보기"}
          </button>
        </span>
      </div>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4 text-sm">
      <dt className="shrink-0 font-medium text-slate-500 dark:text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-all text-right font-bold text-slate-950 dark:text-foreground">{value}</dd>
    </div>
  )
}
