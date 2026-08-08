package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HlsPlaybackDto {

    /** BE HLS manifest 경로 (쿼리 streamToken은 FE에서 부착) */
    private String manifestPath;
    private String hlsStatus;
    private String streamToken;
    /** stream token TTL (초) */
    private long expiresIn;
}
