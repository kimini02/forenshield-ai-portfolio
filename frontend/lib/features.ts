// 프론트엔드 feature flag 모음.
// 환경변수(NEXT_PUBLIC_*)를 직접 화면에서 읽지 말고 이 객체를 통해 사용한다.
//
// .env.local 예시:
//   NEXT_PUBLIC_USE_MOCK_API=false      // true면 mock 데이터 사용
//   NEXT_PUBLIC_UPLOAD_ONLY_MODE=true   // S3/AI 미연동 구간: 업로드만 확인하는 모드
//   NEXT_PUBLIC_AUTH_REFRESH_ENABLED=false // false면 새로고침·직접 접속 시 세션 복구 차단
//   NEXT_PUBLIC_AUTH_SESSION_TIMEOUT_MINUTES=15 // 마지막 API·HLS·UI 활동 기준 유휴 세션 유지 시간
// Access JWT는 별도로 만료 2분 전 선제 /api/auth/refresh (AuthProvider)

const configuredSessionTimeoutMinutes = Number(
  process.env.NEXT_PUBLIC_AUTH_SESSION_TIMEOUT_MINUTES
)

const authSessionTimeoutMinutes =
  Number.isFinite(configuredSessionTimeoutMinutes) && configuredSessionTimeoutMinutes > 0
    ? configuredSessionTimeoutMinutes
    : 15

export const features = {
  // mock API 모드. true일 때만 mock/sample 데이터를 사용한다.
  mockApi: process.env.NEXT_PUBLIC_USE_MOCK_API === "true",
  // 업로드 전용 모드. S3/AI 미연동 시 분석 시작/상세 결과를 제한한다.
  uploadOnlyMode: process.env.NEXT_PUBLIC_UPLOAD_ONLY_MODE === "true",
  // 기본적으로 새로고침·직접 접속 시 HttpOnly refresh 쿠키로 세션을 복구한다.
  // 운영에서 명시적으로 false를 설정한 경우에만 비활성화한다.
  authRefresh: process.env.NEXT_PUBLIC_AUTH_REFRESH_ENABLED !== "false",
  // 인증 API 성공 등으로 갱신되는 유휴(비활동) 세션 타임아웃(분).
  authSessionTimeoutMinutes,
} as const
