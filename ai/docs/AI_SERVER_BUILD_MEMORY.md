# ForenShield AI - AI Server Build Memory

## 1. 문서 목적

이 문서는 `ai-forensic` AI 서버 프로젝트를 어떤 기준으로 생성했는지 기록하는 기억 저장소이다.

나중에 프로젝트를 다시 확인하거나 Sprint 2 작업을 이어갈 때, 어떤 파일을 왜 만들었는지 빠르게 파악하기 위해 작성한다.

## 2. 프로젝트 위치

현재 AI 서버 프로젝트 위치는 다음과 같다.

```text
/Users/kimmini/sk-final-deepfake/ai-forensic
```

프로젝트 이름은 기존 백엔드 프로젝트명 `backend-forensic`에서 `backend`를 `ai`로 바꾼 `ai-forensic`으로 정했다.

## 3. 기준 문서

AI 서버 구조는 아래 문서들을 기준으로 생성했다.

```text
docs/AI_SERVER_CONTEXT.md
docs/AI_SERVER_STRUCTURE.md
```

`AI_SERVER_CONTEXT.md`에는 ForenShield AI 전체 목적, AI 서버 역할, 백엔드와의 연결 방식, Sprint 1 범위가 정리되어 있다.

`AI_SERVER_STRUCTURE.md`에는 Sprint 1 최소 파일 구조, 파일별 역할, API 명세, Sprint 2 이후 확장 구조가 정리되어 있다.

## 4. 생성한 Sprint 1 구조

실제로 생성한 Sprint 1 구조는 다음과 같다.

```text
ai-forensic/
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── core/
│   │   └── config.py
│   ├── routers/
│   │   ├── health.py
│   │   └── analyze.py
│   ├── schemas/
│   │   └── analysis.py
│   ├── services/
│   │   └── mock_analyzer.py
│   └── utils/
│       └── hash_utils.py
├── docs/
│   ├── AI_SERVER_CONTEXT.md
│   ├── AI_SERVER_STRUCTURE.md
│   └── AI_SERVER_BUILD_MEMORY.md
├── requirements.txt
├── .env.example
├── .gitignore
└── README.md
```

IntelliJ IDEA가 프로젝트를 열면서 `.idea/` 폴더를 자동 생성했다. `.idea/`는 `.gitignore`에 포함했다.

## 5. 파일별 생성 이유

`app/main.py`

- FastAPI 앱 인스턴스를 생성한다.
- `health`, `analyze` 라우터를 등록한다.

`app/core/config.py`

- `.env` 파일을 로딩한다.
- `AI_SERVER_NAME`, `AI_SERVER_PORT`, `BACKEND_RESULT_API_URL`, `RABBITMQ_URL` 설정값을 관리한다.

`app/routers/health.py`

- `GET /health` API를 담당한다.
- 서버 상태 확인용으로 `{"status":"ok","service":"forenshield-ai"}` 형태의 JSON을 반환한다.

`app/routers/analyze.py`

- `POST /ai/analyze` API를 담당한다.
- 요청을 `AnalysisRequest`로 검증하고 `mock_analyzer`를 호출한다.

`app/schemas/analysis.py`

- 백엔드와 주고받을 분석 요청/응답 JSON 스키마를 정의한다.
- `fileType`은 Sprint 1 기준으로 `video`, `audio`, `image`만 허용한다.

`app/services/mock_analyzer.py`

- 실제 모델 없이 고정된 Mock 분석 결과를 반환한다.
- Sprint 1 백엔드 연동 테스트용이다.

`app/utils/hash_utils.py`

- SHA-256 계산 함수를 제공한다.
- Sprint 2 이후 S3에서 임시 다운로드한 원본 파일의 무결성 재검증에 사용할 예정이다.

`requirements.txt`

- Sprint 1 실행에 필요한 최소 패키지를 기록했다.
- `fastapi`, `uvicorn[standard]`, `pydantic`, `python-dotenv`, `requests`만 포함했다.

`.env.example`

- 로컬 실행에 필요한 환경변수 예시를 제공한다.
- 실제 `.env` 파일은 Git에 올리지 않는다.

`.gitignore`

- `.venv/`, `.env`, `__pycache__/`, `*.pyc`, `.idea/`, `.vscode/`, `.DS_Store`를 제외한다.

`README.md`

- 가상환경 생성, 패키지 설치, 서버 실행, API 테스트 방법을 정리했다.

## 6. 현재 구현된 API

Sprint 1에서 구현된 API는 다음 두 개이다.

```http
GET /health
```

응답:

```json
{
  "status": "ok",
  "service": "forenshield-ai"
}
```

