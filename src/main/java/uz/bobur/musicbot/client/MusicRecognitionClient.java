package uz.bobur.musicbot.client;

import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.RecognitionResult;

import java.util.Optional;

public interface MusicRecognitionClient {

    Optional<RecognitionResult> recognize(DownloadedTelegramFile file);
}
