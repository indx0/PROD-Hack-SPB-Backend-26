package ru.example.prodbackend.unit.ml.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.example.prodbackend.ml.config.LlmProxyConfig;
import ru.example.prodbackend.ml.config.MlRateLimitConfig;
import ru.example.prodbackend.ml.config.MlRateLimitProperties;
import ru.example.prodbackend.ml.service.MlRateLimiter;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class MlRateLimitConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MlRateLimitConfig.class, LlmProxyConfig.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void defaultsToFifteenRequestsPerMinute() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MlRateLimitProperties.class)).isEqualTo(new MlRateLimitProperties(15, 60));
        });
    }

    @Test
    void configurationCanOverrideBothLimits() {
        runner.withPropertyValues("ml.rate-limit.max-requests=3", "ml.rate-limit.window-seconds=20")
                .run(context -> assertThat(context.getBean(MlRateLimitProperties.class))
                        .isEqualTo(new MlRateLimitProperties(3, 20)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ml.rate-limit.max-requests=0", "ml.rate-limit.max-requests=-1",
            "ml.rate-limit.window-seconds=0", "ml.rate-limit.window-seconds=-1",
            "ml.rate-limit.window-seconds=9223372037"})
    void invalidLimitsFailAtStartup(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startsTwentyFivePermittedOperationsInParallelAndStopsBothExecutors() {
        var workerReference = new AtomicReference<ExecutorService>();
        var cleanupReference = new AtomicReference<ExecutorService>();
        runner.run(context -> {
            ExecutorService workers = context.getBean("llmExecutor", ExecutorService.class);
            ScheduledExecutorService cleanup = context.getBean("mlRateLimitCleanup", ScheduledExecutorService.class);
            workerReference.set(workers);
            cleanupReference.set(cleanup);
            var limiter = context.getBean(MlRateLimiter.class);
            var entered = new CountDownLatch(25);
            var release = new CountDownLatch(1);
            var pending = new ArrayList<Future<Boolean>>();
            try {
                for (int i = 0; i < 25; i++) {
                    limiter.acquire(UUID.randomUUID());
                    pending.add(workers.submit(() -> {
                        entered.countDown();
                        if (!release.await(5, TimeUnit.SECONDS)) throw new TimeoutException();
                        return Thread.currentThread().isDaemon()
                                && Thread.currentThread().getName().startsWith("llm-proxy-request");
                    }));
                }
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(cleanup.submit(() -> Thread.currentThread().isDaemon()).get(3, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                for (var future : pending) assertThat(future.get(3, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
        });
        assertThat(workerReference.get().isShutdown()).isTrue();
        assertThat(cleanupReference.get().isShutdown()).isTrue();
    }
}
