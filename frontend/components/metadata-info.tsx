"use client"

import { Info, FileCode, Hash, Calendar, HardDrive, Clock } from "lucide-react"
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { AnalysisStatusBadge } from "@/components/analysis-status-badge"
import { ReadinessBadge } from "@/components/readiness-badge"
import type { MetadataDisplayItem } from "@/lib/metadata-types"
import type { MediaMetadata } from "@/lib/evidence-api"
import { formatFileSize } from "@/lib/formatters"

function formatBytes(bytes: number) {
  return formatFileSize(bytes, {
    zeroLabel: "0 B",
    maxUnit: "GB",
    trimTrailingZero: true,
  })
}

function formatUploadedAt(iso: string) {
  return new Date(iso).toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  })
}

function formatMetadata(metadata: MediaMetadata | string | null): string {
  if (!metadata) return "메타데이터 없음"
  if (typeof metadata === "string") return metadata

  const parts: string[] = []
  if (metadata.type) parts.push(metadata.type.toUpperCase())
  if (metadata.codec) parts.push(metadata.codec)
  if (metadata.width && metadata.height) {
    parts.push(`${metadata.width}x${metadata.height}`)
  }
  if (metadata.duration != null) parts.push(`${metadata.duration.toFixed(1)}초`)
  if (metadata.fps != null) parts.push(`${metadata.fps}fps`)
  if (metadata.sampleRate) parts.push(`${metadata.sampleRate}Hz`)
  if (metadata.channels) parts.push(`${metadata.channels}ch`)

  return parts.length > 0 ? parts.join(" · ") : "추출된 메타데이터 없음"
}

type MetadataInfoProps = {
  items: MetadataDisplayItem[]
}

function PendingMetadataCard({ item }: { item: Extract<MetadataDisplayItem, { phase: "pending" }> }) {
  return (
    <div className="rounded-lg border border-border bg-muted/20 p-4">
      <div className="mb-3 flex items-start justify-between gap-2">
        <p className="truncate text-sm font-medium text-foreground">{item.fileName}</p>
        <Badge variant="outline" className="shrink-0 text-[10px]">
          업로드 대기 중
        </Badge>
      </div>
      <dl className="space-y-2">
        <div className="flex flex-col gap-1">
          <dt className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
            <HardDrive className="size-3" />
            파일 크기
          </dt>
          <dd className="text-sm text-foreground">{formatBytes(item.fileSize)}</dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
            <Clock className="size-3" />
            상태
          </dt>
          <dd className="text-sm text-muted-foreground">
            업로드 후 해시·메타데이터가 표시됩니다.
          </dd>
        </div>
      </dl>
    </div>
  )
}

function UploadedMetadataCard({
  item,
}: {
  item: Extract<MetadataDisplayItem, { phase: "uploaded" }>
}) {
  const { upload, analysisStatus } = item

  return (
    <div className="rounded-lg border border-border bg-muted/20 p-4">
        <div className="mb-3 flex flex-wrap items-start justify-end gap-2">
          {upload.readiness ? (
            <ReadinessBadge tier={upload.readiness.readinessTier} />
          ) : null}
          {analysisStatus ? (
            <AnalysisStatusBadge status={analysisStatus} />
          ) : (
            <Badge variant="outline" className="shrink-0 text-[10px]">
              업로드 완료
            </Badge>
          )}
        </div>
        <p className="mb-3 truncate text-sm font-medium text-foreground">{upload.fileName}</p>
      <dl className="space-y-3">
        {upload.caseName ? (
          <div className="flex flex-col gap-1">
            <dt className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              <FileCode className="size-3" />
              사건명
            </dt>
            <dd className="text-sm text-foreground">{upload.caseName}</dd>
          </div>
        ) : null}

        <div className="flex flex-col gap-1">
          <dt className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
            <HardDrive className="size-3" />
            파일 크기 / 증거 ID
          </dt>
          <dd className="text-sm text-foreground">
            {formatBytes(upload.fileSize)}
            <span className="mx-1 text-muted-foreground">·</span>
            ID {upload.evidenceId}
          </dd>
        </div>

        <div className="flex flex-col gap-1">
          <dt className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
            <Hash className="size-3" />
            해시값 (무결성 검증)
          </dt>
          <dd className="break-all rounded bg-muted/50 p-2 font-mono text-[10px] leading-relaxed text-foreground">
            {upload.hashAlgorithm}: {upload.hashValue}
          </dd>
        </div>

        <div className="flex flex-col gap-1">
          <dt className="text-xs font-medium text-muted-foreground">추출 메타데이터</dt>
          <dd className="text-sm text-foreground">{formatMetadata(upload.metadata)}</dd>
        </div>

        <div className="flex flex-col gap-1">
          <dt className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
            <Calendar className="size-3" />
            업로드 일시
          </dt>
          <dd className="text-sm text-foreground">{formatUploadedAt(upload.uploadedAt)}</dd>
        </div>
      </dl>
    </div>
  )
}

export function MetadataInfo({ items }: MetadataInfoProps) {
  return (
    <Card className="border-border shadow-sm">
      <CardHeader className="border-b border-border px-5 py-4">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Info className="size-4 text-primary" />
            <CardTitle className="text-base font-semibold">파일 메타데이터</CardTitle>
          </div>
          {items.length > 0 && (
            <span className="text-xs text-muted-foreground">{items.length}건</span>
          )}
        </div>
      </CardHeader>
      <CardContent className="max-h-[520px] space-y-3 overflow-y-auto p-5">
        {items.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            파일을 선택하거나 업로드하면 메타데이터가 여기에 표시됩니다.
          </p>
        ) : (
          items.map((item) =>
            item.phase === "pending" ? (
              <PendingMetadataCard key={item.id} item={item} />
            ) : (
              <UploadedMetadataCard key={item.id} item={item} />
            )
          )
        )}
      </CardContent>
    </Card>
  )
}
