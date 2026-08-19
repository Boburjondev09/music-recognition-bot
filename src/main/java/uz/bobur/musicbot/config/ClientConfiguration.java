package uz.bobur.musicbot.config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class ClientConfiguration {

    @Bean
    public TelegramClient telegramClient(TelegramBotProperties properties) {
        long connectTimeoutMs = Math.max(1L, properties.connectTimeout().toMillis());
        long uploadTimeoutMs = Math.max(1L, properties.uploadTimeout().toMillis());

        /*
         * OkHttpTelegramClient(token) creates its own default OkHttpClient. That means the
         * timeout values from application.yml are NOT applied to media uploads. OkHttp's
         * default write timeout is short enough for a large MP3/MP4 upload to time out even
         * though normal text messages keep working.
         *
         * Use an explicitly configured client for every Telegram Bot API call. The longer
         * write/read/call timeouts are intentional: a 20-50 MB file can take considerably
         * longer than a text request to upload from a home/VPS connection.
         */
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(uploadTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(uploadTimeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(uploadTimeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();

        return new OkHttpTelegramClient(okHttpClient, properties.token());
    }

    @Bean
    @Qualifier("telegramRestClient")
    public RestClient telegramRestClient(RestClient.Builder builder, TelegramBotProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @Qualifier("acrcloudRestClient")
    public RestClient acrcloudRestClient(RestClient.Builder builder, ACRCloudProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        MappingJackson2HttpMessageConverter acrcloudJsonConverter = new MappingJackson2HttpMessageConverter();
        acrcloudJsonConverter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));

        return builder
                .baseUrl("https://" + properties.host())
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .messageConverters(converters -> converters.add(0, acrcloudJsonConverter))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}