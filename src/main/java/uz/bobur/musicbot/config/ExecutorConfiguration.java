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
    public ExecutorService botTaskExecutor(ApplicationProperties properties, MeterRegistry meterRegistry) {
        int workers = properties.processing().maxConcurrentJobs();
        int queueCapacity = properties.processing().queueCapacity();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity), Thread.ofVirtual().name("music-bot-worker-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());

        // Exposed via /actuator/metrics so maxConcurrentJobs/queueCapacity can be tuned
        // against real load instead of guesswork.
        meterRegistry.gauge("bot.executor.active", executor, ThreadPoolExecutor::getActiveCount);
        meterRegistry.gauge("bot.executor.queue.size", executor, exec -> exec.getQueue().size());
        meterRegistry.gauge("bot.executor.completed", executor, ThreadPoolExecutor::getCompletedTaskCount);

        return executor;
    }
}
