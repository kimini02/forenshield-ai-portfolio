import { apiDownload, apiRequest } from "@/lib/api/client"
import { features } from "@/lib/features"

export type ReportType = "ANALYSIS" | "COMPARE" | string

export type ReportSummary = {
  reportId: number
  reportType: ReportType
  evidenceId: number | null
  compareId?: number | null
  caseId?: string | null
  caseName?: string | null
  reportFileName: string
  verdictLabel?: string | null
  createdAt: string
  reportHash: string
  downloadPath: string
}

export type ReportListPage = {
  content: ReportSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export async function fetchReports(page = 0, size = 10): Promise<ReportListPage> {
  if (features.mockApi) {
    await delay(350)
    return buildMockReports(page, size)
  }

  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<ReportListPage>(`/api/v1/reports?${params.toString()}`)
}

export async function downloadReportPdf(report: ReportSummary): Promise<Blob> {
  if (features.mockApi) {
    const response = await fetch("/mock/report-sample.pdf")
    if (!response.ok) {
      throw new Error("샘플 PDF를 불러오지 못했습니다.")
    }
    return response.blob()
  }

  return apiDownload(report.downloadPath)
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

const MOCK_CASE_NAMES = [
  "협박 메시지 영상 분석",
  "딥페이크 정상/의심 비교 사건",
  "CCTV 위변조 검증 요청",
  "인터뷰 클립 진위 확인",
  "블랙박스 영상 원본성 확인",
]

const MOCK_ANALYSIS_VERDICTS = ["위험", "주의", "정상"]
const MOCK_COMPARE_VERDICTS = ["원본 일치", "원본과 차이 확인", "판정 보류"]

function buildMockReportList(): ReportSummary[] {
  const reports: ReportSummary[] = []
  for (let index = 0; index < 23; index++) {
    const isCompare = index % 3 === 1
    const evidenceId = 2024062700 + index
    const compareId = isCompare ? 8800 + index : null
    const day = String(14 - (index % 10)).padStart(2, "0")
    const hour = String(9 + (index % 9)).padStart(2, "0")
    const hashPrefix = index.toString(16).padStart(2, "0")
    reports.push({
      reportId: 100 - index,
      reportType: isCompare ? "COMPARE" : "ANALYSIS",
      evidenceId,
      compareId,
      caseId: `CASE-2026${String(700 + index)}`,
      caseName: MOCK_CASE_NAMES[index % MOCK_CASE_NAMES.length],
      reportFileName: isCompare
        ? `ForenShield_Compare_Report_${compareId}.pdf`
        : `ForenShield_Report_EVD-${evidenceId}.pdf`,
      verdictLabel: isCompare
        ? MOCK_COMPARE_VERDICTS[index % MOCK_COMPARE_VERDICTS.length]
        : MOCK_ANALYSIS_VERDICTS[index % MOCK_ANALYSIS_VERDICTS.length],
      createdAt: `2026-07-${day}T${hour}:20:00+09:00`,
      reportHash: `${hashPrefix}f81c09d2e47b16f8c05a913e2d84c7715f0b6a8d94e21c3b7f6a0d5e8c92d4`.slice(0, 64),
      downloadPath: isCompare
        ? `/api/v1/compare/${compareId}/reports/pdf`
        : `/api/v1/evidences/${evidenceId}/reports/pdf`,
    })
  }
  return reports
}

function buildMockReports(page: number, size: number): ReportListPage {
  const reports = buildMockReportList()
  const start = page * size
  return {
    content: reports.slice(start, start + size),
    page,
    size,
    totalElements: reports.length,
    totalPages: Math.max(1, Math.ceil(reports.length / size)),
  }
}
