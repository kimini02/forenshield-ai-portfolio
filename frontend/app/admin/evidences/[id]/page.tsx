"use client"

import { useCallback, useEffect, useState } from "react"
import Link from "next/link"
import { useParams, useRouter } from "next/navigation"
import { ArrowLeft, Copy, Loader2, Trash2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { DeleteEvidenceDialog } from "@/app/admin/_components/delete-evidence-dialog"
import { useAdminToast } from "@/app/admin/_components/admin-toast-provider"
import type { AdminEvidenceDetail } from "@/app/admin/_types/admin"
import { deleteAdminEvidence, fetchAdminEvidenceDetail } from "@/lib/api/admin"
import { getApiErrorMessage } from "@/lib/api/errors"
import { formatFileSize as formatSharedFileSize } from "@/lib/formatters"

function formatFileSize(bytes: number) {
  return formatSharedFileSize(bytes, {
    zeroLabel: "0 B",
    maxUnit: "MB",
  })
}

export default function AdminEvidenceDetailPage() {
  const params = useParams<{ id: string }>()
  const router = useRouter()
  const { toast } = useAdminToast()
  const [detail, setDetail] = useState<AdminEvidenceDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [deleting, setDeleting] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const loadDetail = useCallback(async () => {
    setLoading(true)
    try {
      const response = await fetchAdminEvidenceDetail(params.id)
      setDetail(response)
    } catch (error) {
      const message = getApiErrorMessage(error, "증거 상세를 불러오지 못했습니다.")
      toast({ title: "조회 실패", description: message })
    } finally {
      setLoading(false)
    }
  }, [params.id, toast])

  useEffect(() => {
    void loadDetail()
  }, [loadDetail])

  async function handleCopyHash() {
    if (!detail) return
    await navigator.clipboard.writeText(detail.hashValue)
    toast({ title: "복사 완료", description: "해시값이 클립보드에 복사되었습니다." })
  }

  async function handleDelete(reason: string) {
    setDeleting(true)
    try {
      await deleteAdminEvidence(params.id, reason)
      toast({ title: "삭제 완료", description: "증거가 삭제되었습니다." })
      setDeleteOpen(false)
      router.push("/admin/evidences")
    } catch (error) {
      const message = getApiErrorMessage(error, "증거 삭제 중 오류가 발생했습니다.")
      toast({ title: "삭제 실패", description: message })
    } finally {
      setDeleting(false)
    }
  }

  if (loading) {
    return (
      <main className="mx-auto flex max-w-5xl items-center justify-center px-4 py-24">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </main>
    )
  }

  if (!detail) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-8">
        <p className="text-sm text-destructive">증거 정보를 불러올 수 없습니다.</p>
        <Button
          variant="outline"
          className="mt-4"
          render={<Link href="/admin/evidences" />}
          nativeButton={false}
        >
          목록으로
        </Button>
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-5xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <Button
            variant="ghost"
            size="sm"
            className="mb-2 -ml-2"
            render={<Link href="/admin/evidences" />}
            nativeButton={false}
          >
            <ArrowLeft className="size-4" />
            증거 목록
          </Button>
          <h1 className="text-2xl font-semibold tracking-tight">{detail.fileName}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {detail.caseName ?? "-"} · {detail.uploaderUsername} ({detail.department})
          </p>
        </div>
        {detail.status === "UPLOADED" && (
          <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
            <Trash2 className="size-4" />
            증거 삭제
          </Button>
        )}
      </div>

      <section className="rounded-xl border border-border bg-card p-6">
        <h2 className="text-base font-semibold">기본 정보</h2>
        <dl className="mt-4 grid gap-3 sm:grid-cols-2">
          <InfoItem label="파일 유형" value={`${detail.fileType} (${detail.mimeType})`} />
          <InfoItem label="파일 크기" value={formatFileSize(detail.fileSize)} />
          <InfoItem label="사건 번호" value={detail.caseNumber ?? "-"} />
          <InfoItem label="사건명" value={detail.caseName ?? "-"} />
          <InfoItem label="업로더" value={`${detail.uploaderName} (${detail.uploaderUsername})`} />
          <InfoItem label="업로드 일시" value={detail.uploadedAt} />
          <InfoItem label="상태" value={detail.status === "UPLOADED" ? "업로드됨" : "삭제됨"} />
          <InfoItem label="분석 상태" value={detail.analysisStatus} />
        </dl>
      </section>

      <section className="rounded-xl border border-border bg-card p-6">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-base font-semibold">무결성 (SHA-256)</h2>
          <Button size="sm" variant="outline" onClick={handleCopyHash}>
            <Copy className="size-3" />
            해시 복사
          </Button>
        </div>
        <p className="mt-3 break-all font-mono text-sm text-muted-foreground">{detail.hashValue}</p>
      </section>

      {detail.metadata && (
        <section className="rounded-xl border border-border bg-card p-6">
          <h2 className="text-base font-semibold">메타데이터</h2>
          <dl className="mt-4 grid gap-3 sm:grid-cols-2">
            {detail.metadata.width != null && (
              <InfoItem label="해상도" value={`${detail.metadata.width} x ${detail.metadata.height}`} />
            )}
            {detail.metadata.durationSec != null && (
              <InfoItem label="길이" value={`${detail.metadata.durationSec}초`} />
            )}
            {detail.metadata.codec && <InfoItem label="코덱" value={detail.metadata.codec} />}
            {detail.metadata.fps != null && <InfoItem label="FPS" value={String(detail.metadata.fps)} />}
            {detail.metadata.sampleRate != null && (
              <InfoItem label="샘플레이트" value={`${detail.metadata.sampleRate} Hz`} />
            )}
            {detail.metadata.deviceInfo && (
              <InfoItem label="기기 정보" value={detail.metadata.deviceInfo} />
            )}
            {detail.metadata.extractionStatus && (
              <InfoItem label="추출 상태" value={detail.metadata.extractionStatus} />
            )}
          </dl>
        </section>
      )}

      <section className="rounded-xl border border-border bg-card p-6">
        <h2 className="text-base font-semibold">분석 이력</h2>
        {detail.analysisHistory.length === 0 ? (
          <p className="mt-3 text-sm text-muted-foreground">분석 요청 이력이 없습니다.</p>
        ) : (
          <Table className="mt-4">
            <TableHeader>
              <TableRow>
                <TableHead>요청 ID</TableHead>
                <TableHead>상태</TableHead>
                <TableHead>요청 시각</TableHead>
                <TableHead>완료 시각</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {detail.analysisHistory.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="font-mono text-sm">{item.id}</TableCell>
                  <TableCell>{item.status}</TableCell>
                  <TableCell>{item.requestedAt}</TableCell>
                  <TableCell>{item.completedAt ?? "-"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>

      <section className="rounded-xl border border-border bg-card p-6">
        <h2 className="text-base font-semibold">CoC 타임라인</h2>
        {detail.custodyLogs.length === 0 ? (
          <p className="mt-3 text-sm text-muted-foreground">기록된 CoC 로그가 없습니다.</p>
        ) : (
          <Table className="mt-4">
            <TableHeader>
              <TableRow>
                <TableHead>시간</TableHead>
                <TableHead>행위자</TableHead>
                <TableHead>행위</TableHead>
                <TableHead>상세</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {detail.custodyLogs.map((log) => (
                <TableRow key={log.id}>
                  <TableCell className="font-mono text-xs">{log.timestamp}</TableCell>
                  <TableCell>{log.actor}</TableCell>
                  <TableCell>{log.action}</TableCell>
                  <TableCell className="text-muted-foreground">{log.detail ?? "-"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>

      <DeleteEvidenceDialog
        fileName={detail.fileName}
        open={deleteOpen}
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteOpen(false)}
      />
    </main>
  )
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-sm font-medium text-foreground">{value}</dd>
    </div>
  )
}
