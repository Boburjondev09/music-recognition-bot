package uz.bobur.musicbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.bobur.musicbot.domain.MusicSearchResult;
import uz.bobur.musicbot.domain.search.AudioQuality;
import uz.bobur.musicbot.exception.Mp3SourceException;
import uz.bobur.musicbot.util.FileNameSanitizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class Mp3DownloadService {

    private static final Logger log = LoggerFactory.getLogger(Mp3DownloadService.class);
    private static final String YT_DLP = "yt-dlp";
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(180);
    private static final String MAX_SOURCE_FILESIZE = "50M";
    private static final int LOG_EXCERPT_LENGTH = 500;

    /**
     * YouTube now requires a proof-of-origin token before it will serve the actual
     * audio bytes, otherwise the download fails with HTTP 403. The pot-provider
     * sidecar container (bgutil-ytdlp-pot-provider) generates that token on demand.
     */
    private static final String POT_PROVIDER_EXTRACTOR_ARGS =
            "youtubepot-bgutilhttp:base_url=http://pot-provider:4416";

    private final FileNameSanitizer fileNameSanitizer;
    private final String proxy;

    public Mp3DownloadService(
            FileNameSanitizer fileNameSanitizer,
            @Value("${ytdlp.proxy:}") String proxy
    ) {
        this.fileNameSanitizer = fileNameSanitizer;
        this.proxy = proxy;
    }

    public DownloadedMp3 download(
            MusicSearchResult requested,
            AudioQuality quality
    ) {
        Path tempDir = createTempDir();

        try {
            Path outputFile = tempDir.resolve("audio.mp3");
            String query = buildQuery(requested);

            List<String> command = new ArrayList<>(List.of(
                    YT_DLP, "--no-warnings", "--no-playlist",
                    "--extractor-args", POT_PROVIDER_EXTRACTOR_ARGS
            ));

            if (proxy != null && !proxy.isBlank()) {
                command.add("--proxy");
                command.add(proxy);
            }

            command.addAll(List.of(
                    "-f", "bestaudio",
                    "-x", "--audio-format", "mp3",
                    "--audio-quality", quality == null ? AudioQuality.AUTO.ytDlpValue() : quality.ytDlpValue(),
                    "--max-filesize", MAX_SOURCE_FILESIZE,
                    "-o", tempDir.resolve("audio.%(ext)s").toString(),
                    "ytsearch1:" + query
            ));

            run(command, tempDir);

            if (!Files.exists(outputFile)) {
                throw new Mp3SourceException("Bu qo‘shiq uchun YouTube’dan audio manba topilmadi.");
            }

            byte[] content = Files.readAllBytes(outputFile);
            if (content.length == 0) {
                throw new Mp3SourceException("MP3 fayl bo‘sh qaytdi.");
            }

            String fileName = fileNameSanitizer.sanitize(
                    safe(requested.artist()) + " - " + safe(requested.title()) + ".mp3"
            );

            return new DownloadedMp3(fileName, content, requested.trackId());
        } catch (IOException exception) {
            throw new Mp3SourceException("MP3 faylni tayyorlab bo‘lmadi.", exception);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private String buildQuery(MusicSearchResult requested) {
        return (safe(requested.artist()) + " " + safe(requested.title())).trim();
    }

    private void run(List<String> command, Path workingDir) throws IOException {
        Path logFile = Files.createTempFile(workingDir, "yt-dlp-", ".log");

        Process process = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();

        boolean finished;
        try {
            finished = process.waitFor(DOWNLOAD_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new Mp3SourceException("Yuklab olish to‘xtatildi.");
        }

        if (!finished) {
            process.destroyForcibly();
            throw new Mp3SourceException("Yuklab olish vaqti tugadi. Birozdan keyin qayta urinib ko‘ring.");
        }

        if (process.exitValue() != 0) {
            String output = Files.readString(logFile);
            log.warn("yt-dlp muvaffaqiyatsiz tugadi (exit={}): {}", process.exitValue(), limit(output));
            throw new Mp3SourceException("Bu qo‘shiq uchun audio manba topilmadi yoki yuklab bo‘lmadi.");
        }
    }

    private String limit(String value) {
        return value.length() <= LOG_EXCERPT_LENGTH ? value : value.substring(0, LOG_EXCERPT_LENGTH);
    }

    private Path createTempDir() {
        try {
            return Files.createTempDirectory("mp3-source-");
        } catch (IOException exception) {
            throw new Mp3SourceException("Vaqtinchalik papka yaratib bo‘lmadi.", exception);
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    public record DownloadedMp3(
            String fileName,
            byte[] content,
            String sourceTrackId
    ) {
    }
}
