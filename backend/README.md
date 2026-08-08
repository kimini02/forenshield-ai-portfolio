# Demo Application

Spring Boot를 기반으로 한 파일 업로드 및 증거 관리 시스템 프로토타입입니다.

## 🛠 Tech Stack

- **Framework**: Spring Boot 3.5.14
- **Language**: Java 17
- **Database**: H2 (File-based), PostgreSQL (Runtime 지원)
- **AI Integration**: Spring AI (Vector Store 지원 예정)
- **Build Tool**: Gradle

## 🚀 시작하기

### 필수 요구 사항

- JDK 17 이상
- Gradle (또는 내장 gradlew 사용)

### 실행 방법

```bash
./gradlew bootRun
```

애플리케이션이 시작되면 `http://localhost:8080`에서 접근 가능합니다.

## 📂 프로젝트 구조

- `src/main/java/com/example/demo/controller`: API 컨트롤러 (증거 업로드 등)
- `src/main/java/com/example/demo/service`: 비즈니스 로직 (파일 저장 처리 등)
- `src/main/java/com/example/demo/dto`: 데이터 전송 객체
- `uploads/original`: 업로드된 파일이 저장되는 디렉토리

## 🔌 API Endpoints

### 파일 업로드

- **URL**: `/api/evidences/upload`
- **Method**: `POST`
- **Parameters**: `file` (MultipartFile)
- **Response**: `FileUploadResponse` 또는 `ErrorResponse`

## ⚙️ 설정

주요 설정은 `src/main/resources/application.yaml`에서 관리됩니다.
- 업로드 경로: `file.upload-dir` (기본: `uploads/original`)
- 데이터베이스: H2 파일 DB (경로: `./data/demo`)
