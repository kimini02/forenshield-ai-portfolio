"use client"

import type { ReactNode } from "react"
import { Files } from "lucide-react"

// 왼쪽 손잡이 + hover/포커스 시 슬라이드 인 되는 오버레이 드로어.
// 결과 영역이 전체 폭을 쓰도록 패널은 absolute로 띄운다.
// children = 증거 파일 패널(EvidenceSelector).
export function EvidenceDrawer({ count, children }: { count: number; children: ReactNode }) {
  return (
    <div className="group fixed left-0 top-[19.75rem] z-50">
      <div className="relative">
        {/* 손잡이: 항상 보임 */}
        <button
          type="button"
          aria-label={`증거 파일 ${count}개 목록 열기`}
          className="flex h-44 w-10 items-center justify-center rounded-r-xl border border-l-0 border-border bg-card shadow-sm transition-colors hover:bg-muted/40 group-hover:bg-muted/40"
        >
          <span className="flex items-center gap-1.5 text-xs font-bold text-muted-foreground [writing-mode:vertical-rl]">
            <Files className="size-4 rotate-90" aria-hidden="true" />
            증거 {count}
          </span>
        </button>

        {/* 패널: 화면 왼쪽 끝에서 hover/focus 시 슬라이드 인 */}
        <div className="pointer-events-none absolute left-0 top-0 w-[420px] max-w-[calc(100vw-0.75rem)] -translate-x-full opacity-0 transition-all duration-200 group-hover:pointer-events-auto group-hover:translate-x-0 group-hover:opacity-100 group-focus-within:pointer-events-auto group-focus-within:translate-x-0 group-focus-within:opacity-100">
          {children}
        </div>
      </div>
    </div>
  )
}
