// 담당: 김민희
// 역할: 회원가입 - 기관 유형 enum (백엔드 OrgType 과 매핑)
// 부서 목록은 GET /api/v1/organizations/departments API 사용

// 기관 유형 (value 는 백엔드 enum 과 매핑)
export const ORG_TYPES = [
  { value: "POLICE", label: "경찰기관" },
  { value: "PROSECUTION", label: "검찰기관" },
  { value: "NFS", label: "국과수/감정기관" },
  { value: "PUBLIC_SECURITY", label: "공공기관 감사/보안" },
  { value: "ETC", label: "기타" },
] as const

export type OrgType = (typeof ORG_TYPES)[number]["value"]