```http
POST /ai/analyze
```

요청 예시:

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

응답 예시:

```json
{
  "analysisRequestId": 101,
  "fileId": 15,
  "fileType": "video",
  "status": "MOCK_ANALYSIS_COMPLETED",
  "rawScore": 0.75,
  "confidence": 70,
  "evidence": [
    "Mock 분석 결과입니다. 실제 모델은 Sprint 2 연동 예정."
  ],
  "modelName": "mock-deepfake-detector",
  "modelVersion": "v0.1"
}
```

## 7. 생성하지 않은 것

Sprint 1 범위를 넘지 않기 위해 다음 항목은 만들지 않았다.

- 실제 딥페이크 탐지 모델
- PyTorch 모델 로딩
- OpenCV 분석
- Librosa 분석
- STT
- 화자 분리
- 화자 검증
- RabbitMQ Consumer
- S3 다운로드 실구현
- Spring Boot Result API 호출 실구현
- GPU 추론
- Sprint 2 확장 폴더인 `preprocessing/`, `inference/`, `models/`, `storage/`, `callbacks/`, `workers/`

## 8. 검증 기록

Python 문법 검증은 다음 명령으로 수행했다.

```bash
python3 -m compileall app
```

검증 결과 모든 Python 파일이 정상 컴파일되었다.

컴파일 과정에서 생성된 `__pycache__` 폴더는 정리했다.

## 9. 가상환경 및 API 실행 검증 기록

`docs/AI_SERVER_VENV_SETUP.md` 문서를 기준으로 로컬 실행 검증을 진행했다.

프로젝트 위치:

```text
/Users/kimmini/sk-final-deepfake/ai-forensic
```

확인한 Python 버전:

```text
Python 3.12.2
```

수행한 작업은 다음과 같다.

```text
1. docs/AI_SERVER_VENV_SETUP.md 내용 확인
2. 프로젝트 위치 확인
3. python3 --version 확인
4. python3 -m venv .venv 실행
5. .venv/bin/pip install -r requirements.txt 실행
6. .venv/bin/pip list로 설치 패키지 확인
7. .venv/bin/uvicorn app.main:app --reload --port 8000 실행 시도
8. reload 권한 문제로 실패 확인
9. .venv/bin/uvicorn app.main:app --port 8000으로 서버 실행
10. /docs 응답 확인
11. GET /health 응답 확인
12. POST /ai/analyze Mock API 응답 확인
13. 테스트 완료 후 Uvicorn 서버 종료
14. 생성된 __pycache__ 폴더 정리
```

패키지 설치 결과 다음 주요 패키지가 정상 설치되었다.

```text
fastapi
uvicorn
pydantic
python-dotenv
requests
```

`--reload` 실행은 다음 문제로 실패했다.

```text
ERROR: [Errno 1] Operation not permitted
```

이 문제는 Uvicorn reload 모드가 파일 변경 감시를 시도하면서 발생한 권한 문제로 판단했다. API 동작 검증은 reload 없이 아래 명령으로 진행했다.

```bash
.venv/bin/uvicorn app.main:app --port 8000
```

서버는 다음 주소에서 정상 실행되었다.

```text
http://127.0.0.1:8000
```

Swagger 문서 확인:

```text
HEAD /docs -> 200 OK
```

`GET /health` 테스트 결과:

```json
{
  "status": "ok",
  "service": "forenshield-ai"
}
```

`POST /ai/analyze` 테스트 요청:

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

`POST /ai/analyze` 테스트 응답:

```json
{
  "analysisRequestId": 101,
  "fileId": 15,
  "fileType": "video",
  "status": "MOCK_ANALYSIS_COMPLETED",
  "rawScore": 0.75,
  "confidence": 70,
  "evidence": [
    "Mock 분석 결과입니다. 실제 모델은 Sprint 2 연동 예정."
  ],
  "modelName": "mock-deepfake-detector",
  "modelVersion": "v0.1"
}
```

테스트 결과 Uvicorn 로그 기준으로 다음 요청들이 모두 정상 처리되었다.

```text
HEAD /docs -> 200 OK
GET /health -> 200 OK
POST /ai/analyze -> 200 OK
```

테스트 후 서버는 종료했다.

검증 과정에서 생성된 `__pycache__` 폴더는 정리했다.

현재 `.venv/`는 생성되어 있으며 `.gitignore`에 포함되어 있다.

현재 `ai-forensic` 폴더는 아직 Git 저장소가 아니므로 `git status`는 다음 오류로 확인되지 않았다.

```text
fatal: not a git repository (or any of the parent directories): .git
```
