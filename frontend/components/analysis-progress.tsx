"use client"

import { CheckCircle2, Loader2 } from "lucide-react"
import { cn } from "@/lib/utils"

const progressItems = [
  { label: "파일 해시 생성 (SHA-256)", threshold: 14 },
  { label: "메타데이터 추출", threshold: 28 },
  { label: "AI 위변조 분석", threshold: 48 },
  { label: "디지털 서명 적용 (PKI · RSA-4096)", threshold: 64 },
  { label: "원본 파일 WORM 저장", threshold: 78 },
  { label: "블록체인 앵커링", threshold: 90 },
  { label: "Tx Hash 저장 및 리포트 생성", threshold: 100 },
]

export function AnalysisProgress({
  fileName,
  progress,
  title = "분석 요청 처리 중...",
}: {
  fileName: string
  progress: number
  title?: string
}) {
  return (
    <div>
      <div className="flex flex-col items-center text-center">
        <div className="relative flex size-16 items-center justify-center rounded-full bg-teal-50 dark:bg-teal-500/10">
          <div className="absolute size-12 animate-spin rounded-full border-4 border-teal-100 border-t-teal-500" />
          <div className="size-7 rounded-full bg-teal-500/20" />
        </div>
        <h1 className="mt-5 text-xl font-bold text-slate-950 dark:text-foreground">{title}</h1>
        <p className="mt-2 text-sm font-bold text-slate-400 dark:text-muted-foreground">
          {fileName}
        </p>
      </div>

      <div className="mx-auto mt-8 max-w-xl">
        <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-muted">
          <div
            className="h-full rounded-full bg-teal-600 transition-all duration-200"
            style={{ width: `${progress}%` }}
          />
        </div>
        <p className="mt-5 text-center text-base font-bold text-teal-600">{progress}%</p>

        <ul className="mt-6 space-y-3">
          {progressItems.map((item) => {
            const completed = progress >= item.threshold
            const processing = !completed && progress + 22 >= item.threshold

            return (
              <li key={item.label} className="flex items-center justify-between gap-4 text-sm">
                <div className="flex min-w-0 items-center gap-3">
                  <span
                    className={cn(
                      "flex size-5 shrink-0 items-center justify-center rounded-full border",
                      completed
                        ? "border-teal-500 bg-teal-500 text-white"
                        : "border-teal-300 bg-teal-50 text-teal-600 dark:border-teal-500/30 dark:bg-teal-500/10 dark:text-teal-300"
                    )}
                  >
                    {completed ? (
                      <CheckCircle2 className="size-3.5" aria-hidden="true" />
                    ) : (
                      <Loader2
                        className={cn("size-3.5", processing && "animate-spin")}
                        aria-hidden="true"
                      />
                    )}
                  </span>
                  <span className="truncate font-bold text-slate-600 dark:text-muted-foreground">
                    {item.label}
                  </span>
                </div>
                <span className="shrink-0 text-xs font-bold text-teal-600">
                  {completed ? (
                    <CheckCircle2 className="size-4" aria-label="완료" />
                  ) : processing ? (
                    "처리 중"
                  ) : (
                    ""
                  )}
                </span>
              </li>
            )
          })}
        </ul>

        <div className="mt-7 rounded-lg border border-teal-100 bg-teal-50 px-4 py-3 text-center text-xs font-bold text-teal-700 dark:border-teal-500/25 dark:bg-teal-500/10 dark:text-teal-300">
          화면을 이동해도 분석은 계속 진행됩니다.
        </div>
      </div>
    </div>
  )
}
