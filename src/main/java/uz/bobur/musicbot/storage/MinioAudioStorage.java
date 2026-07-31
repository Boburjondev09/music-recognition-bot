package uz.bobur.musicbot.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.bobur.musicbot.config.MinioProperties;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.TelegramUserContext;
import uz.bobur.musicbot.exception.MusicBotException;
import uz.bobur.musicbot.util.FileNameSanitizer;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
public class MinioAudioStorage implements AudioStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioAudioStorage.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final FileNameSanitizer fileNameSanitizer;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public MinioAudioStorage(MinioClient minioClient, MinioProperties properties, FileNameSanitizer fileNameSanitizer) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.fileNameSanitizer = fileNameSanitizer;
    }

    @Override
    public Optional<String> store(DownloadedTelegramFile file, TelegramUserContext user) {
        try {
            ensureBucketExists();
            String objectName = buildObjectName(file.fileName(), user.userId());

            minioClient.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectName).stream(new ByteArrayInputStream(file.content()), file.size(), -1).contentType(file.contentType()).build());

            return Optional.of(objectName);
        } catch (Exception exception) {
            log.warn("MinIO fayl saqlash xatosi: {}", exception.getClass().getSimpleName());
            if (properties.failOnError()) {
                throw new MusicBotException("Audio faylni MinIO'ga saqlab bo‘lmadi");
            }
            return Optional.empty();
        }
    }

    private void ensureBucketExists() throws Exception {
        if (bucketReady.get()) {
            return;
        }

        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }

            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());

            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
            bucketReady.set(true);
        }
    }

    private String buildObjectName(String originalFileName, long userId) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        String safeName = fileNameSanitizer.sanitize(originalFileName);
        return "%04d/%02d/%02d/%d/%s-%s".formatted(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), userId, UUID.randomUUID(), safeName);
    }
}
