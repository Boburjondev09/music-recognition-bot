package uz.bobur.musicbot.client.dto.spotify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uz.bobur.musicbot.config.SpotifyProperties;
import uz.bobur.musicbot.exception.MusicSearchException;

@Component
public class SpotifyCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(SpotifyCatalogClient.class);
    private static final int SPOTIFY_MAX_PAGE_SIZE = 10;

    private final SpotifyProperties properties;
    private final uz.bobur.musicbot.client.dto.spotify.SpotifyTokenService tokenService;
    private final RestClient apiClient;

    public SpotifyCatalogClient(
            RestClient.Builder builder,
            SpotifyProperties properties,
            uz.bobur.musicbot.client.dto.spotify.SpotifyTokenService tokenService
    ) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.apiClient = builder
                .baseUrl("https://api.spotify.com/v1")
                .build();
    }

    public SpotifySearchResponse searchTracks(String query, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, SPOTIFY_MAX_PAGE_SIZE));
        int safeOffset = Math.max(0, Math.min(offset, 1000));

        return executeSearch(query, safeLimit, safeOffset, false);
    }

    private SpotifySearchResponse executeSearch(
            String query,
            int limit,
            int offset,
            boolean retriedAfter401
    ) {
        try {
            String token = tokenService.getAccessToken();

            return apiClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("type", "track")
                            .queryParam("market", market())
                            .queryParam("limit", limit)
                            .queryParam("offset", offset)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(SpotifySearchResponse.class);

        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();

            if (status == 401 && !retriedAfter401) {
                tokenService.invalidate();
                return executeSearch(query, limit, offset, true);
            }

            if (status == 429) {
                String retryAfter = exception.getResponseHeaders() == null
                        ? null
                        : exception.getResponseHeaders().getFirst("Retry-After");

                throw new MusicSearchException(
                        retryAfter == null
                                ? "Spotify qidiruv limiti vaqtincha tugadi. Birozdan keyin qayta urinib ko‘ring."
                                : "Spotify qidiruv limiti vaqtincha tugadi. Taxminan " + retryAfter + " soniyadan keyin qayta urinib ko‘ring.",
                        exception
                );
            }

            log.warn(
                    "Spotify search failed. status={}, query={}, offset={}, body={}",
                    status,
                    query,
                    offset,
                    limitLog(exception.getResponseBodyAsString())
            );

            throw new MusicSearchException(
                    "Spotify orqali qo‘shiq qidirib bo‘lmadi. Birozdan keyin qayta urinib ko‘ring.",
                    exception
            );
        } catch (MusicSearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MusicSearchException("Spotify servisiga ulanib bo‘lmadi.", exception);
        }
    }

    private String market() {
        return properties.market() == null || properties.market().isBlank()
                ? "UZ"
                : properties.market().trim().toUpperCase();
    }

    private String limitLog(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}