package ru.example.prodbackend.ml.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import ru.example.prodbackend.ml.config.MlRateLimitProperties;
import ru.example.prodbackend.ml.exception.MlRateLimitException;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public class MlRateLimiter {
    private final ConcurrentHashMap<UUID, ArrayDeque<Long>> requests = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowNanos;
    private final LongSupplier ticker;
    private final Counter rejected;

    public MlRateLimiter(MlRateLimitProperties properties, MeterRegistry meters, LongSupplier ticker) {
        this.maxRequests = properties.getMaxRequests();
        this.windowNanos = Math.multiplyExact(properties.getWindowSeconds(), 1_000_000_000L);
        this.ticker = ticker;
        this.rejected = meters.counter("movermate.ml.rate_limit.rejected");
    }

    public void acquire(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        requests.compute(userId, (id, timestamps) -> {
            long now = ticker.getAsLong();
            if (timestamps == null) timestamps = new ArrayDeque<>();
            expire(timestamps, now);
            if (timestamps.size() >= maxRequests) {
                long remaining = windowNanos - (now - timestamps.getFirst());
                long seconds = TimeUnit.NANOSECONDS.toSeconds(remaining);
                if (remaining % TimeUnit.SECONDS.toNanos(1) != 0) seconds++;
                rejected.increment();
                throw new MlRateLimitException(seconds);
            }
            timestamps.addLast(now);
            return timestamps;
        });
    }

    public void removeExpired() {
        requests.forEach((id, ignored) -> requests.computeIfPresent(id, (userId, timestamps) -> {
            expire(timestamps, ticker.getAsLong());
            return timestamps.isEmpty() ? null : timestamps;
        }));
    }

    private void expire(ArrayDeque<Long> timestamps, long now) {
        while (!timestamps.isEmpty() && now - timestamps.getFirst() >= windowNanos) {
            timestamps.removeFirst();
        }
    }
}
