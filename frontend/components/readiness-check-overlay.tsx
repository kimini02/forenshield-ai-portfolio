"use client"

import { Loader2 } from "lucide-react"

import type { ReadinessCheckPhase } from "@/lib/readiness"

type ReadinessCheckOverlayProps = {
  open: boolean
  phase?: ReadinessCheckPhase
}

const PHASE_COPY: Record<
  NonNullable<Exclude<ReadinessCheckPhase, null>>,
  { title: string; description: string }
> = {
  metadata: {
    title: "영상 정보 확인 중",
    description: "해상도·길이·코덱 등 메타데이터를 확인하고 있습니다.",
  },
  frameSampling: {
    title: "화질 분석 중",
    description:
      "Blur, Blockiness, FFT Peak 분석을 시작합니다. 잠시만 기다려 주세요.",
  },
  aiAnalysis: {
    title: "AI 분석 중",
    description: "딥페이크·위변조 분석을 시작하고 있습니다. 잠시만 기다려 주세요.",
  },
}

export function ReadinessCheckOverlay({ open, phase = "metadata" }: ReadinessCheckOverlayProps) {
  if (!open) return null

  const copy = phase ? PHASE_COPY[phase] : PHASE_COPY.metadata

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/45 px-4">
      <div
        role="status"
        aria-live="polite"
        aria-busy="true"
        className="flex max-w-sm flex-col items-center gap-4 rounded-xl border border-border bg-card px-8 py-7 text-center shadow-lg"
      >
        <Loader2 className="size-8 animate-spin text-primary" aria-hidden="true" />
        <div>
          <p className="text-base font-semibold text-foreground">{copy.title}</p>
          <p className="mt-1 text-sm text-muted-foreground">{copy.description}</p>
        </div>
      </div>
    </div>
  )
}
