# 로컬 개발 가이드

## 실행

```bash
# 의존성 설치
pnpm install

# 개발 서버 (http://localhost:3000)
pnpm dev

# 프로덕션 빌드 검증
pnpm build
```

백엔드는 로컬에서 `http://localhost:8080` 기준으로 동작한다고 가정한다.

## `.env.local` 예시

```env
BACKEND_API_ORIGIN=http://localhost:8080
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_USE_MOCK_API=false
NEXT_PUBLIC_UPLOAD_ONLY_MODE=true
```

> `.env.local`은 로컬 확인용 설정이므로 **유지**한다(삭제·되돌리기 금지).

## 모드 전환

| 변수 | 값 | 동작 |
|---|---|---|
| `NEXT_PUBLIC_USE_MOCK_API` | `true` | mock 데이터 사용 (백엔드 없이 화면 확인) |
|  | `false` | 실제 백엔드(`NEXT_PUBLIC_API_URL`) 호출 |
| `NEXT_PUBLIC_UPLOAD_ONLY_MODE` | `true` | S3/AI 미연동 구간: 업로드만 확인, 분석 시작/상세 결과 제한 |
|  | `false` | 전체 흐름 시도 |

코드에서는 환경변수를 직접 읽지 말고 `lib/features.ts`의 `features`를 사용한다.

```ts
import { features } from "@/lib/features"

if (features.mockApi) { /* mock */ }
if (features.uploadOnlyMode) { /* 업로드만 */ }
```

## API 주소

- 프론트: `http://localhost:3000`
- 백엔드: `http://localhost:8080` (`NEXT_PUBLIC_API_URL`)
- 표준 클라이언트: `lib/api/client.ts` (`apiRequest` / `apiRequestForm` / `apiDownload`)

## S3 / AI 미연동 시 주의점

- **업로드는 로컬에서 연결 확인 가능**하지만, S3/AI가 안 붙은 구간에서는 **분석 시작·상세 결과가 제한**된다.
- 결과 데이터가 없을 때 **가짜 차트/결과를 만들지 않는다.** `분석 결과 없음` / `분석 대기` / `AI 연동 전` 같은 상태 UI로 표시.
- **비교 검증**은 AI가 아니라 백엔드의 **해시/메타데이터 기반 비교**다 (업로드만 되면 동작).

## 주의 (git)

- GitHub push 금지, 커밋은 요청 시에만.
- `git reset --hard`, `git checkout --`, `git restore` 사용 금지.
- 사용자 로컬 변경을 되돌리지 않는다.
