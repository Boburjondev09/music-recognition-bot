package uz.bobur.musicbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MusicRecognitionBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicRecognitionBotApplication.class, args);
    }
}
