package ru.example.prodbackend.ml.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("ml.rate-limit")
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlRateLimitProperties {

    @Min(1)
    private int maxRequests = 15;

    @Min(1)
    @Max(Long.MAX_VALUE / 1_000_000_000L)
    private long windowSeconds = 60L;
}
