package uz.bobur.musicbot.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import uz.bobur.musicbot.domain.TelegramUserContext;

import java.time.Instant;

@Entity
@Table(name = "bot_users")
public class BotUser {

    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "telegram_chat_id", nullable = false)
    private Long telegramChatId;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BotUser() {
    }

    public static BotUser register(TelegramUserContext user) {
        BotUser botUser = new BotUser();
        botUser.telegramUserId = user.userId();
        botUser.updateProfile(user);
        return botUser;
    }

    public void updateProfile(TelegramUserContext user) {
        this.telegramChatId = user.chatId();
        this.telegramUsername = user.username();
        this.firstName = user.firstName();
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

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public String getFirstName() {
        return firstName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
