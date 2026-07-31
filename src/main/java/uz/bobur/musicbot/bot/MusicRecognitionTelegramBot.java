package uz.bobur.musicbot.bot;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.bobur.musicbot.config.TelegramBotProperties;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Component
public class MusicRecognitionTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramBotProperties properties;
    private final TelegramUpdateHandler updateHandler;
    private final TelegramMessageSender messageSender;
    private final ExecutorService taskExecutor;

    public MusicRecognitionTelegramBot(TelegramBotProperties properties, TelegramUpdateHandler updateHandler, TelegramMessageSender messageSender, @Qualifier("botTaskExecutor") ExecutorService taskExecutor) {
        this.properties = properties;
        this.updateHandler = updateHandler;
        this.messageSender = messageSender;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public String getBotToken() {
        return properties.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            taskExecutor.execute(() -> updateHandler.handle(update));
        } catch (RejectedExecutionException exception) {
            if (update != null && update.hasMessage()) {
                messageSender.sendText(update.getMessage().getChatId(), "Bot hozir band. Iltimos, birozdan keyin qayta yuboring.");
            }
        }
    }
}
