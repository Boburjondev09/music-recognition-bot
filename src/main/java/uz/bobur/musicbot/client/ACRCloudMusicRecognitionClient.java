package uz.bobur.musicbot.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uz.bobur.musicbot.client.dto.ACRCloudResponse;
import uz.bobur.musicbot.config.ACRCloudProperties;
import uz.bobur.musicbot.domain.DownloadedTelegramFile;
import uz.bobur.musicbot.domain.RecognitionResult;
import uz.bobur.musicbot.exception.RecognitionProviderException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
public class ACRCloudMusicRecognitionClient implements MusicRecognitionClient {

    private static final String HTTP_METHOD = "POST";
    private static final String URI = "/v1/identify";
    private static final String DATA_TYPE = "audio";
    private static final String SIGNATURE_VERSION = "1";
    private static final int NO_RESULT_CODE = 1001;
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 500L;
    private static final long MAX_JITTER_MILLIS = 250L;

    private static final Logger log = LoggerFactory.getLogger(ACRCloudMusicRecognitionClient.class);

    private final RestClient restClient;
    private final ACRCloudProperties properties;

    public ACRCloudMusicRecognitionClient(
            @Qualifier("acrcloudRestClient") RestClient restClient,
            ACRCloudProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Optional<RecognitionResult> recognize(DownloadedTelegramFile file) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ACRCloudResponse response = restClient.post()
                        .uri(URI)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(buildRequestBody(file))
                        .retrieve()
                        .body(ACRCloudResponse.class);

                return mapResponse(response);
            } catch (RecognitionProviderException exception) {
                throw exception;
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                boolean retryable = isRetryableStatus(status);
                log.warn(
                        "ACRCloud HTTP xatosi ({}-urinish/{}): status={}, retryable={}, body={}",
                        attempt,
                        MAX_ATTEMPTS,
                        status,
                        retryable,
                        limit(exception.getResponseBodyAsString(), 500)
                );

                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw new RecognitionProviderException("ACRCloud servisiga ulanishda xatolik yuz berdi", exception);
                }
                sleepBeforeRetry(attempt);
            } catch (RestClientException exception) {
                log.warn(
                        "ACRCloud so‘rovi muvaffaqiyatsiz ({}-urinish/{}): {}",
                        attempt,
                        MAX_ATTEMPTS,
                        exception.toString()
                );
                if (attempt == MAX_ATTEMPTS) {
                    throw new RecognitionProviderException("ACRCloud servisiga ulanishda xatolik yuz berdi", exception);
                }
                sleepBeforeRetry(attempt);
            }
        }

        throw new RecognitionProviderException("ACRCloud servisiga ulanishda xatolik yuz berdi");
    }

    boolean isRetryableStatus(int status) {
        return status == 429 || status >= 500;
    }

    private void sleepBeforeRetry(int attempt) {
        long exponential = BASE_BACKOFF_MILLIS * (1L << Math.max(0, attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(MAX_JITTER_MILLIS + 1);
        long delay = exponential + jitter;

        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RecognitionProviderException("ACRCloud so‘rovi to‘xtatildi", interrupted);
        }
    }

    private MultiValueMap<String, Object> buildRequestBody(DownloadedTelegramFile file) {
        ByteArrayResource resource = new ByteArrayResource(file.content()) {
            @Override
            public String getFilename() {
                return file.fileName();
            }
        };

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = sign(timestamp);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("sample", resource);
        body.add("sample_bytes", String.valueOf(file.content().length));
        body.add("access_key", properties.accessKey());
        body.add("data_type", DATA_TYPE);
        body.add("signature_version", SIGNATURE_VERSION);
        body.add("signature", signature);
        body.add("timestamp", timestamp);
        return body;
    }

    private String sign(String timestamp) {
        String stringToSign = String.join(
                "\n",
                HTTP_METHOD,
                URI,
                properties.accessKey(),
                DATA_TYPE,
                SIGNATURE_VERSION,
                timestamp
        );

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(properties.accessSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] signed = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signed);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new RecognitionProviderException("ACRCloud so‘rovini imzolashda xatolik", exception);
        }
    }

    private Optional<RecognitionResult> mapResponse(ACRCloudResponse response) {
        if (response == null || response.status() == null) {
            throw new RecognitionProviderException("ACRCloud bo‘sh javob qaytardi");
        }

        int code = response.status().code();
        if (code == NO_RESULT_CODE) {
            return Optional.empty();
        }
        if (code != 0) {
            throw new RecognitionProviderException("ACRCloud xatosi: " + limit(response.status().msg(), 500));
        }

        List<ACRCloudResponse.Music> matches = response.metadata() == null
                ? null
                : response.metadata().music();
        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }

        ACRCloudResponse.Music track = matches.getFirst();
        String artist = track.artists() == null
                ? null
                : track.artists().stream()
                .map(ACRCloudResponse.Artist::name)
                .collect(Collectors.joining(", "));
        String album = track.album() == null ? null : track.album().name();
        String timecode = formatTimecode(track.playOffsetMs());

        String spotifyUrl = null;
        if (track.externalMetadata() != null) {
            if (track.externalMetadata().spotify() != null
                    && track.externalMetadata().spotify().track() != null) {
                spotifyUrl = "https://open.spotify.com/track/" + track.externalMetadata().spotify().track().id();
            }
        }

        return Optional.of(new RecognitionResult(
                track.title(),
                artist,
                album,
                track.releaseDate(),
                track.label(),
                timecode,
                null,
                spotifyUrl,
                null
        ));
    }

    private String formatTimecode(Integer playOffsetMs) {
        if (playOffsetMs == null) {
            return null;
        }
        long totalSeconds = playOffsetMs / 1000;
        return "%02d:%02d".formatted(totalSeconds / 60, totalSeconds % 60);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "Noma’lum ACRCloud xatosi";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}