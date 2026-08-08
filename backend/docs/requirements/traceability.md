# ForenShield 요구사항 추적 매트릭스 (RTM)

> **기준:** `docs/requirements/source/기능명세서_최종.xlsx` · **백엔드(BE) 시트**
> **미디어 스코프:** 영상(VIDEO) 전용 — Excel REQ·DTL·CMP는 영상 전제

구현 상태는 `api/specification.md`·코드와 교차검토하세요. ✅/🟡/⬜는 자동 힌트입니다.

## 1. ID 체계

| 구분 | 형식 | 예시 |
| :--- | :--- | :--- |
| 요구사항 | `RQ-{영역}-{번호}` | `RQ-REQ-047` |
| 기능(파트별) | `FN-{영역}-{번호}-{접미}` | `FN-REQ-047-BE` |

접미사: `FE` · `BE` · `AI` · `INF`

## 2. 백엔드 기능 ↔ RQ 매핑

| FN-ID | RQ-ID | 기능명 | API/컴포넌트 | 구현 상태 |
| :--- | :--- | :--- | :--- | :---: |
| `FN-ADMIN-114-BE` | `RQ-ADMIN-114` | 관리자 페이지 접근 제한 | — | 🟡 |
| `FN-ADMIN-115-BE` | `RQ-ADMIN-115` | 미인증 사용자 차단 | — | 🟡 |
| `FN-ADMIN-116-BE` | `RQ-ADMIN-116` | 일반 사용자 접근 차단 | — | 🟡 |
| `FN-ADMIN-117-BE` | `RQ-ADMIN-117` | 관리자 공통 레이아웃 | — | 🟡 |
| `FN-ADMIN-119-BE` | `RQ-ADMIN-119` | 관리자 API JWT 인증 | — | ✅ |
| `FN-ADMIN-120-BE` | `RQ-ADMIN-120` | 관리자 통계 조회 | — | ✅ |
| `FN-ADMIN-123-BE` | `RQ-ADMIN-123` | 통계 조회 실패 처리 | — | 🟡 |
| `FN-ADMIN-124-BE` | `RQ-ADMIN-124` | 사용자 목록 조회 | — | ✅ |
| `FN-ADMIN-125-BE` | `RQ-ADMIN-125` | 사용자 검색 | — | 🟡 |
| `FN-ADMIN-126-BE` | `RQ-ADMIN-126` | 상태별 필터링 | — | 🟡 |
| `FN-ADMIN-127-BE` | `RQ-ADMIN-127` | 가입 승인 | — | 🟡 |
| `FN-ADMIN-128-BE` | `RQ-ADMIN-128` | 가입 반려 | — | 🟡 |
| `FN-ADMIN-130-BE` | `RQ-ADMIN-130` | 비밀번호 초기화 | — | 🟡 |
| `FN-ADMIN-131-BE` | `RQ-ADMIN-131` | 사용자 삭제 | — | 🟡 |
| `FN-ADMIN-132-BE` | `RQ-ADMIN-132` | 계정 관리 이력 기록 | — | 🟡 |
| `FN-ADMIN-133-BE` | `RQ-ADMIN-133` | 초대코드 목록 조회 | — | ✅ |
| `FN-ADMIN-134-BE` | `RQ-ADMIN-134` | 초대코드 생성 | — | 🟡 |
| `FN-ADMIN-135-BE` | `RQ-ADMIN-135` | 초대코드 복사 | — | 🟡 |
| `FN-ADMIN-137-BE` | `RQ-ADMIN-137` | 시스템 로그 조회 | — | ✅ |
| `FN-ADMIN-138-BE` | `RQ-ADMIN-138` | CoC 로그 전용 조회 | — | 🟡 |
| `FN-ADMIN-139-BE` | `RQ-ADMIN-139` | 부서별 로그 필터 | — | 🟡 |
| `FN-ADMIN-140-BE` | `RQ-ADMIN-140` | 로그 검색 | — | 🟡 |
| `FN-ADMIN-141-BE` | `RQ-ADMIN-141` | 로그 CSV 내보내기 | — | 🟡 |
| `FN-ADMIN-143-BE` | `RQ-ADMIN-143` | 관리자 프로필 조회 | — | 🟡 |
| `FN-ADMIN-145-BE` | `RQ-ADMIN-145` | 관리자 비밀번호 변경 | — | 🟡 |
| `FN-ADMIN-146-BE` | `RQ-ADMIN-146` | 증거 목록 조회 | — | ✅ |
| `FN-ADMIN-147-BE` | `RQ-ADMIN-147` | 증거 검색·필터 | — | 🟡 |
| `FN-ADMIN-148-BE` | `RQ-ADMIN-148` | 증거 상세 조회 | — | 🟡 |
| `FN-ADMIN-149-BE` | `RQ-ADMIN-149` | 해시값 복사 | — | 🟡 |
| `FN-ADMIN-150-BE` | `RQ-ADMIN-150` | 관리자 분석 통계 조회 | `AdminAnalysisStatsService` | ✅ |
| `FN-CMP-091-BE` | `RQ-CMP-091` | 원본 증거 선택 | `controller/` · `service/` | 🟡 |
| `FN-CMP-092-BE` | `RQ-CMP-092` | 검증 대상 파일 업로드 | `controller/` · `service/` | 🟡 |
| `FN-CMP-093-BE` | `RQ-CMP-093` | 비교 검증 실행 | `controller/` · `service/` | ⬜ |
| `FN-CMP-094-BE` | `RQ-CMP-094` | SHA-256 해시 비교 | `controller/` · `service/` | ⬜ |
| `FN-CMP-095-BE` | `RQ-CMP-095` | 파일 크기 비교 | `controller/` · `service/` | ⬜ |
| `FN-CMP-096-BE` | `RQ-CMP-096` | 영상 길이 비교 | `controller/` · `service/` | ⬜ |
| `FN-CMP-097-BE` | `RQ-CMP-097` | 코덱 정보 비교 | `controller/` · `service/` | ⬜ |
| `FN-CMP-098-BE` | `RQ-CMP-098` | 메타데이터 타임스탬프 비교 | `controller/` · `service/` | ⬜ |
| `FN-CMP-099-BE` | `RQ-CMP-099` | GOP 구조 비교 | `controller/` · `service/` | ⬜ |
| `FN-CMP-100-BE` | `RQ-CMP-100` | 스트림 체크섬 비교 | `controller/` · `service/` | ⬜ |
| `FN-CMP-101-BE` | `RQ-CMP-101` | 비교 결과 요약 표시 | `controller/` · `service/` | ⬜ |
| `FN-CMP-102-BE` | `RQ-CMP-102` | 항목별 비교표 표시 | `controller/` · `service/` | ⬜ |
| `FN-CMP-103-BE` | `RQ-CMP-103` | 블록체인 등록 해시 대조 | `controller/` · `service/` | ⬜ |
| `FN-CMP-104-BE` | `RQ-CMP-104` | 비교검증 PDF 다운로드 | `controller/` · `service/` | ⬜ |
| `FN-DSH-041-BE` | `RQ-DSH-041` | 서비스 소개 및 바로가기 | `EvidenceController` · `DashboardIntroService` | ✅ |
| `FN-DSH-042-BE` | `RQ-DSH-042` | 프로세스 단계 시각화 | `controller/` · `service/` | — (FE 주도) |
| `FN-DSH-043-BE` | `RQ-DSH-043` | 실시간 현황 통계 카드 | `controller/` · `service/` | ✅ |
| `FN-DTL-053-BE` | `RQ-DTL-053` | 분석 결과 상세페이지 접근 | CaseController | ✅ |
| `FN-DTL-054-BE` | `RQ-DTL-054` | 파일 기본정보 표시 | CaseController | 🟡 |
| `FN-DTL-055-BE` | `RQ-DTL-055` | 분석 상태 표시 | CaseController | ✅ |
| `FN-DTL-057-BE` | `RQ-DTL-057` | 최종 위험도 표시 | CaseController | 🟡 |
| `FN-DTL-058-BE` | `RQ-DTL-058` | 위험 등급 표시 | CaseController | 🟡 |
| `FN-DTL-059-BE` | `RQ-DTL-059` | 신뢰도 표시 | CaseController | 🟡 |
| `FN-DTL-060-BE` | `RQ-DTL-060` | 프레임별 위험도 시각화 | CaseController | 🟡 |
| `FN-DTL-061-BE` | `RQ-DTL-061` | 의심 구간 표시 | CaseController | 🟡 |
| `FN-DTL-062-BE` | `RQ-DTL-062` | 영상 딥페이크 탐지 근거 표시 | CaseController | 🟡 |
| `FN-DTL-063-BE` | `RQ-DTL-063` | 립싱크 의심 결과 표시 | CaseController | 🟡 |
| `FN-DTL-064-BE` | `RQ-DTL-064` | 프레임 편집 결과 표시 | CaseController | 🟡 |
| `FN-DTL-065-BE` | `RQ-DTL-065` | 영상 편집 의심 결과 표시 | CaseController | 🟡 |
| `FN-DTL-066-BE` | `RQ-DTL-066` | 재인코딩 흔적 표시 | CaseController | 🟡 |
| `FN-DTL-067-BE` | `RQ-DTL-067` | 메타데이터 결과 표시 | CaseController | 🟡 |
| `FN-DTL-068-BE` | `RQ-DTL-068` | 코덱·해상도·FPS 정보 표시 | CaseController | 🟡 |
| `FN-DTL-069-BE` | `RQ-DTL-069` | EXIF 및 타임스탬프 정보 표시 | CaseController | 🟡 |
| `FN-DTL-071-BE` | `RQ-DTL-071` | Recovery Score 표시 | CaseController | 🟡 |
| `FN-DTL-072-BE` | `RQ-DTL-072` | 데이터 소실도 표시 | CaseController | 🟡 |
| `FN-DTL-073-BE` | `RQ-DTL-073` | 분석 처리 타임라인 표시 | CaseController | 🟡 |
| `FN-DTL-074-BE` | `RQ-DTL-074` | SHA-256 해시값 표시 | CaseController | 🟡 |
| `FN-DTL-075-BE` | `RQ-DTL-075` | Evidence Manifest 정보 표시 | CaseController | 🟡 |
| `FN-DTL-077-BE` | `RQ-DTL-077` | CoC 요약 표시 | CaseController | 🟡 |
| `FN-DTL-078-BE` | `RQ-DTL-078` | 블록체인 등록 상태 표시 | CaseController | ⬜ |
| `FN-DTL-079-BE` | `RQ-DTL-079` | 블록체인 무결성 검증 결과 표시 | CaseController | ⬜ |
| `FN-DTL-081-BE` | `RQ-DTL-081` | 구간별 비교 분석 결과 표시 | CaseController | ⬜ |
| `FN-DTL-082-BE` | `RQ-DTL-082` | 모델명·버전 표시 | CaseController | 🟡 |
| `FN-DTL-083-BE` | `RQ-DTL-083` | 모델별 점수 표시 | CaseController | 🟡 |
| `FN-DTL-084-BE` | `RQ-DTL-084` | PDF 리포트 다운로드 | CaseController | ⬜ |
| `FN-DTL-085-BE` | `RQ-DTL-085` | reportHash 표시 | CaseController | 🟡 |
| `FN-DTL-086-BE` | `RQ-DTL-086` | 검증번호 표시 | CaseController | 🟡 |
| `FN-DTL-087-BE` | `RQ-DTL-087` | QR 정보 표시 | CaseController | 🟡 |
| `FN-DTL-089-BE` | `RQ-DTL-089` | 분석 근거 없음 상태 표시 | CaseController | 🟡 |
| `FN-DTL-090-BE` | `RQ-DTL-090` | 본인 분석 결과만 조회 | CaseController | 🟡 |
| `FN-HIS-106-BE` | `RQ-HIS-106` | 전체 분석 목록 조회 | `controller/` · `service/` | ✅ |
| `FN-HIS-107-BE` | `RQ-HIS-107` | 감사 로그 체인 검증 | `controller/` · `service/` | 🟡 |
| `FN-LOGIN-017-BE` | `RQ-LOGIN-017` | 로그인 페이지 진입 | AuthController | — (FE 주도) |
| `FN-LOGIN-018-BE` | `RQ-LOGIN-018` | 로그인 화면 레이아웃 제공 | AuthController | — (FE 주도) |
| `FN-LOGIN-019-BE` | `RQ-LOGIN-019` | 로그인 정보 입력 | AuthController | — (FE 주도) |
| `FN-LOGIN-020-BE` | `RQ-LOGIN-020` | 로그인 요청 처리 | AuthController | ✅ |
| `FN-LOGIN-021-BE` | `RQ-LOGIN-021` | 승인된 계정만 로그인 허용 | AuthController | ✅ |
| `FN-LOGIN-024-BE` | `RQ-LOGIN-024` | 로그인 세션 저장 | AuthController | 🟡 |
| `FN-LOGIN-026-BE` | `RQ-LOGIN-026` | 회원가입 화면 이동 | AuthController | 🟡 |
| `FN-LOGIN-029-BE` | `RQ-LOGIN-029` | 서버 오류 안내 | AuthController | 🟡 |
| `FN-LOGIN-031-BE` | `RQ-LOGIN-031` | 접속 기록 고지 | AuthController | 🟡 |
| `FN-MY-108-BE` | `RQ-MY-108` | 마이페이지 접근 | UserController | ✅ |
| `FN-MY-109-BE` | `RQ-MY-109` | 내 정보 조회 | UserController | ✅ |
| `FN-MY-111-BE` | `RQ-MY-111` | 비밀번호 변경 | UserController | 🟡 |
| `FN-MY-112-BE` | `RQ-MY-112` | 환경 설정 | UserController | 🟡 |
| `FN-NFR-160-BE` | `RQ-NFR-160` | JWT 기반 인증 | — | 🟡 |
| `FN-NFR-161-BE` | `RQ-NFR-161` | 세션 만료 처리 | — | 🟡 |
| `FN-NFR-162-BE` | `RQ-NFR-162` | 관리자 API 권한 검증 | — | 🟡 |
| `FN-PER-155-BE` | `RQ-PER-155` | 대시보드 로딩 속도 | `controller/` · `service/` | 🟡 |
| `FN-PER-156-BE` | `RQ-PER-156` | 목록 조회 응답 | `controller/` · `service/` | 🟡 |
| `FN-REQ-047-BE` | `RQ-REQ-047` | 영상 파일 업로드 | EvidenceController | ✅ |
| `FN-REQ-048-BE` | `RQ-REQ-048` | 원본 파일 WORM 봉인 | EvidenceController | 🟡 |
| `FN-REQ-049-BE` | `RQ-REQ-049` | 비동기 AI 분석 큐 연동 | EvidenceController | ✅ |
| `FN-REQ-050-BE` | `RQ-REQ-050` | 사본 복제 및 디지털 서명 | EvidenceController | 🟡 |
| `FN-REQ-051-BE` | `RQ-REQ-051` | 감사 로그 해시 체이닝 | EvidenceController | ✅ |
| `FN-REQ-052-BE` | `RQ-REQ-052` | 원본 해시 블록체인 앵커링 | EvidenceController | ⬜ |
| `FN-SEC-151-BE` | `RQ-SEC-151` | 블록체인 머클 트리 앵커링 | — | ⬜ |
| `FN-SEC-152-BE` | `RQ-SEC-152` | 블록체인 기록 불변성 보장 | — | ⬜ |
| `FN-SEC-153-BE` | `RQ-SEC-153` | 서명 유효성 검증 실패 대응 | — | 🟡 |
| `FN-SIGNUP-032-BE` | `RQ-SIGNUP-032` | 회원가입 페이지 진입 | AuthController | 🟡 |
| `FN-SIGNUP-034-BE` | `RQ-SIGNUP-034` | 초대코드 입력·검증 | AuthController | 🟡 |
| `FN-SIGNUP-035-BE` | `RQ-SIGNUP-035` | 필수 입력 검증 | AuthController | 🟡 |
| `FN-SIGNUP-036-BE` | `RQ-SIGNUP-036` | 비밀번호 확인 일치 검증 | AuthController | 🟡 |
| `FN-SIGNUP-037-BE` | `RQ-SIGNUP-037` | 회원가입 요청 처리 | AuthController | ✅ |
| `FN-SIGNUP-039-BE` | `RQ-SIGNUP-039` | 가입 승인 대기 안내 | AuthController | 🟡 |
| `FN-SIGNUP-040-BE` | `RQ-SIGNUP-040` | 가입 완료 후 로그인 이동 | AuthController | 🟡 |

## 3. AI · INF (RTM 요약)

전체 RQ↔FN 매핑은 Excel `RTM` 시트 또는 `_extracted/fn_RTM.json`을 참조하세요.

## 4. 재생성

```bash
python scripts/extract_requirements_from_excel.py
python scripts/generate_requirements_markdown.py
```
