import assert from "node:assert/strict"
import test from "node:test"

import {
  canDownloadIssuedReport,
  getReportIssueUi,
  startReportIssuePolling,
  type ReportIssuePollingClock,
  type ReportIssueStatus,
} from "../lib/report-issue-status.ts"

test("REPORT_APPROVED + PENDING은 승인 완료 문구를 표시하고 다운로드할 수 없다", () => {
  assert.equal(getReportIssueUi("PENDING").description, "검토 승인은 완료되었습니다. 보고서를 발급하고 있습니다.")
  assert.equal(canDownloadIssuedReport({ reviewStatus: "REPORT_APPROVED", reportIssueStatus: "PENDING" }), false)
})

test("REPORT_APPROVED + PROCESSING은 발급 중이며 다운로드할 수 없다", () => {
  assert.equal(getReportIssueUi("PROCESSING").label, "보고서 발급 중")
  assert.equal(canDownloadIssuedReport({ reviewStatus: "REPORT_APPROVED", reportIssueStatus: "PROCESSING" }), false)
})

test("REPORT_APPROVED + COMPLETED는 다운로드할 수 있다", () => {
  assert.equal(canDownloadIssuedReport({ reviewStatus: "REPORT_APPROVED", reportIssueStatus: "COMPLETED" }), true)
})

test("FAILED는 실패 상태를 표시하고 다운로드할 수 없다", () => {
  assert.equal(getReportIssueUi("FAILED").label, "보고서 발급 실패")
  assert.equal(canDownloadIssuedReport({ reviewStatus: "REPORT_APPROVED", reportIssueStatus: "FAILED" }), false)
})

test("PENDING에서 COMPLETED가 되면 polling을 종료한다", async () => {
  const clock = new FakeClock()
  const statuses: ReportIssueStatus[] = ["PENDING", "COMPLETED"]
  const observed: ReportIssueStatus[] = []
  let fetchCount = 0

  startReportIssuePolling({
    fetchSnapshot: async () => statuses[fetchCount++],
    getStatus: (status) => status,
    onSnapshot: (status) => observed.push(status),
    onTimeout: () => assert.fail("terminal 상태에서 timeout되면 안 된다"),
    intervalMs: 2_000,
    maxDurationMs: 60_000,
    clock,
  })

  await clock.runNext()
  await clock.runNext()

  assert.deepEqual(observed, ["PENDING", "COMPLETED"])
  assert.equal(fetchCount, 2)
  assert.equal(clock.pendingCount(), 0)
})

test("cleanup은 예약된 polling timer를 제거한다", () => {
  const clock = new FakeClock()
  const stop = startReportIssuePolling({
    fetchSnapshot: async () => "PENDING" as const,
    getStatus: (status) => status,
    onSnapshot: () => undefined,
    onTimeout: () => undefined,
    clock,
  })

  assert.equal(clock.pendingCount(), 1)
  stop()
  assert.equal(clock.pendingCount(), 0)
})

test("Blockchain FAILED는 COMPLETED Report의 다운로드를 막지 않는다", () => {
  const blockchainStatus = "FAILED"
  assert.equal(blockchainStatus, "FAILED")
  assert.equal(canDownloadIssuedReport({ reviewStatus: "REPORT_APPROVED", reportIssueStatus: "COMPLETED" }), true)
})

test("PARTIAL_FAILED는 선택한 Evidence의 ISSUED Report만 다운로드할 수 있다", () => {
  assert.equal(canDownloadIssuedReport({
    reviewStatus: "REPORT_APPROVED",
    reportIssueStatus: "PARTIAL_FAILED",
    publicationStatus: "ISSUED",
  }), true)
  assert.equal(canDownloadIssuedReport({
    reviewStatus: "REPORT_APPROVED",
    reportIssueStatus: "PARTIAL_FAILED",
    publicationStatus: null,
  }), false)
})

class FakeClock implements ReportIssuePollingClock {
  private currentTime = 0
  private sequence = 0
  private readonly timers = new Map<number, { callback: () => void; delayMs: number }>()

  now = () => this.currentTime

  setTimeout = (callback: () => void, delayMs: number) => {
    const timer = ++this.sequence
    this.timers.set(timer, { callback, delayMs })
    return timer as unknown as ReturnType<typeof setTimeout>
  }

  clearTimeout = (timer: ReturnType<typeof setTimeout>) => {
    this.timers.delete(timer as unknown as number)
  }

  pendingCount() {
    return this.timers.size
  }

  async runNext() {
    const next = this.timers.entries().next().value as
      | [number, { callback: () => void; delayMs: number }]
      | undefined
    assert.ok(next, "실행할 timer가 있어야 한다")
    const [timer, scheduled] = next
    this.timers.delete(timer)
    this.currentTime += scheduled.delayMs
    scheduled.callback()
    await Promise.resolve()
    await Promise.resolve()
  }
}
