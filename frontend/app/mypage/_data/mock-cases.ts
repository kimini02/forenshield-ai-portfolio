import type { CaseSummary } from "@/app/mypage/_types/case"

// API 연동 전 UI 확인용 mock 데이터 (GET /api/v1/cases/me 응답 형태)
export const mockCases: CaseSummary[] = [
  {
    caseId: "c4b37830-3653-4b23-b17b-5241b3783038",
    caseName: "가세연 녹취록 딥페이크 의혹 사건",
    status: "PROCESSING",
    createdAt: "2026-06-18T14:30:00",
    evidenceCount: 2,
    representativeFileName: "suspect_video_01.mp4",
    riskScore: 94,
  },
  {
    caseId: "a1f90210-8821-4c11-9a02-1100aa220011",
    caseName: "CCTV 영상 위변조 검증 요청",
    status: "COMPLETED",
    createdAt: "2026-06-15T09:12:00",
    evidenceCount: 5,
    representativeFileName: "cctv_entrance_02.mov",
    riskScore: 12,
  },
  {
    caseId: "b7e44321-9912-4d22-8c13-2211bb331122",
    caseName: "음성 메일 증거 분석",
    status: "PROCESSING",
    createdAt: "2026-06-17T11:45:00",
    evidenceCount: 1,
    representativeFileName: "voice_mail_0617.wav",
    riskScore: null,
  },
  {
    caseId: "d9c55632-aa23-4e33-9d24-3322cc442233",
    caseName: "인터뷰 클립 진위 확인",
    status: "FAILED",
    createdAt: "2026-06-10T16:20:00",
    evidenceCount: 3,
    representativeFileName: "interview_clip_03.mp4",
    riskScore: null,
  },
]
