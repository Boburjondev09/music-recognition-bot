package uz.bobur.musicbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import uz.bobur.musicbot.config.ApplicationProperties;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.RecognitionResult;
import uz.bobur.musicbot.domain.SearchHistoryView;
import uz.bobur.musicbot.domain.TelegramMedia;
import uz.bobur.musicbot.domain.TelegramUserContext;
import uz.bobur.musicbot.exception.FileValidationException;
import uz.bobur.musicbot.exception.MusicBotException;
import uz.bobur.musicbot.service.MusicRecognitionService;
import uz.bobur.musicbot.service.SearchHistoryService;
import uz.bobur.musicbot.service.TelegramFileService;
import uz.bobur.musicbot.service.UserRateLimiter;
import uz.bobur.musicbot.service.UserRegistrationService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class TelegramUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);

    private final TelegramMediaExtractor mediaExtractor;
    private final TelegramFileService telegramFileService;
    private final MusicRecognitionService recognitionService;
    private final SearchHistoryService historyService;
    private final UserRegistrationService userRegistrationService;
    private final TelegramMessageSender messageSender;
    private final BotMessageFormatter formatter;
    private final MusicTextSearchHandler textSearchHandler;
    private final ApplicationProperties properties;
    private final UserRateLimiter rateLimiter;

    public TelegramUpdateHandler(
            TelegramMediaExtractor mediaExtractor,
            TelegramFileService telegramFileService,
            MusicRecognitionService recognitionService,
            SearchHistoryService historyService,
            UserRegistrationService userRegistrationService,
            TelegramMessageSender messageSender,
            BotMessageFormatter formatter,
            MusicTextSearchHandler textSearchHandler,
            ApplicationProperties properties,
            UserRateLimiter rateLimiter
    ) {
        this.mediaExtractor = mediaExtractor;
        this.telegramFileService = telegramFileService;
        this.recognitionService = recognitionService;
        this.historyService = historyService;
        this.userRegistrationService = userRegistrationService;
        this.messageSender = messageSender;
        this.formatter = formatter;
        this.textSearchHandler = textSearchHandler;
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
            if (message.hasText() && !message.getText().isBlank()) {
                String text = message.getText().trim();

                if (looksLikeUrl(text)) {
                    messageSender.sendText(
                            user.chatId(),
                            "Link yuborish o‘chirilgan. Qo‘shiq nomi yoki ijrochini yozing. Masalan: Arctic Monkeys I Wanna Be Yours"
                    );
                    return;
                }

                textSearchHandler.handleText(user, text);
                return;
            }

            messageSender.sendText(
                    user.chatId(),
                    formatter.help(properties.musicSearch().enabled(), properties.recognition().maxFileSizeBytes())
            );
            return;
        }

        TelegramMedia media = mediaOptional.get();

        if (isDeclaredFileTooLarge(media)) {
            messageSender.sendText(
                    user.chatId(),
                    formatter.fileTooLarge(properties.recognition().maxFileSizeBytes())
            );
            return;
        }

        if (!rateLimiter.tryAcquire(user.userId())) {
            messageSender.sendText(user.chatId(), formatter.rateLimitExceeded());
            return;
        }

        Optional<RecognitionResult> cachedMedia = historyService.findCachedResult(
                media.fileUniqueId(),
                media.mediaType()
        );

        if (cachedMedia.isPresent()) {
            messageSender.sendText(
                    user.chatId(),
                    formatter.recognized(cachedMedia.get())
            );
            return;
        }

        Integer progressMessageId = messageSender.sendAndReturn(
                user.chatId(),
                "⏳ Audio tahlil qilinmoqda..."
        );

        processRecognition(
                user,
                progressMessageId,
                () -> new RecognitionInput(
                        media,
                        telegramFileService.download(media)
                ),
                result -> {
                }
        );
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        messageSender.answerCallbackQuery(callbackQuery.getId());

        String data = callbackQuery.getData();
        if (data == null || callbackQuery.getMessage() == null) {
            return;
        }

        User from = callbackQuery.getFrom();
        TelegramUserContext user = new TelegramUserContext(
                from.getId(),
                callbackQuery.getMessage().getChatId(),
                from.getUserName(),
                from.getFirstName()
        );

        userRegistrationService.registerOrUpdate(user);

        if (textSearchHandler.supportsCallback(data)) {
            textSearchHandler.handleCallback(
                    user,
                    data,
                    callbackQuery.getMessage().getMessageId()
            );
        }
    }

    private record RecognitionInput(
            TelegramMedia media,
            DownloadedTelegramFile file
    ) {
    }

    private void processRecognition(
            TelegramUserContext user,
            Integer progressMessageId,
            Supplier<RecognitionInput> inputSupplier,
            Consumer<RecognitionResult> onRecognized
    ) {
        try {
            RecognitionInput input = inputSupplier.get();
            Optional<RecognitionResult> result = recognitionService.recognize(
                    user,
                    input.media(),
                    input.file()
            );

            if (result.isPresent()) {
                messageSender.sendText(
                        user.chatId(),
                        formatter.recognized(result.get())
                );
                onRecognized.accept(result.get());
            } else {
                messageSender.sendText(
                        user.chatId(),
                        "Qo‘shiqni aniqlab bo‘lmadi. 10–20 soniyalik, shovqini kamroq parcha yuboring."
                );
            }
        } catch (FileValidationException exception) {
            messageSender.sendText(user.chatId(), exception.getMessage());
        } catch (MusicBotException exception) {
            log.warn(
                    "Recognition jarayonida boshqariladigan xatolik: {}",
                    exception.getMessage()
            );
            messageSender.sendText(
                    user.chatId(),
                    "So‘rovni bajarib bo‘lmadi. Birozdan keyin qayta urinib ko‘ring."
            );
        } catch (RuntimeException exception) {
            log.error("Recognition jarayonida kutilmagan xatolik", exception);
            messageSender.sendText(
                    user.chatId(),
                    "Ichki xatolik yuz berdi. Birozdan keyin qayta urinib ko‘ring."
            );
        } finally {
            messageSender.deleteMessage(user.chatId(), progressMessageId);
        }
    }

    private void handleCommand(TelegramUserContext user, String rawCommand) {
        String command = rawCommand
                .trim()
                .split("\\s+", 2)[0]
                .split("@", 2)[0]
                .toLowerCase(Locale.ROOT);

        switch (command) {
            case "/start" -> messageSender.sendText(
                    user.chatId(),
                    formatter.start(
                            user.firstName(),
                            properties.admin().isAdmin(user.userId()),
                            properties.musicSearch().enabled(),
                            properties.recognition().maxFileSizeBytes()
                    )
            );

            case "/help" -> messageSender.sendText(
                    user.chatId(),
                    formatter.help(properties.musicSearch().enabled(), properties.recognition().maxFileSizeBytes())
            );

            case "/history" -> {
                if (!properties.admin().isAdmin(user.userId())) {
                    messageSender.sendText(user.chatId(), formatter.adminOnly());
                    return;
                }

                List<SearchHistoryView> history = historyService.recent(
                        user.userId(),
                        properties.recognition().historyLimit()
                );

                messageSender.sendText(
                        user.chatId(),
                        formatter.history(history)
                );
            }

            case "/clear_history" -> {
                if (!properties.admin().isAdmin(user.userId())) {
                    messageSender.sendText(user.chatId(), formatter.adminOnly());
                    return;
                }

                try {
                    long deleted = historyService.clear(user.userId());
                    messageSender.sendText(
                            user.chatId(),
                            deleted == 0
                                    ? "Qidiruv tarixi allaqachon bo‘sh."
                                    : "Qidiruv tarixi va unga bog‘liq MinIO fayllari o‘chirildi."
                    );
                } catch (MusicBotException exception) {
                    log.warn(
                            "Unable to clear history for userId={}: {}",
                            user.userId(),
                            exception.getMessage()
                    );
                    messageSender.sendText(
                            user.chatId(),
                            "Qidiruv tarixini tozalab bo‘lmadi. Keyinroq qayta urinib ko‘ring."
                    );
                }
            }

            default -> messageSender.sendText(
                    user.chatId(),
                    "Noma’lum komanda. /help ni yuboring."
            );
        }
    }

    private TelegramUserContext userContext(Message message) {
        User from = message.getFrom();
        long userId = from == null ? message.getChatId() : from.getId();
        String username = from == null ? null : from.getUserName();
        String firstName = from == null ? null : from.getFirstName();

        return new TelegramUserContext(
                userId,
                message.getChatId(),
                username,
                firstName
        );
    }

    private boolean isDeclaredFileTooLarge(TelegramMedia media) {
        return media.declaredFileSize() != null
                && media.declaredFileSize() > properties.recognition().maxFileSizeBytes();
    }

    private boolean looksLikeUrl(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("www.");
    }
}