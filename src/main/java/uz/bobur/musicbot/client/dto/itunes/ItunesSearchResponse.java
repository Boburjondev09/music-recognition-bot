package uz.bobur.musicbot.client.dto.itunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItunesSearchResponse(
        int resultCount,
        List<Track> results
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Track(
            Long trackId,
            String trackName,
            String artistName,
            String collectionName,
            Long trackTimeMillis,
            String trackViewUrl,
            String previewUrl,
            String primaryGenreName,
            String country
    ) {
    }
}