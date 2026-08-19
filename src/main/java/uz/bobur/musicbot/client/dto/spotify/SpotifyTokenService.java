package uz.bobur.musicbot.client.dto.spotify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uz.bobur.musicbot.client.dto.spotify.SpotifyTokenResponse;
import uz.bobur.musicbot.config.SpotifyProperties;
import uz.bobur.musicbot.exception.MusicSearchException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Component
public class SpotifyTokenService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyTokenService.class);

    private final SpotifyProperties properties;
    private final RestClient accountsClient;

    private volatile CachedToken cachedToken;

    public SpotifyTokenService(
            RestClient.Builder builder,
            SpotifyProperties properties
    ) {
        this.properties = properties;
        this.accountsClient = builder
                .baseUrl("https://accounts.spotify.com")
                .build();
    }

    public String getAccessToken() {
        if (!properties.enabled()) {
            throw new MusicSearchException("Spotify qidiruvi hozircha o‘chirilgan.");
        }

        if (properties.clientId() == null || properties.clientId().isBlank()
                || properties.clientSecret() == null || properties.clientSecret().isBlank()) {
            throw new MusicSearchException(
                    "Spotify credential yo‘q. SPOTIFY_CLIENT_ID va SPOTIFY_CLIENT_SECRET ni sozlang."
            );
        }

        CachedToken current = cachedToken;
        Instant now = Instant.now();

        if (current != null && current.expiresAt().isAfter(now.plusSeconds(30))) {
            return current.value();
        }

        synchronized (this) {
            current = cachedToken;
            now = Instant.now();

            if (current != null && current.expiresAt().isAfter(now.plusSeconds(30))) {
                return current.value();
            }

            SpotifyTokenResponse response = requestToken();
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new MusicSearchException("Spotify access token olinmadi.");
            }

            long ttl = Math.max(60L, response.expiresIn());
            cachedToken = new CachedToken(
                    response.accessToken(),
                    Instant.now().plusSeconds(ttl)
            );

            return cachedToken.value();
        }
    }

    public void invalidate() {
        cachedToken = null;
    }

    private SpotifyTokenResponse requestToken() {
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        String basic = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
        );

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        try {
            return accountsClient
                    .post()
                    .uri("/api/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SpotifyTokenResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Spotify token request failed. status={}, body={}",
                    exception.getStatusCode().value(),
                    limit(exception.getResponseBodyAsString())
            );
            throw new MusicSearchException(
                    "Spotify autentifikatsiyasi ishlamadi. SPOTIFY_CLIENT_ID va SPOTIFY_CLIENT_SECRET ni tekshiring.",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new MusicSearchException("Spotify autentifikatsiya servisiga ulanib bo‘lmadi.", exception);
        }
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 400 ? normalized : normalized.substring(0, 400);
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}