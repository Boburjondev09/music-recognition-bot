package uz.bobur.musicbot.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bobur.musicbot.domain.TelegramUserContext;
import uz.bobur.musicbot.persistence.BotUser;
import uz.bobur.musicbot.persistence.BotUserRepository;

@Service
public class UserRegistrationService {

    private final BotUserRepository repository;

    public UserRegistrationService(BotUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registerOrUpdate(TelegramUserContext user) {
        BotUser botUser = repository.findById(user.userId()).orElseGet(() -> BotUser.register(user));
        botUser.updateProfile(user);
        repository.save(botUser);
    }
}
