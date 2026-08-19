package uz.bobur.musicbot.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.bobur.musicbot.config.ApplicationProperties;
import uz.bobur.musicbot.domain.MusicSearchResult;
import uz.bobur.musicbot.domain.TelegramUserContext;
import uz.bobur.musicbot.domain.search.SearchSort;
import uz.bobur.musicbot.domain.search.SearchTheme;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class MusicSearchSessionService {

    private final ConcurrentMap<String, SearchSession> sessions = new ConcurrentHashMap<>();
    private final ApplicationProperties properties;

    public MusicSearchSessionService(ApplicationProperties properties) {
        this.properties = properties;
    }

    public SearchSession create(TelegramUserContext user, String query, List<MusicSearchResult> results) {
        cleanupExpired();
        enforceCapacity();

        String id = newSessionId();
        Instant expiresAt = Instant.now().plus(properties.musicSearch().sessionTtl());
        SearchSession session = new SearchSession(
                id,
                user.userId(),
                user.chatId(),
                query,
                List.copyOf(results),
                expiresAt,
                properties.musicSearch().pageSize()
        );
        sessions.put(id, session);
        return session;
    }

    public Optional<SearchSession> find(String sessionId, TelegramUserContext user) {
        if (sessionId == null || user == null) {
            return Optional.empty();
        }

        SearchSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }

        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(sessionId, session);
            return Optional.empty();
        }

        if (session.telegramUserId() != user.userId() || session.telegramChatId() != user.chatId()) {
            return Optional.empty();
        }

        return Optional.of(session);
    }

    public Optional<MusicSearchResult> findResult(String sessionId, int index, TelegramUserContext user) {
        return find(sessionId, user).flatMap(session -> session.resultAt(index));
    }

    public Optional<SearchSession> setPage(String sessionId, int page, TelegramUserContext user) {
        return find(sessionId, user).map(session -> {
            session.setPage(page);
            return session;
        });
    }

    public Optional<SearchSession> cycleSort(String sessionId, TelegramUserContext user) {
        return find(sessionId, user).map(session -> {
            session.cycleSort();
            return session;
        });
    }



    public Optional<SearchSession> setTheme(String sessionId, SearchTheme theme, TelegramUserContext user) {
        return find(sessionId, user).map(session -> {
            session.setTheme(theme);
            return session;
        });
    }

    public void remove(String sessionId, TelegramUserContext user) {
        find(sessionId, user).ifPresent(session -> sessions.remove(sessionId, session));
    }

    @Scheduled(fixedDelayString = "${app.music-search.cleanup-interval-millis:60000}")
    public void cleanupExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    int activeSessions() {
        return sessions.size();
    }

    private void enforceCapacity() {
        int maxSessions = properties.musicSearch().maxSessions();
        if (sessions.size() < maxSessions) {
            return;
        }

        sessions.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                .ifPresent(entry -> sessions.remove(entry.getKey(), entry.getValue()));
    }

    private String newSessionId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            if (!sessions.containsKey(id)) {
                return id;
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static final class SearchSession {
        private final String id;
        private final long telegramUserId;
        private final long telegramChatId;
        private final String query;
        private final List<MusicSearchResult> relevanceResults;
        private final Instant expiresAt;
        private final int pageSize;

        private int page;
        private SearchSort sort = SearchSort.RELEVANCE;
        private SearchTheme theme = SearchTheme.DEFAULT;

        private SearchSession(
                String id,
                long telegramUserId,
                long telegramChatId,
                String query,
                List<MusicSearchResult> results,
                Instant expiresAt,
                int pageSize
        ) {
            this.id = id;
            this.telegramUserId = telegramUserId;
            this.telegramChatId = telegramChatId;
            this.query = query;
            this.relevanceResults = List.copyOf(results);
            this.expiresAt = expiresAt;
            this.pageSize = pageSize;
        }

        public String id() {
            return id;
        }

        public long telegramUserId() {
            return telegramUserId;
        }

        public long telegramChatId() {
            return telegramChatId;
        }

        public String query() {
            return query;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public synchronized int page() {
            return page;
        }

        public int pageSize() {
            return pageSize;
        }

        public int totalResults() {
            return relevanceResults.size();
        }

        public synchronized SearchSort sort() {
            return sort;
        }



        public synchronized SearchTheme theme() {
            return theme;
        }

        public synchronized int totalPages() {
            if (relevanceResults.isEmpty()) {
                return 0;
            }
            return (relevanceResults.size() + pageSize - 1) / pageSize;
        }

        public synchronized void setPage(int requestedPage) {
            int maxPage = Math.max(0, totalPages() - 1);
            page = Math.max(0, Math.min(requestedPage, maxPage));
        }

        public synchronized void cycleSort() {
            sort = sort.next();
            page = 0;
        }



        public synchronized void setTheme(SearchTheme theme) {
            if (theme != null) {
                this.theme = theme;
            }
        }

        public synchronized List<MusicSearchResult> sortedResults() {
            List<MusicSearchResult> sorted = new ArrayList<>(relevanceResults);
            Comparator<MusicSearchResult> titleComparator = Comparator.comparing(
                    result -> safe(result.title()),
                    String.CASE_INSENSITIVE_ORDER
            );
            Comparator<MusicSearchResult> durationComparator = Comparator.comparingLong(result -> {
                long duration = result.durationSeconds();
                return duration <= 0 ? Long.MAX_VALUE : duration;
            });

            switch (sort) {
                case RELEVANCE -> {
                    return sorted;
                }
                case TITLE_ASC -> sorted.sort(titleComparator);
                case TITLE_DESC -> sorted.sort(titleComparator.reversed());
                case DURATION_ASC -> sorted.sort(durationComparator.thenComparing(titleComparator));
                case DURATION_DESC -> sorted.sort(durationComparator.reversed().thenComparing(titleComparator));
            }
            return sorted;
        }

        public synchronized List<MusicSearchResult> currentPageResults() {
            List<MusicSearchResult> sorted = sortedResults();
            if (sorted.isEmpty()) {
                return List.of();
            }

            int from = Math.min(page * pageSize, sorted.size());
            int to = Math.min(from + pageSize, sorted.size());
            return List.copyOf(sorted.subList(from, to));
        }

        public synchronized Optional<MusicSearchResult> resultAt(int globalIndex) {
            List<MusicSearchResult> sorted = sortedResults();
            if (globalIndex < 0 || globalIndex >= sorted.size()) {
                return Optional.empty();
            }
            return Optional.of(sorted.get(globalIndex));
        }

        public synchronized int pageStartIndex() {
            return page * pageSize;
        }

        private String safe(String value) {
            return value == null ? "" : value;
        }
    }
}