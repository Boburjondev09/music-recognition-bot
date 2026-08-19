package uz.bobur.musicbot.client.dto.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifySearchResponse(
        Tracks tracks
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tracks(
            String href,
            int limit,
            String next,
            int offset,
            String previous,
            int total,
            List<Track> items
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Track(
            String id,
            String name,
            @JsonProperty("duration_ms") long durationMs,
            List<Artist> artists,
            Album album,
            @JsonProperty("external_urls") ExternalUrls externalUrls
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artist(
            String id,
            String name
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Album(
            String id,
            String name
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalUrls(
            String spotify
    ) {
    }
}