package uz.bobur.musicbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.bobur.musicbot.config.ApplicationProperties;
import uz.bobur.musicbot.exception.YoutubeDownloadException;
import uz.bobur.musicbot.util.FileNameSanitizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class YoutubeMediaDownloader {

    private static final Logger log = LoggerFactory.getLogger(YoutubeMediaDownloader.class);
    private static final String BINARY = "yt-dlp";
    private static final int LOG_EXCERPT_LENGTH = 500;
    private static final int MAX_ATTEMPTS = 3;
    private static final int SAMPLE_DURATION_SECONDS = 30;
    private static final String PRINT_TEMPLATE = "after_move:%(id)s\t%(title)s\t%(duration)s";
    private static final String MAX_AUDIO_FILESIZE = "20M";
    private static final String MAX_VIDEO_FILESIZE = "50M";
    private static final String VIDEO_FORMAT = "bestvideo[height<=1080]+bestaudio/best[height<=1080]";

    private final ApplicationProperties properties;
    private final FileNameSanitizer fileNameSanitizer;

    public YoutubeMediaDownloader(ApplicationProperties properties, FileNameSanitizer fileNameSanitizer) {
        this.properties = properties;
        this.fileNameSanitizer = fileNameSanitizer;
    }

    public record DownloadedYoutubeMedia(String videoId, String fileName, String contentType, byte[] content, long durationSeconds) {
    }

    /**
     * Downloads only a short clip for recognition — fingerprinting doesn't need the full track,
     * and skipping the rest cuts yt-dlp/ffmpeg time and the ACRCloud upload size dramatically.
     */
    public DownloadedYoutubeMedia downloadSample(String url) {
        return downloadAudio(url, "*0-" + SAMPLE_DURATION_SECONDS);
    }

    public DownloadedYoutubeMedia downloadFullAudio(String url) {
        DownloadedYoutubeMedia audio = downloadAudio(url, null);
        enforceMaxDuration(audio);
        return audio;
    }

    public DownloadedYoutubeMedia downloadVideo(String url) {
        Path tempDir = createTempDir();

        try {
            Path outputTemplate = tempDir.resolve("video.%(ext)s");
            List<String> command = List.of(
                    BINARY, "--no-warnings", "--no-playlist",
                    "-f", VIDEO_FORMAT,
                    "--merge-output-format", "mp4",
                    "--max-filesize", MAX_VIDEO_FILESIZE,
                    "-o", outputTemplate.toString(),
                    "--print", PRINT_TEMPLATE,
                    url
            );

            DownloadedYoutubeMedia media = runDownload(command, tempDir, "video.mp4", "video/mp4", "mp4");
            enforceMaxDuration(media);
            return media;
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void enforceMaxDuration(DownloadedYoutubeMedia media) {
        long maxDuration = properties.youtube().maxDurationSeconds();
        if (media.durationSeconds() > maxDuration) {
            throw new YoutubeDownloadException("Video juda uzun. Iltimos, %d daqiqadan qisqaroq video yuboring.".formatted(maxDuration / 60));
        }
    }

    private DownloadedYoutubeMedia downloadAudio(String url, String section) {
        Path tempDir = createTempDir();

        try {
            Path outputTemplate = tempDir.resolve("audio.%(ext)s");
            List<String> command = new ArrayList<>(List.of(BINARY, "--no-warnings", "--no-playlist", "-f", "bestaudio"));
            if (section != null) {
                command.add("--download-sections");
                command.add(section);
            }
            command.addAll(List.of(
                    "-x", "--audio-format", "mp3", "--audio-quality", "5",
                    "--max-filesize", MAX_AUDIO_FILESIZE,
                    "-o", outputTemplate.toString(),
                    "--print", PRINT_TEMPLATE,
                    url
            ));

            return runDownload(command, tempDir, "audio.mp3", "audio/mpeg", "mp3");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private DownloadedYoutubeMedia runDownload(List<String> command, Path tempDir, String expectedFileName, String contentType, String extension) {
        try {
            String output = run(command, tempDir).strip();
            VideoMetadata metadata = parseMetadata(output);

            Path mediaFile = tempDir.resolve(expectedFileName);
            if (!Files.exists(mediaFile)) {
                throw new YoutubeDownloadException("Faylni ajratib bo‘lmadi");
            }

            byte[] content = Files.readAllBytes(mediaFile);
            if (content.length == 0) {
                throw new YoutubeDownloadException("YouTube'dan fayl olib bo‘lmadi");
            }

            String fileName = fileNameSanitizer.sanitize(metadata.title() + "." + extension);
            return new DownloadedYoutubeMedia(metadata.videoId(), fileName, contentType, content, metadata.durationSeconds());
        } catch (IOException exception) {
            throw new YoutubeDownloadException("YouTube faylini o‘qib bo‘lmadi");
        }
    }

    private record VideoMetadata(String videoId, String title, long durationSeconds) {
    }

    private VideoMetadata parseMetadata(String output) {
        String lastLine = output.lines().reduce((first, second) -> second).orElse("");
        String[] parts = lastLine.split("\t", 3);
        if (parts.length < 3 || parts[0].isBlank()) {
            throw new YoutubeDownloadException("YouTube linkini ochib bo‘lmadi. Linkni tekshiring.");
        }
        return new VideoMetadata(parts[0], parts[1], parseDuration(parts[2]));
    }

    private long parseDuration(String raw) {
        try {
            return (long) Double.parseDouble(raw.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String run(List<String> command, Path workingDir) throws IOException {
        // Total time budget shared across all attempts, so one flaky link can't occupy a
        // worker for MAX_ATTEMPTS x downloadTimeout — a single attempt already timing out
        // means retrying with the same (network) conditions is unlikely to help either.
        Instant deadline = Instant.now().plus(properties.youtube().downloadTimeout());

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isNegative() || remaining.isZero()) {
                throw new YoutubeDownloadException("Yuklab olish vaqti tugadi. Qisqaroq video bilan urinib ko‘ring.");
            }

            RunResult result = runOnce(command, workingDir, remaining);
            if (result.exitCode() == 0) {
                return result.output();
            }

            log.warn("yt-dlp muvaffaqiyatsiz tugadi ({}-urinish/{}, exit={}): {}", attempt, MAX_ATTEMPTS, result.exitCode(), limit(result.output()));
            if (attempt == MAX_ATTEMPTS) {
                throw new YoutubeDownloadException("YouTube havolasidan fayl olib bo‘lmadi. Linkni tekshiring.");
            }
        }
        throw new YoutubeDownloadException("YouTube havolasidan fayl olib bo‘lmadi. Linkni tekshiring.");
    }

    private record RunResult(int exitCode, String output) {
    }

    private RunResult runOnce(List<String> command, Path workingDir, Duration timeout) throws IOException {
        Path logFile = Files.createTempFile(workingDir, "yt-dlp-", ".log");

        Process process = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new YoutubeDownloadException("Yuklab olish to‘xtatildi");
        }

        String output = Files.readString(logFile);
        if (!finished) {
            process.destroyForcibly();
            throw new YoutubeDownloadException("Yuklab olish vaqti tugadi. Qisqaroq video bilan urinib ko‘ring.");
        }
        return new RunResult(process.exitValue(), output);
    }

    private String limit(String value) {
        return value.length() <= LOG_EXCERPT_LENGTH ? value : value.substring(0, LOG_EXCERPT_LENGTH);
    }

    private Path createTempDir() {
        try {
            return Files.createTempDirectory("ytdlp-");
        } catch (IOException exception) {
            throw new YoutubeDownloadException("Vaqtinchalik papka yaratib bo‘lmadi");
        }
    }

    private void deleteRecursively(Path path) {
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException exception) {
            log.warn("Vaqtinchalik papkani tozalab bo‘lmadi: {}", path);
        }
    }
}
