package uz.bobur.musicbot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "acrcloud")
public record ACRCloudProperties(
        @NotBlank String host,
        @NotBlank String accessKey,
        @NotBlank String accessSecret,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
