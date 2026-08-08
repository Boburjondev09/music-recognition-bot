package uz.bobur.musicbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
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
import uz.bobur.musicbot.service.UserRegistrationService;
import uz.bobur.musicbot.service.YoutubeMediaDownloader;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class TelegramUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);

    private final TelegramMediaExtractor mediaExtractor;
    private final YoutubeLinkDetector youtubeLinkDetector;
    private final TelegramFileService telegramFileService;
    private final YoutubeMediaDownloader youtubeMediaDownloader;
    private final MusicRecognitionService recognitionService;
    private final SearchHistoryService historyService;
    private final UserRegistrationService userRegistrationService;
    private final TelegramMessageSender messageSender;
    private final BotMessageFormatter formatter;
    private final ApplicationProperties properties;
    private final UserRateLimiter rateLimiter;

    public TelegramUpdateHandler(TelegramMediaExtractor mediaExtractor, YoutubeLinkDetector youtubeLinkDetector, TelegramFileService telegramFileService, YoutubeMediaDownloader youtubeMediaDownloader, MusicRecognitionService recognitionService, SearchHistoryService historyService, UserRegistrationService userRegistrationService, TelegramMessageSender messageSender, BotMessageFormatter formatter, ApplicationProperties properties, UserRateLimiter rateLimiter) {
        this.mediaExtractor = mediaExtractor;
        this.youtubeLinkDetector = youtubeLinkDetector;
        this.telegramFileService = telegramFileService;
        this.youtubeMediaDownloader = youtubeMediaDownloader;
        this.recognitionService = recognitionService;
        this.historyService = historyService;
        this.userRegistrationService = userRegistrationService;
        this.messageSender = messageSender;
        this.formatter = formatter;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    public void handle(Update update) {
        if (update == null) {
            return;
        }

        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }

        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        TelegramUserContext user = userContext(message);
        userRegistrationService.registerOrUpdate(user);

        if (message.hasText() && message.getText().startsWith("/")) {
            handleCommand(user, message.getText());
            return;
        }

        Optional<TelegramMedia> mediaOptional = mediaExtractor.extract(message);
        if (mediaOptional.isEmpty()) {
            Optional<YoutubeLinkDetector.YoutubeLink> youtubeLink = message.hasText() ? youtubeLinkDetector.extract(message.getText()) : Optional.empty();
            if (youtubeLink.isPresent()) {
                handleYoutubeLink(user, youtubeLink.get());
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
            messageSender.sendText(user.chatId(), formatter.rateLimitExceeded());
            return;
        }

        Optional<RecognitionResult> cachedMedia = historyService.findCachedResult(media.fileUniqueId(), media.mediaType());
        if (cachedMedia.isPresent()) {
            messageSender.sendText(user.chatId(), formatter.recognized(cachedMedia.get()));
            return;
        }

        Integer progressMessageId = messageSender.sendAndReturn(user.chatId(), "⏳ Audio tahlil qilinmoqda...");
        processRecognition(user, progressMessageId, () -> new RecognitionInput(media, telegramFileService.download(media)), result -> {
        });
    }

    private void handleYoutubeLink(TelegramUserContext user, YoutubeLinkDetector.YoutubeLink link) {
        if (!properties.youtube().enabled()) {
            messageSender.sendText(user.chatId(), "YouTube link orqali aniqlash hozircha o‘chirilgan. Iltimos, audio yoki voice fayl yuboring.");
            return;
        }

        if (!rateLimiter.tryAcquire(user.userId())) {
            messageSender.sendText(user.chatId(), formatter.rateLimitExceeded());
            return;
        }

        messageSender.sendYoutubeDownloadOptions(user.chatId(), link.videoId());

        Optional<RecognitionResult> cached = historyService.findCachedResult(link.videoId(), MediaType.YOUTUBE_LINK);
        if (cached.isPresent()) {
            messageSender.sendText(user.chatId(), formatter.recognized(cached.get()));
            return;
        }

        String url = link.url();
        Integer progressMessageId = messageSender.sendAndReturn(user.chatId(), "⏳ YouTube havolasidan audio yuklab olinmoqda...");
        processRecognition(user, progressMessageId, () -> {
            YoutubeMediaDownloader.DownloadedYoutubeMedia sample = youtubeMediaDownloader.downloadSample(url);
            TelegramMedia media = new TelegramMedia(sample.videoId(), sample.videoId(), MediaType.YOUTUBE_LINK, sample.fileName(), sample.contentType(), (long) sample.content().length);
            DownloadedTelegramFile file = new DownloadedTelegramFile(url, sample.fileName(), sample.contentType(), sample.content());
            return new RecognitionInput(media, file);
        }, result -> {
        });
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        messageSender.answerCallbackQuery(callbackQuery.getId());

        String data = callbackQuery.getData();
        if (data == null || callbackQuery.getMessage() == null) {
            return;
        }

        User from = callbackQuery.getFrom();
        TelegramUserContext user = new TelegramUserContext(from.getId(), callbackQuery.getMessage().getChatId(), from.getUserName(), from.getFirstName());
        userRegistrationService.registerOrUpdate(user);

        if (data.startsWith("yta:")) {
            handleYoutubeAudioButton(user, data.substring(4));
        } else if (data.startsWith("ytv:")) {
            handleYoutubeVideoButton(user, data.substring(4));
        }
    }

    private void handleYoutubeAudioButton(TelegramUserContext user, String videoId) {
        String url = "https://www.youtube.com/watch?v=" + videoId;
        Optional<RecognitionResult> cached = historyService.findCachedResult(videoId, MediaType.YOUTUBE_LINK);
        String title = cached.map(RecognitionResult::title).orElse(null);
        String artist = cached.map(RecognitionResult::artist).orElse(null);

        handleYoutubeButton(user, "⏳ Audio tayyorlanmoqda...", "Audio yuklab berishda kutilmagan xatolik", "Audio faylni yuklab bo‘lmadi. Birozdan keyin qayta urinib ko‘ring.",
                () -> youtubeMediaDownloader.downloadFullAudio(url),
                audio -> messageSender.sendAudio(user.chatId(), audio.content(), audio.fileName(), title, artist));
    }

    private void handleYoutubeVideoButton(TelegramUserContext user, String videoId) {
        String url = "https://www.youtube.com/watch?v=" + videoId;
        handleYoutubeButton(user, "⏳ Video tayyorlanmoqda...", "Video yuklab berishda kutilmagan xatolik", "Video faylni yuklab bo‘lmadi. Birozdan keyin qayta urinib ko‘ring.",
                () -> youtubeMediaDownloader.downloadVideo(url),
                video -> messageSender.sendVideo(user.chatId(), video.content(), video.fileName()));
    }

    private void handleYoutubeButton(TelegramUserContext user, String progressText, String logMessage, String failureMessage,
                                      Supplier<YoutubeMediaDownloader.DownloadedYoutubeMedia> downloader,
                                      Consumer<YoutubeMediaDownloader.DownloadedYoutubeMedia> sender) {
        if (!rateLimiter.tryAcquire(user.userId())) {
            messageSender.sendText(user.chatId(), formatter.rateLimitExceeded());
            return;
        }

        Integer progressMessageId = messageSender.sendAndReturn(user.chatId(), progressText);
        try {
            sender.accept(downloader.get());
        } catch (YoutubeDownloadException exception) {
            messageSender.sendText(user.chatId(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error(logMessage, exception);
            messageSender.sendText(user.chatId(), failureMessage);
        } finally {
            messageSender.deleteMessage(user.chatId(), progressMessageId);
        }
    }

    private record RecognitionInput(TelegramMedia media, DownloadedTelegramFile file) {
    }

    private void processRecognition(TelegramUserContext user, Integer progressMessageId, Supplier<RecognitionInput> inputSupplier, Consumer<RecognitionResult> onRecognized) {
        try {
            RecognitionInput input = inputSupplier.get();
            Optional<RecognitionResult> result = recognitionService.recognize(user, input.media(), input.file());

            if (result.isPresent()) {
                messageSender.sendText(user.chatId(), formatter.recognized(result.get()));
                onRecognized.accept(result.get());
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
                    messageSender.sendText(user.chatId(), formatter.adminOnly());
                    return;
                }
                List<SearchHistoryView> history = historyService.recent(user.userId(), properties.recognition().historyLimit());
                messageSender.sendText(user.chatId(), formatter.history(history));
            }
            case "/clear_history" -> {
                if (!properties.admin().isAdmin(user.userId())) {
                    messageSender.sendText(user.chatId(), formatter.adminOnly());
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
