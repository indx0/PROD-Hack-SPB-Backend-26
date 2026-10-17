package ru.example.prodbackend.exception;

public class TaskSetAlreadyAppliedException extends RuntimeException {
    public TaskSetAlreadyAppliedException() {
        super("Шаблон уже применён с другим первоначальным назначением. Переместите существующие задачи через PATCH задач.");
    }
}
