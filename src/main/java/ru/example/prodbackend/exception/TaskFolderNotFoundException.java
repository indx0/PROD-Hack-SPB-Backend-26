package ru.example.prodbackend.exception;

public class TaskFolderNotFoundException extends RuntimeException {
    public TaskFolderNotFoundException() {
        super("Папка задач не найдена в этом переезде");
    }
}
