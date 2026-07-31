package uz.bobur.musicbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.bobur.musicbot.client.dto.TelegramFileInfo;
import uz.bobur.musicbot.client.dto.TelegramGetFileResponse;
import uz.bobur.musicbot.config.TelegramBotProperties;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.TelegramMedia;
import uz.bobur.musicbot.exception.TelegramFileDownloadException;

@Service
public class TelegramFileService {

    private static final Logger log = LoggerFactory.getLogger(TelegramFileService.class);

    private final RestClient restClient;
    private final TelegramBotProperties properties;

    public TelegramFileService(@Qualifier("telegramRestClient") RestClient restClient, TelegramBotProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public DownloadedTelegramFile download(TelegramMedia media) {
        try {
            TelegramGetFileResponse response = restClient.get().uri(uriBuilder -> uriBuilder.path("/bot" + properties.token() + "/getFile").queryParam("file_id", media.fileId()).build()).retrieve().body(TelegramGetFileResponse.class);

            TelegramFileInfo fileInfo = validateGetFileResponse(response);

            byte[] content = restClient.get().uri("/file/bot" + properties.token() + "/" + fileInfo.filePath()).retrieve().body(byte[].class);

            if (content == null || content.length == 0) {
                throw new TelegramFileDownloadException("Telegram bo‘sh fayl qaytardi");
            }

            return new DownloadedTelegramFile(fileInfo.filePath(), media.fileName(), media.contentType(), content);
        } catch (TelegramFileDownloadException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Telegram faylini yuklash muvaffaqiyatsiz: {}", exception.getClass().getSimpleName());
            throw new TelegramFileDownloadException("Telegram faylini yuklab bo‘lmadi");
        }
    }

    private TelegramFileInfo validateGetFileResponse(TelegramGetFileResponse response) {
        if (response == null) {
            throw new TelegramFileDownloadException("Telegram getFile bo‘sh javob qaytardi");
        }
        if (!response.ok()) {
            throw new TelegramFileDownloadException("Telegram getFile so‘rovi muvaffaqiyatsiz");
        }
        if (response.result() == null || response.result().filePath() == null) {
            throw new TelegramFileDownloadException("Telegram file_path qaytarmadi");
        }
        return response.result();
    }
}
