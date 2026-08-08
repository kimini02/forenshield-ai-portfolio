package com.example.demo.service.evidence;

import com.example.demo.dto.MediaMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FFmpeg 메타데이터 추출 기능을 수동으로 테스트하기 위한 유틸리티 클래스입니다.
 */
public class MediaServiceManualTest {

    public static void main(String[] args) {
        // 1. 설정 준비
        String ffmpegPath = "C:\\ffmpeg\\ffmpeg-8.1.1-essentials_build\\bin";
        ObjectMapper objectMapper = new ObjectMapper();
        MediaService mediaService = new MediaService(ffmpegPath, objectMapper);

        // 2. 테스트할 파일 경로 (사용자가 제공한 경로)
        Path testFilePath = Paths.get("C:\\Users\\user\\Videos\\Captures\\MATE - Chrome 2026-04-08 11-43-13.mp4");

        System.out.println("--- FFmpeg 메타데이터 추출 테스트 시작 ---");
        System.out.println("대상 파일: " + testFilePath.toAbsolutePath());

        try {
            // 3. 메타데이터 추출 실행
            MediaMetadata metadata = mediaService.extractMetadata(testFilePath);

            // 4. 결과 출력
            System.out.println("\n[추출 결과]");
            System.out.println("파일 타입: " + metadata.getType());
            System.out.println("재생 시간: " + metadata.getDuration() + " 초");
            System.out.println("코덱: " + metadata.getCodec());
            
            if ("video".equals(metadata.getType())) {
                System.out.println("해상도: " + metadata.getWidth() + "x" + metadata.getHeight());
                System.out.println("FPS: " + metadata.getFps());
                if (Boolean.TRUE.equals(metadata.getHasAudioTrack())) {
                    System.out.println("내장 오디오 샘플레이트: " + metadata.getSampleRate() + " Hz");
                    System.out.println("내장 오디오 채널 수: " + metadata.getChannels());
                }
            }

            System.out.println("\n추출 성공!");

        } catch (Exception e) {
            System.err.println("\n추출 실패!");
            System.err.println("에러 메시지: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n--- 테스트 종료 ---");
    }
}
