package uz.bobur.musicbot.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.enums.MediaType;
import uz.bobur.musicbot.domain.RecognitionResult;
import uz.bobur.musicbot.enums.RecognitionStatus;
import uz.bobur.musicbot.domain.TelegramMedia;
import uz.bobur.musicbot.domain.TelegramUserContext;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "music_search_history")
public class MusicSearchHistory {

    @Id
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "telegram_chat_id", nullable = false)
    private Long telegramChatId;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Column(name = "telegram_file_id", nullable = false, length = 512)
    private String telegramFileId;

    @Column(name = "telegram_file_unique_id", length = 512)
    private String telegramFileUniqueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 32)
    private MediaType mediaType;

    @Column(name = "original_file_name", length = 512)
    private String originalFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "minio_object_name", length = 1024)
    private String minioObjectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RecognitionStatus status;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "artist", length = 512)
    private String artist;

    @Column(name = "album", length = 512)
    private String album;

    @Column(name = "release_date", length = 64)
    private String releaseDate;

    @Column(name = "label_name", length = 512)
    private String labelName;

    @Column(name = "timecode", length = 64)
    private String timecode;

    @Column(name = "song_link", length = 2048)
    private String songLink;

    @Column(name = "spotify_url", length = 2048)
    private String spotifyUrl;

    @Column(name = "apple_music_url", length = 2048)
    private String appleMusicUrl;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MusicSearchHistory() {
    }

    public static MusicSearchHistory processing(TelegramUserContext user, TelegramMedia media, DownloadedTelegramFile file) {
        MusicSearchHistory history = new MusicSearchHistory();
        history.id = UUID.randomUUID();
        history.telegramUserId = user.userId();
        history.telegramChatId = user.chatId();
        history.telegramUsername = user.username();
        history.telegramFileId = media.fileId();
        history.telegramFileUniqueId = media.fileUniqueId();
        history.mediaType = media.mediaType();
        history.originalFileName = file.fileName();
        history.contentType = file.contentType();
        history.fileSize = file.size();
        history.status = RecognitionStatus.PROCESSING;
        return history;
    }

    public void setMinioObjectName(String minioObjectName) {
        this.minioObjectName = minioObjectName;
    }

    public void markRecognized(RecognitionResult result) {
        this.status = RecognitionStatus.RECOGNIZED;
        this.title = result.title();
        this.artist = result.artist();
        this.album = result.album();
        this.releaseDate = result.releaseDate();
        this.labelName = result.label();
        this.timecode = result.timecode();
        this.songLink = result.songLink();
        this.spotifyUrl = result.spotifyUrl();
        this.appleMusicUrl = result.appleMusicUrl();
        this.errorMessage = null;
    }

    public RecognitionResult toRecognitionResult() {
        return new RecognitionResult(title, artist, album, releaseDate, labelName, timecode, songLink, spotifyUrl, appleMusicUrl);
    }

    public void markNotFound() {
        this.status = RecognitionStatus.NOT_FOUND;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = RecognitionStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public RecognitionStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
