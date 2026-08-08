"use client"

import { Badge } from "@/components/ui/badge"

type TechnicalMetadata = {
  width?: number
  height?: number
  fps?: number
  codec?: string
  durationSec?: number
  sampleRate?: number
  channels?: number
  deviceInfo?: string
  capturedAt?: string
}

type MetadataProps = {
  data: TechnicalMetadata
  status: string
}

type MetadataViewProps = {
  fileType: string
  metadata: TechnicalMetadata | null
  status: string
}

function MetadataItem({ label, value }: { label: string; value: string | number }) {
  return (
    <div>
      <span className="block text-xs text-muted-foreground">{label}</span>
      <span className="text-sm font-medium">{value}</span>
    </div>
  )
}

function StatusBadge({
  status,
  className,
}: {
  status: string
  className: string
}) {
  return (
    <div>
      <span className="block text-xs text-muted-foreground">추출 상태</span>
      <Badge className={className}>{status}</Badge>
    </div>
  )
}

function VideoMetadata({ data, status }: MetadataProps) {
  return (
    <div className="grid grid-cols-2 gap-x-8 gap-y-4 md:grid-cols-3">
      <MetadataItem label="해상도" value={`${data.width ?? "-"} x ${data.height ?? "-"}`} />
      <MetadataItem label="프레임레이트" value={data.fps ? `${data.fps} FPS` : "정보 없음"} />
      <MetadataItem label="코덱" value={data.codec?.toUpperCase() ?? "정보 없음"} />
      <MetadataItem label="길이" value={data.durationSec ? `${data.durationSec}초` : "정보 없음"} />
      <StatusBadge
        status={status}
        className="border-none bg-green-100 text-[10px] text-green-800"
      />
    </div>
  )
}

function AudioMetadata({ data, status }: MetadataProps) {
  return (
    <div className="grid grid-cols-2 gap-x-8 gap-y-4 md:grid-cols-3">
      <MetadataItem
        label="샘플링 레이트"
        value={data.sampleRate ? `${data.sampleRate} Hz` : "정보 없음"}
      />
      <MetadataItem label="채널" value={data.channels ? `${data.channels} Ch` : "정보 없음"} />
      <MetadataItem label="코덱" value={data.codec?.toUpperCase() ?? "정보 없음"} />
      <MetadataItem
        label="길이"
        value={data.durationSec ? `${data.durationSec.toFixed(2)}초` : "정보 없음"}
      />
      <StatusBadge
        status={status}
        className="border-none bg-blue-100 text-[10px] text-blue-800"
      />
    </div>
  )
}

function ImageMetadata({ data, status }: MetadataProps) {
  return (
    <div className="grid grid-cols-2 gap-x-8 gap-y-4 md:grid-cols-3">
      <MetadataItem label="해상도" value={`${data.width ?? "-"} x ${data.height ?? "-"}`} />
      <MetadataItem label="기기 정보" value={data.deviceInfo ?? "정보 없음"} />
      <MetadataItem label="촬영 일시" value={data.capturedAt ?? "정보 없음"} />
      <StatusBadge
        status={status}
        className="border-none bg-purple-100 text-[10px] text-purple-800"
      />
    </div>
  )
}

export default function MetadataView({ fileType, metadata, status }: MetadataViewProps) {
  if (!metadata) {
    return <div className="text-sm text-muted-foreground">메타데이터 정보가 없습니다.</div>
  }

  switch (fileType?.toUpperCase()) {
    case "VIDEO":
      return <VideoMetadata data={metadata} status={status} />
    case "AUDIO":
      return <AudioMetadata data={metadata} status={status} />
    case "IMAGE":
      return <ImageMetadata data={metadata} status={status} />
    default:
      return (
        <div className="text-sm text-muted-foreground">
          지원하지 않는 미디어 타입입니다. ({fileType})
        </div>
      )
  }
}
