ForenShield AI 프로젝트의 AI 서버 폴더 구조 설계 문서를 먼저 만들어줘.

아직 Python 코드 구현은 하지 마.
아직 requirements.txt도 만들지 마.
아직 FastAPI 실행 코드를 만들지 마.
이번 단계에서는 폴더 구조를 설명하는 Markdown 파일만 생성한다.

생성할 파일은 하나만이다.

ai-server/AI_SERVER_STRUCTURE.md

만약 ai-server 폴더가 없다면 ai-server 폴더만 생성하고, 그 안에 AI_SERVER_STRUCTURE.md 파일만 만들어라.

주의:
- app 폴더는 아직 만들지 마.
- routers 폴더는 아직 만들지 마.
- schemas 폴더는 아직 만들지 마.
- services 폴더는 아직 만들지 마.
- Python 파일은 아직 만들지 마.
- requirements.txt는 아직 만들지 마.
- README.md도 아직 만들지 마.
- 지금은 AI_SERVER_STRUCTURE.md 하나만 만든다.

AI_SERVER_STRUCTURE.md에는 아래 내용을 그대로 작성해라.

# ForenShield AI - AI Server Folder Structure

## 1. 문서 목적

이 문서는 ForenShield AI 프로젝트의 AI 서버 폴더 구조를 정의하기 위한 문서이다.

AI 서버는 Python FastAPI 기반으로 구성한다.

Sprint 1에서는 실제 딥페이크 탐지 모델을 붙이지 않고, 백엔드와 연결 가능한 기본 서버 구조와 Mock 분석 API를 만들기 위한 준비만 한다.

이 문서를 기준으로 다음 단계에서 실제 폴더와 파일을 생성한다.

## 2. AI 서버 역할

AI 서버는 Spring Boot 백엔드로부터 분석 요청을 받아 영상·음성·이미지 파일을 분석하고 결과 JSON을 반환하는 역할을 한다.

Sprint 1에서는 다음 기능까지만 준비한다.

- FastAPI 서버 기본 구조
- /health 상태 확인 API
- /ai/analyze Mock 분석 API
- 요청/응답 JSON 스키마
- Mock 분석 결과 반환 구조
- SHA-256 재검증 유틸 준비

실제 AI 모델 연동은 Sprint 2 이후 진행한다.

## 3. Sprint 1 최소 폴더 구조

다음 구조를 Sprint 1의 최소 AI 서버 구조로 사용한다.

ai-server/
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── core/
│   │   ├── __init__.py
│   │   └── config.py
│   ├── routers/
│   │   ├── __init__.py
│   │   ├── health.py
│   │   └── analyze.py
│   ├── schemas/
│   │   ├── __init__.py
│   │   └── analysis.py
│   ├── services/
│   │   ├── __init__.py
│   │   └── mock_analyzer.py
│   └── utils/
│       ├── __init__.py
│       └── hash_utils.py
├── docs/
│   └── model-candidates.md
├── requirements.txt
├── .gitignore
└── README.md

## 4. 폴더별 역할

### app/

FastAPI 애플리케이션의 실제 코드가 들어가는 최상위 폴더이다.

### app/main.py

FastAPI 앱의 시작점이다.

역할:
- FastAPI 인스턴스 생성
- health router 등록
- analyze router 등록

### app/core/

공통 설정 파일을 관리하는 폴더이다.

Sprint 1에서는 최소 설정만 둔다.

### app/routers/

API 라우터를 관리하는 폴더이다.

포함 파일:
- health.py
- analyze.py

### app/routers/health.py

AI 서버 상태 확인 API를 담당한다.

예상 API:
GET /health

예상 응답:
{
"status": "ok",
"service": "forenshield-ai"
}

### app/routers/analyze.py

AI 분석 요청 API를 담당한다.

Sprint 1에서는 실제 모델 분석이 아니라 Mock 분석 결과를 반환한다.

예상 API:
POST /ai/analyze

### app/schemas/

백엔드와 AI 서버가 주고받는 요청/응답 JSON 스키마를 정의하는 폴더이다.

포함 파일:
- analysis.py

정의할 스키마:
- AnalysisRequest
- AnalysisResponse

### app/services/

비즈니스 로직 또는 분석 로직을 관리하는 폴더이다.

Sprint 1에서는 실제 모델 대신 Mock 분석 결과를 반환하는 mock_analyzer.py만 둔다.

### app/services/mock_analyzer.py

