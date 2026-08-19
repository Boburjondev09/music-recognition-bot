package uz.bobur.musicbot.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.bobur.musicbot.config.ApplicationProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class UserRateLimiter {

    private final ConcurrentMap<Long, Deque<Instant>> userRequests = new ConcurrentHashMap<>();

    private final int maxRequests;
    private final Duration window;
    private final int maxTrackedUsers;

    public UserRateLimiter(ApplicationProperties properties) {

        this.maxRequests = properties.rateLimit().maxRequests();

        this.window = Duration.ofSeconds(properties.rateLimit().windowSeconds());

        this.maxTrackedUsers = properties.rateLimit().maxTrackedUsers();
    }

    public boolean tryAcquire(long userId) {

        Instant now = Instant.now();

        Instant threshold = now.minus(window);

        Deque<Instant> existing = userRequests.get(userId);

        if (existing == null && userRequests.size() >= maxTrackedUsers) {

            cleanupExpired();

            if (userRequests.size() >= maxTrackedUsers) {

                return false;
            }
        }

        Deque<Instant> timestamps = userRequests.computeIfAbsent(userId, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {

            prune(timestamps, threshold);

            if (timestamps.size() >= maxRequests) {

                return false;
            }

            timestamps.addLast(now);

            return true;
        }
    }

    @Scheduled(fixedDelayString = "${app.rate-limit.cleanup-interval-millis:60000}")
    public void cleanupExpired() {

        Instant threshold = Instant.now().minus(window);

        userRequests.forEach((userId, timestamps) -> {

            synchronized (timestamps) {

                prune(timestamps, threshold);

                if (timestamps.isEmpty()) {

                    userRequests.remove(userId, timestamps);
                }
            }
        });
    }

    int trackedUsers() {

        return userRequests.size();
    }

    private void prune(Deque<Instant> timestamps, Instant threshold) {

        while (!timestamps.isEmpty()) {

            Instant first = timestamps.peekFirst();

            if (first == null || !first.isBefore(threshold)) {

                break;
            }

            timestamps.pollFirst();
        }
    }
}