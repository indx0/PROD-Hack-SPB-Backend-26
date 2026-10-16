package ru.example.prodbackend.exception;

import lombok.Getter;

@Getter
public class RequestValidationException extends RuntimeException {

    private final String field;

    public RequestValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
