package com.example.demo.controller;

import com.example.demo.config.OpenApiConfig;
import com.example.demo.dto.AnalysisStatusResponse;
import com.example.demo.dto.FileUploadResponse;
import com.example.demo.dto.StartAnalysisRequest;
import com.example.demo.dto.StartAnalysisResponse;
import com.example.demo.dto.detail.EvidenceDetailResponse;
import com.example.demo.dto.readiness.EvidenceReadinessResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.analysis.AnalysisCancelService;
import com.example.demo.service.analysis.AnalysisService;
import com.example.demo.service.analysis.AnalysisStatusService;
import com.example.demo.service.evidence.EvidenceCancelService;
import com.example.demo.service.evidence.EvidenceDetailService;
import com.example.demo.service.evidence.FileService;
import com.example.demo.service.integrity.EvidenceIntegrityResult;
import com.example.demo.service.integrity.IntegrityVerificationService;
import com.example.demo.service.readiness.EvidenceReadinessService;
import com.example.demo.service.auth.StepUpAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Evidence", description = "증거 관련 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping(EvidenceApiPaths.BASE)
@RequiredArgsConstructor
public class EvidenceController {

    private final FileService fileService;
    private final AnalysisService analysisService;
    private final EvidenceDetailService evidenceDetailService;
    private final EvidenceCancelService evidenceCancelService;
    private final AnalysisCancelService analysisCancelService;
    private final AnalysisStatusService analysisStatusService;
    private final IntegrityVerificationService integrityVerificationService;
    private final AuthUserResolver authUserResolver;
    private final EvidenceReadinessService evidenceReadinessService;
    private final StepUpAuthService stepUpAuthService;

    @Operation(summary = "파일 업로드", description = "파일을 서버에 업로드하고 SHA-256 해시를 생성합니다.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse upload(
            @Parameter(description = "업로드할 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "사건명") @RequestParam(value = "caseName", required = false) String caseName,
            @Parameter(description = "사건번호 (선택, 미입력 시 사건명과 동일)") @RequestParam(value = "caseNumber", required = false) String caseNumber
    ) {
        return fileService.upload(
                file,
                caseName,
                caseNumber,
                authUserResolver.requireCurrentUser().getUserId()
        );
    }

    @Operation(summary = "업로드 취소", description = "분석 시작 전 업로드된 증거를 취소하고 DB·S3에서 삭제합니다.")
    @DeleteMapping("/{evidenceId}")
    public ResponseEntity<Void> cancelUpload(@PathVariable Long evidenceId) {
        evidenceCancelService.cancelUpload(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "분석 상태 조회", description = "증거의 큐 대기·진행·완료·실패 상태와 진행률을 조회합니다. FAILED 시 errorCode·errorMessage 포함.")
    @GetMapping("/{evidenceId}/analysis-status")
    public AnalysisStatusResponse getAnalysisStatus(@PathVariable Long evidenceId) {
        return analysisStatusService.getStatus(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }

    @Operation(summary = "증거 초기화", description = "메인 화면 초기화 시 증거와 연결된 분석 요청을 모두 삭제하고 DB·S3에서 제거합니다.")
    @DeleteMapping("/{evidenceId}/reset")
    public ResponseEntity<Void> resetEvidence(@PathVariable Long evidenceId) {
        evidenceCancelService.resetEvidence(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "분석 중단", description = "대기·진행 중인 분석 작업만 중단합니다. 원본 증거는 DB·S3에 유지됩니다.")
    @DeleteMapping("/{evidenceId}/analysis")
    public ResponseEntity<Void> cancelAnalysis(@PathVariable Long evidenceId) {
        analysisCancelService.cancelAnalysis(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
        return ResponseEntity.noContent().build();
    }

// 클라이언트 요청
// → ① JWT로 로그인 사용자 확인
//   → ② X-Step-Up-Token 검증 (없으면 403)
//   → ③ 무결성 검증
//   → ④ 상세 데이터 반환
    @Operation(
            summary = "증거 상세",
            description = "증거 ID로 상세 분석·메타데이터·무결성 정보를 조회합니다. X-Step-Up-Token 헤더 필수. RQ-SEC-153: 조회 시 무결성 실패면 보안 알림 자동 발송."
    )
    @GetMapping("/{evidenceId}/detail")
    public EvidenceDetailResponse getEvidenceDetail(
            @PathVariable Long evidenceId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        var user = authUserResolver.requireCurrentUser();
        stepUpAuthService.requireValidStepUp(user, stepUpToken);
        EvidenceIntegrityResult integrity = integrityVerificationService.verifyAndNotifySecurityIssues(user, evidenceId);
        return evidenceDetailService.getEvidenceDetail(user, integrity.evidence(), integrity.verification());
    }

    @Operation(summary = "분석 시작", description = "업로드된 증거에 대한 분석을 요청합니다. evidenceId(단건) 또는 evidenceIds(복수) 중 하나를 사용합니다.")
    @PostMapping("/analyze")
    public StartAnalysisResponse startAnalysis(@RequestBody StartAnalysisRequest request) {
        return analysisService.startAnalysis(
                authUserResolver.requireCurrentUser(),
                request
        );
    }

    @Operation(summary = "분석 적합성 조회", description = "업로드 시 저장된 readiness 스냅샷(ffprobe 기반 또는 프레임 검사 결과)을 조회합니다.")
    @GetMapping("/{evidenceId}/readiness")
    public EvidenceReadinessResponse getReadiness(
            @Parameter(description = "증거 ID (upload 응답의 evidenceId)", example = "1")
            @PathVariable Long evidenceId
    ) {
        return evidenceReadinessService.getReadiness(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }

    @Operation(summary = "프레임 화질 검사", description = "S3 원본을 내려받아 video_readiness.py 로 blur/blockiness/FFT 를 샘플링하고 readiness_json 을 갱신합니다.")
    @PostMapping("/{evidenceId}/readiness-check")
    public EvidenceReadinessResponse runReadinessCheck(
            @Parameter(description = "증거 ID (upload 응답의 evidenceId)", example = "1")
            @PathVariable Long evidenceId
    ) {
        return evidenceReadinessService.runFrameReadinessCheck(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }
}
