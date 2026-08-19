package uz.bobur.musicbot.service;

import org.springframework.stereotype.Service;
import uz.bobur.musicbot.client.dto.itunes.ItunesSearchResponse;
import uz.bobur.musicbot.client.dto.itunes.ItunesCatalogClient;
import uz.bobur.musicbot.config.ApplicationProperties;
import uz.bobur.musicbot.domain.MusicSearchResult;
import uz.bobur.musicbot.exception.MusicSearchException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
public class MusicCatalogSearchService {

    private final ItunesCatalogClient itunesCatalogClient;
    private final ApplicationProperties properties;
    private final Semaphore searchPermits;

    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(32, 0.75f, true);

    public MusicCatalogSearchService(
            ItunesCatalogClient itunesCatalogClient,
            ApplicationProperties properties
    ) {
        this.itunesCatalogClient = itunesCatalogClient;
        this.properties = properties;
        this.searchPermits = new Semaphore(
                properties.musicSearch().maxConcurrentSearches(),
                true
        );
    }

    public List<MusicSearchResult> searchWithRecommendations(String rawQuery) {
        String query = normalizeAndValidate(rawQuery);
        ApplicationProperties.MusicSearch config = properties.musicSearch();

        if (!config.enabled()) {
            throw new MusicSearchException("Matn orqali qo‘shiq qidirish hozircha o‘chirilgan.");
        }

        String cacheKey = "query::" + normalizeCacheKey(query);
        List<MusicSearchResult> cached = getCached(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (!searchPermits.tryAcquire()) {
            throw new MusicSearchException(
                    "Qidiruv xizmati hozir band. Birozdan keyin qayta urinib ko‘ring."
            );
        }

        try {
            int maxResults = config.maxResults();
            int primaryLimit = Math.min(config.primaryResults(), maxResults);

            List<MusicSearchResult> primary = searchItunes(query, primaryLimit);
            if (primary.isEmpty()) {
                putCached(cacheKey, List.of());
                return List.of();
            }

            Map<String, MusicSearchResult> unique = new LinkedHashMap<>();
            primary.forEach(item -> unique.putIfAbsent(item.trackId(), item));

            if (unique.size() < maxResults) {
                MusicSearchResult seed = primary.getFirst();

                if (hasText(seed.artist())) {
                    int recommendationLimit = Math.min(
                            config.recommendationResults(),
                            maxResults - unique.size()
                    );

                    List<MusicSearchResult> recommendations = searchArtistTracks(
                            seed.artist(),
                            recommendationLimit
                    );

                    for (MusicSearchResult item : recommendations) {
                        if (unique.size() >= maxResults) {
                            break;
                        }
                        unique.putIfAbsent(item.trackId(), item);
                    }
                }
            }

            List<MusicSearchResult> result = unique.values().stream()
                    .limit(maxResults)
                    .toList();

            putCached(cacheKey, result);
            return result;

        } finally {
            searchPermits.release();
        }
    }

    public List<MusicSearchResult> searchArtistRecommendations(MusicSearchResult seed) {
        if (seed == null || !hasText(seed.artist())) {
            return List.of();
        }

        String cacheKey = "artist::" + normalizeCacheKey(seed.artist());
        List<MusicSearchResult> cached = getCached(cacheKey);

        if (cached != null) {
            return cached.stream()
                    .filter(item -> !item.trackId().equals(seed.trackId()))
                    .toList();
        }

        if (!searchPermits.tryAcquire()) {
            throw new MusicSearchException(
                    "Qidiruv xizmati hozir band. Birozdan keyin qayta urinib ko‘ring."
            );
        }

        try {
            int maxResults = properties.musicSearch().maxResults();

            List<MusicSearchResult> results = searchArtistTracks(
                    seed.artist(),
                    maxResults
            );

            putCached(cacheKey, results);

            return results.stream()
                    .filter(item -> !item.trackId().equals(seed.trackId()))
                    .toList();

        } finally {
            searchPermits.release();
        }
    }

    private List<MusicSearchResult> searchItunes(String query, int wanted) {
        if (wanted <= 0) {
            return List.of();
        }

        ItunesSearchResponse response = itunesCatalogClient.searchTracks(
                query,
                wanted
        );

        if (response == null
                || response.results() == null
                || response.results().isEmpty()) {
            return List.of();
        }

        Map<String, MusicSearchResult> unique = new LinkedHashMap<>();

        for (ItunesSearchResponse.Track track : response.results()) {
            MusicSearchResult mapped = map(track);
            if (mapped != null) {
                unique.putIfAbsent(mapped.trackId(), mapped);
            }
        }

        return unique.values().stream()
                .limit(wanted)
                .toList();
    }

    private List<MusicSearchResult> searchArtistTracks(String artist, int wanted) {
        if (!hasText(artist) || wanted <= 0) {
            return List.of();
        }

        int fetchLimit = Math.min(
                200,
                Math.max(wanted, Math.min(wanted * 2, 200))
        );

        List<MusicSearchResult> raw = searchItunes(
                artist,
                fetchLimit
        );

        String normalizedArtist = normalizeComparable(artist);
        List<MusicSearchResult> filtered = new ArrayList<>();

        for (MusicSearchResult item : raw) {
            String candidateArtist = normalizeComparable(item.artist());

            if (artistMatches(normalizedArtist, candidateArtist)) {
                filtered.add(item);
            }

            if (filtered.size() >= wanted) {
                break;
            }
        }

        return filtered;
    }

    private MusicSearchResult map(ItunesSearchResponse.Track track) {
        if (track == null
                || track.trackId() == null
                || !hasText(track.trackName())) {
            return null;
        }

        long durationMillis = track.trackTimeMillis() == null
                ? 0L
                : Math.max(0L, track.trackTimeMillis());

        long durationSeconds = durationMillis == 0L
                ? 0L
                : Math.round(durationMillis / 1000.0);

        return new MusicSearchResult(
                String.valueOf(track.trackId()),
                track.trackName().trim(),
                blankToNull(track.artistName()),
                blankToNull(track.collectionName()),
                durationSeconds,
                blankToNull(track.trackViewUrl()),
                blankToNull(track.previewUrl())
        );
    }

    private String normalizeAndValidate(String rawQuery) {
        if (rawQuery == null) {
            throw new MusicSearchException("Qidiruv matni bo‘sh.");
        }

        String query = rawQuery
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        ApplicationProperties.MusicSearch config = properties.musicSearch();

        if (query.length() < config.minQueryLength()) {
            throw new MusicSearchException(
                    "Qidiruv uchun kamida %d ta belgi yozing."
                            .formatted(config.minQueryLength())
            );
        }

        if (query.length() > config.maxQueryLength()) {
            throw new MusicSearchException(
                    "Qidiruv matni juda uzun. Maksimum %d ta belgi."
                            .formatted(config.maxQueryLength())
            );
        }

        return query;
    }

    private boolean artistMatches(String requested, String candidate) {
        if (!hasText(requested) || !hasText(candidate)) {
            return false;
        }

        return requested.equals(candidate)
                || requested.contains(candidate)
                || candidate.contains(requested);
    }

    private String normalizeComparable(String value) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\[[^]]*]", " ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeCacheKey(String value) {
        return normalizeComparable(value);
    }

    private synchronized List<MusicSearchResult> getCached(String key) {
        cleanupExpiredCache();

        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        if (!entry.expiresAt().isAfter(Instant.now())) {
            cache.remove(key);
            return null;
        }

        return entry.results();
    }

    private synchronized void putCached(String key, List<MusicSearchResult> results) {
        cleanupExpiredCache();

        int maxEntries = properties.musicSearch().maxCachedQueries();

        while (cache.size() >= maxEntries && !cache.isEmpty()) {
            String eldestKey = cache.keySet().iterator().next();
            cache.remove(eldestKey);
        }

        cache.put(
                key,
                new CacheEntry(
                        List.copyOf(results),
                        Instant.now().plus(properties.musicSearch().cacheTtl())
                )
        );
    }

    private void cleanupExpiredCache() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CacheEntry(
            List<MusicSearchResult> results,
            Instant expiresAt
    ) {
    }
}