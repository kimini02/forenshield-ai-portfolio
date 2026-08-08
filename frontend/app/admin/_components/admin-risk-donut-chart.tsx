"use client"

import type { AdminRiskDistribution } from "@/lib/api/admin"

type AdminRiskDonutChartProps = {
  distribution: AdminRiskDistribution
}

const segments = [
  { key: "dangerCount" as const, label: "위험 (80~100)", color: "#ef4444" },
  { key: "cautionCount" as const, label: "주의 (50~79)", color: "#f97316" },
  { key: "safeCount" as const, label: "적합 (0~49)", color: "#14b8a6" },
]

export function AdminRiskDonutChart({ distribution }: AdminRiskDonutChartProps) {
  const total =
    distribution.safeCount + distribution.cautionCount + distribution.dangerCount

  if (total === 0) {
    return (
      <div className="flex h-[280px] items-center justify-center text-sm text-slate-400">
        위험도 분포 데이터가 없습니다.
      </div>
    )
  }

  const radius = 78
  const stroke = 28
  const circumference = 2 * Math.PI * radius
  let offset = 0

  const arcs = segments.map((segment) => {
    const value = distribution[segment.key]
    const ratio = value / total
    const length = circumference * ratio
    const dasharray = `${length} ${circumference - length}`
    const dashoffset = -offset
    offset += length
    return { ...segment, value, ratio, dasharray, dashoffset }
  })

  return (
    <div className="flex flex-col items-center gap-6 lg:flex-row lg:items-center lg:justify-center">
      <svg viewBox="0 0 200 200" className="h-[220px] w-[220px]" role="img" aria-label="위험도 분포 도넛 차트">
        <g transform="translate(100 100) rotate(-90)">
          <circle r={radius} fill="none" stroke="#f1f5f9" strokeWidth={stroke} />
          {arcs.map((arc) => (
            <circle
              key={arc.key}
              r={radius}
              fill="none"
              stroke={arc.color}
              strokeWidth={stroke}
              strokeDasharray={arc.dasharray}
              strokeDashoffset={arc.dashoffset}
              strokeLinecap="butt"
            />
          ))}
        </g>
        <text
          x="100"
          y="96"
          textAnchor="middle"
          className="fill-slate-900 text-[22px] font-bold"
        >
          {total}
        </text>
        <text x="100" y="116" textAnchor="middle" className="fill-slate-400 text-[11px]">
          전체 분석
        </text>
      </svg>

      <div className="space-y-3">
        {arcs.map((arc) => (
          <div key={arc.key} className="flex items-center gap-3 text-sm">
            <span className="size-3 rounded-full" style={{ backgroundColor: arc.color }} />
            <span className="text-slate-600">{arc.label}</span>
            <span className="font-semibold text-slate-900">
              {arc.value}건 ({Math.round(arc.ratio * 100)}%)
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
