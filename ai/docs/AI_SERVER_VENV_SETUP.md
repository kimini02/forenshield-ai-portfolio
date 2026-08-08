# ForenShield AI - AI Server 가상환경 및 실행 체크리스트

## 1. 문서 목적

이 문서는 `ai-forensic` AI 서버 프로젝트를 로컬 환경에서 실행하기 위한 가상환경 생성 및 서버 실행 절차를 정리한 문서이다.

현재 Sprint 1 범위에서는 실제 딥페이크 탐지 모델을 실행하지 않고, FastAPI 기반 AI 서버가 정상적으로 실행되는지와 Mock 분석 API가 정상 응답하는지 확인한다.

---

## 2. 현재 프로젝트 상태

현재 AI 서버 프로젝트에는 다음 구조가 생성되어 있다.

```text
ai-forensic/
├── app/
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
├── requirements.txt
├── .env.example
├── .gitignore
└── README.md
```

현재 구현된 API는 다음 두 개이다.

```text
GET /health
POST /ai/analyze
```

## 3. 이번 단계에서 해야 할 일

이번 단계의 목표는 FastAPI 서버를 로컬에서 실행하고 Mock API가 정상 동작하는지 확인하는 것이다.

체크리스트는 다음과 같다.

1. 프로젝트 위치로 이동
2. Python 가상환경 생성
3. 가상환경 활성화
4. requirements.txt 패키지 설치
5. FastAPI 서버 실행
6. /health API 테스트
7. /ai/analyze Mock API 테스트
8. 테스트 결과 확인
9. Git에 올리면 안 되는 파일 확인

## 4. 프로젝트 위치로 이동

터미널에서 AI 서버 프로젝트 위치로 이동한다.

```bash
cd /Users/kimmini/sk-final-deepfake/ai-forensic
```

현재 위치가 맞는지 확인한다.

```bash
pwd
```

예상 위치:

```text
/Users/kimmini/sk-final-deepfake/ai-forensic
```

## 5. Python 버전 확인

Python 버전을 확인한다.

```bash
python3 --version
```

권장 버전:

```text
Python 3.10 이상
Python 3.11 권장
```

## 6. 가상환경 생성

프로젝트 루트에서 가상환경을 생성한다.

```bash
python3 -m venv .venv
```

생성 후 프로젝트 폴더 안에 `.venv` 폴더가 생기면 정상이다.

```text
ai-forensic/
└── .venv/
```

## 7. 가상환경 활성화

macOS 또는 Linux 기준:

```bash
source .venv/bin/activate
```

활성화되면 터미널 앞에 `(.venv)`가 표시된다.

예시:

```text
(.venv) kimmini@MacBook ai-forensic %
```

Windows 기준:

```bat
.venv\Scripts\activate
```

## 8. 패키지 설치

가상환경이 활성화된 상태에서 패키지를 설치한다.

```bash
pip install -r requirements.txt
```

설치 후 패키지 목록을 확인한다.

```bash
pip list
```

최소한 다음 패키지가 보여야 한다.

```text
fastapi
uvicorn
pydantic
python-dotenv
requests
```

## 9. FastAPI 서버 실행

다음 명령어로 서버를 실행한다.

```bash
uvicorn app.main:app --reload --port 8000
```

정상 실행 시 다음과 비슷한 메시지가 출력된다.

```text
Uvicorn running on http://127.0.0.1:8000
```

## 10. Swagger 문서 확인

브라우저에서 아래 주소에 접속한다.

```text
http://localhost:8000/docs
```

Swagger UI가 열리면 FastAPI 서버가 정상 실행 중이다.

## 11. /health API 테스트

브라우저 또는 curl로 테스트한다.

```bash
curl http://localhost:8000/health
```

예상 응답:

```json
{
  "status": "ok",
  "service": "forenshield-ai"
}
```

이 응답이 나오면 `/health` API는 정상이다.

## 12. /ai/analyze Mock API 테스트

다음 명령어로 Mock 분석 API를 테스트한다.

```bash
curl -X POST "http://localhost:8000/ai/analyze" \
  -H "Content-Type: application/json" \
  -d '{
    "analysisRequestId": 101,
    "fileId": 15,
    "caseId": 3,
    "fileType": "video",
    "s3ObjectKey": "original-files/3/15/original.mp4",
    "presignedDownloadUrl": "https://example.com/presigned-url",
    "originalSha256": "abc123...",
    "requestedAt": "2026-06-10T10:30:00"
  }'
```

예상 응답:

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

## 13. 테스트 성공 기준

다음 항목이 모두 확인되면 이번 단계는 완료이다.

- 가상환경이 생성된다.
- 가상환경이 정상 활성화된다.
- requirements.txt 패키지 설치가 완료된다.
- FastAPI 서버가 8000번 포트에서 실행된다.
- http://localhost:8000/docs 접속이 가능하다.
- GET /health API가 정상 응답한다.
- POST /ai/analyze API가 Mock 분석 결과를 반환한다.

## 14. Git에 올리면 안 되는 파일

다음 파일과 폴더는 Git에 올리면 안 된다.

```text
.venv/
.env
__pycache__/
*.pyc
.idea/
.vscode/
.DS_Store
```

`.gitignore`에 위 항목이 포함되어 있는지 확인한다.

```bash
cat .gitignore
```

## 15. Git 상태 확인

작업 후 Git 상태를 확인한다.

```bash
git status
```

정상적으로는 다음 파일만 변경 또는 추가되어야 한다.

```text
docs/AI_SERVER_VENV_SETUP.md
```

`.venv/`, `.idea/`, `__pycache__/` 등이 Git에 잡히면 안 된다.

## 16. 다음 단계

이 단계가 완료되면 다음 작업으로 넘어간다.

1. README 실행 방법 보완
2. 백엔드 담당자에게 AI 서버 Base URL 공유
3. /ai/analyze 요청/응답 JSON 백엔드 명세와 비교
4. Sprint 2 모델 후보 정리
5. 실제 모델 포팅 준비

백엔드 담당자에게 공유할 내용은 다음과 같다.

```text
AI Server Base URL:
http://localhost:8000

Health Check:
GET /health

Mock Analyze:
POST /ai/analyze

현재는 실제 모델 없이 Mock 분석 결과를 반환한다.
Sprint 2에서 실제 영상·음성·이미지 모델을 연동할 예정이다.
```

## 17. 완료 기록

완료 후 아래 항목을 체크한다.

- [ ] python3 --version 확인
- [ ] python3 -m venv .venv 실행
- [ ] source .venv/bin/activate 실행
- [ ] pip install -r requirements.txt 실행
- [ ] uvicorn app.main:app --reload --port 8000 실행
- [ ] http://localhost:8000/docs 접속 확인
- [ ] GET /health 응답 확인
- [ ] POST /ai/analyze 응답 확인
- [ ] git status 확인
- [ ] .venv, .env, .idea, __pycache__가 Git에 잡히지 않는지 확인
