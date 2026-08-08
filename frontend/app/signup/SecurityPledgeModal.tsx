// 담당: 김민희
// 역할: 증거자료 보안 서약 전문 모달 (공통 AgreementModal 재사용)
"use client"

import AgreementModal from "./AgreementModal"

const PLEDGE_CLAUSES = [
  "본인은 ForenShield AI를 승인된 업무 목적 내에서만 사용합니다.",
  "본인은 업로드된 증거 파일, 분석 결과, 보고서, 로그 정보를 외부로 무단 반출하지 않습니다.",
  "본인은 타인의 계정을 사용하거나 계정 정보를 공유하지 않습니다.",
  "본인은 분석 결과를 임의로 조작하거나 삭제하지 않습니다.",
  "본인은 Chain of Custody 로그 및 시스템 감사 기록이 남는 것에 동의합니다.",
  "본인은 개인정보 및 수사 관련 민감 정보를 내부 보안 규정에 따라 처리합니다.",
  "본인은 위반 시 계정 제한 및 내부 절차에 따른 조치가 있을 수 있음을 확인합니다.",
]

export default function SecurityPledgeModal({
  open,
  onClose,
}: {
  open: boolean
  onClose: () => void
}) {
  return <AgreementModal open={open} title="보안 서약서" clauses={PLEDGE_CLAUSES} onClose={onClose} />
}
