package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaMetadata {
    /** MVP: always video (embedded audio track metadata optional) */
    private String type;
    private Double duration;
    private String codec;
    private Integer width;
    private Integer height;
    private Double fps;
    /** Embedded audio track (within video file) */
    private Integer sampleRate;
    private Integer channels;
    private Boolean hasAudioTrack;
    private String ffprobeJson;
}
