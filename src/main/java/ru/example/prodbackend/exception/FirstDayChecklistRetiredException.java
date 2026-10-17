package ru.example.prodbackend.exception;

public class FirstDayChecklistRetiredException extends RuntimeException {
    public FirstDayChecklistRetiredException() {
        super("Чек-лист первого дня перенесён в обычные задачи. Используйте API задач и папок.");
    }
}
