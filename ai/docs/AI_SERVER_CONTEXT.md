# ForenShield AI - AI Server Context

## 1. 프로젝트 개요

ForenShield AI는 공공기관/수사기관용 딥페이크 포렌식 분석 보조 시스템이다.

사용자가 영상, 음성, 이미지 파일을 업로드하면 시스템은 다음 흐름을 수행한다.

- 원본 파일 업로드
- 원본 파일 SHA-256 해시 생성
- AWS S3 forensic-evidence에 원본 파일 보존
- PostgreSQL에 파일 정보와 해시값 저장
- Chain of Custody 로그 기록
- RabbitMQ를 통한 비동기 분석 요청
- On-Prem GPU Worker에서 AI 분석 수행
- 분석 결과 JSON 반환
- Spring Boot 백엔드가 분석 결과 저장
- PDF 보고서 즉시 생성 및 다운로드

## 2. 전체 시스템 역할 분리

Spring Boot 백엔드는 다음 역할을 담당한다.

- 사용자 인증
- 파일 업로드 API
- 파일 형식 검증
- SHA-256 해시 생성
- AWS S3 원본 저장
- PostgreSQL 저장
- Chain of Custody 로그 저장
- RabbitMQ 분석 요청 발행
- AI 분석 결과 저장
- PDF 보고서 생성 및 다운로드

AI 서버는 다음 역할을 담당한다.

- 분석 요청 수신
- S3 원본 파일을 임시 다운로드할 준비
- 다운로드 파일 SHA-256 재검증
- 영상, 음성, 이미지 전처리
- 딥페이크 및 위변조 탐지 모델 추론
- 분석 결과 JSON 생성
- Spring Boot 결과 저장 API로 결과 반환
- 임시 분석 파일 삭제

AI 서버는 다음 역할을 하지 않는다.

- 사용자 인증
- 원본 파일 영구 저장
- PostgreSQL 직접 저장
- Chain of Custody 로그 직접 저장
- PDF 생성
- 관리자 승인 기능

## 3. AI 서버 위치

AI 서버는 Python FastAPI 기반으로 만든다.

최종 구조에서는 On-Prem RTX 5080 GPU Worker 서버 안에서 FastAPI Worker가 실행된다.

GPU Worker는 백엔드가 발급한 presignedDownloadUrl을 사용하여 S3 원본 파일을 임시 다운로드한다.

다운로드 경로 예시는 다음과 같다.

```text
/tmp/job-{analysisRequestId}/evidence.ext
```

분석이 완료되거나 실패하면 `/tmp/job-{analysisRequestId}` 디렉터리는 삭제한다.

## 4. 파일 처리 정책

원본 파일은 AWS S3 forensic-evidence에 보존한다.

분석용 복사본은 S3에 따로 저장하지 않는다.

AI/GPU Worker가 분석 요청 시점에만 원본 파일을 임시 다운로드하여 분석한다.

즉, 파일 저장 정책은 다음과 같다.

- 원본 파일: S3 forensic-evidence에 보존
- 분석용 복사본: GPU 서버 `/tmp/job-{analysisRequestId}`에 임시 생성 후 삭제
- PDF 보고서: Spring Boot가 요청 시 즉시 생성하여 다운로드
- AI 모델 파일: Sprint 2 이후 S3 model bucket 또는 GPU 서버 local `/models`에서 관리 예정

## 5. 백엔드와 AI 서버 통신 방식

Sprint 1에서는 실제 RabbitMQ Consumer를 구현하지 않는다.

Sprint 1에서는 백엔드 연동을 준비하기 위해 HTTP Mock API를 먼저 만든다.

필요한 API는 다음과 같다.

```http
GET /health
```

- AI 서버 상태 확인

```http
POST /ai/analyze
```

- 실제 모델 없이 Mock 분석 결과 반환

Sprint 2 이후에는 RabbitMQ analysis_queue를 소비하는 Worker 구조로 확장한다.

## 6. AI 분석 요청 JSON 초안

백엔드가 AI 서버에 보낼 요청 JSON은 다음 형태를 기준으로 한다.

```json
{
  "analysisRequestId": 101,
  "fileId": 15,
  "caseId": 3,
  "fileType": "video",
  "s3ObjectKey": "original-files/3/15/original.mp4",
  "presignedDownloadUrl": "https://example.com/presigned-url",
  "originalSha256": "abc123...",
  "requestedAt": "2026-06-10T10:30:00"
}
```

중요 필드는 다음과 같다.

- analysisRequestId
- fileId
- fileType
- presignedDownloadUrl
- originalSha256

## 7. AI 분석 응답 JSON 초안

Sprint 1 Mock 응답은 다음 형태를 기준으로 한다.

```json
{
  "analysisRequestId": 101,
  "fileId": 15,
  "fileType": "video",
  "status": "MOCK_ANALYSIS_COMPLETED",
  "rawScore": 0.75,
  "confidence": 70,
  "evidence": [
    "Mock 분석 결과입니다. 실제 딥페이크 탐지 모델은 Sprint 2에서 연동 예정입니다."
  ],
  "modelName": "mock-deepfake-detector",
  "modelVersion": "v0.1"
}
```

## 8. Sprint 1 AI 서버 범위

Sprint 1에서 AI 담당자가 해야 할 범위는 다음과 같다.

- AI 서버 프로젝트 방향 정리
- FastAPI 서버 구조 준비
- `/health` API 구현 예정
- `/ai/analyze` Mock API 구현 예정
- 요청/응답 JSON 스키마 준비
- GPU Worker 처리 흐름 정리
- Sprint 2에서 포팅할 모델 후보 정리

Sprint 1에서 하지 않는 것:

- 실제 딥페이크 모델 구현
- PyTorch 모델 연동
- RabbitMQ Consumer 구현
- S3 다운로드 실제 구현
- GPU 추론 구현
- PDF 생성
- DB 저장

## 9. Sprint 2 이후 확장 예정

Sprint 2 이후에는 다음 기능을 추가한다.

- 영상 Face Swap 탐지 모델 포팅
- 음성 합성/TTS 탐지 모델 포팅
- 이미지 조작/생성형 AI 탐지 모델 포팅
- STT 변환
- 화자 분리
- 화자 검증
- S3 파일 다운로드
- SHA-256 재검증
- GPU 추론
- Spring Boot 결과 저장 API 호출

## 10. 현재 결정한 아키텍처 선택 이유

이 구조를 선택한 이유는 다음과 같다.

- 원본 파일은 S3에 보존하여 포렌식 무결성을 유지한다.
- 분석은 GPU 서버의 임시 복사본으로 수행하여 원본 오염을 방지한다.
- GPU 서버에는 AWS Access Key를 직접 두지 않고 presigned URL 방식으로 파일을 받는다.
- AI 서버는 분석만 담당하고, DB 저장과 CoC 로그는 Spring Boot가 중앙에서 관리한다.
- Sprint 1에서는 실제 모델보다 백엔드와 통신 가능한 Mock API를 먼저 만든다.
