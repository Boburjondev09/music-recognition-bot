package uz.bobur.musicbot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramBotProperties(
        @NotBlank String token,
        @NotBlank String username,
        @NotBlank String apiBaseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
