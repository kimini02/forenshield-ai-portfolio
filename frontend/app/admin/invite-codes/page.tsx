"use client"

import { useCallback, useEffect, useState } from "react"
import { Copy, Loader2, Plus, Ticket } from "lucide-react"
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
import { useAdminToast } from "@/app/admin/_components/admin-toast-provider"
import type { InviteCode } from "@/app/admin/_types/admin"
import { createAdminInviteCode, fetchAdminInviteCodes } from "@/lib/api/admin"
import { getApiErrorMessage } from "@/lib/api/errors"

function getStatusBadge(status: InviteCode["status"]) {
  switch (status) {
    case "UNUSED":
      return (
        <Badge variant="outline" className="border-primary/40 bg-primary/10 text-primary">
          미사용
        </Badge>
      )
    case "USED":
      return (
        <Badge variant="outline" className="border-emerald-200 bg-emerald-100 text-emerald-700">
          사용됨
        </Badge>
      )
    case "EXPIRED":
      return (
        <Badge variant="outline" className="border-muted-foreground/30 text-muted-foreground">
          만료
        </Badge>
      )
  }
}

export default function AdminInviteCodesPage() {
  const [codes, setCodes] = useState<InviteCode[]>([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const { toast } = useAdminToast()

  const loadCodes = useCallback(async () => {
    setLoading(true)
    try {
      const response = await fetchAdminInviteCodes()
      setCodes(response)
    } catch (error) {
      const message = getApiErrorMessage(error, "생성코드 목록을 불러오지 못했습니다.")
      toast({ title: "조회 실패", description: message })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    void loadCodes()
  }, [loadCodes])

  async function handleGenerate() {
    setCreating(true)
    try {
      const newCode = await createAdminInviteCode(30)
      setCodes((prev) => [newCode, ...prev])
      toast({
        title: "생성코드 발급 완료",
        description: `${newCode.code} 코드가 발급되었습니다.`,
      })
    } catch (error) {
      const message = getApiErrorMessage(error, "생성코드 발급에 실패했습니다.")
      toast({ title: "발급 실패", description: message })
    } finally {
      setCreating(false)
    }
  }

  async function handleCopy(code: string) {
    await navigator.clipboard.writeText(code)
    toast({
      title: "복사 완료",
      description: "생성코드가 클립보드에 복사되었습니다.",
    })
  }

  return (
    <>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between px-8 pt-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            유효 생성코드 관리
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            회원가입용 코드를 생성하고 관리합니다.
          </p>
        </div>
        <Button onClick={handleGenerate} disabled={creating}>
          {creating ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <Plus data-icon="inline-start" />}
          코드 생성
        </Button>
      </div>

      <div className="mb-4 flex items-center gap-2 rounded-lg border border-border bg-card px-4 py-3 text-sm mx-8">
        <Ticket className="size-4 text-primary" />
        <span className="text-muted-foreground">미사용 코드</span>
        <span className="font-semibold text-foreground">
          {codes.filter((c) => c.status === "UNUSED").length}개
        </span>
      </div>

      <div className="overflow-hidden rounded-xl border border-border bg-card mx-8 mb-8">
        <Table>
          <TableHeader className="bg-muted/50">
            <TableRow>
              <TableHead>코드</TableHead>
              <TableHead>생성일</TableHead>
              <TableHead>만료일</TableHead>
              <TableHead>상태</TableHead>
              <TableHead>사용자</TableHead>
              <TableHead className="text-right">관리</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                  <Loader2 className="mx-auto size-5 animate-spin" />
                </TableCell>
              </TableRow>
            ) : codes.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                  발급된 생성코드가 없습니다.
                </TableCell>
              </TableRow>
            ) : (
              codes.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="font-mono font-medium">{item.code}</TableCell>
                  <TableCell className="text-muted-foreground">{item.createdAt}</TableCell>
                  <TableCell className="text-muted-foreground">{item.expiresAt ?? "-"}</TableCell>
                  <TableCell>{getStatusBadge(item.status)}</TableCell>
                  <TableCell className="text-muted-foreground">{item.usedBy ?? "-"}</TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" variant="outline" onClick={() => handleCopy(item.code)}>
                      <Copy className="size-3" />
                      복사
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </>
  )
}
