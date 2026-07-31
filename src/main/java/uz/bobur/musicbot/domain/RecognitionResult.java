package uz.bobur.musicbot.domain;

public record RecognitionResult(
        String title,
        String artist,
        String album,
        String releaseDate,
        String label,
        String timecode,
        String songLink,
        String spotifyUrl,
        String appleMusicUrl
) {
}
