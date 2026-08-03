package uz.bobur.musicbot.bot;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class YoutubeLinkDetectorTest {

    private final YoutubeLinkDetector detector = new YoutubeLinkDetector();

    @Test
    void shouldExtractFromWatchUrl() {
        Optional<String> result = detector.extract("https://www.youtube.com/watch?v=jNQXAC9IVRw&feature=share");

        assertThat(result).contains("https://www.youtube.com/watch?v=jNQXAC9IVRw");
    }

    @Test
    void shouldExtractFromShortLink() {
        Optional<String> result = detector.extract("check this out youtu.be/jNQXAC9IVRw?si=abc123");

        assertThat(result).contains("https://www.youtube.com/watch?v=jNQXAC9IVRw");
    }

    @Test
    void shouldExtractFromShorts() {
        Optional<String> result = detector.extract("https://youtube.com/shorts/jNQXAC9IVRw");

        assertThat(result).contains("https://www.youtube.com/watch?v=jNQXAC9IVRw");
    }

    @Test
    void shouldExtractFromRadioMixPlaylistShare() {
        Optional<String> result = detector.extract("https://youtube.com/playlist?list=RD2Vv-BfVoq4g&playnext=1&si=e6KwTNoS7pKjVtim");

        assertThat(result).contains("https://www.youtube.com/watch?v=2Vv-BfVoq4g");
    }

    @Test
    void shouldReturnEmptyForPlainText() {
        assertThat(detector.extract("hello there")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForRegularUserPlaylistWithoutVideoId() {
        assertThat(detector.extract("https://youtube.com/playlist?list=PLsomeUserPlaylistId")).isEmpty();
    }
}
