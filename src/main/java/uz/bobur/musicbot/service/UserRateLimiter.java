package uz.bobur.musicbot.service;

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

    public UserRateLimiter(ApplicationProperties properties) {
        this.maxRequests = properties.rateLimit().maxRequests();
        this.window = Duration.ofSeconds(properties.rateLimit().windowSeconds());
    }

    public boolean tryAcquire(long userId) {
        Instant now = Instant.now();
        Instant threshold = now.minus(window);

        Deque<Instant> timestamps = userRequests.computeIfAbsent(userId, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(threshold)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
