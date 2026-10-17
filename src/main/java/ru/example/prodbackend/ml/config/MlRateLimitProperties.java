package ru.example.prodbackend.ml.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("ml.rate-limit")
@Validated
public record MlRateLimitProperties(
        @DefaultValue("15") @Min(1) int maxRequests,
        @DefaultValue("60") @Min(1) @Max(Long.MAX_VALUE / 1_000_000_000L) long windowSeconds) {
}
