// 라우트 파라미터·사건 URL 조립 시 인코딩을 한 곳에서 처리한다.

export function decodeRouteParam(value: string | undefined): string {
  if (!value) return ""

  let current = value
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const decoded = decodeURIComponent(current.replace(/\+/g, " "))
      if (decoded === current) break
      current = decoded
    } catch {
      break
    }
  }

  return current
}

export function buildCaseDetailPath(caseKey: string, evidenceId?: number): string {
  const normalizedCaseKey = decodeRouteParam(caseKey).trim()
  const path = `/cases/${encodeURIComponent(normalizedCaseKey)}`

  if (evidenceId != null && Number.isFinite(evidenceId)) {
    return `${path}?evidenceId=${encodeURIComponent(String(evidenceId))}`
  }

  return path
}
