package uz.bobur.musicbot.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileNameSanitizerTest {

    private final FileNameSanitizer sanitizer = new FileNameSanitizer();

    @Test
    void shouldSanitizeUnsafeCharacters() {
        assertThat(sanitizer.sanitize("my song (live).mp3"))
                .isEqualTo("my_song_live_.mp3");
    }

    @Test
    void shouldUseFallbackForBlankName() {
        assertThat(sanitizer.sanitize(" ")).isEqualTo("media.bin");
    }
}
