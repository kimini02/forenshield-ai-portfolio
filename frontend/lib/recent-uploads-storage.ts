import type { UploadResult } from "@/lib/evidence-api"

const RECENT_ANALYSES_KEY = "veriforensics-recent-analyses"
const LEGACY_RECENT_UPLOADS_KEY = "veriforensics-recent-uploads"
const LAST_UPLOAD_KEY = "veriforensics-last-upload"
export const MAX_RECENT_UPLOADS = 5

export function loadRecentUploads(): UploadResult[] {
  if (typeof window === "undefined") return []

  const raw =
    localStorage.getItem(RECENT_ANALYSES_KEY) ??
    localStorage.getItem(LEGACY_RECENT_UPLOADS_KEY)

  if (!raw) return []

  try {
    const parsed = JSON.parse(raw) as UploadResult[]
    return Array.isArray(parsed) ? parsed.slice(0, MAX_RECENT_UPLOADS) : []
  } catch {
    return []
  }
}

export function loadLastUpload(): UploadResult | null {
  if (typeof window === "undefined") return null

  const raw = localStorage.getItem(LAST_UPLOAD_KEY)
  if (!raw) return null

  try {
    return JSON.parse(raw) as UploadResult
  } catch {
    return null
  }
}

export function saveRecentUploads(uploads: UploadResult[]) {
  localStorage.setItem(
    RECENT_ANALYSES_KEY,
    JSON.stringify(uploads.slice(0, MAX_RECENT_UPLOADS))
  )
  localStorage.removeItem(LEGACY_RECENT_UPLOADS_KEY)
}

export function saveLastUpload(upload: UploadResult | null) {
  if (!upload) {
    localStorage.removeItem(LAST_UPLOAD_KEY)
    return
  }
  localStorage.setItem(LAST_UPLOAD_KEY, JSON.stringify(upload))
}

/** 해시 기준 중복 제거 후 최신순 최대 5건 유지 */
export function mergeRecentUploads(
  existing: UploadResult[],
  incoming: UploadResult[]
): { merged: UploadResult[]; added: UploadResult[] } {
  const knownHashes = new Set(existing.map((item) => item.hashValue))
  const added: UploadResult[] = []

  for (const item of incoming) {
    if (!knownHashes.has(item.hashValue)) {
      knownHashes.add(item.hashValue)
      added.push(item)
    }
  }

  const merged = [...added, ...existing]
    .sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime())
    .slice(0, MAX_RECENT_UPLOADS)

  return { merged, added }
}
