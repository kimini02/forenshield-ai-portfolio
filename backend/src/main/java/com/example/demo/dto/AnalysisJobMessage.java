package com.example.demo.dto;

import com.example.demo.dto.FrameAnalysisSpecDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisJobMessage {

    private Long analysisRequestId;
    private Long evidenceId;
    @JsonProperty("fileType")
    private String fileType;
    /** S3 object key for analysis copy (cases/.../copy/...) */
    private String filePath;
    /** Same as filePath — AI worker compatibility alias */
    private String s3ObjectKey;
    private String s3Bucket;
    private String s3Region;
    /** Presigned GET URL or s3:// URI for GPU worker download */
    private String presignedDownloadUrl;
    private String originalHash;
    /** Same as originalHash — AI worker compatibility alias */
    private String originalSha256;
    private String caseName;
    private String requestedAt;

    /** SK-401: AI 워커 프레임 분석 입력 스펙 */
    private FrameAnalysisSpecDto frameAnalysis;
}
