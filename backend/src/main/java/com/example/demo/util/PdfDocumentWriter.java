package com.example.demo.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PdfDocumentWriter {

    private static final Color INK = new Color(15, 23, 42);
    private static final Color SLATE = new Color(71, 85, 105);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color LIGHT = new Color(148, 163, 184);
    private static final Color BORDER = new Color(100, 116, 139);
    private static final Color LIGHT_BORDER = new Color(203, 213, 225);
    private static final Color SURFACE = new Color(241, 245, 249);
    private static final Color TEAL = new Color(0, 137, 123);
    private static final Color DANGER = new Color(185, 28, 28);
    private static final String VERDICT_SIGNAL_DETECTED = "의심 신호 확인";
    private static final String VERDICT_NO_SIGNAL = "의심 신호 미확인";
    private static final String VERDICT_UNDETERMINED = "판정 불가";
    private static final String VERDICT_NOT_PERFORMED = "미실시";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final BaseFont DOCUMENT_BASE_FONT = loadDocumentBaseFont();

    private PdfDocumentWriter() {
    }

    public static byte[] writeReport(String title, List<String> lines) {
        return writeReport(title, lines, null);
    }

    public static byte[] writeReport(String title, List<String> lines, String qrContent) {
        return writeReport(title, lines, qrContent, null);
    }

    public static byte[] writeReport(String title, List<String> lines, String qrContent, String verificationCode) {
        return writeReport(title, lines, qrContent, verificationCode, null);
    }

    public static byte[] writeReport(
            String title,
            List<String> lines,
            String qrContent,
            String verificationCode,
            String reportNo
    ) {
        return writeReport(title, lines, qrContent, verificationCode, reportNo, true);
    }

    public static byte[] writeDraftReport(String title, List<String> lines) {
        return writeReport(title, lines, null, null, null, false);
    }

    private static byte[] writeReport(
            String title,
            List<String> lines,
            String qrContent,
            String verificationCode,
            String reportNo,
            boolean issued
    ) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ReportData data = ReportData.parse(lines);
            boolean compareReport = isCompareReport(title, data);

            Document document = new Document(PageSize.A4, 54, 54, 46, 56);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            if (compareReport) {
                addCompareReportPage(document, data, reportNo, issued);
                document.newPage();
                addIntegrityPage(document, data, reportNo, qrContent, true, issued);
            } else {
                addAnalysisOverviewPage(document, data, reportNo, issued);
                document.newPage();
                addAnalysisDetailPage(document, data, reportNo);
                document.newPage();
                addAnalysisEvidencePage(document, data, reportNo);
                document.newPage();
                addAnalysisTechnicalPage(document, data, reportNo);
                document.newPage();
                addAnalysisPublicationPage(document, data, reportNo, qrContent, issued);
            }

            document.close();
            return addPageFooters(outputStream.toByteArray());
        } catch (DocumentException ex) {
            throw new IllegalStateException("PDF 생성에 실패했습니다.", ex);
        }
    }

    public static byte[] addPreviewWatermark(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return pdfBytes;
        }

        try {
            PdfReader reader = new PdfReader(pdfBytes);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, outputStream);
            PdfGState state = new PdfGState();
            state.setFillOpacity(0.055f);

            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                Rectangle pageSize = reader.getPageSizeWithRotation(page);
                PdfContentByte canvas = stamper.getUnderContent(page);
                canvas.saveState();
                canvas.setGState(state);
                ColumnText.showTextAligned(
                        canvas,
                        Element.ALIGN_CENTER,
                        new Phrase("미리보기 - 검증 불가", font(34, Font.BOLD, MUTED)),
                        pageSize.getWidth() / 2f,
                        pageSize.getHeight() / 2f,
                        0
                );
                canvas.restoreState();
            }

            stamper.close();
            reader.close();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 미리보기 워터마크 적용에 실패했습니다.", ex);
        }
    }

    private static void addAnalysisOverviewPage(
            Document document,
            ReportData data,
            String reportNo,
            boolean issued
    )
            throws DocumentException {
        addPageHeader(document, "AI 기반 영상 분석 결과보고서", reportHeaderSubtitle(data, reportNo));

        addSectionTitle(document, 1, "문서 및 분석대상 식별");
        addInfoGrid(document, List.of(
                row("보고서 유형", "AI 기반 영상 분석 결과보고서"),
                row("문서 구분", issued ? "최종 발행본" : "내부 검토용"),
                row("문서 상태", issued ? "발행 완료" : "미발행 · 미리보기"),
                row("발행일", issued ? publicationDate(data) : "발행 전"),
                row("검토 상태", reviewStatusLabel(data.value("Review Status").orElse("NONE"))),
                row("사건 번호", displayValue(data.value("Case Number").orElse(null))),
                row("사건명", displayValue(data.value("Case Name").orElse(null))),
                row("분석 대상", prefixedEvidenceId(displayValue(data.value("Evidence ID").orElse(null)))),
                row("파일명", displayValue(data.value("File Name").orElse(null))),
                row("분석 실행 ID", displayValue(data.value("Analysis Result ID").orElse(null))),
                row("분석 완료", displayValue(data.value("Analyzed At").orElse(null)))
        ));

        addSectionTitle(document, 2, "분석 영역별 결과");
        addAnalysisAreaSummary(document, data);

        addSectionTitle(document, 3, "AI 기반 종합 분석 결과");
        addVerdictBox(document, riskLabel(data), data.value("Summary").orElse("-"));
        addNarrativeConclusion(document, data);
        addInfoGrid(document, List.of(
                row("종합 모델 출력 점수", scoreOrMissing(data.value("Risk Score").orElse(null))),
                row("위험 분류 (시스템 판정 기준)", riskClassLabel(data.value("Risk Level").orElse(null))),
                row("분석 처리 상태", analysisStatusLabel(data.value("Analysis Status").orElse(null))),
                row("전문가 추가 검토", expertReviewLabel(data))
        ));

        addSectionTitle(document, 4, "핵심 근거 요약");
        addReasonRows(document, data);
    }

    private static void addAnalysisDetailPage(Document document, ReportData data, String reportNo)
            throws DocumentException {
        addPageHeader(document, "분석 방법 및 모델별 결과", reportHeaderSubtitle(data, reportNo));

        Paragraph method = new Paragraph(
                "각 점수는 해당 분석 모듈의 모델 출력값이다. 모델마다 점수의 의미가 다를 수 있으므로, "
                        + "동일 행의 판정 기준값과 처리 상태를 함께 확인해야 한다.",
                font(9.5f, Font.NORMAL, SLATE)
        );
        method.setLeading(15);
        method.setSpacingAfter(10);
        document.add(method);

        List<ModuleBlock> modules = moduleRows(data);
        List<ModuleBlock> deepfakeModules = modules.stream()
                .filter(module -> !isForgeryModule(module.name))
                .limit(4)
                .toList();
        List<ModuleBlock> forgeryModules = modules.stream()
                .filter(module -> isForgeryModule(module.name))
                .limit(4)
                .toList();

        addAnalysisMethodSection(
                document,
                data,
                1,
                "딥페이크 분석 방법론 및 결과",
                "얼굴 합성·교체와 관련된 의심 신호를 확인하기 위해, 얼굴 기반 분석 모듈의 출력값과 판정 기준을 비교한다.",
                deepfakeModules
        );
        addAnalysisMethodSection(
                document,
                data,
                2,
                "영상 콘텐츠 위변조 분석 방법론 및 결과",
                "프레임 편집·삽입·삭제 및 시간적 불일치와 관련된 의심 신호를 확인하기 위해, 영상 내용 분석 모듈의 출력값과 판정 기준을 비교한다.",
                forgeryModules
        );
        if (modules.size() > 8) {
            addFootnote(document, "※ 핵심본에는 상위 8개 모듈을 표시한다. 전체 분석 모듈: " + modules.size() + "개");
        }

        addSectionTitle(document, 3, "공통 판정 구조와 해석 범위");
        addInfoGrid(document, List.of(
                row("점수 표시", "모델 출력값"),
                row("판정 기준", "기록이 있는 경우 같은 행에 표시"),
                row("기준 초과", VERDICT_SIGNAL_DETECTED),
                row("기준 미초과", VERDICT_NO_SIGNAL)
        ));
        addAvailabilityNotice(
                document,
                "영역별 기여도 또는 가중치 기록이 없는 경우 임의로 추정하지 않는다. "
                        + "영역별 결과는 종합 모델 또는 판정 규칙의 결과와 함께 해석해야 한다."
        );

        String errorCode = data.value("Analysis Error Code").orElse(null);
        String errorMessage = data.value("Analysis Error Message").orElse(null);
        if (errorCode != null || errorMessage != null) {
            addSectionTitle(document, 4, "판별 보류·실패 정보");
            addInfoGrid(document, List.of(
                    row("오류 코드", displayValue(errorCode)),
                    row("사유", displayValue(errorMessage))
            ));
        }
    }

    private static void addAnalysisEvidencePage(Document document, ReportData data, String reportNo)
            throws DocumentException {
        addPageHeader(document, "시간축 및 시각적 근거", reportHeaderSubtitle(data, reportNo));

        if (!hasDetailedEvidence(data)) {
            addSectionTitle(document, 1, "시각적 근거");
            addAvailabilityNotice(
                    document,
                    "모듈 타임라인, 의심 구간 또는 대표 프레임 기록이 없다. "
                            + "빈 도표나 임의 데이터를 생성하지 않는다."
            );
            return;
        }

        addSectionTitle(document, 1, "통합 타임라인 요약");
        addModuleTimelineSummary(document, data);

        addSectionTitle(document, 2, "주요 의심 구간·지점");
        addPrimaryEvidenceTable(document, data);
        addFootnote(document, "종합 점수는 영상 전체에 대한 모델 출력이며, 구간 위험도는 해당 구간의 최대값이다.");

        addSectionTitle(document, 3, "대표 프레임 및 시각화 자료");
        addRepresentativeFrameCards(document, data);
    }

    private static void addAnalysisTechnicalPage(Document document, ReportData data, String reportNo)
            throws DocumentException {
        addPageHeader(document, "분석 입력 파일 및 무결성 검증", reportHeaderSubtitle(data, reportNo));

        int sectionNumber = 1;
        List<DisplayRow> fileRows = presentRows(
                row("증거 ID", prefixedEvidenceId(data.value("Evidence ID").orElse(null))),
                row("파일명", displayValue(data.value("File Name").orElse(null))),
                row("파일 크기", displayValue(data.value("File Size").orElse(null))),
                row("MIME 유형", displayValue(data.value("MIME Type").orElse(null))),
                row("파일 유형", displayValue(data.value("File Type").orElse(null))),
                row("시스템 등록 시각", displayValue(data.value("Uploaded At").orElse(null)))
        );
        if (!fileRows.isEmpty()) {
            addSectionTitle(document, sectionNumber++, "분석대상 파일 정보");
            addInfoGrid(document, fileRows);
        }

        List<DisplayRow> technicalRows = presentRows(
                row("해상도", technicalValue(data, "Resolution")),
                row("영상 길이(초)", technicalValue(data, "Duration Seconds")),
                row("프레임레이트", technicalValue(data, "FPS")),
                row("영상 코덱", technicalValue(data, "Video Codec")),
                row("오디오 샘플레이트", technicalValue(data, "Audio Sample Rate")),
                row("오디오 채널", technicalValue(data, "Audio Channels"))
        );
        if (!technicalRows.isEmpty()) {
            addSectionTitle(document, sectionNumber++, "영상 기술정보");
            addInfoGrid(document, technicalRows);
        }

        List<DisplayRow> executionRows = presentRows(
                row("분석 요청 ID", displayValue(data.value("Analysis Request ID").orElse(null))),
                row("분석 결과 ID", displayValue(data.value("Analysis Result ID").orElse(null))),
                row("요청 시각", displayValue(data.value("Requested At").orElse(null))),
                row("시작 시각", displayValue(data.value("Started At").orElse(null))),
                row("처리 상태", analysisStatusLabel(data.value("Analysis Status").orElse(null)))
        );
        executionRows.add(row(
                "분석 완료 시각",
                displayValue(data.value("Completed At").orElse(data.value("Analyzed At").orElse(null)))
        ));
        addSectionTitle(document, sectionNumber++, "분석 실행 정보");
        addInfoGrid(document, executionRows);

        addSectionTitle(document, sectionNumber++, "분석 입력 파일 식별 해시(SHA-256)");
        addFullWidthValue(document, displayValue(data.value("SHA-256").orElse(null)), true);
        addAvailabilityNotice(
                document,
                "이 SHA-256 값은 본 분석에 투입된 파일의 바이트열을 식별하기 위한 값이다. "
                        + "해당 파일이 촬영장치에서 생성된 최초 파일인지 또는 수집·보관의 연속성이 유지되었는지를 이 값만으로 보증하지 않는다."
        );

        addSectionTitle(document, sectionNumber, "발행 시점 무결성 검증 요약");
        addInfoGrid(document, List.of(
                row(
                        "증거 매니페스트 서명",
                        manifestSignatureLabel(data.value("Manifest Signature Status").orElse(null))
                ),
                row("CoC 해시 체인", cocChainLabel(data.value("CoC Chain Status").orElse(null))),
                row("CoC 기록 건수", countLabel(data.value("CoC Log Count").orElse(null))),
                row(
                        "증거 해시 블록체인",
                        evidenceBlockchainLabel(
                                data.value("Evidence Blockchain Status").orElse(null),
                                data.value("Evidence Blockchain Network").orElse(null)
                        )
                ),
                row("검증 수행 시각", displayValue(data.value("Integrity Verified At").orElse(null)))
        ));
    }

    private static void addAnalysisPublicationPage(
            Document document,
            ReportData data,
            String reportNo,
            String qrContent,
            boolean issued
    ) throws DocumentException {
        addPageHeader(document, "검토·발행 및 공개 검증", reportHeaderSubtitle(data, reportNo));

        addSectionTitle(document, 1, "작성·검토 및 발행 책임");
        addResponsibilityTable(document, data, issued);

        addSectionTitle(document, 2, "발행 등록 정보");
        addInfoGrid(document, List.of(
                row("발행 상태", issued ? "기관 발행 등록 완료" : "미발행 · 미리보기"),
                row("검토 결과", reviewStatusLabel(data.value("Review Status").orElse("NONE")))
        ));

        addSectionTitle(document, 3, "발행 등록 조회");
        addVerificationBlock(document, qrContent, issued);

        addSectionTitle(document, 4, "검증 범위 및 AI 분석 한계");
        List<String> scopeClauses = new ArrayList<>();
        scopeClauses.add(
                "본 보고서는 발행 시점에 기록된 AI 모델 출력과 시스템 처리 결과를 요약한 기술 참고자료이며, 법적 판단을 대신하지 않는다."
        );
        scopeClauses.add(
                "'의심 신호 확인'은 조작 사실을 확정하지 않으며, '의심 신호 미확인'은 영상이 원본이거나 조작되지 않았음을 보증하지 않는다."
        );
        scopeClauses.add(
                issued
                        ? "QR 검증 페이지는 본 보고서의 발행 등록 상태 조회와, 업로드한 PDF가 발행 등록본과 동일한 파일인지(바이트 단위 일치 여부)의 확인 기능만을 제공한다. "
                                + "원본 영상의 촬영 원본성, 증거 수집의 적법성, 수집·인계·보관의 연속성, AI 분석 결론의 정확성은 검증 범위에 포함되지 않는다."
                        : "본 문서는 발행 등록 전 미리보기이므로 QR 조회 및 PDF 파일 동일성 검증 기능을 제공하지 않는다."
        );
        scopeClauses.add(
                "최종 판단은 사건 맥락, 수집·보관 절차, 그 밖의 증거자료 및 전문가 검토를 종합하여 이루어져야 한다."
        );
        addClauseList(document, scopeClauses);
    }

    private static void addClauseList(Document document, List<String> clauses) throws DocumentException {
        for (int i = 0; i < clauses.size(); i++) {
            Paragraph clause = new Paragraph((i + 1) + ". " + clauses.get(i), font(10, Font.NORMAL, INK));
            clause.setLeading(16);
            clause.setIndentationLeft(12);
            clause.setSpacingAfter(i == clauses.size() - 1 ? 0 : 6);
            document.add(clause);
        }
    }

    private static void addCompareReportPage(
            Document document,
            ReportData data,
            String reportNo,
            boolean issued
    )
            throws DocumentException {
        addPageHeader(document, "비교검증 보고서", reportHeaderSubtitle(data, reportNo));

        addSectionTitle(document, 1, "검증 개요");
        addInfoGrid(document, List.of(
                row("보고서 유형", "비교검증 보고서"),
                row("검토 상태", issued ? "검토 승인 완료" : "검토 승인 대기"),
                row("비교검증 ID", data.value("Compare ID").orElse("-")),
                row("생성일", data.value("Created At").orElse("-")),
                row("판정", compareVerdict(data.value("Verdict").orElse("-"))),
                row("검증 방식", "해시 · 파일 속성 · 메타데이터 항목 대조")
        ));

        addSectionTitle(document, 2, "원본 및 비교 대상");
        Section original = data.section("Original File Information");
        Section candidate = data.section("Candidate File Information");
        PdfPTable files = reportTable(new float[]{1f, 1f});
        addFileInfoCell(files, "기준 증거", original);
        addFileInfoCell(files, "비교 대상", candidate);
        document.add(files);

        addSectionTitle(document, 3, "비교 결과 요약");
        addInfoGrid(document, List.of(
                row("일치", data.value("Match Count").orElse("0") + "건"),
                row("불일치", data.value("Mismatch Count").orElse("0") + "건"),
                row("제외", data.value("Skipped Count").orElse("0") + "건"),
                row("최종 판정", compareVerdict(data.value("Verdict").orElse("-")))
        ));

        addSectionTitle(document, 4, "주요 불일치 항목");
        Section comparison = data.section("Comparison Results");
        PdfPTable result = reportTable(new float[]{1.25f, 1.9f, 1.9f, 0.85f});
        addTableHeader(result, "항목", "기준", "대상", "결과");
        if (comparison == null || comparison.rows.isEmpty()) {
            addTableRow(result, "비교 결과", "-", "-", VERDICT_UNDETERMINED);
        } else {
            for (Row row : comparison.rows) {
                String serializedRow = row.value.isBlank()
                        ? row.label
                        : row.label + ": " + row.value;
                CompareRow item = CompareRow.parse(serializedRow);
                addTableRow(result, item.label, item.original, item.candidate, compareResult(item.result));
            }
        }
        document.add(result);
    }

    private static void addIntegrityPage(
            Document document,
            ReportData data,
            String reportNo,
            String qrContent,
            boolean compareReport,
            boolean issued
    ) throws DocumentException {
        addPageHeader(document, "무결성 및 감사 이력", reportHeaderSubtitle(data, reportNo));

        addSectionTitle(document, 1, "무결성 검증 결과");
        addInfoGrid(document, List.of(
                row("분석대상 SHA-256", data.value("SHA-256").orElse(rowValue(data.section("Original File Information"), "SHA-256"))),
                row("최종 PDF SHA-256", issued ? "QR 검증 페이지에서 확인" : "최종 발행 전"),
                row("해시 알고리즘", "SHA-256"),
                row("QR 검증 범위", issued ? "발행 등록정보 조회" : "발행 등록 전"),
                row("PDF 동일성 확인", issued ? "검증 페이지에서 파일 해시 대조" : "발행 전")
        ));

        addSectionTitle(document, 2, "발행 등록 조회");
        addVerificationBlock(document, qrContent, issued);
        Paragraph verificationCaution = new Paragraph(
                "QR 조회로는 보고서 번호와 발행 등록정보를 확인할 수 있다. PDF 파일이 발행 당시 원본과 동일한지 확인하려면, 검증 페이지에 해당 PDF를 업로드하여 등록된 SHA-256 해시값과 대조해야 한다.",
                font(10, Font.BOLD, SLATE)
        );
        verificationCaution.setLeading(15);
        verificationCaution.setSpacingBefore(6);
        verificationCaution.setSpacingAfter(6);
        document.add(verificationCaution);
        Paragraph caution = new Paragraph(
                compareReport
                        ? "본 결과는 보고서에 특정된 두 파일의 기술적 동일성 또는 차이를 나타낸다. 불일치의 원인, 고의적 변조 여부 또는 법률상 원본성은 본 결과만으로 판단할 수 없다."
                        : "본 보고서의 AI 분석 결과는 조작 여부를 확정하는 판정이 아니며, 확률에 기반한 참고 자료이다. 최종 판단 시에는 원본 자료, 사건 맥락, 파일 비교 결과 및 전문가 검토를 종합해야 한다.",
                font(11, Font.NORMAL, INK)
        );
        caution.setLeading(16);
        caution.setSpacingBefore(8);
        document.add(caution);

        addApprovalBlock(document, data, issued);
    }

    private static void addPageHeader(Document document, String title, String subtitle) throws DocumentException {
        PdfPTable meta = new PdfPTable(new float[]{0.9f, 1.6f});
        meta.setWidthPercentage(100);

        PdfPCell left = noBorderCell(new Phrase("ForenShield AI 디지털 증거 분석 시스템", font(9, Font.NORMAL, SLATE)));
        left.setHorizontalAlignment(Element.ALIGN_LEFT);
        meta.addCell(left);

        PdfPCell right = noBorderCell();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph reportNo = new Paragraph(blankFallback(subtitle, "-"), font(8.5f, Font.NORMAL, SLATE));
        reportNo.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(reportNo);
        meta.addCell(right);
        document.add(meta);

        addRule(document, INK, 1.8f, 4, 2);
        addRule(document, INK, 0.7f, 0, 24);

        Paragraph heading = new Paragraph(title, font(22, Font.BOLD, INK));
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingAfter(14);
        document.add(heading);
        PdfPTable smallRule = new PdfPTable(1);
        smallRule.setWidthPercentage(18);
        smallRule.setHorizontalAlignment(Element.ALIGN_CENTER);
        smallRule.setSpacingAfter(18);
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(1.5f);
        cell.setBackgroundColor(INK);
        smallRule.addCell(cell);
        document.add(smallRule);
    }

    private static void addSectionTitle(Document document, int number, String title) throws DocumentException {
        Paragraph section = new Paragraph(number + ". " + title, font(13, Font.BOLD, INK));
        section.setSpacingBefore(7);
        section.setSpacingAfter(5);
        document.add(section);
    }

    private static void addInfoGrid(Document document, List<DisplayRow> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1.3f, 2.2f, 1.3f, 2.2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        table.setSplitLate(false);

        for (int index = 0; index < rows.size(); index += 2) {
            DisplayRow left = rows.get(index);
            addInfoLabel(table, left.label());
            addInfoValue(table, left.value());

            if (index + 1 < rows.size()) {
                DisplayRow right = rows.get(index + 1);
                addInfoLabel(table, right.label());
                addInfoValue(table, right.value());
            } else {
                addInfoLabel(table, "");
                addInfoValue(table, "");
            }
        }
        document.add(table);
    }

    private static void addVerdictBox(Document document, String label, String summary) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(INK);
        cell.setBorderWidth(1.4f);
        cell.setPadding(14);

        Paragraph small = new Paragraph("종합 판정", font(10, Font.NORMAL, MUTED));
        small.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(small);

        Paragraph verdict = new Paragraph(label, font(18, Font.BOLD, INK));
        verdict.setAlignment(Element.ALIGN_CENTER);
        verdict.setSpacingAfter(6);
        cell.addElement(verdict);

        if (!isMissingValue(summary)) {
            Paragraph body = new Paragraph(summary.trim(), font(10, Font.NORMAL, SLATE));
            body.setAlignment(Element.ALIGN_CENTER);
            body.setLeading(16);
            cell.addElement(body);
        }

        table.addCell(cell);
        document.add(table);
    }

    private static void addReasonRows(Document document, ReportData data) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1.2f, 4.4f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        List<String> evidenceItems = sectionValues(data.section("Evidence Items"), "Evidence Item");
        if (!evidenceItems.isEmpty()) {
            if (evidenceItems.size() == 1) {
                Paragraph reason = new Paragraph(reportLanguage(evidenceItems.get(0)), font(10, Font.NORMAL, INK));
                reason.setLeading(16);
                reason.setSpacingAfter(10);
                document.add(reason);
                return;
            }
            for (int index = 0; index < evidenceItems.size(); index++) {
                addReasonRow(table, "근거 " + (index + 1), reportLanguage(evidenceItems.get(index)));
            }
        } else if (data.modules.isEmpty()) {
            addReasonRow(table, "판정 요약", data.value("Summary").orElse("-"));
        } else {
            for (ModuleBlock module : data.modules) {
                String detail = switch (detectedLabel(module.value("Detected").orElse(null))) {
                    case VERDICT_SIGNAL_DETECTED -> "해당 모듈에서 판정 기준을 초과하는 의심 신호가 확인되었다.";
                    case VERDICT_NO_SIGNAL -> "해당 모듈에서 판정 기준을 초과하는 의심 신호가 확인되지 않았다.";
                    default -> "해당 모듈의 탐지 판정값을 확인할 수 없다.";
                };
                addReasonRow(table, moduleLabel(module.name), detail);
            }
        }
        document.add(table);
    }

    private static void addNarrativeConclusion(Document document, ReportData data) throws DocumentException {
        String fileName = displayValue(data.value("File Name").orElse(null));
        String evidenceId = prefixedEvidenceId(data.value("Evidence ID").orElse(null));
        String performedAreas = performedAreaText(data);
        String score = scoreOrMissing(data.value("Risk Score").orElse(null));
        String threshold = overallThreshold(data);

        StringBuilder narrative = new StringBuilder();
        narrative.append("분석 대상 영상(")
                .append(fileName)
                .append(", ")
                .append(evidenceId)
                .append(")에 대하여 ")
                .append(performedAreas)
                .append("을 수행한 결과");

        Double scoreValue = parseDouble(data.value("Risk Score").orElse(null));
        Double thresholdValue = parseDouble(threshold);
        if (scoreValue != null && thresholdValue != null) {
            boolean exceeds = normalizedScore(scoreValue) > normalizedScore(thresholdValue);
            narrative.append(", 종합 모델 출력 ")
                    .append(score)
                    .append("로 판정 기준(")
                    .append(threshold)
                    .append(")을 ")
                    .append(exceeds ? "초과하는 " : "초과하지 않는 ")
                    .append("의심 신호가 ")
                    .append(exceeds ? "확인되었다." : "확인되지 않았다.");
        } else if (scoreValue != null) {
            narrative.append(", 종합 모델 출력 ").append(score).append("가 산출되었다.");
        } else {
            narrative.append(", 종합 판정은 ").append(riskLabel(data)).append("으로 분류되었다.");
        }

        highestSuspiciousSegment(data).ifPresent(segment -> narrative
                .append(" 특히 ")
                .append(segment.range())
                .append("에서 ")
                .append(segment.reason())
                .append(" 신호가 최대 ")
                .append(segment.score())
                .append("로 관찰되었다."));

        List<String> unperformed = new ArrayList<>();
        if (data.modules.stream().noneMatch(module -> !isForgeryModule(module.name))) {
            unperformed.add("딥페이크 분석");
        }
        if (data.modules.stream().noneMatch(module -> isForgeryModule(module.name))) {
            unperformed.add("영상 콘텐츠 위변조 분석");
        }
        if (!unperformed.isEmpty()) {
            narrative.append(" ")
                    .append(String.join("과 ", unperformed))
                    .append("은 본 건에서 수행되지 않았다.");
        }
        narrative.append(" 본 결과는 AI 분석에 기반한 기술 참고자료로서, 최종 판단은 사건 맥락 및 전문가 검토와 함께 이루어져야 한다.");

        Paragraph paragraph = new Paragraph(narrative.toString(), font(10, Font.NORMAL, INK));
        paragraph.setLeading(16);
        paragraph.setSpacingAfter(10);
        document.add(paragraph);
    }

    private static String performedAreaText(ReportData data) {
        boolean deepfake = data.modules.stream().anyMatch(module -> !isForgeryModule(module.name));
        boolean forgery = data.modules.stream().anyMatch(module -> isForgeryModule(module.name));
        if (deepfake && forgery) {
            return "딥페이크 분석 및 영상 콘텐츠 위변조 분석";
        }
        if (deepfake) {
            return "딥페이크 분석";
        }
        if (forgery) {
            return "영상 콘텐츠 위변조 분석";
        }
        return "AI 기반 영상 분석";
    }

    private static String reportHeaderSubtitle(ReportData data, String reportNo) {
        StringBuilder subtitle = new StringBuilder("보고서 번호: ")
                .append(blankFallback(reportNo, "-"));
        data.value("Case Number")
                .filter(value -> !value.isBlank() && !"-".equals(value))
                .ifPresent(value -> subtitle.append(" · 사건 번호: ").append(value.trim()));
        return subtitle.toString();
    }

    private static String publicationDate(ReportData data) {
        String value = data.value("Issued At")
                .orElse(data.value("Review Approved At").orElse(null));
        if (isMissingValue(value)) {
            return "기록 없음";
        }
        String normalized = value.trim();
        return normalized.length() >= 10 ? normalized.substring(0, 10) : normalized;
    }

    private static String overallThreshold(ReportData data) {
        for (String value : sectionValues(data.section("Module Timeline Summaries"), "Module Timeline")) {
            String threshold = field(parseFields(value), "threshold");
            if (parseDouble(threshold) != null) {
                return scoreOrMissing(threshold);
            }
        }
        return "기록 없음";
    }

    private static Optional<SuspiciousSegmentSummary> highestSuspiciousSegment(ReportData data) {
        return sectionValues(data.section("Suspicious Segments"), "Suspicious Segment").stream()
                .map(PdfDocumentWriter::parseFields)
                .filter(fields -> parseDouble(field(fields, "score")) != null)
                .max((left, right) -> Double.compare(
                        normalizedScore(parseDouble(field(left, "score"))),
                        normalizedScore(parseDouble(field(right, "score")))
                ))
                .map(fields -> new SuspiciousSegmentSummary(
                        timeRange(field(fields, "start"), field(fields, "end")),
                        reportLanguage(field(fields, "reason")),
                        percentageText(field(fields, "score"))
                ));
    }

    private static double normalizedScore(Double value) {
        if (value == null) {
            return 0.0;
        }
        return Math.abs(value) <= 1.0 ? value * 100.0 : value;
    }

    private static void addAnalysisAreaSummary(Document document, ReportData data) throws DocumentException {
        PdfPTable table = reportTable(new float[]{1.45f, 0.8f, 0.85f, 0.85f, 2.1f});
        addTableHeader(table, "분석 영역", "처리 상태", "대표 출력", "판정 기준", "범주형 결과");
        addAnalysisAreaRow(table, data, false);
        addAnalysisAreaRow(table, data, true);
        document.add(table);
    }

    private static void addAnalysisAreaRow(PdfPTable table, ReportData data, boolean forgery) {
        List<ModuleBlock> modules = data.modules.stream()
                .filter(module -> isForgeryModule(module.name) == forgery)
                .toList();
        String area = forgery ? "영상 콘텐츠 위변조 분석" : "딥페이크 분석";
        if (modules.isEmpty()) {
            addTableRow(table, area, VERDICT_NOT_PERFORMED, "기록 없음", "기록 없음", VERDICT_NOT_PERFORMED);
            return;
        }

        ModuleBlock representative = modules.stream()
                .max((left, right) -> Double.compare(
                        parseDouble(left.value("Score").orElse(null)) == null
                                ? -1.0
                                : parseDouble(left.value("Score").orElse(null)),
                        parseDouble(right.value("Score").orElse(null)) == null
                                ? -1.0
                                : parseDouble(right.value("Score").orElse(null))
                ))
                .orElse(modules.get(0));
        boolean detected = modules.stream()
                .anyMatch(module -> "true".equalsIgnoreCase(module.value("Detected").orElse("")));
        boolean hasDecision = modules.stream()
                .anyMatch(module -> module.value("Detected").filter(value -> !value.isBlank()).isPresent());
        String threshold = areaThreshold(data, modules, representative);
        boolean hasThreshold = !isMissingValue(threshold);
        String result = !hasDecision || !hasThreshold
                ? VERDICT_UNDETERMINED
                : detected
                ? VERDICT_SIGNAL_DETECTED
                : VERDICT_NO_SIGNAL;
        addTableRow(
                table,
                area,
                "완료",
                scoreOrMissing(representative.value("Score").orElse(null)),
                threshold,
                result
        );
    }

    private static String areaThreshold(ReportData data, List<ModuleBlock> modules, ModuleBlock representative) {
        String directThreshold = moduleThreshold(data, representative);
        if (!isMissingValue(directThreshold)) {
            return directThreshold;
        }
        boolean forgeryArea = isForgeryModule(representative.name);
        for (String value : sectionValues(data.section("Module Timeline Summaries"), "Module Timeline")) {
            Map<String, String> fields = parseFields(value);
            if (isForgeryModule(field(fields, "module")) == forgeryArea
                    && parseDouble(field(fields, "threshold")) != null) {
                return scoreOrMissing(field(fields, "threshold"));
            }
        }
        for (ModuleBlock module : modules) {
            String threshold = moduleThreshold(data, module);
            if (!isMissingValue(threshold)) {
                return threshold;
            }
        }
        return "기록 없음";
    }

    private static void addAnalysisMethodSection(
            Document document,
            ReportData data,
            int sectionNumber,
            String title,
            String description,
            List<ModuleBlock> modules
    ) throws DocumentException {
        addSectionTitle(document, sectionNumber, title);
        Paragraph methodology = new Paragraph(description, font(9, Font.NORMAL, SLATE));
        methodology.setLeading(14);
        methodology.setSpacingAfter(7);
        document.add(methodology);

        PdfPTable table = reportTable(new float[]{1f, 1.65f, 0.75f, 0.8f, 0.75f, 1.45f});
        addTableHeader(table, "모듈", "모델·버전", "처리", "모델 출력", "판정 기준", "결과");
        for (ModuleBlock module : modules) {
            addTableRow(
                    table,
                    moduleLabel(module.name),
                    displayValue(module.value("Model").orElse(null)),
                    moduleProcessingStatus(module),
                    scoreOrMissing(module.value("Score").orElse(null)),
                    moduleThreshold(data, module),
                    detectedLabel(module.value("Detected").orElse(null))
            );
        }
        if (modules.isEmpty()) {
            addTableRow(table, VERDICT_NOT_PERFORMED, "기록 없음", VERDICT_NOT_PERFORMED, "기록 없음", "기록 없음", VERDICT_NOT_PERFORMED);
        }
        document.add(table);
    }

    private static void addPrimaryEvidenceTable(Document document, ReportData data) throws DocumentException {
        List<String> segments = sectionValues(data.section("Suspicious Segments"), "Suspicious Segment");
        if (!segments.isEmpty()) {
            addSuspiciousSegmentTable(document, data);
            return;
        }
        addTimelinePointTable(document, data);
    }

    private static void addRepresentativeFrameCards(Document document, ReportData data) throws DocumentException {
        Section section = data.section("Representative Frames");
        List<String> values = sectionValues(section, "Representative Frame");
        if (values.isEmpty()) {
            addAvailabilityNotice(document, "대표 프레임 없음");
            addAvailabilityNotice(document, "히트맵 결과 없음");
            return;
        }

        List<String> selected = values.stream().limit(3).toList();
        PdfPTable frames = reportTable(new float[]{1f, 1f, 1f});
        frames.setSplitLate(true);
        int renderedCount = 0;
        for (String value : selected) {
            Map<String, String> fields = parseFields(value);
            Image image = loadReportImage(field(fields, "imageUrl"));
            if (image == null) {
                continue;
            }
            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(LIGHT_BORDER);
            cell.setPadding(7);
            cell.setVerticalAlignment(Element.ALIGN_TOP);

            image.scaleToFit(135, 94);
            image.setAlignment(Image.ALIGN_CENTER);
            cell.addElement(image);

            String timestamp = field(fields, "timestamp");
            if ("-".equals(timestamp)) {
                timestamp = formatSeconds(field(fields, "timeSec"));
            }
            Paragraph caption = new Paragraph(
                    "시점 " + timestamp
                            + " · 프레임 " + field(fields, "frameNumber")
                            + "\n모델 출력 " + percentageText(field(fields, "score")),
                    font(8, Font.NORMAL, INK)
            );
            caption.setLeading(12);
            caption.setSpacingBefore(6);
            cell.addElement(caption);
            frames.addCell(cell);
            renderedCount++;
        }
        if (renderedCount == 0) {
            addAvailabilityNotice(document, "대표 프레임: 별첨 없음");
        } else {
            for (int index = renderedCount; index < 3; index++) {
                PdfPCell empty = new PdfPCell(new Phrase(""));
                empty.setBorder(Rectangle.NO_BORDER);
                frames.addCell(empty);
            }
            document.add(frames);
            addIncludedCountFootnote(document, section, renderedCount);
            addFootnote(document, "※ 대표 프레임은 분석 산출물로 포함된 실제 이미지만 표시한다.");
        }
        addAvailabilityNotice(document, "히트맵 결과 없음");
    }

    private static Image loadReportImage(String value) {
        String source = blankFallback(value, "-").trim();
        if ("-".equals(source) || "기록 없음".equals(source)) {
            return null;
        }
        try {
            if (source.startsWith("data:image/")) {
                int separator = source.indexOf(',');
                if (separator < 0) {
                    return null;
                }
                return Image.getInstance(Base64.getDecoder().decode(source.substring(separator + 1)));
            }
            URI uri = URI.create(source);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            return Image.getInstance(uri.toURL());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void addModelScoreBars(Document document, ReportData data) throws DocumentException {
        List<ModuleBlock> modules = moduleRows(data);
        if (modules.isEmpty()) {
            Paragraph notice = new Paragraph(
                    "해당 분석 미실시",
                    font(10, Font.NORMAL, SLATE)
            );
            notice.setSpacingAfter(10);
            document.add(notice);
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{1.25f, 3.8f, 0.55f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        table.setSplitLate(false);

        for (ModuleBlock module : modules) {
            int score = percent(module.value("Score").orElse("-"));
            PdfPCell label = noBorderCell(new Phrase(moduleLabel(module.name), font(10, Font.NORMAL, INK)));
            label.setMinimumHeight(22);
            table.addCell(label);

            PdfPCell bar = noBorderCell(new Phrase(" "));
            bar.setMinimumHeight(22);
            bar.setCellEvent(new ScoreBarCellEvent(score));
            table.addCell(bar);

            PdfPCell scoreCell = noBorderCell(new Phrase(String.valueOf(score), font(10, Font.BOLD, INK)));
            scoreCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(scoreCell);
        }

        document.add(table);
    }

    private static boolean hasDetailedEvidence(ReportData data) {
        return !sectionValues(data.section("Module Timeline Summaries"), "Module Timeline").isEmpty()
                || !sectionValues(data.section("Timeline Points"), "Timeline Point").isEmpty()
                || !sectionValues(data.section("Suspicious Segments"), "Suspicious Segment").isEmpty()
                || !sectionValues(data.section("Representative Frames"), "Representative Frame").isEmpty();
    }

    private static void addModuleTimelineSummary(Document document, ReportData data) throws DocumentException {
        Section section = data.section("Module Timeline Summaries");
        List<String> values = sectionValues(section, "Module Timeline");
        if (values.isEmpty()) {
            addAvailabilityNotice(document, "모듈별 타임라인 기록 없음");
            return;
        }

        PdfPTable table = reportTable(new float[]{1.05f, 1.35f, 0.75f, 0.75f, 0.8f, 1.1f});
        addTableHeader(table, "모듈", "모델", "영상 점수", "판정 기준", "탐지", "탐지 내역");
        for (String value : values) {
            Map<String, String> fields = parseFields(value);
            String points = field(fields, "points");
            String segments = field(fields, "segments");
            addTableRow(table,
                    moduleLabel(field(fields, "module")),
                    field(fields, "model"),
                    percentageText(field(fields, "videoScore")),
                    percentageText(field(fields, "threshold")),
                    detectedLabel(field(fields, "detected")),
                    "지점 " + blankFallback(points, "0") + " / 구간 " + blankFallback(segments, "0"));
        }
        document.add(table);
        addIncludedCountFootnote(document, section, values.size());
    }

    private static void addTimelinePointTable(Document document, ReportData data) throws DocumentException {
        Section section = data.section("Timeline Points");
        List<String> values = sectionValues(section, "Timeline Point");
        if (values.isEmpty()) {
            addAvailabilityNotice(document, "프레임·클립·프레임쌍 타임라인 지점 기록 없음");
            return;
        }

        PdfPTable table = reportTable(new float[]{1.15f, 0.8f, 1.2f, 0.75f, 2f});
        addTableHeader(table, "출처", "종류", "시점/구간", "위험도", "참조");
        for (String value : values) {
            Map<String, String> fields = parseFields(value);
            addTableRow(table,
                    moduleLabel(field(fields, "source")),
                    timelineKindLabel(field(fields, "kind")),
                    timeRange(field(fields, "start"), field(fields, "end")),
                    percentageText(field(fields, "score")),
                    field(fields, "reference"));
        }
        document.add(table);
        addIncludedCountFootnote(document, section, values.size());
    }

    private static void addSuspiciousSegmentTable(Document document, ReportData data) throws DocumentException {
        Section section = data.section("Suspicious Segments");
        List<String> values = sectionValues(section, "Suspicious Segment");
        if (values.isEmpty()) {
            addAvailabilityNotice(document, "의심 구간 기록 없음");
            return;
        }

        PdfPTable table = reportTable(new float[]{1.1f, 1.25f, 0.8f, 2.75f});
        addTableHeader(table, "출처", "구간", "최대 위험도", "탐지 사유");
        for (String value : values) {
            Map<String, String> fields = parseFields(value);
            addTableRow(table,
                    moduleLabel(field(fields, "source")),
                    timeRange(field(fields, "start"), field(fields, "end")),
                    percentageText(field(fields, "score")),
                    field(fields, "reason"));
        }
        document.add(table);
        addIncludedCountFootnote(document, section, values.size());
    }

    private static void addRepresentativeFrameTable(Document document, ReportData data) throws DocumentException {
        Section section = data.section("Representative Frames");
        List<String> values = sectionValues(section, "Representative Frame");
        if (values.isEmpty()) {
            addAvailabilityNotice(document, "대표 프레임 없음");
            return;
        }

        PdfPTable table = reportTable(new float[]{1.4f, 1.15f, 1.1f, 1.05f});
        addTableHeader(table, "시점", "프레임 번호", "위험도", "이미지 참조");
        for (String value : values) {
            Map<String, String> fields = parseFields(value);
            String timestamp = field(fields, "timestamp");
            if ("-".equals(timestamp)) {
                timestamp = formatSeconds(field(fields, "timeSec"));
            }
            addTableRow(table,
                    timestamp,
                    field(fields, "frameNumber"),
                    percentageText(field(fields, "score")),
                    booleanLabel(field(fields, "imageRegistered"), "등록됨", "없음"));
        }
        document.add(table);
        addIncludedCountFootnote(document, section, values.size());
        addFootnote(document, "※ 이미지 접근 URL은 만료될 수 있어 PDF에 기록하지 않고, 발행 당시 이미지 참조 등록 여부만 표시한다.");
    }

    private static void addAvailabilityNotice(Document document, String message) throws DocumentException {
        Paragraph notice = new Paragraph(message, font(9.5f, Font.NORMAL, SLATE));
        notice.setLeading(15);
        notice.setSpacingAfter(10);
        document.add(notice);
    }

    private static void addIncludedCountFootnote(Document document, Section section, int includedCount)
            throws DocumentException {
        int totalCount = parseInteger(rowValue(section, "Total Count"), includedCount);
        if (totalCount > includedCount) {
            addFootnote(document, "※ 위험도 기준 상위 " + includedCount + "건을 표시한다. 전체 분석 데이터: " + totalCount + "건");
        }
    }

    private static void addVerificationBlock(Document document, String qrContent, boolean issued)
            throws DocumentException {
        if (!issued) {
            PdfPTable placeholder = new PdfPTable(1);
            placeholder.setWidthPercentage(100);
            placeholder.setSpacingAfter(12);

            PdfPCell placeholderCell = new PdfPCell();
            placeholderCell.setBorder(Rectangle.BOX);
            placeholderCell.setBorderColor(LIGHT_BORDER);
            placeholderCell.setBackgroundColor(SURFACE);
            placeholderCell.setPadding(16);
            placeholderCell.setMinimumHeight(66);
            placeholderCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph message = new Paragraph(
                    "검증 QR과 URL은 발행 등록 후 생성된다.",
                    font(10.5f, Font.BOLD, SLATE)
            );
            message.setAlignment(Element.ALIGN_CENTER);
            placeholderCell.addElement(message);
            placeholder.addCell(placeholderCell);
            document.add(placeholder);
            return;
        }

        PdfPTable outer = new PdfPTable(new float[]{1.2f, 4.1f});
        outer.setWidthPercentage(100);
        outer.setSpacingAfter(12);

        PdfPCell qrCell = new PdfPCell();
        qrCell.setBorder(Rectangle.BOX);
        qrCell.setBorderColor(BORDER);
        qrCell.setPadding(6);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (qrContent != null && !qrContent.isBlank()) {
            Image qrImage = QrCodeImageWriter.createPdfImage(qrContent, 78);
            qrImage.setAlignment(Image.ALIGN_CENTER);
            qrCell.addElement(qrImage);
        } else {
            Paragraph noQr = new Paragraph("QR 없음", font(10, Font.BOLD, LIGHT));
            noQr.setAlignment(Element.ALIGN_CENTER);
            qrCell.addElement(noQr);
        }
        outer.addCell(qrCell);

        PdfPCell info = new PdfPCell();
        info.setBorder(Rectangle.BOX);
        info.setBorderColor(BORDER);
        info.setPadding(8);
        info.addElement(verificationLine("발행 상태", "기관 발행 등록 완료"));
        info.addElement(verificationLine("조회 방법", "QR 코드 스캔 또는 아래 URL 접속"));
        info.addElement(verificationLine("검증 URL", blankFallback(qrContent, "발행 URL 생성 오류")));
        info.addElement(verificationLine("검증 범위", "발행 등록정보 조회 (PDF 파일 자체는 미검사)"));
        outer.addCell(info);

        document.add(outer);
    }

    private static void addApprovalBlock(Document document, ReportData data, boolean issued) throws DocumentException {
        Paragraph title = new Paragraph("작성·검토 및 승인 정보", font(12, Font.BOLD, INK));
        title.setSpacingBefore(6);
        title.setSpacingAfter(4);
        document.add(title);

        String approvalTime = data.value("Review Approved At").orElse("-");
        String approvalInfo = issued
                ? "시스템 승인" + ("-".equals(approvalTime) ? "" : " · " + approvalTime)
                : "승인 전";
        addInfoGrid(document, List.of(
                row("작성자", actorSummary(data, "Analyst", "-")),
                row("검토자", actorSummary(data, "Reviewer", "미배정")),
                row("검토 결과", reviewStatusLabel(data.value("Review Status").orElse("NONE"))),
                row("승인 정보", approvalInfo),
                row("분석 완료", data.value("Analyzed At").orElse(data.value("Created At").orElse("-"))),
                row("발행 상태", issued ? "기관 발행 등록 완료" : "미발행 · 미리보기")
        ));
    }

    private static void addResponsibilityTable(Document document, ReportData data, boolean issued)
            throws DocumentException {
        PdfPTable table = reportTable(new float[]{1.05f, 1.9f, 1.35f, 1.85f, 1.45f});
        addTableHeader(table, "역할", "이름·소속·직위", "수행 행위", "상태", "수행 시각");
        addTableRow(
                table,
                "분석관",
                actorSummary(data, "Analyst", "기록 없음"),
                "분석 결과 작성",
                analysisStatusLabel(data.value("Analysis Status").orElse(null)),
                displayValue(data.value("Analyzed At").orElse(null))
        );
        addTableRow(
                table,
                "기술 검토자",
                actorSummary(data, "Reviewer", "미배정"),
                "분석 결과 기술 검토",
                reviewStatusLabel(data.value("Review Status").orElse("NONE")),
                displayValue(data.value("Review Approved At").orElse(null))
        );
        addTableRow(
                table,
                "발행 등록",
                issued ? "ForenShield AI 시스템" : "미발행",
                "PDF 발행 등록",
                issued ? "기관 발행 등록 완료" : "승인 전",
                issued ? displayValue(data.value("Review Approved At").orElse(null)) : "기록 없음"
        );
        document.add(table);
    }

    private static void addFullWidthValue(Document document, String value, boolean monospace)
            throws DocumentException {
        PdfPTable table = reportTable(new float[]{1f});
        PdfPCell cell = new PdfPCell(new Phrase(
                displayValue(value),
                monospace ? monoFont(9, Font.NORMAL, INK) : font(9, Font.NORMAL, INK)
        ));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setPadding(9);
        table.addCell(cell);
        document.add(table);
    }

    private static PdfPTable reportTable(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        table.setSplitLate(false);
        return table;
    }

    private static void addFileInfoCell(PdfPTable table, String title, Section section) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setPadding(10);
        cell.addElement(new Paragraph(title, font(11, Font.BOLD, INK)));
        cell.addElement(smallLine("증거번호", prefixedEvidenceId(rowValue(section, "Evidence ID"))));
        cell.addElement(smallLine("파일명", rowValue(section, "File Name")));
        cell.addElement(smallLine("파일 크기", rowValue(section, "File Size")));
        cell.addElement(smallLine("SHA-256", shorten(rowValue(section, "SHA-256"), 14, 10)));
        cell.addElement(smallLine("등록 일시", rowValue(section, "Uploaded At")));
        table.addCell(cell);
    }

    private static void addTableHeader(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font(9, Font.BOLD, INK)));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setBackgroundColor(SURFACE);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(BORDER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private static void addTableRow(PdfPTable table, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(blankFallback(value, "-"), font(8.5f, Font.NORMAL, INK)));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(BORDER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private static void addInfoLabel(PdfPTable table, String label) {
        PdfPCell cell = new PdfPCell(new Phrase(blankFallback(label, ""), font(9, Font.BOLD, INK)));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setBackgroundColor(SURFACE);
        cell.setPadding(6);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(cell);
    }

    private static void addInfoValue(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, font(9, Font.NORMAL, INK)));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setPadding(6);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(cell);
    }

    private static void addReasonRow(PdfPTable table, String label, String value) {
        PdfPCell left = noBorderCell(new Phrase(blankFallback(label, "-"), font(10, Font.BOLD, INK)));
        left.setPaddingBottom(8);
        table.addCell(left);

        PdfPCell right = noBorderCell(new Phrase(blankFallback(value, "-"), font(10, Font.NORMAL, INK)));
        right.setPaddingBottom(8);
        table.addCell(right);
    }

    private static Paragraph verificationLine(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.setLeading(16);
        paragraph.setSpacingAfter(4);
        paragraph.add(new Phrase(label + "  ", font(9, Font.BOLD, SLATE)));
        paragraph.add(new Phrase(blankFallback(value, "-"), font(9, Font.NORMAL, INK)));
        return paragraph;
    }

    private static Paragraph smallLine(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.setLeading(15);
        paragraph.setSpacingBefore(4);
        paragraph.add(new Phrase(label + "  ", font(8, Font.BOLD, MUTED)));
        paragraph.add(new Phrase(blankFallback(value, "-"), font(8, Font.NORMAL, INK)));
        return paragraph;
    }

    private static void addFootnote(Document document, String text) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, font(8.5f, Font.NORMAL, MUTED));
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        paragraph.setSpacingAfter(10);
        document.add(paragraph);
    }

    private static void addRule(Document document, Color color, float height, float spacingBefore, float spacingAfter)
            throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(spacingBefore);
        line.setSpacingAfter(spacingAfter);
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(height);
        cell.setBackgroundColor(color);
        line.addCell(cell);
        document.add(line);
    }

    private static PdfPCell noBorderCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private static PdfPCell noBorderCell(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private static Font font(float size, int style, Color color) {
        return new Font(DOCUMENT_BASE_FONT, size, style, color);
    }

    private static Font monoFont(float size, int style, Color color) {
        Font fallback = FontFactory.getFont(FontFactory.COURIER, size, style, color);
        return fallback == null ? new Font(Font.COURIER, size, style, color) : fallback;
    }

    private static BaseFont loadDocumentBaseFont() {
        String[] candidates = {
                "/System/Library/Fonts/AppleSDGothicNeo.ttc,0",
                "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/opentype/noto/NotoSansCJKkr-Regular.otf",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        };

        for (String candidate : candidates) {
            String filePath = candidate.contains(",")
                    ? candidate.substring(0, candidate.indexOf(','))
                    : candidate;
            if (!Files.exists(Paths.get(filePath))) {
                continue;
            }
            try {
                return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {
                // Try the next platform font.
            }
        }

        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 폰트 초기화에 실패했습니다.", ex);
        }
    }

    private static List<ModuleBlock> moduleRows(ReportData data) {
        return data.modules;
    }

    private static String riskLabel(ReportData data) {
        return switch (data.value("Risk Level").orElse("").trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> VERDICT_SIGNAL_DETECTED;
            case "LOW" -> VERDICT_NO_SIGNAL;
            default -> VERDICT_UNDETERMINED;
        };
    }

    private static String detectedLabel(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return VERDICT_UNDETERMINED;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return VERDICT_SIGNAL_DETECTED;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return VERDICT_NO_SIGNAL;
        }
        return VERDICT_UNDETERMINED;
    }

    private static String riskClassLabel(String value) {
        return switch (blankFallback(value, "").trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> "높음";
            case "MEDIUM" -> "중간";
            case "LOW" -> "낮음";
            default -> "기록 없음";
        };
    }

    private static String analysisStatusLabel(String value) {
        return switch (blankFallback(value, "").trim().toUpperCase(Locale.ROOT)) {
            case "COMPLETED" -> "분석 완료";
            case "FAILED" -> "분석 실패";
            case "PROCESSING", "RUNNING", "ANALYZING" -> "분석 중";
            case "PENDING", "QUEUED", "WAITING" -> "분석 대기";
            default -> "기록 없음";
        };
    }

    private static String manifestSignatureLabel(String value) {
        return switch (blankFallback(value, "").trim().toUpperCase(Locale.ROOT)) {
            case "VALID" -> "유효";
            case "INVALID" -> "검증 실패";
            case "FAILED" -> "서명 생성 실패";
            default -> "기록 없음";
        };
    }

    private static String cocChainLabel(String value) {
        return switch (blankFallback(value, "").trim().toUpperCase(Locale.ROOT)) {
            case "VALID" -> "유효";
            case "INVALID" -> "검증 실패";
            default -> "기록 없음";
        };
    }

    private static String countLabel(String value) {
        if (isMissingValue(value)) {
            return "기록 없음";
        }
        String normalized = value.trim();
        return normalized.endsWith("건") ? normalized : normalized + "건";
    }

    private static String evidenceBlockchainLabel(String status, String network) {
        String label = switch (blankFallback(status, "").trim().toUpperCase(Locale.ROOT)) {
            case "MATCHED" -> "일치";
            case "MISMATCHED" -> "불일치";
            case "PENDING" -> "등록 대기";
            case "FAILED" -> "등록 실패";
            default -> "미등록";
        };
        if (isMissingValue(network)) {
            return label;
        }
        String normalizedNetwork = network.trim();
        if ("local-simulated".equalsIgnoreCase(normalizedNetwork)) {
            return label + " (개발 검증용 기록)";
        }
        return label + " (" + normalizedNetwork + ")";
    }

    private static String expertReviewLabel(ReportData data) {
        String status = data.value("Analysis Status").orElse("");
        if ("FAILED".equalsIgnoreCase(status)) {
            return "분석 실패 사유 확인 필요";
        }
        return "사건 맥락과 함께 전문가 검토 권고";
    }

    private static String displayValue(String value) {
        if (value == null || value.isBlank()) {
            return "기록 없음";
        }
        String normalized = value.trim();
        if ("-".equals(normalized) || "null".equalsIgnoreCase(normalized)) {
            return "기록 없음";
        }
        return reportLanguage(normalized);
    }

    private static List<DisplayRow> presentRows(DisplayRow... candidates) {
        List<DisplayRow> rows = new ArrayList<>();
        for (DisplayRow candidate : candidates) {
            if (!isMissingValue(candidate.value())) {
                rows.add(candidate);
            }
        }
        return rows;
    }

    private static boolean isMissingValue(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim();
        return "-".equals(normalized)
                || "null".equalsIgnoreCase(normalized)
                || "기록 없음".equals(normalized);
    }

    private static String scoreOrMissing(String raw) {
        return parseDouble(raw) == null ? "기록 없음" : percentageText(raw);
    }

    private static String moduleProcessingStatus(ModuleBlock module) {
        boolean hasScore = parseDouble(module.value("Score").orElse(null)) != null;
        boolean hasDecision = module.value("Detected")
                .map(String::trim)
                .filter(value -> !value.isBlank() && !"null".equalsIgnoreCase(value))
                .isPresent();
        return hasScore || hasDecision ? "완료" : "기록 없음";
    }

    private static String analysisArea(String moduleName) {
        return isForgeryModule(moduleName) ? "영상 콘텐츠 위변조 분석" : "딥페이크 분석";
    }

    private static boolean isForgeryModule(String moduleName) {
        String normalized = blankFallback(moduleName, "").toLowerCase(Locale.ROOT);
        return normalized.contains("forgery")
                || normalized.contains("tamper")
                || normalized.contains("trufor")
                || normalized.contains("splicing")
                || normalized.contains("frame_edit")
                || normalized.contains("frameedit")
                || normalized.contains("re_encoding")
                || normalized.contains("reencoding");
    }

    private static String moduleThreshold(ReportData data, ModuleBlock module) {
        List<String> timelines = sectionValues(data.section("Module Timeline Summaries"), "Module Timeline");
        String targetKey = canonicalModuleKey(module.name);
        for (String value : timelines) {
            Map<String, String> fields = parseFields(value);
            String source = field(fields, "module");
            if (targetKey.equals(canonicalModuleKey(source))) {
                return scoreOrMissing(field(fields, "threshold"));
            }
        }
        return "기록 없음";
    }

    private static String canonicalModuleKey(String value) {
        String normalized = blankFallback(value, "").toLowerCase(Locale.ROOT);
        boolean forgery = isForgeryModule(normalized);
        if (forgery && (normalized.contains("spatial") || normalized.contains("trufor") || normalized.contains("frame"))) {
            return "forgery_spatial";
        }
        if (forgery && (normalized.contains("temporal") || normalized.contains("times"))) {
            return "forgery_temporal";
        }
        if (normalized.contains("xception") || normalized.contains("cnn")) {
            return "deepfake_cnn";
        }
        if (normalized.contains("temporal") || normalized.contains("times")) {
            return "deepfake_temporal";
        }
        if (normalized.contains("optical") || normalized.contains("gmflow") || normalized.contains("flow")) {
            return "deepfake_optical";
        }
        if (normalized.contains("fusion") || normalized.equals("deepfake")) {
            return "deepfake_fusion";
        }
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    private static String technicalValue(ReportData data, String key) {
        return displayValue(rowValue(data.section("Technical Metadata"), key));
    }

    private static String actorSummary(ReportData data, String prefix, String fallback) {
        List<String> parts = new ArrayList<>();
        data.value(prefix + " Name")
                .filter(value -> !value.isBlank() && !"-".equals(value))
                .ifPresent(parts::add);
        data.value(prefix + " Department")
                .filter(value -> !value.isBlank() && !"-".equals(value))
                .ifPresent(parts::add);
        data.value(prefix + " Position")
                .filter(value -> !value.isBlank() && !"-".equals(value))
                .ifPresent(parts::add);
        return parts.isEmpty() ? fallback : String.join(" / ", parts);
    }

    private static String reviewStatusLabel(String value) {
        return switch (blankFallback(value, "NONE").trim().toUpperCase(Locale.ROOT)) {
            case "REPORT_APPROVED" -> "기관 내부 절차상 최종 승인";
            case "REVIEW_COMPLETED" -> "검토 완료";
            case "REVIEW_ASSIGNED" -> "검토 중";
            case "REVIEW_REQUESTED" -> "검토자 배정 대기";
            case "REVIEW_SUPPLEMENT_REQUESTED", "SUPPLEMENT_REQUESTED",
                    "REVIEW_REVISION_REQUESTED", "REVISION_REQUESTED", "REVIEW_NEEDS_CHANGES" -> "보완 요청";
            default -> "검토 전";
        };
    }

    private static String compareVerdict(String value) {
        String normalized = blankFallback(value, "-").toLowerCase(Locale.ROOT);
        if (normalized.contains("tamper") || normalized.contains("mismatch") || normalized.contains("위변조")) {
            return "위변조 의심";
        }
        if (normalized.contains("match") || normalized.contains("일치")) {
            return "일치";
        }
        return blankFallback(value, "-");
    }

    private static String compareResult(String value) {
        String normalized = blankFallback(value, "-").toLowerCase(Locale.ROOT);
        if (normalized.contains("mismatch")) {
            return "불일치";
        }
        if (normalized.contains("match")) {
            return "일치";
        }
        if (normalized.contains("skipped")) {
            return "제외";
        }
        return blankFallback(value, "-");
    }

    private static List<String> sectionValues(Section section, String label) {
        if (section == null) {
            return List.of();
        }
        return section.rows.stream()
                .filter(row -> row.label.equals(label))
                .map(Row::value)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static Map<String, String> parseFields(String value) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return fields;
        }
        for (String part : value.split("\\|")) {
            String trimmed = part.trim();
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            fields.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
        }
        return fields;
    }

    private static String field(Map<String, String> fields, String key) {
        return blankFallback(fields.get(key), "-");
    }

    private static String timelineKindLabel(String value) {
        return switch (blankFallback(value, "-").trim().toUpperCase(Locale.ROOT)) {
            case "FRAME" -> "프레임";
            case "CLIP" -> "클립";
            case "PAIR" -> "프레임쌍";
            default -> blankFallback(value, "-");
        };
    }

    private static String timeRange(String start, String end) {
        String formattedStart = formatSeconds(start);
        String formattedEnd = formatSeconds(end);
        if (formattedStart.equals(formattedEnd)) {
            return formattedStart;
        }
        return formattedStart + " - " + formattedEnd;
    }

    private static String formatSeconds(String raw) {
        Double value = parseDouble(raw);
        if (value == null) {
            return "-";
        }
        double safe = Math.max(0.0, value);
        int hours = (int) (safe / 3600.0);
        int minutes = (int) ((safe % 3600.0) / 60.0);
        double seconds = safe % 60.0;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%02d:%02d:%05.2f", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%05.2f", minutes, seconds);
    }

    private static String percentageText(String raw) {
        Double value = parseDouble(raw);
        if (value == null) {
            return "-";
        }
        if (Math.abs(value) <= 1.0) {
            value *= 100.0;
        }
        double bounded = Math.max(0.0, Math.min(100.0, value));
        return String.format(Locale.ROOT, "%.1f%%", bounded);
    }

    private static String booleanLabel(String raw, String trueLabel, String falseLabel) {
        if ("true".equalsIgnoreCase(blankFallback(raw, ""))) {
            return trueLabel;
        }
        if ("false".equalsIgnoreCase(blankFallback(raw, ""))) {
            return falseLabel;
        }
        return VERDICT_UNDETERMINED;
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replace("%", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parseInteger(String raw, int fallback) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String moduleLabel(String value) {
        String normalized = blankFallback(value, "").toLowerCase(Locale.ROOT);
        if (normalized.contains("trufor") || normalized.contains("forgery_spatial")) {
            return "TruFor";
        }
        if (normalized.contains("forgery_temporal")) {
            return "TimeSFormer (내용 조작)";
        }
        if (normalized.contains("fusion") || normalized.equals("deepfake")) {
            return "Late Fusion";
        }
        if (normalized.contains("xception") || normalized.contains("cnn")) {
            return "Xception";
        }
        if (normalized.contains("times") || normalized.contains("temporal")) {
            return "TimeSFormer";
        }
        if (normalized.contains("gmflow") || normalized.contains("optical")) {
            return "GMFlow";
        }
        return blankFallback(value, "분석 모듈");
    }

    private static String moduleMetric(String value) {
        String label = moduleLabel(value);
        return switch (label) {
            case "Late Fusion" -> "종합 위험 프로파일";
            case "Xception" -> "얼굴 경계부·질감 패턴";
            case "TimeSFormer" -> "시간적 일관성";
            case "GMFlow" -> "움직임 벡터";
            default -> "분석 점수";
        };
    }

    private static String moduleNote(String value) {
        String label = moduleLabel(value);
        return switch (label) {
            case "Late Fusion" -> "최종 종합";
            case "Xception" -> "CNN";
            case "TimeSFormer" -> "클립";
            case "GMFlow" -> "프레임쌍";
            default -> "모듈";
        };
    }

    private static int percent(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) {
            return 0;
        }
        try {
            String cleaned = raw.replace("%", "").trim();
            double value = Double.parseDouble(cleaned);
            if (value <= 1d) {
                value *= 100d;
            }
            return Math.max(0, Math.min(100, (int) Math.round(value)));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String scoreText(String raw) {
        return String.valueOf(percent(raw));
    }

    private static String prefixedEvidenceId(String value) {
        String safe = displayValue(value);
        if ("기록 없음".equals(safe) || safe.startsWith("EVD-")) {
            return safe;
        }
        return "EVD-" + safe;
    }

    private static String reportLanguage(String value) {
        if (value == null) {
            return "기록 없음";
        }
        return value
                .replace("가 저장되었습니다", "가 확인되었습니다")
                .replace("이 저장되었습니다", "이 확인되었습니다")
                .replace("확인되지 않았습니다", "확인되지 않았다")
                .replace("확인되었습니다", "확인되었다")
                .replace("미제공", "기록 없음");
    }

    private static String shorten(String value, int head, int tail) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= head + tail + 3) {
            return trimmed;
        }
        return trimmed.substring(0, head) + "..." + trimmed.substring(trimmed.length() - tail);
    }

    private static String rowValue(Section section, String key) {
        if (section == null) {
            return "-";
        }
        return section.value(key).orElse("-");
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static DisplayRow row(String label, String value) {
        return new DisplayRow(label, value);
    }

    private static boolean isCompareReport(String title, ReportData data) {
        return title != null && title.toLowerCase(Locale.ROOT).contains("compare")
                || data.value("Compare ID").isPresent()
                || data.section("Comparison Results") != null;
    }

    private static final class ScoreBarCellEvent implements PdfPCellEvent {
        private final int score;

        private ScoreBarCellEvent(int score) {
            this.score = Math.max(0, Math.min(100, score));
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte canvas = canvases[PdfPTable.LINECANVAS];
            float left = position.getLeft() + 2;
            float right = position.getRight() - 2;
            float centerY = (position.getTop() + position.getBottom()) / 2f;
            float width = right - left;
            float height = 8f;

            canvas.saveState();
            canvas.setColorStroke(BORDER);
            canvas.rectangle(left, centerY - height / 2f, width, height);
            canvas.stroke();

            canvas.setColorFill(INK);
            canvas.rectangle(left, centerY - height / 2f, width * score / 100f, height);
            canvas.fill();
            canvas.restoreState();
        }
    }

    private static byte[] addPageFooters(byte[] pdfBytes) {
        try {
            PdfReader reader = new PdfReader(pdfBytes);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, output);
            int totalPages = reader.getNumberOfPages();

            for (int page = 1; page <= totalPages; page++) {
                Rectangle pageSize = reader.getPageSizeWithRotation(page);
                float left = pageSize.getLeft(54);
                float right = pageSize.getRight(54);
                float ruleY = pageSize.getBottom(44);
                float textY = pageSize.getBottom(26);
                PdfContentByte canvas = stamper.getOverContent(page);

                canvas.saveState();
                canvas.setColorStroke(INK);
                canvas.setLineWidth(0.6f);
                canvas.moveTo(left, ruleY + 3);
                canvas.lineTo(right, ruleY + 3);
                canvas.stroke();
                canvas.setLineWidth(1.4f);
                canvas.moveTo(left, ruleY);
                canvas.lineTo(right, ruleY);
                canvas.stroke();
                canvas.restoreState();

                ColumnText.showTextAligned(
                        canvas,
                        Element.ALIGN_LEFT,
                        new Phrase(
                                "본 문서는 ForenShield AI 시스템이 생성한 분석 결과보고서이며, AI 결과는 기술 참고자료이다.",
                                font(8, Font.NORMAL, MUTED)
                        ),
                        left,
                        textY,
                        0
                );
                ColumnText.showTextAligned(
                        canvas,
                        Element.ALIGN_RIGHT,
                        new Phrase("- " + page + " / " + totalPages + " -", font(8, Font.NORMAL, MUTED)),
                        right,
                        textY,
                        0
                );
            }

            stamper.close();
            reader.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 페이지 번호 적용에 실패했습니다.", ex);
        }
    }

    private record DisplayRow(String label, String value) {
    }

    private record SuspiciousSegmentSummary(String range, String reason, String score) {
    }

    private record Row(String label, String value) {
        Optional<String> valueFor(String key) {
            return label.equals(key) ? Optional.ofNullable(value) : Optional.empty();
        }
    }

    private static final class Section {
        private final String title;
        private final List<Row> rows = new ArrayList<>();

        private Section(String title) {
            this.title = title;
        }

        private Optional<String> value(String key) {
            return rows.stream()
                    .map(row -> row.valueFor(key))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }
    }

    private static final class ModuleBlock {
        private final String name;
        private final List<Row> rows = new ArrayList<>();

        private ModuleBlock(String name) {
            this.name = name;
        }

        private Optional<String> value(String key) {
            return rows.stream()
                    .map(row -> row.valueFor(key))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }
    }

    private static final class ReportData {
        private final Section root = new Section("Overview");
        private final List<Section> sections = new ArrayList<>();
        private final List<ModuleBlock> modules = new ArrayList<>();

        private static ReportData parse(List<String> lines) {
            ReportData data = new ReportData();
            Section currentSection = data.root;
            ModuleBlock currentModule = null;

            for (String rawLine : lines == null ? List.<String>of() : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isBlank()) {
                    currentModule = null;
                    continue;
                }
                if (line.startsWith("--- Module:") && line.endsWith("---")) {
                    String moduleName = line
                            .replace("--- Module:", "")
                            .replace("---", "")
                            .trim();
                    currentModule = new ModuleBlock(moduleName);
                    data.modules.add(currentModule);
                    continue;
                }
                if (line.startsWith("===") && line.endsWith("===")) {
                    String sectionTitle = line.replace("=", "").trim();
                    currentSection = new Section(sectionTitle);
                    data.sections.add(currentSection);
                    currentModule = null;
                    continue;
                }

                Row row = parseRow(line);
                if (currentModule != null) {
                    currentModule.rows.add(row);
                } else {
                    currentSection.rows.add(row);
                }
            }
            return data;
        }

        private Optional<String> value(String key) {
            Optional<String> rootValue = root.value(key);
            if (rootValue.isPresent()) {
                return rootValue;
            }
            return sections.stream()
                    .map(section -> section.value(key))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }

        private Section section(String title) {
            return sections.stream()
                    .filter(section -> section.title.equalsIgnoreCase(title))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record CompareRow(String label, String original, String candidate, String result) {
        private static CompareRow parse(String value) {
            String[] parts = value == null ? new String[0] : value.split("\\|");
            String label = parts.length > 0 ? parts[0].trim() : "-";
            String original = "-";
            String candidate = "-";
            String result = "-";
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.startsWith("original=")) {
                    original = normalizeSerializedValue(trimmed.substring("original=".length()));
                } else if (trimmed.startsWith("candidate=")) {
                    candidate = normalizeSerializedValue(trimmed.substring("candidate=".length()));
                } else if (trimmed.startsWith("result=")) {
                    result = normalizeSerializedValue(trimmed.substring("result=".length()));
                }
            }
            return new CompareRow(label, shorten(original, 18, 10), shorten(candidate, 18, 10), result);
        }

        private static String normalizeSerializedValue(String value) {
            if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
                return "-";
            }
            return value.trim();
        }
    }

    private static Row parseRow(String line) {
        int separator = line.indexOf(':');
        if (separator >= 0) {
            return new Row(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return new Row(line, "");
    }
}
