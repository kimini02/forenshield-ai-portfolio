package com.example.demo.service.dashboard;

import com.example.demo.dto.DashboardIntroResponse;
import com.example.demo.dto.DashboardShortcutDto;
import com.example.demo.dto.DashboardTrustHighlightDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DashboardIntroService {

    /** RQ-DSH-041 / SK-840: 메인 대시보드 히어로·바로가기 카드 (FE dashboard-overview HeroPanel과 동기) */
    public DashboardIntroResponse getIntro() {
        return DashboardIntroResponse.builder()
                .badgeLabel("디지털 포렌식 증거 검증 플랫폼")
                .titleLine1("디지털 미디어 파일")
                .titleLine2("분석 대시보드")
                .description(
                        "업로드된 영상 파일의 딥페이크 여부를 AI로 분석하고, "
                                + "디지털 서명과 체인 오브 커스터디로 증거 무결성을 보장합니다."
                )
                .shortcuts(List.of(
                        DashboardShortcutDto.builder()
                                .label("분석 시작하기")
                                .actionType("IN_APP")
                                .actionTarget("#new-analysis")
                                .variant("primary")
                                .build(),
                        DashboardShortcutDto.builder()
                                .label("비교 검증")
                                .actionType("ROUTE")
                                .actionTarget("/compare")
                                .variant("outline")
                                .build()
                ))
                .trustHighlights(List.of(
                        DashboardTrustHighlightDto.builder()
                                .label("CoC 감사 추적")
                                .iconKey("history")
                                .build(),
                        DashboardTrustHighlightDto.builder()
                                .label("SHA-256 해시 검증")
                                .iconKey("check-circle")
                                .build(),
                        DashboardTrustHighlightDto.builder()
                                .label("영상 딥페이크 분석")
                                .iconKey("layers")
                                .build()
                ))
                .build();
    }
}
