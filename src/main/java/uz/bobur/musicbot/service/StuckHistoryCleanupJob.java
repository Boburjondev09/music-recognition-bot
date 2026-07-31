package uz.bobur.musicbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.bobur.musicbot.config.ApplicationProperties;
import uz.bobur.musicbot.enums.RecognitionStatus;
import uz.bobur.musicbot.persistence.MusicSearchHistoryRepository;

import java.time.Duration;
import java.time.Instant;

@Component
public class StuckHistoryCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StuckHistoryCleanupJob.class);

    private final MusicSearchHistoryRepository repository;
    private final ApplicationProperties properties;

    public StuckHistoryCleanupJob(MusicSearchHistoryRepository repository, ApplicationProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval-millis}")
    @Transactional
    public void cleanupStuckProcessing() {
        Instant now = Instant.now();
        Instant threshold = now.minus(Duration.ofMinutes(properties.cleanup().stuckProcessingMinutes()));
        int updated = repository.markStuckProcessingAsFailed(
                RecognitionStatus.PROCESSING,
                RecognitionStatus.FAILED,
                threshold,
                "Qayta ishlash vaqti tugadi (timeout)",
                now
        );
        if (updated > 0) {
            log.info("Stuck PROCESSING history yozuvlari FAILED qilindi: {}", updated);
        }
    }
}
