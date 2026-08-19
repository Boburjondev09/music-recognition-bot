package uz.bobur.musicbot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "itunes")
public record ItunesProperties(
        boolean enabled,
        String country,
        @Min(1) @Max(200) int searchLimit,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
    public String normalizedCountry() {
        if (country == null || country.isBlank()) {
            return "US";
        }
        return country.trim().toUpperCase();
    }
}