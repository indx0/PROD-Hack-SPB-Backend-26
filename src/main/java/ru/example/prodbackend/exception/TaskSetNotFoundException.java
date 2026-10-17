package ru.example.prodbackend.exception;

public class TaskSetNotFoundException extends RuntimeException {
    public TaskSetNotFoundException() { super("Набор задач не найден"); }
}
