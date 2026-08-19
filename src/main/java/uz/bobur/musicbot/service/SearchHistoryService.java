package uz.bobur.musicbot.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.RecognitionResult;
import uz.bobur.musicbot.domain.SearchHistoryView;
import uz.bobur.musicbot.domain.TelegramMedia;
import uz.bobur.musicbot.domain.TelegramUserContext;
import uz.bobur.musicbot.enums.MediaType;
import uz.bobur.musicbot.enums.RecognitionStatus;
import uz.bobur.musicbot.persistence.MusicSearchHistory;
import uz.bobur.musicbot.persistence.MusicSearchHistoryRepository;
import uz.bobur.musicbot.storage.AudioStorage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SearchHistoryService {

    private final MusicSearchHistoryRepository repository;
    private final AudioStorage audioStorage;

    public SearchHistoryService(MusicSearchHistoryRepository repository, AudioStorage audioStorage) {
        this.repository = repository;

        this.audioStorage = audioStorage;
    }

    @Transactional
    public UUID createProcessing(TelegramUserContext user, TelegramMedia media, DownloadedTelegramFile file) {

        return repository.save(MusicSearchHistory.processing(user, media, file)).getId();
    }

    @Transactional
    public void attachMinioObject(UUID historyId, String objectName) {

        MusicSearchHistory history = getRequired(historyId);

        history.setMinioObjectName(objectName);
    }

    @Transactional
    public void markRecognized(UUID historyId, RecognitionResult result) {

        MusicSearchHistory history = getRequired(historyId);

        history.markRecognized(result);
    }

    @Transactional
    public void markNotFound(UUID historyId) {

        MusicSearchHistory history = getRequired(historyId);

        history.markNotFound();
    }

    @Transactional
    public void markFailed(UUID historyId, String errorMessage) {

        MusicSearchHistory history = getRequired(historyId);

        history.markFailed(limit(errorMessage, 2000));
    }

    @Transactional(readOnly = true)
    public Optional<RecognitionResult> findCachedResult(String telegramFileUniqueId, MediaType mediaType) {

        if (telegramFileUniqueId == null || telegramFileUniqueId.isBlank()) {

            return Optional.empty();
        }

        return repository.findFirstByTelegramFileUniqueIdAndMediaTypeAndStatusOrderByCreatedAtDesc(telegramFileUniqueId, mediaType, RecognitionStatus.RECOGNIZED).map(MusicSearchHistory::toRecognitionResult);
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryView> recent(long telegramUserId, int limit) {

        return repository.findByTelegramUserIdOrderByCreatedAtDesc(telegramUserId, PageRequest.of(0, limit)).stream().map(item -> new SearchHistoryView(item.getStatus(), item.getTitle(), item.getArtist(), item.getErrorMessage(), item.getCreatedAt())).toList();
    }

    @Transactional
    public long clear(long telegramUserId) {

        List<String> objectNames = repository.findMinioObjectNamesByTelegramUserId(telegramUserId);

        for (String objectName : objectNames) {

            audioStorage.delete(objectName);
        }

        return repository.deleteByTelegramUserId(telegramUserId);
    }

    private MusicSearchHistory getRequired(UUID id) {

        return repository.findById(id).orElseThrow(() -> new IllegalStateException("Search history topilmadi: " + id));
    }

    private String limit(String value, int maxLength) {

        if (value == null) {
            return "Noma’lum xatolik";
        }

        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}