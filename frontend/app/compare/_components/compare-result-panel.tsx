import { useState } from "react"
import { AlertTriangle, CheckCircle2, Download, GitCompare } from "lucide-react"

import { Button } from "@/components/ui/button"
import { CompareReportExportDialog } from "@/app/compare/_components/compare-report-export-dialog"
import type { CompareResult, CompareVerdict } from "@/lib/api/compare"
import { cn } from "@/lib/utils"

type CompareResultPanelProps = {
  result: CompareResult | null
  downloadError: string | null
  isDownloading: boolean
  reportApproved: boolean
  onReset: () => void
  onDownloadReport: () => void
}

export function CompareResultPanel({
  result,
  downloadError,
  isDownloading,
  reportApproved,
  onReset,
  onDownloadReport,
}: CompareResultPanelProps) {
  const [reportDialogOpen, setReportDialogOpen] = useState(false)

  if (!result) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-7 text-center shadow-sm dark:border-border dark:bg-card">
        <p className="text-sm font-bold text-slate-500 dark:text-muted-foreground">
          검증 결과가 없습니다.
        </p>
        <Button onClick={onReset} className="mt-5 h-12 rounded-lg bg-teal-600 px-6 text-base font-bold hover:bg-teal-700">
          새 검증
        </Button>
      </div>
    )
  }

  const verdict = getVerdictDisplay(result.verdict, result.summary.verdictLabel)

  return (
    <div className="space-y-6">
      <div
        className={cn(
          "flex items-start gap-4 rounded-xl border p-4 shadow-sm sm:p-6",
          verdict.containerClassName
        )}
      >
        {verdict.icon}
        <div>
          <p className="text-lg font-bold">{verdict.title}</p>
          <p className="mt-1 text-sm font-semibold opacity-80">{verdict.description}</p>
          <p className="mt-3 text-xs font-bold opacity-70">
            Compare ID: {result.compareId} · 비교 대상: 제출본
          </p>
        </div>
      </div>

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-border dark:bg-card">
        <div className="flex flex-col gap-3 border-b border-slate-200 px-4 py-4 dark:border-border sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div className="flex items-center gap-2">
            <GitCompare className="size-5 text-teal-600 dark:text-teal-300" aria-hidden="true" />
            <h1 className="text-base font-bold text-slate-950 dark:text-foreground">비교 결과</h1>
          </div>
          <div className="flex flex-wrap gap-2">
            <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300">
              일치 {result.summary.matchCount}
            </span>
            <span className="rounded-full bg-red-50 px-3 py-1 text-xs font-bold text-red-700 dark:bg-red-500/10 dark:text-red-400">
              불일치 {result.summary.mismatchCount}
            </span>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm dark:divide-border">
            <thead className="bg-slate-50 dark:bg-muted/40">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-bold text-slate-500 dark:text-muted-foreground">
                  항목
                </th>
                <th className="px-6 py-3 text-left text-xs font-bold text-slate-500 dark:text-muted-foreground">
                  기준 증거
                </th>
                <th className="px-6 py-3 text-left text-xs font-bold text-slate-500 dark:text-muted-foreground">
                  비교 대상
                </th>
                <th className="px-6 py-3 text-left text-xs font-bold text-slate-500 dark:text-muted-foreground">
                  결과
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-border">
              {result.items.map((item) => {
                const isMismatch = item.result === "MISMATCH"

                return (
                  <tr
                    key={item.itemKey}
                    className={cn(
                      isMismatch && "bg-red-50/70 text-red-700 dark:bg-red-500/10 dark:text-red-400"
                    )}
                  >
                    <td className="px-6 py-4 font-bold text-slate-900 dark:text-foreground">
                      {item.label}
                    </td>
                    <td className="max-w-[220px] truncate px-6 py-4 font-semibold text-slate-500 dark:text-muted-foreground">
                      {formatCompareItemValue(item, item.originalValue)}
                    </td>
                    <td className="max-w-[220px] truncate px-6 py-4 font-semibold text-slate-500 dark:text-muted-foreground">
                      {formatCompareItemValue(item, item.candidateValue)}
                    </td>
                    <td className="px-6 py-4 font-bold">
                      {getCompareItemResultLabel(item.result)}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Button
          variant="outline"
          onClick={onReset}
          className="h-12 w-full rounded-lg border-slate-200 bg-white px-6 text-base font-bold text-slate-950 shadow-none hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground sm:w-auto"
        >
          새 검증
        </Button>
        <Button
          onClick={() => setReportDialogOpen(true)}
          disabled={isDownloading || !reportApproved}
          title={reportApproved ? undefined : "검토자 승인 후 PDF를 열 수 있습니다"}
          variant="outline"
          className="h-12 w-full rounded-lg border-slate-200 bg-white px-6 text-base font-bold text-slate-950 shadow-none hover:bg-slate-50 dark:border-border dark:bg-card dark:text-foreground sm:w-auto"
        >
          <Download className="size-5" aria-hidden="true" />
          {isDownloading ? "PDF 생성 중" : reportApproved ? "PDF 보고서" : "승인 후 PDF"}
        </Button>
      </div>

      <CompareReportExportDialog
        open={reportDialogOpen}
        onClose={() => setReportDialogOpen(false)}
        result={result}
        isDownloading={isDownloading}
        reportApproved={reportApproved}
        downloadError={downloadError}
        onDownload={onDownloadReport}
      />

      {downloadError ? (
        <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-xs font-bold text-red-600 dark:border-red-500/25 dark:bg-red-500/10 dark:text-red-300">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          {downloadError}
        </div>
      ) : null}
    </div>
  )
}

function getCompareItemResultLabel(result: string) {
  const labels: Record<string, string> = {
    MATCH: "일치",
    MISMATCH: "불일치",
    SKIPPED: "제외",
  }

  return labels[result] ?? result
}

function formatCompareItemValue(item: { itemKey: string; label: string }, value: string) {
  const itemText = `${item.itemKey} ${item.label}`.toLowerCase()
  if (itemText.includes("filename") || itemText.includes("file_name") || item.label.includes("파일명")) {
    return "비공개"
  }
  return value || "-"
}

function getVerdictDisplay(verdict: CompareVerdict, verdictLabel: string) {
  if (verdict === "ORIGINAL_MATCH") {
    return {
      title: verdictLabel || "내용 기준 일치",
      description: "기준 증거와 비교 대상 파일이 일치합니다.",
      containerClassName:
        "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-500/25 dark:bg-emerald-500/10 dark:text-emerald-300",
      icon: <CheckCircle2 className="mt-1 size-7 shrink-0" aria-hidden="true" />,
    }
  }

  if (verdict === "TAMPERED") {
    return {
      title: verdictLabel || "내용 기준 차이 확인",
      description: "기준 증거와 비교 대상 파일 사이에 차이 항목이 확인되었습니다.",
      containerClassName:
        "border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-500/25 dark:bg-amber-500/10 dark:text-amber-300",
      icon: <AlertTriangle className="mt-1 size-7 shrink-0" aria-hidden="true" />,
    }
  }

  return {
    title: verdictLabel || "판정 보류",
    description: "일부 항목만 비교되어 최종 판정을 보류했습니다.",
    containerClassName:
      "border-slate-200 bg-slate-50 text-slate-600 dark:border-border dark:bg-muted/30 dark:text-muted-foreground",
    icon: <AlertTriangle className="mt-1 size-7 shrink-0" aria-hidden="true" />,
  }
}
