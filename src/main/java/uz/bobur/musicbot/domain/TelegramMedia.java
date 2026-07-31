package uz.bobur.musicbot.domain;

import uz.bobur.musicbot.enums.MediaType;

public record TelegramMedia(
        String fileId,
        String fileUniqueId,
        MediaType mediaType,
        String fileName,
        String contentType,
        Long declaredFileSize
) {
}
