package com.example.demo.service.evidence;

import com.example.demo.exception.FileSizeExceededException;
import com.example.demo.exception.UnsupportedFileTypeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileValidationServiceTest {

    private final FileValidationService fileValidationService = new FileValidationService();

    @Test
    @DisplayName("정상적인 영상 파일 검증 성공 (MP4)")
    void validateSuccess_VideoMp4() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[1024]);
        assertDoesNotThrow(() -> fileValidationService.validate(file));
    }

    @Test
    @DisplayName("정상적인 영상 파일 검증 성공 (MOV)")
    void validateSuccess_VideoMov() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mov", "video/quicktime", new byte[1024]);
        assertDoesNotThrow(() -> fileValidationService.validate(file));
    }

    @Test
    @DisplayName("이미지 파일 업로드 시 실패")
    void validateFail_ImageNotSupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[1024]);
        assertThrows(UnsupportedFileTypeException.class, () -> fileValidationService.validate(file));
    }

    @Test
    @DisplayName("음성 파일 업로드 시 실패")
    void validateFail_AudioNotSupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp3", "audio/mpeg", new byte[1024]);
        assertThrows(UnsupportedFileTypeException.class, () -> fileValidationService.validate(file));
    }

    @Test
    @DisplayName("지원하지 않는 확장자 업로드 시 실패")
    void validateFail_UnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[1024]);
        assertThrows(UnsupportedFileTypeException.class, () -> fileValidationService.validate(file));
    }

    @Test
    @DisplayName("확장자와 MIME 타입 불일치 시 실패")
    void validateFail_MismatchedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "image/jpeg", new byte[1024]);
        assertThrows(UnsupportedFileTypeException.class, () -> fileValidationService.validate(file));
    }
}
