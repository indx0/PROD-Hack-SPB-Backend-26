package ru.example.prodbackend.exception;

public class MoveTaskNotFoundException extends RuntimeException {
    public MoveTaskNotFoundException(String message) {
        super(message);
    }
}
