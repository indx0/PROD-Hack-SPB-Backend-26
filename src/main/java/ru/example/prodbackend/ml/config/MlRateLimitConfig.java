package ru.example.prodbackend.ml.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.example.prodbackend.ml.service.MlRateLimiter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MlRateLimitProperties.class)
public class MlRateLimitConfig {
    @Bean
    MlRateLimiter mlRateLimiter(MlRateLimitProperties properties, MeterRegistry meters) {
        return new MlRateLimiter(properties, meters, System::nanoTime);
    }

    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService mlRateLimitCleanup(MlRateLimiter limiter) {
        var scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "ml-rate-limit-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(limiter::removeExpired, 1, 1, TimeUnit.MINUTES);
        return scheduler;
    }
}
