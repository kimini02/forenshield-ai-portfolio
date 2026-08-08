import type { AdminLog, AdminProfile, AdminUser, InviteCode } from "@/app/admin/_types/admin"

export const MOCK_ADMIN_USERS: AdminUser[] = [
  {
    id: "1",
    username: "admin_kim",
    displayName: "김관리",
    email: "kim@police.go.kr",
    organizationName: "서울경찰청",
    organizationType: "POLICE",
    department: "사이버수사과",
    role: "ORG_ADMIN",
    joinedAt: "2026-06-01",
    status: "PENDING",
  },
  {
    id: "2",
    username: "lee_forensic",
    displayName: "이포렌",
    email: "lee@nfs.go.kr",
    organizationName: "서울경찰청",
    organizationType: "NFS",
    department: "디지털분석팀",
    role: "INVESTIGATOR",
    joinedAt: "2026-06-03",
    status: "PENDING",
  },
  {
    id: "3",
    username: "park_invest",
    displayName: "박수사",
    email: "park@prosecution.go.kr",
    organizationName: "서울경찰청",
    organizationType: "PROSECUTION",
    department: "과학수사부",
    role: "INVESTIGATOR",
    joinedAt: "2026-05-28",
    status: "APPROVED",
  },
  {
    id: "4",
    username: "choi_audit",
    displayName: "최감사",
    email: "choi@audit.go.kr",
    organizationName: "서울경찰청",
    organizationType: "PUBLIC_SECURITY",
    department: "감사팀",
    role: "REVIEWER",
    joinedAt: "2026-05-20",
    status: "APPROVED",
  },
  {
    id: "5",
    username: "jung_temp",
    displayName: "정임시",
    email: "jung@example.com",
    organizationName: "서울경찰청",
    organizationType: "ETC",
    department: "외부협력",
    role: "INVESTIGATOR",
    joinedAt: "2026-06-08",
    status: "REJECTED",
  },
]

export const MOCK_INVITE_CODES: InviteCode[] = [
  {
    id: "c1",
    code: "VF-A3K9-7M2P",
    createdAt: "2026-06-01",
    expiresAt: "2026-07-01",
    status: "USED",
    usedBy: "park_invest",
  },
  {
    id: "c2",
    code: "VF-B8N1-4Q6R",
    createdAt: "2026-06-05",
    expiresAt: "2026-07-05",
    status: "UNUSED",
  },
]

export const MOCK_ADMIN_LOGS: AdminLog[] = [
  { id: "l13", timestamp: "2026-06-09 11:24", category: "SECURITY", actor: "lee_forensic", actorId: "2", department: "디지털분석팀", action: "화면 캡처 감지", detail: "PrintScreen 키 입력 감지 · EVD-2024-0184" },
  { id: "l1", timestamp: "2026-06-09 09:12", category: "AUTH", actor: "admin_kim", actorId: "1", department: "사이버수사과", action: "로그인", detail: "내부망 접속" },
  { id: "l2", timestamp: "2026-06-09 09:15", category: "COC", actor: "lee_forensic", actorId: "2", department: "디지털분석팀", action: "증거 업로드", detail: "interview_clip_04.mp4" },
  { id: "l3", timestamp: "2026-06-09 09:18", category: "ANALYSIS", actor: "system", actorId: "system", department: "시스템", action: "분석 요청", detail: "CASE-2026-0412" },
  { id: "l4", timestamp: "2026-06-09 09:22", category: "COC", actor: "park_invest", actorId: "3", department: "과학수사부", action: "증거 해시 검증", detail: "SHA-256 일치" },
  { id: "l5", timestamp: "2026-06-09 09:25", category: "ANALYSIS", actor: "system", actorId: "system", department: "시스템", action: "분석 완료", detail: "위변조 의심 96%" },
  { id: "l6", timestamp: "2026-06-09 09:30", category: "ADMIN", actor: "admin_kim", actorId: "1", department: "사이버수사과", action: "가입 승인", detail: "park_invest" },
  { id: "l7", timestamp: "2026-06-09 10:01", category: "COC", actor: "choi_audit", actorId: "4", department: "감사팀", action: "결과 열람", detail: "CASE-2026-0410" },
  { id: "l8", timestamp: "2026-06-09 10:05", category: "AUTH", actor: "lee_forensic", actorId: "2", department: "디지털분석팀", action: "로그인", detail: "내부망 접속" },
  { id: "l9", timestamp: "2026-06-09 10:12", category: "ADMIN", actor: "admin_kim", actorId: "1", department: "사이버수사과", action: "생성코드 발급", detail: "VF-B8N1-4Q6R" },
  { id: "l10", timestamp: "2026-06-09 10:20", category: "COC", actor: "admin_kim", actorId: "1", department: "사이버수사과", action: "CoC 로그보내기", detail: "CSV 120건" },
  { id: "l11", timestamp: "2026-06-09 11:00", category: "ANALYSIS", actor: "park_invest", actorId: "3", department: "과학수사부", action: "분석 요청", detail: "voicemail_evidence.wav" },
  { id: "l12", timestamp: "2026-06-09 11:15", category: "COC", actor: "lee_forensic", actorId: "2", department: "디지털분석팀", action: "증거 다운로드", detail: "scene_photo_117.jpg" },
]

export const MOCK_ADMIN_PROFILE: AdminProfile = {
  username: "admin_kim",
  displayName: "김관리",
  email: "kim@police.go.kr",
  department: "사이버수사과",
  phone: "010-1234-5678",
  role: "시스템 관리자",
}

export function getLogDepartments(logs: AdminLog[]): string[] {
  return [...new Set(logs.map((log) => log.department))].sort()
}
