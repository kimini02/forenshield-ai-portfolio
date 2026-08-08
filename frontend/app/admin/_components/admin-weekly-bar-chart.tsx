"use client"

import type { AdminWeeklyAnalysisPoint } from "@/lib/api/admin"

type AdminWeeklyBarChartProps = {
  points: AdminWeeklyAnalysisPoint[]
}

export function AdminWeeklyBarChart({ points }: AdminWeeklyBarChartProps) {
  if (points.length === 0) {
    return (
      <div className="flex h-[280px] items-center justify-center text-sm text-slate-400">
        주간 데이터가 없습니다.
      </div>
    )
  }

  const maxValue = Math.max(
    1,
    ...points.flatMap((point) => [point.requestedCount, point.completedCount])
  )
  const width = 560
  const height = 280
  const padding = { top: 20, right: 16, bottom: 40, left: 36 }
  const graphWidth = width - padding.left - padding.right
  const graphHeight = height - padding.top - padding.bottom
  const groupWidth = graphWidth / points.length
  const barWidth = Math.min(18, groupWidth / 3)
  const gap = 4

  const yTicks = Array.from(new Set([0, Math.ceil(maxValue / 2), maxValue])).sort(
    (a, b) => a - b
  )

  return (
    <div>
      <div className="mb-4 flex items-center gap-5 text-xs">
        <div className="flex items-center gap-2">
          <span className="size-2.5 rounded-sm bg-teal-500" />
          <span className="text-slate-600">분석 요청</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="size-2.5 rounded-sm bg-sky-400" />
          <span className="text-slate-600">분석 완료</span>
        </div>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} className="h-[280px] w-full" role="img" aria-label="주간 분석 현황 막대 그래프">
        {yTicks.map((tick) => {
          const y = padding.top + graphHeight - (tick / maxValue) * graphHeight
          return (
            <g key={tick}>
              <line
                x1={padding.left}
                y1={y}
                x2={padding.left + graphWidth}
                y2={y}
                stroke="#e2e8f0"
                strokeWidth="1"
              />
              <text x={padding.left - 8} y={y + 4} textAnchor="end" className="fill-slate-400 text-[10px]">
                {tick}
              </text>
            </g>
          )
        })}
        {points.map((point, index) => {
          const centerX = padding.left + groupWidth * index + groupWidth / 2
          const requestedHeight = (point.requestedCount / maxValue) * graphHeight
          const completedHeight = (point.completedCount / maxValue) * graphHeight
          const baseY = padding.top + graphHeight

          return (
            <g key={point.date}>
              <rect
                x={centerX - barWidth - gap / 2}
                y={baseY - requestedHeight}
                width={barWidth}
                height={requestedHeight}
                rx="3"
                fill="#14b8a6"
              />
              <rect
                x={centerX + gap / 2}
                y={baseY - completedHeight}
                width={barWidth}
                height={completedHeight}
                rx="3"
                fill="#38bdf8"
              />
              <text x={centerX} y={height - 12} textAnchor="middle" className="fill-slate-500 text-[11px]">
                {point.label}
              </text>
            </g>
          )
        })}
      </svg>
    </div>
  )
}
