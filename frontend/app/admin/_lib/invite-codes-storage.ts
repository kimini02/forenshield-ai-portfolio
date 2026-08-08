import type { InviteCode } from "@/app/admin/_types/admin"
import { MOCK_INVITE_CODES } from "@/app/admin/_data/mock-admin"

const STORAGE_KEY = "veriforensics-invite-codes"

export function getInviteCodes(): InviteCode[] {
  if (typeof window === "undefined") return MOCK_INVITE_CODES

  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(MOCK_INVITE_CODES))
    return MOCK_INVITE_CODES
  }

  try {
    return JSON.parse(raw) as InviteCode[]
  } catch {
    return MOCK_INVITE_CODES
  }
}

export function saveInviteCodes(codes: InviteCode[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(codes))
}

export function generateInviteCode(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  const part = () =>
    Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join("")
  return `VF-${part()}-${part()}`
}
