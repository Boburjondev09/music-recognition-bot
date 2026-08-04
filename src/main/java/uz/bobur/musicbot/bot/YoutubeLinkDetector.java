package uz.bobur.musicbot.bot;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class YoutubeLinkDetector {

    private static final Pattern CONTAINS_YOUTUBE_DOMAIN = Pattern.compile("(youtube\\.com|youtu\\.be)", Pattern.CASE_INSENSITIVE);

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:[?&]v=|youtu\\.be/|shorts/|[?&]list=RD)([\\w-]{11})",
            Pattern.CASE_INSENSITIVE
    );

    public record YoutubeLink(String videoId, String url) {
    }

    public Optional<YoutubeLink> extract(String text) {
        if (text == null || text.isBlank() || !CONTAINS_YOUTUBE_DOMAIN.matcher(text).find()) {
            return Optional.empty();
        }

        Matcher matcher = VIDEO_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String videoId = matcher.group(1);
        return Optional.of(new YoutubeLink(videoId, "https://www.youtube.com/watch?v=" + videoId));
    }
}
