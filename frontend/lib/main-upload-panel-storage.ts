import type { AnalysisStatus } from "@/lib/analysis-status"
import type { UploadResult } from "@/lib/evidence-api"

const MAIN_UPLOAD_PANEL_KEY = "veriforensics-main-upload-panel"

export type PersistedUploadEntry = {
  result: UploadResult
  analysisStatus?: AnalysisStatus
  analysisProgress?: number
}

export type MainUploadPanelSession = {
  caseName: string
  entries: PersistedUploadEntry[]
  hasUploadedOnce: boolean
}

export function loadMainUploadPanelSession(): MainUploadPanelSession | null {
  if (typeof window === "undefined") return null

  const raw = localStorage.getItem(MAIN_UPLOAD_PANEL_KEY)
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as MainUploadPanelSession
    if (!parsed || !Array.isArray(parsed.entries)) return null
    return parsed
  } catch {
    return null
  }
}

export function saveMainUploadPanelSession(session: MainUploadPanelSession) {
  if (typeof window === "undefined") return

  if (session.entries.length === 0 && !session.caseName && !session.hasUploadedOnce) {
    localStorage.removeItem(MAIN_UPLOAD_PANEL_KEY)
    return
  }

  localStorage.setItem(MAIN_UPLOAD_PANEL_KEY, JSON.stringify(session))
}

export function clearMainUploadPanelSession() {
  if (typeof window === "undefined") return
  localStorage.removeItem(MAIN_UPLOAD_PANEL_KEY)
}

export function createPlaceholderFile(result: UploadResult): File {
  return new File([], result.fileName, {
    type: "application/octet-stream",
  })
}
