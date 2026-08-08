"use client"

import { useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { AlertCircle, UploadCloud, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { ApiError } from "@/lib/api/client"
import { createCase, setRepresentativeEvidence, uploadEvidenceToCase } from "@/lib/api/case-workflow"
import type { AuthSession } from "@/lib/auth"
import { canCreateCase, getAppUserFromSession } from "@/lib/permissions"
import { buildCaseDetailPath } from "@/lib/route-params"
import { cn } from "@/lib/utils"

const MAX_INITIAL_EVIDENCE_FILE_SIZE = 500 * 1024 * 1024
const ALLOWED_INITIAL_EVIDENCE_EXTENSIONS = new Set(["mp4", "mov", "avi", "mkv"])
const INITIAL_EVIDENCE_ACCEPT = ".mp4,.mov,.avi,.mkv,video/mp4,video/quicktime,video/x-msvideo,video/x-matroska"

export function CaseCreateDialog({
  open,
  onClose,
  session,
  existingCaseNames = [],
}: {
  open: boolean
  onClose: () => void
  session: AuthSession | null
  existingCaseNames?: string[]
}) {
  const router = useRouter()
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const [newCaseName, setNewCaseName] = useState("")
  const [representativeFile, setRepresentativeFile] = useState<File | null>(null)
  const [createError, setCreateError] = useState<string | null>(null)
  const [recoverableCase, setRecoverableCase] = useState<{ caseId: string; caseName: string } | null>(null)
  const [isCreating, setIsCreating] = useState(false)

  if (!open) return null

  async function handleCreateCase() {
    const trimmed = newCaseName.trim()
    if (!trimmed) {
      setCreateError("사건명을 입력해 주세요.")
      return
    }

    if (existingCaseNames.some((name) => normalizeCaseNameForCompare(name) === normalizeCaseNameForCompare(trimmed))) {
      setCreateError("이미 등록된 사건명입니다. 다른 사건명을 입력해 주세요.")
      return
    }

    const fileError = validateOptionalEvidenceFile(representativeFile)
    if (fileError) {
      setCreateError(fileError)
      return
    }
    const initialEvidenceFile = representativeFile

    setIsCreating(true)
    setCreateError(null)

    try {
      const created = await createCase(trimmed)
      if (!initialEvidenceFile) {
        resetForm()
        onClose()
        router.push(buildCaseDetailPath(created.caseId))
        return
      }

      let representativeEvidence: Awaited<ReturnType<typeof uploadEvidenceToCase>>
      try {
        representativeEvidence = await uploadEvidenceToCase(
          created.caseId,
          created.caseName,
          initialEvidenceFile
        )
      } catch (uploadError) {
        setRecoverableCase({ caseId: created.caseId, caseName: created.caseName })
        setCreateError(getEvidenceUploadRecoveryMessage(uploadError))
        return
      }

      try {
        await setRepresentativeEvidence(created.caseId, representativeEvidence.evidenceId)
      } catch {
        // 실제 API 계약 전에는 대표 증거 지정이 지원되지 않을 수 있다.
      }

      resetForm()
      onClose()
      router.push(buildCaseDetailPath(created.caseId, representativeEvidence.evidenceId))
    } catch (error) {
      setCreateError(getCreateCaseErrorMessage(error))
    } finally {
      setIsCreating(false)
    }
  }

  async function retryEvidenceUpload() {
    if (!recoverableCase) return

    const fileError = validateOptionalEvidenceFile(representativeFile)
    if (fileError) {
      setCreateError(fileError)
      return
    }
    const initialEvidenceFile = representativeFile
    if (!initialEvidenceFile) return

    setIsCreating(true)
    setCreateError(null)

    try {
      const representativeEvidence = await uploadEvidenceToCase(
        recoverableCase.caseId,
        recoverableCase.caseName,
        initialEvidenceFile
      )
      try {
        await setRepresentativeEvidence(recoverableCase.caseId, representativeEvidence.evidenceId)
      } catch {
        // 실제 API 계약 전에는 대표 증거 지정이 지원되지 않을 수 있다.
      }
      const targetPath = buildCaseDetailPath(recoverableCase.caseId, representativeEvidence.evidenceId)
      resetForm()
      onClose()
      router.push(targetPath)
    } catch (error) {
      setCreateError(getEvidenceUploadRecoveryMessage(error))
    } finally {
      setIsCreating(false)
    }
  }

  function closeDialog() {
    if (isCreating) return
    resetForm()
    onClose()
  }

  function resetForm() {
    setNewCaseName("")
    setRepresentativeFile(null)
    setCreateError(null)
    setRecoverableCase(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ""
    }
  }

  function selectRepresentativeFile(fileList: FileList | null) {
    const file = fileList?.item(0)
    if (!file) return

    const fileError = validateOptionalEvidenceFile(file)
    if (fileError) {
      setRepresentativeFile(null)
      setCreateError(fileError)
      if (fileInputRef.current) {
        fileInputRef.current.value = ""
      }
      return
    }

    setRepresentativeFile(file)
    setCreateError(null)
  }

  const appUser = getAppUserFromSession(session)
  const receptionistText = appUser
    ? `${appUser.name} · ${appUser.organizationName} · ${appUser.department}`
    : `${session?.name ?? "김민희"} · 서울경찰청 · 사이버수사팀`

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/35 px-4 py-6">
      <section className="w-full max-w-5xl rounded-xl border border-border bg-card p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold text-foreground">사건 등록</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              사건을 먼저 등록하고, 필요하면 증거 영상을 함께 업로드합니다.
            </p>
          </div>
          <button
            type="button"
            onClick={closeDialog}
            className="inline-flex size-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground"
            aria-label="사건 등록 닫기"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 grid gap-4">
          <section className="rounded-xl border border-border bg-background p-4">
            <div className="mb-4 flex items-center gap-3">
              <span className="flex size-7 items-center justify-center rounded-full bg-teal-600 text-sm font-bold text-white">
                1
              </span>
              <div>
                <h3 className="text-sm font-bold text-foreground">사건 정보</h3>
                <p className="text-xs text-muted-foreground">목록과 상세 화면에 표시될 사건명을 입력합니다.</p>
              </div>
            </div>
            <div className="grid gap-4">
              <div>
                <label htmlFor="newCaseName" className="block text-sm font-bold text-foreground">
                  사건명 <span className="text-red-500">*</span>
                </label>
                <input
                  id="newCaseName"
                  value={newCaseName}
                  onChange={(event) => setNewCaseName(event.target.value)}
                  placeholder="예: 2026-서울-0123 영상 증거 분석"
                  className="mt-2 h-11 w-full rounded-lg border border-border bg-card px-4 text-sm font-medium text-foreground outline-none transition-colors placeholder:text-muted-foreground focus:border-teal-300 focus:ring-4 focus:ring-teal-100"
                />
              </div>
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg bg-muted/40 px-3 py-2 text-xs">
              <span className="font-semibold text-muted-foreground">접수자</span>
              <span className="font-bold text-foreground">{receptionistText}</span>
            </div>
          </section>

          <section className="rounded-xl border border-border bg-background p-4">
            <div className="mb-4 flex items-center gap-3">
              <span className="flex size-7 items-center justify-center rounded-full border border-border bg-muted text-sm font-bold text-muted-foreground">
                2
              </span>
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-sm font-bold text-foreground">증거 업로드</h3>
                  <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-bold text-muted-foreground">
                    선택
                  </span>
                </div>
                <p className="text-xs text-muted-foreground">
                  영상이 준비되어 있으면 사건 등록과 함께 업로드할 수 있습니다.
                </p>
              </div>
            </div>

            <label
              htmlFor="dashboardEvidenceFile"
              className={cn(
                "flex min-h-36 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed px-4 py-6 text-center transition-colors",
                representativeFile
                  ? "border-teal-300 bg-teal-50/70 text-teal-700"
                  : "border-border bg-card text-muted-foreground hover:border-teal-300 hover:bg-teal-50/50 hover:text-teal-700"
              )}
            >
              <UploadCloud className="size-8" aria-hidden="true" />
              <span className="mt-3 text-sm font-bold text-foreground">
                {representativeFile ? "대표 증거 선택 완료" : "대표 증거 영상 선택"}
              </span>
              <span className="mt-1 text-xs text-muted-foreground">
                MP4, MOV 등 영상 파일을 선택하세요.
              </span>
              {representativeFile ? (
                <span className="mt-3 flex max-w-full items-center gap-2 rounded-full bg-card px-3 py-1.5 text-xs font-bold text-teal-700 shadow-sm">
                  <span className="max-w-[420px] truncate">{representativeFile.name}</span>
                  <button
                    type="button"
                    className="inline-flex size-5 shrink-0 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                    aria-label="선택한 대표 증거 제거"
                    onClick={(event) => {
                      event.preventDefault()
                      if (fileInputRef.current) {
                        fileInputRef.current.value = ""
                      }
                      setRepresentativeFile(null)
                    }}
                  >
                    <X className="size-3.5" aria-hidden="true" />
                  </button>
                </span>
              ) : null}
            </label>
            <input
              ref={fileInputRef}
              id="dashboardEvidenceFile"
              type="file"
              accept={INITIAL_EVIDENCE_ACCEPT}
              className="sr-only"
              onChange={(event) => {
                selectRepresentativeFile(event.target.files)
              }}
            />
          </section>
        </div>

        {createError ? (
          <div className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-bold text-red-600">
            <p className="flex items-center gap-2">
              <AlertCircle className="size-4" aria-hidden="true" />
              {createError}
            </p>
            {recoverableCase ? (
              <div className="mt-3 flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="h-8 border-red-200 bg-white px-3 text-xs font-bold text-red-700 hover:bg-red-50"
                  disabled={isCreating}
                  onClick={() => router.push(buildCaseDetailPath(recoverableCase.caseId))}
                >
                  사건 상세로 이동
                </Button>
                <Button
                  type="button"
                  className="h-8 bg-red-700 px-3 text-xs font-bold text-white hover:bg-red-800"
                  disabled={isCreating}
                  onClick={() => void retryEvidenceUpload()}
                >
                  다시 업로드 시도
                </Button>
              </div>
            ) : null}
          </div>
        ) : null}
        <div className="mt-6 flex justify-end gap-2">
          <p className="mr-auto self-center text-xs font-semibold text-muted-foreground">
            영상까지 선택하면 원본 저장, SHA-256 해시 생성, 메타데이터 추출이 자동으로 진행됩니다.
          </p>
          <Button
            type="button"
            variant="outline"
            className="h-10 px-4 font-bold"
            disabled={isCreating}
            onClick={closeDialog}
          >
            취소
          </Button>
          <Button
            type="button"
            className="h-10 bg-teal-600 px-4 font-bold hover:bg-teal-700"
            disabled={isCreating}
            onClick={() => void (recoverableCase ? retryEvidenceUpload() : handleCreateCase())}
          >
            {isCreating
              ? "등록 중"
              : recoverableCase
                ? "증거 다시 업로드"
                : representativeFile
                  ? "사건 + 증거 등록"
                  : "사건 등록"}
          </Button>
        </div>
      </section>
    </div>
  )
}

export function canRegisterCase(session: AuthSession | null) {
  return canCreateCase(getAppUserFromSession(session))
}

function normalizeCaseNameForCompare(caseName: string) {
  return caseName.trim().toLowerCase()
}

function validateOptionalEvidenceFile(file: File | null) {
  if (!file) return null

  const extension = file.name.split(".").pop()?.toLowerCase() ?? ""
  const hasAllowedExtension = ALLOWED_INITIAL_EVIDENCE_EXTENSIONS.has(extension)
  const hasValidMimeType = !file.type || file.type.startsWith("video/")

  if (!hasAllowedExtension || !hasValidMimeType) {
    return "지원하지 않는 영상 형식입니다. mp4, mov, avi, mkv 파일만 등록할 수 있습니다."
  }

  if (file.size > MAX_INITIAL_EVIDENCE_FILE_SIZE) {
    return "파일 크기가 너무 큽니다. 500MB 이하의 영상을 선택해주세요."
  }

  return null
}

function getEvidenceUploadRecoveryMessage(error: unknown) {
  const detail = getCreateCaseErrorMessage(error)
  return `사건은 생성되었지만 증거 영상 업로드에 실패했습니다. 사건 상세 화면에서 증거를 다시 업로드할 수 있습니다. (${detail})`
}

function getCreateCaseErrorMessage(error: unknown) {
  if (isLocalFileReadError(error)) {
    return "선택한 파일을 브라우저가 읽지 못했습니다. 파일이 이동/삭제되었거나 접근 권한이 끊겼을 수 있어요. 파일을 다시 선택한 뒤 등록해 주세요."
  }

  if (error instanceof ApiError) {
    if (error.errorCode === "DUPLICATE_CASE_NAME" || error.status === 409) {
      return "이미 등록된 사건명입니다. 다른 사건명을 입력해 주세요."
    }
    if (error.errorCode === "INVALID_REQUEST" || error.status === 400) {
      return error.details?.[0]?.reason ?? error.message ?? "사건명을 확인해 주세요."
    }
    if (error.errorCode === "FORBIDDEN" || error.status === 403) {
      return "사건을 등록할 권한이 없습니다. 관리자에게 권한을 확인해 주세요."
    }
    return error.message
  }

  return error instanceof Error
    ? error.message
    : "사건과 증거 등록에 실패했습니다. 잠시 후 다시 시도해 주세요."
}

function isLocalFileReadError(error: unknown) {
  if (!(error instanceof Error)) return false

  return (
    error.name === "NotReadableError" ||
    error.message.includes("requested file could not be read") ||
    error.message.includes("permission problems")
  )
}
