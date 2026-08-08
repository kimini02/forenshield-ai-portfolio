"use client"

import { useEffect, useRef, useState } from "react"
import { AlertTriangle, ArrowRight, Check, ChevronDown, Search } from "lucide-react"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import type { SourceCase, SourceEvidence } from "./compare-verification-flow"
import type { HlsPlayback } from "@/lib/hls-playback"
import { SourceEvidenceMediaPreview } from "./source-evidence-media-preview"

type SourceEvidenceSelectorProps = {
  evidenceQuery: string
  selectedCaseId: string
  selectedEvidenceId: number | null
  selectedCase: SourceCase
  cases: SourceCase[]
  evidences: SourceEvidence[]
  isLoadingCases: boolean
  isLoadingEvidences: boolean
  sourceError: string | null
  onEvidenceQueryChange: (value: string) => void
  onSelectCase: (id: string) => void
  onSelectEvidence: (id: number) => void
  onUnavailableEvidenceSelect: (evidence: SourceEvidence) => void
  hlsPlayback?: HlsPlayback | null
  onNext: () => void
}

export function SourceEvidenceSelector({
  evidenceQuery,
  selectedCaseId,
  selectedEvidenceId,
  selectedCase,
  cases,
  evidences,
  isLoadingCases,
  isLoadingEvidences,
  sourceError,
  onEvidenceQueryChange,
  onSelectCase,
  onSelectEvidence,
  onUnavailableEvidenceSelect,
  hlsPlayback,
  onNext,
}: SourceEvidenceSelectorProps) {
  const selectedEvidence = selectedCase.evidences.find((evidence) => evidence.id === selectedEvidenceId)
  const selectedCaseOption = cases.find((sourceCase) => sourceCase.id === selectedCaseId)
  const selectedCaseLabel = isLoadingCases
    ? "사건 목록을 불러오는 중..."
    : selectedCaseOption
      ? `${selectedCaseOption.title} · ${selectedCaseOption.department}`
      : "사건을 선택하세요"
  const [caseMenuOpen, setCaseMenuOpen] = useState(false)
  const caseMenuRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!caseMenuOpen) return

    function handlePointerDown(event: MouseEvent) {
      if (!caseMenuRef.current?.contains(event.target as Node)) {
        setCaseMenuOpen(false)
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setCaseMenuOpen(false)
    }

    document.addEventListener("mousedown", handlePointerDown)
    document.addEventListener("keydown", handleKeyDown)

    return () => {
      document.removeEventListener("mousedown", handlePointerDown)
      document.removeEventListener("keydown", handleKeyDown)
    }
  }, [caseMenuOpen])

  function selectCase(caseId: string) {
    setCaseMenuOpen(false)
    if (caseId !== selectedCaseId) onSelectCase(caseId)
  }

  return (
    <section className="rounded-xl border border-slate-200 bg-white shadow-none dark:border-border dark:bg-card">
      <div className="border-b border-slate-200 px-4 py-5 dark:border-border sm:px-6">
        <h1 className="text-lg font-bold text-slate-950 dark:text-foreground">기준 증거 선택</h1>
        <p className="mt-1 text-sm font-medium text-slate-500">
          비교 기준이 될 원본 증거를 선택하세요.
        </p>
      </div>

      <div className="space-y-4 px-4 py-5 sm:px-6">
        {sourceError ? (
          <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-bold text-amber-700 dark:border-amber-500/25 dark:bg-amber-500/10 dark:text-amber-300">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {sourceError}
          </div>
        ) : null}

        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="min-w-0 flex-1 sm:max-w-sm">
            <span className="mb-1.5 block text-xs font-bold text-slate-400">사건</span>
            <div ref={caseMenuRef} className="relative">
              <button
                type="button"
                disabled={isLoadingCases}
                aria-haspopup="listbox"
                aria-expanded={caseMenuOpen}
                onClick={() => setCaseMenuOpen((open) => !open)}
                className="flex h-10 w-full items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-3 text-left text-sm font-semibold text-slate-950 outline-none transition-colors hover:border-slate-300 focus:border-slate-400 disabled:opacity-60 dark:border-border dark:bg-card dark:text-foreground"
              >
                <span className="min-w-0 truncate">{selectedCaseLabel}</span>
                <ChevronDown
                  className={cn(
                    "size-4 shrink-0 text-slate-500 transition-transform",
                    caseMenuOpen && "rotate-180"
                  )}
                  aria-hidden="true"
                />
              </button>

              {caseMenuOpen && !isLoadingCases ? (
                <div
                  role="listbox"
                  className="absolute left-0 right-0 z-30 mt-1 max-h-56 overflow-y-auto rounded-lg border border-slate-200 bg-white p-1 shadow-lg dark:border-border dark:bg-card"
                >
                  {cases.map((sourceCase) => {
                    const isSelectedCase = sourceCase.id === selectedCaseId
                    const label = `${sourceCase.title} · ${sourceCase.department}`

                    return (
                      <button
                        key={sourceCase.id}
                        type="button"
                        role="option"
                        aria-selected={isSelectedCase}
                        onClick={() => selectCase(sourceCase.id)}
                        className={cn(
                          "flex h-9 w-full items-center gap-2 rounded-md px-2.5 text-left text-sm font-semibold transition-colors",
                          isSelectedCase
                            ? "bg-slate-950 text-white dark:bg-foreground dark:text-background"
                            : "text-slate-700 hover:bg-slate-50 dark:text-foreground dark:hover:bg-secondary"
                        )}
                      >
                        <Check
                          className={cn("size-4 shrink-0", isSelectedCase ? "opacity-100" : "opacity-0")}
                          aria-hidden="true"
                        />
                        <span className="min-w-0 truncate">{label}</span>
                      </button>
                    )
                  })}
                </div>
              ) : null}
            </div>
          </div>
          <label className="min-w-0 flex-1">
            <span className="mb-1.5 block text-xs font-bold text-slate-400">증거 검색</span>
            <span className="relative block">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <input
                value={evidenceQuery}
                onChange={(event) => onEvidenceQueryChange(event.target.value)}
                placeholder="EVD 번호 또는 증거명 검색"
                className="h-10 w-full rounded-lg border border-slate-200 bg-white pl-10 pr-3 text-sm font-semibold text-slate-950 outline-none transition-colors placeholder:font-medium placeholder:text-slate-400 focus:border-slate-400 dark:border-border dark:bg-card dark:text-foreground"
              />
            </span>
          </label>
        </div>

        <div className="grid gap-4 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)] lg:items-start">
          <div className="max-h-[320px] space-y-1.5 overflow-y-auto pr-1 sm:max-h-[420px]">
            {isLoadingEvidences || isLoadingCases ? (
              <EmptyPaneMessage label="증거 목록을 불러오는 중입니다." />
            ) : evidences.length > 0 ? (
              evidences.map((evidence, index) => {
                const isSelected = evidence.id === selectedEvidenceId
                const isCompareReady = evidence.isCompareReady

                return (
                  <button
                    key={evidence.id}
                    type="button"
                    onClick={() => {
                      if (!isCompareReady) {
                        onUnavailableEvidenceSelect(evidence)
                        return
                      }

                      onSelectEvidence(evidence.id)
                    }}
                    className={cn(
                      "flex w-full items-center gap-3 rounded-lg border bg-white px-3.5 py-3 text-left transition-colors dark:bg-card",
                      isSelected
                        ? "border-slate-950 dark:border-foreground"
                        : "border-slate-200 hover:border-slate-300 hover:bg-slate-50/70 dark:border-border dark:hover:bg-secondary/40",
                      !isCompareReady && "text-slate-500"
                    )}
                  >
                    <span
                      className={cn(
                        "flex size-4 shrink-0 items-center justify-center rounded-full border-2",
                        isSelected
                          ? "border-slate-950 bg-slate-950 dark:border-foreground dark:bg-foreground"
                          : "border-slate-300 dark:border-border"
                      )}
                    >
                      {isSelected ? <span className="size-1.5 rounded-full bg-white dark:bg-background" /> : null}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-bold text-slate-950 dark:text-foreground">
                        {evidence.displayLabel || `증거 ${index + 1}`}
                      </span>
                      <span className="mt-0.5 block font-mono text-xs font-medium text-slate-400">
                        {formatEvidenceId(evidence.id)}
                      </span>
                    </span>
                    <span
                      className={cn(
                        "shrink-0 rounded-full px-2 py-1 text-[11px] font-bold",
                        isCompareReady
                          ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300"
                          : "bg-slate-100 text-slate-500 dark:bg-secondary dark:text-muted-foreground"
                      )}
                    >
                      {isCompareReady ? "분석 완료" : "분석 전"}
                    </span>
                  </button>
                )
              })
            ) : (
              <EmptyPaneMessage label="선택한 사건에서 일치하는 증거가 없습니다." />
            )}
          </div>

          <aside className="rounded-xl border border-slate-200 bg-slate-50/60 p-3 dark:border-border dark:bg-background sm:p-4">
            <p className="text-xs font-bold text-slate-400">선택된 기준 증거</p>
            {selectedEvidence ? (
              <>
                <SourceEvidenceMediaPreview
                  evidence={selectedEvidence}
                  hlsPlayback={hlsPlayback}
                  className="mt-3 aspect-video w-full"
                />
                <p className="mt-3 truncate text-sm font-bold text-slate-950 dark:text-foreground">
                  비교검증 기준 증거
                </p>
                <p className="mt-0.5 font-mono text-xs font-medium text-slate-400">
                  {formatEvidenceId(selectedEvidence.id)}
                </p>
                <dl className="mt-3 space-y-2 border-t border-slate-200 pt-3 text-xs dark:border-border">
                  <div className="flex items-center justify-between gap-3">
                    <dt className="font-semibold text-slate-400">파일 유형</dt>
                    <dd className="font-bold text-slate-700 dark:text-foreground">{selectedEvidence.codecLabel}</dd>
                  </div>
                </dl>
              </>
            ) : (
              <div className="mt-3 flex min-h-40 items-center justify-center rounded-lg border border-dashed border-slate-200 text-sm font-semibold text-slate-400 dark:border-border">
                왼쪽 목록에서 증거를 선택하세요.
              </div>
            )}
          </aside>
        </div>
      </div>

      <div className="flex items-center justify-end border-t border-slate-200 px-4 py-4 dark:border-border sm:px-6">
        <Button
          type="button"
          onClick={onNext}
          disabled={selectedEvidenceId === null}
          className="h-10 w-full rounded-full bg-slate-950 px-6 text-sm font-semibold text-white hover:bg-slate-800 dark:bg-foreground dark:text-background sm:w-auto"
        >
          다음 · 파일 업로드
          <ArrowRight className="size-4" aria-hidden="true" />
        </Button>
      </div>
    </section>
  )
}

function EmptyPaneMessage({ label }: { label: string }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50/70 px-4 py-10 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
      {label}
    </div>
  )
}

function formatEvidenceId(evidenceId: number) {
  return `EVD-${evidenceId}`
}
