package uz.bobur.musicbot.client.dto.itunes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import uz.bobur.musicbot.client.dto.itunes.ItunesSearchResponse;
import uz.bobur.musicbot.config.ItunesProperties;
import uz.bobur.musicbot.exception.MusicSearchException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class ItunesCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(ItunesCatalogClient.class);
    private static final String SEARCH_URL = "https://itunes.apple.com/search";
    private static final int API_MAX_LIMIT = 200;

    private final ItunesProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ItunesCatalogClient(
            ItunesProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public ItunesSearchResponse searchTracks(String query, int requestedLimit) {
        if (!properties.enabled()) {
            throw new MusicSearchException("iTunes qidiruvi hozircha o‘chirilgan.");
        }

        if (query == null || query.isBlank()) {
            throw new MusicSearchException("Qidiruv matni bo‘sh.");
        }

        int safeLimit = Math.max(
                1,
                Math.min(
                        Math.min(requestedLimit, properties.searchLimit()),
                        API_MAX_LIMIT
                )
        );

        URI uri = UriComponentsBuilder
                .fromUriString(SEARCH_URL)
                .queryParam("term", query.trim())
                .queryParam("country", properties.normalizedCountry())
                .queryParam("media", "music")
                .queryParam("entity", "song")
                .queryParam("limit", safeLimit)
                .queryParam("explicit", "Yes")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(properties.readTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", "music-recognition-bot/1.0")
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            int status = response.statusCode();

            if (status == 429) {
                throw new MusicSearchException(
                        "iTunes qidiruv limiti vaqtincha tugadi. Birozdan keyin qayta urinib ko‘ring."
                );
            }

            if (status < 200 || status >= 300) {
                log.warn(
                        "iTunes search failed. status={}, query={}, body={}",
                        status,
                        limitLog(query),
                        limitLog(response.body())
                );

                throw new MusicSearchException(
                        "iTunes orqali qo‘shiq qidirib bo‘lmadi. Birozdan keyin qayta urinib ko‘ring."
                );
            }

            ItunesSearchResponse result = objectMapper.readValue(
                    response.body(),
                    ItunesSearchResponse.class
            );

            return result == null
                    ? new ItunesSearchResponse(0, java.util.List.of())
                    : result;

        } catch (MusicSearchException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MusicSearchException("iTunes qidiruvi to‘xtatildi.", exception);
        } catch (JsonProcessingException exception) {
            log.warn("iTunes JSON javobini parse qilib bo‘lmadi", exception);
            throw new MusicSearchException("iTunes noto‘g‘ri formatdagi javob qaytardi.", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new MusicSearchException("iTunes servisiga ulanib bo‘lmadi.", exception);
        }
    }

    private String limitLog(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.length() <= 500
                ? normalized
                : normalized.substring(0, 500);
    }
}