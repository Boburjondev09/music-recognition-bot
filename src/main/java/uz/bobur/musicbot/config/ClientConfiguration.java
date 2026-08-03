package uz.bobur.musicbot.config;

import io.minio.MinioClient;
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

@Configuration
public class ClientConfiguration {

    @Bean
    public TelegramClient telegramClient(TelegramBotProperties properties) {
        return new OkHttpTelegramClient(properties.token());
    }

    @Bean
    @Qualifier("telegramRestClient")
    public RestClient telegramRestClient(RestClient.Builder builder, TelegramBotProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder.baseUrl(properties.apiBaseUrl()).requestFactory(requestFactory).build();
    }

    @Bean
    @Qualifier("acrcloudRestClient")
    public RestClient acrcloudRestClient(RestClient.Builder builder, ACRCloudProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        MappingJackson2HttpMessageConverter acrcloudJsonConverter = new MappingJackson2HttpMessageConverter();
        acrcloudJsonConverter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));

        return builder.baseUrl("https://" + properties.host())
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .messageConverters(converters -> converters.add(0, acrcloudJsonConverter))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder().endpoint(properties.endpoint()).credentials(properties.accessKey(), properties.secretKey()).build();
    }
}
