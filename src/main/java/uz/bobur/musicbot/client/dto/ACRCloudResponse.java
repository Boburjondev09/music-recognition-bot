package uz.bobur.musicbot.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ACRCloudResponse(Status status, Metadata metadata) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(int code, String msg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(List<Music> music) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Music(String title, List<Artist> artists, Album album,
                         @JsonProperty("release_date") String releaseDate, String label,
                         @JsonProperty("play_offset_ms") Integer playOffsetMs,
                         @JsonProperty("external_metadata") ExternalMetadata externalMetadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artist(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Album(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalMetadata(Spotify spotify, Youtube youtube) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Spotify(SpotifyTrack track) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpotifyTrack(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Youtube(String vid) {
    }
}
