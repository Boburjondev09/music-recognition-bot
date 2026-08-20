package uz.bobur.musicbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.bobur.musicbot.config.ApplicationProperties;
import uz.bobur.musicbot.domain.MusicSearchResult;
import uz.bobur.musicbot.domain.TelegramUserContext;
import uz.bobur.musicbot.domain.search.AudioQuality;
import uz.bobur.musicbot.domain.search.SearchTheme;
import uz.bobur.musicbot.exception.MusicSearchException;
import uz.bobur.musicbot.service.MusicCatalogSearchService;
import uz.bobur.musicbot.service.MusicSearchSessionService;
import uz.bobur.musicbot.service.UserRateLimiter;
import uz.bobur.musicbot.service.Mp3DownloadService;

import java.util.List;
import java.util.Optional;

@Component
public class MusicTextSearchHandler {

    private static final Logger log = LoggerFactory.getLogger(MusicTextSearchHandler.class);

    private final MusicCatalogSearchService catalogSearchService;
    private final MusicSearchSessionService sessionService;
    private final Mp3DownloadService mp3DownloadService;
    private final TelegramMessageSender messageSender;
    private final BotMessageFormatter formatter;
    private final UserRateLimiter rateLimiter;
    private final ApplicationProperties properties;

    public MusicTextSearchHandler(
            MusicCatalogSearchService catalogSearchService,
            MusicSearchSessionService sessionService,
            Mp3DownloadService mp3DownloadService,
            TelegramMessageSender messageSender,
            BotMessageFormatter formatter,
            UserRateLimiter rateLimiter,
            ApplicationProperties properties
    ) {
        this.catalogSearchService = catalogSearchService;
        this.sessionService = sessionService;
        this.mp3DownloadService = mp3DownloadService;
        this.messageSender = messageSender;
        this.formatter = formatter;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public boolean supportsCallback(String data) {
        if (data == null) {
            return false;
        }

        return data.startsWith("ms:")
                || data.startsWith("ma:")
                || data.startsWith("mak:")
                || data.startsWith("mr:")
                || data.startsWith("mx:")
                || data.startsWith("mp:")
                || data.startsWith("mb:")
                || data.startsWith("mg:")
                || data.startsWith("mo:")
                || data.startsWith("so:")
                || data.startsWith("mt:");
    }

    public void handleText(TelegramUserContext user, String query) {
        if (!properties.musicSearch().enabled()) {
            messageSender.sendText(
                    user.chatId(),
                    "Matn orqali qo‘shiq qidirish hozircha o‘chirilgan."
            );
            return;
        }

        if (!rateLimiter.tryAcquire(user.userId())) {
            messageSender.sendText(user.chatId(), formatter.rateLimitExceeded());
            return;
        }

        Integer progressMessageId = messageSender.sendAndReturn(
                user.chatId(),
                "🔎 Qo‘shiq va similar treklar qidirilmoqda..."
        );

        try {
            List<MusicSearchResult> results = catalogSearchService.searchWithRecommendations(query);

            if (results.isEmpty()) {
                messageSender.sendText(user.chatId(), formatter.musicSearchNotFound(query));
                return;
            }

            MusicSearchSessionService.SearchSession session = sessionService.create(
                    user,
                    query,
                    results
            );

            messageSender.sendMusicSearchResults(
                    user.chatId(),
                    formatter.musicSearchResults(session),
                    session
            );
        } catch (MusicSearchException exception) {
            messageSender.sendText(user.chatId(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Matn orqali qo‘shiq qidirishda kutilmagan xatolik", exception);
            messageSender.sendText(
                    user.chatId(),
                    "Qo‘shiq qidirishda ichki xatolik yuz berdi. Birozdan keyin qayta urinib ko‘ring."
            );
        } finally {
            messageSender.deleteMessage(user.chatId(), progressMessageId);
        }
    }

    public void handleCallback(
            TelegramUserContext user,
            String data,
            Integer sourceMessageId
    ) {
        if (data.startsWith("mx:")) {
            close(user, data.substring(3), sourceMessageId);
            return;
        }

        if (data.startsWith("mb:")) {
            renderResults(user, data.substring(3), sourceMessageId);
            return;
        }

        if (data.startsWith("mg:")) {
            renderSettings(user, data.substring(3), sourceMessageId);
            return;
        }

        if (data.startsWith("mo:")) {
            changeSort(user, data.substring(3), sourceMessageId, false);
            return;
        }

        if (data.startsWith("so:")) {
            changeSort(user, data.substring(3), sourceMessageId, true);
            return;
        }

        if (data.startsWith("mt:")) {
            changeTheme(user, data, sourceMessageId);
            return;
        }

        if (data.startsWith("mak:")) {
            handleSelectedBitrate(user, data, sourceMessageId);
            return;
        }

        ParsedCallback callback = parseIndexedCallback(data);
        if (callback == null) {
            messageSender.sendText(
                    user.chatId(),
                    "Qidiruv tugmasi noto‘g‘ri. Qayta qidirib ko‘ring."
            );
            return;
        }

        if ("mp".equals(callback.action())) {
            changePage(
                    user,
                    callback.sessionId(),
                    callback.index(),
                    sourceMessageId
            );
            return;
        }

        Optional<MusicSearchSessionService.SearchSession> sessionOptional =
                sessionService.find(callback.sessionId(), user);

        if (sessionOptional.isEmpty()) {
            expired(user);
            return;
        }

        MusicSearchSessionService.SearchSession session = sessionOptional.get();
        Optional<MusicSearchResult> resultOptional = session.resultAt(callback.index());

        if (resultOptional.isEmpty()) {
            expired(user);
            return;
        }

        MusicSearchResult result = resultOptional.get();

        switch (callback.action()) {
            case "ms" -> showSelectedTrack(
                    user,
                    sourceMessageId,
                    callback.index(),
                    session,
                    result
            );

            case "ma" -> showAudioQualityOptions(
                    user,
                    sourceMessageId,
                    callback.index(),
                    session
            );

            case "mr" -> sendRecommendations(
                    user,
                    sourceMessageId,
                    session,
                    result
            );

            default -> messageSender.sendText(
                    user.chatId(),
                    "Noma’lum qidiruv amali."
            );
        }
    }

    private void changePage(
            TelegramUserContext user,
            String sessionId,
            int page,
            Integer sourceMessageId
    ) {
        sessionService.setPage(sessionId, page, user)
                .ifPresentOrElse(
                        session -> messageSender.editMusicSearchResults(
                                user.chatId(),
                                sourceMessageId,
                                formatter.musicSearchResults(session),
                                session
                        ),
                        () -> expired(user)
                );
    }

    private void renderResults(
            TelegramUserContext user,
            String sessionId,
            Integer sourceMessageId
    ) {
        sessionService.find(sessionId, user)
                .ifPresentOrElse(
                        session -> messageSender.editMusicSearchResults(
                                user.chatId(),
                                sourceMessageId,
                                formatter.musicSearchResults(session),
                                session
                        ),
                        () -> expired(user)
                );
    }

    private void renderSettings(
            TelegramUserContext user,
            String sessionId,
            Integer sourceMessageId
    ) {
        sessionService.find(sessionId, user)
                .ifPresentOrElse(
                        session -> messageSender.editMusicSearchSettings(
                                user.chatId(),
                                sourceMessageId,
                                formatter.searchSettings(session),
                                session
                        ),
                        () -> expired(user)
                );
    }

    private void changeSort(
            TelegramUserContext user,
            String sessionId,
            Integer sourceMessageId,
            boolean stayInSettings
    ) {
        sessionService.cycleSort(sessionId, user)
                .ifPresentOrElse(
                        session -> editAfterSortChange(
                                user,
                                sourceMessageId,
                                session,
                                stayInSettings
                        ),
                        () -> expired(user)
                );
    }

    private void editAfterSortChange(
            TelegramUserContext user,
            Integer sourceMessageId,
            MusicSearchSessionService.SearchSession session,
            boolean stayInSettings
    ) {
        if (stayInSettings) {
            messageSender.editMusicSearchSettings(
                    user.chatId(),
                    sourceMessageId,
                    formatter.searchSettings(session),
                    session
            );
            return;
        }

        messageSender.editMusicSearchResults(
                user.chatId(),
                sourceMessageId,
                formatter.musicSearchResults(session),
                session
        );
    }

    private void changeTheme(
            TelegramUserContext user,
            String data,
            Integer sourceMessageId
    ) {
        String[] parts = data.split(":", 3);
        if (parts.length != 3) {
            messageSender.sendText(user.chatId(), "Theme tugmasi noto‘g‘ri.");
            return;
        }

        SearchTheme theme = switch (parts[2]) {
            case "D" -> SearchTheme.DEFAULT;
            case "M" -> SearchTheme.MINIMAL;
            case "P" -> SearchTheme.PIXEL;
            case "U" -> SearchTheme.MUSIC;
            default -> null;
        };

        if (theme == null) {
            messageSender.sendText(user.chatId(), "Noma’lum theme.");
            return;
        }

        sessionService.setTheme(parts[1], theme, user)
                .ifPresentOrElse(
                        session -> messageSender.editMusicSearchSettings(
                                user.chatId(),
                                sourceMessageId,
                                formatter.searchSettings(session),
                                session
                        ),
                        () -> expired(user)
                );
    }

    private void showSelectedTrack(
            TelegramUserContext user,
            Integer sourceMessageId,
            int globalIndex,
            MusicSearchSessionService.SearchSession session,
            MusicSearchResult result
    ) {
        messageSender.editMusicSearchTrackOptions(
                user.chatId(),
                sourceMessageId,
                formatter.selectedTrack(result, session),
                session,
                globalIndex
        );
    }

    private void showAudioQualityOptions(
            TelegramUserContext user,
            Integer sourceMessageId,
            int globalIndex,
            MusicSearchSessionService.SearchSession session
    ) {
        messageSender.editMusicSearchAudioQualityOptions(
                user.chatId(),
                sourceMessageId,
                session,
                globalIndex
        );
    }

    private void handleSelectedBitrate(
            TelegramUserContext user,
            String data,
            Integer sourceMessageId
    ) {
        SearchAudioQualityCallback callback = parseAudioQualityCallback(data);
        if (callback == null) {
            messageSender.sendText(user.chatId(), "MP3 sifati noto‘g‘ri tanlandi.");
            return;
        }

        Optional<MusicSearchSessionService.SearchSession> sessionOptional =
                sessionService.find(callback.sessionId(), user);

        if (sessionOptional.isEmpty()) {
            expired(user);
            return;
        }

        MusicSearchSessionService.SearchSession session = sessionOptional.get();
        Optional<MusicSearchResult> resultOptional = session.resultAt(callback.globalIndex());

        if (resultOptional.isEmpty()) {
            expired(user);
            return;
        }

        AudioQuality quality;
        try {
            quality = AudioQuality.fromKbps(callback.kbps());
        } catch (IllegalArgumentException exception) {
            messageSender.sendText(
                    user.chatId(),
                    "Faqat 128, 192, 256 yoki 320 kbps ni tanlang."
            );
            return;
        }

        downloadAudio(
                user,
                resultOptional.get(),
                quality
        );
    }

    private void downloadAudio(
            TelegramUserContext user,
            MusicSearchResult result,
            AudioQuality quality
    ) {
        if (!rateLimiter.tryAcquire(user.userId())) {
            messageSender.sendText(user.chatId(), formatter.rateLimitExceeded());
            return;
        }

        Integer progressMessageId = messageSender.sendAndReturn(
                user.chatId(),
                "⏳ MP3 tayyorlanmoqda: %s: %s...".formatted(result.title(), quality.label())
        );

        try {
            Mp3DownloadService.DownloadedMp3 audio =
                    mp3DownloadService.download(
                            result,
                            quality
                    );

            boolean sent = messageSender.sendAudio(
                    user.chatId(),
                    audio.content(),
                    audio.fileName(),
                    result.title(),
                    result.artist()
            );

            if (!sent) {
                messageSender.sendText(
                        user.chatId(),
                        "MP3 tayyorlandi, lekin Telegram orqali yuborib bo‘lmadi. Birozdan keyin qayta urinib ko‘ring."
                );
            }
        } catch (uz.bobur.musicbot.exception.Mp3SourceException exception) {
            messageSender.sendText(user.chatId(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Qidiruv natijasidan MP3 yuklab berishda xatolik", exception);
            messageSender.sendText(
                    user.chatId(),
                    "MP3 faylni tayyorlab bo‘lmadi. Birozdan keyin qayta urinib ko‘ring."
            );
        } finally {
            messageSender.deleteMessage(user.chatId(), progressMessageId);
        }
    }

    private void sendRecommendations(
            TelegramUserContext user,
            Integer sourceMessageId,
            MusicSearchSessionService.SearchSession oldSession,
            MusicSearchResult seed
    ) {
        if (!rateLimiter.tryAcquire(user.userId())) {
            messageSender.sendText(user.chatId(), formatter.rateLimitExceeded());
            return;
        }

        Integer progressMessageId = messageSender.sendAndReturn(
                user.chatId(),
                "🔁 Similar qo‘shiqlar qidirilmoqda..."
        );

        try {
            List<MusicSearchResult> results =
                    catalogSearchService.searchArtistRecommendations(seed);

            if (results.isEmpty()) {
                messageSender.sendText(user.chatId(), "O‘xshash qo‘shiqlar topilmadi.");
                return;
            }

            String recommendationQuery =
                    seed.artist() == null || seed.artist().isBlank()
                            ? seed.title()
                            : seed.artist() + " — similar songs";

            MusicSearchSessionService.SearchSession newSession = sessionService.create(
                    user,
                    recommendationQuery,
                    results
            );

            // Faqat UI theme saqlanadi. MP3 bitrate har download oldidan tanlanadi.
            sessionService.setTheme(newSession.id(), oldSession.theme(), user);

            messageSender.editMusicSearchResults(
                    user.chatId(),
                    sourceMessageId,
                    formatter.musicSearchResults(newSession),
                    newSession
            );

            sessionService.remove(oldSession.id(), user);
        } catch (MusicSearchException exception) {
            messageSender.sendText(user.chatId(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Recommendation qidirishda xatolik", exception);
            messageSender.sendText(
                    user.chatId(),
                    "O‘xshash qo‘shiqlarni qidirib bo‘lmadi. Birozdan keyin qayta urinib ko‘ring."
            );
        } finally {
            messageSender.deleteMessage(user.chatId(), progressMessageId);
        }
    }

    private void close(
            TelegramUserContext user,
            String sessionId,
            Integer sourceMessageId
    ) {
        sessionService.remove(sessionId, user);
        messageSender.deleteMessage(user.chatId(), sourceMessageId);
    }

    private void expired(TelegramUserContext user) {
        messageSender.sendText(
                user.chatId(),
                "Bu qidiruv natijasi eskirgan. Qo‘shiq nomini qayta yozib qidiring."
        );
    }

    private ParsedCallback parseIndexedCallback(String data) {
        String[] parts = data.split(":", 3);
        if (parts.length != 3 || parts[1].isBlank()) {
            return null;
        }

        try {
            int index = Integer.parseInt(parts[2]);
            if (index < 0
                    || index > Math.max(properties.musicSearch().maxResults(), 1000)) {
                return null;
            }

            return new ParsedCallback(
                    parts[0],
                    parts[1],
                    index
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private SearchAudioQualityCallback parseAudioQualityCallback(String data) {
        String[] parts = data.split(":", 4);
        if (parts.length != 4
                || !"mak".equals(parts[0])
                || parts[1].isBlank()) {
            return null;
        }

        try {
            int globalIndex = Integer.parseInt(parts[2]);
            int kbps = Integer.parseInt(parts[3]);

            if (globalIndex < 0
                    || globalIndex > Math.max(properties.musicSearch().maxResults(), 1000)) {
                return null;
            }

            return new SearchAudioQualityCallback(
                    parts[1],
                    globalIndex,
                    kbps
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record ParsedCallback(
            String action,
            String sessionId,
            int index
    ) {
    }

    private record SearchAudioQualityCallback(
            String sessionId,
            int globalIndex,
            int kbps
    ) {
    }
}