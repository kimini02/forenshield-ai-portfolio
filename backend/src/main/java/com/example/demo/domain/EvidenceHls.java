package com.example.demo.domain;

import com.example.demo.domain.enums.HlsStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 증거별 HLS 패키징 메타 (1:1). {@link Evidence}에 JPA 연관을 두지 않고 repository로만 조회한다.
 */
@Entity
@Table(name = "evidence_hls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceHls {

    @Id
    @Column(name = "evidence_id")
    private Long evidenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "hls_status", nullable = false, length = 20)
    private HlsStatus hlsStatus;

    @Column(name = "hls_storage_prefix", length = 500)
    private String hlsStoragePrefix;

    @Column(name = "hls_packaged_at")
    private LocalDateTime hlsPackagedAt;

    @Column(name = "content_key_enc")
    private byte[] contentKeyEnc;

    @Column(name = "hls_error", columnDefinition = "text")
    private String hlsError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static EvidenceHls createPending(Long evidenceId, LocalDateTime now) {
        EvidenceHls row = new EvidenceHls();
        row.evidenceId = evidenceId;
        row.hlsStatus = HlsStatus.PENDING;
        row.createdAt = now;
        row.updatedAt = now;
        return row;
    }

    public void markPackaging(LocalDateTime now) {
        this.hlsStatus = HlsStatus.PACKAGING;
        this.hlsError = null;
        this.updatedAt = now;
    }

    public void markReady(String storagePrefix, byte[] encryptedKey, LocalDateTime now) {
        this.hlsStatus = HlsStatus.READY;
        this.hlsStoragePrefix = storagePrefix;
        this.contentKeyEnc = encryptedKey;
        this.hlsPackagedAt = now;
        this.hlsError = null;
        this.updatedAt = now;
    }

    public void markFailed(String errorMessage, LocalDateTime now) {
        this.hlsStatus = HlsStatus.FAILED;
        this.hlsError = errorMessage;
        this.updatedAt = now;
    }

    public void rollbackStalePackaging(LocalDateTime now) {
        this.hlsStatus = HlsStatus.PENDING;
        this.updatedAt = now;
    }

    public void requeueForPackaging(LocalDateTime now) {
        this.hlsStatus = HlsStatus.PENDING;
        this.hlsError = null;
        this.updatedAt = now;
    }
}
