export type ReportIssueStatus =
  | "NOT_REQUIRED"
  | "PENDING"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "PARTIAL_FAILED"

export type ReportIssueUi = {
  label: string
  description: string
  tone: "neutral" | "info" | "success" | "danger" | "warning"
}

const REPORT_ISSUE_UI: Record<ReportIssueStatus, ReportIssueUi> = {
  NOT_REQUIRED: {
    label: "발급 대상 보고서 없음",
    description: "현재 사건에는 발급할 보고서가 없습니다.",
    tone: "neutral",
  },
  PENDING: {
    label: "보고서 발급 대기 중",
    description: "검토 승인은 완료되었습니다. 보고서를 발급하고 있습니다.",
    tone: "info",
  },
  PROCESSING: {
    label: "보고서 발급 중",
    description: "검토 승인은 완료되었습니다. 보고서를 발급하고 있습니다.",
    tone: "info",
  },
  COMPLETED: {
    label: "보고서 발급 완료",
    description: "발급된 PDF 보고서를 확인하고 다운로드할 수 있습니다.",
    tone: "success",
  },
  FAILED: {
    label: "보고서 발급 실패",
    description: "보고서를 발급하지 못했습니다. 잠시 후 상태를 다시 확인해 주세요.",
    tone: "danger",
  },
  PARTIAL_FAILED: {
    label: "일부 보고서 발급 실패",
    description: "일부 보고서만 발급되었습니다. 발급 완료된 보고서는 확인할 수 있습니다.",
    tone: "warning",
  },
}

export function normalizeReportIssueStatus(
  status?: ReportIssueStatus | null
): ReportIssueStatus {
  return status ?? "NOT_REQUIRED"
}

export function getReportIssueUi(status?: ReportIssueStatus | null): ReportIssueUi {
  return REPORT_ISSUE_UI[normalizeReportIssueStatus(status)]
}

export function isReportIssuePolling(status?: ReportIssueStatus | null): boolean {
  return status === "PENDING" || status === "PROCESSING"
}

export function canDownloadIssuedReport({
  reviewStatus,
  reportIssueStatus,
  publicationStatus,
}: {
  reviewStatus?: string | null
  reportIssueStatus?: ReportIssueStatus | null
  publicationStatus?: string | null
}): boolean {
  if (reviewStatus !== "REPORT_APPROVED") return false
  if (reportIssueStatus === "COMPLETED") return true
  return reportIssueStatus === "PARTIAL_FAILED" && publicationStatus === "ISSUED"
}

type PollingTimer = ReturnType<typeof setTimeout>

export type ReportIssuePollingClock = {
  now: () => number
  setTimeout: (callback: () => void, delayMs: number) => PollingTimer
  clearTimeout: (timer: PollingTimer) => void
}

type StartReportIssuePollingOptions<T> = {
  fetchSnapshot: () => Promise<T>
  getStatus: (snapshot: T) => ReportIssueStatus | null | undefined
  onSnapshot: (snapshot: T) => void
  onTimeout: () => void
  intervalMs?: number
  maxDurationMs?: number
  clock?: ReportIssuePollingClock
}

const browserClock: ReportIssuePollingClock = {
  now: () => Date.now(),
  setTimeout: (callback, delayMs) => setTimeout(callback, delayMs),
  clearTimeout: (timer) => clearTimeout(timer),
}

export function startReportIssuePolling<T>({
  fetchSnapshot,
  getStatus,
  onSnapshot,
  onTimeout,
  intervalMs = 2_000,
  maxDurationMs = 60_000,
  clock = browserClock,
}: StartReportIssuePollingOptions<T>): () => void {
  const startedAt = clock.now()
  let stopped = false
  let timer: PollingTimer | null = null

  const schedule = () => {
    if (stopped) return
    timer = clock.setTimeout(() => void poll(), intervalMs)
  }

  const poll = async () => {
    if (stopped) return
    if (clock.now() - startedAt >= maxDurationMs) {
      stopped = true
      onTimeout()
      return
    }

    try {
      const snapshot = await fetchSnapshot()
      if (stopped) return
      onSnapshot(snapshot)
      if (!isReportIssuePolling(getStatus(snapshot))) {
        stopped = true
        return
      }
    } catch {
      if (stopped) return
    }

    if (clock.now() - startedAt >= maxDurationMs) {
      stopped = true
      onTimeout()
      return
    }
    schedule()
  }

  schedule()
  return () => {
    stopped = true
    if (timer != null) clock.clearTimeout(timer)
  }
}
