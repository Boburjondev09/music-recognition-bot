package uz.bobur.musicbot.util;

import org.springframework.stereotype.Component;

@Component
public class FileNameSanitizer {

    private static final int MAX_LENGTH = 180;

    public String sanitize(String fileName) {
        String value = fileName == null || fileName.isBlank() ? "media.bin" : fileName.trim();

        value = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        value = value.replaceAll("_+", "_");

        if (value.length() > MAX_LENGTH) {
            value = value.substring(value.length() - MAX_LENGTH);
        }
        return value;
    }
}
