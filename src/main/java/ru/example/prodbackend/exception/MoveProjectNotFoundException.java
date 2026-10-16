package ru.example.prodbackend.exception;

public class MoveProjectNotFoundException extends RuntimeException {
    public MoveProjectNotFoundException(String message) {
        super(message);
    }
}
