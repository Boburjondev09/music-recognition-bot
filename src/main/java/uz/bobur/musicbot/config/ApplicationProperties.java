package uz.bobur.musicbot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static final class Admin {
        private final Set<Long> adminIds;

        public Admin(String userIds) {
            this.adminIds = parse(userIds);
        }

        public boolean isAdmin(long userId) {
            return adminIds.contains(userId);
        }

        private static Set<Long> parse(String userIds) {
            if (userIds == null || userIds.isBlank()) {
                return Set.of();
            }
            try {
                return Arrays.stream(userIds.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(Long::parseLong)
                        .collect(Collectors.toUnmodifiableSet());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("ADMIN_USER_IDS noto‘g‘ri formatda: " + userIds, exception);
            }
        }
    }
}
