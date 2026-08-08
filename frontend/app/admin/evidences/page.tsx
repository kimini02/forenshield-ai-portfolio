"use client"

import { useCallback, useEffect, useState } from "react"
import Link from "next/link"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Loader2, Search, Copy } from "lucide-react"
import { fetchAdminEvidences } from "@/lib/api/admin"
import { getApiErrorMessage } from "@/lib/api/errors"
import { formatFileSize as formatSharedFileSize } from "@/lib/formatters"
import { useAdminToast } from "@/app/admin/_components/admin-toast-provider"
import type { AdminEvidence, EvidenceFileType, EvidenceStatus } from "@/app/admin/_types/admin"

const PAGE_SIZE = 10

function formatFileSize(bytes: number) {
  return formatSharedFileSize(bytes, {
    zeroLabel: "0 B",
    maxUnit: "MB",
  })
}

function getFileTypeLabel(type: EvidenceFileType) {
  switch (type) {
    case "IMAGE":
      return "이미지"
    case "VIDEO":
      return "영상"
    case "AUDIO":
      return "음성"
  }
}

function getAnalysisBadge(status: AdminEvidence["analysisStatus"]) {
  switch (status) {
    case "COMPLETED":
      return (
        <Badge variant="outline" className="border-emerald-200 bg-emerald-100 text-emerald-700">
          완료
        </Badge>
      )
    case "ANALYZING":
    case "PROCESSING":
      return (
        <Badge variant="outline" className="border-primary/40 bg-primary/10 text-primary">
          분석 중
        </Badge>
      )
    case "FAILED":
      return (
        <Badge variant="outline" className="border-rose-200 bg-rose-100 text-rose-700">
          실패
        </Badge>
      )
    case "QUEUED":
    case "PENDING":
      return (
        <Badge variant="outline" className="border-amber-200 bg-amber-100 text-amber-700">
          대기
        </Badge>
      )
    default:
      return (
        <Badge variant="outline" className="text-muted-foreground">
          없음
        </Badge>
      )
  }
}

function getStatusBadge(status: EvidenceStatus) {
  if (status === "DELETED") {
    return (
      <Badge variant="outline" className="border-rose-200 bg-rose-100 text-rose-700">
        삭제됨
      </Badge>
    )
  }
  return (
    <Badge variant="outline" className="border-emerald-200 bg-emerald-100 text-emerald-700">
      업로드됨
    </Badge>
  )
}

export default function AdminEvidencesPage() {
  const [items, setItems] = useState<AdminEvidence[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState("")
  const [fileTypeFilter, setFileTypeFilter] = useState<"ALL" | EvidenceFileType>("ALL")
  const [statusFilter, setStatusFilter] = useState<"ALL" | EvidenceStatus>("ALL")
  const [page, setPage] = useState(0)
  const { toast } = useAdminToast()

  const loadEvidences = useCallback(async () => {
    setLoading(true)
    try {
      const response = await fetchAdminEvidences({
        search,
        fileType: fileTypeFilter,
        status: statusFilter,
        page,
        size: PAGE_SIZE,
      })
      setItems(response.items)
      setTotal(response.total)
    } catch (error) {
      const message = getApiErrorMessage(error, "증거 목록을 불러오지 못했습니다.")
      toast({ title: "조회 실패", description: message })
    } finally {
      setLoading(false)
    }
  }, [fileTypeFilter, page, search, statusFilter, toast])

  useEffect(() => {
    void loadEvidences()
  }, [loadEvidences])

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  async function handleCopyHash(hash: string) {
    await navigator.clipboard.writeText(hash)
    toast({ title: "복사 완료", description: "해시값이 클립보드에 복사되었습니다." })
  }

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">증거 관리</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          업로드된 증거 파일을 검색·조회하고 삭제할 수 있습니다. (승인/반려 워크플로 없음)
        </p>
      </div>

      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
          <input
            value={search}
            onChange={(e) => {
              setSearch(e.target.value)
              setPage(0)
            }}
            placeholder="파일명, 사건명, 해시 검색"
            className="h-9 w-full rounded-lg border border-border bg-background pr-3 pl-9 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          />
        </div>
        <select
          value={fileTypeFilter}
          onChange={(e) => {
            setFileTypeFilter(e.target.value as "ALL" | EvidenceFileType)
            setPage(0)
          }}
          className="h-9 rounded-lg border border-border bg-background px-3 text-sm outline-none"
        >
          <option value="ALL">전체 유형</option>
          <option value="IMAGE">이미지</option>
          <option value="VIDEO">영상</option>
          <option value="AUDIO">음성</option>
        </select>
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value as "ALL" | EvidenceStatus)
            setPage(0)
          }}
          className="h-9 rounded-lg border border-border bg-background px-3 text-sm outline-none"
        >
          <option value="UPLOADED">업로드됨</option>
          <option value="DELETED">삭제됨</option>
          <option value="ALL">전체 상태</option>
        </select>
      </div>

      <div className="overflow-hidden rounded-xl border border-border bg-card">
        <Table>
          <TableHeader className="bg-muted/50">
            <TableRow>
              <TableHead>파일</TableHead>
              <TableHead>사건</TableHead>
              <TableHead>업로더</TableHead>
              <TableHead>해시</TableHead>
              <TableHead>업로드</TableHead>
              <TableHead>분석</TableHead>
              <TableHead>상태</TableHead>
              <TableHead className="text-right">관리</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={8} className="py-10 text-center">
                  <Loader2 className="mx-auto size-5 animate-spin text-muted-foreground" />
                </TableCell>
              </TableRow>
            ) : items.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} className="py-10 text-center text-muted-foreground">
                  조건에 맞는 증거가 없습니다.
                </TableCell>
              </TableRow>
            ) : (
              items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    <div className="flex flex-col">
                      <span className="font-medium">{item.fileName}</span>
                      <span className="text-xs text-muted-foreground">
                        {getFileTypeLabel(item.fileType)} · {formatFileSize(item.fileSize)}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-col">
                      <span>{item.caseName ?? "-"}</span>
                      <span className="text-xs text-muted-foreground">{item.caseNumber ?? "-"}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-col">
                      <span className="font-mono text-sm">{item.uploaderUsername}</span>
                      <span className="text-xs text-muted-foreground">{item.department}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <span className="font-mono text-xs text-muted-foreground">
                        {item.hashValue.slice(0, 8)}…
                      </span>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="size-7 p-0"
                        onClick={() => handleCopyHash(item.hashValue)}
                      >
                        <Copy className="size-3" />
                      </Button>
                    </div>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">{item.uploadedAt}</TableCell>
                  <TableCell>{getAnalysisBadge(item.analysisStatus)}</TableCell>
                  <TableCell>{getStatusBadge(item.status)}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      size="sm"
                      variant="outline"
                      render={<Link href={`/admin/evidences/${item.id}`} />}
                      nativeButton={false}
                    >
                      상세
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
        <span>
          총 {total}건 · {page + 1}/{totalPages} 페이지
        </span>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="outline"
            disabled={page <= 0 || loading}
            onClick={() => setPage((p) => p - 1)}
          >
            이전
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={page + 1 >= totalPages || loading}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      </div>
    </main>
  )
}
