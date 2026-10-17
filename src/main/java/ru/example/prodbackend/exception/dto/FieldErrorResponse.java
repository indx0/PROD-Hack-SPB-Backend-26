package ru.example.prodbackend.exception.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FieldErrorResponse {
    @Schema(example = "name", description = "Имя поля запроса с ошибкой")
    private String field;
    @Schema(example = "name is blank", description = "Сообщение проверки поля; передаётся без перевода")
    private String issue;
}