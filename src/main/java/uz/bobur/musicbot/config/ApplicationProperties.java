package uz.bobur.musicbot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Arrays;

@Validated
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
        @Valid @NotNull Recognition recognition,
        @Valid @NotNull Processing processing,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Cleanup cleanup,
        @Valid @NotNull Youtube youtube,
        @Valid @NotNull Admin admin
) {
    public record Recognition(
            @Min(1) long maxFileSizeBytes,
            @Min(1) int historyLimit
    ) {
    }

    public record Processing(
            @Min(1) int maxConcurrentJobs,
            @Min(1) int queueCapacity
    ) {
    }

    public record RateLimit(
            @Min(1) int maxRequests,
            @Min(1) int windowSeconds
    ) {
    }

    public record Cleanup(
            @Min(1) int stuckProcessingMinutes,
            @Min(1000) long intervalMillis
    ) {
    }

    public record Youtube(
            boolean enabled,
            @Min(1) long maxDurationSeconds,
            @NotNull Duration downloadTimeout
    ) {
    }

    public record Admin(
            String userIds
    ) {
        public boolean isAdmin(long userId) {
            if (userIds == null || userIds.isBlank()) {
                return false;
            }
            return Arrays.stream(userIds.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .mapToLong(Long::parseLong)
                    .anyMatch(id -> id == userId);
        }
    }
}
