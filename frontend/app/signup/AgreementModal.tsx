// 담당: 김민희
// 역할: 약관/서약 전문을 보여주는 공통 모달 (제목 + 조항 목록)
"use client"

import { X } from "lucide-react"
import { Button } from "@/components/ui/button"

export default function AgreementModal({
  open,
  title,
  clauses,
  onClose,
}: {
  open: boolean
  title: string
  clauses: string[]
  onClose: () => void
}) {
  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={onClose}
    >
      <div
        className="flex max-h-[80vh] w-full max-w-md flex-col overflow-hidden rounded-xl border border-border bg-card shadow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <h2 className="text-base font-semibold text-foreground">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            className="text-muted-foreground transition-colors hover:text-foreground"
            aria-label="닫기"
          >
            <X className="size-5" aria-hidden="true" />
          </button>
        </div>

        {/* 본문 */}
        <ol className="flex-1 space-y-3 overflow-y-auto px-5 py-4 text-sm leading-relaxed text-foreground">
          {clauses.map((clause, i) => (
            <li key={i} className="flex gap-2">
              <span className="shrink-0 font-mono text-muted-foreground">{i + 1}.</span>
              <span>{clause}</span>
            </li>
          ))}
        </ol>

        {/* 푸터 */}
        <div className="border-t border-border p-4">
          <Button type="button" size="lg" className="w-full" onClick={onClose}>
            확인
          </Button>
        </div>
      </div>
    </div>
  )
}
