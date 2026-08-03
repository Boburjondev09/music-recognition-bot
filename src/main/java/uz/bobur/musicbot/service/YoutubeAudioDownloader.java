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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class YoutubeAudioDownloader {

    private static final Logger log = LoggerFactory.getLogger(YoutubeAudioDownloader.class);
    private static final String BINARY = "yt-dlp";
    private static final int LOG_EXCERPT_LENGTH = 500;
    private static final int MAX_ATTEMPTS = 3;

    private final ApplicationProperties properties;
    private final FileNameSanitizer fileNameSanitizer;

    public YoutubeAudioDownloader(ApplicationProperties properties, FileNameSanitizer fileNameSanitizer) {
        this.properties = properties;
        this.fileNameSanitizer = fileNameSanitizer;
    }

    public record DownloadedYoutubeAudio(String videoId, String fileName, String contentType, byte[] content) {
    }

    public DownloadedYoutubeAudio download(String url) {
        Path tempDir = createTempDir();

        try {
            VideoMetadata metadata = fetchMetadata(url, tempDir);

            long maxDuration = properties.youtube().maxDurationSeconds();
            if (metadata.durationSeconds() > maxDuration) {
                throw new YoutubeDownloadException("Video juda uzun. Iltimos, %d daqiqadan qisqaroq video yuboring.".formatted(maxDuration / 60));
            }

            Path audioFile = downloadAudio(url, tempDir);
            byte[] content = Files.readAllBytes(audioFile);
            if (content.length == 0) {
                throw new YoutubeDownloadException("YouTube'dan audio olib bo‘lmadi");
            }

            String fileName = fileNameSanitizer.sanitize(metadata.title() + ".mp3");
            return new DownloadedYoutubeAudio(metadata.videoId(), fileName, "audio/mpeg", content);
        } catch (IOException exception) {
            throw new YoutubeDownloadException("YouTube faylini o‘qib bo‘lmadi");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private record VideoMetadata(String videoId, String title, long durationSeconds) {
    }

    private VideoMetadata fetchMetadata(String url, Path tempDir) throws IOException {
        List<String> command = List.of(BINARY, "--no-warnings", "--skip-download", "--print", "%(id)s\t%(title)s\t%(duration)s", url);
        String output = run(command, tempDir).strip();

        String[] parts = output.split("\t", 3);
        if (parts.length < 3 || parts[0].isBlank()) {
            throw new YoutubeDownloadException("YouTube linkini ochib bo‘lmadi. Linkni tekshiring.");
        }
        return new VideoMetadata(parts[0], parts[1], parseDuration(parts[2]));
    }

    private long parseDuration(String raw) {
        try {
            return (long) Double.parseDouble(raw.trim());
        } catch (NumberFormatException exception) {
            throw new YoutubeDownloadException("Video davomiyligini aniqlab bo‘lmadi (jonli efir bo‘lishi mumkin)");
        }
    }

    private Path downloadAudio(String url, Path tempDir) throws IOException {
        Path outputTemplate = tempDir.resolve("audio.%(ext)s");
        List<String> command = List.of(
                BINARY, "--no-warnings", "--no-playlist",
                "-f", "bestaudio",
                "-x", "--audio-format", "mp3", "--audio-quality", "5",
                "--max-filesize", "20M",
                "-o", outputTemplate.toString(),
                url
        );
        run(command, tempDir);

        Path audioFile = tempDir.resolve("audio.mp3");
        if (!Files.exists(audioFile)) {
            throw new YoutubeDownloadException("Audio faylni ajratib bo‘lmadi");
        }
        return audioFile;
    }

    private String run(List<String> command, Path workingDir) throws IOException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            RunResult result = runOnce(command, workingDir);
            if (result.exitCode() == 0) {
                return result.output();
            }

            log.warn("yt-dlp muvaffaqiyatsiz tugadi ({}-urinish/{}, exit={}): {}", attempt, MAX_ATTEMPTS, result.exitCode(), limit(result.output()));
            if (attempt == MAX_ATTEMPTS) {
                throw new YoutubeDownloadException("YouTube havolasidan audio olib bo‘lmadi. Linkni tekshiring.");
            }
        }
        throw new YoutubeDownloadException("YouTube havolasidan audio olib bo‘lmadi. Linkni tekshiring.");
    }

    private record RunResult(int exitCode, String output) {
    }

    private RunResult runOnce(List<String> command, Path workingDir) throws IOException {
        Duration timeout = properties.youtube().downloadTimeout();
        Path logFile = Files.createTempFile(workingDir, "yt-dlp-", ".log");

        Process process = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();

        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
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
