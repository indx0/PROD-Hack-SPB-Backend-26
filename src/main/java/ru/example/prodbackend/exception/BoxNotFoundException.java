package ru.example.prodbackend.exception;

public class BoxNotFoundException extends RuntimeException {

    public BoxNotFoundException() {
        super("Коробка не найдена");
    }
}
