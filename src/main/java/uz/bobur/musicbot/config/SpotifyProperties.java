package uz.bobur.musicbot.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "spotify")
public record SpotifyProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String market,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}