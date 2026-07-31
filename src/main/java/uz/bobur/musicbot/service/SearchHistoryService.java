package uz.bobur.musicbot.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.RecognitionResult;
import uz.bobur.musicbot.domain.SearchHistoryView;
import uz.bobur.musicbot.domain.TelegramMedia;
import uz.bobur.musicbot.domain.TelegramUserContext;
import uz.bobur.musicbot.persistence.MusicSearchHistory;
import uz.bobur.musicbot.persistence.MusicSearchHistoryRepository;

import java.util.List;
import java.util.UUID;

@Service
public class SearchHistoryService {

    private final MusicSearchHistoryRepository repository;

    public SearchHistoryService(MusicSearchHistoryRepository repository) {
        this.repository = repository;
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
    public List<SearchHistoryView> recent(long telegramUserId, int limit) {
        return repository.findByTelegramUserIdOrderByCreatedAtDesc(telegramUserId, PageRequest.of(0, limit)).stream().map(item -> new SearchHistoryView(item.getStatus(), item.getTitle(), item.getArtist(), item.getErrorMessage(), item.getCreatedAt())).toList();
    }

    @Transactional
    public long clear(long telegramUserId) {
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
