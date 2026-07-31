package uz.bobur.musicbot.bot;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Audio;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Video;
import org.telegram.telegrambots.meta.api.objects.VideoNote;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import uz.bobur.musicbot.enums.MediaType;
import uz.bobur.musicbot.domain.TelegramMedia;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class TelegramMediaExtractor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "wav", "ogg", "oga", "m4a", "aac", "flac", "opus", "mp4", "m4v", "mov", "webm", "mpeg", "mpg");

    public Optional<TelegramMedia> extract(Message message) {
        if (message == null) {
            return Optional.empty();
        }

        if (message.hasVoice()) {
            Voice voice = message.getVoice();

            return Optional.of(new TelegramMedia(voice.getFileId(), voice.getFileUniqueId(), MediaType.VOICE, "voice_%d.ogg".formatted(message.getMessageId()), defaultValue(voice.getMimeType(), "audio/ogg"), toLong(voice.getFileSize())));
        }

        if (message.hasAudio()) {
            Audio audio = message.getAudio();

            return Optional.of(new TelegramMedia(audio.getFileId(), audio.getFileUniqueId(), MediaType.AUDIO, defaultValue(audio.getFileName(), "audio_%d.mp3".formatted(message.getMessageId())), defaultValue(audio.getMimeType(), "audio/mpeg"), toLong(audio.getFileSize())));
        }

        if (message.hasVideo()) {
            Video video = message.getVideo();

            return Optional.of(new TelegramMedia(video.getFileId(), video.getFileUniqueId(), MediaType.VIDEO, defaultValue(video.getFileName(), "video_%d.mp4".formatted(message.getMessageId())), defaultValue(video.getMimeType(), "video/mp4"), toLong(video.getFileSize())));
        }

        if (message.hasVideoNote()) {
            VideoNote videoNote = message.getVideoNote();

            return Optional.of(new TelegramMedia(videoNote.getFileId(), videoNote.getFileUniqueId(), MediaType.VIDEO_NOTE, "video_note_%d.mp4".formatted(message.getMessageId()), "video/mp4", toLong(videoNote.getFileSize())));
        }

        if (message.hasDocument()) {
            Document document = message.getDocument();

            if (isSupportedDocument(document)) {
                return Optional.of(new TelegramMedia(document.getFileId(), document.getFileUniqueId(), MediaType.DOCUMENT, defaultValue(document.getFileName(), "media_%d.bin".formatted(message.getMessageId())), defaultValue(document.getMimeType(), "application/octet-stream"), toLong(document.getFileSize())));
            }
        }

        return Optional.empty();
    }

    private boolean isSupportedDocument(Document document) {
        if (document == null) {
            return false;
        }

        String mimeType = document.getMimeType();

        if (mimeType != null && (mimeType.startsWith("audio/") || mimeType.startsWith("video/"))) {
            return true;
        }

        String fileName = document.getFileName();

        if (fileName == null || !fileName.contains(".")) {
            return false;
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);

        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    private Long toLong(Number value) {
        return value == null ? null : value.longValue();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}