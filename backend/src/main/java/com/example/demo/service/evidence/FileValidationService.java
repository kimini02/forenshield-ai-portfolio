package com.example.demo.service.evidence;

import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.ValidatedFile;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.FileSizeExceededException;
import com.example.demo.exception.UnsupportedFileTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
public class FileValidationService {

    private static final String VIDEO = FileType.VIDEO.name();

    private static final List<String> ALLOWED_EXTENSIONS = List.of("mp4", "mov");

    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "video/mp4",
            "video/quicktime"
    );

    private static final long MAX_VIDEO_SIZE = 500L * 1024 * 1024;

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FILE_NOT_FOUND", "업로드된 파일이 없습니다.");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "파일명이 존재하지 않습니다.");
        }

        String extension = getExtension(fileName);
        String mimeType = file.getContentType();

        if (!isSupportedVideo(extension, mimeType)) {
            throw new UnsupportedFileTypeException("지원하지 않는 파일 형식입니다. 영상(MP4, MOV) 파일만 업로드할 수 있습니다.");
        }

        validateSize(file.getSize());

        return new ValidatedFile(
                fileName,
                FileType.VIDEO,
                mimeType,
                file.getSize()
        );
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1).toLowerCase();
    }

    private boolean isSupportedVideo(String extension, String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(extension) && ALLOWED_MIME_TYPES.contains(mimeType);
    }

    private void validateSize(long size) {
        if (size > MAX_VIDEO_SIZE) {
            throw new FileSizeExceededException(String.format(
                    "%s 파일의 최대 허용 용량은 %dMB입니다.",
                    VIDEO,
                    MAX_VIDEO_SIZE / (1024 * 1024)));
        }
    }
}
