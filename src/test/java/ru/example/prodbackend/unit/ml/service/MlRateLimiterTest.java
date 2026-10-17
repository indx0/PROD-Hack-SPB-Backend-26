package ru.example.prodbackend.unit.ml.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.example.prodbackend.ml.config.MlRateLimitProperties;
import ru.example.prodbackend.ml.exception.MlRateLimitException;
import ru.example.prodbackend.ml.service.MlRateLimiter;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

class MlRateLimiterTest {
    private final AtomicLong now = new AtomicLong();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MlRateLimiter limiter = new MlRateLimiter(new MlRateLimitProperties(15, 60), meters, now::get);
    private final UUID user = UUID.randomUUID();

    @Test
    void sixteenthRequestIsRejectedAndOtherUsersAreIndependent() {
        fill();
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOfSatisfying(MlRateLimitException.class, error -> {
            assertThat(error.getCode()).isEqualTo("ML_RATE_LIMIT_EXCEEDED");
            assertThat(error.getRetryAfterSeconds()).isEqualTo(60);
        });
        limiter.acquire(UUID.randomUUID());
        assertThat(meters.get("movermate.ml.rate_limit.rejected").counter().count()).isEqualTo(1);
        assertThat(meters.get("movermate.ml.rate_limit.rejected").counter().getId().getTags()).isEmpty();
    }

    @Test
    void roundsRetryAfterUpAndRejectedAttemptsDoNotExtendTheWindow() {
        fill();
        now.set(TimeUnit.SECONDS.toNanos(59) + 1);
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOfSatisfying(MlRateLimitException.class,
                error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(1));
        now.set(TimeUnit.SECONDS.toNanos(60) - 1);
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOf(MlRateLimitException.class);
        now.incrementAndGet();
        fill();
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOf(MlRateLimitException.class);
    }

    @Test
    void usesSlidingWindowInsteadOfResettingAtMinuteBoundaries() {
        limiter.acquire(user);
        now.set(TimeUnit.SECONDS.toNanos(30));
        for (int i = 0; i < 14; i++) limiter.acquire(user);
        now.set(TimeUnit.SECONDS.toNanos(60));
        limiter.acquire(user);
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOfSatisfying(MlRateLimitException.class,
                error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(30));
        now.set(TimeUnit.SECONDS.toNanos(90));
        for (int i = 0; i < 14; i++) limiter.acquire(user);
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOf(MlRateLimitException.class);
    }

    @Test
    void cleanupRemovesOnlyExpiredUsers() {
        fill();
        now.set(TimeUnit.SECONDS.toNanos(30));
        UUID active = UUID.randomUUID();
        limiter.acquire(active);
        now.set(TimeUnit.SECONDS.toNanos(60));
        limiter.removeExpired();
        var tracked = (Map<?, ?>) ReflectionTestUtils.getField(limiter, "requests");
        assertThat(tracked).hasSize(1);
        assertThat(tracked.containsKey(active)).isTrue();
        fill();
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOf(MlRateLimitException.class);
    }

    @Test
    void concurrentAdmissionsAndCleanupCannotExceedTheQuota() throws Exception {
        fill();
        now.set(TimeUnit.SECONDS.toNanos(60));
        ExecutorService workers = Executors.newFixedThreadPool(16);
        var start = new CountDownLatch(1);
        try {
            var attempts = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 100; i++) {
                attempts.add(workers.submit(() -> {
                    start.await();
                    limiter.removeExpired();
                    try {
                        limiter.acquire(user);
                        return true;
                    } catch (MlRateLimitException ignored) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (var attempt : attempts) if (attempt.get(5, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(15);
            assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOf(MlRateLimitException.class);
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void supportsNanoTimeWraparound() {
        now.set(Long.MAX_VALUE - 1000);
        fill();
        now.addAndGet(TimeUnit.SECONDS.toNanos(60));
        fill();
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOf(MlRateLimitException.class);
    }

    private void fill() {
        for (int i = 0; i < 15; i++) limiter.acquire(user);
    }
}
