package uz.bobur.musicbot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
        @Valid @NotNull Recognition recognition,
        @Valid @NotNull Processing processing,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Cleanup cleanup
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
}
