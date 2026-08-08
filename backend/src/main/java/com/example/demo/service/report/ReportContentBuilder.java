package com.example.demo.service.report;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.dto.ClipRiskDto;
import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.PairRiskDto;
import com.example.demo.dto.RepresentativeFrameDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import com.example.demo.dto.compare.CompareFileInfoDto;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.dto.detail.ModuleTimelineDto;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.analysis.VideoModuleDetailsReader;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportContentBuilder {

    private static final String VIDEO_TIMELINE_MODULE = "video_timeline";
    private static final int MAX_EVIDENCE_ITEMS = 4;
    private static final int MAX_MODULE_TIMELINES = 4;
    private static final int MAX_TIMELINE_POINTS = 4;
    private static final int MAX_SUSPICIOUS_SEGMENTS = 4;
    private static final int MAX_REPRESENTATIVE_FRAMES = 3;

    private final CaseProfileRepository caseProfileRepository;
    private final EvidenceMetadataRepository evidenceMetadataRepository;
    private final UserRepository userRepository;
    private final VideoModuleDetailsReader videoModuleDetailsReader;
    private final ReportIntegritySnapshotService reportIntegritySnapshotService;

    public List<String> buildEvidenceLines(
            Evidence evidence,
            AnalysisRequest request,
            AnalysisResult result,
            List<AnalysisModuleResult> modules
    ) {
        List<String> lines = new ArrayList<>();
        appendEvidenceContext(lines, evidence);
        lines.add("Evidence ID: " + evidence.getEvidenceId());
        lines.add("File Name: " + evidence.getFileName());
        lines.add("File Type: " + valueOrDash(evidence.getFileType()));
        lines.add("MIME Type: " + valueOrDash(evidence.getMimeType()));
        lines.add("File Size: " + valueOrDash(evidence.getFileSize()));
        lines.add("Uploaded At: " + ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()));
        lines.add("Hash Algorithm: " + valueOrDash(evidence.getHashAlgorithm()));
        lines.add("SHA-256: " + evidence.getOriginalHashValue());
        lines.add("Analysis Request ID: " + valueOrDash(request.getAnalysisRequestId()));
        lines.add("Analysis Result ID: " + valueOrDash(result.getAnalysisResultId()));
        lines.add("Analysis Status: " + request.getStatus());
        lines.add("Requested At: " + ApiDateTimeFormatter.formatUtc(request.getRequestedAt()));
        lines.add("Started At: " + ApiDateTimeFormatter.formatUtc(request.getStartedAt()));
        lines.add("Completed At: " + ApiDateTimeFormatter.formatUtc(request.getCompletedAt()));
        if (request.getErrorCode() != null && !request.getErrorCode().isBlank()) {
            lines.add("Analysis Error Code: " + request.getErrorCode().trim());
        }
        if (request.getErrorMessage() != null && !request.getErrorMessage().isBlank()) {
            lines.add("Analysis Error Message: " + fieldValue(request.getErrorMessage()));
        }
        lines.add("Risk Level: " + (result.getRiskLevel() == null ? "-" : result.getRiskLevel()));
        lines.add("Risk Score: " + (result.getRiskScore() == null ? "-" : result.getRiskScore()));
        lines.add("Confidence: " + (result.getConfidenceScore() == null ? "-" : result.getConfidenceScore()));
        lines.add("Summary: " + (result.getSummary() == null ? "-" : result.getSummary()));
        lines.add("Analyzed At: " + ApiDateTimeFormatter.formatUtc(result.getAnalyzedAt()));

        for (AnalysisModuleResult module : modules) {
            if (VIDEO_TIMELINE_MODULE.equalsIgnoreCase(module.getModuleName())) {
                continue;
            }
            lines.add("--- Module: " + module.getModuleName() + " ---");
            lines.add("Model: " + modelLabel(module));
            lines.add("Detected: " + module.getDetected());
            lines.add("Score: " + module.getScore());
            lines.add("Confidence: " + module.getConfidence());
        }
        appendTechnicalMetadata(lines, evidence.getEvidenceId());
        lines.addAll(reportIntegritySnapshotService.toReportLines(evidence));
        appendVisualizationContext(lines, modules);
        return lines;
    }

    private void appendTechnicalMetadata(List<String> lines, Long evidenceId) {
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);
        lines.add(" ");
        lines.add("=== Technical Metadata ===");
        if (metadata == null) {
            lines.add("Metadata Status: 해당 분석 미실시");
            return;
        }
        lines.add("Metadata Status: " + valueOrDash(metadata.getExtractionStatus()));
        lines.add("Resolution: " + resolution(metadata));
        lines.add("Duration Seconds: " + valueOrDash(metadata.getDurationSec()));
        lines.add("FPS: " + valueOrDash(metadata.getFps()));
        lines.add("Video Codec: " + valueOrDash(metadata.getCodec()));
        lines.add("Audio Sample Rate: " + valueOrDash(metadata.getSampleRate()));
        lines.add("Audio Channels: " + valueOrDash(metadata.getChannels()));
    }

    private String resolution(EvidenceMetadata metadata) {
        if (metadata.getWidth() == null || metadata.getHeight() == null) {
            return "기록 없음";
        }
        return metadata.getWidth() + " x " + metadata.getHeight();
    }

    private String modelLabel(AnalysisModuleResult module) {
        String name = valueOrDash(module.getModelName());
        String version = valueOrDash(module.getModelVersion());
        if ("-".equals(version)) {
            return name;
        }
        return name + " v" + version;
    }

    private void appendVisualizationContext(List<String> lines, List<AnalysisModuleResult> modules) {
        VideoModuleDetailsReader.VisualizationData visualization = videoModuleDetailsReader.readVisualization(modules);
        appendEvidenceItems(lines, visualization.evidenceItems());
        appendModuleTimelines(lines, visualization.moduleTimelines());
        appendTimelinePoints(lines, visualization);
        appendSuspiciousSegments(lines, visualization);
        appendRepresentativeFrames(lines, visualization.representativeFrames());
    }

    private void appendEvidenceItems(List<String> lines, List<String> items) {
        List<String> safeItems = items == null ? List.of() : items.stream()
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        lines.add(" ");
        lines.add("=== Evidence Items ===");
        lines.add("Total Count: " + safeItems.size());
        safeItems.stream()
                .limit(MAX_EVIDENCE_ITEMS)
                .forEach(item -> lines.add("Evidence Item: " + fieldValue(item)));
    }

    private void appendModuleTimelines(List<String> lines, List<ModuleTimelineDto> timelines) {
        List<ModuleTimelineDto> safeTimelines = timelines == null ? List.of() : timelines;
        lines.add(" ");
        lines.add("=== Module Timeline Summaries ===");
        lines.add("Total Count: " + safeTimelines.size());
        safeTimelines.stream()
                .limit(MAX_MODULE_TIMELINES)
                .forEach(timeline -> lines.add("Module Timeline: "
                        + "module=" + fieldValue(timeline.getModule())
                        + " | model=" + fieldValue(modelLabel(timeline))
                        + " | videoScore=" + timeline.getVideoScore()
                        + " | threshold=" + timeline.getThreshold()
                        + " | detected=" + timeline.isDetected()
                        + " | points=" + timelinePointCount(timeline)
                        + " | segments=" + sizeOf(timeline.getSuspiciousSegments())));
    }

    private void appendTimelinePoints(
            List<String> lines,
            VideoModuleDetailsReader.VisualizationData visualization
    ) {
        List<TimelinePoint> points = buildTimelinePoints(visualization);
        lines.add(" ");
        lines.add("=== Timeline Points ===");
        lines.add("Total Count: " + points.size());
        points.stream()
                .sorted(Comparator.comparingDouble(TimelinePoint::score).reversed())
                .limit(MAX_TIMELINE_POINTS)
                .forEach(point -> lines.add("Timeline Point: "
                        + "source=" + fieldValue(point.source())
                        + " | kind=" + point.kind()
                        + " | start=" + point.startTime()
                        + " | end=" + point.endTime()
                        + " | score=" + point.score()
                        + " | reference=" + fieldValue(point.reference())));
    }

    private List<TimelinePoint> buildTimelinePoints(
            VideoModuleDetailsReader.VisualizationData visualization
    ) {
        List<TimelinePoint> points = new ArrayList<>();
        List<ModuleTimelineDto> moduleTimelines = visualization.moduleTimelines();
        if (moduleTimelines != null && !moduleTimelines.isEmpty()) {
            moduleTimelines.forEach(timeline -> appendModuleTimelinePoints(points, timeline));
            appendFramePoints(points, "전체 프레임", visualization.frameRisks());
            return points;
        }

        appendFramePoints(points, "전체 프레임", visualization.frameRisks());
        appendClipPoints(points, "TimeSFormer", visualization.clipRisks());
        appendPairPoints(points, "GMFlow", visualization.pairRisks());
        return points;
    }

    private void appendModuleTimelinePoints(List<TimelinePoint> points, ModuleTimelineDto timeline) {
        String source = valueOrDash(timeline.getModule());
        appendFramePoints(points, source, timeline.getFrameRisks());
        appendClipPoints(points, source, timeline.getClipRisks());
        appendPairPoints(points, source, timeline.getPairRisks());
    }

    private void appendFramePoints(List<TimelinePoint> points, String source, List<FrameRiskDto> frameRisks) {
        if (frameRisks == null) {
            return;
        }
        frameRisks.forEach(frame -> points.add(new TimelinePoint(
                source,
                "FRAME",
                frame.getTimestampSec(),
                frame.getTimestampSec(),
                frame.getRiskScore(),
                "프레임 " + frame.getFrameIndex()
        )));
    }

    private void appendClipPoints(List<TimelinePoint> points, String source, List<ClipRiskDto> clipRisks) {
        if (clipRisks == null) {
            return;
        }
        clipRisks.forEach(clip -> points.add(new TimelinePoint(
                source,
                "CLIP",
                clip.getStartTimeSec(),
                clip.getEndTimeSec(),
                clip.getRiskScore(),
                "클립 " + clip.getClipIndex() + " / 프레임 "
                        + clip.getStartFrameIndex() + "-" + clip.getEndFrameIndex()
        )));
    }

    private void appendPairPoints(List<TimelinePoint> points, String source, List<PairRiskDto> pairRisks) {
        if (pairRisks == null) {
            return;
        }
        pairRisks.forEach(pair -> points.add(new TimelinePoint(
                source,
                "PAIR",
                pair.getTimestampSec(),
                pair.getTimestampSec(),
                pair.getRiskScore(),
                "프레임쌍 " + pair.getPairIndex() + " / 프레임 "
                        + pair.getFrameIndexA() + "-" + pair.getFrameIndexB()
                        + (pair.getMotionMagnitude() == null ? "" : " / 움직임 " + pair.getMotionMagnitude())
        )));
    }

    private void appendSuspiciousSegments(
            List<String> lines,
            VideoModuleDetailsReader.VisualizationData visualization
    ) {
        Map<String, SegmentEntry> segments = new LinkedHashMap<>();
        addSegments(segments, "전체", visualization.suspiciousSegments());
        addSegments(segments, "TimeSFormer", visualization.temporalSuspiciousSegments());
        addSegments(segments, "GMFlow", visualization.opticalSuspiciousSegments());
        if (visualization.moduleTimelines() != null) {
            visualization.moduleTimelines().forEach(timeline ->
                    addSegments(segments, valueOrDash(timeline.getModule()), timeline.getSuspiciousSegments()));
        }

        List<SegmentEntry> ordered = segments.values().stream()
                .sorted(Comparator.comparingDouble(SegmentEntry::maxRiskScore).reversed())
                .toList();
        lines.add(" ");
        lines.add("=== Suspicious Segments ===");
        lines.add("Total Count: " + ordered.size());
        ordered.stream()
                .limit(MAX_SUSPICIOUS_SEGMENTS)
                .forEach(segment -> lines.add("Suspicious Segment: "
                        + "source=" + fieldValue(segment.source())
                        + " | start=" + segment.startTime()
                        + " | end=" + segment.endTime()
                        + " | score=" + segment.maxRiskScore()
                        + " | reason=" + fieldValue(segment.reason())));
    }

    private void addSegments(
            Map<String, SegmentEntry> target,
            String source,
            List<SuspiciousSegmentDto> segments
    ) {
        if (segments == null) {
            return;
        }
        for (SuspiciousSegmentDto segment : segments) {
            SegmentEntry entry = new SegmentEntry(
                    source,
                    segment.getStartTime(),
                    segment.getEndTime(),
                    segment.getMaxRiskScore(),
                    segment.getReason()
            );
            String key = source + "|" + entry.startTime() + "|" + entry.endTime() + "|" + entry.maxRiskScore();
            target.putIfAbsent(key, entry);
        }
    }

    private void appendRepresentativeFrames(List<String> lines, List<RepresentativeFrameDto> frames) {
        List<RepresentativeFrameDto> safeFrames = frames == null ? List.of() : frames.stream()
                .filter(frame -> frame != null)
                .sorted(Comparator.comparingDouble(this::representativeFrameScore).reversed())
                .toList();
        lines.add(" ");
        lines.add("=== Representative Frames ===");
        lines.add("Total Count: " + safeFrames.size());
        safeFrames.stream()
                .limit(MAX_REPRESENTATIVE_FRAMES)
                .forEach(frame -> lines.add("Representative Frame: "
                        + "timeSec=" + nullableNumber(frame.getTimeSec())
                        + " | timestamp=" + fieldValue(frame.getTimestamp())
                        + " | frameNumber=" + nullableNumber(frame.getFrameNumber())
                        + " | score=" + nullableNumber(frame.getScore())
                        + " | imageRegistered=" + (frame.getImageUrl() != null && !frame.getImageUrl().isBlank())
                        + " | imageUrl=" + fieldValue(frame.getImageUrl())));
    }

    private String modelLabel(ModuleTimelineDto timeline) {
        String name = valueOrDash(timeline.getModelName());
        String version = valueOrDash(timeline.getModelVersion());
        if ("-".equals(version)) {
            return name;
        }
        return name + " v" + version;
    }

    private int timelinePointCount(ModuleTimelineDto timeline) {
        return sizeOf(timeline.getFrameRisks()) + sizeOf(timeline.getClipRisks()) + sizeOf(timeline.getPairRisks());
    }

    private int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private double representativeFrameScore(RepresentativeFrameDto frame) {
        return frame.getScore() == null ? -1.0 : frame.getScore();
    }

    private String nullableNumber(Number value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String fieldValue(Object value) {
        String text = valueOrDash(value);
        return text.replace("|", "/").replaceAll("\\s+", " ").trim();
    }

    private record TimelinePoint(
            String source,
            String kind,
            double startTime,
            double endTime,
            double score,
            String reference
    ) {
    }

    private record SegmentEntry(
            String source,
            double startTime,
            double endTime,
            double maxRiskScore,
            String reason
    ) {
    }

    private void appendEvidenceContext(List<String> lines, Evidence evidence) {
        String caseKey = EvidenceCaseIdResolver.resolve(evidence);
        CaseProfile profile = caseProfileRepository
                .findByUploaderIdAndCaseKey(evidence.getUploaderId(), caseKey)
                .orElse(null);
        User analyst = resolveUser(evidence.getUploaderId());
        User reviewer = resolveUser(profile == null ? null : profile.getReviewerId());

        lines.add("Case Name: " + valueOrDash(evidence.getCaseName()));
        lines.add("Case Number: " + valueOrDash(evidence.getCaseNumber()));
        appendUserContext(lines, "Analyst", analyst, "-");
        appendUserContext(lines, "Reviewer", reviewer, "미배정");
        lines.add("Review Status: " + (profile == null || profile.getReviewStatus() == null
                ? "NONE"
                : profile.getReviewStatus().name()));
        if (profile != null && profile.getReviewApprovedAt() != null) {
            lines.add("Review Approved At: " + ApiDateTimeFormatter.formatUtc(profile.getReviewApprovedAt()));
        }
    }

    private User resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElse(null);
    }

    private void appendUserContext(List<String> lines, String prefix, User user, String nameFallback) {
        lines.add(prefix + " Name: " + (user == null ? nameFallback : valueOrDash(user.getName())));
        if (user == null) {
            return;
        }
        if (user.getDepartment() != null && !user.getDepartment().isBlank()) {
            lines.add(prefix + " Department: " + user.getDepartment().trim());
        }
        if (user.getPosition() != null && !user.getPosition().isBlank()) {
            lines.add(prefix + " Position: " + user.getPosition().trim());
        }
    }

    private String valueOrDash(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "-" : text;
    }

    public List<String> buildCompareLines(
            CompareVerification verification,
            Evidence originalEvidence,
            CompareFileInfoDto originalInfo,
            CompareFileInfoDto candidateInfo,
            List<CompareItemDto> items
    ) {
        List<String> lines = new ArrayList<>();
        appendEvidenceContext(lines, originalEvidence);
        lines.add("Compare ID: " + verification.getCompareId());
        lines.add("Verdict: " + verification.getVerdict());
        lines.add("Match Count: " + verification.getMatchCount());
        lines.add("Mismatch Count: " + verification.getMismatchCount());
        lines.add("Skipped Count: " + verification.getSkippedCount());
        lines.add("Created At: " + ApiDateTimeFormatter.formatUtc(verification.getCreatedAt()));
        lines.add(" ");
        lines.add("=== Original File Information ===");
        appendCompareFileInfoLines(lines, originalInfo);
        lines.add(" ");
        lines.add("=== Candidate File Information ===");
        appendCompareFileInfoLines(lines, candidateInfo);
        lines.add(" ");
        lines.add("=== Comparison Results ===");
        for (CompareItemDto item : items) {
            lines.add(item.getLabel() + " | original=" + item.getOriginalValue()
                    + " | candidate=" + item.getCandidateValue()
                    + " | result=" + item.getResult());
        }
        return lines;
    }

    private void appendCompareFileInfoLines(List<String> lines, CompareFileInfoDto info) {
        if (info.getEvidenceId() != null) {
            lines.add("Evidence ID: " + info.getEvidenceId());
        }
        if (info.getCompareId() != null) {
            lines.add("Compare ID: " + info.getCompareId());
        }
        lines.add("File Name: " + info.getFileName());
        lines.add("File Size: " + info.getFileSize());
        lines.add("SHA-256: " + info.getSha256());
        if (info.getCaseName() != null) {
            lines.add("Case Name: " + info.getCaseName());
        }
        if (info.getCaseNumber() != null) {
            lines.add("Case Number: " + info.getCaseNumber());
        }
        if (info.getFileType() != null) {
            lines.add("File Type: " + info.getFileType());
        }
        if (info.getMimeType() != null) {
            lines.add("MIME Type: " + info.getMimeType());
        }
        if (info.getUploadedAt() != null) {
            lines.add("Uploaded At: " + info.getUploadedAt());
        }
    }
}
