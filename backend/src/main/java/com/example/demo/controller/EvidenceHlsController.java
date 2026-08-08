package com.example.demo.controller;

import com.example.demo.config.OpenApiConfig;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.evidence.hls.EvidenceHlsStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Evidence HLS", description = "증거 HLS 스트림 API (BE 프록시)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping(EvidenceApiPaths.BASE)
@RequiredArgsConstructor
public class EvidenceHlsController {

    private static final MediaType M3U8 = MediaType.parseMediaType("application/vnd.apple.mpegurl");
    private static final MediaType MPEG_TS = MediaType.parseMediaType("video/mp2t");

    private final EvidenceHlsStreamService hlsStreamService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "HLS master manifest", description = "AES-128 HLS master.m3u8 (키·세그먼트 URI는 BE 프록시 경로로 재작성)")
    @GetMapping(value = "/{evidenceId}/hls/master.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> getMasterManifest(
            @PathVariable Long evidenceId,
            @RequestParam(value = "streamToken", required = false) String streamToken
    ) {
        var user = authUserResolver.requireCurrentUser();
        String manifest = hlsStreamService.loadMasterManifest(user, evidenceId, streamToken);
        return ResponseEntity.ok()
                .contentType(M3U8)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(manifest);
    }

    @Operation(
            summary = "HLS AES-128 content key",
            description = "16바이트 AES-128 키. JWT + streamToken + X-Step-Up-Token 필수."
    )
    @GetMapping(value = "/{evidenceId}/hls/key", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getContentKey(
            @PathVariable Long evidenceId,
            @RequestParam(value = "streamToken", required = false) String streamToken,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        var user = authUserResolver.requireCurrentUser();
        byte[] key = hlsStreamService.loadContentKey(user, evidenceId, streamToken, stepUpToken);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(key.length)
                .body(key);
    }

    @Operation(summary = "HLS 세그먼트", description = "암호화 .ts 세그먼트 BE 프록시 (S3 URL 미노출)")
    @GetMapping(value = "/{evidenceId}/hls/segments/{fileName}", produces = "video/mp2t")
    public ResponseEntity<byte[]> getSegment(
            @PathVariable Long evidenceId,
            @PathVariable String fileName,
            @RequestParam(value = "streamToken", required = false) String streamToken
    ) {
        var user = authUserResolver.requireCurrentUser();
        byte[] segment = hlsStreamService.loadSegment(user, evidenceId, fileName, streamToken);
        return ResponseEntity.ok()
                .contentType(MPEG_TS)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(segment.length)
                .body(segment);
    }
}
