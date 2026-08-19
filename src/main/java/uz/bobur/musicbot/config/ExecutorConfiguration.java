package uz.bobur.musicbot.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ExecutorConfiguration {

    @Bean(name = "botTaskExecutor", destroyMethod = "shutdown")
    public ExecutorService botTaskExecutor(
            ApplicationProperties properties,
            MeterRegistry meterRegistry
    ) {
        int workers = properties.processing().maxConcurrentJobs();
        int queueCapacity = properties.processing().queueCapacity();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofVirtual().name("music-bot-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );

        meterRegistry.gauge("bot.executor.active", executor, ThreadPoolExecutor::getActiveCount);
        meterRegistry.gauge("bot.executor.queue.size", executor, exec -> exec.getQueue().size());
        meterRegistry.gauge("bot.executor.completed", executor, ThreadPoolExecutor::getCompletedTaskCount);

        return executor;
    }

    /**
     * Dedicated executor for the expensive second-stage search. It must not occupy
     * the Telegram update workers; otherwise background recommendations would make
     * new user messages feel slow again.
     */
    @Bean(name = "musicSearchBackgroundExecutor", destroyMethod = "shutdown")
    public ExecutorService musicSearchBackgroundExecutor(
            ApplicationProperties properties,
            MeterRegistry meterRegistry
    ) {
        int workers = Math.max(1, properties.musicSearch().maxConcurrentSearches() - 1);
        int queueCapacity = Math.max(50, properties.processing().queueCapacity());

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofVirtual().name("music-search-bg-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );

        meterRegistry.gauge("music.search.background.active", executor, ThreadPoolExecutor::getActiveCount);
        meterRegistry.gauge("music.search.background.queue.size", executor, exec -> exec.getQueue().size());
        meterRegistry.gauge("music.search.background.completed", executor, ThreadPoolExecutor::getCompletedTaskCount);

        return executor;
    }
}