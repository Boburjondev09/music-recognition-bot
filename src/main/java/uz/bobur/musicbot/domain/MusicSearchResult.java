package uz.bobur.musicbot.domain;

public record MusicSearchResult(
        String trackId,
        String title,
        String artist,
        String album,
        long durationSeconds,
        String catalogUrl,
        String previewUrl
) {
    public MusicSearchResult(
            String trackId,
            String title,
            String artist,
            long durationSeconds
    ) {
        this(
                trackId,
                title,
                artist,
                null,
                durationSeconds,
                null,
                null
        );
    }

    public MusicSearchResult(
            String trackId,
            String title,
            String artist,
            String album,
            long durationSeconds,
            String catalogUrl
    ) {
        this(
                trackId,
                title,
                artist,
                album,
                durationSeconds,
                catalogUrl,
                null
        );
    }
}