"use client"

import { useEffect, useState, type ReactNode } from "react"
import { useParams, useRouter } from "next/navigation"
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  Check,
  CheckCircle2,
  ChevronDown,
  Download,
  Loader2,
  Minus,
  ScanSearch,
  X,
} from "lucide-react"

import { SiteFooter } from "@/components/site-footer"
import { SiteHeader } from "@/components/site-header"
import { Button } from "@/components/ui/button"
import { CompareReportExportDialog } from "@/app/compare/_components/compare-report-export-dialog"
import {
  downloadCompareReport,
  fetchCompareOriginal,
  fetchCompareResult,
  type CompareBlockchainInfo,
  type CompareItem,
  type CompareItemResult,
  type CompareResult,
  type CompareSignatureInfo,
  type CompareSignatureStatus,
  type CompareVerdict,
} from "@/lib/api/compare"
import { fetchCaseDetail } from "@/lib/api/evidence-detail"
import { getApiErrorMessage } from "@/lib/api/errors"
import { getSession, isReviewerSession } from "@/lib/auth"
import { formatDateTime } from "@/lib/formatters"
import { cn } from "@/lib/utils"

export default function CompareReportPage() {
  const params = useParams()
  const router = useRouter()
  const compareIdParam = Array.isArray(params.compareId) ? params.compareId[0] : params.compareId
  const compareId = Number(compareIdParam)
  const [result, setResult] = useState<CompareResult | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isDownloading, setIsDownloading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const [reportDialogOpen, setReportDialogOpen] = useState(false)
  const [reportApprovalState, setReportApprovalState] = useState<ReportApprovalState>("loading")
  const isReviewer = isReviewerSession(getSession())

  useEffect(() => {
    let cancelled = false

    async function loadReport() {
      if (!Number.isFinite(compareId) || compareId <= 0) {
        setError("올바른 비교검증 리포트 주소가 아닙니다.")
        setIsLoading(false)
        return
      }

      setIsLoading(true)
      setError(null)
      setReportApprovalState("loading")

      try {
        const data = await fetchCompareResult(compareId)
        if (cancelled) return
        setResult(data)

        try {
          const original = await fetchCompareOriginal(data.originalEvidenceId)
          const caseKey = original.caseNumber?.trim() || original.caseName?.trim()
          if (!caseKey) {
            setReportApprovalState("unknown")
            return
          }

          const caseDetail = await fetchCaseDetail(caseKey)
          if (!cancelled) {
            setReportApprovalState(
              caseDetail.reviewStatus === "REPORT_APPROVED" ? "approved" : "pending"
            )
          }
        } catch {
          if (!cancelled) setReportApprovalState("unknown")
        }
      } catch (error) {
        if (!cancelled) {
          setError(getApiErrorMessage(error, "비교검증 리포트를 불러오지 못했습니다."))
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void loadReport()

    return () => {
      cancelled = true
    }
  }, [compareId])

  async function handleDownloadReport() {
    if (!result) return
    if (reportApprovalState !== "approved") {
      setDownloadError("검토자가 승인한 뒤 비교검증 PDF를 다운로드할 수 있습니다.")
      return
    }

    setDownloadError(null)
    setIsDownloading(true)

    try {
      const blob = await downloadCompareReport(result.compareId)
      const url = URL.createObjectURL(blob)
      const link = document.createElement("a")
      link.href = url
      link.download = `compare-report-${result.compareId}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (error) {
      setDownloadError(getApiErrorMessage(error, "PDF 리포트 다운로드에 실패했습니다."))
    } finally {
      setIsDownloading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#f6f8fa] text-slate-900 dark:bg-background dark:text-foreground">
      <SiteHeader />
      <main className="mx-auto flex w-full max-w-[1080px] flex-col gap-4 px-5 py-7 sm:px-8 lg:px-10">
        <button
          type="button"
          onClick={() => router.back()}
          className="inline-flex w-fit items-center gap-1.5 text-sm font-semibold text-slate-500 transition-colors hover:text-slate-900 dark:text-muted-foreground dark:hover:text-foreground"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          이전 화면
        </button>

        {isLoading ? (
          <ReportStateCard>
            <Loader2 className="size-9 animate-spin text-slate-400" aria-hidden="true" />
            <p className="text-sm font-semibold text-muted-foreground">비교검증 리포트를 불러오는 중입니다.</p>
          </ReportStateCard>
        ) : error ? (
          <ReportStateCard>
            <AlertCircle className="size-9 text-slate-400" aria-hidden="true" />
            <div className="space-y-2 text-center">
              <p className="text-base font-bold text-foreground">리포트를 열 수 없습니다.</p>
              <p className="text-sm font-semibold text-muted-foreground">{error}</p>
            </div>
            {!isReviewer ? (
              <Button
                variant="outline"
                className="h-10 w-full rounded-lg px-4 font-bold sm:w-auto"
                onClick={() => router.push("/compare")}
              >
                새 비교검증
              </Button>
            ) : null}
          </ReportStateCard>
        ) : result ? (
          <CompareReport
            result={result}
            isDownloading={isDownloading}
            onOpenReport={() => setReportDialogOpen(true)}
            readOnly={isReviewer}
            reportApprovalState={reportApprovalState}
          />
        ) : null}

        {result ? (
          <CompareReportExportDialog
            open={reportDialogOpen}
            onClose={() => setReportDialogOpen(false)}
            result={result}
            isDownloading={isDownloading}
            reportApproved={reportApprovalState === "approved"}
            downloadError={downloadError}
            onDownload={handleDownloadReport}
          />
        ) : null}

        {downloadError ? (
          <div className="flex items-start gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-red-700 dark:border-border dark:bg-card dark:text-red-400">
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {downloadError}
          </div>
        ) : null}
      </main>
      <SiteFooter />
    </div>
  )
}

type ReportApprovalState = "loading" | "approved" | "pending" | "unknown"

type LayerTone = "danger" | "neutral" | "muted"

type LayerStatus = {
  status: string
  tone: LayerTone
}

function CompareReport({
  result,
  isDownloading,
  onOpenReport,
  readOnly = false,
  reportApprovalState,
}: {
  result: CompareResult
  isDownloading: boolean
  onOpenReport: () => void
  readOnly?: boolean
  reportApprovalState: ReportApprovalState
}) {
  const router = useRouter()
  const verdict = getVerdictDisplay(result)
  const mismatchItems = result.items.filter((item) => item.result === "MISMATCH")
  const matchItems = result.items.filter((item) => item.result === "MATCH")
  const skippedItems = result.items.filter((item) => item.result === "SKIPPED")
  const orderedItems = [...mismatchItems, ...matchItems, ...skippedItems]

  const [itemsOpen, setItemsOpen] = useState(false)
  const [signatureOpen, setSignatureOpen] = useState(false)
  const [blockchainOpen, setBlockchainOpen] = useState(false)

  const itemsLayer: LayerStatus =
    mismatchItems.length > 0
      ? { status: `불일치 ${mismatchItems.length}건`, tone: "danger" }
      : { status: "전체 일치", tone: "neutral" }
  const signatureLayer = result.signature ? getSignatureLayerStatus(result.signature) : null
  const blockchainLayer = result.blockchain ? getBlockchainLayerStatus(result.blockchain) : null

  function openSection(key: "items" | "signature" | "blockchain") {
    if (key === "items") setItemsOpen(true)
    if (key === "signature") setSignatureOpen(true)
    if (key === "blockchain") setBlockchainOpen(true)
    window.setTimeout(() => {
      document.getElementById(`section-${key}`)?.scrollIntoView({ behavior: "smooth", block: "start" })
    }, 80)
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col items-stretch justify-between gap-3 sm:flex-row sm:flex-wrap sm:items-end">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-950 dark:text-foreground">비교검증 결과</h1>
          <p className="mt-1 text-sm font-medium text-slate-500 dark:text-muted-foreground">
            저장된 검증 기록입니다. 이 페이지 주소로 언제든 다시 열 수 있습니다.
          </p>
        </div>
        <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:gap-3">
          {!readOnly ? (
            <Button
              type="button"
              variant="outline"
              className="h-12 w-full rounded-lg border-slate-200 bg-white px-6 text-base font-bold text-slate-950 shadow-none hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground sm:w-auto"
              onClick={() => router.push("/compare")}
            >
              새 검증
            </Button>
          ) : null}
          <Button
            type="button"
            variant="outline"
            className="h-12 w-full rounded-lg border-slate-200 bg-white px-6 text-base font-bold text-slate-950 shadow-none hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground sm:w-auto"
            onClick={onOpenReport}
            disabled={isDownloading || reportApprovalState !== "approved"}
            title={
              reportApprovalState === "approved"
                ? undefined
                : reportApprovalState === "loading"
                  ? "검토 승인 상태를 확인하고 있습니다"
                  : "검토자 승인 후 PDF를 열 수 있습니다"
            }
          >
            {isDownloading ? (
              <Loader2 className="size-5 animate-spin" aria-hidden="true" />
            ) : (
              <Download className="size-5" aria-hidden="true" />
            )}
            {isDownloading
              ? "PDF 생성 중"
              : reportApprovalState === "loading"
                ? "승인 상태 확인 중"
                : reportApprovalState === "approved"
                  ? "PDF 보고서"
                  : "승인 후 PDF"}
          </Button>
        </div>
      </div>

      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-border dark:bg-card">
        <div className="flex items-start gap-3.5 px-6 py-5">
          {verdict.icon}
          <div className="min-w-0">
            <p className={cn("text-lg font-bold", verdict.text)}>{verdict.title}</p>
            <p className="mt-0.5 text-sm font-medium text-slate-500 dark:text-muted-foreground">
              {verdict.description}
            </p>
            <p className="mt-2.5 truncate text-xs font-semibold text-slate-400">
              기준 증거 EVD-{result.originalEvidenceId} · 제출본 파일과 비교 · {formatDateTime(result.createdAt)} 검증
            </p>
          </div>
        </div>
        <div className="grid divide-y divide-slate-100 border-t border-slate-100 dark:divide-border dark:border-border sm:grid-cols-3 sm:divide-x sm:divide-y-0">
          <VerdictLayerCell label="항목 비교" layer={itemsLayer} onClick={() => openSection("items")} />
          <VerdictLayerCell
            label="전자서명"
            layer={signatureLayer ?? { status: "정보 없음", tone: "muted" }}
            onClick={signatureLayer ? () => openSection("signature") : undefined}
          />
          <VerdictLayerCell
            label="블록체인 기록"
            layer={blockchainLayer ?? { status: "정보 없음", tone: "muted" }}
            onClick={blockchainLayer ? () => openSection("blockchain") : undefined}
          />
        </div>
      </section>

      <CollapsibleSection
        id="section-items"
        title="항목별 비교"
        subtitle="해시·메타데이터 항목을 기준 증거와 대조합니다."
        layer={itemsLayer}
        open={itemsOpen}
        onToggle={() => setItemsOpen((current) => !current)}
      >
        <ItemComparisonBody
          orderedItems={orderedItems}
          mismatchCount={mismatchItems.length}
          summaryText={`일치 ${result.summary.matchCount} · 불일치 ${result.summary.mismatchCount} · 제외 ${result.summary.skippedCount}`}
        />
      </CollapsibleSection>

      {result.signature && signatureLayer ? (
        <CollapsibleSection
          id="section-signature"
          title="전자서명 검증"
          subtitle="서명이 유효하지 않으면 서명 이후 파일이 변경되었음을 의미합니다."
          layer={signatureLayer}
          open={signatureOpen}
          onToggle={() => setSignatureOpen((current) => !current)}
        >
          <SignatureBody signature={result.signature} />
        </CollapsibleSection>
      ) : null}

      {result.blockchain && blockchainLayer ? (
        <CollapsibleSection
          id="section-blockchain"
          title="블록체인 기록 검증"
          subtitle="원본 등록 시 기록된 해시와 대조해 위·변조 여부를 확인합니다."
          layer={blockchainLayer}
          open={blockchainOpen}
          onToggle={() => setBlockchainOpen((current) => !current)}
        >
          <BlockchainBody blockchain={result.blockchain} />
        </CollapsibleSection>
      ) : null}

      {result.verdict === "TAMPERED" ? (
        <section className="flex flex-wrap items-start gap-3 rounded-xl border border-slate-200 bg-white p-5 dark:border-border dark:bg-card">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-600 dark:bg-secondary dark:text-muted-foreground">
            <ScanSearch className="size-5" aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="text-sm font-bold text-slate-950 dark:text-foreground">어느 부분이 변조됐는지 확인하려면</h2>
            <p className="mt-1 text-sm font-medium leading-6 text-slate-500">
              이 비교는 파일·메타데이터 기준의 사실 판정입니다. 변조 위치와 방식에 대한 AI 추정 분석이 필요하면 비교
              대상 파일을 사건 증거로 등록한 뒤 딥페이크 분석을 요청하세요. AI 분석 결과는 확정 판정이 아닌 참고
              소견으로 제공됩니다.
            </p>
          </div>
        </section>
      ) : null}

      <p className="text-xs font-medium text-slate-400">
        검증 기록 · Compare #{result.compareId} · 기준 증거 EVD-{result.originalEvidenceId} · 수행일시{" "}
        {formatDateTime(result.createdAt)}
      </p>
    </div>
  )
}

function VerdictLayerCell({
  label,
  layer,
  onClick,
}: {
  label: string
  layer: LayerStatus
  onClick?: () => void
}) {
  const content = (
    <>
      <p className="text-xs font-semibold text-slate-400">{label}</p>
      <p className={cn("mt-1 flex items-center gap-1.5 text-sm font-bold", getLayerToneClassName(layer.tone))}>
        {getLayerToneIcon(layer.tone)}
        {layer.status}
      </p>
    </>
  )

  if (!onClick) {
    return <div className="px-6 py-3.5">{content}</div>
  }

  return (
    <button
      type="button"
      onClick={onClick}
      className="px-6 py-3.5 text-left transition-colors hover:bg-slate-50 dark:hover:bg-secondary/30"
    >
      {content}
    </button>
  )
}

function CollapsibleSection({
  id,
  title,
  subtitle,
  layer,
  open,
  onToggle,
  children,
}: {
  id: string
  title: string
  subtitle: string
  layer: LayerStatus
  open: boolean
  onToggle: () => void
  children: ReactNode
}) {
  return (
    <section
      id={id}
      className="scroll-mt-24 overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-border dark:bg-card"
    >
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-3 px-6 py-4 text-left transition-colors hover:bg-slate-50/70 dark:hover:bg-secondary/30"
      >
        <div className="min-w-0">
          <h2 className="text-base font-bold text-slate-950 dark:text-foreground">{title}</h2>
          <p className="mt-0.5 truncate text-xs font-semibold text-slate-500">{subtitle}</p>
        </div>
        <span className="flex shrink-0 items-center gap-2.5">
          <span className={cn("flex items-center gap-1.5 text-sm font-bold", getLayerToneClassName(layer.tone))}>
            {getLayerToneIcon(layer.tone)}
            {layer.status}
          </span>
          <ChevronDown
            className={cn("size-4 text-slate-300 transition-transform", open && "rotate-180")}
            aria-hidden="true"
          />
        </span>
      </button>
      {open ? <div className="border-t border-slate-100 dark:border-border">{children}</div> : null}
    </section>
  )
}

function getLayerToneClassName(tone: LayerTone) {
  return cn(
    tone === "danger" && "text-red-700 dark:text-red-400",
    tone === "neutral" && "text-slate-700 dark:text-foreground",
    tone === "muted" && "text-slate-400"
  )
}

function getLayerToneIcon(tone: LayerTone) {
  return {
    danger: <X className="size-3.5 shrink-0 text-red-700 dark:text-red-400" aria-hidden="true" />,
    neutral: <Check className="size-3.5 shrink-0 text-slate-400" aria-hidden="true" />,
    muted: <Minus className="size-3.5 shrink-0 text-slate-300" aria-hidden="true" />,
  }[tone]
}

function ItemComparisonBody({
  orderedItems,
  mismatchCount,
  summaryText,
}: {
  orderedItems: CompareItem[]
  mismatchCount: number
  summaryText: string
}) {
  const [showAllItems, setShowAllItems] = useState(false)
  const defaultVisibleCount = Math.max(mismatchCount + 3, 5)
  const visibleItems = showAllItems ? orderedItems : orderedItems.slice(0, defaultVisibleCount)
  const hiddenCount = orderedItems.length - visibleItems.length

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-6 py-3 dark:border-border">
        <p className="text-xs font-semibold text-slate-500">
          불일치 항목을 먼저 표시합니다. 긴 값은 클릭하면 전체 값이 복사됩니다.
        </p>
        <p className="text-xs font-semibold text-slate-400">{summaryText}</p>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-100 dark:border-border">
              <th className="px-6 py-3 text-left text-xs font-bold text-slate-400">항목</th>
              <th className="px-6 py-3 text-left text-xs font-bold text-slate-400">기준 증거</th>
              <th className="px-6 py-3 text-left text-xs font-bold text-slate-400">비교 대상</th>
              <th className="w-24 px-6 py-3 text-left text-xs font-bold text-slate-400">결과</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-border">
            {visibleItems.map((item) => (
              <CompareItemRow key={item.itemKey} item={item} />
            ))}
          </tbody>
        </table>
      </div>
      {hiddenCount > 0 || showAllItems ? (
        <button
          type="button"
          onClick={() => setShowAllItems((current) => !current)}
          className="w-full border-t border-slate-100 px-6 py-3 text-center text-xs font-bold text-slate-500 transition-colors hover:bg-slate-50 hover:text-slate-700 dark:border-border dark:hover:bg-secondary/40"
        >
          {showAllItems ? "항목 접기" : `일치·제외 항목 ${hiddenCount}개 더 보기`}
        </button>
      ) : null}
      <p className="border-t border-slate-100 px-6 py-3 text-xs font-medium leading-5 text-slate-400 dark:border-border">
        SHA-256 같은 해시값은 바이트 단위 동일성 확인용입니다. 재인코딩·컨테이너 변경만으로도 값이 달라질 수 있어
        내용 기준 판정과 별도로 해석해야 합니다.
      </p>
    </div>
  )
}

function SignatureBody({ signature }: { signature: CompareSignatureInfo }) {
  const metaRows = [
    { label: "알고리즘", value: signature.algorithm },
    { label: "서명자", value: signature.signedBy },
    { label: "서명일시", value: signature.signedAt ? formatDateTime(signature.signedAt) : null },
  ].filter((row) => Boolean(row.value))

  return (
    <div className="px-6 py-1">
      <SignatureRow label="기준 증거 서명" status={signature.originalStatus} />
      <SignatureRow label="비교 대상 서명" status={signature.candidateStatus} />
      {metaRows.length > 0 ? (
        <div className="border-t border-slate-100 py-3 dark:border-border">
          {metaRows.map((row) => (
            <KeyValueRow key={row.label} label={row.label} value={row.value as string} />
          ))}
        </div>
      ) : null}
    </div>
  )
}

function SignatureRow({ label, status }: { label: string; status: CompareSignatureStatus }) {
  const display = {
    VALID: {
      badge: "유효",
      badgeClassName: "text-slate-600 dark:text-muted-foreground",
      note: "서명이 파일 내용과 일치합니다.",
      icon: <CheckCircle2 className="size-4 text-slate-400" aria-hidden="true" />,
    },
    INVALID: {
      badge: "유효하지 않음",
      badgeClassName: "text-red-700 dark:text-red-400",
      note: "서명 이후 파일이 변경된 것으로 확인됩니다.",
      icon: <AlertTriangle className="size-4 text-red-700 dark:text-red-400" aria-hidden="true" />,
    },
    UNSIGNED: {
      badge: "서명 없음",
      badgeClassName: "text-slate-400",
      note: "전자서명이 확인되지 않았습니다.",
      icon: <AlertCircle className="size-4 text-slate-300" aria-hidden="true" />,
    },
  }[status]

  return (
    <div className="flex items-center justify-between gap-4 border-b border-slate-100 py-3.5 last:border-0 dark:border-border">
      <div className="min-w-0">
        <p className="text-sm font-bold text-slate-800 dark:text-foreground">{label}</p>
        <p className="mt-0.5 text-xs font-semibold text-slate-400">{display.note}</p>
      </div>
      <span className={cn("flex shrink-0 items-center gap-1.5 text-sm font-bold", display.badgeClassName)}>
        {display.icon}
        {display.badge}
      </span>
    </div>
  )
}

function BlockchainBody({ blockchain }: { blockchain: CompareBlockchainInfo }) {
  const note = {
    MATCH:
      "원본 증거 등록 당시 파일 해시가 블록체인에 기록되어 있으며, 기준 증거의 해시와 일치합니다. 기준 증거가 등록 이후 변경되지 않았음을 의미합니다.",
    MISMATCH:
      "블록체인에 기록된 해시와 기준 증거의 해시가 일치하지 않습니다. 기준 증거가 등록 이후 변경되었을 가능성이 있습니다.",
    NOT_ANCHORED: "이 증거는 블록체인에 기록되어 있지 않습니다.",
  }[blockchain.status]

  const metaRows = [
    { label: "네트워크", value: blockchain.network },
    { label: "블록 번호", value: blockchain.blockNumber ? `#${blockchain.blockNumber.toLocaleString()}` : null },
    { label: "앵커링 일시", value: blockchain.anchoredAt ? formatDateTime(blockchain.anchoredAt) : null },
  ].filter((row) => Boolean(row.value))

  return (
    <div className="px-6 py-1">
      <p className="py-3.5 text-sm font-medium leading-6 text-slate-500 dark:text-muted-foreground">{note}</p>
      {metaRows.length > 0 || blockchain.txHash || blockchain.anchoredHash ? (
        <div className="border-t border-slate-100 py-3 dark:border-border">
          {metaRows.map((row) => (
            <KeyValueRow key={row.label} label={row.label} value={row.value as string} />
          ))}
          {blockchain.txHash ? <KeyValueRow label="트랜잭션 해시" value={blockchain.txHash} mono /> : null}
          {blockchain.anchoredHash ? <KeyValueRow label="기록된 해시" value={blockchain.anchoredHash} mono /> : null}
        </div>
      ) : null}
    </div>
  )
}

function KeyValueRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-4 py-1.5">
      <p className="shrink-0 text-xs font-semibold text-slate-400">{label}</p>
      {mono ? (
        <CompareValue value={value} />
      ) : (
        <p className="truncate text-xs font-bold text-slate-600 dark:text-muted-foreground">{value}</p>
      )}
    </div>
  )
}

function CompareItemRow({ item }: { item: CompareItem }) {
  const isMismatch = item.result === "MISMATCH"
  const isSkipped = item.result === "SKIPPED"

  return (
    <tr className={cn(isSkipped && "text-slate-300")}>
      <td className="px-6 py-3 font-bold text-slate-900 dark:text-foreground">{item.label}</td>
      <td className="max-w-[240px] px-6 py-3">
        <CompareValue value={formatCompareItemValue(item, item.originalValue)} />
      </td>
      <td className="max-w-[240px] px-6 py-3">
        <CompareValue value={formatCompareItemValue(item, item.candidateValue)} tone={isMismatch ? "danger" : undefined} />
      </td>
      <td
        className={cn(
          "px-6 py-3 font-bold",
          isMismatch ? "text-red-700 dark:text-red-400" : isSkipped ? "text-slate-300" : "text-slate-400"
        )}
      >
        {getCompareItemResultLabel(item.result)}
      </td>
    </tr>
  )
}

function CompareValue({ value, tone }: { value: string; tone?: "danger" }) {
  const [copied, setCopied] = useState(false)
  const trimmed = value?.trim() ?? ""

  if (!trimmed) {
    return <span className="font-semibold text-slate-300">-</span>
  }

  const isLong = trimmed.length > 26
  const textClassName = cn(
    "font-semibold",
    tone === "danger" ? "text-red-700 dark:text-red-400" : "text-slate-500 dark:text-muted-foreground"
  )

  if (!isLong) {
    return <span className={textClassName}>{trimmed}</span>
  }

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
    <button
      type="button"
      onClick={copyValue}
      title={`${trimmed}\n클릭하면 전체 값이 복사됩니다.`}
      className={cn("cursor-copy font-mono text-xs", textClassName, copied && "text-teal-700 dark:text-teal-300")}
    >
      {copied ? "복사됨" : `${trimmed.slice(0, 10)}…${trimmed.slice(-8)}`}
    </button>
  )
}

function ReportStateCard({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-[48vh] flex-col items-center justify-center gap-4 rounded-xl border border-border bg-card p-8">
      {children}
    </div>
  )
}

function getVerdictDisplay(result: CompareResult) {
  const mismatchCount = result.summary.mismatchCount
  const verdict: CompareVerdict = result.verdict

  if (verdict === "ORIGINAL_MATCH") {
    return {
      title: result.summary.verdictLabel || "원본과 일치",
      description: "비교 가능한 모든 항목이 기준 증거와 일치합니다.",
      text: "text-teal-700 dark:text-teal-300",
      icon: <CheckCircle2 className="mt-0.5 size-6 shrink-0 text-teal-600 dark:text-teal-300" aria-hidden="true" />,
    }
  }

  if (verdict === "TAMPERED") {
    return {
      title: result.summary.verdictLabel || "원본과 차이 확인",
      description: `${mismatchCount}개 항목에서 기준 증거와 다른 값이 확인되었습니다. 원본과 동일한 파일이 아닙니다.`,
      text: "text-red-700 dark:text-red-400",
      icon: <AlertTriangle className="mt-0.5 size-6 shrink-0 text-red-700 dark:text-red-400" aria-hidden="true" />,
    }
  }

  return {
    title: result.summary.verdictLabel || "판정 보류",
    description: "데이터가 부족하거나 일부 항목만 비교되어 판정을 보류했습니다.",
    text: "text-slate-700 dark:text-foreground",
    icon: <AlertCircle className="mt-0.5 size-6 shrink-0 text-slate-500" aria-hidden="true" />,
  }
}

function getSignatureLayerStatus(signature: CompareSignatureInfo): LayerStatus {
  if (signature.candidateStatus === "INVALID" || signature.originalStatus === "INVALID") {
    return {
      status: signature.candidateStatus === "INVALID" ? "제출본 서명 무효" : "기준 증거 서명 무효",
      tone: "danger",
    }
  }
  if (signature.candidateStatus === "VALID" && signature.originalStatus === "VALID") {
    return { status: "서명 유효", tone: "neutral" }
  }
  return { status: "서명 없음", tone: "muted" }
}

function getBlockchainLayerStatus(blockchain: CompareBlockchainInfo): LayerStatus {
  if (blockchain.status === "MATCH") {
    return { status: "원본 해시 확인", tone: "neutral" }
  }
  if (blockchain.status === "MISMATCH") {
    return { status: "기록 해시 불일치", tone: "danger" }
  }
  return { status: "기록 없음", tone: "muted" }
}

function getCompareItemResultLabel(result: CompareItemResult) {
  const labels: Record<CompareItemResult, string> = {
    MATCH: "일치",
    MISMATCH: "불일치",
    SKIPPED: "제외",
  }

  return labels[result]
}

function formatCompareItemValue(item: { itemKey: string; label: string }, value: string) {
  const itemText = `${item.itemKey} ${item.label}`.toLowerCase()
  if (itemText.includes("filename") || itemText.includes("file_name") || item.label.includes("파일명")) {
    return "비공개"
  }
  return value || "-"
}
