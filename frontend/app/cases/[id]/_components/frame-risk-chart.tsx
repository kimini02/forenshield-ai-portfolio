"use client"

import {
  CategoryScale,
  Chart as ChartJS,
  Filler,
  Legend,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
  type ChartData,
  type ChartOptions,
  type Plugin,
} from "chart.js"
import { Line } from "react-chartjs-2"

import { normalizeResultValue } from "@/lib/api/analysis-result-ui"
import type { FrameScore } from "@/lib/api/evidence-detail"

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Filler, Tooltip, Legend)

function formatSecondsForViewer(seconds: number) {
  if (!Number.isFinite(seconds)) return "-"
  const rounded = Math.round(seconds * 10) / 10
  return `${Number.isInteger(rounded) ? rounded.toFixed(0) : rounded.toFixed(1)}초`
}

type FrameRiskChartProps = {
  scores: FrameScore[]
  threshold?: number
  title?: string
  emptyMessage?: string
}

export function FrameRiskChart({
  scores,
  threshold = 0.6,
  title = "위험도 추이",
  emptyMessage = "프레임별 위험 점수가 없습니다. 분석 서버가 데이터를 제공하면 표시됩니다.",
}: FrameRiskChartProps) {
  if (scores.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm font-semibold text-slate-400 dark:border-border dark:bg-background">
        {emptyMessage}
      </p>
    )
  }

  const thresholdPercent = Math.round(threshold * 100)
  const items = scores.slice(0, 36).map((item) => ({
    value: normalizeResultValue(item.score),
    timeSec: item.timeSec ?? null,
  }))
  const peakIndex = items.reduce((peak, item, index) => (item.value > items[peak].value ? index : peak), 0)
  const labels = items.map((item, index) => formatSecondsForViewer(item.timeSec ?? index))
  const riskScores = items.map((item) => Math.round(item.value * 100))
  const thresholdScores = items.map(() => thresholdPercent)
  const peakScores = items.map((_, index) => (index === peakIndex ? riskScores[index] : null))
  const tickIndexSet = new Set([0, 0.25, 0.5, 0.75, 1].map((ratio) => Math.round((items.length - 1) * ratio)))

  const data: ChartData<"line", (number | null)[], string> = {
    labels,
    datasets: [
      {
        label: "위험 점수",
        data: riskScores,
        borderColor: "#64748b",
        backgroundColor: "rgba(100, 116, 139, 0.08)",
        borderWidth: 2.5,
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHitRadius: 10,
        pointBackgroundColor: "#64748b",
        pointBorderColor: "#ffffff",
        tension: 0.35,
        fill: true,
        segment: {
          borderColor(context) {
            const startValue = Number(context.p0?.parsed?.y)
            const endValue = Number(context.p1?.parsed?.y)
            return startValue >= thresholdPercent || endValue >= thresholdPercent ? "#b91c1c" : "#64748b"
          },
        },
      },
      {
        label: `임계값 ${thresholdPercent} / 100`,
        data: thresholdScores,
        borderColor: "rgba(185, 28, 28, 0.4)",
        borderWidth: 1.5,
        borderDash: [8, 8],
        pointRadius: 0,
        pointHitRadius: 0,
        tension: 0,
      },
      {
        label: "최고 위험",
        data: peakScores,
        borderColor: "transparent",
        backgroundColor: "#b91c1c",
        pointBackgroundColor: "#b91c1c",
        pointBorderColor: "#ffffff",
        pointBorderWidth: 4,
        pointRadius: 8,
        pointHoverRadius: 9,
        showLine: false,
      },
    ],
  }

  const peakLabelPlugin: Plugin<"line"> = {
    id: "frameRiskPeakLabel",
    afterDatasetsDraw(chart) {
      const peakDatasetIndex = chart.data.datasets.findIndex((dataset) => dataset.label === "최고 위험")
      if (peakDatasetIndex < 0) return

      const peakDataset = chart.data.datasets[peakDatasetIndex]
      const peakDataIndex = peakDataset.data.findIndex((value) => typeof value === "number")
      const peakValue = peakDataset.data[peakDataIndex]
      const peakPoint = chart.getDatasetMeta(peakDatasetIndex).data[peakDataIndex]
      const peakValueNumber = Number(peakValue)
      if (!Number.isFinite(peakValueNumber) || !peakPoint) return

      const { x, y } = peakPoint.tooltipPosition(true)
      const xPosition = Number(x)
      const yPosition = Number(y)
      if (!Number.isFinite(xPosition) || !Number.isFinite(yPosition)) return
      const { ctx, chartArea } = chart
      ctx.save()
      ctx.font = "700 14px system-ui, -apple-system, BlinkMacSystemFont, sans-serif"
      ctx.fillStyle = "#b91c1c"
      ctx.textAlign = "center"
      ctx.textBaseline = "bottom"
      ctx.fillText(
        `${Math.round(peakValueNumber)} / 100`,
        xPosition,
        Math.max(chartArea.top + 18, yPosition - 18)
      )
      ctx.restore()
    },
  }

  const options: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: 650,
      easing: "easeOutQuart",
    },
    interaction: {
      intersect: false,
      mode: "index",
    },
    layout: {
      padding: {
        top: 28,
        right: 14,
        bottom: 0,
        left: 4,
      },
    },
    scales: {
      x: {
        border: { display: false },
        grid: { display: false, drawTicks: false },
        ticks: {
          color: "#94a3b8",
          font: { size: 12, weight: 700 },
          maxRotation: 0,
          autoSkip: false,
          callback(_value, index) {
            return tickIndexSet.has(index) ? labels[index] : ""
          },
        },
      },
      y: {
        min: 0,
        max: 100,
        border: { display: false },
        grid: {
          color: "rgba(148, 163, 184, 0.22)",
          drawTicks: false,
        },
        ticks: {
          stepSize: 20,
          color: "#94a3b8",
          font: { size: 12, weight: 700 },
          padding: 12,
          callback(value) {
            const numericValue = Number(value)
            return numericValue === 0 || numericValue === thresholdPercent || numericValue === 100
              ? String(numericValue)
              : ""
          },
        },
      },
    },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: "rgba(15, 23, 42, 0.92)",
        borderColor: "rgba(255, 255, 255, 0.14)",
        borderWidth: 1,
        displayColors: false,
        padding: 10,
        callbacks: {
          title(items) {
            return items[0]?.label ?? ""
          },
          label(context) {
            const value = typeof context.raw === "number" ? context.raw : Number(context.parsed.y)
            if (context.dataset.label?.startsWith("임계값")) return `임계값: ${thresholdPercent} / 100`
            return `${context.dataset.label}: ${Math.round(value)} / 100`
          },
        },
      },
    },
  }

  return (
    <div>
      <p className="text-sm font-bold text-slate-950 dark:text-foreground">{title}</p>
      <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs font-semibold text-slate-500 dark:text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <span className="h-0.5 w-3.5 rounded-full bg-red-700" />
          위험 점수
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-3.5 border-t-2 border-dashed border-red-700/35" />
          임계값 {thresholdPercent} / 100
        </span>
        <span className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-full bg-red-700" />
          최고 위험
        </span>
      </div>
      <div className="relative mt-3 h-56 rounded-lg bg-slate-50 px-3 py-4 dark:bg-background sm:px-5">
        <Line data={data} options={options} plugins={[peakLabelPlugin]} aria-label="위험도 선 그래프" />
      </div>
    </div>
  )
}
