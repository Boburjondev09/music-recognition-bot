package uz.bobur.musicbot.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.bobur.musicbot.enums.RecognitionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MusicSearchHistoryRepository extends JpaRepository<MusicSearchHistory, UUID> {

    List<MusicSearchHistory> findByTelegramUserIdOrderByCreatedAtDesc(
            Long telegramUserId,
            Pageable pageable
    );

    long deleteByTelegramUserId(Long telegramUserId);

    @Modifying
    @Query("update MusicSearchHistory h set h.status = :failedStatus, h.errorMessage = :message, h.updatedAt = :now " +
            "where h.status = :processingStatus and h.createdAt < :threshold")
    int markStuckProcessingAsFailed(
            @Param("processingStatus") RecognitionStatus processingStatus,
            @Param("failedStatus") RecognitionStatus failedStatus,
            @Param("threshold") Instant threshold,
            @Param("message") String message,
            @Param("now") Instant now
    );
}
