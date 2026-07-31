package uz.bobur.musicbot.domain;

import uz.bobur.musicbot.enums.RecognitionStatus;

import java.time.Instant;

public record SearchHistoryView(
        RecognitionStatus status,
        String title,
        String artist,
        String errorMessage,
        Instant createdAt
) {
}
