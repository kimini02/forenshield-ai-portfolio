# 🪣 Amazon S3 저장소 정책 및 구조

증거 데이터의 사법적 무결성 보호와 안전한 내부 인프라 통신을 위한 정책입니다.

## 1. 버킷 보안 정책 (S3 Security)

### A. forenshield-evidence (증거 전용)
*   **Object Lock (WORM)**: `original/` 경로에 Compliance Mode의 Object Lock을 적용하여 법적 보존 기간 내 수정/삭제를 원천 차단합니다.
*   **VPC Endpoint**: 인터넷 게이트웨이를 통하지 않고 AWS 내부망을 통해 백엔드 및 AI 워커와 통신합니다.
*   **IRSA (IAM Roles for Service Accounts)**: 쿠버네티스 서비스 어카운트에 IAM Role을 직접 부여하여 Access Key 노출 없이 최소 권한으로 접근합니다.

### B. forenshield-models (AI 모델 전용)
*   **Versioning**: AI 추론 모델 가중치 파일의 버전 관리를 활성화하여 모델 업데이트 및 롤백을 안전하게 관리합니다.
*   **AI Worker Access Control**: 특정 AI 워커 노드 그룹의 IAM 역할만 Read-Only로 접근 가능하도록 버킷 정책을 제한합니다.

## 2. 버킷 디렉토리 구조

```text
forenshield-evidence/
└── cases/
    └── {case_id}/
        └── {file_id}/
            ├── original/    ← [WORM 적용] 사법적 원본 보관
            ├── copy/        ← [분석용] AI 워커 다운로드 및 전처리 대상
            └── reports/     ← 분석 결과 보고서 (PDF/JSON)
```
