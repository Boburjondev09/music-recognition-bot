package uz.bobur.musicbot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
}
