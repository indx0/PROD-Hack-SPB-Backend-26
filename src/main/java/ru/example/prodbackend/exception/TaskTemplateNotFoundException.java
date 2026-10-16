package ru.example.prodbackend.exception;

public class TaskTemplateNotFoundException extends RuntimeException {
    public TaskTemplateNotFoundException(String message) {
        super(message);
    }
}
