package ru.example.prodbackend.exception;

public class ChecklistItemNotFoundException extends RuntimeException {

    public ChecklistItemNotFoundException() {
        super("Пункт чек-листа не найден в этом проекте");
    }
}
