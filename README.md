# ForenShield AI

> 영상 증거 등록부터 AI 분석, 검토·승인, 보고서 발급 및 외부 검증까지 연결하는 딥페이크 포렌식 플랫폼

**개발 기간: 약 2개월 반 · Team Project**

[Notion Portfolio](TODO) · [Tech Blog](TODO)

<!-- TODO: 대표 이미지 -->

ForenShield AI는 단순히 영상의 딥페이크 여부를 판별하는 데서 끝나지 않고,  
영상이 증거로 등록된 이후 분석·검토·보고서 발급·외부 검증까지 이어지는  
업무 흐름을 하나의 플랫폼으로 구현한 프로젝트입니다.

---

## Problem

딥페이크 탐지 기술이 발전해도 실제 포렌식 업무는  
탐지 결과만으로 끝나지 않습니다.

| Problem | ForenShield AI |
|---|---|
| 분석과 검토 과정이 사람 중심으로 분리되어 있음 | AI 분석부터 검토·승인까지 하나의 workflow로 연결 |
| 원본 파일의 변경 여부와 처리 이력을 확인해야 함 | SHA-256과 CoC 기반으로 증거 처리 과정을 기록 |
| 분석·검토·보고서 발급 과정이 서로 분리되어 있음 | 사건 등록부터 보고서 발급까지 하나의 플랫폼에서 관리 |

---

## 핵심 기능

### 사건 · 증거 관리

<!-- TODO: 사건·증거 관리 기능 이미지 -->

영상 증거를 사건 단위로 등록하고 분석 상태와 처리 이력을 관리합니다.

### AI 분석

<!-- TODO: AI 분석 기능 이미지 -->

장시간 분석 작업의 상태를 추적하고 완료 후 상세 분석 결과를 제공합니다.

### 검토 · 승인

<!-- TODO: 검토·승인 기능 이미지 -->

검토 요청, 검토자 배정, 승인·보완 요청까지 역할 기반 workflow를 제공합니다.

### 보고서 · 외부 검증

<!-- TODO: 보고서·외부 검증 기능 이미지 -->

분석 결과를 PDF로 발행하고 QR 기반 발행 정보 조회와 PDF SHA-256 비교를 제공합니다.

---

## System Architecture

<!-- TODO: 시스템 아키텍처 이미지 -->

```text
Next.js → Spring Boot → PostgreSQL / S3 / RabbitMQ → AI Worker
```

위 구성은 팀 프로젝트 전체 시스템 아키텍처입니다.

---

## Tech Stack

### 📱 Frontend

![Next.js](https://img.shields.io/badge/Next.js-000000?style=for-the-badge&logo=nextdotjs&logoColor=white)
![React](https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white)

### 💾 Backend

![Java 17](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white)

### 🗄 Data & Messaging

![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-FF4438?style=for-the-badge&logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)
![Amazon S3](https://img.shields.io/badge/Amazon_S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white)

### 🔃 DevOps

![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazonwebservices&logoColor=white)

### 🤖 AI / Media

![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![FFmpeg](https://img.shields.io/badge/FFmpeg-007808?style=for-the-badge&logo=ffmpeg&logoColor=white)

---

## 서비스 구현 화면

### 01. 사건 및 영상 증거 등록

<!-- TODO: 사건·증거 등록 화면 -->

사건을 생성하고 분석할 영상 증거를 등록합니다.

### 02. AI 분석 및 결과 조회

<!-- TODO: 분석 결과 화면 -->

분석 진행 상태를 확인하고 완료 후 상세 분석 결과를 조회합니다.

### 03. 검토 및 승인

<!-- TODO: 검토 화면 -->

분석 결과를 검토하고 승인 또는 보완 요청을 처리합니다.

### 04. PDF 보고서

<!-- TODO: PDF 보고서 화면 -->

승인된 분석 결과를 PDF 보고서로 발행합니다.

### 05. QR / PDF 검증

<!-- TODO: QR 검증 화면 -->

QR로 보고서 발행 정보를 조회하고 보유한 PDF의 SHA-256을 비교합니다.

---

## Problem Solving

### 01. 분석 결과를 보호하는 상태 정책

**Problem**

완료·실패한 분석 요청까지 취소 처리될 수 있어  
이미 생성된 분석 결과의 상태가 변경될 가능성이 있었습니다.

**Solution**

취소 가능한 상태를 `QUEUED`, `ANALYZING`으로 제한하는 whitelist 방식으로 변경하고,  
MVC 테스트를 통해 terminal 상태의 분석 결과가 유지되는 것을 검증했습니다.

→ 자세한 과정: Tech Blog / Notion `(TODO)`

### 02. 역할을 넘어 자원 범위까지 검증

**Problem**

검토자 배정 과정에서 역할과 기관만 확인하면  
다른 부서의 검토자가 사건에 배정될 수 있었습니다.

**Solution**

동일 기관·동일 부서 조건을 공통 권한 로직으로 적용하고,  
다른 부서 검토자 배정을 차단하는 통합 테스트를 추가했습니다.

→ 자세한 과정: Tech Blog / Notion `(TODO)`

### 03. 보고서 조회와 파일 무결성 검증의 의미 분리

**Problem**

QR 조회 결과가 사용자가 보유한 PDF 파일의 무결성까지  
보장하는 것처럼 해석될 수 있었습니다.

**Solution**

QR은 ‘시스템에 등록된 보고서 발행 정보 조회’로 정의하고,  
사용자가 보유한 PDF는 별도의 SHA-256 비교를 통해  
MATCH / MISMATCH를 확인하도록 흐름을 분리했습니다.

→ 자세한 과정: Tech Blog / Notion `(TODO)`

---

## Links

- [Notion Portfolio](TODO)
- [Tech Blog](TODO)
- [Demo](TODO)
