import Link from "next/link"
import { ChevronRight, FileStack } from "lucide-react"
import type { CaseSummary } from "@/app/mypage/_types/case"
import { formatCreatedAt } from "@/app/mypage/_lib/format-date"
import { CaseStatusBadge } from "@/app/mypage/_components/case-status-badge"
import { CaseHistoryEmpty } from "@/app/mypage/_components/case-history-empty"
import type { DateFormat } from "@/lib/user-settings"
import { reviewStatusLabelMap } from "@/lib/permissions"
import { buildCaseDetailPath } from "@/lib/route-params"

export function CaseHistoryList({
  cases,
  dateFormat = "kr",
}: {
  cases: CaseSummary[]
  dateFormat?: DateFormat
}) {
  if (cases.length === 0) {
    return <CaseHistoryEmpty />
  }

  return (
    <>
      {/* 모바일: 카드형 리스트 */}
      <ul className="divide-y divide-border md:hidden">
        {cases.map((item) => (
          <li key={item.caseId}>
            <Link
              href={buildCaseDetailPath(item.caseId)}
              className="flex items-center gap-4 px-5 py-4 transition-colors hover:bg-accent/40"
            >
              <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-secondary text-muted-foreground">
                <FileStack className="size-4" aria-hidden="true" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-foreground">
                  {item.caseName}
                </p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {formatRepresentativeEvidence(item)} · 증거 {item.evidenceCount}건
                </p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {formatCreatedAt(item.createdAt, dateFormat)}
                </p>
                {item.reviewStatus && item.reviewStatus !== "NONE" ? (
                  <p className="mt-1 text-xs font-semibold text-slate-600">
                    {reviewStatusLabelMap[item.reviewStatus]}
                  </p>
                ) : null}
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <CaseStatusBadge status={item.status} />
                <ChevronRight className="size-4 text-muted-foreground" aria-hidden="true" />
              </div>
            </Link>
          </li>
        ))}
      </ul>

      {/* 데스크톱: 테이블 */}
      <div className="hidden overflow-hidden md:block">
        <table className="w-full table-fixed text-left text-sm">
          <thead>
            <tr className="border-b border-border text-xs text-muted-foreground">
              <th className="w-[27%] whitespace-nowrap break-keep px-4 py-3 font-medium">사건</th>
              <th className="w-[22%] whitespace-nowrap break-keep px-3 py-3 font-medium">대표 증거</th>
              <th className="w-[7%] whitespace-nowrap break-keep px-2 py-3 text-center font-medium">증거 수</th>
              <th className="w-[11%] whitespace-nowrap break-keep px-3 py-3 font-medium">분석 상태</th>
              <th className="w-[10%] whitespace-nowrap break-keep px-3 py-3 font-medium">배정</th>
              <th className="w-[19%] whitespace-nowrap break-keep px-3 py-3 font-medium">최근 분석일</th>
              <th className="w-[4%] whitespace-nowrap px-2 py-3 font-medium">
                <span className="sr-only">상세 보기</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {cases.map((item) => (
              <tr key={item.caseId} className="transition-colors hover:bg-accent/40">
                <td className="px-4 py-3.5">
                  <Link
                    href={buildCaseDetailPath(item.caseId)}
                    className="block truncate font-medium text-foreground hover:underline"
                  >
                    {item.caseName}
                  </Link>
                </td>
                <td className="px-3 py-3.5">
                  <span className="block truncate text-muted-foreground">
                    {formatRepresentativeEvidence(item)}
                  </span>
                </td>
                <td className="whitespace-nowrap px-2 py-3.5 text-center text-muted-foreground">
                  {item.evidenceCount}건
                </td>
                <td className="px-3 py-3.5">
                  <CaseStatusBadge status={item.status} />
                </td>
                <td className="whitespace-nowrap px-3 py-3.5">
                  <ReviewStatusBadge status={item.reviewStatus ?? "NONE"} />
                </td>
                <td className="truncate whitespace-nowrap px-3 py-3.5 text-muted-foreground">
                  {formatCreatedAt(item.createdAt, dateFormat)}
                </td>
                <td className="px-2 py-3.5 text-right">
                  <Link
                    href={buildCaseDetailPath(item.caseId)}
                    className="inline-flex items-center gap-0.5 text-muted-foreground transition-colors hover:text-foreground"
                    aria-label={`${item.caseName} 상세 보기`}
                  >
                    <ChevronRight className="size-4" aria-hidden="true" />
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

function ReviewStatusBadge({ status }: { status: keyof typeof reviewStatusLabelMap }) {
  const tone =
    status === "NONE"
      ? "bg-slate-100 text-slate-500"
      : "bg-slate-100 text-slate-700"

  return (
    <span className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold ${tone}`}>
      {reviewStatusLabelMap[status]}
    </span>
  )
}

function formatRepresentativeEvidence(item: CaseSummary) {
  if (item.representativeEvidenceLabel && item.representativeEvidenceId) {
    return `${item.representativeEvidenceLabel} / EVD-${item.representativeEvidenceId}`
  }

  if (item.representativeEvidenceId) {
    return `EVD-${item.representativeEvidenceId}`
  }

  return "대표 증거 없음"
}
