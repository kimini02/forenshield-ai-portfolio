package com.example.demo.dto;

import com.example.demo.domain.enums.FileType;

public record ValidatedFile(
        String fileName,
        FileType fileType,
        String mimeType,
        long fileSize
) {
}
