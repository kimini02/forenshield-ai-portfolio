// 화면 전반에서 공통으로 쓰는 포맷터.
// 날짜/파일 크기 포맷 중복을 여기로 모은다.
// (기존 화면은 점진적으로 이 함수들로 교체한다.)

type DateTimeValue = string | number | Date | null | undefined

// 2026-06-18T19:38:29 -> 2026.06.18 19:38
export function formatDateTime(value?: DateTimeValue): string {
  if (value === null || value === undefined || value === "") return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  const hours = String(date.getHours()).padStart(2, "0")
  const minutes = String(date.getMinutes()).padStart(2, "0")

  return `${year}.${month}.${day} ${hours}:${minutes}`
}

// 초 단위 포함: 2026.06.18 19:38:29
export function formatDateTimeWithSeconds(value?: DateTimeValue): string {
  if (value === null || value === undefined || value === "") return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)

  const seconds = String(date.getSeconds()).padStart(2, "0")
  return `${formatDateTime(value)}:${seconds}`
}

type FileSizeUnit = "B" | "KB" | "MB" | "GB" | "TB"

type FormatFileSizeOptions = {
  zeroLabel?: string
  minUnit?: FileSizeUnit
  maxUnit?: FileSizeUnit
  trimTrailingZero?: boolean
}

const FILE_SIZE_UNITS: FileSizeUnit[] = ["B", "KB", "MB", "GB", "TB"]

// 1280000 -> 1.2 MB
export function formatFileSize(
  bytes?: number | null,
  {
    zeroLabel = "-",
    minUnit = "B",
    maxUnit = "TB",
    trimTrailingZero = false,
  }: FormatFileSizeOptions = {}
): string {
  if (!bytes || bytes <= 0) return zeroLabel

  const naturalIndex = Math.floor(Math.log(bytes) / Math.log(1024))
  const minIndex = FILE_SIZE_UNITS.indexOf(minUnit)
  const maxIndex = FILE_SIZE_UNITS.indexOf(maxUnit)
  const boundedIndex = Math.min(
    Math.max(naturalIndex, minIndex >= 0 ? minIndex : 0),
    maxIndex >= 0 ? maxIndex : FILE_SIZE_UNITS.length - 1
  )
  const value = (bytes / 1024 ** boundedIndex).toFixed(1)
  return `${trimTrailingZero ? Number.parseFloat(value) : value} ${FILE_SIZE_UNITS[boundedIndex]}`
}

// 21.0 -> 00:21 (초 -> mm:ss)
export function formatDuration(totalSeconds?: number | null): string {
  if (totalSeconds == null || Number.isNaN(totalSeconds) || totalSeconds < 0) return "-"

  const minutes = Math.floor(totalSeconds / 60)
  const seconds = Math.floor(totalSeconds % 60)

  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`
}
