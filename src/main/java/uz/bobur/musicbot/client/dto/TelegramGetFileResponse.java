package uz.bobur.musicbot.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramGetFileResponse(
        boolean ok,
        TelegramFileInfo result,
        String description
) {
}
