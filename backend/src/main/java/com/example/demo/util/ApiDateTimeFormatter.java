package com.example.demo.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class ApiDateTimeFormatter {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private ApiDateTimeFormatter() {
    }

    public static String formatUtc(LocalDateTime value) {
        return value == null ? null : UTC.format(value);
    }

    public static LocalDateTime parseUtc(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        return Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDateTime();
    }
}
