package ru.example.prodbackend.ml.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MlRateLimitException extends MlException {
    private final long retryAfterSeconds;

    public MlRateLimitException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "ML_RATE_LIMIT_EXCEEDED", "Превышен лимит запросов к ML API");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
