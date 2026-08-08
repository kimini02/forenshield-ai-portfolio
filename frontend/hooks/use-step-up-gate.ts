"use client"

import { useCallback, useEffect, useRef, useState } from "react"

import type { StepUpDialogMode } from "@/components/step-up-gate"
import { fetchEvidenceDetail, type EvidenceDetailData } from "@/lib/api/evidence-detail"
import { getApiErrorMessage, isStepUpRequiredError } from "@/lib/api/errors"
import {
  clearStepUpToken,
  isStepUpValid,
  verifyStepUpPassword,
} from "@/lib/api/step-up-auth"
import { getSession } from "@/lib/auth"
import { features } from "@/lib/features"

export class StepUpCancelledError extends Error {
  constructor() {
    super("STEP_UP_CANCELLED")
    this.name = "StepUpCancelledError"
  }
}

export function isStepUpCancelledError(error: unknown): boolean {
  return error instanceof StepUpCancelledError
}

type PendingStepUp = {
  resolve: () => void
  reject: (error: unknown) => void
}

export function useStepUpGate() {
  const [dialogMode, setDialogMode] = useState<StepUpDialogMode>("closed")
  const [passwordLoading, setPasswordLoading] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [loginId, setLoginId] = useState(() => getSession()?.loginId ?? "")
  const pendingRef = useRef<PendingStepUp | null>(null)

  useEffect(() => {
    function syncLoginId() {
      setLoginId(getSession()?.loginId ?? "")
    }

    syncLoginId()
    window.addEventListener("auth-change", syncLoginId)
    return () => window.removeEventListener("auth-change", syncLoginId)
  }, [])

  const ensureStepUp = useCallback(async () => {
    if (features.mockApi || isStepUpValid()) return

    return new Promise<void>((resolve, reject) => {
      pendingRef.current = { resolve, reject }
      setPasswordError(null)
      setDialogMode("password")
    })
  }, [])

  const submitPassword = useCallback(async (password: string) => {
    setPasswordLoading(true)
    setPasswordError(null)

    try {
      await verifyStepUpPassword(password)
      setDialogMode("success")
      pendingRef.current?.resolve()
      pendingRef.current = null
    } catch (error) {
      setPasswordError(getApiErrorMessage(error, "비밀번호 확인에 실패했습니다."))
    } finally {
      setPasswordLoading(false)
    }
  }, [])

  const cancelPassword = useCallback(() => {
    setDialogMode("closed")
    setPasswordLoading(false)
    setPasswordError(null)
    pendingRef.current?.reject(new StepUpCancelledError())
    pendingRef.current = null
  }, [])

  const closeSuccessDialog = useCallback(() => {
    setDialogMode("closed")
  }, [])

  const fetchEvidenceDetailWithStepUp = useCallback(
    async (evidenceId: number): Promise<EvidenceDetailData> => {
      await ensureStepUp()

      try {
        return await fetchEvidenceDetail(evidenceId)
      } catch (error) {
        if (isStepUpRequiredError(error)) {
          clearStepUpToken()
          await ensureStepUp()
          return await fetchEvidenceDetail(evidenceId)
        }
        throw error
      }
    },
    [ensureStepUp]
  )

  useEffect(() => {
    return () => {
      pendingRef.current?.reject(new StepUpCancelledError())
      pendingRef.current = null
    }
  }, [])

  return {
    dialogMode,
    loginId,
    passwordLoading,
    passwordError,
    submitPassword,
    cancelPassword,
    closeSuccessDialog,
    ensureStepUp,
    fetchEvidenceDetailWithStepUp,
  }
}
