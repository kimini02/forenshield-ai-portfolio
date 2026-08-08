"use client"

import {
  Chart as ChartJS,
  Filler,
  Legend,
  LineElement,
  PointElement,
  RadialLinearScale,
  Tooltip,
  type ChartData,
  type ChartOptions,
} from "chart.js"
import { Radar } from "react-chartjs-2"

ChartJS.register(RadialLinearScale, PointElement, LineElement, Filler, Tooltip, Legend)

type ModelRadarPoint = {
  label: string
  source: string
  score: number
}

type ModelRadarChartProps = {
  models: ModelRadarPoint[]
  threshold?: number
}

function toPercent(score: number) {
  const normalized = score > 0 && score <= 1 ? score * 100 : score
  return Math.max(0, Math.min(100, Math.round(normalized)))
}

export function ModelRadarChart({ models, threshold = 60 }: ModelRadarChartProps) {
  const labels = models.map((model) => model.label)
  const riskScores = models.map((model) => toPercent(model.score))
  const thresholdScores = models.map(() => threshold)

  const data: ChartData<"radar", number[], string> = {
    labels,
    datasets: [
      {
        label: "위험 프로파일",
        data: riskScores,
        borderColor: "rgb(185, 28, 28)",
        backgroundColor: "rgba(185, 28, 28, 0.16)",
        pointBackgroundColor: "rgb(185, 28, 28)",
        pointBorderColor: "#ffffff",
        pointHoverBackgroundColor: "#ffffff",
        pointHoverBorderColor: "rgb(185, 28, 28)",
        borderWidth: 2,
        pointRadius: 4,
        pointHoverRadius: 5,
        fill: true,
      },
      {
        label: `임계값 ${threshold}`,
        data: thresholdScores,
        borderColor: "rgba(185, 28, 28, 0.58)",
        backgroundColor: "rgba(185, 28, 28, 0.04)",
        pointBackgroundColor: "rgba(185, 28, 28, 0.58)",
        pointBorderColor: "#ffffff",
        borderDash: [6, 4],
        borderWidth: 1.5,
        pointRadius: 3,
        pointHoverRadius: 4,
        fill: false,
      },
    ],
  }

  const options: ChartOptions<"radar"> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: 650,
      easing: "easeOutQuart",
    },
    scales: {
      r: {
        min: 0,
        max: 100,
        beginAtZero: true,
        grid: {
          color: "rgba(148, 163, 184, 0.26)",
        },
        angleLines: {
          color: "rgba(148, 163, 184, 0.24)",
        },
        pointLabels: {
          color: "#334155",
          font: {
            size: 12,
            weight: 700,
          },
        },
        ticks: {
          stepSize: 20,
          color: "#94a3b8",
          backdropColor: "transparent",
          font: {
            size: 10,
            weight: 600,
          },
        },
      },
    },
    plugins: {
      legend: {
        position: "bottom",
        labels: {
          usePointStyle: true,
          boxWidth: 8,
          boxHeight: 8,
          color: "#475569",
          padding: 18,
          font: {
            size: 12,
            weight: 700,
          },
        },
      },
      tooltip: {
        callbacks: {
          title(items) {
            const label = items[0]?.label
            return label
          },
          label(context) {
            const value = typeof context.parsed.r === "number" ? context.parsed.r : Number(context.raw)
            return `${context.dataset.label}: ${Math.round(value)} / 100`
          },
        },
      },
    },
  }

  return (
    <section className="mt-4 rounded-xl border border-slate-100 bg-white p-5 dark:border-border dark:bg-card">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs font-semibold text-slate-400">모델별 위험 프로파일</p>
        <span className="rounded-full bg-red-50 px-2.5 py-1 text-[11px] font-bold text-red-700 dark:bg-red-950/30 dark:text-red-300">
          임계값 {threshold}
        </span>
      </div>
      <div className="mt-4 h-64 w-full sm:h-72">
        <Radar data={data} options={options} aria-label="모델 위험 프로파일 레이더 차트" />
      </div>
      <div className="mt-3 flex flex-wrap justify-center gap-x-3 gap-y-1 text-[11px] font-semibold text-slate-500">
        {models.map((model, index) => (
          <span key={`${model.source}-${model.label}-${index}`}>
            {model.label} · {model.source}
          </span>
        ))}
      </div>
    </section>
  )
}
