package uz.bobur.musicbot.storage;

import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.TelegramUserContext;

import java.util.Optional;

public interface AudioStorage {

    Optional<String> store(DownloadedTelegramFile file, TelegramUserContext user);
}
