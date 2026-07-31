package uz.bobur.musicbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ExecutorConfiguration {

    @Bean(name = "botTaskExecutor", destroyMethod = "shutdown")
    public ExecutorService botTaskExecutor(ApplicationProperties properties) {
        int workers = properties.processing().maxConcurrentJobs();
        int queueCapacity = properties.processing().queueCapacity();

        return new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity), Thread.ofVirtual().name("music-bot-worker-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }
}
