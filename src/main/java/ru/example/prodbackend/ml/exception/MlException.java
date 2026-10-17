package ru.example.prodbackend.ml.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@Getter
public class MlException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final UUID traceId = UUID.randomUUID();

    public MlException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
