package uz.bobur.musicbot.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.TelegramUserContext;

import java.util.Optional;

@Component
@ConditionalOnProperty(
        prefix = "minio",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpAudioStorage implements AudioStorage {

    @Override
    public Optional<String> store(DownloadedTelegramFile file, TelegramUserContext user) {
        return Optional.empty();
    }
}