Mock 분석 결과를 생성하는 서비스이다.

역할:
- 요청받은 fileType 확인
- 임시 rawScore 반환
- 임시 confidence 반환
- 임시 evidence 반환
- modelName, modelVersion 반환

### app/utils/

공통 유틸 함수를 관리하는 폴더이다.

### app/utils/hash_utils.py

SHA-256 계산 유틸을 관리한다.

최종 구조에서는 GPU Worker가 S3 원본 파일을 임시 다운로드한 뒤, 백엔드가 전달한 originalSha256과 비교할 때 사용한다.

Sprint 1에서는 함수 구조만 준비한다.

### docs/

AI 서버 관련 문서를 보관하는 폴더이다.

### docs/model-candidates.md

Sprint 2에서 포팅할 모델 후보를 정리하는 문서이다.

대상:
- 영상 Face Swap 탐지
- 음성 합성/TTS 탐지
- 이미지 조작/생성형 AI 탐지
- STT
- 화자 분리
- 화자 검증

### requirements.txt

AI 서버 실행에 필요한 Python 패키지 목록이다.

Sprint 1 최소 패키지 후보:
- fastapi
- uvicorn
- pydantic
- requests

### .gitignore

Git에 올리지 않을 파일을 정의한다.

포함 예정:
- .venv/
- __pycache__/
- *.pyc
- .env
- .idea/
- .vscode/

### README.md

AI 서버 실행 방법과 API 테스트 방법을 정리한다.

## 5. Sprint 1에서 만들 API

### GET /health

AI 서버가 살아 있는지 확인하는 API이다.

예상 응답:
{
"status": "ok",
"service": "forenshield-ai"
}

### POST /ai/analyze

백엔드와 연동 테스트를 위한 Mock 분석 API이다.

요청 예시:
{
"analysisRequestId": 101,
"fileId": 15,
"caseId": 3,
"fileType": "video",
"s3ObjectKey": "original-files/3/15/original.mp4",
"presignedDownloadUrl": "https://example.com/mock",
"originalSha256": "abc123"
}

응답 예시:
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

## 6. Sprint 2 이후 확장 구조

Sprint 2 이후 실제 모델을 붙일 때는 아래 폴더를 추가한다.

ai-server/
├── app/
│   ├── preprocessing/
│   │   ├── video_preprocessor.py
│   │   ├── audio_preprocessor.py
│   │   └── image_preprocessor.py
│   ├── inference/
│   │   ├── video_detector.py
│   │   ├── audio_detector.py
│   │   └── image_detector.py
│   ├── models/
│   │   ├── model_loader.py
│   │   └── model_registry.py
│   ├── storage/
│   │   └── s3_downloader.py
│   ├── callbacks/
│   │   └── result_callback.py
│   └── workers/
│       └── rabbitmq_consumer.py

## 7. Sprint 2 이후 확장 폴더 역할

### preprocessing/

영상·음성·이미지 파일을 모델 입력 형태로 변환한다.

역할:
- 영상 프레임 추출
- 음성 샘플레이트 변환
- 이미지 리사이즈 및 정규화

### inference/

실제 딥페이크 탐지 모델을 실행한다.

역할:
- 영상 딥페이크 탐지
- 음성 합성 탐지
- 이미지 조작 탐지

### models/

모델 로딩과 모델 버전 정보를 관리한다.

역할:
- 모델 파일 로딩
- modelName 관리
- modelVersion 관리

### storage/

S3 원본 파일 다운로드 기능을 담당한다.

역할:
- presignedDownloadUrl 기반 파일 다운로드
- /tmp/job-{analysisRequestId}/evidence.ext 저장

### callbacks/

AI 분석 결과를 Spring Boot 백엔드로 반환한다.

역할:
- 분석 성공 결과 반환
- 분석 실패 결과 반환

### workers/

RabbitMQ 메시지를 소비하는 Worker를 관리한다.

역할:
- analysis_queue 메시지 수신
- 분석 요청 처리
- 실패 시 DLQ 연계 검토

## 8. 지금 단계에서 만들지 않는 것

이번 단계에서는 아래 항목을 만들지 않는다.

- 실제 Python 코드
- FastAPI 실행 코드
- 실제 폴더 구조
- requirements.txt
- README.md
- S3 다운로드 코드
- RabbitMQ Consumer
- PyTorch 모델 코드
- GPU 추론 코드

이 문서는 다음 단계에서 실제 폴더와 파일을 만들기 위한 기준 문서이다.