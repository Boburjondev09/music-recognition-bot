package uz.bobur.musicbot.bot;

import org.junit.jupiter.api.Test;
import uz.bobur.musicbot.domain.RecognitionResult;

import static org.assertj.core.api.Assertions.assertThat;

class BotMessageFormatterTest {

    private final BotMessageFormatter formatter =
            new BotMessageFormatter();

    @Test
    void shouldFormatRecognizedTrack() {

        RecognitionResult result =
                new RecognitionResult(
                        "Warriors",
                        "Imagine Dragons",
                        "Smoke + Mirrors",
                        "2014-09-18",
                        "Universal Music",
                        "02:32",
                        "https://lis.tn/Warriors",
                        "https://open.spotify.com/example",
                        null
                );

        String message =
                formatter.recognized(result);

        assertThat(message)
                .contains("Qo‘shiq aniqlandi")
                .contains("Warriors")
                .contains("Imagine Dragons")
                .contains("Smoke + Mirrors")
                .contains("2014-09-18")
                .contains("Universal Music")
                .contains("02:32")
                .doesNotContain("Spotify");
    }
}