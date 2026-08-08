"use client"

import { useEffect, useMemo, useState } from "react"
import { AlertTriangle, Download, Eye, EyeOff, Link2, Loader2, Search, ShieldCheck } from "lucide-react"

import { AdminPageHeader } from "@/app/admin/_components/admin-page-header"
import { useAdminToast } from "@/app/admin/_components/admin-toast-provider"
import { Button } from "@/components/ui/button"
import { fetchAdminCocChains, type CocChainDetail, type CocChainsResponse } from "@/lib/api/admin-coc"
import { getApiErrorMessage } from "@/lib/api/errors"
import { cn } from "@/lib/utils"

export default function AdminCocPage() {
  const [response, setResponse] = useState<CocChainsResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState("")
  const [selectedEvidenceId, setSelectedEvidenceId] = useState<number | null>(null)
  const { toast } = useAdminToast()

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const data = await fetchAdminCocChains()
        if (cancelled) return
        setResponse(data)
        setSelectedEvidenceId((current) => current ?? data.chains[0]?.evidenceId ?? null)
      } catch (error) {
        if (!cancelled) {
          toast({ title: "조회 실패", description: getApiErrorMessage(error, "CoC 체인을 불러오지 못했습니다.") })
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [toast])

  const filteredChains = useMemo(() => {
    const chains = response?.chains ?? []
    const keyword = search.trim().toLowerCase()
    if (!keyword) return chains
    return chains.filter(
      (chain) =>
        `evd-${chain.evidenceId}`.includes(keyword) ||
        String(chain.evidenceId).includes(keyword) ||
        chain.caseName.toLowerCase().includes(keyword) ||
        chain.caseId.toLowerCase().includes(keyword)
    )
  }, [response, search])

  const selectedChain =
    filteredChains.find((chain) => chain.evidenceId === selectedEvidenceId) ?? filteredChains[0] ?? null

  function handleExportCsv() {
    if (!selectedChain) return
    const header = "logId,eventType,label,actor,createdAt,currentLogHash,chainValid"
    const rows = selectedChain.events.map((event) =>
      [
        event.logId,
        event.eventType,
        event.label,
        event.actor,
        event.createdAt,
        event.currentLogHash,
        event.chainValid ? "VALID" : "BROKEN",
      ].join(",")
    )
    const blob = new Blob([`﻿${[header, ...rows].join("\n")}`], { type: "text/csv;charset=utf-8" })
    const url = URL.createObjectURL(blob)
    const link = document.createElement("a")
    link.href = url
    link.download = `coc-chain-EVD-${selectedChain.evidenceId}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    toast({ title: "CSV 내보내기 완료", description: `EVD-${selectedChain.evidenceId} 보관 체인이 다운로드되었습니다.` })
  }

  return (
    <>
      <AdminPageHeader
        title="CoC 감사"
        description="증거별 보관 연속성(Chain of Custody) 검증"
        action={
          <Button variant="outline" disabled={!selectedChain || loading} onClick={handleExportCsv}>
            <Download className="size-4" />
            체인 CSV
          </Button>
        }
      />

      <div className="px-8 py-6">
        {response?.sample ? (
          <p className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-2.5 text-xs font-medium text-amber-800">
            백엔드 CoC 감사 API(GET /api/v1/admin/coc/chains)가 아직 연동되지 않아 샘플 데이터를 표시하고 있습니다.
          </p>
        ) : null}

        <div className="mb-5 grid gap-3 sm:grid-cols-3">
          <StatCard label="추적 중인 증거" value={loading ? "-" : `${response?.totalCount ?? 0}건`} />
          <StatCard label="체인 정상" value={loading ? "-" : `${response?.validCount ?? 0}건`} />
          <StatCard
            label="체인 경고"
            value={loading ? "-" : `${response?.brokenCount ?? 0}건`}
            danger={(response?.brokenCount ?? 0) > 0}
          />
        </div>

        <div className="grid items-start gap-4 lg:grid-cols-[380px_minmax(0,1fr)]">
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-100 p-3">
              <div className="relative">
                <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" />
                <input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="증거 ID, 사건명 검색..."
                  className="h-9 w-full rounded-lg border border-slate-200 bg-white pr-3 pl-9 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20"
                />
              </div>
            </div>

            {loading ? (
              <div className="py-16 text-center">
                <Loader2 className="mx-auto size-5 animate-spin text-slate-400" />
              </div>
            ) : filteredChains.length === 0 ? (
              <p className="py-16 text-center text-sm text-slate-400">조건에 맞는 증거가 없습니다.</p>
            ) : (
              <ul className="max-h-[560px] overflow-y-auto">
                {filteredChains.map((chain) => {
                  const selected = chain.evidenceId === selectedChain?.evidenceId
                  const broken = chain.status === "BROKEN"
                  return (
                    <li key={chain.evidenceId} className="border-b border-slate-50 last:border-0">
                      <button
                        type="button"
                        onClick={() => setSelectedEvidenceId(chain.evidenceId)}
                        className={cn(
                          "w-full px-4 py-3 text-left transition-colors hover:bg-slate-50",
                          selected && "bg-teal-50/60 hover:bg-teal-50/60"
                        )}
                      >
                        <div className="flex items-center justify-between gap-3">
                          <span className="font-mono text-sm font-semibold text-slate-900">
                            EVD-{chain.evidenceId}
                          </span>
                          <span
                            className={cn(
                              "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium",
                              broken
                                ? "border-red-200 bg-red-50 text-red-700"
                                : "border-slate-200 bg-slate-50 text-slate-600"
                            )}
                          >
                            {broken ? <AlertTriangle className="size-3" aria-hidden="true" /> : null}
                            {broken ? "체인 단절" : "정상"}
                          </span>
                        </div>
                        <p className="mt-1 truncate text-xs text-slate-500">{chain.caseName}</p>
                        <p className="mt-0.5 text-xs text-slate-400">
                          이벤트 {chain.eventCount}건 · 마지막: {chain.lastEventLabel} ·{" "}
                          {formatShortDateTime(chain.lastEventAt)}
                        </p>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            {loading ? (
              <div className="py-16 text-center">
                <Loader2 className="mx-auto size-5 animate-spin text-slate-400" />
              </div>
            ) : selectedChain ? (
              <ChainTimeline chain={selectedChain} />
            ) : (
              <p className="py-16 text-center text-sm text-slate-400">왼쪽에서 증거를 선택하세요.</p>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

function StatCard({ label, value, danger = false }: { label: string; value: string; danger?: boolean }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
      <p className="text-xs text-slate-500">{label}</p>
      <p className={cn("mt-1 text-2xl font-semibold", danger ? "text-red-700" : "text-slate-900")}>{value}</p>
    </div>
  )
}

function ChainTimeline({ chain }: { chain: CocChainDetail }) {
  const broken = chain.status === "BROKEN"
  const [showStreamEvents, setShowStreamEvents] = useState(false)
  const streamEventCount = chain.events.filter((event) => event.eventType === "EVIDENCE_STREAM_ACCESS").length
  const visibleEvents = showStreamEvents
    ? chain.events
    : chain.events.filter((event) => event.eventType !== "EVIDENCE_STREAM_ACCESS")

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900">
            <span className="font-mono">EVD-{chain.evidenceId}</span> 보관 체인
            {broken ? (
              <span className="inline-flex items-center gap-1 rounded-full border border-red-200 bg-red-50 px-2.5 py-0.5 text-xs font-medium text-red-700">
                <AlertTriangle className="size-3" aria-hidden="true" />
                체인 단절
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 rounded-full border border-teal-200 bg-teal-50 px-2.5 py-0.5 text-xs font-medium text-teal-700">
                <ShieldCheck className="size-3" aria-hidden="true" />
                검증됨
              </span>
            )}
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            {chain.caseName} · <span className="font-mono">{chain.caseId}</span>
          </p>
        </div>
        {streamEventCount > 0 ? (
          <Button
            size="sm"
            variant="outline"
            onClick={() => setShowStreamEvents((current) => !current)}
          >
            {showStreamEvents ? <EyeOff className="size-3.5" /> : <Eye className="size-3.5" />}
            {showStreamEvents ? "스트리밍 로그 접기" : `스트리밍 ${streamEventCount}건 표시`}
          </Button>
        ) : null}
      </div>

      {streamEventCount > 0 && !showStreamEvents ? (
        <p className="mt-3 border-y border-slate-100 py-2 text-xs text-slate-500">
          HLS 세그먼트·키 요청 {streamEventCount}건은 접어두었습니다. 체인 검증과 CSV에는 포함됩니다.
        </p>
      ) : null}

      <ol className="mt-5">
        {visibleEvents.map((event, index) => {
          const isFirst = index === 0
          const isLast = index === visibleEvents.length - 1
          const linkBroken = !event.chainValid

          return (
            <li key={event.logId} className="relative flex gap-4 pb-6 last:pb-0">
              <div className="flex w-4 flex-col items-center">
                {!isFirst ? (
                  <span
                    aria-hidden="true"
                    className={cn(
                      "absolute -top-6 h-6 w-0",
                      linkBroken ? "border-l-2 border-dashed border-red-500" : "border-l-2 border-teal-600"
                    )}
                  />
                ) : null}
                <span
                  aria-hidden="true"
                  className={cn(
                    "z-10 mt-1 size-3 shrink-0 rounded-full ring-4 ring-white",
                    linkBroken ? "bg-red-600" : "bg-teal-600"
                  )}
                />
                {!isLast ? (
                  <span
                    aria-hidden="true"
                    className={cn(
                      "w-0 flex-1",
                      visibleEvents[index + 1] && !visibleEvents[index + 1].chainValid
                        ? "border-l-2 border-dashed border-red-500"
                        : "border-l-2 border-teal-600"
                    )}
                  />
                ) : null}
              </div>

              <div className="min-w-0 flex-1 pb-1">
                <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
                  <p className={cn("text-sm font-semibold", linkBroken ? "text-red-700" : "text-slate-900")}>
                    {event.label}
                  </p>
                  <p className="text-xs text-slate-500">
                    {event.actor} · {formatShortDateTime(event.createdAt)}
                  </p>
                </div>
                <p className="mt-1 font-mono text-[11px] text-slate-400">
                  hash {shortenHash(event.currentLogHash)}
                  {" → "}
                  {linkBroken ? (
                    <span className="font-sans font-semibold text-red-700">이전 해시 불일치 — 체인 단절 지점</span>
                  ) : (
                    <span className="font-sans text-slate-500">{isFirst ? "체인 시작" : "검증됨"}</span>
                  )}
                </p>
                {event.detail ? <p className="mt-0.5 text-xs text-slate-500">{event.detail}</p> : null}
              </div>
            </li>
          )
        })}
      </ol>

      {broken ? (
        <p className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-xs font-medium leading-5 text-red-700">
          체인 단절이 확인된 증거는 로그 위·변조 또는 기록 누락 가능성이 있습니다. 해당 구간의 시스템 로그와
          담당자 활동 내역을 함께 확인하세요.
        </p>
      ) : null}

      <div className="mt-4 flex items-center gap-1.5 border-t border-slate-100 pt-3 text-xs text-slate-400">
        <Link2 className="size-3.5" aria-hidden="true" />
        해시 체인은 각 CoC 로그의 currentLogHash를 다음 로그가 참조하는 방식으로 연결됩니다.
      </div>
    </div>
  )
}

function shortenHash(value: string) {
  if (!value) return "-"
  if (value.length <= 18) return value
  return `${value.slice(0, 8)}…${value.slice(-6)}`
}

function formatShortDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  const hour = String(date.getHours()).padStart(2, "0")
  const minute = String(date.getMinutes()).padStart(2, "0")
  return `${month}-${day} ${hour}:${minute}`
}
