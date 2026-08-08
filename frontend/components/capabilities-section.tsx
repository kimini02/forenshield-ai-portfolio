"use client"

import { useEffect, useState } from "react"
import { AudioLines, Video, ImageIcon, ScanFace, Waves, Layers, Loader2 } from "lucide-react"
import { fetchEvidenceStats } from "@/lib/evidence-api"

const capabilities = [
  {
    icon: ScanFace,
    title: "얼굴 딥페이크 탐지",
    desc: "GAN·디퓨전 기반 합성 얼굴, 표정 변조, 얼굴 교체 흔적을 프레임 단위로 분석합니다.",
    kind: "영상 · 이미지",
  },
  {
    icon: Waves,
    title: "음성 합성 분석",
    desc: "TTS·음성 클로닝으로 생성된 합성 음성과 편집된 오디오 구간을 식별합니다.",
    kind: "음성",
  },
  {
    icon: Layers,
    title: "메타데이터 · 위변조 검증",
    desc: "EXIF, 압축 이력, 노이즈 패턴(ELA)을 교차 분석해 편집·합성 여부를 판별합니다.",
    kind: "이미지 · 영상",
  },
]

const mediaStatConfig = [
  { key: "totalAnalysisCount" as const, icon: ImageIcon, label: "총 분석" },
  { key: "deepfakeDetectedCount" as const, icon: Video, label: "딥페이크 의심" },
  { key: "completedCount" as const, icon: AudioLines, label: "분석 완료" },
]

function formatCount(value: number) {
  return value.toLocaleString("ko-KR")
}

type CapabilitiesSectionProps = {
  refreshKey?: number
}

export function CapabilitiesSection({ refreshKey = 0 }: CapabilitiesSectionProps) {
  const [stats, setStats] = useState({
    totalAnalysisCount: 0,
    deepfakeDetectedCount: 0,
    completedCount: 0,
    inProgressCount: 0,
  })
  const [isLoading, setIsLoading] = useState(true)
  const [hasError, setHasError] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function loadStats() {
      setIsLoading(true)
      setHasError(false)
      try {
        const data = await fetchEvidenceStats()
        if (!cancelled) {
          setStats(data)
        }
      } catch {
        if (!cancelled) {
          setHasError(true)
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    loadStats()
    return () => {
      cancelled = true
    }
  }, [refreshKey])

  return (
    <section className="space-y-6" aria-label="분석 역량">
      <div className="grid gap-4 sm:grid-cols-3">
        {mediaStatConfig.map((s) => (
          <div
            key={s.label}
            className="flex items-center gap-4 rounded-xl border border-border bg-card p-4"
          >
            <div className="flex size-11 items-center justify-center rounded-lg bg-secondary text-primary">
              <s.icon className="size-5" aria-hidden="true" />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">{s.label}</p>
              <p className="font-mono text-xl font-semibold text-foreground">
                {isLoading ? (
                  <Loader2 className="size-5 animate-spin text-muted-foreground" />
                ) : hasError ? (
                  <span className="text-sm text-destructive">조회 실패</span>
                ) : (
                  <>
                    {formatCount(stats[s.key])}
                    <span className="ml-1 text-sm font-normal text-muted-foreground">
                      건
                    </span>
                  </>
                )}
              </p>
            </div>
          </div>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {capabilities.map((c) => (
          <article
            key={c.title}
            className="group rounded-xl border border-border bg-card p-5 transition-colors hover:border-primary/40"
          >
            <div className="mb-4 flex size-10 items-center justify-center rounded-lg bg-primary/15 text-primary ring-1 ring-primary/25">
              <c.icon className="size-5" aria-hidden="true" />
            </div>
            <span className="font-mono text-[11px] uppercase tracking-wide text-primary">
              {c.kind}
            </span>
            <h3 className="mt-1 text-base font-semibold text-card-foreground">
              {c.title}
            </h3>
            <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
              {c.desc}
            </p>
          </article>
        ))}
      </div>
    </section>
  )
}
