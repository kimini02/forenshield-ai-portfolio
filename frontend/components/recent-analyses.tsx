"use client"

import Link from "next/link"
import {
  AudioLines,
  Video,
  ImageIcon,
  ChevronRight,
  FileText,
  Search,
  FileSearch,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { AnalysisStatusBadge } from "@/components/analysis-status-badge"
import type { UploadResult } from "@/lib/evidence-api"
import { buildCaseDetailPath } from "@/lib/route-params"

type MediaKind = "audio" | "video" | "image" | "unknown"

function kindFromFileName(name: string): MediaKind {
  const lower = name.toLowerCase()
  if (/\.(mp3|wav|aac|flac|ogg|m4a)$/.test(lower)) return "audio"
  if (/\.(mp4|mov|avi|mkv|webm)$/.test(lower)) return "video"
  if (/\.(jpg|jpeg|png|gif|webp|bmp)$/.test(lower)) return "image"
  return "unknown"
}

const kindIcon = {
  audio: AudioLines,
  video: Video,
  image: ImageIcon,
  unknown: FileSearch,
}

function formatUploadedAt(iso: string) {
  return new Date(iso).toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}

type RecentAnalysesProps = {
  uploads: UploadResult[]
}

export function RecentAnalyses({ uploads }: RecentAnalysesProps) {
  return (
    <section
      id="reports"
      className="relative rounded-xl border border-border bg-card shadow-sm"
      aria-label="최근 분석 내역"
    >
      <div className="flex items-center justify-between border-b border-border px-5 py-4">
        <div className="flex items-center gap-2">
          <FileText className="size-4 text-primary" />
          <h2 className="text-base font-semibold text-card-foreground">
            최근 분석 내역
          </h2>
        </div>
        <Link
          href="/mypage"
          className="flex items-center gap-0.5 text-sm text-muted-foreground transition-colors hover:text-primary"
        >
          전체 보기
          <ChevronRight className="size-4" aria-hidden="true" />
        </Link>
      </div>

      {uploads.length === 0 ? (
        <div className="px-5 py-8 text-center text-sm text-muted-foreground">
          아직 분석을 시작한 증거가 없습니다.
        </div>
      ) : (
        <ul className="divide-y divide-border">
          {uploads.map((item) => {
            const kind = kindFromFileName(item.fileName)
            const Icon = kindIcon[kind]
            const href = item.caseName
              ? buildCaseDetailPath(item.caseName, item.evidenceId)
              : "/mypage"

            return (
              <li
                key={item.hashValue}
                className="group flex flex-col gap-3 px-5 py-4 transition-colors hover:bg-accent/40"
              >
                <div className="flex items-center gap-4">
                  <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-secondary text-muted-foreground">
                    <Icon className="size-4" aria-hidden="true" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="truncate text-sm font-medium text-foreground">
                        {item.fileName}
                      </p>
                      <AnalysisStatusBadge
                        status={item.analysisStatus ?? "PENDING"}
                        className="h-5 px-1.5"
                      />
                    </div>
                    <p className="font-mono text-[10px] text-muted-foreground">
                      EV-{item.evidenceId}
                      {item.caseName ? ` · ${item.caseName}` : ""}
                    </p>
                  </div>
                  <div className="hidden text-right sm:block">
                    <p className="font-mono text-[10px] text-muted-foreground">
                      {formatUploadedAt(item.uploadedAt)}
                    </p>
                  </div>
                </div>

                <div className="flex justify-end gap-2">
                  <Button
                    variant="outline"
                    size="xs"
                    className="h-7 gap-1.5 text-xs"
                    render={<Link href={href} />}
                    nativeButton={false}
                  >
                    <Search className="size-3" />
                    상세 보기
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
