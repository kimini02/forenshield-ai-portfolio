"use client"

import type { AdminUsageTrendPoint } from "@/lib/api/admin"

type AdminUsageChartProps = {
  points: AdminUsageTrendPoint[]
  loading?: boolean
}

export function AdminUsageChart({ points, loading }: AdminUsageChartProps) {
  if (loading) {
    return (
      <div className="flex h-[260px] items-center justify-center text-sm text-slate-400">
        차트를 불러오는 중...
      </div>
    )
  }

  if (points.length === 0) {
    return (
      <div className="flex h-[260px] items-center justify-center text-sm text-slate-400">
        최근 7일 데이터가 없습니다.
      </div>
    )
  }

  const maxValue = Math.max(
    1,
    ...points.map((point) => Math.max(point.analysis, point.signups))
  )
  const width = 680
  const height = 240
  const padding = { top: 20, right: 20, bottom: 40, left: 36 }
  const graphWidth = width - padding.left - padding.right
  const graphHeight = height - padding.top - padding.bottom

  function toCoords(key: "analysis" | "signups") {
    return points.map((point, index) => {
      const x =
        padding.left +
        (points.length === 1 ? graphWidth / 2 : (index / (points.length - 1)) * graphWidth)
      const y =
        padding.top + graphHeight - (point[key] / maxValue) * graphHeight
      return { x, y, label: point.label }
    })
  }

  const analysisCoords = toCoords("analysis")
  const signupCoords = toCoords("signups")
  const analysisLine = analysisCoords.map((p) => `${p.x},${p.y}`).join(" ")
  const signupLine = signupCoords.map((p) => `${p.x},${p.y}`).join(" ")

  const yTicks = Array.from(new Set([0, Math.ceil(maxValue / 2), maxValue])).sort(
    (a, b) => a - b
  )

  return (
    <div>
      <div className="mb-4 flex items-center gap-5 text-xs">
        <div className="flex items-center gap-2">
          <span className="size-2.5 rounded-full bg-teal-500" />
          <span className="text-slate-600">분석 요청</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="size-2.5 rounded-full bg-blue-500" />
          <span className="text-slate-600">신규 가입</span>
        </div>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} className="h-[260px] w-full" role="img" aria-label="최근 7일 시스템 사용 현황">
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
              <text x={padding.left - 10} y={y + 4} textAnchor="end" className="fill-slate-400 text-[10px]">
                {tick}
              </text>
            </g>
          )
        })}
        <polyline
          points={analysisLine}
          fill="none"
          stroke="#14b8a6"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <polyline
          points={signupLine}
          fill="none"
          stroke="#3b82f6"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {analysisCoords.map((point) => (
          <text
            key={point.label}
            x={point.x}
            y={height - 12}
            textAnchor="middle"
            className="fill-slate-400 text-[10px]"
          >
            {point.label}
          </text>
        ))}
      </svg>
    </div>
  )
}
