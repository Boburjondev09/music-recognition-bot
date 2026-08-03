package uz.bobur.musicbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import uz.bobur.musicbot.config.ApplicationProperties;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.RecognitionResult;
import uz.bobur.musicbot.domain.SearchHistoryView;
import uz.bobur.musicbot.domain.TelegramMedia;
import uz.bobur.musicbot.domain.TelegramUserContext;
import uz.bobur.musicbot.enums.MediaType;
import uz.bobur.musicbot.exception.FileValidationException;
import uz.bobur.musicbot.exception.MusicBotException;
import uz.bobur.musicbot.exception.YoutubeDownloadException;
import uz.bobur.musicbot.service.MusicRecognitionService;
import uz.bobur.musicbot.service.SearchHistoryService;
import uz.bobur.musicbot.service.TelegramFileService;
import uz.bobur.musicbot.service.UserRateLimiter;
import uz.bobur.musicbot.service.YoutubeAudioDownloader;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class TelegramUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);

    private final TelegramMediaExtractor mediaExtractor;
    private final YoutubeLinkDetector youtubeLinkDetector;
    private final TelegramFileService telegramFileService;
    private final YoutubeAudioDownloader youtubeAudioDownloader;
    private final MusicRecognitionService recognitionService;
    private final SearchHistoryService historyService;
    private final TelegramMessageSender messageSender;
    private final BotMessageFormatter formatter;
    private final ApplicationProperties properties;
    private final UserRateLimiter rateLimiter;

    public TelegramUpdateHandler(TelegramMediaExtractor mediaExtractor, YoutubeLinkDetector youtubeLinkDetector, TelegramFileService telegramFileService, YoutubeAudioDownloader youtubeAudioDownloader, MusicRecognitionService recognitionService, SearchHistoryService historyService, TelegramMessageSender messageSender, BotMessageFormatter formatter, ApplicationProperties properties, UserRateLimiter rateLimiter) {
        this.mediaExtractor = mediaExtractor;
        this.youtubeLinkDetector = youtubeLinkDetector;
        this.telegramFileService = telegramFileService;
        this.youtubeAudioDownloader = youtubeAudioDownloader;
        this.recognitionService = recognitionService;
        this.historyService = historyService;
        this.messageSender = messageSender;
        this.formatter = formatter;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    public void handle(Update update) {
        if (update == null || !update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        TelegramUserContext user = userContext(message);

        if (message.hasText() && message.getText().startsWith("/")) {
            handleCommand(user, message.getText());
            return;
        }

        Optional<TelegramMedia> mediaOptional = mediaExtractor.extract(message);
        if (mediaOptional.isEmpty()) {
            Optional<String> youtubeUrl = message.hasText() ? youtubeLinkDetector.extract(message.getText()) : Optional.empty();
            if (youtubeUrl.isPresent()) {
                handleYoutubeLink(user, youtubeUrl.get());
                return;
            }
            messageSender.sendText(user.chatId(), formatter.help());
            return;
        }

        TelegramMedia media = mediaOptional.get();
        if (isDeclaredFileTooLarge(media)) {
            messageSender.sendText(user.chatId(), "Fayl 10 MB limitdan katta. Iltimos, qisqaroq audio yoki video parcha yuboring.");
            return;
        }

        if (!rateLimiter.tryAcquire(user.userId())) {
            messageSender.sendText(user.chatId(), "Juda ko‘p so‘rov yubordingiz. Iltimos, birozdan keyin qayta urinib ko‘ring.");
            return;
        }

        Integer progressMessageId = messageSender.sendAndReturn(user.chatId(), "⏳ Audio tahlil qilinmoqda...");
        processRecognition(user, progressMessageId, () -> new RecognitionInput(media, telegramFileService.download(media)));
    }

    private void handleYoutubeLink(TelegramUserContext user, String url) {
        if (!properties.youtube().enabled()) {
            messageSender.sendText(user.chatId(), "YouTube link orqali aniqlash hozircha o‘chirilgan. Iltimos, audio yoki voice fayl yuboring.");
            return;
        }

        if (!rateLimiter.tryAcquire(user.userId())) {
            messageSender.sendText(user.chatId(), "Juda ko‘p so‘rov yubordingiz. Iltimos, birozdan keyin qayta urinib ko‘ring.");
            return;
        }

        Integer progressMessageId = messageSender.sendAndReturn(user.chatId(), "⏳ YouTube havolasidan audio yuklab olinmoqda...");
        processRecognition(user, progressMessageId, () -> {
            YoutubeAudioDownloader.DownloadedYoutubeAudio audio = youtubeAudioDownloader.download(url);
            TelegramMedia media = new TelegramMedia(audio.videoId(), audio.videoId(), MediaType.YOUTUBE_LINK, audio.fileName(), audio.contentType(), (long) audio.content().length);
            DownloadedTelegramFile file = new DownloadedTelegramFile(url, audio.fileName(), audio.contentType(), audio.content());
            return new RecognitionInput(media, file);
        });
    }

    private record RecognitionInput(TelegramMedia media, DownloadedTelegramFile file) {
    }

    private void processRecognition(TelegramUserContext user, Integer progressMessageId, Supplier<RecognitionInput> inputSupplier) {
        try {
            RecognitionInput input = inputSupplier.get();
            Optional<RecognitionResult> result = recognitionService.recognize(user, input.media(), input.file());

            if (result.isPresent()) {
                messageSender.sendText(user.chatId(), formatter.recognized(result.get()));
                if (input.media().mediaType() == MediaType.YOUTUBE_LINK) {
                    messageSender.sendAudio(user.chatId(), input.file().content(), input.file().fileName(), result.get().title(), result.get().artist());
                }
            } else {
                messageSender.sendText(user.chatId(), "Qo‘shiqni aniqlab bo‘lmadi. 10–20 soniyalik, shovqini kamroq parcha yuboring.");
            }
        } catch (FileValidationException | YoutubeDownloadException exception) {
            messageSender.sendText(user.chatId(), exception.getMessage());
        } catch (MusicBotException exception) {
            log.warn("Recognition jarayonida boshqariladigan xatolik: {}", exception.getMessage());
            messageSender.sendText(user.chatId(), "So‘rovni bajarib bo‘lmadi. Birozdan keyin qayta urinib ko‘ring.");
        } catch (RuntimeException exception) {
            log.error("Recognition jarayonida kutilmagan xatolik", exception);
            messageSender.sendText(user.chatId(), "Ichki xatolik yuz berdi. Birozdan keyin qayta urinib ko‘ring.");
        } finally {
            messageSender.deleteMessage(user.chatId(), progressMessageId);
        }
    }

    private void handleCommand(TelegramUserContext user, String rawCommand) {
        String command = rawCommand.trim().split("\\s+", 2)[0].split("@", 2)[0].toLowerCase(Locale.ROOT);

        switch (command) {
            case "/start" -> messageSender.sendText(user.chatId(), formatter.start(user.firstName(), properties.admin().isAdmin(user.userId())));
            case "/help" -> messageSender.sendText(user.chatId(), formatter.help());
            case "/history" -> {
                if (!properties.admin().isAdmin(user.userId())) {
                    messageSender.sendText(user.chatId(), "Bu komanda faqat admin uchun mavjud.");
                    return;
                }
                List<SearchHistoryView> history = historyService.recent(user.userId(), properties.recognition().historyLimit());
                messageSender.sendText(user.chatId(), formatter.history(history));
            }
            case "/clear_history" -> {
                if (!properties.admin().isAdmin(user.userId())) {
                    messageSender.sendText(user.chatId(), "Bu komanda faqat admin uchun mavjud.");
                    return;
                }
                long deleted = historyService.clear(user.userId());
                messageSender.sendText(user.chatId(), deleted == 0 ? "Qidiruv tarixi allaqachon bo‘sh." : "Qidiruv tarixi o‘chirildi.");
            }
            default -> messageSender.sendText(user.chatId(), "Noma’lum komanda. /help ni yuboring.");
        }
    }

    private TelegramUserContext userContext(Message message) {
        User from = message.getFrom();
        long userId = from == null ? message.getChatId() : from.getId();
        String username = from == null ? null : from.getUserName();
        String firstName = from == null ? null : from.getFirstName();
        return new TelegramUserContext(userId, message.getChatId(), username, firstName);
    }

    private boolean isDeclaredFileTooLarge(TelegramMedia media) {
        return media.declaredFileSize() != null && media.declaredFileSize() > properties.recognition().maxFileSizeBytes();
    }
}
