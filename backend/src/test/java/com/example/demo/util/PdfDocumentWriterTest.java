package com.example.demo.util;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentWriterTest {

    private static final List<String> REPORT_LINES = List.of(
            "Case Name: 서울-디지털증거-2026",
            "Case Number: CASE-2026-0710",
            "Analyst Name: 김OO",
            "Analyst Department: 디지털포렌식팀",
            "Analyst Position: 주임",
            "Reviewer Name: 이OO",
            "Reviewer Department: 검토팀",
            "Reviewer Position: 검토관",
            "Review Status: REPORT_APPROVED",
            "Review Approved At: 2026.07.14 10:30",
            "Evidence ID: 101",
            "File Name: sample.mp4",
            "SHA-256: 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            "Risk Level: HIGH",
            "Risk Score: 72",
            "Confidence: 91",
            "Analyzed At: 2026.07.14 10:00",
            "=== Integrity Verification Snapshot ===",
            "Integrity Verified At: 2026.07.14 10:00",
            "Manifest Signature Status: VALID",
            "Manifest Signature Algorithm: SHA256withRSA",
            "Manifest Signer Certificate Subject: CN=ForenShield Test",
            "CoC Chain Status: VALID",
            "CoC Log Count: 7",
            "CoC Broken At Log ID: -",
            "Evidence Blockchain Status: MATCHED",
            "Evidence Blockchain Network: hyperledger-fabric-forenshield",
            "Evidence Blockchain Transaction Hash: tx-report-fixture-001",
            "Evidence Blockchain Anchored At: 2026.07.14 09:59",
            "--- Module: xception ---",
            "Detected: true",
            "Score: 72",
            "Confidence: 91",
            "=== Evidence Items ===",
            "Total Count: 1",
            "Evidence Item: 얼굴 경계 불일치 신호가 확인되었습니다.",
            "=== Module Timeline Summaries ===",
            "Total Count: 1",
            "Module Timeline: module=temporal | model=TimeSFormer v1.2 | videoScore=0.72 | threshold=0.6 | detected=true | points=2 | segments=1",
            "=== Timeline Points ===",
            "Total Count: 2",
            "Timeline Point: source=temporal | kind=CLIP | start=12.0 | end=15.0 | score=0.82 | reference=클립 3 / 프레임 288-360",
            "Timeline Point: source=optical | kind=PAIR | start=12.2 | end=12.2 | score=0.74 | reference=프레임쌍 15 / 프레임 300-301 / 움직임 1.1",
            "=== Suspicious Segments ===",
            "Total Count: 1",
            "Suspicious Segment: source=temporal | start=12.0 | end=15.0 | score=0.82 | reason=시간적 불일치",
            "=== Representative Frames ===",
            "Total Count: 1",
            "Representative Frame: timeSec=12.4 | timestamp=00:12 | frameNumber=310 | score=0.82 | imageRegistered=true"
    );

    @Test
    void generatedReportContainsAutomaticPublicVerificationUrlWithoutManualCode() throws Exception {
        String verifyUrl = "https://verify.example.test/verify?token=vrf_test_token";
        String verificationCode = "VF-TEST-1234";

        byte[] pdf = PdfDocumentWriter.writeReport(
                "ForenShield Analysis Report",
                REPORT_LINES,
                verifyUrl,
                verificationCode,
                "RPT-20260710-TEST"
        );

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(5);
            String overviewText = new PdfTextExtractor(reader).getTextFromPage(1);
            String detailPageText = new PdfTextExtractor(reader).getTextFromPage(2);
            String evidencePageText = new PdfTextExtractor(reader).getTextFromPage(3);
            String technicalPageText = new PdfTextExtractor(reader).getTextFromPage(4);
            String publicationPageText = new PdfTextExtractor(reader).getTextFromPage(5);
            assertThat(overviewText)
                    .contains("서울-디지털증거-2026")
                    .contains("AI 기반 영상 분석 결과보고서")
                    .contains("문서 구분")
                    .contains("최종 발행본")
                    .contains("발행일")
                    .contains("2026.07.14")
                    .contains("딥페이크 분석")
                    .contains("영상 콘텐츠 위변조 분석")
                    .contains("60.0%")
                    .contains("의심 신호 확인")
                    .contains("얼굴 경계 불일치 신호가 확인되었다.")
                    .doesNotContain("근거 1");
            assertThat(overviewText.replaceAll("\\s+", ""))
                    .contains("분석대상영상(sample.mp4,EVD-101)에대하여딥페이크분석을수행한결과")
                    .contains("종합모델출력72.0%로판정기준(60.0%)을초과하는의심신호가확인되었다.")
                    .contains("특히00:12.00-00:15.00에서시간적불일치신호가최대82.0%로관찰되었다.")
                    .contains("영상콘텐츠위변조분석은본건에서수행되지않았다.");
            assertThat(detailPageText)
                    .contains("분석 방법 및 모델별 결과")
                    .contains("딥페이크 분석 방법론 및 결과")
                    .contains("영상 콘텐츠 위변조 분석 방법론 및 결과")
                    .doesNotContain("전체 구간")
                    .doesNotContain("기준 미만");
            assertThat(detailPageText.replaceAll("\\s+", ""))
                    .contains("의심신호확인");
            assertThat(evidencePageText)
                    .contains("시간축 및 시각적 근거")
                    .contains("TimeSFormer")
                    .contains("00:12.00 - 00:15.00")
                    .contains("시간적 불일치")
                    .contains("종합 점수는 영상 전체에 대한 모델 출력이며, 구간 위험도는 해당 구간의 최대값이다.")
                    .contains("대표 프레임: 별첨 없음")
                    .contains("히트맵 결과 없음");
            assertThat(technicalPageText)
                    .contains("분석 입력 파일 및 무결성 검증")
                    .contains("분석 입력 파일 식별 해시(SHA-256)")
                    .contains("발행 시점 무결성 검증 요약")
                    .contains("증거 매니페스트 서명")
                    .contains("CoC 해시 체인")
                    .contains("CoC 기록 건수")
                    .contains("7건")
                    .contains("증거 해시 블록체인")
                    .contains("hyperledger-fabric-forenshield")
                    .contains("검증 수행 시각")
                    .doesNotContain("수집·인계·보관 이력 범위")
                    .doesNotContain("파일 크기")
                    .doesNotContain("MIME 유형")
                    .doesNotContain("오디오 샘플레이트");
            assertThat(technicalPageText.replaceAll("\\s+", ""))
                    .contains("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
            assertThat(publicationPageText)
                    .contains("김OO / 디지털포렌식팀 / 주임")
                    .contains("이OO / 검토팀 / 검토관")
                    .contains("기관 내부 절차상 최종 승인")
                    .contains("발행 등록 정보")
                    .contains(verifyUrl)
                    .contains("발행 등록정보 조회")
                    .contains("발행 등록 상태 조회")
                    .contains("검증 범위에 포함되지 않는다")
                    .contains("기술 참고자료")
                    .doesNotContain("PDF 전자서명")
                    .doesNotContain("외부 해시 앵커")
                    .doesNotContain("미적용 (도입 예정)")
                    .doesNotContain(verificationCode)
                    .doesNotContain("(서명)")
                    .doesNotContain("출력본 확인 서명");
            for (int page = 1; page <= 5; page++) {
                String pageText = new PdfTextExtractor(reader).getTextFromPage(page);
                assertThat(pageText.replaceAll("\\s+", ""))
                        .contains("사건번호:CASE-2026-0710");
                assertThat(pageText).contains("- " + page + " / 5 -");
            }
            if ("true".equalsIgnoreCase(System.getenv("WRITE_PDF_FIXTURE"))) {
                Path fixture = Path.of("build/pdf-fixtures/analysis-report-5p.pdf");
                Files.createDirectories(fixture.getParent());
                Files.write(fixture, pdf);
            }
        } finally {
            reader.close();
        }
    }

    @Test
    void draftReportContainsNoPublicVerificationCredential() throws Exception {
        byte[] pdf = PdfDocumentWriter.writeDraftReport("ForenShield Analysis Report", REPORT_LINES);

        PdfReader reader = new PdfReader(pdf);
        try {
            String overviewText = new PdfTextExtractor(reader).getTextFromPage(1);
            String publicationPageText = new PdfTextExtractor(reader).getTextFromPage(5);
            assertThat(overviewText)
                    .contains("내부 검토용")
                    .contains("미발행 · 미리보기");
            assertThat(publicationPageText)
                    .contains("검증 QR과 URL은 발행 등록 후 생성된다.")
                    .contains("미발행 · 미리보기")
                    .contains("승인 전")
                    .doesNotContain("출력본 확인 서명")
                    .doesNotContain("vrf_")
                    .doesNotContain("VF-TEST");
        } finally {
            reader.close();
        }
    }

    @Test
    void previewWatermarkClearlyStatesThatVerificationIsUnavailable() throws Exception {
        byte[] pdf = PdfDocumentWriter.addPreviewWatermark(
                PdfDocumentWriter.writeDraftReport("ForenShield Analysis Report", REPORT_LINES)
        );

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1))
                    .contains("미리보기 - 검증 불가");
        } finally {
            reader.close();
        }
    }

    @Test
    void analysisReportWithoutVisualizationShowsNoFabricatedDetail() throws Exception {
        byte[] pdf = PdfDocumentWriter.writeDraftReport(
                "ForenShield Analysis Report",
                List.of("Risk Level: LOW", "Risk Score: 10")
        );

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(5);
            String evidencePageText = new PdfTextExtractor(reader).getTextFromPage(3);
            assertThat(evidencePageText)
                    .contains("모듈 타임라인, 의심 구간 또는 대표 프레임 기록이 없다.")
                    .contains("임의 데이터를 생성하지 않는다.")
                    .doesNotContain("전체 구간");
        } finally {
            reader.close();
        }
    }

    @Test
    void compareReportRendersActualSerializedComparisonRows() throws Exception {
        List<String> lines = List.of(
                "Compare ID: 42",
                "Verdict: TAMPERED",
                "Match Count: 0",
                "Mismatch Count: 1",
                "Skipped Count: 0",
                "Review Status: REPORT_APPROVED",
                "=== Comparison Results ===",
                "SHA-256 | original=aaaaaaaa | candidate=bbbbbbbb | result=MISMATCH",
                "Duration | original=null | candidate=null | result=SKIPPED"
        );

        byte[] pdf = PdfDocumentWriter.writeReport(
                "ForenShield Compare Verification Report",
                lines,
                "https://verify.example.test/verify?token=vrf_compare",
                "VF-COMP-1234",
                "RPT-20260714-COMPARE"
        );

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(2);
            String resultPageText = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(resultPageText)
                    .contains("SHA-256")
                    .contains("aaaaaaaa")
                    .contains("bbbbbbbb")
                    .contains("불일치")
                    .doesNotContain("null");
        } finally {
            reader.close();
        }
    }
}
