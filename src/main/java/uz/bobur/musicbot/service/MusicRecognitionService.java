package uz.bobur.musicbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.bobur.musicbot.client.MusicRecognitionClient;
import uz.bobur.musicbot.config.ApplicationProperties;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.RecognitionResult;
import uz.bobur.musicbot.domain.TelegramMedia;
import uz.bobur.musicbot.domain.TelegramUserContext;
import uz.bobur.musicbot.exception.FileValidationException;
import uz.bobur.musicbot.storage.AudioStorage;

import java.util.Optional;
import java.util.UUID;

@Service
public class MusicRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(MusicRecognitionService.class);

    private final MusicRecognitionClient recognitionClient;
    private final AudioStorage audioStorage;
    private final SearchHistoryService historyService;
    private final ApplicationProperties properties;

    public MusicRecognitionService(MusicRecognitionClient recognitionClient, AudioStorage audioStorage, SearchHistoryService historyService, ApplicationProperties properties) {
        this.recognitionClient = recognitionClient;
        this.audioStorage = audioStorage;
        this.historyService = historyService;
        this.properties = properties;
    }

    public Optional<RecognitionResult> recognize(TelegramUserContext user, TelegramMedia media, DownloadedTelegramFile file) {
        validate(file);
        UUID historyId = historyService.createProcessing(user, media, file);

        try {
            audioStorage.store(file, user).ifPresent(objectName -> historyService.attachMinioObject(historyId, objectName));

            Optional<RecognitionResult> result = recognitionClient.recognize(file);
            if (result.isPresent()) {
                historyService.markRecognized(historyId, result.get());
            } else {
                historyService.markNotFound(historyId);
            }
            return result;
        } catch (RuntimeException exception) {
            safelyMarkFailed(historyId, exception.getMessage());
            throw exception;
        }
    }

    private void validate(DownloadedTelegramFile file) {
        if (file.content() == null || file.content().length == 0) {
            throw new FileValidationException("Audio/video fayl bo‘sh");
        }

        long maxSize = properties.recognition().maxFileSizeBytes();
        if (file.size() > maxSize) {
            throw new FileValidationException("Fayl hajmi ruxsat etilgan limitdan katta: " + toMegabytes(maxSize) + " MB");
        }
    }

    private void safelyMarkFailed(UUID historyId, String message) {
        try {
            historyService.markFailed(historyId, message);
        } catch (RuntimeException historyException) {
            log.error("History statusini FAILED qilishda xatolik", historyException);
        }
    }

    private long toMegabytes(long bytes) {
        return bytes / (1024 * 1024);
    }
}
