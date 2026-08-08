"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"

const inputClassName =
  "h-9 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"

type DeleteEvidenceDialogProps = {
  fileName: string
  open: boolean
  loading: boolean
  onConfirm: (reason: string) => void
  onCancel: () => void
}

export function DeleteEvidenceDialog({
  fileName,
  open,
  loading,
  onConfirm,
  onCancel,
}: DeleteEvidenceDialogProps) {
  const [reason, setReason] = useState("")

  if (!open) return null

  function handleConfirm() {
    if (!reason.trim()) return
    onConfirm(reason.trim())
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <div
        role="alertdialog"
        aria-labelledby="delete-evidence-title"
        aria-describedby="delete-evidence-desc"
        className="w-full max-w-md rounded-xl border border-border bg-card p-6 shadow-lg"
      >
        <h2 id="delete-evidence-title" className="text-lg font-semibold text-foreground">
          증거 삭제
        </h2>
        <p id="delete-evidence-desc" className="mt-2 text-sm text-muted-foreground">
          <span className="font-mono font-medium text-foreground">{fileName}</span>{" "}
          증거를 삭제합니다. CoC 로그에 사유가 기록됩니다.
        </p>
        <div className="mt-4 space-y-1.5">
          <label htmlFor="delete-reason" className="text-sm font-medium text-foreground">
            삭제 사유
          </label>
          <textarea
            id="delete-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            className={`${inputClassName} min-h-20 py-2`}
            placeholder="삭제 사유를 입력하세요."
          />
        </div>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="outline" onClick={onCancel} disabled={loading}>
            취소
          </Button>
          <Button
            variant="destructive"
            onClick={handleConfirm}
            disabled={loading || !reason.trim()}
          >
            {loading ? "삭제 중..." : "삭제"}
          </Button>
        </div>
      </div>
    </div>
  )
}
